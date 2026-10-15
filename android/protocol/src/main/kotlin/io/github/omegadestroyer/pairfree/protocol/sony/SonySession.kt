package io.github.omegadestroyer.pairfree.protocol.sony

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value

/**
 * One connection to Sony earbuds or headphones, shown on PairFree's screens (docs/sony/PROTOCOL.md).
 * It asks which table set they speak, their model name and firmware, and which functions they have;
 * then it reads each function PairFree offers, and every reply or notification updates [state] (the
 * battery, the noise mode, the firmware) and [groups] (everything else). Each change becomes the
 * message Sony's app sends for it and shows at once; the earbuds' notification confirms it.
 *
 * [tableSet] starts as the service the channel opened says (1 or 2) and follows the earbuds' first
 * reply, whose length says which they speak.
 */
public class SonySession(tableSet: Int, public val model: SonyModel? = null) {
    public var tableSet: Int = tableSet
        private set

    /** What the shared screens show. */
    public var state: EarbudState = EarbudState()
        private set

    /** The model name the earbuds gave, such as `WH-1000XM4`. */
    public var modelName: String? = null
        private set

    public var firmware: String? = null
        private set

    /** The functions the earbuds listed (table 1); see data/sony/functions.json. */
    public var functions: Set<Int> = emptySet()
        private set

    public var noise: SonyNoise? = null
        private set

    /** The equalizer, once the earbuds listed one; what it holds fills in as they answer. */
    public var eq: SonyEq? = null
        private set

    private var batteryTypes: List<Int> = emptyList()
    private var dsee: Boolean? = null
    private var pauseWhenOff: Boolean? = null
    private var speakToChat: Boolean? = null
    private var speakToChatPreview = OFF_V2
    private var speakToChatType: Int? = null
    private var voiceGuidance: Boolean? = null
    private val outbox = mutableListOf<SonyOut>()

    /** The last payload of each kind the earbuds sent, for the device report: by table, command and type. */
    public val replies: Map<String, ByteArray> get() = _replies
    private val _replies = linkedMapOf<String, ByteArray>()

    /** What to send on opening the channel: the protocol request, which every table set answers the same way. */
    public fun start(): List<SonyOut> = listOf(SonyOut.table1(CONNECT_GET_PROTOCOL_INFO, FIXED))

    /** What the session wants sent next: the reads that follow the earbuds' answers. */
    public fun takeOutbox(): List<SonyOut> = outbox.toList().also { outbox.clear() }

    /** The battery reads, to send again now and then. */
    public fun batteryRequests(): List<SonyOut> = batteryTypes.map { SonyOut.table1(if (tableSet == 1) S1.COMMON_GET_BATTERY_LEVEL else S2.POWER_GET_STATUS, it) }

    /** Applies one message from the earbuds. Returns true when [state] or [groups] changed. */
    public fun receive(message: SonyMessage): Boolean {
        val p = message.payload
        if (p.isEmpty() || message.dataType == SonyWire.ACK) return false
        val table = if (message.dataType == SonyWire.TABLE2) 2 else 1
        _replies["$table ${p.u8(0).hex2()}${if (p.size > 1) " " + p.u8(1).hex2() else ""}"] = p.copyOf()
        val before = Snapshot(state, groups)
        if (table == 2) {
            if (tableSet == 1 && (p.u8(0) == S1T2.VOICE_GUIDANCE_RET_PARAM || p.u8(0) == S1T2.VOICE_GUIDANCE_NTFY_PARAM) &&
                p.size == 4 && p.u8(1) == VOICE_GUIDANCE_SETTING && p.u8(2) == ON_OFF_DETAIL
            ) {
                voiceGuidance = p.u8(3) == 1
            }
        } else {
            receiveTable1(p)
        }
        refresh()
        return Snapshot(state, groups) != before
    }

