package io.github.omegadestroyer.pairfree.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.CustomKeys
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.soundcore.AmbientMode
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreButtonGroup
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreTouch
import io.github.omegadestroyer.pairfree.protocol.soundcore.TouchButton
import io.github.omegadestroyer.pairfree.protocol.soundcore.TouchPress
import io.github.omegadestroyer.pairfree.protocol.soundcore.TouchSide
import io.github.omegadestroyer.pairfree.ui.theme.Studio

/**
 * Touch controls (docs/DESIGN.md, Controls): pick a side, then what each tap and hold does.
 * Only the gestures the earbuds report are listed. Slide gestures have no known command
 * yet on SoundPEATS earbuds, so they are not shown there. Soundcore earbuds report their
 * touch controls in their state ([touch]); see [SoundcoreControls].
 */
@Composable
internal fun ControlsScreen(
    device: Paired,
    state: EarbudState,
    actions: EarbudsActions,
    touch: SoundcoreTouch? = null,
    settings: List<SettingGroup> = emptyList(),
) {
    if (device.brand == Brand.SOUNDCORE) {
        SoundcoreControls(device, state, touch, actions)
        return
    }
    if (device.brand == Brand.SAMSUNG || device.brand == Brand.SONY || device.brand == Brand.BOSE || device.brand == Brand.JBL || device.brand == Brand.APPLE || device.brand == Brand.NOTHING) {
        // Galaxy Buds', AirPods' and Nothing's touch settings come as generic rows; Sony's, Bose's and JBL's are not offered yet.
        TabContent {
            Text(stringResource(R.string.tab_controls), style = Studio.type.title)
            if (settings.none { it.tab == SettingTab.CONTROLS }) {
                Text(stringResource(R.string.no_controls), color = Studio.colors.secondaryText)
            }
            SettingGroups(settings, SettingTab.CONTROLS, actions::changeSetting)
        }
        return
    }
    var side by rememberSaveable { mutableStateOf(Side.LEFT) }
    var editing by remember { mutableStateOf<Pair<GestureKind, Int>?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val keys = state.keys.associate { it.gesture to it.action }
    val hasKeys = state.supports(Command.CUSTOM_KEYS)
    val hasTouchSwitch = state.supports(Command.TOUCH_DISABLED)

    TabContent {
        Text(stringResource(R.string.tab_controls), style = Studio.type.title)
        if (!hasKeys && !hasTouchSwitch) {
            Text(stringResource(R.string.no_controls), color = Studio.colors.secondaryText)
        }
        if (hasKeys) {
            PairDrawing(deviceArt(state.firmware?.code, device.name).bud, side)
            Segmented(Side.entries, side, { sideName(it) }, { side = it })
            val gestures = GestureKind.entries.mapNotNull { kind ->
                gesture(kind, side)?.takeIf { it in keys }?.let { kind to it }
            }
            Panel {
                Label(stringResource(R.string.taps))
                gestures.forEachIndexed { i, (kind, number) ->
                    if (i > 0) Divider()
                    ValueRow(gestureName(kind), actionName(keys.getValue(number)), { editing = kind to number })
                }
            }
        }
        if (hasTouchSwitch) {
            Panel {
                SwitchRow(
                    stringResource(R.string.touch_controls),
                    on = state.switch(Command.TOUCH_DISABLED) == false,
                    onChange = { on -> actions.set(Command.TOUCH_DISABLED, if (on) 0 else 1) },
                    note = stringResource(R.string.touch_controls_note),
                )
            }
        }
        if (hasKeys) {
            StudioButton(stringResource(R.string.reset_gestures), { confirmReset = true }, Modifier.fillMaxWidth())
        }
    }

    editing?.let { (kind, number) ->
        PickerDialog(
            title = stringResource(R.string.pick_action, gestureName(kind), sideName(side)),
            options = CustomKeys.ACTIONS.keys.sorted(),
            selected = keys[number],
            label = { actionName(it) },
            onPick = { actions.set(Command.CUSTOM_KEYS, number, it) },
            onDismiss = { editing = null },
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.reset_gestures_title),
            body = stringResource(R.string.reset_gestures_body),
            confirm = stringResource(R.string.reset),
            onConfirm = { actions.set(Command.RESET_CUSTOM_KEYS) },
            onDismiss = { confirmReset = false },
        )
    }
}

private val SLIDES = setOf(TouchPress.SLIDE_UP, TouchPress.SLIDE_DOWN)

/**
 * Soundcore touch controls (docs/soundcore/PROTOCOL.md section 9), read from the earbuds'
 * state: what each press does on the chosen side, in taps and slides, and during calls on
 * models that have that; then touch tone, touch lock, the switch for all touch controls and
 * which noise modes the noise-control action cycles through, where the model has them. The
 * earbuds keep separate actions for when one earbud is used alone; the tab shows and sets
 * the ones for how they are worn now.
 */
