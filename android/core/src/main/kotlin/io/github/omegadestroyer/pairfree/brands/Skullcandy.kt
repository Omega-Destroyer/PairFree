package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.EarbudsConnection
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Conversation
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.TalkWire
import io.github.omegadestroyer.pairfree.protocol.jbl.RacePacket
import io.github.omegadestroyer.pairfree.protocol.jbl.RaceParser
import io.github.omegadestroyer.pairfree.protocol.skullcandy.RACE_SERVICES
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyAtSession
import io.github.omegadestroyer.pairfree.protocol.skullcandy.BesMessage
import io.github.omegadestroyer.pairfree.protocol.skullcandy.BragiRpc
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyRpcBandEq
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyRpcTalk
import io.github.omegadestroyer.pairfree.protocol.skullcandy.usesRail
import io.github.omegadestroyer.pairfree.protocol.skullcandy.usesRpc
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyBandEq
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyBesBandEq
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyBesTalk
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyModels
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyRaceTalk
import io.github.omegadestroyer.pairfree.protocol.skullcandy.model
import io.github.omegadestroyer.pairfree.protocol.skullcandy.service
import io.github.omegadestroyer.pairfree.protocol.skullcandy.usesAt
import io.github.omegadestroyer.pairfree.protocol.skullcandy.usesAtSerial
import io.github.omegadestroyer.pairfree.protocol.skullcandy.usesBes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.io.IOException

/**
 * Skullcandy's earbuds and headphones, from Skullcandy's own app (5.0.6, docs/skullcandy/PROTOCOL.md). The products are told by their Bluetooth name; the chip family and
 * the protocol follow from the app's own table: the Grind, Grind Fuel, Push Active and Crusher ANC 2 speak AT lines over their own serial service and 32 more Airoha models over
 * the standard serial port ([SkullcandyAtSession]); the Airoha models with custom RACE settings or a custom equaliser also get a second channel ([SkullcandyRaceTalk]).
 * The others are found but have no session yet.
 */
object SkullcandySpec : BrandSpec(Brand.SKULLCANDY) {
    override val officialApp = "Skullcandy"

    override val offersRecording: Boolean get() = false

    override fun paired(address: String, name: String, uuids: List<String>): Paired? =
        SkullcandyModels.fromName(name)?.let { p -> Paired(address, name, Brand.SKULLCANDY, service = p.service, model = p.name) }

    override fun model(device: Paired, state: EarbudState): Model? = SkullcandyModels.model(device.model ?: device.name)

    override fun driver(device: Paired, host: DriverHost): BrandDriver =
        SkullcandyModels.fromName(device.model ?: device.name).let { p ->
            when {
                p?.usesBes == true -> SkullcandyBesDriver(host, device)
                p?.usesRpc == true -> SkullcandyRpcDriver(host, device)
                p?.usesRail == true -> SkullcandyRailDriver(host, device)
                else -> SkullcandyDriver(host, device)
            }
        }

    /**
     * The AT channel, and for the models with settings that only Skullcandy's RACE channel carries (multipoint, wear detection, bass boost, the custom equaliser) a second serial channel
     * on the Airoha service the device lists (docs/skullcandy/PROTOCOL.md sections 9.4, 11 and 13). A second channel that does not open leaves the first as it is.
     */
    override suspend fun open(env: OpenEnv, device: Paired, driver: BrandDriver): ControlLink {
        val main = super.open(env, device, driver)
        val d = driver as? RaceSide ?: return main
        val product = SkullcandyModels.fromName(device.model ?: device.name) ?: return main
        // only the models the AT and RPC conversations serve get a RACE one beside it (the Rail and the rest have no session yet)
        if (!(product.usesAt || product.usesAtSerial || product.usesRpc) || !SkullcandyRaceTalk(product).hasRaceFeatures) return main
        for (uuid in RACE_SERVICES) {
            try {
                env.note("RACE channel $uuid")
                d.raceLink = env.hold(env.transport.serial(device.address, uuid, env.linkLog)).apply { connect() }
                break
            } catch (e: IOException) {
                env.note("no RACE channel on $uuid: ${e.message}")
            }
        }
        return main
    }

    override val mayHaveNoise: Boolean get() = true
}

/** The AT conversation ([SkullcandyAtSession]) and, beside it, the RACE one ([SkullcandyRaceTalk]); the models no session speaks yet get no talk, so the channel stays quiet. */
/** A driver that keeps a second serial channel beside its own, for Skullcandy's Airoha RACE commands ([SkullcandySpec.open] fills it). */
interface RaceSide {
    var raceLink: ControlLink?
}

class SkullcandyDriver(host: DriverHost, private val device: Paired) : TalkDriver<String, SkullcandyAtSession>(host), RaceSide {
    val session: SkullcandyAtSession? get() = talk

    /** The second serial channel, for Skullcandy's custom RACE commands and the equaliser; set by [SkullcandySpec.open], null when the model has none or it did not open. */
    override var raceLink: ControlLink? = null
    private var raceTalk: SkullcandyRaceTalk? = null
    private var raceConversation: Conversation<RacePacket>? = null
    private val raceKeys = setOf(SkullcandyRaceTalk.MULTIPOINT, SkullcandyRaceTalk.WEAR_DETECT, SkullcandyRaceTalk.BASS_BOOST, SkullcandyRaceTalk.ANC_LEVEL)
    private var lastMode: String? = null

    /** The hearing mode in force, from the AT conversation's state. */
    private fun modeOf(at: SkullcandyAtSession): String? = at.state.number(Command.ANC_MODE)?.let { Command.ANC_MODE.labels[it] }

