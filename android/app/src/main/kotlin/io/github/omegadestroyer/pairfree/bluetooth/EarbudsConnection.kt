package io.github.omegadestroyer.pairfree.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import io.github.omegadestroyer.pairfree.brands.BrandDriver
import io.github.omegadestroyer.pairfree.brands.Brands
import io.github.omegadestroyer.pairfree.brands.DriverHost
import io.github.omegadestroyer.pairfree.brands.EqEvent
import io.github.omegadestroyer.pairfree.brands.NeedsNewerAndroid
import io.github.omegadestroyer.pairfree.brands.NotFound
import io.github.omegadestroyer.pairfree.brands.OpenEnv
import io.github.omegadestroyer.pairfree.brands.ScanNotAllowed
import io.github.omegadestroyer.pairfree.brands.SoundpeatsDriver
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.ConnectionReport
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Maker
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A brand PairFree can control: the one list [io.github.omegadestroyer.pairfree.protocol.Identify] tells the paired
 * devices apart by. Each has its own protocol (docs/<brand>/PROTOCOL.md) and its own file in `brands`.
 */
typealias Brand = Maker

/**
 * A pair of earbuds the phone is paired with, as its brand's [io.github.omegadestroyer.pairfree.brands.BrandSpec] made
 * it. [service] is the RFCOMM service UUID to open: null for the standard serial port, which SoundPEATS earbuds use.
 * [model] is what the brand knows the model by before the channel opens: the Soundcore model number, the Galaxy Buds
 * model number, the Sony or JBL model's name, the Nothing model's product id, the Pixel Buds model's device type (for
 * Pixel Buds [service] also picks the transport), the 1MORE or EarFun model's name.
 */
data class Paired(
    val address: String,
    val name: String,
    val brand: Brand = Brand.SOUNDPEATS,
    val service: String? = null,
    val model: String? = null,
    /** The model id Galaxy Buds2 and later publish in their service list, when it was there (for the device report). */
    val modelId: Int? = null,
    /** The brand the earbuds are sold as, when another brand's protocol runs them (SOUNDPEATS' QCY-built Capsule3 Pro). */
    val soldAs: Brand? = null,
)

sealed interface LinkStatus {
    data object Idle : LinkStatus
    data class Connecting(val device: Paired) : LinkStatus
    data class Connected(val device: Paired) : LinkStatus

    /** The channel closed after being open; trying again, with the last state kept. */
    data class Reconnecting(val device: Paired) : LinkStatus
    /** [attempt] is what PairFree tried, for the connection report the user can copy (issue #3). */
    data class Failed(val device: Paired, val reason: Reason, val attempt: ConnectionReport.Attempt? = null) : LinkStatus

    /**
     * [NO_SCAN_PERMISSION]: JBL earbuds that did not answer on their classic address, or QCY earbuds, which
     * PairFree may not scan for. [NEEDS_NEWER_ANDROID]: AirPods whose channel this phone's Bluetooth stack
     * refuses ([AppleSupport]). [NOT_FOUND]: QCY earbuds whose advert a scan did not find.
     */
    enum class Reason { BLUETOOTH_OFF, COULD_NOT_OPEN, LOST, NO_PERMISSION, NO_SCAN_PERMISSION, NEEDS_NEWER_ANDROID, HIDDEN_BY_ANDROID, NOT_FOUND }
}

/**
 * One control connection to one pair of earbuds, of any brand. It opens the channel the way the brand's
 * [io.github.omegadestroyer.pairfree.brands.BrandSpec] says, keeps it open (a drop is followed by a few retries, with the
 * last state kept on screen), and hands it to the brand's [BrandDriver], which speaks the brand's protocol and turns
 * what the earbuds say into [state] and [settings]. Nothing here knows any one brand: the brands are in the `brands`
 * package. [log], when set, gets a line for every frame and for the channel opening and closing. [context] opens LE
 * connections.
 */
