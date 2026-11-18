@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.omegadestroyer.pairfree.windows

import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.brands.GattChannel
import io.github.omegadestroyer.pairfree.brands.GattPairing
import io.github.omegadestroyer.pairfree.brands.GattSpec
import io.github.omegadestroyer.pairfree.brands.LeTarget
import io.github.omegadestroyer.pairfree.protocol.GattEnvelope
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import kotlin.time.TimeSource

/** A Bluetooth LE device found by address: Windows' own name for it is its 48-bit address as a number. */
internal class WinLeTarget(override val address: String) : LeTarget

/** One GATT characteristic: its UUID and properties, and what can be done with it. */
internal class Characteristic(val c: Com) {
    val uuid: String by lazy { WinRt.guid(c, 11) }
    val properties: Int get() = WinRt.int(c, 7)
    val canNotify: Boolean get() = properties and NOTIFY != 0
    val canWrite: Boolean get() = properties and WRITE != 0

    private var token = 0L
    private var callback: ComCallback? = null

    /** A write; with a response when [withResponse]. True when the device accepted it. */
    suspend fun write(bytes: ByteArray, withResponse: Boolean): Boolean {
        val buffer = WinRt.buffer(bytes) ?: return false
        val op = memScoped {
            val out = alloc<COpaquePointerVar>()
            comSlot(c, 17).reinterpret<CFunction<(Com?, Com?, Int, CPointer<COpaquePointerVar>?) -> Int>>()(c, buffer, if (withResponse) 0 else 1, out.ptr)
            out.value
        }
        comRelease(buffer)
        return WinRt.awaitInt(op, timeoutMs = WRITE_MS) == 0
    }

    /** A read of the value held on the device; null when it failed. */
    suspend fun read(): ByteArray? {
        val op = WinRt.obj(c, 14)
        val result = WinRt.await(op, WRITE_MS) ?: return null
        val ok = WinRt.int(result, 6) == 0
        val value = WinRt.obj(result, 7)
        val bytes = if (ok) WinRt.bytes(value) else null
        comRelease(value)
        comRelease(result)
        return bytes
    }

    /** Turns notifications on; each arrives at [onValue]. True when the device accepted. */
    suspend fun subscribe(onValue: (ByteArray) -> Unit): Boolean {
        val handler = ComCallback("c1f420f6-6292-5760-a2c9-9ddf98683cfc") { _, args ->
            if (args != null) {
                val buffer = WinRt.obj(args, 6)
                val bytes = WinRt.bytes(buffer)
                comRelease(buffer)
                onValue(bytes)
            }
        }
        callback = handler
        token = memScoped {
            val t = alloc<LongVar>()
            comSlot(c, 20).reinterpret<CFunction<(Com?, Com?, CPointer<LongVar>?) -> Int>>()(c, handler.pointer, t.ptr)
            t.value
        }
        val op = memScoped {
            val out = alloc<COpaquePointerVar>()
            comSlot(c, 19).reinterpret<CFunction<(Com?, Int, CPointer<COpaquePointerVar>?) -> Int>>()(c, 1 /* notify */, out.ptr)
            out.value
        }
        return WinRt.awaitInt(op, timeoutMs = WRITE_MS) == 0
    }

    fun unsubscribe() {
        if (callback != null) comSlot(c, 21).reinterpret<CFunction<(Com?, Long) -> Int>>()(c, token)
        callback = null
    }

    fun release() {
        unsubscribe()
        comRelease(c)
    }

    companion object {
        const val WRITE = 0x08
        const val NOTIFY = 0x10
        const val WRITE_MS = 3_000L
    }
}

/** A Bluetooth LE device opened through Windows: its GATT services and their characteristics. */
internal class LeDevice private constructor(private val device: Com, private val device3: Com, private var session: Com?) {
    /** The ATT MTU Windows holds for the connection; 23 (the least) where it cannot say. */
    val mtu: Int get() = session?.let { WinRt.ushort(it, 10) } ?: 23

    private val services = mutableListOf<Com>()
    private val characteristics = mutableListOf<Characteristic>()

