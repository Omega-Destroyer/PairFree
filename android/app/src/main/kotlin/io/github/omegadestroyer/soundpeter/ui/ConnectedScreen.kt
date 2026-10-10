package io.github.omegadestroyer.soundpeter.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omegadestroyer.soundpeter.AppViewModel
import io.github.omegadestroyer.soundpeter.R
import io.github.omegadestroyer.soundpeter.bluetooth.Paired
import io.github.omegadestroyer.soundpeter.protocol.Command
import io.github.omegadestroyer.soundpeter.protocol.EarbudState
import io.github.omegadestroyer.soundpeter.protocol.Frame
import io.github.omegadestroyer.soundpeter.protocol.Wire
import io.github.omegadestroyer.soundpeter.ui.theme.Studio
import io.github.omegadestroyer.soundpeter.ui.theme.StudioShape
import io.github.omegadestroyer.soundpeter.ui.theme.StudioTheme

private enum class Tab(@StringRes val title: Int) {
    HOME(R.string.tab_home),
    SOUND(R.string.tab_sound),
    CONTROLS(R.string.tab_controls),
    DEVICE(R.string.tab_device),
}

@Composable
fun ConnectedScreen(vm: AppViewModel, device: Paired) {
    val state by vm.connection.state.collectAsStateWithLifecycle()
    ConnectedScreen(device, state, onSwitch = vm::switchEarbuds)
}

@Composable
private fun ConnectedScreen(device: Paired, state: EarbudState, onSwitch: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.HOME -> HomeScreen(device, state, onSwitch)
                else -> NotBuilt(tab)
            }
        }
        TabBar(tab) { tab = it }
    }
}

@Composable
private fun HomeScreen(device: Paired, state: EarbudState, onSwitch: () -> Unit) {
    val art = deviceArt(state.firmware?.code, device.name)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Model name and connection state, and a button to switch earbuds.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(device.name, style = Studio.type.title)
                val firmware = state.firmware?.let { stringResource(R.string.firmware, it.version) }
                Label(listOfNotNull(stringResource(R.string.connected), firmware).joinToString("  ·  "))
            }
            StudioButton(stringResource(R.string.switch_earbuds), onSwitch)
        }

        // Battery for left, case and right: drawing, percentage, bar. Only what the earbuds report.
        val batteries = listOfNotNull(
            Battery(R.string.left, state.batteryLeft, art?.bud, mirrored = false)
                .takeIf { state.supports(Command.BATTERY_LEFT) },
            Battery(R.string.case_label, state.batteryCase, art?.case, mirrored = false)
                .takeIf { state.supports(Command.BATTERY_CASE) },
            Battery(R.string.right, state.batteryRight, art?.bud, mirrored = true)
                .takeIf { state.supports(Command.BATTERY_RIGHT) },
        )
        if (batteries.isEmpty()) {
            Label(stringResource(R.string.reading))
        } else {
            Panel {
                Label(stringResource(R.string.battery))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (b in batteries) BatteryItem(b, Modifier.weight(1f))
                }
            }
        }
    }
}

private data class Battery(@StringRes val label: Int, val percent: Int?, @DrawableRes val drawing: Int?, val mirrored: Boolean)

@Composable
private fun BatteryItem(battery: Battery, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (battery.drawing != null) {
            Image(
                painterResource(battery.drawing),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .graphicsLayer(scaleX = if (battery.mirrored) -1f else 1f),
            )
        }
        Label(stringResource(battery.label))
        Text(
            battery.percent?.let { stringResource(R.string.percent, it) } ?: stringResource(R.string.unknown),
            style = Studio.type.bigNumber,
        )
        LevelBar(battery.percent)
    }
}

/**
 * The whole connected screen, tab bar included, as it looks with an H3 attached:
 * replies recorded in data/reports/S5_0.4.9.txt.
 */
@Preview(widthDp = 390, heightDp = 844, backgroundColor = 0xFF101214, showBackground = true)
@Composable
private fun ConnectedPreview() {
    val replies = listOf(
        Command.FIRMWARE_VERSION to "0053355f32303235303630355f56302e342e39",
        Command.BATTERY_LEFT to "0064",
        Command.BATTERY_CASE to "002f",
        Command.BATTERY_RIGHT to "0064",
    )
    val state = replies.fold(EarbudState()) { s, (command, hex) ->
        val payload = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        s.update(Frame(Wire.VENDOR_MAIN, command.get!! or Wire.REPLY_MASK, payload))
    }
    StudioTheme {
        Box(Modifier.background(Studio.colors.background)) {
            ConnectedScreen(Paired("00:00:00:00:00:00", "SOUNDPEATS H3"), state, onSwitch = {})
        }
    }
}

@Composable
private fun NotBuilt(tab: Tab) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(tab.title), style = Studio.type.title)
        Text(stringResource(R.string.not_built), color = Studio.colors.secondaryText)
    }
}

/**
 * Four tabs along the bottom. The selected one gets an amber bar and amber text, not colour alone.
 * Each tab is a Box so it takes the height of its label: a weighted spacer here would stretch
 * the bar over the whole screen.
 */
@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val c = Studio.colors
    Column(Modifier.background(c.panel)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(StudioShape.hairline)
                .background(c.hairline),
        )
        Row(Modifier.fillMaxWidth()) {
            for (tab in Tab.entries) {
                val on = tab == selected
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .selectable(selected = on, role = Role.Tab) { onSelect(tab) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(0.5f)
                            .height(2.dp)
                            .background(if (on) c.accent else Color.Transparent),
                    )
                    Label(
                        stringResource(tab.title),
                        Modifier.padding(vertical = 16.dp),
                        color = if (on) c.accent else c.label,
                    )
                }
            }
        }
    }
}
