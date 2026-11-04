package io.github.omegadestroyer.pairfree.protocol.earfun

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Frame
import io.github.omegadestroyer.pairfree.protocol.FrameParser
import io.github.omegadestroyer.pairfree.protocol.PowerActions
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One connection to EarFun earbuds or headphones (docs/earfun/PROTOCOL.md), shown on PairFree's screens. The
 * [model]'s protocol picks the frames: GAIA with vendor 000A on the Qualcomm models, EarFun's own frame on the
 * others. It reads what EarFun's app offers for the model, and a setting shows once its read has answered. The
 * battery, firmware, noise mode, game mode, two devices and finding the earbuds go to [state]; everything else
 * to [groups]. The EQ cannot be read: [eqFrames] builds what EarFun's app writes.
 *
 * Frames go out and come in as bytes ([start], [feed]); a change shows at once, and [takeReadBacks] gives the
 * reads that confirm it, which the connection sends a little later.
 */
public class EarfunSession(public val model: EarfunModel) {
    public var state: EarbudState = EarbudState()
        private set

    public var firmware: String? = null
        private set

    /** The last payload of each read PairFree sends, by command name, for the device report. */
    public val replies: Map<String, ByteArray> get() = _replies
    private val _replies = linkedMapOf<String, ByteArray>()

    private val qualcomm = model.qualcomm
    private val qccParser = FrameParser()
    private val efParser = EarfunParser()
    private val outbox = mutableListOf<ByteArray>()
    private val readBacks = mutableListOf<ByteArray>()

    private var noise: Int? = null
    private var cancellingTop: String? = null       // the cancelling mode to go back to, where the noise mode itself has several
    private var cancelMode: String? = null
    private var ambientMode: String? = null
    private var game: Boolean? = null
    private var multipoint: Boolean? = null
    private var mic: Int? = null
    private var promptVolume: String? = null
    private var hearing: String? = null
    private var autoOff: String? = null
    private var spatial: Boolean? = null
    private var checkEar: Int? = null
    private var keys: Map<String, Int>? = null       // the action byte per touch gesture and side ("hold_left") or button and gesture ("nc_hold")
    private var quality: Int? = null                 // the codec bits the earbuds reported
    private var language: String? = null
    private var lockLeft: Boolean? = null
    private var lockRight: Boolean? = null
    private var findLeft: Boolean? = null
    private var findRight: Boolean? = null

    // --- Frames ---

    /** A Qualcomm model's command [code] (vendor 000A, 03 then the code) with [data]. */
    private fun qcc(code: Int, vararg data: Int): ByteArray =
        Frame.build((EarfunQcc.COMMAND_PREFIX shl 8) or code, ByteArray(data.size) { data[it].toByte() }, EarfunQcc.VENDOR)

    /** An EarFun-frame command with [data]. */
    private fun ef(command: Int, vararg data: Int): ByteArray = EarfunFrame(command, ByteArray(data.size) { data[it].toByte() }).bytes()

    private fun read(qccCode: Int, efCode: Int): ByteArray = if (qualcomm) qcc(qccCode) else ef(efCode)

    /** The reads on opening the channel: those EarFun's app sends, for what the model has. */
    public fun start(): List<ByteArray> = buildList {
        val m = model
        if (!m.covered) return@buildList
        if (qualcomm && m.gaiaInit) addAll(gaiaInit())
        add(read(EarfunQcc.GET_FW_VERSION, EarfunCommand.GET_VERSION))
        addAll(batteryRequests())
        val n = m.noise
        if (n != null) {
            add(read(EarfunQcc.GET_NOISE_MODE, EarfunCommand.GET_NOISE))
            if (n.cancelling != null) add(read(EarfunQcc.GET_NOISE_CANCEL_MODE, EarfunCommand.GET_NOISE_CANCEL_MODE))
            if (n.ambient != null) add(read(EarfunQcc.GET_AMBIENT_MODE, EarfunCommand.GET_AMBIENT_MODE))
        }
        if (m.game) add(read(EarfunQcc.GET_GAME_MODE, EarfunCommand.GET_GAME_MODE))
        if (m.multipoint) add(read(EarfunQcc.GET_MULTIPOINT, EarfunCommand.GET_MULTIPOINT))
        if (m.mic && qualcomm) add(qcc(EarfunQcc.GET_MIC))
        if (m.promptVolume) add(read(EarfunQcc.GET_VOICE_VOLUME, EarfunCommand.GET_VOICE_VOLUME))
        if (m.hearingProtection) add(read(EarfunQcc.GET_VOICE_PROTECT, EarfunCommand.GET_VOICE_PROTECT))
        if (m.autoOff.isNotEmpty()) add(read(EarfunQcc.GET_AUTO_OFF, EarfunCommand.GET_AUTO_OFF))
        if (m.spatial && !qualcomm) add(ef(EarfunCommand.GET_SPATIAL_AUDIO))
        if (m.inEar && qualcomm) add(qcc(EarfunQcc.GET_CHECK_EAR))
        if (m.find && m.form == Form.EARBUDS) add(read(EarfunQcc.GET_FIND, EarfunCommand.GET_FIND))
        if (m.quality.isNotEmpty()) add(read(EarfunQcc.GET_QUALITY, EarfunCommand.GET_QUALITY))
        if (m.voiceLanguages.isNotEmpty() && !qualcomm) add(ef(EarfunCommand.GET_VOICE_WIZARD))
        m.touch?.let { t ->
            add(read(EarfunQcc.GET_KEY, EarfunCommand.GET_KEY))
            if (t.disable != null) add(read(EarfunQcc.GET_DISABLE_KEY, EarfunCommand.GET_DISABLE_KEY))
        }
        if (m.buttons != null) add(read(EarfunQcc.GET_KEY, EarfunCommand.GET_KEY))
    }

