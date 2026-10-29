package io.github.omegadestroyer.pairfree.protocol.bose

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value

/**
 * One connection to Bose headphones or earbuds over BMAP (docs/bose/PROTOCOL.md), shown on PairFree's
 * screens. It reads the product, the firmware, the battery and every setting PairFree offers; a
 * function the device does not have answers with an error and stays out. The battery and firmware go
 * to [state], everything else to [groups].
 *
 * Every write is a SETGET, as Bose's own app sends these settings, or for the listening mode a START;
 * bosectl (github.com/aaronsb/bosectl, MIT) found both work without Bose's cloud sign-in, which plain
 * SET needs. The device answers a SETGET with the new value, or with an error, after which PairFree
 * reads the setting again so the screen shows what the device holds.
 */
public class BoseSession {
    /** What the shared screens show. */
    public var state: EarbudState = EarbudState()
        private set

    public var productId: Int? = null
        private set

    public var variant: Int? = null
        private set

    public var firmware: String? = null
        private set

    /** The model, once the device has reported its product id. */
    public val model: BoseModel? get() = BoseModels.of(productId)

    /**
     * The last payload of each function and operator the device sent, for the device report: only the
     * functions PairFree reads, so nothing the device sends unasked (which can hold another device's
     * address) and no mode names, which the user types.
     */
    public val replies: Map<String, ByteArray> get() = _replies
    private val _replies = linkedMapOf<String, ByteArray>()

    private var voicePrompts: Int? = null
    private var cnc: ByteArray? = null
    private var anr: Int? = null
    private var eq: List<EqBand> = emptyList()
    private var multipoint: Int? = null
    private var sidetone: ByteArray? = null
    private var autoPlayPause: Boolean? = null
    private var autoAnswer: Boolean? = null
    private var standbyMinutes: Int? = null
    private var button: ByteArray? = null
    private var spatial: Int? = null
    private var currentMode: Int? = null
    private var settingsConfig: ByteArray? = null
    private val modes = sortedMapOf<Int, Mode>()
    private val outbox = mutableListOf<BosePacket>()

    /** One EQ band as the device reports it: its id, range and value (Bose's RangeControl). */
    public data class EqBand(val id: Int, val min: Int, val max: Int, val value: Int)

    /** One listening mode: index, prompt (Quiet is 0 1, Aware 0 2), name, and whether it is set up. */
    public data class Mode(val index: Int, val prompt: Int, val name: String, val userSlot: Boolean, val configured: Boolean) {
        /** Firmware presets and the modes the user set up; empty user slots are left out. */
        val listed: Boolean get() = (!userSlot || configured) && name.isNotBlank()
    }

    /** The reads on opening the channel. */
    public fun start(): List<BosePacket> = buildList {
        // The BMAP version first: bosectl sends it before anything else, as the QC35 and QC45 need.
        add(BosePacket.get(BoseFunction.PRODUCT_INFO_BMAP_VERSION))
        add(BosePacket.get(BoseFunction.PRODUCT_INFO_PRODUCT_ID_VARIANTS))
        add(BosePacket.get(BoseFunction.PRODUCT_INFO_FIRMWARE_VERSION))
        add(BosePacket.get(BoseFunction.STATUS_BATTERY_LEVEL))
        for (f in SETTINGS) add(BosePacket.get(f))
        add(BosePacket.get(BoseFunction.AUDIO_MANAGEMENT_SPATIAL_AUDIO_MODE))
        add(BosePacket.get(BoseFunction.AUDIO_MODES_CURRENT_MODE))
        add(BosePacket.get(BoseFunction.AUDIO_MODES_SETTINGS_CONFIG))
        // Every mode's configuration, as STATUS messages (bosectl's mode list).
        add(BosePacket.start(BoseFunction.AUDIO_MODES_GET_ALL))
    }

    /** What the session wants sent next: settings to read again after a refused write. */
    public fun takeOutbox(): List<BosePacket> = outbox.toList().also { outbox.clear() }

    /** The battery read, to send again now and then. */
    public fun batteryRequests(): List<BosePacket> = listOf(BosePacket.get(BoseFunction.STATUS_BATTERY_LEVEL))

    /** True when [reply] answers [sent]: the same function, whatever the operator. */
    public fun answers(sent: BosePacket, reply: BosePacket): Boolean = reply.function == sent.function

