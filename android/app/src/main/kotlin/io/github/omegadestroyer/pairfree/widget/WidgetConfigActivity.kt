package io.github.omegadestroyer.pairfree.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.omegadestroyer.pairfree.MainActivity
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.hub
import io.github.omegadestroyer.pairfree.protocol.widget.WidgetGrid
import io.github.omegadestroyer.pairfree.protocol.widget.WidgetSlot
import io.github.omegadestroyer.pairfree.ui.Chip
import io.github.omegadestroyer.pairfree.ui.Label
import io.github.omegadestroyer.pairfree.ui.Panel
import io.github.omegadestroyer.pairfree.ui.StudioButton
import io.github.omegadestroyer.pairfree.ui.StudioDialog
import io.github.omegadestroyer.pairfree.ui.Text
import io.github.omegadestroyer.pairfree.ui.theme.Studio
import io.github.omegadestroyer.pairfree.ui.theme.StudioTheme

/**
 * Sets up a widget (docs/DESIGN.md, Widget): the paired earbuds PairFree can control, and for each the eight buttons. Nothing
 * connects; the choices come from [WidgetOptions]. The widget shows the buttons of whichever of these earbuds is connected.
 */
class WidgetConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Cancelled until saved: Android then drops a widget that was never set up.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            StudioTheme {
                Column(Modifier.fillMaxSize().background(Studio.colors.background).systemBarsPadding()) {
                    Setup(applicationContext, ::save, ::finish)
                }
            }
        }
    }

    private fun save(config: WidgetConfig) {
        WidgetStore.save(hub.prefs, widgetId, config)
        PairFreeWidget.refresh(this, intArrayOf(widgetId))
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    @Composable
    private fun Setup(context: Context, onSave: (WidgetConfig) -> Unit, onCancel: () -> Unit) {
        val hub = context.hub
        val devices = remember { if (hub.hasPermission()) hub.pairedEarbuds() else emptyList() }
        val saved = remember { WidgetStore.load(hub.prefs, widgetId) }
        val layouts = remember { mutableStateMapOf<String, List<WidgetSlot>>().apply { saved?.devices?.let(::putAll) } }
        var editing by remember { mutableStateOf<Paired?>(null) }
        var last by remember { mutableStateOf(saved?.last) }
        val open = editing
        BackHandler(open != null) { editing = null }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Label(stringResource(R.string.widget_title))
            when {
                !hub.hasPermission() -> NeedsPermission(context)
                devices.isEmpty() -> Text(stringResource(R.string.widget_no_earbuds), color = Studio.colors.secondaryText)
                open != null -> Buttons(context, open, layouts[open.address] ?: emptyList(), { layouts[open.address] = it; last = open.address }, { editing = null })
                else -> {
                    Text(stringResource(R.string.widget_follows_hint), color = Studio.colors.secondaryText)
                    Panel {
                        for (device in devices) {
                            val set = layouts[device.address].orEmpty().count { it != WidgetSlot.Empty }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(device.name, style = Studio.type.heading)
                                    Label(if (set == 0) stringResource(R.string.widget_not_set_up) else stringResource(R.string.widget_buttons_set, set))
                                }
                                StudioButton(stringResource(R.string.widget_title), { editing = device })
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StudioButton(stringResource(R.string.widget_cancel), onCancel, Modifier.weight(1f))
                        StudioButton(
                            stringResource(R.string.widget_save),
                            { onSave(WidgetConfig(last ?: layouts.keys.firstOrNull(), layouts.toMap())) },
                            Modifier.weight(1f),
                            primary = true,
                            enabled = layouts.values.any { slots -> slots.any { it != WidgetSlot.Empty } },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun NeedsPermission(context: Context) {
        Text(stringResource(R.string.widget_needs_permission), color = Studio.colors.secondaryText)
        StudioButton(stringResource(R.string.widget_open_app), { context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
    }

    /** One pair's eight buttons. A pair with none yet starts with a noise toggle and an EQ toggle where the earbuds have them. */
    @Composable
    private fun Buttons(context: Context, device: Paired, saved: List<WidgetSlot>, onChange: (List<WidgetSlot>) -> Unit, onBack: () -> Unit) {
        val options = remember(device) { WidgetOptions.of(context, device) }
        val slots = WidgetStore.padded(saved.ifEmpty { defaults(options) })
        var editing by remember { mutableStateOf<Int?>(null) }
        Text(device.name, style = Studio.type.heading)
        if (options.needsConnection) Text(stringResource(R.string.widget_connect_once), color = Studio.colors.secondaryText)
        if (options.noise.isEmpty() && options.eq.isEmpty()) {
            Text(stringResource(R.string.widget_nothing_to_offer), color = Studio.colors.secondaryText)
        } else {
            Text(stringResource(R.string.widget_size_hint), color = Studio.colors.secondaryText)
            for (row in 0 until 2) {
                Panel {
                    for (i in row * WidgetGrid.PER_ROW until (row + 1) * WidgetGrid.PER_ROW) {
                        val slot = slots[i]
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { editing = i },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Label("${i + 1}", Modifier.padding(end = 4.dp))
                            Column(Modifier.weight(1f)) {
                                Text(describe(context, slot, options), color = if (slot == WidgetSlot.Empty) Studio.colors.label else Studio.colors.text)
                                kindName(slot)?.let { Label(stringResource(it)) }
                            }
                        }
                    }
                }
            }
        }
        StudioButton(stringResource(R.string.widget_done), onBack, Modifier.fillMaxWidth(), primary = true)
        editing?.let { index ->
            SlotDialog(context, options, slots[index], onDismiss = { editing = null }) { slot ->
                onChange(slots.toMutableList().also { it[index] = slot })
                editing = null
            }
        }
    }

    private fun defaults(options: WidgetChoices): List<WidgetSlot> {
        val noise = options.noise.filter { it.sub == null }
        return buildList {
            add(if (noise.size >= 2) WidgetSlot.NoiseCycle(noise) else WidgetSlot.Empty)
            add(if (options.eq.size >= 2) WidgetSlot.EqCycle(options.eq.take(3).map { it.first }) else WidgetSlot.Empty)
        }
    }

    private fun kindName(slot: WidgetSlot): Int? = when (slot) {
        WidgetSlot.Empty -> null
        is WidgetSlot.Noise -> R.string.widget_type_noise
        is WidgetSlot.NoiseCycle -> R.string.widget_type_noise_cycle
        is WidgetSlot.Eq -> R.string.widget_type_eq
        is WidgetSlot.EqCycle -> R.string.widget_type_eq_cycle
    }

    private fun describe(context: Context, slot: WidgetSlot, options: WidgetChoices): String {
        val eq = options.eq.toMap()
        fun profile(id: String) = eq[id] ?: context.getString(R.string.widget_profile_missing)
        return when (slot) {
            WidgetSlot.Empty -> context.getString(R.string.widget_empty_slot)
            is WidgetSlot.Noise -> WidgetLabels.noise(context, slot.choice)
            is WidgetSlot.NoiseCycle -> slot.choices.joinToString(" → ") { WidgetLabels.noise(context, it) }
            is WidgetSlot.Eq -> profile(slot.profileId)
            is WidgetSlot.EqCycle -> slot.profileIds.joinToString(" → ") { profile(it) }
        }
    }

    private enum class Kind(val label: Int) {
        EMPTY(R.string.widget_type_empty),
        NOISE(R.string.widget_type_noise),
        NOISE_CYCLE(R.string.widget_type_noise_cycle),
        EQ(R.string.widget_type_eq),
        EQ_CYCLE(R.string.widget_type_eq_cycle),
    }

    /** Edits one button: its kind, then the choice, or for a toggle the choices to step through in the order ticked. */
    @Composable
    private fun SlotDialog(context: Context, options: WidgetChoices, slot: WidgetSlot, onDismiss: () -> Unit, onPick: (WidgetSlot) -> Unit) {
        val kinds = Kind.entries.filter {
            when (it) {
                Kind.EMPTY -> true
                Kind.NOISE, Kind.NOISE_CYCLE -> options.noise.isNotEmpty()
                Kind.EQ, Kind.EQ_CYCLE -> options.eq.isNotEmpty()
            }
        }
        var kind by remember {
            mutableStateOf(
                when (slot) {
                    WidgetSlot.Empty -> Kind.EMPTY
                    is WidgetSlot.Noise -> Kind.NOISE
                    is WidgetSlot.NoiseCycle -> Kind.NOISE_CYCLE
                    is WidgetSlot.Eq -> Kind.EQ
                    is WidgetSlot.EqCycle -> Kind.EQ_CYCLE
                },
            )
        }
        val noiseLabels = options.noise.map { WidgetLabels.noise(context, it) }
        val eqLabels = options.eq.map { it.second }
        // The ticked choices, as indices in the order they were ticked.
        var ticked by remember {
            mutableStateOf(
                when (slot) {
                    is WidgetSlot.Noise -> listOfNotNull(options.noise.indexOf(slot.choice).takeIf { it >= 0 })
                    is WidgetSlot.NoiseCycle -> slot.choices.mapNotNull { c -> options.noise.indexOf(c).takeIf { it >= 0 } }
                    is WidgetSlot.Eq -> listOfNotNull(options.eq.indexOfFirst { it.first == slot.profileId }.takeIf { it >= 0 })
                    is WidgetSlot.EqCycle -> slot.profileIds.mapNotNull { id -> options.eq.indexOfFirst { it.first == id }.takeIf { it >= 0 } }
                    WidgetSlot.Empty -> emptyList()
                },
            )
        }
        val cycle = kind == Kind.NOISE_CYCLE || kind == Kind.EQ_CYCLE
        val labels = when (kind) {
            Kind.NOISE, Kind.NOISE_CYCLE -> noiseLabels
            Kind.EQ, Kind.EQ_CYCLE -> eqLabels
            Kind.EMPTY -> emptyList()
        }
        val valid = when (kind) {
            Kind.EMPTY -> true
            Kind.NOISE, Kind.EQ -> ticked.size == 1
            else -> ticked.size >= 2
        }
        StudioDialog(stringResource(R.string.widget_title), onDismiss) {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in kinds) Chip(stringResource(k.label), k == kind) {
                    if (k != kind) {
                        kind = k
                        ticked = emptyList()
                    }
                }
                if (cycle) Text(stringResource(R.string.widget_cycle_hint), color = Studio.colors.secondaryText)
                labels.forEachIndexed { index, label ->
                    val position = ticked.indexOf(index)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).selectable(position >= 0, role = if (cycle) Role.Checkbox else Role.RadioButton) {
                            ticked = when {
                                position >= 0 -> ticked - index
                                cycle -> ticked + index
                                else -> listOf(index)
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Label(if (position >= 0) (if (cycle) "${position + 1}" else "●") else "○", Modifier.padding(end = 4.dp), Studio.colors.accent)
                        Text(label)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StudioButton(stringResource(R.string.widget_cancel), onDismiss, Modifier.weight(1f))
                StudioButton(
                    stringResource(R.string.widget_done),
                    {
                        onPick(
                            when (kind) {
                                Kind.EMPTY -> WidgetSlot.Empty
                                Kind.NOISE -> WidgetSlot.Noise(options.noise[ticked.single()])
                                Kind.NOISE_CYCLE -> WidgetSlot.NoiseCycle(ticked.map { options.noise[it] })
                                Kind.EQ -> WidgetSlot.Eq(options.eq[ticked.single()].first)
                                Kind.EQ_CYCLE -> WidgetSlot.EqCycle(ticked.map { options.eq[it].first })
                            },
                        )
                    },
                    Modifier.weight(1f),
                    primary = true,
                    enabled = valid,
                )
            }
        }
    }
}
