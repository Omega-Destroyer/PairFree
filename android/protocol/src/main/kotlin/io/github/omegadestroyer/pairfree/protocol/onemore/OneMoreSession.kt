package io.github.omegadestroyer.pairfree.protocol.onemore

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value

/**
 * One connection to 1MORE earbuds or headphones (docs/1more/PROTOCOL.md), shown on PairFree's screens. It
 * reads what the [model]'s entry in data/1more/devices.json says the model has, as 1MORE's app does with
 * its product list, and a setting shows once its read has answered. The battery, firmware, noise mode,
 * two devices and finding the earbuds go to [state]; everything else to [groups].
 *
 * A change shows at once; the earbuds answer a write with success or failure, a refused write is read
 * again at once, and [takeReadBacks] gives the reads that confirm the others, which the connection sends
 * a little later, so the screen ends up showing what the earbuds hold.
 */
public class OneMoreSession(public val model: OneMoreModel?) {
    public var state: EarbudState = EarbudState()
        private set

    public var firmware: String? = null
        private set

    /** The last payload of each read PairFree sends, for the device report. */
    public val replies: Map<String, ByteArray> get() = _replies
    private val _replies = linkedMapOf<String, ByteArray>()

    private val form: Form get() = model?.form ?: Form.EARBUDS
    private val modes: List<String> get() = model?.listenModes ?: DEFAULT_MODES

    private var sequence = 0
    private val outbox = mutableListOf<OneMoreFrame>()
    private val readBacks = mutableListOf<OneMoreFrame>()

    private var listenMode: Int? = null
    private var cancelling: Int? = null     // the cancelling mode to go back to
    private var transparency: Int? = null   // the transparency mode to go back to
    private var connection: Int? = null
    private var autoplay: Int? = null
    private var sleepDetection: Boolean? = null
    private var dualDevice: Boolean? = null
    private var hotWord: Boolean? = null
    private var spatial: Boolean? = null
    private var doubleClick: Int? = null
    private var tripleClick: Int? = null
    private var custom: ByteArray? = null
    private var earReturn: Boolean? = null
    private var earReturnDepth: Int? = null
    private var volumeLimit: Int? = null
    private var findSupported = false
    private var presetSound: Int? = null
    private var eqRaw: List<Int>? = null   // the custom EQ's raw gain per band, as the earbuds reported it
    private val ringing = mutableSetOf<String>()

    /** A frame to the earbuds with the next sequence number. */
    public fun frame(command: Int, vararg payload: Int): OneMoreFrame =
        OneMoreFrame(command, ByteArray(payload.size) { payload[it].toByte() }, nextSequence())

    private fun nextSequence(): Int = sequence.also { sequence = (sequence + 1) % OneMoreFrame.SEQUENCE_WRAP }

    /** The reads on opening the channel: those 1MORE's app sends, for what the model has. */
    public fun start(): List<OneMoreFrame> = buildList {
        val m = model
        add(frame(OneMoreCommand.GET_BINAURAL_INFO))
        if (modes.isNotEmpty()) add(frame(OneMoreCommand.GET_LISTEN_MODE))
        if (m == null) return@buildList
        if (m.connection.isNotEmpty()) add(frame(OneMoreCommand.GET_CONNECTION_MODE))
        if (m.dualDevice) add(frame(OneMoreCommand.GET_DUAL_DEVICE))
        if (m.spatialAudio) add(frame(OneMoreCommand.GET_SPATIAL_AUDIO))
        if (m.autoplay) add(frame(OneMoreCommand.GET_AUTOPLAY))
        if (m.sleepDetection) add(frame(OneMoreCommand.GET_SLEEP_DETECTION))
        if (m.hotWord) add(frame(OneMoreCommand.GET_HOT_WORD))
        when (m.touch) {
            TOUCH_EACH -> add(frame(OneMoreCommand.GET_ALL_CUSTOM))
            TOUCH_BOTH -> {
                add(frame(OneMoreCommand.GET_DOUBLE_CLICK))
                add(frame(OneMoreCommand.GET_TRIPLE_CLICK))
            }
        }
        if (m.earReturn) {
            add(frame(OneMoreCommand.GET_EAR_RETURN))
            add(frame(OneMoreCommand.GET_EAR_RETURN_DEPTH))
        }
        if (m.volumeLimit) add(frame(OneMoreCommand.GET_MAX_SOUND_LIMIT))
        if (m.eqPresets.isNotEmpty()) add(frame(OneMoreCommand.GET_PRESET_SOUND))
        eqRead()?.let(::add)
        // 1MORE's app asks with 01 whether the earbuds can ring; earbuds that can answer it.
        if (form == Form.EARBUDS) add(frame(OneMoreCommand.FIND_DEVICE_SUPPORT, 1))
    }

