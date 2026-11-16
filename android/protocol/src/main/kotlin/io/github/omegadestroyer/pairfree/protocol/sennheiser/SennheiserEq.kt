package io.github.omegadestroyer.pairfree.protocol.sennheiser

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** The equalizers a model may have, newest first (docs/sennheiser/PROTOCOL.md section 6). */
public enum class SennheiserEqKind {
    /** user-eq-2: five band gains the earbuds hold, in tenths of a dB. */
    GEN2,

    /** user-eq-1 with five bands: a shelf filter set Smart Control calculates from the band gains. */
    GEN1,

    /** user-eq-1 with bass, mid and treble: the earbuds' older knobs. */
    THREE_BAND,
}

/** What a model has of equalizers (data/sennheiser/devices.json `eq`, from Smart Control's product files and `EqPresetsService`). */
public class SennheiserModelEq(
    public val gen2: Boolean,
    /** The key of its five-band shelf set in [SennheiserEqTables.GEN1], or null. */
    public val gen1: String?,
    /** The firmware (`major.minor.patch`) from which the app uses the five-band set instead of the three bands. */
    public val gen1MinFirmware: String?,
    /** The three-band EQ's gain limit (`gainLimit`). */
    public val threeBandLimit: Int,
    /** The key of its presets in [SennheiserEqTables.PRESETS]. */
    public val presets: String,
)

/** The numbers of one product's five-band shelf equalizer (Smart Control's `get5BandParametersForDevice`). */
public class SennheiserShelfEq(
    public val bassBoost: List<Double>,
    public val loudnessWeights: List<Double>,
    public val maxHeadroom: List<Double>,
    public val q: List<Double>,
    public val bandFrequencies: List<Int>,
    public val hz: List<Int>,
    public val rangeDb: Int,
)

/**
 * Smart Control 4.9.4's equalizer arithmetic, ported line by line from its JavaScript (`Eq5BandGen1`, `Eq3Band`, `EqKnobValuesCoder`): what the earbuds are told for a
 * set of band gains, and what the earbuds' answers say the gains are. JavaScript's rounding (`Math.round` is half up) and truncation (`| 0`, the 16-bit setters) are kept.
 */
public object SennheiserEq {
    /** `Math.round`. */
    private fun round(x: Double): Double = floor(x + 0.5)

    /** What `Eq5BandGen1.getGainConfiguration` gives: the level shift in dB, the master gain and the four shelf gains. */
    public class ShelfConfig(public val levelShift: Double, public val master: Double, public val shelfGains: List<Double>)

    public fun shelfConfig(gains: List<Double>, p: SennheiserShelfEq, bassBoost: Boolean): ShelfConfig {
        val o = if (bassBoost) gains.mapIndexed { i, g -> g + p.bassBoost[i] } else gains
        // Loudness compensation: -10 log10 of the summed weighted power.
        val s = -10 * log10(o.mapIndexed { i, g -> 10.0.pow((g + p.loudnessWeights[i]) / 10) }.sum())
        // Clipping prevention: the most the shelves would exceed their headroom by.
        val r = o.mapIndexed { i, g -> g + s - p.maxHeadroom[i] }.fold(0.0) { m, v -> max(m, v) }
        val c = round(10 * (if (r > 0) s - r else s)) / 10
        val i = o.map { it + c }
        val d = MutableList(o.size) { 0.0 }
        d[0] = i[0]
        for (k in 1 until i.size) d[k] = i[k] - i[k - 1]
        if (truncate(100 * d[0]) == 425.0) d[0] = 4.24
        return ShelfConfig(c, d[0], d.drop(1))
    }

    private fun truncate(x: Double): Double = if (x < 0) -floor(-x) else floor(x)

    /** JavaScript's `ToInt16` of a number set with `DataView.setInt16`: truncated, then taken modulo 2^16. */
    private fun int16(x: Double): Int = truncate(x).toLong().toInt() and 0xFFFF

