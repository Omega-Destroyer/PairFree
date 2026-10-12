package io.github.omegadestroyer.soundpeter

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
import io.github.omegadestroyer.soundpeter.bluetooth.EarbudsConnection
import io.github.omegadestroyer.soundpeter.bluetooth.LinkStatus
import io.github.omegadestroyer.soundpeter.bluetooth.Paired
import io.github.omegadestroyer.soundpeter.capture.BugReport
import io.github.omegadestroyer.soundpeter.capture.Capture
import io.github.omegadestroyer.soundpeter.capture.CaptureResult
import io.github.omegadestroyer.soundpeter.eq.Equalizer
import io.github.omegadestroyer.soundpeter.protocol.Command
import io.github.omegadestroyer.soundpeter.protocol.DeviceReport
import io.github.omegadestroyer.soundpeter.protocol.EqCapture
import io.github.omegadestroyer.soundpeter.protocol.Models
import io.github.omegadestroyer.soundpeter.ui.CaptureActions
import io.github.omegadestroyer.soundpeter.ui.EarbudsActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Holds the connection while the app is on screen. The app talks to the earbuds only
 * while it is visible (docs/DESIGN.md): [onStart] reconnects to the last earbuds,
 * [onStop] closes the channel.
 */
class AppViewModel(app: Application) : AndroidViewModel(app), EarbudsActions, CaptureActions {
    private val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
    private val prefs = app.getSharedPreferences("soundpeter", Context.MODE_PRIVATE)

    /** Debug builds log every frame to Logcat under the tag `SoundPeter`; release builds log nothing. */
    val connection = EarbudsConnection(
        viewModelScope,
        adapter,
        log = if (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) { line -> Log.d(LOG_TAG, line) } else null,
    )

    /** EQ profiles and the current EQ, kept on the phone; changes go out over [connection]. */
    val equalizer = Equalizer(prefs, viewModelScope) { frames -> connection.sendEq(frames) }

    init {
        // The earbuds cannot report their EQ, so each newly opened channel gets this phone's back.
        viewModelScope.launch {
            connection.status.collect { if (it is LinkStatus.Connected) equalizer.channelOpened(it.device.address) }
        }
    }

    private val _hasPermission = MutableStateFlow(checkPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _paired = MutableStateFlow<List<Paired>>(emptyList())

    /** Paired audio devices, SoundPEATS first. */
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
        return buildString {
            appendLine("SoundPeter device report")
            appendLine("Paste this whole report into a new issue at")
            appendLine("$ISSUES to help add support")
            appendLine("for this model. It holds no account, address or other personal data.")
            appendLine()
            appendLine("Bluetooth name:  ${device?.name.orEmpty()}")
            appendLine("Firmware:        ${firmware?.text ?: "unknown"}")
            appendLine("Firmware code:   ${firmware?.code ?: "unknown"}")
            appendLine("App:             SoundPeter $version (SPP)")
            appendLine("OS:              Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Captured:        $date")
            appendLine()
            appendLine("The app asked the earbuds every read it knows. Each line is the feature,")
            appendLine("its command, the decoded reply and the raw bytes. \"no reply\" means the")
            appendLine("firmware does not support that read.")
            appendLine()
            DeviceReport.lines(connection.lastReplies).forEach(::appendLine)
            appendLine()
            appendLine("This is enough to add this model's controls. For its equalizer, use")
            appendLine("\"Record the EQ from PeatsAudio\" on SoundPeter's Device tab as well.")
        }
    }

    private val _capture = MutableStateFlow<Capture>(Capture.Closed)

    /** The EQ capture screen (docs/DESIGN.md, EQ capture). Not [Capture.Closed] while it is open. */
    val capture: StateFlow<Capture> = _capture.asStateFlow()

    override fun openCapture() {
        if (_capture.value == Capture.Closed) _capture.value = Capture.Guide
    }

