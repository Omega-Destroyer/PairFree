package io.github.omegadestroyer.pairfree

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.EarbudsConnection
import io.github.omegadestroyer.pairfree.bluetooth.LinkStatus
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.capture.BugReport
import io.github.omegadestroyer.pairfree.capture.Capture
import io.github.omegadestroyer.pairfree.capture.CaptureResult
import io.github.omegadestroyer.pairfree.eq.Equalizer
import io.github.omegadestroyer.pairfree.eq.SoundcoreEqTarget
import io.github.omegadestroyer.pairfree.eq.SoundpeatsEqTarget
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.CurveMatch
import io.github.omegadestroyer.pairfree.protocol.DeviceReport
import io.github.omegadestroyer.pairfree.protocol.EqCapture
import io.github.omegadestroyer.pairfree.protocol.LoggedReads
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.Models
import io.github.omegadestroyer.pairfree.protocol.RecordingReport
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreButtons
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreEqSpec
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreLayout
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreModels
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreState
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreTouch
import io.github.omegadestroyer.pairfree.ui.CaptureActions
import io.github.omegadestroyer.pairfree.ui.EarbudsActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.TimeZone

/**
 * Holds the connection while the app is on screen. The app talks to the earbuds only
 * while it is visible (docs/DESIGN.md): [onStart] reconnects to the last earbuds,
 * [onStop] closes the channel.
 */
class AppViewModel(app: Application) : AndroidViewModel(app), EarbudsActions, CaptureActions {
    private val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
    // Keeps the app's old name: renamed, every installed copy would lose its settings.
    private val prefs = app.getSharedPreferences("soundpeter", Context.MODE_PRIVATE)

    /** Debug builds log every frame to Logcat under the tag `PairFree`; release builds log nothing. */
    val connection = EarbudsConnection(
        viewModelScope,
        adapter,
        log = if (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) { line -> Log.d(LOG_TAG, line) } else null,
    )

    /** EQ profiles and the current EQ, kept on the phone; changes go out over [connection]. */
    val equalizer = Equalizer(prefs, viewModelScope) { frames -> connection.sendEq(frames) }

    /** The Soundcore EQ while the Sound tab shows Soundcore earbuds whose EQ write is confirmed. */
    private var soundcoreEq: SoundcoreEqTarget? = null

    /**
     * Opens the Sound tab's EQ for [device]: SoundPEATS models from their table, Soundcore models
     * whose EQ write a recording or OpenSCQ30's tests confirm (docs/soundcore/PROTOCOL.md section 8).
     */
    fun openEqualizer(device: Paired, model: Model?) {
        if (device.brand != Brand.SOUNDCORE) {
            soundcoreEq = null
            equalizer.open(device.address, model)
            return
        }
        val spec = SoundcoreEqSpec.of(device.model)?.takeIf { it.checked != null }
        val target = spec?.let { SoundcoreEqTarget(it, { connection.soundcoreState.value }, SoundcoreLayout.of(it.model)?.checksum ?: true) }
        soundcoreEq = target
        equalizer.open(device.address, model, target?.layout, target ?: SoundpeatsEqTarget)
        connection.soundcoreState.value?.let(::reportEq)
    }

    private fun reportEq(state: SoundcoreState) {
        val (profile, gains) = soundcoreEq?.reported(state) ?: return
        equalizer.reported(profile, gains)
    }

