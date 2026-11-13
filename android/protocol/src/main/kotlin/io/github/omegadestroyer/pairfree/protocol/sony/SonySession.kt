package io.github.omegadestroyer.pairfree.protocol.sony

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.PowerActions
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value

/**
 * One connection to Sony earbuds or headphones, shown on PairFree's screens (docs/sony/PROTOCOL.md): Sony's talk
 * itself, with no adapter between it and the conversation loop.
 * It asks which table set they speak, their model name and firmware, and which functions they have;
 * then it reads each function PairFree offers, and every reply or notification updates [state] (the
 * battery, the noise mode, the firmware) and [groups] (everything else). Each change becomes the
 * message Sony's app sends for it and shows at once; the earbuds' notification confirms it.
 *
 * [tableSet] starts as the service the channel opened says (1 or 2) and follows the earbuds' first
 * reply, whose length says which they speak.
 */
public class SonySession(tableSet: Int, public val model: SonyModel? = null) : SessionTalk<SonyOut>() {
    public var tableSet: Int = tableSet
        private set

    /** What the shared screens show. */
    override var state: EarbudState = EarbudState()
        private set

    /** The frames' sequence numbers and acknowledgements, and the split of the channel's bytes into messages. */
    private val link = SonyLink()
    private val parser = SonyParser()

    /**
     * Each message waits 750 ms for its acknowledgement and goes again up to ten times, as Sony's app sends them
     * (docs/sony/PROTOCOL.md section 2). A message never acknowledged leaves the two sides' sequence numbers in doubt, so
     * the channel closes and opens again, as Sony's app does.
     */
    override val pacing: Pacing = Pacing(replyMs = 750, resends = 10, closeWhenUnanswered = true)

    /** The battery, read again now and then; changes arrive as notifications. */
    override fun poll(): List<SonyOut> = batteryRequests()

    override fun bytes(message: SonyOut): ByteArray = link.frame(message)

    /** Every message from the earbuds is acknowledged at once; an acknowledgement of theirs answers the message waiting. */
    override fun feed(bytes: ByteArray): Fed {
        var changed = false
        val acks = ArrayList<Any>()
        val now = ArrayList<ByteArray>()
        for (message in parser.feed(bytes)) guarded {
            log?.invoke("recv $message")
            if (message.dataType == SonyWire.ACK) {
                if (link.acked(message)) acks += Unit
            } else {
                val (ack, fresh) = link.received(message)
                now += ack
                if (fresh && receive(message)) changed = true
            }
        }
        return Fed(changed, acks, now)
    }

    /** The model name the earbuds gave, such as `WH-1000XM4`. */
    public var modelName: String? = null
        private set

    public var firmware: String? = null
        private set

    /** The functions the earbuds listed (table 1); see data/sony/functions.json. */
    public var functions: Set<Int> = emptySet()
        private set

    /** The functions set 2 earbuds listed in table 2 (voice guidance among them); set 1 has no such list. */
    public var functions2: Set<Int> = emptySet()
        private set

    public var noise: SonyNoise? = null
        private set

    /** The equalizer, once the earbuds listed one; what it holds fills in as they answer. */
    override var eq: SonyEq? = null
    private var ebb: SonyEbb? = null
    private var effect: SonyEffect? = null
    private var ultAssign: SonyUltAssign? = null
        private set

    private var batteryTypes: List<Int> = emptyList()
    private var dsee: Boolean? = null
    private var pauseWhenOff: Boolean? = null
    private var speakToChat: Boolean? = null
    private var speakToChatPreview = OFF_V2
    private var speakToChatType: Int? = null
    private var voiceGuidance: Boolean? = null
    private var voiceGuidanceType: Int? = null
    private var voiceGuidanceLanguage: Int? = null
    private var autoOff: SonyAutoOff? = null
    private var keys: SonyKeys? = null
    private var connectionType: Int? = null
    private var connectionMode: Int? = null
    private val switches = linkedMapOf<String, Boolean>()   // set 2's simple switches the earbuds answered, by key
    private var bgm: Pair<Boolean, Int>? = null             // the background music effect: on, and the room size
    private var guidanceVolume: Int? = null
    private var guidanceVolumeType: Int? = null
    private var maxVolume: Int? = null          // the volume limit's step, 0 to 4 (SafeListeningInquiredType.MAX_VOL_LV_LIMIT)
    private var optimizerType: Int? = null      // the optimizer the earbuds have, by its type; null without one
    private var optimizing = false
    private val generalTitles = linkedMapOf<Int, String>()     // each general setting the earbuds described as a switch: its title
    private val generalValues = linkedMapOf<Int, Boolean>()
    private val outbox = mutableListOf<SonyOut>()

    /** The last payload of each kind the earbuds sent, for the device report: by table, command and type. */
    public val replies: Map<String, ByteArray> get() = _replies
    private val _replies = linkedMapOf<String, ByteArray>()

    /** What to send on opening the channel: the protocol request, which every table set answers the same way. */
    override fun start(): List<SonyOut> = listOf(SonyOut.table1(CONNECT_GET_PROTOCOL_INFO, FIXED))

