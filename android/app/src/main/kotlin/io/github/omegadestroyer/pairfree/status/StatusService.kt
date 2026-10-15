package io.github.omegadestroyer.pairfree.status

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import io.github.omegadestroyer.pairfree.MainActivity
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.LinkStatus
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.hub
import io.github.omegadestroyer.pairfree.protocol.BatteryLevel
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.ui.modelOf
import io.github.omegadestroyer.pairfree.ui.numberOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * PairFree's status notification (docs/DESIGN.md, Notification). While earbuds whose choice is
 * PairFree's ([StatusChoice]) are connected, it holds the channel through the [hub] and shows
 * their battery and noise mode, with a button for the next noise mode and one for game mode,
 * each where the earbuds have it. It ends when the earbuds disconnect, when the user picks
 * another choice or other earbuds, or when the channel cannot be opened.
 */
class StatusService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null
    private var device: Paired? = null
    private var shown: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> show(intent.getStringExtra(EXTRA_ADDRESS))
            ACTION_NEXT_NOISE -> nextNoise()
            ACTION_GAME_MODE -> toggleGameMode()
            ACTION_STOP -> {
                val address = intent.getStringExtra(EXTRA_ADDRESS)
                if (address == null || address == device?.address) stop() else if (device == null) stop()
            }
            else -> stop()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission") // checked first
    private fun show(address: String?) {
        createChannel(this)
        val hub = hub
        val target = if (hub.hasPermission()) hub.pairedEarbuds().firstOrNull { it.address == address } else null
        val status = hub.connection.status.value
        val onIt = target != null && (
            (status as? LinkStatus.Connected)?.device?.address == target.address ||
                (status as? LinkStatus.Reconnecting)?.device?.address == target.address
            )
        // Android wants the notification at once, on every start: the earbuds as they stand when
        // the channel is open already, else connecting. A placeholder here would stay until the
        // earbuds next change something.
        val first = if (onIt) StatusNotification.build(this, target, hub.connection.state.value, status) else StatusNotification.build(this, target, EarbudState(), LinkStatus.Idle)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        shown = key(first)
        if (target == null) return stop()
        device = target
        hub.notification(holds = true)
        if (!onIt) hub.connection.connect(target)
        if (watching == null) watching = scope.launch { watch() }
    }

    /** What a notification says, to post it again only when that changes. */
    private fun key(notification: Notification): String =
        notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString() + "|" +
            notification.extras.getCharSequence(Notification.EXTRA_TEXT) + "|" + notification.actions?.joinToString { it.title }

    /** Follows the connection: updates the notification, opens the channel again a few times while the earbuds finish connecting, and ends when it is gone. */
    @SuppressLint("MissingPermission") // the permission was there when the notification started
    private suspend fun watch() {
        val hub = hub
        var retries = 0
        combine(hub.connection.status, hub.connection.state) { s, st -> s to st }.collect { (status, state) ->
            val current = device ?: return@collect
            when (status) {
                is LinkStatus.Idle -> stop()
                is LinkStatus.Connected -> {
                    retries = 0
                    if (status.device.address != current.address) {
                        // The screens switched earbuds: follow them if the user wants this notification for them.
                        if (wanted(this, status.device)) device = status.device else return@collect stop()
                    }
                }
                is LinkStatus.Failed -> {
                    if (status.device.address != current.address) return@collect stop()
                    if (status.reason == LinkStatus.Reason.COULD_NOT_OPEN && retries < MAX_RETRIES && hub.hasPermission()) {
                        retries++
                        delay(RETRY_MS)
                        hub.connection.connect(current)
                    } else {
                        return@collect stop()
                    }
                }
                else -> Unit
            }
            val notification = StatusNotification.build(this, device, state, status)
            val key = key(notification)
            if (key != shown) {
                shown = key
                if (canNotify(this)) NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
            }
        }
    }

    /** Off, cancelling, transparency, and round again, in the order the Home tab shows them. */
    private fun nextNoise() {
        val connection = hub.connection
        if (connection.status.value !is LinkStatus.Connected) return
        val order = listOf("off", "anc", "transparency").map { numberOf(Command.ANC_MODE, it) }
        val now = order.indexOf(connection.state.value.number(Command.ANC_MODE))
        connection.set(Command.ANC_MODE, order[(now + 1) % order.size])
    }

    private fun toggleGameMode() {
        val connection = hub.connection
        val on = connection.state.value.switch(Command.GAME_MODE) ?: return
        connection.set(Command.GAME_MODE, if (on) 0 else 1)
    }

    private fun stop() {
        watching?.cancel()
        watching = null
        device = null
        shown = null
        hub.notification(holds = false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        hub.notification(holds = false)
        super.onDestroy()
    }

    companion object {
        internal const val CHANNEL = "status"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_SHOW = "io.github.omegadestroyer.pairfree.status.SHOW"
        private const val ACTION_STOP = "io.github.omegadestroyer.pairfree.status.STOP"
        internal const val ACTION_NEXT_NOISE = "io.github.omegadestroyer.pairfree.status.NEXT_NOISE"
        internal const val ACTION_GAME_MODE = "io.github.omegadestroyer.pairfree.status.GAME_MODE"
        private const val EXTRA_ADDRESS = "address"

        /** Earbuds can take a few seconds after connecting before their control channel opens. */
        private const val MAX_RETRIES = 5
        private const val RETRY_MS = 3_000L

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL, context.getString(R.string.status_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.status_channel_description)
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        /** True when the user wants PairFree's notification for [device], and Android lets PairFree show it. */
        fun wanted(context: Context, device: Paired): Boolean {
            val settings = StatusSettings(context.hub.prefs)
            // The firmware the earbuds reported names a SOUNDPEATS model better than its Bluetooth name.
            val connection = context.hub.connection
            val connectedTo = (connection.status.value as? LinkStatus.Connected)?.device?.address
            val model = modelOf(device, if (connectedTo == device.address) connection.state.value else EarbudState())
            return settings.choice(device, settings.fastPair(device, model)) == StatusChoice.PAIRFREE && canNotify(context)
        }

        /**
         * Starts the notification for the earbuds at [address] if the user wants it. From the
         * screens it can always start; when the earbuds connect with PairFree closed, Android
         * allows it only once the user has linked PairFree to them ([CompanionLink]).
         */
        fun startIfWanted(context: Context, address: String, fromBackground: Boolean) {
            val hub = context.hub
            if (!hub.hasPermission()) return
            val device = hub.pairedEarbuds().firstOrNull { it.address == address } ?: return
            if (!wanted(context, device)) return
            if (fromBackground && !CompanionLink.isAssociated(context, address)) return
            val intent = Intent(context, StatusService::class.java).setAction(ACTION_SHOW).putExtra(EXTRA_ADDRESS, address)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: IllegalStateException) {
                // Android refused a start from the background; the notification shows next time PairFree is open.
            }
        }

        /** Ends the notification, for the earbuds at [address] only when one is given. */
        fun stop(context: Context, address: String? = null) {
            val intent = Intent(context, StatusService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_ADDRESS, address)
            try {
                context.startService(intent)
            } catch (_: IllegalStateException) {
                // Not running, and not allowed to start from the background: nothing to stop.
            }
        }
    }
}