    /** The characteristics of the service [uuid], or of every service when null. */
    suspend fun characteristicsOf(uuid: String?): List<Characteristic> {
        val result = if (uuid == null) {
            memScoped {
                val out = alloc<COpaquePointerVar>()
                comSlot(device3, 9).reinterpret<CFunction<(Com?, Int, CPointer<COpaquePointerVar>?) -> Int>>()(device3, 1 /* uncached */, out.ptr)
                WinRt.await(out.value)
            }
        } else {
            memScoped {
                val out = alloc<COpaquePointerVar>()
                comGuid(uuid).usePinned { id ->
                    comSlot(device3, 11).reinterpret<CFunction<(Com?, CPointer<ByteVar>?, Int, CPointer<COpaquePointerVar>?) -> Int>>()(device3, id.addressOf(0), 1, out.ptr)
                }
                WinRt.await(out.value)
            }
        } ?: return emptyList()
        if (WinRt.int(result, 6) != 0) {
            comRelease(result)
            return emptyList()
        }
        val list = WinRt.obj(result, 8)
        val found = mutableListOf<Characteristic>()
        for (s in if (list == null) emptyList() else WinRt.elements(list)) {
            services += s
            val service3 = comQuery(s, "b293a950-0c53-437c-a9b3-5c3210c6e569") ?: continue
            if (session == null) {
                // The service's session: keep the connection while PairFree uses it, as an app's own would, and read the MTU from it.
                session = WinRt.obj(service3, 7)?.also { comSlot(it, 8).reinterpret<CFunction<(Com?, Int) -> Int>>()(it, 1) }
            }
            val chars = memScoped {
                val out = alloc<COpaquePointerVar>()
                comSlot(service3, 12).reinterpret<CFunction<(Com?, Int, CPointer<COpaquePointerVar>?) -> Int>>()(service3, 1, out.ptr)
                WinRt.await(out.value)
            }
            comRelease(service3)
            if (chars == null) continue
            if (WinRt.int(chars, 6) == 0) {
                val vector = WinRt.obj(chars, 8)
                if (vector != null) {
                    for (e in WinRt.elements(vector)) found += Characteristic(e).also { characteristics += it }
                    comRelease(vector)
                }
            }
            comRelease(chars)
        }
        comRelease(list)
        comRelease(result)
        return found
    }

    fun close() {
        characteristics.forEach { it.release() }
        characteristics.clear()
        services.forEach { comRelease(it) }
        services.clear()
        session?.let { memScoped { comSlot(it, 8).reinterpret<CFunction<(Com?, Int) -> Int>>()(it, 0) } }
        comRelease(session)
        comRelease(device3)
        comRelease(device)
        session = null
    }

    companion object {
        /** Opens the device at [address] ("AA:BB:CC:DD:EE:FF"), or null when Windows knows no LE device there. */
        suspend fun open(address: String): LeDevice? {
            WinRt.trace("factory")
            val statics = WinRt.factory("Windows.Devices.Bluetooth.BluetoothLEDevice", "c8cf1a19-f0b6-4bf0-8689-41303de2d9f4") ?: return null
            WinRt.trace("got factory")
            val op = memScoped {
                val out = alloc<COpaquePointerVar>()
                comSlot(statics, 7).reinterpret<CFunction<(Com?, ULong, CPointer<COpaquePointerVar>?) -> Int>>()(statics, addressNumber(address).toULong(), out.ptr)
                out.value
            }
            comRelease(statics)
            WinRt.trace("started operation")
            val device = WinRt.await(op) ?: return null
            WinRt.trace("got device")
            val device3 = comQuery(device, "aee9e493-44ac-40dc-af33-b2c13c01ca46")
            if (device3 == null) {
                comRelease(device)
                return null
            }
            WinRt.trace("got device3")
            return LeDevice(device, device3, null)
        }
    }
}

/**
 * Harman's, Airoha's and Sennheiser's command service over Bluetooth LE through Windows' GATT API, the counterpart of Android's GattLink: the
 * notify characteristic is subscribed and each frame is written, with a response, to the write characteristic; each notification is one
 * frame. The earbuds must be paired for audio. Sennheiser's bonding steps and JBL's raw handshake are done as the Android link does.
 */
internal class WinRtGattChannel(private val address: String, private val spec: GattSpec, private val log: (String) -> Unit) : GattChannel {
    private var device: LeDevice? = null
    private var write: Characteristic? = null
    private val subscribed = mutableListOf<Characteristic>()
    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private val writeLock = Mutex()
    private var closed = false

    override var mtu: Int = 23
        private set

