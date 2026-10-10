package io.github.omegadestroyer.soundpeter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import io.github.omegadestroyer.soundpeter.R
import io.github.omegadestroyer.soundpeter.bluetooth.Paired
import io.github.omegadestroyer.soundpeter.protocol.Command
import io.github.omegadestroyer.soundpeter.protocol.CustomKeys
import io.github.omegadestroyer.soundpeter.protocol.EarbudState
import io.github.omegadestroyer.soundpeter.ui.theme.Studio

/**
 * Touch controls (docs/DESIGN.md, Controls): pick a side, then what each tap and hold does.
 * Only the gestures the earbuds report are listed. Slide gestures have no known command
 * yet, so they are not shown.
 */
@Composable
internal fun ControlsScreen(device: Paired, state: EarbudState, actions: EarbudsActions) {
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
