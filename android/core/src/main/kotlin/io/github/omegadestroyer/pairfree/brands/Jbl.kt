package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.eq.EqTarget
import io.github.omegadestroyer.pairfree.eq.JblBandEq
import io.github.omegadestroyer.pairfree.protocol.ConnectionReport
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.Wire
import io.github.omegadestroyer.pairfree.protocol.jbl.AvneraModels
import io.github.omegadestroyer.pairfree.protocol.jbl.AvneraPacket
import io.github.omegadestroyer.pairfree.protocol.jbl.AvneraTalk
import io.github.omegadestroyer.pairfree.protocol.jbl.JblAdvert
import io.github.omegadestroyer.pairfree.protocol.jbl.JblEqSpec
import io.github.omegadestroyer.pairfree.protocol.jbl.JblFrame
import io.github.omegadestroyer.pairfree.protocol.jbl.JblModel
import io.github.omegadestroyer.pairfree.protocol.jbl.JblModels
import io.github.omegadestroyer.pairfree.protocol.jbl.JblP4
import io.github.omegadestroyer.pairfree.protocol.jbl.JblP4Packet
import io.github.omegadestroyer.pairfree.protocol.jbl.JblRace
import io.github.omegadestroyer.pairfree.protocol.jbl.JblSession
import io.github.omegadestroyer.pairfree.protocol.jbl.JblTalk
import io.github.omegadestroyer.pairfree.protocol.jbl.JblWallClock
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice
import kotlinx.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** JBL (docs/jbl/PROTOCOL.md). */
object JblSpec : BrandSpec(Brand.JBL) {
    override val officialApp = "JBL Headphones"

    /** JBL's LE models may need a scan to be found (docs/jbl/PROTOCOL.md section 3). */
    override val needsScan: Boolean get() = true

    override fun eqNote(device: Paired): EqNote = EqNote.JBL

    /** PersoniFi's test, on the models whose dashboard has it and whose EQ PairFree can write its curve to. */
    override fun ownHearingTest(device: Paired): OwnHearingTest? =
        modelOf(device)?.takeIf { "personifi" in it.extras && it.eq?.path.let { p -> p == JblEqSpec.Path.A2 || p == JblEqSpec.Path.A2_Q10 || p == JblEqSpec.Path.P4 || p == JblEqSpec.Path.CLUB } }
            ?.let { OwnHearingTest.Jbl("personifi_open" in it.extras) }

    override fun paired(address: String, name: String, uuids: List<String>): Paired? =
        JblModels.fromName(name)?.let { Paired(address, name, Brand.JBL, model = it.name) }
            // The Everest Elite range and the Reflect Aware: Avnera's LightX over the standard serial port (docs/jbl/PROTOCOL.md section 12).
            ?: AvneraModels.fromName(name)?.let { Paired(address, name, Brand.JBL, model = it.name) }

    /** By the model its product id names, which the firmware line carries; before then, its name. */
    override fun model(device: Paired, state: EarbudState): Model? =
        JblModels.model(state.firmware?.code ?: device.model ?: device.name) ?: AvneraModels.model(device.model ?: device.name)

    override fun driver(device: Paired, host: DriverHost): BrandDriver =
        if (JblModels.fromName(device.name) == null && AvneraModels.fromName(device.name) != null) AvneraDriver(host, device) else JblDriver(host, device)

    internal fun modelOf(device: Paired): JblModel? = JblModels.fromName(device.name) ?: JblModels.ALL.firstOrNull { it.name == device.model }

    /**
     * The classic serial channel, or for JBL's LE models GATT, first on the classic address, where the Tune Flex 2's LE
     * side answers (JblTray), else on the LE device a scan finds (docs/jbl/PROTOCOL.md section 3).
     */
    override suspend fun open(env: OpenEnv, device: Paired, driver: BrandDriver): ControlLink {
        val model = modelOf(device)
        val transport = env.transport
        // JBL's Airoha models have Airoha's own serial channel and LE service (docs/jbl/PROTOCOL.md section 10).
        val airoha = model?.family == JblModel.Family.AIROHA
        // Airoha earbuds paired over LE Audio are an LE device: JBL's app reaches them over Airoha's LE service at that
        // address, whatever the model's usual connection (docs/jbl/PROTOCOL.md section 10).
        if (airoha && transport.hasLe && transport.isLeOnly(device.address)) {
            env.note("Airoha's LE service on the LE address")
            return env.hold(transport.gatt(transport.at(device.address), GattSpec(GattUuids.AIROHA), env.linkLog)).apply { connect() }
        }
        if (model?.gatt != true || !transport.hasLe) {
            val uuid = if (airoha) JblRace.SPP_UUID else device.service ?: Wire.SPP_UUID
            env.note("serial channel $uuid")
            return env.hold(transport.serial(device.address, uuid, env.linkLog)).apply { connect() }
        }
        // JBL's protocol 4 security modes add connection steps (docs/jbl/PROTOCOL.md section 7); modes 1 and 3 use
        // the raw characteristic, and mode 3 writes the phone's name checksum to it first.
        val rawNotify = model.security == 1 || model.security == 3
        val preamble = if (model.security == 3) hostNameCrc(transport.hostName()) else null
        val spec = GattSpec(if (airoha) GattUuids.AIROHA else GattUuids.HARMAN, rawNotify = rawNotify, rawPreamble = preamble)
        fun gatt(target: LeTarget) = env.hold(transport.gatt(target, spec, env.linkLog))
        try {
            env.note("LE (GATT) on the classic address, security mode ${model.security}")
            return gatt(transport.at(device.address)).apply { connect() }
        } catch (e: IOException) {
            env.log?.invoke("LE on the classic address: ${e.message}")
            env.note("failed: ${ConnectionReport.describe(e)}")
        }
        currentCoroutineContext().ensureActive()   // not after a disconnect closed the first try
        if (!transport.mayScan) throw ScanNotAllowed()
        val found = transport.findJbl(model, device.address, env.linkLog) ?: throw IOException("not found over LE")
        env.note("LE (GATT) on the address the scan found")
        return gatt(found).apply { connect() }
    }