    private fun receiveTable1(p: ByteArray) {
        when (p.u8(0)) {
            CONNECT_RET_PROTOCOL_INFO -> {
                // Sony's app reads 4 bytes from set 1 and 8 from set 2 (Gadgetbridge picks the set the same way).
                tableSet = when (p.size) {
                    SET1_PROTOCOL_REPLY -> 1
                    SET2_PROTOCOL_REPLY -> 2
                    else -> tableSet
                }
                outbox += SonyOut.table1(CONNECT_GET_DEVICE_INFO, MODEL_NAME)
                outbox += SonyOut.table1(CONNECT_GET_DEVICE_INFO, FW_VERSION)
                outbox += SonyOut.table1(CONNECT_GET_SUPPORT_FUNCTION, FIXED)
            }
            CONNECT_RET_DEVICE_INFO -> if (p.size >= 3 && p.size >= 3 + p.u8(2)) {
                val text = p.copyOfRange(3, 3 + p.u8(2)).decodeToString().trim()
                when (p.u8(1)) {
                    MODEL_NAME -> modelName = text.ifEmpty { null }
                    FW_VERSION -> firmware = text.ifEmpty { null }
                }
            }
            CONNECT_RET_SUPPORT_FUNCTION -> if (p.size >= 3) supportFunctions(p)
            else -> if (tableSet == 1) receiveSet1(p) else receiveSet2(p)
        }
    }

    /** The functions the earbuds have, then a read for each PairFree offers. */
    private fun supportFunctions(p: ByteArray) {
        val count = p.u8(2)
        // Set 1: one byte per function. Set 2: a function and its version.
        val width = if (tableSet == 1) 1 else 2
        if (p.size < 3 + count * width) return
        functions = (0 until count).map { p.u8(3 + it * width) }.toSet()
        val f = functions
        if (tableSet == 1) {
            batteryTypes = listOfNotNull(
                0.takeIf { SonyFunction.Set1.BATTERY_LEVEL in f },
                1.takeIf { SonyFunction.Set1.LEFT_RIGHT_BATTERY_LEVEL in f },
                2.takeIf { SonyFunction.Set1.CRADLE_BATTERY_LEVEL in f },
            )
        } else {
            batteryTypes = listOfNotNull(
                (if (SonyFunction.Set2.BATTERY_LEVEL_WITH_THRESHOLD in f) 8 else 0).takeIf {
                    SonyFunction.Set2.BATTERY_LEVEL_INDICATOR in f || SonyFunction.Set2.BATTERY_LEVEL_WITH_THRESHOLD in f
                },
                (if (SonyFunction.Set2.LR_BATTERY_LEVEL_WITH_THRESHOLD in f) 9 else 1).takeIf {
                    SonyFunction.Set2.LEFT_RIGHT_BATTERY_LEVEL_INDICATOR in f || SonyFunction.Set2.LR_BATTERY_LEVEL_WITH_THRESHOLD in f
                },
                (if (SonyFunction.Set2.CRADLE_BATTERY_LEVEL_WITH_THRESHOLD in f) 10 else 2).takeIf {
                    SonyFunction.Set2.CRADLE_BATTERY_LEVEL_INDICATOR in f || SonyFunction.Set2.CRADLE_BATTERY_LEVEL_WITH_THRESHOLD in f
                },
            )
        }
        outbox += batteryRequests()
        SonyNoise.typeFor(tableSet, f)?.let { outbox += SonyOut.table1(NCASM_GET_PARAM, it) }
        SonyEq.of(tableSet, f)?.let { e ->
            eq = e
            outbox += SonyOut.table1(EQEBB_GET_CAPABILITY, e.type)
            outbox += SonyOut.table1(EQEBB_GET_EXTENDED_INFO, e.type)
            outbox += SonyOut.table1(EQEBB_GET_PARAM, e.type)
        }
        if (tableSet == 1) {
            if (SonyFunction.Set1.UPSCALING in f) outbox += SonyOut.table1(AUDIO_GET_PARAM, S1_UPSCALING)
            if (SonyFunction.Set1.CONTROL_BY_WEARING in f) outbox += SonyOut.table1(SYSTEM_GET_PARAM, S1_CONTROL_BY_WEARING)
            if (SonyFunction.Set1.SMART_TALKING_MODE in f) outbox += SonyOut.table1(SYSTEM_GET_PARAM, S1_SMART_TALKING)
            if (SonyFunction.Set1.VOICE_GUIDANCE in f) outbox += SonyOut.table2(S1T2.VOICE_GUIDANCE_GET_PARAM, VOICE_GUIDANCE_SETTING, ON_OFF_DETAIL)
        } else {
            if (SonyFunction.Set2.UPSCALING_AUTO_OFF in f) outbox += SonyOut.table1(AUDIO_GET_PARAM, S2_UPSCALING)
            if (SonyFunction.Set2.PLAYBACK_CONTROL_BY_WEARING_REMOVING_HEADPHONE_ON_OFF in f) outbox += SonyOut.table1(SYSTEM_GET_PARAM, S2_CONTROL_BY_WEARING)
            speakToChatType = when {
                SonyFunction.Set2.SMART_TALKING_MODE_TYPE2 in f -> S2_SMART_TALKING_TYPE2
                SonyFunction.Set2.SMART_TALKING_MODE_TYPE1 in f -> S2_SMART_TALKING_TYPE1
                else -> null
            }
            speakToChatType?.let { outbox += SonyOut.table1(SYSTEM_GET_PARAM, it) }
        }
    }

