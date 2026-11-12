package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.EarbudsConnection
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.eq.EqTarget
import io.github.omegadestroyer.pairfree.eq.SoundcoreBandEq
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreButtons
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreEqSpec
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreLayout
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreModels
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreSession
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreState
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreTalk
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreTouch
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice

/** Soundcore (docs/soundcore/PROTOCOL.md). */
object SoundcoreSpec : BrandSpec(Brand.SOUNDCORE) {
    override val officialApp = "Soundcore"
    override val controls: Controls get() = Controls.SOUNDCORE

    /** Soundcore earbuds PairFree knows by name and number only: ask for a report rather than show a guess. */
    override val reportOnlyWhenUnknown: Boolean get() = true

    override fun eqNote(device: Paired): Int = R.string.eq_soundcore_unchecked

    override fun paired(address: String, name: String, uuids: List<String>): Paired? =
        SoundcoreModels.identify(name, uuids)?.let { Paired(address, name, Brand.SOUNDCORE, it.service, it.model) }

    /** By the model number the earbuds' service or name gave (data/soundcore/devices.json). */
    override fun model(device: Paired, state: EarbudState): Model? = SoundcoreModels.model(device.model ?: state.firmware?.code)

    override fun driver(device: Paired, host: DriverHost): BrandDriver = SoundcoreDriver(host, device)

    override fun recordedModel(code: String?): Model? = SoundcoreModels.model(code)
    override val recordedUnmatched: String get() = "Soundcore earbuds (model not matched)"

    override fun noiseOffline(device: Paired): List<NoiseChoice> =
        if (SoundcoreLayout.of(device.model)?.has("sound_modes") == true) three() else emptyList()

    override fun eqOffline(device: Paired): Pair<EqLayout, EqTarget>? =
        SoundcoreEqSpec.of(device.model)?.let { BandEqTarget(SoundcoreBandEq(it, { null }, true)) }?.let { it.layout to it }
}

/**
 * Soundcore earbuds ([SoundcoreTalk]). Each full state they send is the host's `extra`, which their touch controls and
 * their EQ are read from.
 */
class SoundcoreDriver(host: DriverHost, private val device: Paired) : TalkDriver<ByteArray, SoundcoreTalk>(host) {
    /** The session of the channel now or last open; for the device report. */
    val session: SoundcoreSession? get() = talk?.session

    private val lastState: SoundcoreState? get() = host.extra.value as? SoundcoreState

    override fun talk(opened: ControlLink): SoundcoreTalk = SoundcoreTalk(device.model.orEmpty())

    override fun fed(talk: SoundcoreTalk) {
        if (talk.session.lastState !== host.extra.value) shown(talk.session)
    }

    /** The session's last state and rows, for the screens and the open EQ. */
    private fun shown(session: SoundcoreSession, rows: Boolean = true) {
        host.extra.value = session.lastState
        if (rows) host.settings.value = session.groups
        host.eq(EqEvent.REPORT)
    }

    /** The packets, then the whole state read back, which confirms the change. */
    override fun changeSetting(change: SettingChange) {
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
        val spec = SoundcoreEqSpec.of(device.model) ?: return null
        val band = SoundcoreBandEq(spec, { lastState }, SoundcoreLayout.of(spec.model)?.checksum ?: true)
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
        }
    }
}
