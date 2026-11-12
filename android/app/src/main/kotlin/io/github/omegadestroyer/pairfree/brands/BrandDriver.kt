package io.github.omegadestroyer.pairfree.brands

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.EarbudsConnection
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.bluetooth.SppLink
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.eq.EqTarget
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Conversation
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.FitTest
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.ReportPrivacy
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SoundpeatsBandEq
import io.github.omegadestroyer.pairfree.protocol.Talk
import io.github.omegadestroyer.pairfree.protocol.TalkWire
import io.github.omegadestroyer.pairfree.protocol.Wire
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice
import io.github.omegadestroyer.pairfree.protocol.widget.Sub
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * What a brand's [BrandDriver] may use of the connection it serves ([EarbudsConnection]): the state and rows the
 * screens show, the open channel, and the scope everything one connection runs in.
 */
interface DriverHost {
    /** Gets a line per frame in debug builds; null in release builds. */
    val log: ((String) -> Unit)?

    /** What the shared screens show. */
    val state: MutableStateFlow<EarbudState>

    /** The brand's settings beyond the shared ones, as rows. */
    val settings: MutableStateFlow<List<SettingGroup>>

    /** Something of the brand's own for a screen of its own (Soundcore's state, for its touch controls). */
    val extra: MutableStateFlow<Any?>

    /** True while the channel is open. */
    val connected: Boolean

    /** The channel, open or opening; null after a disconnect. */
    val link: ControlLink?

    /** Runs [block] with the connection, so it ends when the connection does. Null when there is none. */
    fun launch(block: suspend CoroutineScope.() -> Unit): Job?

    /** Tells the Sound tab's opener that what the earbuds said about their EQ changed. */
    fun eq(event: EqEvent)

    /** Whether the phone's digital assistant is Google's (Samsung's touch-and-hold option asks). */
    fun assistantIsGoogle(): Boolean
}

/** What changed about the earbuds' EQ, for [io.github.omegadestroyer.pairfree.eq.EqOpener]. */
enum class EqEvent {
    /** The earbuds answered what the EQ needs, or listed other bands: open it (again). */
    OPEN,

    /** More arrived for an EQ that opens from the model's table: open it again if it is open. */
    OPEN_IF_OPEN,

    /** The earbuds reported the EQ they hold: the open screen follows it. */
    REPORT,
}

/** What a device report starts with, the same for every brand. */
class ReportInfo(val device: Paired?, val version: String?, val date: String, val issues: String) {
    val name: String get() = ReportPrivacy.name(device?.name.orEmpty())
    val os: String get() = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    val osAndPhone: String get() = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}, ${Build.MANUFACTURER}, build ${Build.ID})"
}

/** Bytes as the reports print them: two hex digits each, a space between. */
internal fun ByteArray.reportHex(): String = joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

/**
 * One brand's side of a connection: it talks to the earbuds over the open channel in the brand's protocol and turns what
 * they say into the [DriverHost]'s state and rows, and the screens' requests into the brand's messages. One is made for
 * each connection ([BrandSpec.driver]); its session is made anew each time the channel opens, as the earbuds start over.
 *
 * A new brand is a [BrandSpec] and a driver in this package and a line in [Brands]; nothing else in the app names it.
 */
abstract class BrandDriver(protected val host: DriverHost) {
    /** Talks to the earbuds over [opened] until the channel closes. */
    abstract suspend fun serve(opened: ControlLink)

    /** One of the shared settings ([Command]); the change shows at once and the earbuds' answer confirms it. */
    open fun set(command: Command, payload: IntArray) = Unit

    /** One of the brand's own rows ([DriverHost.settings]). */
    open fun changeSetting(change: SettingChange) = Unit

    /** What the Sound tab's [EqTarget] built for these earbuds, to go out as the brand sends it. */
    open fun sendEq(frames: List<ByteArray>) = Unit

    /** The session, when the earbuds run an ear tip fit test themselves. */
    open val fitTest: FitTest? get() = null

    /** Starts the fit test. [keepAnc] matters to SOUNDPEATS alone. */
    open fun startFitTest(keepAnc: Boolean) {
        val fit = fitTest ?: return
        val frames = fit.startFitTest() ?: return
        sendEq(frames)
        host.state.value = fit.state
    }

