package io.github.omegadestroyer.pairfree.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresPermission
import io.github.omegadestroyer.pairfree.protocol.GattEnvelope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
 * Connecting follows QCY's app (`QCYConnectManager.connectBLE` over BluetoothKit's `BleConnectRequest`,
 * docs/qcy/PROTOCOL.md section 2): up to [ATTEMPTS] connections of [CONNECT_MS] each, [RETRY_MS] apart; the
 * services found, up to [DISCOVER_TRIES] times of [DISCOVER_MS] each; the notifications subscribed; and only
 * then a larger MTU asked for.
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
    /** One connection attempt: each step completes its deferred, and a drop fails all of them. */
    private class Attempt {
        /** Set once `connectGatt` returns; until then the attempt takes the callbacks of any connection (the older ones are closed). */
        @Volatile var gatt: BluetoothGatt? = null

        val linked = CompletableDeferred<Unit>()
        var discovered = CompletableDeferred<Int>()
        val subscribed = CompletableDeferred<Unit>()
        val mtu = CompletableDeferred<Unit>()

        fun fail(e: IOException) {
            linked.completeExceptionally(e)
            discovered.completeExceptionally(e)
            subscribed.completeExceptionally(e)
            mtu.completeExceptionally(e)
        }
    }

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var attempt = Attempt()
    @Volatile private var open = false
    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private val done = Channel<Int>(Channel.CONFLATED)
    private val lock = Mutex()
    private val pending = ArrayDeque<BluetoothGattCharacteristic>()
    @Volatile private var closed = false

    /** The attempt [gatt] belongs to; null for a connection an earlier attempt left behind, whose callbacks are ignored. */
    private fun current(gatt: BluetoothGatt): Attempt? = attempt.takeIf { it.gatt == null || it.gatt === gatt }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val a = current(gatt) ?: return
            when {
                newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                    log?.invoke("gatt: connected")
                    a.linked.complete(Unit)
                }
                newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS -> fail(a, "disconnected (status $status)")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            current(gatt)?.discovered?.complete(status)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val a = current(gatt) ?: return
            if (status != BluetoothGatt.GATT_SUCCESS) log?.invoke("subscribe ${short(descriptor.characteristic.uuid)} refused (status $status)")
            subscribeNext(gatt, a)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            val a = current(gatt) ?: return
            log?.invoke("mtu $mtu (status $status)")
            a.mtu.complete(Unit)
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

    /** Subscribes the next notifying characteristic, one descriptor write at a time; done when none are left. */
    @Suppress("MissingPermission")
    private fun subscribeNext(gatt: BluetoothGatt, a: Attempt) {
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
        a.subscribed.complete(Unit)
    }

    /** A drop or failure of attempt [a]; once the link is open, it ends [reads]. */
    private fun fail(a: Attempt, reason: String) {
        log?.invoke("gatt: $reason")
        val e = IOException(reason)
        a.fail(e)
        if (open) incoming.close(e)
    }

    /**
     * Connects over LE and subscribes, trying again as QCY's app does; throws an IOException when no attempt
     * gets that far. A link that does not open closes itself, so no half-open connection is left behind.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun connect() {
        try {
            var last = IOException("no answer over LE")
            for (n in 1..ATTEMPTS) {
                if (closed) throw IOException("closed")
                if (n > 1) {
                    delay(RETRY_MS)
                    log?.invoke("LE connection, attempt $n of $ATTEMPTS")
                }
                try {
                    once()
                    open = true
                    return
                } catch (e: NoService) {
                    // The earbuds answered and have no such service: another try finds the same (issue #28).
                    drop()
                    throw IOException(e.message)
                } catch (e: IOException) {
                    last = e
                    drop()
                }
            }
            throw last
        } catch (e: Throwable) {
            close()
            throw e
        }
    }

    private class NoService(message: String) : IOException(message)

    /** One attempt: connect, find the service, subscribe, then ask for the larger MTU (not needed to go on). */
    @Suppress("MissingPermission")
    private suspend fun once() {
        val a = Attempt()
        attempt = a
        val g = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE) ?: throw IOException("connectGatt failed")
        a.gatt = g
        gatt = g
        // As BluetoothKit's BleConnectWorker.openGatt does right after connectGatt.
        try {
            g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        } catch (_: Exception) {
        }
        withTimeoutOrNull(CONNECT_MS) { a.linked.await() } ?: throw IOException("no answer over LE").also { log?.invoke("gatt: no answer within ${CONNECT_MS / 1000} s") }

        var s: BluetoothGattService? = null
        for (t in 1..DISCOVER_TRIES) {
            if (t > 1) delay(RETRY_MS)
            a.discovered = CompletableDeferred()
            if (!g.discoverServices()) {
                log?.invoke("gatt: could not discover services")
                continue
            }
            val status = withTimeoutOrNull(DISCOVER_MS) { a.discovered.await() }
            if (status == null) {
                log?.invoke("gatt: services not found within ${DISCOVER_MS / 1000} s")
                continue
            }
            s = g.getService(service)
            if (status == BluetoothGatt.GATT_SUCCESS && s != null) break
            log?.invoke("gatt: no control service (status $status)")
            if (status == BluetoothGatt.GATT_SUCCESS) throw NoService("no control service (status $status)")
        }
        if (s == null) throw IOException("no control service")

        pending.clear()
        pending.addAll(s.characteristics.filter { it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 })
        subscribeNext(g, a)
        withTimeoutOrNull(DISCOVER_MS) { a.subscribed.await() } ?: throw IOException("subscribing not answered")

        if (g.requestMtu(MTU)) withTimeoutOrNull(OPERATION_MS) { a.mtu.await() } ?: log?.invoke("mtu not answered")
    }

    /** Ends the attempt's connection before the next one. */
    @Suppress("MissingPermission")
    private fun drop() {
        val g = gatt
        gatt = null
        try {
            g?.disconnect()
            g?.close()
        } catch (_: SecurityException) {
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

    override fun close() {
        if (closed) return
        closed = true
        drop()
        val e = IOException("closed")
        log?.invoke("gatt: closed")
        attempt.fail(e)
        incoming.close(e)
    }

    companion object {
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val BASE = "-0000-1000-8000-00805f9b34fb"

        /** The 16-bit short form of a Bluetooth base UUID. */
        fun short(uuid: UUID): Int = ((uuid.mostSignificantBits shr 32) and 0xFFFF).toInt()

        /** The full UUID of a short one. */
        fun full(short: Int): UUID = UUID.fromString("0000" + short.toString(16).padStart(4, '0') + BASE)

        /** QCY's app asks for 512 once the services are known (BluetoothKit's `BleChangeMTURequest`). */
        private const val MTU = 512

        /** QCY's app: one connection plus three retries, 1 s apart (`QCYConnectManager.connectBLE(mac, z)` = `connectBLE(mac, 3, 10000, z)`). */
        const val ATTEMPTS = 4
        private const val RETRY_MS = 1_000L

        /** QCY's app waits 10 s for each connection (the same call). */
        const val CONNECT_MS = 10_000L

        /** QCY's app: service discovery 20 s, retried three times. */
        private const val DISCOVER_MS = 20_000L
        private const val DISCOVER_TRIES = 4
        private const val OPERATION_MS = 3_000L
    }
}
