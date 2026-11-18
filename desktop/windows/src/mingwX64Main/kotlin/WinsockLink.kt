@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.omegadestroyer.pairfree.windows

import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.protocol.FrameParser
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import platform.posix.INVALID_SOCKET
import platform.posix.SOCK_STREAM
import platform.posix.WSADATA
import platform.posix.WSAGetLastError
import platform.posix.WSAStartup
import platform.posix.closesocket
import platform.posix.connect
import platform.posix.recv
import platform.posix.send
import platform.posix.socket

private const val AF_BTH = 32
private const val BTHPROTO_RFCOMM = 3

/** Starts Windows' sockets once for the program. */
internal object Winsock {
    init {
        memScoped {
            val data = alloc<WSADATA>()
            check(WSAStartup(0x0202u, data.ptr) == 0) { "Windows' sockets did not start" }
        }
    }

    fun ensure() = Unit
}

/** A GUID's text as the 16 bytes Windows lays it out in. */
private fun guid(text: String): ByteArray {
    val h = text.replace("-", "")
    fun b(i: Int) = h.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    return byteArrayOf(b(3), b(2), b(1), b(0), b(5), b(4), b(7), b(6)) + ByteArray(8) { b(8 + it) }
}

/** The address "AA:BB:CC:DD:EE:FF" as the number Windows' Bluetooth sockets take. */
internal fun addressNumber(address: String): Long =
    address.replace(":", "").toLongOrNull(16) ?: throw IOException("not a Bluetooth address: $address")

/** Windows' SOCKADDR_BTH (packed, 30 bytes): family, the address as a number, the service's GUID, and port 0 so Windows looks the channel up. */
private fun sockaddr(address: Long, service: String): ByteArray {
    val b = ByteArray(30)
    b[0] = AF_BTH.toByte()
    for (i in 0 until 8) b[2 + i] = (address shr (8 * i)).toByte()
    guid(service).copyInto(b, 10)
    return b
}

/**
 * The classic serial channel (RFCOMM) of paired earbuds, over Windows' Bluetooth sockets, as Android's SppLink is over its own:
 * the same reading until the channel closes, with the official app's 50 ms between writes.
 */
internal class WinsockLink(private val address: String, private val service: String, private val log: ((String) -> Unit)?) : ControlLink {
    private var socket: ULong = INVALID_SOCKET
    private val writeLock = Mutex()
    private var lastWrite: TimeMark? = null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        Winsock.ensure()
        val s = socket(AF_BTH, SOCK_STREAM, BTHPROTO_RFCOMM)
        if (s == INVALID_SOCKET) throw IOException("no Bluetooth socket (Windows error ${WSAGetLastError()})")
        socket = s
        val to = sockaddr(addressNumber(address), service)
        val result = to.usePinned { connect(s, it.addressOf(0).reinterpret(), to.size) }
        if (result != 0) {
            val error = WSAGetLastError()
            close()
            throw IOException("could not open the serial channel (Windows error $error)")
        }
    }

    override suspend fun send(frame: ByteArray): Unit = writeLock.withLock {
        val s = socket
        if (s == INVALID_SOCKET) throw IOException("not connected")
        val wait = lastWrite?.let { WRITE_GAP_MS - it.elapsedNow().inWholeMilliseconds } ?: 0
        if (wait > 0) delay(wait)
        withContext(Dispatchers.IO) {
            var sent = 0
            while (sent < frame.size) {
                val n = frame.usePinned { send(s, it.addressOf(sent).reinterpret(), frame.size - sent, 0) }
                if (n <= 0) throw IOException("send failed (Windows error ${WSAGetLastError()})")
                sent += n
            }
        }
        lastWrite = TimeSource.Monotonic.markNow()
        log?.let { log ->
            val frames = FrameParser().feed(frame)
            if (frames.isEmpty()) log("send " + frame.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }) else frames.forEach { log("send " + it) }
        }
    }

    override fun reads(): Flow<ByteArray> = flow {
        val buffer = ByteArray(1024)
        while (true) {
            val s = socket
            if (s == INVALID_SOCKET) throw IOException("channel closed")
            val n = buffer.usePinned { recv(s, it.addressOf(0).reinterpret(), buffer.size, 0) }
            if (n <= 0) throw IOException("channel closed")
            emit(buffer.copyOf(n))
        }
    }.flowOn(Dispatchers.IO)

    override fun close() {
        val s = socket
        socket = INVALID_SOCKET
        if (s != INVALID_SOCKET) closesocket(s)
    }

    private companion object {
        /** The official app leaves 50 ms between writes (CLAUDE.md). */
        const val WRITE_GAP_MS = 50L
    }
}