    /** The battery read, to send again now and then. */
    public fun batteryRequests(): List<OneMoreFrame> = listOf(frame(OneMoreCommand.GET_BINAURAL_INFO))

    /** What the session wants sent next: a read after a refused write. */
    public fun takeOutbox(): List<OneMoreFrame> = outbox.toList().also { outbox.clear() }

    /** The reads that confirm the last changes, for the connection to send a little later. */
    public fun takeReadBacks(): List<OneMoreFrame> = readBacks.distinctBy { it.command }.also { readBacks.clear() }

    /** True when [reply] answers [sent]: the earbuds answer with the same command. */
    public fun answers(sent: OneMoreFrame, reply: OneMoreFrame): Boolean = reply.fromEarbuds && reply.command == sent.command

    /** False for what PairFree never sends: the handshake, the AI licence read, and deleting or uploading music. */
    public fun allowed(frame: OneMoreFrame): Boolean = !frame.fromEarbuds && frame.command !in OneMoreCommand.NEVER_SEND

    /** Applies one frame from the earbuds. Returns true when [state] or [groups] changed. */
    public fun receive(frame: OneMoreFrame): Boolean {
        if (!frame.fromEarbuds) return false
        val before = state to groups
        val p = frame.payload
        val read = READ_OF[frame.command]
        if (read != null) {
            // A write's answer: one byte, 1 success. A refused write is read again, so the screen shows what the earbuds hold.
            if (p.isEmpty() || p.u8(0) != RESULT_SUCCESS) outbox += (if (read == OneMoreCommand.GET_EQ_PARAMETERS) eqRead() else frame(read)) ?: return false
        } else {
            if (frame.command in OneMoreCommand.NAMES) _replies[key(frame.command)] = p.copyOf()
            if (p.isNotEmpty()) reply(frame.command, p)
        }
        refresh()
        return (state to groups) != before
    }

    private fun reply(command: Int, p: ByteArray) {
        val v = p.u8(0)
        when (command) {
            OneMoreCommand.GET_BINAURAL_INFO -> binaural(p)
            OneMoreCommand.GET_LISTEN_MODE -> listen(v)
            OneMoreCommand.GET_CONNECTION_MODE -> connection = v
            OneMoreCommand.GET_DUAL_DEVICE -> dualDevice = v == ON
            OneMoreCommand.GET_SPATIAL_AUDIO -> spatial = v == ON
            OneMoreCommand.GET_AUTOPLAY -> autoplay = v
            OneMoreCommand.GET_SLEEP_DETECTION -> sleepDetection = v == ON
            OneMoreCommand.GET_HOT_WORD -> hotWord = v == ON
            OneMoreCommand.GET_DOUBLE_CLICK -> doubleClick = v
            OneMoreCommand.GET_TRIPLE_CLICK -> tripleClick = v
            OneMoreCommand.GET_ALL_CUSTOM -> if (p.size >= CUSTOM_GESTURES) custom = p.copyOf(CUSTOM_GESTURES)
            OneMoreCommand.GET_EAR_RETURN -> earReturn = v == ON
            OneMoreCommand.GET_EAR_RETURN_DEPTH -> earReturnDepth = v
            OneMoreCommand.GET_MAX_SOUND_LIMIT -> volumeLimit = v
            OneMoreCommand.FIND_DEVICE_SUPPORT -> findSupported = true
            OneMoreCommand.GET_PRESET_SOUND -> presetSound = v
            // One record per band: a flag, then the gain, frequency and Q, two bytes each, high first.
            OneMoreCommand.GET_EQ_PARAMETERS -> model?.eqBands?.let { b ->
                if (p.size >= b.hz.size * EQ_RECORD) eqRaw = b.hz.indices.map { i -> (p.u8(i * EQ_RECORD + 1) shl 8) or p.u8(i * EQ_RECORD + 2) }
            }
            // The earbuds report which side is ringing: a 0 ends that side's ring.
            OneMoreCommand.FIND_DEVICE -> {
                findSupported = true
                if (v == 0) ringing -= LEFT
                if (p.size > 1 && p.u8(1) == 0) ringing -= RIGHT
            }
        }
    }

