package io.github.omegadestroyer.pairfree.protocol.airoha

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One PEQ band as Airoha's SDK takes it: its frequency, gain in dB and width: the bandwidth in Hz (frequency / Q) for
 * [AirohaPeq.Library.PEQ], Q itself for [AirohaPeq.Library.SERIALIZE].
 */
public data class AirohaPeqBand(val hz: Double, val gainDb: Double, val width: Double)

/**
 * A port of Airoha's PEQ library (libnative-peq.so in JBL Headphones 5.37.10, the same in EarFun's and QCY's apps):
 * generate_cofe, change_rescale_cofe, get_cofe_param and generate_freq_resp, read from its arm64 code and checked
 * against the library itself, run in an emulator with Android 17's own libc and libm (docs/jbl/PROTOCOL.md section
 * 11.2). The band order follows Android 17's qsort, which the library sorts the bands with.
 */
public object AirohaPeq {
    /**
     * Which of Airoha's two libraries: libnative-peq.so (AirohaPeqMgr, bandwidths, a comparator that sorts by whole
     * decibels) or libnative-peq-serialize.so (AirohaEQSerialize on protocol 4's models, Q, a strict comparator, and the
     * response's peak taken in single precision). With JBL's settings the two are otherwise the same, as checked against
     * both libraries.
     */
    public enum class Library { PEQ, SERIALIZE }

    private const val TWO_PI = 6.283185307179586
    private const val Q23 = 8388608.0
    private const val Q22 = 4194304.0
    private const val CLAMP_LOW = -8388608.0
    private const val CLAMP_HIGH = 8388607.0
    private const val HEADROOM = 1.19
    private const val ROOM_DB = 18.0618

    /** The sample rates the library has tables for. */
    public val RATES: List<Double> = listOf(32000.0, 44100.0, 48000.0, 88200.0, 96000.0)

    /** The result of [coefficients]: the library's code (0, or -1 for a rate it lacks, -10 - i for an unstable band i) and the shorts. */
    public class Result internal constructor(public val code: Int, public val shorts: ShortArray?, internal val state: State?)

    internal class State(
        val fs: Double,
        val stages: List<DoubleArray>,
        val cum: Double,
        val scale: Double,
        val lin: Double,
        val top: Double,
        val peak: Double,
    )

    /**
     * NativePeq.setParam, setPeqPoint for each band, generateCofe, changeRescaleCofe([masterDb]) and getCofeParam at
     * [fs]: the band count, each stage's b0, b1, b2, a1 and a2 as 32-bit numbers in two shorts each (high first), then
     * the master gain (Q26) the same way.
     */
    public fun coefficients(fs: Double, bands: List<AirohaPeqBand>, masterDb: Double, library: Library = Library.PEQ): Result {
        if (fs !in RATES || bands.isEmpty()) return Result(-1, null, null)
        val n = bands.size
        val coef = ArrayList<DoubleArray>(n)
        val resp = ArrayList<Array<DoubleArray>>(n)
        val recs = ArrayList<Pair<Int, Double>>(n)
        for ((i, band) in bands.withIndex()) {
            val q = if (library == Library.SERIALIZE) band.width else band.hz / band.width
            val w0 = band.hz * TWO_PI / fs
            val a = sqrt(10.0.pow(band.gainDb / 20.0))
            val s = sin(w0)
            val c = cos(w0)
            val alpha = s / (q + q)
            val d1 = alpha / a
            val den = d1 + 1.0
            val b0 = (alpha * a + 1.0) / den
            val b2 = (1.0 - alpha * a) / den
            val a1 = c * -2.0 / den
            val a2 = (1.0 - d1) / den
            if (largestPole(a1, a2) > 1.0) return Result(-10 - i, null, null)
            coef += doubleArrayOf(b0, a1, b2, a1, a2)
            recs += i to band.gainDb
            resp += response(coef.last(), fs)
        }
        var prod = resp[0]
        for (k in 1 until n) prod = multiply(prod, resp[k])
        val peak = peak(prod, fs)
        val peakDb = log10(peak) * 20.0
        val scale = if (peak < 4.0) 1.0 else peak * 0.25
        val order = sortedOrder(recs, library)
        val sresp = order.map { resp[it] }
        val scoef = order.map { coef[it] }

        val out = ArrayList<DoubleArray>(n)
        var m = numeratorMax(scoef[0])
        var d = scale * m * HEADROOM
        var inv = 1.0 / d
        if (inv * peak(sresp[0], fs) > 4.0) {
            m = if (m < 1.0) 1.0 else m
            d = m * scale * HEADROOM
            inv = 1.0 / d
        }
        out += quantize(scoef[0], d)
        var cum = 1.0 * m
        prod = sresp[0]
        for (k in 1 until n) {
            m = numeratorMax(scoef[k])
            prod = multiply(prod, sresp[k])
            val prev = inv
            inv = prev * (1.0 / m)
            if (inv * peak(prod, fs) > 4.0) {
                m = if (m < 1.0) 1.0 else m
                inv = prev * (1.0 / m)
            }
            out += quantize(scoef[k], m)
            cum *= m
        }

        // change_rescale_cofe, mode 1.
        var lin = 10.0.pow(masterDb / 20.0)
        val top: Double
        if (peak <= 8.0) {
            val room = ROOM_DB - peakDb
            if (room < masterDb) lin = 10.0.pow(room / 20.0)
            top = peak
        } else {
            if (masterDb > 0) lin = 1.0
            top = 8.0
        }
        val gain = floor(clamp(lin * (cum * scale / peak * top * HEADROOM) * 262144.0 + 0.5)) * 2.0.pow(-18)

        val shorts = ShortArray(3 + 10 * n)
        shorts[0] = n.toShort()
        out.forEachIndexed { i, st ->
            val scales = doubleArrayOf(2147483648.0, 1073741824.0, 2147483648.0, 1073741824.0, 2147483648.0)
            for (k in 0..4) put(shorts, 1 + 10 * i + 2 * k, (st[k] * scales[k]).toLong().toInt())
        }
        put(shorts, 1 + 10 * n, (gain * 67108864.0).toLong().toInt())
        return Result(0, shorts, State(fs, out, cum, scale, lin, top, peak))
    }