    init {
        // Soundcore earbuds report their EQ with every state; the Sound tab follows it.
        viewModelScope.launch { connection.soundcoreState.collect { s -> s?.let(::reportEq) } }
        // SoundPEATS earbuds cannot report their EQ, so each newly opened channel gets this phone's back.
        // It also notes when its channel is open, so a recording can leave out what it sent itself.
        viewModelScope.launch {
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

    /** When the current channel opened, on [snoopClockMs]; null while none is open. */
    private var openSince: Long? = null

    /** Now, as a snoop log counts it: local wall-clock time read as UTC (SnoopScanner.Found). */
    private fun snoopClockMs(): Long {
        val now = System.currentTimeMillis()
        return now + TimeZone.getDefault().getOffset(now)
    }

    /** PairFree's own channel sessions of the last days, the open one included, widened by [OWN_MARGIN_MS]. */
    private fun ownSessions(): List<LongRange> {
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

    private val _hasPermission = MutableStateFlow(checkPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _paired = MutableStateFlow<List<Paired>>(emptyList())

    /** Paired earbuds PairFree can control, of every brand, by name. */
    val paired: StateFlow<List<Paired>> = _paired.asStateFlow()

    private val _bluetoothOn = MutableStateFlow(true)
    val bluetoothOn: StateFlow<Boolean> = _bluetoothOn.asStateFlow()

    fun onStart() {
        _hasPermission.value = checkPermission()
        if (!_hasPermission.value) return
        refreshPaired()
        val last = prefs.getString(KEY_LAST, null) ?: return
        if (connection.status.value is LinkStatus.Idle) {
            _paired.value.firstOrNull { it.address == last }?.let(::connect)
        }
    }

    fun onStop() = connection.disconnect()

    fun onPermissionResult(granted: Boolean) {
        _hasPermission.value = granted
        if (granted) onStart()
    }

    @SuppressLint("MissingPermission") // only reachable once the permission is granted
    fun connect(device: Paired) {
        if (!checkPermission()) {
            _hasPermission.value = false
            return
        }
        prefs.edit { putString(KEY_LAST, device.address) }
        connection.connect(device)
    }

    /** Back to the list of paired earbuds, and no automatic reconnect until one is chosen. */
    override fun switchEarbuds() {
        prefs.edit { remove(KEY_LAST) }
        connection.disconnect()
        refreshPaired()
    }

    override fun set(command: Command, vararg payload: Int) = connection.set(command, *payload)

    override fun factoryResetConfirmedByUser() = connection.factoryResetConfirmedByUser()

    override fun changeTouch(make: (SoundcoreTouch) -> SoundcoreButtons.Change?) = connection.changeSoundcoreTouch(make)

    override fun resetTouch() = connection.resetSoundcoreTouch()

    override fun startFitTest() {
        val name = (connection.status.value as? LinkStatus.Connected)?.device?.name
        val model = Models.find(connection.state.value.firmware?.code, name)
        connection.startFitTest(keepAnc = model?.fitKeepsAnc == true)
    }

    /**
     * A complete, pasteable capture of what the earbuds answered, so a model can be added
     * from a user's phone with no developer mode and no Bluetooth snooping. On connect the
     * app sends every read it knows (Requests.ON_CONNECT); this formats the replies the same
     * way tools/probe.py does, under a header that identifies the model and says where to paste.
     */
    override fun deviceReport(): String {
        val app = getApplication<Application>()
        val version = app.packageManager.getPackageInfo(app.packageName, 0).versionName
        val device = (connection.status.value as? LinkStatus.Connected)?.device
        val firmware = connection.state.value.firmware
        val date = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").format(LocalDateTime.now(ZoneOffset.UTC))
        connection.soundcoreSession?.let { session ->
            return buildString {
                appendLine("PairFree device report")
                appendLine("Paste this whole report into a new issue at")
                appendLine("$ISSUES to help add support")
                appendLine("for this model. The serial number's Bluetooth address is masked.")
                appendLine()
                appendLine("Bluetooth name:  ${device?.name.orEmpty()}")
                appendLine("Brand:           Soundcore")
                appendLine("Model:           ${session.model}")
                appendLine("Service:         ${device?.service ?: "serial port"}")
                appendLine("Firmware:        ${session.lastState?.firmware ?: "unknown"}")
                appendLine("App:             PairFree $version")
                appendLine("OS:              Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Captured:        $date")
                appendLine()
                val expected = session.layout?.minSize?.let { "at least $it bytes expected" } ?: "no layout"
                val got = session.lastState?.size?.let { "$it bytes" } ?: "no reply yet"
                appendLine("The earbuds' reply to the state request (01 01) ($got, $expected), as hex:")
                appendLine(session.lastState?.reportHex() ?: "no reply yet")
            }
        }
        return buildString {
            appendLine("PairFree device report")
            appendLine("Paste this whole report into a new issue at")
            appendLine("$ISSUES to help add support")
            appendLine("for this model. It holds no account, address or other personal data.")
            appendLine()
            appendLine("Bluetooth name:  ${device?.name.orEmpty()}")
            appendLine("Firmware:        ${firmware?.text ?: "unknown"}")
            appendLine("Firmware code:   ${firmware?.code ?: "unknown"}")
            appendLine("App:             PairFree $version (SPP)")
            appendLine("OS:              Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Captured:        $date")
            appendLine()
            appendLine("The app asked the earbuds every read it knows. Each line is the feature,")
            appendLine("its command, the decoded reply and the raw bytes. \"no reply\" means the")
            appendLine("firmware does not support that read.")
            appendLine()
            DeviceReport.lines(connection.lastReplies).forEach(::appendLine)
            connection.state.value.gaiaFeatures?.let { appendLine(DeviceReport.gaiaLine(it)) }
            appendLine()
            appendLine("This is enough to add this model's controls. For its equalizer too, use")
            appendLine("\"Record from PeatsAudio\" on PairFree's Device tab instead.")
        }
    }

    private val _capture = MutableStateFlow<Capture>(Capture.Closed)

    /** The EQ capture screen (docs/DESIGN.md, EQ capture). Not [Capture.Closed] while it is open. */
    val capture: StateFlow<Capture> = _capture.asStateFlow()

    override fun openCapture() {
        if (_capture.value != Capture.Closed) return
        _capture.value = Capture.Guide
        // A recording starts here: what is older in the Bluetooth log is left out. Opening the
        // screen again during the same run keeps the first time.
        val opened = prefs.getLong(KEY_RECORD_OPENED, 0)
        if (snoopClockMs() - opened > RECORD_RUN_MS) prefs.edit { putLong(KEY_RECORD_OPENED, snoopClockMs()) }
    }

    override fun closeCapture() {
        // A finished recording ends the run; the next one starts afresh.
        if (_capture.value is Capture.Done) prefs.edit { remove(KEY_RECORD_OPENED) }
        _capture.value = Capture.Closed
    }

    /** Shows everything in the log, or only the recording again. */
    override fun showWholeLog(whole: Boolean) {
        val done = _capture.value as? Capture.Done ?: return
        _capture.value = analyse(done.result, whole)
    }

    /**
     * Works out the recording from what the file held: where it starts (RecordingReport.start),
     * its EQ curves with the names of those PairFree knows, and the settings read.
     */
    private fun analyse(result: CaptureResult, wholeLog: Boolean): Capture.Done {
        val frames = (result as? CaptureResult.Found)?.frames.orEmpty()
        val opened = prefs.getLong(KEY_RECORD_OPENED, 0).takeIf { it > 0 && snoopClockMs() - it < RECORD_RUN_MS }
        val since = if (wholeLog) null else RecordingReport.start(frames, opened?.times(1000))
        val writes = EqCapture.writes(frames, since)
        val matches = writes.map(CurveMatch::of)
        // Known presets are named already; the user's own slider curves teach nothing and stay out.
        val names = matches.map { (it as? CurveMatch.Preset)?.name }
        return Capture.Done(result, since, wholeLog, writes, matches, LoggedReads.of(frames, since), names)
    }

    /** Reads a bug report picked on the capture screen, off the main thread. */
    override fun importBugReport(uri: Uri) = importBugReport(listOf(uri))

    /**
     * Reads the first bug report among [files], off the main thread. A share holds the
     * report and its screenshot; anything that is not a bug report is skipped.
     */
    fun importBugReport(files: List<Uri>) {
        _capture.value = Capture.Reading
        val resolver = getApplication<Application>().contentResolver
        // What PairFree sent itself (its connect reads, its EQ put back) is not PeatsAudio's.
        val own = ownSessions()
        val mine = { timeMicros: Long -> own.any { timeMicros / 1000 in it } }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                var fallback: CaptureResult = CaptureResult.NotABugReport
                for (file in files) {
                    when (val read = BugReport.read(resolver, file, skip = mine)) {
                        is CaptureResult.Found, CaptureResult.NoBluetoothLog -> return@withContext read
                        CaptureResult.Unreadable -> fallback = read
                        CaptureResult.NotABugReport -> Unit
                    }
                }
                fallback
            }
            _capture.value = analyse(result, wholeLog = false)
        }
    }

    /** Names the [index]th captured write, or leaves it out of the report with null. */
    override fun nameCapturedWrite(index: Int, name: String?) {
        val done = _capture.value as? Capture.Done ?: return
        if (index !in done.names.indices) return
        _capture.value = done.copy(names = done.names.toMutableList().also { it[index] = name?.trim()?.takeIf(String::isNotEmpty) })
    }

    /** The capture report to paste into an issue: the model, then every named write's exact bytes. */
    override fun captureReport(): String = report(compact = false)

    /** The report; [compact] leaves out what the raw bytes already say, to fit in a link. */
    private fun report(compact: Boolean): String {
        val done = _capture.value as? Capture.Done ?: return ""
        val named = done.named
        val app = getApplication<Application>()
        val version = app.packageManager.getPackageInfo(app.packageName, 0).versionName
        val first = (named.map { it.second.timeMicros }).minOrNull()
        return buildString {
            appendLine("PairFree recording from PeatsAudio")
            appendLine("Paste this whole report into a new issue at")
            appendLine("$ISSUES so this model's controls and")
            appendLine("equalizer can be added. It holds no names, addresses or other personal data.")
            appendLine()
            appendLine("Model:     ${captureModel(done)}")
            appendLine("App:       PairFree $version")
            appendLine("Phone:     Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            // Snoop log times are the phone's local time (SnoopScanner.Found).
            if (first != null) appendLine("Recorded:  ${DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.ofEpochMilli(first / 1000).atOffset(ZoneOffset.UTC))}, phone's local time")
            RecordingReport.sections(done.reads, ownReads(done), named, compact).forEach(::appendLine)
        }
    }

    /**
     * A new-issue page with the report filled in, compacted so it fits in the link, or with a
     * request to paste it when even that is too long.
     */
    override fun captureIssueUri(): Uri {
        val done = _capture.value as? Capture.Done
        val title = "Recording: ${done?.let(::captureModel) ?: "unknown model"}"
        // Form encoding turns a space into one character, not three as in a path.
        fun form(s: String) = URLEncoder.encode(s, "UTF-8")
        fun link(body: String) = "$ISSUES/new?title=${form(title)}&body=${form(body)}".toUri()
        // In a code block, so GitHub shows the report as it is and the tools read it back.
        val filled = link("```\n${report(compact = true)}```\n")
        return filled.takeIf { it.toString().length <= MAX_LINK } ?: link(PASTE_HERE)
    }

    private fun captureModel(done: Capture.Done): String =
        done.firmware?.let(RecordingReport::earbudsName) ?: "unknown (the firmware reply is not in the log)"

    /**
     * PairFree's own reads, when it is connected to earbuds of a model in the recording:
     * every read it knows, which PeatsAudio may not send.
     */
    private fun ownReads(done: Capture.Done): RecordingReport.OwnReads? {
        if (connection.status.value !is LinkStatus.Connected) return null
        val state = connection.state.value
        val code = state.firmware?.code ?: return null
        val inRecording = (done.reads.map { it.firmware } + done.firmware).any { it?.code == code }
        if (!inRecording) return null
        return RecordingReport.OwnReads(state.firmware, connection.lastReplies, state.gaiaFeatures)
    }

    @SuppressLint("MissingPermission") // callers check the permission first
    fun refreshPaired() {
        if (!checkPermission()) return
        val on = adapter?.isEnabled == true
        _bluetoothOn.value = on
        // Only earbuds the app can control, not every bonded audio device. The AUDIO_VIDEO
        // class also drops SoundPEATS watches, which share the brand name. Soundcore earbuds
        // are known by their vendor service, which can name the model, or else by their name
        // (docs/soundcore/PROTOCOL.md).
        _paired.value = if (!on) emptyList() else adapter.bondedDevices
            .filter { it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO }
            .mapNotNull { d ->
                val name = d.name ?: d.address
                val soundcore = SoundcoreModels.identify(name, d.uuids.orEmpty().map { it.uuid.toString() })
                when {
                    soundcore != null -> Paired(d.address, name, Brand.SOUNDCORE, soundcore.service, soundcore.model)
                    Models.isSupported(d.name ?: "") -> Paired(d.address, name)
                    else -> null
                }
            }
            .sortedBy { it.name }
    }

    override fun onCleared() = connection.disconnect()

    private fun checkPermission(): Boolean =
        ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val KEY_LAST = "last_earbuds"
        const val LOG_TAG = "PairFree"
        const val ISSUES = "https://github.com/Omega-Destroyer/PairFree/issues"

        /** GitHub refuses links much longer than this; a longer report is pasted instead. */
        const val MAX_LINK = 8000
        const val PASTE_HERE = "Paste the report that PairFree copied here."

        const val KEY_OWN_SESSIONS = "own_sessions"
        const val KEY_RECORD_OPENED = "record_opened"

        /** A recording run lasts at most this long from opening the screen to sharing the bug report. */
        const val RECORD_RUN_MS = 3L * 60 * 60 * 1000

        /** Frames this close to PairFree's own session count as its own. */
        const val OWN_MARGIN_MS = 1_500L
        const val OWN_SESSIONS_KEPT_MS = 7L * 24 * 60 * 60 * 1000
        const val MAX_OWN_SESSIONS = 200
    }
}