    /**
     * Both earbuds' state (BinauralInfo): left is master (0), the left earbud's version (three bytes), its
     * battery, the right's version and battery, then the case's battery. 0 is an earbud that is not there.
     */
    private fun binaural(p: ByteArray) {
        if (p.size < BINAURAL_SIZE) return
        fun level(i: Int) = p.u8(i).takeIf { it in 1..PERCENT }
        val values = mutableMapOf<Command, Value>()
        values[Command.BATTERY_LEFT] = Value.Number(level(4) ?: level(8).takeIf { form != Form.EARBUDS })
        if (form == Form.EARBUDS) {
            values[Command.BATTERY_RIGHT] = Value.Number(level(8))
            if (model?.case != false) values[Command.BATTERY_CASE] = Value.Number(level(9))
        }
        state = state.copy(values = state.values + values)
        // The version as 1MORE's app shows it: each byte in hex.
        val left = (1..3).map { p.u8(it) }
        val right = (5..7).map { p.u8(it) }
        firmware = (left.takeIf { level(4) != null } ?: right).joinToString(".") { it.toString(16) }
    }

    private fun listen(mode: Int) {
        listenMode = mode
        when (mode) {
            in CANCELLING.values -> cancelling = mode
            in TRANSPARENCY.values -> transparency = mode
        }
    }

    /** Puts what the earbuds reported where the shared screens read it. */
    private fun refresh() {
        val values = state.values.toMutableMap()
        firmware?.let { values[Command.FIRMWARE_VERSION] = Value.Text("${model?.model ?: BRAND}_$it") }
        val mode = listenMode
        if (sharedNoise && mode != null) {
            values[Command.ANC_MODE] = Value.Number(
                when (mode) {
                    MODE_OFF -> ancNumber("off")
                    in TRANSPARENCY.values -> ancNumber("transparency")
                    else -> ancNumber("anc")
                },
            )
        }
        dualDevice?.let { values[Command.MULTIPOINT] = Value.Switch(it) }
        if (findSupported && form == Form.EARBUDS) {
            values[Command.FIND_LEFT] = Value.Switch(LEFT in ringing)
            values[Command.FIND_RIGHT] = Value.Switch(RIGHT in ringing)
        }
        state = state.copy(values = values)
    }

    /**
     * The custom EQ's read, as 1MORE's app sends it: each band's frequency, two bytes high first, with a length field
     * of 20 whatever the band count (BluetoothOrderConstents writes the header out in full).
     */
    private fun eqRead(): OneMoreFrame? {
        val b = model?.eqBands ?: return null
        val payload = ByteArray(b.hz.size * 2) { i -> (if (i % 2 == 0) b.hz[i / 2] shr 8 else b.hz[i / 2]).toByte() }
        return OneMoreFrame(OneMoreCommand.GET_EQ_PARAMETERS, payload, nextSequence(), declaredLength = EQ_READ_LENGTH)
    }

    /** A band's raw gain as a whole dB from -3 to 3: hundredths above the model's zero, or whole dB from 15 (useProtocolGain). */
    private fun db(b: OneMoreEqBands, band: Int, raw: Int): Int {
        val db = if (b.wholeDb) raw / 100 - WHOLE_DB_ZERO else (raw - b.zero - (b.offsets.getOrNull(band) ?: 0)).let { if (it < 0) (it - 50) / 100 else (it + 50) / 100 }
        return db.coerceIn(-EQ_RANGE, EQ_RANGE)
    }

    private fun raw(b: OneMoreEqBands, band: Int, db: Int): Int =
        if (b.wholeDb) (db + WHOLE_DB_ZERO) * 100 else b.zero + (b.offsets.getOrNull(band) ?: 0) + db * 100

