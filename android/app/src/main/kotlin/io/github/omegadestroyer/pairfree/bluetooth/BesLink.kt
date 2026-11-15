package io.github.omegadestroyer.pairfree.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import androidx.annotation.RequiresPermission
import io.github.omegadestroyer.pairfree.protocol.soundcore.BesChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID

/**
 * The Sleep A30's second serial channel, the BES service the Soundcore app sends sounds over (docs/soundcore/PROTOCOL.md section 14). A [BesChannel] for
 * `BesUploadRunner`. Opened beside the control channel: how the earbuds take that is not known (UNTESTED ON AN A30).
 */
class BesLink @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT) constructor(device: BluetoothDevice) : BesChannel, AutoCloseable {
    private val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(UUID.fromString(SERVICE))
    private val inbox = Channel<ByteArray>(Channel.UNLIMITED)
    private var reader: Job? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun connect(scope: CoroutineScope) {
        withContext(Dispatchers.IO) {
            try {
                socket.connect()
            } catch (e: IOException) {
                close()
                throw e
            }
        }
        reader = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(1024)
            try {
                while (true) {
                    val n = socket.inputStream.read(buffer)
                    if (n < 0) break
                    inbox.trySend(buffer.copyOf(n))
                }
                inbox.close(IOException("channel closed"))
            } catch (e: IOException) {
                inbox.close(e)
            }
        }
    }

    override suspend fun write(bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            socket.outputStream.write(bytes)
            socket.outputStream.flush()
        }
    }

    /** Throws the channel's failure when it has closed; null when nothing came in time. */
    override suspend fun read(timeoutMs: Long): ByteArray? = withTimeoutOrNull(timeoutMs) { inbox.receive() }

    override fun close() {
        reader?.cancel()
        try {
            socket.close()
        } catch (_: IOException) {
        }
    }

    companion object {
        /** `BesSdkConstants.f30049a` in the Soundcore app 5.0.35. */
        const val SERVICE = "66666666-6666-6666-6666-666666666666"
    }
}