    /** The parameter group `Eq5BandGen1.generateCommandParamsFromGainConfiguration` builds: a count, then address and value pairs, then 0. */
    public fun shelfParams(c: ShelfConfig, p: SennheiserShelfEq): List<Int> {
        val f = p.bandFrequencies
        val values = listOf(
            17.0, 257.0, 60 * c.master, 272.0, 11.0, 273.0, 3.0 * f[1], 274.0, 60 * c.shelfGains[0], 275.0, 4096 * p.q[0],
            288.0, 11.0, 289.0, 3.0 * f[2], 290.0, 60 * c.shelfGains[1], 291.0, 4096 * p.q[1],
            304.0, 11.0, 305.0, 3.0 * f[3], 306.0, 60 * c.shelfGains[2], 307.0, 4096 * p.q[2],
            320.0, 11.0, 321.0, 3.0 * f[4], 322.0, 60 * c.shelfGains[3], 323.0, 4096 * p.q[3], 0.0,
        )
        return values.map(::int16)
    }

    /** What to ask for to read the shelf gains back (`getGainRefreshParams`). */
    public val SHELF_REFRESH: List<Int> = listOf(5, 257, 0, 274, 0, 290, 0, 306, 0, 322, 0)

    /**
     * The band gains the earbuds hold, from their level shift (in dB), their knob Y value and the answer to [SHELF_REFRESH] (`Eq5BandGen1.decode`):
     * the master and the four shelves are summed to the bands' gains, less the bass boost where it is on.
     */
    public fun shelfGains(levelShiftDb: Double, knobY: Int, reply: List<Int>, p: SennheiserShelfEq): Pair<List<Double>, Boolean>? {
        if (reply.size < 11) return null
        val bass = knobBass(knobY)
        val o = (0 until 5).map { reply[2 + 2 * it] / 60.0 }
        val a = MutableList(5) { 0.0 }
        a[0] = o[0]
        for (k in 0 until 4) a[k + 1] = o[k + 1] + a[k]
        val gains = a.mapIndexed { i, v -> (if (bass) v - p.bassBoost[i] else v).let { round(10 * (it - levelShiftDb)) / 10 } }
        return gains to bass
    }

    /** The knob Y value the five-band set writes (`EqKnobValuesCoder.encode` with no gains): the new-storage bit and the bass boost bit. */
    public fun shelfKnobY(bassBoost: Boolean): Int = NEW_STORAGE or (if (bassBoost) BASS_BOOST else 0)

    /** Whether the bass boost bit of a knob Y value is set, as `EqKnobValuesCoder.decode` reads it (old storage reads as off). */
    public fun knobBass(y: Int): Boolean {
        if ((y and OLD_CLASSIC) == OLD_CLASSIC || (y and NEW_STORAGE) != NEW_STORAGE) return false
        return (y and NEW_STORAGE.inv() and BASS_BOOST) == BASS_BOOST
    }

    // Three bands ------------------------------------------------------------------------------------------------------------------------------

    /** `Eq3Band.smooth`: the gains centred on their mean and cut so that none passes the gain limit. */
    public fun threeSmooth(t: List<Double>, gainLimit: Int): List<Double> {
        val a = t.sum() / 3
        val c = t.map { it - a }
        val o = max(max(max(c[0], c[1]), c[2]), gainLimit.toDouble()) - gainLimit
        return c.map { it - o }
    }

    /** The parameter group `Eq3Band.generateCommandParamsFromBandGains` builds from the smoothed gains. */
    public fun threeParams(t: List<Double>, bassBoost: Boolean): List<Int> {
        val n = t[0]
        val i = t[1]
        val r = t[2]
        val o = if (truncate(100 * i) == 425.0) 4.24 else i
        val s = if (bassBoost) SennheiserEqTables.THREE_BAND_BASS_FREQUENCY else SennheiserEqTables.THREE_BAND_LOWER_MID_FREQUENCY
        fun toInt32(x: Double) = truncate(x).toLong().toInt()
        val values = listOf(
            9, 257, toInt32(60 * o), 272, 10, 273, 3 * s, 274, toInt32(60 * (n - i)), 275, 2896,
            288, 11, 289, 3 * SennheiserEqTables.THREE_BAND_UPPER_MID_FREQUENCY, 290, toInt32(60 * (r - i)), 291, 2896, 0,
        )
        return values.map { it and 0xFFFF }
    }

