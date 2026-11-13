package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.CambridgeBandEq
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.Talk
import io.github.omegadestroyer.pairfree.protocol.cambridge.CambridgeConnectSession
import io.github.omegadestroyer.pairfree.protocol.cambridge.CambridgeConnectTalk
import io.github.omegadestroyer.pairfree.protocol.cambridge.CambridgeModels
import io.github.omegadestroyer.pairfree.protocol.cambridge.CambridgeSession
import io.github.omegadestroyer.pairfree.protocol.cambridge.CambridgeTalk

/** Cambridge Audio's Melomania: the legacy models (Melomania 2.8.12) and the Melomania Connect ones (A100, M100, P100, X100; GAIA v3). All their settings are rows. */
object CambridgeSpec : BrandSpec(Brand.CAMBRIDGE) {
    override val officialApp = "Melomania by Cambridge Audio"

    override val offersRecording: Boolean get() = false

    override fun eqNote(device: Paired): Int =
        if (CambridgeModels.fromName(device.model ?: device.name)?.connect == true) R.string.eq_cambridge_connect else R.string.eq_cambridge

    override fun paired(address: String, name: String, uuids: List<String>): Paired? =
        CambridgeModels.fromName(name)?.let { Paired(address, name, Brand.CAMBRIDGE, service = CambridgeModels.service(uuids), model = it.name) }

    override fun model(device: Paired, state: EarbudState): Model? = CambridgeModels.model(device.model ?: device.name)

    override fun driver(device: Paired, host: DriverHost): BrandDriver = CambridgeDriver(host, device)
}

/** Legacy Melomania ([CambridgeTalk]). It never takes the shared commands or SOUNDPEATS' EQ frames. */
class CambridgeDriver(host: DriverHost, private val device: Paired) : TalkDriver<ByteArray, Talk<ByteArray>>(host) {
    /** The session of the channel now or last open; for the device report. */
    val session: CambridgeSession? get() = (talk as? CambridgeTalk)?.session
    private val connectSession: CambridgeConnectSession? get() = (talk as? CambridgeConnectTalk)?.session

    override fun talk(opened: ControlLink): Talk<ByteArray>? = CambridgeModels.fromName(device.model ?: device.name)?.let {
        if (it.connect) CambridgeConnectTalk(it) else CambridgeTalk(it)
    }

    /** Melomania Connect's EQ goes out through the talk's queue as Qualcomm music processing frames. */
    override fun sendEq(frames: List<ByteArray>) {
        val talk = talk as? CambridgeConnectTalk ?: return
        if (!host.connected) return
        if (send(talk, frames)) publish(talk)
    }

    /** Legacy Melomania's five gain controls have no established frequency grid and use Sound-tab rows; the Connect models open the EQ over the bands the earbuds report. */
    override fun eqSource(device: Paired, model: Model?): EqSource? {
        val session = connectSession?.takeIf { it.eqKnown && it.eqPresets.isNotEmpty() } ?: return null
        val band = CambridgeBandEq(session)
        val target = BandEqTarget(band)
        return EqSource(session to band.eqBandsHz, target.layout, target) { current -> band.report()?.let { target.reportedAs(it, current) } }
    }

    override fun report(info: ReportInfo): String? {
        connectSession?.let { c ->
            return buildString {
                appendLine("PairFree device report")
                appendLine("Bluetooth name:  ${info.name}")
                appendLine("Brand:           Cambridge Audio (Melomania Connect)")
                appendLine("Model:           ${c.model.name}")
                appendLine("Firmware:        ${c.firmware ?: "unknown"}")
                reportApp(info)
                appendLine("Only known fields from PairFree's own reads follow; addresses are never read.")
                reportReplies(c.replies.mapKeys { it.key })
            }
        }
        val session = session ?: return null
        return buildString {
            appendLine("PairFree device report")
            appendLine("Bluetooth name:  ${info.name}")
            appendLine("Brand:           Cambridge Audio (legacy Melomania)")
            appendLine("Model:           ${session.model.name}")
            appendLine("Firmware:        ${session.firmware ?: "unknown"}")
            reportApp(info)
            appendLine("Addresses redacted; only known fields from requested settings reads follow.")
            reportReplies(session.replies)
        }
    }
}
