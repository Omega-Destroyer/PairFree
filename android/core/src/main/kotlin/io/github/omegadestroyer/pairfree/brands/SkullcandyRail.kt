package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.jbl.RacePacket
import io.github.omegadestroyer.pairfree.protocol.jbl.RaceParser
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyBandEq
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyModels
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyRaceTalk
import io.github.omegadestroyer.pairfree.protocol.skullcandy.usesRail

/**
 * The Rail and Rail ANC: the SDK's configurations put every feature of these two on Airoha's RACE over Airoha's own serial service, so the RACE conversation ([SkullcandyRaceTalk]) is their main one:
 * the batteries, the hearing mode and its strength, multipoint and the equaliser (docs/skullcandy/PROTOCOL.md section 16).
 */
class SkullcandyRailDriver(host: DriverHost, private val device: Paired) : TalkDriver<RacePacket, SkullcandyRaceTalk>(host) {
    override fun talk(opened: ControlLink): SkullcandyRaceTalk? =
        SkullcandyModels.fromName(device.model ?: device.name)?.takeIf { it.usesRail }?.let { SkullcandyRaceTalk(it) }

    override fun publish(talk: SkullcandyRaceTalk) {
        show(talk.state, talk.groups)
        eqIs(talk.eq)
    }

    /** The steps of the custom curve go through the one conversation, a newer curve replacing an older one still waiting. */
    override fun sendEq(frames: List<ByteArray>) {
        val t = talk ?: return
        val parser = RaceParser()
        send(t, frames.flatMap { parser.feed(it) }, reads = emptyList(), replaces = EQ_WRITE)
    }

    /** The custom equaliser, once the earbuds have named their chip. */
    override fun eqSource(device: Paired, model: Model?): EqSource? {
        val race = talk?.takeIf { it.hasCustomEq && it.chipKnown } ?: return null
        val band = SkullcandyBandEq(race)
        val target = BandEqTarget(band)
        return EqSource(race to band.eqBandsHz, target.layout, target) { current -> band.eqReport?.let { target.reportedAs(it, current) } }
    }

    override fun report(info: ReportInfo): String? {
        val t = talk ?: return null
        return buildString {
            reportHead(info)
            appendLine("Brand:           Skullcandy (Airoha RACE)")
            appendLine("Model:           ${t.product.name}")
            appendLine("Chip family:     ${t.product.chip}")
            reportApp(info)
        }
    }
}