    /** Applies one packet from the device. Returns true when [state] or [groups] changed. */
    public fun receive(packet: BosePacket): Boolean {
        val p = packet.payload
        if (packet.function in REPORTED) _replies["${packet.block}.${BoseFunction.function(packet.function)} op ${packet.operator}"] = p.copyOf()
        val before = state to groups
        when (packet.operator) {
            BoseOperator.STATUS, BoseOperator.RESULT -> status(packet.function, p)
            // A refused write: read the setting again, so the screen shows what the device holds.
            BoseOperator.ERROR -> if (packet.function in WRITTEN) outbox += BosePacket.get(packet.function)
        }
        refresh()
        return (state to groups) != before
    }

    private fun status(function: Int, p: ByteArray) {
        when (function) {
            BoseFunction.PRODUCT_INFO_PRODUCT_ID_VARIANTS -> if (p.size >= 3) {
                productId = (p.u8(0) shl 8) or p.u8(1)
                variant = p.u8(2)
            }
            BoseFunction.PRODUCT_INFO_FIRMWARE_VERSION -> firmware = p.decodeToString().trim { it <= ' ' }.ifEmpty { null }
            BoseFunction.STATUS_BATTERY_LEVEL -> battery(p)
            BoseFunction.SETTINGS_VOICE_PROMPTS -> if (p.isNotEmpty()) voicePrompts = p.u8(0)
            BoseFunction.SETTINGS_CNC -> if (p.size >= 3) cnc = p.copyOf()
            BoseFunction.SETTINGS_ANR -> if (p.isNotEmpty()) anr = p.u8(0)
            BoseFunction.SETTINGS_RANGE_CONTROL -> eq = rangeControl(p)
            BoseFunction.SETTINGS_MULTIPOINT -> if (p.isNotEmpty()) multipoint = p.u8(0)
            BoseFunction.SETTINGS_SIDETONE -> if (p.size >= 2) sidetone = p.copyOf()
            BoseFunction.SETTINGS_AUTO_PLAY_PAUSE -> if (p.isNotEmpty()) autoPlayPause = p.u8(0) == 1
            BoseFunction.SETTINGS_AUTO_ANSWER -> if (p.isNotEmpty()) autoAnswer = p.u8(0) == 1
            // Minutes, 0 for never; Bose's app reads a reply of three bytes or more as the first byte and the third.
            BoseFunction.SETTINGS_STANDBY_TIMER -> if (p.isNotEmpty()) standbyMinutes = if (p.size < 3) p.u8(0) else (p.u8(2) shl 8) or p.u8(0)
            BoseFunction.SETTINGS_BUTTONS -> if (p.size >= 3) button = p.copyOf()
            BoseFunction.AUDIO_MANAGEMENT_SPATIAL_AUDIO_MODE -> if (p.isNotEmpty()) spatial = p.u8(0)
            BoseFunction.AUDIO_MODES_CURRENT_MODE -> if (p.isNotEmpty()) currentMode = p.u8(0)
            BoseFunction.AUDIO_MODES_SETTINGS_CONFIG -> if (p.size >= SETTINGS_CONFIG_SIZE) settingsConfig = p.copyOf(SETTINGS_CONFIG_SIZE)
            BoseFunction.AUDIO_MODES_MODE_CONFIG -> mode(p)?.let { modes[it.index] = it }
        }
    }

    /**
     * The battery: one record of the level, the minutes left (2 bytes, FFFF unknown) and the component
     * (0 single, 1 right, 2 left, 3 case), as Bose's app reads it; one battery may come without a
     * component. A level over 100 is unknown.
     */
    private fun battery(p: ByteArray) {
        if (p.isEmpty()) return
        val records = if (p.size <= RECORD) listOf(p) else (0 until p.size / RECORD).map { p.copyOfRange(it * RECORD, it * RECORD + RECORD) }
        val values = mutableMapOf<Command, Value>()
        for (r in records) {
            val level = r.u8(0).takeIf { it <= 100 }
            when (if (r.size >= RECORD) r.u8(3) else SINGLE) {
                SINGLE -> values[Command.BATTERY_LEFT] = Value.Number(level)
                RIGHT -> values[Command.BATTERY_RIGHT] = Value.Number(level)
                LEFT -> values[Command.BATTERY_LEFT] = Value.Number(level)
                CASE -> values[Command.BATTERY_CASE] = Value.Number(level)
            }
        }
        state = state.copy(values = state.values + values)
    }

