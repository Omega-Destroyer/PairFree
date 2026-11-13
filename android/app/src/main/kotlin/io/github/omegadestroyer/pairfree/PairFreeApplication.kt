package io.github.omegadestroyer.pairfree


import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import io.github.omegadestroyer.pairfree.bluetooth.AudioLinks
import io.github.omegadestroyer.pairfree.brands.Brands
import io.github.omegadestroyer.pairfree.bluetooth.EarbudsConnection
import io.github.omegadestroyer.pairfree.bluetooth.LinkStatus
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.EqOpener
import io.github.omegadestroyer.pairfree.eq.Equalizer
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Identify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import io.github.omegadestroyer.pairfree.widget.PairFreeWidget
import io.github.omegadestroyer.pairfree.widget.WidgetCache
import io.github.omegadestroyer.pairfree.widget.WidgetNoise
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import java.util.TimeZone

/** The app process; it holds the [Hub] the screens and the status notification share. */
class PairFreeApplication : Application() {
    override fun attachBaseContext(base: Context) = super.attachBaseContext(Languages.wrap(base))

    val hub: Hub by lazy { Hub(this) }
}

/** What the home screen widget follows: the channel, the earbuds' state and rows, and the EQ. */
private data class WidgetInputs(
    val status: LinkStatus,
    val state: EarbudState,
    val groups: List<io.github.omegadestroyer.pairfree.protocol.SettingGroup>,
    val eq: io.github.omegadestroyer.pairfree.eq.EqState?,
)

/** The process's [Hub]. */
val Context.hub: Hub get() = (applicationContext as PairFreeApplication).hub

/**
 * What outlives the screens: the connection to the earbuds, the EQ this phone keeps for them,
 * and the notes of when PairFree's own channel was open. The screens and the status
 * notification share them. The channel stays open while either needs it and closes when
 * neither does (docs/DESIGN.md, Notification).
 */
