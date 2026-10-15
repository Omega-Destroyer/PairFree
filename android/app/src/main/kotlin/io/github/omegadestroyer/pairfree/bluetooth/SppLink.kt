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
    device: BluetoothDevice,
    /** Gets one line per frame sent or received, when set. */
    private val log: ((String) -> Unit)? = null,
    uuid: String = Wire.SPP_UUID,
) : ControlLink {
    private val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(UUID.fromString(uuid))
    private val writeLock = Mutex()
    private var lastWrite = 0L

    /** Blocks until the channel is open. Only one app can hold it; PeatsAudio must be closed. */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun connect() = withContext(Dispatchers.IO) { socket.connect() }

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
        try {
            socket.close()
        } catch (_: IOException) {
        }
    }

    companion object {
        /** The official app leaves 50 ms between writes (CLAUDE.md). */
        const val WRITE_GAP_MS = 50L
    }
}