/** Starts the status notification when supported earbuds connect, and ends it when they disconnect. */
class ConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> StatusService.startIfWanted(context, device.address, fromBackground = true)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> StatusService.stop(context, device.address)
        }
    }
}

/** The status notification's look: the earbuds' name, their battery and noise mode, and the buttons they have. */
internal object StatusNotification {
    fun build(context: Context, device: Paired?, state: EarbudState, status: LinkStatus): Notification = with(context) {
        val text = when (status) {
            is LinkStatus.Connected -> listOfNotNull(batteryLine(state), noiseName(state)).joinToString("  ·  ").ifEmpty { getString(R.string.status_connected) }
            is LinkStatus.Reconnecting -> getString(R.string.status_reconnecting)
            else -> getString(R.string.status_connecting)
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, StatusService.CHANNEL)
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle(device?.name ?: getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (status is LinkStatus.Connected) {
            if (state.supports(Command.ANC_MODE)) builder.addAction(0, getString(R.string.status_next_noise), action(context, StatusService.ACTION_NEXT_NOISE))
            state.switch(Command.GAME_MODE)?.let { on ->
                builder.addAction(0, getString(if (on) R.string.status_game_off else R.string.status_game_on), action(context, StatusService.ACTION_GAME_MODE))
            }
        }
        builder.build()
    }

    private fun action(context: Context, action: String): PendingIntent =
        PendingIntent.getService(context, action.hashCode(), Intent(context, StatusService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)

    /** Left, case and right, as the earbuds report them: percent, or steps such as 3/5. One battery shows alone. */
    private fun Context.batteryLine(state: EarbudState): String? {
        fun text(level: BatteryLevel?): String? {
            val l = level?.level ?: return null
            return if (level.isPercent) "$l%" else "$l/${level.levels}"
        }
        val left = text(state.battery(Command.BATTERY_LEFT))
        val case = text(state.battery(Command.BATTERY_CASE))
        val right = text(state.battery(Command.BATTERY_RIGHT))
        if (right == null && case == null) return left?.let { getString(R.string.status_battery_one, it) }
        return listOfNotNull(
            left?.let { getString(R.string.status_battery_left, it) },
            case?.let { getString(R.string.status_battery_case, it) },
            right?.let { getString(R.string.status_battery_right, it) },
        ).joinToString("  ").ifEmpty { null }
    }

    private fun Context.noiseName(state: EarbudState): String? = when (state.number(Command.ANC_MODE)) {
        numberOf(Command.ANC_MODE, "off") -> getString(R.string.anc_off)
        numberOf(Command.ANC_MODE, "anc") -> getString(R.string.anc_cancelling)
        numberOf(Command.ANC_MODE, "transparency") -> getString(R.string.anc_transparency)
        else -> null
    }
}
