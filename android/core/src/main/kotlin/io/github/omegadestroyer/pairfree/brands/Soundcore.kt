package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.EarbudsConnection
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.eq.EqTarget
import io.github.omegadestroyer.pairfree.eq.SoundcoreBandEq
import io.github.omegadestroyer.pairfree.eq.SoundcoreNormalizedBandEq
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreButtons
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreCaseClock
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreCaseSpec
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreCaseTalk
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreEqSpec
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreHearingResult
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreHearingSpec
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreLayout
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreModels
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreNormalizedSpec
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcorePacket
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreSession
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreState
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreTalk
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreTouch
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice
import io.github.omegadestroyer.pairfree.protocol.Conversation
import io.github.omegadestroyer.pairfree.protocol.TalkWire
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/** Soundcore (docs/soundcore/PROTOCOL.md). */
object SoundcoreSpec : BrandSpec(Brand.SOUNDCORE) {
    override val officialApp = "Soundcore"
    override val controls: Controls get() = Controls.SOUNDCORE

    /** Soundcore earbuds PairFree knows by name and number only: ask for a report rather than show a guess. */
    override val reportOnlyWhenUnknown: Boolean get() = true

    /** The Sleep A30 (D1301): its sounds go over a second serial channel (docs/soundcore/PROTOCOL.md section 14). Untested on one. */
    override fun offersSleepSounds(device: Paired): Boolean = device.model == "D1301"

    override fun eqNote(device: Paired): EqNote = EqNote.SOUNDCORE_UNCHECKED

    override fun ownHearingTest(device: Paired): OwnHearingTest? = SoundcoreHearingSpec.of(device.model)?.let { OwnHearingTest.Soundcore(it) }

    override fun paired(address: String, name: String, uuids: List<String>): Paired? =
        SoundcoreModels.identify(name, uuids)?.let { Paired(address, name, Brand.SOUNDCORE, it.service, it.model) }

    /** By the model number the earbuds' service or name gave (data/soundcore/devices.json). */
    override fun model(device: Paired, state: EarbudState): Model? = SoundcoreModels.model(device.model ?: state.firmware?.code)

    override fun driver(device: Paired, host: DriverHost): BrandDriver = SoundcoreDriver(host, device)

    /** The earbuds' channel; the driver also gets the means to reach a case that is a device of its own, once the earbuds name it. */
    override suspend fun open(env: OpenEnv, device: Paired, driver: BrandDriver): ControlLink {
        val spec = SoundcoreCaseSpec.of(device.model)
        if (spec != null && driver is SoundcoreDriver) {
            driver.openCase = { address ->
                if (!spec.le) {
                    env.transport.serial(address, spec.uuid, env.linkLog)
                } else {
                    val uuids = GattUuids(spec.uuid, SoundcoreCaseSpec.NOTIFY, SoundcoreCaseSpec.WRITE, SoundcoreCaseSpec.MTU)
                    env.transport.gatt(env.transport.at(address), GattSpec(uuids), env.linkLog)
                }
            }
        }
        return super.open(env, device, driver)
    }

    override fun recordedModel(code: String?): Model? = SoundcoreModels.model(code)
    override val recordedUnmatched: String get() = "Soundcore earbuds (model not matched)"

    override fun noiseOffline(device: Paired): List<NoiseChoice> =
        if (SoundcoreLayout.of(device.model)?.has("sound_modes") == true) three() else emptyList()

    override fun eqOffline(device: Paired): Pair<EqLayout, EqTarget>? =
        (SoundcoreEqSpec.of(device.model)?.let { BandEqTarget(SoundcoreBandEq(it, { null }, true)) }
            ?: SoundcoreNormalizedSpec.of(device.model)?.let { BandEqTarget(SoundcoreNormalizedBandEq(it, { null }, true)) })?.let { it.layout to it }
}

