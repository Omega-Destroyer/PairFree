package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.BatteryLevel
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.FitResult
import io.github.omegadestroyer.pairfree.protocol.FitTest
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value

/**
 * One connection to Soundcore earbuds, shown on PairFree's screens. The screens work in the
 * SoundPEATS command table ([Command], [EarbudState]), so the earbuds' state becomes
 * [EarbudState] values and a setting from the screens becomes a Soundcore packet. Only the
 * settings both brands share are mapped: battery, noise control, game mode, LDAC and two
 * devices at once. The model's [SoundcoreLayout] says where each is, and only what the model
 * has is shown. Touch controls have no SoundPEATS counterpart; the Controls tab reads them
 * from [touch].
 */
public class SoundcoreSession(public val model: String) : FitTest {
    public val layout: SoundcoreLayout? = SoundcoreLayout.orReportOnly(model)
    private val checksum: Boolean = layout?.checksum ?: true

    /** What the screens show. */
    override var state: EarbudState = EarbudState(fitTest = model in WEAR_FIT_MODELS)
        private set

    /** The last full state the earbuds sent, for the device report. */
    public var lastState: SoundcoreState? = null
        private set

    // The sound modes block as the earbuds last reported it; a noise mode change sends it back with byte 0 changed.
    private var soundModes: ByteArray? = null

    /**
     * Applies one packet from the earbuds. Returns true when [state] changed. Acknowledgements
     * (empty bodies) and packets PairFree does not use change nothing.
     */
    public fun receive(packet: SoundcorePacket): Boolean {
        val body = packet.body
        if (body.isEmpty()) return false
        val before = state
        when (packet.command) {
            SoundcoreCommand.STATE -> {
                val l = layout ?: return false
                val s = SoundcoreState.parse(l, body) ?: return false
                lastState = s
                soundModes = s.soundModes
                val values = buildMap {
                    put(Command.FIRMWARE_VERSION, Value.Text("${model}_${s.firmware ?: "unknown"}"))
                    if (l.battery != null && EARBUD_BATTERIES.any(l::has)) {
                        put(Command.BATTERY_LEFT, steps(s.batteryReading(0)))
                        if (l.has("dual_battery") || l.has("dual_battery_level")) put(Command.BATTERY_RIGHT, steps(s.batteryReading(1)))
                    }
                    if (l.case != null && l.has("case_battery")) put(Command.BATTERY_CASE, steps(s.caseReading))
                    if (l.has("sound_modes")) put(Command.ANC_MODE, Value.Number(ancValue(s.ambientMode)))
                    s.flag("gaming_mode")?.let { put(Command.GAME_MODE, Value.Switch(it)) }
                    s.flag("ldac")?.let { put(Command.LDAC, Value.Switch(it)) }
                    s.flag("dual_connections_enabled")?.let { put(Command.MULTIPOINT, Value.Switch(it)) }
                }
                state = state.copy(values = state.values + values)
            }
            SoundcoreCommand.BATTERY -> {
                // One level per earbud. OpenSCQ30 reads two more bytes it does not name; on the
                // A40 the third is the case (docs/soundcore/PROTOCOL.md), so only the A40 reads it.
                val l = layout ?: return false
                val scale = l.battery ?: return false
                val dual = l.has("dual_battery") || l.has("dual_battery_level")
                if (body.size < (if (dual) 2 else 1)) return false
                state = state.copy(
                    values = state.values + buildMap {
                        put(Command.BATTERY_LEFT, steps(scale.reading(body.u8(0))))
                        if (dual) put(Command.BATTERY_RIGHT, steps(scale.reading(body.u8(1))))
                        val case = l.case
                        if (model == A3936 && case != null && body.size >= 3) put(Command.BATTERY_CASE, steps(case.reading(body.u8(2))))
                    },
                )
            }
            SoundcoreCommand.SOUND_MODES -> if (body.size >= 4 && soundModes?.size == 4) {
                val modes = body.copyOfRange(0, 4)
                soundModes = modes
                lastState = lastState?.with("sound_modes", modes)
                put(Command.ANC_MODE, Value.Number(ancValue(AmbientMode.of(modes.u8(0)))))
            }
            // The fit test's reply: a code per earbud, 0 or 1 a good fit (Soundcore 2.9.9's `analyzeWearTest`).
            SoundcoreCommand.WEAR_FIT_DETECTION -> if (body.size >= 2) {
                fun fit(code: Int) = if (code <= 1) FitResult.Fit.GOOD else FitResult.Fit.NOT_FIT
                state = state.copy(fit = FitResult(fit(body.u8(0)), fit(body.u8(1))))
            }
            SoundcoreCommand.GAMING_MODE_UPDATE -> put(Command.GAME_MODE, Value.Switch(body.u8(0) != 0))
            SoundcoreCommand.LDAC_STATE -> put(Command.LDAC, Value.Switch(body.u8(0) != 0))
            // Whether both earbuds are in decides which touch actions apply (SoundcoreButtons).
            SoundcoreCommand.TWS_STATUS -> lastState?.let { s -> lastState = s.with("tws_status", body) }
        }
        return state != before
    }

