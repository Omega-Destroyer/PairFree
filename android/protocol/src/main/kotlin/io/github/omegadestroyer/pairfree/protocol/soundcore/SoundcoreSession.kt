package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.BatteryLevel
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
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
public class SoundcoreSession(public val model: String) {
    public val layout: SoundcoreLayout? = SoundcoreLayout.orReportOnly(model)
    private val checksum: Boolean = layout?.checksum ?: true

    /** What the screens show. */
    public var state: EarbudState = EarbudState()
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
            SoundcoreCommand.SOUND_MODES -> if (body.size == soundModes?.size) {
                soundModes = body.copyOf()
                put(Command.ANC_MODE, Value.Number(ancValue(AmbientMode.of(body.u8(0)))))
            }
            SoundcoreCommand.GAMING_MODE_UPDATE -> put(Command.GAME_MODE, Value.Switch(body.u8(0) != 0))
            SoundcoreCommand.LDAC_STATE -> put(Command.LDAC, Value.Switch(body.u8(0) != 0))
            // Whether both earbuds are in decides which touch actions apply (SoundcoreButtons).
            SoundcoreCommand.TWS_STATUS -> lastState?.let { s -> lastState = s.with("tws_status", body) }
        }
        return state != before
    }

    /** The Controls tab's settings from the last state, or null before the first state. */
    public val touch: SoundcoreTouch? get() = lastState?.let(SoundcoreTouch::of)

    /** Shows a change from [SoundcoreTouch] at once; the next state the earbuds send confirms it. */
    public fun expect(change: SoundcoreButtons.Change) {
        if (change.state.layout === layout) lastState = change.state
    }

    /** [packets] as frames for these earbuds, with or without the checksum as the model needs. */
    public fun frames(packets: List<SoundcorePacket>): List<ByteArray> =
        packets.filter { it.command !in SoundcoreCommand.NEVER_SEND }.map { SoundcorePacket.build(it.command, it.body, checksum) }

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
                SoundcorePacket.build(SoundcoreCommand.SET_SOUND_MODES, changed, checksum)
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
            val rows = listOfNotNull(sleepTimerRow(s))
            return if (rows.isEmpty()) emptyList() else listOf(SettingGroup("sleep", SettingTab.DEVICE, rows))
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

    // A battery in the model's own steps (fifths on most), so the screens can show bars as the Soundcore app does.
    private fun steps(reading: BatteryLevel?): Value = Value.Steps(reading?.level, reading?.levels ?: BatteryLevel.PERCENT)

    public companion object {
        public const val A3936: String = "A3936"

        /** Models whose state PairFree can read: every model with a layout in data/soundcore/devices.json. */
        public val SUPPORTED_MODELS: Set<String> get() = SoundcoreLayout.ALL.map { it.model }.toSet()

        // The Sleep A20's sleep timer (state bytes 67-70, 15 85): 1 on or 2 off, the minutes, a byte kept as it is.
        // The choices: the 30 minutes a recording shows, then OpenSCQ30's note of 1 to 8 hours in half hours.
        private const val SLEEP_TIMER = "sleep_timer"
        private const val SLEEP_TIMER_ON = 1
        private const val SLEEP_TIMER_OFF = 2
        private const val OFF = "off"
        private val SLEEP_TIMER_MINUTES = (30..480 step 30).toList()

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