    /** Factory reset, where PairFree has one for the brand. Only from a confirmation the user has just accepted. */
    open fun factoryResetConfirmedByUser() = Unit

    /**
     * The brand's equalizer for the Sound tab, or null while the model or the earbuds' answers give none. By default the
     * model's own table, as SOUNDPEATS' models have it.
     */
    open fun eqSource(device: Paired, model: Model?): EqSource? =
        model?.eq?.let { layout -> BandEqTarget(SoundpeatsBandEq(layout)).let { EqSource("plain", it.layout, it) } }

    /** The device report for an issue, or null while there is nothing of the brand's to report. */
    open fun report(info: ReportInfo): String? = null

    /** The first lines of every device report. */
    protected fun StringBuilder.reportHead(info: ReportInfo, bluetoothName: Boolean = true) {
        appendLine("PairFree device report")
        appendLine("Paste this whole report into a new issue at")
        appendLine("${info.issues} to help check")
        appendLine("this model. It holds no serial number, address or other personal data.")
        appendLine()
        if (bluetoothName) appendLine("Bluetooth name:  ${info.name}")
    }

    /** The app, phone and time lines of a device report. */
    protected fun StringBuilder.reportApp(info: ReportInfo, phone: Boolean = false) {
        appendLine("App:             PairFree ${info.version}")
        appendLine("OS:              ${if (phone) info.osAndPhone else info.os}")
        appendLine("Captured:        ${info.date}")
    }

    /** A session's last answers, one per line. */
    protected fun StringBuilder.reportReplies(replies: Map<String, ByteArray>) {
        replies.forEach { (key, payload) -> appendLine("  $key: ${payload.reportHex()}") }
    }

    /**
     * Runs one packet's handling, keeping a single bad frame from killing the connection: a thrown
     * exception (other than cancellation) is logged and the frame dropped, so the channel stays open.
     * None of the device code has been tried on most models, so an unforeseen byte must not freeze it.
     */
    protected inline fun guardPacket(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            host.log?.invoke("dropped a frame: ${e.message}")
        }
    }

    /** Shows [state] and [groups] on the screens. */
    protected fun show(state: EarbudState, groups: List<SettingGroup>) {
        host.state.value = state
        host.settings.value = groups
    }

    private var eqSeen: Any? = null

    /**
     * Notes what the earbuds last said about their EQ, and tells the opener when it changed: to open the EQ, and with
     * [follow] to bring an open screen up to what they hold now.
     */
    protected fun eqIs(value: Any?, follow: Boolean = false) {
        if (eqSeen == value) return
        eqSeen = value
        host.eq(EqEvent.OPEN)
        if (follow) host.eq(EqEvent.REPORT)
    }

    /** Sends [frames] on the open channel as they are, then [then] [EarbudsConnection.READ_BACK_MS] later. False when there is no channel. */
    protected fun raw(frames: List<ByteArray>, then: ByteArray? = null, allowed: (ByteArray) -> Boolean = { true }): Boolean {
        if (!host.connected) return false
        val l = host.link ?: return false
        host.launch {
            try {
                for (frame in frames) if (allowed(frame)) l.send(frame)
                if (then != null) {
                    delay(EarbudsConnection.READ_BACK_MS)
                    l.send(then)
                }
            } catch (_: IOException) {
                // the channel closed; the reader notices and reports it
            }
        } ?: return false
        return true
    }

    private var eqWrite: Job? = null

    /** Sends one EQ write as it is, then [readBack] at once; a newer write replaces one still going out. */
    protected fun rawEq(frames: List<ByteArray>, readBack: ByteArray?) {
        if (!host.connected) return
        val l = host.link ?: return
        eqWrite?.cancel()
        eqWrite = host.launch {
            try {
                for (frame in frames) l.send(frame)
                // Earbuds that report their EQ: read it back, so the screen shows what they took.
                readBack?.let { l.send(it) }
            } catch (_: IOException) {
                // the channel closed; the reader notices and reports it
            }
        }
    }
}

/**
 * The driver of a brand whose conversation is a [Talk]: the one loop every brand shares ([Conversation]) runs it, so the
 * driver only says which talk the channel gets, and adds what the brand has beyond it (its EQ for the Sound tab, its
 * device report). [M] is the talk's message.
 */
abstract class TalkDriver<M, T : Talk<M>>(host: DriverHost) : BrandDriver(host) {
    /** The talk of the channel now or last open. */
    var talk: T? = null
        private set
    private var conversation: Conversation<M>? = null