    private fun receiveSet1(p: ByteArray) {
        when (p.u8(0)) {
            S1.COMMON_RET_BATTERY_LEVEL, S1.COMMON_NTFY_BATTERY_LEVEL -> battery(p)
            NCASM_RET_PARAM, NCASM_NTFY_PARAM -> SonyNoise.of(1, p)?.let { noise = it }
            AUDIO_RET_PARAM, AUDIO_NTFY_PARAM ->
                // Upscaling: its setting type, then off (0) or auto (1).
                if (p.size == 4 && p.u8(1) == S1_UPSCALING) dsee = p.u8(3) == 1
            SYSTEM_RET_PARAM, SYSTEM_NTFY_PARAM -> if (p.size == 4) when (p.u8(1)) {
                // Control by wearing: its setting type, then 1 for on. Speak-to-chat: its parameter, then 1 for on.
                S1_CONTROL_BY_WEARING -> pauseWhenOff = p.u8(3) == 1
                S1_SMART_TALKING -> if (p.u8(2) == S1_MODE_ON_OFF) speakToChat = p.u8(3) == 1
            }
            else -> readEq(p)
        }
    }

    private fun receiveSet2(p: ByteArray) {
        when (p.u8(0)) {
            S2.POWER_RET_STATUS, S2.POWER_NTFY_STATUS -> battery(p)
            NCASM_RET_PARAM, NCASM_NTFY_PARAM -> SonyNoise.of(2, p)?.let { noise = it }
            // Upscaling: off (0) or auto (1).
            AUDIO_RET_PARAM, AUDIO_NTFY_PARAM -> if (p.size == 3 && p.u8(1) == S2_UPSCALING) dsee = p.u8(2) == 1
            SYSTEM_RET_PARAM, SYSTEM_NTFY_PARAM -> when {
                // Set 2's on and off are 0 and 1 (Sony's app: OnOffSettingValue, ProtocolOnOff).
                p.size == 3 && p.u8(1) == S2_CONTROL_BY_WEARING -> pauseWhenOff = p.u8(2) == ON_V2
                p.size == 4 && p.u8(1) == speakToChatType -> {
                    speakToChat = p.u8(2) == ON_V2
                    speakToChatPreview = p.u8(3)
                }
            }
            else -> readEq(p)
        }
    }

    private fun readEq(p: ByteArray) {
        val e = eq ?: return
        e.read(p)?.let { eq = it }
    }

    /** One battery reply: the type, then one level and charging byte, or the left's and the right's. */
    private fun battery(p: ByteArray) {
        if (p.size < 4) return
        val type = p.u8(1)
        fun level(at: Int, dual: Boolean): Int? = p.u8(at).takeIf { it in 0..100 && !(dual && it == 0) }
        val values = when (type) {
            0, 8 -> mapOf(Command.BATTERY_LEFT to level(2, dual = false))
            1, 9 -> if (p.size >= 6) mapOf(Command.BATTERY_LEFT to level(2, dual = true), Command.BATTERY_RIGHT to level(4, dual = true)) else return
            2, 10 -> mapOf(Command.BATTERY_CASE to level(2, dual = false))
            else -> return
        }
        state = state.copy(values = state.values + values.mapValues { Value.Number(it.value) })
    }

