package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value
import io.github.omegadestroyer.pairfree.protocol.jbl.JblModel.Mode

/**
 * One connection to JBL headphones or earbuds over Harman's commands (docs/jbl/PROTOCOL.md), shown on
 * PairFree's screens. It reads the product id, the firmware, the battery and the settings JBL's app
 * shows for the model ([JblModel]), each the way the model's control class in JBL's app reads it. The
 * battery, firmware and noise control (where the model has both noise cancelling and Ambient Aware) go
 * to [state], everything else to [groups].
 *
 * A change shows at once; the earbuds acknowledge it, and [takeReadBacks] gives the reads that confirm
 * it, which the connection sends a little later, so the screen ends up showing what the earbuds hold.
 */
public class JblSession(initial: JblModel) {
    public var model: JblModel = initial
        private set

    /** What the shared screens show. */
    public var state: EarbudState = EarbudState()
        private set

    /** The product id the device reported. */
    public var productId: Int? = null
        private set

    public var firmware: String? = null
        private set

    /**
     * The last answer to each of PairFree's reads, for the device report, by command (and status type
     * or feature id): the device info with its name and address blanked, and no feature but the ones
     * PairFree asks for, since the earbuds can send their name and address unasked.
     */
    public val replies: Map<String, ByteArray> get() = _replies
    private val _replies = linkedMapOf<String, ByteArray>()

    private var noiseKnown = false
    private var mode: Mode? = null       // null while off
    private var ancOn: Boolean? = null   // the old commands report noise cancelling and ambient sound apart
    private var ambient: Int? = null
    private var autoOffOn: Boolean? = null
    private var autoOffMinutes: Int? = null
    private var autoPlayPause: Boolean? = null
    private val outbox = mutableListOf<JblPacket>()
    private val readBacks = mutableListOf<JblPacket>()

    /** The reads on opening the channel. */
    public fun start(): List<JblPacket> = buildList {
        when (model.family) {
            JblModel.Family.DIRECT -> {
                add(JblPacket.of(JblCommand.DEVICE_INFO))
                // The Tune 720BT ignores reads of single status types but answers this one (FajkPes).
                add(JblPacket.of(JblCommand.STATUS, JblStatus.ALL))
            }
            JblModel.Family.FEATURE -> add(JblPacket.featureGet(featureIds()))
        }
        add(batteryRead())
        addAll(noiseReads())
    }

    /** The battery read, to send again now and then. */
    public fun batteryRequests(): List<JblPacket> = buildList {
        add(batteryRead())
        if (model.family == JblModel.Family.FEATURE) add(JblPacket.featureGet(batteryFeatures()))
    }

    /** What the session wants sent next: the reads of a model learnt from its product id. */
    public fun takeOutbox(): List<JblPacket> = outbox.toList().also { outbox.clear() }

    /** The reads that confirm the last changes, to send once the earbuds have had time to apply them. */
    public fun takeReadBacks(): List<JblPacket> = readBacks.distinct().also { readBacks.clear() }

    /** False for what PairFree never sends: factory reset, power off, firmware update, analytics. */
    public fun allowed(packet: JblPacket): Boolean = packet.command !in JblCommand.NEVER_SEND

    /** Applies one packet from the device. Returns true when [state] or [groups] changed. */
    public fun receive(packet: JblPacket): Boolean {
        val before = state to groups
        val p = packet.payload
        when (packet.command) {
            JblCommand.DEVICE_INFO_REPLY -> deviceInfo(p)
            JblCommand.STATUS_REPLY -> status(p)
            JblCommand.BATTERY -> battery(p)
            JblCommand.NOISE_MODES -> noiseModes(p)
            JblCommand.FEATURE -> for ((id, v) in packet.features()) feature(id, v)
        }
        refresh()
        return (state to groups) != before
    }

