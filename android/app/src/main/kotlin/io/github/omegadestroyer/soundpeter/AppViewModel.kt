package io.github.omegadestroyer.soundpeter

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.omegadestroyer.soundpeter.bluetooth.EarbudsConnection
import io.github.omegadestroyer.soundpeter.bluetooth.LinkStatus
import io.github.omegadestroyer.soundpeter.bluetooth.Paired
import io.github.omegadestroyer.soundpeter.eq.Equalizer
import io.github.omegadestroyer.soundpeter.protocol.Command
import io.github.omegadestroyer.soundpeter.protocol.DeviceReport
import io.github.omegadestroyer.soundpeter.protocol.Models
import io.github.omegadestroyer.soundpeter.ui.EarbudsActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Holds the connection while the app is on screen. The app talks to the earbuds only
 * while it is visible (docs/DESIGN.md): [onStart] reconnects to the last earbuds,
 * [onStop] closes the channel.
 */
class AppViewModel(app: Application) : AndroidViewModel(app), EarbudsActions {
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

    /** The same lines as tools/probe.py, under a header like the reports in data/reports. */
    override fun deviceReport(): String {
        val app = getApplication<Application>()
        val version = app.packageManager.getPackageInfo(app.packageName, 0).versionName
        val device = (connection.status.value as? LinkStatus.Connected)?.device
        val date = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").format(LocalDateTime.now(ZoneOffset.UTC))
        return buildString {
            appendLine("Bluetooth name:  ${device?.name.orEmpty()}")
            appendLine("Date:            $date")
            appendLine("OS:              Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Link:            SoundPeter $version, SPP")
            appendLine()
            DeviceReport.lines(connection.lastReplies).forEach(::appendLine)
        }
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
    }
}