    /**
     * AirohaPeqMgr.getPeakValue's figure: the highest point of the library's response (generate_freq_resp's 4096
     * points, in dB) for these bands at 44.1 kHz; 0 when the library refuses the curve.
     */
    public fun peakDb(bands: List<AirohaPeqBand>, masterDb: Double, library: Library = Library.PEQ): Double {
        val st = coefficients(44100.0, bands, masterDb, library).state ?: return 0.0
        val y = responseDb(st)
        if (library == Library.SERIALIZE) {
            // AirohaEQSerialize keeps each point as a float and takes the last of equal highs.
            var best = y[0].toFloat()
            for (v in y) if (v.toFloat() >= best) best = v.toFloat()
            return best.toDouble()
        }
        var best = y[0]
        for (v in y) if (best < v) best = v
        return best
    }

    /** generate_freq_resp: 20 log10 |H| of the quantized stages, master gain applied, at the library's 4096 points. */
    internal fun responseDb(st: State): DoubleArray {
        var prod = response(st.stages[0], st.fs)
        for (k in 1 until st.stages.size) prod = multiply(prod, response(st.stages[k], st.fs))
        fun k(x: Double) = st.cum * x * HEADROOM * st.scale * st.lin * st.top / st.peak
        val step = (st.fs * 0.5 + -5000.0) / 2096.0
        val points = IntArray(4096) { i ->
            when {
                i < 500 -> i
                i < 1000 -> 2 * i - 500
                i < 1500 -> 3 * i - 1500
                i < 2000 -> 4 * i - 3000
                else -> ((i - 2000) * step + 5000.0).toInt()
            }
        }
        return DoubleArray(points.size) { i ->
            val re = k(prod[0][points[i]])
            val im = k(prod[1][points[i]])
            log10(abs(sqrt(re * re + im * im))) * 20.0
        }
    }

    private fun largestPole(a1: Double, a2: Double): Double {
        val disc = a1 * a1 + a2 * -4.0
        return if (disc < 0) {
            val im = sqrt(-disc)
            val re = a1 * -0.5
            maxOf(abs(sqrt(re * re + im * -0.5 * (im * -0.5))), abs(sqrt(re * re + im * 0.5 * (im * 0.5))))
        } else {
            val sd = abs(sqrt(disc))
            val r1 = (-1.0 * a1 + -sd) * 0.5
            val r2 = (-1.0 * a1 + sd) * 0.5
            maxOf(abs(sqrt(r1 * r1 + 0.0)), abs(sqrt(r2 * r2 + 0.0)))
        }
    }