/** The local offset from UTC in seconds, to the half hour, from the local date and time at [epochSeconds]. */
internal fun offsetSeconds(local: LocalTime, epochSeconds: Long): Int {
    // Days since 1970 of the local date (H. Hinnant's days_from_civil).
    val y = (if (local.month <= 2) local.year - 1 else local.year).toLong()
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (if (local.month > 2) local.month - 3 else local.month + 9) + 2) / 5 + local.day - 1
    val days = era * 146097 + yoe * 365 + yoe / 4 - yoe / 100 + doy - 719468
    val seconds = days * 86400 + local.hour * 3600 + local.minute * 60 + local.second
    return ((seconds - epochSeconds + 900).floorDiv(1800L) * 1800).toInt().coerceIn(-12 * 3600, 14 * 3600)
}

/**
 * Soundcore earbuds ([SoundcoreTalk]). Each full state they send is the host's `extra`, which their touch controls and
 * their EQ are read from.
 */
class SoundcoreDriver(host: DriverHost, private val device: Paired) : TalkDriver<ByteArray, SoundcoreTalk>(host) {
    /** The session of the channel now or last open; for the device report. */
    val session: SoundcoreSession? get() = talk?.session

    private val lastState: SoundcoreState? get() = host.extra.value as? SoundcoreState

    /** Makes the link to the case at an address; set by [SoundcoreSpec.open] for the models whose case has one. */
    var openCase: ((String) -> ControlLink)? = null
    private var caseJob: Job? = null
    private var caseTalk: SoundcoreCaseTalk? = null
    private var caseConversation: Conversation<ByteArray>? = null

    override fun talk(opened: ControlLink): SoundcoreTalk = SoundcoreTalk(device.model.orEmpty()).also { talk ->
        // A new channel to the earbuds starts the case's over.
        caseJob?.cancel()
        caseJob = null
        caseTalk = null
        caseConversation = null
        talk.session.secondOfDay = { host.localTime()?.let { it.hour * 3600 + it.minute * 60 + it.second } }
    }

    override fun fed(talk: SoundcoreTalk) {
        if (talk.session.lastState !== host.extra.value) shown(talk.session)
        reachCase(talk.session)
    }

    /** Opens the case's own conversation, once per channel, when the earbuds have said where the case is. */
    private fun reachCase(session: SoundcoreSession) {
        if (caseJob != null) return
        val open = openCase ?: return
        val address = SoundcoreCaseSpec.address(session.lastState) ?: return
        val case = SoundcoreCaseTalk(device.model.orEmpty(), ::caseClock)
        caseJob = host.launch {
            var link: ControlLink? = null
            try {
                val made = open(address).also { link = it }
                made.connect()
                val wire = object : TalkWire {
                    override suspend fun send(frame: ByteArray) = made.send(frame)
                    override fun reads(): Flow<ByteArray> = made.reads()
                    override fun close() = made.close()
                }
                caseTalk = case
                val running = Conversation(case, wire, host.log, changed = { caseShown() }, fed = {})
                caseConversation = running
                running.run()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The case out of reach or out of power leaves the earbuds as they are.
                host.log?.invoke("case: ${e.message}")
            } finally {
                link?.close()
                if (caseTalk === case) {
                    caseTalk = null
                    caseConversation = null
                    caseShown()
                }
            }
        }
    }

    private fun caseShown() {
        talk?.let { host.settings.value = it.session.groups + caseTalk?.groups.orEmpty() }
    }

    private fun caseClock(): SoundcoreCaseClock? {
        val local = host.localTime() ?: return null
        val now = Clock.System.now().epochSeconds
        return SoundcoreCaseClock(now, offsetSeconds(local, now), local.twentyFourHour)
    }

    /** The session's last state and rows, for the screens and the open EQ. */
    private fun shown(session: SoundcoreSession, rows: Boolean = true) {
        host.extra.value = session.lastState
        if (rows) host.settings.value = session.groups + caseTalk?.groups.orEmpty()
        host.eq(EqEvent.REPORT)
    }

    /** The open channel's part in a hearing test, or null where the earbuds have none. */
    fun hearing(): SoundcoreHearingHost? {
        val test = SoundcoreHearingSpec.of(device.model) ?: return null
        if (talk == null) return null
        fun write(packets: List<SoundcorePacket>): Boolean {
            val talk = talk ?: return false
            if (!send(talk, talk.written(talk.session.frames(packets)))) return false
            shown(talk.session)
            return true
        }
        return object : SoundcoreHearingHost {
            override val spec = test
            override fun testing(running: Boolean) {
                talk?.let { write(it.session.hearingTest(running)) }
            }
            override fun save(result: SoundcoreHearingResult): Boolean = talk?.session?.hearingSave(result)?.let(::write) ?: false
        }
    }