    /** `EqKnobValuesCoder.encode` for the three bands: the knob X and Y values (bass and mid gains; treble gain and flags). */
    public fun threeKnobs(gains: List<Double>, bassBoost: Boolean): Pair<Int, Int> {
        val max = SennheiserEqTables.THREE_BAND_RANGE_DB.toDouble()
        fun two(v: Double) = round(v * 100) / 100
        fun byte(v: Double): Int = truncate(two(v) / max * 127).toLong().toInt() and 255
        val x = byte(gains[0]) or (byte(gains[1]) shl 8)
        val y = byte(gains[2]) or NEW_STORAGE or (if (bassBoost) BASS_BOOST else 0)
        return x to y
    }

    /** `EqKnobValuesCoder.decode`: the three gains and the bass boost the knob values say, or null where the earbuds hold an older storage. */
    public fun threeFromKnobs(x: Int, y: Int): Pair<List<Double>, Boolean>? {
        if ((y and OLD_CLASSIC) == OLD_CLASSIC || (y and NEW_STORAGE) != NEW_STORAGE) return null
        val max = SennheiserEqTables.THREE_BAND_RANGE_DB.toDouble()
        var n = y and NEW_STORAGE.inv()
        if ((n and CLASSIC_EQ) == CLASSIC_EQ) n = n and CLASSIC_EQ.inv()
        val bass = (n and BASS_BOOST) == BASS_BOOST
        if (bass) n = n and BASS_BOOST.inv()
        if ((n and PEAK_FILTER) == PEAK_FILTER) n = n and PEAK_FILTER.inv()
        fun gain(o: Int) = (if (o > 128) o - 256 else o) / 127.0 * max
        fun two(v: Double) = round(v * 100) / 100
        return listOf(two(gain(x and 255)), two(gain((x shr 8) and 255)), two(gain(n and 255))) to bass
    }

    /** `EqKnobValuesCoder.encode` with the Momentum 3's peak filter flag (high-end sound tuning) in place of the bass boost. */
    public fun threeKnobsPeak(gains: List<Double>, peak: Boolean): Pair<Int, Int> {
        val (x, y) = threeKnobs(gains, false)
        return x to (if (peak) y or PEAK_FILTER else y)
    }

    /** `EqKnobValuesCoder.decode` for the Momentum 3: the gains and the peak filter flag; an older storage reads as flat (`resetEQ`). */
    public fun threeKnobsPeakState(x: Int, y: Int): Pair<List<Double>, Boolean> {
        val got = threeFromKnobs(x, y) ?: return listOf(0.0, 0.0, 0.0) to false
        return got.first to ((y and NEW_STORAGE.inv() and PEAK_FILTER) == PEAK_FILTER)
    }

    private fun lowShelf(f: Double, gainDb: Double, q: Double, fs: Double): DoubleArray {
        val a = 2 * Math.PI * f / fs
        val o = Math.pow(10.0, gainDb / 40)
        val s = StrictMath.sin(a) / (2 * q)
        val c = o * (o + 1 - (o - 1) * StrictMath.cos(a) + 2 * Math.sqrt(o) * s)
        val d = 2 * o * (o - 1 - (o + 1) * StrictMath.cos(a))
        val l = o * (o + 1 - (o - 1) * StrictMath.cos(a) - 2 * Math.sqrt(o) * s)
        val u = o + 1 + (o - 1) * StrictMath.cos(a) + 2 * Math.sqrt(o) * s
        val v = -2 * (o - 1 + (o + 1) * StrictMath.cos(a))
        val p = o + 1 + (o - 1) * StrictMath.cos(a) - 2 * Math.sqrt(o) * s
        return doubleArrayOf(c / u, d / u, l / u, v / u, p / u)
    }

    private fun highShelf(f: Double, gainDb: Double, q: Double, fs: Double): DoubleArray {
        val a = 2 * Math.PI * f / fs
        val o = Math.pow(10.0, gainDb / 40)
        val s = StrictMath.sin(a) / (2 * q)
        val c = o * (o + 1 + (o - 1) * StrictMath.cos(a) + 2 * Math.sqrt(o) * s)
        val d = -2 * o * (o - 1 + (o + 1) * StrictMath.cos(a))
        val l = o * (o + 1 + (o - 1) * StrictMath.cos(a) - 2 * Math.sqrt(o) * s)
        val u = o + 1 - (o - 1) * StrictMath.cos(a) + 2 * Math.sqrt(o) * s
        val v = 2 * (o - 1 - (o + 1) * StrictMath.cos(a))
        val p = o + 1 - (o - 1) * StrictMath.cos(a) - 2 * Math.sqrt(o) * s
        return doubleArrayOf(c / u, d / u, l / u, v / u, p / u)
    }