    private fun deviceInfo(p: ByteArray) {
        // Name (16 bytes), product id (low first), colour, battery, address (6 bytes), firmware (3 bytes).
        _replies[key(JblCommand.DEVICE_INFO_REPLY)] = p.copyOf().also { r ->
            for (i in 0 until minOf(INFO_NAME_END, r.size)) r[i] = 0
            for (i in INFO_MAC until minOf(INFO_MAC + MAC_BYTES, r.size)) r[i] = 0
        }
        if (p.size >= INFO_PID + 2) productIdIs((p.u8(INFO_PID + 1) shl 8) or p.u8(INFO_PID))
        if (p.size >= INFO_FIRMWARE + 3) firmware = "${p.u8(INFO_FIRMWARE)}.${p.u8(INFO_FIRMWARE + 1)}.${p.u8(INFO_FIRMWARE + 2)}"
    }

    /** A status reply: the type, then its values (JBL's HandleParse.parseDevStatus and parseDeviceAllStatus). */
    private fun status(p: ByteArray) {
        if (p.size < 2) return
        val type = p.u8(0)
        _replies[key(JblCommand.STATUS_REPLY, type)] = p.copyOf()
        when (type) {
            JblStatus.ALL -> {
                if (model.noise == JblModel.Noise.OLD || model.noise == JblModel.Noise.CLUB) {
                    ancOn = boolean(p.u8(1))
                    if (p.size > 2) ambient = p.u8(2)
                    oldNoise()
                }
                // The one-byte auto power-off, unless FF, and on newer firmware the three-byte one after it.
                if (p.size > ALL_AUTO_OFF && p.u8(ALL_AUTO_OFF) != 0xFF) autoOffByte(p.u8(ALL_AUTO_OFF))
                if (p.size > ALL_AUTO_PLAY) autoPlayPause = p.u8(ALL_AUTO_PLAY) != 0
                if (p.size >= ALL_AUTO_OFF_2 + 3) {
                    val minutes = p.u8(ALL_AUTO_OFF_2 + 1) or (p.u8(ALL_AUTO_OFF_2 + 2) shl 8)
                    val on = boolean(p.u8(ALL_AUTO_OFF_2))
                    if (minutes != 0xFFFF && on != null) {
                        autoOffOn = on
                        autoOffMinutes = minutes
                    }
                }
            }
            JblStatus.ANC -> {
                ancOn = boolean(p.u8(1))
                oldNoise()
            }
            JblStatus.AMBIENT -> {
                ambient = p.u8(1)
                oldNoise()
            }
            JblStatus.AUTO_OFF -> if (p.size == 2) {
                autoOffByte(p.u8(1))
            } else if (p.size >= 4) {
                autoOffOn = p.u8(1) != 0
                autoOffMinutes = p.u8(2) or (p.u8(3) shl 8)
            }
            JblStatus.AUTO_PLAY_PAUSE -> autoPlayPause = p.u8(1) != 0
        }
    }

    /** One byte of auto power-off: bit 7 on, bits 0 to 6 the minutes. */
    private fun autoOffByte(b: Int) {
        autoOffOn = b and AUTO_OFF_ON != 0
        autoOffMinutes = b and AUTO_OFF_MINUTES
    }

    /** Noise control from the old commands: noise cancelling on wins, else the ambient mode. */
    private fun oldNoise() {
        val anc = ancOn ?: return
        mode = when {
            anc -> Mode.ANC
            ambient == JblAmbient.TALK_THRU -> Mode.TALK_THRU
            ambient == JblAmbient.AMBIENT_AWARE -> Mode.AMBIENT_AWARE
            else -> null
        }
        noiseKnown = true
    }

