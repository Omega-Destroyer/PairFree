package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.EarbudsConnection
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EqTarget
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.DeviceReport
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.Models
import io.github.omegadestroyer.pairfree.protocol.NoiseModeInfo
import io.github.omegadestroyer.pairfree.protocol.Requests
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SoundpeatsBandEq
import io.github.omegadestroyer.pairfree.protocol.SoundpeatsSettings
import io.github.omegadestroyer.pairfree.protocol.SoundpeatsTalk
import io.github.omegadestroyer.pairfree.protocol.qcy.Capsule
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice
import io.github.omegadestroyer.pairfree.protocol.widget.Sub
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** SOUNDPEATS (docs/soundpeats/PROTOCOL.md). Its QCY-built models go to [QcySpec]'s driver under SOUNDPEATS' name. */
object SoundpeatsSpec : BrandSpec(Brand.SOUNDPEATS) {
    override val officialApp = "PeatsAudio or SOUNDPEATS"
    override val controls: Controls get() = Controls.SOUNDPEATS
    override val hasFactoryReset: Boolean get() = true

    // The QCY-built Capsule3 Pro runs on QCY's protocol, under SOUNDPEATS' name (docs/soundpeats/PROTOCOL.md section 10).
    override fun paired(address: String, name: String, uuids: List<String>): Paired? =
        Models.find(null, name)?.takeIf { Capsule.of(it) }?.let { Paired(address, name, Brand.QCY, model = it.code, soldAs = Brand.SOUNDPEATS) }
            ?: if (Models.isSupported(name)) Paired(address, name) else null

    /** By firmware code, or by name until the code is known. */
    override fun model(device: Paired, state: EarbudState): Model? = Models.find(state.firmware?.code, device.name)

    /** SOUNDPEATS earbuds' extra settings follow from the state they answered. */
    override fun groups(state: EarbudState, model: Model?, reported: List<SettingGroup>): List<SettingGroup> = SoundpeatsSettings.groups(state, model)

    override fun driver(device: Paired, host: DriverHost): BrandDriver = SoundpeatsDriver(host, device)

    override fun noiseOffline(device: Paired): List<NoiseChoice>? = Models.find(null, device.name)?.noiseModes?.let(::fromServer)

    override val mayHaveNoise: Boolean get() = true

    override fun eqOffline(device: Paired): Pair<EqLayout, EqTarget>? =
        Models.find(null, device.name)?.eq?.let { BandEqTarget(SoundpeatsBandEq(it)) }?.let { it.layout to it }

    /** SOUNDPEATS' server list for the model: the modes it offers, with the scenes under cancelling and transparency. */
    private fun fromServer(modes: List<NoiseModeInfo>): List<NoiseChoice> = buildList {
        for (info in modes) when (info.mode) {
            0 -> add(OFF)
            1 -> {
                add(NoiseChoice("anc"))
                info.scenes.forEach { add(NoiseChoice("anc", Sub.Level(Command.ANC_LEVEL, it))) }
            }
            2 -> {
                add(NoiseChoice("transparency"))
                info.scenes.forEach { add(NoiseChoice("transparency", Sub.Level(Command.TRANSPARENCY_LEVEL, it))) }
            }
        }
    }
}

/** SOUNDPEATS earbuds ([SoundpeatsTalk]). */
class SoundpeatsDriver(host: DriverHost, private val device: Paired) : TalkDriver<ByteArray, SoundpeatsTalk>(host) {
    /** The last raw reply to each GET, for the device report. */
    val replies: Map<Command, ByteArray> get() = talk?.replies ?: emptyMap()

    /** Each talk starts from what the screens show and the replies so far: the earbuds restart their link after some changes. */
    override fun talk(opened: ControlLink): SoundpeatsTalk = SoundpeatsTalk(device.name, host.state.value, replies)

    /** A SET of any length: the gestures take two numbers, their reset none. */
    override fun set(command: Command, payload: IntArray) {
        val talk = talk ?: return
        if (!host.connected) return
        if (send(talk, talk.write(command, *payload))) publish(talk)
    }

