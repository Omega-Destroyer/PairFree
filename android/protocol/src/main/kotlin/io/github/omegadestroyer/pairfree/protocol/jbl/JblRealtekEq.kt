package io.github.omegadestroyer.pairfree.protocol.jbl

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Realtek's segment EQ on the Tune 520BT family (command A6; docs/jbl/PROTOCOL.md section 11.1). JBL's app sends the
 * curve twice over: as the earbuds' biquad coefficients at 44.1 and 48 kHz, worked out with the design EQ the earbuds
 * report by Realtek's DspConfig library, and as the Harman curve the earbuds report back. [RealtekDsp] is a port of that
 * library's calculateEq.
 */
public object JblRealtekEq {
    /** A read of [category]'s EQ: 01 and the category, with a one-byte length. */
    public fun read(category: Int): JblPacket =
        JblPacket(JblCommand.SEGMENT_EQ, byteArrayOf(JblSegmentEq.READ.toByte(), category.toByte()), wide = false)

    /** The app's acknowledgement of each reply part but the last: AA 01, then A6 and 00. */
    public fun ack(): JblPacket = JblPacket.of(JblEq41.APP_ACK, JblCommand.SEGMENT_EQ, JblSegmentEq.ACK_OK)

    /**
     * A write of [curve] on top of the earbuds' [design] EQ (RtkEqUtils.serializeEqContent, SegmentEQCmd): the category, the
     * block count (2), the 44.1 and 48 kHz coefficient blocks and the Harman curve, cut into parts of the negotiated [mtu]
     * less 18, each led by 03, the provider, the part count and its index. Null when the library would send nothing (a
     * master gain beyond 12 dB).
     */
    public fun write(curve: JblEqCurve, design: JblEqCurve, mtu: Int): List<JblPacket>? {
        val blocks = listOf(44100.0 to JblSegmentEq.RATE_44, 48000.0 to JblSegmentEq.RATE_48).map { (fs, code) ->
            block(fs, code, curve, design) ?: return null
        }
        val harman = harman(curve)
        val content = byteArrayOf(curve.category.toByte(), blocks.size.toByte()) + blocks.reduce { a, b -> a + b } + harman
        val size = mtu - JblSegmentEq.MTU_LESS
        val count = (content.size + size - 1) / size
        return List(count) { i ->
            val head = byteArrayOf(JblSegmentEq.WRITE.toByte(), JblSegmentEq.PROVIDER.toByte(), count.toByte(), i.toByte())
            JblPacket(JblCommand.SEGMENT_EQ, head + content.copyOfRange(i * size, minOf(content.size, (i + 1) * size)))
        }
    }

    /**
     * One reply part (SegmentEQCmd.assembleEqPackage): 04, the provider, the part count, its index, then data. Returns the
     * part's index, count and data, or null when it is not a reply part.
     */
    public fun part(p: ByteArray): Triple<Int, Int, ByteArray>? {
        if (p.size < PART_HEADER || (p[0].toInt() and 0xFF) != JblSegmentEq.REPLY) return null
        val count = p[2].toInt() and 0xFF
        val index = p[3].toInt() and 0xFF
        if (index >= count) return null
        return Triple(index, count, p.copyOfRange(PART_HEADER, p.size))
    }