    override fun closeCapture() {
        _capture.value = Capture.Closed
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
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                var fallback: CaptureResult = CaptureResult.NotABugReport
                for (file in files) {
                    when (val read = BugReport.read(resolver, file)) {
                        is CaptureResult.Found, CaptureResult.NoBluetoothLog -> return@withContext read
                        CaptureResult.Unreadable -> fallback = read
                        CaptureResult.NotABugReport -> Unit
                    }
                }
                fallback
            }
            val done = Capture.Done(result, emptyList())
            _capture.value = done.copy(names = List(done.writes.size) { null })
        }
    }

    /** Names the [index]th captured write, or leaves it out of the report with null. */
    override fun nameCapturedWrite(index: Int, name: String?) {
        val done = _capture.value as? Capture.Done ?: return
        if (index !in done.names.indices) return
        _capture.value = done.copy(names = done.names.toMutableList().also { it[index] = name?.trim()?.takeIf(String::isNotEmpty) })
    }

    /** The capture report to paste into an issue: the model, then every named write's exact bytes. */
    override fun captureReport(): String {
        val done = _capture.value as? Capture.Done ?: return ""
        val named = done.named
        val app = getApplication<Application>()
        val version = app.packageManager.getPackageInfo(app.packageName, 0).versionName
        val first = named.firstOrNull()?.second?.timeMicros
        return buildString {
            appendLine("SoundPeter EQ capture")
            appendLine("Paste this whole report into a new issue at")
            appendLine("$ISSUES so this model's equalizer and")
            appendLine("presets can be added. It holds no names, addresses or other personal data.")
            appendLine()
            appendLine("Model:     ${captureModel(done)}")
            appendLine("App:       SoundPeter $version")
            appendLine("Phone:     Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            // Snoop log times are the phone's local time (SnoopScanner.Found).
            if (first != null) appendLine("Recorded:  ${DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.ofEpochMilli(first / 1000).atOffset(ZoneOffset.UTC))}, phone's local time")
            EqCapture.reportLines(named).forEach(::appendLine)
        }
    }

    /** A new-issue page with the report filled in, or with a request to paste it when it is too long for a link. */
    override fun captureIssueUri(report: String): Uri {
        val done = _capture.value as? Capture.Done
        val title = "EQ capture: ${done?.let(::captureModel) ?: "unknown model"}"
        fun link(body: String) = "$ISSUES/new".toUri().buildUpon()
            .appendQueryParameter("title", title)
            .appendQueryParameter("body", body)
            .build()
        // In a code block, so GitHub shows the report as it is and the import tool reads it back.
        return link("```\n$report```\n").takeIf { it.toString().length <= MAX_LINK } ?: link(PASTE_HERE)
    }

    private fun captureModel(done: Capture.Done): String {
        val firmware = (done.named.map { it.second } + done.writes).firstNotNullOfOrNull { it.firmware }
            ?: return "unknown (the firmware reply is not in the log)"
        val name = Models.find(firmware.code, null)?.names?.firstOrNull() ?: "not in SoundPeter's table yet"
        return "$name, firmware ${firmware.text}"
    }

    @SuppressLint("MissingPermission") // callers check the permission first
    fun refreshPaired() {
        if (!checkPermission()) return
        val on = adapter?.isEnabled == true
        _bluetoothOn.value = on
        // Only SoundPEATS models the app can control, not every bonded audio device. The
        // AUDIO_VIDEO class also drops SoundPEATS watches, which share the brand name.
        _paired.value = if (!on) emptyList() else adapter.bondedDevices
            .filter { it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO }
            .filter { Models.isSupported(it.name ?: "") }
            .map { Paired(it.address, it.name ?: it.address) }
            .sortedBy { it.name }
    }

    override fun onCleared() = connection.disconnect()

    private fun checkPermission(): Boolean =
        ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val KEY_LAST = "last_earbuds"
        const val LOG_TAG = "SoundPeter"
        const val ISSUES = "https://github.com/Omega-Destroyer/SoundPeter/issues"

        /** GitHub refuses links much longer than this; a longer report is pasted instead. */
        const val MAX_LINK = 8000
        const val PASTE_HERE = "Paste the EQ capture report that SoundPeter copied here."
    }
}
