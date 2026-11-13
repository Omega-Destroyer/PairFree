package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.eq.EqTarget
import io.github.omegadestroyer.pairfree.eq.OneMoreBandEq
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.onemore.OneMoreFrame
import io.github.omegadestroyer.pairfree.protocol.onemore.OneMoreModels
import io.github.omegadestroyer.pairfree.protocol.onemore.OneMoreSession
import io.github.omegadestroyer.pairfree.protocol.onemore.OneMoreTalk
import io.github.omegadestroyer.pairfree.protocol.onemore.OneMoreWire
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice
import io.github.omegadestroyer.pairfree.protocol.widget.Sub

/** 1MORE (docs/1more/PROTOCOL.md). */
object OneMoreSpec : BrandSpec(Brand.ONEMORE) {
    override val officialApp = "1MORE"

    override fun eqNote(device: Paired): Int = R.string.eq_onemore

    override fun paired(address: String, name: String, uuids: List<String>): Paired? =
        OneMoreModels.identify(name)?.let { Paired(address, name, Brand.ONEMORE, model = it.name) }

    /** By the model the Bluetooth name matched. */
    override fun model(device: Paired, state: EarbudState): Model? = OneMoreModels.model(device.model ?: device.name)

    override fun driver(device: Paired, host: DriverHost): BrandDriver = OneMoreDriver(host, device)

    override fun noiseOffline(device: Paired): List<NoiseChoice>? = OneMoreModels.fromName(device.name)?.let { m ->
        OneMoreSession(m).let { s ->
            if (!s.sharedNoise) emptyList()
            else three(s.strengths.map { Sub.Row("noise_strength", it) }, s.transparencyKinds.map { Sub.Row("transparency_mode", it) })
        }
    }

    override fun eqOffline(device: Paired): Pair<EqLayout, EqTarget>? =
        OneMoreModels.fromName(device.name)?.let { BandEqTarget(OneMoreBandEq(OneMoreSession(it))) }?.let { it.layout to it }
}

/** 1MORE earbuds and headphones ([OneMoreTalk]). */
class OneMoreDriver(host: DriverHost, private val device: Paired) : TalkDriver<OneMoreFrame, OneMoreTalk>(host) {
    /** The session of the channel now or last open; for the device report and the EQ. */
    val session: OneMoreSession? get() = talk?.session

    override fun talk(opened: ControlLink): OneMoreTalk =
        OneMoreTalk(OneMoreModels.identify(device.model ?: device.name) ?: OneMoreModels.identify(device.name))

    /** A frame the 1MORE session built, read back from its bytes, its length field included. */
    private fun frameOf(bytes: ByteArray): OneMoreFrame? {
        if (bytes.size < OneMoreWire.HEADER) return null
        fun u16(at: Int) = ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)
        return OneMoreFrame(u16(2), bytes.copyOfRange(OneMoreWire.HEADER, bytes.size), u16(6), u16(0), u16(4))
    }

    /** 1MORE's EQ target hands over its session's frames as bytes; they go through the talk's queue, as sent. */
    override fun sendEq(frames: List<ByteArray>) {
        val talk = talk ?: return
        if (!host.connected) return
        if (send(talk, frames.mapNotNull(::frameOf))) publish(talk)
    }

    /** 1MORE models' EQ is in their table; it opens at once and follows what the earbuds report. */
    override fun eqSource(device: Paired, model: Model?): EqSource? {
        val session = session ?: return null
        val m = session.model ?: return null
        if (m.eqPresets.isEmpty() && m.eqBands == null) return null
        val band = OneMoreBandEq(session)
        val target = BandEqTarget(band)
        return EqSource(session, target.layout, target) { current -> this.session?.eq?.let { band.report(it) }?.let { target.reportedAs(it, current) } }
    }

    /** The model, the firmware and each read's last answer. */
    override fun report(info: ReportInfo): String? {
        val session = session ?: return null
        return buildString {
            reportHead(info)
            appendLine("Brand:           1MORE")
            val model = session.model
            appendLine("Model:           ${model?.name ?: "not in PairFree's table"} (${model?.model ?: "model unknown"})")
            appendLine("Firmware:        ${session.firmware ?: "unknown"}")
            reportApp(info)
            appendLine()
            appendLine("The last answer to each read PairFree sent (command: payload as hex):")
            reportReplies(session.replies)
        }
    }
}
