package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.eq.EqTarget
import io.github.omegadestroyer.pairfree.eq.NothingBandEq
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingFrame
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingModels
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingParser
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingSession
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingTalk
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingWire
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice
import io.github.omegadestroyer.pairfree.protocol.widget.Sub

/** Nothing and CMF (docs/nothing/PROTOCOL.md). */
object NothingSpec : BrandSpec(Brand.NOTHING) {
    override val officialApp = "Nothing X"

    override fun eqNote(device: Paired): Int = R.string.eq_nothing

    override fun paired(address: String, name: String, uuids: List<String>): Paired =
        Paired(address, name, Brand.NOTHING, NothingWire.SERVICE_UUID, NothingModels.fromName(name)?.productId)

    /** By the product id the earbuds report, which the firmware line carries; before then, the name's. */
    override fun model(device: Paired, state: EarbudState): Model? =
        NothingModels.model(state.firmware?.code ?: device.model ?: device.name, if (state.supports(Command.BATTERY_RIGHT)) Form.EARBUDS else Form.HEADPHONES)

    override fun driver(device: Paired, host: DriverHost): BrandDriver = NothingDriver(host, device)

    override fun recordedModel(code: String?): Model? = NothingModels.model(code)
    override val recordedUnmatched: String get() = "Nothing or CMF earbuds (model not matched)"

    override fun noiseOffline(device: Paired): List<NoiseChoice>? = NothingModels.of(device.model)?.let { m ->
        NothingSession(m).let { s -> if (s.sharedNoise) three(s.levels.takeIf { it.size > 1 }.orEmpty().map { Sub.Row("noise_strength", it) }) else emptyList() }
    }

    override fun eqOffline(device: Paired): Pair<EqLayout, EqTarget>? =
        NothingModels.of(device.model)?.let { BandEqTarget(NothingBandEq(NothingSession(it))) }?.takeIf { it.hasCustom || it.layout.presets.isNotEmpty() }?.let { it.layout to it }
}

/** Nothing and CMF earbuds and headphones ([NothingTalk]). */
class NothingDriver(host: DriverHost, private val device: Paired) : TalkDriver<NothingFrame, NothingTalk>(host) {
    /** The session of the channel now or last open; for the device report and the EQ. */
    val session: NothingSession? get() = talk?.session

    override fun talk(opened: ControlLink): NothingTalk = NothingTalk(NothingModels.of(device.model) ?: NothingModels.fromName(device.name))

    /** Nothing's EQ target hands over its session's frames as bytes; they go through the talk's queue. */
    override fun sendEq(frames: List<ByteArray>) {
        val talk = talk ?: return
        if (!host.connected) return
        if (send(talk, frames.flatMap { NothingParser().feed(it) })) publish(talk)
    }

    /** Nothing earbuds name their model in their first answers; the EQ opens then, with the model's presets and custom curve. */
    override fun eqSource(device: Paired, model: Model?): EqSource? {
        val session = session ?: return null
        if (session.model == null || (session.eqPresets.isEmpty() && session.simpleEqFilters == null)) return null
        val band = NothingBandEq(session)
        val target = BandEqTarget(band)
        return EqSource(session, target.layout, target) { current -> this.session?.eq?.let { band.report(it) }?.let { target.reportedAs(it, current) } }
    }

    override fun report(info: ReportInfo): String? {
        val session = session ?: return null
        return buildString {
            reportHead(info)
            appendLine("Brand:           Nothing")
            appendLine("Model:           ${session.model?.name ?: "not in PairFree's table"} (product ${session.productId ?: "unknown"})")
            appendLine("Firmware:        ${session.firmware ?: "unknown"}")
            reportApp(info)
            appendLine()
            appendLine("The last answer to each read PairFree sent, and the battery and noise events")
            appendLine("(command: payload as hex; empty means the earbuds answered without a value):")
            reportReplies(session.replies)
        }
    }
}
