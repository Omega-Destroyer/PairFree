package io.github.omegadestroyer.pairfree

import android.annotation.SuppressLint
import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.EarbudsConnection
import io.github.omegadestroyer.pairfree.brands.Brands
import io.github.omegadestroyer.pairfree.brands.OneplusDriver
import io.github.omegadestroyer.pairfree.brands.ReportInfo
import io.github.omegadestroyer.pairfree.brands.SoundcoreDriver
import io.github.omegadestroyer.pairfree.brands.SoundpeatsDriver
import io.github.omegadestroyer.pairfree.bluetooth.LinkStatus
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.capture.BugReport
import io.github.omegadestroyer.pairfree.capture.Capture
import io.github.omegadestroyer.pairfree.capture.CaptureResult
import io.github.omegadestroyer.pairfree.eq.Equalizer
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.ConnectionReport
import io.github.omegadestroyer.pairfree.protocol.CurveMatch
import io.github.omegadestroyer.pairfree.protocol.EqCapture
import io.github.omegadestroyer.pairfree.protocol.LoggedReads
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.Models
import io.github.omegadestroyer.pairfree.protocol.RecordingReport
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.VendorRecording
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreButtons
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreTouch
import io.github.omegadestroyer.pairfree.status.CompanionLink
import io.github.omegadestroyer.pairfree.status.FastPairCheck
import io.github.omegadestroyer.pairfree.status.StatusChoice
import io.github.omegadestroyer.pairfree.status.StatusPrompt
import io.github.omegadestroyer.pairfree.status.StatusService
import io.github.omegadestroyer.pairfree.status.StatusSettings
import io.github.omegadestroyer.pairfree.status.StatusUi
import io.github.omegadestroyer.pairfree.status.canNotify
import io.github.omegadestroyer.pairfree.ui.CaptureActions
import io.github.omegadestroyer.pairfree.ui.EarbudsActions
import io.github.omegadestroyer.pairfree.ui.modelOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The screens' side of the app. The connection lives in the [Hub], which the status
 * notification shares: [onStart] reconnects to the last earbuds, and [onStop] lets the channel
 * go unless the notification holds it (docs/DESIGN.md).
 */
class AppViewModel(app: Application) : AndroidViewModel(app), EarbudsActions, CaptureActions {
    private val hub = app.hub
    private val adapter = hub.adapter
    private val prefs = hub.prefs

    val connection: EarbudsConnection = hub.connection

    /** EQ profiles and the current EQ, kept on the phone; changes go out over [connection]. */
    val equalizer: Equalizer = hub.equalizer

    /** Opens the Sound tab's EQ for [device] (the [EqOpener] in the Hub does the work). */
    fun openEqualizer(device: Paired, model: Model?) = hub.eqOpener.open(device, model)

    private val statusSettings = StatusSettings(prefs)
    private val _statusUi = MutableStateFlow<StatusUi?>(null)

    /** The battery notification choice for the connected earbuds, for the Device tab. */
    val statusUi: StateFlow<StatusUi?> = _statusUi.asStateFlow()

    private val _statusPrompt = MutableStateFlow<StatusPrompt?>(null)

    /** What to ask Android for next, the first time earbuds with PairFree's notification connect. */
    val statusPrompt: StateFlow<StatusPrompt?> = _statusPrompt.asStateFlow()

    /** Earbuds whose Fast Pair was looked for on the earbuds during this run. */
    private val fastPairChecked = mutableSetOf<String>()

    // After the fields above: the collectors run at once on the main thread.
    init {
        // The status notification follows the earbuds the screens connect to.
        viewModelScope.launch {
            connection.status.collect {
                when (it) {
                    is LinkStatus.Connected -> earbudsConnected(it.device)
                    is LinkStatus.Idle -> _statusUi.value = null
                    else -> Unit
                }
            }
        }
        // A SOUNDPEATS model is known for sure once its firmware answers.
        viewModelScope.launch {
            connection.state.map { it.firmware?.code }.distinctUntilChanged().collect { connectedDevice()?.let(::refreshStatus) }
        }
    }

