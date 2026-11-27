package io.github.omegadestroyer.pairfree.ui

import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.brands.Brands
import io.github.omegadestroyer.pairfree.brands.Controls
import io.github.omegadestroyer.pairfree.brands.modelOf
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.CustomKeys
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.soundcore.AmbientMode
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreButtonGroup
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreButtons
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreTouch
import io.github.omegadestroyer.pairfree.protocol.soundcore.TouchButton
import io.github.omegadestroyer.pairfree.protocol.soundcore.TouchPress
import io.github.omegadestroyer.pairfree.protocol.soundcore.TouchSide
import io.github.omegadestroyer.pairfree.shell.ArtDrawing
import io.github.omegadestroyer.pairfree.shell.DesktopStrings
import io.github.omegadestroyer.pairfree.shell.LabelWords
import io.github.omegadestroyer.pairfree.shell.GestureKind
import io.github.omegadestroyer.pairfree.shell.Side
import io.github.omegadestroyer.pairfree.shell.deviceArt
import io.github.omegadestroyer.pairfree.shell.gesture

/** What the Controls tab asks the app for. */
internal class ControlsCallbacks(
    val set: (Command, IntArray) -> Unit,
    val changeTouch: (make: (SoundcoreTouch) -> SoundcoreButtons.Change?) -> Unit,
    val resetTouch: () -> Unit,
    val confirm: (title: String, body: String, confirm: String, onConfirm: () -> Unit) -> Unit,
    val selectSide: (Side) -> Unit,
    val changed: () -> Unit,
)

/**
 * Touch controls (docs/DESIGN.md, Controls). SOUNDPEATS earbuds: pick a side, then what each tap and hold does. Soundcore earbuds report
 * theirs in their state. Every other brand's come as the rows its driver offers.
 */
