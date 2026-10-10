package io.github.omegadestroyer.soundpeter

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.omegadestroyer.soundpeter.bluetooth.EarbudsConnection
import io.github.omegadestroyer.soundpeter.bluetooth.LinkStatus
import io.github.omegadestroyer.soundpeter.bluetooth.Paired
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the connection while the app is on screen. The app talks to the earbuds only
 * while it is visible (docs/DESIGN.md): [onStart] reconnects to the last earbuds,
 * [onStop] closes the channel.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
    private val prefs = app.getSharedPreferences("soundpeter", Context.MODE_PRIVATE)

    val connection = EarbudsConnection(viewModelScope, adapter)

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
    fun switchEarbuds() {
        prefs.edit { remove(KEY_LAST) }
        connection.disconnect()
        refreshPaired()
    }

    @SuppressLint("MissingPermission") // callers check the permission first
    fun refreshPaired() {
        if (!checkPermission()) return
        val on = adapter?.isEnabled == true
        _bluetoothOn.value = on
        _paired.value = if (!on) emptyList() else adapter.bondedDevices
            .filter { it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO }
            .map { Paired(it.address, it.name ?: it.address) }
            .sortedWith(compareByDescending<Paired> { it.name.startsWith("SOUNDPEATS", ignoreCase = true) }.thenBy { it.name })
    }

    override fun onCleared() = connection.disconnect()

    private fun checkPermission(): Boolean =
        ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val KEY_LAST = "last_earbuds"
    }
}