    private fun connectedDevice(): Paired? = (connection.status.value as? LinkStatus.Connected)?.device

    /**
     * Earbuds connected: learns whether they have Fast Pair when their model does not say
     * (FastPairCheck), then starts or ends the notification, asking for what it needs the first time.
     */
    @SuppressLint("MissingPermission") // checked first
    private fun earbudsConnected(device: Paired) {
        val fastPair = statusSettings.fastPair(device, modelOf(device, connection.state.value))
        val remote = adapter?.takeIf { checkPermission() }?.getRemoteDevice(device.address)
        if (fastPair == null && remote != null && fastPairChecked.add(device.address)) {
            refreshStatus(device)
            // The prompts wait for the answer: earbuds with Fast Pair keep Google's notification until the user chooses.
            FastPairCheck.run(getApplication(), remote) { found ->
                found?.let { statusSettings.detected(device.address, it) }
                if (connectedDevice()?.address == device.address) applyStatus(device, prompt = true)
            }
            return
        }
        applyStatus(device, prompt = true)
    }

    private fun refreshStatus(device: Paired) {
        val app = getApplication<Application>()
        val fastPair = statusSettings.fastPair(device, modelOf(device, connection.state.value))
        _statusUi.value = StatusUi(
            device.address,
            statusSettings.options(fastPair),
            statusSettings.choice(device, fastPair),
            fastPair == true,
            canNotify(app),
            CompanionLink.isAssociated(app, device.address),
        )
    }

    /** Starts or ends the notification as the choice says; with [prompt], the first time, asks for what it needs. */
    private fun applyStatus(device: Paired, prompt: Boolean) {
        refreshStatus(device)
        val ui = _statusUi.value ?: return
        val app = getApplication<Application>()
        if (ui.choice != StatusChoice.PAIRFREE) {
            StatusService.stop(app, device.address)
            return
        }
        if (ui.canNotify) StatusService.startIfWanted(app, device.address, fromBackground = false)
        if (prompt && !statusSettings.asked(device.address)) {
            statusSettings.markAsked(device.address)
            _statusPrompt.value = when {
                !ui.canNotify -> StatusPrompt.NOTIFICATIONS
                !ui.linked -> StatusPrompt.LINK
                else -> null
            }
        }
    }

    override fun chooseStatus(choice: StatusChoice) {
        val device = connectedDevice() ?: return
        statusSettings.choose(device.address, choice)
        applyStatus(device, prompt = false)
    }

    override fun statusChanged() {
        connectedDevice()?.let { applyStatus(it, prompt = false) }
    }

    /** A first-time prompt closed: the notification starts if it now can, and the companion link is asked for next. */
    fun statusPromptDone(prompt: StatusPrompt) {
        _statusPrompt.value = null
        val device = connectedDevice() ?: return
        applyStatus(device, prompt = false)
        val ui = _statusUi.value ?: return
        if (prompt == StatusPrompt.NOTIFICATIONS && ui.choice == StatusChoice.PAIRFREE && ui.canNotify && !ui.linked) {
            _statusPrompt.value = StatusPrompt.LINK
        }
    }

