package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.protocol.ConnectionReport
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyCChip
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyModels
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyQualcommBandEq
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyQualcommTalk
import io.github.omegadestroyer.pairfree.protocol.skullcandy.usesQualcomm
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.io.IOException

/**
 * Opens the Bluetooth LE channel of the six older Qualcomm models: Qualcomm's GAIA service on the classic address first, else on the device a scan finds (docs/skullcandy/PROTOCOL.md section 17).
 * [SkullcandySpec.open] hands the product here.
 */
internal suspend fun openSkullcandyLe(env: OpenEnv, device: Paired, names: List<String>): ControlLink {
    val transport = env.transport
    if (!transport.hasLe) throw IOException("this system has no Bluetooth LE")
    val spec = GattSpec(GattUuids(SkullcandyCChip.SERVICE, SkullcandyCChip.NOTIFY, SkullcandyCChip.WRITE, MTU))
    fun gatt(target: LeTarget): ControlLink = QualcommLink(env.hold(transport.gatt(target, spec, env.linkLog)))
    try {
        env.note("LE (GATT) on the classic address")
        return gatt(transport.at(device.address)).also { it.connect() }
    } catch (e: IOException) {
        env.log?.invoke("LE on the classic address: ${e.message}")
        env.note("failed: ${ConnectionReport.describe(e)}")
    }
    currentCoroutineContext().ensureActive()
    if (!transport.mayScan) throw ScanNotAllowed()
    val found = transport.findSkullcandy(names, device.address, env.linkLog) ?: throw NotFound()
    env.note("LE (GATT) on the address the scan found")
    return gatt(found).also { it.connect() }
}

/** The LE channel of a Qualcomm model, marked so the driver of a name two products share can tell which of them it is talking to. */
internal class QualcommLink(private val inner: ControlLink) : ControlLink by inner

/**
 * The driver of a name that a classic Airoha product and a Qualcomm LE one share (docs/skullcandy/PROTOCOL.md section 17): the classic channel is tried first, and when it does not open the LE one, so the
 * driver of whichever channel opened serves it.
 */
class SkullcandyEitherDriver(host: DriverHost, device: Paired) : BrandDriver(host), RaceSide {
    private val classic = SkullcandyDriver(host, device)
    private val le = SkullcandyQualcommDriver(host, device)
    private var active: BrandDriver = classic

    override var raceLink: ControlLink?
        get() = classic.raceLink
        set(value) {
            classic.raceLink = value
        }

    override suspend fun serve(opened: ControlLink) {
        active = if (opened is QualcommLink) le else classic
        active.serve(opened)
    }

    override fun set(command: io.github.omegadestroyer.pairfree.protocol.Command, payload: IntArray) = active.set(command, payload)

    override fun changeSetting(change: io.github.omegadestroyer.pairfree.protocol.SettingChange) = active.changeSetting(change)

    override fun sendEq(frames: List<ByteArray>) = active.sendEq(frames)

    override val fitTest: io.github.omegadestroyer.pairfree.protocol.FitTest? get() = active.fitTest

    override fun startFitTest(keepAnc: Boolean) = active.startFitTest(keepAnc)

    override fun factoryResetConfirmedByUser() = active.factoryResetConfirmedByUser()

    override fun eqSource(device: Paired, model: Model?): EqSource? = active.eqSource(device, model)

    override fun report(info: ReportInfo): String? = active.report(info)
}

private const val MTU = 64

/** The six older Qualcomm models over their own C-Chip frames ([SkullcandyQualcommTalk]): the batteries, the Indy models' ambient mode and the three equaliser presets. */
class SkullcandyQualcommDriver(host: DriverHost, private val device: Paired) : TalkDriver<ByteArray, SkullcandyQualcommTalk>(host) {
    override fun talk(opened: ControlLink): SkullcandyQualcommTalk? =
        (SkullcandyModels.leFromName(device.model ?: device.name) ?: SkullcandyModels.fromName(device.model ?: device.name)?.takeIf { it.usesQualcomm })?.let { SkullcandyQualcommTalk(it) }

    override fun publish(talk: SkullcandyQualcommTalk) {
        show(talk.state, talk.groups)
        eqIs(talk.eq)
    }

    override fun sendEq(frames: List<ByteArray>) {
        val t = talk ?: return
        send(t, frames, reads = t.takeReadBacks(), replaces = EQ_WRITE)
    }

    /** The three presets, once the earbuds have said which is in force. */
    override fun eqSource(device: Paired, model: Model?): EqSource? {
        val t = talk?.takeIf { it.hasEq && it.eqPresetName != null } ?: return null
        val band = SkullcandyQualcommBandEq(t)
        val target = BandEqTarget(band)
        return EqSource(t to t.eqPresetName, target.layout, target) { current -> band.eqReport?.let { target.reportedAs(it, current) } }
    }

    override fun report(info: ReportInfo): String? {
        val t = talk ?: return null
        return buildString {
            reportHead(info)
            appendLine("Brand:           Skullcandy (Qualcomm, C-Chip over LE)")
            appendLine("Model:           ${t.product.name}")
            reportApp(info)
            appendLine()
            appendLine("The last payload to each frame type PairFree asked (type, then the bytes):")
            reportReplies(t.replies)
        }
    }
}