    /**
     * The battery: a type byte (01), then whether the secondary and the primary unit are charging, their
     * levels, whether the case is charging and its level. JBL's app shows the secondary as the left
     * earbud, the primary as the right one and, on headphones, the primary alone. Over 100 is unknown.
     */
    private fun battery(p: ByteArray) {
        if (p.size < BATTERY_CASE + 1 || p.u8(0) != 1) return
        _replies[key(JblCommand.BATTERY)] = p.copyOf()
        val secondary = level(p.u8(BATTERY_SECONDARY))
        val primary = level(p.u8(BATTERY_PRIMARY))
        val values = if (model.form == Form.HEADPHONES) {
            mapOf(Command.BATTERY_LEFT to Value.Number(primary ?: secondary))
        } else {
            mapOf(
                Command.BATTERY_LEFT to Value.Number(secondary),
                Command.BATTERY_RIGHT to Value.Number(primary),
                Command.BATTERY_CASE to Value.Number(level(p.u8(BATTERY_CASE))),
            )
        }
        state = state.copy(values = state.values + values)
    }

    /** Command 91's answer: the sub-command, then pairs of mode and value; the mode with a value is on. */
    private fun noiseModes(p: ByteArray) {
        val sub = if (p.isEmpty()) return else p.u8(0)
        // 12 answers a read; JblTray saw the earbuds send 10, the write's own form, too.
        if (sub != JblNoise.REPLY && sub != JblNoise.SET) return
        _replies[key(JblCommand.NOISE_MODES)] = p.copyOf()
        var on: Mode? = null
        var at = 1
        while (at + 1 < p.size) {
            if (p.u8(at + 1) != 0) on = modeOf(p.u8(at)) ?: on
            at += 2
        }
        mode = on
        noiseKnown = true
    }

    private fun feature(id: Int, v: ByteArray) {
        if (id !in REPORTED_FEATURES) return
        _replies[key(JblCommand.FEATURE, id)] = v.copyOf()
        when (id) {
            JblFeature.PRODUCT_ID -> if (v.size >= 2) productIdIs((v.u8(1) shl 8) or v.u8(0))
            JblFeature.FIRMWARE -> if (v.size >= 3) firmware = "${v.u8(0)}.${v.u8(1)}.${v.u8(2)}"
            JblFeature.AUTO_OFF -> if (v.size >= 3) {
                autoOffOn = v.u8(0) == 1
                autoOffMinutes = v.u8(1) or (v.u8(2) shl 8)
            }
            JblFeature.AUTO_PLAY_PAUSE -> if (v.isNotEmpty()) autoPlayPause = v.u8(0) == 1
            JblFeature.BATTERY_HEADBAND -> unitBattery(Command.BATTERY_LEFT, v)
            JblFeature.BATTERY_LEFT -> unitBattery(Command.BATTERY_LEFT, v)
            JblFeature.BATTERY_RIGHT -> unitBattery(Command.BATTERY_RIGHT, v)
            JblFeature.BATTERY_CASE -> unitBattery(Command.BATTERY_CASE, v)
        }
    }

    /** A battery feature: whether it is charging, then its level. */
    private fun unitBattery(command: Command, v: ByteArray) {
        if (v.size < 2) return
        state = state.copy(values = state.values + (command to Value.Number(level(v.u8(1)))))
    }

    /** The product id names the model where two share a name; a model of another kind is read afresh. */
    private fun productIdIs(pid: Int) {
        productId = pid
        val known = JblModels.of(pid) ?: return
        if (known == model) return
        val reread = known.family != model.family || known.noise != model.noise
        model = known
        if (reread) outbox += start()
    }

    /** Puts the firmware and, where the model has both noise cancelling and Ambient Aware, the noise mode where the shared screens read them. */
    private fun refresh() {
        val values = buildMap {
            firmware?.let { put(Command.FIRMWARE_VERSION, Value.Text("${model.name}_$it")) }
            if (sharedNoise && noiseKnown) put(Command.ANC_MODE, Value.Number(ancNumberOf(mode)))
        }
        state = state.copy(values = state.values + values)
    }

    /** The shared noise control (off, noise cancelling, ambient sound) fits models with noise cancelling and Ambient Aware. */
    private val sharedNoise: Boolean get() = Mode.ANC in model.modes && Mode.AMBIENT_AWARE in model.modes

    /** Every setting the model has beyond the shared screens. */
    public val groups: List<SettingGroup>
        get() = listOfNotNull(
            group("noise", SettingTab.HOME, noiseRows()),
            group("other", SettingTab.DEVICE, otherRows()),
        )