class Hub(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prefs: SharedPreferences = app.getSharedPreferences("pairfree", Context.MODE_PRIVATE)
    val adapter: BluetoothAdapter? = app.getSystemService(BluetoothManager::class.java)?.adapter

    /** Debug builds log every frame to Logcat under the tag `PairFree`; release builds log nothing. */
    val connection = EarbudsConnection(
        scope,
        adapter,
        context = app,
        log = if (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) { line -> Log.d(LOG_TAG, line) } else null,
        assistantIsGoogle = ::assistantIsGoogle,
    )

    /**
     * Whether the phone's digital assistant is Google's, as Samsung's plugin decides it for the Buds4
     * family's touch-and-hold option. Android may not let an app read the setting; Google is then assumed.
     */
    private fun assistantIsGoogle(): Boolean = try {
        val assistant = Settings.Secure.getString(app.contentResolver, "assistant").orEmpty()
        assistant.isEmpty() || assistant.substringBefore('/') == GOOGLE_ASSISTANT
    } catch (_: SecurityException) {
        true
    }

    /** Whether paired earbuds are connected to the phone for sound, for the status notification. */
    val audio = AudioLinks(app, adapter)

    /** EQ profiles and the current EQ, kept on the phone; changes go out over [connection]. */
    val equalizer = Equalizer(prefs, scope, { frames -> connection.sendEq(frames) }, { app.getString(R.string.eq_imported_name) })

    /** Opens [equalizer] for the connected earbuds of any brand; the Sound tab and the home screen widget share it. */
    val eqOpener = EqOpener(connection, equalizer, scope)

    private var screensVisible = false
    private var notificationHolds = false
    private var widgetHolds = false

    /** The screens are on view, or not: they need the channel only while they are. */
    fun screens(visible: Boolean) {
        screensVisible = visible
        releaseIfUnused()
    }

    /** The status notification holds the channel, or lets it go. */
    fun notification(holds: Boolean) {
        notificationHolds = holds
        releaseIfUnused()
    }

    /** The home screen widget holds the channel while it applies a tap, or lets it go. */
    fun widget(holds: Boolean) {
        widgetHolds = holds
        releaseIfUnused()
    }

    private fun releaseIfUnused() {
        if (!screensVisible && !notificationHolds && !widgetHolds) connection.disconnect()
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /**
     * Paired earbuds PairFree can control, of every brand. Only earbuds the app can control, not every
     * bonded audio device; the AUDIO_VIDEO class also drops watches. The brand comes from [Identify]
     * (a brand's own service, else a brand word in the name, else an exact model name; data/identify.json),
     * then that brand's code picks the model and service (each brand's PROTOCOL.md). Empty without the
     * permission or with Bluetooth off.
     */
    @SuppressLint("MissingPermission") // checked first
    fun pairedEarbuds(): List<Paired> {
        if (!hasPermission() || adapter?.isEnabled != true) return emptyList()
        return adapter.bondedDevices
            .filter { it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO }
            .mapNotNull { d ->
                val name = d.name ?: d.address
                val uuids = d.uuids.orEmpty().map { it.uuid.toString() }
                // The brand first (Identify, data/identify.json), then that brand's own code picks the model and the
                // service to open ([io.github.omegadestroyer.pairfree.brands.BrandSpec.paired]); a brand's earbuds it cannot
                // control (Sony's LE side, 1MORE's Tuya models, EarFun's on other chips) drop out there.
                Identify.maker(name, uuids)?.let { Brands[it].paired(d.address, name, uuids) }
            }
            .sortedBy { it.name }
    }

    /** When the current channel opened, on [snoopClockMs]; null while none is open. */
    private var openSince: Long? = null

    /** Now, as a snoop log counts it: local wall-clock time read as UTC (SnoopScanner.Found). */
    fun snoopClockMs(): Long {
        val now = System.currentTimeMillis()
        return now + TimeZone.getDefault().getOffset(now)
    }

    /** PairFree's own channel sessions of the last days, the open one included, widened by [OWN_MARGIN_MS]. */
    fun ownSessions(): List<LongRange> {
        val open = openSince?.let { listOf(it..snoopClockMs()) }.orEmpty()
        return (storedSessions() + open).map { (it.first - OWN_MARGIN_MS)..(it.last + OWN_MARGIN_MS) }
    }

    private fun storedSessions(): List<LongRange> = try {
        val a = JSONArray(prefs.getString(KEY_OWN_SESSIONS, "[]"))
        List(a.length()) { a.getJSONArray(it).let { s -> s.getLong(0)..s.getLong(1) } }
    } catch (_: JSONException) {
        emptyList()
    }

    private fun storeOwnSession(start: Long, end: Long) {
        val keep = storedSessions().filter { it.last > end - OWN_SESSIONS_KEPT_MS } + listOf(start..end)
        val a = JSONArray()
        for (s in keep.takeLast(MAX_OWN_SESSIONS)) a.put(JSONArray().put(s.first).put(s.last))
        prefs.edit { putString(KEY_OWN_SESSIONS, a.toString()) }
    }

    init {
        // SoundPEATS earbuds cannot report their EQ, so each newly opened channel gets this phone's back.
        // It also notes when its channel is open, so a recording can leave out what it sent itself.
        scope.launch {
            connection.status.collect {
                if (it is LinkStatus.Connected) {
                    equalizer.channelOpened(it.device.address)
                    if (openSince == null) openSince = snoopClockMs()
                } else {
                    openSince?.let { start -> storeOwnSession(start, snoopClockMs()) }
                    openSince = null
                }
            }
        }
    }

    init {
        // The home screen widget follows the earbuds and their state, and remembers what connected earbuds show that the tables
        // do not give (docs/DESIGN.md, Widget).
        scope.launch {
            combine(connection.status, connection.state, connection.settings, equalizer.state) { status, state, groups, eq -> WidgetInputs(status, state, groups, eq) }.collect { (status, state, groups, eq) ->
                if (status is LinkStatus.Connected) {
                    val device = status.device
                    WidgetCache.record(prefs, device, state, eq.takeIf { equalizer.openFor == device.address })
                    WidgetNoise.record(prefs, device.address, state, groups)
                }
                PairFreeWidget.requestRefresh(app, scope)
            }
        }
    }

    companion object {
        const val LOG_TAG = "PairFree"
        private const val GOOGLE_ASSISTANT = "com.google.android.googlequicksearchbox"
        private const val KEY_OWN_SESSIONS = "own_sessions"

        /** Frames this close to PairFree's own session count as its own. */
        private const val OWN_MARGIN_MS = 1_500L
        private const val OWN_SESSIONS_KEPT_MS = 7L * 24 * 60 * 60 * 1000
        private const val MAX_OWN_SESSIONS = 200
    }
}