    /** The EQ: four bytes per band, its minimum, maximum and value (signed) and its id. */
    private fun rangeControl(p: ByteArray): List<EqBand> =
        (0 until p.size / EQ_BAND).map { i ->
            val at = i * EQ_BAND
            EqBand(p.u8(at + 3), p[at].toInt(), p[at + 1].toInt(), p[at + 2].toInt())
        }

    /** A mode's configuration: index, prompt (2 bytes), user slot, set up, favourite, then 32 bytes of name. */
    private fun mode(p: ByteArray): Mode? {
        if (p.size < MODE_NAME_END) return null
        val name = p.copyOfRange(MODE_NAME_START, MODE_NAME_END).takeWhile { it != 0.toByte() }.toByteArray().decodeToString()
        return Mode(p.u8(0), (p.u8(1) shl 8) or p.u8(2), name, userSlot = p.u8(3) == 1, configured = p.u8(4) == 1)
    }

    /** Puts the firmware where the shared screens read it: the model's name and the version. */
    private fun refresh() {
        val fw = firmware ?: return
        val name = model?.name ?: productId?.let { "Bose " + it.toString(16).uppercase().padStart(4, '0') } ?: "Bose"
        state = state.copy(values = state.values + (Command.FIRMWARE_VERSION to Value.Text("${name}_$fw")))
    }

    /** Every setting the device has, grouped by heading and tab. */
    public val groups: List<SettingGroup>
        get() = listOfNotNull(
            group("noise", SettingTab.HOME, noiseRows()),
            group(
                "sound", SettingTab.SOUND,
                eq.mapNotNull { b -> EQ_KEYS[b.id]?.let { Setting.Level(it, b.value.coerceIn(b.min, b.max), b.min, b.max) } } +
                    listOfNotNull(
                        spatial?.let { v -> SPATIAL.firstOrNull { it.first == v }?.let { Setting.Choice(SPATIAL_KEY, it.second, SPATIAL.map { s -> s.second }) } },
                    ),
            ),
            group("touch", SettingTab.CONTROLS, listOfNotNull(button?.let(::buttonRow))),
            group(
                "connection", SettingTab.DEVICE,
                listOfNotNull(
                    multipoint?.takeIf { it and MULTIPOINT_SUPPORTED != 0 }?.let {
                        val on = it and MULTIPOINT_ENABLED != 0
                        // Some models cannot turn it off (Bose's "disable supported" bit).
                        Setting.Toggle("multipoint", on, enabled = !on || it and MULTIPOINT_DISABLE_SUPPORTED != 0)
                    },
                    standbyMinutes?.let { m ->
                        val current = if (m == 0) NEVER else m.toString()
                        Setting.Choice(AUTO_OFF_KEY, current, (STANDBY_MINUTES.map { it.toString() } + NEVER + current).distinct())
                    },
                    autoPlayPause?.let { Setting.Toggle("pause_taken_off", it) },
                    autoAnswer?.let { Setting.Toggle("auto_answer", it) },
                    voicePrompts?.takeIf { it and PROMPTS_TOGGLABLE != 0 }?.let { Setting.Toggle("voice_prompts", it and PROMPTS_ENABLED != 0) },
                ),
            ),
            group(
                "calls", SettingTab.DEVICE,
                listOfNotNull(
                    sidetone?.let { s ->
                        val supported = if (s.size >= 3) s.u8(2) else ALL_SIDETONE
                        val options = SIDETONE.filter { (value, _) -> supported and (1 shl value) != 0 }.map { it.second }
                        Setting.Choice("sidetone_level", SIDETONE.firstOrNull { it.first == s.u8(1) }?.second, options)
                    },
                ),
            ),
        )

    private fun noiseRows(): List<Setting> = buildList {
        val listed = modes.values.filter { it.listed }
        val current = currentMode
        if (listed.isNotEmpty() && current != null) {
            add(Setting.Choice("audio_mode", listed.firstOrNull { it.index == current }?.let(::optionOf), listed.map(::optionOf)))
        }
        anr?.let { v -> add(Setting.Choice("anr", ANR.firstOrNull { it.first == v }?.second, ANR.map { it.second })) }
        cnc?.let { c ->
            val max = c.u8(0) - 1
            if (max > 0) add(Setting.Level("noise_level", c.u8(1).coerceIn(0, max), 0, max))
        }
    }

