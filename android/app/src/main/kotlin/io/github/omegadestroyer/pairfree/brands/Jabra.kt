package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.jabra.JabraModels
import io.github.omegadestroyer.pairfree.protocol.jabra.JabraPacket
import io.github.omegadestroyer.pairfree.protocol.jabra.JabraSession
import io.github.omegadestroyer.pairfree.protocol.jabra.fromName
import io.github.omegadestroyer.pairfree.protocol.jabra.gnp
import io.github.omegadestroyer.pairfree.protocol.jabra.model
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice

/**
 * Jabra's earbuds and headphones that speak GNP, the protocol of the Sound+ app's own SDK (docs/jabra/PROTOCOL.md, read from Jabra Sound+ 5.22.0). The channel is the
 * standard serial port; the products are told by name. The models Sound+ drives through Qualcomm's GAIA (Elite 2, 3, 3 Active, 4, 4 Active, 5, Connect 5t) are not covered yet.
 * Not tried on any Jabra product.
 */
object JabraSpec : BrandSpec(Brand.JABRA) {
    override val officialApp = "Jabra Sound+"

    override val offersRecording: Boolean get() = false

    override fun paired(address: String, name: String, uuids: List<String>): Paired? =
        JabraModels.fromName(name)?.takeIf { it.gnp }?.let { Paired(address, name, Brand.JABRA, model = it.name) }

    override fun model(device: Paired, state: EarbudState): Model? = JabraModels.model(device.model ?: device.name)

    override fun driver(device: Paired, host: DriverHost): BrandDriver = JabraDriver(host, device)

    override val mayHaveNoise: Boolean get() = true

    /** Off, cancelling and hear-through as the model's definition lists its loop; the earbuds' replies then say more. */
    override fun noiseOffline(device: Paired): List<NoiseChoice>? {
        val v = JabraModels.fromName(device.model ?: device.name)?.variants?.firstOrNull() ?: return emptyList()
        val anc = "anc" in v.noiseLoop || v.has("ffanc")
        val transparent = "hearThrough" in v.noiseLoop || v.has("adjustable_ambience_mode")
        return when {
            anc && transparent -> three()
            transparent -> listOf(OFF, NoiseChoice("transparency"))
            anc -> listOf(OFF, NoiseChoice("anc"))
            else -> emptyList()
        }
    }
}

/** Jabra's GNP conversation ([JabraSession]): one request at a time over the serial port, each row once its read answered. */
class JabraDriver(host: DriverHost, private val device: Paired) : TalkDriver<JabraPacket, JabraSession>(host) {
    /** The session of the channel now or last open; for the device report. */
    val session: JabraSession? get() = talk

    override fun talk(opened: ControlLink): JabraSession? = JabraModels.fromName(device.model ?: device.name)?.takeIf { it.gnp }?.let { JabraSession(it) }

    override fun report(info: ReportInfo): String? {
        val s = session ?: return null
        return buildString {
            reportHead(info)
            appendLine("Brand:           Jabra (GNP)")
            appendLine("Model:           ${s.model.name}")
            appendLine("Product id:      ${s.pid ?: "unknown"}")
            appendLine("Firmware:        ${s.firmware ?: "unknown"}")
            appendLine("Snapshot:        ${s.profile?.snapshot ?: "none"}")
            reportApp(info)
            appendLine()
            appendLine("The last answer to each read PairFree made (command, selector bytes: payload as hex):")
            reportReplies(s.answered)
        }
    }
}