    /** Puts the noise mode and the firmware where the shared screens read them. */
    private fun refresh() {
        val values = buildMap {
            firmware?.let { put(Command.FIRMWARE_VERSION, Value.Text("${modelName ?: model?.name ?: "Sony"}_$it")) }
            noise?.takeIf { it.hasNoiseCancelling && it.hasAmbient }?.let { put(Command.ANC_MODE, Value.Number(it.mode?.let(::ancNumberOf))) }
        }
        state = state.copy(values = state.values + values)
    }

    /** Every setting the earbuds have beyond the shared screens. */
    public val groups: List<SettingGroup>
        get() = listOfNotNull(
            group("noise", SettingTab.HOME, noiseRows()),
            group(
                "sound", SettingTab.SOUND,
                listOfNotNull(
                    eq?.takeIf { it.customizable }?.clearBass?.let { Setting.Level("clear_bass", it, -eq!!.center, eq!!.center) },
                    dsee?.let { Setting.Toggle("dsee", it) },
                ),
            ),
            group(
                "connection", SettingTab.DEVICE,
                listOfNotNull(
                    pauseWhenOff?.let { Setting.Toggle("pause_taken_off", it) },
                    voiceGuidance?.let { Setting.Toggle("voice_guidance", it) },
                ),
            ),
        )

    private fun noiseRows(): List<Setting> = buildList {
        val n = noise
        if (n != null) {
            if (!n.hasAmbient) add(Setting.Toggle("anc", n.mode == SonyNoise.Mode.NOISE_CANCELLING))
            if (!n.hasNoiseCancelling) add(Setting.Toggle("ambient_sound", n.mode == SonyNoise.Mode.AMBIENT))
            n.wind?.let { add(Setting.Toggle("wind_reduction", it, enabled = n.mode == SonyNoise.Mode.NOISE_CANCELLING)) }
            if (n.hasAmbient) {
                n.level?.let { add(Setting.Level("ambient_volume", it.coerceIn(0, AMBIENT_MAX), 0, AMBIENT_MAX)) }
                n.voice?.let { add(Setting.Toggle("voice_focus", it)) }
            }
        }
        speakToChat?.let { add(Setting.Toggle("speak_to_chat", it)) }
    }

    private fun group(key: String, tab: SettingTab, settings: List<Setting>) = settings.takeIf { it.isNotEmpty() }?.let { SettingGroup(key, tab, it) }

    /**
     * The messages for a change from the screens, or null when the earbuds do not have the setting.
     * The change shows in [groups] and [state] at once.
     */
    public fun change(change: SettingChange): List<SonyOut>? {
        val setting = groups.flatMap { it.settings }.firstOrNull { it.key == change.key } ?: return null
        val out: SonyOut = when (change) {
            is SettingChange.Toggle -> {
                if (setting !is Setting.Toggle || !setting.enabled) return null
                val on = change.on
                when (change.key) {
                    "anc" -> noiseSet(mode = if (on) SonyNoise.Mode.NOISE_CANCELLING else SonyNoise.Mode.OFF)
                    "ambient_sound" -> noiseSet(mode = if (on) SonyNoise.Mode.AMBIENT else SonyNoise.Mode.OFF)
                    "wind_reduction" -> noiseSet(wind = on)
                    "voice_focus" -> noiseSet(voice = on)
                    "speak_to_chat" -> speakToChat(on)
                    "dsee" -> if (tableSet == 1) SonyOut.table1(AUDIO_SET_PARAM, S1_UPSCALING, S1_AUTO_OFF, if (on) 1 else 0)
                    else SonyOut.table1(AUDIO_SET_PARAM, S2_UPSCALING, if (on) 1 else 0)
                    "pause_taken_off" -> if (tableSet == 1) SonyOut.table1(SYSTEM_SET_PARAM, S1_CONTROL_BY_WEARING, S1_ON_OFF, if (on) 1 else 0)
                    else SonyOut.table1(SYSTEM_SET_PARAM, S2_CONTROL_BY_WEARING, if (on) ON_V2 else OFF_V2)
                    "voice_guidance" -> SonyOut.table2(S1T2.VOICE_GUIDANCE_SET_PARAM, VOICE_GUIDANCE_SETTING, ON_OFF_DETAIL, if (on) 1 else 0)
                    else -> return null
                }?.also {
                    when (change.key) {
                        "speak_to_chat" -> speakToChat = on
                        "dsee" -> dsee = on
                        "pause_taken_off" -> pauseWhenOff = on
                        "voice_guidance" -> voiceGuidance = on
                    }
                } ?: return null
            }
            is SettingChange.Level -> {
                if (setting !is Setting.Level) return null
                val v = change.value.coerceIn(setting.min, setting.max)
                when (change.key) {
                    "ambient_volume" -> noiseSet(level = v)
                    "clear_bass" -> eqSet(eq?.setCurve(clearBass = v))
                    else -> return null
                } ?: return null
            }
            is SettingChange.Choice, is SettingChange.Press -> return null
        }
        refresh()
        return listOf(out)
    }

