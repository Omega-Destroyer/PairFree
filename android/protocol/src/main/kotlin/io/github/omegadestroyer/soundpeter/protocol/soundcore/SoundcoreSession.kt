package io.github.omegadestroyer.soundpeter.protocol.soundcore

import io.github.omegadestroyer.soundpeter.protocol.Command
import io.github.omegadestroyer.soundpeter.protocol.EarbudState
import io.github.omegadestroyer.soundpeter.protocol.Value

/**
 * One connection to Soundcore earbuds, shown on SoundPeter's screens. The screens work in the
 * SoundPEATS command table ([Command], [EarbudState]), so the earbuds' state becomes
 * [EarbudState] values and a setting from the screens becomes a Soundcore packet. Only the
 * settings both brands share are mapped: battery, noise control, game mode, LDAC and two
 * devices at once. Today only the Space A40 ([SUPPORTED_MODELS]) has a known state layout.
 */
public class SoundcoreSession(public val model: String) {
    /** What the screens show. */
    public var state: EarbudState = EarbudState()
        private set

    /** The last full state the earbuds sent, for the device report. */
    public var lastState: A3936State? = null
        private set

    // The sound modes block as the earbuds last reported it; a noise mode change sends it back with byte 0 changed.
    private var soundModes: ByteArray? = null

    /**
     * Applies one packet from the earbuds. Returns true when [state] changed. Acknowledgements
     * (empty bodies) and packets SoundPeter does not use change nothing.
     */
    public fun receive(packet: SoundcorePacket): Boolean {
        val body = packet.body
        if (body.isEmpty()) return false
        val before = state
        when (packet.command) {
            SoundcoreCommand.STATE -> {
                val s = (if (model == A3936) A3936State.parse(body) else null) ?: return false
                lastState = s
                soundModes = s.soundModes
                state = state.copy(
                    values = state.values + mapOf(
                        Command.FIRMWARE_VERSION to Value.Text("${model}_${s.firmwareLeft}"),
                        Command.BATTERY_LEFT to Value.Number(levelPercent(s.batteryLeft)),
                        Command.BATTERY_RIGHT to Value.Number(levelPercent(s.batteryRight)),
                        Command.BATTERY_CASE to Value.Number(s.caseBattery.takeIf { it <= 100 }),
                        Command.ANC_MODE to Value.Number(ancValue(s.ambientMode)),
                        Command.GAME_MODE to Value.Switch(s.gamingMode),
                        Command.LDAC to Value.Switch(s.ldac),
                        Command.MULTIPOINT to Value.Switch(s.multipoint),
                    ),
                )
            }
            SoundcoreCommand.BATTERY -> if (body.size >= 3) {
                state = state.copy(
                    values = state.values + mapOf(
                        Command.BATTERY_LEFT to Value.Number(levelPercent(body.u8(0))),
                        Command.BATTERY_RIGHT to Value.Number(levelPercent(body.u8(1))),
                        Command.BATTERY_CASE to Value.Number(body.u8(2).takeIf { it <= 100 }),
                    ),
                )
            }
            SoundcoreCommand.SOUND_MODES -> if (body.size == soundModes?.size) {
                soundModes = body.copyOf()
                put(Command.ANC_MODE, Value.Number(ancValue(AmbientMode.of(body.u8(0)))))
            }
            SoundcoreCommand.GAMING_MODE_UPDATE -> put(Command.GAME_MODE, Value.Switch(body.u8(0) != 0))
            SoundcoreCommand.LDAC_STATE -> put(Command.LDAC, Value.Switch(body.u8(0) != 0))
        }
        return state != before
    }

    /**
     * The packet for a setting from the screens, or null when these earbuds cannot take it.
     * The change shows in [state] at once; the next state the earbuds send confirms it.
     */
    public fun set(command: Command, value: Int): ByteArray? {
        val packet = when (command) {
            Command.ANC_MODE -> {
                val modes = soundModes ?: return null
                val ambient = ambientFor(value) ?: return null
                val changed = modes.copyOf().also { it[0] = ambient.value.toByte() }
                soundModes = changed
                SoundcorePacket.build(SoundcoreCommand.SET_SOUND_MODES, changed)
            }
            Command.GAME_MODE -> flag(SoundcoreCommand.GAMING_MODE, value)
            Command.LDAC -> flag(SoundcoreCommand.LDAC, value)
            Command.MULTIPOINT -> flag(SoundcoreCommand.DUAL_CONNECTIONS_ENABLED, value)
            else -> return null
        }
        state = state.expect(command, value)
        return packet
    }

    /** The request for the full state, sent on connecting and now and then after. */
    public fun requestState(): ByteArray = SoundcorePacket.build(SoundcoreCommand.STATE)

    private fun flag(command: Int, value: Int): ByteArray = SoundcorePacket.build(command, byteArrayOf(if (value != 0) 1 else 0))

    private fun put(command: Command, value: Value) {
        state = state.copy(values = state.values + (command to value))
    }

    private fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF

    public companion object {
        public const val A3936: String = "A3936"

        /** Models whose state SoundPeter can read. */
        public val SUPPORTED_MODELS: Set<String> = setOf(A3936)

        /** The A40 reports each earbud in fifths; the screens show percent. */
        private fun levelPercent(level: Int): Int? = if (level in 0..5) level * 20 else null

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