    /**
     * The Harman curve the earbuds report (RtkEqParameterConverter.deserializeHarmanEq): the category, the band count (low
     * nibble) and sample-rate code (high), a spare byte, the master gain in hundredths of a dB, then five bytes a band: the
     * type (3 bits), Q in hundredths (10 bits), the gain in hundredths (12 bits, signed) and the frequency (15 bits).
     * Null when cut short or with more than ten bands. One byte or none is JBL's default curve.
     */
    public fun parse(data: ByteArray): JblEqCurve? {
        if (data.size <= 1) return JblEqCurve(0, 1f, RATE_44_HZ, 0f, 0f, JblEq.BANDS_HZ.map { JblEqBand(JblEq.PEAK, it.toFloat(), 0f, 1f) })
        if (data.size < HARMAN_HEADER) return null
        val count = data[1].toInt() and 0x0F
        if (count > MAX_STAGES || count * HARMAN_BAND > data.size - HARMAN_HEADER) return null
        val gain = gain(((data[4].toInt() shl 8) or (data[3].toInt() and 0xFF)) and 0xFFFF, 0x8000)
        val bands = List(count) { i ->
            val at = HARMAN_HEADER + i * HARMAN_BAND
            val b0 = data[at].toInt()
            val b1 = data[at + 1].toInt()
            val b3 = data[at + 3].toInt()
            val q = (((b1 shl 5) and 0x3E0) or ((b0 shr 3) and 0x1F)) / 100f
            val g = (((((b1 and 0xE0) shr 5) and 0xFF) or (data[at + 2].toInt() shl 3)) and 0x7FF) or (if (b3 and 1 == 0) 0 else 0xFF shl 11)
            val hz = ((b3 shr 1) and 0x7F) or ((data[at + 4].toInt() shl 7) and 0x7F80)
            JblEqBand(harmanType(b0 and 7), hz.toFloat(), gain(g and 0xFFFF, 0x800), q)
        }
        val rate = when (data[1].toInt() shr 4 and 0x0F) {
            JblSegmentEq.RATE_44 -> RATE_44_HZ
            JblSegmentEq.RATE_48 -> RATE_48_HZ
            JblSegmentEq.RATE_96 -> RATE_96_HZ
            else -> 0
        }
        return JblEqCurve(data[0].toInt() and 0xFF, 0f, rate, gain, gain, bands)
    }

    /** RtkEqParameterConverter.serializeHarmanEq: the layout [parse] reads. */
    internal fun harman(curve: JblEqCurve): ByteArray {
        val n = curve.bands.size
        val out = ByteArray(HARMAN_HEADER + n * HARMAN_BAND)
        out[0] = curve.category.toByte()
        out[1] = ((n and 0x0F) or ((rateCode(curve.sampleRate) and 0x0F) shl 4)).toByte()
        val global = (curve.rightGain * 100f).toInt()
        out[3] = global.toByte()
        out[4] = (global shr 8).toByte()
        curve.bands.forEachIndexed { i, b ->
            val q = (b.q * 100f).toInt()
            var g = (b.gainDb * 100f).toInt()
            val hz = b.hz.toInt()
            if (g < 0) g = ((-g).inv() + 1) and 0xFFFF
            val word = (q shl 3) or (realtekType(b.type) and 7) or ((g and 0xFFF) shl 13) or ((hz and 0x7F) shl 25)
            val at = HARMAN_HEADER + i * HARMAN_BAND
            out[at] = word.toByte()
            out[at + 1] = (word shr 8).toByte()
            out[at + 2] = (word shr 16).toByte()
            out[at + 3] = (word shr 24).toByte()
            out[at + 4] = (hz shr 7).toByte()
        }
        return out
    }

    /**
     * One sample rate's coefficient block (RtkEqUtils.createRtkSrEntity and RtkSrEntity): the rate code, the design EQ's
     * band count, the largest gain the curve adds in 1/128 dB, the data's length plus 4, 00 00 00 01, then the library's
     * stage data. The curve's bands come first, then the design EQ's.
     */
    internal fun block(fs: Double, code: Int, curve: JblEqCurve, design: JblEqCurve): ByteArray? {
        val bands = curve.bands + design.bands
        val (maxDb, data) = RealtekDsp.calculate(
            fs,
            (curve.rightGain + design.rightGain).toDouble(),
            bands.map { it.gainDb.toDouble() },
            bands.map { it.hz.toDouble() },
            bands.map { it.q.toDouble() },
            bands.map { realtekType(it.type) },
            design.rightGain.toDouble(),
            design.bands.size,
        ) ?: return null
        val length = data.size + 4
        return byteArrayOf(
            code.toByte(), design.bands.size.toByte(), maxDb.toByte(), (maxDb shr 8).toByte(),
            length.toByte(), (length shr 8).toByte(), 0, 0, 0, 1,
        ) + data
    }

    /** Harman's band type to Realtek's (RtkEqParameterConverter.wrapperBandType): 0 low shelf to 1, 1 peak to 0, 2 to 6 the same. */
    private fun realtekType(t: Int): Int = when (t) {
        0 -> 1
        in 2..6 -> t
        else -> 0
    }

    /** Realtek's band type to Harman's (decodeBandType). */
    private fun harmanType(t: Int): Int = when (t) {
        0 -> 1
        in 2..6 -> t
        else -> 0
    }

