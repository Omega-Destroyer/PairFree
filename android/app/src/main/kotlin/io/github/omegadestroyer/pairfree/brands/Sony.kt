package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.eq.SonyBandEq
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.sony.SonyFunction
import io.github.omegadestroyer.pairfree.protocol.sony.SonyModels
import io.github.omegadestroyer.pairfree.protocol.sony.SonyOut
import io.github.omegadestroyer.pairfree.protocol.sony.SonySession
import io.github.omegadestroyer.pairfree.protocol.sony.SonyTalk
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice

/** Sony (docs/sony/PROTOCOL.md). */
object SonySpec : BrandSpec(Brand.SONY) {
    override val officialApp = "Sony | Sound Connect"

    override fun eqNote(device: Paired): Int = R.string.eq_sony_none

    override fun paired(address: String, name: String, uuids: List<String>): Paired? =
        SonyModels.identify(name, uuids)?.let { Paired(address, name, Brand.SONY, it.service, it.model?.name) }

    /** By the model name the earbuds give, else their Bluetooth name; one battery is headphones. */
    override fun model(device: Paired, state: EarbudState): Model? =
        SonyModels.model(state.firmware?.code ?: device.model ?: device.name, if (state.supports(Command.BATTERY_RIGHT)) Form.EARBUDS else Form.HEADPHONES)

    override fun driver(device: Paired, host: DriverHost): BrandDriver = SonyDriver(host, device)

    override fun recordedModel(code: String?): Model? = code?.let { SonyModels.model(it) }
    override val recordedUnmatched: String get() = "Sony earbuds or headphones (model not matched)"

    /** Sony's earbuds say only in their replies. */
    override fun noiseOffline(device: Paired): List<NoiseChoice>? = null

    override val mayHaveNoise: Boolean get() = true

    override fun mayHaveEq(device: Paired): Boolean = true
}

/** Sony earbuds and headphones ([SonyTalk]). */
class SonyDriver(host: DriverHost, private val device: Paired) : TalkDriver<SonyOut, SonyTalk>(host) {
    /** The session of the channel now or last open; for the device report. */
    val session: SonySession? get() = talk?.session

    override fun talk(opened: ControlLink): SonyTalk = SonyTalk(SonyModels.tableSetOf(device.service), SonyModels.of(device.model))

    /**
     * Sony's earbuds notify every change of their EQ (EQEBB_NTFY_PARAM), and Sony's app updates its EQ on each one
     * (13.3.0, read 2026-11-11; docs/sony/PROTOCOL.md): so does the Sound tab.
     */
    override val followsEqReports: Boolean get() = true

    /** Sony's EQ target hands over message payloads; they go through the talk's queue. */
    override fun sendEq(frames: List<ByteArray>) {
        val talk = talk ?: return
        if (send(talk, frames.mapNotNull(SonyBandEq::decode), emptyList())) eqIs(talk.eq, followsEqReports)
    }

    /** Sony earbuds list their presets and bands as they answer; the EQ opens once they have, and again if what they listed changes. */
    override fun eqSource(device: Paired, model: Model?): EqSource? {
        val session = session ?: return null
        val eq = session.eq ?: return null
        if (eq.steps == null || eq.bands == null) return null
        val band = SonyBandEq(session, eq)
        val target = BandEqTarget(band)
        // A layout of its own per band set, so profiles saved for one model's bands stay with it.
        return EqSource(listOf(device.address, session, eq.presets, eq.bands, eq.steps), target.layout, target) { current ->
            this.session?.eq?.let { band.report(it) }?.let { target.reportedAs(it, current) }
        }
    }

    override fun report(info: ReportInfo): String? {
        val session = session ?: return null
        return buildString {
            reportHead(info)
            appendLine("Brand:           Sony")
            appendLine("Model:           ${session.modelName ?: session.model?.name ?: "unknown"}")
            appendLine("Firmware:        ${session.firmware ?: "unknown"}")
            appendLine("Table set:       ${session.tableSet} (service ${info.device?.service ?: "unknown"})")
            reportApp(info)
            appendLine()
            val names = if (session.tableSet == 1) SonyFunction.Set1.NAMES else SonyFunction.Set2.NAMES
            appendLine("Functions the earbuds list (data/sony/functions.json):")
            session.functions.sorted().forEach { appendLine("  %02x %s".format(it, names[it] ?: "unknown")) }
            appendLine()
            appendLine("The last message of each kind they sent (table, command, type: payload as hex):")
            reportReplies(session.replies)
        }
    }
}