    private fun noiseRows(): List<Setting> {
        if (!noiseKnown) return emptyList()
        // TalkThru beside the shared control; on other models a switch for each mode it has.
        val separate = if (sharedNoise) model.modes.filter { it == Mode.TALK_THRU } else model.modes
        return separate.map { Setting.Toggle(KEYS.getValue(it), mode == it) }
    }

    private fun otherRows(): List<Setting> = listOfNotNull(
        model.autoOff?.let { autoOffRow() },
        autoPlayPause?.takeIf { model.autoPlayPause }?.let { Setting.Toggle("pause_taken_off", it) },
    )

    /** Auto power-off: never or after the minutes JBL's app offers, or on and off alone where it offers no times. */
    private fun autoOffRow(): Setting? {
        val on = autoOffOn ?: return null
        if (model.autoOffMinutes.isEmpty()) return Setting.Toggle("auto_off", on)
        val minutes = autoOffMinutes
        val options = listOf(NEVER) + (model.autoOffMinutes + listOfNotNull(minutes?.takeIf { on })).distinct().sorted().map { it.toString() }
        return Setting.Choice("auto_off", if (on) minutes?.toString() else NEVER, options)
    }

    private fun group(key: String, tab: SettingTab, settings: List<Setting>) = settings.takeIf { it.isNotEmpty() }?.let { SettingGroup(key, tab, it) }

    /**
     * The packets for a change from the screens, or null when the model does not have the setting. The
     * change shows at once; [takeReadBacks] then has the reads that confirm it.
     */
    public fun change(change: SettingChange): List<JblPacket>? {
        val setting = groups.flatMap { it.settings }.firstOrNull { it.key == change.key } ?: return null
        return when (change) {
            is SettingChange.Toggle -> {
                if (setting !is Setting.Toggle) return null
                when (change.key) {
                    "anc", "ambient_aware", "talk_thru" -> {
                        val m = KEYS.entries.first { it.value == change.key }.key
                        noise(if (change.on) m else null)
                    }
                    "auto_off" -> autoOff(change.on, autoOffMinutes?.takeIf { it > 0 } ?: CLUB_ONE_MINUTES)
                    "pause_taken_off" -> autoPlay(change.on)
                    else -> null
                }
            }
            is SettingChange.Choice -> {
                if (setting !is Setting.Choice || change.option !in setting.options || change.key != "auto_off") return null
                if (change.option == NEVER) {
                    autoOff(false, autoOffMinutes?.takeIf { it > 0 } ?: model.autoOffMinutes.first())
                } else {
                    autoOff(true, change.option.toIntOrNull() ?: return null)
                }
            }
            is SettingChange.Level, is SettingChange.Press -> null
        }
    }

    /** The noise mode from the shared screens, on models with noise cancelling and Ambient Aware. */
    public fun set(command: Command, value: Int): List<JblPacket>? {
        if (command != Command.ANC_MODE || !sharedNoise || !noiseKnown) return null
        val m = when (value) {
            ancNumber("off") -> null
            ancNumber("anc") -> Mode.ANC
            ancNumber("transparency") -> Mode.AMBIENT_AWARE
            else -> return null
        }
        return noise(m)
    }