    /** The talk for the channel that has just opened, or null when the model is not one PairFree can talk to. */
    protected abstract fun talk(opened: ControlLink): T?

    final override suspend fun serve(opened: ControlLink) {
        val talk = talk(opened) ?: return
        this.talk = talk
        val wire = object : TalkWire {
            override suspend fun send(frame: ByteArray) = opened.send(frame)
            override fun reads(): Flow<ByteArray> = opened.reads()
            override fun close() = opened.close()
        }
        val running = Conversation(talk, wire, host.log, changed = { publish(talk) }, fed = { fed(talk) })
        conversation = running
        try {
            running.run()
        } finally {
            if (conversation === running) conversation = null
        }
    }

    /**
     * True when the open Sound tab follows every EQ report the earbuds send, as the brand's own app does: for earbuds
     * that report a change of their EQ unasked. False where they only answer PairFree's own reads, or where what the
     * brand's app does with a report has not been read: the screen then takes what they hold when the EQ opens.
     */
    protected open val followsEqReports: Boolean get() = false

    /** Shows what the talk holds now, and tells the Sound tab when what the earbuds said about their EQ changed. */
    protected open fun publish(talk: T) {
        show(talk.state, talk.groups)
        eqIs(talk.eq, followsEqReports)
    }

    /** After every read of the channel, changed or not. */
    protected open fun fed(talk: T) = Unit

    /**
     * Queues [out] on the open conversation, and [EarbudsConnection.READ_BACK_MS] later [reads], which confirm them.
     * False when there is no open channel.
     */
    protected fun send(talk: T, out: List<M>, reads: List<M> = talk.takeReadBacks()): Boolean {
        if (!host.connected) return false
        val running = conversation ?: return false
        running.send(out)
        if (reads.isNotEmpty()) {
            host.launch {
                delay(EarbudsConnection.READ_BACK_MS)
                running.send(reads)
            }
        }
        return true
    }

    override fun set(command: Command, payload: IntArray) {
        val talk = talk ?: return
        val out = talk.set(command, payload.singleOrNull() ?: return) ?: return
        if (send(talk, out)) publish(talk)
    }

    override fun changeSetting(change: SettingChange) {
        val talk = talk ?: return
        val out = talk.change(change) ?: return
        if (send(talk, out)) publish(talk)
    }

    override val fitTest: FitTest? get() = talk?.fitTest
}

/** What opening a channel may use of the connection ([BrandSpec.open]). */
interface OpenEnv {
    val context: Context?
    val adapter: BluetoothAdapter?

    /** The debug log; null in release builds. */
    val log: ((String) -> Unit)?

    /** The log for a link: its lines also count as steps of the connection report while the channel opens. */
    val linkLog: (String) -> Unit

    /** A step of the connection report. */
    fun note(step: String)

    /** Makes [link] the connection's before it opens, so a disconnect can abort it. */
    fun <L : ControlLink> hold(link: L): L
}

/** Earbuds that need an LE scan, which the user has not allowed. */
class ScanNotAllowed : IOException("scan not allowed")

/** Earbuds whose advert a scan did not find. */
class NotFound : IOException("not found")

/** AirPods on a phone whose Bluetooth stack LibrePods' list does not include. */
class NeedsNewerAndroid : IOException("needs Android 17")

/**
 * Everything the app knows about one brand apart from talking to its earbuds: how to tell its earbuds among the paired
 * devices, which model they are, how their channel opens, and what the screens and the widget say about the brand.
 */
abstract class BrandSpec(val brand: Brand) {
    /** The brand's own app, which holds the control channel while it is open. A name, so not translated; empty when there is none. */
    abstract val officialApp: String

    /** True when finding the earbuds may take an LE scan, so the permission is asked for when the user opens them. */
    open val needsScan: Boolean get() = false

    /** What the Controls tab shows: by default the driver's rows. */
    open val controls: Controls get() = Controls.ROWS

    /** True when PairFree has a factory reset for the brand, offered behind a confirmation. */
    open val hasFactoryReset: Boolean get() = false

    /** True when a model the tables do not know is shown as report-only, asking for a device report. */
    open val reportOnlyWhenUnknown: Boolean get() = false

