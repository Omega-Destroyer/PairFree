@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.omegadestroyer.pairfree.windows

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value

/**
 * Windows' COM objects called without a binding: every method is a slot of the object's table of functions, in the order the
 * interface declares them (read from the Windows SDK's headers and metadata). The audio endpoints use it here; Bluetooth LE through
 * WinRT uses it too (docs/DESKTOP.md, phase 4).
 */
internal typealias Com = COpaquePointer

/** A GUID's text as the 16 bytes Windows lays it out in. */
internal fun comGuid(text: String): ByteArray {
    val h = text.replace("-", "")
    fun b(i: Int) = h.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    return byteArrayOf(b(3), b(2), b(1), b(0), b(5), b(4), b(7), b(6)) + ByteArray(8) { b(8 + it) }
}

/** The function in slot [index] of [o]'s table. */
internal fun comSlot(o: Com, index: Int): COpaquePointer = o.reinterpret<CPointerVar<COpaquePointerVar>>().pointed.value!![index]!!

/** QueryInterface: [o] as another interface, or null. */
internal fun comQuery(o: Com, iid: String): Com? = memScoped {
    val out = alloc<COpaquePointerVar>()
    comGuid(iid).usePinned { comSlot(o, 0).reinterpret<CFunction<(Com?, CPointer<ByteVar>?, CPointer<COpaquePointerVar>?) -> Int>>()(o, it.addressOf(0), out.ptr) }
    out.value
}

internal fun comRelease(o: Com?) {
    if (o != null) comSlot(o, 2).reinterpret<CFunction<(Com?) -> UInt>>()(o)
}
