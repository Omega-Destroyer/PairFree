package io.github.omegadestroyer.pairfree.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.LinkStatus
import io.github.omegadestroyer.pairfree.hub
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice
import io.github.omegadestroyer.pairfree.protocol.widget.Sub
import io.github.omegadestroyer.pairfree.protocol.widget.WidgetGrid
import io.github.omegadestroyer.pairfree.protocol.widget.WidgetSlot
import io.github.omegadestroyer.pairfree.ui.sceneLabel
import io.github.omegadestroyer.pairfree.ui.transparencyLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The words on a widget button, for the widget and for its setup screen. */
object WidgetLabels {
    fun noise(context: Context, c: NoiseChoice): String {
        val mode = context.getString(
            when (c.mode) {
                "off" -> R.string.anc_off
                "anc" -> R.string.anc_cancelling
                else -> R.string.anc_transparency
            },
        )
        val sub = when (val s = c.sub) {
            null -> return mode
            is Sub.Level -> if (s.command.name == "ANC_LEVEL") context.sceneLabel(s.code) else context.transparencyLabel(s.code)
            is Sub.Row -> humanize(s.option)
        }
        return "$mode · $sub"
    }

    /** `anti_wind` as "Anti wind": a brand row's option where the app's own words are only reachable from a composition. */
    fun humanize(option: String): String = option.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/**
 * PairFree's home screen widget (docs/DESIGN.md, Widget): up to eight buttons that set a noise control choice, step through
 * some, select an EQ profile or step through some, for whichever of the user's earbuds is connected. A launcher gives
 * a size from 1x1 to 4x2 cells; each size shows its first buttons. Taps go to [WidgetService].
 */
class PairFreeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context, ids)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: android.os.Bundle) =
        refresh(context, intArrayOf(id))

    override fun onDeleted(context: Context, ids: IntArray) {
        for (id in ids) WidgetStore.delete(context.hub.prefs, id)
    }

    companion object {
        private var pending: Job? = null
        private var lastRetry = 0L
        private const val RETRY_GAP_MS = 10_000L

        /**
         * Draws every widget again shortly: many changes arrive together, so they draw once. The Hub passes its own [scope]
         * while it is being built, when asking it for the Hub would start it a second time.
         */
        fun requestRefresh(context: Context, scope: CoroutineScope = context.applicationContext.hub.scope) {
            val app = context.applicationContext
            pending?.cancel()
            pending = scope.launch {
                delay(REFRESH_DELAY_MS)
                refreshAll(app)
            }
        }

        fun refreshAll(context: Context) {
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, PairFreeWidget::class.java))
            if (ids.isNotEmpty()) refresh(context, ids)
        }

        fun refresh(context: Context, ids: IntArray) {
            val manager = AppWidgetManager.getInstance(context)
            for (id in ids) manager.updateAppWidget(id, build(context, id))
        }

        private const val REFRESH_DELAY_MS = 300L
        private const val DIMMED = 0.45f

        private fun build(context: Context, id: Int): RemoteViews {
            val hub = context.hub
            val config = WidgetStore.load(hub.prefs, id)
            val shown = config?.let { shownDevice(context, it) }
            if (config == null || shown == null) return setupViews(context, id)
            val (address, live) = shown
            val device = if (hub.hasPermission()) hub.pairedEarbuds().firstOrNull { it.address == address } else null
            val slots = WidgetStore.padded(config.devices[address].orEmpty())
            val choices = device?.let { WidgetOptions.of(context, it) }
            val names = choices?.eq?.toMap().orEmpty()
            val connection = hub.connection
            val onChannel = (connection.status.value as? LinkStatus.Connected)?.device?.address == address
            val rows = if (onChannel) connection.settings.value.flatMap { it.settings } else emptyList()
            val state = connection.state.value
            val selectedEq = if (hub.equalizer.openFor == address) hub.equalizer.state.value?.selected?.id else null
            val views = buildMap {
                for (c in 1..WidgetGrid.PER_ROW) for (r in 1..2) {
                    put(SizeF(WidgetGrid.minWidthDp(c), WidgetGrid.minHeightDp(r)), sizeViews(context, id, address, slots, c, r, live, onChannel, rows, state, selectedEq, names))
                }
            }
            return RemoteViews(views)
        }

        /** Which earbuds' buttons to show: the connected pair, else one the phone has connected for sound, else the last one shown. */
        private fun shownDevice(context: Context, config: WidgetConfig): Pair<String, Boolean>? {
            val hub = context.hub
            val addresses = config.devices.filterValues { slots -> slots.any { it != WidgetSlot.Empty } }.keys
            if (addresses.isEmpty()) return null
            val channel = (hub.connection.status.value as? LinkStatus.Connected)?.device?.address
            if (channel != null && channel in addresses) return channel to true
            addresses.firstOrNull { hub.audio.connected(it) == true }?.let { return it to true }
            // Android's sound profiles may not have answered yet: look again once, not in a loop.
            val now = System.currentTimeMillis()
            if (hub.hasPermission() && now - lastRetry > RETRY_GAP_MS) {
                lastRetry = now
                hub.scope.launch { delay(1_000); requestRefresh(context) }
            }
            return (config.last?.takeIf { it in addresses } ?: addresses.first()) to false
        }

        private fun setupViews(context: Context, id: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget)
            views.setViewVisibility(R.id.widget_row_1, View.GONE)
            views.setViewVisibility(R.id.widget_row_2, View.GONE)
            views.setViewVisibility(R.id.widget_setup, View.VISIBLE)
            val configure = Intent(context, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, id, configure, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            return views
        }

        private fun sizeViews(
            context: Context,
            id: Int,
            address: String,
            slots: List<WidgetSlot>,
            cols: Int,
            rows: Int,
            live: Boolean,
            onChannel: Boolean,
            settings: List<Setting>,
            state: io.github.omegadestroyer.pairfree.protocol.EarbudState,
            selectedEq: String?,
            eqNames: Map<String, String>,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget)
            views.setViewVisibility(R.id.widget_row_2, if (rows >= 2) View.VISIBLE else View.GONE)
            val visible = WidgetGrid.visibleSlots(cols, rows)
            for (i in 0 until WidgetGrid.SLOTS) {
                val slot = slots[i]
                val cell = CELLS[i]
                if (i !in visible) {
                    views.setViewVisibility(cell[0], View.GONE)
                    continue
                }
                views.setViewVisibility(cell[0], View.VISIBLE)
                var caption = ""
                var label = ""
                var active = false
                when (slot) {
                    WidgetSlot.Empty -> Unit
                    is WidgetSlot.Noise -> {
                        caption = context.getString(R.string.widget_caption_noise)
                        label = WidgetLabels.noise(context, slot.choice)
                        active = onChannel && slot.choice.isActive(state, settings)
                    }
                    is WidgetSlot.NoiseCycle -> {
                        caption = context.getString(R.string.widget_caption_noise)
                        val now = if (onChannel) slot.choices.firstOrNull { it.sub != null && it.isActive(state, settings) } ?: slot.choices.firstOrNull { it.isActive(state, settings) } else null
                        label = WidgetLabels.noise(context, now ?: slot.choices.first())
                        active = now != null
                    }
                    is WidgetSlot.Eq -> {
                        caption = context.getString(R.string.widget_caption_eq)
                        label = eqNames[slot.profileId] ?: context.getString(R.string.widget_profile_missing)
                        active = selectedEq == slot.profileId
                    }
                    is WidgetSlot.EqCycle -> {
                        caption = context.getString(R.string.widget_caption_eq)
                        val now = slot.profileIds.firstOrNull { it == selectedEq }
                        label = eqNames[now ?: slot.profileIds.first()] ?: context.getString(R.string.widget_profile_missing)
                        active = now != null
                    }
                }
                views.setTextViewText(cell[1], caption)
                views.setTextViewText(cell[2], label)
                views.setInt(cell[0], "setBackgroundResource", if (active) R.drawable.widget_button_active else R.drawable.widget_button)
                views.setTextColor(cell[2], context.getColor(if (active) R.color.widget_on_accent else R.color.widget_text))
                views.setTextColor(cell[1], context.getColor(if (active) R.color.widget_on_accent else R.color.widget_secondary))
                views.setFloat(cell[0], "setAlpha", if (live || slot == WidgetSlot.Empty) 1f else DIMMED)
                if (slot != WidgetSlot.Empty) {
                    views.setContentDescription(cell[0], context.getString(R.string.widget_slot_description, caption, label))
                    views.setOnClickPendingIntent(cell[0], WidgetService.tapIntent(context, id, i, address))
                }
            }
            return views
        }

        /** The ids of each slot's cell, caption and label in the layout. */
        private val CELLS = listOf(
            intArrayOf(R.id.widget_slot_0, R.id.widget_caption_0, R.id.widget_label_0),
            intArrayOf(R.id.widget_slot_1, R.id.widget_caption_1, R.id.widget_label_1),
            intArrayOf(R.id.widget_slot_2, R.id.widget_caption_2, R.id.widget_label_2),
            intArrayOf(R.id.widget_slot_3, R.id.widget_caption_3, R.id.widget_label_3),
            intArrayOf(R.id.widget_slot_4, R.id.widget_caption_4, R.id.widget_label_4),
            intArrayOf(R.id.widget_slot_5, R.id.widget_caption_5, R.id.widget_label_5),
            intArrayOf(R.id.widget_slot_6, R.id.widget_caption_6, R.id.widget_label_6),
            intArrayOf(R.id.widget_slot_7, R.id.widget_caption_7, R.id.widget_label_7),
        )
    }
}
