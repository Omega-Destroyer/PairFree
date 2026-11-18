@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.omegadestroyer.pairfree.spike

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import platform.windows.GetProcAddress
import platform.windows.LoadLibraryW
import platform.windows.Sleep

// The spike's answer to "can Kotlin/Native call Windows' Bluetooth LE API": WinRT is COM, so every call is a slot of an
// object's table of functions. Interface ids and method order were read from Windows' own metadata (Windows.Devices.Bluetooth).

private typealias Obj = COpaquePointer

/** A GUID's text as the 16 bytes Windows lays it out in. */
private fun guid(text: String): ByteArray {
    val h = text.replace("-", "")
    fun b(i: Int) = h.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    return byteArrayOf(b(3), b(2), b(1), b(0), b(5), b(4), b(7), b(6)) + ByteArray(8) { b(8 + it) }
}

private fun slot(o: Obj, index: Int): COpaquePointer =
    o.reinterpret<CPointerVar<COpaquePointerVar>>().pointed.value!![index]!!

/** A method that only returns an object or a handle through its last parameter. */
private fun getObject(o: Obj, index: Int): Obj? = memScoped {
    val out = alloc<COpaquePointerVar>()
    val hr = slot(o, index).reinterpret<CFunction<(Obj?, CPointer<COpaquePointerVar>?) -> Int>>()(o, out.ptr)
    if (hr != 0) println("  slot $index failed: 0x${hr.toUInt().toString(16)}")
    out.value
}

private fun getInt(o: Obj, index: Int): Int = memScoped {
    val out = alloc<IntVar>()
    slot(o, index).reinterpret<CFunction<(Obj?, CPointer<IntVar>?) -> Int>>()(o, out.ptr)
    out.value
}

private fun query(o: Obj, iid: String): Obj? = memScoped {
    val out = alloc<COpaquePointerVar>()
    val id = guid(iid)
    id.usePinned { slot(o, 0).reinterpret<CFunction<(Obj?, CPointer<ByteVar>?, CPointer<COpaquePointerVar>?) -> Int>>()(o, it.addressOf(0), out.ptr) }
    out.value
}

private fun release(o: Obj?) {
    if (o != null) slot(o, 2).reinterpret<CFunction<(Obj?) -> UInt>>()(o)
}

/** Waits for a WinRT asynchronous operation by asking its IAsyncInfo for the status (0 started, 1 completed), then takes the result. */
private fun await(operation: Obj): Obj? {
    val info = query(operation, "00000036-0000-0000-C000-000000000046") ?: return null
    var waited = 0
    while (getInt(info, 7) == 0 && waited < 15000) { Sleep(50u); waited += 50 }
    val status = getInt(info, 7)
    release(info)
    if (status != 1) { println("  operation ended with status $status after $waited ms"); return null }
    return getObject(operation, 8).also { release(operation) }
}

/** `PairFreeSpike.exe --gatt AABBCCDDEEFF`: asks a known Bluetooth LE device for its GATT services and prints their UUIDs. */
fun gattServices(address: Long): Unit = memScoped {
    val combase = LoadLibraryW("combase.dll")
    fun proc(name: String) = GetProcAddress(combase, name)!!
    val roInitialize = proc("RoInitialize").reinterpret<CFunction<(Int) -> Int>>()
    val createString = proc("WindowsCreateString").reinterpret<CFunction<(CPointer<kotlinx.cinterop.UShortVar>?, UInt, CPointer<COpaquePointerVar>?) -> Int>>()
    val stringBuffer = proc("WindowsGetStringRawBuffer").reinterpret<CFunction<(COpaquePointer?, CPointer<UIntVar>?) -> CPointer<kotlinx.cinterop.UShortVar>?>>()
    val activationFactory = proc("RoGetActivationFactory").reinterpret<CFunction<(COpaquePointer?, CPointer<ByteVar>?, CPointer<COpaquePointerVar>?) -> Int>>()
    println("RoInitialize: 0x${roInitialize(1).toUInt().toString(16)}")

    val className = "Windows.Devices.Bluetooth.BluetoothLEDevice"
    val hstring = alloc<COpaquePointerVar>()
    createString(className.wcstr.ptr, className.length.toUInt(), hstring.ptr)
    val factory = alloc<COpaquePointerVar>()
    val statics = guid("c8cf1a19-f0b6-4bf0-8689-41303de2d9f4")
    val hr = statics.usePinned { activationFactory(hstring.value, it.addressOf(0), factory.ptr) }
    val deviceStatics = factory.value ?: return println("no BluetoothLEDevice factory: 0x${hr.toUInt().toString(16)}")
    println("factory: ok")

    // IBluetoothLEDeviceStatics.FromBluetoothAddressAsync is slot 7 (six of IInspectable, then FromIdAsync).
    val op = alloc<COpaquePointerVar>()
    slot(deviceStatics, 7).reinterpret<CFunction<(Obj?, ULong, CPointer<COpaquePointerVar>?) -> Int>>()(deviceStatics, address.toULong(), op.ptr)
    val device = op.value?.let { await(it) } ?: return println("Windows knows no Bluetooth LE device at that address")
    val name = alloc<COpaquePointerVar>()
    slot(device, 7).reinterpret<CFunction<(Obj?, CPointer<COpaquePointerVar>?) -> Int>>()(device, name.ptr)
    println("device: ${stringBuffer(name.value, null)?.toKStringFromUtf16()}, connection status ${getInt(device, 9)}")

    // IBluetoothLEDevice3.GetGattServicesAsync() is its slot 8; the result's status is slot 6 and its services slot 8.
    val device3 = query(device, "aee9e493-44ac-40dc-af33-b2c13c01ca46") ?: return println("no IBluetoothLEDevice3")
    val result = getObject(device3, 8)?.let { await(it) } ?: return println("no services result")
    println("GATT status: ${getInt(result, 6)} (0 success, 1 unreachable, 2 protocol error, 3 access denied)")
    val services = getObject(result, 8) ?: return
    val size = alloc<UIntVar>()
    slot(services, 7).reinterpret<CFunction<(Obj?, CPointer<UIntVar>?) -> Int>>()(services, size.ptr)
    println("services: ${size.value}")
    for (i in 0u until size.value) {
        val service = alloc<COpaquePointerVar>()
        slot(services, 6).reinterpret<CFunction<(Obj?, UInt, CPointer<COpaquePointerVar>?) -> Int>>()(services, i, service.ptr)
        val s = service.value ?: continue
        val uuid = allocArray<ByteVar>(16)
        slot(s, 9).reinterpret<CFunction<(Obj?, CPointer<ByteVar>?) -> Int>>()(s, uuid)
        val b = ByteArray(16) { uuid[it] }
        val order = intArrayOf(3, 2, 1, 0, 5, 4, 7, 6, 8, 9, 10, 11, 12, 13, 14, 15)
        val hex = order.joinToString("") { (b[it].toInt() and 0xFF).toString(16).padStart(2, '0') }
        println("  ${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}")
        release(s)
    }
    release(services); release(result); release(device3); release(device); release(deviceStatics)
}