    private val _hasPermission = MutableStateFlow(checkPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _paired = MutableStateFlow<List<Paired>>(emptyList())

    /** Paired earbuds PairFree can control, of every brand, by name. */
    val paired: StateFlow<List<Paired>> = _paired.asStateFlow()

    private val _bluetoothOn = MutableStateFlow(true)
    val bluetoothOn: StateFlow<Boolean> = _bluetoothOn.asStateFlow()

    fun onStart() {
        hub.screens(visible = true)
        _hasPermission.value = checkPermission()
        if (!_hasPermission.value) return
        refreshPaired()
        val last = prefs.getString(KEY_LAST, null) ?: return
        if (connection.status.value is LinkStatus.Idle) {
            _paired.value.firstOrNull { it.address == last }?.let(::connect)
        }
    }

    fun onStop() = hub.screens(visible = false)

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

    override fun changeTouch(make: (SoundcoreTouch) -> SoundcoreButtons.Change?) {
        (connection.driver as? SoundcoreDriver)?.changeTouch(make)
    }

    override fun resetTouch() {
        (connection.driver as? SoundcoreDriver)?.resetTouch()
    }

    override fun changeSetting(change: SettingChange) = connection.changeSetting(change)

    override fun sendFrames(frames: List<ByteArray>) = connection.sendEq(frames)

    override fun oneplusHearingTest() =
        (connection.driver as? OneplusDriver)?.session?.hearingTest(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")))

    override fun startFitTest() {
        val name = (connection.status.value as? LinkStatus.Connected)?.device?.name
        val model = Models.find(connection.state.value.firmware?.code, name)
        connection.startFitTest(keepAnc = model?.fitKeepsAnc == true)
    }

    /**
     * The connection report for the last failed attempt, any brand (issue #3), or null when nothing failed: what PairFree
     * tried and how each try ended, which release builds keep nowhere else.
     */
    fun connectionReport(): String? {
        val attempt = (connection.status.value as? LinkStatus.Failed)?.attempt ?: return null
        val app = getApplication<Application>()
        val version = app.packageManager.getPackageInfo(app.packageName, 0).versionName
        return ConnectionReport.text(ConnectionReport.Attempt(attempt.name, listOf("App" to "PairFree $version") + attempt.facts, attempt.steps), ISSUES)
    }

    /**
     * The device report to paste into an issue: what the earbuds answered, as their brand's driver writes it
     * ([io.github.omegadestroyer.pairfree.brands.BrandDriver.report]). Before a driver has anything of its own, SOUNDPEATS' form.
     */
    override fun deviceReport(): String {
        val app = getApplication<Application>()
        val version = app.packageManager.getPackageInfo(app.packageName, 0).versionName
        val device = (connection.status.value as? LinkStatus.Connected)?.device
        val date = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").format(LocalDateTime.now(ZoneOffset.UTC))
        val info = ReportInfo(device, version, date, ISSUES)
        return connection.driver?.report(info) ?: SoundpeatsDriver.plainReport(info, connection.lastReplies, connection.state.value)
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
        if (hub.snoopClockMs() - opened > RECORD_RUN_MS) prefs.edit { putLong(KEY_RECORD_OPENED, hub.snoopClockMs()) }
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
     * its EQ curves with the names of those PairFree knows, the settings read, and what other
     * brands' apps and earbuds sent each other, with each pair's model.
     */
    private fun analyse(result: CaptureResult, wholeLog: Boolean): Capture.Done {
        val found = result as? CaptureResult.Found
        val frames = found?.frames.orEmpty()
        val packets = found?.packets.orEmpty()
        val opened = prefs.getLong(KEY_RECORD_OPENED, 0).takeIf { it > 0 && hub.snoopClockMs() - it < RECORD_RUN_MS }
        val since = if (wholeLog) null else RecordingReport.start(frames, opened?.times(1000), packets)
        val writes = EqCapture.writes(frames, since)
        val matches = writes.map(CurveMatch::of)
        // Known presets are named already; the user's own slider curves teach nothing and stay out.
        val names = matches.map { (it as? CurveMatch.Preset)?.name }
        val vendor = VendorRecording.of(packets, since)
        return Capture.Done(result, since, wholeLog, writes, matches, LoggedReads.of(frames, since), names, vendor, vendor.map(::vendorName))
    }

    /** The paired device at the address of [earbuds] in a recording; the address itself goes into no report. */
    private fun pairedFor(earbuds: VendorRecording.Earbuds): Paired? {
        val address = earbuds.address?.reversedArray()?.joinToString(":") { "%02X".format(it) } ?: return null
        return _paired.value.firstOrNull { it.address.equals(address, ignoreCase = true) }
    }

    /** The model of other earbuds in a recording, as PairFree knows the paired device at their address. */
    private fun vendorName(earbuds: VendorRecording.Earbuds): String {
        // The recorder's brands are named as the app's are.
        val spec = Brands[Brand.valueOf(earbuds.brand.name)]
        val model = spec.recordedModel(pairedFor(earbuds)?.model)
        // Sony models are known by their name alone, which is also their code.
        if (model != null) return if (model.names.first() == model.code) model.code else "${model.names.first()} (${model.code})"
        return spec.recordedUnmatched
    }

    /** Reads a bug report picked on the capture screen, off the main thread. */
    override fun importBugReport(uri: Uri) = importBugReport(listOf(uri))

    /**
     * Reads the first bug report among [files], off the main thread. A share holds the
     * report and its screenshot; anything that is not a bug report is skipped.
     */
    fun importBugReport(files: List<Uri>) {
        _capture.value = Capture.Reading
        // Other brands' earbuds in the log are matched to the paired devices by address.
        refreshPaired()
        val resolver = getApplication<Application>().contentResolver
        // What PairFree sent itself (its connect reads, its EQ put back) is not the official app's.
        val own = hub.ownSessions()
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
            appendLine("PairFree recording from the official app")
            appendLine("Paste this whole report into a new issue at")
            appendLine("$ISSUES so this model's controls and")
            appendLine("equalizer can be added. It holds no names, addresses or other personal data.")
            appendLine()
            appendLine("Model:     ${captureModel(done)}")
            appendLine("App:       PairFree $version")
            appendLine("Phone:     Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            // Snoop log times are the phone's local time (SnoopScanner.Found).
            if (first != null) appendLine("Recorded:  ${DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.ofEpochMilli(first / 1000).atOffset(ZoneOffset.UTC))}, phone's local time")
            RecordingReport.sections(done.reads, ownReads(done), named, compact, done.vendorNames.zip(done.vendor)).forEach(::appendLine)
            ownVendorReport(done)?.let { (name, lines) ->
                appendLine()
                appendLine("---- PairFree's own device report for $name ----")
                lines.forEach(::appendLine)
            }
            if (done.vendor.isNotEmpty()) {
                appendLine()
                appendLine("---- What I changed in the official app, in order ----")
                appendLine("(Write it here.)")
            }
        }
    }

    /**
     * PairFree's own device report, without its opening lines, when it is connected to other
     * brands' earbuds that are in the recording: how PairFree reads the same state.
     */
    private fun ownVendorReport(done: Capture.Done): Pair<String, List<String>>? {
        val device = (connection.status.value as? LinkStatus.Connected)?.device ?: return null
        val index = done.vendor.indexOfFirst { pairedFor(it)?.address == device.address }
        if (index < 0) return null
        val lines = deviceReport().lines()
        return done.vendorNames[index] to lines.drop(lines.indexOfFirst(String::isEmpty) + 1).dropLastWhile(String::isEmpty)
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

    private fun captureModel(done: Capture.Done): String = when {
        done.firmware != null -> RecordingReport.earbudsName(done.firmware)
        !done.hasSoundpeats && done.vendorNames.isNotEmpty() -> done.vendorNames.distinct().joinToString(", ")
        else -> "unknown (the firmware reply is not in the log)"
    }

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

    fun refreshPaired() {
        if (!checkPermission()) return
        _bluetoothOn.value = adapter?.isEnabled == true
        _paired.value = hub.pairedEarbuds()
    }

    override fun onCleared() = hub.screens(visible = false)

    private fun checkPermission(): Boolean = hub.hasPermission()

    private companion object {
        const val KEY_LAST = "last_earbuds"
        const val ISSUES = "https://github.com/Omega-Destroyer/PairFree/issues"

        /** GitHub refuses links much longer than this; a longer report is pasted instead. */
        const val MAX_LINK = 8000
        const val PASTE_HERE = "Paste the report that PairFree copied here."

        const val KEY_RECORD_OPENED = "record_opened"

        /** A recording run lasts at most this long from opening the screen to sharing the bug report. */
        const val RECORD_RUN_MS = 3L * 60 * 60 * 1000

    }
}
