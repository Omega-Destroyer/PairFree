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
import io.github.omegadestroyer.pairfree.protocol.GattEnvelope
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
 * A Bluetooth LE control channel spread over several characteristics of one [service], as QCY's is
 * (docs/qcy/PROTOCOL.md section 2). Frames to and from it are [GattEnvelope]s: each write or read names
 * its characteristic by the 16-bit short UUID, and each notification and read result comes back the same
 * way. As QCY's app does, it subscribes to every characteristic of the service that notifies.
 *
 * The earbuds must be paired for audio; PairFree does not bond over LE. [device] is their LE side, which
 * an advert scan found ([QcyFinder]).
 */
class EnvelopeGattLink(
    private val context: Context,
    private val device: BluetoothDevice,
    private val service: UUID,
    private val log: ((String) -> Unit)? = null,
) : ControlLink {
    private var gatt: BluetoothGatt? = null
    private val ready = CompletableDeferred<Unit>()
    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private val done = Channel<Int>(Channel.CONFLATED)
    private val lock = Mutex()
    private val pending = ArrayDeque<BluetoothGattCharacteristic>()
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

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val s = gatt.getService(service)
            if (status != BluetoothGatt.GATT_SUCCESS || s == null) {
                fail("no control service (status $status)")
                return
            }
            pending.clear()
            pending.addAll(s.characteristics.filter { it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 })
            subscribeNext(gatt)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) log?.invoke("subscribe ${short(descriptor.characteristic.uuid)} refused (status $status)")
            subscribeNext(gatt)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            done.trySend(status)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) incoming.trySend(GattEnvelope.received(short(characteristic.uuid), value.copyOf()))
            done.trySend(status)
        }

        // Android 13 and later call both forms; before 13 only this one.
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            @Suppress("DEPRECATION")
            if (status == BluetoothGatt.GATT_SUCCESS) characteristic.value?.let { incoming.trySend(GattEnvelope.received(short(characteristic.uuid), it.copyOf())) }
            done.trySend(status)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            incoming.trySend(GattEnvelope.received(short(characteristic.uuid), value.copyOf()))
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            @Suppress("DEPRECATION")
            characteristic.value?.let { incoming.trySend(GattEnvelope.received(short(characteristic.uuid), it.copyOf())) }
        }
    }

    /** Subscribes the next notifying characteristic, one descriptor write at a time; ready when none are left. */
    @Suppress("MissingPermission")
    private fun subscribeNext(gatt: BluetoothGatt) {
        while (pending.isNotEmpty()) {
            val c = pending.removeFirst()
            val cccd = c.getDescriptor(CCCD) ?: continue
            if (!gatt.setCharacteristicNotification(c, true)) continue
            val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                (cccd.setValue(value) && gatt.writeDescriptor(cccd))
            }
            if (started) return
        }
        ready.complete(Unit)
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

    /** Carries out one [GattEnvelope]: a write (with a response where the characteristic takes one) or a read, and waits for it. */
    @Suppress("MissingPermission")
    override suspend fun send(frame: ByteArray): Unit = lock.withLock {
        val g = gatt ?: throw IOException("not connected")
        val out = GattEnvelope.out(frame) ?: throw IOException("not an envelope")
        val c = g.getService(service)?.getCharacteristic(full(out.short)) ?: throw IOException("no characteristic ${out.short.toString(16)}")
        while (done.tryReceive().isSuccess) Unit
        val started = if (out.operation == GattEnvelope.READ) {
            g.readCharacteristic(c)
        } else {
            val type = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(c, out.value, type) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                c.writeType = type
                @Suppress("DEPRECATION")
                c.setValue(out.value) && g.writeCharacteristic(c)
            }
        }
        if (!started) throw IOException("gatt operation refused")
        val status = withTimeoutOrNull(OPERATION_MS) { done.receive() } ?: throw IOException("gatt operation not answered")
        log?.invoke("${if (out.operation == GattEnvelope.READ) "read" else "send"} ${out.short.toString(16).padStart(4, '0')} ${out.value.joinToString("") { "%02x".format(it) }} (status $status)")
        Unit
    }

    /** Each notification and read result as it comes, as a [GattEnvelope]. Ends with an IOException when the link drops. */
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
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val BASE = "-0000-1000-8000-00805f9b34fb"

        /** The 16-bit short form of a Bluetooth base UUID. */
        fun short(uuid: UUID): Int = ((uuid.mostSignificantBits shr 32) and 0xFFFF).toInt()

        /** The full UUID of a short one. */
        fun full(short: Int): UUID = UUID.fromString("0000" + short.toString(16).padStart(4, '0') + BASE)

        private const val MTU = 512
        const val CONNECT_MS = 10_000L
        private const val OPERATION_MS = 3_000L
    }
}
