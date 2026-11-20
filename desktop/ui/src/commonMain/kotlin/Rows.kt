package io.github.omegadestroyer.pairfree.ui

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.shell.DesktopStrings
import io.github.omegadestroyer.pairfree.shell.SettingWords

/** What the rows keep between frames: the row opened for choosing, and the level being dragged. */
internal class RowState {
    var expanded: String? = null
    val pending = HashMap<String, Int>()

    /** How to send a level that is being dragged, by the row's key. */
    val senders = HashMap<String, (Int) -> Unit>()
}

/** A row showing a value that opens, when pressed, the choices under it: a gesture's action, a prompt language, a timer. */
internal fun choiceRow(
    state: RowState,
    key: String,
    title: Widget,
    value: String,
    names: List<String>,
    selected: Int?,
    theme: Theme,
    changed: () -> Unit,
    onPick: (Int) -> Unit,
): Widget {
    val open = state.expanded == key
    val head = Tappable(Split(title, Label(value, theme.body, theme.accent, Align.END), 160f), "row_$key") {
        state.expanded = if (open) null else key
        changed()
    }
    if (!open) return head
    val chooser = segmented("opt_${key}_", names, selected, primary = false, perRow = 3) {
        state.expanded = null
        onPick(it)
        changed()
    }
    return Column(listOf(head, chooser), 6f)
}

/** A whole row with a switch at its end. */
internal fun switchRow(key: String, title: Widget, on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit): Widget =
    Tappable(Split(title, SwitchMark(on, enabled), 44f), "row_$key", enabled) { onChange(!on) }

/** What the rows ask the app for. */
internal class RowCallbacks(
    val change: (SettingChange) -> Unit,
    val confirm: (title: String, body: String, confirm: String, onConfirm: () -> Unit) -> Unit,
    val askText: (title: String, hint: String, maxLength: Int, confirm: String, onDone: (String) -> Unit) -> Unit,
    val openUrl: (String) -> Unit,
    val changed: () -> Unit,
)

/**
 * A brand's own settings for one tab ([SettingGroup]s): one panel per group, with switches, choices, levels, buttons, text and readings.
 * The words come from [SettingWords], the Android screens' own mapping.
 */
