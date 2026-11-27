package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.eq.JabraBandEq
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.jabra.JabraGaiaSession
import io.github.omegadestroyer.pairfree.protocol.jabra.JabraModels
import io.github.omegadestroyer.pairfree.protocol.jabra.JabraPacket
import io.github.omegadestroyer.pairfree.protocol.jabra.JabraParser
import io.github.omegadestroyer.pairfree.protocol.jabra.JabraSession
import io.github.omegadestroyer.pairfree.protocol.jabra.fromName
import io.github.omegadestroyer.pairfree.protocol.jabra.gnp
import io.github.omegadestroyer.pairfree.protocol.jabra.model
import io.github.omegadestroyer.pairfree.protocol.jabra.noise
import io.github.omegadestroyer.pairfree.protocol.sennheiser.Gaia3Packet
import io.github.omegadestroyer.pairfree.protocol.sennheiser.Gaia3Parser
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice

/**
 * Jabra's earbuds and headphones that speak GNP, the protocol of the Sound+ app's own SDK (docs/jabra/PROTOCOL.md, read from Jabra Sound+ 5.22.0). The channel is the
 * standard serial port; the products are told by name. The models Sound+ drives through Qualcomm's GAIA (Elite 2, 3, 3 Active, 4, 4 Active, 5, Connect 5t) have a session of their own.
 * Not tried on any Jabra product.
 */
object JabraSpec : BrandSpec(Brand.JABRA) {
    override val officialApp = "Jabra Sound+"

    override val offersRecording: Boolean get() = false

    /** The GAIA products list Qualcomm's GAIA service; Sound+ opens that where it is there and the serial port otherwise (docs/jabra/PROTOCOL.md). */
    override fun paired(address: String, name: String, uuids: List<String>): Paired? =
        JabraModels.fromName(name)?.let { m ->
            val service = if (m.gnp) null else uuids.firstOrNull { it.equals(GAIA_SERVICE, ignoreCase = true) }
            Paired(address, name, Brand.JABRA, service = service, model = m.name)
        }

    private const val GAIA_SERVICE = "00001107-d102-11e1-9b23-00025b00a5a5"

    override fun model(device: Paired, state: EarbudState): Model? = JabraModels.model(device.model ?: device.name)

    override fun driver(device: Paired, host: DriverHost): BrandDriver =
        if (JabraModels.fromName(device.model ?: device.name)?.gnp == false) JabraGaiaDriver(host, device) else JabraDriver(host, device)

    override val mayHaveNoise: Boolean get() = true

    /** Off, cancelling and hear-through as the model's definition lists its loop; the earbuds' replies then say more. */
    override fun noiseOffline(device: Paired): List<NoiseChoice>? {
        val v = JabraModels.fromName(device.model ?: device.name)?.variants?.firstOrNull() ?: return emptyList()
        val noise = v.noise()
        val anc = noise.anc
        val transparent = noise.transparency
        return when {
            anc && transparent -> three()
            transparent -> listOf(OFF, NoiseChoice("transparency"))
            anc -> listOf(OFF, NoiseChoice("anc"))
            else -> emptyList()
        }
    }
}

/** Jabra's GAIA conversation ([JabraGaiaSession]): the plugins Sound+ drives the Elite 2 to 5 and the Connect 5t with. */
class JabraGaiaDriver(host: DriverHost, private val device: Paired) : TalkDriver<Gaia3Packet, JabraGaiaSession>(host) {
    val session: JabraGaiaSession? get() = talk

    override fun talk(opened: ControlLink): JabraGaiaSession? = JabraModels.fromName(device.model ?: device.name)?.takeIf { !it.gnp }?.let { JabraGaiaSession(it) }

    /** The equalizer's frames are the session's own packets, sent through the talk's queue. */
    override fun sendEq(frames: List<ByteArray>) {
        val talk = talk ?: return
        if (!host.connected) return
        val parser = Gaia3Parser(setOf(JabraGaiaSession.VENDOR, JabraGaiaSession.QUALCOMM))
        if (send(talk, frames.flatMap { parser.feed(it) })) publish(talk)
    }

    override fun eqSource(device: Paired, model: Model?): EqSource? {
        val session = talk?.takeIf { it.eqKnown } ?: return null
        val band = JabraBandEq(session)
        val target = BandEqTarget(band)
        return EqSource(session to band.eqBandsHz, target.layout, target) { current -> band.report()?.let { target.reportedAs(it, current) } }
    }

    override fun report(info: ReportInfo): String? {
        val s = session ?: return null
        return buildString {
            reportHead(info)
            appendLine("Brand:           Jabra (GAIA)")
            appendLine("Model:           ${s.model.name}")
            appendLine("Features:        ${s.featureIds.sorted().joinToString(" ")}")
            reportApp(info)
            appendLine()
            appendLine("The last answer to each read PairFree made (feature/command: payload as hex):")
            reportReplies(s.answered)
        }
    }
}

/** Jabra's GNP conversation ([JabraSession]): one request at a time over the serial port, each row once its read answered. */
class JabraDriver(host: DriverHost, private val device: Paired) : TalkDriver<JabraPacket, JabraSession>(host) {
    /** The session of the channel now or last open; for the device report. */
    val session: JabraSession? get() = talk

    override fun talk(opened: ControlLink): JabraSession? = JabraModels.fromName(device.model ?: device.name)?.takeIf { it.gnp }?.let { JabraSession(it) }

    /** The equalizer's frames are packets the session built; they go out through the talk's queue, one after another. */
    override fun sendEq(frames: List<ByteArray>) {
        val talk = talk ?: return
        if (!host.connected) return
        val parser = JabraParser()
        if (send(talk, frames.flatMap { parser.feed(it) })) publish(talk)
    }

    /** The Sound tab opens over the bands the earbuds described. */
    override fun eqSource(device: Paired, model: Model?): EqSource? {
        val session = talk?.takeIf { it.eqKnown } ?: return null
        val band = JabraBandEq(session)
        val target = BandEqTarget(band)
        return EqSource(session to band.eqBandsHz, target.layout, target) { current -> band.report()?.let { target.reportedAs(it, current) } }
    }

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
