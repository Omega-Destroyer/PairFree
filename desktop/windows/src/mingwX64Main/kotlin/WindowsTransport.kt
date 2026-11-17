package io.github.omegadestroyer.pairfree.windows

import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.brands.GattChannel
import io.github.omegadestroyer.pairfree.brands.GattSpec
import io.github.omegadestroyer.pairfree.brands.LeTarget
import io.github.omegadestroyer.pairfree.brands.PlatformInfo
import io.github.omegadestroyer.pairfree.brands.QcyFound
import io.github.omegadestroyer.pairfree.brands.Transport
import io.github.omegadestroyer.pairfree.protocol.jbl.JblModel
import kotlinx.io.IOException

/**
 * Windows' Bluetooth as the brands open their channels through it. So far the classic serial channel (most brands); Bluetooth LE
 * and the AirPods' channel are not here yet (docs/DESKTOP.md, phase 4, and the AirPods decision), and a brand that asks for them
 * fails to open as a channel that could not be opened.
 */
class WindowsTransport : Transport {
    override val isOn: Boolean get() = true

    override fun notPermitted(failure: Throwable): Boolean = false

    override fun hiddenBySystem(failure: Throwable): Boolean = false

    override fun deviceFacts(address: String): List<Pair<String, String>> = emptyList()

    override val hasLe: Boolean get() = false

    override val mayScan: Boolean get() = false

    override val opensAppleChannel: Boolean get() = false

    override fun isLeOnly(address: String): Boolean = false

    override fun hostName(): String? = null

    override fun at(address: String): LeTarget = throw IOException("Bluetooth LE is not available yet on Windows")

    override fun serial(address: String, uuid: String, log: (String) -> Unit): ControlLink = WinsockLink(address, uuid, log)

    override fun gatt(target: LeTarget, spec: GattSpec, log: (String) -> Unit): GattChannel = throw IOException("Bluetooth LE is not available yet on Windows")

    override fun envelopeGatt(target: LeTarget, service: String, log: (String) -> Unit): ControlLink = throw IOException("Bluetooth LE is not available yet on Windows")

    override fun appleChannel(address: String, log: (String) -> Unit): ControlLink = throw IOException("AirPods are not supported on Windows")

    override suspend fun findJbl(model: JblModel, address: String, log: (String) -> Unit): LeTarget? = null

    override suspend fun findQcy(company: Int, address: String, log: (String) -> Unit): QcyFound? = null

    override suspend fun findSennheiser(names: List<String>, address: String, log: (String) -> Unit): LeTarget? = null

    companion object {
        /** What the connection and the reports say about this computer. */
        val platform = PlatformInfo(
            os = "Windows",
            osAndDevice = "Windows",
            madeBySamsung = false,
            androidSdk = 0,
            hostLabel = "Computer",
            host = "Windows",
        )
    }
}