    private fun peakFilter(f: Double, gainDb: Double, q: Double, fs: Double): DoubleArray {
        val a = 2 * Math.PI * f / fs
        val o = Math.pow(10.0, gainDb / 40)
        val s = StrictMath.sin(a) / (2 * q)
        val c = 1 + s * o
        val d = -2 * StrictMath.cos(a)
        val l = 1 - s * o
        val u = 1 + s / o
        val p = 1 - s / o
        return doubleArrayOf(c / u, d / u, l / u, d / u, p / u)
    }

    /**
     * The three parameter groups the Momentum 3's equalizer writes (`Eq3BandM3.send`): the high shelf, the low shelf and the master gain, each as three bytes (index, no commit, type)
     * and five 32-bit numbers, the coefficients scaled by 2^30 as `x * 2**30 | 0` does. [gains] are the clipped band gains in dB; [peak] the high-end sound tuning.
     */
    public fun m3Groups(gains: List<Double>, peak: Boolean): List<List<Long>> {
        val t = threeSmooth(gains, 4)
        val n: DoubleArray
        if (peak) {
            val a = peakFilter(50.0, -4.0, 0.5, 44100.0)
            val b = peakFilter(500.0, 3.0, 1.0, 44100.0)
            var i = 1.0
            while (maxOf(abs(a[0]), abs(a[1]), abs(a[2])) > 1.9) { a[0] *= .5; a[1] *= .5; a[2] *= .5; i *= 2 }
            while (maxOf(abs(b[0]), abs(b[1]), abs(b[2])) > 1.9) { b[0] *= .5; b[1] *= .5; b[2] *= .5; i *= 2 }
            n = a + b + doubleArrayOf(i)
        } else {
            val lo = lowShelf(SennheiserEqTables.THREE_BAND_LOWER_MID_FREQUENCY.toDouble(), t[0] - t[1], Math.sqrt(.5), 44100.0)
            val hi = highShelf(SennheiserEqTables.THREE_BAND_UPPER_MID_FREQUENCY.toDouble(), t[2] - t[1], Math.sqrt(.5), 44100.0)
            n = lo + hi + doubleArrayOf(Math.pow(10.0, t[1] / 20))
        }
        val low = n.copyOfRange(0, 5)
        val high = n.copyOfRange(5, 10)
        var master = n[10]
        var o = maxOf(abs(low[0]), abs(low[1]), abs(low[2]))
        var s = maxOf(abs(high[0]), abs(high[1]), abs(high[2]))
        while (o > 1.9) { low[0] *= .5; low[1] *= .5; low[2] *= .5; master *= 2; o = maxOf(abs(low[0]), abs(low[1]), abs(low[2])) }
        while (s > 1.9) { high[0] *= .5; high[1] *= .5; high[2] *= .5; master *= 2; s = maxOf(abs(high[0]), abs(high[1]), abs(high[2])) }
        val scale = Math.pow(2.0, 30.0)
        fun int32(x: Double): Long = truncate(x).toLong().toInt().toLong()
        var c = 1
        while (master >= 1.9) { c += 1; master *= .5 }
        return listOf(
            listOf(1L, 1L, 0L) + high.map { int32(it * scale) },
            listOf(0L, 1L, 0L) + low.map { int32(it * scale) },
            listOf(0L, 0L, 3L, int32(master * scale), c.toLong(), 2L, 0L, 0L),
        )
    }

    private const val NEW_STORAGE = 16384
    private const val OLD_CLASSIC = 65280
    private const val CLASSIC_EQ = 8192
    private const val BASS_BOOST = 4096
    private const val PEAK_FILTER = 2048

    /** The numbers `a.b.c` of a firmware version, or null. */
    public fun version(text: String?): List<Int>? = text?.split('.')?.map { it.toIntOrNull() ?: return null }?.takeIf { it.size == 3 }

    /** True when version [have] is at least [min] (both `major.minor.patch`). */
    public fun atLeast(have: List<Int>, min: List<Int>): Boolean {
        for (k in 0 until 3) if (have[k] != min[k]) return have[k] > min[k]
        return true
    }
}
