package io.github.omegadestroyer.pairfree.protocol.jbl

import kotlin.math.abs

/**
 * JBL's PersoniFi: the hearing test's thresholds turned into an equalizer curve (docs/jbl/PROTOCOL.md section 4.14). A port of the analysis JBL Headphones 5.37.10 does in Kotlin
 * (`HearingTestBandComputation`, `HearingTestBandProvider`, `sk/q.java`) and of `calCompensation` of its native library `libpersonifiAlgorithm.so` (mode 3, the one every model
 * uses), checked against that library run in an emulator on thousands of inputs (`data/jbl/personifi_vectors.json`).
 */
public object JblPersonifi {
    /** One band of the curve: [type] 0 low shelf, 1 peaking, 2 high shelf, 5 off (`PersoniFiBand`, `Band`). */
    public class Band(public val type: Int, public val gain: Double, public val freq: Double, public val q: Double)

    /** The two ears' curves and the master gain that keeps them from clipping, both ears the same (`BandPayload`). */
    public class Curve(public val master: Double, public val left: List<Band>, public val right: List<Band>) {
        /**
         * The curve as the app puts it on the earbuds (`zk.c.a`, `ih.b.c`): category 201, no calibration, 48 kHz, the master gain for both ears and the two ears'
         * twelve bands each, the left ear first. A band of type 5 stays in the list, off.
         */
        public fun eq(): JblEqCurve = JblEqCurve(
            JblEqWire.PERSONIFI, 0f, JblEq.SAMPLE_RATE, master.toFloat(), master.toFloat(),
            (left + right).map { JblEqBand(it.type, it.freq.toFloat(), it.gain.toFloat(), it.q.toFloat()) },
        )
    }

    /** The nine tones of the test, in the order the app plays them (`ek.a`); 1 kHz first, as the app does. */
    public val TEST_FREQS: List<Int> = listOf(1000, 500, 250, 2000, 4000, 6000, 8000, 10000, 12000)

    /** The calibration's frequencies in the library's own order (`freq_table`), and what each is compared against (the table at 0xf70) and its Q (0xf40). */
    private val FREQS = intArrayOf(250, 500, 1000, 2000, 4000, 6000, 8000, 10000, 12000, 750, 1500, 3000)
    private val REF = floatArrayOf(20f, 10f, 10f, 15f, 10f, 20f, 20f, 10f, 5f, 10f, 10f, 10f)
    private val Q = doubleArrayOf(1.0, 2.0, 2.0, 2.0, 3.0, 3.0, 3.0, 3.0, 1.0, 2.0, 2.0, 3.0)
    private const val K = 0.30000001192092896
    private const val TOL = 1e-8

    /** The level a tone is at when the user stops hearing it after [seconds] of its fade: 43 - 3n dB for n from 1 to 14 (`caldB`). */
    public fun levelDb(seconds: Long): Double = (43f + (seconds.coerceIn(1, 14).toFloat()) * -3f).toDouble()

    /**
     * The curve for a test: each ear's level in dB by tone frequency (as [levelDb] gives them). JBL's app also works out gains of its own for the report screen
     * (`HearingTestBandComputation`), but the library takes only each band's frequency and level and works the gains out again, so only those go in. [openFit] leaves the
     * 250 Hz tone out, as the app does for the open-fit models (`sk/q.H1`, `zk.d.i`).
     */
    public fun curve(left: Map<Int, Float>, right: Map<Int, Float>, openFit: Boolean = false): Curve {
        fun bands(m: Map<Int, Float>) = m.entries.filter { !(openFit && it.key == 250) }.map { (f, v) -> doubleArrayOf(1.0, 0.0, f.toDouble(), 2.0, maxOf(v, 0f).toDouble()) }
        return calCompensation(bands(left), bands(right))
    }

    /** The library's `calCompensation`, mode 3: bands as (type, gain, frequency, q, dB), of which only the frequency and the level are read. */
    internal fun calCompensation(left: List<DoubleArray>, right: List<DoubleArray>): Curve {
        val lo = shape(sideRaw(left))
        val ro = shape(sideRaw(right))
        var m = 0.0
        for (o in listOf(lo, ro)) for (k in 0 until 9) m = maxOf(m, o[k].gain)
        return Curve(-m, lo, ro)
    }

    private class W(var type: Int, var gain: Double, var freq: Double, var q: Double)

    private fun idxOf(freq: Double): Int? = FREQS.indices.firstOrNull { abs(FREQS[it].toDouble() - freq) < TOL }

