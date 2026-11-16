package io.github.omegadestroyer.pairfree.brands

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.protocol.ConnectionReport
import io.github.omegadestroyer.pairfree.bluetooth.GattLink
import io.github.omegadestroyer.pairfree.bluetooth.SennheiserFinder
import io.github.omegadestroyer.pairfree.protocol.sennheiser.SennheiserBle
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.eq.SennheiserBandEq
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.sennheiser.Gaia2Models
import io.github.omegadestroyer.pairfree.protocol.sennheiser.Gaia2Session
import io.github.omegadestroyer.pairfree.protocol.sennheiser.Gaia2Wire
import io.github.omegadestroyer.pairfree.protocol.sennheiser.NautilusModels
import io.github.omegadestroyer.pairfree.protocol.sennheiser.NautilusSession
import io.github.omegadestroyer.pairfree.protocol.sennheiser.NautilusWire
import io.github.omegadestroyer.pairfree.protocol.sennheiser.SennheiserCommands
import io.github.omegadestroyer.pairfree.protocol.sennheiser.SennheiserEqSource
import io.github.omegadestroyer.pairfree.protocol.sennheiser.SennheiserModels
import io.github.omegadestroyer.pairfree.protocol.sennheiser.SennheiserSession
import io.github.omegadestroyer.pairfree.protocol.sennheiser.SennheiserWire
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice

/**
 * Sennheiser's GAIA v3 products (docs/sennheiser/PROTOCOL.md): the Accentum family, CX 200/Plus/500, Momentum 4, Momentum Sport and Momentum True Wireless 3 and 4
 * from Smart Control 4.9.4, and the Momentum 5, Momentum True Wireless 5, HDB 630 and HDR 275 from Smart Control Plus 1.6.3; and the GAIA v2 headphones Smart Control opens
 * over the serial port (CX 350BT, HD 250BT to HD 458BT, IE 80S BT). Not tried on any of them.
 */
object SennheiserSpec : BrandSpec(Brand.SENNHEISER) {
    override val officialApp = "Sennheiser Smart Control"

    override val offersRecording: Boolean get() = false

    private fun bleOf(device: Paired): SennheiserBle? = SennheiserModels.ALL.firstOrNull { it.name == device.model }?.ble ?: Gaia2Models.ALL.firstOrNull { it.name == device.model }?.bleUuids

    override fun needsScan(device: Paired): Boolean = bleOf(device) != null

    /**
     * The serial port, or for the products Smart Control opens only over Bluetooth LE a GATT channel: first on the classic address, else on the device a scan finds
     * (docs/sennheiser/PROTOCOL.md section 6). The link runs Sennheiser's own bonding steps when the earbuds are not bonded for LE.
     */
    @SuppressLint("MissingPermission") // only called while connecting, which needs the permission; the scan's is checked here
    override suspend fun open(env: OpenEnv, remote: BluetoothDevice, device: Paired, driver: BrandDriver): ControlLink {
        val ble = bleOf(device)
        val ctx = env.context
        if (ble == null || ctx == null) return super.open(env, remote, device, driver)
        val uuids = GattLink.Uuids(java.util.UUID.fromString(ble.service), java.util.UUID.fromString(ble.notify), java.util.UUID.fromString(ble.write), 517)
        val pairing = GattLink.Pairing(java.util.UUID.fromString(ble.pairing), java.util.UUID.fromString(ble.data))
        fun gatt(d: BluetoothDevice) = env.hold(GattLink(ctx, d, env.linkLog, uuids = uuids, pairing = pairing))
        try {
            env.note("LE (GATT) on the classic address")
            return gatt(remote).apply { connect() }
        } catch (e: IOException) {
            env.log?.invoke("LE on the classic address: ${e.message}")
            env.note("failed: ${ConnectionReport.describe(e)}")
        }
        currentCoroutineContext().ensureActive()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) throw ScanNotAllowed()
        val names = (SennheiserModels.ALL.firstOrNull { it.name == device.model }?.names ?: Gaia2Models.ALL.firstOrNull { it.name == device.model }?.names).orEmpty() + device.name
        val found = env.adapter?.let { SennheiserFinder(it, env.linkLog).find(names, device.address) } ?: throw NotFound()
        env.note("LE (GATT) on the address the scan found")
        return gatt(found).apply { connect() }
    }

    override fun paired(address: String, name: String, uuids: List<String>): Paired? =
        SennheiserModels.fromName(name)?.let { Paired(address, name, Brand.SENNHEISER, SennheiserWire.CLASSIC_UUID, it.name) }
            ?: Gaia2Models.fromName(name)?.let { Paired(address, name, Brand.SENNHEISER, Gaia2Wire.SPP_UUID, it.name) }
            ?: NautilusModels.fromName(name)?.let { Paired(address, name, Brand.SENNHEISER, NautilusWire.SPP_UUID, it.name) }
            ?: uuids.firstOrNull { it.equals(SennheiserWire.CLASSIC_UUID, ignoreCase = true) }?.let { Paired(address, name, Brand.SENNHEISER, SennheiserWire.CLASSIC_UUID, null) }

    override fun model(device: Paired, state: EarbudState): Model? = SennheiserModels.model(device.name) ?: SennheiserModels.ALL.firstOrNull { it.name == device.model }?.let { SennheiserModels.model(it.names.first()) }
        ?: Gaia2Models.model(device.name) ?: Gaia2Models.ALL.firstOrNull { it.name == device.model }?.let { Gaia2Models.model(it.names.first()) }
        ?: NautilusModels.model(device.name) ?: NautilusModels.ALL.firstOrNull { it.name == device.model }?.let { NautilusModels.model(it.names.first()) }

    override fun driver(device: Paired, host: DriverHost): BrandDriver = SennheiserDriver(host, device)

    /** A model with a product file in Smart Control has noise control by what the file lists; one without (the Momentum 5...) only by what it answers. */
    override fun noiseOffline(device: Paired): List<NoiseChoice>? = if (Gaia2Models.ALL.any { it.name == device.model } || NautilusModels.ALL.any { it.name == device.model }) emptyList() else SennheiserModels.ALL.firstOrNull { it.name == device.model }?.let { m ->
        val properties = m.properties ?: return null
        if (SennheiserCommands.ANC_STATUS in properties && SennheiserCommands.TRANSPARENCY_STATUS in properties) three() else emptyList()
    }

    override val mayHaveNoise: Boolean get() = true

    override fun eqNote(device: Paired): Int = R.string.eq_sennheiser
}