    /**
     * The configurable button (Bose's SettingsButtons, 1.9): the button, its gesture, the action, then a mask of the
     * actions it can take and one of those it cannot take now, read as Bose's app reads them (the last byte holds
     * actions 0 to 7). PairFree offers those that need nothing set up elsewhere, and shows any other as it is.
     */
    private fun buttonRow(b: ByteArray): Setting.Choice {
        val supported = actionMask(b, 3)
        val unavailable = actionMask(b, MASK_SIZE + 3)
        val offered = supported.filter { it in SELECTABLE_ACTIONS && it !in unavailable }
        return Setting.Choice(BUTTON_KEY + b.u8(0), actionName(b.u8(2)), (offered + b.u8(2)).distinct().map(::actionName))
    }

    /** The actions a mask of up to four bytes from [at] names, as Bose's BitSetUtil reads it: bit 0 is in the last byte. */
    private fun actionMask(b: ByteArray, at: Int): List<Int> {
        if (b.size <= at) return emptyList()
        val bytes = b.copyOfRange(at, minOf(at + MASK_SIZE, b.size))
        return (1 until bytes.size * 8).filter { i -> (bytes[bytes.size - 1 - i / 8].toInt() shr (i % 8)) and 1 == 1 }
    }

    private fun actionName(id: Int): String = ACTIONS[id] ?: "other_$id"

    /** A mode as a choice: its name, with its index where two share a name. */
    private fun optionOf(m: Mode): String = if (modes.values.count { it.listed && it.name == m.name } > 1) "${m.name} (${m.index})" else m.name

    private fun group(key: String, tab: SettingTab, settings: List<Setting>) = settings.takeIf { it.isNotEmpty() }?.let { SettingGroup(key, tab, it) }

