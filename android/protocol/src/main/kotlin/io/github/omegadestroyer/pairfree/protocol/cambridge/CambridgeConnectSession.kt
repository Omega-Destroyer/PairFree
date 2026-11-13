package io.github.omegadestroyer.pairfree.protocol.cambridge

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value

/**
 * Melomania Connect 2.6.0's custom GAIA v3 API for the A100, M100, P100 and X100 families (docs/cambridge/PROTOCOL.md section 9). A row shows once its
 * read answered, as the app does; the reads that came after API level 1.9, 1.14 and 1.17 wait for that level. Not sent: the Bluetooth addresses (never
 * read), the device list's connect and disconnect (its reply layout is not clear from the app) and flight mode (no screen of the app uses it). Which rows a
 * model gets follows the app's own per-model table ([ConnectConfig]). The EQ is Qualcomm's music processing feature (vendor `001D`, feature 5): the earbuds hold
 * one user set of bands, which the app fills with a preset's gains or the user's.
 */
public class CambridgeConnectSession(public val model: CambridgeModel) {
    public var state: EarbudState = EarbudState()
        private set
    public var firmware: String? = null
        private set
    public var replies: Map<String, ByteArray> = emptyMap()
        private set
    private val parser = ConnectParser()
    private var api: Pair<Int, Int>? = null
    private var readBacks = emptyList<ByteArray>()
    private val sent = mutableSetOf<Int>()
    private var noise: Int? = null
    private var toggle: List<Boolean>? = null
    private var language: Int? = null
    private var autoOff: Int? = null
    private val flags = mutableMapOf<String, Boolean>()
    private var spatialMode = CambridgeConnectVirtualizer.MODE_1
    private var supportedGestures = 0
    private var gestures: Int? = null
    private var enabledCodecs: List<Int>? = null
    private var availableCodecs: List<Int>? = null

    private val config: ConnectConfig = model.config ?: ConnectConfig(false, false, false, false, false, false, false, false, false, false, -9.0, 3.0, false, emptyList())
    private var bandCount = 0
    private var bands: List<Pair<Int, Double>>? = null
    private var userSet = false
    private var balance: Int? = null
    private val caseGestures = mutableMapOf<String, Boolean>()

    private fun read(feature: Int, command: Int, vararg payload: Int, qtil: Boolean = false): ByteArray {
        val packet = ConnectPacket(feature, CambridgeConnectWire.TYPE_COMMAND, command, payload.map(Int::toByte).toByteArray(),
            if (qtil) CambridgeConnectWire.QTIL_VENDOR else CambridgeConnectWire.VENDOR)
        sent += packet.key
        return packet.bytes()
    }