internal class SettingRows(
    private val groups: List<SettingGroup>,
    private val tab: SettingTab,
    private val words: SettingWords,
    private val s: DesktopStrings,
    private val theme: Theme,
    private val state: RowState,
    private val call: RowCallbacks,
) {
    private val confirmKeys = setOf("restart", "power_off", "sh_personalized_reset")
    private val confirmChoices = setOf("sh_peer_delete")
    private val links = mapOf("find_hub" to "https://www.google.com/android/find", "sk_tile" to "https://www.tile.com/")

    fun panels(): List<Widget> {
        val mine = groups.filter { it.tab == tab }
        val out = mutableListOf<Widget>()
        var i = 0
        while (i < mine.size) {
            val group = mine[i]
            val next = mine.getOrNull(i + 1)
            // A group for the left earbud followed by the right one's sits beside it on a wide window.
            if (next != null && group.key.endsWith("_left") && next.key == group.key.removeSuffix("_left") + "_right") {
                out += Beside(panel(group), panel(next))
                i += 2
            } else {
                out += panel(group)
                i++
            }
        }
        return out
    }

    private fun panel(group: SettingGroup): Widget {
        val rows = mutableListOf<Widget>(Label(words.groupTitle(group.key), theme.labelFont, theme.label, caps = true))
        group.settings.forEachIndexed { i, setting ->
            if (i > 0) rows += Divider()
            rows += row(setting)
        }
        words.groupNote(group.key)?.let { rows += Label(it, theme.small, theme.secondaryText) }
        return Panel(Column(rows, 8f))
    }

    private fun titleWithNote(key: String, color: Int? = null): Widget {
        val note = words.settingNote(key)
        val title = Label(words.settingTitle(key), theme.body, color)
        return if (note == null) title else Column(listOf(title, Label(note, theme.small, theme.secondaryText)), 2f)
    }

    private fun row(setting: Setting): Widget = when (setting) {
        is Setting.Toggle -> Tappable(
            Split(titleWithNote(setting.key, if (setting.enabled) null else theme.secondaryText), SwitchMark(setting.on, setting.enabled), 44f),
            "row_${setting.key}", setting.enabled,
        ) { call.change(SettingChange.Toggle(setting.key, !setting.on)) }
        is Setting.Choice -> choice(setting)
        is Setting.Text -> Tappable(
            Split(titleWithNote(setting.key), Label(setting.value.ifEmpty { s.text("unknown") }, theme.body, theme.accent, Align.END), 140f),
            "row_${setting.key}",
        ) { call.askText(words.settingTitle(setting.key), words.settingTitle(setting.key), setting.maxLength, s.text("setting_sh_auracast_code_send")) { call.change(SettingChange.Choice(setting.key, it)) } }
        is Setting.Level -> level(setting)
        is Setting.Action -> action(setting)
        is Setting.Information -> Split(
            Label(words.settingTitle(setting.key), theme.body),
            Label(information(setting), theme.body, theme.secondaryText, Align.END),
            160f,
        )
    }

    private fun information(setting: Setting.Information): String = when {
        setting.key.startsWith("sh_wear") || setting.key.startsWith("sh_fit_") -> words.optionLabel(setting.key, setting.value)
        setting.key == "sh_auracast_source" -> words.optionLabel(setting.key, setting.value.substringBefore(':')) + ": " + setting.value.substringAfter(':')
        else -> setting.value
    }

    private fun choice(setting: Setting.Choice): Widget {
        val current = setting.value?.let { words.optionLabel(setting.key, it) } ?: s.text("unknown")
        val names = setting.options.map { words.optionLabel(setting.key, it) }
        // A few short choices are all shown at once as one segmented control; a long list opens under its row.
        if (setting.options.size in 2..4 && names.all { it.length <= 18 } && setting.key !in confirmChoices) {
            return Column(
                listOf(
                    titleWithNote(setting.key),
                    segmented("seg_${setting.key}_", names, setting.options.indexOf(setting.value).takeIf { it >= 0 }, primary = false) { i ->
                        call.change(SettingChange.Choice(setting.key, setting.options[i]))
                    },
                ),
                6f,
            )
        }
        return choiceRow(state, setting.key, titleWithNote(setting.key), current, names, setting.options.indexOf(setting.value).takeIf { it >= 0 }, theme, call.changed) { i ->
            val option = setting.options[i]
            val send = { call.change(SettingChange.Choice(setting.key, option)) }
            if (setting.key in confirmChoices) call.confirm(words.settingTitle(setting.key), s.text("setting_sh_peer_delete_body", option), s.text("setting_sh_peer_delete_confirm")) { send() } else send()
        }
    }

    private fun level(setting: Setting.Level): Widget {
        val steps = (setting.min..setting.max).toList()
        val key = setting.key
        val title = titleWithNote(key, if (setting.enabled) null else theme.secondaryText)
        if (steps.size <= 5) {
            val chooser = segmented("lvl_${key}_", steps.map { words.levelName(key, it, setting.max) }, steps.indexOf(setting.value).takeIf { it >= 0 }, primary = false) {
                if (setting.enabled) call.change(SettingChange.Level(key, steps[it]))
            }
            return Column(listOf(title, chooser), 6f)
        }
        val current = state.pending[key] ?: setting.value
        state.senders[key] = { v -> call.change(SettingChange.Level(key, v)) }
        val send = { v: Int -> if (setting.enabled) { state.pending[key] = v; call.changed() } }
        return Column(
            listOf(
                Split(title, Label(words.levelName(key, current, setting.max), theme.bigNumber.copy(size = 14f), theme.accent, Align.END), 80f),
                Row(
                    listOf(
                        StepButton("−", "lm_$key", setting.enabled && current > setting.min) { call.change(SettingChange.Level(key, current - 1)) },
                        Slider(current, setting.min, setting.max, "ls_$key") { send(it) },
                        StepButton("+", "lp_$key", setting.enabled && current < setting.max) { call.change(SettingChange.Level(key, current + 1)) },
                    ),
                    10f,
                    listOf(0.1f, 1f, 0.1f),
                ),
            ),
            6f,
        )
    }

    private fun action(setting: Setting.Action): Widget {
        val key = setting.key
        val text = s.text(if (setting.active) words.actionStop(key) else words.actionStart(key))
        return Button(text, "act_$key", primary = setting.active) {
            val link = links[key]
            when {
                link != null -> call.openUrl(link)
                key in confirmKeys -> {
                    val body = when (key) {
                        "power_off" -> "setting_power_off_body"
                        "sh_personalized_reset" -> "setting_sh_personalized_reset_body"
                        else -> "setting_restart_body"
                    }
                    call.confirm(s.text(words.actionStart(key)), s.text(body), s.text(words.actionStart(key))) { call.change(SettingChange.Press(key)) }
                }
                else -> call.change(SettingChange.Press(key))
            }
        }
    }
}
