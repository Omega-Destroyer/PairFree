package io.github.omegadestroyer.pairfree.protocol.jbl

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The warble's levels are those measured in JBL's files (docs/jbl/PROTOCOL.md section 4.14): the start level of each frequency, 3 dB less each second, silence between pulses. */
class JblWarbleTest {
    private fun rms(x: FloatArray, from: Int, to: Int): Double = sqrt((from until to).sumOf { (x[it] * x[it]).toDouble() } / (to - from))

    private fun db(v: Double) = 20 * log10(v)

    @Test
    fun everyFrequencyOfTheTestHasATone() {
        for (hz in JblPersonifi.TEST_FREQS + listOf(750, 1500, 3000)) assertTrue(JblWarble.samples(hz, 48000).isNotEmpty(), hz.toString())
        assertEquals(0, JblWarble.samples(1234, 48000).size)
    }

    @Test
    fun theFirstPulseStartsAtTheMeasuredLevel() {
        val rate = 48000
        for ((hz, start) in JblWarble.START_DB) {
            val x = JblWarble.samples(hz, rate)
            val peak = (0 until (0.185 * rate).toInt()).maxOf { abs(x[it]) }
            assertEquals(start, db(peak.toDouble()), 0.6, hz.toString())
        }
    }

    @Test
    fun itGetsThreeDecibelsQuieterEachSecondAndStopsAtThirteen() {
        val rate = 48000
        val x = JblWarble.samples(1000, rate)
        // The same stretch of the pulse train, one second apart (the period is 0.4 s, so 2.0 s is five periods later).
        val a = rms(x, (0.0 * rate).toInt(), (0.185 * rate).toInt())
        val b = rms(x, (2.0 * rate).toInt(), (2.185 * rate).toInt())
        assertEquals(6.0, db(a) - db(b), 0.8)
        assertEquals(0.0, abs(x[(0.3 * rate).toInt()]).toDouble(), 0.0) // between pulses
        assertEquals(13 * rate, x.size)
    }
}