    /** What the session wants sent next: the reads that follow the earbuds' answers. */
    override fun takeOutbox(): List<SonyOut> = outbox.toList().also { outbox.clear() }

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
            if (tableSet == 2) receiveSet2Table2(p)
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
                // Set 2 lists the functions of its second table on their own (voice guidance among them).
                if (tableSet == 2) outbox += SonyOut.table2(S2T2.CONNECT_GET_SUPPORT_FUNCTION, FIXED)
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
            // Type 4 has a capability too (Sony's parser reads it as the plain preset EQ's); the WF-1000XM6 recording did not show the app asking, so the steps
            // start at 13 (flat 6, as in the recording) and the answer, if the earbuds give one, replaces them and lists the presets.
            outbox += SonyOut.table1(EQEBB_GET_CAPABILITY, e.type)
            outbox += SonyOut.table1(EQEBB_GET_EXTENDED_INFO, e.type)
            outbox += SonyOut.table1(EQEBB_GET_PARAM, e.type)
        }
        // The extra bass level, the sound effect and the ULT button's effects share the EQEBB command (SonyEffects.kt).
        val ebbType = if (tableSet == 1) SonyEbb.TYPE_SET1.takeIf { SonyFunction.Set1.EBB in f } else SonyEbb.TYPE_SET2.takeIf { SonyFunction.Set2.EBB in f }
        ebbType?.let {
            ebb = SonyEbb(it)
            outbox += SonyOut.table1(EQEBB_GET_CAPABILITY, it)
            outbox += SonyOut.table1(EQEBB_GET_PARAM, it)
        }
        if (tableSet == 2 && SonyFunction.Set2.SOUND_EFFECT in f) {
            effect = SonyEffect()
            outbox += SonyOut.table1(EQEBB_GET_CAPABILITY, SonyEffect.TYPE)
            outbox += SonyOut.table1(EQEBB_GET_PARAM, SonyEffect.TYPE)
        }
        if (tableSet == 2 && SonyFunction.Set2.ULT_SOUND_EFFECT_ASSIGN in f) {
            ultAssign = SonyUltAssign()
            outbox += SonyOut.table1(EQEBB_GET_CAPABILITY, SonyUltAssign.TYPE)
            outbox += SonyOut.table1(EQEBB_GET_PARAM, SonyUltAssign.TYPE)
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
            for (s in S2_SWITCHES.filter { it.table == 1 && it.function in f }) outbox += SonyOut.table1(s.get, s.type)
            if (SonyFunction.Set2.BGM_MODE_SMALL_MIDDLE_LARGE in f) outbox += SonyOut.table1(AUDIO_GET_PARAM, S2_BGM)
        }
        SonyAutoOff.of(tableSet, f)?.let { a ->
            autoOff = a
            outbox += a.requests
        }
        SonyKeys.of(tableSet, f)?.let { k ->
            keys = k
            outbox += k.requests
        }
        connectionType = when {
            tableSet == 1 -> S1_CONNECTION_MODE.takeIf { SonyFunction.Set1.CONNECTION_MODE in f }
            SonyFunction.Set2.CONNECTION_MODE_SOUND_QUALITY_SOUND_WITH_LDAC_STATUS_QUALITY_CONNECTION_QUALITY in f -> S2_CONNECTION_MODE_LDAC
            SonyFunction.Set2.CONNECTION_MODE_SOUND_QUALITY_CONNECTION_QUALITY in f -> S2_CONNECTION_MODE
            else -> null
        }
        connectionType?.let { outbox += SonyOut.table1(AUDIO_GET_PARAM, it) }
        // The noise cancelling optimizer: set 1's one type, set 2's by which variant the earbuds list (OptInquiredType).
        optimizerType = when {
            tableSet == 1 -> S1_OPTIMIZER.takeIf { SonyFunction.Set1.NC_OPTIMIZER in f }
            SonyFunction.Set2.NC_OPTIMIZER_PERSONAL_BAROMETRIC in f -> 0
            SonyFunction.Set2.NC_OPTIMIZER_PERSONAL in f -> 1
            SonyFunction.Set2.NC_OPTIMIZER_BAROMETRIC in f -> 2
            else -> null
        }
        // The general settings: what each is (its title, in English), then its value (Sony's app: GENERAL_SETTING1 to 4).
        val general = if (tableSet == 1) {
            listOf(SonyFunction.Set1.GENERAL_SETTING1, SonyFunction.Set1.GENERAL_SETTING2, SonyFunction.Set1.GENERAL_SETTING3)
        } else {
            listOf(SonyFunction.Set2.GENERAL_SETTING_1, SonyFunction.Set2.GENERAL_SETTING_2, SonyFunction.Set2.GENERAL_SETTING_3, SonyFunction.Set2.GENERAL_SETTING_4)
        }
        general.forEachIndexed { i, function ->
            if (function in f) outbox += SonyOut.table1(GS_GET_CAPABILITY, GS_FIRST_TYPE + i, DISPLAY_ENGLISH)
        }
    }

    /**
     * A general setting's description. Set 1: the type, the title's format, length and text, the summary's length and
     * text, then the setting type (1 a switch). Set 2: the type, the setting type (0 a switch), then the title and
     * summary the same way. PairFree takes the switches; a list type is another page of Sony's app.
     */
    private fun readGeneralCapability(p: ByteArray) {
        if (p.size < 2) return
        val type = p.u8(1)
        val titleAt = if (tableSet == 1) 2 else 3
        if (p.size < titleAt + 2) return
        val format = p.u8(titleAt)
        val length = p.u8(titleAt + 1)
        if (p.size < titleAt + 2 + length + 1) return
        val title = p.copyOfRange(titleAt + 2, titleAt + 2 + length).decodeToString().trim()
        val switch = if (tableSet == 1) {
            val summary = p.u8(titleAt + 2 + length)
            val at = titleAt + 3 + length + summary
            at < p.size && p.u8(at) == GS1_BOOLEAN
        } else {
            p.u8(2) == GS2_BOOLEAN
        }
        if (!switch || title.isEmpty()) return
        generalTitles[type] = if (format == (if (tableSet == 1) GS1_ENUM_NAME else GS2_ENUM_NAME)) title else RAW_TITLE + title
        outbox += SonyOut.table1(GS_GET_PARAM, type)
    }

    /** A general setting's value: the type, the setting type, then on (set 1: 1, set 2: 0) or off. */
    private fun readGeneralParam(p: ByteArray) {
        if (p.size != 4 || p.u8(1) !in generalTitles) return
        generalValues[p.u8(1)] = p.u8(3) == if (tableSet == 1) 1 else ON_V2
    }

    /** The optimizer's status: its type, enabled, then idle (0) or measuring (1 personal, 2 air pressure). */
    private fun readOptimizer(p: ByteArray) {
        if (p.size == 4 && p.u8(1) == optimizerType) optimizing = p.u8(3) != OPT_IDLE
    }

    /** The general setting that is multipoint, which goes on the shared screens' two devices switch. */
    private val multipointType: Int? get() = generalTitles.entries.firstOrNull { it.value == GS_MULTIPOINT }?.key

    /** Set 2's second table: the functions it lists, and voice guidance. */
    private fun receiveSet2Table2(p: ByteArray) {
        when (p.u8(0)) {
            S2T2.CONNECT_RET_SUPPORT_FUNCTION -> {
                // A function and its version each, as in table 1.
                if (p.size < 3 || p.size < 3 + p.u8(2) * 2) return
                functions2 = (0 until p.u8(2)).map { p.u8(3 + it * 2) }.toSet()
                val f = SonyFunction.Set2Table2
                voiceGuidanceType = when {
                    f.VOICE_GUIDANCE_SETTING_ONLY_ON_OFF_SWITCH in functions2 -> S2_GUIDANCE_ON_OFF
                    f.VOICE_GUIDANCE_SETTING_SUPPORT_LANGUAGE_SWITCH in functions2 -> S2_GUIDANCE_LANGUAGE
                    f.VOICE_GUIDANCE_SETTING_MTK_TRANSFER_WITHOUT_DISCONNECTION_SUPPORT_LANGUAGE_SWITCH_AND_VOLUME_ADJUSTMENT in functions2 ||
                        f.VOICE_GUIDANCE_SETTING_MTK_TRANSFER_WITHOUT_DISCONNECTION_SUPPORT_LANGUAGE_SWITCH in functions2 -> S2_GUIDANCE_MTK_LANGUAGE
                    f.VOICE_GUIDANCE_SETTING_MTK_TRANSFER_WITHOUT_DISCONNECTION_NOT_SUPPORT_LANGUAGE_SWITCH in functions2 -> S2_GUIDANCE_MTK
                    else -> null
                }
                voiceGuidanceType?.let { outbox += SonyOut.table2(S2T2.VOICE_GUIDANCE_GET_PARAM, it) }
                for (s in S2_SWITCHES.filter { it.table == 2 && it.function in functions2 }) outbox += SonyOut.table2(s.get, s.type)
                guidanceVolumeType = when {
                    f.VOICE_GUIDANCE_VOLUME_SETTING_MTK_FIXED_TO_5_STEPS in functions2 -> S2_GUIDANCE_VOLUME_5
                    f.VOICE_GUIDANCE_SETTING_MTK_TRANSFER_WITHOUT_DISCONNECTION_SUPPORT_LANGUAGE_SWITCH_AND_VOLUME_ADJUSTMENT in functions2 -> S2_GUIDANCE_VOLUME
                    else -> null
                }
                guidanceVolumeType?.let { outbox += SonyOut.table2(S2T2.VOICE_GUIDANCE_GET_PARAM, it) }
                if (f.MAX_VOLUME_LEVEL_LIMIT in functions2) outbox += SonyOut.table2(S2T2.SAFE_LISTENING_GET_PARAM, MAX_VOL_LV_LIMIT)
            }
            S2T2.POWER_RET_PARAM, S2T2.POWER_NTFY_PARAM, S2T2.SYSTEM_RET_PARAM, S2T2.SYSTEM_NTFY_PARAM -> readSwitch(2, p)
            // The volume limit's step: the type, then 0 to 4 (Sony's app: SafeListeningInquiredType.MAX_VOL_LV_LIMIT).
            S2T2.SAFE_LISTENING_RET_PARAM, S2T2.SAFE_LISTENING_NTFY_PARAM ->
                if (p.size == 3 && p.u8(1) == MAX_VOL_LV_LIMIT && p.u8(2) in 0..MAX_VOLUME_STEPS - 1) maxVolume = p.u8(2)
            // The type, on (0) or off (1), and on two types the language, which a change to type 2 sends back.
            S2T2.VOICE_GUIDANCE_RET_PARAM, S2T2.VOICE_GUIDANCE_NTFY_PARAM -> when {
                p.size >= 3 && p.u8(1) == voiceGuidanceType -> {
                    voiceGuidance = p.u8(2) == ON_V2
                    if (p.size >= 4) voiceGuidanceLanguage = p.u8(3)
                }
                // The volume, -2 to 2, a signed byte (Sony's app: VoiceGuidanceInquiredType VOLUME and the five-step type).
                p.size >= 3 && p.u8(1) == guidanceVolumeType -> guidanceVolume = p[2].toInt()
                else -> readSwitch(2, p)
            }
        }
    }

    /** Automatic power-off and the keys' functions, from any reply about them; false when [p] is about neither. */
    private fun readControls(p: ByteArray): Boolean {
        autoOff?.read(p)?.let {
            autoOff = it
            return true
        }
        keys?.read(p)?.let {
            keys = it
            return true
        }
        return false
    }

    private fun receiveSet1(p: ByteArray) {
        when (p.u8(0)) {
            GS_RET_CAPABILITY -> readGeneralCapability(p)
            GS_RET_PARAM, GS_NTFY_PARAM -> readGeneralParam(p)
            OPT_RET_STATUS, OPT_NTFY_STATUS -> readOptimizer(p)
            S1.COMMON_RET_BATTERY_LEVEL, S1.COMMON_NTFY_BATTERY_LEVEL -> battery(p)
            NCASM_RET_PARAM, NCASM_NTFY_PARAM -> SonyNoise.of(1, p)?.let { noise = it }
            AUDIO_RET_PARAM, AUDIO_NTFY_PARAM -> when {
                // Upscaling: its setting type, then off (0) or auto (1).
                p.size == 4 && p.u8(1) == S1_UPSCALING -> dsee = p.u8(3) == 1
                // Connection mode: its setting type, then sound quality (0) or a stable connection (1).
                p.size == 4 && p.u8(1) == S1_CONNECTION_MODE && connectionType != null -> connectionMode = p.u8(3)
            }
            SYSTEM_RET_CAPABILITY -> readControls(p)
            SYSTEM_RET_PARAM, SYSTEM_NTFY_PARAM -> if (!readControls(p) && p.size == 4) when (p.u8(1)) {
                // Control by wearing: its setting type, then 1 for on. Speak-to-chat: its parameter, then 1 for on.
                S1_CONTROL_BY_WEARING -> pauseWhenOff = p.u8(3) == 1
                S1_SMART_TALKING -> if (p.u8(2) == S1_MODE_ON_OFF) speakToChat = p.u8(3) == 1
            }
            else -> readEq(p)
        }
    }

    private fun receiveSet2(p: ByteArray) {
        when (p.u8(0)) {
            GS_RET_CAPABILITY -> readGeneralCapability(p)
            GS_RET_PARAM, GS_NTFY_PARAM -> readGeneralParam(p)
            OPT_RET_STATUS, OPT_NTFY_STATUS -> readOptimizer(p)
            S2.POWER_RET_STATUS, S2.POWER_NTFY_STATUS -> battery(p)
            S2.POWER_RET_CAPABILITY -> readControls(p)
            S2.POWER_RET_PARAM, S2.POWER_NTFY_PARAM -> if (!readControls(p)) readSwitch(1, p)
            S2.SAR_AUTO_PLAY_RET_PARAM, S2.SAR_AUTO_PLAY_NTFY_PARAM -> readSwitch(1, p)
            NCASM_RET_PARAM, NCASM_NTFY_PARAM -> SonyNoise.of(2, p)?.let { noise = it }
            AUDIO_RET_PARAM, AUDIO_NTFY_PARAM -> when {
                // Upscaling: off (0) or auto (1).
                p.size == 3 && p.u8(1) == S2_UPSCALING -> dsee = p.u8(2) == 1
                // Connection mode: sound quality (0), a stable connection (1) or, on some, low latency (2).
                p.size == 3 && p.u8(1) == connectionType -> connectionMode = p.u8(2)
                // The background music effect: on or off, then the room size (0 small, 1 middle, 2 large).
                p.size == 4 && p.u8(1) == S2_BGM -> bgm = (p.u8(2) == ON_V2) to p.u8(3)
                else -> readSwitch(1, p)
            }
            SYSTEM_RET_CAPABILITY -> readControls(p)
            SYSTEM_RET_PARAM, SYSTEM_NTFY_PARAM -> if (!readControls(p)) when {
                // Set 2's on and off are 0 and 1 (Sony's app: OnOffSettingValue, ProtocolOnOff).
                p.size == 3 && p.u8(1) == S2_CONTROL_BY_WEARING -> pauseWhenOff = p.u8(2) == ON_V2
                p.size == 4 && p.u8(1) == speakToChatType -> {
                    speakToChat = p.u8(2) == ON_V2
                    speakToChatPreview = p.u8(3)
                }
                else -> readSwitch(1, p)
            }
            else -> readEq(p)
        }
    }

    /** One of set 2's simple switches: its type, then on (0) or off (1) (Sony's OnOffSettingValue). */
    private fun readSwitch(table: Int, p: ByteArray) {
        if (tableSet != 2 || p.size != 3) return
        val s = S2_SWITCHES.firstOrNull { it.table == table && it.type == p.u8(1) && p.u8(0) in it.replies } ?: return
        switches[s.key] = p.u8(2) == ON_V2
    }

    private fun readEq(p: ByteArray) {
        ebb?.read(p)?.let { ebb = it; return }
        effect?.read(p)?.let { effect = it; return }
        ultAssign?.read(p)?.let { ultAssign = it; return }
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
            multipointType?.let { type -> generalValues[type]?.let { put(Command.MULTIPOINT, Value.Switch(it)) } }
        }
        state = state.copy(values = state.values + values)
    }

    /** Every setting the earbuds have beyond the shared screens. */
    override val groups: List<SettingGroup>
        get() = listOfNotNull(
            group("noise", SettingTab.HOME, noiseRows()),
            group(
                "sound", SettingTab.SOUND,
                listOfNotNull(
                    eq?.takeIf { it.customizable }?.clearBass?.let { Setting.Level("clear_bass", it, -eq!!.center, eq!!.center) },
                    eq?.ult?.let { Setting.Choice(ULT_KEY, ULT_MODES[it] ?: OFF, ULT_MODES.values.toList()) },
                    ebb?.takeIf { it.known }?.let { Setting.Level(EBB_KEY, it.level!!, it.min!!, it.max!!) },
                    effect?.takeIf { it.current != null && it.offered.isNotEmpty() }?.let { e ->
                        Setting.Choice(EFFECT_KEY, SonyEffectNames.BY_ID[e.current] ?: OFF, e.offered.mapNotNull { SonyEffectNames.BY_ID[it] })
                    },
                    dsee?.let { Setting.Toggle("dsee", it) },
                    bgm?.let { (on, room) -> Setting.Choice(BGM_KEY, if (on) BGM_ROOMS[room] ?: OFF else OFF, listOf(OFF) + BGM_ROOMS.values) },
                ) + switchRows(SettingTab.SOUND),
            ),
            group("touch", SettingTab.CONTROLS, keys?.settings.orEmpty()),
            group(
                "ult_button", SettingTab.CONTROLS,
                ultAssign?.takeIf { it.items.isNotEmpty() && it.min != null && it.max != null }?.items.orEmpty()
                    .map { (id, on) -> Setting.Toggle(ULT_BUTTON_KEY + SonyEffectNames.BY_ID.getValue(id), on) },
            ),
            group(
                "connection", SettingTab.DEVICE,
                listOfNotNull(
                    connectionMode?.let { m ->
                        Setting.Choice(CONNECTION_KEY, CONNECTION_OPTIONS[m], (listOf(SOUND_QUALITY, STABLE) + m).distinct().mapNotNull { CONNECTION_OPTIONS[it] })
                    },
                    autoOff?.setting,
                    pauseWhenOff?.let { Setting.Toggle("pause_taken_off", it) },
                    voiceGuidance?.let { Setting.Toggle("voice_guidance", it) },
                    maxVolume?.let { Setting.Level(MAX_VOLUME_KEY, it + 1, 1, MAX_VOLUME_STEPS) },
                    guidanceVolume?.let { Setting.Level(GUIDANCE_VOLUME_KEY, it.coerceIn(-GUIDANCE_VOLUME_MAX, GUIDANCE_VOLUME_MAX), -GUIDANCE_VOLUME_MAX, GUIDANCE_VOLUME_MAX) },
                ) + switchRows(SettingTab.DEVICE) + generalRows(),
            ),
            group(PowerActions.GROUP, SettingTab.DEVICE, listOfNotNull(Setting.Action(PowerActions.POWER_OFF).takeIf { hasPowerOff })),
        )

    /** The earbuds list power off (set 1's function 33, set 2's 35), which Sony's app offers as a button. */
    private val hasPowerOff: Boolean
        get() = if (tableSet == 1) SonyFunction.Set1.POWER_OFF in functions else SonyFunction.Set2.POWER_OFF in functions

    /** The general settings the earbuds described as switches, multipoint aside, keyed by their title. */
    private fun generalRows(): List<Setting> = generalTitles.entries.filter { it.value != GS_MULTIPOINT }.mapNotNull { (type, title) ->
        generalValues[type]?.let { Setting.Toggle(GS_KEY + title, it) }
    }

    private fun switchRows(tab: SettingTab): List<Setting> =
        S2_SWITCHES.filter { it.tab == tab }.mapNotNull { s -> switches[s.key]?.let { Setting.Toggle(s.key, it) } }

    private fun noiseRows(): List<Setting> = buildList {
        val n = noise
        if (n != null) {
            if (!n.hasAmbient) add(Setting.Toggle("anc", n.mode == SonyNoise.Mode.NOISE_CANCELLING))
            if (!n.hasNoiseCancelling) add(Setting.Toggle("ambient_sound", n.mode == SonyNoise.Mode.AMBIENT))
            n.wind?.let { add(Setting.Toggle("wind_reduction", it, enabled = n.mode == SonyNoise.Mode.NOISE_CANCELLING)) }
            if (n.hasAmbient) {
                // The ambient level and voice pass through do nothing outside ambient sound (GitHub issue #26): shown, greyed out.
                val ambient = n.mode == SonyNoise.Mode.AMBIENT
                n.level?.let { add(Setting.Level("ambient_volume", it.coerceIn(0, AMBIENT_MAX), 0, AMBIENT_MAX, enabled = ambient)) }
                n.voice?.let { add(Setting.Toggle("voice_focus", it, enabled = ambient)) }
            }
        }
        speakToChat?.let { add(Setting.Toggle("speak_to_chat", it)) }
        if (optimizerType != null) add(Setting.Action(OPTIMIZER_KEY, active = optimizing))
    }

    private fun group(key: String, tab: SettingTab, settings: List<Setting>) = settings.takeIf { it.isNotEmpty() }?.let { SettingGroup(key, tab, it) }

    /**
     * The messages for a change from the screens, or null when the earbuds do not have the setting.
     * The change shows in [groups] and [state] at once.
     */
    override fun change(change: SettingChange): List<SonyOut>? {
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
                    "voice_guidance" -> voiceGuidanceSet(on)
                    else -> ultButtonSet(change.key, on) ?: generalSet(change.key.removePrefix(GS_KEY).takeIf { change.key.startsWith(GS_KEY) }, on) ?: S2_SWITCHES.firstOrNull { it.key == change.key }?.let { s ->
                        switches[s.key] = on
                        val v = if (on) ON_V2 else OFF_V2
                        if (s.table == 1) SonyOut.table1(s.set, s.type, v) else SonyOut.table2(s.set, s.type, v)
                    }
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
                if (setting !is Setting.Level || !setting.enabled) return null
                val v = change.value.coerceIn(setting.min, setting.max)
                when (change.key) {
                    "ambient_volume" -> noiseSet(level = v)
                    "clear_bass" -> eqSet(eq?.setCurve(clearBass = v))
                    EBB_KEY -> ebb?.set(v)?.also { ebb = ebb?.after(v) }
                    MAX_VOLUME_KEY -> maxVolume?.let {
                        maxVolume = v - 1
                        SonyOut.table2(S2T2.SAFE_LISTENING_SET_PARAM, MAX_VOL_LV_LIMIT, v - 1)
                    }
                    // The volume, then whether to play a sample of it (Sony's app plays one: feedbackSound).
                    GUIDANCE_VOLUME_KEY -> guidanceVolumeType?.let { type ->
                        guidanceVolume = v
                        SonyOut.table2(S2T2.VOICE_GUIDANCE_SET_PARAM, type, v and 0xFF, ON_V2)
                    }
                    else -> return null
                } ?: return null
            }
            is SettingChange.Choice -> {
                if (setting !is Setting.Choice || change.option !in setting.options) return null
                when {
                    change.key == SonyAutoOff.KEY -> autoOff?.set(change.option)?.let { (out, next) ->
                        autoOff = next
                        out
                    }
                    change.key == CONNECTION_KEY -> connectionSet(change.option)
                    change.key == EFFECT_KEY -> SonyEffectNames.BY_ID.entries.firstOrNull { it.value == change.option }?.key?.let { id ->
                        effect?.set(id)?.also { effect = effect?.copy(current = id) }
                    }
                    change.key == ULT_KEY -> ULT_MODES.entries.firstOrNull { it.value == change.option }?.key?.let { eqSet(eq?.setUlt(it)) }
                    change.key == BGM_KEY -> {
                        val room = BGM_ROOMS.entries.firstOrNull { it.value == change.option }?.key
                        val previous = bgm?.second ?: 0
                        bgm = (room != null) to (room ?: previous)
                        SonyOut.table1(AUDIO_SET_PARAM, S2_BGM, if (room != null) ON_V2 else OFF_V2, room ?: previous)
                    }
                    else -> keys?.set(change.key, change.option)?.let { (out, next) ->
                        keys = next
                        out
                    }
                } ?: return null
            }
            // The screen asks the user to confirm first. Set 1: the fixed value, then user power off (tm0 m3); set 2:
            // power status type 3, power off, then user power off (yn0 b1).
            is SettingChange.Press -> when (change.key) {
                // Start, or cancel one under way: the type, enabled (0), then start (1) or cancel (0) (Sony's OPT_SET_STATUS).
                OPTIMIZER_KEY -> optimizerType?.let { type ->
                    val start = !optimizing
                    optimizing = start
                    SonyOut.table1(OPT_SET_STATUS, type, 0, if (start) 1 else 0)
                } ?: return null
                PowerActions.POWER_OFF -> if (tableSet == 1) SonyOut.table1(S1.COMMON_SET_POWER_OFF, FIXED, USER_POWER_OFF)
                else SonyOut.table1(S2.POWER_SET_STATUS, S2_POWER_OFF, USER_POWER_OFF)
                else -> return null
            }
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

    private fun voiceGuidanceSet(on: Boolean): SonyOut? = if (tableSet == 1) {
        SonyOut.table2(S1T2.VOICE_GUIDANCE_SET_PARAM, VOICE_GUIDANCE_SETTING, ON_OFF_DETAIL, if (on) 1 else 0)
    } else {
        // Set 2: the type and on (0) or off (1); the language-switch type also sends the language the earbuds use.
        val type = voiceGuidanceType
        val value = if (on) ON_V2 else OFF_V2
        when (type) {
            null -> null
            S2_GUIDANCE_LANGUAGE -> voiceGuidanceLanguage?.let { SonyOut.table2(S2T2.VOICE_GUIDANCE_SET_PARAM, type, value, it) }
            else -> SonyOut.table2(S2T2.VOICE_GUIDANCE_SET_PARAM, type, value)
        }
    }

    private fun connectionSet(option: String): SonyOut? {
        val type = connectionType ?: return null
        val mode = CONNECTION_OPTIONS.entries.firstOrNull { it.value == option }?.key ?: return null
        connectionMode = mode
        return if (tableSet == 1) SonyOut.table1(AUDIO_SET_PARAM, type, S1_SOUND_CONNECTION, mode) else SonyOut.table1(AUDIO_SET_PARAM, type, mode)
    }

    /** One of the ULT button's effects turned on or off. */
    private fun ultButtonSet(key: String, on: Boolean): SonyOut? {
        if (!key.startsWith(ULT_BUTTON_KEY)) return null
        val id = SonyEffectNames.BY_ID.entries.firstOrNull { ULT_BUTTON_KEY + it.value == key }?.key ?: return null
        val out = ultAssign?.set(id, on) ?: return null
        ultAssign = ultAssign?.after(id, on)
        return out
    }

    private fun eqSet(out: SonyOut?): SonyOut? {
        out ?: return null
        eq = eq?.after(out)
        return out
    }

    /** A general setting by its title: the type, the switch type, then on or off as the set writes them. */
    private fun generalSet(title: String?, on: Boolean): SonyOut? {
        val type = generalTitles.entries.firstOrNull { it.value == title }?.key ?: return null
        generalValues[type] = on
        return if (tableSet == 1) SonyOut.table1(GS_SET_PARAM, type, GS1_BOOLEAN, if (on) 1 else 0)
        else SonyOut.table1(GS_SET_PARAM, type, GS2_BOOLEAN, if (on) ON_V2 else OFF_V2)
    }

    /** The noise mode from the shared screens, on earbuds with both noise cancelling and ambient sound; multipoint, where it is a general setting. */
    override fun set(command: Command, value: Int): List<SonyOut>? {
        if (command == Command.MULTIPOINT) {
            val out = generalSet(GS_MULTIPOINT.takeIf { multipointType != null }, value == 1) ?: return null
            refresh()
            return listOf(out)
        }
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
    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun allowed(out: SonyOut): Boolean = out.command !in when {
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
        private val S2T2 = SonyCommand.Set2Table2

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
        private const val SYSTEM_RET_CAPABILITY = SonyCommand.Set1.SYSTEM_RET_CAPABILITY
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
        private const val S1_CONNECTION_MODE = 1
        private const val S1_SOUND_CONNECTION = 0

        // Set 2's types; on is 0 and off 1 there.
        private const val S2_UPSCALING = 1
        private const val S2_CONTROL_BY_WEARING = 1
        private const val S2_SMART_TALKING_TYPE1 = 2
        private const val S2_SMART_TALKING_TYPE2 = 0x0C
        /** Power off: set 2's power status type for it, and the value both sets write (PowerOffSettingValue.USER_POWER_OFF). */
        private const val S2_POWER_OFF = 3
        private const val USER_POWER_OFF = 1
        /** The noise cancelling optimizer's commands, the same in both sets; set 1's type (NC_OPTIMIZER) and idle status. */
        private const val OPT_RET_STATUS = 0x83
        private const val OPT_SET_STATUS = 0x84
        private const val OPT_NTFY_STATUS = 0x85
        private const val S1_OPTIMIZER = 1
        private const val OPT_IDLE = 0
        private const val OPTIMIZER_KEY = "nc_optimizer"

        /** The general settings' commands, the same in both sets, and their types (GENERAL_SETTING1 is D1). */
        private const val GS_GET_CAPABILITY = 0xD0
        private const val GS_RET_CAPABILITY = 0xD1
        private const val GS_GET_PARAM = 0xD6
        private const val GS_RET_PARAM = 0xD7
        private const val GS_SET_PARAM = 0xD8
        private const val GS_NTFY_PARAM = 0xD9
        private const val GS_FIRST_TYPE = 0xD1
        /** The capability is asked in English (DisplayLanguage.ENGLISH). */
        private const val DISPLAY_ENGLISH = 1
        /** A switch, and a title that is one of Sony's names rather than text: set 1 (1, 2) and set 2 (0, 1) number them differently. */
        private const val GS1_BOOLEAN = 1
        private const val GS2_BOOLEAN = 0
        private const val GS1_ENUM_NAME = 2
        private const val GS2_ENUM_NAME = 1
        /** The title Sony gives multipoint (GsTitleTitle.MULTIPOINT_SETTING). */
        private const val GS_MULTIPOINT = "MULTIPOINT_SETTING"
        /** A general setting's row: this, then its title (Sony's name, or [RAW_TITLE] and the text the earbuds gave). */
        public const val GS_KEY: String = "sony_gs_"
        public const val RAW_TITLE: String = "raw:"

        /** Set 2's background music effect (AudioInquiredType.BGM_MODE) and its room sizes (RoomSize). */
        private const val S2_BGM = 3
        private const val BGM_KEY = "bgm_effect"
        private const val ULT_KEY = "ult_mode"
        private const val EBB_KEY = "ebb"
        private const val EFFECT_KEY = "sound_effect"
        private const val ULT_BUTTON_KEY = "ult_button_"
        private val ULT_MODES = linkedMapOf(0 to "off", 1 to "ult_1", 2 to "ult_2")
        private val BGM_ROOMS = linkedMapOf(0 to "small", 1 to "medium", 2 to "large")
        private const val OFF = "off"

        /** Set 2's voice guidance volume types (VoiceGuidanceInquiredType VOLUME and VOLUME_SETTING_FIXED_TO_5_STEPS): -2 to 2. */
        private const val S2_GUIDANCE_VOLUME = 32
        private const val S2_GUIDANCE_VOLUME_5 = 33
        private const val GUIDANCE_VOLUME_KEY = "voice_guidance_volume"
        private const val MAX_VOLUME_KEY = "max_volume_limit"
        private const val MAX_VOL_LV_LIMIT = 5
        private const val MAX_VOLUME_STEPS = 5
        private const val GUIDANCE_VOLUME_MAX = 2

        /**
         * One of set 2's settings that is a type byte, then on (0) or off (1), in the same command family both ways
         * (Sony's app: the param messages with OnOffSettingValue): read with [get], written with [set], answered and
         * notified with [replies]; [function] in table [table]'s list says the earbuds have it.
         */
        private data class S2Switch(val key: String, val tab: SettingTab, val table: Int, val function: Int, val type: Int, val get: Int, val set: Int, val replies: Set<Int>)

        private val S2_SWITCHES: List<S2Switch> by lazy {
            val power = setOf(SonyCommand.Set2.POWER_RET_PARAM, SonyCommand.Set2.POWER_NTFY_PARAM)
            val audio = setOf(AUDIO_RET_PARAM, AUDIO_NTFY_PARAM)
            val system = setOf(SYSTEM_RET_PARAM, SYSTEM_NTFY_PARAM)
            val guidance = setOf(SonyCommand.Set2Table2.VOICE_GUIDANCE_RET_PARAM, SonyCommand.Set2Table2.VOICE_GUIDANCE_NTFY_PARAM)
            val sar = setOf(SonyCommand.Set2.SAR_AUTO_PLAY_RET_PARAM, SonyCommand.Set2.SAR_AUTO_PLAY_NTFY_PARAM)
            val powerT2 = setOf(SonyCommand.Set2Table2.POWER_RET_PARAM, SonyCommand.Set2Table2.POWER_NTFY_PARAM)
            val systemT2 = setOf(SonyCommand.Set2Table2.SYSTEM_RET_PARAM, SonyCommand.Set2Table2.SYSTEM_NTFY_PARAM)
            val f = SonyFunction.Set2
            val f2 = SonyFunction.Set2Table2
            val g = SonyCommand.Set2Table2.VOICE_GUIDANCE_GET_PARAM
            val s = SonyCommand.Set2Table2.VOICE_GUIDANCE_SET_PARAM
            listOf(
                // PowerInquiredType.POWER_SAVE_MODE
                S2Switch("power_saving", SettingTab.DEVICE, 1, f.POWER_SAVING_MODE_ON_OFF, 6, SonyCommand.Set2.POWER_GET_PARAM, SonyCommand.Set2.POWER_SET_PARAM, power),
                // AudioInquiredType: SOUND_LEAKAGE_REDUCTION, VOICE_CONTENTS, UPMIX_CINEMA
                S2Switch("sound_leakage_reduction", SettingTab.SOUND, 1, f.SOUND_LEAKAGE_REDUCTION, 7, AUDIO_GET_PARAM, AUDIO_SET_PARAM, audio),
                S2Switch("voice_contents", SettingTab.SOUND, 1, f.VOICE_CONTENTS, 6, AUDIO_GET_PARAM, AUDIO_SET_PARAM, audio),
                S2Switch("upmix_cinema", SettingTab.SOUND, 1, f.UPMIX_CINEMA, 4, AUDIO_GET_PARAM, AUDIO_SET_PARAM, audio),
                // SystemInquiredType.AUTO_VOLUME
                S2Switch("adaptive_volume", SettingTab.SOUND, 1, f.AUTO_VOLUME, 10, SYSTEM_GET_PARAM, SYSTEM_SET_PARAM, system),
                // VoiceGuidanceInquiredType: BATTERY_LV_VOICE, POWER_ONOFF_SOUND, SOUNDEFFECT_ULT_BEEP_ONOFF
                S2Switch("battery_voice", SettingTab.DEVICE, 2, f2.VOICE_GUIDANCE_BATTERY_LEVEL_VOICE, 48, g, s, guidance),
                S2Switch("power_sound", SettingTab.DEVICE, 2, f2.VOICE_GUIDANCE_POWER_ON_OFF_SOUND, 49, g, s, guidance),
                S2Switch("ult_beep", SettingTab.DEVICE, 2, f2.VOICE_GUIDANCE_SOUND_EFFECT_ULT_BEEP_ON_OFF, 50, g, s, guidance),
                // Table 2's PowerInquiredType: AUTO_STANDBY, CARING_CHARGE_WITH_THRESHOLD (the param is on or off; the threshold is read-only status)
                S2Switch("auto_standby", SettingTab.DEVICE, 2, f2.AUTO_STANDBY, 0, SonyCommand.Set2Table2.POWER_GET_PARAM, SonyCommand.Set2Table2.POWER_SET_PARAM, powerT2),
                S2Switch("caring_charge_threshold", SettingTab.DEVICE, 2, f2.CARING_CHARGE_WITH_THRESHOLD, 1, SonyCommand.Set2Table2.POWER_GET_PARAM, SonyCommand.Set2Table2.POWER_SET_PARAM, powerT2),
                // Table 1's PowerInquiredType CARING_CHARGE (12), STAMINA (14) and AUTOMATIC_TOUCH_PANEL_BACKLIGHT_TURN_OFF (15); SARAutoPlayInquiredType AUTO_PLAY (1)
                S2Switch("caring_charge", SettingTab.DEVICE, 1, f.CARING_CHARGE, 12, SonyCommand.Set2.POWER_GET_PARAM, SonyCommand.Set2.POWER_SET_PARAM, power),
                S2Switch("stamina", SettingTab.DEVICE, 1, f.STAMINA, 14, SonyCommand.Set2.POWER_GET_PARAM, SonyCommand.Set2.POWER_SET_PARAM, power),
                S2Switch("touch_panel_backlight_off", SettingTab.DEVICE, 1, f.AUTOMATIC_TOUCH_PANEL_BACKLIGHT_TURN_OFF, 15, SonyCommand.Set2.POWER_GET_PARAM, SonyCommand.Set2.POWER_SET_PARAM, power),
                S2Switch("auto_play", SettingTab.DEVICE, 1, f.AUTO_PLAY, 1, SonyCommand.Set2.SAR_AUTO_PLAY_GET_PARAM, SonyCommand.Set2.SAR_AUTO_PLAY_SET_PARAM, sar),
                // Table 2's SystemInquiredType: AUTO_VOLUME_OPTIMIZER, AUTO_VOLUME_WITH_LIMITATION
                S2Switch("auto_volume_optimizer", SettingTab.SOUND, 2, f2.AUTO_VOLUME_OPTIMIZER, 3, SonyCommand.Set2Table2.SYSTEM_GET_PARAM, SonyCommand.Set2Table2.SYSTEM_SET_PARAM, systemT2),
                S2Switch("auto_volume_limited", SettingTab.SOUND, 2, f2.AUTO_VOLUME_WITH_LIMITATION, 4, SonyCommand.Set2Table2.SYSTEM_GET_PARAM, SonyCommand.Set2Table2.SYSTEM_SET_PARAM, systemT2),
            )
        }
        private const val ON_V2 = 0
        private const val OFF_V2 = 1

        // Set 2's connection mode types (AudioInquiredType), and its voice guidance types (VoiceGuidanceInquiredType).
        private const val S2_CONNECTION_MODE = 0
        private const val S2_CONNECTION_MODE_LDAC = 2
        private const val S2_GUIDANCE_MTK = 0
        private const val S2_GUIDANCE_MTK_LANGUAGE = 1
        private const val S2_GUIDANCE_LANGUAGE = 2
        private const val S2_GUIDANCE_ON_OFF = 3

        // Sony's connection modes (ConnectionModeSettingValue, PriorMode) by the option the row shows.
        private const val CONNECTION_KEY = "connection_priority"
        private const val SOUND_QUALITY = 0
        private const val STABLE = 1
        private val CONNECTION_OPTIONS = mapOf(SOUND_QUALITY to "sound_quality", STABLE to "stable", 2 to "low_latency")

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