    private fun numeratorMax(c: DoubleArray): Double {
        val m = if (abs(c[0]) > abs(c[1] * 0.5)) abs(c[0]) else abs(c[1] * 0.5)
        return if (m > abs(c[2])) m else abs(c[2])
    }

    private fun quantize(c: DoubleArray, d: Double): DoubleArray = doubleArrayOf(
        floor(clamp(c[0] / d * Q23 + 0.5)) / Q23,
        floor(clamp(c[1] / d * Q22 + 0.5)) / Q22,
        floor(clamp(c[2] / d * Q23 + 0.5)) / Q23,
        floor(clamp(c[3] * Q22 + 0.5)) / Q22,
        floor(clamp(c[4] * Q23 + 0.5)) / Q23,
    )

    private fun clamp(v: Double): Double = if (!(v > CLAMP_LOW)) CLAMP_LOW else if (!(v < CLAMP_HIGH)) CLAMP_HIGH else v

    private fun put(out: ShortArray, at: Int, v: Int) {
        out[at] = (v ushr 16).toShort()
        out[at + 1] = v.toShort()
    }

    /** The band order: by gain, highest first, as qsort leaves them with the library's comparator; equal gains by index. */
    private fun sortedOrder(recs: List<Pair<Int, Double>>, library: Library): List<Int> {
        val a = recs.toMutableList()
        if (library == Library.SERIALIZE) {
            BionicSort.sort(a) { x, y -> if (x.second > y.second) -1 else if (x.second < y.second) 1 else 0 }
        } else {
            BionicSort.sort(a) { x, y -> (y.second - x.second).toInt() }
        }
        val n = a.size
        val groups = IntArray(16)
        var g = 0
        for (j in 0 until n) {
            groups[g]++
            if (j + 1 >= n || a[j].second != a[j + 1].second) g++
            if (g >= groups.size) break
        }
        var pos = 0
        for (c in groups) {
            if (c >= 2) {
                val part = a.subList(pos, pos + c).toMutableList()
                BionicSort.sort(part) { x, y -> x.first - y.first }
                for (i in 0 until c) a[pos + i] = part[i]
                pos += c
            } else if (c == 1) {
                pos += 1
            }
        }
        return a.map { it.first }
    }

    /** The response of one biquad at each whole hertz below fs/2, real and imaginary parts. */
    private fun response(c: DoubleArray, fs: Double): Array<DoubleArray> {
        val (t1, t2) = tables(fs)
        val n = t1[0].size
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        val b0 = c[0]
        val b1 = c[1]
        val b2 = c[2]
        val a1 = c[3]
        val a2 = c[4]
        for (f in 0 until n) {
            val r1 = t1[0][f]
            val i1 = t1[1][f]
            val r2 = t2[0][f]
            val i2 = t2[1][f]
            val nr = b2 * r2 + (b1 * r1 + b0)
            val ni = b1 * i1 + b2 * i2
            val dr = a2 * r2 + (a1 * r1 + 1.0)
            val di = a1 * i1 + a2 * i2
            val den = dr * dr + di * di
            re[f] = (nr * dr + ni * di) / den
            im[f] = (dr * ni - nr * di) / den
        }
        return arrayOf(re, im)
    }

    private fun multiply(p: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
        val n = p[0].size
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        for (f in 0 until n) {
            val pr = p[0][f]
            val pi = p[1][f]
            val br = b[0][f]
            val bi = b[1][f]
            re[f] = br * pr + -(bi * pi)
            im[f] = bi * pr + br * pi
        }
        return arrayOf(re, im)
    }

    private fun peak(r: Array<DoubleArray>, fs: Double): Double {
        var best = 0.0
        val half = fs * 0.5
        var f = 0
        while (half > f) {
            val re = r[0][f]
            val im = r[1][f]
            val m = abs(sqrt(re * re + im * im))
            if (m > best) best = m
            f++
        }
        return best
    }

    private val cache = HashMap<Double, Pair<Array<DoubleArray>, Array<DoubleArray>>>()

