package io.github.omegadestroyer.pairfree.brands

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.GattLink
import io.github.omegadestroyer.pairfree.bluetooth.JblFinder
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.bluetooth.SppLink
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.eq.EqTarget
import io.github.omegadestroyer.pairfree.eq.JblBandEq
import io.github.omegadestroyer.pairfree.protocol.ConnectionReport
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.Wire
import io.github.omegadestroyer.pairfree.protocol.jbl.JblAdvert
import io.github.omegadestroyer.pairfree.protocol.jbl.JblFrame
import io.github.omegadestroyer.pairfree.protocol.jbl.JblModel
import io.github.omegadestroyer.pairfree.protocol.jbl.JblModels
import io.github.omegadestroyer.pairfree.protocol.jbl.JblP4
import io.github.omegadestroyer.pairfree.protocol.jbl.JblP4Packet
import io.github.omegadestroyer.pairfree.protocol.jbl.JblRace
import io.github.omegadestroyer.pairfree.protocol.jbl.JblSession
import io.github.omegadestroyer.pairfree.protocol.jbl.JblTalk
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** JBL (docs/jbl/PROTOCOL.md). */
object JblSpec : BrandSpec(Brand.JBL) {
    override val officialApp = "JBL Headphones"

    /** JBL's LE models may need a scan to be found (docs/jbl/PROTOCOL.md section 3). */
    override val needsScan: Boolean get() = true

    override fun eqNote(device: Paired): Int = R.string.eq_jbl

    override fun paired(address: String, name: String, uuids: List<String>): Paired? =
        JblModels.fromName(name)?.let { Paired(address, name, Brand.JBL, model = it.name) }

    /** By the model its product id names, which the firmware line carries; before then, its name. */
    override fun model(device: Paired, state: EarbudState): Model? = JblModels.model(state.firmware?.code ?: device.model ?: device.name)

    override fun driver(device: Paired, host: DriverHost): BrandDriver = JblDriver(host, device)

    internal fun modelOf(device: Paired): JblModel? = JblModels.fromName(device.name) ?: JblModels.ALL.firstOrNull { it.name == device.model }

    /**
     * The classic serial channel, or for JBL's LE models GATT, first on the classic address, where the Tune Flex 2's LE
     * side answers (JblTray), else on the LE device a scan finds (docs/jbl/PROTOCOL.md section 3).
     */
    @SuppressLint("MissingPermission") // only called while connecting, which needs the permission; the scan's is checked here
    override suspend fun open(env: OpenEnv, remote: BluetoothDevice, device: Paired, driver: BrandDriver): ControlLink {
        val model = modelOf(device)
        val ctx = env.context
        // JBL's Airoha models have Airoha's own serial channel and LE service (docs/jbl/PROTOCOL.md section 10).
        val airoha = model?.family == JblModel.Family.AIROHA
        // Airoha earbuds paired over LE Audio are an LE device: JBL's app reaches them over Airoha's LE service at that
        // address, whatever the model's usual connection (docs/jbl/PROTOCOL.md section 10).
        if (airoha && ctx != null && remote.type == BluetoothDevice.DEVICE_TYPE_LE) {
            env.note("Airoha's LE service on the LE address")
            return env.hold(GattLink(ctx, remote, env.linkLog, uuids = GattLink.AIROHA)).apply { connect() }
        }
        if (model?.gatt != true || ctx == null) {
            val uuid = if (airoha) JblRace.SPP_UUID else device.service ?: Wire.SPP_UUID
            env.note("serial channel $uuid")
            return env.hold(SppLink(remote, env.linkLog, uuid)).apply { connect() }
        }
        // JBL's protocol 4 security modes add connection steps (docs/jbl/PROTOCOL.md section 7); modes 1 and 3 use
        // the raw characteristic, and mode 3 writes the phone's name checksum to it first.
        val rawNotify = model.security == 1 || model.security == 3
        val preamble = if (model.security == 3) hostNameCrc(env.adapter) else null
        val uuids = if (airoha) GattLink.AIROHA else GattLink.HARMAN
        fun gatt(d: BluetoothDevice) = env.hold(GattLink(ctx, d, env.linkLog, rawNotify = rawNotify, rawPreamble = preamble, uuids = uuids))
        try {
            env.note("LE (GATT) on the classic address, security mode ${model.security}")
            return gatt(remote).apply { connect() }
        } catch (e: IOException) {
            env.log?.invoke("LE on the classic address: ${e.message}")
            env.note("failed: ${ConnectionReport.describe(e)}")
        }
        currentCoroutineContext().ensureActive()   // not after a disconnect closed the first try
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) throw ScanNotAllowed()
        val found = env.adapter?.let { JblFinder(it, env.linkLog).find(model, device.address) } ?: throw IOException("not found over LE")
        env.note("LE (GATT) on the address the scan found")
        return gatt(found).apply { connect() }
    }

    /** The checksum of this phone's Bluetooth name, as JBL's security mode 3 writes it (JblAdvert's CRC-16/ARC, low byte first). */
    @SuppressLint("MissingPermission") // only called with the permission
    private fun hostNameCrc(adapter: BluetoothAdapter?): ByteArray? {
        val name = try {
            adapter?.name
        } catch (_: SecurityException) {
            null
        } ?: return null
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

    override fun talk(opened: ControlLink): JblTalk? =
        JblSpec.modelOf(device)?.let { JblTalk(it, notifications = opened is GattLink, mtu = (opened as? GattLink)?.mtu) }

    /**
     * JBL's EQ target has its session build the write; its frames go through the talk's queue as the session built
     * them (the 41 transfer's header and parts are not frames the reader could rebuild from the bytes).
     */
    override fun sendEq(frames: List<ByteArray>) {
        val talk = talk ?: return
        if (!host.connected) return
        if (send(talk, talk.session.takeEqWrite())) publish(talk)
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
            val id = session.productId?.let { "%04X".format(it) } ?: "unknown"
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