    override suspend fun connect() {
        try {
            if (closed) throw IOException("closed")
            val opened = LeDevice.open(address) ?: throw IOException("Windows knows no Bluetooth LE device at $address")
            device = opened
            val u = spec.uuids
            // The product's service holds the command characteristics; Sennheiser's models list more than one service that may.
            var chars = opened.characteristicsOf(u.service)
            if (chars.none { it.uuid == u.notify.lowercase() }) chars = opened.characteristicsOf(null)
            val notify = chars.firstOrNull { it.uuid == u.notify.lowercase() }
            val w = chars.firstOrNull { it.uuid == u.write.lowercase() }
            if (notify == null || w == null) throw IOException("no command service")
            write = w
            mtu = opened.mtu
            log("mtu $mtu")
            val raw = chars.firstOrNull { it.uuid == RAW }
            // Modes 1 and 3 subscribe the raw characteristic first, as JBL's app does; the raw steps are best effort.
            if (spec.rawNotify && raw != null) {
                if (raw.subscribe { incoming.trySend(it) }) subscribed += raw
                spec.rawPreamble?.let { raw.write(it, true) }
            } else if (spec.pairing != null) {
                pair(chars, spec.pairing!!)
            }
            if (!notify.subscribe { incoming.trySend(it) }) throw IOException("subscribe refused")
            subscribed += notify
        } catch (e: Throwable) {
            close()
            throw e
        }
    }

    /** Sennheiser's steps: reset the earbuds' pairing state, ask for the user's action (the buttons), then read the encrypted characteristic each second, which makes Windows bond. */
    private suspend fun pair(chars: List<Characteristic>, p: GattPairing) {
        val steps = chars.firstOrNull { it.uuid == p.characteristic.lowercase() } ?: return
        val encrypted = chars.firstOrNull { it.uuid == p.encrypted.lowercase() } ?: return
        if (encrypted.read() != null) return // already bonded
        log("gatt: not bonded, starting Sennheiser's pairing steps")
        steps.write(byteArrayOf(0), true)
        delay(200)
        steps.write(byteArrayOf(1), true)
        val started = TimeSource.Monotonic.markNow()
        while (!closed && started.elapsedNow().inWholeMilliseconds < BOND_MS) {
            if (encrypted.read() != null) return
            delay(1000)
        }
        throw IOException("not bonded after a minute: hold the earbuds' buttons as the app asks")
    }

    override suspend fun send(frame: ByteArray): Unit = writeLock.withLock {
        val w = write ?: throw IOException("not connected")
        if (!w.write(frame, true)) throw IOException("write failed")
        log("send " + frame.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') })
    }

    override fun reads(): Flow<ByteArray> = incoming.receiveAsFlow()

    override fun close() {
        closed = true
        subscribed.forEach { it.unsubscribe() }
        subscribed.clear()
        device?.close()
        device = null
        write = null
        incoming.close(IOException("closed"))
    }

    private companion object {
        /** The raw characteristic JBL's security modes 1 and 3 use for their extra handshake steps. */
        const val RAW = "65786365-6c70-6f69-6e74-2e636f6d0003"
        const val BOND_MS = 60_000L
    }
}

/**
 * QCY's kind of Bluetooth LE channel: one service whose characteristics are each read, written and notified on their own, carried as
 * [GattEnvelope]s (docs/qcy/PROTOCOL.md section 2). Every characteristic of the service that notifies is subscribed, as QCY's app does.
 */
internal class WinRtEnvelopeLink(private val address: String, private val service: String, private val log: (String) -> Unit) : ControlLink {
    private var device: LeDevice? = null
    private var chars: List<Characteristic> = emptyList()
    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private val lock = Mutex()
    private var closed = false

    private fun short(uuid: String) = uuid.substring(4, 8).toInt(16)

    override suspend fun connect() {
        try {
            if (closed) throw IOException("closed")
            val opened = LeDevice.open(address) ?: throw IOException("Windows knows no Bluetooth LE device at $address")
            device = opened
            chars = opened.characteristicsOf(service)
            if (chars.isEmpty()) throw IOException("no control service")
            for (c in chars.filter { it.canNotify }) {
                val s = short(c.uuid)
                if (!c.subscribe { incoming.trySend(GattEnvelope.received(s, it)) }) log("subscribe ${s.toString(16)} refused")
            }
        } catch (e: Throwable) {
            close()
            throw e
        }
    }

