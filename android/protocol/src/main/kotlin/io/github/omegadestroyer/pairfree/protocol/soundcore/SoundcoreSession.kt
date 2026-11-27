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
        // The Life NC asks for its state with `01 09` ([SoundcoreCommand.DEVICE_INFO]) and answers under it.
        val command = if (packet.command == SoundcoreCommand.DEVICE_INFO && layout?.stateCommand == SoundcoreCommand.DEVICE_INFO) SoundcoreCommand.STATE else packet.command
        when (command) {
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
            // Whether the earbuds are beeping (left, right, case), also when the Soundcore app or a touch stopped them.
            SoundcoreCommand.DEVICE_INFO -> if (beeps && body.size >= 2) {
                finding = body.u8(0) == 1 || body.u8(1) == 1
                mutedLeft = finding && body.u8(0) != 1
                mutedRight = finding && body.u8(1) != 1
                return true
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
            // The earbuds' notice holds the state's block, or more bytes after it (the Liberty 4 Pro's has a fifth).
            SoundcoreCommand.SOUND_MODES -> if (soundModes?.size?.let { body.size >= minOf(it, 4) } == true) {
                // A notice shorter than the block (the Q20i's sound modes are six bytes) updates the bytes it holds.
                val modes = soundModes!!.copyOf().also { body.copyInto(it, 0, 0, minOf(body.size, it.size)) }
                soundModes = modes
                lastState = lastState?.with("sound_modes", modes)
                put(Command.ANC_MODE, Value.Number(ancValue(AmbientMode.of(modes.u8(0)))))
            }
            // The fit test's reply: a code per earbud, 0 or 1 a good fit (Soundcore 2.9.9's `analyzeWearTest`).
            SoundcoreCommand.WEAR_FIT_DETECTION -> if (body.size >= 2) {
                fun fit(code: Int) = if (code <= 1) FitResult.Fit.GOOD else FitResult.Fit.NOT_FIT
                state = state.copy(fit = FitResult(fit(body.u8(0)), fit(body.u8(1))))
            }
            // Only where the state holds the field: a switch shown from a notice alone cannot be written (the Liberty Buds 2's LDAC did nothing, issue #76).
            SoundcoreCommand.GAMING_MODE_UPDATE -> if (lastState?.flag("gaming_mode") != null) put(Command.GAME_MODE, Value.Switch(body.u8(0) != 0))
            SoundcoreCommand.LDAC_STATE -> if (lastState?.flag("ldac") != null) put(Command.LDAC, Value.Switch(body.u8(0) != 0))
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

    private fun soundModesBody(modes: ByteArray): ByteArray = when {
        model == "A3954" && modes.size == 4 -> modes + byteArrayOf(0xFF.toByte())
        // The Q20i's block: the app writes the wind byte as its suppression bit alone (`isWindNoiseSuppression ? 1 : 0`), the sensitivity as reported (issue #11).
        model == "A3004" && modes.size == 6 -> modes.copyOf().also { it[4] = (it[4].toInt() and 1).toByte() }
        else -> modes
    }

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
                val changed = modes.copyOf().also {
                    it[0] = ambient.value.toByte()
                    // The Liberty 4 Pro's slider goes with its mode, from the widget and the notification too.
                    if (model == "A3954" && it.size >= 2) it[1] = a3954Slider(it.u8(0), it.u8(1)).toByte()
                    // The P30i and P31i families' blocks go back as the official app writes them (its M0 of both families).
                    if (hasModesBlock(l.features)) normalizeModes(it, l.features)
                }
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
            val find = findRows()
            val switches = SWITCHES.mapNotNull { switchRow(s, it) } + listOfNotNull(pressRow(s), maxVolumeRow(s))
            val rows = listOfNotNull(sleepTimerRow(s)) + A30_FLAGS.filter { it.home.not() }.mapNotNull { flagRow(s, it) } +
                listOfNotNull(listeningRow(s, A30_LISTENING_MODE), listeningRow(s, A30_DEFAULT_LISTENING_MODE))
            val home = A30_FLAGS.filter { it.home }.mapNotNull { flagRow(s, it) } + listOfNotNull(listeningRow(s, A30_LISTENING_MODE))
            return listOfNotNull(
                if (switches.isEmpty()) null else SettingGroup("sc_device", SettingTab.DEVICE, switches),
                if (home.isEmpty()) null else SettingGroup("sleep_sound", SettingTab.HOME, home),
                if (rows.filterNot { it in home }.isEmpty()) null else SettingGroup("sleep", SettingTab.DEVICE, rows.filterNot { it in home }),
                (a3954SoundRows(s) ?: a3959SoundRows(s))?.takeIf { it.isNotEmpty() }?.let { SettingGroup("noise", SettingTab.HOME, it) },
                a3959FeatureRows(s)?.takeIf { it.isNotEmpty() }?.let { SettingGroup("other", SettingTab.DEVICE, it) },
                a3954FeatureRows(s)?.takeIf { it.isNotEmpty() }?.let { SettingGroup("other", SettingTab.DEVICE, it) },
                if (find.isEmpty()) null else SettingGroup("find", SettingTab.DEVICE, find),
            )
        }

    // Find my earbuds: whether it is on, and the earbud left out of it. The earbuds beep on 10 89, or on some models the phone plays a sound through them.
    private var finding = false
    private var mutedLeft = false
    private var mutedRight = false
    private val beeps: Boolean get() = layout?.features?.contains("find_device") == true
    private val phoneBeeps: Boolean get() = layout?.features?.contains("find_device_phone") == true

    private fun findRows(): List<Setting> {
        if (!beeps && !phoneBeeps) return emptyList()
        val start = Setting.Action(if (beeps) FIND else FIND_PHONE, finding)
        return listOf(start) + if (finding) listOf(Setting.Toggle(MUTE_LEFT, mutedLeft), Setting.Toggle(MUTE_RIGHT, mutedRight)) else emptyList()
    }

    /** The write for what the earbuds should be doing now, `10 89`: left, right, case (0 0 0 stops). Nothing to send where the phone makes the sound. */
    private fun findPackets(): List<SoundcorePacket> =
        if (!beeps) emptyList()
        else listOf(SoundcorePacket(SoundcoreCommand.FIND_DEVICE, byteArrayOf(if (finding && !mutedLeft) 1 else 0, if (finding && !mutedRight) 1 else 0, 0)))

    private fun findChange(change: SettingChange): List<SoundcorePacket>? {
        if ((!beeps && !phoneBeeps) || lastState == null) return null
        when {
            change is SettingChange.Press && change.key == (if (beeps) FIND else FIND_PHONE) -> {
                finding = !finding
                if (!finding) {
                    mutedLeft = false
                    mutedRight = false
                }
            }
            change is SettingChange.Toggle && change.key == MUTE_LEFT -> mutedLeft = change.on
            change is SettingChange.Toggle && change.key == MUTE_RIGHT -> mutedRight = change.on
            else -> return null
        }
        return findPackets()
    }

    /**
     * The Liberty 4 Pro's noise control (OpenSCQ30's a3954 module, checked against the 5.0.35 app's reply parser and its sound modes write, which
     * send the same five bytes): the mode, a slider from 1 to 11 with 6 the middle, airplane mode's update setting, and wind noise suppression.
     * The slider runs as a Liberty 4 Pro takes it (Onlineraider's pair, pull request 42 and issue #50): 1 to 5 is transparency and 7 to 11 noise
     * cancelling, the other way round from OpenSCQ30's reading (docs/UPSTREAM.md). 0.12.1 followed OpenSCQ30, so the earbuds moved each slider
     * PairFree sent back to its mode's own side and the level row vanished. A level row now shows whenever its mode is on, whatever the slider
     * holds. Which end of each side is the strongest is not confirmed: PairFree takes the slider's two ends (1 and 11) as the strongest.
     */
    /** The Liberty 4 Pro's slider for [mode]: [slider] where it is on that mode's side, else the step next to the middle on that side. */
    private fun a3954Slider(mode: Int, slider: Int): Int = when (mode) {
        0 -> if (slider in 7..11) slider else 7
        1 -> if (slider in 1..5) slider else 5
        2 -> 6
        else -> slider
    }

    private fun a3954SoundRows(s: SoundcoreState): List<Setting>? {
        if (model != "A3954") return null
        val b = s.field("sound_modes") ?: return null
        val mode = b.u8(0)
        val slider = b.u8(1)
        val modeOption = when (mode) { 0 -> "anc"; 1 -> "transparency"; 2 -> "normal"; 3 -> "airplane"; else -> null }
        return listOfNotNull(
            Setting.Choice("a3954_ambient_mode", modeOption, listOf("transparency", "normal", "anc", "airplane")),
            if (mode == 0) Setting.Level("a3954_anc_strength", (slider - 6).coerceIn(1, 5), 1, 5) else null,
            if (mode == 1) Setting.Level("a3954_transparency_strength", (6 - slider).coerceIn(1, 5), 1, 5) else null,
            if (mode == 3) Setting.Choice("a3954_airplane_mode", listOf("manual", "automatic").getOrNull(b.u8(2)), listOf("manual", "automatic")) else null,
            if (mode == 3) s.field("air_pressure")?.let { Setting.Information("a3954_air_pressure", "${it.u8(0) / 100}.${(it.u8(0) % 100).toString().padStart(2, '0')} atm") } else null,
            Setting.Toggle("a3954_wind_noise", b.u8(3) and 1 != 0),
        )
    }

    /**
     * The P30i family's noise control (OpenSCQ30's a3959 module and the recording of a P30i / R50i NC from the official app, issue #45): the sound mode
     * block's seven bytes are the mode, manual strength (high nibble) and adaptive level (low nibble), a second mode byte the app leaves 0, the noise
     * cancelling kind (manual, adaptive, multi-scene), wind noise (bit 0 suppression, bit 1 detected), the adaptive sensitivity (0 to 10) and the
     * multi-scene setting (transport, outdoor, indoor).
     */
    private fun a3959SoundRows(s: SoundcoreState): List<Setting>? {
        if (!hasModesBlock(s.layout.features)) return null
        val b = s.field("sound_modes")?.takeIf { it.size >= 7 } ?: return null
        val ambient = b.u8(0)
        val kind = b.u8(3)
        val anc = ambient == 0
        return listOfNotNull(
            if (anc) Setting.Choice("a3959_nc_mode", A3959_KINDS.getOrNull(kind), A3959_KINDS) else null,
            if (anc && kind == 0) Setting.Level("a3959_manual_strength", (b.u8(1) shr 4).coerceIn(1, 5), 1, 5) else null,
            if (anc && kind == 1) Setting.Information("a3959_adaptive_level", "${(b.u8(1) and 15).coerceIn(0, 5)}/5") else null,
            if (anc && kind == 2) Setting.Choice("a3959_scene", A3959_SCENES.getOrNull(b.u8(6)), A3959_SCENES) else null,
            if (ambient <= 1) Setting.Toggle("a3959_wind_noise", b.u8(4) and 1 != 0) else null,
        )
    }

    /** The write for a change to one of [a3959SoundRows]: the seven bytes as the earbuds reported them with the one field changed. */
    private fun a3959Change(s: SoundcoreState, change: SettingChange): List<SoundcorePacket>? {
        if (!hasModesBlock(s.layout.features) || !change.key.startsWith("a3959_")) return null
        val bytes = s.field("sound_modes")?.takeIf { it.size >= 7 } ?: return null
        when (change) {
            is SettingChange.Choice -> when (change.key) {
                "a3959_nc_mode" -> bytes[3] = A3959_KINDS.indexOf(change.option).takeIf { it >= 0 }?.toByte() ?: return null
                "a3959_scene" -> bytes[6] = A3959_SCENES.indexOf(change.option).takeIf { it >= 0 }?.toByte() ?: return null
                else -> return null
            }
            is SettingChange.Level -> when (change.key) {
                "a3959_manual_strength" -> bytes[1] = ((change.value.coerceIn(1, 5) shl 4) or (bytes.u8(1) and 15)).toByte()
                else -> return null
            }
            is SettingChange.Toggle -> if (change.key == "a3959_wind_noise") bytes[4] = (if (change.on) 1 else 0).toByte() else return null
            else -> return null
        }
        normalizeModes(bytes, s.layout.features)
        lastState = s.with("sound_modes", bytes)
        soundModes = bytes.copyOf()
        return listOf(SoundcorePacket(SoundcoreCommand.SET_SOUND_MODES, bytes))
    }

    /** Whether the model's sound mode block is the P30i family's (seven bytes) or the P31i family's (eight), the two the official app's ANC page drives alike. */
    private fun hasModesBlock(features: Set<String>) = A3959_MODES in features || D1202_MODES in features

    /**
     * The bytes the official app writes back (docs/soundcore/PROTOCOL.md, section 14; `A3958CmdService.M0`, `D1202CmdService.M0` of Soundcore 5.0.35):
     * the wind byte holds only the suppression switch (bit 0; bit 1, "wind detected", is the earbuds' and the app never sends it); the P30i family's
     * byte 2 is the transparency option (0 to 2) and its byte 5 the environment detection switch (1 or 0), so an unset 0xFF goes back as 0; the P31i
     * family's byte 2 is the side tone switch (1 or 0), and its sensitivity and real-time adaptive bytes (5 and 7) go back as the earbuds reported them.
     * The "adaptive sensitivity 0 to 10" slider OpenSCQ30 offers is in neither family's screens in the official app, which only carries the byte.
     */
    private fun normalizeModes(bytes: ByteArray, features: Set<String>) {
        bytes[4] = (bytes.u8(4) and 1).toByte()
        if (A3959_MODES in features) {
            bytes[2] = bytes.u8(2).coerceIn(0, 2).toByte()
            bytes[5] = (if (bytes.u8(5) == 1) 1 else 0).toByte()
        } else {
            bytes[2] = (if (bytes.u8(2) == 1) 1 else 0).toByte()
        }
    }

    /** The P30i family's power-off durations (10, 20, 30 or 60 minutes, as the recording of the official app shows). */
    private fun a3959FeatureRows(s: SoundcoreState): List<Setting>? {
        if (A3959_MODES !in s.layout.features) return null
        return listOfNotNull(
            s.field("auto_power_off")?.takeIf { it.size >= 2 }?.let {
                val options = listOf("off", "10", "20", "30", "60")
                Setting.Choice("a3954_auto_power_off_duration", if (it.u8(0) == 0) "off" else options.getOrNull(it.u8(1) + 1), options)
            },
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
            s.field("limit_high_volume")?.let { Setting.Level("a3954_volume_limit_db", it.u8(1).coerceIn(75, 100) / 5 * 5, 75, 100) },
        )
    }

    /**
     * One of the earbuds' own switches ([SWITCHES]: wear detection, side tone, prompts and the rest, as the Soundcore app's code names them), where the
     * model's state has its field. A switch of more than one byte keeps the other bytes as the earbuds reported them.
     */
    private fun switchRow(s: SoundcoreState, w: Switch): Setting? =
        // The P30i family shows its power-off durations instead of the bare switch.
        if (w.key == "sc_auto_power_off" && A3959_MODES in s.layout.features) null
        else if (s.layout.has(w.field) && s.flag(w.field) != null) Setting.Toggle(w.key, s.flag(w.field) == true) else null

    /** The A3953's press sensitivity: five levels from softest (0) to firmest (4), set with both earbuds connected (its screen). */
    private fun pressRow(s: SoundcoreState): Setting? {
        val v = s.field("press_sensitivity")?.takeIf { it.isNotEmpty() }?.u8(0) ?: return null
        return Setting.Choice(PRESS_SENSITIVITY, PRESS_LEVELS.getOrNull(v), PRESS_LEVELS)
    }

    /** The Life NC's volume limit: 0 to 16 steps. */
    private fun maxVolumeRow(s: SoundcoreState): Setting? =
        s.field("max_volume")?.firstOrNull()?.let { Setting.Level(MAX_VOLUME, (it.toInt() and 0xFF).coerceIn(0, MAX_VOLUME_STEPS), 0, MAX_VOLUME_STEPS) }

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
        if (change.key == FIND || change.key == FIND_PHONE || change.key == MUTE_LEFT || change.key == MUTE_RIGHT) return findChange(change)
        val s = lastState ?: return null
        a3959Change(s, change)?.let { return it }
        if (change is SettingChange.Toggle) {
            if (model == "A3954" && change.key.startsWith("a3954_")) {
                val f = when (change.key) {
                    "a3954_easy_chat" -> "easy_chat_enabled" to 0x109D
                    "a3954_wind_noise" -> "sound_modes" to SoundcoreCommand.SET_SOUND_MODES
                    "a3954_case_atmospheric" -> "case_features" to 0x0787
                    "a3954_case_camera" -> "case_features" to 0x0787
                    "a3954_case_find" -> "case_features" to 0x0787
                    "a3954_case_spatial" -> "case_features" to 0x0787
                    "a3954_volume_limit" -> "limit_high_volume" to 0x2082
                    else -> null
                } ?: return null
                val bytes = s.field(f.first) ?: return null
                when (change.key) {
                    "a3954_wind_noise" -> bytes[3] = (if (change.on) 1 else 0).toByte() // the detection bit is the earbuds' own
                    "a3954_case_atmospheric", "a3954_case_camera", "a3954_case_find", "a3954_case_spatial" -> {
                        val i = listOf("a3954_case_atmospheric", "a3954_case_camera", "a3954_case_find", "a3954_case_spatial").indexOf(change.key)
                        bytes[i] = (if (change.on) 1 else 0).toByte()
                    }
                    else -> bytes[0] = (if (change.on) 1 else 0).toByte()
                }
                lastState = s.with(f.first, bytes)
                val body = if (change.key == "a3954_easy_chat") {
                    byteArrayOf(bytes[0], s.field("easy_chat_wait_time")?.get(0) ?: 0)
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
            if (model == "A3954" && change.key.startsWith("a3954_") && change.key != "a3954_auto_power_off_duration") {
                val f = when (change.key) {
                    "a3954_ambient_mode" -> Quad("sound_modes", SoundcoreCommand.SET_SOUND_MODES, 0, listOf("anc", "transparency", "normal", "airplane"))
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
                    // A mode goes with a slider on its own side of the middle; airplane mode keeps the slider.
                    bytes[1] = a3954Slider(bytes.u8(0), bytes.u8(1)).toByte()
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
        if (change is SettingChange.Level && change.key == MAX_VOLUME) {
            if (maxVolumeRow(s) == null) return null
            val body = byteArrayOf(change.value.coerceIn(0, MAX_VOLUME_STEPS).toByte())
            lastState = s.with("max_volume", body)
            return listOf(SoundcorePacket(SoundcoreCommand.MAX_VOLUME, body))
        }
        if (change is SettingChange.Level && model == "A3954") {
            if (change.key == "a3954_volume_limit_db") {
                val bytes = s.field("limit_high_volume") ?: return null
                // Three bytes, as the app writes them: the switch, the limit in dB (steps of 5) and the third byte (the reading refresh) as reported.
                bytes[1] = (change.value.coerceIn(75, 100) / 5 * 5).toByte()
                lastState = s.with("limit_high_volume", bytes)
                return listOf(SoundcorePacket(0x2082, bytes))
            }
            val bytes = s.field("sound_modes") ?: return null
            val v = change.value.coerceIn(1, 5)
            val slider = when {
                change.key == "a3954_anc_strength" && bytes.u8(0) == 0 -> 6 + v
                change.key == "a3954_transparency_strength" && bytes.u8(0) == 1 -> 6 - v
                else -> return null
            }
            bytes[1] = slider.toByte()
            lastState = s.with("sound_modes", bytes)
            soundModes = bytes.copyOf()
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
    public fun requestState(): ByteArray = SoundcorePacket.build(layout?.stateCommand ?: SoundcoreCommand.STATE, checksum = checksum)

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
        public val WEAR_FIT_MODELS: Set<String> = setOf("A3931", "A3935", "A3951", "A3959")

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
        private const val A3959_MODES = "a3959_sound_modes"
        private const val D1202_MODES = "d1202_sound_modes"
        private val A3959_KINDS = listOf("manual", "adaptive", "multi_scene")
        private val A3959_SCENES = listOf("transport", "outdoor", "indoor")
        private const val MAX_VOLUME = "sc_max_volume"
        private const val MAX_VOLUME_STEPS = 16
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
            Switch("sc_surround_sound", "surround_sound", SoundcoreCommand.SURROUND_SOUND),
            Switch("sc_vibration", "vibration", SoundcoreCommand.VIBRATION),
            Switch("sc_max_volume_switch", "max_volume_switch", SoundcoreCommand.MAX_VOLUME_SWITCH),
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

        public const val FIND: String = "find"
        public const val FIND_PHONE: String = "find_phone"
        private const val MUTE_LEFT = "find_mute_left"
        private const val MUTE_RIGHT = "find_mute_right"
        private val EARBUD_BATTERIES = listOf("dual_battery", "dual_battery_level", "single_battery", "single_battery_charging_first", "battery_level")

        // The noise modes by their names in data/soundpeats/commands.json, so no number is retyped.
        private fun ancNumber(name: String): Int = Command.ANC_MODE.labels.entries.first { it.value == name }.key

        private fun ancValue(mode: AmbientMode?): Int? = when (mode) {
            AmbientMode.NORMAL -> ancNumber("off")
            AmbientMode.NOISE_CANCELLING -> ancNumber("anc")
            AmbientMode.TRANSPARENCY -> ancNumber("transparency")
            AmbientMode.ADAPTIVE, null -> null
        }

        private fun ambientFor(value: Int): AmbientMode? = when (value) {
            ancNumber("off") -> AmbientMode.NORMAL
            ancNumber("anc") -> AmbientMode.NOISE_CANCELLING
            ancNumber("transparency") -> AmbientMode.TRANSPARENCY
            else -> null
        }
    }
}
