@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)

package io.github.omegadestroyer.pairfree.app

import io.github.omegadestroyer.pairfree.bluetooth.EarbudsConnection
import io.github.omegadestroyer.pairfree.shell.DesktopSettings
import io.github.omegadestroyer.pairfree.ui.DesktopApp
import io.github.omegadestroyer.pairfree.windows.PairedDevices
import io.github.omegadestroyer.pairfree.windows.WindowsTransport
import io.github.omegadestroyer.pairfree.windows.runAppWindow
import io.github.omegadestroyer.pairfree.windows.windowsColors
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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

fun main() {
    val settingsPath = Path(programFolder(), DesktopSettings.FILE_NAME)
    val settings = DesktopSettings(settingsPath)
    runAppWindow(settings, settingsPath.toString()) { host, invalidate ->
        // One thread for the connection, as on Android where it is the main thread's: EarbudsConnection is not thread safe.
        val scope = CoroutineScope(newSingleThreadContext("pairfree") + SupervisorJob())
        val connection = EarbudsConnection(scope, WindowsTransport(), WindowsTransport.platform)
        scope.launch { combine(connection.status, connection.state) { s, e -> s to e }.collect { invalidate() } }
        val app = DesktopApp(connection, settings, host, ::windowsColors, invalidate) { block -> scope.launch { block() } }
        // The earbuds Windows has paired, now and every few seconds, so earbuds paired or removed while PairFree runs show up.
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (true) {
                app.setDevices(PairedDevices.earbuds())
                delay(10_000)
            }
        }
        app
    }
}
