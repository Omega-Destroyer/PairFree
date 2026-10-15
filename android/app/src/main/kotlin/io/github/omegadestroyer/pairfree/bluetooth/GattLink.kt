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
 * 3). As JBL's app does, it asks for a 512-byte MTU, subscribes to the notify characteristic and writes
 * each frame, with a response, to the write characteristic. Each notification is one frame.
 *
 * The earbuds must be paired for audio; PairFree does not bond over LE. [device] is the earbuds' LE
 * side: the classic address itself where they share it, else the device an LE scan found ([JblFinder]).
 */
class GattLink(
    private val context: Context,
    private val device: BluetoothDevice,
    private val log: ((String) -> Unit)? = null,
) : ControlLink {
    private var gatt: BluetoothGatt? = null
    private var write: BluetoothGattCharacteristic? = null
    private val ready = CompletableDeferred<Unit>()
    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private val written = Channel<Int>(Channel.CONFLATED)
    private val writeLock = Mutex()
    @Volatile private var closed = false

    private val callback = object : BluetoothGattCallback() {
        @Suppress("MissingPermission") // the link is only made with the permission
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when {
                newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                    if (!gatt.requestMtu(MTU)) gatt.discoverServices()
                }
                newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS -> fail("disconnected (status $status)")
            }
        }

        @Suppress("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            log?.invoke("mtu $mtu (status $status)")
            if (!gatt.discoverServices()) fail("could not discover services")
        }

        @Suppress("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(SERVICE)
            val notify = service?.getCharacteristic(NOTIFY)
            val w = service?.getCharacteristic(WRITE)
            if (status != BluetoothGatt.GATT_SUCCESS || notify == null || w == null) {
                fail("no Harman command service (status $status)")
                return
            }
            write = w
            val cccd = notify.getDescriptor(CCCD)
            if (!gatt.setCharacteristicNotification(notify, true) || cccd == null) {
                fail("could not subscribe")
                return
            }
            val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                cccd.setValue(value) && gatt.writeDescriptor(cccd)
            }
            if (!started) fail("could not subscribe")
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) ready.complete(Unit) else fail("subscribe refused (status $status)")
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            written.trySend(status)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == NOTIFY) incoming.trySend(value.copyOf())
        }

        // Android 13 and later call both forms; before 13 only this one.
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || characteristic.uuid != NOTIFY) return
            @Suppress("DEPRECATION")
            characteristic.value?.let { incoming.trySend(it.copyOf()) }
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
            withTimeoutOrNull(CONNECT_MS) { ready.await() } ?: throw IOException("no answer over LE")
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
        /** Harman's command service ("excelpoint.com" in ASCII) and its characteristics, from JBL's app (Constants). */
        val SERVICE: UUID = UUID.fromString("65786365-6c70-6f69-6e74-2e636f6d0000")
        val NOTIFY: UUID = UUID.fromString("65786365-6c70-6f69-6e74-2e636f6d0001")
        val WRITE: UUID = UUID.fromString("65786365-6c70-6f69-6e74-2e636f6d0002")
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** The MTU JBL's app asks for. */
        private const val MTU = 512

        /** How long an LE connection may take before PairFree gives up on it. */
        const val CONNECT_MS = 10_000L

        private const val WRITE_MS = 3_000L
    }
}