    /** Sets noise control to [m], or off, the way the model's control class in JBL's app does. */
    private fun noise(m: Mode?): List<JblPacket>? {
        if (m != null && m !in model.modes) return null
        val out = when (model.noise ?: return null) {
            JblModel.Noise.MODES -> {
                readBacks += JblPacket.of(JblCommand.NOISE_MODES, JblNoise.GET)
                if (m == null) {
                    listOf(JblPacket.of(JblCommand.NOISE_MODES, JblNoise.OFF))
                } else {
                    // Every mode with its value, the chosen one 1, as JBL's app writes them.
                    val pairs = MODE_ORDER.flatMap { listOf(MODE_NUMBERS.getValue(it), if (it == m) 1 else 0) }
                    listOf(JblPacket.of(JblCommand.NOISE_MODES, JblNoise.SET, *pairs.toIntArray()))
                }
            }
            JblModel.Noise.OLD, JblModel.Noise.CLUB -> {
                readBacks += JblPacket.of(JblCommand.STATUS, JblStatus.ANC)
                readBacks += JblPacket.of(JblCommand.STATUS, JblStatus.AMBIENT)
                when (m) {
                    null -> listOfNotNull(
                        JblPacket.of(JblCommand.SET_ANC, 0),
                        // The Club One's control class turns noise cancelling off alone.
                        JblPacket.of(JblCommand.SET_AMBIENT, JblAmbient.OFF).takeIf { model.noise == JblModel.Noise.OLD },
                    )
                    Mode.ANC -> listOf(JblPacket.of(JblCommand.SET_ANC, 1))
                    Mode.TALK_THRU -> listOf(JblPacket.of(JblCommand.SET_AMBIENT, JblAmbient.TALK_THRU))
                    Mode.AMBIENT_AWARE -> listOf(JblPacket.of(JblCommand.SET_AMBIENT, JblAmbient.AMBIENT_AWARE))
                }
            }
        }
        mode = m
        if (model.noise != JblModel.Noise.MODES) {
            ancOn = m == Mode.ANC
            ambient = when (m) {
                Mode.TALK_THRU -> JblAmbient.TALK_THRU
                Mode.AMBIENT_AWARE -> JblAmbient.AMBIENT_AWARE
                else -> JblAmbient.OFF
            }
        }
        refresh()
        return out
    }

    private fun autoOff(on: Boolean, minutes: Int): List<JblPacket>? {
        val kind = model.autoOff ?: return null
        val out = when (kind) {
            JblModel.AutoOff.V1 -> JblPacket.of(JblCommand.SET_AUTO_OFF, (if (on) AUTO_OFF_ON else 0) or (minutes and AUTO_OFF_MINUTES))
            JblModel.AutoOff.V2 -> JblPacket.of(JblCommand.SET_AUTO_OFF, if (on) 1 else 0, minutes and 0xFF, (minutes shr 8) and 0xFF)
            JblModel.AutoOff.FEATURE -> JblPacket.featureSet(JblFeature.AUTO_OFF, if (on) 1 else 0, minutes and 0xFF, (minutes shr 8) and 0xFF)
        }
        readBacks += settingsRead(JblFeature.AUTO_OFF)
        autoOffOn = on
        autoOffMinutes = minutes
        return listOf(out)
    }

    private fun autoPlay(on: Boolean): List<JblPacket>? {
        if (!model.autoPlayPause) return null
        val out = when (model.family) {
            JblModel.Family.DIRECT -> JblPacket.of(JblCommand.SET_AUTO_PLAY_PAUSE, if (on) 1 else 0)
            JblModel.Family.FEATURE -> JblPacket.featureSet(JblFeature.AUTO_PLAY_PAUSE, if (on) 1 else 0)
        }
        readBacks += settingsRead(JblFeature.AUTO_PLAY_PAUSE)
        autoPlayPause = on
        return listOf(out)
    }

    /** The read that reports a setting: the whole status on direct models, the one feature on the others. */
    private fun settingsRead(feature: Int): JblPacket = when (model.family) {
        JblModel.Family.DIRECT -> JblPacket.of(JblCommand.STATUS, JblStatus.ALL)
        JblModel.Family.FEATURE -> JblPacket.featureGet(listOf(feature))
    }

    private fun noiseReads(): List<JblPacket> = when (model.noise) {
        JblModel.Noise.MODES -> listOf(JblPacket.of(JblCommand.NOISE_MODES, JblNoise.GET))
        JblModel.Noise.OLD -> listOf(JblPacket.of(JblCommand.STATUS, JblStatus.ANC), JblPacket.of(JblCommand.STATUS, JblStatus.AMBIENT))
        JblModel.Noise.CLUB -> listOf(JblPacket.of(JblCommand.STATUS, JblStatus.ANC))
        null -> emptyList()
    }