    /**
     * The GAIA v3 opening EarFun's app sends on its Qualcomm models (QualcommProtocolParser.initCommand): 03 00 on
     * vendor 000A, then on vendor 001D the features, the protocol version and the notifications it registers for,
     * leaving out firmware update's (feature 06), which PairFree never asks for.
     */
    private fun gaiaInit(): List<ByteArray> = buildList {
        add(Frame.build(EarfunQcc.COMMAND_PREFIX shl 8, ByteArray(0), EarfunQcc.VENDOR))
        add(Frame.build(GAIA_GET_FEATURES, ByteArray(0), EarfunQcc.EQ_VENDOR))
        add(Frame.build(GAIA_SET_PROTOCOL, byteArrayOf(7, 0, 0, 0, GAIA_PROTOCOL_VERSION.toByte()), EarfunQcc.EQ_VENDOR))
        for (feature in GAIA_NOTIFICATIONS) add(Frame.build(GAIA_REGISTER, byteArrayOf(feature.toByte()), EarfunQcc.EQ_VENDOR))
    }

    /** The battery reads, to send again now and then. */
    public fun batteryRequests(): List<ByteArray> = buildList {
        if (qualcomm) {
            add(qcc(EarfunQcc.GET_LEFT_BATTERY))
            if (model.form == Form.EARBUDS) add(qcc(EarfunQcc.GET_RIGHT_BATTERY))
            if (model.case) add(qcc(EarfunQcc.GET_BOX_BATTERY))
        } else {
            add(ef(EarfunCommand.GET_BATTERY))
            if (model.case) add(ef(EarfunCommand.GET_BOX_BATTERY))
        }
    }

    /** What the session wants sent next. */
    public fun takeOutbox(): List<ByteArray> = outbox.toList().also { outbox.clear() }

    /** The reads that confirm the last changes, for the connection to send a little later. */
    public fun takeReadBacks(): List<ByteArray> = readBacks.distinctBy { it.toList() }.also { readBacks.clear() }

    /** The reply [sent] waits for, by [replyKey], or null when it gets none worth waiting for (a write, or GAIA v3). */
    public fun awaits(sent: ByteArray): Int? {
        if (qualcomm) {
            val f = FrameParser().feed(sent).singleOrNull() ?: return null
            if (f.vendor != EarfunQcc.VENDOR) return null
            val code = f.command and 0xFF
            return code.takeIf { EarfunQcc.NAMES[it]?.startsWith("GET_") == true }
        }
        val f = EarfunParser().feed(sent).singleOrNull() ?: return null
        return f.command.takeIf { EarfunCommand.NAMES[it]?.startsWith("GET_") == true }
    }

    /**
     * False for what PairFree never sends: on either protocol factory reset, renaming, device management, firmware
     * update and the private reads; on GAIA v3 anything but the opening and the EQ. Restarting and powering off pass:
     * they come only from an action the user confirms on screen ([change]).
     */
    public fun allowed(bytes: ByteArray): Boolean {
        if (!qualcomm) {
            val f = EarfunParser().feed(bytes).singleOrNull() ?: return false
            return f.command !in EarfunCommand.NEVER_SEND && f.command !in EarfunCommand.PRIVATE
        }
        val f = FrameParser().feed(bytes).singleOrNull() ?: return false
        return when (f.vendor) {
            EarfunQcc.VENDOR -> {
                val code = f.command and 0xFF
                (f.command shr 8) == EarfunQcc.COMMAND_PREFIX && code !in EarfunQcc.NEVER_SEND && code !in EarfunQcc.PRIVATE
            }
            EarfunQcc.EQ_VENDOR -> when (f.command) {
                EarfunQcc.EQ_COMMAND, GAIA_GET_FEATURES, GAIA_SET_PROTOCOL -> true
                GAIA_REGISTER -> f.payload.size == 1 && f.payload[0].toInt() != GAIA_UPGRADE_FEATURE
                else -> false
            }
            else -> false
        }
    }

    /** What [feed] found: whether [state] or [groups] changed, and the keys of the replies, for [awaits]. */
    public data class Fed(val changed: Boolean, val replies: List<Int>)

    /** Applies bytes from the earbuds. */
    public fun feed(bytes: ByteArray): Fed {
        val before = state to groups
        val keys = mutableListOf<Int>()
        if (qualcomm) {
            for (f in qccParser.feed(bytes)) {
                if (f.vendor != EarfunQcc.VENDOR || (f.command shr 8) !in EarfunQcc.REPLY_PREFIXES) continue
                val code = f.command and 0xFF
                keys += code
                EarfunQcc.NAMES[code]?.let { _replies[it] = f.payload.copyOf() }
                // The answer's first byte is its status; the value follows (QualcommProtocolParser.getNormalData).
                if (f.payload.size > 1) qccReply(code, f.payload)
            }
        } else {
            for (f in efParser.feed(bytes)) {
                keys += f.command
                EarfunCommand.NAMES[f.command]?.let { _replies[it] = f.payload.copyOf() }
                if (f.payload.isNotEmpty()) efReply(f.command, f.payload)
            }
        }
        refresh()
        return Fed((state to groups) != before, keys)
    }

