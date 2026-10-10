package io.github.omegadestroyer.soundpeter.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import io.github.omegadestroyer.soundpeter.protocol.Command
import io.github.omegadestroyer.soundpeter.protocol.EarbudState
import io.github.omegadestroyer.soundpeter.protocol.Replies
import io.github.omegadestroyer.soundpeter.protocol.Requests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
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

    /** The channel closed after being open; trying again, with the last state kept. */
    data class Reconnecting(val device: Paired) : LinkStatus
    data class Failed(val device: Paired, val reason: Reason) : LinkStatus

    enum class Reason { BLUETOOTH_OFF, COULD_NOT_OPEN, LOST, NO_PERMISSION }
}

/**
 * One control connection to one pair of earbuds. On connect it sends the read-only GETs
 * in [Requests.ON_CONNECT], then reads the battery every [BATTERY_POLL_MS]. Every reply,
 * asked for or not, updates [state]. [log], when set, gets a line for every frame and
 * for the channel opening and closing.
 */
class EarbudsConnection(
    private val scope: CoroutineScope,
    private val adapter: BluetoothAdapter?,
    private val log: ((String) -> Unit)? = null,
) {
    private val _status = MutableStateFlow<LinkStatus>(LinkStatus.Idle)
    val status: StateFlow<LinkStatus> = _status.asStateFlow()

    private val _state = MutableStateFlow(EarbudState())
    val state: StateFlow<EarbudState> = _state.asStateFlow()

    // All of this is touched only from the scope's (main) thread.
    private var session: CoroutineScope? = null   // everything one connection runs; cancelled on disconnect
    private var link: SppLink? = null
    private var attempt: Any? = null      // the current connection attempt; null after disconnect()
    private var replies: Map<Command, ByteArray> = emptyMap()

    /** The last raw reply to each GET, for the device report. */
    val lastReplies: Map<Command, ByteArray> get() = replies

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: Paired) {
        disconnect()
        _state.value = EarbudState()
        replies = emptyMap()
        val remote = adapter?.takeIf { it.isEnabled }?.getRemoteDevice(device.address)
        if (remote == null) {
            _status.value = LinkStatus.Failed(device, LinkStatus.Reason.BLUETOOTH_OFF)
            return
        }
        val token = Any()
        attempt = token
        _status.value = LinkStatus.Connecting(device)
        val s = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job]))
        session = s
        s.launch {
            // Once the channel has been open, a drop is followed by a few retries: the H3
            // restarts its Bluetooth link after an LDAC or two-devices change
            // (docs/PROTOCOL.md section 10). The last state stays on screen meanwhile.
            var everOpen = false
            var retries = 0
            while (true) {
                val opened = try {
                    SppLink(remote, log).also { link = it }.apply { connect() }
                } catch (e: IOException) {
                    log?.invoke("could not open: ${e.message}")
                    if (everOpen && retries < RECONNECT_DELAYS_MS.size) {
                        delay(RECONNECT_DELAYS_MS[retries++])
                        continue
                    }
                    fail(token, device, if (everOpen) LinkStatus.Reason.LOST else LinkStatus.Reason.COULD_NOT_OPEN)
                    return@launch
                } catch (e: SecurityException) {
                    fail(token, device, LinkStatus.Reason.NO_PERMISSION)
                    return@launch
                }
                everOpen = true
                _status.value = LinkStatus.Connected(device)
                log?.invoke("open")
                val openedAt = SystemClock.elapsedRealtime()
                serve(opened)
                log?.invoke("closed")
                opened.close()
                // A channel that keeps closing straight away does not earn fresh retries.
                if (SystemClock.elapsedRealtime() - openedAt >= STABLE_MS) retries = 0
                if (attempt !== token) return@launch
                if (retries >= RECONNECT_DELAYS_MS.size) {
                    fail(token, device, LinkStatus.Reason.LOST)
                    return@launch
                }
                _status.value = LinkStatus.Reconnecting(device)
                delay(RECONNECT_DELAYS_MS[retries++])
            }
        }
    }

    /** Asks for everything, reads the battery now and then, and applies every reply until the channel closes. */
    private suspend fun serve(opened: SppLink) = coroutineScope {
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
            opened.frames().collect { frame ->
                Replies.commandFor(frame)?.let { replies = replies + (it to frame.payload) }
                _state.update { it.update(frame) }
            }
        } catch (_: IOException) {
        }
        requests.cancel()
    }

    /**
     * Sends a SET. The change shows at once, and [READ_BACK_MS] later the value is read
     * back, so the earbuds' answer replaces the guess (docs/DESIGN.md). Factory reset and
     * reboot are refused; see [factoryResetConfirmedByUser].
     */
    fun set(command: Command, vararg payload: Int) {
        val frame = Requests.set(command, *payload)
        if (send(frame, Requests.readBack(command))) _state.update { it.expect(command, *payload) }
    }

    private var eqWrite: Job? = null

    /**
     * Sends one EQ write: a frame per band, as [io.github.omegadestroyer.soundpeter.protocol.EqLayout.frames]
     * builds them. A newer write replaces one still going out. The earbuds do not reply.
     */
    fun sendEq(frames: List<ByteArray>) {
        if (_status.value !is LinkStatus.Connected) return
        val s = session ?: return
        val l = link ?: return
        eqWrite?.cancel()
        eqWrite = s.launch {
            try {
                for (frame in frames) l.send(frame)
            } catch (_: IOException) {
                // the channel closed; the reader notices and reports it
            }
        }
    }

    /** Factory reset. Call only from a confirmation the user has just accepted on screen. */
    fun factoryResetConfirmedByUser() {
        send(Requests.factoryResetConfirmedByUser(), readBack = null)
    }

    /** Queues [frame] on the open channel. False when there is none. */
    private fun send(frame: ByteArray, readBack: Command?): Boolean {
        if (_status.value !is LinkStatus.Connected) return false
        val s = session ?: return false
        val l = link ?: return false
        s.launch {
            try {
                l.send(frame)
                if (readBack != null) {
                    delay(READ_BACK_MS)
                    l.send(Requests.get(readBack))
                }
            } catch (_: IOException) {
                // the channel closed; the reader notices and reports it
            }
        }
        return true
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

        /** The H3 sends the new state about 2 s after a SET (docs/PROTOCOL.md section 10). */
        const val READ_BACK_MS = 3_000L

        /** Waits before each try to reopen a channel that closed. The H3 took about 2 s to take one again. */
        val RECONNECT_DELAYS_MS = longArrayOf(1_000, 2_000, 3_000, 5_000, 8_000)

        /** A channel open this long counts as working, so the retries start over when it closes. */
        const val STABLE_MS = 10_000L
    }
}