    /**
     * The EQ the earbuds last reported: the preset sound by its name (custom when they hold the custom curve), and
     * the custom curve's gains in whole dB, where the model has one.
     */
    public val eq: OneMoreEq?
        get() {
            val m = model ?: return null
            val byte = presetSound
            val gains = m.eqBands?.let { b -> eqRaw?.mapIndexed { i, r -> db(b, i, r) } }
            if (byte == null && gains == null) return null
            val custom = byte != null && ONEMORE_PRESET_SOUNDS[byte] == CUSTOM_PRESET || byte == null && m.eqPresets.isEmpty()
            return OneMoreEq(m.eqPresets.firstOrNull { presetByte(it) == byte }, custom, gains)
        }

    /** The byte for preset sound [name]; SOUND BY LUCA, on the HQ60, goes as acoustic's (getStyleCmd). */
    private fun presetByte(name: String): Int? =
        if (name == LUCA) presetByte(ACOUSTIC) else ONEMORE_PRESET_SOUNDS.entries.firstOrNull { it.value == name }?.key

    /** The frames that put preset sound [name] on the earbuds: its byte, as 1MORE's app sends it (DefaultSoundActivity.getStyleCmd). */
    public fun eqPreset(name: String): List<OneMoreFrame>? {
        if (name !in (model?.eqPresets ?: return null)) return null
        val v = presetByte(name) ?: return null
        presetSound = v
        readBacks += frame(OneMoreCommand.GET_PRESET_SOUND)
        return listOf(frame(OneMoreCommand.SET_PRESET_SOUND, v))
    }

    /**
     * The frames that put the custom curve [gains] (whole dB from -3 to 3, one per band) on the earbuds, every band
     * in one write as 1MORE's app resets the curve (CustomSoundActivity.reset): per band 01, the raw gain, the
     * frequency and the Q, two bytes each, high first.
     */
    public fun eqCustom(gains: List<Int>): List<OneMoreFrame>? {
        val b = model?.eqBands ?: return null
        if (gains.size != b.hz.size) return null
        val raws = gains.mapIndexed { i, g -> raw(b, i, g.coerceIn(-EQ_RANGE, EQ_RANGE)) }
        eqRaw = raws
        val payload = mutableListOf<Int>()
        for (i in b.hz.indices) payload += listOf(1, raws[i] shr 8, raws[i] and 0xFF, b.hz[i] shr 8, b.hz[i] and 0xFF, b.q[i] shr 8, b.q[i] and 0xFF)
        eqRead()?.let { readBacks += it }
        readBacks += frame(OneMoreCommand.GET_PRESET_SOUND)
        return listOf(frame(OneMoreCommand.SET_EQ_PARAMETERS, *payload.toIntArray()))
    }

    /** The cancelling and transparency modes the model has, in 1MORE's app's order. */
    private val cancellingModes: List<String> get() = CANCELLING.keys.filter { it in modes }
    private val transparencyModes: List<String> get() = TRANSPARENCY.keys.filter { it in modes }

    /** Off, cancelling and transparency on the shared control, where the model has all three. */
    private val sharedNoise: Boolean get() = "off" in modes && cancellingModes.isNotEmpty() && transparencyModes.isNotEmpty()

    /** Every setting the earbuds have, grouped by heading and tab. */
    public val groups: List<SettingGroup>
        get() {
            val m = model
            return listOfNotNull(
                group("noise", SettingTab.HOME, noiseRows()),
                group("sound", SettingTab.SOUND, listOfNotNull(spatial?.takeIf { m?.spatialAudio == true }?.let { Setting.Toggle("spatial_audio", it) })),
                group(
                    "connection", SettingTab.DEVICE,
                    listOfNotNull(
                        connection?.takeIf { m != null && m.connection.isNotEmpty() }?.let { v ->
                            Setting.Choice("connection_mode", CONNECTION[v], listOf(STANDARD) + CONNECTION.values.filter { it in m!!.connection })
                        },
                    ),
                ),
                group(
                    "other", SettingTab.DEVICE,
                    listOfNotNull(
                        autoplay?.takeIf { m?.autoplay == true }?.let { Setting.Choice("smart_playback", AUTOPLAY[it], AUTOPLAY.values.toList()) },
                        sleepDetection?.takeIf { m?.sleepDetection == true }?.let { Setting.Toggle("sleep_detection", it) },
                        hotWord?.takeIf { m?.hotWord == true }?.let { Setting.Toggle("voice_command", it) },
                        earReturn?.takeIf { m?.earReturn == true }?.let { on ->
                            Setting.Choice("ear_return", LEVELS[if (on) earReturnDepth ?: LEVEL_LOW else LEVEL_OFF], LEVELS.values.toList())
                        },
                        volumeLimit?.takeIf { m?.volumeLimit == true }?.let { Setting.Choice("volume_limit", LEVELS[it], LEVELS.values.toList()) },
                    ),
                ),
            ) + touchGroups()
        }

