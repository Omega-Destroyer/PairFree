package io.github.omegadestroyer.pairfree.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresPermission
import io.github.omegadestroyer.pairfree.brands.GattChannel
import io.github.omegadestroyer.pairfree.protocol.jbl.JblRace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID

/**
 * JBL's control channel: Harman's command service over Bluetooth LE GATT (docs/jbl/PROTOCOL.md section
 * 3), or on JBL's Airoha models Airoha's ([AIROHA], section 10). As JBL's app does, it asks for a large
 * MTU, subscribes to the notify characteristic and writes each frame, with a response, to the write
 * characteristic. Each notification is one frame.
 *
 * The earbuds must be paired for audio; PairFree does not bond over LE. [device] is the earbuds' LE
 * side: the classic address itself where they share it, else the device an LE scan found ([JblFinder]).
 */
class GattLink(
    private val context: Context,
    private val device: BluetoothDevice,
    private val log: ((String) -> Unit)? = null,
    /** JBL's protocol 4 security modes 1 and 3 also subscribe the raw characteristic (docs/jbl/PROTOCOL.md section 7). */
    private val rawNotify: Boolean = false,
    /** Mode 3 writes this frame (the host name's checksum) to the raw characteristic before the main subscription. */
    private val rawPreamble: ByteArray? = null,
    /** The service and its characteristics: Harman's, or [AIROHA]'s. */
    private val uuids: Uuids = HARMAN,
    /** Sennheiser's Bluetooth LE bonding (docs/sennheiser/PROTOCOL.md section 6), for a device that is not bonded yet: null for every other brand. */
    private val pairing: Pairing? = null,
) : GattChannel {
    /** The characteristic Sennheiser's pairing steps are written to, and the encrypted one whose read provokes the bond. */
    class Pairing(val characteristic: UUID, val encrypted: UUID)

    /** A command service: its UUID, the characteristic the device notifies on, the one PairFree writes to, and the MTU asked for. */
    class Uuids(val service: UUID, val notify: UUID, val write: UUID, val mtu: Int)

    private var gatt: BluetoothGatt? = null
    private var write: BluetoothGattCharacteristic? = null
    private var raw: BluetoothGattCharacteristic? = null
    private val ready = CompletableDeferred<Unit>()
    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private val written = Channel<Int>(Channel.CONFLATED)
    private val writeLock = Mutex()
    @Volatile private var closed = false

    /** The MTU the phone and device agreed on; 23 (the least) until they do. */
    @Volatile override var mtu: Int = MIN_MTU
        private set

    /** Where the opening sequence is: through the raw characteristic's extra steps, or the main subscription. */
    private enum class Phase { RAW_SUBSCRIBE, RAW_WRITE, NOTIFY_SUBSCRIBE, PAIR_RESET, PAIR_USER, PAIR_WAIT }
    private var commandService: android.bluetooth.BluetoothGattService? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var phase = Phase.NOTIFY_SUBSCRIBE

    private val callback = object : BluetoothGattCallback() {
        @Suppress("MissingPermission") // the link is only made with the permission
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when {
                newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                    if (!gatt.requestMtu(uuids.mtu)) gatt.discoverServices()
                }
                newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS -> fail("disconnected (status $status)")
            }
        }

        @Suppress("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            log?.invoke("mtu $mtu (status $status)")
            if (status == BluetoothGatt.GATT_SUCCESS) this@GattLink.mtu = mtu
            if (!gatt.discoverServices()) fail("could not discover services")
        }

        @Suppress("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            // The product's service holds the command characteristics; Sennheiser's models list more than one service that may.
            val service = gatt.getService(uuids.service)?.takeIf { it.getCharacteristic(uuids.notify) != null } ?: gatt.services.firstOrNull { it.getCharacteristic(uuids.notify) != null && it.getCharacteristic(uuids.write) != null }
            commandService = service
            val notify = service?.getCharacteristic(uuids.notify)
            val w = service?.getCharacteristic(uuids.write)
            if (status != BluetoothGatt.GATT_SUCCESS || notify == null || w == null) {
                fail("no command service (status $status)")
                return
            }
            write = w
            raw = service.getCharacteristic(RAW)
            // Modes 1 and 3 subscribe the raw characteristic first, as JBL's app does; without it, go straight to notify.
            if (rawNotify && raw != null) {
                phase = Phase.RAW_SUBSCRIBE
                if (!subscribe(gatt, raw!!)) subscribeNotify(gatt)
            } else if (pairing != null && device.bondState != BluetoothDevice.BOND_BONDED) {
                startPairing(gatt)
            } else {
                subscribeNotify(gatt)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            when (phase) {
                // The raw steps are best-effort: carry on even if they are refused, so the main channel still opens.
                Phase.RAW_SUBSCRIBE -> if (rawPreamble != null && raw != null && writeRaw(gatt)) phase = Phase.RAW_WRITE else subscribeNotify(gatt)
                Phase.NOTIFY_SUBSCRIBE -> if (status == BluetoothGatt.GATT_SUCCESS) ready.complete(Unit) else fail("subscribe refused (status $status)")
                Phase.RAW_WRITE, Phase.PAIR_RESET, Phase.PAIR_USER, Phase.PAIR_WAIT -> Unit
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            when {
                characteristic.uuid == RAW && phase == Phase.RAW_WRITE -> subscribeNotify(gatt)
                // Sennheiser's steps: reset the earbuds' pairing state, then ask for the user's action (the buttons), then wait for the bond.
                phase == Phase.PAIR_RESET && characteristic.uuid == pairing?.characteristic -> {
                    phase = Phase.PAIR_USER
                    main.postDelayed({ if (!closed) writePairing(gatt, PAIRING_USER_ACTION) }, 200)
                }
                phase == Phase.PAIR_USER && characteristic.uuid == pairing?.characteristic -> {
                    phase = Phase.PAIR_WAIT
                    waitForBond(gatt, System.currentTimeMillis())
                }
                else -> written.trySend(status)
            }
        }

        @Suppress("MissingPermission")
        private fun writeRaw(gatt: BluetoothGatt): Boolean {
            val c = raw ?: return false
            val frame = rawPreamble ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(c, frame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                (c.setValue(frame) && gatt.writeCharacteristic(c))
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == uuids.notify || characteristic.uuid == RAW) incoming.trySend(value.copyOf())
        }

        // Android 13 and later call both forms; before 13 only this one.
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || (characteristic.uuid != uuids.notify && characteristic.uuid != RAW)) return
            @Suppress("DEPRECATION")
            characteristic.value?.let { incoming.trySend(it.copyOf()) }
        }
    }

    @Suppress("MissingPermission")
    private fun subscribeNotify(gatt: BluetoothGatt) {
        phase = Phase.NOTIFY_SUBSCRIBE
        val notify = commandService?.getCharacteristic(uuids.notify)
        if (notify == null || !subscribe(gatt, notify)) fail("could not subscribe")
    }

    @Suppress("MissingPermission")
    private fun startPairing(gatt: BluetoothGatt) {
        val p = pairing ?: return subscribeNotify(gatt)
        log?.invoke("gatt: not bonded, starting Sennheiser's pairing steps")
        phase = Phase.PAIR_RESET
        if (!writePairing(gatt, PAIRING_RESET)) subscribeNotify(gatt)
    }

    @Suppress("MissingPermission")
    private fun writePairing(gatt: BluetoothGatt, code: Int): Boolean {
        val p = pairing ?: return false
        val c = gatt.services.firstNotNullOfOrNull { it.getCharacteristic(p.characteristic) } ?: return false
        val frame = byteArrayOf(code.toByte())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(c, frame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            (c.setValue(frame) && gatt.writeCharacteristic(c))
        }
    }

    /** Reads the encrypted characteristic every second, as the app does, which makes Android pair; carries on once the device is bonded, or fails after a minute. */
    @Suppress("MissingPermission")
    private fun waitForBond(gatt: BluetoothGatt, since: Long) {
        if (closed) return
        when {
            device.bondState == BluetoothDevice.BOND_BONDED -> subscribeNotify(gatt)
            System.currentTimeMillis() - since > BOND_MS -> fail("not bonded after a minute: hold the earbuds' buttons as the app asks")
            else -> {
                pairing?.let { p -> gatt.services.firstNotNullOfOrNull { it.getCharacteristic(p.encrypted) }?.let { gatt.readCharacteristic(it) } }
                main.postDelayed({ waitForBond(gatt, since) }, 1000)
            }
        }
    }

    /** Turns on notifications for [characteristic] and writes its client-config descriptor. */
    @Suppress("MissingPermission")
    private fun subscribe(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Boolean {
        val cccd = characteristic.getDescriptor(CCCD) ?: return false
        if (!gatt.setCharacteristicNotification(characteristic, true)) return false
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            (cccd.setValue(value) && gatt.writeDescriptor(cccd))
        }
    }

    private fun fail(reason: String) {
        log?.invoke("gatt: $reason")
        val e = IOException(reason)
        ready.completeExceptionally(e)
        incoming.close(e)
    }

    /**
     * Connects over LE and subscribes; throws an IOException when that does not happen within [CONNECT_MS].
     * A link that does not open closes itself, so no half-open connection is left behind.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun connect() {
        try {
            if (closed) throw IOException("closed")
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE) ?: throw IOException("connectGatt failed")
            withTimeoutOrNull(if (pairing != null) CONNECT_MS + BOND_MS else CONNECT_MS) { ready.await() } ?: throw IOException("no answer over LE")
        } catch (e: Throwable) {
            close()
            throw e
        }
    }

    /** Writes one frame and waits for the write's response. */
    @Suppress("MissingPermission")
    override suspend fun send(frame: ByteArray): Unit = writeLock.withLock {
        val g = gatt ?: throw IOException("not connected")
        val w = write ?: throw IOException("not connected")
        while (written.tryReceive().isSuccess) Unit
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(w, frame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            w.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            w.setValue(frame) && g.writeCharacteristic(w)
        }
        if (!started) throw IOException("write refused")
        val status = withTimeoutOrNull(WRITE_MS) { written.receive() } ?: throw IOException("write not acknowledged")
        if (status != BluetoothGatt.GATT_SUCCESS) throw IOException("write failed (status $status)")
        log?.invoke("send ${frame.joinToString("") { "%02x".format(it) }}")
        Unit
    }

    /** Each notification as it comes. Ends with an IOException when the link drops. */
    override fun reads(): Flow<ByteArray> = incoming.receiveAsFlow()

    @Suppress("MissingPermission")
    override fun close() {
        closed = true
        val g = gatt
        gatt = null
        try {
            g?.disconnect()
            g?.close()
        } catch (_: SecurityException) {
        }
        fail("closed")
    }

    companion object {
        /** Harman's command service ("excelpoint.com" in ASCII) and its characteristics, from JBL's app (Constants), with the MTU it asks for. */
        val HARMAN = Uuids(
            UUID.fromString("65786365-6c70-6f69-6e74-2e636f6d0000"),
            UUID.fromString("65786365-6c70-6f69-6e74-2e636f6d0001"),
            UUID.fromString("65786365-6c70-6f69-6e74-2e636f6d0002"),
            512,
        )

        /** Airoha's service on JBL's Airoha models (data/jbl/commands.json), with the MTU Airoha's SDK asks for (GattLinkParam). */
        val AIROHA = Uuids(UUID.fromString(JblRace.GATT_SERVICE), UUID.fromString(JblRace.GATT_NOTIFY), UUID.fromString(JblRace.GATT_WRITE), 517)

        /** The raw characteristic JBL's security modes 1 and 3 use for their extra handshake steps. */
        val RAW: UUID = UUID.fromString("65786365-6c70-6f69-6e74-2e636f6d0003")
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** How long an LE connection may take before PairFree gives up on it. */
        const val CONNECT_MS = 10_000L

        /** Sennheiser's pairing steps (the app's `RESET_REQ` and `USER_ACTION_REQUIRED_REQ`) and how long the bonding may take. */
        private const val PAIRING_RESET = 0
        private const val PAIRING_USER_ACTION = 1
        private const val BOND_MS = 60_000L

        private const val WRITE_MS = 3_000L
        private const val MIN_MTU = 23
    }
}
