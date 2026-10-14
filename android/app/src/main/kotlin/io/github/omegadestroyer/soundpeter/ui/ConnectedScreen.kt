package io.github.omegadestroyer.soundpeter.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omegadestroyer.soundpeter.AppViewModel
import io.github.omegadestroyer.soundpeter.R
import io.github.omegadestroyer.soundpeter.bluetooth.Brand
import io.github.omegadestroyer.soundpeter.bluetooth.Paired
import io.github.omegadestroyer.soundpeter.eq.EqActions
import io.github.omegadestroyer.soundpeter.eq.EqState
import io.github.omegadestroyer.soundpeter.protocol.Command
import io.github.omegadestroyer.soundpeter.protocol.EarbudState
import io.github.omegadestroyer.soundpeter.protocol.Model
import io.github.omegadestroyer.soundpeter.protocol.Models
import io.github.omegadestroyer.soundpeter.protocol.soundcore.SoundcoreModels
import io.github.omegadestroyer.soundpeter.protocol.blockedByLdac
import io.github.omegadestroyer.soundpeter.ui.theme.Studio
import io.github.omegadestroyer.soundpeter.ui.theme.StudioShape

internal enum class Tab(@StringRes val title: Int) {
    HOME(R.string.tab_home),
    SOUND(R.string.tab_sound),
    CONTROLS(R.string.tab_controls),
    DEVICE(R.string.tab_device),
}

/** The tabs for connected earbuds. [live] is false while the channel is being reopened; the last state stays. */
@Composable
fun ConnectedScreen(vm: AppViewModel, device: Paired, live: Boolean) {
    val state by vm.connection.state.collectAsStateWithLifecycle()
    val model = modelOf(device, state)
    LaunchedEffect(device.address, model) { vm.equalizer.open(device.address, model) }
    val eq by vm.equalizer.state.collectAsStateWithLifecycle()
    ConnectedScreen(device, state, vm, eq, vm.equalizer, live = live)
}

@Composable
internal fun ConnectedScreen(
    device: Paired,
    state: EarbudState,
    actions: EarbudsActions,
    eq: EqState?,
    eqActions: EqActions,
    startTab: Tab = Tab.HOME,
    live: Boolean = true,
) {
    var tab by rememberSaveable { mutableStateOf(startTab) }
    var fitTest by rememberSaveable { mutableStateOf(false) }
    val model = modelOf(device, state)
    if (fitTest) {
        FitTestScreen(device, state, actions, onClose = { fitTest = false })
        return
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.HOME -> HomeScreen(device, state, model, actions, live, eq) { tab = Tab.SOUND }
                // Recording from the official app exists for SoundPEATS only so far.
                Tab.SOUND -> SoundScreen(eq, eqActions, actions::openCapture.takeIf { device.brand == Brand.SOUNDPEATS })
                Tab.CONTROLS -> ControlsScreen(device, state, actions)
                Tab.DEVICE -> DeviceScreen(device, state, model, actions) { fitTest = true }
            }
        }
        TabBar(tab) { tab = it }
    }
}

/**
 * The model's table entry: SoundPEATS by firmware code, or by name until the code is known;
 * Soundcore by the model number its service named (data/soundcore/devices.json).
 */
internal fun modelOf(device: Paired, state: EarbudState): Model? = when (device.brand) {
    Brand.SOUNDCORE -> SoundcoreModels.model(state.firmware?.code)
    Brand.SOUNDPEATS -> Models.find(state.firmware?.code, device.name)
}

/** A tab's content: scrolls if it must, 16 px gutters, 16 px between blocks. */
@Composable
internal fun TabContent(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
    }
}

@Composable
private fun HomeScreen(
    device: Paired,
    state: EarbudState,
    model: Model?,
    actions: EarbudsActions,
    live: Boolean,
    eq: EqState?,
    onOpenSound: () -> Unit,
) {
    val art = deviceArt(state.firmware?.code, device.name)
    TabContent {
        // Model name and connection state, and a button to switch earbuds.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(device.name, style = Studio.type.title)
                val firmware = state.firmware?.let { stringResource(R.string.firmware, it.version) }
                val link = stringResource(if (live) R.string.connected else R.string.reconnecting)
                Label(listOfNotNull(link, firmware).joinToString("  ·  "), color = if (live) Studio.colors.label else Studio.colors.accent)
            }
            StudioButton(stringResource(R.string.switch_earbuds), actions::switchEarbuds)
        }

        // Battery for left, case and right: drawing, percentage, bar. Only what the earbuds report.
        val batteries = listOfNotNull(
            Battery(R.string.left, state.batteryLeft, art.bud, mirrored = false)
                .takeIf { state.supports(Command.BATTERY_LEFT) },
            Battery(R.string.case_label, state.batteryCase, art.case, mirrored = false)
                .takeIf { state.supports(Command.BATTERY_CASE) },
            Battery(R.string.right, state.batteryRight, art.bud, mirrored = true)
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

        if (state.supports(Command.ANC_MODE)) NoiseControl(state, actions)
        eq?.let { SoundCard(it, onOpenSound) }
        QuickSwitches(state, model, actions)
    }
}