    /** Starts the ear tip fit test on the models whose manager in Soundcore 2.9.9 has it; the result arrives as a packet. */
    override fun startFitTest(): List<ByteArray>? {
        if (model !in WEAR_FIT_MODELS) return null
        state = state.copy(fit = null)
        return listOf(SoundcorePacket.build(SoundcoreCommand.WEAR_FIT_DETECTION, checksum = checksum))
    }

    /** The Controls tab's settings from the last state, or null before the first state. */
    public val touch: SoundcoreTouch? get() = lastState?.let(SoundcoreTouch::of)

    /** Shows a change from [SoundcoreTouch] at once; the next state the earbuds send confirms it. */
    public fun expect(change: SoundcoreButtons.Change) {
        if (change.state.layout === layout) lastState = change.state
    }

    /** [packets] as frames for these earbuds, with or without the checksum as the model needs. */
    public fun frames(packets: List<SoundcorePacket>): List<ByteArray> =
        packets.filter { it.command !in SoundcoreCommand.NEVER_SEND }.map {
            val body = if (model == "A3954" && it.command == SoundcoreCommand.SET_SOUND_MODES && it.body.size == 4) soundModesBody(it.body) else it.body
            SoundcorePacket.build(it.command, body, checksum)
        }

    private fun soundModesBody(modes: ByteArray): ByteArray =
        if (model == "A3954" && modes.size == 4) modes + byteArrayOf(0xFF.toByte()) else modes

    /**
     * The packet for a setting from the screens, or null when these earbuds cannot take it: a
     * setting their last state did not show (the model or its firmware lacks it), or any setting
     * before the first state. The change shows in [state] at once; the next state the earbuds
     * send confirms it.
     */
    public fun set(command: Command, value: Int): ByteArray? {
        val l = layout ?: return null
        val shown = lastState ?: return null
        fun switch(field: String, number: Int): ByteArray? = if (shown.flag(field) != null) flag(number, value) else null
        val packet = when (command) {
            Command.ANC_MODE -> {
                val modes = soundModes ?: return null
                val ambient = ambientFor(value) ?: return null
                val changed = modes.copyOf().also { it[0] = ambient.value.toByte() }
                soundModes = changed
                lastState = shown.with("sound_modes", changed)
                SoundcorePacket.build(SoundcoreCommand.SET_SOUND_MODES, soundModesBody(changed), checksum)
            }
            Command.GAME_MODE -> switch("gaming_mode", l.gamingModeCommand)
            Command.LDAC -> switch("ldac", SoundcoreCommand.LDAC)
            Command.MULTIPOINT -> switch("dual_connections_enabled", SoundcoreCommand.DUAL_CONNECTIONS_ENABLED)
            else -> null
        } ?: return null
        state = state.expect(command, value)
        return packet
    }