    private fun batteryRead(): JblPacket = JblPacket.of(JblCommand.BATTERY, 0)

    /** The features read on connecting, as the model's control class in JBL's app asks for them. */
    private fun featureIds(): List<Int> = buildList {
        add(JblFeature.PRODUCT_ID)
        add(JblFeature.FIRMWARE)
        if (model.autoOff != null) add(JblFeature.AUTO_OFF)
        if (model.autoPlayPause) add(JblFeature.AUTO_PLAY_PAUSE)
        addAll(batteryFeatures())
    }

    private fun batteryFeatures(): List<Int> =
        if (model.form == Form.HEADPHONES) listOf(JblFeature.BATTERY_HEADBAND) else listOf(JblFeature.BATTERY_LEFT, JblFeature.BATTERY_RIGHT, JblFeature.BATTERY_CASE)

    private fun key(command: Int, part: Int? = null): String =
        command.toString(16).padStart(2, '0') + (part?.let { "." + it.toString(16).padStart(2, '0') } ?: "")

    private fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF

    private companion object {
        // Device info (payload offsets): name 0 to 15, product id 16 and 17, address 20 to 25, firmware 26 to 28.
        const val INFO_NAME_END = 16
        const val INFO_PID = 16
        const val INFO_MAC = 20
        const val INFO_FIRMWARE = 26
        const val MAC_BYTES = 6

        // The whole status (payload offsets after the type): auto power-off (one byte), auto play/pause, auto power-off (three bytes).
        const val ALL_AUTO_OFF = 3
        const val ALL_AUTO_PLAY = 8
        const val ALL_AUTO_OFF_2 = 10

        const val AUTO_OFF_ON = 0x80
        const val AUTO_OFF_MINUTES = 0x7F

        /** The time JBL's app writes with the Club One's auto power-off switch. */
        const val CLUB_ONE_MINUTES = 10

        const val BATTERY_SECONDARY = 3
        const val BATTERY_PRIMARY = 4
        const val BATTERY_CASE = 6
        const val NEVER = "never"

        val MODE_ORDER = listOf(Mode.ANC, Mode.AMBIENT_AWARE, Mode.TALK_THRU)
        val MODE_NUMBERS = mapOf(Mode.ANC to JblNoise.MODE_ANC, Mode.AMBIENT_AWARE to JblNoise.MODE_AMBIENT_AWARE, Mode.TALK_THRU to JblNoise.MODE_TALK_THRU)
        val KEYS = mapOf(Mode.ANC to "anc", Mode.AMBIENT_AWARE to "ambient_aware", Mode.TALK_THRU to "talk_thru")

        val REPORTED_FEATURES = setOf(
            JblFeature.PRODUCT_ID, JblFeature.FIRMWARE, JblFeature.AUTO_OFF, JblFeature.AUTO_PLAY_PAUSE,
            JblFeature.BATTERY_HEADBAND, JblFeature.BATTERY_LEFT, JblFeature.BATTERY_RIGHT, JblFeature.BATTERY_CASE,
        )

        fun modeOf(number: Int): Mode? = MODE_NUMBERS.entries.firstOrNull { it.value == number }?.key

        fun level(v: Int): Int? = v.takeIf { it <= 100 }

        /** JBL's parseBoolean: 00 off, 01 or 02 on, anything else unknown. */
        fun boolean(v: Int): Boolean? = when (v) {
            0 -> false
            1, 2 -> true
            else -> null
        }

        // The noise modes by their names in data/soundpeats/commands.json, so no number is retyped.
        fun ancNumber(name: String): Int = Command.ANC_MODE.labels.entries.first { it.value == name }.key

        fun ancNumberOf(mode: Mode?): Int? = when (mode) {
            null -> ancNumber("off")
            Mode.ANC -> ancNumber("anc")
            Mode.AMBIENT_AWARE -> ancNumber("transparency")
            Mode.TALK_THRU -> null
        }
    }
}
