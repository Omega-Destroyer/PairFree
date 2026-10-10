package io.github.omegadestroyer.soundpeter.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import androidx.annotation.RequiresPermission
import io.github.omegadestroyer.soundpeter.protocol.EarbudState
import io.github.omegadestroyer.soundpeter.protocol.Requests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** A pair of earbuds the phone is paired with. */
data class Paired(val address: String, val name: String)

sealed interface LinkStatus {
    data object Idle : LinkStatus
    data class Connecting(val device: Paired) : LinkStatus
    data class Connected(val device: Paired) : LinkStatus
    data class Failed(val device: Paired, val reason: Reason) : LinkStatus

    enum class Reason { BLUETOOTH_OFF, COULD_NOT_OPEN, LOST, NO_PERMISSION }
}

/**
 * One control connection to one pair of earbuds. On connect it sends the read-only GETs
 * in [Requests.ON_CONNECT], then reads the battery every [BATTERY_POLL_MS]. Every reply,
 * asked for or not, updates [state].
 */
class EarbudsConnection(private val scope: CoroutineScope, private val adapter: BluetoothAdapter?) {
    private val _status = MutableStateFlow<LinkStatus>(LinkStatus.Idle)
    val status: StateFlow<LinkStatus> = _status.asStateFlow()

    private val _state = MutableStateFlow(EarbudState())
    val state: StateFlow<EarbudState> = _state.asStateFlow()

    // All of this is touched only from the scope's (main) thread.
    private var session: Job? = null
    private var link: SppLink? = null
    private var attempt: Any? = null      // the current connection attempt; null after disconnect()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: Paired) {
        disconnect()
        _state.value = EarbudState()
        val remote = adapter?.takeIf { it.isEnabled }?.getRemoteDevice(device.address)
        if (remote == null) {
            _status.value = LinkStatus.Failed(device, LinkStatus.Reason.BLUETOOTH_OFF)
            return
        }
        val token = Any()
        attempt = token
        _status.value = LinkStatus.Connecting(device)
        session = scope.launch {
            val opened = try {
                SppLink(remote).also { link = it }.apply { connect() }
            } catch (e: IOException) {
                fail(token, device, LinkStatus.Reason.COULD_NOT_OPEN)
                return@launch
            } catch (e: SecurityException) {
                fail(token, device, LinkStatus.Reason.NO_PERMISSION)
                return@launch
            }
            _status.value = LinkStatus.Connected(device)
            val requests = launch {
                try {
                    for (command in Requests.ON_CONNECT) opened.send(Requests.get(command))
                    while (true) {
                        delay(BATTERY_POLL_MS)
                        for (command in Requests.BATTERY) opened.send(Requests.get(command))
                    }
                } catch (_: IOException) {
                    // the channel closed; the reader below notices too
                }
            }
            try {
                opened.frames().collect { frame -> _state.update { it.update(frame) } }
            } catch (_: IOException) {
            }
            requests.cancel()
            fail(token, device, LinkStatus.Reason.LOST)
        }
    }

    /** Closes the channel. Safe to call at any time, including while connecting. */
    fun disconnect() {
        attempt = null
        link?.close()             // unblocks a pending connect or read
        link = null
        session?.cancel()
        session = null
        _status.value = LinkStatus.Idle
    }

    /** Reports a failure of [token]'s attempt, unless [disconnect] or a newer attempt replaced it. */
    private fun fail(token: Any, device: Paired, reason: LinkStatus.Reason) {
        if (attempt !== token) return
        attempt = null
        link?.close()
        link = null
        _status.value = LinkStatus.Failed(device, reason)
    }

    companion object {
        const val BATTERY_POLL_MS = 30_000L
    }
}