class EarbudsConnection(
    private val scope: CoroutineScope,
    private val adapter: BluetoothAdapter?,
    private val context: Context? = null,
    private val log: ((String) -> Unit)? = null,
    private val assistantIsGoogle: () -> Boolean = { true },
) {
    private val _status = MutableStateFlow<LinkStatus>(LinkStatus.Idle)
    val status: StateFlow<LinkStatus> = _status.asStateFlow()

    private val _state = MutableStateFlow(EarbudState())
    val state: StateFlow<EarbudState> = _state.asStateFlow()

    private val _settings = MutableStateFlow<List<SettingGroup>>(emptyList())

    /** Settings beyond the shared ones, as generic rows for the screens; empty for brands without them. */
    val settings: StateFlow<List<SettingGroup>> = _settings.asStateFlow()

    private val _extra = MutableStateFlow<Any?>(null)

    /** Something of the brand's own for a screen of its own: Soundcore earbuds' last state, which their touch controls are read from. */
    val extra: StateFlow<Any?> = _extra.asStateFlow()

    private val _eqEvents = MutableSharedFlow<EqEvent>(extraBufferCapacity = 64)

    /** What the earbuds said about their EQ, as it changes; the Sound tab's opener follows it. */
    val eqEvents: SharedFlow<EqEvent> = _eqEvents.asSharedFlow()

    // All of this is touched only from the scope's (main) thread.
    private var session: CoroutineScope? = null   // everything one connection runs; cancelled on disconnect
    private var link: ControlLink? = null
    private var attempt: Any? = null      // the current connection attempt; null after disconnect()

    /** The brand's side of the current connection; null while there is none. */
    var driver: BrandDriver? = null
        private set

    // What the current attempt tried, for the connection report: the steps from [note], timed from [startedAt],
    // and the device it is for. The links' own lines count only while a channel is opening ([opening]).
    private val steps = mutableListOf<String>()
    private var startedAt = 0L
    private var opening = false
    private var reportDevice: BluetoothDevice? = null

    private fun note(step: String) {
        synchronized(steps) { steps += "+${SystemClock.elapsedRealtime() - startedAt} $step" }
    }

    /** What a driver may use of this connection. */
    private val host = object : DriverHost {
        override val log: ((String) -> Unit)? get() = this@EarbudsConnection.log
        override val state: MutableStateFlow<EarbudState> get() = _state
        override val settings: MutableStateFlow<List<SettingGroup>> get() = _settings
        override val extra: MutableStateFlow<Any?> get() = _extra
        override val connected: Boolean get() = _status.value is LinkStatus.Connected
        override val link: ControlLink? get() = this@EarbudsConnection.link
        override fun launch(block: suspend CoroutineScope.() -> Unit): Job? = session?.launch(block = block)
        override fun eq(event: EqEvent) {
            _eqEvents.tryEmit(event)
        }
        override fun assistantIsGoogle(): Boolean = this@EarbudsConnection.assistantIsGoogle()
    }

    /** What opening a channel may use of this connection. */
    private val env = object : OpenEnv {
        override val context: Context? get() = this@EarbudsConnection.context
        override val adapter: BluetoothAdapter? get() = this@EarbudsConnection.adapter
        override val log: ((String) -> Unit)? get() = this@EarbudsConnection.log

        /** [log], and while a channel opens, the links' lines as steps of the report too. */
        override val linkLog: (String) -> Unit = { line ->
            this@EarbudsConnection.log?.invoke(line)
            if (opening) this@EarbudsConnection.note(line)
        }
        override fun note(step: String) = this@EarbudsConnection.note(step)
        override fun <L : ControlLink> hold(link: L): L = link.also { this@EarbudsConnection.link = it }
    }

    /** The last raw reply to each of SOUNDPEATS' GETs, for the device report and a recording's own reads. Empty for other brands. */
    val lastReplies: Map<Command, ByteArray> get() = (driver as? SoundpeatsDriver)?.replies ?: emptyMap()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: Paired) {
        disconnect()
        _state.value = EarbudState()
        _settings.value = emptyList()
        _extra.value = null
        val remote = adapter?.takeIf { it.isEnabled }?.getRemoteDevice(device.address)
        if (remote == null) {
            _status.value = LinkStatus.Failed(device, LinkStatus.Reason.BLUETOOTH_OFF)
            return
        }
        val token = Any()
        attempt = token
        synchronized(steps) { steps.clear() }
        startedAt = SystemClock.elapsedRealtime()
        reportDevice = remote
        val spec = Brands[device.brand]
        val driver = spec.driver(device, host)
        this.driver = driver
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
                opening = true
                val opened = try {
                    // Each link is [link] while it opens, so [disconnect] can abort it.
                    spec.open(env, remote, device, driver)
                } catch (_: ScanNotAllowed) {
                    note("stopped: no permission to scan for it")
                    fail(token, device, LinkStatus.Reason.NO_SCAN_PERMISSION)
                    return@launch
                } catch (_: NotFound) {
                    note("stopped: the scan did not find its advert")
                    fail(token, device, LinkStatus.Reason.NOT_FOUND)
                    return@launch
                } catch (e: IOException) {
                    log?.invoke("could not open: ${e.message}")
                    note("failed: ${ConnectionReport.describe(e)}")
                    if (e is ConstructorsHidden && !everOpen) {
                        fail(token, device, LinkStatus.Reason.HIDDEN_BY_ANDROID)
                        return@launch
                    }
                    if (e is NeedsNewerAndroid && !everOpen) {
                        fail(token, device, LinkStatus.Reason.NEEDS_NEWER_ANDROID)
                        return@launch
                    }
                    if (everOpen && retries < RECONNECT_DELAYS_MS.size) {
                        delay(RECONNECT_DELAYS_MS[retries++])
                        continue
                    }
                    fail(token, device, if (everOpen) LinkStatus.Reason.LOST else LinkStatus.Reason.COULD_NOT_OPEN)
                    return@launch
                } catch (e: SecurityException) {
                    note("failed: ${ConnectionReport.describe(e)}")
                    fail(token, device, LinkStatus.Reason.NO_PERMISSION)
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RuntimeException) {
                    // A fault in one brand's opening is a channel that did not open, not a crash.
                    log?.invoke("could not open: $e")
                    note("failed: ${ConnectionReport.describe(e)}")
                    fail(token, device, if (everOpen) LinkStatus.Reason.LOST else LinkStatus.Reason.COULD_NOT_OPEN)
                    return@launch
                } finally {
                    opening = false
                }
                everOpen = true
                _status.value = LinkStatus.Connected(device)
                log?.invoke("open")
                note("open")
                val openedAt = SystemClock.elapsedRealtime()
                try {
                    driver.serve(opened)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RuntimeException) {
                    // A fault in one brand's driver ends this channel, which is then opened again; it never ends the app.
                    log?.invoke("stopped: $e")
                    note("stopped: ${ConnectionReport.describe(e)}")
                }
                log?.invoke("closed")
                note("closed after ${(SystemClock.elapsedRealtime() - openedAt) / 1000} s")
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

    /** Changes one of the [settings]: it shows at once, and the earbuds' acknowledgement or next state confirms it. */
    fun changeSetting(change: SettingChange) {
        driver?.changeSetting(change)
    }

    /**
     * Sets one of the shared settings. The change shows at once, and the earbuds' answer or a read-back
     * [READ_BACK_MS] later replaces the guess (docs/DESIGN.md); how it goes out is the brand's driver's.
     */
    fun set(command: Command, vararg payload: Int) {
        driver?.set(command, payload)
    }

    /** Sends one EQ write, built by the Sound tab's target for these earbuds, as their brand sends it. */
    fun sendEq(frames: List<ByteArray>) {
        driver?.sendEq(frames)
    }

    /** Starts the ear tip fit test where the earbuds have one; the result arrives in [state]. */
    fun startFitTest(keepAnc: Boolean) {
        driver?.startFitTest(keepAnc)
    }

    /** Factory reset. Call only from a confirmation the user has just accepted on screen. */
    fun factoryResetConfirmedByUser() {
        driver?.factoryResetConfirmedByUser()
    }

    /** Closes the channel. Safe to call at any time, including while connecting. */
    fun disconnect() {
        attempt = null
        link?.close()             // unblocks a pending connect or read
        link = null
        driver = null
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
        _status.value = LinkStatus.Failed(device, reason, attemptReport(device, reason))
    }

    /** What the failed attempt tried, with the facts that tell one phone and device from another (no serial, no full address). */
    @SuppressLint("MissingPermission") // only after connecting, which needs the permission; a refusal is caught
    private fun attemptReport(device: Paired, reason: LinkStatus.Reason): ConnectionReport.Attempt {
        val remote = reportDevice
        fun known(read: () -> String): String = try { read() } catch (e: SecurityException) { "not allowed to read" }
        val type = remote?.let { r ->
            known {
                when (r.type) {
                    BluetoothDevice.DEVICE_TYPE_CLASSIC -> "classic"
                    BluetoothDevice.DEVICE_TYPE_LE -> "LE only"
                    BluetoothDevice.DEVICE_TYPE_DUAL -> "classic and LE"
                    else -> "unknown"
                }
            }
        } ?: "unknown"
        val bond = remote?.let { r ->
            known {
                when (r.bondState) {
                    BluetoothDevice.BOND_BONDED -> "paired"
                    BluetoothDevice.BOND_BONDING -> "pairing"
                    else -> "not paired"
                }
            }
        } ?: "unknown"
        val services = remote?.let { r -> known { r.uuids?.joinToString(", ") { it.toString() } ?: "none cached" } } ?: "unknown"
        val facts = listOf(
            "Brand" to device.brand.name.lowercase(),
            "Model" to (device.model ?: "not known before the channel opens"),
            "Service" to (device.service ?: "the brand's default"),
            "Failure" to reason.name.lowercase().replace('_', ' '),
            "Device" to "$type, $bond",
            "Its services" to services,
            "Phone" to "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), build ${Build.ID}",
        )
        return ConnectionReport.Attempt(device.name, facts, synchronized(steps) { steps.toList() })
    }

    companion object {
        /** How often the brands that poll read the battery again. */
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
    }
}
