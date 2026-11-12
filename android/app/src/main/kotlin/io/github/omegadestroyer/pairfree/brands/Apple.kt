package io.github.omegadestroyer.pairfree.brands

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.AppleLink
import io.github.omegadestroyer.pairfree.bluetooth.AppleSupport
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.bluetooth.SppLink
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.Wire
import io.github.omegadestroyer.pairfree.protocol.apple.AppleModels
import io.github.omegadestroyer.pairfree.protocol.apple.AppleSession
import io.github.omegadestroyer.pairfree.protocol.apple.AppleTalk
import io.github.omegadestroyer.pairfree.protocol.apple.AppleWire
import io.github.omegadestroyer.pairfree.protocol.apple.BcdSession
import io.github.omegadestroyer.pairfree.protocol.apple.BeatsControl
import io.github.omegadestroyer.pairfree.protocol.apple.BeatsTalk
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice
import java.io.IOException

/** AirPods and the Beats on Apple's chips (docs/apple/PROTOCOL.md, docs/apple/B2P.md). */
object AppleSpec : BrandSpec(Brand.APPLE) {
    /** AirPods have no official app on Android to close (issue #3). */
    override val officialApp = ""

    override fun eqNote(device: Paired): Int =
        if (AppleModels.b2p(device.name) != null || AppleModels.bcd(device.name) != null) R.string.eq_beats else R.string.eq_apple

    override fun paired(address: String, name: String, uuids: List<String>): Paired = Paired(address, name, Brand.APPLE)

    /** By the model their model number names, which the firmware line carries; one battery is headphones. */
    override fun model(device: Paired, state: EarbudState): Model? =
        AppleModels.model(state.firmware?.code ?: device.name, if (state.supports(Command.BATTERY_RIGHT)) Form.EARBUDS else Form.HEADPHONES)

    override fun driver(device: Paired, host: DriverHost): BrandDriver =
        if (AppleModels.beatsSession(device.name) != null) BeatsDriver(host, device) else AirPodsDriver(host)

    /**
     * AirPods' channel over L2CAP, on phones whose Bluetooth stack opens it ([AppleSupport]). On the others
     * it is not tried: what a refused channel does to the AirPods' sound there is not known, and the user
     * is told Android 17 is needed instead, as the owner chose (2026-10-15) and as LibrePods does by default.
     */
    @SuppressLint("MissingPermission") // only called while connecting, which needs the permission
    override suspend fun open(env: OpenEnv, remote: BluetoothDevice, device: Paired, driver: BrandDriver): ControlLink {
        if (AppleModels.beatsSession(device.name) != null) {
            // Apple's Beats app opens the plain serial channel to these Beats (docs/apple/B2P.md): no Android version matters.
            env.note("serial channel ${Wire.SPP_UUID} (${if (AppleModels.b2p(device.name) != null) "B2P" else "BCD"})")
            return env.hold(SppLink(remote, env.linkLog, Wire.SPP_UUID)).apply { connect() }
        }
        if (!AppleSupport.opensChannel()) {
            env.note("not tried: this phone's Bluetooth stack is not on LibrePods' list")
            throw NeedsNewerAndroid()
        }
        val a = env.adapter ?: throw IOException("no Bluetooth adapter")
        env.note("L2CAP PSM 0x%04x".format(AppleWire.PSM))
        return env.hold(AppleLink(a, remote, env.linkLog)).apply { connect() }
    }

    /** AirPods say only in their replies. */
    override fun noiseOffline(device: Paired): List<NoiseChoice>? = null

    override val mayHaveNoise: Boolean get() = true

    /** LibrePods leaves 200 ms between its opening frames ... */
    internal const val GAP_MS = 200L
}

/** AirPods ([AppleTalk]). */
class AirPodsDriver(host: DriverHost) : TalkDriver<ByteArray, AppleTalk>(host) {
    /** The session of the channel now or last open; for the device report. */
    val session: AppleSession? get() = talk?.session

    override fun talk(opened: ControlLink): AppleTalk = AppleTalk()

    override fun report(info: ReportInfo): String? {
        val session = session ?: return null
        return buildString {
            reportHead(info, bluetoothName = false)
            appendLine("Brand:           Apple (AirPods or Beats)")
            appendLine("Model:           ${session.model?.name ?: "not in PairFree's table"} (model number ${session.modelNumber ?: "unknown"})")
            appendLine("Firmware:        ${session.firmware ?: "unknown"}")
            reportApp(info, phone = true)
            appendLine()
            appendLine("Battery (count, then component, 01, level, status, 01 each), as hex:")
            appendLine("  ${session.batteryData?.reportHex() ?: "none yet"}")
            appendLine("Settings the AirPods reported (control command id: value, as hex):")
            session.controls.forEach { (id, value) -> appendLine("  %02x: %s".format(id, value.reportHex())) }
        }
    }
}

/** Beats on Apple's chips that speak B2P or BCD ([BeatsTalk]). */
class BeatsDriver(host: DriverHost, private val device: Paired) : TalkDriver<ByteArray, BeatsTalk>(host) {
    /** The session of the channel now or last open; for the device report. */
    val session: BeatsControl? get() = talk?.session

    override fun talk(opened: ControlLink): BeatsTalk? = AppleModels.beatsSession(device.name)?.let(::BeatsTalk)

    override fun report(info: ReportInfo): String? {
        val session = session ?: return null
        return buildString {
            reportHead(info, bluetoothName = false)
            appendLine("Brand:           Apple (Beats, ${if (session is BcdSession) "BCD" else "B2P"} protocol)")
            appendLine("Model:           ${session.model?.name ?: "not in PairFree's table"}")
            reportApp(info, phone = true)
            appendLine()
            session.reportLines.forEach { appendLine(it) }
            appendLine("Reads answered (value, or none):")
            session.answers.forEach { (key, value) -> appendLine("  $key: ${value?.let { "%02x".format(it) } ?: "none"}") }
        }
    }
}