private data class Battery(@StringRes val label: Int, val percent: Int?, @DrawableRes val drawing: Int?, val mirrored: Boolean)

@Composable
private fun BatteryItem(battery: Battery, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (battery.drawing != null) {
            Image(
                painterResource(battery.drawing),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
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

/** The three noise modes, in the order docs/DESIGN.md gives. Their numbers come from data/soundpeats/commands.json. */
private enum class NoiseMode(val key: String, @StringRes val title: Int) {
    OFF("off", R.string.anc_off),
    CANCELLING("anc", R.string.anc_cancelling),
    TRANSPARENCY("transparency", R.string.anc_transparency),
    ;

    val value: Int get() = numberOf(Command.ANC_MODE, key)
}

/**
 * Off, Cancelling, Transparency. Under Cancelling, the scenes the earbuds report; under
 * Transparency, the levels they report. Modes outside these three select nothing.
 */
@Composable
private fun NoiseControl(state: EarbudState, actions: EarbudsActions) {
    val mode = NoiseMode.entries.firstOrNull { it.value == state.number(Command.ANC_MODE) }
    val levels = when (mode) {
        NoiseMode.CANCELLING -> Command.ANC_LEVEL
        NoiseMode.TRANSPARENCY -> Command.TRANSPARENCY_LEVEL
        else -> null
    }
    val choices = levels?.let(state::levels).orEmpty()
    Panel {
        Label(stringResource(R.string.noise_control))
        Segmented(NoiseMode.entries, mode, { stringResource(it.title) }, { actions.set(Command.ANC_MODE, it.value) })
        if (levels != null && choices.isNotEmpty()) {
            Label(stringResource(if (levels == Command.ANC_LEVEL) R.string.scene else R.string.transparency_level))
            Segmented(
                choices.map { it.code },
                choices.firstOrNull { it.active }?.code,
                { if (levels == Command.ANC_LEVEL) sceneName(it) else transparencyName(it) },
                { actions.set(levels, it) },
                primary = false,
            )
        }
    }
}

/** Game mode, two devices, in-ear detection and LDAC: whichever the earbuds answered for. */
@Composable
private fun QuickSwitches(state: EarbudState, model: Model?, actions: EarbudsActions) {
    val switches = listOf(
        Command.GAME_MODE to R.string.game_mode,
        Command.MULTIPOINT to R.string.two_devices,
        Command.IN_EAR_DETECTION to R.string.in_ear_detection,
        Command.LDAC to R.string.ldac,
    ).filter { state.supports(it.first) }
    if (switches.isEmpty()) return
    Panel {
        Label(stringResource(R.string.quick_switches))
        for (row in switches.chunked(2)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for ((command, title) in row) {
                    SwitchTile(command, stringResource(title), state, model, actions, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A switch as a tile: name, switch, and its state in words, or why it cannot be used now. */
@Composable
private fun SwitchTile(
    command: Command,
    title: String,
    state: EarbudState,
    model: Model?,
    actions: EarbudsActions,
    modifier: Modifier = Modifier,
) {
    val c = Studio.colors
    val shape = RoundedCornerShape(StudioShape.smallCorner)
    val on = state.switch(command) == true
    val blocked = state.blockedByLdac(command, model)
    Column(
        modifier
            .fillMaxHeight()
            .clip(shape)
            .background(c.raised)
            .border(StudioShape.hairline, if (on && !blocked) c.accent else c.hairline, shape)
            .toggleable(value = on, enabled = !blocked, role = Role.Switch) { actions.set(command, if (it) 1 else 0) }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, Modifier.weight(1f), color = if (blocked) c.secondaryText else c.text)
            SwitchMark(on, enabled = !blocked)
        }
        Text(
            when {
                blocked -> stringResource(R.string.needs_ldac_off)
                on -> stringResource(R.string.on)
                else -> stringResource(R.string.off)
            },
            style = Studio.type.small,
            color = c.secondaryText,
        )
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