/** Sennheiser products ([SennheiserSession] on GAIA v3, [Gaia2Session] on GAIA v2): one command at a time over RFCOMM, each row once its read answered. */
class SennheiserDriver(host: DriverHost, private val device: Paired) : TalkDriver<ByteArray, SessionTalk<ByteArray>>(host) {
    /** The session of the channel now or last open, for GAIA v3 products; for the device report. */
    val session: SennheiserSession? get() = talk as? SennheiserSession

    private val legacy get() = Gaia2Models.ALL.firstOrNull { it.name == device.model } ?: Gaia2Models.fromName(device.name)

    private val nautilus get() = NautilusModels.ALL.firstOrNull { it.name == device.model } ?: NautilusModels.fromName(device.name)

    override fun talk(opened: ControlLink): SessionTalk<ByteArray> {
        val v3 = SennheiserModels.ALL.firstOrNull { it.name == device.model } ?: SennheiserModels.fromName(device.name)
        if (v3 == null) legacy?.let { return Gaia2Session(it, ble = it.ble) }
        if (v3 == null) nautilus?.let { return NautilusSession(it) }
        return SennheiserSession(v3, ble = v3?.ble != null)
    }

    /** The equalizer goes out through the talk's queue, one message after another. */
    override fun sendEq(frames: List<ByteArray>) {
        val talk = talk ?: return
        if (!host.connected) return
        if (send(talk, frames)) publish(talk)
    }

    /** The Sound tab opens over the bands of whichever equalizer the earbuds answered as. */
    override fun eqSource(device: Paired, model: Model?): EqSource? {
        val session = (talk as? SennheiserEqSource)?.takeIf { it.eqKnown } ?: return null
        val band = SennheiserBandEq(session)
        val target = BandEqTarget(band)
        return EqSource(session to band.eqBandsHz, target.layout, target) { current -> band.report()?.let { target.reportedAs(it, current) } }
    }

    override fun report(info: ReportInfo): String? {
        (talk as? Gaia2Session)?.let { old ->
            return buildString {
                reportHead(info)
                appendLine("Brand:           Sennheiser (GAIA v2)")
                appendLine("Model:           ${old.model.name}")
                appendLine("Firmware:        ${old.firmware ?: "unknown"}")
                reportApp(info)
                appendLine()
                appendLine("The last answer to each property PairFree read (name: payload as hex):")
                reportReplies(old.replies)
            }
        }
        (talk as? NautilusSession)?.let { old ->
            return buildString {
                reportHead(info)
                appendLine("Brand:           Sennheiser (Nautilus)")
                appendLine("Model:           ${old.model.name}")
                appendLine("Firmware:        ${old.firmware ?: "unknown"}")
                reportApp(info)
                appendLine()
                appendLine("The last answer to each property PairFree read (name: payload as hex):")
                reportReplies(old.replies)
            }
        }
        val session = session ?: return null
        return buildString {
            reportHead(info)
            appendLine("Brand:           Sennheiser")
            appendLine("Model:           ${session.model?.name ?: "not in PairFree's table"} (model number ${session.modelId ?: "unknown"})")
            appendLine("Firmware:        ${session.firmware ?: "unknown"}")
            reportApp(info)
            appendLine()
            appendLine("The last answer to each property PairFree read (name: payload as hex):")
            reportReplies(session.replies)
        }
    }
}