    /**
     * Settings the shared screens and the Controls tab have no place for, as brand rows: the Sleep A20's sleep timer
     * (docs/soundcore/PROTOCOL.md section 11). Each shows only where the model has it and the last state holds it.
     */
    public val groups: List<SettingGroup>
        get() {
            val s = lastState ?: return emptyList()
            val switches = SWITCHES.mapNotNull { switchRow(s, it) } + listOfNotNull(pressRow(s))
            val rows = listOfNotNull(sleepTimerRow(s)) + A30_FLAGS.filter { it.home.not() }.mapNotNull { flagRow(s, it) } +
                listOfNotNull(listeningRow(s, A30_LISTENING_MODE), listeningRow(s, A30_DEFAULT_LISTENING_MODE))
            val home = A30_FLAGS.filter { it.home }.mapNotNull { flagRow(s, it) } + listOfNotNull(listeningRow(s, A30_LISTENING_MODE))
            return listOfNotNull(
                if (switches.isEmpty()) null else SettingGroup("sc_device", SettingTab.DEVICE, switches),
                if (home.isEmpty()) null else SettingGroup("sleep_sound", SettingTab.HOME, home),
                if (rows.filterNot { it in home }.isEmpty()) null else SettingGroup("sleep", SettingTab.DEVICE, rows.filterNot { it in home }),
                a3954SoundRows(s)?.takeIf { it.isNotEmpty() }?.let { SettingGroup("noise", SettingTab.HOME, it) },
                a3954FeatureRows(s)?.takeIf { it.isNotEmpty() }?.let { SettingGroup("other", SettingTab.DEVICE, it) },
            )
        }

    private fun a3954SoundRows(s: SoundcoreState): List<Setting>? {
        if (model != "A3954") return null
        val b = s.field("sound_modes") ?: return null
        val mode = b.u8(0)
        val modeOption = when (mode) { 0 -> "anc"; 1 -> "transparency"; 2 -> "normal"; 3 -> "airplane"; else -> null }
        return listOfNotNull(
            Setting.Choice("a3954_ambient_mode", modeOption, listOf("transparency", "normal", "anc", "airplane")),
            if (mode == 3) Setting.Choice("a3954_airplane_mode", b.u8(2).toString(), listOf("manual", "automatic")) else null,
            if (mode == 3 && b.u8(2) == 0) Setting.Action("a3954_air_pressure_update") else null,
            if (mode == 3) s.field("air_pressure")?.let { Setting.Information("a3954_air_pressure", "${it.u8(0) / 100}.${(it.u8(0) % 100).toString().padStart(2, '0')} atm") } else null,
            Setting.Toggle("a3954_wind_noise", b.u8(3) and 1 != 0),
            if (mode != 3) Setting.Level("a3954_noise_strength", b.u8(1).coerceIn(1, 11), 1, 11) else null,
        )
    }

    private fun a3954FeatureRows(s: SoundcoreState): List<Setting>? {
        if (model != "A3954") return null
        val spatial = s.field("spatial_audio") ?: return null
        val easy = s.field("easy_chat_enabled") ?: return null
        val wait = s.field("easy_chat_wait_time") ?: return null
        val case = s.field("case_features")
        val spatialMode = if (spatial.u8(0) == 0) null else when {
            spatial.u8(2) == 0 && spatial.u8(1) == 1 -> "fixed"
            spatial.u8(2) == 0 && spatial.u8(1) == 2 -> "head_tracking"
            spatial.u8(2) == 1 -> "podcast"
            spatial.u8(2) == 2 -> "movie"
            spatial.u8(2) == 3 -> "gaming"
            else -> null
        }
        return listOfNotNull(
            Setting.Toggle("a3954_spatial_audio", spatial.u8(0) != 0),
            if (spatial.u8(0) != 0) Setting.Choice("a3954_spatial_mode", spatialMode, SPATIAL_MODES) else null,
            Setting.Toggle("a3954_easy_chat", easy.u8(0) != 0),
            Setting.Choice("a3954_easy_chat_wait", listOf("5s", "10s", "15s").getOrNull(wait.u8(0)), listOf("5s", "10s", "15s")),
            s.field("auto_power_off")?.let {
                val options = listOf("off", "10", "20", "30", "60")
                val value = if (it.u8(0) == 0) "off" else options.getOrNull(it.u8(1) + 1)
                Setting.Choice("a3954_auto_power_off_duration", value, options)
            },
            s.field("case_language")?.let { Setting.Choice("a3954_case_language", listOf("Japanese", "English", "Chinese").getOrNull(it.u8(0)), listOf("Japanese", "English", "Chinese")) },
            case?.let { Setting.Toggle("a3954_case_atmospheric", it.u8(0) != 0) },
            case?.let { Setting.Toggle("a3954_case_camera", it.u8(1) != 0) },
            case?.let { Setting.Toggle("a3954_case_find", it.u8(2) != 0) },
            case?.let { Setting.Toggle("a3954_case_spatial", it.u8(3) != 0) },
            s.field("limit_high_volume")?.let { Setting.Toggle("a3954_volume_limit", it.u8(0) != 0) },
            s.field("limit_high_volume")?.let { Setting.Level("a3954_volume_limit_db", it.u8(1).coerceIn(75, 100), 75, 100) },
            s.field("low_battery_prompt")?.let { Setting.Toggle("a3954_low_battery_prompt", it.u8(0) != 0) },
            s.field("wearing_detection")?.let { Setting.Toggle("a3954_wearing_detection", it.u8(0) != 0) },
            s.field("sound_leak_compensation")?.let { Setting.Toggle("a3954_sound_leak_compensation", it.u8(0) != 0) },
        )
    }

