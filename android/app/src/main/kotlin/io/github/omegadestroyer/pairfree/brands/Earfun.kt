package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EarfunBandEq
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.eq.EqTarget
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.earfun.EarfunModels
import io.github.omegadestroyer.pairfree.protocol.earfun.EarfunSession
import io.github.omegadestroyer.pairfree.protocol.earfun.EarfunTalk
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice
import io.github.omegadestroyer.pairfree.protocol.widget.Sub

/** EarFun (docs/earfun/PROTOCOL.md). */
object EarfunSpec : BrandSpec(Brand.EARFUN) {
    override val officialApp = "EarFun Audio"

    override fun eqNote(device: Paired): Int = R.string.eq_earfun

    // EarFun's models on chips whose protocol PairFree does not cover yet drop out (docs/earfun/PROTOCOL.md section 9).
    override fun paired(address: String, name: String, uuids: List<String>): Paired? =
        EarfunModels.fromName(name)?.takeIf { it.covered }?.let { Paired(address, name, Brand.EARFUN, model = it.name) }

    /** By the model the Bluetooth name matched. */
    override fun model(device: Paired, state: EarbudState): Model? = EarfunModels.model(device.model ?: device.name)

    override fun driver(device: Paired, host: DriverHost): BrandDriver = EarfunDriver(host, device)

    override fun noiseOffline(device: Paired): List<NoiseChoice>? = EarfunModels.fromName(device.name)?.let { m ->
        if (m.noise == null) emptyList()
        else EarfunSession(m).let { s ->
            three(s.cancellingChoices.map { Sub.Row(EarfunSession.ANC_MODE_KEY, it) }, s.ambientChoices.map { Sub.Row(EarfunSession.AMBIENT_MODE_KEY, it) })
        }
    }

    override fun eqOffline(device: Paired): Pair<EqLayout, EqTarget>? =
        EarfunModels.fromName(device.name)?.let { m -> m.eq?.let { BandEqTarget(EarfunBandEq(EarfunSession(m), it)) } }?.let { it.layout to it }
}

/** EarFun earbuds and headphones ([EarfunTalk]). */
class EarfunDriver(host: DriverHost, private val device: Paired) : TalkDriver<ByteArray, EarfunTalk>(host) {
    /** The session of the channel now or last open; for the device report and the EQ. */
    val session: EarfunSession? get() = talk?.session

    override fun talk(opened: ControlLink): EarfunTalk? =
        (EarfunModels.fromName(device.model ?: device.name) ?: EarfunModels.fromName(device.name))?.let(::EarfunTalk)

    /** EarFun's EQ target hands over its session's frames as bytes; they go through the talk's queue. */
    override fun sendEq(frames: List<ByteArray>) {
        val talk = talk ?: return
        if (!host.connected) return
        send(talk, frames)
    }

    /** EarFun models' EQ is in their table and cannot be read: the screen shows what this phone last sent. */
    override fun eqSource(device: Paired, model: Model?): EqSource? {
        val session = session ?: return null
        val spec = session.model.eq ?: return null
        val target = BandEqTarget(EarfunBandEq(session, spec))
        return EqSource(session, target.layout, target)
    }

    /** The model, its protocol, the firmware and each read's last answer. */
    override fun report(info: ReportInfo): String? {
        val session = session ?: return null
        return buildString {
            reportHead(info)
            appendLine("Brand:           EarFun")
            val model = session.model
            appendLine("Model:           ${model.name} (${model.codes.joinToString(" or ")}, ${model.chip}, ${model.protocol} protocol)")
            appendLine("Firmware:        ${session.firmware ?: "unknown"}")
            reportApp(info)
            appendLine()
            appendLine("The last answer to each read PairFree sent (command: payload as hex):")
            reportReplies(session.replies)
        }
    }
}
