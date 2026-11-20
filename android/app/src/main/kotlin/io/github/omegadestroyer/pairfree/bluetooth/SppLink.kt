package io.github.omegadestroyer.pairfree.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import io.github.omegadestroyer.pairfree.protocol.FrameParser
import io.github.omegadestroyer.pairfree.protocol.Wire
import io.github.omegadestroyer.pairfree.protocol.summary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * The earbuds' control channel: classic Bluetooth serial (RFCOMM) on [uuid], the standard SPP
 * UUID for SoundPEATS, the earbuds' own service for Soundcore. The earbuds must already be
 * paired and connected for audio. There is no handshake.
 *
 * The socket is created up front so [close] can abort a [connect] that is still waiting.
 * Reads have no timeout on purpose: after a touch control the H3 goes quiet for up to
 * about 15 s and then answers everything at once (docs/soundpeats/PROTOCOL.md section 11).
 */
class SppLink @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT) constructor(
    private val device: BluetoothDevice,
    /** Gets one line per frame sent or received, when set. */
    private val log: ((String) -> Unit)? = null,
    private val uuid: String = Wire.SPP_UUID,
) : ControlLink {
    @Volatile
    private var socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(UUID.fromString(uuid))
    private var closed = false
    private val writeLock = Mutex()
    private var lastWrite = 0L

    /** Blocks until the channel is open. Only one app can hold it; PeatsAudio must be closed. */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun connect() = withContext(Dispatchers.IO) {
        // SOUNDPEATS 2.5.3 tries a standard socket, then an insecure one, then a direct one to channel 1 (SppBluetoothService.createSocket,
        // selectConnectionMethod); a phone's stack that cannot find or open the service record by its UUID may still open one of those.
        // PairFree does the same, the direct one only for the standard serial UUID, whose channel is nearly always 1.
        var failure: IOException? = null
        for (attempt in 1..3) {
            if (closed) break
            val tried = try {
                when (attempt) {
                    1 -> socket
                    2 -> device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(uuid))
                    else -> if (uuid.equals(Wire.SPP_UUID, ignoreCase = true)) directSocket() else null
                }
            } catch (e: Exception) {
                log?.invoke("socket attempt $attempt not made: ${e.javaClass.simpleName}")
                null
            } ?: continue
            socket = tried
            try {
                tried.connect()
                if (attempt > 1) log?.invoke("opened on socket attempt $attempt")
                return@withContext
            } catch (e: IOException) {
                failure = e
                log?.invoke("socket attempt $attempt failed: ${e.javaClass.simpleName}: ${e.message}")
                try {
                    tried.close()
                } catch (_: IOException) {
                }
                if (attempt < 3) delay(RETRY_GAP_MS)
            }
        }
        // A socket that did not open is closed here, as GattLink does: a retry makes a new link and drops this one.
        close()
        throw failure ?: IOException("no socket could be made")
    }

    /** A socket straight to RFCOMM channel 1, without the service lookup (the official app's "reflection" method). */
    @Suppress("DiscouragedPrivateApi")
    private fun directSocket(): BluetoothSocket? =
        device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(device, 1) as? BluetoothSocket

    /** Sends one frame, at least [WRITE_GAP_MS] after the previous one. */
    override suspend fun send(frame: ByteArray): Unit = writeLock.withLock {
        val wait = lastWrite + WRITE_GAP_MS - SystemClock.elapsedRealtime()
        if (wait > 0) delay(wait)
        withContext(Dispatchers.IO) {
            socket.outputStream.write(frame)
            socket.outputStream.flush()
        }
        lastWrite = SystemClock.elapsedRealtime()
        log?.let { log ->
            val frames = FrameParser().feed(frame)
            if (frames.isEmpty()) log("send ${frame.joinToString("") { "%02x".format(it) }}") else frames.forEach { log("send ${it.summary()}") }
        }
        Unit
    }

    /** The bytes of each read, as they come. Ends with an IOException when the channel closes. */
    override fun reads(): Flow<ByteArray> = flow {
        val buffer = ByteArray(1024)
        val input = socket.inputStream
        while (true) {
            val n = input.read(buffer)
            if (n < 0) throw IOException("channel closed")
            emit(buffer.copyOf(n))
        }
    }.flowOn(Dispatchers.IO)

    override fun close() {
        closed = true
        try {
            socket.close()
        } catch (_: IOException) {
        }
    }

    companion object {
        /** The official app leaves 50 ms between writes (CLAUDE.md). */
        const val WRITE_GAP_MS = 50L

        /** A pause before the next socket attempt (the official app waits 1.5 s and more). */
        const val RETRY_GAP_MS = 700L
    }
}