    /** The packets, then the whole state read back, which confirms the change. */
    override fun changeSetting(change: SettingChange) {
        if (change.key.startsWith(SoundcoreCaseTalk.PREFIX)) {
            val case = caseTalk ?: return
            val running = caseConversation ?: return
            val out = case.change(change) ?: return
            running.send(out, null)
            val reads = case.takeReadBacks()
            host.launch {
                delay(EarbudsConnection.READ_BACK_MS)
                running.send(reads)
            }
            caseShown()
            return
        }
        val talk = talk ?: return
        val out = talk.change(change) ?: return
        if (send(talk, out)) shown(talk.session)
    }

    /** The model's EQ packets, then a state request, so the earbuds report the EQ they took. */
    override fun sendEq(frames: List<ByteArray>) {
        val talk = talk ?: return
        send(talk, frames + talk.session.requestState(), emptyList(), replaces = EQ_WRITE)
    }

    /**
     * Changes touch controls ([SoundcoreTouch], from the last state the earbuds sent): the change shows at
     * once, goes out as the model's packets, and [EarbudsConnection.READ_BACK_MS] later the whole state is
     * read back, so the earbuds' answer replaces the guess.
     */
    fun changeTouch(make: (SoundcoreTouch) -> SoundcoreButtons.Change?) {
        val talk = talk ?: return
        val change = talk.session.touch?.let(make) ?: return
        if (!send(talk, talk.written(talk.session.frames(change.packets)))) return
        talk.session.expect(change)
        shown(talk.session, rows = false)
    }

    /** Puts touch actions back to the model's defaults, then reads the state back. */
    fun resetTouch() {
        val talk = talk ?: return
        val packet = talk.session.touch?.reset() ?: return
        send(talk, talk.written(talk.session.frames(listOf(packet))))
    }

    /**
     * Every model with an EQ in data/soundcore/devices.json (owner, 2026-10-29); the screen says when its write is not
     * confirmed. The earbuds report their EQ with every state; the Sound tab follows it.
     */
    override fun eqSource(device: Paired, model: Model?): EqSource? {
        val checksum = SoundcoreLayout.of(device.model)?.checksum ?: true
        SoundcoreNormalizedSpec.of(device.model)?.let { normalized ->
            val band = SoundcoreNormalizedBandEq(normalized, { lastState }, checksum)
            val target = BandEqTarget(band)
            return EqSource(normalized, target.layout, target) { current -> lastState?.let { band.report(it) }?.let { target.reportedAs(it, current) } }
        }
        val spec = SoundcoreEqSpec.of(device.model) ?: return null
        val band = SoundcoreBandEq(spec, { lastState }, checksum)
        val target = BandEqTarget(band)
        return EqSource(spec, target.layout, target) { current -> lastState?.let { band.report(it) }?.let { target.reportedAs(it, current) } }
    }

    override fun report(info: ReportInfo): String? {
        val session = session ?: return null
        return buildString {
            appendLine("PairFree device report")
            appendLine("Paste this whole report into a new issue at")
            appendLine("${info.issues} to help add support")
            appendLine("for this model. The serial number's Bluetooth address is masked.")
            appendLine()
            appendLine("Bluetooth name:  ${info.name}")
            appendLine("Brand:           Soundcore")
            appendLine("Model:           ${session.model}")
            appendLine("Service:         ${info.device?.service ?: "serial port"}")
            appendLine("Firmware:        ${session.lastState?.firmware ?: "unknown"}")
            reportApp(info)
            appendLine()
            val expected = session.layout?.minSize?.let { "at least $it bytes expected" } ?: "no layout"
            val got = session.lastState?.size?.let { "$it bytes" } ?: "no reply yet"
            appendLine("The earbuds' reply to the state request (01 01) ($got, $expected), as hex:")
            appendLine(session.lastState?.reportHex() ?: "no reply yet")
            caseTalk?.reportHex()?.let {
                appendLine()
                appendLine("The charging case's reply to the state request, as hex:")
                appendLine(it)
            }
        }
    }
}