    /** A frame per band, and the earbuds do not reply. A newer write replaces what is left of one still going out. */
    override fun sendEq(frames: List<ByteArray>) {
        send(talk ?: return, frames, emptyList(), replaces = EQ_WRITE)
    }

    /**
     * Starts the ear tip fit test; the result replaces [EarbudState.fit] about 9 s later.
     * Unless [keepAnc], noise control goes off first, as the official app does (docs/soundpeats/PROTOCOL.md
     * section 11), and back to what it was once the result is in. The official app leaves it off.
     */
    override fun startFitTest(keepAnc: Boolean) {
        val talk = talk ?: return
        if (!host.connected) return
        val l = host.link ?: return
        val mode = host.state.value.number(Command.ANC_MODE)
        val switchOff = !keepAnc && mode != null && mode != ANC_OFF
        talk.update { it.copy(fit = null) }
        publish(talk)
        host.launch {
            try {
                if (switchOff) {
                    // Started too soon after ANC goes off, the test gives up within 2 s and
                    // reports a poor fit. Wait for the earbuds to confirm, then a little more.
                    l.send(Requests.set(Command.ANC_MODE, ANC_OFF))
                    withTimeoutOrNull(EarbudsConnection.ANC_CONFIRM_MS) { host.state.first { it.number(Command.ANC_MODE) == ANC_OFF } }
                    delay(EarbudsConnection.ANC_SETTLE_MS)
                }
                for (frame in Requests.fitTest()) l.send(frame)
                if (switchOff && mode != null) {
                    withTimeoutOrNull(EarbudsConnection.FIT_WAIT_MS) { host.state.first { it.fit != null } }
                    l.send(Requests.set(Command.ANC_MODE, mode))
                    talk.update { it.expect(Command.ANC_MODE, mode) }
                    publish(talk)
                }
            } catch (_: IOException) {
                // the channel closed; the reader notices and reports it
            }
        }
    }

    override fun factoryResetConfirmedByUser() {
        send(talk ?: return, listOf(Requests.factoryResetConfirmedByUser()), emptyList())
    }

    override fun report(info: ReportInfo): String = plainReport(info, replies, host.state.value)

    companion object {
        /** ANC mode "off" (data/soundpeats/commands.json). */
        private val ANC_OFF = Command.ANC_MODE.labels.entries.first { it.value == "off" }.key

        /**
         * A complete, pasteable capture of what the earbuds answered, so a model can be added
         * from a user's phone with no developer mode and no Bluetooth snooping. On connect the
         * app sends every read it knows (Requests.ON_CONNECT); this formats the replies the same
         * way tools/probe.py does, under a header that identifies the model and says where to paste.
         */
        fun plainReport(info: ReportInfo, replies: Map<Command, ByteArray>, state: EarbudState): String = buildString {
            val firmware = state.firmware
            appendLine("PairFree device report")
            appendLine("Paste this whole report into a new issue at")
            appendLine("${info.issues} to help add support")
            appendLine("for this model. It holds no account, address or other personal data.")
            appendLine()
            appendLine("Bluetooth name:  ${info.name}")
            appendLine("Firmware:        ${firmware?.text ?: "unknown"}")
            appendLine("Firmware code:   ${firmware?.code ?: "unknown"}")
            appendLine("App:             PairFree ${info.version} (SPP)")
            appendLine("OS:              ${info.os}")
            appendLine("Captured:        ${info.date}")
            appendLine()
            appendLine("The app asked the earbuds every read it knows. Each line is the feature,")
            appendLine("its command, the decoded reply and the raw bytes. \"no reply\" means the")
            appendLine("firmware does not support that read.")
            appendLine()
            DeviceReport.lines(replies).forEach(::appendLine)
            state.gaiaFeatures?.let { appendLine(DeviceReport.gaiaLine(it)) }
            appendLine()
            appendLine("This is enough to add this model's controls. For its equalizer too, use")
            appendLine("\"Record from the official app\" on PairFree's Device tab instead.")
        }
    }
}