    private fun noiseSet(mode: SonyNoise.Mode? = null, level: Int? = null, voice: Boolean? = null, wind: Boolean? = null): SonyOut? {
        val n = noise ?: return null
        val out = n.set(mode, level, voice, wind) ?: return null
        noise = n.after(out)
        return out
    }

    private fun speakToChat(on: Boolean): SonyOut? = if (tableSet == 1) {
        SonyOut.table1(SYSTEM_SET_PARAM, S1_SMART_TALKING, S1_MODE_ON_OFF, if (on) 1 else 0)
    } else {
        // Mode, then preview, as Sony's app writes them; the preview stays as the earbuds last said, else off.
        SonyOut.table1(SYSTEM_SET_PARAM, speakToChatType ?: return null, if (on) ON_V2 else OFF_V2, speakToChatPreview)
    }

    private fun eqSet(out: SonyOut?): SonyOut? {
        out ?: return null
        eq = eq?.after(out)
        return out
    }

    /** The noise mode from the shared screens, on earbuds with both noise cancelling and ambient sound. */
    public fun set(command: Command, value: Int): List<SonyOut>? {
        if (command != Command.ANC_MODE) return null
        noise?.takeIf { it.hasNoiseCancelling && it.hasAmbient } ?: return null
        val mode = modeOf(value) ?: return null
        val out = noiseSet(mode = mode) ?: return null
        refresh()
        state = state.expect(command, value)
        return listOf(out)
    }

    /** Selects one of the earbuds' EQ presets. */
    public fun eqPreset(id: Int): List<SonyOut>? {
        val e = eq ?: return null
        if (e.presets.none { it.first == id }) return null
        return listOfNotNull(eqSet(e.selectPreset(id)))
    }

    /** Puts a curve of the user's own on the earbuds: [gains] from flat, one per frequency band. */
    public fun eqCurve(gains: List<Int>): List<SonyOut>? {
        val e = eq ?: return null
        if (gains.size != e.frequencies.size) return null
        return listOfNotNull(eqSet(e.setCurve(gains = gains)))
    }

    /** False for what PairFree never sends: firmware update, test and log commands. */
    public fun allowed(out: SonyOut): Boolean = out.command !in when {
        tableSet == 1 && out.table == 1 -> S1.NEVER_SEND
        tableSet == 1 -> S1T2.NEVER_SEND
        out.table == 1 -> S2.NEVER_SEND
        else -> SonyCommand.Set2Table2.NEVER_SEND
    }

    private data class Snapshot(val state: EarbudState, val groups: List<SettingGroup>)

    private fun Int.hex2(): String = toString(16).padStart(2, '0')

