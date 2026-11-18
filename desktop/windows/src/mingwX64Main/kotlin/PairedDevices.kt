@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.omegadestroyer.pairfree.windows

import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.brands.Brands
import io.github.omegadestroyer.pairfree.protocol.Identify
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.windows.GetProcAddress
import platform.windows.LoadLibraryW

/** A device Windows has paired: what its Bluetooth settings list. */
data class WindowsDevice(val address: String, val name: String, val audio: Boolean, val connected: Boolean, val services: List<String>)

/**
 * The paired Bluetooth devices, from the system's own device search (bthprops, found by name since the toolchain's headers do not declare
 * it), and the earbuds among them, told as Android's list is: an audio device whose brand [Identify] knows from its services or its name.
 */
object PairedDevices {
    private const val INFO_SIZE = 560
    private const val NAME_AT = 64
    private val library by lazy { LoadLibraryW("bthprops.cpl") }

    private inline fun <reified F : Function<*>> bth(name: String): CPointer<CFunction<F>>? = library?.let { GetProcAddress(it, name)?.reinterpret() }

    private fun address(info: CPointer<ByteVar>): String = (5 downTo 0).joinToString(":") { (info[8 + it].toInt() and 0xFF).toString(16).uppercase().padStart(2, '0') }

    private fun guid(g: CPointer<ByteVar>): String {
        fun b(i: Int) = (g[i].toInt() and 0xFF).toString(16).padStart(2, '0')
        return listOf(3, 2, 1, 0).joinToString("") { b(it) } + "-" + b(5) + b(4) + "-" + b(7) + b(6) + "-" + b(8) + b(9) + "-" + (10..15).joinToString("") { b(it) }
    }

    /** Every paired device Windows knows, connected or not; empty where Windows' Bluetooth search is not there. */
    fun all(): List<WindowsDevice> {
        val first = bth<(COpaquePointer?, COpaquePointer?) -> COpaquePointer?>("BluetoothFindFirstDevice") ?: return emptyList()
        val next = bth<(COpaquePointer?, COpaquePointer?) -> Int>("BluetoothFindNextDevice") ?: return emptyList()
        val close = bth<(COpaquePointer?) -> Int>("BluetoothFindDeviceClose") ?: return emptyList()
        val services = bth<(COpaquePointer?, COpaquePointer?, COpaquePointer?, COpaquePointer?) -> UInt>("BluetoothEnumerateInstalledServices")
        val out = mutableListOf<WindowsDevice>()
        memScoped {
            val params = allocArray<ByteVar>(40)
            params.reinterpret<UIntVar>()[0] = 40u
            val flags = params.reinterpret<UIntVar>()
            flags[1] = 1u // authenticated
            flags[2] = 1u // remembered
            flags[3] = 0u // unknown
            flags[4] = 1u // connected
            flags[5] = 0u // no inquiry: only what is already paired
            val info = allocArray<ByteVar>(INFO_SIZE)
            info.reinterpret<UIntVar>()[0] = INFO_SIZE.toUInt()
            val handle = first(params, info) ?: return@memScoped
            do {
                val cod = info.reinterpret<UIntVar>()[4].toInt() // offset 16
                val connected = info.reinterpret<UIntVar>()[5] != 0u // offset 20
                val name = (info + NAME_AT)!!.reinterpret<UShortVar>().toKString()
                val uuids = mutableListOf<String>()
                if (services != null) {
                    val guids = allocArray<ByteVar>(16 * 64)
                    val count = alloc<UIntVar>()
                    count.value = 64u
                    if (services(null, info, count.ptr, guids) == 0u) for (i in 0 until count.value.toInt()) uuids += guid((guids + 16 * i)!!)
                }
                out += WindowsDevice(address(info), name, audio = ((cod shr 8) and 0x1F) == 4, connected = connected, services = uuids)
            } while (next(handle, info) != 0)
            close(handle)
        }
        return out
    }

    /** The earbuds among them that PairFree can control, by name. */
    fun earbuds(): List<Paired> = all().filter { it.audio }.mapNotNull { d ->
        val name = d.name.ifBlank { d.address }
        Identify.maker(name, d.services)?.let { Brands[it].paired(d.address, name, d.services) }
    }.sortedBy { it.name }

    /** Whether Windows has the earbuds connected for audio, which their control channel needs. */
    fun isConnected(address: String): Boolean = all().firstOrNull { it.address.equals(address, ignoreCase = true) }?.connected == true
}