    private fun qccReply(code: Int, p: ByteArray) {
        val v = p.u8(1)
        when (code) {
            EarfunQcc.GET_FW_VERSION -> firmware = p.copyOfRange(1, p.size).decodeToString().trim { it <= ' ' }
            EarfunQcc.GET_LEFT_BATTERY -> battery(Command.BATTERY_LEFT, v)
            EarfunQcc.GET_RIGHT_BATTERY -> battery(Command.BATTERY_RIGHT, v)
            EarfunQcc.GET_BOX_BATTERY -> battery(Command.BATTERY_CASE, if (model.caseChargeBit) v and 0x7F else v)
            EarfunQcc.GET_NOISE_MODE -> noiseMode(v)
            EarfunQcc.GET_NOISE_CANCEL_MODE -> cancelMode = subMode(model.noise?.cancelling, EarfunValues.NOISE_CANCEL_MODE, v)
            EarfunQcc.GET_AMBIENT_MODE -> ambientMode = subMode(model.noise?.ambient, EarfunValues.AMBIENT_MODE, v)
            EarfunQcc.GET_GAME_MODE -> game = v == 1
            EarfunQcc.GET_MULTIPOINT -> multipoint = v == 1
            EarfunQcc.GET_MIC -> mic = if (v in 1..2) v else 0
            EarfunQcc.GET_VOICE_VOLUME -> promptVolume = nameOf(EarfunValues.VOICE_VOLUME.getValue(QUALCOMM), v) ?: "100"
            EarfunQcc.GET_VOICE_PROTECT -> hearing = nameOf(EarfunValues.VOICE_PROTECT, v) ?: OFF
            EarfunQcc.GET_AUTO_OFF -> autoOff = autoOffName(v)
            EarfunQcc.GET_CHECK_EAR -> checkEar = v
            EarfunQcc.GET_FIND -> {
                findLeft = v and 1 != 0
                findRight = v and 2 != 0
            }
            EarfunQcc.GET_DISABLE_KEY -> {
                lockLeft = v == 1 || v == 2
                lockRight = v == 1 || v == 3
            }
            EarfunQcc.GET_KEY -> keys = if (model.buttons != null) waveProXButtons(p) else qccTouch(p)
            EarfunQcc.GET_QUALITY -> quality = v
        }
    }

    private fun efReply(command: Int, p: ByteArray) {
        val v = p.u8(0)
        when (command) {
            EarfunCommand.GET_VERSION -> firmware = if (model.versionText) p.decodeToString().trim { it <= ' ' }
            else if (p.size >= 3) "${p.u8(0)}.${p.u8(1)}.${p.u8(2)}" else null
            EarfunCommand.GET_BATTERY -> {
                battery(Command.BATTERY_LEFT, v)
                if (model.form == Form.EARBUDS && p.size > 1) battery(Command.BATTERY_RIGHT, p.u8(1))
            }
            EarfunCommand.GET_BOX_BATTERY -> battery(Command.BATTERY_CASE, v)
            EarfunCommand.GET_NOISE -> noiseMode(v)
            EarfunCommand.GET_NOISE_CANCEL_MODE -> cancelMode = subMode(model.noise?.cancelling, EarfunValues.NOISE_CANCEL_MODE, v)
            // Natural is 2; anything else the default (EarfunProtocolParser.setData).
            EarfunCommand.GET_AMBIENT_MODE -> ambientMode = if (v == EarfunValues.AMBIENT_MODE.getValue(EARFUN)["natural"]) "natural" else "default"
            EarfunCommand.GET_GAME_MODE -> game = v == EF_ON
            EarfunCommand.GET_MULTIPOINT -> multipoint = v == EF_ON
            EarfunCommand.GET_SPATIAL_AUDIO -> spatial = v == EF_ON
            EarfunCommand.GET_VOICE_VOLUME -> promptVolume = nameOf(EarfunValues.VOICE_VOLUME.getValue(EARFUN), v) ?: "0"
            EarfunCommand.GET_VOICE_PROTECT -> hearing = nameOf(EarfunValues.VOICE_PROTECT, v) ?: OFF
            EarfunCommand.GET_AUTO_OFF -> autoOff = autoOffName(v)
            EarfunCommand.GET_FIND -> {
                findLeft = v == 1
                if (p.size > 1) findRight = p.u8(1) == 1
            }
            EarfunCommand.GET_DISABLE_KEY -> {
                lockLeft = v == 1
                if (p.size > 1) lockRight = p.u8(1) == 1
            }
            EarfunCommand.GET_KEY -> keys = (if (model.buttons != null) HEAD_BUTTONS else efTouchOrder()).takeIf { p.size >= it.size }
                ?.let { order -> order.mapIndexed { i, key -> key to p.u8(i) }.toMap() }
            EarfunCommand.GET_QUALITY -> quality = v
            // 1 English, 2 Japanese, 3 Chinese, 4 German (EarfunProtocolParser.setData); the Clip 2's 5 is a voice of the user's own.
            EarfunCommand.GET_VOICE_WIZARD -> language = nameOf(EarfunValues.VOICE_LANGUAGE.getValue(EARFUN), v)
        }
    }

