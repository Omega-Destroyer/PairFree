package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.EarbudsConnection
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.protocol.Conversation
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.TalkWire
import io.github.omegadestroyer.pairfree.protocol.jbl.RacePacket
import io.github.omegadestroyer.pairfree.protocol.jbl.RaceParser
import io.github.omegadestroyer.pairfree.protocol.skullcandy.BragiRpc
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyBandEq
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyModels
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyRaceTalk
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyRpcBandEq
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyRpcTalk
import io.github.omegadestroyer.pairfree.protocol.skullcandy.usesRpc
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * The conversation of the three products that speak Bragi's protobuf RPC ([SkullcandyRpcTalk]), with the second serial channel beside it where the model has settings that are Airoha RACE's: multipoint
 * on all three and the custom equaliser of the Method models (the Crusher 1080 ANC's equaliser is its own parametric one over RPC).
 */
class SkullcandyRpcDriver(host: DriverHost, private val device: Paired) : TalkDriver<BragiRpc, SkullcandyRpcTalk>(host), RaceSide {
    override var raceLink: ControlLink? = null
    private var raceTalk: SkullcandyRaceTalk? = null
    private var raceConversation: Conversation<RacePacket>? = null

    override fun talk(opened: ControlLink): SkullcandyRpcTalk? {
        val product = SkullcandyModels.fromName(device.model ?: device.name)?.takeIf { it.usesRpc } ?: return null
        val rpc = SkullcandyRpcTalk(product)
        val link = raceLink
        if (link != null) {
            val race = SkullcandyRaceTalk(product)
            raceTalk = race
            val wire = object : TalkWire {
                override suspend fun send(frame: ByteArray) = link.send(frame)
                override fun reads(): Flow<ByteArray> = link.reads()
                override fun close() = link.close()
            }
            val running = Conversation(race, wire, host.log, changed = { publish(rpc) }, fed = { publish(rpc) })
            raceConversation = running
            host.launch { running.run() }
        }
        return rpc
    }

    override fun publish(talk: SkullcandyRpcTalk) {
        show(talk.state, talk.groups + raceTalk?.groups.orEmpty())
        eqIs(talk.eq)
    }

    override fun changeSetting(change: SettingChange) {
        if (change.key != SkullcandyRaceTalk.MULTIPOINT) return super.changeSetting(change)
        val race = raceTalk ?: return
        val running = raceConversation ?: return
        val out = race.change(change) ?: return
        if (!host.connected) return
        val reads = race.takeReadBacks()
        running.send(out, null)
        if (reads.isNotEmpty()) host.launch { delay(EarbudsConnection.READ_BACK_MS); running.send(reads) }
        talk?.let { publish(it) }
    }

    /** The Crusher 1080 ANC's curve is an RPC request; the Method models' is the RACE steps of the custom curve. */
    override fun sendEq(frames: List<ByteArray>) {
        val t = talk ?: return
        if (t.product.sdk == "CHP300") {
            send(t, frames.mapNotNull { t.messageOf(it) }, reads = emptyList(), replaces = EQ_WRITE)
            return
        }
        val running = raceConversation ?: return
        if (!host.connected) return
        val parser = RaceParser()
        running.send(frames.flatMap { parser.feed(it) }, EQ_WRITE)
    }

    override fun eqSource(device: Paired, model: Model?): EqSource? {
        val t = talk ?: return null
        if (t.product.sdk == "CHP300") {
            if (!t.hasEq) return null
            val band = SkullcandyRpcBandEq(t)
            val target = BandEqTarget(band)
            return EqSource(t to band.eqBandsHz, target.layout, target) { current -> band.eqReport?.let { target.reportedAs(it, current) } }
        }
        val race = raceTalk?.takeIf { it.hasCustomEq && it.chipKnown } ?: return null
        val band = SkullcandyBandEq(race)
        val target = BandEqTarget(band)
        return EqSource(race to band.eqBandsHz, target.layout, target) { current -> band.eqReport?.let { target.reportedAs(it, current) } }
    }

    override fun report(info: ReportInfo): String? {
        val t = talk ?: return null
        return buildString {
            reportHead(info)
            appendLine("Brand:           Skullcandy (Bragi RPC)")
            appendLine("Model:           ${t.product.name}")
            appendLine("Secure access:   ${t.authenticatedAccess?.let { if (it) "required by the earbuds" else "not required" } ?: "not reported"}")
            appendLine("Access denied:   ${if (t.denied) "yes (status 41)" else "no"}")
            appendLine("RACE channel:    ${if (raceTalk == null) "none" else "open"}")
            reportApp(info)
            appendLine()
            appendLine("Requests the earbuds refused (service.method: status):")
            t.failures.forEach { (k, v) -> appendLine("  ${k.first}.${k.second}: $v") }
            appendLine()
            appendLine("The last reply body to each request PairFree sent (service.method, then the bytes):")
            reportReplies(t.replies)
        }
    }
}
