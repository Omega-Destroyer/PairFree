@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.omegadestroyer.pairfree.windows

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.windows.CoCreateInstance
import platform.windows.CoInitializeEx

/**
 * The master volume and the sound level of the default output, through Windows' audio endpoint interfaces. The hearing tests' tones are
 * levels below full scale, which mean something only at a known volume: like the Android app, a test turns the volume to the top and
 * puts it back after, and a test does not start while something else is playing.
 */
internal object SystemVolume {
    private const val CLSID_ENUMERATOR = "BCDE0395-E52F-467C-8E3D-C4579291692E"
    private const val IID_ENUMERATOR = "A95664D2-9614-4F35-A746-DE8DB63617E6"
    private const val IID_ENDPOINT_VOLUME = "5CDF2C82-841E-4546-9722-0CF74078229A"
    private const val IID_METER = "C02216F6-8C67-4B5B-9D00-D008E73E0064"
    private const val CLSCTX_ALL = 23

    private var saved: Pair<Float, Boolean>? = null

    /** The default output's endpoint as [iid], or null; the caller releases it. */
    private fun endpoint(iid: String): Com? = memScoped {
        CoInitializeEx(null, 2u /* COINIT_APARTMENTTHREADED */)
        val enumerator = alloc<COpaquePointerVar>()
        val hr = comGuid(CLSID_ENUMERATOR).usePinned { clsid ->
            comGuid(IID_ENUMERATOR).usePinned { id ->
                CoCreateInstance(clsid.addressOf(0).reinterpret(), null, CLSCTX_ALL.toUInt(), id.addressOf(0).reinterpret(), enumerator.ptr)
            }
        }
        val e = enumerator.value
        if (hr != 0 || e == null) return@memScoped null
        val device = alloc<COpaquePointerVar>()
        // GetDefaultAudioEndpoint(eRender = 0, eConsole = 0)
        comSlot(e, 4).reinterpret<CFunction<(Com?, Int, Int, CPointer<COpaquePointerVar>?) -> Int>>()(e, 0, 0, device.ptr)
        comRelease(e)
        val d = device.value ?: return@memScoped null
        val out = alloc<COpaquePointerVar>()
        comGuid(iid).usePinned { id ->
            comSlot(d, 3).reinterpret<CFunction<(Com?, CPointer<ByteVar>?, Int, COpaquePointerVar?, CPointer<COpaquePointerVar>?) -> Int>>()(d, id.addressOf(0), CLSCTX_ALL, null, out.ptr)
        }
        comRelease(d)
        out.value
    }

    /** True while something plays sound on the default output. */
    fun musicActive(): Boolean {
        val meter = endpoint(IID_METER) ?: return false
        val peak = memScoped {
            val v = alloc<FloatVar>()
            comSlot(meter, 3).reinterpret<CFunction<(Com?, CPointer<FloatVar>?) -> Int>>()(meter, v.ptr)
            v.value
        }
        comRelease(meter)
        return peak > 0.01f
    }

    /** Turns the volume to the top (and sound on), remembering what it was. */
    fun begin() {
        val volume = endpoint(IID_ENDPOINT_VOLUME) ?: return
        memScoped {
            if (saved == null) {
                val level = alloc<FloatVar>()
                val muted = alloc<IntVar>()
                comSlot(volume, 9).reinterpret<CFunction<(Com?, CPointer<FloatVar>?) -> Int>>()(volume, level.ptr)
                comSlot(volume, 15).reinterpret<CFunction<(Com?, CPointer<IntVar>?) -> Int>>()(volume, muted.ptr)
                saved = level.value to (muted.value != 0)
            }
            comSlot(volume, 14).reinterpret<CFunction<(Com?, Int, COpaquePointerVar?) -> Int>>()(volume, 0, null)
            comSlot(volume, 7).reinterpret<CFunction<(Com?, Float, COpaquePointerVar?) -> Int>>()(volume, 1.0f, null)
        }
        comRelease(volume)
    }

    /** Puts the volume back. */
    fun end() {
        val (level, muted) = saved ?: return
        saved = null
        val volume = endpoint(IID_ENDPOINT_VOLUME) ?: return
        comSlot(volume, 7).reinterpret<CFunction<(Com?, Float, COpaquePointerVar?) -> Int>>()(volume, level, null)
        comSlot(volume, 14).reinterpret<CFunction<(Com?, Int, COpaquePointerVar?) -> Int>>()(volume, if (muted) 1 else 0, null)
        comRelease(volume)
    }
}
