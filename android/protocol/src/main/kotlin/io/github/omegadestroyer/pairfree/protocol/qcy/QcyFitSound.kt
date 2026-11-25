package io.github.omegadestroyer.pairfree.protocol.qcy

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The sound the phone plays while QCY's earbuds measure the fit of their ear tips (item 11, docs/qcy/PROTOCOL.md section 8). QCY's app plays a clip of its own
 * (`res/raw/compactness.m4a`, a 20-second AAC file at 16 kHz that repeats a 5-second pattern); the clip is QCY's and is not shipped. What the earbuds measure is how much
 * of a low-frequency sound leaks out of the ear, so PairFree makes its own: noise shaped to the clip's measured spectrum (read 2026-11-24 with an AAC decoder outside
 * the repo): about 56 % of the power below 100 Hz, 11 % from 100 to 500 Hz, 8 % from 500 to 1200 Hz, 18 % from 1.2 to 4 kHz and 2 % above, at the clip's level (0.12 RMS, peak
 * under 0.9 of full scale).
 *
 * It is made from a fixed seed, so it is the same sound every time, and it loops without a seam: each band's filter runs over the noise twice and the second pass is kept.
 */
public object QcyFitSound {
    /** The power in each band of the clip, from its edges in Hz: low edge, high edge and its share of the total (percent). */
    private val BANDS = listOf(
        Triple(0.0, 50.0, 32.9), Triple(50.0, 100.0, 22.9), Triple(100.0, 200.0, 5.6), Triple(200.0, 300.0, 2.9), Triple(300.0, 500.0, 7.4),
        Triple(500.0, 800.0, 6.0), Triple(800.0, 1200.0, 2.4), Triple(1200.0, 2000.0, 6.0), Triple(2000.0, 4000.0, 11.8), Triple(4000.0, 8000.0, 1.9),
    )

    /** The level of the clip: its RMS in full scale. */
    public const val RMS: Double = 0.12

    /** The highest sample, so that nothing clips. */
    public const val PEAK: Double = 0.9

    /** [seconds] of the sound as mono samples at [rate] Hz. */
    public fun make(rate: Int, seconds: Int): FloatArray {
        val n = rate * seconds
        val out = DoubleArray(n)
        var seed = 0x2545F491L
        fun noise(): Double {
            // xorshift, summed three times: close enough to a Gaussian for noise to listen to.
            var s = 0.0
            repeat(3) {
                seed = seed xor (seed shl 13 and 0xFFFFFFFFL)
                seed = seed xor (seed shr 17)
                seed = seed xor (seed shl 5 and 0xFFFFFFFFL)
                s += (seed and 0xFFFFFFFFL).toDouble() / 0x80000000L - 1.0
            }
            return s
        }
        val white = DoubleArray(n) { noise() }
        for ((lo, hi, share) in BANDS) {
            val band = filter(white, rate, lo, hi)
            val rms = sqrt(band.sumOf { it * it } / n)
            if (rms == 0.0) continue
            val scale = sqrt(share / 100.0) / rms
            for (i in 0 until n) out[i] += band[i] * scale
        }
        val total = sqrt(out.sumOf { it * it } / n)
        var peak = 0.0
        for (i in 0 until n) {
            out[i] *= RMS / total
            peak = max(peak, abs(out[i]))
        }
        val limit = if (peak > PEAK) PEAK / peak else 1.0
        return FloatArray(n) { (out[it] * limit).toFloat() }
    }

    /** [x] through a second-order low-pass ([lo] 0) or band-pass filter, run twice over the noise (the second run starts where the first ended, so the ends meet). */
    private fun filter(x: DoubleArray, rate: Int, lo: Double, hi: Double): DoubleArray {
        val (b0, b1, b2, a1, a2) = if (lo <= 0.0) {
            val w = 2 * PI * hi / rate
            val alpha = sin(w) / (2 * 0.7071)
            val a0 = 1 + alpha
            Coefficients((1 - cos(w)) / 2 / a0, (1 - cos(w)) / a0, (1 - cos(w)) / 2 / a0, -2 * cos(w) / a0, (1 - alpha) / a0)
        } else {
            val f0 = sqrt(lo * hi)
            val w = 2 * PI * f0 / rate
            val alpha = sin(w) / (2 * (f0 / (hi - lo)))
            val a0 = 1 + alpha
            Coefficients(alpha / a0, 0.0, -alpha / a0, -2 * cos(w) / a0, (1 - alpha) / a0)
        }
        val out = DoubleArray(x.size)
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        for (pass in 0 until 2) {
            for (i in x.indices) {
                val y = b0 * x[i] + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1
                x1 = x[i]
                y2 = y1
                y1 = y
                if (pass == 1) out[i] = y
            }
        }
        return out
    }

    private data class Coefficients(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double)
}
