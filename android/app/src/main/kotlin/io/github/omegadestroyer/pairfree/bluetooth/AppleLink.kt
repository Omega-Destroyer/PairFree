package io.github.omegadestroyer.pairfree.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Build
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import io.github.omegadestroyer.pairfree.protocol.apple.AppleWire
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.lang.reflect.InvocationTargetException

/**
 * The AirPods' control channel: Apple's accessory protocol on classic Bluetooth L2CAP, PSM 0x1001
 * (docs/apple/PROTOCOL.md section 1). Android's public L2CAP call (API 36's createUsingSocketSettings) takes PSMs up to 255
 * only, so the socket comes from BluetoothSocket's hidden constructors, tried in turn for each Android version's signature,
 * as LibrePods does (github.com/kavishdevar/librepods, GPL-3.0-or-later, BluetoothConnectionManager.kt). Android's Bluetooth
 * stack then refuses the AirPods' channel on most phones before Android 17 ([AppleSupport]). Each read is one packet.
 */
/** Android's hidden-API rules show apps none of [BluetoothSocket]'s constructors, so the AirPods' channel cannot be opened. */
class ConstructorsHidden(message: String) : IOException(message)

class AppleLink @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT) constructor(
    adapter: BluetoothAdapter,
    device: BluetoothDevice,
    private val log: ((String) -> Unit)? = null,
) : ControlLink {
    private val socket: BluetoothSocket = l2capSocket(adapter, device, log)
    private val writeLock = Mutex()

    /** Opens the channel, or gives up after [CONNECT_MS] as LibrePods does. */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun connect() = coroutineScope {
        val watchdog = launch {
            delay(CONNECT_MS)
            log?.invoke("no channel after $CONNECT_MS ms: closing the socket")
            close()
        }
        try {
            withContext(Dispatchers.IO) { socket.connect() }
        } finally {
            watchdog.cancel()
        }
    }

    override suspend fun send(frame: ByteArray): Unit = writeLock.withLock {
        withContext(Dispatchers.IO) {
            socket.outputStream.write(frame)
            socket.outputStream.flush()
        }
        log?.invoke("send ${frame.joinToString("") { "%02x".format(it) }}")
    }

    override fun reads(): Flow<ByteArray> = flow {
        val buffer = ByteArray(READ_SIZE)
        val input = socket.inputStream
        while (true) {
            val n = input.read(buffer)
            if (n < 0) throw IOException("channel closed")
            emit(buffer.copyOf(n))
        }
    }.flowOn(Dispatchers.IO)

    override fun close() {
        try {
            socket.close()
        } catch (_: IOException) {
        }
    }

    companion object {
        /** LibrePods gives the connection 5 s. */
        const val CONNECT_MS = 5_000L

        private const val READ_SIZE = 4096

        /** The signatures of BluetoothSocket's constructors, for a connection report when none of ours fits (hidden API rules may hide them). */
        private fun constructorsOf(): String = try {
            BluetoothSocket::class.java.declaredConstructors.joinToString(" | ") { c -> c.parameterTypes.joinToString(",") { it.simpleName } }.ifEmpty { "none listed" }
        } catch (e: RuntimeException) {
            "not readable"
        }

        /** What the hidden-API exemption did, for the connection report; null until it is tried. */
        @Volatile private var exemption: String? = null

        /**
         * Android's hidden-API rules can hide BluetoothSocket's constructors from apps (Android 17 on a Pixel 8 listed none).
         * This asks the app's own runtime to relax them for that one class, which needs no root (owner, 2026-11-10; LibrePods
         * does the same with VMRuntime.setHiddenApiExemptions). It is tried only after the constructors came back hidden, only
         * for AirPods, and says so in the connection report.
         */
        private fun exemptBluetoothSocket(log: ((String) -> Unit)?) {
            exemption = try {
                val ok = org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/BluetoothSocket;")
                if (ok) "applied to BluetoothSocket" else "refused by this Android"
            } catch (e: Throwable) {
                "failed: ${e.javaClass.simpleName}"
            }
            log?.invoke("hidden-API exemption for BluetoothSocket: $exemption")
        }

        /** BluetoothSocket's type for L2CAP. */
        private const val TYPE_L2CAP = 3

        /**
         * An L2CAP socket on [AppleWire.PSM], authenticated and encrypted, through the first of
         * BluetoothSocket's hidden constructors that this Android version has (LibrePods' list, newest first).
         */
        private fun l2capSocket(adapter: BluetoothAdapter, device: BluetoothDevice, log: ((String) -> Unit)?): BluetoothSocket {
            val uuid = ParcelUuid.fromString(AppleWire.SERVICE_UUID)
            val psm = AppleWire.PSM
            val failures = mutableListOf<String>()
            val seenFirst = constructorsOf()
            if (seenFirst == "none listed" || seenFirst == "not readable") exemptBluetoothSocket(log)
            val signatures = listOf(
                arrayOf(adapter, device, TYPE_L2CAP, true, true, psm, uuid), // Android 16 QPR3
                arrayOf(device, TYPE_L2CAP, true, true, psm, uuid),
                arrayOf(device, TYPE_L2CAP, 1, true, true, psm, uuid),
                arrayOf(TYPE_L2CAP, 1, true, true, device, psm, uuid),
                arrayOf(TYPE_L2CAP, true, true, device, psm, uuid),
            )
            for ((i, args) in signatures.withIndex()) {
                try {
                    val types = args.map { it::class.javaPrimitiveType ?: it::class.java }.toTypedArray()
                    val constructor = BluetoothSocket::class.java.getDeclaredConstructor(*types)
                    constructor.isAccessible = true
                    return (constructor.newInstance(*args) as BluetoothSocket)
                        .also { log?.invoke("socket from BluetoothSocket's hidden constructor ${i + 1} of ${signatures.size}") }
                } catch (e: InvocationTargetException) {
                    // The constructor is there and refused: what it threw says why.
                    failures += "hidden constructor ${i + 1} threw ${e.cause ?: e}"
                } catch (e: NoSuchMethodException) {
                    failures += "hidden constructor ${i + 1}: absent"
                } catch (e: ReflectiveOperationException) {
                    failures += "hidden constructor ${i + 1}: ${e.javaClass.simpleName}"
                } catch (e: SecurityException) {
                    failures += "hidden constructor ${i + 1}: ${e.javaClass.simpleName}"
                } catch (e: IllegalArgumentException) {
                    failures += "hidden constructor ${i + 1}: ${e.javaClass.simpleName}"
                }
            }
            val seen = constructorsOf()
            val exempted = if (exemption == null) "no exemption tried" else exemption
            val message = "no L2CAP socket on Android ${Build.VERSION.SDK_INT}: ${failures.joinToString("; ")}; this Android's constructors: $seen; hidden-API exemption: $exempted"
            // No constructor listed at all means Android's hidden-API rules hide the class's constructors from apps. PairFree does not
            // switch those rules off (owner, 2026-11-10), so there is nothing more to try.
            if (seen == "none listed" || seen == "not readable") throw ConstructorsHidden(message)
            throw IOException(message)
        }
    }
}

/**
 * Where the phone's Bluetooth stack opens the AirPods' channel without root, by LibrePods' list
 * (RootlessSupport.kt): Android 17 and later, which has Google's fix (issue 371713238); a Pixel on
 * Android 16 QPR3 (build ids starting CP1A); and OnePlus, OPPO and realme phones on Android 16.
 */
object AppleSupport {
    fun opensChannel(): Boolean {
        if (Build.VERSION.SDK_INT >= ANDROID_17) return true
        val maker = Build.MANUFACTURER.lowercase()
        if (maker == "google" && Build.VERSION.SDK_INT == ANDROID_16) return Build.ID.startsWith(PIXEL_QPR3)
        if (maker in OPPO_FAMILY) return Build.VERSION.SDK_INT >= ANDROID_16
        return false
    }

    private const val ANDROID_16 = 36
    private const val ANDROID_17 = 37
    private const val PIXEL_QPR3 = "CP1A"
    private val OPPO_FAMILY = setOf("oneplus", "oppo", "realme")
}
