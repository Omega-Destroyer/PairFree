package io.github.omegadestroyer.pairfree.brands

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.EnvelopeGattLink
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.bluetooth.QcyFinder
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.eq.EqTarget
import io.github.omegadestroyer.pairfree.eq.QcyBandEq
import io.github.omegadestroyer.pairfree.protocol.CapsuleBandEq
import io.github.omegadestroyer.pairfree.protocol.CapsuleWire
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.Models
import io.github.omegadestroyer.pairfree.protocol.qcy.Capsule
import io.github.omegadestroyer.pairfree.protocol.qcy.JieliWire
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyControl
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyModels
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyProtocol
import io.github.omegadestroyer.pairfree.protocol.qcy.QcySession
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyTalk
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyWire
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice
import io.github.omegadestroyer.pairfree.protocol.widget.Sub
import java.io.IOException

/** QCY (docs/qcy/PROTOCOL.md), and SOUNDPEATS' QCY-built models, which come here from [SoundpeatsSpec] with `soldAs`. */
object QcySpec : BrandSpec(Brand.QCY) {
    override val officialApp = "QCY"

    /** QCY's earbuds are always found by a scan (docs/qcy/PROTOCOL.md section 2). */
    override val needsScan: Boolean get() = true

    override fun eqNote(device: Paired): Int = R.string.eq_qcy

    // QCY's model is known for sure from the advert's vendor id, once PairFree looks for it (docs/qcy/PROTOCOL.md).
    override fun paired(address: String, name: String, uuids: List<String>): Paired =
        Paired(address, name, Brand.QCY, model = QcyModels.fromName(name).singleOrNull()?.code)

    /** By the vendor id the advert named, which the firmware line carries; before then, the name's. */
    override fun model(device: Paired, state: EarbudState): Model? =
        if (device.soldAs == Brand.SOUNDPEATS) Models.find(device.model, device.name)
        else QcyModels.model(state.firmware?.code ?: device.model ?: device.name, if (state.supports(Command.BATTERY_RIGHT)) Form.EARBUDS else Form.HEADPHONES)

    override fun driver(device: Paired, host: DriverHost): BrandDriver = QcyDriver(host, device)

    /**
     * QCY earbuds over Bluetooth LE (docs/qcy/PROTOCOL.md section 2): a scan finds their advert, which names
     * the model and the LE side to connect to. A scan that already found them is used again on a reconnect.
     * Models on JieLi's chips take their settings through JieLi's service (section 6), the others QCY's own.
     */
    @SuppressLint("MissingPermission") // only called while connecting, which needs the permission; the scan's is checked here
    override suspend fun open(env: OpenEnv, remote: BluetoothDevice, device: Paired, driver: BrandDriver): ControlLink {
        driver as QcyDriver
        val ctx = env.context ?: throw IOException("no context for Bluetooth LE")
        val found = driver.found ?: run {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) throw ScanNotAllowed()
            env.note("scanning for QCY's advert")
            // SOUNDPEATS' QCY-built models advertise under SOUNDPEATS' company id.
            val company = if (device.soldAs == Brand.SOUNDPEATS) CapsuleWire.COMPANY else QcyWire.COMPANY
            env.adapter?.let { QcyFinder(it, env.linkLog, company).find(device.address) } ?: throw NotFound()
        }
        driver.found = found
        val jieli = QcyModels.of(found.advert.vendorId)?.protocol.let { it == QcyProtocol.JIELI || it == QcyProtocol.JIELI_CUSTOM }
        val service = if (jieli) JieliWire.SERVICE else QcyWire.SERVICE
        env.note("LE (GATT) service $service")
        return env.hold(EnvelopeGattLink(ctx, found.device, java.util.UUID.fromString(service), env.linkLog)).apply { connect() }
    }

    override fun noiseOffline(device: Paired): List<NoiseChoice>? =
        // The Capsule3 Pro runs on QCY's protocol: all three modes.
        if (device.soldAs == Brand.SOUNDPEATS) {
            if (Capsule.of(Models.find(device.model, device.name))) three() else null
        } else {
            QcyModels.fromName(device.name).singleOrNull()?.takeIf { QcySession(it).sharedNoise }?.let { m ->
                // Several scenes: the scene row. One scene with a range: its strength slider. Transparency: its strength slider.
                val scenes = m.mode("cancelling")?.scenes.orEmpty()
                val cancelling = if (scenes.size > 1) scenes.map { Sub.Row("anc_scene", it.scene) } else {
                    scenes.singleOrNull()?.takeIf { it.to > it.from }?.let { s -> (1..s.to - s.from + 1).map { Sub.Step("anc_scene_level", it) } }.orEmpty()
                }
                val transparency = m.mode("transparency")?.takeIf { it.to > it.from }?.let { t -> (1..t.to - t.from + 1).map { Sub.Step("transparency_strength", it) } }.orEmpty()
                three(cancelling, transparency)
            }
        }

    override fun eqOffline(device: Paired): Pair<EqLayout, EqTarget>? =
        if (device.soldAs == Brand.SOUNDPEATS) Models.find(device.model, device.name)?.eq?.let { BandEqTarget(CapsuleBandEq(it)) }?.let { it.layout to it } else null

    override fun mayHaveEq(device: Paired): Boolean = device.soldAs == null
}

/** QCY earbuds and headphones ([QcyTalk]). */
class QcyDriver(host: DriverHost, private val device: Paired) : TalkDriver<ByteArray, QcyTalk>(host) {
    /** The session of the channel now or last open; for the device report and the EQ. */
    val session: QcyControl? get() = talk?.session

    /** What the scan found before the channel opened; used again on a reconnect. */
    internal var found: QcyFinder.Found? = null

    override fun talk(opened: ControlLink): QcyTalk {
        val found = found
        val capsule = Models.find(device.model, device.name)?.takeIf { device.soldAs == Brand.SOUNDPEATS && Capsule.of(it) }
        val model = capsule?.let(Capsule::qcyModel) ?: found?.let { QcyModels.of(it.advert.vendorId) }
        return QcyTalk(QcyControl.of(model, found?.advert))
    }

    /** QCY's EQ target hands over GATT messages; they go through the talk's queue. */
    override fun sendEq(frames: List<ByteArray>) {
        val talk = talk ?: return
        if (!host.connected) return
        if (send(talk, frames)) publish(talk)
    }

    /** QCY earbuds report their EQ after they connect, and it opens over the bands they report. SOUNDPEATS' QCY-built models use SOUNDPEATS' own curve. */
    override fun eqSource(device: Paired, model: Model?): EqSource? {
        if (device.soldAs == Brand.SOUNDPEATS) return model?.eq?.let { layout -> BandEqTarget(CapsuleBandEq(layout)).let { EqSource("capsule", it.layout, it) } }
        val session = session ?: return null
        val eq = session.eq ?: return null
        val band = QcyBandEq(session, eq)
        val target = BandEqTarget(band)
        return EqSource(session to eq.freqs, target.layout, target) { current -> this.session?.eq?.let { band.report(it) }?.let { target.reportedAs(it, current) } }
    }

    override fun report(info: ReportInfo): String? {
        val session = session ?: return null
        return buildString {
            reportHead(info)
            appendLine("Brand:           QCY")
            val model = session.model
            appendLine("Model:           ${model?.name ?: "not in PairFree's table"} (vendor id ${model?.code ?: "unknown"})")
            appendLine("Firmware:        ${session.firmware ?: "unknown"}")
            reportApp(info)
            appendLine()
            appendLine("The last value of each item and characteristic PairFree read (item or name: value as hex):")
            reportReplies(session.replies)
        }
    }
}