    /** calc_z: z^-1 and z^-2 at each whole hertz below fs/2, as libc++'s exp(k log z). */
    private fun tables(fs: Double): Pair<Array<DoubleArray>, Array<DoubleArray>> = cache.getOrPut(fs) {
        val n = fs.toInt() / 2
        val t1 = arrayOf(DoubleArray(n), DoubleArray(n))
        val t2 = arrayOf(DoubleArray(n), DoubleArray(n))
        for (f in 0 until n) {
            val w = f * TWO_PI / fs
            val zr = if (w == 0.0) 1.0 else cos(w)
            val zi = if (w == 0.0) 0.0 else sin(w)
            val lg = ln(hypot(zr, zi))
            val th = atan2(zi, zr)
            for ((k, t) in listOf(-1.0 to t1, -2.0 to t2)) {
                val re = k * lg - 0.0 * th
                val im = 0.0 * lg + k * th
                val e = exp(re)
                t[0][f] = if (im == 0.0) e else e * cos(im)
                t[1][f] = if (im == 0.0) im else e * sin(im)
            }
        }
        t1 to t2
    }
}

/**
 * Android 17's qsort for short arrays (bionic's unstable sort, read from its arm64 libc.so): median-of-three pivots at
 * 0, 4/8 and 7/8 of the range, branchless Lomuto partitions, the ancestor pivot's shortcut for equal runs and heapsort
 * at a depth of twice log2 of the length. The order matters because the PEQ library's comparator is not a strict one.
 */
internal object BionicSort {
    fun <T> sort(a: MutableList<T>, cmp: (T, T) -> Int) {
        val n = a.size
        if (n < 2) return
        require(n < 64) { "only short arrays" }
        quicksort(a, 0, n, null, 2 * (31 - (n or 1).countLeadingZeroBits()), cmp)
    }

    private fun <T> swap(a: MutableList<T>, i: Int, j: Int) {
        val t = a[i]
        a[i] = a[j]
        a[j] = t
    }

    private fun <T> quicksort(a: MutableList<T>, start: Int, length: Int, firstAncestor: T?, depth: Int, cmp: (T, T) -> Int) {
        var lo = start
        var n = length
        var ancestor = firstAncestor
        var limit = depth
        while (n > 1) {
            if (limit == 0) {
                heapsort(a, lo, n, cmp)
                return
            }
            val piv = if (n < 8) {
                0
            } else {
                val e = n / 8
                val x = cmp(a[lo], a[lo + 4 * e]) < 0
                val y = cmp(a[lo], a[lo + 7 * e]) < 0
                if (x != y) 0 else if ((cmp(a[lo + 4 * e], a[lo + 7 * e]) < 0) != x) 7 * e else 4 * e
            }
            limit--
            if (ancestor != null && cmp(ancestor, a[lo + piv]) >= 0) {
                swap(a, lo, lo + piv)
                var count = 0
                for (j in 1 until n) {
                    val le = cmp(a[lo], a[lo + j]) >= 0
                    swap(a, lo + 1 + count, lo + j)
                    if (le) count++
                }
                swap(a, lo, lo + count)
                lo += count + 1
                n -= count + 1
                ancestor = null
                continue
            }
            swap(a, lo, lo + piv)
            var count = 0
            for (j in 1 until n) {
                val lt = cmp(a[lo + j], a[lo]) < 0
                swap(a, lo + 1 + count, lo + j)
                if (lt) count++
            }
            swap(a, lo, lo + count)
            if (n == 2) return
            quicksort(a, lo, count, ancestor, limit, cmp)
            ancestor = a[lo + count]
            lo += count + 1
            n -= count + 1
        }
    }

    private fun <T> heapsort(a: MutableList<T>, lo: Int, length: Int, cmp: (T, T) -> Int) {
        var n = length
        var node = n / 2
        while (true) {
            if (node > 0) {
                node--
            } else {
                n--
                swap(a, lo, lo + n)
            }
            var cur = node
            while (true) {
                var child = 2 * cur + 1
                if (child >= n) break
                if (child + 1 < n && cmp(a[lo + child], a[lo + child + 1]) < 0) child++
                if (cmp(a[lo + cur], a[lo + child]) >= 0) break
                swap(a, lo + cur, lo + child)
                cur = child
            }
            if (n <= 1) return
        }
    }
}