    /**
     * One of the earbuds' own switches ([SWITCHES]: wear detection, side tone, prompts and the rest, as the Soundcore app's code names them), where the
     * model's state has its field. A switch of more than one byte keeps the other bytes as the earbuds reported them.
     */
    private fun switchRow(s: SoundcoreState, w: Switch): Setting? =
        if (s.layout.has(w.field) && s.flag(w.field) != null) Setting.Toggle(w.key, s.flag(w.field) == true) else null

    /** The A3953's press sensitivity: five levels from softest (0) to firmest (4), set with both earbuds connected (its screen). */
    private fun pressRow(s: SoundcoreState): Setting? {
        val v = s.field("press_sensitivity")?.takeIf { it.isNotEmpty() }?.u8(0) ?: return null
        return Setting.Choice(PRESS_SENSITIVITY, PRESS_LEVELS.getOrNull(v), PRESS_LEVELS)
    }

    /** One of the Sleep A30's switches (OpenSCQ30's d1301 flags), where the model's state holds it. */
    private fun flagRow(s: SoundcoreState, f: A30Flag): Setting? {
        if (f.feature !in s.layout.features) return null
        return s.flag(f.field)?.let { Setting.Toggle(f.key, it) }
    }

    /** The Sleep A30's listening mode, or the one it starts in: Bluetooth audio, or the audio held on the earbuds. */
    private fun listeningRow(s: SoundcoreState, c: A30Choice): Setting? {
        if (c.feature !in s.layout.features) return null
        val v = s.field(c.field)?.takeIf { it.isNotEmpty() }?.u8(0) ?: return null
        return Setting.Choice(c.key, A30_MODES.getOrNull(v), A30_MODES)
    }

    /** The sleep timer: off, or the minutes after which the earbuds stop the audio. */
    private fun sleepTimerRow(s: SoundcoreState): Setting? {
        if (SLEEP_TIMER !in s.layout.features) return null
        val b = s.field(SLEEP_TIMER) ?: return null
        val value = if (b.u8(0) == SLEEP_TIMER_ON) b.u16(1).toString() else OFF
        return Setting.Choice(SLEEP_TIMER, value, listOf(OFF) + SLEEP_TIMER_MINUTES.map { it.toString() })
    }