    public fun start(): List<ByteArray> = buildList {
        add(read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_API_LEVEL))
        add(read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_BATTERY_STATE))
        add(read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_FIRMWARE_VERSIONS))
        add(read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_AMBIENT_NOISE_MODE))
        add(read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_LANGUAGE))
        add(read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_AUTO_POWER_DOWN_TIMEOUT))
        add(read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_FIND_HEADPHONES_ALERT))
        add(read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_LEFT_RIGHT_BALANCE))
        add(read(CambridgeConnectWire.FEATURE_MODES, CambridgeConnectModes.GET_GAMING_MODE))
        add(read(CambridgeConnectWire.FEATURE_MODES, CambridgeConnectModes.GET_PROXIMITY_MODE))
        if (config.mono) add(read(CambridgeConnectWire.FEATURE_MODES, CambridgeConnectModes.GET_MONO_MODE))
        if (config.sleep) add(read(CambridgeConnectWire.FEATURE_MODES, CambridgeConnectModes.GET_SLEEP_MODE))
        if (config.codec) {
            add(read(CambridgeConnectWire.FEATURE_CODEC, CambridgeConnectCodecCmd.GET_ENABLED_CODECS))
            add(read(CambridgeConnectWire.FEATURE_CODEC, CambridgeConnectCodecCmd.GET_AVAILABLE_CODECS))
        }
        if (config.touch) {
            add(read(CambridgeConnectWire.FEATURE_GESTURES, CambridgeConnectGestureCmd.GET_SUPPORTED_GESTURE_CONTROLS))
            add(read(CambridgeConnectWire.FEATURE_GESTURES, CambridgeConnectGestureCmd.GET_GESTURE_CONTROLS_ON_OFF))
        }
        if (config.eqPresets.isNotEmpty()) {
            add(read(CambridgeConnectWire.FEATURE_MUSIC, CambridgeConnectMusic.GET_BAND_COUNT, qtil = true))
            add(read(CambridgeConnectWire.FEATURE_MUSIC, CambridgeConnectMusic.GET_SET, qtil = true))
        }
    }

    public fun batteryRequests(): List<ByteArray> = listOf(read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_BATTERY_STATE))

    /** The reads that confirm the last change, and the ones the API level just allowed. */
    public fun takeReadBacks(): List<ByteArray> = readBacks.also { readBacks = emptyList() }

    private fun atLeast(minor: Int): Boolean = api?.let { (major, m) -> major > 1 || (major == 1 && m >= minor) } == true

    /** The keys (feature * 128 + command) of what answered or failed, so the queue goes on. */
    public fun feed(bytes: ByteArray): List<Int> {
        val answered = mutableListOf<Int>()
        for (packet in parser.feed(bytes)) {
            val key = packet.key
            if (packet.isError) { answered += key; continue }
            val p = packet.payload.map { it.toInt() and 0xff }
            if (packet.vendor == CambridgeConnectWire.QTIL_VENDOR) {
                if (packet.feature == CambridgeConnectWire.FEATURE_MUSIC) {
                    if (packet.type == CambridgeConnectWire.TYPE_NOTIFICATION) musicChanged(packet.command)
                    else if (packet.isResponse && key in sent) { answered += key; music(packet.command, p) }
                }
                continue
            }
            if (!packet.isResponse || key !in sent) continue
            answered += key
            fun on(): Boolean? = p.getOrNull(0)?.let { it != 0 }
            when (packet.feature) {
                CambridgeConnectWire.FEATURE_MISC -> when (packet.command) {
                    CambridgeConnectMisc.GET_API_LEVEL -> if (p.size >= 2) { api = p[0] to p[1]; readBacks = readBacks + laterReads() }
                    CambridgeConnectMisc.GET_BATTERY_STATE -> battery(p)
                    CambridgeConnectMisc.GET_FIRMWARE_VERSIONS -> if (p.size >= 6) {
                        firmware = "L ${p[0]}.${p[1]}.${p[2]} / R ${p[3]}.${p[4]}.${p[5]}"
                        state = state.copy(values = state.values + (Command.FIRMWARE_VERSION to Value.Text("${model.name}_$firmware")))
                    }
                    CambridgeConnectMisc.GET_AMBIENT_NOISE_MODE, CambridgeConnectMisc.SET_AMBIENT_NOISE_MODE -> noise = p.getOrNull(0)
                    CambridgeConnectMisc.GET_AMBIENT_NOISE_TOGGLE_OPTIONS, CambridgeConnectMisc.SET_AMBIENT_NOISE_TOGGLE_OPTIONS ->
                        if (p.size >= 3) toggle = p.take(3).map { it != 0 }
                    CambridgeConnectMisc.GET_LANGUAGE, CambridgeConnectMisc.SET_LANGUAGE -> language = p.getOrNull(0)
                    CambridgeConnectMisc.GET_AUTO_POWER_DOWN_TIMEOUT, CambridgeConnectMisc.SET_AUTO_POWER_DOWN_TIMEOUT ->
                        if (p.size >= 3) autoOff = p[0] * 3600 + p[1] * 60 + p[2]
                    CambridgeConnectMisc.GET_FIND_HEADPHONES_ALERT, CambridgeConnectMisc.SET_FIND_HEADPHONES_ALERT -> on()?.let { flags["ca_find_alert"] = it }
                    CambridgeConnectMisc.GET_LEFT_RIGHT_BALANCE, CambridgeConnectMisc.SET_LEFT_RIGHT_BALANCE -> if (p.size >= 2 && p[1] in 0..100) {
                        balance = if (p[0] != 0) -p[1] else p[1]
                    }
                    CambridgeConnectMisc.GET_LOUDNESS, CambridgeConnectMisc.SET_LOUDNESS -> on()?.let { flags["ca_loudness"] = it }
                    CambridgeConnectMisc.GET_SIDE_TONE, CambridgeConnectMisc.SET_SIDE_TONE -> on()?.let { flags["ca_side_tone"] = it }
                    CambridgeConnectMisc.GET_SPATIAL_AUDIO, CambridgeConnectMisc.SET_SPATIAL_AUDIO -> if (p.size >= 2) {
                        flags["ca_spatial"] = p[0] != 0
                        if (p[1] in CambridgeConnectVirtualizer.MODE_1..CambridgeConnectVirtualizer.MODE_2) spatialMode = p[1]
                    }
                }
                CambridgeConnectWire.FEATURE_MODES -> on()?.let { v ->
                    when (packet.command) {
                        CambridgeConnectModes.GET_GAMING_MODE, CambridgeConnectModes.SET_GAMING_MODE -> flags["ca_gaming"] = v
                        CambridgeConnectModes.GET_PROXIMITY_MODE, CambridgeConnectModes.SET_PROXIMITY_MODE -> flags["ca_proximity"] = v
                        CambridgeConnectModes.GET_MONO_MODE, CambridgeConnectModes.SET_MONO_MODE -> flags["ca_mono"] = v
                        CambridgeConnectModes.GET_SLEEP_MODE, CambridgeConnectModes.SET_SLEEP_MODE -> flags["ca_sleep"] = v
                    }
                }
                CambridgeConnectWire.FEATURE_CODEC -> when (packet.command) {
                    CambridgeConnectCodecCmd.GET_ENABLED_CODECS, CambridgeConnectCodecCmd.SET_ENABLED_CODECS -> enabledCodecs = p
                    CambridgeConnectCodecCmd.GET_AVAILABLE_CODECS -> availableCodecs = p
                }
                CambridgeConnectWire.FEATURE_GESTURES -> if (p.size >= 2) {
                    val mask = p[0] or (p[1] shl 8)
                    if (packet.command == CambridgeConnectGestureCmd.GET_SUPPORTED_GESTURE_CONTROLS) supportedGestures = mask else gestures = mask
                }
            }
            replies = replies + ("${packet.feature}.${packet.command}" to packet.payload.copyOf(minOf(packet.payload.size, 16)))
        }
        return answered
    }

    /** One reply of the music processing feature: the number of bands, the selected set, or the bands' own configuration. */
    private fun music(command: Int, p: List<Int>) {
        when (command) {
            CambridgeConnectMusic.GET_BAND_COUNT -> if (p.isNotEmpty() && p[0] in 1..MAX_BANDS) {
                bandCount = p[0]
                readBacks = readBacks + read(CambridgeConnectWire.FEATURE_MUSIC, CambridgeConnectMusic.GET_BAND_CONFIG, 0, bandCount - 1, qtil = true)
            }
            CambridgeConnectMusic.GET_SET, CambridgeConnectMusic.SET_SET -> p.getOrNull(0)?.let { userSet = it == CambridgeConnectMusic.USER_SET }
            CambridgeConnectMusic.GET_BAND_CONFIG -> if (p.size >= 2) {
                val first = p[0]
                val count = p[1] - first + 1
                if (first == 0 && count == bandCount && p.size >= 2 + count * CambridgeConnectMusic.BAND_LENGTH) {
                    bands = List(count) { i ->
                        val at = 2 + i * CambridgeConnectMusic.BAND_LENGTH
                        val gain = ((p[at + 5] shl 8) or p[at + 6]).toShort().toInt()
                        ((p[at] shl 8) or p[at + 1]) to gain.toDouble() / CambridgeConnectMusic.GAIN_FACTOR
                    }
                }
            }
        }
    }

    /** The earbuds changed their set or a band on their own: ask again. */
    private fun musicChanged(command: Int) {
        if (command == CambridgeConnectMusic.NOTIFY_SET_CHANGE) readBacks = readBacks + read(CambridgeConnectWire.FEATURE_MUSIC, CambridgeConnectMusic.GET_SET, qtil = true)
        if (command == CambridgeConnectMusic.NOTIFY_BAND_CHANGE && bandCount > 0)
            readBacks = readBacks + read(CambridgeConnectWire.FEATURE_MUSIC, CambridgeConnectMusic.GET_BAND_CONFIG, 0, bandCount - 1, qtil = true)
    }

    /** The bands the earbuds report (frequency in Hz and gain in dB), or null until they have. Null too for a model whose EQ the app does not let the user edit. */
    public val eqBands: List<Pair<Int, Double>>? get() = bands.takeIf { config.eqCustom }

    /** The presets the app bundles for the model, as gains in dB band by band, for the bands the earbuds report. */
    public val eqPresets: List<Pair<String, List<Double>>> get() = config.eqPresets.filter { bandCount == 0 || it.second.size == bandCount }
    public val eqMinDb: Double get() = config.eqMinDb
    public val eqMaxDb: Double get() = config.eqMaxDb
    public val eqKnown: Boolean get() = bands != null

    /** The frames that put [gains] (dB, one per band) on the earbuds' user set, as the app does: select the set, then write the gains. Null when the bands are not known. */
    public fun eqCurve(gains: List<Double>): List<ByteArray>? {
        if (bandCount == 0 || gains.size != bandCount) return null
        val clamped = gains.map { it.coerceIn(config.eqMinDb, config.eqMaxDb) }
        val payload = intArrayOf(0, bandCount - 1) + clamped.flatMap { g ->
            val raw = Math.round(g * CambridgeConnectMusic.GAIN_FACTOR).toInt()
            listOf((raw shr 8) and 0xff, raw and 0xff)
        }
        bands = bands?.mapIndexed { i, b -> b.first to clamped[i] }
        readBacks = listOf(read(CambridgeConnectWire.FEATURE_MUSIC, CambridgeConnectMusic.GET_BAND_CONFIG, 0, bandCount - 1, qtil = true))
        return listOf(
            ConnectPacket(CambridgeConnectWire.FEATURE_MUSIC, CambridgeConnectWire.TYPE_COMMAND, CambridgeConnectMusic.SET_SET, byteArrayOf(CambridgeConnectMusic.USER_SET.toByte()), CambridgeConnectWire.QTIL_VENDOR).bytes(),
            ConnectPacket(CambridgeConnectWire.FEATURE_MUSIC, CambridgeConnectWire.TYPE_COMMAND, CambridgeConnectMusic.SET_GAINS, payload.map(Int::toByte).toByteArray(), CambridgeConnectWire.QTIL_VENDOR).bytes(),
        )
    }

    private fun laterReads(): List<ByteArray> = buildList {
        if (atLeast(CambridgeConnectApi.TOGGLE_OPTIONS_MINOR)) add(read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_AMBIENT_NOISE_TOGGLE_OPTIONS))
        if (config.loudness && atLeast(CambridgeConnectApi.LOUDNESS_MINOR)) add(read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_LOUDNESS))
        if (config.sideTone && atLeast(CambridgeConnectApi.SIDE_TONE_MINOR)) add(read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_SIDE_TONE))
        if (config.spatial) add(read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_SPATIAL_AUDIO))
    }

    private fun battery(p: List<Int>) {
        if (p.size < 9) return
        val values = state.values.toMutableMap()
        for ((c, i) in listOf(Command.BATTERY_LEFT to 4, Command.BATTERY_RIGHT to 5)) {
            if (p[i] in 0..100) values[c] = Value.Number(p[i]) else values.remove(c)
        }
        state = state.copy(values = values)
    }

    public val groups: List<SettingGroup> get() = buildList {
        val home = buildList<Setting> {
            noise?.let { n -> add(Setting.Choice("ca_noise", NOISE.entries.firstOrNull { it.value == n }?.key, NOISE.keys.toList())) }
            toggle?.let { t ->
                add(Setting.Toggle("ca_toggle_normal", t[0]))
                add(Setting.Toggle("ca_toggle_anc", t[1]))
                add(Setting.Toggle("ca_toggle_transparency", t[2]))
            }
        }
        if (home.isNotEmpty()) add(SettingGroup("noise", SettingTab.HOME, home))
        val sound = buildList<Setting> {
            FLAG_ORDER.filter { it in flags && it in SOUND_FLAGS }.forEach { add(Setting.Toggle(it, flags.getValue(it))) }
            balance?.let { add(Setting.Level("ca_balance", it, -100, 100)) }
        }
        if (sound.isNotEmpty()) add(SettingGroup("ca_sound", SettingTab.SOUND, sound))
        val buttons = gestures?.let { mask ->
            GESTURES.filter { supportedGestures and (1 shl it.value) != 0 }.map { Setting.Toggle("ca_gesture_${it.key}", mask and (1 shl it.value) != 0) }
        }.orEmpty()
        if (buttons.isNotEmpty()) add(SettingGroup("touch_buttons", SettingTab.CONTROLS, buttons))
        if (gestures != null && config.touch) add(SettingGroup("ca_case", SettingTab.CONTROLS, CASE_GESTURES.map { g ->
            Setting.Choice("ca_case_${g.key}", caseGestures["ca_case_${g.key}"]?.let { if (it) "on" else "off" }, listOf("on", "off"))
        }))
        val device = buildList<Setting> {
            FLAG_ORDER.filter { it in flags && it !in SOUND_FLAGS }.forEach { add(Setting.Toggle(it, flags.getValue(it))) }
            language?.let { l -> LANGUAGES.entries.firstOrNull { it.value == l }?.let { add(Setting.Choice("ca_language", it.key, LANGUAGES.keys.toList())) } }
            autoOff?.let { s -> add(Setting.Choice("ca_auto_off", AUTO_OFF.entries.firstOrNull { it.value == s }?.key, AUTO_OFF.keys.toList())) }
            val enabled = enabledCodecs
            if (enabled != null) availableCodecs.orEmpty().filter { it in CODEC_NAMES }.forEach { add(Setting.Toggle("ca_codec_${CODEC_NAMES.getValue(it)}", it in enabled)) }
        }
        if (device.isNotEmpty()) add(SettingGroup("ca_connection", SettingTab.DEVICE, device))
    }

    /** The messages for one of the rows above, or null when the row is not offered. */
    public fun change(change: SettingChange): List<ByteArray>? {
        val row = groups.flatMap { it.settings }.firstOrNull { it.key == change.key } ?: return null
        val key = change.key
        val out: ByteArray
        val back: ByteArray
        fun frame(feature: Int, command: Int, vararg payload: Int) =
            ConnectPacket(feature, CambridgeConnectWire.TYPE_COMMAND, command, payload.map(Int::toByte).toByteArray()).bytes()
        val misc = CambridgeConnectWire.FEATURE_MISC
        when {
            row is Setting.Choice && change is SettingChange.Choice && change.option in row.options -> when (key) {
                "ca_noise" -> {
                    out = frame(misc, CambridgeConnectMisc.SET_AMBIENT_NOISE_MODE, NOISE.getValue(change.option))
                    back = read(misc, CambridgeConnectMisc.GET_AMBIENT_NOISE_MODE)
                }
                "ca_language" -> {
                    out = frame(misc, CambridgeConnectMisc.SET_LANGUAGE, LANGUAGES.getValue(change.option))
                    back = read(misc, CambridgeConnectMisc.GET_LANGUAGE)
                }
                "ca_auto_off" -> {
                    val s = AUTO_OFF.getValue(change.option)
                    out = frame(misc, CambridgeConnectMisc.SET_AUTO_POWER_DOWN_TIMEOUT, s / 3600, s / 60 % 60, s % 60)
                    back = read(misc, CambridgeConnectMisc.GET_AUTO_POWER_DOWN_TIMEOUT)
                }
                else -> {
                    val g = CASE_GESTURES.firstOrNull { "ca_case_${it.key}" == key } ?: return null
                    val on = change.option == "on"
                    caseGestures[key] = on
                    out = frame(CambridgeConnectWire.FEATURE_GESTURES, CambridgeConnectGestureCmd.SET_GESTURE_CONTROL_ON_OFF, g.value, if (on) 1 else 0)
                    back = read(CambridgeConnectWire.FEATURE_GESTURES, CambridgeConnectGestureCmd.GET_GESTURE_CONTROLS_ON_OFF)
                }
            }
            row is Setting.Level && change is SettingChange.Level && key == "ca_balance" && change.value in -100..100 -> {
                balance = change.value
                out = frame(misc, CambridgeConnectMisc.SET_LEFT_RIGHT_BALANCE, if (change.value < 0) 1 else 0, kotlin.math.abs(change.value))
                back = read(misc, CambridgeConnectMisc.GET_LEFT_RIGHT_BALANCE)
            }
            row is Setting.Toggle && change is SettingChange.Toggle -> {
                val v = if (change.on) 1 else 0
                when {
                    key.startsWith("ca_toggle_") -> {
                        val t = (toggle ?: return null).toMutableList()
                        t[listOf("normal", "anc", "transparency").indexOf(key.removePrefix("ca_toggle_"))] = change.on
                        if (t.none { it }) return null // the app needs at least one mode in the cycle
                        out = frame(misc, CambridgeConnectMisc.SET_AMBIENT_NOISE_TOGGLE_OPTIONS, *t.map { if (it) 1 else 0 }.toIntArray())
                        back = read(misc, CambridgeConnectMisc.GET_AMBIENT_NOISE_TOGGLE_OPTIONS)
                    }
                    key.startsWith("ca_gesture_") -> {
                        val g = GESTURES.firstOrNull { "ca_gesture_${it.key}" == key } ?: return null
                        out = frame(CambridgeConnectWire.FEATURE_GESTURES, CambridgeConnectGestureCmd.SET_GESTURE_CONTROL_ON_OFF, g.value, v)
                        back = read(CambridgeConnectWire.FEATURE_GESTURES, CambridgeConnectGestureCmd.GET_GESTURE_CONTROLS_ON_OFF)
                    }
                    key.startsWith("ca_codec_") -> {
                        val id = CODEC_NAMES.entries.firstOrNull { "ca_codec_${it.value}" == key }?.key ?: return null
                        val next = enabledCodecs.orEmpty().filter { it != id } + if (change.on) listOf(id) else emptyList()
                        if (next.isEmpty()) return null // the earbuds keep at least one codec
                        out = frame(CambridgeConnectWire.FEATURE_CODEC, CambridgeConnectCodecCmd.SET_ENABLED_CODECS, *next.toIntArray())
                        back = read(CambridgeConnectWire.FEATURE_CODEC, CambridgeConnectCodecCmd.GET_ENABLED_CODECS)
                    }
                    key == "ca_spatial" -> {
                        out = frame(misc, CambridgeConnectMisc.SET_SPATIAL_AUDIO, v, spatialMode)
                        back = read(misc, CambridgeConnectMisc.GET_SPATIAL_AUDIO)
                    }
                    else -> {
                        val (feature, set, get) = SIMPLE[key] ?: return null
                        out = frame(feature, set, v)
                        back = read(feature, get)
                    }
                }
            }
            else -> return null
        }
        readBacks = listOf(back)
        return listOf(out)
    }

    /** Only frames this session builds: the reads above and the writes of its rows. Gestures are switched on or off, never triggered: no power, reset, pairing or firmware command exists here. */
    public fun allowed(bytes: ByteArray): Boolean {
        val p = ConnectParser().feed(bytes).singleOrNull() ?: return false
        if (!p.bytes().contentEquals(bytes) || p.type != CambridgeConnectWire.TYPE_COMMAND) return false
        if (p.vendor == CambridgeConnectWire.QTIL_VENDOR) return allowedQtil(p)
        val n = p.payload.map { it.toInt() and 0xff }
        val c = p.command
        return when (p.feature) {
            CambridgeConnectWire.FEATURE_MISC -> when (c) {
                in READS_MISC -> n.isEmpty()
                CambridgeConnectMisc.SET_AMBIENT_NOISE_MODE -> n.size == 1 && n[0] in NOISE.values
                CambridgeConnectMisc.SET_LANGUAGE -> n.size == 1 && n[0] in LANGUAGES.values
                CambridgeConnectMisc.SET_AUTO_POWER_DOWN_TIMEOUT -> n.size == 3 && (n[0] * 3600 + n[1] * 60 + n[2]) in AUTO_OFF.values
                CambridgeConnectMisc.SET_AMBIENT_NOISE_TOGGLE_OPTIONS -> n.size == 3 && n.all { it <= 1 } && n.any { it == 1 }
                CambridgeConnectMisc.GET_LEFT_RIGHT_BALANCE -> n.isEmpty()
                CambridgeConnectMisc.SET_LEFT_RIGHT_BALANCE -> n.size == 2 && n[0] <= 1 && n[1] <= 100
                CambridgeConnectMisc.SET_FIND_HEADPHONES_ALERT, CambridgeConnectMisc.SET_LOUDNESS, CambridgeConnectMisc.SET_SIDE_TONE -> n.size == 1 && n[0] <= 1
                CambridgeConnectMisc.SET_SPATIAL_AUDIO -> n.size == 2 && n[0] <= 1 && n[1] in CambridgeConnectVirtualizer.MODE_1..CambridgeConnectVirtualizer.MODE_2
                else -> false
            }
            CambridgeConnectWire.FEATURE_MODES -> when (c) {
                in READS_MODES -> n.isEmpty()
                CambridgeConnectModes.SET_GAMING_MODE, CambridgeConnectModes.SET_PROXIMITY_MODE, CambridgeConnectModes.SET_MONO_MODE,
                CambridgeConnectModes.SET_SLEEP_MODE -> n.size == 1 && n[0] <= 1
                else -> false
            }
            CambridgeConnectWire.FEATURE_CODEC -> when (c) {
                CambridgeConnectCodecCmd.GET_ENABLED_CODECS, CambridgeConnectCodecCmd.GET_AVAILABLE_CODECS -> n.isEmpty()
                CambridgeConnectCodecCmd.SET_ENABLED_CODECS -> n.isNotEmpty() && n.all { it in CODEC_NAMES } && availableCodecs?.containsAll(n) == true
                else -> false
            }
            CambridgeConnectWire.FEATURE_GESTURES -> when (c) {
                CambridgeConnectGestureCmd.GET_SUPPORTED_GESTURE_CONTROLS, CambridgeConnectGestureCmd.GET_GESTURE_CONTROLS_ON_OFF -> n.isEmpty()
                CambridgeConnectGestureCmd.SET_GESTURE_CONTROL_ON_OFF ->
                    n.size == 2 && n[1] <= 1 && (CASE_GESTURES.any { it.value == n[0] } ||
                        (GESTURES.any { it.value == n[0] } && supportedGestures and (1 shl n[0]) != 0))
                else -> false
            }
            else -> false
        }
    }

    /** The music processing frames [eqCurve] and the reads build, from Qualcomm's vendor, and no other of its features. */
    private fun allowedQtil(p: ConnectPacket): Boolean {
        if (p.feature != CambridgeConnectWire.FEATURE_MUSIC) return false
        val n = p.payload.map { it.toInt() and 0xff }
        return when (p.command) {
            CambridgeConnectMusic.GET_BAND_COUNT, CambridgeConnectMusic.GET_SET -> n.isEmpty()
            CambridgeConnectMusic.GET_BAND_CONFIG -> bandCount > 0 && n == listOf(0, bandCount - 1)
            CambridgeConnectMusic.SET_SET -> n == listOf(CambridgeConnectMusic.USER_SET)
            CambridgeConnectMusic.SET_GAINS -> bandCount > 0 && n.size == 2 + 2 * bandCount && n[0] == 0 && n[1] == bandCount - 1 &&
                (0 until bandCount).all { i ->
                    val db = ((n[2 + 2 * i] shl 8) or n[3 + 2 * i]).toShort().toInt().toDouble() / CambridgeConnectMusic.GAIN_FACTOR
                    db in config.eqMinDb..config.eqMaxDb
                }
            else -> false
        }
    }

    private class Gesture(val key: String, val value: Int)

    private companion object {
        val NOISE = linkedMapOf(
            "off" to CambridgeConnectAmbientMode.OFF, "anc_high" to CambridgeConnectAmbientMode.ANC_HIGH, "anc_med" to CambridgeConnectAmbientMode.ANC_MED,
            "anc_low" to CambridgeConnectAmbientMode.ANC_LOW, "transparency_low" to CambridgeConnectAmbientMode.TRANSPARENCY_LOW,
            "transparency_high" to CambridgeConnectAmbientMode.TRANSPARENCY_HIGH, "voice_focus" to CambridgeConnectAmbientMode.TRANSPARENCY_VOICE_FOCUS,
        )
        val LANGUAGES = linkedMapOf(
            "tones" to CambridgeConnectLanguage.TONES, "en" to CambridgeConnectLanguage.ENGLISH, "de" to CambridgeConnectLanguage.GERMAN,
            "es" to CambridgeConnectLanguage.SPANISH, "it" to CambridgeConnectLanguage.ITALIAN, "ja" to CambridgeConnectLanguage.JAPANESE,
            "ko" to CambridgeConnectLanguage.KOREAN, "yue" to CambridgeConnectLanguage.CANTONESE, "zh" to CambridgeConnectLanguage.MANDARIN,
            "fr" to CambridgeConnectLanguage.FRENCH, "celebrity" to CambridgeConnectLanguage.CELEBRITY, "off" to CambridgeConnectLanguage.OFF,
        )
        val AUTO_OFF = linkedMapOf("30" to CAMBRIDGE_CONNECT_AUTO_OFF[0], "60" to CAMBRIDGE_CONNECT_AUTO_OFF[1], "never" to CAMBRIDGE_CONNECT_AUTO_OFF[2])
        val CODEC_NAMES = mapOf(
            CambridgeConnectCodec.SBC to "sbc", CambridgeConnectCodec.AAC_LC to "aac", CambridgeConnectCodec.APTX to "aptx",
            CambridgeConnectCodec.APTX_ADAPTIVE to "aptx_adaptive", CambridgeConnectCodec.APTX_ADAPTIVE_LOSSLESS to "aptx_lossless",
            CambridgeConnectCodec.APTX_HD to "aptx_hd", CambridgeConnectCodec.APTX_LL to "aptx_ll", CambridgeConnectCodec.LDAC to "ldac",
            CambridgeConnectCodec.LHDC to "lhdc", CambridgeConnectCodec.LC3 to "lc3",
        )

        /** The gestures in the mask's bits 0 to 15, switched on or off when the earbuds list them. */
        val GESTURES = listOf(
            Gesture("play_pause", CambridgeConnectGesture.PLAY_PAUSE), Gesture("next", CambridgeConnectGesture.NEXT_TRACK),
            Gesture("previous", CambridgeConnectGesture.PREVIOUS_TRACK), Gesture("mic_mute", CambridgeConnectGesture.MIC_MUTE),
            Gesture("volume_up", CambridgeConnectGesture.VOLUME_UP), Gesture("volume_down", CambridgeConnectGesture.VOLUME_DOWN),
            Gesture("accept_call", CambridgeConnectGesture.ACCEPT_CALL), Gesture("reject_call", CambridgeConnectGesture.REJECT_CALL),
            Gesture("end_call", CambridgeConnectGesture.END_CALL), Gesture("assistant", CambridgeConnectGesture.VOICE_ASSISTANT),
            Gesture("transparency", CambridgeConnectGesture.TRANSPARENCY), Gesture("anc", CambridgeConnectGesture.ANC),
            Gesture("pairing_mode", CambridgeConnectGesture.PAIRING_MODE), Gesture("wear_detect", CambridgeConnectGesture.WEAR_DETECT),
            Gesture("ambient_modes", CambridgeConnectGesture.AMBIENT_NOISE_MODES), Gesture("gaming", CambridgeConnectGesture.GAMING_MODE),
        )

        /** Gestures outside the 16-bit mask: the app lists them in its user guide as switches, with no way to read them, so only what was sent shows. */
        val CASE_GESTURES = listOf(
            Gesture("power", CambridgeConnectGesture.POWER_ON_OFF), Gesture("pairing", CambridgeConnectGesture.CASE_PAIRING),
            Gesture("reset", CambridgeConnectGesture.CASE_FACTORY_RESET),
        )
        const val MAX_BANDS = 16
        val SIMPLE = mapOf(
            "ca_gaming" to Triple(CambridgeConnectWire.FEATURE_MODES, CambridgeConnectModes.SET_GAMING_MODE, CambridgeConnectModes.GET_GAMING_MODE),
            "ca_proximity" to Triple(CambridgeConnectWire.FEATURE_MODES, CambridgeConnectModes.SET_PROXIMITY_MODE, CambridgeConnectModes.GET_PROXIMITY_MODE),
            "ca_mono" to Triple(CambridgeConnectWire.FEATURE_MODES, CambridgeConnectModes.SET_MONO_MODE, CambridgeConnectModes.GET_MONO_MODE),
            "ca_sleep" to Triple(CambridgeConnectWire.FEATURE_MODES, CambridgeConnectModes.SET_SLEEP_MODE, CambridgeConnectModes.GET_SLEEP_MODE),
            "ca_find_alert" to Triple(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.SET_FIND_HEADPHONES_ALERT, CambridgeConnectMisc.GET_FIND_HEADPHONES_ALERT),
            "ca_loudness" to Triple(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.SET_LOUDNESS, CambridgeConnectMisc.GET_LOUDNESS),
            "ca_side_tone" to Triple(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.SET_SIDE_TONE, CambridgeConnectMisc.GET_SIDE_TONE),
        )
        val FLAG_ORDER = listOf("ca_gaming", "ca_mono", "ca_sleep", "ca_loudness", "ca_side_tone", "ca_spatial", "ca_proximity", "ca_find_alert")
        val SOUND_FLAGS = setOf("ca_gaming", "ca_mono", "ca_loudness", "ca_side_tone", "ca_spatial")
        val READS_MISC = setOf(
            CambridgeConnectMisc.GET_API_LEVEL, CambridgeConnectMisc.GET_BATTERY_STATE, CambridgeConnectMisc.GET_FIRMWARE_VERSIONS,
            CambridgeConnectMisc.GET_AMBIENT_NOISE_MODE, CambridgeConnectMisc.GET_LANGUAGE, CambridgeConnectMisc.GET_AUTO_POWER_DOWN_TIMEOUT,
            CambridgeConnectMisc.GET_FIND_HEADPHONES_ALERT, CambridgeConnectMisc.GET_AMBIENT_NOISE_TOGGLE_OPTIONS, CambridgeConnectMisc.GET_LOUDNESS,
            CambridgeConnectMisc.GET_SIDE_TONE, CambridgeConnectMisc.GET_SPATIAL_AUDIO,
        )
        val READS_MODES = setOf(
            CambridgeConnectModes.GET_GAMING_MODE, CambridgeConnectModes.GET_PROXIMITY_MODE, CambridgeConnectModes.GET_MONO_MODE, CambridgeConnectModes.GET_SLEEP_MODE,
        )
    }
}