    /** The checksum of this phone's Bluetooth name, as JBL's security mode 3 writes it (JblAdvert's CRC-16/ARC, low byte first). */
    private fun hostNameCrc(name: String?): ByteArray? {
        if (name == null) return null
        val crc = JblAdvert.crc16(name.encodeToByteArray())
        return JblP4Packet.set(JblP4.FEATURE_HOST_NAME_CRC to byteArrayOf(crc.toByte(), (crc shr 8).toByte())).bytes()
    }

    override fun noiseOffline(device: Paired): List<NoiseChoice>? =
        JblModels.fromName(device.name)?.let { if (JblSession(it).sharedNoise) three() else emptyList() }

    override fun eqOffline(device: Paired): Pair<EqLayout, EqTarget>? =
        JblModels.fromName(device.name)?.takeIf { it.eq?.written == true }?.let { BandEqTarget(JblBandEq(JblSession(it))) }?.let { it.layout to it }
}

/** JBL headphones and earbuds ([JblTalk]). */
class JblDriver(host: DriverHost, private val device: Paired) : TalkDriver<JblFrame, JblTalk>(host) {
    /** The session of the channel now or last open; for the device report and the EQ. */
    val session: JblSession? get() = talk?.session

    private fun clock(): JblWallClock? = host.localTime()?.let { JblWallClock(it.year, it.month, it.day, it.weekday, it.hour, it.minute, it.second) }

    override fun talk(opened: ControlLink): JblTalk? =
        JblSpec.modelOf(device)?.let { JblTalk(it, notifications = opened is GattChannel, mtu = (opened as? GattChannel)?.mtu, clock = ::clock) }

    /**
     * JBL's EQ target has its session build the write; its frames go through the talk's queue as the session built
     * them (the 41 transfer's header and parts are not frames the reader could rebuild from the bytes).
     */
    override fun sendEq(frames: List<ByteArray>) {
        val talk = talk ?: return
        if (!host.connected) return
        if (send(talk, talk.session.takeEqWrite())) publish(talk)
    }

    /** The hearing test's hold on the channel: each call has the session build its messages, which go out through the talk's queue. */
    fun personifi(): PersonifiHost? {
        val talk = talk ?: return null
        val session = talk.session
        if (!session.personifiTest) return null
        fun go() {
            if (host.connected && send(talk, session.takeEqWrite())) publish(talk)
        }
        return object : PersonifiHost {
            override val openFit get() = session.personifiOpenFit
            override fun begin() { session.personifiBegin(); go() }
            override fun tone(left: Boolean, hz: Int) { session.personifiTone(left, hz); go() }
            override val readyHz get() = session.personifiReadyHz
            override val status get() = session.personifiStatus
            override fun apply(curve: io.github.omegadestroyer.pairfree.protocol.jbl.JblPersonifi.Curve): Boolean = session.personifiApply(curve).also { if (it) go() }
            override fun end() { session.personifiEnd(); go() }
        }
    }

    /** JBL models whose EQ PairFree writes, once the ones that report their EQ have reported it. */
    override fun eqSource(device: Paired, model: Model?): EqSource? {
        val session = session ?: return null
        val spec = session.model.eq
        if (spec?.written != true || (spec.reports && session.eq == null)) return null
        val band = JblBandEq(session)
        val target = BandEqTarget(band)
        return EqSource(session, target.layout, target) { current -> this.session?.eq?.let { band.report(it) }?.let { target.reportedAs(it, current) } }
    }

    override fun report(info: ReportInfo): String? {
        val session = session ?: return null
        return buildString {
            reportHead(info)
            appendLine("Brand:           JBL")
            val id = session.productId?.let { it.hexText(4, upper = true) } ?: "unknown"
            appendLine("Model:           ${session.model.name} (product $id, ${if (session.model.gatt) "Bluetooth LE" else "serial channel"})")
            appendLine("Firmware:        ${session.firmware ?: "unknown"}")
            if (session.model.family == JblModel.Family.AIROHA) {
                appendLine("Airoha chip:     ${session.chipName ?: "unknown"}")
                appendLine("Model name:      ${session.modelName ?: "unknown"}")
            }
            reportApp(info)
            appendLine()
            appendLine("The last answer to each of PairFree's reads (command, with the status type or feature id:")
            appendLine("payload as hex; the device info's name and address are blanked):")
            reportReplies(session.replies)
        }
    }
}

/** The Everest Elite range and the Reflect Aware ([AvneraTalk]): one conversation of Avnera's own packets, every setting a row. */
class AvneraDriver(host: DriverHost, private val device: Paired) : TalkDriver<AvneraPacket, AvneraTalk>(host) {
    override fun talk(opened: ControlLink): AvneraTalk? = AvneraModels.fromName(device.name)?.let { AvneraTalk(it) }

    override fun report(info: ReportInfo): String? {
        val talk = talk ?: return null
        return buildString {
            reportHead(info)
            appendLine("Brand:           JBL (Avnera platform)")
            appendLine("Model:           ${talk.model.name}")
            reportApp(info)
            appendLine()
            appendLine("The last answer to each of PairFree's reads (the key, then the buffer as hex):")
            reportReplies(talk.replies.mapKeys { it.key.toString() })
        }
    }
}
