package io.github.omegadestroyer.pairfree.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.FrameParser
import io.github.omegadestroyer.pairfree.protocol.Models
import io.github.omegadestroyer.pairfree.protocol.Replies
import io.github.omegadestroyer.pairfree.protocol.Requests
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.Wire
import io.github.omegadestroyer.pairfree.protocol.apple.AapPacket
import io.github.omegadestroyer.pairfree.protocol.apple.AppleOpcode
import io.github.omegadestroyer.pairfree.protocol.apple.AppleSession
import io.github.omegadestroyer.pairfree.protocol.jbl.JblAdvert
import io.github.omegadestroyer.pairfree.protocol.jbl.JblModels
import io.github.omegadestroyer.pairfree.protocol.jbl.JblFrame
import io.github.omegadestroyer.pairfree.protocol.jbl.JblP4
import io.github.omegadestroyer.pairfree.protocol.jbl.JblP4Packet
import io.github.omegadestroyer.pairfree.protocol.jbl.JblReader
import io.github.omegadestroyer.pairfree.protocol.jbl.JblSession
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingFrame
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingModels
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingParser
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingSession
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyModels
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyProtocol
import io.github.omegadestroyer.pairfree.protocol.qcy.QcySession
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyWire
import io.github.omegadestroyer.pairfree.protocol.summary
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungModels
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungParser
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungSession
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungStatus
import io.github.omegadestroyer.pairfree.eq.SonyEqTarget
import io.github.omegadestroyer.pairfree.protocol.bose.BosePacket
import io.github.omegadestroyer.pairfree.protocol.bose.BoseParser
import io.github.omegadestroyer.pairfree.protocol.bose.BoseSession
import io.github.omegadestroyer.pairfree.protocol.sony.SonyEq
import io.github.omegadestroyer.pairfree.protocol.sony.SonyLink
import io.github.omegadestroyer.pairfree.protocol.sony.SonyModels
import io.github.omegadestroyer.pairfree.protocol.sony.SonyOut
import io.github.omegadestroyer.pairfree.protocol.sony.SonyParser
import io.github.omegadestroyer.pairfree.protocol.sony.SonySession
import io.github.omegadestroyer.pairfree.protocol.sony.SonyWire
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreButtons
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreModels
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreParser
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreSession
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreState
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreTouch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** A brand PairFree can control. Each has its own protocol (docs/<brand>/PROTOCOL.md). */
enum class Brand { SOUNDPEATS, SOUNDCORE, SAMSUNG, SONY, BOSE, JBL, APPLE, NOTHING, QCY }

/**
 * A pair of earbuds the phone is paired with. [service] is the RFCOMM service UUID to open:
 * null for the standard serial port, which SoundPEATS earbuds use. [model] is the Soundcore
 * model number, from the earbuds' service or name ([SoundcoreModels.identify]), the Galaxy
 * Buds model number from the name ([SamsungModels.fromName]), or the Sony model from the name
 * ([SonyModels.identify]), null for a Sony model not in the table, the JBL model's name
 * ([JblModels.fromName]), or the Nothing model's product id from the name ([NothingModels.fromName]).
 */
data class Paired(
    val address: String,
    val name: String,
    val brand: Brand = Brand.SOUNDPEATS,
    val service: String? = null,
    val model: String? = null,
)

sealed interface LinkStatus {
    data object Idle : LinkStatus
    data class Connecting(val device: Paired) : LinkStatus
    data class Connected(val device: Paired) : LinkStatus

    /** The channel closed after being open; trying again, with the last state kept. */
    data class Reconnecting(val device: Paired) : LinkStatus
    data class Failed(val device: Paired, val reason: Reason) : LinkStatus

    /**
     * [NO_SCAN_PERMISSION]: JBL earbuds that did not answer on their classic address, or QCY earbuds, which
     * PairFree may not scan for. [NEEDS_NEWER_ANDROID]: AirPods whose channel this phone's Bluetooth stack
     * refuses ([AppleSupport]). [NOT_FOUND]: QCY earbuds whose advert a scan did not find. [NOT_YET]: QCY
     * earbuds on JieLi's protocol, which PairFree does not speak yet.
     */
    enum class Reason { BLUETOOTH_OFF, COULD_NOT_OPEN, LOST, NO_PERMISSION, NO_SCAN_PERMISSION, NEEDS_NEWER_ANDROID, NOT_FOUND, NOT_YET }
}

/**
 * One control connection to one pair of earbuds. On connect it sends the read-only GETs
 * in [Requests.ON_CONNECT], then reads the battery every [BATTERY_POLL_MS]. Every reply,
 * asked for or not, updates [state]. [log], when set, gets a line for every frame and
 * for the channel opening and closing.
 *
 * Soundcore earbuds speak another protocol; a [SoundcoreSession] turns it into the same
 * [state] and turns [set] into Soundcore packets, so the screens work for both brands. Galaxy
 * Buds go through a [SamsungSession] the same way, and their settings beyond the shared ones
 * come out as [settings]; Sony earbuds and headphones through a [SonySession], Bose's through a
 * [BoseSession], JBL's through a [JblSession], over Bluetooth LE ([GattLink]) for most of them, and
 * AirPods and Apple-chip Beats through an [AppleSession] over L2CAP ([AppleLink]), and Nothing's and CMF's
 * through a [NothingSession]. [context] opens LE connections.
 */