    /**
     * The packets for a change to one of [groups], or null when it is not one of them. The change shows at once;
     * the next state the earbuds send confirms it.
     */
    public fun change(change: SettingChange): List<SoundcorePacket>? {
        val s = lastState ?: return null
        if (change is SettingChange.Press && change.key == "a3954_air_pressure_update") {
            val modes = s.field("sound_modes") ?: return null
            if (model != "A3954" || modes.u8(0) != 3 || modes.u8(2) != 0) return null
            return listOf(SoundcorePacket(0x1681, ByteArray(0)))
        }
        if (change is SettingChange.Toggle) {
            if (model == "A3954") {
                val f = when (change.key) {
                    "a3954_spatial_audio" -> "spatial_audio" to 0x1081
                    "a3954_easy_chat" -> "easy_chat_enabled" to 0x109D
                    "a3954_wind_noise" -> "sound_modes" to SoundcoreCommand.SET_SOUND_MODES
                    "a3954_case_atmospheric" -> "case_features" to 0x0787
                    "a3954_case_camera" -> "case_features" to 0x0787
                    "a3954_case_find" -> "case_features" to 0x0787
                    "a3954_case_spatial" -> "case_features" to 0x0787
                    "a3954_volume_limit" -> "limit_high_volume" to 0x2082
                    "a3954_low_battery_prompt" -> "low_battery_prompt" to 0x1082
                    "a3954_wearing_detection" -> "wearing_detection" to 0x0181
                    "a3954_sound_leak_compensation" -> "sound_leak_compensation" to 0x1086
                    else -> null
                } ?: return null
                val bytes = s.field(f.first) ?: return null
                when (change.key) {
                    "a3954_wind_noise" -> bytes[3] = ((bytes.u8(3) and 2) or (if (change.on) 1 else 0)).toByte()
                    "a3954_case_atmospheric", "a3954_case_camera", "a3954_case_find", "a3954_case_spatial" -> {
                        val i = listOf("a3954_case_atmospheric", "a3954_case_camera", "a3954_case_find", "a3954_case_spatial").indexOf(change.key)
                        bytes[i] = (if (change.on) 1 else 0).toByte()
                    }
                    else -> bytes[0] = (if (change.on) 1 else 0).toByte()
                }
                lastState = s.with(f.first, bytes)
                val body = if (change.key == "a3954_easy_chat") {
                    byteArrayOf(bytes[0], s.field("easy_chat_wait_time")?.get(0) ?: 0)
                } else if (change.key == "a3954_volume_limit") {
                    byteArrayOf(bytes[0], bytes[1])
                } else bytes
                return listOf(SoundcorePacket(f.second, body))
            }
            SWITCHES.firstOrNull { it.key == change.key }?.let { w ->
                if (switchRow(s, w) == null) return null
                val held = s.field(w.field) ?: return null
                val body = held.copyOf().also { it[0] = if (change.on) 1 else 0 }
                lastState = s.with(w.field, body)
                return listOf(SoundcorePacket(w.command, body))
            }
            val f = A30_FLAGS.firstOrNull { it.key == change.key } ?: return null
            if (flagRow(s, f) == null) return null
            lastState = s.with(f.field, byteArrayOf(if (change.on) 1 else 0))
            return listOf(SoundcorePacket(f.command, byteArrayOf(if (change.on) 1 else 0)))
        }
        if (change is SettingChange.Choice && change.key == PRESS_SENSITIVITY) {
            val v = PRESS_LEVELS.indexOf(change.option).takeIf { it >= 0 && pressRow(s) != null } ?: return null
            lastState = s.with("press_sensitivity", byteArrayOf(v.toByte()))
            return listOf(SoundcorePacket(SoundcoreCommand.PRESS_SENSITIVITY, byteArrayOf(v.toByte())))
        }
        if (change is SettingChange.Choice && change.key != SLEEP_TIMER) {
            if (model == "A3954") {
                val f = when (change.key) {
                    "a3954_ambient_mode" -> Quad("sound_modes", SoundcoreCommand.SET_SOUND_MODES, 0, listOf("transparency", "normal", "anc", "airplane"))
                    "a3954_airplane_mode" -> Quad("sound_modes", SoundcoreCommand.SET_SOUND_MODES, 2, listOf("manual", "automatic"))
                    "a3954_spatial_mode" -> {
                        val bytes = s.field("spatial_audio") ?: return null
                        val encoded = when (change.option) {
                            "fixed" -> byteArrayOf(1, 1, 0)
                            "head_tracking" -> byteArrayOf(1, 2, 0)
                            "podcast" -> byteArrayOf(1, 1, 1)
                            "movie" -> byteArrayOf(1, 1, 2)
                            "gaming" -> byteArrayOf(1, 1, 3)
                            else -> return null
                        }
                        lastState = s.with("spatial_audio", encoded)
                        return listOf(SoundcorePacket(0x1081, encoded))
                    }
                    "a3954_easy_chat_wait" -> Quad("easy_chat_wait_time", 0x109D, 0, listOf("5s", "10s", "15s"))
                    "a3954_case_language" -> Quad("case_language", 0x078A, 0, listOf("Japanese", "English", "Chinese"))
                    else -> return null
                }
                val selected = f.options.indexOf(change.option).takeIf { it >= 0 } ?: return null
                val bytes = s.field(f.field) ?: return null
                val v = selected
                bytes[f.index] = v.toByte()
                if (change.key == "a3954_ambient_mode") {
                    val mode = when (change.option) { "anc" -> 0; "transparency" -> 1; "normal" -> 2; else -> 3 }
                    bytes[0] = mode.toByte()
                    bytes[1] = when (mode) { 0 -> 9; 1 -> 3; 2 -> 6; else -> bytes.u8(1) }.toByte()
                }
                lastState = s.with(f.field, bytes)
                if (f.field == "sound_modes") soundModes = bytes.copyOf()
                if (change.key == "a3954_ambient_mode") put(Command.ANC_MODE, Value.Number(when (bytes.u8(0)) { 0 -> ancNumber("anc"); 1 -> ancNumber("transparency"); 2 -> ancNumber("off"); else -> null }))
                val body = if (f.field == "easy_chat_wait_time") byteArrayOf(s.field("easy_chat_enabled")?.get(0) ?: return null, v.toByte()) else bytes
                return listOf(SoundcorePacket(f.command, body))
            }
            if (change.key == "a3954_auto_power_off_duration") {
                val bytes = s.field("auto_power_off") ?: return null
                val options = listOf("off", "10", "20", "30", "60")
                val selected = options.indexOf(change.option).takeIf { it >= 0 } ?: return null
                if (selected == 0) bytes[0] = 0 else {
                    bytes[0] = 1
                    bytes[1] = (selected - 1).toByte()
                }
                lastState = s.with("auto_power_off", bytes)
                return listOf(SoundcorePacket(0x0186, bytes))
            }
            val c = listOf(A30_LISTENING_MODE, A30_DEFAULT_LISTENING_MODE).firstOrNull { it.key == change.key } ?: return null
            val v = A30_MODES.indexOf(change.option).takeIf { it >= 0 && listeningRow(s, c) != null } ?: return null
            lastState = s.with(c.field, byteArrayOf(v.toByte()))
            return listOf(SoundcorePacket(c.command, byteArrayOf(v.toByte())))
        }
        if (change is SettingChange.Level && model == "A3954") {
            if (change.key == "a3954_volume_limit_db") {
                val bytes = s.field("limit_high_volume") ?: return null
                bytes[1] = (change.value.coerceIn(75, 100) / 5 * 5).toByte()
                lastState = s.with("limit_high_volume", bytes)
                return listOf(SoundcorePacket(0x2082, byteArrayOf(bytes[0], bytes[1])))
            }
            val bytes = s.field("sound_modes") ?: return null
            if (change.key != "a3954_noise_strength") return null
            val next = change.value
            if (change.key != "a3954_noise_strength" || bytes.u8(0) == 3 || next !in 1..11) return null
            bytes[1] = next.toByte()
            bytes[0] = when (next) { in 1..5 -> 1; 6 -> 2; else -> 0 }.toByte()
            lastState = s.with("sound_modes", bytes)
            soundModes = bytes.copyOf()
            put(Command.ANC_MODE, Value.Number(when (bytes.u8(0)) { 0 -> ancNumber("anc"); 1 -> ancNumber("transparency"); else -> ancNumber("off") }))
            return listOf(SoundcorePacket(SoundcoreCommand.SET_SOUND_MODES, bytes))
        }
        if (change !is SettingChange.Choice || change.key != SLEEP_TIMER || SLEEP_TIMER !in s.layout.features) return null
        val b = s.field(SLEEP_TIMER) ?: return null
        val minutes = change.option.toIntOrNull()
        if (minutes == null && change.option != OFF) return null
        // Off keeps the minutes, and the last byte goes back as the earbuds sent it, as the Soundcore app does.
        val m = minutes ?: b.u16(1)
        val body = byteArrayOf((if (minutes != null) SLEEP_TIMER_ON else SLEEP_TIMER_OFF).toByte(), m.toByte(), (m shr 8).toByte(), b[3])
        lastState = s.with(SLEEP_TIMER, body)
        return listOf(SoundcorePacket(SoundcoreCommand.SET_SLEEP_TIMER, body))
    }

