@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.native.concurrent.ObsoleteWorkersApi::class)

package io.github.omegadestroyer.pairfree.spike

import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.FrameParser
import io.github.omegadestroyer.pairfree.protocol.Maker
import io.github.omegadestroyer.pairfree.protocol.Requests
import io.github.omegadestroyer.pairfree.protocol.SoundpeatsTalk
import io.github.omegadestroyer.pairfree.protocol.Talk
import io.github.omegadestroyer.pairfree.protocol.apple.*
import io.github.omegadestroyer.pairfree.protocol.bose.*
import io.github.omegadestroyer.pairfree.protocol.cambridge.*
import io.github.omegadestroyer.pairfree.protocol.earfun.*
import io.github.omegadestroyer.pairfree.protocol.jbl.*
import io.github.omegadestroyer.pairfree.protocol.nothing.*
import io.github.omegadestroyer.pairfree.protocol.onemore.*
import io.github.omegadestroyer.pairfree.protocol.oneplus.*
import io.github.omegadestroyer.pairfree.protocol.pixel.*
import io.github.omegadestroyer.pairfree.protocol.qcy.*
import io.github.omegadestroyer.pairfree.protocol.samsung.*
import io.github.omegadestroyer.pairfree.protocol.sennheiser.*
import io.github.omegadestroyer.pairfree.protocol.sony.*
import io.github.omegadestroyer.pairfree.protocol.soundcore.*
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import platform.windows.PostMessageW
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

/** Every brand's conversation made and started once, so the linker keeps all of them and the EXE's size and memory are the real app's. */
private fun linkAll(): String {
    val talks: List<Talk<*>> = listOf(
        SoundpeatsTalk("H3"), SoundcoreTalk(""), AppleTalk(), BoseTalk(), NothingTalk(null), OneMoreTalk(null), OneplusTalk(null),
        PixelTalk(PixelTransport.MAESTRO, null), SennheiserSession(null), SonySession(1),
        CambridgeTalk(CambridgeModels.ALL.first()), CambridgeConnectTalk(CambridgeModels.ALL.first()), EarfunTalk(EarfunModels.ALL.first()),
        JblTalk(JblModels.ALL.first(), false), QcyTalk(QcyControl.of(QcyModels.ALL.first(), null)),
        Gaia2Session(Gaia2Models.ALL.first()), NautilusSession(NautilusModels.ALL.first()),
    )
    val messages = talks.sumOf { it.start().size + it.groups.size }
    val models = SoundcoreModels.ALL.size + SamsungModels.ALL.size + SonyModels.ALL.size + BoseModels.ALL.size + JblModels.ALL.size + AppleModels.ALL.size +
        NothingModels.ALL.size + PixelModels.ALL.size + QcyModels.ALL.size + OneMoreModels.ALL.size + EarfunModels.ALL.size + CambridgeModels.ALL.size +
        OneplusModels.ALL.size + SennheiserModels.ALL.size + Gaia2Models.ALL.size + NautilusModels.ALL.size
    return "${Maker.entries.size} brands, $models models and ${talks.size} conversations linked in ($messages opening messages)"
}

/** The three battery reads (read-only, as the probe) over Windows' Bluetooth sockets, decoded by the shared protocol code. */
private fun readBattery(address: Long): String = memScoped {
    val data = alloc<WSADATA>()
    check(WSAStartup(0x0202u, data.ptr) == 0) { "WSAStartup failed" }
    val s = socket(AF_BTH, SOCK_STREAM, BTHPROTO_RFCOMM)
    if (s == INVALID_SOCKET) return "socket failed: ${WSAGetLastError()}"
    val to = sockaddr(address)
    val connected = to.usePinned { connect(s, it.addressOf(0).reinterpret(), to.size) }
    if (connected != 0) return "Not connected (Windows error ${WSAGetLastError()})"
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
    var tries = 0
    while (tries++ < 6 && (state.batteryLeft == null || state.batteryRight == null || state.batteryCase == null)) {
        val n = buffer.usePinned { recv(s, it.addressOf(0).reinterpret(), buffer.size, 0) }
        if (n > 0) for (frame in parser.feed(buffer.copyOf(n))) state = state.update(frame)
    }
    closesocket(s)
    WSACleanup()
    "L ${state.batteryLeft ?: "?"}%   R ${state.batteryRight ?: "?"}%   Case ${state.batteryCase ?: "?"}%"
}

/**
 * The spike: `PairFreeSpike.exe AABBCCDDEEFF` opens a window at once, then reads the battery of paired SOUNDPEATS earbuds on another
 * thread and shows it.
 */
fun main(args: Array<String>) {
    val address = args.firstOrNull()?.replace(":", "")?.toLongOrNull(16)
    linkedLine = linkAll()
    if (address == null) statusLine = "No address given"
    runWindow { window ->
        if (address != null) {
            Worker.start().execute(TransferMode.SAFE, { address to window }) { (a, w) ->
                statusLine = readBattery(a)
                PostMessageW(w, WM_STATUS, 0u, 0)
                Unit
            }
        }
    }
}