    private fun rateCode(rate: Int): Int = when (rate) {
        44, RATE_44_HZ -> JblSegmentEq.RATE_44
        48, RATE_48_HZ -> JblSegmentEq.RATE_48
        96, RATE_96_HZ -> JblSegmentEq.RATE_96
        else -> 0
    }

    /** A signed value in hundredths, its sign at [sign] (convertGain, convertGainV11). */
    private fun gain(v: Int, sign: Int): Float = (if (v and sign == 0) v / 100.0 else 0.0 - ((v.inv() + 1) and 0xFFFF) / 100.0).toFloat()

    private const val PART_HEADER = 4
    private const val HARMAN_HEADER = 5
    private const val HARMAN_BAND = 5
    private const val MAX_STAGES = 10
    private const val RATE_44_HZ = 44100
    private const val RATE_48_HZ = 48000
    private const val RATE_96_HZ = 96000
}

/**
 * A port of Realtek's DspConfig library (libDspConfig.so, JBL Headphones 5.37.10): calculateEq and AssembleModuleData,
 * read from its arm64 code and checked against the library run in an emulator (docs/jbl/PROTOCOL.md section 11.1).
 */
internal object RealtekDsp {
    private const val PI = 3.141592653589793
    private const val TWO_PI = 6.283185307179586
    private const val SQRT2 = 1.4142135623730951
    private const val POINTS = 65536
    private const val FLOOR = 1e-5
    private const val Q30 = 1073741824.0

    /** A biquad: b0..b2, a0..a2 (a0 1) and the scale the numerator was divided by. */
    private class Filter(b: DoubleArray, a1: Double, a2: Double) {
        val b: DoubleArray
        val a = doubleArrayOf(1.0, a1, a2)
        val scale: Double

        init {
            // Every design scales the numerator down by its largest term once any term reaches 2.
            if (abs(b[0]) >= 2 || abs(b[1]) >= 2 || abs(b[2]) >= 2) {
                var m = if (b[0].isNaN()) 0.0 else abs(b[0])
                m = if (abs(b[1]) >= m) abs(b[1]) else m
                m = if (abs(b[2]) >= m) abs(b[2]) else m
                this.b = doubleArrayOf(b[0] / m, b[1] / m, b[2] / m)
                scale = m
            } else {
                this.b = b
                scale = 1.0
            }
        }

        /** FilterCoefFix(30): b1, b2, a1 and a2 onto the Q30 grid; b0 stays as it is. */
        fun fix() {
            for (k in 1..2) {
                b[k] = roundAway(b[k] * Q30) / Q30
                a[k] = roundAway(a[k] * Q30) / Q30
            }
        }
    }

    /**
     * The stage data DspConfig.calculateEq returns after its 14-byte header, and the largest gain the first
     * `gains.size - designCount` bands add over the design EQ's last [designCount], in 1/128 dB. Null when the library
     * gives up (a master gain beyond 12 dB).
     */
    fun calculate(
        fs: Double,
        gain: Double,
        gains: List<Double>,
        freqs: List<Double>,
        qs: List<Double>,
        types: List<Int>,
        designGain: Double,
        designCount: Int,
    ): Pair<Int, ByteArray>? {
        if (abs(gain) > 12.0) return null
        val n = gains.size
        val base = n - designCount
        var total = dbToLinear(gain)
        val filters = ArrayList<Filter>(n)
        val designs = ArrayList<Filter>(designCount)
        for (i in 0 until n) {
            val f = design(types[i], dbToLinear(gains[i]), freqs[i], qs[i], fs)
            // The library designs the design EQ's bands with the first bands' types, as here.
            if (i < designCount) designs += design(types[i], dbToLinear(gains[base + i]), freqs[base + i], qs[base + i], fs).also { it.fix() }
            f.fix()
            filters += f
            total *= f.scale
        }
        val out = ByteArray(4 + n * 20)
        putInt(out, 0, (n and 0x1F) or 0x40 or (quantize(total, 24, 12) shl 8))
        filters.forEachIndexed { i, f ->
            listOf(f.b[0], f.b[1], f.b[2], f.a[1], f.a[2]).forEachIndexed { k, v -> putInt(out, 4 + i * 20 + k * 4, quantize(v, 32, 30)) }
        }
        return maxGain(gain, filters, designGain, designs) to out
    }

