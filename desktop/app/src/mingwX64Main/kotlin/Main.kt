@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)

package io.github.omegadestroyer.pairfree.app

import io.github.omegadestroyer.pairfree.bluetooth.EarbudsConnection
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.protocol.Maker
import io.github.omegadestroyer.pairfree.shell.DesktopSettings
import io.github.omegadestroyer.pairfree.ui.DesktopApp
import io.github.omegadestroyer.pairfree.windows.WindowsTransport
import io.github.omegadestroyer.pairfree.windows.runAppWindow
import io.github.omegadestroyer.pairfree.windows.windowsColors
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.io.files.Path
import platform.windows.GetModuleFileNameW
import platform.windows.WCHARVar

/** The folder the program is in: its settings file lies beside it. */
private fun programFolder(): String = memScoped {
    val buffer = allocArray<WCHARVar>(1024)
    GetModuleFileNameW(null, buffer, 1024u)
    buffer.toKString().substringBeforeLast('\\', ".")
}

/** `PairFree.exe AABBCCDDEEFF ["Name"]`: until the list of paired earbuds is built, the earbuds are named on the command line. */
fun main(args: Array<String>) {
    val settings = DesktopSettings(Path(programFolder(), DesktopSettings.FILE_NAME))
    val address = args.firstOrNull()?.replace(":", "")?.chunked(2)?.joinToString(":") ?: "98:80:BB:41:92:E1"
    val name = args.getOrNull(1) ?: "SOUNDPEATS H3"
    runAppWindow("PairFree", settings) { invalidate ->
        // One thread for the connection, as on Android where it is the main thread's: EarbudsConnection is not thread safe.
        val scope = CoroutineScope(newSingleThreadContext("pairfree") + SupervisorJob())
        val connection = EarbudsConnection(scope, WindowsTransport(), WindowsTransport.platform)
        val device = Paired(address, name, Maker.SOUNDPEATS)
        scope.launch { combine(connection.status, connection.state) { s, e -> s to e }.collect { invalidate() } }
        scope.launch { connection.connect(device) }
        DesktopApp(connection, device, settings, ::windowsColors, invalidate) { block -> scope.launch { block() } }
    }
}