class EarbudsConnection(
    private val scope: CoroutineScope,
    private val adapter: BluetoothAdapter?,
    private val context: Context? = null,
    private val log: ((String) -> Unit)? = null,
    private val assistantIsGoogle: () -> Boolean = { true },
) {
    private val _status = MutableStateFlow<LinkStatus>(LinkStatus.Idle)
    val status: StateFlow<LinkStatus> = _status.asStateFlow()

    private val _state = MutableStateFlow(EarbudState())
    val state: StateFlow<EarbudState> = _state.asStateFlow()

    // All of this is touched only from the scope's (main) thread.
    private var session: CoroutineScope? = null   // everything one connection runs; cancelled on disconnect
    private var link: ControlLink? = null
    private var attempt: Any? = null      // the current connection attempt; null after disconnect()
    private var replies: Map<Command, ByteArray> = emptyMap()
    private var soundcore: SoundcoreSession? = null   // set while connected to Soundcore earbuds

    /** The last raw reply to each GET, for the device report. Empty for Soundcore earbuds. */
    val lastReplies: Map<Command, ByteArray> get() = replies

    /** The Soundcore session, while connected to Soundcore earbuds; for the device report. */
    val soundcoreSession: SoundcoreSession? get() = soundcore

    private var samsung: SamsungSession? = null   // set while connected to Galaxy Buds

    /** The Samsung session, while connected to Galaxy Buds; for the device report. */
    val samsungSession: SamsungSession? get() = samsung

    private val _settings = MutableStateFlow<List<SettingGroup>>(emptyList())

    /** Settings beyond the shared ones, as generic rows for the screens; empty for brands without them. */
    val settings: StateFlow<List<SettingGroup>> = _settings.asStateFlow()

    private val _samsungStatus = MutableStateFlow<SamsungStatus?>(null)

    /** The Galaxy Buds' state as they last sent it, with changes since; the Sound tab reads their EQ from it. */
    val samsungStatus: StateFlow<SamsungStatus?> = _samsungStatus.asStateFlow()

    private val _samsungEqCurves = MutableStateFlow<List<List<Int>>>(emptyList())

    /** The Galaxy Buds' EQ curves by equalizer number, where they reported them; the Sound tab draws the presets from them. */
    val samsungEqCurves: StateFlow<List<List<Int>>> = _samsungEqCurves.asStateFlow()

    private var sony: SonySession? = null   // set while connected to Sony earbuds
    private var sonyQueue: Channel<SonyOut>? = null

    /** The Sony session, while connected to Sony earbuds or headphones; for the device report. */
    val sonySession: SonySession? get() = sony

    private var bose: BoseSession? = null   // set while connected to Bose headphones or earbuds
    private var boseQueue: Channel<BosePacket>? = null

    /** The Bose session, while connected to Bose headphones or earbuds; for the device report. */
    val boseSession: BoseSession? get() = bose

    private var jbl: JblSession? = null   // set while connected to JBL headphones or earbuds
    private var jblQueue: Channel<JblFrame>? = null

    /** The JBL session, while connected to JBL headphones or earbuds; for the device report. */
    val jblSession: JblSession? get() = jbl

    private var apple: AppleSession? = null   // set while connected to AirPods or Beats

    /** The Apple session, while connected to AirPods or Beats; for the device report. */
    val appleSession: AppleSession? get() = apple

    private var nothing: NothingSession? = null   // set while connected to Nothing or CMF earbuds or headphones
    private var nothingQueue: Channel<NothingFrame>? = null

    /** The Nothing session, while connected to Nothing or CMF earbuds or headphones; for the device report. */
    val nothingSession: NothingSession? get() = nothing

    private var qcy: QcySession? = null   // set while connected to QCY earbuds or headphones
    private var qcyQueue: Channel<ByteArray>? = null
    private var qcyFound: QcyFinder.Found? = null   // what the scan found before the channel opened

    /** The QCY session, while connected to QCY earbuds or headphones; for the device report. */
    val qcySession: QcySession? get() = qcy

    private val _sonyEq = MutableStateFlow<SonyEq?>(null)

    /** The Sony equalizer as the earbuds last reported it; the Sound tab follows it. */
    val sonyEq: StateFlow<SonyEq?> = _sonyEq.asStateFlow()

    private val _soundcoreState = MutableStateFlow<SoundcoreState?>(null)

    /** Each full state Soundcore earbuds send, as they send it; the Sound tab reads their EQ from it. */
    val soundcoreState: StateFlow<SoundcoreState?> = _soundcoreState.asStateFlow()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: Paired) {
        disconnect()
        _state.value = EarbudState()
        replies = emptyMap()
        soundcore = null
        _soundcoreState.value = null
        samsung = null
        sony = null
        bose = null
        jbl = null
        apple = null
        nothing = null
        qcy = null
        qcyFound = null
        _sonyEq.value = null
        _settings.value = emptyList()
        _samsungStatus.value = null
        val remote = adapter?.takeIf { it.isEnabled }?.getRemoteDevice(device.address)
        if (remote == null) {
            _status.value = LinkStatus.Failed(device, LinkStatus.Reason.BLUETOOTH_OFF)
            return
        }
        val token = Any()
        attempt = token
        _status.value = LinkStatus.Connecting(device)
        val s = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job]))
        session = s
        s.launch {
            // Once the channel has been open, a drop is followed by a few retries: the H3
            // restarts its Bluetooth link after an LDAC or two-devices change
            // (docs/soundpeats/PROTOCOL.md section 11). The last state stays on screen meanwhile.
            var everOpen = false
            var retries = 0
            while (true) {
                val opened = try {
                    open(remote, device)
                } catch (_: ScanNotAllowed) {
                    fail(token, device, LinkStatus.Reason.NO_SCAN_PERMISSION)
                    return@launch
                } catch (_: NotFound) {
                    fail(token, device, LinkStatus.Reason.NOT_FOUND)
                    return@launch
                } catch (_: NotYet) {
                    fail(token, device, LinkStatus.Reason.NOT_YET)
                    return@launch
                } catch (e: IOException) {
                    log?.invoke("could not open: ${e.message}")
                    if (e is NeedsNewerAndroid && !everOpen) {
                        fail(token, device, LinkStatus.Reason.NEEDS_NEWER_ANDROID)
                        return@launch
                    }
                    if (everOpen && retries < RECONNECT_DELAYS_MS.size) {
                        delay(RECONNECT_DELAYS_MS[retries++])
                        continue
                    }
                    fail(token, device, if (everOpen) LinkStatus.Reason.LOST else LinkStatus.Reason.COULD_NOT_OPEN)
                    return@launch
                } catch (e: SecurityException) {
                    fail(token, device, LinkStatus.Reason.NO_PERMISSION)
                    return@launch
                }
                everOpen = true
                _status.value = LinkStatus.Connected(device)
                log?.invoke("open")
                val openedAt = SystemClock.elapsedRealtime()
                when (device.brand) {
                    Brand.SOUNDCORE -> serveSoundcore(opened, device)
                    Brand.SAMSUNG -> serveSamsung(opened, device)
                    Brand.SONY -> serveSony(opened, device)
                    Brand.BOSE -> serveBose(opened)
                    Brand.JBL -> serveJbl(opened, device)
                    Brand.APPLE -> serveApple(opened)
                    Brand.NOTHING -> serveNothing(opened, device)
                    Brand.QCY -> serveQcy(opened)
                    Brand.SOUNDPEATS -> serve(opened, device)
                }
                log?.invoke("closed")
                opened.close()
                // A channel that keeps closing straight away does not earn fresh retries.
                if (SystemClock.elapsedRealtime() - openedAt >= STABLE_MS) retries = 0
                if (attempt !== token) return@launch
                if (retries >= RECONNECT_DELAYS_MS.size) {
                    fail(token, device, LinkStatus.Reason.LOST)
                    return@launch
                }
                _status.value = LinkStatus.Reconnecting(device)
                delay(RECONNECT_DELAYS_MS[retries++])
            }
        }
    }

    /**
     * Opens the control channel: the classic serial channel, or for JBL's LE models GATT, first on the
     * classic address, where the Tune Flex 2's LE side answers (JblTray), else on the LE device a scan
     * finds (docs/jbl/PROTOCOL.md section 3). Each link is [link] while it opens, so [disconnect] can abort it.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun open(remote: BluetoothDevice, device: Paired): ControlLink {
        if (device.brand == Brand.APPLE) return openApple(remote)
        if (device.brand == Brand.QCY) return openQcy(device)
        val model = if (device.brand == Brand.JBL) JblModels.fromName(device.name) ?: JblModels.ALL.firstOrNull { it.name == device.model } else null
        val ctx = context
        if (model?.gatt != true || ctx == null) return SppLink(remote, log, device.service ?: Wire.SPP_UUID).also { link = it }.apply { connect() }
        // JBL's protocol 4 security modes add connection steps (docs/jbl/PROTOCOL.md section 7); modes 1 and 3 use
        // the raw characteristic, and mode 3 writes the phone's name checksum to it first.
        val rawNotify = model.security == 1 || model.security == 3
        val preamble = if (model.security == 3) jblHostNameCrc() else null
        fun gatt(d: BluetoothDevice) = GattLink(ctx, d, log, rawNotify = rawNotify, rawPreamble = preamble).also { link = it }
        try {
            return gatt(remote).apply { connect() }
        } catch (e: IOException) {
            log?.invoke("LE on the classic address: ${e.message}")
        }
        currentCoroutineContext().ensureActive()   // not after a disconnect closed the first try
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) throw ScanNotAllowed()
        val found = adapter?.let { JblFinder(it, log).find(model, device.address) } ?: throw IOException("not found over LE")
        return gatt(found).apply { connect() }
    }

    /** The checksum of this phone's Bluetooth name, as JBL's security mode 3 writes it (JblAdvert's CRC-16/ARC, low byte first). */
    @SuppressLint("MissingPermission") // only called with the permission
    private fun jblHostNameCrc(): ByteArray? {
        val name = try {
            adapter?.name
        } catch (_: SecurityException) {
            null
        } ?: return null
        val crc = JblAdvert.crc16(name.encodeToByteArray())
        return JblP4Packet.set(JblP4.FEATURE_HOST_NAME_CRC to byteArrayOf(crc.toByte(), (crc shr 8).toByte())).bytes()
    }

    /** JBL earbuds that need an LE scan, which the user has not allowed. */
    private class ScanNotAllowed : IOException("scan not allowed")

    /**
     * QCY earbuds over Bluetooth LE (docs/qcy/PROTOCOL.md section 2): a scan finds their advert, which names
     * the model and the LE side to connect to. A scan that already found them is used again on a reconnect.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun openQcy(device: Paired): ControlLink {
        val ctx = context ?: throw IOException("no context for Bluetooth LE")
        val found = qcyFound ?: run {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) throw ScanNotAllowed()
            adapter?.let { QcyFinder(it, log).find(device.address) } ?: throw NotFound()
        }
        qcyFound = found
        val model = QcyModels.of(found.advert.vendorId)
        if (model != null && model.protocol != QcyProtocol.QCY) throw NotYet()
        return EnvelopeGattLink(ctx, found.device, java.util.UUID.fromString(QcyWire.SERVICE), log).also { link = it }.apply { connect() }
    }

    /** QCY earbuds whose advert a scan did not find. */
    private class NotFound : IOException("not found")

    /** QCY earbuds on JieLi's protocol, which PairFree does not speak yet. */
    private class NotYet : IOException("protocol not supported yet")

    /**
     * AirPods' channel over L2CAP, on phones whose Bluetooth stack opens it ([AppleSupport]). On the others
     * it is not tried: what a refused channel does to the AirPods' sound there is not known, and the user
     * is told Android 17 is needed instead, as the owner chose (2026-10-15) and as LibrePods does by default.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun openApple(remote: BluetoothDevice): ControlLink {
        if (!AppleSupport.opensChannel()) throw NeedsNewerAndroid()
        val a = adapter ?: throw IOException("no Bluetooth adapter")
        return AppleLink(a, remote, log).also { link = it }.apply { connect() }
    }

    /** AirPods on a phone whose Bluetooth stack LibrePods' list does not include. */
    private class NeedsNewerAndroid : IOException("needs Android 17")

    /** Asks for everything, reads the battery now and then, and applies every reply until the channel closes. */
    private suspend fun serve(opened: ControlLink, device: Paired) = coroutineScope {
        val requests = launch {
            try {
                for (command in Requests.ON_CONNECT) opened.send(Requests.get(command))
                // Qualcomm models also have GAIA v3 features, such as the fit test. By now the
                // firmware reply has named the model; the read goes to Qualcomm models only.
                if (Models.find(_state.value.firmware?.code, device.name)?.isQualcomm == true) opened.send(Requests.gaiaFeatures())
                while (true) {
                    delay(BATTERY_POLL_MS)
                    for (command in Requests.BATTERY) opened.send(Requests.get(command))
                }
            } catch (_: IOException) {
                // the channel closed; the reader below notices too
            }
        }
        try {
            val parser = FrameParser()
            opened.reads().collect { bytes ->
                for (frame in parser.feed(bytes)) guardPacket {
                    log?.invoke("recv ${frame.summary()}")
                    Replies.commandFor(frame)?.let { replies = replies + (it to frame.payload) }
                    _state.update { it.update(frame) }
                }
            }
        } catch (_: IOException) {
        }
        requests.cancel()
    }

    /**
     * Soundcore earbuds: asks for the state on connecting and every [BATTERY_POLL_MS] after,
     * and applies every packet until the channel closes. The earbuds also send changes unasked.
     */
    private suspend fun serveSoundcore(opened: ControlLink, device: Paired) = coroutineScope {
        val session = SoundcoreSession(device.model.orEmpty())
        soundcore = session
        val requests = launch {
            try {
                while (true) {
                    opened.send(session.requestState())
                    delay(BATTERY_POLL_MS)
                }
            } catch (_: IOException) {
                // the channel closed; the reader below notices too
            }
        }
        try {
            val parser = SoundcoreParser(checksum = session.layout?.checksum ?: true)
            opened.reads().collect { bytes ->
                for (packet in parser.feed(bytes)) guardPacket {
                    log?.invoke("recv $packet")
                    if (session.receive(packet)) _state.value = session.state
                    if (session.lastState !== _soundcoreState.value) _soundcoreState.value = session.lastState
                }
            }
        } catch (_: IOException) {
        }
        requests.cancel()
    }

    /**
     * Galaxy Buds: they send their whole state on connecting, then each change as it happens, so
     * nothing is asked for. Every packet updates [state] and [settings]; what the session wants to
     * say back (the manager info, the version request) goes out at once.
     */
    private suspend fun serveSamsung(opened: ControlLink, device: Paired) = coroutineScope {
        val model = SamsungModels.of(device.model) ?: return@coroutineScope
        val phoneIsSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        val session = SamsungSession(model, phoneIsSamsung = phoneIsSamsung, androidSdk = Build.VERSION.SDK_INT, assistantIsGoogle = assistantIsGoogle())
        samsung = session
        _samsungEqCurves.value = emptyList()
        try {
            val parser = SamsungParser(model.legacy)
            opened.reads().collect { bytes ->
                for (packet in parser.feed(bytes)) guardPacket {
                    log?.invoke("recv $packet")
                    if (session.receive(packet)) publish(session)
                    _samsungEqCurves.value = session.eqCurves
                    for (frame in session.frames(session.takeOutbox())) opened.send(frame)
                }
            }
        } catch (_: IOException) {
        }
    }

    /**
     * Sony earbuds and headphones: each message waits for its acknowledgement before the next goes,
     * as Sony's app sends them (docs/sony/PROTOCOL.md section 2), and every message from them is
     * acknowledged at once. The session asks for the protocol, then for what the earbuds list; the
     * battery is read again every [BATTERY_POLL_MS], and changes arrive as notifications. A message
     * never acknowledged leaves the two sides' sequence numbers in doubt, so the channel closes and
     * opens again, as Sony's app does.
     */
    private suspend fun serveSony(opened: ControlLink, device: Paired) = coroutineScope {
        val session = SonySession(SonyModels.tableSetOf(device.service), SonyModels.of(device.model))
        sony = session
        val link = SonyLink()
        val queue = Channel<SonyOut>(Channel.UNLIMITED)
        val acked = Channel<Unit>(Channel.CONFLATED)
        sonyQueue = queue
        val sender = launch {
            try {
                for (out in queue) {
                    if (!session.allowed(out)) continue
                    val frame = link.frame(out)
                    acked.tryReceive()
                    var acknowledged = false
                    for (attempt in 0..SONY_RESENDS) {
                        opened.send(frame)
                        if (withTimeoutOrNull(SONY_ACK_MS) { acked.receive() } != null) {
                            acknowledged = true
                            break
                        }
                    }
                    if (!acknowledged) {
                        log?.invoke("no acknowledgement for $out")
                        opened.close()
                        break
                    }
                }
            } catch (_: IOException) {
                // the channel closed; the reader below notices too
            }
        }
        session.start().forEach(queue::trySend)
        val poll = launch {
            while (true) {
                delay(BATTERY_POLL_MS)
                session.batteryRequests().forEach(queue::trySend)
            }
        }
        try {
            val parser = SonyParser()
            opened.reads().collect { bytes ->
                for (message in parser.feed(bytes)) guardPacket {
                    log?.invoke("recv $message")
                    if (message.dataType == SonyWire.ACK) {
                        if (link.acked(message)) acked.trySend(Unit)
                        continue
                    }
                    val (ack, fresh) = link.received(message)
                    opened.send(ack)
                    if (fresh && session.receive(message)) publish(session)
                    session.takeOutbox().forEach(queue::trySend)
                }
            }
        } catch (_: IOException) {
        }
        poll.cancel()
        sender.cancel()
        queue.close()
        if (sonyQueue === queue) sonyQueue = null
    }

    /**
     * Bose headphones and earbuds: one request at a time, each waiting for its answer (or
     * [BOSE_REPLY_MS]), as bosectl and Bose's app send them (docs/bose/PROTOCOL.md section 2). The
     * device also sends changes made with its buttons as they happen.
     */
    private suspend fun serveBose(opened: ControlLink) = coroutineScope {
        val session = BoseSession()
        bose = session
        val queue = Channel<BosePacket>(Channel.UNLIMITED)
        val answered = Channel<BosePacket>(Channel.UNLIMITED)
        boseQueue = queue
        val sender = launch {
            try {
                for (packet in queue) {
                    if (!session.allowed(packet)) continue
                    while (answered.tryReceive().isSuccess) Unit
                    opened.send(packet.bytes())
                    withTimeoutOrNull(BOSE_REPLY_MS) {
                        while (!session.answers(packet, answered.receive())) Unit
                    }
                }
            } catch (_: IOException) {
                // the channel closed; the reader below notices too
            }
        }
        session.start().forEach(queue::trySend)
        val poll = launch {
            while (true) {
                delay(BATTERY_POLL_MS)
                session.batteryRequests().forEach(queue::trySend)
            }
        }
        try {
            val parser = BoseParser()
            opened.reads().collect { bytes ->
                for (packet in parser.feed(bytes)) guardPacket {
                    log?.invoke("recv $packet")
                    if (session.receive(packet)) publish(session)
                    answered.trySend(packet)
                    session.takeOutbox().forEach(queue::trySend)
                }
            }
        } catch (_: IOException) {
        }
        poll.cancel()
        sender.cancel()
        queue.close()
        if (boseQueue === queue) boseQueue = null
    }

    /**
     * JBL headphones and earbuds: one command at a time, each waiting for anything from the earbuds or
     * [JBL_REPLY_MS], as JBL's app sends them (docs/jbl/PROTOCOL.md section 2). The earbuds also send
     * changes made with their buttons as they happen. Over GATT each notification is one frame.
     */
    private suspend fun serveJbl(opened: ControlLink, device: Paired) = coroutineScope {
        val model = JblModels.fromName(device.name) ?: JblModels.ALL.firstOrNull { it.name == device.model } ?: return@coroutineScope
        val session = JblSession(model)
        jbl = session
        val queue = Channel<JblFrame>(Channel.UNLIMITED)
        val heard = Channel<Unit>(Channel.CONFLATED)
        jblQueue = queue
        val sender = launch {
            try {
                for (packet in queue) {
                    if (!session.allowed(packet)) continue
                    heard.tryReceive()
                    opened.send(packet.bytes())
                    withTimeoutOrNull(JBL_REPLY_MS) { heard.receive() }
                }
            } catch (_: IOException) {
                // the channel closed; the reader below notices too
            }
        }
        session.start().forEach(queue::trySend)
        val poll = launch {
            while (true) {
                delay(BATTERY_POLL_MS)
                session.batteryRequests().forEach(queue::trySend)
            }
        }
        try {
            val parser = JblReader()
            val notifications = opened is GattLink
            opened.reads().collect { bytes ->
                for (packet in parser.feed(bytes, newMessage = notifications)) guardPacket {
                    log?.invoke("recv $packet")
                    if (session.receive(packet)) publish(session)
                    heard.trySend(Unit)
                    session.takeOutbox().forEach(queue::trySend)
                }
            }
        } catch (_: IOException) {
        }
        poll.cancel()
        sender.cancel()
        queue.close()
        if (jblQueue === queue) jblQueue = null
    }

    /**
     * AirPods and Apple-chip Beats: the handshake, the feature flags and the notification request, 200 ms
     * apart and once more 5 s later, as LibrePods sends them (docs/apple/PROTOCOL.md section 3). The AirPods
     * then send their battery and settings, and every change, as it happens; nothing is polled.
     */
    private suspend fun serveApple(opened: ControlLink) = coroutineScope {
        val session = AppleSession()
        apple = session
        val opening = launch {
            try {
                repeat(APPLE_OPENING_ROUNDS) { round ->
                    if (round > 0) delay(APPLE_AGAIN_MS)
                    for (frame in session.start()) {
                        if (session.allowed(frame)) opened.send(frame)
                        delay(APPLE_GAP_MS)
                    }
                }
            } catch (_: IOException) {
                // the channel closed; the reader below notices too
            }
        }
        try {
            opened.reads().collect { bytes ->
                guardPacket {
                    // The information packet carries serial numbers: keep them out of the log.
                    val packet = AapPacket.parse(bytes)
                    log?.invoke(if (packet?.opcode == AppleOpcode.INFORMATION) "recv information (${bytes.size} bytes)" else "recv ${bytes.joinToString("") { "%02x".format(it) }}")
                    if (session.receive(bytes)) publish(session)
                }
            }
        } catch (_: IOException) {
        }
        opening.cancel()
    }

    /**
     * Nothing and CMF earbuds and headphones: one command at a time, each waiting for its answer (or
     * [NOTHING_REPLY_MS]), as Nothing X sends them (docs/nothing/PROTOCOL.md section 3). The earbuds
     * also send changes made on them as events. A read they do not have gets an error, an empty
     * answer, or none at all.
     */
    private suspend fun serveNothing(opened: ControlLink, device: Paired) = coroutineScope {
        val session = NothingSession(NothingModels.of(device.model) ?: NothingModels.fromName(device.name))
        nothing = session
        val queue = Channel<NothingFrame>(Channel.UNLIMITED)
        val answered = Channel<NothingFrame>(Channel.UNLIMITED)
        nothingQueue = queue
        val sender = launch {
            try {
                for (frame in queue) {
                    if (!session.allowed(frame)) continue
                    while (answered.tryReceive().isSuccess) Unit
                    opened.send(frame.bytes())
                    withTimeoutOrNull(NOTHING_REPLY_MS) {
                        while (!session.answers(frame, answered.receive())) Unit
                    }
                    delay(NOTHING_GAP_MS)
                }
            } catch (_: IOException) {
                // the channel closed; the reader below notices too
            }
        }
        session.start().forEach(queue::trySend)
        val poll = launch {
            while (true) {
                delay(BATTERY_POLL_MS)
                session.batteryRequests().forEach(queue::trySend)
            }
        }
        try {
            val parser = NothingParser()
            opened.reads().collect { bytes ->
                for (frame in parser.feed(bytes)) guardPacket {
                    log?.invoke("recv $frame")
                    if (session.receive(frame)) publish(session)
                    answered.trySend(frame)
                    session.takeOutbox().forEach(queue::trySend)
                }
            }
        } catch (_: IOException) {
        }
        poll.cancel()
        sender.cancel()
        queue.close()
        if (nothingQueue === queue) nothingQueue = null
    }

    /**
     * QCY earbuds and headphones on QCY's own protocol: one GATT operation at a time, as QCY's app sends them
     * (docs/qcy/PROTOCOL.md section 3). The earbuds report each setting asked for, and every change made on
     * them, as notifications on the settings characteristic.
     */
    private suspend fun serveQcy(opened: ControlLink) = coroutineScope {
        val found = qcyFound
        val session = QcySession(found?.let { QcyModels.of(it.advert.vendorId) }, found?.advert)
        qcy = session
        publish(session)
        val queue = Channel<ByteArray>(Channel.UNLIMITED)
        qcyQueue = queue
        val sender = launch {
            try {
                for (envelope in queue) {
                    if (!session.allowed(envelope)) continue
                    opened.send(envelope)
                    delay(QCY_GAP_MS)
                }
            } catch (_: IOException) {
                // the channel closed; the reader below notices too
            }
        }
        session.start().forEach(queue::trySend)
        val poll = launch {
            while (true) {
                delay(BATTERY_POLL_MS)
                session.batteryRequests().forEach(queue::trySend)
            }
        }
        try {
            opened.reads().collect { bytes ->
                guardPacket {
                    log?.invoke("recv ${bytes.joinToString("") { "%02x".format(it) }}")
                    if (session.receive(bytes)) publish(session)
                }
            }
        } catch (_: IOException) {
        }
        poll.cancel()
        sender.cancel()
        queue.close()
        if (qcyQueue === queue) qcyQueue = null
    }

    private fun publish(session: QcySession) {
        _state.value = session.state
        _settings.value = session.groups
    }

    /** Queues QCY messages on the open channel, then [READ_BACK_MS] later the requests that confirm them. False when there is none. */
    private fun sendQcy(session: QcySession, messages: List<ByteArray>): Boolean {
        val reads = session.takeReadBacks()
        if (_status.value !is LinkStatus.Connected) return false
        val queue = qcyQueue ?: return false
        val s = this.session ?: return false
        messages.forEach(queue::trySend)
        s.launch {
            delay(READ_BACK_MS)
            reads.forEach(queue::trySend)
        }
        return true
    }

    private fun publish(session: NothingSession) {
        _state.value = session.state
        _settings.value = session.groups
    }

    /** Queues Nothing frames on the open channel, then [READ_BACK_MS] later the reads that confirm them. False when there is none. */
    private fun sendNothing(session: NothingSession, frames: List<NothingFrame>): Boolean {
        val reads = session.takeReadBacks()
        if (_status.value !is LinkStatus.Connected) return false
        val queue = nothingQueue ?: return false
        val s = this.session ?: return false
        frames.forEach(queue::trySend)
        s.launch {
            delay(READ_BACK_MS)
            reads.forEach(queue::trySend)
        }
        return true
    }

    private fun publish(session: AppleSession) {
        _state.value = session.state
        _settings.value = session.groups
    }

    /** Sends AirPods packets on the open channel. False when there is none. */
    private fun sendApple(session: AppleSession, packets: List<AapPacket>): Boolean {
        if (_status.value !is LinkStatus.Connected) return false
        val s = this.session ?: return false
        val l = link ?: return false
        s.launch {
            try {
                for (p in packets) if (session.allowed(p.bytes())) l.send(p.bytes())
            } catch (_: IOException) {
                // the channel closed; the reader notices and reports it
            }
        }
        return true
    }

    private fun publish(session: JblSession) {
        _state.value = session.state
        _settings.value = session.groups
    }

    /** Queues JBL commands on the open channel, then [READ_BACK_MS] later the reads that confirm them. False when there is none. */
    private fun sendJbl(session: JblSession, packets: List<JblFrame>): Boolean {
        val reads = session.takeReadBacks()
        if (_status.value !is LinkStatus.Connected) return false
        val queue = jblQueue ?: return false
        val s = this.session ?: return false
        packets.forEach(queue::trySend)
        s.launch {
            delay(READ_BACK_MS)
            reads.forEach(queue::trySend)
        }
        return true
    }

    private fun publish(session: BoseSession) {
        _state.value = session.state
        _settings.value = session.groups
    }

    private fun publish(session: SonySession) {
        _state.value = session.state
        _settings.value = session.groups
        _sonyEq.value = session.eq
    }

    /** Queues Sony messages on the open channel. False when there is none. */
    private fun sendSony(outs: List<SonyOut>): Boolean {
        if (_status.value !is LinkStatus.Connected) return false
        val queue = sonyQueue ?: return false
        outs.forEach(queue::trySend)
        return true
    }

    private fun publish(session: SamsungSession) {
        _state.value = session.state
        _settings.value = session.groups
        _samsungStatus.value = session.status
    }

    /** Changes one of the [settings]: it shows at once, and the earbuds' acknowledgement or next state confirms it. */
    fun changeSetting(change: SettingChange) {
        qcy?.let { session ->
            val messages = session.change(change) ?: return
            if (sendQcy(session, messages)) publish(session)
            return
        }
        nothing?.let { session ->
            val frames = session.change(change) ?: return
            if (sendNothing(session, frames)) publish(session)
            return
        }
        apple?.let { session ->
            val packets = session.change(change) ?: return
            if (sendApple(session, packets)) publish(session)
            return
        }
        jbl?.let { session ->
            val packets = session.change(change) ?: return
            if (sendJbl(session, packets)) publish(session)
            return
        }
        bose?.let { session ->
            val packets = session.change(change) ?: return
            val queue = boseQueue ?: return
            if (_status.value !is LinkStatus.Connected) return
            packets.forEach(queue::trySend)
            publish(session)
            return
        }
        sony?.let { session ->
            val outs = session.change(change) ?: return
            if (sendSony(outs)) publish(session)
            return
        }
        val session = samsung ?: return
        val packets = session.change(change) ?: return
        if (send(session.frames(packets), then = null)) publish(session)
    }

    /**
     * Sends a SET. The change shows at once, and [READ_BACK_MS] later the value is read
     * back, so the earbuds' answer replaces the guess (docs/DESIGN.md). Factory reset and
     * reboot are refused; see [factoryResetConfirmedByUser]. On Soundcore earbuds the
     * setting goes out as a Soundcore packet, and the whole state is read back; on Galaxy
     * Buds as a Samsung message, which the earbuds acknowledge.
     */
    fun set(command: Command, vararg payload: Int) {
        // Bose's settings are all brand rows (changeSetting); nothing of the shared commands goes to them.
        if (bose != null) return
        qcy?.let { session ->
            val messages = session.set(command, payload.singleOrNull() ?: return) ?: return
            if (sendQcy(session, messages)) publish(session)
            return
        }
        nothing?.let { session ->
            val frames = session.set(command, payload.singleOrNull() ?: return) ?: return
            if (sendNothing(session, frames)) publish(session)
            return
        }
        apple?.let { session ->
            val packets = session.set(command, payload.singleOrNull() ?: return) ?: return
            if (sendApple(session, packets)) publish(session)
            return
        }
        jbl?.let { session ->
            val packets = session.set(command, payload.singleOrNull() ?: return) ?: return
            if (sendJbl(session, packets)) publish(session)
            return
        }
        sony?.let { session ->
            val outs = session.set(command, payload.singleOrNull() ?: return) ?: return
            if (sendSony(outs)) publish(session)
            return
        }
        samsung?.let { session ->
            val packets = session.set(command, payload.singleOrNull() ?: return) ?: return
            if (send(session.frames(packets), then = null)) publish(session)
            return
        }
        soundcore?.let { session ->
            val frame = session.set(command, payload.singleOrNull() ?: return) ?: return
            if (send(frame, then = session.requestState())) _state.value = session.state
            return
        }
        val frame = Requests.set(command, *payload)
        if (send(frame, Requests.readBack(command)?.let(Requests::get))) _state.update { it.expect(command, *payload) }
    }

    private var eqWrite: Job? = null

    /**
     * Sends one EQ write: for SoundPEATS a frame per band, as [io.github.omegadestroyer.pairfree.protocol.EqLayout.frames]
     * builds them, and the earbuds do not reply; for Soundcore the model's EQ packets, then a state
     * request, so the earbuds report the EQ they took. A newer write replaces one still going out.
     */
    fun sendEq(frames: List<ByteArray>) {
        if (_status.value !is LinkStatus.Connected) return
        if (bose != null || jbl != null || apple != null || nothing != null || qcy != null) return
        // Sony's EQ target hands over message payloads; they go through the Sony link's queue.
        sony?.let { session ->
            if (sendSony(frames.mapNotNull(SonyEqTarget::decode))) _sonyEq.value = session.eq
            return
        }
        val s = session ?: return
        val l = link ?: return
        val readBack = soundcore?.requestState()
        eqWrite?.cancel()
        eqWrite = s.launch {
            try {
                for (frame in frames) l.send(frame)
                // Soundcore earbuds report their EQ: read it back, so the screen shows what they took.
                readBack?.let { l.send(it) }
            } catch (_: IOException) {
                // the channel closed; the reader notices and reports it
            }
        }
    }

    /**
     * Starts the ear tip fit test; the result replaces [EarbudState.fit] about 9 s later.
     * Unless [keepAnc], noise control goes off first, as the official app does (docs/soundpeats/PROTOCOL.md
     * section 11), and back to what it was once the result is in. The official app leaves it off.
     */
    fun startFitTest(keepAnc: Boolean) {
        samsung?.let { session ->
            // Galaxy Buds run the test themselves; the result arrives as its own message.
            val packets = session.startFitTest() ?: return
            if (send(session.frames(packets), then = null)) publish(session)
            return
        }
        if (_status.value !is LinkStatus.Connected || soundcore != null || sony != null || bose != null || jbl != null || apple != null || nothing != null || qcy != null) return
        val s = session ?: return
        val l = link ?: return
        val mode = _state.value.number(Command.ANC_MODE)
        val switchOff = !keepAnc && mode != null && mode != ANC_OFF
        _state.update { it.copy(fit = null) }
        s.launch {
            try {
                if (switchOff) {
                    // Started too soon after ANC goes off, the test gives up within 2 s and
                    // reports a poor fit. Wait for the earbuds to confirm, then a little more.
                    l.send(Requests.set(Command.ANC_MODE, ANC_OFF))
                    withTimeoutOrNull(ANC_CONFIRM_MS) { _state.first { it.number(Command.ANC_MODE) == ANC_OFF } }
                    delay(ANC_SETTLE_MS)
                }
                for (frame in Requests.fitTest()) l.send(frame)
                if (switchOff && mode != null) {
                    withTimeoutOrNull(FIT_WAIT_MS) { _state.first { it.fit != null } }
                    l.send(Requests.set(Command.ANC_MODE, mode))
                    _state.update { it.expect(Command.ANC_MODE, mode) }
                }
            } catch (_: IOException) {
                // the channel closed; the reader notices and reports it
            }
        }
    }

    /** Factory reset. Call only from a confirmation the user has just accepted on screen. */
    fun factoryResetConfirmedByUser() {
        if (soundcore != null || samsung != null || sony != null || bose != null || jbl != null || apple != null || nothing != null || qcy != null) return
        send(Requests.factoryResetConfirmedByUser(), then = null)
    }

    /**
     * Changes Soundcore touch controls ([SoundcoreTouch], from the last state the earbuds sent):
     * the change shows at once, goes out as the model's packets, and [READ_BACK_MS] later the
     * whole state is read back, so the earbuds' answer replaces the guess.
     */
    fun changeSoundcoreTouch(make: (SoundcoreTouch) -> SoundcoreButtons.Change?) {
        val session = soundcore ?: return
        val change = session.touch?.let(make) ?: return
        if (!send(session.frames(change.packets), then = session.requestState())) return
        session.expect(change)
        _soundcoreState.value = session.lastState
    }

    /** Puts Soundcore touch actions back to the model's defaults, then reads the state back. */
    fun resetSoundcoreTouch() {
        val session = soundcore ?: return
        val packet = session.touch?.reset() ?: return
        send(session.frames(listOf(packet)), then = session.requestState())
    }

    /** Queues [frame] on the open channel, then [then] [READ_BACK_MS] later. False when there is no channel. */
    private fun send(frame: ByteArray, then: ByteArray?): Boolean = send(listOf(frame), then)

    /** Queues [frames] on the open channel, then [then] [READ_BACK_MS] later. False when there is no channel. */
    private fun send(frames: List<ByteArray>, then: ByteArray?): Boolean {
        if (_status.value !is LinkStatus.Connected) return false
        val s = session ?: return false
        val l = link ?: return false
        s.launch {
            try {
                for (frame in frames) l.send(frame)
                if (then != null) {
                    delay(READ_BACK_MS)
                    l.send(then)
                }
            } catch (_: IOException) {
                // the channel closed; the reader notices and reports it
            }
        }
        return true
    }

    /** Closes the channel. Safe to call at any time, including while connecting. */
    fun disconnect() {
        attempt = null
        link?.close()             // unblocks a pending connect or read
        link = null
        soundcore = null
        samsung = null
        sony = null
        sonyQueue = null
        bose = null
        boseQueue = null
        jbl = null
        jblQueue = null
        apple = null
        nothing = null
        nothingQueue = null
        qcy = null
        qcyQueue = null
        session?.cancel()
        session = null
        _status.value = LinkStatus.Idle
    }

    /** Reports a failure of [token]'s attempt, unless [disconnect] or a newer attempt replaced it. */
    /**
     * Runs one packet's handling, keeping a single bad frame from killing the connection: a thrown
     * exception (other than cancellation) is logged and the frame dropped, so the channel stays open.
     * None of the device code has been tried on most models, so an unforeseen byte must not freeze it.
     */
    private inline fun guardPacket(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log?.invoke("dropped a frame: ${e.message}")
        }
    }

    private fun fail(token: Any, device: Paired, reason: LinkStatus.Reason) {
        if (attempt !== token) return
        attempt = null
        link?.close()
        link = null
        _status.value = LinkStatus.Failed(device, reason)
    }

    companion object {
        const val BATTERY_POLL_MS = 30_000L

        /** The H3 sends the new state about 2 s after a SET (docs/soundpeats/PROTOCOL.md section 11). */
        const val READ_BACK_MS = 3_000L

        /** Waits before each try to reopen a channel that closed. The H3 took about 2 s to take one again. */
        val RECONNECT_DELAYS_MS = longArrayOf(1_000, 2_000, 3_000, 5_000, 8_000)

        /** A channel open this long counts as working, so the retries start over when it closes. */
        const val STABLE_MS = 10_000L

        /** The H3 confirms ANC off about 1.7 s after the SET; wait at most this long. */
        const val ANC_CONFIRM_MS = 5_000L

        /** Then this long before the fit test: started 0.3 s after, it failed; the official app waits about 1 s. */
        const val ANC_SETTLE_MS = 1_500L

        /** How long turning ANC off usually takes before the test starts, for the progress bar. */
        const val ANC_OFF_TYPICAL_MS = 3_200L

        /** Longest wait for a fit result before noise control goes back on regardless. */
        const val FIT_WAIT_MS = 20_000L

        /** Sony's app waits this long for an acknowledgement before it sends a message again ... */
        const val SONY_ACK_MS = 750L

        /** ... and sends it again at most this many times. */
        const val SONY_RESENDS = 10

        /** How long a Bose request waits for its answer before the next goes; bosectl waits 2 s. */
        const val BOSE_REPLY_MS = 2_000L

        /** How long a JBL command waits for the earbuds before the next goes, as JBL's app waits (HandleSend). */
        const val JBL_REPLY_MS = 1_000L

        /** How long a Nothing command waits for its answer before the next goes; Nothing X waits 1 s for the version. */
        const val NOTHING_REPLY_MS = 1_000L

        /** Nothing X leaves 40 ms between frames (HeadsetSppConnector.SEND_DELAY). */
        const val NOTHING_GAP_MS = 40L

        /** Between QCY messages, as SOUNDPEATS' app leaves between its writes. */
        const val QCY_GAP_MS = 50L

        /** LibrePods leaves 200 ms between its opening frames ... */
        const val APPLE_GAP_MS = 200L

        /** ... and sends them again 5 s later. */
        const val APPLE_AGAIN_MS = 5_000L
        private const val APPLE_OPENING_ROUNDS = 2

        /** ANC mode "off" (data/soundpeats/commands.json). */
        private val ANC_OFF = Command.ANC_MODE.labels.entries.first { it.value == "off" }.key
    }
}
