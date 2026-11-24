package io.github.omegadestroyer.pairfree.protocol.jbl

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The warble tones of PersoniFi's hearing test (docs/jbl/PROTOCOL.md section 4.14), made by PairFree from what the test's resource files show when they are decoded:
 * a tone that pulses (about 185 ms on and 215 ms off, 10 ms ramps at each end) and whose pitch swings 11 % either way about ten times a second, quieter by 3 dB each
 * second for 13 seconds, and in one earbud. Each frequency starts at its own level ([START_DB], the highest peak of the file's first pulse in dB below full scale), which
 * is what makes the 3 dB steps of [JblPersonifi.levelDb] mean the same at every frequency. The files themselves are not used or shipped.
 */
public object JblWarble {
    /** The level each frequency starts at, in dB re full scale (the peak of its first pulse in JBL's file). */
    public val START_DB: Map<Int, Double> = mapOf(
        250 to -0.5, 500 to -14.0, 750 to -17.0, 1000 to -20.0, 1500 to -19.0, 2000 to -18.5,
        3000 to -17.0, 4000 to -15.5, 6000 to -20.0, 8000 to -11.5, 10000 to -7.5, 12000 to -2.5,
    )

    /** The length of a tone in seconds: the files are 14 s long, silent from 13 s. */
    public const val SECONDS: Int = 13

    public const val FADE_DB_PER_SECOND: Double = 3.0
    private const val PERIOD = 0.4
    private const val ON = 0.185
    private const val RAMP = 0.010
    private const val SWING = 0.11
    private const val SWING_HZ = 10.5

    /** The tone at [hz] as mono samples at [rate]: peak 1.0 is full scale. An empty array for a frequency the test does not use. */
    public fun samples(hz: Int, rate: Int): FloatArray {
        val start = START_DB[hz] ?: return FloatArray(0)
        val out = FloatArray(SECONDS * rate)
        var phase = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / rate
            val into = t % PERIOD
            val f = hz * (1 + SWING * cos(2 * PI * SWING_HZ * t - 0.2))
            phase += 2 * PI * f / rate
            if (into >= ON) continue
            val edge = minOf(1.0, into / RAMP, (ON - into) / RAMP)
            val gain = 10.0.pow((start - FADE_DB_PER_SECOND * t) / 20.0)
            out[i] = (gain * edge * sin(phase)).toFloat()
        }
        return out
    }
}
