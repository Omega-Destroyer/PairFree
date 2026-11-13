package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.eq.EqTarget
import io.github.omegadestroyer.pairfree.eq.PixelBandEq
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.pixel.PixelFamily
import io.github.omegadestroyer.pairfree.protocol.pixel.PixelMessage
import io.github.omegadestroyer.pairfree.protocol.pixel.PixelModels
import io.github.omegadestroyer.pairfree.protocol.pixel.PixelSession
import io.github.omegadestroyer.pairfree.protocol.pixel.PixelTalk
import io.github.omegadestroyer.pairfree.protocol.pixel.PixelTransport
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice

/** Google's Pixel Buds (docs/pixel/PROTOCOL.md). */
object PixelSpec : BrandSpec(Brand.PIXEL) {
    override val officialApp = "Pixel Buds"

    override fun eqNote(device: Paired): Int = R.string.eq_pixel

    /** The service the earbuds list picks the transport, else the model their name gives (docs/pixel/PROTOCOL.md section 1). */
    override fun paired(address: String, name: String, uuids: List<String>): Paired {
        val known = PixelModels.fromName(name)
        val transport = PixelModels.transportOf(uuids) ?: known?.transport ?: PixelTransport.MAESTRO
        return Paired(address, name, Brand.PIXEL, PixelModels.serviceFor(transport), known?.deviceType?.toString())
    }

    /** By the device type they report, which the firmware line carries; before then, their name's. */
    override fun model(device: Paired, state: EarbudState): Model? = PixelModels.model(state.firmware?.code ?: device.model ?: device.name)

    override fun driver(device: Paired, host: DriverHost): BrandDriver = PixelDriver(host, device)

    override fun noiseOffline(device: Paired): List<NoiseChoice>? =
        PixelModels.of(device.model?.toIntOrNull())?.let { if (it.family == PixelFamily.ALLEGRO) three() else emptyList() }

    /** Google's presets and five bands; "Last saved" joins them once the earbuds have reported it. */
    override fun eqOffline(device: Paired): Pair<EqLayout, EqTarget>? =
        PixelModels.of(device.model?.toIntOrNull())?.takeIf { it.family == PixelFamily.ALLEGRO }?.let { BandEqTarget(PixelBandEq(PixelSession(it.transport, it))) }?.let { it.layout to it }
}

/** Pixel Buds ([PixelTalk]). */
class PixelDriver(host: DriverHost, private val device: Paired) : TalkDriver<PixelMessage, PixelTalk>(host) {
    /** The session of the channel now or last open; for the device report and the EQ. */
    val session: PixelSession? get() = talk?.session

    override fun talk(opened: ControlLink): PixelTalk {
        val transport = PixelModels.transportOf(listOfNotNull(device.service)) ?: PixelTransport.MAESTRO
        return PixelTalk(transport, PixelModels.fromName(device.name) ?: device.model?.toIntOrNull()?.let(PixelModels::of))
    }

    /** The Pixel Buds' EQ target hands over its session's messages as bytes; they go through the talk's queue. */
    override fun sendEq(frames: List<ByteArray>) {
        val talk = talk ?: return
        if (!host.connected) return
        if (send(talk, talk.session.eqMessages(frames))) publish(talk)
    }

    /** Pixel Buds Pro, Pro 2 and 2a once their EQ read answered; the 2020 model and the A-Series have bass boost alone, a row on the Sound tab. */
    override fun eqSource(device: Paired, model: Model?): EqSource? {
        val session = session ?: return null
        if (!session.hasEq) return null
        val band = PixelBandEq(session)
        val target = BandEqTarget(band)
        // Open again when the saved curve arrived after the EQ: Last saved joins the presets.
        return EqSource(session to band.hasSaved, target.layout, target) { current -> this.session?.eq?.let { band.report(it) }?.let { target.reportedAs(it, current) } }
    }

    override fun report(info: ReportInfo): String? {
        val session = session ?: return null
        return buildString {
            reportHead(info)
            appendLine("Brand:           Google Pixel Buds")
            appendLine("Model:           ${session.model?.name ?: "not in PairFree's table"} (device type ${session.deviceType ?: "unknown"})")
            appendLine("Transport:       ${session.transport.name.lowercase()}${session.channel?.let { " (channel $it)" }.orEmpty()}")
            appendLine("Firmware:        ${session.firmware ?: "unknown"}")
            reportApp(info)
            appendLine()
            appendLine("The last answer to each read PairFree sent (setting N: the field as protobuf, or the error")
            appendLine("status the earbuds gave; the battery and firmware as protobuf):")
            reportReplies(session.replies)
            if (session.refused.isNotEmpty()) appendLine("Settings the earbuds do not have: ${session.refused.joinToString(", ")}")
        }
    }
}
