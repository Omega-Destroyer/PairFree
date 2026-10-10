package io.github.omegadestroyer.soundpeter.protocol

/** Builds the frames the app sends. */
public object Requests {
    /**
     * The GETs sent on connect: the same list as tools/probe.py, all read-only and tried
     * on an H3. A test keeps the two in step.
     */
    public val ON_CONNECT: List<Command> = listOf(
        Command.FIRMWARE_VERSION, Command.BATTERY_LEFT, Command.BATTERY_RIGHT, Command.BATTERY_CASE,
        Command.ANC_MODE, Command.ANC_LEVEL, Command.TRANSPARENCY_LEVEL, Command.ANC_ENV_ADAPTIVE,
        Command.GAME_MODE, Command.MULTIPOINT, Command.LDAC, Command.LE_AUDIO, Command.IN_EAR_DETECTION,
        Command.TOUCH_DISABLED, Command.CUSTOM_KEYS, Command.PROMPT_VOLUME, Command.AUTO_SHUTDOWN,
        Command.VOICE_LANGUAGE, Command.DYNAMIC_EQ, Command.DYNAMIC_BASS, Command.MOVIE_MODE,
        Command.SPATIAL_AUDIO, Command.PANORAMIC_SOUND, Command.DOLBY, Command.ADAPTIVE_VOLUME,
        Command.PERSONAL_VOLUME, Command.LIGHT_EFFECT, Command.PRIVACY_MODE, Command.RESET_STATE,
    )

    public val BATTERY: List<Command> = listOf(Command.BATTERY_LEFT, Command.BATTERY_CASE, Command.BATTERY_RIGHT)

    /** Commands that must never go out without the user confirming in that moment (CLAUDE.md). */
    private val NEEDS_CONFIRMATION: Set<Command> = setOf(Command.FACTORY_RESET, Command.REBOOT)

    public fun get(command: Command): ByteArray =
        Frame.build(requireNotNull(command.get) { "${command.id} has no GET" })

    /** A SET with the given payload bytes (each 0 to 255). Refuses factory reset and reboot. */
    public fun set(command: Command, vararg payload: Int): ByteArray {
        require(command !in NEEDS_CONFIRMATION) { "${command.id} needs an explicit user confirmation" }
        require(payload.all { it in 0..0xFF }) { "payload bytes must be 0 to 255" }
        return Frame.build(
            requireNotNull(command.set) { "${command.id} has no SET" },
            ByteArray(payload.size) { payload[it].toByte() },
        )
    }
}
