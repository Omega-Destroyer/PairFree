package io.github.omegadestroyer.pairfree.widget

import io.github.omegadestroyer.pairfree.Languages
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.LinkStatus
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.hub
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice
import io.github.omegadestroyer.pairfree.protocol.widget.Sub
import io.github.omegadestroyer.pairfree.protocol.widget.WidgetCycle
import io.github.omegadestroyer.pairfree.protocol.widget.WidgetSlot
import io.github.omegadestroyer.pairfree.ui.modelOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Applies a tap on the home screen widget (docs/DESIGN.md, Widget). The channel to the earbuds is closed when PairFree is
 * not open, so the service opens it, sets the noise choice or EQ profile, waits for the write and its read-back, and lets the
 * channel go again unless the screens or the status notification hold it. Taps are handled one after another.
 */
class WidgetService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val taps = Channel<Tap>(Channel.UNLIMITED)
    private var worker = false

    private data class Tap(val widgetId: Int, val slot: Int, val address: String)

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Languages.wrap(newBase))

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android wants the notification at once.
        createChannel(this)
        val syncing = intent?.action == ACTION_SYNC
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(this, syncing), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        when (intent?.action) {
            ACTION_TAP -> taps.trySend(Tap(intent.getIntExtra(EXTRA_WIDGET, -1), intent.getIntExtra(EXTRA_SLOT, -1), intent.getStringExtra(EXTRA_ADDRESS).orEmpty()))
            ACTION_SYNC -> taps.trySend(Tap(-1, SYNC, intent.getStringExtra(EXTRA_ADDRESS).orEmpty()))
        }
        if (!worker) {
            worker = true
            scope.launch { work() }
        }
        return START_NOT_STICKY
    }

    private suspend fun work() {
        while (true) {
            val tap = taps.tryReceive().getOrNull() ?: run {
                // Nothing waiting: give the last write time to reach the earbuds, then let go unless another tap came.
                delay(LET_GO_MS)
                taps.tryReceive().getOrNull()
            } ?: break
            try {
                apply(tap)
            } catch (_: RuntimeException) {
                // A tap that failed leaves the earbuds as they were.
            }
        }
        hub.widget(holds = false)
        worker = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @SuppressLint("MissingPermission") // checked first
    private suspend fun apply(tap: Tap) {
        val hub = hub
        if (!hub.hasPermission()) return
        val device = hub.pairedEarbuds().firstOrNull { it.address == tap.address } ?: return
        if (tap.slot == SYNC) return sync(device)
        val slot = WidgetStore.load(hub.prefs, tap.widgetId)?.devices?.get(tap.address)?.let(WidgetStore::padded)?.getOrNull(tap.slot) ?: return
        if (slot == WidgetSlot.Empty) return
        val connection = hub.connection
        val onIt = (connection.status.value as? LinkStatus.Connected)?.device?.address == tap.address
        // Earbuds that are not connected to the phone: nothing to change, and nothing to open a channel to.
        if (!onIt && !connectedForSound(tap.address)) return PairFreeWidget.requestRefresh(this)
        hub.widget(holds = true)
        if (!onIt) {
            connection.connect(device)
            val opened = withTimeoutOrNull(CONNECT_MS) {
                connection.status.first { it is LinkStatus.Connected && it.device.address == tap.address }
            }
            if (opened == null) return
        }
        when (slot) {
            is WidgetSlot.Noise -> setNoise(slot.choice)
            is WidgetSlot.NoiseCycle -> {
                // Which choice is next depends on where the earbuds are now, and they say so a moment after the channel
                // opens: choosing before that finds no choice active and starts the cycle over every time.
                withTimeoutOrNull(STATE_MS) { connection.state.first { it.supports(Command.ANC_MODE) } } ?: return
                awaitSubModes(slot.choices)
                val rows = connection.settings.value.flatMap { it.settings }
                setNoise(WidgetCycle.nextNoise(slot.choices, connection.state.value, rows))
            }
            is WidgetSlot.Eq -> selectEq(device, slot.profileId, null)
            is WidgetSlot.EqCycle -> selectEq(device, null, slot.profileIds)
            WidgetSlot.Empty -> Unit
        }
        PairFreeWidget.requestRefresh(this)
    }

    /**
     * When some choice fixes a sub-mode of the mode the earbuds are in, waits for them to report it (SOUNDPEATS' scene list,
     * or the brand's row), so a choice with a sub-mode is told from a bare mode.
     */
    private suspend fun awaitSubModes(choices: List<NoiseChoice>) {
        val connection = hub.connection
        val mode = connection.state.value.number(Command.ANC_MODE)
        val relevant = choices.filter { it.sub != null && it.modeValue == mode }
        if (relevant.isEmpty()) return
        withTimeoutOrNull(SUB_MS) {
            combine(connection.state, connection.settings) { state, groups -> state to groups }.first { (state, groups) ->
                relevant.any { c ->
                    when (val sub = c.sub) {
                        is Sub.Level -> state.levels(sub.command).isNotEmpty()
                        is Sub.Row -> groups.any { g -> g.settings.any { it.key == sub.key } }
                        is Sub.Step -> groups.any { g -> g.settings.any { it.key == sub.key } }
                        null -> true
                    }
                }
            }
        }
    }

    /**
     * The earbuds have just connected: opens the channel as the app does, so what they report (the noise mode and scene) reaches
     * the widget, and opens the EQ so the one this phone last set goes back on them (docs/DESIGN.md, Widget). The channel
     * then stays open for the idle wait, long enough for the earbuds to answer.
     */
    @SuppressLint("MissingPermission") // checked first
    private suspend fun sync(device: Paired) {
        val hub = hub
        val connection = hub.connection
        val onIt = (connection.status.value as? LinkStatus.Connected)?.device?.address == device.address
        // Android's sound profiles come up a moment after the Bluetooth connection.
        if (!onIt && !connectedForSound(device.address, SYNC_AUDIO_TRIES)) return
        hub.widget(holds = true)
        if (!onIt) {
            connection.connect(device)
            withTimeoutOrNull(CONNECT_MS) {
                connection.status.first { it is LinkStatus.Connected && it.device.address == device.address }
            } ?: return
        }
        withTimeoutOrNull(STATE_MS) { connection.state.first { it.supports(Command.ANC_MODE) } }
        hub.eqOpener.open(device, modelOf(device, connection.state.value))
        PairFreeWidget.requestRefresh(this)
    }

    /** True when the phone has the earbuds at [address] connected for sound; Android's profiles may need a moment to answer. */
    private suspend fun connectedForSound(address: String, tries: Int = AUDIO_TRIES): Boolean {
        for (attempt in 0 until tries) {
            when (hub.audio.connected(address)) {
                true -> return true
                false -> return false
                null -> delay(AUDIO_STEP_MS)
            }
        }
        return false
    }

    private suspend fun setNoise(choice: NoiseChoice) {
        val connection = hub.connection
        withTimeoutOrNull(STATE_MS) { connection.state.first { it.supports(Command.ANC_MODE) } } ?: return
        if (connection.state.value.number(Command.ANC_MODE) != choice.modeValue) {
            connection.set(Command.ANC_MODE, choice.modeValue)
            withTimeoutOrNull(STATE_MS) { connection.state.first { it.number(Command.ANC_MODE) == choice.modeValue } }
            delay(STEP_MS)
        }
        when (val sub = choice.sub) {
            null -> Unit
            is Sub.Level -> {
                val active = connection.state.value.levels(sub.command).any { it.code == sub.code && it.active }
                if (!active) connection.set(sub.command, sub.code)
            }
            is Sub.Row -> {
                // The row shows only in its own mode, so wait for the earbuds to report it.
                val rows = withTimeoutOrNull(STATE_MS) { connection.settings.first { groups -> groups.any { g -> g.settings.any { it.key == sub.key } } } }
                    ?: return
                // A key can name several rows (EarFun's cancelling modes and their sub-modes): is the option the value of one?
                val now = rows.flatMap { it.settings }.any { it.key == sub.key && (it as? Setting.Choice)?.value == sub.option }
                if (!now) connection.changeSetting(SettingChange.Choice(sub.key, sub.option))
            }
            is Sub.Step -> {
                val rows = withTimeoutOrNull(STATE_MS) { connection.settings.first { groups -> groups.any { g -> g.settings.any { it.key == sub.key } } } }
                    ?: return
                val now = rows.flatMap { it.settings }.any { it.key == sub.key && (it as? Setting.Level)?.value == sub.value }
                if (!now) connection.changeSetting(SettingChange.Level(sub.key, sub.value))
            }
        }
    }

    /** Opens the EQ for [device] without a screen, then selects [profileId] or the profile after the selected one in [ids]. */
    private suspend fun selectEq(device: Paired, profileId: String?, ids: List<String>?) {
        val hub = hub
        val wanted = profileId ?: ids?.firstOrNull() ?: return
        var state: io.github.omegadestroyer.pairfree.eq.EqState? = null
        for (attempt in 0 until EQ_TRIES) {
            hub.eqOpener.open(device, modelOf(device, hub.connection.state.value))
            state = hub.equalizer.state.value?.takeIf { hub.equalizer.openFor == device.address && it.profiles.any { p -> p.id == wanted } }
            if (state != null) break
            delay(EQ_STEP_MS)
        }
        val open = state ?: hub.equalizer.state.value?.takeIf { hub.equalizer.openFor == device.address } ?: return
        val target = profileId ?: WidgetCycle.nextEq(ids.orEmpty(), open.selected?.id)
        open.profiles.firstOrNull { it.id == target }?.let(hub.equalizer::select)
    }

    override fun onDestroy() {
        scope.cancel()
        hub.widget(holds = false)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "widget"
        private const val NOTIFICATION_ID = 2
        private const val ACTION_TAP = "io.github.omegadestroyer.pairfree.widget.TAP"
        private const val ACTION_SYNC = "io.github.omegadestroyer.pairfree.widget.SYNC"
        private const val EXTRA_WIDGET = "widget"
        private const val EXTRA_SLOT = "slot"
        private const val EXTRA_ADDRESS = "address"

        /** How long to wait for the channel to open, for the earbuds' state, and between a mode and its sub-mode. */
        private const val CONNECT_MS = 15_000L
        private const val STATE_MS = 5_000L
        private const val STEP_MS = 400L

        /** How long to wait for the earbuds to report a sub-mode they are in. */
        private const val SUB_MS = 2_500L
        private const val AUDIO_TRIES = 8
        private const val SYNC_AUDIO_TRIES = 36
        private const val SYNC = -2
        private const val AUDIO_STEP_MS = 250L
        private const val EQ_TRIES = 20
        private const val EQ_STEP_MS = 500L

        /**
         * After the last write, the channel stays open this long. The connection reads a setting back 3 s after writing it
         * (EarbudsConnection.READ_BACK_MS) and the earbuds answer after that: closing at 3 s left the widget with the state
         * it had guessed, or with a late answer to an older question.
         */
        private const val LET_GO_MS = 6_500L

        /** The tap on button [slot] of widget [widgetId] for the earbuds at [address]. */
        fun tapIntent(context: Context, widgetId: Int, slot: Int, address: String): PendingIntent {
            val intent = Intent(context, WidgetService::class.java).setAction(ACTION_TAP)
                .putExtra(EXTRA_WIDGET, widgetId).putExtra(EXTRA_SLOT, slot).putExtra(EXTRA_ADDRESS, address)
            return PendingIntent.getForegroundService(context, widgetId * 8 + slot, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        /**
         * The earbuds at [address] connected: if a widget has buttons for them, reads their state and puts their EQ back, as the
         * app does. From the background Android allows the service only for earbuds PairFree is linked to (the companion link the
         * status notification asks for), so without it nothing starts.
         */
        fun syncIfWanted(context: Context, address: String) {
            if (!PairFreeWidget.hasButtonsFor(context, address)) return
            val intent = Intent(context, WidgetService::class.java).setAction(ACTION_SYNC).putExtra(EXTRA_ADDRESS, address)
            try {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } catch (_: IllegalStateException) {
                // Not allowed from the background: the widget shows what it last knew until the next tap or visit.
            }
        }

        private fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL, context.getString(R.string.widget_channel), NotificationManager.IMPORTANCE_MIN)
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        private fun notification(context: Context, syncing: Boolean): Notification =
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_status)
                .setContentTitle(if (syncing) context.getString(R.string.widget_syncing) else context.getString(R.string.widget_working, context.getString(R.string.app_name)))
                .setOngoing(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
    }
}
