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
 * read), the device list's connect and disconnect, left/right balance and flight mode (the app's own screens do not use them), and the case's reset,
 * pairing and power gestures. The app sets the EQ and the noise levels through Qualcomm's audio curation, which this does not speak yet.
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

    private fun read(feature: Int, command: Int): ByteArray {
        sent += feature * 128 + command
        return ConnectPacket(feature, CambridgeConnectWire.TYPE_COMMAND, command).bytes()
    }

    public fun start(): List<ByteArray> = listOf(
        read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_API_LEVEL),
        read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_BATTERY_STATE),
        read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_FIRMWARE_VERSIONS),
        read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_AMBIENT_NOISE_MODE),
        read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_LANGUAGE),
        read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_AUTO_POWER_DOWN_TIMEOUT),
        read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_FIND_HEADPHONES_ALERT),
        read(CambridgeConnectWire.FEATURE_MODES, CambridgeConnectModes.GET_GAMING_MODE),
        read(CambridgeConnectWire.FEATURE_MODES, CambridgeConnectModes.GET_PROXIMITY_MODE),
        read(CambridgeConnectWire.FEATURE_MODES, CambridgeConnectModes.GET_MONO_MODE),
        read(CambridgeConnectWire.FEATURE_MODES, CambridgeConnectModes.GET_SLEEP_MODE),
        read(CambridgeConnectWire.FEATURE_CODEC, CambridgeConnectCodecCmd.GET_ENABLED_CODECS),
        read(CambridgeConnectWire.FEATURE_CODEC, CambridgeConnectCodecCmd.GET_AVAILABLE_CODECS),
        read(CambridgeConnectWire.FEATURE_GESTURES, CambridgeConnectGestureCmd.GET_SUPPORTED_GESTURE_CONTROLS),
        read(CambridgeConnectWire.FEATURE_GESTURES, CambridgeConnectGestureCmd.GET_GESTURE_CONTROLS_ON_OFF),
    )

    public fun batteryRequests(): List<ByteArray> = listOf(read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_BATTERY_STATE))

    /** The reads that confirm the last change, and the ones the API level just allowed. */
    public fun takeReadBacks(): List<ByteArray> = readBacks.also { readBacks = emptyList() }

    private fun atLeast(minor: Int): Boolean = api?.let { (major, m) -> major > 1 || (major == 1 && m >= minor) } == true

    /** The keys (feature * 128 + command) of what answered or failed, so the queue goes on. */
    public fun feed(bytes: ByteArray): List<Int> {
        val answered = mutableListOf<Int>()
        for (packet in parser.feed(bytes)) {
            val key = packet.feature * 128 + packet.command
            if (packet.isError) { answered += key; continue }
            if (!packet.isResponse || key !in sent) continue
            answered += key
            val p = packet.payload.map { it.toInt() and 0xff }
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

    private fun laterReads(): List<ByteArray> = buildList {
        if (atLeast(CambridgeConnectApi.TOGGLE_OPTIONS_MINOR)) add(read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_AMBIENT_NOISE_TOGGLE_OPTIONS))
        if (atLeast(CambridgeConnectApi.LOUDNESS_MINOR)) add(read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_LOUDNESS))
        if (atLeast(CambridgeConnectApi.SIDE_TONE_MINOR)) add(read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_SIDE_TONE))
        add(read(CambridgeConnectWire.FEATURE_MISC, CambridgeConnectMisc.GET_SPATIAL_AUDIO))
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
        val sound = FLAG_ORDER.filter { it in flags && it in SOUND_FLAGS }.map { Setting.Toggle(it, flags.getValue(it)) }
        if (sound.isNotEmpty()) add(SettingGroup("ca_sound", SettingTab.SOUND, sound))
        val buttons = gestures?.let { mask ->
            GESTURES.filter { supportedGestures and (1 shl it.value) != 0 }.map { Setting.Toggle("ca_gesture_${it.key}", mask and (1 shl it.value) != 0) }
        }.orEmpty()
        if (buttons.isNotEmpty()) add(SettingGroup("touch_buttons", SettingTab.CONTROLS, buttons))
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
                else -> return null
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

    /** Only frames this session builds: the reads above and the writes of its rows; never a reset, power, pairing or firmware command. */
    public fun allowed(bytes: ByteArray): Boolean {
        val p = ConnectParser().feed(bytes).singleOrNull() ?: return false
        if (!p.bytes().contentEquals(bytes) || p.type != CambridgeConnectWire.TYPE_COMMAND) return false
        val n = p.payload.map { it.toInt() and 0xff }
        val c = p.command
        return when (p.feature) {
            CambridgeConnectWire.FEATURE_MISC -> when (c) {
                in READS_MISC -> n.isEmpty()
                CambridgeConnectMisc.SET_AMBIENT_NOISE_MODE -> n.size == 1 && n[0] in NOISE.values
                CambridgeConnectMisc.SET_LANGUAGE -> n.size == 1 && n[0] in LANGUAGES.values
                CambridgeConnectMisc.SET_AUTO_POWER_DOWN_TIMEOUT -> n.size == 3 && (n[0] * 3600 + n[1] * 60 + n[2]) in AUTO_OFF.values
                CambridgeConnectMisc.SET_AMBIENT_NOISE_TOGGLE_OPTIONS -> n.size == 3 && n.all { it <= 1 } && n.any { it == 1 }
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
                    n.size == 2 && n[1] <= 1 && GESTURES.any { it.value == n[0] } && supportedGestures and (1 shl n[0]) != 0
                else -> false
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

        /** The gestures in the mask's bits 0 to 15; pairing, power and the case's reset lie outside it and are never sent. */
        val GESTURES = listOf(
            Gesture("play_pause", CambridgeConnectGesture.PLAY_PAUSE), Gesture("next", CambridgeConnectGesture.NEXT_TRACK),
            Gesture("previous", CambridgeConnectGesture.PREVIOUS_TRACK), Gesture("mic_mute", CambridgeConnectGesture.MIC_MUTE),
            Gesture("volume_up", CambridgeConnectGesture.VOLUME_UP), Gesture("volume_down", CambridgeConnectGesture.VOLUME_DOWN),
            Gesture("accept_call", CambridgeConnectGesture.ACCEPT_CALL), Gesture("reject_call", CambridgeConnectGesture.REJECT_CALL),
            Gesture("end_call", CambridgeConnectGesture.END_CALL), Gesture("assistant", CambridgeConnectGesture.VOICE_ASSISTANT),
            Gesture("transparency", CambridgeConnectGesture.TRANSPARENCY), Gesture("anc", CambridgeConnectGesture.ANC),
            Gesture("ambient_modes", CambridgeConnectGesture.AMBIENT_NOISE_MODES), Gesture("gaming", CambridgeConnectGesture.GAMING_MODE),
        )
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