    /** calculateDiffMaxAndMinDb: the largest ratio of the whole response to the design EQ's, as 1/128 dB (RoundtoInt). */
    private fun maxGain(gain: Double, filters: List<Filter>, designGain: Double, designs: List<Filter>): Int {
        val totalResponse = DoubleArray(POINTS) { dbToLinear(gain) }
        val designResponse = DoubleArray(POINTS) { dbToLinear(designGain) }
        for (f in filters) response(f, totalResponse)
        for (f in designs) response(f, designResponse)
        var best = 0.0
        for (i in 0 until POINTS) {
            val v = totalResponse[i] / designResponse[i]
            if (v > best) best = v
        }
        val db = if (best > 0) log10(best) * 20.0 else 0.0
        return (db * 128.0 + 0.5).toInt()
    }

    /** FreqResponse over 65536 points from 0 to -pi, multiplied into [into]; at least 1e-5. */
    private fun response(f: Filter, into: DoubleArray) {
        val (cs, sn) = table
        for (i in 0 until POINTS) {
            var br = f.b[0] + 0.0
            var bi = 0.0
            var ar = 1.0
            var ai = 0.0
            for (k in 1..2) {
                val c = cs[k - 1][i]
                val s = sn[k - 1][i]
                br += f.b[k] * c
                bi += f.b[k] * s
                ar += f.a[k] * c
                ai += f.a[k] * s
            }
            val den = ar * ar + ai * ai
            val im = (ar * bi - ai * br) / den
            val re = (br * ar + ai * bi) / den
            val m = sqrt(re * re + im * im) * f.scale
            into[i] *= if (abs(m) > FLOOR) m else FLOOR
        }
    }

    /** cos and sin of k times each point's angle, for k 1 and 2: the library's sincos(i * -pi / 65536 * k). */
    private val table: Pair<Array<DoubleArray>, Array<DoubleArray>> by lazy {
        val step = -PI / POINTS
        val cs = Array(2) { k -> DoubleArray(POINTS) { i -> cos(step * i * (k + 1)) } }
        val sn = Array(2) { k -> DoubleArray(POINTS) { i -> sin(step * i * (k + 1)) } }
        cs to sn
    }

    private fun design(type: Int, a: Double, fc: Double, q: Double, fs: Double): Filter = when (type) {
        1 -> shelf(a, fc, q, fs, high = false)
        2 -> shelf(a, fc, q, fs, high = true)
        3 -> lowPass(a, fc, fs)
        4 -> highPass(a, fc, fs)
        5 -> bandPass(a, fc, q, fs)
        6 -> bandReject(a, fc, q, fs)
        7 -> allPass(a, fc, q, fs)
        else -> peak(a, fc, q, fs)
    }

    private fun peak(a: Double, fc: Double, q: Double, fs: Double): Filter {
        val w0 = fc * TWO_PI / fs
        val k = w0 / ((if (a < 1.0) a + a else 2.0) * q)
        val beta = ((1.0 - k) * -0.5) / (k + 1.0)
        val gamma = cos(w0) * (0.5 - beta)
        val d4 = beta * 0.5 + 0.25
        val b0 = (a - 1.0) * d4 + 0.5
        val b2 = -((a - 1.0) * d4) - beta
        return Filter(doubleArrayOf(b0 + b0, gamma * -2.0, b2 + b2), gamma * -2.0, beta * -2.0)
    }

    private fun bandPass(a: Double, fc: Double, q: Double, fs: Double): Filter {
        val t = tan(fc * PI / fs)
        val t2 = t * t
        val d = t2 * q + t + q
        return Filter(doubleArrayOf(t / d * a, 0.0 * a, -t / d * a), (q + q) * (t * t - 1.0) / d, (t2 * q - t + q) / d)
    }

    private fun bandReject(a: Double, fc: Double, q: Double, fs: Double): Filter {
        val t = tan(fc * PI / fs)
        val t2 = t * t
        val d = t2 * q + t + q
        val a1 = (q + q) * (t * t - 1.0) / d
        val b0 = q * (t * t + 1.0) / d * a
        return Filter(doubleArrayOf(b0, a1 * a, b0), a1, (t2 * q - t + q) / d)
    }

    private fun clampHz(fc: Double): Double = if (fc < 20.0) 20.0 else if (fc > 20000.0) 20000.0 else fc