    private fun sideRaw(bands: List<DoubleArray>): Array<W> {
        val local = Array(12) { W(5, 0.0, 0.0, 0.0) }
        val diff = FloatArray(12) { 120f }
        for (b in bands) {
            val i = idxOf(b[2]) ?: continue
            local[i] = W(b[0].toInt(), b[1], b[2], b[3])
            diff[i] = (b[4] - REF[i].toDouble()).toFloat()
        }
        for (i in 0 until 12) {
            val w = local[i]
            if (w.freq > 10) {
                var g = diff[i] * 0.6f
                if (g < 0f) g = 0f
                if (g > 9f) g = 9f
                w.gain = g.toDouble()
                w.q = Q[i]
                w.type = if (abs(w.freq - 250.0) < TOL) 0 else if (abs(w.freq - 12000.0) < TOL) 2 else 1
            }
        }
        return local
    }

    /**
     * a x b + c with one rounding, as the library's fused multiply-add does: the exact product as two doubles (Dekker), then summed with the error terms kept.
     */
    private fun fma(a: Double, b: Double, c: Double): Double {
        val split = 134217729.0
        val ca = split * a
        val ah = ca - (ca - a)
        val al = a - ah
        val cb = split * b
        val bh = cb - (cb - b)
        val bl = b - bh
        val p = a * b
        val e = ((ah * bh - p) + ah * bl + al * bh) + al * bl
        val s = p + c
        val bb = s - p
        val err = (p - (s - bb)) + (c - bb)
        return s + (err + e)
    }

    /** Single precision `(float)trunc(x + 0.5) / 10`, as a double. */
    private fun round10(x: Double): Double = ((x + 0.5).toInt().toFloat() / 10f).toDouble()

    private fun shape(inb: Array<W>): List<Band> {
        val present0 = inb[0].freq > 10
        val w8 = if (present0) 1 else 2
        val first = w8 - 1
        var mx = inb[first].gain
        var mn = mx
        var sum = mx
        for (k in first + 1..8) {
            val g = inb[k].gain
            sum = g + sum
            if (g > mx) mx = g
            if (g < mn) mn = g
        }
        if (mx - mn < 1.0) {
            val v = round10(sum * 10.0 / (10 - w8).toDouble())
            for (b in inb) b.type = 5
            if (abs(v) >= TOL) {
                inb[9].gain = v
                inb[9].type = 0
                inb[9].freq = 18000.0
                inb[9].q = 1.0
            }
            return finish(inb)
        }
        if (present0) {
            val d3 = if (inb[6].gain < 0) 0.0 else inb[6].gain
            val d4 = maxOf(d3, inb[7].gain)
            val d5 = maxOf(d4, inb[8].gain)
            val diff = d5 - inb[0].gain
            if (!(diff < 5.0)) inb[0].gain = fma(diff, K, inb[0].gain)
        }
        val g5 = inb[5].gain
        val g6 = inb[6].gain
        val g7 = inb[7].gain
        val g8 = inb[8].gain
        val hi = maxOf(g8, g7)
        val lo = minOf(g8, g7)
        val d5 = maxOf(hi, g6)
        val d7 = minOf(lo, g6)
        val d6 = maxOf(d5, g5)
        val d16 = minOf(d7, g5)
        if (hi - lo < 1.0) {
            val s2 = g7 + g8
            if (d5 - d7 >= 1.0) {
                inb[7].type = 2
                inb[7].q = 1.0
                inb[7].gain = round10(s2 * 10.0 * 0.5)
            } else {
                val s3 = g6 + s2
                if (d6 - d16 >= 1.0) {
                    inb[6].type = 2
                    inb[6].q = 1.0
                    inb[7].type = 5
                    inb[6].gain = round10(s3 * 10.0 / 3.0)
                } else {
                    inb[5].type = 2
                    inb[5].q = 1.0
                    inb[6].type = 5
                    inb[7].type = 5
                    inb[5].gain = round10((g5 + s3) * 10.0 * 0.25)
                }
            }
            inb[8].type = 5
        }
        if (present0) {
            val g0 = inb[0].gain
            val g1 = inb[1].gain
            val g2 = inb[2].gain
            val hi01 = maxOf(g0, g1)
            val lo01 = minOf(g0, g1)
            if (hi01 - lo01 < 1.0) {
                val s = g1 + g0
                if (maxOf(hi01, g2) - minOf(lo01, g2) >= 1.0) {
                    inb[1].type = 0
                    inb[1].q = 1.0
                    inb[1].gain = round10(s * 10.0 * 0.5)
                    inb[0].type = 5
                } else {
                    inb[2].type = 0
                    inb[2].q = 1.0
                    inb[2].gain = round10((g2 + s) * 10.0 / 3.0)
                    inb[1].type = 5
                    inb[0].type = 5
                }
            }
        }
        return finish(inb)
    }

    private fun finish(inb: Array<W>): List<Band> = List(12) { i ->
        val b = inb[i]
        if (b.freq > 10) Band(b.type, b.gain, b.freq, b.q) else Band(5, 0.0, FREQS[i].toDouble(), 1.0)
    }
}