    private fun noiseRows(): List<Setting> = buildList {
        val mode = listenMode ?: return@buildList
        if (!sharedNoise) {
            // A model without the three: a switch for what it has.
            when {
                cancellingModes.isNotEmpty() -> add(Setting.Toggle("anc", mode in CANCELLING.values))
                transparencyModes.isNotEmpty() -> add(Setting.Toggle("ambient_sound", mode in TRANSPARENCY.values))
            }
        }
        if (mode in CANCELLING.values && cancellingModes.size > 1) {
            add(Setting.Choice("noise_strength", STRENGTH[CANCELLING.entries.firstOrNull { it.value == mode }?.key], cancellingModes.mapNotNull { STRENGTH[it] }))
        }
        if (mode in TRANSPARENCY.values && transparencyModes.size > 1) {
            add(Setting.Choice("transparency_mode", AMBIENT[TRANSPARENCY.entries.firstOrNull { it.value == mode }?.key], transparencyModes.mapNotNull { AMBIENT[it] }))
        }
    }

    /**
     * The touch controls: per earbud, a double and a triple tap each ("each"), or one double and one triple
     * tap for both ("both"), with the actions 1MORE's app offers. Only once the earbuds reported them.
     */
    private fun touchGroups(): List<SettingGroup> {
        val m = model ?: return emptyList()
        return when (m.touch) {
            TOUCH_EACH -> {
                val c = custom ?: return emptyList()
                val options = EACH_ORDER.filter { it != EACH_ASSISTANT || m.touchAssistant }.mapNotNull { EACH_ACTIONS[it] }
                fun row(gesture: Int, name: String) = Setting.Choice(name, EACH_ACTIONS[c.u8(gesture - 1)] ?: OTHER, options)
                listOf(
                    SettingGroup("touch_left", SettingTab.CONTROLS, listOf(row(DOUBLE_LEFT, "touch_double_left"), row(TRIPLE_LEFT, "touch_triple_left"))),
                    SettingGroup("touch_right", SettingTab.CONTROLS, listOf(row(DOUBLE_RIGHT, "touch_double_right"), row(TRIPLE_RIGHT, "touch_triple_right"))),
                )
            }
            TOUCH_BOTH -> listOfNotNull(
                group(
                    "touch", SettingTab.CONTROLS,
                    listOfNotNull(
                        doubleClick?.let { Setting.Choice("touch_double_whole", BOTH_ACTIONS[it] ?: OTHER, BOTH_ACTIONS.values.toList()) },
                        tripleClick?.let { Setting.Choice("touch_triple_whole", BOTH_ACTIONS[it] ?: OTHER, BOTH_ACTIONS.values.toList()) },
                    ),
                ),
            )
            else -> emptyList()
        }
    }

    private fun group(key: String, tab: SettingTab, settings: List<Setting>) = settings.takeIf { it.isNotEmpty() }?.let { SettingGroup(key, tab, it) }