    public companion object {
        private val S1 = SonyCommand.Set1
        private val S1T2 = SonyCommand.Set1Table2
        private val S2 = SonyCommand.Set2

        // The commands both table sets number alike (data/sony/commands.json).
        private const val CONNECT_GET_PROTOCOL_INFO = SonyCommand.Set1.CONNECT_GET_PROTOCOL_INFO
        private const val CONNECT_RET_PROTOCOL_INFO = SonyCommand.Set1.CONNECT_RET_PROTOCOL_INFO
        private const val CONNECT_GET_DEVICE_INFO = SonyCommand.Set1.CONNECT_GET_DEVICE_INFO
        private const val CONNECT_RET_DEVICE_INFO = SonyCommand.Set1.CONNECT_RET_DEVICE_INFO
        private const val CONNECT_GET_SUPPORT_FUNCTION = SonyCommand.Set1.CONNECT_GET_SUPPORT_FUNCTION
        private const val CONNECT_RET_SUPPORT_FUNCTION = SonyCommand.Set1.CONNECT_RET_SUPPORT_FUNCTION
        private const val NCASM_GET_PARAM = SonyCommand.Set1.NCASM_GET_PARAM
        private const val NCASM_RET_PARAM = SonyCommand.Set1.NCASM_RET_PARAM
        private const val NCASM_NTFY_PARAM = SonyCommand.Set1.NCASM_NTFY_PARAM
        private const val EQEBB_GET_CAPABILITY = SonyCommand.Set1.EQEBB_GET_CAPABILITY
        private const val EQEBB_GET_PARAM = SonyCommand.Set1.EQEBB_GET_PARAM
        private const val EQEBB_GET_EXTENDED_INFO = SonyCommand.Set1.EQEBB_GET_EXTENDED_INFO
        private const val AUDIO_GET_PARAM = SonyCommand.Set1.AUDIO_GET_PARAM
        private const val AUDIO_RET_PARAM = SonyCommand.Set1.AUDIO_RET_PARAM
        private const val AUDIO_SET_PARAM = SonyCommand.Set1.AUDIO_SET_PARAM
        private const val AUDIO_NTFY_PARAM = SonyCommand.Set1.AUDIO_NTFY_PARAM
        private const val SYSTEM_GET_PARAM = SonyCommand.Set1.SYSTEM_GET_PARAM
        private const val SYSTEM_RET_PARAM = SonyCommand.Set1.SYSTEM_RET_PARAM
        private const val SYSTEM_SET_PARAM = SonyCommand.Set1.SYSTEM_SET_PARAM
        private const val SYSTEM_NTFY_PARAM = SonyCommand.Set1.SYSTEM_NTFY_PARAM

        /** The inquired type of the connect requests: always 0. */
        private const val FIXED = 0
        private const val MODEL_NAME = 1
        private const val FW_VERSION = 2
        private const val SET1_PROTOCOL_REPLY = 4
        private const val SET2_PROTOCOL_REPLY = 8

        // Set 1's types (Sony's app: AudioInquiredType, SystemInquiredType and their parameters).
        private const val S1_UPSCALING = 2
        private const val S1_AUTO_OFF = 0
        private const val S1_CONTROL_BY_WEARING = 3
        private const val S1_ON_OFF = 0
        private const val S1_SMART_TALKING = 5
        private const val S1_MODE_ON_OFF = 1
        private const val VOICE_GUIDANCE_SETTING = 1
        private const val ON_OFF_DETAIL = 1

        // Set 2's types; on is 0 and off 1 there.
        private const val S2_UPSCALING = 1
        private const val S2_CONTROL_BY_WEARING = 1
        private const val S2_SMART_TALKING_TYPE1 = 2
        private const val S2_SMART_TALKING_TYPE2 = 0x0C
        private const val ON_V2 = 0
        private const val OFF_V2 = 1

        /** The highest ambient sound level Gadgetbridge reads from Sony's earbuds. */
        private const val AMBIENT_MAX = 20

        // The noise modes by their names in data/soundpeats/commands.json, so no number is retyped.
        private fun ancNumber(name: String): Int = Command.ANC_MODE.labels.entries.first { it.value == name }.key

        private fun ancNumberOf(mode: SonyNoise.Mode): Int = when (mode) {
            SonyNoise.Mode.OFF -> ancNumber("off")
            SonyNoise.Mode.NOISE_CANCELLING -> ancNumber("anc")
            SonyNoise.Mode.AMBIENT -> ancNumber("transparency")
        }

        private fun modeOf(value: Int): SonyNoise.Mode? = when (value) {
            ancNumber("off") -> SonyNoise.Mode.OFF
            ancNumber("anc") -> SonyNoise.Mode.NOISE_CANCELLING
            ancNumber("transparency") -> SonyNoise.Mode.AMBIENT
            else -> null
        }
    }
}