    /** The request for the full state, sent on connecting and now and then after. */
    public fun requestState(): ByteArray = SoundcorePacket.build(SoundcoreCommand.STATE, checksum = checksum)

    private fun flag(command: Int, value: Int): ByteArray =
        SoundcorePacket.build(command, byteArrayOf(if (value != 0) 1 else 0), checksum)

    private fun put(command: Command, value: Value) {
        state = state.copy(values = state.values + (command to value))
    }

    private fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF

    private fun ByteArray.u16(at: Int): Int = u8(at) or (u8(at + 1) shl 8)

    private data class Quad(val field: String, val command: Int, val index: Int, val options: List<String>)

    // A battery in the model's own steps (fifths on most), so the screens can show bars as the Soundcore app does.
    private fun steps(reading: BatteryLevel?): Value = Value.Steps(reading?.level, reading?.levels ?: BatteryLevel.PERCENT)

    public companion object {
        public const val A3936: String = "A3936"

        /** The models whose manager in Soundcore 2.9.9 sends the wear fit detection (`0901`): the A3951 family's `M0()` list that PairFree has. */
        public val WEAR_FIT_MODELS: Set<String> = setOf("A3931", "A3935", "A3951")

        /** Models whose state PairFree can read: every model with a layout in data/soundcore/devices.json. */
        public val SUPPORTED_MODELS: Set<String> get() = SoundcoreLayout.ALL.map { it.model }.toSet()

        // The Sleep A20's sleep timer (state bytes 67-70, 15 85): 1 on or 2 off, the minutes, a byte kept as it is.
        // The choices: the 30 minutes a recording shows, then OpenSCQ30's note of 1 to 8 hours in half hours.
        private const val SLEEP_TIMER = "sleep_timer"
        private const val SLEEP_TIMER_ON = 1
        private const val SLEEP_TIMER_OFF = 2
        private const val OFF = "off"
        private val SLEEP_TIMER_MINUTES = (30..480 step 30).toList()

        // The Sleep A30's switches and listening modes (OpenSCQ30 d1301; no recording of PairFree's yet): the row, the state field, the
        // OpenSCQ30 feature that lists it for the model, the command, and whether it shows on the Home tab.
        /**
         * The earbuds' own switches, by the state field that holds them and the command that sets them (Soundcore 3.3.0's `Cmm2CmdData`, whose callbacks
         * name each: `setMoreWearDetectionCallback`, `setMoreSideToneCallback`, `setLowBatteryAlertSwitchCallback`...). A switch whose body has more than one
         * byte (auto power off: switch and duration index; spatial audio: switch, effect and mode) is written back with the other bytes as reported.
         */
        private const val PRESS_SENSITIVITY = "sc_press_sensitivity"
        private val PRESS_LEVELS = listOf("softest", "soft", "normal", "firm", "firmest")

        private class Switch(val key: String, val field: String, val command: Int)
        private val SWITCHES = listOf(
            Switch("sc_wearing_detection", "wearing_detection", SoundcoreCommand.WEARING_DETECTION),
            Switch("sc_side_tone", "side_tone", SoundcoreCommand.SIDE_TONE),
            Switch("sc_in_ear_tone", "in_ear_tone", SoundcoreCommand.IN_EAR_TONE),
            Switch("sc_bass_up", "bass_up", SoundcoreCommand.BASS_UP),
            Switch("sc_spatial_audio", "spatial_audio", SoundcoreCommand.SPATIAL_AUDIO),
            Switch("sc_low_battery_prompt", "low_battery_prompt", SoundcoreCommand.LOW_BATTERY_PROMPT),
            Switch("sc_ambient_sound_prompt", "ambient_sound_prompt", SoundcoreCommand.AMBIENT_SOUND_PROMPT),
            Switch("sc_sound_leak_compensation", "sound_leak_compensation", SoundcoreCommand.SOUND_LEAK_COMPENSATION),
            Switch("sc_auto_power_off", "auto_power_off", SoundcoreCommand.AUTO_POWER_OFF),
            Switch("sc_smart_play", "smart_play", SoundcoreCommand.SMART_PLAY),
            Switch("sc_adaptive_noise", "adaptive_noise", SoundcoreCommand.ADAPTIVE_NOISE),
            Switch("sc_super_hearing", "super_hearing", SoundcoreCommand.SUPER_HEARING),
            Switch("sc_call_mix", "call_mix", SoundcoreCommand.CALL_MIX),
            Switch("sc_microphone", "microphone", SoundcoreCommand.MICROPHONE),
        )

        private class A30Flag(val key: String, val field: String, val feature: String, val command: Int, val home: Boolean)
        private class A30Choice(val key: String, val field: String, val feature: String, val command: Int)
        private val A30_FLAGS = listOf(
            A30Flag("sleep_noise_canceling", "noise_canceling", "d1301_noise_canceling", SoundcoreCommand.SET_NOISE_CANCELING, true),
            A30Flag("sleep_noise_canceling_prompt", "noise_canceling_prompt", "d1301_noise_canceling_prompt", SoundcoreCommand.SET_NOISE_CANCELING_PROMPT, false),
            A30Flag("sleep_listening_mode_prompt", "listening_mode_prompt", "d1301_listening_mode_prompt", SoundcoreCommand.SET_LISTENING_MODE_PROMPT, false),
            A30Flag("sleep_auto_power_off_prompt", "auto_power_off_prompt", "d1301_auto_power_off_prompt", SoundcoreCommand.SET_AUTO_POWER_OFF_PROMPT, false),
            A30Flag("sleep_calls_during_bluetooth", "incoming_calls_during_bluetooth", "d1301_incoming_calls_during_bluetooth_mode", SoundcoreCommand.SET_INCOMING_CALLS_DURING_BLUETOOTH, false),
        )
        private val A30_LISTENING_MODE = A30Choice("sleep_listening_mode", "listening_mode", "d1301_listening_mode", SoundcoreCommand.SET_LISTENING_MODE)
        private val A30_DEFAULT_LISTENING_MODE = A30Choice("sleep_default_listening_mode", "default_listening_mode", "d1301_listening_mode", SoundcoreCommand.SET_DEFAULT_LISTENING_MODE)
        private val A30_MODES = listOf("bluetooth", "local")
        private val SPATIAL_MODES = listOf("fixed", "head_tracking", "podcast", "movie", "gaming")

        private val EARBUD_BATTERIES = listOf("dual_battery", "dual_battery_level", "single_battery", "single_battery_charging_first", "battery_level")

        // The noise modes by their names in data/soundpeats/commands.json, so no number is retyped.
        private fun ancNumber(name: String): Int = Command.ANC_MODE.labels.entries.first { it.value == name }.key

        private fun ancValue(mode: AmbientMode?): Int? = when (mode) {
            AmbientMode.NORMAL -> ancNumber("off")
            AmbientMode.NOISE_CANCELLING -> ancNumber("anc")
            AmbientMode.TRANSPARENCY -> ancNumber("transparency")
            null -> null
        }

        private fun ambientFor(value: Int): AmbientMode? = when (value) {
            ancNumber("off") -> AmbientMode.NORMAL
            ancNumber("anc") -> AmbientMode.NOISE_CANCELLING
            ancNumber("transparency") -> AmbientMode.TRANSPARENCY
            else -> null
        }
    }
}
