package io.github.omegadestroyer.pairfree.protocol

/**
 * Every control the screens would show must write something when used (issue #76: the Soundcore LDAC switch showed and did nothing). Each brand's tests build a session the
 * way their other tests do and pass it here: every shared switch and noise mode the state holds, and every row, is used once on a fresh session.
 *
 * Returns what was shown and wrote nothing. [skip] names row keys that rightly write nothing (a sound the phone itself plays, a link the screen opens).
 */
internal fun <S : Any> deadControls(
    label: String,
    fresh: () -> S,
    state: (S) -> EarbudState,
    groups: (S) -> List<SettingGroup>,
    set: (S, Command, Int) -> Any?,
    change: (S, SettingChange) -> Any?,
    skip: Set<String> = emptySet(),
): List<String> {
    fun nothing(out: Any?) = out == null || (out is List<*> && out.isEmpty())
    val first = fresh()
    val dead = mutableListOf<String>()
    val shown = state(first)
    for (command in listOf(Command.ANC_MODE, Command.GAME_MODE, Command.MULTIPOINT, Command.IN_EAR_DETECTION, Command.LDAC)) {
        if (!shown.supports(command)) continue
        val value = if (command == Command.ANC_MODE) shown.number(command) ?: continue else if (shown.switch(command) == true) 0 else 1
        if (nothing(set(fresh(), command, value))) dead += "$label $command"
    }
    for (group in groups(first)) for (row in group.settings) {
        if (row.key in skip) continue
        val c = when (row) {
            is Setting.Toggle -> if (row.enabled) SettingChange.Toggle(row.key, !row.on) else null
            is Setting.Choice -> row.options.firstOrNull { it != row.value }?.let { SettingChange.Choice(row.key, it) }
            is Setting.Level -> if (row.enabled && row.max > row.min) SettingChange.Level(row.key, if (row.value >= row.max) row.min else row.value + 1) else null
            is Setting.Action -> SettingChange.Press(row.key)
            else -> null
        } ?: continue
        if (nothing(change(fresh(), c))) dead += "$label ${row.key}"
    }
    return dead
}