@Composable
private fun SoundcoreControls(device: Paired, state: EarbudState, touch: SoundcoreTouch?, actions: EarbudsActions) {
    var side by rememberSaveable { mutableStateOf(Side.LEFT) }
    var editing by remember { mutableStateOf<Pair<SoundcoreButtonGroup, TouchButton>?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val twoSides = touch?.groups.orEmpty().any { (_, buttons) -> buttons.any { it.side == TouchSide.RIGHT } }

    TabContent {
        Text(stringResource(R.string.tab_controls), style = Studio.type.title)
        if (touch == null) {
            Label(stringResource(R.string.reading))
        } else if (touch.isEmpty) {
            Text(stringResource(R.string.no_controls), color = Studio.colors.secondaryText)
        } else {
            if (twoSides) {
                PairDrawing(deviceArt(state.firmware?.code, device.name).bud, side)
                Segmented(Side.entries, side, { sideName(it) }, { side = it })
            }
            val wanted = if (side == Side.LEFT) TouchSide.LEFT else TouchSide.RIGHT
            for ((group, buttons) in touch.groups) {
                val shown = buttons.filter { !twoSides || it.side == wanted }
                val panels = if (group.duringCall) {
                    listOf(R.string.during_calls to shown)
                } else {
                    listOf(R.string.taps to shown.filter { it.press !in SLIDES }, R.string.slides to shown.filter { it.press in SLIDES })
                }
                for ((title, rows) in panels) {
                    val fixed = if (title == R.string.taps) touch.spec?.fixed.orEmpty() else emptyMap()
                    if (rows.isEmpty() && fixed.isEmpty()) continue
                    Panel {
                        Label(stringResource(title))
                        fixed.entries.forEachIndexed { i, (press, action) ->
                            if (i > 0) Divider()
                            FixedRow(pressName(press), soundcoreActionName(action))
                        }
                        rows.forEachIndexed { i, b ->
                            if (i > 0 || fixed.isNotEmpty()) Divider()
                            ValueRow(pressName(b.press), touchValue(b), { editing = group to b })
                        }
                    }
                }
            }
            if (touch.hasOneEarbudActions) {
                Text(
                    stringResource(if (touch.bothIn) R.string.touch_both_in else R.string.touch_one_in),
                    style = Studio.type.small,
                    color = Studio.colors.secondaryText,
                )
            }
            val switches = listOfNotNull(
                touch.touchEnabled?.let { on -> Triple(R.string.touch_controls, on, R.string.touch_controls_note) to { v: Boolean -> actions.changeTouch { it.setTouchEnabled(v) } } },
                touch.touchLock?.let { on -> Triple(R.string.touch_lock, on, null) to { v: Boolean -> actions.changeTouch { it.setTouchLock(v) } } },
                touch.touchTone?.let { on -> Triple(R.string.touch_tone, on, R.string.touch_tone_note) to { v: Boolean -> actions.changeTouch { it.setTouchTone(v) } } },
            )
            if (switches.isNotEmpty()) {
                Panel {
                    switches.forEachIndexed { i, (row, onChange) ->
                        if (i > 0) Divider()
                        SwitchRow(stringResource(row.first), row.second, onChange, note = row.third?.let { stringResource(it) })
                    }
                }
            }
            touch.noiseCycle?.let { cycle -> NoiseCycle(cycle, actions) }
            if (touch.canReset) {
                StudioButton(stringResource(R.string.reset_gestures), { confirmReset = true }, Modifier.fillMaxWidth())
            }
        }
    }

    editing?.let { (group, b) ->
        val title = if (twoSides) stringResource(R.string.pick_action, pressName(b.press), sideName(side)) else pressName(b.press)
        PickerDialog(
            title = title,
            options = (if (b.canTurnOff) listOf<Int?>(null) else emptyList()) + b.choices.map { it.id },
            selected = b.actionId,
            label = { id -> b.choices.firstOrNull { it.id == id }?.let { soundcoreActionName(it.name) } ?: stringResource(R.string.action_off) },
            onPick = { id -> actions.changeTouch { it.setButton(group, b.side, b.press, id) } },
            onDismiss = { editing = null },
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.reset_gestures_title),
            body = stringResource(R.string.reset_gestures_body),
            confirm = stringResource(R.string.reset),
            onConfirm = { actions.resetTouch() },
            onDismiss = { confirmReset = false },
        )
    }
}

/** Which noise modes the noise-control touch action cycles through. At least one stays on. */
@Composable
private fun NoiseCycle(cycle: Set<AmbientMode>, actions: EarbudsActions) {
    val modes = listOf(
        AmbientMode.NOISE_CANCELLING to R.string.anc_cancelling,
        AmbientMode.TRANSPARENCY to R.string.anc_transparency,
        AmbientMode.NORMAL to R.string.anc_off,
    )
    Panel {
        Label(stringResource(R.string.noise_cycle))
        modes.forEachIndexed { i, (mode, title) ->
            if (i > 0) Divider()
            val on = mode in cycle
            SwitchRow(
                stringResource(title),
                on,
                { v -> actions.changeTouch { t -> t.noiseCycle?.let { now -> t.setNoiseCycle(if (v) now + mode else now - mode) } } },
                enabled = !(on && cycle.size == 1),
            )
        }
    }
}

/** What a Soundcore button does now: its action, Off, or the number of an action PairFree does not know. */
@Composable
private fun touchValue(b: TouchButton): String = when {
    b.actionId == null -> stringResource(R.string.action_off)
    b.action != null -> soundcoreActionName(b.action!!.name)
    else -> stringResource(R.string.code_unknown, "0x" + b.actionId!!.toString(16).uppercase())
}

/** A press the model does not let anyone change: its name and what it does, without a picker. */
@Composable
private fun FixedRow(title: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, Modifier.weight(1f))
        Text(value, color = Studio.colors.secondaryText)
    }
}

/** Both earbuds, the chosen one in full and underlined, the other faded. */
@Composable
private fun PairDrawing(bud: Int, side: Side) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)) {
        for (s in Side.entries) {
            val on = s == side
            Column(
                Modifier.width(120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    painterResource(bud),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .graphicsLayer(scaleX = if (s == Side.RIGHT) -1f else 1f, alpha = if (on) 1f else 0.35f),
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.5f)
                        .height(2.dp)
                        .background(if (on) Studio.colors.accent else Color.Transparent),
                )
            }
        }
    }
}