    /**
     * The Qualcomm touch read: the action bytes at every other byte from the third (after the status), left and right in
     * turn for one, double, triple tap and press and hold, then the call gestures (QualcommProtocolParser.positionKeyMap).
     */
    private fun qccTouch(p: ByteArray): Map<String, Int>? {
        val out = mutableMapOf<String, Int>()
        for ((gesture, index) in QCC_TOUCH_INDEX) {
            for (side in 0..1) {
                val at = QCC_KEY_FIRST + 2 * (index + side)
                if (at < p.size) out["${gesture}_${SIDES[side]}"] = p.u8(at)
            }
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /** The Wave Pro X's buttons (HP1000ProtocolParser.resetData), by their bytes in its key read. */
    private fun waveProXButtons(p: ByteArray): Map<String, Int>? =
        WAVE_PRO_X_BUTTONS.filterValues { it < p.size }.mapValues { (_, at) -> p.u8(at) }.takeIf { it.isNotEmpty() }

    /** EarFun's frame holds the four gestures of the left earbud, then the right's, then on the Clip 2 the call gestures. */
    private fun efTouchOrder(): List<String> =
        SIDES.flatMap { side -> GESTURES.map { "${it}_$side" } } +
            if (model.touch?.callMap != null) listOf("triple_call_left", "hold_call_left", "triple_call_right", "hold_call_right") else emptyList()

    private fun battery(command: Command, level: Int) {
        state = state.copy(values = state.values + (command to Value.Number(level.takeIf { it in 1..PERCENT })))
    }

    private fun noiseMode(v: Int) {
        noise = v
        val name = noiseName(v) ?: return
        if (name != OFF && name != AMBIENT) cancellingTop = name
    }

    private fun noiseName(v: Int): String? = model.noise?.values?.entries?.firstOrNull { it.value == v }?.key

    private fun subMode(sub: EarfunSubMode?, tables: Map<String, Map<String, Int>>, v: Int): String? {
        val table = tables[sub?.table ?: return null] ?: return null
        return table.entries.firstOrNull { it.value == v && it.key in sub.options }?.key
    }

    private fun nameOf(table: Map<String, Int>, v: Int): String? = table.entries.firstOrNull { it.value == v }?.key

    private fun autoOffName(v: Int): String {
        val table = EarfunValues.AUTO_OFF[model.autoOffTable] ?: return NEVER
        return nameOf(table, v)?.takeIf { it in model.autoOff } ?: NEVER
    }

    /** Puts what the earbuds reported where the shared screens read it. */
    private fun refresh() {
        val values = state.values.toMutableMap()
        firmware?.let { values[Command.FIRMWARE_VERSION] = Value.Text("${model.codes.first()}_$it") }
        noise?.let { v ->
            values[Command.ANC_MODE] = Value.Number(
                when (noiseName(v)) {
                    OFF -> ancNumber("off")
                    AMBIENT -> ancNumber("transparency")
                    null -> null
                    else -> ancNumber("anc")
                },
            )
        }
        game?.let { values[Command.GAME_MODE] = Value.Switch(it) }
        multipoint?.let { values[Command.MULTIPOINT] = Value.Switch(it) }
        findLeft?.let { values[Command.FIND_LEFT] = Value.Switch(it) }
        findRight?.let { values[Command.FIND_RIGHT] = Value.Switch(it) }
        state = state.copy(values = values)
    }

    // --- Settings for the screens ---

    /** The cancelling modes the noise mode itself has (anc, adaptive, wind, anc1, anc2), where there are several. */
    private val cancellingTops: List<String>
        get() = model.noise?.values?.keys?.filter { it != OFF && it != AMBIENT }.orEmpty()

    /** Every setting the earbuds have, grouped by heading and tab. */
    public val groups: List<SettingGroup>
        get() {
            val m = model
            val gestures = m.touch?.gestures
            return listOfNotNull(
                group("noise", SettingTab.HOME, noiseRows()),
                group("sound", SettingTab.SOUND, listOfNotNull(spatial?.takeIf { m.spatial }?.let { Setting.Toggle("spatial_audio", it) })),
                group(
                    "other", SettingTab.DEVICE,
                    listOfNotNull(
                        checkEar?.takeIf { m.inEar }?.let { Setting.Toggle("auto_pause", it and CHECK_EAR_PAUSE_OFF == 0) },
                        checkEar?.takeIf { m.inEar }?.let { Setting.Toggle("auto_answer", it and CHECK_EAR_ANSWER_OFF == 0) },
                        mic?.takeIf { m.mic }?.let { Setting.Choice("microphone", MICS[it], MICS.values.toList()) },
                        promptVolume?.takeIf { m.promptVolume }?.let { Setting.Choice("ef_prompt_volume", it, m.promptVolumes.ifEmpty { PROMPT_VOLUMES }) },
                        language?.takeIf { m.voiceLanguages.isNotEmpty() }?.let { Setting.Choice("ef_voice_language", it, m.voiceLanguages) },
                        hearing?.takeIf { m.hearingProtection }?.let { Setting.Choice("ef_hearing_protection", it, HEARING) },
                        autoOff?.takeIf { m.autoOff.isNotEmpty() }?.let { Setting.Choice("auto_off", it, m.autoOff) },
                    ),
                ),
                group("ef_quality", SettingTab.DEVICE, qualityRows()),
                group(
                    PowerActions.GROUP, SettingTab.DEVICE,
                    listOfNotNull(Setting.Action(RESTART_KEY).takeIf { m.restart && qualcomm }, Setting.Action(POWER_OFF_KEY).takeIf { m.powerOff }),
                ),
            ) + touchGroups(gestures) + buttonGroups()
        }

    /** The codecs as switches, as EarFun's sound quality screen shows them (HeadsetQualityFragment). */
    private fun qualityRows(): List<Setting> {
        val q = quality ?: return emptyList()
        val bits = EarfunValues.QUALITY_BITS.getValue(if (qualcomm) QUALCOMM else EARFUN)
        return model.quality.mapNotNull { codec -> bits[codec]?.let { Setting.Toggle("ef_$codec", q and it != 0) } }
    }

    /** The codecs on, by name. */
    private fun codecs(): MutableSet<String> {
        val q = quality ?: 0
        val bits = EarfunValues.QUALITY_BITS.getValue(if (qualcomm) QUALCOMM else EARFUN)
        return model.quality.filter { bits[it]?.let { b -> q and b != 0 } == true }.toMutableSet()
    }

    /** The headphones' buttons: per button and gesture, the actions EarFun's app offers. */
    private fun buttonGroups(): List<SettingGroup> {
        val b = model.buttons ?: return emptyList()
        val k = keys ?: return emptyList()
        val actions = EarfunValues.KEY_ACTIONS[b.map].orEmpty()
        val rows = b.gestures.mapNotNull { (key, options) ->
            val byte = k[key] ?: return@mapNotNull null
            val name = actions.entries.firstOrNull { it.value == byte && it.key in options }?.key ?: actions.entries.firstOrNull { it.value == byte }?.key ?: OTHER
            Setting.Choice("ef_button_$key", name, options)
        }
        return listOfNotNull(group("ef_buttons", SettingTab.CONTROLS, rows))
    }

    private fun noiseRows(): List<Setting> = buildList {
        val n = model.noise ?: return@buildList
        val name = noise?.let(::noiseName) ?: return@buildList
        val tops = cancellingTops
        if (name != OFF && name != AMBIENT) {
            if (tops.size > 1) add(Setting.Choice(ANC_MODE_KEY, name, tops))
            n.cancelling?.let { c -> cancelMode?.let { add(Setting.Choice(ANC_MODE_KEY, it, c.options)) } }
        }
        if (name == AMBIENT) n.ambient?.let { a -> ambientMode?.let { add(Setting.Choice(AMBIENT_MODE_KEY, it, a.options)) } }
    }

    /**
     * The touch controls: per earbud, one, two and three taps and press and hold, with the actions EarFun's app offers
     * for each, and turning the controls off, both earbuds at once or each. Only once the earbuds reported them.
     */
    private fun touchGroups(gestures: Map<String, List<String>>?): List<SettingGroup> {
        val t = model.touch ?: return emptyList()
        val k = keys
        val actions = EarfunValues.KEY_ACTIONS[t.map].orEmpty()
        val lock = mutableListOf<Setting>()
        when (t.disable) {
            BOTH -> if (lockLeft != null && lockRight != null) lock += Setting.Toggle(LOCK_KEY, lockLeft == true && lockRight == true)
            EACH -> {
                lockLeft?.let { lock += Setting.Toggle(LOCK_LEFT_KEY, it) }
                lockRight?.let { lock += Setting.Toggle(LOCK_RIGHT_KEY, it) }
            }
        }
        val callActions = t.callMap?.let { EarfunValues.KEY_ACTIONS[it] }.orEmpty()
        fun side(suffix: String): List<Setting> {
            if (k == null || gestures == null) return emptyList()
            return (GESTURES + CALL_GESTURES).mapNotNull { gesture ->
                val options = gestures[gesture]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val byte = k["${gesture}_$suffix"] ?: return@mapNotNull null
                val table = if (gesture in CALL_GESTURES) callActions else actions
                val name = table.entries.firstOrNull { it.value == byte && it.key in options }?.key
                    ?: table.entries.firstOrNull { it.value == byte }?.key ?: OTHER
                Setting.Choice("touch_${gesture}_$suffix", name, options)
            }
        }
        return listOfNotNull(
            group("touch", SettingTab.CONTROLS, lock.filter { it.key == LOCK_KEY }),
            group("touch_left", SettingTab.CONTROLS, lock.filter { it.key == LOCK_LEFT_KEY } + side("left")),
            group("touch_right", SettingTab.CONTROLS, lock.filter { it.key == LOCK_RIGHT_KEY } + side("right")),
        )
    }

    private fun group(key: String, tab: SettingTab, settings: List<Setting>) = settings.takeIf { it.isNotEmpty() }?.let { SettingGroup(key, tab, it) }

    // --- Changes ---

    /** The frames for a shared setting from the screens, or null when the earbuds do not have it. */
    public fun set(command: Command, value: Int): List<ByteArray>? {
        if (command !in state.values) return null
        val out = when (command) {
            Command.ANC_MODE -> {
                val n = model.noise ?: return null
                val name = when (value) {
                    ancNumber("off") -> OFF
                    ancNumber("transparency") -> AMBIENT.takeIf { it in n.values } ?: return null
                    ancNumber("anc") -> cancellingTop?.takeIf { it in n.values } ?: cancellingTops.firstOrNull() ?: return null
                    else -> return null
                }
                listOf(noiseWrite(name))
            }
            Command.GAME_MODE -> {
                // Game mode cannot go on while LDAC is, on the models whose app says so (HeadsetGameModeViewHolder).
                if (value == 1 && model.ldacGameConflict && "ldac" in codecs()) return null
                game = value == 1
                readBacks += read(EarfunQcc.GET_GAME_MODE, EarfunCommand.GET_GAME_MODE)
                listOf(if (qualcomm) qcc(EarfunQcc.SET_GAME_MODE, value) else ef(EarfunCommand.SET_GAME_MODE, efSwitch(value == 1)))
            }
            Command.MULTIPOINT -> {
                // Two devices cannot go on while LDAC is, on the models whose app says so (HeadsetDoubleConnectFragment).
                if (value == 1 && model.ldacMultipointConflict && "ldac" in codecs()) return null
                multipoint = value == 1
                readBacks += read(EarfunQcc.GET_MULTIPOINT, EarfunCommand.GET_MULTIPOINT)
                // EarFun's app restarts these earbuds 3 s after turning two devices on: the read-back's time.
                if (value == 1 && model.multipointRestart && qualcomm) readBacks += qcc(EarfunQcc.RESTART)
                listOf(if (qualcomm) qcc(EarfunQcc.SET_MULTIPOINT, value) else ef(EarfunCommand.SET_MULTIPOINT, efSwitch(value == 1)))
            }
            Command.FIND_LEFT, Command.FIND_RIGHT -> {
                if (command == Command.FIND_LEFT) findLeft = value == 1 else findRight = value == 1
                val l = if (findLeft == true) 1 else 0
                val r = if (findRight == true) 1 else 0
                // Both sides at once, as EarFun's app writes it (playHeadset): bits on the Qualcomm models, a byte each on the others.
                listOf(if (qualcomm) qcc(EarfunQcc.SET_FIND, (r shl 1) + l) else ef(EarfunCommand.SET_FIND, l, r))
            }
            else -> return null
        }
        refresh()
        return out
    }

    /** The noise mode [name], with the sub-mode last chosen where cancelling has one. */
    private fun noiseWrite(name: String): ByteArray {
        val v = model.noise?.values?.get(name) ?: error("no noise mode $name")
        noiseMode(v)
        readBacks += read(EarfunQcc.GET_NOISE_MODE, EarfunCommand.GET_NOISE)
        return if (qualcomm) qcc(EarfunQcc.SET_NOISE_MODE, v) else ef(EarfunCommand.SET_NOISE, v)
    }

    /** The frames for a change to one of the [groups], or null when the earbuds do not have the setting. */
    public fun change(change: SettingChange): List<ByteArray>? {
        val setting = groups.flatMap { it.settings }.firstOrNull { it.key == change.key } ?: return null
        val out: List<ByteArray> = when (change) {
            is SettingChange.Toggle -> {
                if (setting !is Setting.Toggle || !setting.enabled) return null
                toggle(change.key, change.on) ?: return null
            }
            is SettingChange.Choice -> {
                if (setting !is Setting.Choice || change.option !in setting.options) return null
                choice(change.key, change.option) ?: return null
            }
            // The screen asks the user to confirm both first.
            is SettingChange.Press -> when (change.key) {
                RESTART_KEY -> listOf(qcc(EarfunQcc.RESTART))
                POWER_OFF_KEY -> listOf(if (qualcomm) qcc(EarfunQcc.SET_CONTROL_PLAY, POWER_OFF) else ef(EarfunCommand.SET_CONTROL_PLAY, POWER_OFF))
                else -> return null
            }
            else -> return null
        }
        refresh()
        return out
    }

    private fun toggle(key: String, on: Boolean): List<ByteArray>? = when (key) {
        "spatial_audio" -> {
            spatial = on
            readBacks += ef(EarfunCommand.GET_SPATIAL_AUDIO)
            listOf(ef(EarfunCommand.SET_SPATIAL_AUDIO, efSwitch(on)))
        }
        // In-ear detection's bits, 1 for off; the others stay as the earbuds reported them (TW700ProtocolParser.setCheckEarStateMode).
        "auto_pause", "auto_answer" -> {
            val bit = if (key == "auto_pause") CHECK_EAR_PAUSE_OFF else CHECK_EAR_ANSWER_OFF
            val v = (checkEar ?: return null).let { if (on) it and bit.inv() else it or bit }
            checkEar = v
            readBacks += qcc(EarfunQcc.GET_CHECK_EAR)
            listOf(qcc(EarfunQcc.SET_CHECK_EAR, v))
        }
        "ef_ldac", "ef_aptx", "ef_aptx_adaptive", "ef_aptx_lossless" -> qualityWrite(key.removePrefix("ef_"), on)
        LOCK_KEY, LOCK_LEFT_KEY, LOCK_RIGHT_KEY -> {
            when (key) {
                LOCK_KEY -> { lockLeft = on; lockRight = on }
                LOCK_LEFT_KEY -> lockLeft = on
                else -> lockRight = on
            }
            val l = lockLeft == true
            val r = lockRight == true
            readBacks += read(EarfunQcc.GET_DISABLE_KEY, EarfunCommand.GET_DISABLE_KEY)
            // One byte on the Qualcomm models (1 both, 2 left, 3 right, 0 none), a byte per side on the others (1 off).
            listOf(
                if (qualcomm) qcc(EarfunQcc.SET_DISABLE_KEY, if (l && r) 1 else if (l) 2 else if (r) 3 else 0)
                else ef(EarfunCommand.SET_DISABLE_KEY, if (l) 1 else 0, if (r) 1 else 0),
            )
        }
        else -> null
    }

    /**
     * A codec switched, with the others as EarFun's sound quality screen moves them (HeadsetQualityFragment): aptX
     * Adaptive and Lossless need aptX, Lossless needs Adaptive, and on most models LDAC and aptX exclude each other. LDAC
     * stays off while two devices are on where they conflict. The earbuds restart after the write.
     */
    private fun qualityWrite(codec: String, on: Boolean): List<ByteArray>? {
        val c = codecs()
        val conflict = model.ldacAptxConflict
        if (on) {
            when (codec) {
                "ldac" -> {
                    if (model.ldacMultipointConflict && multipoint == true) return null
                    if (conflict) c -= setOf("aptx", "aptx_adaptive", "aptx_lossless")
                }
                "aptx" -> if (conflict) c -= "ldac"
                "aptx_adaptive" -> {
                    c += "aptx"
                    if (conflict) c -= "ldac"
                }
                "aptx_lossless" -> {
                    c += setOf("aptx", "aptx_adaptive")
                    if (conflict) c -= "ldac"
                }
            }
            c += codec
        } else {
            c -= codec
            when (codec) {
                "aptx" -> c -= setOf("aptx_adaptive", "aptx_lossless")
                "aptx_adaptive" -> c -= "aptx_lossless"
            }
        }
        val bits = EarfunValues.QUALITY_BITS.getValue(if (qualcomm) QUALCOMM else EARFUN)
        val v = c.filter { it in model.quality }.sumOf { bits[it] ?: 0 }
        quality = v
        readBacks += read(EarfunQcc.GET_QUALITY, EarfunCommand.GET_QUALITY)
        return listOf(if (qualcomm) qcc(EarfunQcc.SET_QUALITY, v) else ef(EarfunCommand.SET_QUALITY, v))
    }

    private fun choice(key: String, option: String): List<ByteArray>? = when (key) {
        ANC_MODE_KEY -> {
            val n = model.noise ?: return null
            if (option in cancellingTops && n.values.containsKey(option)) {
                listOf(noiseWrite(option))
            } else {
                val c = n.cancelling ?: return null
                val v = EarfunValues.NOISE_CANCEL_MODE[c.table]?.get(option) ?: return null
                cancelMode = option
                readBacks += read(EarfunQcc.GET_NOISE_CANCEL_MODE, EarfunCommand.GET_NOISE_CANCEL_MODE)
                listOf(if (qualcomm) qcc(EarfunQcc.SET_NOISE_CANCEL_MODE, v) else ef(EarfunCommand.SET_NOISE_CANCEL_MODE, v))
            }
        }
        AMBIENT_MODE_KEY -> {
            val a = model.noise?.ambient ?: return null
            val v = EarfunValues.AMBIENT_MODE[a.table]?.get(option) ?: return null
            ambientMode = option
            readBacks += read(EarfunQcc.GET_AMBIENT_MODE, EarfunCommand.GET_AMBIENT_MODE)
            listOf(if (qualcomm) qcc(EarfunQcc.SET_AMBIENT_MODE, v) else ef(EarfunCommand.SET_AMBIENT_MODE, v))
        }
        "microphone" -> {
            val v = MICS.entries.firstOrNull { it.value == option }?.key ?: return null
            mic = v
            readBacks += qcc(EarfunQcc.GET_MIC)
            listOf(qcc(EarfunQcc.SET_MIC, v))
        }
        "ef_prompt_volume" -> {
            val v = EarfunValues.VOICE_VOLUME.getValue(if (qualcomm) QUALCOMM else EARFUN)[option] ?: return null
            promptVolume = option
            readBacks += read(EarfunQcc.GET_VOICE_VOLUME, EarfunCommand.GET_VOICE_VOLUME)
            listOf(if (qualcomm) qcc(EarfunQcc.SET_VOICE_VOLUME, v) else ef(EarfunCommand.SET_VOICE_VOLUME, v))
        }
        // Some models restart after a change (changeVoiceNeedReset).
        "ef_voice_language" -> {
            val v = EarfunValues.VOICE_LANGUAGE.getValue(EARFUN)[option] ?: return null
            language = option
            readBacks += ef(EarfunCommand.GET_VOICE_WIZARD)
            listOf(ef(EarfunCommand.SET_VOICE_WIZARD, v))
        }
        "ef_hearing_protection" -> {
            val v = EarfunValues.VOICE_PROTECT[option] ?: return null
            hearing = option
            readBacks += read(EarfunQcc.GET_VOICE_PROTECT, EarfunCommand.GET_VOICE_PROTECT)
            listOf(if (qualcomm) qcc(EarfunQcc.SET_VOICE_PROTECT, v) else ef(EarfunCommand.SET_VOICE_PROTECT, v))
        }
        "auto_off" -> {
            val v = EarfunValues.AUTO_OFF[model.autoOffTable]?.get(option) ?: return null
            autoOff = option
            readBacks += read(EarfunQcc.GET_AUTO_OFF, EarfunCommand.GET_AUTO_OFF)
            listOf(if (qualcomm) qcc(EarfunQcc.SET_AUTO_OFF, v) else ef(EarfunCommand.SET_AUTO_OFF, v))
        }
        else -> if (key.startsWith(BUTTON_PREFIX)) buttonChoice(key.removePrefix(BUTTON_PREFIX), option) else touchChoice(key, option)
    }

    /**
     * A button's action: on the Wave Pro X the button, the gesture and the action (HP1000ProtocolParser.setKeyMode; the
     * noise button's call gestures as button 81); on the other headphones all six bytes, one changed.
     */
    private fun buttonChoice(key: String, option: String): List<ByteArray>? {
        val b = model.buttons ?: return null
        val action = EarfunValues.KEY_ACTIONS[b.map]?.get(option) ?: return null
        val k = keys?.toMutableMap() ?: return null
        k[key] = action
        keys = k
        readBacks += read(EarfunQcc.GET_KEY, EarfunCommand.GET_KEY)
        if (b.layout != WAVE_PRO_X) return listOf(ef(EarfunCommand.SET_KEY, *HEAD_BUTTONS.map { k[it] ?: 0 }.toIntArray()))
        val button = key.substringBefore('_')
        val gesture = key.substringAfter('_')
        val buttons = EarfunValues.KEY_BUTTONS.getValue(WAVE_PRO_X)
        val buttonByte = if (gesture.endsWith("_call")) buttons.getValue("nc_call") else buttons[button] ?: return null
        val gestureByte = EarfunValues.KEY_GESTURES.getValue(WAVE_PRO_X)[gesture] ?: return null
        return listOf(qcc(EarfunQcc.SET_KEY, buttonByte, gestureByte, action))
    }

    /**
     * A gesture's action: on the Qualcomm models the gesture and the action on the earbud's side
     * ([gesture, action, 0, 0] left, [0, 0, gesture, action] right); on the others all eight bytes, one changed.
     */
    private fun touchChoice(key: String, option: String): List<ByteArray>? {
        val t = model.touch ?: return null
        if (!key.startsWith("touch_")) return null
        val side = key.substringAfterLast('_')
        val gesture = key.removePrefix("touch_").removeSuffix("_$side")
        if (side !in SIDES || gesture !in GESTURES + CALL_GESTURES) return null
        val map = if (gesture in CALL_GESTURES) t.callMap ?: return null else t.map
        val action = EarfunValues.KEY_ACTIONS[map]?.get(option) ?: return null
        val k = keys?.toMutableMap() ?: return null
        k["${gesture}_$side"] = action
        keys = k
        readBacks += read(EarfunQcc.GET_KEY, EarfunCommand.GET_KEY)
        if (!qualcomm) return listOf(ef(EarfunCommand.SET_KEY, *efTouchOrder().map { k[it] ?: 0 }.toIntArray()))
        val g = EarfunValues.KEY_GESTURES.getValue(QUALCOMM).getValue(gesture)
        return listOf(if (side == SIDES[0]) qcc(EarfunQcc.SET_KEY, g, action, 0, 0) else qcc(EarfunQcc.SET_KEY, 0, 0, g, action))
    }

    // --- EQ ---

    /** The slider positions (-10 to 10) of a curve in dB, one per band: the step nearest each gain. */
    public fun positions(gainsDb: List<Double>): List<Int>? {
        val e = model.eq ?: return null
        if (gainsDb.size != e.hz.size) return null
        return gainsDb.map { db ->
            if (e.positionGain) db.roundToInt().coerceIn(-POSITIONS, POSITIONS)
            else EarfunValues.GAIN_DB.indices.minBy { abs(EarfunValues.GAIN_DB[it] - db) } - POSITIONS
        }
    }

    /** The gain in dB of slider position [p]. */
    public fun gainDb(p: Int): Double = if (model.eq?.positionGain == true) p.toDouble() else EarfunValues.GAIN_DB[p + POSITIONS]

    /**
     * The frames that put the curve [positions] (one per band, -10 to 10) on the earbuds, a frame per band as EarFun's
     * app writes them (BaseEquailzerProtocol.sendEq and each model's equalizer): the band's index, the master gain,
     * the frequency x3, the gain x60 and the Q x4096, each two bytes; on the Qualcomm models inside GAIA vendor 001D's
     * 0E 01.
     */
    public fun eqFrames(positions: List<Int>): List<ByteArray>? {
        val e = model.eq ?: return null
        if (positions.size != e.hz.size) return null
        val p = positions.map { it.coerceIn(-POSITIONS, POSITIONS) }
        val bands = p.indices.map { i -> Triple(e.hz[i], e.q[i], (gainDb(p[i]) * GAIN_SCALE).toInt()) } +
            e.fixedAfter.map { Triple(it.hz, it.q, (it.db * GAIN_SCALE).toInt()) }
        val master = master(e, p)
        return bands.mapIndexed { i, (hz, q, gain) ->
            val head = when (e.frame) {
                FRAME_COUNT -> byteArrayOf(bands.size.toByte(), (i + 1).toByte())
                else -> byteArrayOf((INDEX_BASE + i).toByte())
            }
            val values = u16(master) + u16((hz * FREQ_SCALE).toInt()) + u16(gain) + u16((q * Q_SCALE).toInt())
            val payload = head + values + if (e.frame == FRAME_INDEX_CR) byteArrayOf(BAND_END) else ByteArray(0)
            if (qualcomm) Frame.build(EarfunQcc.EQ_COMMAND, payload, EarfunQcc.EQ_VENDOR) else EarfunFrame(EarfunCommand.SET_EQ, payload).bytes()
        }
    }

    /** The master gain x60 as EarFun's app works it out for the model, from the highest position. */
    private fun master(e: EarfunEqSpec, p: List<Int>): Int {
        val m = e.master
        val peak = p.maxOrNull() ?: 0
        m.db?.let { return (it * GAIN_SCALE).toInt() }
        m.byPeak?.let { return (it[peak + POSITIONS] * GAIN_SCALE).toInt() }
        val base = m.base ?: 0.0
        val f = base - gainDb(peak).coerceAtLeast(0.0)
        return if (m.whole) f.toInt() * GAIN_SCALE.toInt() else (f * GAIN_SCALE).toInt()
    }

    private fun u16(v: Int): ByteArray = byteArrayOf((v shr 8).toByte(), v.toByte())

    private fun efSwitch(on: Boolean): Int = if (on) EF_ON else EF_OFF

    private fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF

    public companion object {
        private const val QUALCOMM = EarfunModel.QUALCOMM
        private const val EARFUN = EarfunModel.EARFUN
        private const val OFF = "off"
        private const val AMBIENT = "ambient"
        private const val NEVER = "never"
        private const val OTHER = "other"
        private const val BOTH = "both"
        private const val EACH = "each"
        private const val PERCENT = 100

        /** EarFun's own frame writes switches as 2 on, 1 off. */
        private const val EF_ON = 2
        private const val EF_OFF = 1

        private const val ANC_MODE_KEY = "ef_anc_mode"
        private const val AMBIENT_MODE_KEY = "ef_ambient_mode"
        private const val LOCK_KEY = "ef_touch_lock"
        private const val LOCK_LEFT_KEY = "ef_touch_lock_left"
        private const val LOCK_RIGHT_KEY = "ef_touch_lock_right"

        /** The gestures in the order both protocols hold them, and the screens' words for them; then the call gestures. */
        private val GESTURES = listOf("single", "double", "triple", "hold")
        private val CALL_GESTURES = listOf("triple_call", "hold_call")
        private val SIDES = listOf("left", "right")

        /** Each Qualcomm touch gesture's place in the key read, the left earbud's; the right's follows (positionKeyMap). */
        private val QCC_TOUCH_INDEX = mapOf("single" to 0, "double" to 2, "triple" to 4, "hold" to 6, "triple_call" to 12, "hold_call" to 14)

        /** The headphones' buttons in EarFun's frame (EarfunHeadProtocolParser): volume up, volume down and noise control, press then hold. */
        private val HEAD_BUTTONS = listOf("plus_single", "plus_hold", "minus_single", "minus_hold", "nc_single", "nc_hold")

        /** The Wave Pro X's buttons by their byte in its key read, after the command (HP1000ProtocolParser.resetData). */
        private val WAVE_PRO_X_BUTTONS = mapOf(
            "plus_single" to 1, "plus_hold" to 3, "minus_single" to 4, "minus_hold" to 6,
            "nc_single" to 7, "nc_double" to 8, "nc_hold" to 9, "nc_single_call" to 10, "nc_hold_call" to 12,
        )
        private const val WAVE_PRO_X = "wave_pro_x"
        private const val BUTTON_PREFIX = "ef_button_"

        public const val RESTART_KEY: String = PowerActions.RESTART
        public const val POWER_OFF_KEY: String = PowerActions.POWER_OFF

        /** SET_CONTROL_PLAY's value for powering off. */
        private const val POWER_OFF = 1

        /** A Qualcomm key read's first action byte, after the status: the left earbud's one tap. */
        private const val QCC_KEY_FIRST = 2

        /** The Air Pro 4+'s and 5's in-ear bits: set turns pausing, or answering calls, off. */
        private const val CHECK_EAR_PAUSE_OFF = 1
        private const val CHECK_EAR_ANSWER_OFF = 2

        private val MICS = linkedMapOf(0 to "automatic", 1 to "left", 2 to "right")
        private val PROMPT_VOLUMES = listOf("0", "25", "50", "75", "100")
        private val HEARING = listOf("off", "90", "80", "70", "60", "50")

        // The EQ (docs/earfun/PROTOCOL.md section 6).
        public const val POSITIONS: Int = 10
        private const val GAIN_SCALE = 60.0
        private const val FREQ_SCALE = 3.0
        private const val Q_SCALE = 4096.0
        private const val INDEX_BASE = 0xA1
        private const val BAND_END: Byte = 0x0D
        private const val FRAME_COUNT = "count"
        private const val FRAME_INDEX_CR = "index_cr"

        // GAIA v3 on vendor 001D: the features, the protocol version, registering for a feature's notifications, and firmware update's feature.
        private const val GAIA_GET_FEATURES = 0x0001
        private const val GAIA_SET_PROTOCOL = 0x000D
        private const val GAIA_REGISTER = 0x0007
        private const val GAIA_PROTOCOL_VERSION = 4
        private const val GAIA_UPGRADE_FEATURE = 0x06
        private val GAIA_NOTIFICATIONS = listOf(0x0D, 0x05, 0x08, 0x01, 0x07, 0x07)

        /** The shared noise mode's number for [name] (data/soundpeats/commands.json). */
        private fun ancNumber(name: String): Int = Command.ANC_MODE.labels.entries.first { it.value == name }.key
    }
}
