@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)

package io.github.omegadestroyer.pairfree.app

import io.github.omegadestroyer.pairfree.bluetooth.EarbudsConnection
import io.github.omegadestroyer.pairfree.bluetooth.LinkStatus
import io.github.omegadestroyer.pairfree.brands.modelOf
import io.github.omegadestroyer.pairfree.eq.EqOpener
import io.github.omegadestroyer.pairfree.eq.Equalizer
import io.github.omegadestroyer.pairfree.shell.DesktopSettings
import io.github.omegadestroyer.pairfree.shell.DesktopStrings
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
        // One thread for the connection, as on Android where it is the main thread's: EarbudsConnection and Equalizer are not thread safe.
        val scope = CoroutineScope(newSingleThreadContext("pairfree") + SupervisorJob())
        val connection = EarbudsConnection(scope, WindowsTransport(), WindowsTransport.platform)
        val equalizer = Equalizer(settings, scope, { frames -> connection.sendEq(frames) }, { DesktopStrings(settings.language).text("eq_imported_name") })
        val opener = EqOpener(connection, equalizer, scope)
        val app = DesktopApp(connection, equalizer, settings, host, ::windowsColors, invalidate) { block -> scope.launch { block() } }
        scope.launch {
            combine(connection.status, connection.state) { s, e -> s to e }.collect { (status, state) ->
                // The equalizer opens for the connected earbuds as Android's screens open it, and a new channel gets the curve this computer last set.
                val device = (status as? LinkStatus.Connected)?.device
                if (device != null) opener.open(device, modelOf(device, state))
                invalidate()
            }
        }
        scope.launch { connection.status.collect { if (it is LinkStatus.Connected) equalizer.channelOpened(it.device.address) } }
        scope.launch { equalizer.state.collect { invalidate() } }
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