    override suspend fun send(frame: ByteArray): Unit = lock.withLock {
        val out = GattEnvelope.out(frame) ?: throw IOException("not an envelope")
        val c = chars.firstOrNull { short(it.uuid) == out.short } ?: throw IOException("no characteristic ${out.short.toString(16)}")
        if (out.operation == GattEnvelope.READ) {
            val value = c.read() ?: throw IOException("gatt operation not answered")
            incoming.trySend(GattEnvelope.received(out.short, value))
            log("read ${out.short.toString(16).padStart(4, '0')} " + value.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') })
        } else {
            if (!c.write(out.value, c.canWrite)) throw IOException("gatt operation refused")
            log("send ${out.short.toString(16).padStart(4, '0')} " + out.value.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') })
        }
    }

    override fun reads(): Flow<ByteArray> = incoming.receiveAsFlow()

    override fun close() {
        closed = true
        chars.forEach { it.unsubscribe() }
        chars = emptyList()
        device?.close()
        device = null
        incoming.close(IOException("closed"))
    }
}

/**
 * A diagnostic for developers (`PairFree.exe --le-probe AABBCCDDEEFF`): opens the Bluetooth LE device at the address and lists its GATT
 * characteristics with their properties, to show what Windows' LE side can do with a device. Reads and writes nothing.
 */
suspend fun leProbe(address: String, log: (String) -> Unit = {}): String {
    val out = StringBuilder()
    WinRt.trace = log
    log("open")
    WinRt.ensure()
    log("RoInitialize: 0x${WinRt.initRoResult.toUInt().toString(16)}")
    val device = LeDevice.open(address)
    if (device == null) return "Windows knows no Bluetooth LE device at $address"
    log("opened")
    out.appendLine("opened, mtu ${device.mtu}")
    log("listing")
    for (c in device.characteristicsOf(null)) {
        val p = c.properties
        val flags = listOfNotNull(if (p and 2 != 0) "read" else null, if (p and 4 != 0) "write-no-response" else null, if (p and 8 != 0) "write" else null, if (p and 0x10 != 0) "notify" else null, if (p and 0x20 != 0) "indicate" else null)
        out.appendLine("  ${c.uuid}  ${flags.joinToString(", ")}")
    }
    log("listed")
    for (c in device.characteristicsOf("00001800-0000-1000-8000-00805f9b34fb")) {
        if (c.uuid.startsWith("00002a00")) out.appendLine("device name read: " + (c.read()?.decodeToString() ?: "failed"))
    }
    // Reading a battery level and subscribing to it exercise the read, the subscription and the event callback.
    val chars = device.characteristicsOf("0000180f-0000-1000-8000-00805f9b34fb")
    for (c in chars) {
        val value = c.read()
        out.appendLine("battery service characteristic ${c.uuid}: read ${value?.joinToString(" ") { (it.toInt() and 0xFF).toString() }}")
        var events = 0
        val ok = c.subscribe { events++ }
        out.appendLine("  subscribed: $ok")
        kotlinx.coroutines.delay(3000)
        out.appendLine("  events in 3 s: $events")
        c.unsubscribe()
    }
    // The Qualcomm GAIA service on SOUNDPEATS earbuds: a read-only question (which features the model has) written to its command
    // characteristic should be answered on its notify one; this exercises the write and the event callback together.
    val gaia = device.characteristicsOf("00001100-d102-11e1-9b23-00025b00a5a5")
    val command = gaia.firstOrNull { it.uuid.startsWith("00001101") }
    val answer = gaia.firstOrNull { it.uuid.startsWith("00001102") }
    if (command != null && answer != null) {
        val got = mutableListOf<String>()
        val ok = answer.subscribe { got += it.joinToString("") { b -> (b.toInt() and 0xFF).toString(16).padStart(2, '0') } }
        out.appendLine("gaia: subscribed $ok")
        val sent = command.write(io.github.omegadestroyer.pairfree.protocol.Requests.gaiaFeatures(), true)
        out.appendLine("gaia: wrote $sent")
        kotlinx.coroutines.delay(3000)
        out.appendLine("gaia: answers ${got.size}: ${got.joinToString(" | ")}")
        answer.unsubscribe()
    }
    device.close()
    // A scan: the advertisement callback fires for every device nearby, which exercises the event object and the parsing of adverts.
    val seen = mutableMapOf<String, Advert>()
    val started = LeScan.scan(6000, log) { a -> seen[a.address] = a; false }
    out.appendLine("scan started: $started, ${seen.size} devices")
    for (a in seen.values.take(8)) {
        out.appendLine("  ${a.address} name='${a.name}' manufacturers=${a.manufacturer.keys.joinToString(",")} services=${a.serviceUuids.size} serviceData=${a.serviceData.keys.joinToString(",")}")
    }
    log("closed")
    return out.toString()
}
