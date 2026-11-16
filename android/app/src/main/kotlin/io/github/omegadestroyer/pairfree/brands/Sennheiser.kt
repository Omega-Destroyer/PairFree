package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.eq.SennheiserBandEq
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.sennheiser.SennheiserCommands
import io.github.omegadestroyer.pairfree.protocol.sennheiser.SennheiserModels
import io.github.omegadestroyer.pairfree.protocol.sennheiser.SennheiserSession
import io.github.omegadestroyer.pairfree.protocol.sennheiser.SennheiserWire
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice

/**
 * Sennheiser's GAIA v3 products (docs/sennheiser/PROTOCOL.md): the Accentum family, CX 200/Plus/500, Momentum 4, Momentum Sport and Momentum True Wireless 3 and 4
 * from Smart Control 4.9.4, and the Momentum 5, Momentum True Wireless 5, HDB 630 and HDR 275 from Smart Control Plus 1.6.3. The older products (GAIA v2, Nautilus) and
 * the Accentum Clip (Airoha) are not covered. Not tried on any of them.
 */
object SennheiserSpec : BrandSpec(Brand.SENNHEISER) {
    override val officialApp = "Sennheiser Smart Control"

    override val offersRecording: Boolean get() = false

    override fun paired(address: String, name: String, uuids: List<String>): Paired? =
        SennheiserModels.fromName(name)?.let { Paired(address, name, Brand.SENNHEISER, SennheiserWire.CLASSIC_UUID, it.name) }
            ?: uuids.firstOrNull { it.equals(SennheiserWire.CLASSIC_UUID, ignoreCase = true) }?.let { Paired(address, name, Brand.SENNHEISER, SennheiserWire.CLASSIC_UUID, null) }

    override fun model(device: Paired, state: EarbudState): Model? = SennheiserModels.model(device.name) ?: SennheiserModels.ALL.firstOrNull { it.name == device.model }?.let { SennheiserModels.model(it.names.first()) }

    override fun driver(device: Paired, host: DriverHost): BrandDriver = SennheiserDriver(host, device)

    /** A model with a product file in Smart Control has noise control by what the file lists; one without (the Momentum 5...) only by what it answers. */
    override fun noiseOffline(device: Paired): List<NoiseChoice>? = SennheiserModels.ALL.firstOrNull { it.name == device.model }?.let { m ->
        val properties = m.properties ?: return null
        if (SennheiserCommands.ANC_STATUS in properties && SennheiserCommands.TRANSPARENCY_STATUS in properties) three() else emptyList()
    }

    override val mayHaveNoise: Boolean get() = true

    override fun eqNote(device: Paired): Int = R.string.eq_sennheiser
}

/** Sennheiser products ([SennheiserSession]): one command at a time over RFCOMM, each row once its read answered. */
class SennheiserDriver(host: DriverHost, private val device: Paired) : TalkDriver<ByteArray, SennheiserSession>(host) {
    /** The session of the channel now or last open; for the device report. */
    val session: SennheiserSession? get() = talk

    override fun talk(opened: ControlLink): SennheiserSession =
        SennheiserSession(SennheiserModels.ALL.firstOrNull { it.name == device.model } ?: SennheiserModels.fromName(device.name))

    /** The equalizer goes out through the talk's queue, one message after another. */
    override fun sendEq(frames: List<ByteArray>) {
        val talk = talk ?: return
        if (!host.connected) return
        if (send(talk, frames)) publish(talk)
    }

    /** The Sound tab opens over the bands of whichever equalizer the earbuds answered as. */
    override fun eqSource(device: Paired, model: Model?): EqSource? {
        val session = session?.takeIf { it.eqKnown } ?: return null
        val band = SennheiserBandEq(session)
        val target = BandEqTarget(band)
        return EqSource(session to band.eqBandsHz, target.layout, target) { current -> band.report()?.let { target.reportedAs(it, current) } }
    }

    override fun report(info: ReportInfo): String? {
        val session = session ?: return null
        return buildString {
            reportHead(info)
            appendLine("Brand:           Sennheiser")
            appendLine("Model:           ${session.model?.name ?: "not in PairFree's table"} (model number ${session.modelId ?: "unknown"})")
            appendLine("Firmware:        ${session.firmware ?: "unknown"}")
            reportApp(info)
            appendLine()
            appendLine("The last answer to each property PairFree read (name: payload as hex):")
            reportReplies(session.replies)
        }
    }
}