    /**
     * The packets for a change from the screens, or null when the device does not have the setting.
     * The change shows at once; the device's answer replaces it.
     */
    public fun change(change: SettingChange): List<BosePacket>? {
        val setting = groups.flatMap { it.settings }.firstOrNull { it.key == change.key } ?: return null
        val out: BosePacket = when (change) {
            is SettingChange.Toggle -> {
                if (setting !is Setting.Toggle || !setting.enabled) return null
                val on = change.on
                val b = if (on) 1 else 0
                when (change.key) {
                    "multipoint" -> BosePacket.setGet(BoseFunction.SETTINGS_MULTIPOINT, b).also {
                        multipoint = multipoint?.let { m -> if (on) m or MULTIPOINT_ENABLED else m and MULTIPOINT_ENABLED.inv() }
                    }
                    "pause_taken_off" -> BosePacket.setGet(BoseFunction.SETTINGS_AUTO_PLAY_PAUSE, b).also { autoPlayPause = on }
                    "auto_answer" -> BosePacket.setGet(BoseFunction.SETTINGS_AUTO_ANSWER, b).also { autoAnswer = on }
                    "voice_prompts" -> {
                        // Bose's app writes on (bit 5) and the language (bits 0 to 4) in one byte.
                        val v = voicePrompts ?: return null
                        val next = (if (on) PROMPTS_ENABLED else 0) or (v and LANGUAGE_MASK)
                        voicePrompts = (v and PROMPTS_ENABLED.inv()) or (next and PROMPTS_ENABLED)
                        BosePacket.setGet(BoseFunction.SETTINGS_VOICE_PROMPTS, next)
                    }
                    else -> return null
                }
            }
            is SettingChange.Level -> {
                if (setting !is Setting.Level) return null
                val v = change.value.coerceIn(setting.min, setting.max)
                when (change.key) {
                    "noise_level" -> noiseLevel(v) ?: return null
                    else -> {
                        val band = EQ_KEYS.entries.firstOrNull { it.value == change.key }?.key ?: return null
                        eq = eq.map { if (it.id == band) it.copy(value = v) else it }
                        BosePacket.setGet(BoseFunction.SETTINGS_RANGE_CONTROL, v and 0xFF, band)
                    }
                }
            }
            is SettingChange.Choice -> {
                if (setting !is Setting.Choice || change.option !in setting.options) return null
                when (change.key) {
                    "audio_mode" -> {
                        val mode = modes.values.firstOrNull { it.listed && optionOf(it) == change.option } ?: return null
                        currentMode = mode.index
                        // The mode, then no voice prompt.
                        BosePacket.start(BoseFunction.AUDIO_MODES_CURRENT_MODE, mode.index, 0)
                    }
                    "anr" -> {
                        val v = ANR.firstOrNull { it.second == change.option }?.first ?: return null
                        anr = v
                        BosePacket.setGet(BoseFunction.SETTINGS_ANR, v)
                    }
                    AUTO_OFF_KEY -> {
                        val m = if (change.option == NEVER) 0 else change.option.toIntOrNull() ?: return null
                        standbyMinutes = m
                        // Bose's app: one byte up to 255 minutes, else two, the low one first.
                        if (m <= 0xFF) BosePacket.setGet(BoseFunction.SETTINGS_STANDBY_TIMER, m)
                        else BosePacket.setGet(BoseFunction.SETTINGS_STANDBY_TIMER, m and 0xFF, m shr 8)
                    }
                    SPATIAL_KEY -> {
                        val v = SPATIAL.firstOrNull { it.second == change.option }?.first ?: return null
                        spatial = v
                        BosePacket.setGet(BoseFunction.AUDIO_MANAGEMENT_SPATIAL_AUDIO_MODE, v)
                    }
                    "sidetone_level" -> {
                        val s = sidetone ?: return null
                        val v = SIDETONE.firstOrNull { it.second == change.option }?.first ?: return null
                        sidetone = s.copyOf().also { it[1] = v.toByte() }
                        // Whether it is kept (bit 0 of the first byte, as the device reported it), then the level.
                        BosePacket.setGet(BoseFunction.SETTINGS_SIDETONE, s.u8(0) and 1, v)
                    }
                    else -> {
                        // The button: its id and gesture as the device reported them, then the action.
                        val b = button?.takeIf { change.key == BUTTON_KEY + it.u8(0) } ?: return null
                        val action = (listOf(b.u8(2)) + ACTIONS.keys).firstOrNull { actionName(it) == change.option } ?: return null
                        button = b.copyOf().also { it[2] = action.toByte() }
                        BosePacket.setGet(BoseFunction.SETTINGS_BUTTONS, b.u8(0), b.u8(1), action)
                    }
                }
            }
            is SettingChange.Press -> return null
        }
        return listOf(out)
    }

    /**
     * The noise cancelling level. Models with AudioModes' settings (31.10) take it there, with the rest
     * of those settings as they are, as bosectl writes it; the others through Settings' CNC (1.5): the
     * step, and whether it is on.
     */
    private fun noiseLevel(v: Int): BosePacket? {
        val c = cnc ?: return null
        cnc = c.copyOf().also { it[1] = v.toByte() }
        val config = settingsConfig
        if (config != null) {
            settingsConfig = config.copyOf().also { it[0] = v.toByte() }
            return BosePacket(BoseFunction.AUDIO_MODES_SETTINGS_CONFIG, BoseOperator.SETGET, settingsConfig!!.copyOf())
        }
        return BosePacket.setGet(BoseFunction.SETTINGS_CNC, v, c.u8(2) and CNC_ENABLED)
    }

    /** False for what PairFree never sends: firmware update, factory default, power, authentication. */
    public fun allowed(packet: BosePacket): Boolean = packet.function !in BoseFunction.NEVER_SEND

    private fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF

