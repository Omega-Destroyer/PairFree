package io.github.omegadestroyer.pairfree.brands

import android.os.Build
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EqReported
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.eq.EqTarget
import io.github.omegadestroyer.pairfree.eq.SamsungBandEq
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungModels
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungSession
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungStatus
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungTalk
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice
import io.github.omegadestroyer.pairfree.protocol.widget.Sub

/** Samsung's Galaxy Buds (docs/samsung/PROTOCOL.md). */
object SamsungSpec : BrandSpec(Brand.SAMSUNG) {
    override val officialApp = "Galaxy Wearable"

    override fun paired(address: String, name: String, uuids: List<String>): Paired? =
        SamsungModels.identify(name, uuids)?.let { Paired(address, name, Brand.SAMSUNG, it.serviceUuid, it.model, SamsungModels.deviceIdIn(uuids)) }

    /** By the model number the name gave (data/samsung/devices.json). */
    override fun model(device: Paired, state: EarbudState): Model? = SamsungModels.model(device.model ?: state.firmware?.code)

    override fun driver(device: Paired, host: DriverHost): BrandDriver = SamsungDriver(host, device)

    override fun ownHearingTest(device: Paired): OwnHearingTest? =
        SamsungModels.of(device.model)?.takeIf { it.buds4Layout }?.let { OwnHearingTest.Samsung(it.legacy) }

    override fun recordedModel(code: String?): Model? = SamsungModels.model(code)
    override val recordedUnmatched: String get() = "Galaxy Buds (model not matched)"

    override fun noiseOffline(device: Paired): List<NoiseChoice>? = SamsungModels.of(device.model)?.let { m ->
        if (!m.supports("noise_control", null)) emptyList()
        else three(if (m.supports("anc_noise_reduction_levels", null)) listOf("low", "high").map { Sub.Row("anc_level", it) } else emptyList())
    }

    override fun eqOffline(device: Paired): Pair<EqLayout, EqTarget>? =
        SamsungModels.of(device.model)?.let { BandEqTarget(SamsungBandEq(it)) }?.let { it.layout to it }
}

/** Galaxy Buds ([SamsungTalk]). */
class SamsungDriver(host: DriverHost, private val device: Paired) : TalkDriver<ByteArray, SamsungTalk>(host) {
    /** The session of the channel now or last open; for the device report. */
    val session: SamsungSession? get() = talk?.session

    /** The Galaxy Buds' state as they last sent it, with changes since; the Sound tab reads their EQ from it. */
    private var status: SamsungStatus? = null

    /** The Galaxy Buds' EQ curves by equalizer number, where they reported them; the Sound tab draws the presets from them. */
    private var curves: List<List<Int>> = emptyList()

    override fun talk(opened: ControlLink): SamsungTalk? {
        val model = SamsungModels.of(device.model) ?: return null
        val phoneIsSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        curves = emptyList()
        return SamsungTalk(SamsungSession(model, phoneIsSamsung = phoneIsSamsung, androidSdk = Build.VERSION.SDK_INT, assistantIsGoogle = host.assistantIsGoogle()))
    }

    override fun publish(talk: SamsungTalk) {
        show(talk.state, talk.groups)
        if (status != talk.session.status) {
            status = talk.session.status
            host.eq(EqEvent.REPORT)
        }
    }

    override fun fed(talk: SamsungTalk) {
        if (curves != talk.session.eqCurves) {
            curves = talk.session.eqCurves
            // The preset curves arrive after the screen opened: draw them then.
            if (curves.isNotEmpty()) host.eq(EqEvent.OPEN_IF_OPEN)
        }
    }

    override fun sendEq(frames: List<ByteArray>) {
        send(talk ?: return, frames, emptyList(), replaces = EQ_WRITE)
    }

    /**
     * Galaxy Buds report which preset they hold, or that they hold a custom curve, but not the curve; the screen shows the curve this phone
     * last sent. Where the earbuds reported every preset's curve (the Buds4 family) they arrive after the screen opened, so the screen opens again.
     */
    override fun eqSource(device: Paired, model: Model?): EqSource? {
        val samsung = SamsungModels.of(device.model) ?: return null
        val curves = curves
        val band = SamsungBandEq(samsung, curves)
        val target = BandEqTarget(band)
        return EqSource(samsung to curves.isNotEmpty(), target.layout, target) { current ->
            status?.let { band.report(it, current) }?.let { (report, custom) ->
                target.reportedAs(report, current)?.let { EqReported(it.profileId, it.gains, resendCustom = custom) }
            }
        }
    }

    override fun report(info: ReportInfo): String? {
        val session = session ?: return null
        return buildString {
            reportHead(info)
            appendLine("Brand:           Samsung")
            appendLine("Model:           ${session.model.model} (${session.model.name})")
            appendLine("Found by:        ${info.device?.modelId?.let { "model id $it in the service list" } ?: "Bluetooth name"}")
            appendLine("Firmware:        ${host.state.value.firmware?.version ?: "unknown"}")
            reportApp(info)
            appendLine()
            val payload = session.lastStatusPayload
            appendLine("The earbuds' full state (message 97, revision ${session.status?.revision ?: "?"}, ${payload?.size ?: 0} bytes), as hex:")
            appendLine(payload?.reportHex() ?: "no state yet")
            appendLine()
            appendLine("As PairFree read it:")
            session.status?.values?.forEach { (k, v) -> appendLine("  $k = $v") }
        }
    }
}
