@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)

package io.github.omegadestroyer.pairfree.spike

import io.github.omegadestroyer.pairfree.bluetooth.EarbudsConnection
import io.github.omegadestroyer.pairfree.bluetooth.LinkStatus
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.brands.Brands
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Maker
import io.github.omegadestroyer.pairfree.windows.WindowsTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import platform.windows.PostMessageW

/** The brands the core module lists and the models their tables hold, to show the whole app's code is in the EXE. */
private fun linkedSummary(): String {
    val specs = Brands.ALL.size
    return "${Maker.entries.size} brands, $specs specs: the core module's connection, drivers and equalizer logic"
}

private fun levels(state: EarbudState): String =
    "L ${state.batteryLeft?.let { "$it%" } ?: "?"}   R ${state.batteryRight?.let { "$it%" } ?: "?"}   Case ${state.batteryCase?.let { "$it%" } ?: "?"}"

/**
 * The spike: `PairFreeSpike.exe AABBCCDDEEFF` opens a window at once and connects to the paired SOUNDPEATS earbuds at that address
 * through the same [EarbudsConnection] the Android app runs, over Windows' Bluetooth ([WindowsTransport]), and shows what the shared
 * brand code makes of them.
 */
fun main(args: Array<String>) {
    if (args.firstOrNull() == "--gatt") return gattServices(args.getOrNull(1)?.replace(":", "")?.toLongOrNull(16) ?: return)
    val address = args.firstOrNull()
    linkedLine = linkedSummary()
    if (address == null) statusLine = "No address given"
    runWindow { window ->
        if (address == null) return@runWindow
        // One thread for the connection, as on Android where it is the main thread's (EarbudsConnection is not thread safe).
        val scope = CoroutineScope(newSingleThreadContext("pairfree") + SupervisorJob())
        val connection = EarbudsConnection(scope, WindowsTransport(), WindowsTransport.platform)
        scope.launch {
            combine(connection.status, connection.state) { status, state ->
                when (status) {
                    is LinkStatus.Connected -> levels(state)
                    is LinkStatus.Failed -> {
                        linkedLine = status.attempt?.steps?.joinToString(" | ") ?: ""
                        val out = platform.posix.fopen("spike-steps.txt", "w")
                        if (out != null) {
                            platform.posix.fputs(status.attempt?.steps?.joinToString(" | ").orEmpty(), out)
                            platform.posix.fclose(out)
                        }
                        "Not connected: ${status.reason.name.lowercase().replace('_', ' ')}"
                    }
                    else -> "Connecting"
                }
            }.collect {
                statusLine = it
                PostMessageW(window, WM_STATUS, 0u, 0)
            }
        }
        scope.launch { connection.connect(Paired(address, "SOUNDPEATS H3", Maker.SOUNDPEATS)) }
    }
}