    public companion object {
        /** The settings read on connecting. */
        private val SETTINGS = listOf(
            BoseFunction.SETTINGS_VOICE_PROMPTS, BoseFunction.SETTINGS_CNC, BoseFunction.SETTINGS_ANR,
            BoseFunction.SETTINGS_RANGE_CONTROL, BoseFunction.SETTINGS_MULTIPOINT, BoseFunction.SETTINGS_SIDETONE,
            BoseFunction.SETTINGS_AUTO_PLAY_PAUSE, BoseFunction.SETTINGS_AUTO_ANSWER, BoseFunction.SETTINGS_STANDBY_TIMER,
            BoseFunction.SETTINGS_BUTTONS,
        )

        /** What the device report shows: the functions PairFree reads. */
        private val REPORTED = SETTINGS.toSet() + setOf(
            BoseFunction.AUDIO_MANAGEMENT_SPATIAL_AUDIO_MODE,
            BoseFunction.PRODUCT_INFO_BMAP_VERSION, BoseFunction.PRODUCT_INFO_PRODUCT_ID_VARIANTS, BoseFunction.PRODUCT_INFO_FIRMWARE_VERSION,
            BoseFunction.STATUS_BATTERY_LEVEL, BoseFunction.AUDIO_MODES_CURRENT_MODE, BoseFunction.AUDIO_MODES_SETTINGS_CONFIG,
        )

        /** What PairFree writes, so a refusal of one is read again. */
        private val WRITTEN = SETTINGS.toSet() + setOf(
            BoseFunction.AUDIO_MODES_CURRENT_MODE, BoseFunction.AUDIO_MODES_SETTINGS_CONFIG, BoseFunction.AUDIO_MANAGEMENT_SPATIAL_AUDIO_MODE,
        )

        private const val RECORD = 4
        private const val SINGLE = 0
        private const val RIGHT = 1
        private const val LEFT = 2
        private const val CASE = 3
        private const val EQ_BAND = 4
        private const val MODE_NAME_START = 6
        private const val MODE_NAME_END = 38
        private const val SETTINGS_CONFIG_SIZE = 5

        // Bose's SettingsMultipointResponse: bit 0 on, bit 1 supported, bit 2 can be turned off.
        private const val MULTIPOINT_ENABLED = 1
        private const val MULTIPOINT_SUPPORTED = 2
        private const val MULTIPOINT_DISABLE_SUPPORTED = 4

        // Bose's SettingsVoicePromptsResponse: bits 0 to 4 the language, bit 5 on, bit 7 can be switched.
        private const val LANGUAGE_MASK = 0x1F
        private const val PROMPTS_ENABLED = 0x20
        private const val PROMPTS_TOGGLABLE = 0x80

        /** Bose's SettingsCncResponse: bit 0 of the third byte is on. */
        private const val CNC_ENABLED = 1

        /** Bose's AnrMode, by value. */
        private val ANR = listOf(0 to "off", 3 to "low", 1 to "high", 2 to "wind")

        /** Bose's SidetoneMode, by value; the reply's third byte has a bit for each the device has. */
        private val SIDETONE = listOf(0 to "off", 3 to "low", 2 to "medium", 1 to "high")
        private const val ALL_SIDETONE = 0x0F

        /** The standby timer's choices: based-connect's for the QC35 (0 is never), as minutes. */
        private const val AUTO_OFF_KEY = "auto_off"
        private const val NEVER = "never"
        private val STANDBY_MINUTES = listOf(5, 20, 40, 60, 180)

        /** Bose's SpatialAudioModeTypes, by value. */
        private const val SPATIAL_KEY = "spatial_audio_mode"
        private val SPATIAL = listOf(0 to "off", 1 to "room", 2 to "head")

        /** The button's row: its key and Bose's ConfigurableButtonId. */
        private const val BUTTON_KEY = "bose_button_"
        private const val MASK_SIZE = 4

        /** Bose's ActionButtonMode, by value, for the ones PairFree names. */
        private val ACTIONS = mapOf(
            1 to "voice_assistant", 2 to "noise_cycle", 3 to "battery_level", 4 to "play_pause", 5 to "cnc_up", 6 to "cnc_down",
            7 to "wake_word", 8 to "switch_device", 9 to "conversation_mode", 10 to "track_forward", 11 to "track_back",
            13 to "wind_mode", 14 to "disabled", 16 to "spotify", 17 to "modes_carousel", 18 to "volume_presets",
            19 to "spatial_audio", 27 to "alexa",
        )

        /** What PairFree lets the user pick: the actions that need no assistant, app or account set up first. */
        private val SELECTABLE_ACTIONS = setOf(1, 2, 3, 4, 5, 6, 8, 9, 10, 11, 13, 14, 17, 18, 19)

        /** Bose's RangeControlIdentifier, by id. */
        private val EQ_KEYS = mapOf(0 to "eq_bass", 3 to "eq_low_mid", 1 to "eq_mid", 4 to "eq_high_mid", 2 to "eq_treble")
    }
}
