@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, DelicateCoroutinesApi::class)

package io.github.omegadestroyer.pairfree.windows

import io.github.omegadestroyer.pairfree.ui.TonePlayer
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.windows.*
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Tones through Windows' waveOut: one 16-bit stereo buffer, played once or looped. The sound goes to the default output; the level is
 * relative to full scale, so the tests mean what they say only at the volume the user has set (the screens say so).
 * [runOnUi] runs a function on the window's thread, where the screens keep their state.
 */
class WaveOutPlayer(private val runOnUi: (() -> Unit) -> Unit) : TonePlayer {
    private var device: HWAVEOUT? = null
    private var header: CPointer<WAVEHDR>? = null
    private var data: CPointer<ByteVar>? = null

    /** Which play it is, so a tone that was stopped does not end the next one. */
    @kotlin.concurrent.Volatile
    private var generation = 0

    override val musicActive: Boolean get() = SystemVolume.musicActive()

    override fun begin() = SystemVolume.begin()

    override fun end() {
        stop()
        SystemVolume.end()
    }

    override fun stop() {
        generation++
        release()
    }

    private fun release() {
        val d = device ?: return
        waveOutReset(d)
        header?.let { waveOutUnprepareHeader(d, it, sizeOf<WAVEHDR>().convert()); nativeHeap.free(it.rawValue) }
        data?.let { nativeHeap.free(it.rawValue) }
        waveOutClose(d)
        device = null
        header = null
        data = null
    }

    override fun play(hz: Float, levelDb: Float, seconds: Int, side: Int, loop: Boolean, onEnd: () -> Unit) {
        stop()
        val mine = generation
        val frames = RATE * seconds
        val amplitude = 10.0.pow(levelDb / 20.0)
        val fade = min(FADE_FRAMES, frames / 2)
        val bytes = frames * 4
        val buffer = nativeHeap.allocArray<ByteVar>(bytes)
        for (i in 0 until frames) {
            // A short fade at each end keeps the start and stop from clicking.
            val edge = min(1.0, min(i, frames - 1 - i).toDouble() / fade)
            val v = (amplitude * edge * sin(2 * PI * hz * i / RATE) * 32767.0).toInt()
            val left = if (side == TonePlayer.RIGHT) 0 else v
            val right = if (side == TonePlayer.LEFT) 0 else v
            buffer[4 * i] = left.toByte()
            buffer[4 * i + 1] = (left shr 8).toByte()
            buffer[4 * i + 2] = right.toByte()
            buffer[4 * i + 3] = (right shr 8).toByte()
        }
        val format = nativeHeap.alloc<WAVEFORMATEX>()
        format.wFormatTag = 1u
        format.nChannels = 2u
        format.nSamplesPerSec = RATE.convert()
        format.nAvgBytesPerSec = (RATE * 4).convert()
        format.nBlockAlign = 4u
        format.wBitsPerSample = 16u
        format.cbSize = 0u
        val out = nativeHeap.alloc<HWAVEOUTVar>()
        val opened = waveOutOpen(out.ptr, WAVE_MAPPER, format.ptr, 0u, 0u, CALLBACK_NULL.convert())
        nativeHeap.free(format.rawPtr)
        if (opened != 0u) {
            nativeHeap.free(buffer.rawValue)
            return
        }
        val hdr = nativeHeap.alloc<WAVEHDR>()
        hdr.lpData = buffer
        hdr.dwBufferLength = bytes.convert()
        hdr.dwFlags = (if (loop) WHDR_BEGINLOOP or WHDR_ENDLOOP else 0).convert()
        hdr.dwLoops = if (loop) 0xFFFFFFFFu else 0u
        device = out.value
        header = hdr.ptr
        data = buffer
        waveOutPrepareHeader(out.value, hdr.ptr, sizeOf<WAVEHDR>().convert())
        waveOutWrite(out.value, hdr.ptr, sizeOf<WAVEHDR>().convert())
        if (!loop) {
            // The buffer reports itself done when it has played out; the poll lives as long as this play does.
            GlobalScope.launch(Dispatchers.Default) {
                while (generation == mine && (hdr.dwFlags.toInt() and WHDR_DONE) == 0) delay(40)
                if (generation == mine) runOnUi { if (generation == mine) onEnd() }
            }
        }
    }

    private companion object {
        const val RATE = 44100
        const val FADE_FRAMES = 2205
    }
}
