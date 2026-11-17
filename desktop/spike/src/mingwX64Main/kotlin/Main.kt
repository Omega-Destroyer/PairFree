@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.omegadestroyer.pairfree.spike

import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.FrameParser
import io.github.omegadestroyer.pairfree.protocol.Maker
import io.github.omegadestroyer.pairfree.protocol.Requests
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.posix.INVALID_SOCKET
import platform.posix.SOCK_STREAM
import platform.windows.Sleep
import platform.posix.WSACleanup
import platform.posix.WSADATA
import platform.posix.WSAGetLastError
import platform.posix.WSAStartup
import platform.posix.closesocket
import platform.posix.connect
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.socket

private const val AF_BTH = 32
private const val BTHPROTO_RFCOMM = 3
private const val SOL_SOCKET = 0xFFFF
private const val SO_RCVTIMEO = 0x1006

/** The serial port service every SOUNDPEATS model listens on, 00001101-0000-1000-8000-00805F9B34FB, as Windows lays a GUID out. */
private val SPP = byteArrayOf(0x01, 0x11, 0, 0, 0, 0, 0, 0x10, 0x80.toByte(), 0, 0, 0x80.toByte(), 0x5F, 0x9B.toByte(), 0x34, 0xFB.toByte())

/** Windows' SOCKADDR_BTH (packed, 30 bytes): family, the address as a number, the service's GUID, and port 0 so Windows looks the channel up. */
private fun sockaddr(address: Long): ByteArray {
    val b = ByteArray(30)
    b[0] = AF_BTH.toByte()
    for (i in 0 until 8) b[2 + i] = (address shr (8 * i)).toByte()
    SPP.copyInto(b, 10)
    return b
}

/**
 * Step two of the spike: `PairFreeSpike.exe AABBCCDDEEFF` opens the serial channel of paired SOUNDPEATS earbuds through Winsock, sends the
 * three battery reads (read-only, as the probe) and prints what the shared protocol code makes of the answers.
 */
fun main(args: Array<String>) {
    println("PairFree spike: brands known = ${Maker.entries.size}")
    val address = args.firstOrNull()?.replace(":", "")?.toLongOrNull(16) ?: return println("usage: PairFreeSpike <bluetooth address>")
    memScoped {
        val data = alloc<WSADATA>()
        check(WSAStartup(0x0202u, data.ptr) == 0) { "WSAStartup failed" }
        val s = socket(AF_BTH, SOCK_STREAM, BTHPROTO_RFCOMM)
        if (s == INVALID_SOCKET) return println("socket failed: ${WSAGetLastError()}")
        val to = sockaddr(address)
        val connected = to.usePinned { connect(s, it.addressOf(0).reinterpret(), to.size) }
        if (connected != 0) return println("connect failed: ${WSAGetLastError()}")
        println("serial channel open")
        val timeout = intArrayOf(500)
        timeout.usePinned { setsockopt(s, SOL_SOCKET, SO_RCVTIMEO, it.addressOf(0).reinterpret(), 4) }
        for (command in Requests.BATTERY) {
            val frame = Requests.get(command)
            frame.usePinned { send(s, it.addressOf(0).reinterpret(), frame.size, 0) }
            Sleep(50u)
        }
        val parser = FrameParser()
        var state = EarbudState()
        val buffer = ByteArray(1024)
        repeat(6) {
            val n = buffer.usePinned { recv(s, it.addressOf(0).reinterpret(), buffer.size, 0) }
            if (n > 0) for (frame in parser.feed(buffer.copyOf(n))) {
                println("  $frame")
                state = state.update(frame)
            }
        }
        println("battery: left ${state.batteryLeft} right ${state.batteryRight} case ${state.batteryCase}")
        closesocket(s)
        WSACleanup()
    }
}
