@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.omegadestroyer.pairfree.windows

import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.brands.GattChannel
import io.github.omegadestroyer.pairfree.brands.GattSpec
import io.github.omegadestroyer.pairfree.brands.LeTarget
import io.github.omegadestroyer.pairfree.brands.LocalTime
import io.github.omegadestroyer.pairfree.brands.PlatformInfo
import io.github.omegadestroyer.pairfree.brands.QcyFound
import io.github.omegadestroyer.pairfree.brands.Transport
import io.github.omegadestroyer.pairfree.protocol.jbl.JblModel
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.io.IOException
import platform.windows.GetLocalTime
import platform.windows.SYSTEMTIME

/**
 * Windows' Bluetooth as the brands open their channels through it: the classic serial channel (Winsock) and Bluetooth LE (the Windows
 * Runtime's GATT and advertisement APIs). The AirPods' channel is not here (docs/DESKTOP.md): Windows gives programs no L2CAP.
 */
class WindowsTransport : Transport {
    override val isOn: Boolean get() = true

    override fun notPermitted(failure: Throwable): Boolean = false

    override fun hiddenBySystem(failure: Throwable): Boolean = false

    override fun deviceFacts(address: String): List<Pair<String, String>> = emptyList()

    override val hasLe: Boolean get() = true

    override val mayScan: Boolean get() = true

    override val opensAppleChannel: Boolean get() = false

    override fun isLeOnly(address: String): Boolean = false

    override fun hostName(): String? = computerName()

    override fun localTime(): LocalTime = memScoped {
        val t = alloc<SYSTEMTIME>()
        GetLocalTime(t.ptr)
        LocalTime(t.wYear.toInt(), t.wMonth.toInt(), t.wDay.toInt(), t.wDayOfWeek.toInt(), t.wHour.toInt(), t.wMinute.toInt(), t.wSecond.toInt())
    }

    override fun at(address: String): LeTarget = WinLeTarget(address)

    override fun serial(address: String, uuid: String, log: (String) -> Unit): ControlLink = WinsockLink(address, uuid, log)

    override fun gatt(target: LeTarget, spec: GattSpec, log: (String) -> Unit): GattChannel = WinRtGattChannel(target.address, spec, log)

    override fun envelopeGatt(target: LeTarget, service: String, log: (String) -> Unit): ControlLink = WinRtEnvelopeLink(target.address, service, log)

    override fun appleChannel(address: String, log: (String) -> Unit): ControlLink = throw IOException("AirPods are not supported on Windows")

    override suspend fun findJbl(model: JblModel, address: String, log: (String) -> Unit): LeTarget? = LeScan.findJbl(model, address, hostName(), log)

    override suspend fun findQcy(company: Int, address: String, log: (String) -> Unit): QcyFound? = LeScan.findQcy(company, address, log)

    override suspend fun findSennheiser(names: List<String>, address: String, log: (String) -> Unit): LeTarget? = LeScan.findSennheiser(names, address, log)

    override suspend fun findSkullcandy(names: List<String>, address: String, log: (String) -> Unit): LeTarget? = LeScan.findSkullcandy(names, address, log)

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

/** This computer's name, which JBL's earbuds advertise a checksum of while connected to it. */
private fun computerName(): String? = memScoped {
    val buffer = allocArray<platform.windows.WCHARVar>(256)
    val size = alloc<kotlinx.cinterop.UIntVar>()
    size.value = 256u
    if (platform.windows.GetComputerNameW(buffer, size.ptr) != 0) buffer.toKString() else null
}
