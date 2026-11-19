package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.EarbudsConnection
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.protocol.Conversation
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.TalkWire
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.skullcandy.RACE_SERVICES
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyAtSession
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyModels
import io.github.omegadestroyer.pairfree.protocol.skullcandy.model
import io.github.omegadestroyer.pairfree.protocol.skullcandy.service
import io.github.omegadestroyer.pairfree.protocol.skullcandy.usesAt
import io.github.omegadestroyer.pairfree.protocol.skullcandy.usesAtSerial
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.io.IOException

/**
 * Skullcandy's earbuds and headphones, from Skullcandy's own app (5.0.6, docs/skullcandy/PROTOCOL.md). The products are told by their Bluetooth name; the chip family and
 * the protocol follow from the app's own table: the Grind, Grind Fuel, Push Active and Crusher ANC 2 speak AT lines over their own serial service ([SkullcandyAtSession]).
 * The others are found but have no session yet.
 */
object SkullcandySpec : BrandSpec(Brand.SKULLCANDY) {
    override val officialApp = "Skullcandy"

    override val offersRecording: Boolean get() = false

    override fun paired(address: String, name: String, uuids: List<String>): Paired? =
        SkullcandyModels.fromName(name)?.let { p -> Paired(address, name, Brand.SKULLCANDY, service = p.service, model = p.name) }

    override fun model(device: Paired, state: EarbudState): Model? = SkullcandyModels.model(device.model ?: device.name)

    override fun driver(device: Paired, host: DriverHost): BrandDriver = SkullcandyDriver(host, device)

    /**
     * The AT channel, and for the models with settings that only Skullcandy's custom RACE commands carry (multipoint, wear detection, bass boost) a second serial channel on the Airoha
     * service the device lists (docs/skullcandy/PROTOCOL.md sections 9.4 and 11). A second channel that does not open leaves the first as it is.
     */
    override suspend fun open(env: OpenEnv, device: Paired, driver: BrandDriver): ControlLink {
        val main = super.open(env, device, driver)
        val d = driver as? SkullcandyDriver ?: return main
        val product = SkullcandyModels.fromName(device.model ?: device.name) ?: return main
        if (!SkullcandyAtSession(product, withRace = true).hasRaceFeatures) return main
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

/** The AT conversation ([SkullcandyAtSession]); the models no session speaks yet get no talk, so the channel stays quiet. */
class SkullcandyDriver(host: DriverHost, private val device: Paired) : TalkDriver<String, SkullcandyAtSession>(host) {
    val session: SkullcandyAtSession? get() = talk

    /** The second serial channel, for Skullcandy's custom RACE commands; set by [SkullcandySpec.open], null when the model has none or it did not open. */
    var raceLink: ControlLink? = null
    private var raceTalk: SkullcandyAtSession? = null
    private var raceConversation: Conversation<String>? = null
    private val RACE_KEYS = setOf(SkullcandyAtSession.MULTIPOINT, SkullcandyAtSession.WEAR_DETECT, SkullcandyAtSession.BASS_BOOST)

    override fun talk(opened: ControlLink): SkullcandyAtSession? {
        val product = SkullcandyModels.fromName(device.model ?: device.name)?.takeIf { it.usesAt || it.usesAtSerial } ?: return null
        val at = SkullcandyAtSession(product)
        val link = raceLink
        if (link != null) {
            val race = SkullcandyAtSession(product, withRace = true)
            raceTalk = race
            val wire = object : TalkWire {
                override suspend fun send(frame: ByteArray) = link.send(frame)
                override fun reads(): Flow<ByteArray> = link.reads()
                override fun close() = link.close()
            }
            val running = Conversation(race, wire, host.log, changed = { publish(at) })
            raceConversation = running
            host.launch { running.run() }
        }
        return at
    }

    /** The AT rows and the RACE rows together. */
    override fun publish(talk: SkullcandyAtSession) {
        show(talk.state, talk.groups + raceTalk?.groups.orEmpty())
    }

    override fun changeSetting(change: SettingChange) {
        val at = talk
        if (change.key !in RACE_KEYS) return super.changeSetting(change)
        val race = raceTalk ?: return
        val running = raceConversation ?: return
        val out = race.change(change) ?: return
        if (!host.connected) return
        val reads = race.takeReadBacks()
        running.send(out, null)
        if (reads.isNotEmpty()) host.launch { delay(EarbudsConnection.READ_BACK_MS); running.send(reads) }
        at?.let { publish(it) }
    }

    override fun report(info: ReportInfo): String? {
        val s = session ?: return null
        return buildString {
            reportHead(info)
            appendLine("Brand:           Skullcandy (AT)")
            appendLine("Model:           ${s.product.name}")
            appendLine("Chip family:     ${s.product.chip}")
            reportApp(info)
            appendLine()
            appendLine("The last answer to each line PairFree sent (line, then the reply lines):")
            reportReplies(s.replies)
        }
    }
}
