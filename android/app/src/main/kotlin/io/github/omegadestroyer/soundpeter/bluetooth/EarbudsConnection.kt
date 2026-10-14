package io.github.omegadestroyer.soundpeter.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import io.github.omegadestroyer.soundpeter.protocol.Command
import io.github.omegadestroyer.soundpeter.protocol.EarbudState
import io.github.omegadestroyer.soundpeter.protocol.Models
import io.github.omegadestroyer.soundpeter.protocol.Replies
import io.github.omegadestroyer.soundpeter.protocol.Requests
import io.github.omegadestroyer.soundpeter.protocol.Wire
import io.github.omegadestroyer.soundpeter.protocol.soundcore.SoundcoreModels
import io.github.omegadestroyer.soundpeter.protocol.soundcore.SoundcoreParser
import io.github.omegadestroyer.soundpeter.protocol.soundcore.SoundcoreSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** A brand SoundPeter can control. Each has its own protocol (docs/<brand>/PROTOCOL.md). */
enum class Brand { SOUNDPEATS, SOUNDCORE }

/**
 * A pair of earbuds the phone is paired with. [service] is the RFCOMM service UUID to open:
 * null for the standard serial port, which SoundPEATS earbuds use. Soundcore earbuds name
 * their model in theirs ([SoundcoreModels.fromServiceUuid]).
 */
