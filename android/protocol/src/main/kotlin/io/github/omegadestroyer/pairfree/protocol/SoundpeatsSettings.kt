package io.github.omegadestroyer.pairfree.protocol

/**
 * SOUNDPEATS settings the official app has (data/soundpeats/commands.json) that the screens draw as brand rows rather
 * than with controls of their own: the sound effects, the panoramic sound mode, the volume balance and limit, adaptive
 * noise cancelling, privacy mode, fall detection and the light. Like every SOUNDPEATS feature, each shows once its GET
 * has answered (the connect reads ask for all of them). A setting the model offers only with LDAC off
 * ([blockedByLdac]) is shown off and not changeable while LDAC is on, as on the Device tab.
 */
public object SoundpeatsSettings {
    private val SOUND_SWITCHES = listOf(
        Command.DYNAMIC_EQ, Command.DYNAMIC_BASS, Command.MOVIE_MODE, Command.SPATIAL_AUDIO, Command.DOLBY,
        Command.ADAPTIVE_VOLUME, Command.PERSONAL_VOLUME,
    )
    private val DEVICE_SWITCHES = listOf(Command.PRIVACY_MODE, Command.FALL_DETECTION, Command.LIGHT_EFFECT)

    /** The volume limit's range: SOUNDPEATS 2.5.3 accepts replies from 50 to 100. */
    private const val LIMIT_MIN = 50
    private const val PERCENT_MAX = 100

    private val ALL: Set<Command> = (
        SOUND_SWITCHES + DEVICE_SWITCHES +
            listOf(Command.PANORAMIC_SOUND, Command.VOLUME_BALANCE, Command.HEARING_PROTECTION, Command.ANC_ENV_ADAPTIVE)
        ).toSet()

    /** A row's key: the command's id, marked as SOUNDPEATS' so it never meets another brand's row of the same name. */
    public fun key(command: Command): String = "sp_" + command.id

    /** The rows for what the earbuds have answered, grouped by tab. */
    public fun groups(state: EarbudState, model: Model?): List<SettingGroup> = buildList {
        val sound = buildList {
            for (c in SOUND_SWITCHES) {
                val on = state.switch(c) ?: continue
                add(Setting.Toggle(key(c), on, enabled = on || !state.blockedByLdac(c, model)))
            }
            state.number(Command.PANORAMIC_SOUND)?.let { v ->
                val labels = Command.PANORAMIC_SOUND.labels
                add(Setting.Choice(key(Command.PANORAMIC_SOUND), labels[v], labels.toSortedMap().values.toList()))
            }
            state.number(Command.VOLUME_BALANCE)?.let { add(Setting.Level(key(Command.VOLUME_BALANCE), it.coerceIn(0, PERCENT_MAX), 0, PERCENT_MAX)) }
            state.number(Command.HEARING_PROTECTION)?.let {
                add(Setting.Level(key(Command.HEARING_PROTECTION), it.coerceIn(LIMIT_MIN, PERCENT_MAX), LIMIT_MIN, PERCENT_MAX))
            }
        }
        if (sound.isNotEmpty()) add(SettingGroup("sound_effects", SettingTab.SOUND, sound))
        state.switch(Command.ANC_ENV_ADAPTIVE)?.let {
            add(SettingGroup("noise_adaptive", SettingTab.HOME, listOf(Setting.Toggle(key(Command.ANC_ENV_ADAPTIVE), it))))
        }
        val device = DEVICE_SWITCHES.mapNotNull { c -> state.switch(c)?.let { Setting.Toggle(key(c), it) } }
        if (device.isNotEmpty()) add(SettingGroup("other", SettingTab.DEVICE, device))
    }

    /** The command and value a change to one of [groups]' rows sends, or null when the change is not one of them. */
    public fun change(change: SettingChange): Pair<Command, Int>? {
        val command = ALL.firstOrNull { key(it) == change.key } ?: return null
        return when (change) {
            is SettingChange.Toggle -> command to if (change.on) 1 else 0
            is SettingChange.Choice -> command.labels.entries.firstOrNull { it.value == change.option }?.let { command to it.key }
            is SettingChange.Level -> command to change.value
            is SettingChange.Press -> null
        }
    }
}
