package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.oneplus.OneplusModels
import io.github.omegadestroyer.pairfree.protocol.oneplus.OneplusSession
import io.github.omegadestroyer.pairfree.protocol.oneplus.OneplusTalk
import io.github.omegadestroyer.pairfree.protocol.oneplus.OneplusWire

/** OnePlus and OPPO, over HeyMelody's protocol (docs/oneplus/PROTOCOL.md). */
object OneplusSpec : BrandSpec(Brand.ONEPLUS) {
    override val officialApp = "HeyMelody"

    override fun eqNote(device: Paired): Int = R.string.eq_oneplus

    override fun paired(address: String, name: String, uuids: List<String>): Paired? =
        OneplusModels.fromName(name)?.let { Paired(address, name, Brand.ONEPLUS, OneplusWire.SPP_UUID, it.id) }

    override fun model(device: Paired, state: EarbudState): Model? = OneplusModels.model(device.name)

    override fun driver(device: Paired, host: DriverHost): BrandDriver = OneplusDriver(host, device)
}

/**
 * OnePlus and OPPO earbuds ([OneplusTalk]). The session is itself the brand-neutral graphic equalizer
 * ([io.github.omegadestroyer.pairfree.protocol.BandEq]), so the Sound tab needs no adapter for it.
 */
class OneplusDriver(host: DriverHost, private val device: Paired) : TalkDriver<ByteArray, OneplusTalk>(host) {
    /** The session of the channel now or last open; for the device report, the EQ and the hearing test. */
    val session: OneplusSession? get() = talk?.session

    override fun talk(opened: ControlLink): OneplusTalk = OneplusTalk(OneplusModels.byId(device.model ?: "") ?: OneplusModels.fromName(device.name))

    override fun sendEq(frames: List<ByteArray>) {
        val talk = talk ?: return
        if (send(talk, frames, emptyList())) publish(talk)
    }

    /** The session's own bands and presets, once it has any; before the channel opens, the model's table. */
    override fun eqSource(device: Paired, model: Model?): EqSource? {
        val band = session ?: return super.eqSource(device, model)
        if (band.eqBandsHz.isEmpty() && band.eqPresetNames.isEmpty()) return null
        val target = BandEqTarget(band)
        return EqSource(band, target.layout, target) { current -> this.session?.eqReport?.let { target.reportedAs(it, current) } }
    }

    override fun report(info: ReportInfo): String? {
        val session = session ?: return null
        return buildString {
            reportHead(info, bluetoothName = false)
            appendLine("Brand:           OnePlus / OPPO (HeyMelody protocol)")
            appendLine("Model:           ${session.model?.name ?: "not in PairFree's table"}")
            appendLine("Product id:      ${session.productId ?: "unknown"}")
            appendLine("Capability bits: ${session.capabilities?.sorted()?.joinToString(" ") ?: "none yet"}")
            reportApp(info, phone = true)
        }
    }
}