data class Paired(val address: String, val name: String, val brand: Brand = Brand.SOUNDPEATS, val service: String? = null)

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
 *
 * Soundcore earbuds speak another protocol; a [SoundcoreSession] turns it into the same
 * [state] and turns [set] into Soundcore packets, so the screens work for both brands.
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
    private var soundcore: SoundcoreSession? = null   // set while connected to Soundcore earbuds

    /** The last raw reply to each GET, for the device report. Empty for Soundcore earbuds. */
    val lastReplies: Map<Command, ByteArray> get() = replies

    /** The Soundcore session, while connected to Soundcore earbuds; for the device report. */
    val soundcoreSession: SoundcoreSession? get() = soundcore

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: Paired) {
        disconnect()
        _state.value = EarbudState()
        replies = emptyMap()
        soundcore = null
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
            // (docs/soundpeats/PROTOCOL.md section 11). The last state stays on screen meanwhile.
            var everOpen = false
            var retries = 0
            while (true) {
                val opened = try {
                    SppLink(remote, log, device.service ?: Wire.SPP_UUID).also { link = it }.apply { connect() }
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
                if (device.brand == Brand.SOUNDCORE) serveSoundcore(opened, device) else serve(opened, device)
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
    private suspend fun serve(opened: SppLink, device: Paired) = coroutineScope {
        val requests = launch {
            try {
                for (command in Requests.ON_CONNECT) opened.send(Requests.get(command))
                // Qualcomm models also have GAIA v3 features, such as the fit test. By now the
                // firmware reply has named the model; the read goes to Qualcomm models only.
                if (Models.find(_state.value.firmware?.code, device.name)?.isQualcomm == true) opened.send(Requests.gaiaFeatures())
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
     * Soundcore earbuds: asks for the state on connecting and every [BATTERY_POLL_MS] after,
     * and applies every packet until the channel closes. The earbuds also send changes unasked.
     */
    private suspend fun serveSoundcore(opened: SppLink, device: Paired) = coroutineScope {
        val session = SoundcoreSession(SoundcoreModels.fromServiceUuid(device.service.orEmpty()).orEmpty())
        soundcore = session
        val requests = launch {
            try {
                while (true) {
                    opened.send(session.requestState())
                    delay(BATTERY_POLL_MS)
                }
            } catch (_: IOException) {
                // the channel closed; the reader below notices too
            }
        }
        try {
            val parser = SoundcoreParser()
            opened.reads().collect { bytes ->
                for (packet in parser.feed(bytes)) {
                    log?.invoke("recv $packet")
                    if (session.receive(packet)) _state.value = session.state
                }
            }
        } catch (_: IOException) {
        }
        requests.cancel()
    }

    /**
     * Sends a SET. The change shows at once, and [READ_BACK_MS] later the value is read
     * back, so the earbuds' answer replaces the guess (docs/DESIGN.md). Factory reset and
     * reboot are refused; see [factoryResetConfirmedByUser]. On Soundcore earbuds the
     * setting goes out as a Soundcore packet, and the whole state is read back.
     */
    fun set(command: Command, vararg payload: Int) {
        soundcore?.let { session ->
            val frame = session.set(command, payload.singleOrNull() ?: return) ?: return
            if (send(frame, then = session.requestState())) _state.value = session.state
            return
        }
        val frame = Requests.set(command, *payload)
        if (send(frame, Requests.readBack(command)?.let(Requests::get))) _state.update { it.expect(command, *payload) }
    }

    private var eqWrite: Job? = null

    /**
     * Sends one EQ write: a frame per band, as [io.github.omegadestroyer.soundpeter.protocol.EqLayout.frames]
     * builds them. A newer write replaces one still going out. The earbuds do not reply.
     */
    fun sendEq(frames: List<ByteArray>) {
        if (_status.value !is LinkStatus.Connected || soundcore != null) return
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

    /**
     * Starts the ear tip fit test; the result replaces [EarbudState.fit] about 9 s later.
     * Unless [keepAnc], noise control goes off first, as the official app does (docs/soundpeats/PROTOCOL.md
     * section 11), and back to what it was once the result is in. The official app leaves it off.
     */
    fun startFitTest(keepAnc: Boolean) {
        if (_status.value !is LinkStatus.Connected || soundcore != null) return
        val s = session ?: return
        val l = link ?: return
        val mode = _state.value.number(Command.ANC_MODE)
        val switchOff = !keepAnc && mode != null && mode != ANC_OFF
        _state.update { it.copy(fit = null) }
        s.launch {
            try {
                if (switchOff) {
                    // Started too soon after ANC goes off, the test gives up within 2 s and
                    // reports a poor fit. Wait for the earbuds to confirm, then a little more.
                    l.send(Requests.set(Command.ANC_MODE, ANC_OFF))
                    withTimeoutOrNull(ANC_CONFIRM_MS) { _state.first { it.number(Command.ANC_MODE) == ANC_OFF } }
                    delay(ANC_SETTLE_MS)
                }
                for (frame in Requests.fitTest()) l.send(frame)
                if (switchOff && mode != null) {
                    withTimeoutOrNull(FIT_WAIT_MS) { _state.first { it.fit != null } }
                    l.send(Requests.set(Command.ANC_MODE, mode))
                    _state.update { it.expect(Command.ANC_MODE, mode) }
                }
            } catch (_: IOException) {
                // the channel closed; the reader notices and reports it
            }
        }
    }

    /** Factory reset. Call only from a confirmation the user has just accepted on screen. */
    fun factoryResetConfirmedByUser() {
        if (soundcore != null) return
        send(Requests.factoryResetConfirmedByUser(), then = null)
    }

    /** Queues [frame] on the open channel, then [then] [READ_BACK_MS] later. False when there is no channel. */
    private fun send(frame: ByteArray, then: ByteArray?): Boolean {
        if (_status.value !is LinkStatus.Connected) return false
        val s = session ?: return false
        val l = link ?: return false
        s.launch {
            try {
                l.send(frame)
                if (then != null) {
                    delay(READ_BACK_MS)
                    l.send(then)
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
        soundcore = null
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

        /** The H3 sends the new state about 2 s after a SET (docs/soundpeats/PROTOCOL.md section 11). */
        const val READ_BACK_MS = 3_000L

        /** Waits before each try to reopen a channel that closed. The H3 took about 2 s to take one again. */
        val RECONNECT_DELAYS_MS = longArrayOf(1_000, 2_000, 3_000, 5_000, 8_000)

        /** A channel open this long counts as working, so the retries start over when it closes. */
        const val STABLE_MS = 10_000L

        /** The H3 confirms ANC off about 1.7 s after the SET; wait at most this long. */
        const val ANC_CONFIRM_MS = 5_000L

        /** Then this long before the fit test: started 0.3 s after, it failed; the official app waits about 1 s. */
        const val ANC_SETTLE_MS = 1_500L

        /** How long turning ANC off usually takes before the test starts, for the progress bar. */
        const val ANC_OFF_TYPICAL_MS = 3_200L

        /** Longest wait for a fit result before noise control goes back on regardless. */
        const val FIT_WAIT_MS = 20_000L

        /** ANC mode "off" (data/soundpeats/commands.json). */
        private val ANC_OFF = Command.ANC_MODE.labels.entries.first { it.value == "off" }.key
    }
}