    /** The frames for a shared setting from the screens, or null when the earbuds do not have it. */
    public fun set(command: Command, value: Int): List<OneMoreFrame>? {
        if (command !in state.values) return null
        val out = when (command) {
            Command.ANC_MODE -> {
                if (!sharedNoise) return null
                val mode = when (value) {
                    ancNumber("off") -> MODE_OFF
                    ancNumber("transparency") -> transparency?.takeIf { t -> TRANSPARENCY.entries.any { it.value == t && it.key in modes } }
                        ?: TRANSPARENCY.getValue(transparencyModes.first())
                    ancNumber("anc") -> cancelling?.takeIf { c -> CANCELLING.entries.any { it.value == c && it.key in modes } } ?: firstCancelling()
                    else -> return null
                }
                listenWrite(mode)
            }
            Command.MULTIPOINT -> {
                dualDevice = value == 1
                readBacks += frame(OneMoreCommand.GET_DUAL_DEVICE)
                frame(OneMoreCommand.SET_DUAL_DEVICE, if (value == 1) ON else OFF)
            }
            Command.FIND_LEFT, Command.FIND_RIGHT -> {
                val side = if (command == Command.FIND_LEFT) LEFT else RIGHT
                if (value == 1) ringing += side else ringing -= side
                // Both sides at once, as 1MORE's app writes it: left, then right, 01 ringing.
                frame(OneMoreCommand.FIND_DEVICE, if (LEFT in ringing) 1 else 0, if (RIGHT in ringing) 1 else 0)
            }
            else -> return null
        }
        refresh()
        return listOf(out)
    }

    /** Noise cancelling without a choice made yet: strong, which 1MORE's app sends first, where the model has it. */
    private fun firstCancelling(): Int = CANCELLING.getValue(if ("strong" in cancellingModes) "strong" else cancellingModes.first())

    private fun listenWrite(mode: Int): OneMoreFrame {
        listen(mode)
        readBacks += frame(OneMoreCommand.GET_LISTEN_MODE)
        return frame(OneMoreCommand.SET_LISTEN_MODE, mode)
    }

    /** The frames for a change to one of the [groups], or null when the earbuds do not have the setting. */
    public fun change(change: SettingChange): List<OneMoreFrame>? {
        val setting = groups.flatMap { it.settings }.firstOrNull { it.key == change.key } ?: return null
        val out: List<OneMoreFrame> = when (change) {
            is SettingChange.Toggle -> {
                if (setting !is Setting.Toggle || !setting.enabled) return null
                val on = change.on
                val b = if (on) ON else OFF
                when (change.key) {
                    "anc" -> listOf(listenWrite(if (on) firstCancelling() else MODE_OFF))
                    "ambient_sound" -> listOf(listenWrite(if (on) TRANSPARENCY.getValue(transparencyModes.first()) else MODE_OFF))
                    "spatial_audio" -> {
                        spatial = on
                        readBacks += frame(OneMoreCommand.GET_SPATIAL_AUDIO)
                        listOf(frame(OneMoreCommand.SET_SPATIAL_AUDIO, b))
                    }
                    "sleep_detection" -> {
                        sleepDetection = on
                        readBacks += frame(OneMoreCommand.GET_SLEEP_DETECTION)
                        listOf(frame(OneMoreCommand.SET_SLEEP_DETECTION, b))
                    }
                    "voice_command" -> {
                        hotWord = on
                        readBacks += frame(OneMoreCommand.GET_HOT_WORD)
                        listOf(frame(OneMoreCommand.SET_HOT_WORD, b))
                    }
                    else -> return null
                }
            }
            is SettingChange.Choice -> {
                if (setting !is Setting.Choice || change.option !in setting.options) return null
                choice(change.key, change.option) ?: return null
            }
            else -> return null
        }
        refresh()
        return out
    }