    private fun lowPass(gain: Double, hz: Double, fs: Double): Filter {
        val fc = clampHz(hz)
        var a = if (gain <= -12.0) -12.0 else gain
        a = if (a >= 6.0) 6.0 else a
        val t = tan(fc * PI / fs)
        val d = t * t + (t * SQRT2 + 1.0)
        val h = t * (a * t) / (d + d)
        val b0 = h + h
        return Filter(doubleArrayOf(b0, b0 + b0, b0), (1.0 - t * t) / d * -2.0, ((t * SQRT2 - 1.0) - t * t) * 0.5 / d * -2.0)
    }

    private fun highPass(a: Double, hz: Double, fs: Double): Filter {
        val fc = clampHz(hz)
        val t = tan(fc * PI / fs)
        val d = t * t + (t * SQRT2 + 1.0)
        val h = a / (d + d)
        val m = h * -2.0
        return Filter(doubleArrayOf(h + h, m + m, h + h), (1.0 - t * t) / d * -2.0, ((t * SQRT2 - 1.0) - t * t) * 0.5 / d * -2.0)
    }

    private fun allPass(a: Double, fc: Double, q: Double, fs: Double): Filter {
        val t = tan(fc * PI / fs)
        val t2 = t * t
        val d = t2 * q + t + q
        val a1 = (q + q) * (t * t - 1.0) / d
        val a2 = (t2 * q - t + q) / d
        // The library keeps 1 as the denominator's last term.
        return Filter(doubleArrayOf(a2 * a, a1 * a, 1.0 * a), a1, 1.0)
    }

    /** Shelving filters with Q as the shelf slope; the library takes the square root of the linear gain, as RBJ's A. */
    private fun shelf(gain: Double, fc: Double, q: Double, fs: Double, high: Boolean): Filter {
        val a = sqrt(gain)
        val w0 = fc * TWO_PI / fs
        val s = sin(w0)
        val c = cos(w0)
        val am1 = a - 1.0
        val ap1 = a + 1.0
        val alpha = s * 0.5 * sqrt((a + 1.0 / a) * (1.0 / q - 1.0) + 2.0)
        val two = sqrt(a) + sqrt(a)
        return if (!high) {
            val d19 = ap1 - am1 * c
            val d1 = am1 * c + ap1
            val a0 = two * alpha + d1
            Filter(
                doubleArrayOf(a * (two * alpha + d19) / a0, (a + a) * (am1 - ap1 * c) / a0, a * (d19 - two * alpha) / a0),
                (ap1 * c + am1) * -2.0 / a0,
                (d1 - two * alpha) / a0,
            )
        } else {
            val d19 = am1 * c + ap1
            val d1 = ap1 - am1 * c
            val a0 = two * alpha + d1
            val x = am1 - ap1 * c
            Filter(
                doubleArrayOf(a * (two * alpha + d19) / a0, a * -2.0 * (ap1 * c + am1) / a0, a * (d19 - two * alpha) / a0),
                (x + x) / a0,
                (d1 - two * alpha) / a0,
            )
        }
    }

    /** SignedQuant: [v] times 2^[frac], rounded half away from zero and held to [bits] bits; NaN gives 0. */
    private fun quantize(v: Double, bits: Int, frac: Int): Int {
        val r = roundAway(v * 2.0.pow(if (frac < bits) frac else bits - 1))
        if (r.isNaN()) return 0
        val lim = 2.0.pow(minOf(bits, 32) - 1)
        return when {
            r >= lim -> (lim - 1.0).toLong().toInt()
            r <= -lim -> (-lim).toLong().toInt()
            else -> r.toLong().toInt()
        }
    }

    private fun roundAway(x: Double): Double {
        if (x.isNaN() || x.isInfinite()) return x
        val ax = abs(x)
        var r = floor(ax)
        if (ax - r >= 0.5) r += 1.0
        return if (x >= 0) r else -r
    }

    private fun dbToLinear(db: Double): Double = 10.0.pow(db / 20.0)

    private fun putInt(out: ByteArray, at: Int, v: Int) {
        out[at] = v.toByte()
        out[at + 1] = (v shr 8).toByte()
        out[at + 2] = (v shr 16).toByte()
        out[at + 3] = (v shr 24).toByte()
    }
}