    override fun talk(opened: ControlLink): SkullcandyAtSession? {
        val product = SkullcandyModels.fromName(device.model ?: device.name)?.takeIf { it.usesAt || it.usesAtSerial } ?: return null
        val at = SkullcandyAtSession(product)
        val link = raceLink
        if (link != null) {
            val race = SkullcandyRaceTalk(product)
            raceTalk = race
            race.mode = { modeOf(at) }
            val wire = object : TalkWire {
                override suspend fun send(frame: ByteArray) = link.send(frame)
                override fun reads(): Flow<ByteArray> = link.reads()
                override fun close() = link.close()
            }
            val running = Conversation(race, wire, host.log, changed = { publish(at) }, fed = { publish(at) })
            raceConversation = running
            host.launch { running.run() }
        }
        return at
    }

    /** The AT rows and the RACE rows together. */
    override fun publish(talk: SkullcandyAtSession) {
        val race = raceTalk
        // The RACE gain is the strength of the mode in force: read it again when the mode changes.
        val m = modeOf(talk)
        if (race != null && race.hasGain && m != lastMode && (m == "anc" || m == "transparency")) {
            lastMode = m
            raceConversation?.send(listOf(race.gainRead()), null)
        } else if (m != lastMode) {
            lastMode = m
        }
        show(talk.state, talk.groups + race?.groups.orEmpty())
    }

    override fun changeSetting(change: SettingChange) {
        val toRace = change.key in raceKeys || (change.key == SkullcandyRaceTalk.TRANSPARENCY_LEVEL && talk?.hasTransparencyLevel != true)
        if (!toRace) return super.changeSetting(change)
        val race = raceTalk ?: return
        val running = raceConversation ?: return
        val out = race.change(change) ?: return
        if (!host.connected) return
        val reads = race.takeReadBacks()
        running.send(out, null)
        if (reads.isNotEmpty()) host.launch { delay(EarbudsConnection.READ_BACK_MS); running.send(reads) }
        talk?.let { publish(it) }
    }

    /** The custom curve's first frames go through the RACE conversation; the later steps follow as the earbuds answer ([SkullcandyRaceTalk.takeOutbox]). */
    override fun sendEq(frames: List<ByteArray>) {
        val running = raceConversation ?: return
        if (!host.connected) return
        val parser = RaceParser()
        running.send(frames.flatMap { parser.feed(it) }, EQ_WRITE)
    }

    /** The custom equaliser, once the earbuds have named their chip. */
    override fun eqSource(device: Paired, model: Model?): EqSource? {
        val race = raceTalk?.takeIf { it.hasCustomEq && it.chipKnown } ?: return null
        val band = SkullcandyBandEq(race)
        val target = BandEqTarget(band)
        return EqSource(race to band.eqBandsHz, target.layout, target) { current -> band.eqReport?.let { target.reportedAs(it, current) } }
    }

    override fun report(info: ReportInfo): String? {
        val s = session ?: return null
        return buildString {
            reportHead(info)
            appendLine("Brand:           Skullcandy (AT)")
            appendLine("Model:           ${s.product.name}")
            appendLine("Chip family:     ${s.product.chip}")
            appendLine("RACE channel:    ${if (raceTalk == null) "none" else "open"}")
            reportApp(info)
            appendLine()
            appendLine("The last answer to each line PairFree sent (line, then the reply lines):")
            reportReplies(s.replies)
        }
    }
}

/** The BES models' TOTA conversation ([SkullcandyBesTalk]) over their serial service: the handshake, then the model's own settings and its equaliser, which the earbuds report. */
class SkullcandyBesDriver(host: DriverHost, private val device: Paired) : TalkDriver<BesMessage, SkullcandyBesTalk>(host) {
    override fun talk(opened: ControlLink): SkullcandyBesTalk? =
        SkullcandyModels.fromName(device.model ?: device.name)?.takeIf { it.usesBes }?.let { SkullcandyBesTalk(it) }

    override fun publish(talk: SkullcandyBesTalk) {
        show(talk.state, talk.groups)
        eqIs(talk.eq)
    }

    /** An equaliser write: payloads the talk built, each sent as a message of its own, a newer curve replacing an older one still waiting. */
    override fun sendEq(frames: List<ByteArray>) {
        val talk = talk ?: return
        send(talk, frames.map { talk.messageOf(it) }, reads = emptyList(), replaces = EQ_WRITE)
    }

    /** The equaliser once the earbuds have reported their custom curve's bands (or at once for a model with presets only). */
    override fun eqSource(device: Paired, model: Model?): EqSource? {
        val talk = talk?.takeIf { it.hasEq && it.curveKnown } ?: return null
        val band = SkullcandyBesBandEq(talk)
        val target = BandEqTarget(band)
        return EqSource(talk to band.eqBandsHz, target.layout, target) { current -> band.eqReport?.let { target.reportedAs(it, current) } }
    }

    override fun report(info: ReportInfo): String? {
        val t = talk ?: return null
        return buildString {
            reportHead(info)
            appendLine("Brand:           Skullcandy (BES TOTA)")
            appendLine("Model:           ${t.product.name}")
            appendLine("Key agreed:      ${if (t.secured) "yes" else if (t.refused) "refused" else "no"}")
            reportApp(info)
            appendLine()
            appendLine("The last decrypted reply to each command PairFree sent (command, then the reply):")
            reportReplies(t.replies)
        }
    }
}