    private fun choice(key: String, option: String): List<OneMoreFrame>? = when (key) {
        "noise_strength" -> {
            val name = STRENGTH.entries.firstOrNull { it.value == option }?.key ?: return null
            listOf(listenWrite(CANCELLING.getValue(name)))
        }
        "transparency_mode" -> {
            val name = AMBIENT.entries.firstOrNull { it.value == option }?.key ?: return null
            listOf(listenWrite(TRANSPARENCY.getValue(name)))
        }
        "connection_mode" -> {
            val v = CONNECTION.entries.firstOrNull { it.value == option }?.key ?: return null
            connection = v
            readBacks += frame(OneMoreCommand.GET_CONNECTION_MODE)
            listOf(frame(OneMoreCommand.SET_CONNECTION_MODE, v))
        }
        "smart_playback" -> {
            val v = AUTOPLAY.entries.firstOrNull { it.value == option }?.key ?: return null
            autoplay = v
            readBacks += frame(OneMoreCommand.GET_AUTOPLAY)
            listOf(frame(OneMoreCommand.SET_AUTOPLAY, v))
        }
        "ear_return" -> {
            val v = LEVELS.entries.firstOrNull { it.value == option }?.key ?: return null
            readBacks += frame(OneMoreCommand.GET_EAR_RETURN)
            readBacks += frame(OneMoreCommand.GET_EAR_RETURN_DEPTH)
            // Off is the switch alone; a depth turns the switch on first where it was off, as 1MORE's app does.
            val wasOn = earReturn == true
            earReturn = v != LEVEL_OFF
            if (v == LEVEL_OFF) {
                listOf(frame(OneMoreCommand.SET_EAR_RETURN, OFF))
            } else {
                earReturnDepth = v
                listOfNotNull(frame(OneMoreCommand.SET_EAR_RETURN, ON).takeUnless { wasOn }, frame(OneMoreCommand.SET_EAR_RETURN_DEPTH, v))
            }
        }
        "volume_limit" -> {
            val v = LEVELS.entries.firstOrNull { it.value == option }?.key ?: return null
            volumeLimit = v
            readBacks += frame(OneMoreCommand.GET_MAX_SOUND_LIMIT)
            listOf(frame(OneMoreCommand.SET_MAX_SOUND_LIMIT, v))
        }
        "touch_double_whole", "touch_triple_whole" -> {
            val v = BOTH_ACTIONS.entries.firstOrNull { it.value == option }?.key ?: return null
            if (key == "touch_double_whole") {
                doubleClick = v
                readBacks += frame(OneMoreCommand.GET_DOUBLE_CLICK)
                listOf(frame(OneMoreCommand.SET_DOUBLE_CLICK, v))
            } else {
                tripleClick = v
                readBacks += frame(OneMoreCommand.GET_TRIPLE_CLICK)
                listOf(frame(OneMoreCommand.SET_TRIPLE_CLICK, v))
            }
        }
        else -> {
            // A gesture on one earbud: its number (1 double left, 2 double right, 3 triple left, 4 triple right), then the action.
            val gesture = EACH_KEYS[key] ?: return null
            val v = EACH_ACTIONS.entries.firstOrNull { it.value == option }?.key ?: return null
            custom = custom?.copyOf()?.also { it[gesture - 1] = v.toByte() }
            readBacks += frame(OneMoreCommand.GET_ALL_CUSTOM)
            listOf(frame(OneMoreCommand.SET_CUSTOM, gesture, v))
        }
    }

    private fun key(command: Int): String = command.toString(16).uppercase().padStart(4, '0')

    private fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF

    public companion object {
        private const val BRAND = "1MORE"
        private const val RESULT_SUCCESS = 1
        private const val ON = 1
        private const val OFF = 0
        private const val PERCENT = 100
        private const val BINAURAL_SIZE = 10

        // The custom EQ (section 4): 7 bytes a band, whole dB from -3 to 3, 15 dB as zero on the whole-dB models,
        // and the read's length field as 1MORE's app writes it.
        private const val EQ_RECORD = 7
        public const val EQ_RANGE: Int = 3
        private const val WHOLE_DB_ZERO = 15
        private const val EQ_READ_LENGTH = 20
        private const val CUSTOM_PRESET = "custom"
        private const val LUCA = "luca"
        private const val ACOUSTIC = "acoustic"
        private const val CUSTOM_GESTURES = 4
        private const val LEFT = "left"
        private const val RIGHT = "right"

        /** A model PairFree does not know reads only the battery and noise mode, as if it had the three noise modes. */
        private val DEFAULT_MODES = listOf("off", "strong", "transparent")

        // The listen modes (Const.kt): 0 off; cancelling 7 adaptive, 1 strong, 2 mild, 4 wind; transparency 3, 5 pass-through, 6 voice enhancement.
        private const val MODE_OFF = 0
        private val CANCELLING = linkedMapOf("adaptive" to 7, "strong" to 1, "mild" to 2, "wind" to 4)
        private val TRANSPARENCY = linkedMapOf("transparent" to 3, "pass_through" to 5, "voice_enhancement" to 6)

        /** The screens' words for them: the cancelling strengths (noise_strength), the transparency kinds (transparency_mode). */
        private val STRENGTH = mapOf("adaptive" to "adaptive", "strong" to "high", "mild" to "low", "wind" to "wind")
        private val AMBIENT = mapOf("transparent" to "normal", "pass_through" to "pass_through", "voice_enhancement" to "voice")

        private const val STANDARD = "standard"
        private val CONNECTION = linkedMapOf(0 to STANDARD, 1 to "game", 2 to "ldac")
        private val AUTOPLAY = linkedMapOf(1 to "pause_play", 0 to "pause", 2 to "off")

        private const val LEVEL_OFF = 0
        private const val LEVEL_LOW = 1
        private val LEVELS = linkedMapOf(LEVEL_OFF to "off", LEVEL_LOW to "low", 2 to "medium", 3 to "high")

        private const val TOUCH_EACH = "each"
        private const val TOUCH_BOTH = "both"
        private const val OTHER = "other"

        // SET_CUSTOM's gestures.
        private const val DOUBLE_LEFT = 1
        private const val DOUBLE_RIGHT = 2
        private const val TRIPLE_LEFT = 3
        private const val TRIPLE_RIGHT = 4
        private val EACH_KEYS = mapOf("touch_double_left" to DOUBLE_LEFT, "touch_double_right" to DOUBLE_RIGHT, "touch_triple_left" to TRIPLE_LEFT, "touch_triple_right" to TRIPLE_RIGHT)

        /** One earbud's actions (clickMonaural) and the order 1MORE's app lists them in; some models lack the assistant (6). */
        private val EACH_ACTIONS = mapOf(0 to "none", 1 to "next", 2 to "previous", 3 to "volume_down", 4 to "volume_up", 5 to "play_pause", 6 to "voice_assistant")
        private val EACH_ORDER = listOf(2, 1, 3, 4, 5, 6, 0)
        private const val EACH_ASSISTANT = 6

        /** Both earbuds' actions (clickBinaural). */
        private val BOTH_ACTIONS = linkedMapOf(1 to "play_pause", 2 to "previous_next", 3 to "volume", 4 to "voice_assistant", 0 to "none")

        /** Each write, and the read that shows its value again. */
        private val READ_OF: Map<Int, Int> = mapOf(
            OneMoreCommand.SET_LISTEN_MODE to OneMoreCommand.GET_LISTEN_MODE,
            OneMoreCommand.SET_CONNECTION_MODE to OneMoreCommand.GET_CONNECTION_MODE,
            OneMoreCommand.SET_DUAL_DEVICE to OneMoreCommand.GET_DUAL_DEVICE,
            OneMoreCommand.SET_SPATIAL_AUDIO to OneMoreCommand.GET_SPATIAL_AUDIO,
            OneMoreCommand.SET_AUTOPLAY to OneMoreCommand.GET_AUTOPLAY,
            OneMoreCommand.SET_SLEEP_DETECTION to OneMoreCommand.GET_SLEEP_DETECTION,
            OneMoreCommand.SET_HOT_WORD to OneMoreCommand.GET_HOT_WORD,
            OneMoreCommand.SET_DOUBLE_CLICK to OneMoreCommand.GET_DOUBLE_CLICK,
            OneMoreCommand.SET_TRIPLE_CLICK to OneMoreCommand.GET_TRIPLE_CLICK,
            OneMoreCommand.SET_CUSTOM to OneMoreCommand.GET_ALL_CUSTOM,
            OneMoreCommand.SET_EAR_RETURN to OneMoreCommand.GET_EAR_RETURN,
            OneMoreCommand.SET_EAR_RETURN_DEPTH to OneMoreCommand.GET_EAR_RETURN_DEPTH,
            OneMoreCommand.SET_MAX_SOUND_LIMIT to OneMoreCommand.GET_MAX_SOUND_LIMIT,
            OneMoreCommand.SET_PRESET_SOUND to OneMoreCommand.GET_PRESET_SOUND,
            OneMoreCommand.SET_EQ_PARAMETERS to OneMoreCommand.GET_EQ_PARAMETERS,
        )

        /** The shared noise mode's number for [name] (data/soundpeats/commands.json). */
        private fun ancNumber(name: String): Int = Command.ANC_MODE.labels.entries.first { it.value == name }.key
    }
}

/**
 * 1MORE earbuds' EQ as they last reported it: the [preset] sound by the model's names, [custom] while they hold the
 * custom curve, and [gains] that curve in whole dB, where they reported one.
 */
public data class OneMoreEq(val preset: String?, val custom: Boolean, val gains: List<Int>?)