internal class ControlsTab(
    private val device: Paired,
    private val state: EarbudState,
    private val touch: SoundcoreTouch?,
    private val rows: List<Widget>,
    private val side: Side,
    private val rowState: RowState,
    private val s: DesktopStrings,
    private val theme: Theme,
    private val call: ControlsCallbacks,
) {
    private val words = LabelWords(s)

    private fun title() = Label(s.text("tab_controls"), theme.title)

    private fun label(text: String) = Label(text, theme.labelFont, theme.label, caps = true)

    private fun note(text: String) = Label(text, theme.small, theme.secondaryText)

    private fun sideChooser(): Widget {
        val bud = deviceArt(state.firmware?.code, device.name).bud
        val pair = Row(
            Side.entries.map { sd ->
                Column(listOf(ArtView(ArtDrawing.of(bud), 64f, mirrored = sd == Side.RIGHT), Label(words.sideName(sd), theme.labelFont, if (sd == side) theme.accent else theme.label, Align.CENTER, caps = true)), 4f) as Widget
            },
            24f,
        )
        return Column(listOf(pair, segmented("side", Side.entries.map(words::sideName), Side.entries.indexOf(side), primary = true) { call.selectSide(Side.entries[it]) }), 10f)
    }

    fun build(): Widget {
        val kind = Brands[device.brand].controls
        return when (kind) {
            Controls.SOUNDCORE -> soundcore()
            Controls.ROWS -> Column(
                listOf<Widget>(title()) + if (rows.isEmpty()) listOf(note(s.text("no_controls"))) else rows,
                16f,
            )
            else -> soundpeats()
        }
    }

    private fun soundpeats(): Widget {
        val keys = state.keys.associate { it.gesture to it.action }
        val hasKeys = state.supports(Command.CUSTOM_KEYS)
        val hasTouchSwitch = state.supports(Command.TOUCH_DISABLED)
        val inverted = modelOf(device, state)?.touchInverted == true
        val parts = mutableListOf<Widget>(title())
        if (!hasKeys && !hasTouchSwitch) parts += note(s.text("no_controls"))
        if (hasKeys) {
            parts += sideChooser()
            val gestures = GestureKind.entries.mapNotNull { k -> gesture(k, side)?.takeIf { it in keys }?.let { k to it } }
            val actionIds = CustomKeys.ACTIONS.keys.sorted()
            val rowsOut = mutableListOf<Widget>(label(s.text("taps")))
            gestures.forEachIndexed { i, (k, number) ->
                if (i > 0) rowsOut += Divider()
                val current = keys.getValue(number)
                rowsOut += choiceRow(
                    rowState, "gesture_${side.name}_${k.name}", Label(words.gestureName(k)), words.actionName(current), actionIds.map(words::actionName),
                    actionIds.indexOf(current).takeIf { it >= 0 }, theme, call.changed,
                ) { call.set(Command.CUSTOM_KEYS, intArrayOf(number, actionIds[it])) }
            }
            parts += Panel(Column(rowsOut, 8f))
        }
        if (hasTouchSwitch) {
            // On the firmware codes SOUNDPEATS 2.5.3 inverts the switch for, 1 on the wire means touch is on.
            val on = state.switch(Command.TOUCH_DISABLED) == inverted
            parts += Panel(
                switchRow("touch_switch", Column(listOf(Label(s.text("touch_controls")), note(s.text("touch_controls_note"))), 2f), on) { v ->
                    call.set(Command.TOUCH_DISABLED, intArrayOf(if (v != inverted) 0 else 1))
                },
            )
        }
        if (hasKeys) {
            parts += Button(s.text("reset_gestures"), "reset_gestures") {
                call.confirm(s.text("reset_gestures_title"), s.text("reset_gestures_body"), s.text("reset")) { call.set(Command.RESET_CUSTOM_KEYS, intArrayOf()) }
            }
        }
        return Column(parts, 16f)
    }

    private val slides = setOf(TouchPress.SLIDE_UP, TouchPress.SLIDE_DOWN)

    private fun touchValue(b: TouchButton): String = when {
        b.actionId == null -> s.text("action_off")
        b.action != null -> words.soundcoreActionName(b.action!!.name)
        else -> s.text("code_unknown", "0x" + b.actionId!!.toString(16).uppercase())
    }

    private fun soundcore(): Widget {
        val parts = mutableListOf<Widget>(title())
        if (touch == null) return Column(parts + Label(s.text("reading"), theme.body, theme.secondaryText), 16f)
        if (touch.isEmpty) return Column(parts + note(s.text("no_controls")), 16f)
        val twoSides = touch.groups.any { (_, buttons) -> buttons.any { it.side == TouchSide.RIGHT } }
        if (twoSides) parts += sideChooser()
        val wanted = if (side == Side.LEFT) TouchSide.LEFT else TouchSide.RIGHT
        for ((group, buttons) in touch.groups) {
            val shown = buttons.filter { !twoSides || it.side == wanted }
            val panels = if (group.duringCall) listOf("during_calls" to shown) else listOf("taps" to shown.filter { it.press !in slides }, "slides" to shown.filter { it.press in slides })
            for ((titleKey, rowsOf) in panels) {
                val fixed = if (titleKey == "taps") touch.spec?.fixed.orEmpty() else emptyMap()
                if (rowsOf.isEmpty() && fixed.isEmpty()) continue
                val out = mutableListOf<Widget>(label(s.text(titleKey)))
                fixed.entries.forEachIndexed { i, (press, action) ->
                    if (i > 0) out += Divider()
                    out += Split(Label(words.pressName(press)), Label(words.soundcoreActionName(action), theme.body, theme.secondaryText, Align.END), 160f)
                }
                rowsOf.forEachIndexed { i, b ->
                    if (i > 0 || fixed.isNotEmpty()) out += Divider()
                    val ids: List<Int?> = (if (b.canTurnOff) listOf<Int?>(null) else emptyList()) + b.choices.map { it.id }
                    val names = ids.map { id -> b.choices.firstOrNull { it.id == id }?.let { words.soundcoreActionName(it.name) } ?: s.text("action_off") }
                    out += choiceRow(
                        rowState, "touch_${group.hashCode()}_${b.side}_${b.press}", Label(words.pressName(b.press)), touchValue(b), names,
                        ids.indexOf(b.actionId).takeIf { it >= 0 }, theme, call.changed,
                    ) { idx -> call.changeTouch { it.setButton(group, b.side, b.press, ids[idx]) } }
                }
                parts += Panel(Column(out, 8f))
            }
        }
        if (touch.hasOneEarbudActions) parts += note(s.text(if (touch.bothIn) "touch_both_in" else "touch_one_in"))
        val switches = listOfNotNull(
            touch.touchEnabled?.let { Triple("touch_controls", it, "touch_controls_note") to { v: Boolean -> call.changeTouch { t -> t.setTouchEnabled(v) } } },
            touch.touchLock?.let { Triple("touch_lock", it, null as String?) to { v: Boolean -> call.changeTouch { t -> t.setTouchLock(v) } } },
            touch.touchTone?.let { Triple("touch_tone", it, "touch_tone_note") to { v: Boolean -> call.changeTouch { t -> t.setTouchTone(v) } } },
        )
        if (switches.isNotEmpty()) {
            val out = mutableListOf<Widget>()
            switches.forEachIndexed { i, (row, onChange) ->
                if (i > 0) out += Divider()
                val titleWidget = Column(listOfNotNull<Widget>(Label(s.text(row.first)), row.third?.let { note(s.text(it)) }), 2f)
                out += switchRow("sc_${row.first}", titleWidget, row.second, onChange = onChange)
            }
            parts += Panel(Column(out, 8f))
        }
        touch.noiseCycle?.let { cycle ->
            val modes = touch.noiseCycleModes.map { mode ->
                mode to when (mode) {
                    AmbientMode.NOISE_CANCELLING -> "anc_cancelling"
                    AmbientMode.TRANSPARENCY -> "anc_transparency"
                    AmbientMode.NORMAL -> "anc_off"
                    AmbientMode.ADAPTIVE -> "scene_adaptive"
                }
            }
            val out = mutableListOf<Widget>(label(s.text("noise_cycle")))
            modes.forEachIndexed { i, (mode, key) ->
                if (i > 0) out += Divider()
                val on = mode in cycle
                out += switchRow("cycle_$key", Label(s.text(key)), on, enabled = !(on && cycle.size == 1)) { v ->
                    call.changeTouch { t -> t.noiseCycle?.let { now -> t.setNoiseCycle(if (v) now + mode else now - mode) } }
                }
            }
            parts += Panel(Column(out, 8f))
        }
        if (touch.canReset) {
            parts += Button(s.text("reset_gestures"), "reset_touch") {
                call.confirm(s.text("reset_gestures_title"), s.text("reset_gestures_body"), s.text("reset")) { call.resetTouch() }
            }
        }
        return Column(parts, 16f)
    }
}