    /**
     * The hearing test the brand's own app runs through the earbuds, where PairFree has it for [device]: the Sound tab then
     * offers that one. Null for the others, which get the phone-run test where their EQ can take its result.
     */
    open fun ownHearingTest(device: Paired): OwnHearingTest? = null

    /**
     * The model of earbuds of this brand found in a recording of the official app, by the code the paired device has, or
     * null when the tables do not know it; and what the report calls such earbuds then. Only brands whose traffic the
     * recorder recognizes need these.
     */
    open fun recordedModel(code: String?): Model? = null
    open val recordedUnmatched: String get() = "${brand.name.lowercase().replaceFirstChar { it.uppercase() }} earbuds (model not matched)"

    /** True when the Sound tab offers a recording from the official app for earbuds with no EQ yet. */
    open val offersRecording: Boolean get() = true

    /** What the Sound tab says while there is no equalizer to show. */
    @StringRes
    open fun eqNote(device: Paired): Int = R.string.eq_unavailable

    /** The paired device as this brand's earbuds, or null when PairFree cannot control it. */
    abstract fun paired(address: String, name: String, uuids: List<String>): Paired?

    /** The model's table entry, as far as the name or the earbuds' answers so far say. */
    abstract fun model(device: Paired, state: EarbudState): Model?

    /** The rows the screens show: by default the ones the driver reported. */
    open fun groups(state: EarbudState, model: Model?, reported: List<SettingGroup>): List<SettingGroup> = reported

    abstract fun driver(device: Paired, host: DriverHost): BrandDriver

    /** Opens the control channel: by default the serial channel on the device's service, else the standard serial port. */
    @SuppressLint("MissingPermission") // only called while connecting, which needs the permission
    open suspend fun open(env: OpenEnv, remote: BluetoothDevice, device: Paired, driver: BrandDriver): ControlLink {
        val uuid = device.service ?: Wire.SPP_UUID
        env.note("serial channel $uuid")
        return env.hold(SppLink(remote, env.linkLog, uuid)).apply { connect() }
    }

    /** The widget's noise choices as the brand's tables give them, or null where only the earbuds' replies say. Empty: the model has none. */
    open fun noiseOffline(device: Paired): List<NoiseChoice>? = emptyList()

    /** True when earbuds of the brand may have noise control that [noiseOffline] cannot tell. */
    open val mayHaveNoise: Boolean get() = false

    /** The brand's EQ layout and target from its tables alone, for the widget, or null where it needs what the earbuds report. */
    open fun eqOffline(device: Paired): Pair<EqLayout, EqTarget>? = null

    /** True when [device] may have an EQ that [eqOffline] cannot tell. */
    open fun mayHaveEq(device: Paired): Boolean = false

    protected companion object {
        val OFF = NoiseChoice("off")

        /** Off, cancelling and transparency, each with the sub-modes given for it. */
        fun three(cancelling: List<Sub> = emptyList(), transparency: List<Sub> = emptyList()): List<NoiseChoice> =
            listOf(OFF, NoiseChoice("anc")) + cancelling.map { NoiseChoice("anc", it) } +
                listOf(NoiseChoice("transparency")) + transparency.map { NoiseChoice("transparency", it) }
    }
}

/** A hearing test a brand's own app runs through the earbuds, each with a screen of its own. */
sealed interface OwnHearingTest {
    /** The Galaxy Buds' (the Buds4 family); [legacy] picks the older packet framing. */
    data class Samsung(val legacy: Boolean) : OwnHearingTest
}

/** What a brand's Controls tab is made of. */
enum class Controls {
    /** The driver's rows for the tab, as every newer brand has them. */
    ROWS,

    /** SOUNDPEATS' gestures, from the shared state. */
    SOUNDPEATS,

    /** Soundcore's touch controls, read from the earbuds' state. */
    SOUNDCORE,
}

/** Every brand PairFree controls. A new brand is added here, and nowhere else outside its own file. */
object Brands {
    val ALL: List<BrandSpec> by lazy {
        listOf(
            SoundpeatsSpec, SoundcoreSpec, SamsungSpec, SonySpec, BoseSpec, JblSpec, AppleSpec, NothingSpec, PixelSpec,
            QcySpec, OneMoreSpec, EarfunSpec, CambridgeSpec, OneplusSpec,
        )
    }

    operator fun get(brand: Brand): BrandSpec = ALL.first { it.brand == brand }

}
