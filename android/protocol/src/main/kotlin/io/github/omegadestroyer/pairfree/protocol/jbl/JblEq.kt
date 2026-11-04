package io.github.omegadestroyer.pairfree.protocol.jbl

/** One band of a JBL EQ: its filter type (1 peak, 0 low shelf, 2 high shelf; docs/jbl/PROTOCOL.md section 11), frequency, gain and Q. */
public data class JblEqBand(val type: Int, val hz: Float, val gainDb: Float, val q: Float)

/**
 * One of JBL's presets (data/jbl/eq_presets.json): its name, the category the earbuds report and are sent, the 41
 * command's preset number where JBL's file gives one, the left and right master gain, and the curve.
 */
public data class JblEqPreset(
    val name: String,
    val category: Int,
    val index: Int?,
    val leftGain: Float,
    val rightGain: Float,
    val bands: List<JblEqBand>,
)

/**
 * A model's EQ (devices.json eq): how JBL's app writes it ([path]), its presets, the custom curve's range in dB either
 * way, whether JBL's app reads the design EQ on connecting and computes the calibration from it.
 */
public data class JblEqSpec(
    val path: Path,
    val presets: List<JblEqPreset>,
    val rangeDb: Int,
    val readsDesign: Boolean,
    val calibrate: Boolean,
) {
    /**
     * The command each model's control class sends (docs/jbl/PROTOCOL.md section 11): A2 ([A2], or [A2_Q10] with Q in
     * tenths), the older 41 transfer ([CMD41], and [CLUB], which picks presets with 40), Realtek's segments ([SEGMENT]),
     * protocol 4's EQ features ([P4]) or Airoha's PEQ ([AIROHA]).
     */
    public enum class Path { A2, A2_Q10, CMD41, CLUB, SEGMENT, P4, AIROHA }

    /** True for the paths PairFree writes so far. */
    public val written: Boolean get() = path == Path.A2 || path == Path.A2_Q10
}

/**
 * An EQ as the earbuds report it or PairFree writes it (JBL's EQSettings2): the category (a preset's, 230 a curve of the
 * user's own, 202 the design EQ), the calibration, the sample rate in kHz, the left and right gain and the bands.
 */
public data class JblEqCurve(
    val category: Int,
    val calibration: Float,
    val sampleRate: Int,
    val leftGain: Float,
    val rightGain: Float,
    val bands: List<JblEqBand>,
)

/** JBL's EQ command A2 (EQCmd, docs/jbl/PROTOCOL.md section 11). */
public object JblEq {
    /** The frequencies of JBL's ten bands, for a curve of the user's own (pp.a's default list). */
    public val BANDS_HZ: List<Int> = listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    /** The sample rate JBL's app writes when the product names none, in kHz. */
    public const val SAMPLE_RATE: Int = 48

    /** A read of [category]'s EQ: 01 and the category, with a one-byte length unlike the replies and writes. */
    public fun read(category: Int): JblPacket = JblPacket(JblCommand.EQ, byteArrayOf(JblEqWire.READ.toByte(), category.toByte()), wide = false)

    /** A write of [curve]; with [q10] the Q goes in tenths (the classes JBL's app marks QMultiply10EQ). */
    public fun write(curve: JblEqCurve, q10: Boolean): JblPacket {
        val out = ArrayList<Byte>(HEADER + curve.bands.size * BAND)
        out += (if (q10) JblEqWire.WRITE_Q10 else JblEqWire.WRITE).toByte()
        out += curve.category.toByte()
        out += float(curve.calibration)
        out += curve.sampleRate.toByte()
        out += float(curve.leftGain)
        out += float(curve.rightGain)
        out += curve.bands.size.toByte()
        for (b in curve.bands) {
            out += b.type.toByte()
            out += float(b.gainDb)
            out += float(b.hz)
            // Java's (byte) of the float: through an int, so 1.0 is 1 and 1.0 in tenths 10.
            out += (if (q10) b.q * Q_TENTHS else b.q).toInt().toByte()
        }
        return JblPacket(JblCommand.EQ, out.toByteArray())
    }

    /** A reply's EQ: the sub-command (02, or 04 with Q in tenths), the category, then as [write] lays it out. Null when cut short. */
    public fun parse(p: ByteArray): JblEqCurve? {
        if (p.size < 2) return null
        val sub = p[0].toInt() and 0xFF
        if (sub != JblEqWire.REPLY && sub != JblEqWire.REPLY_Q10) return null
        val category = p[1].toInt() and 0xFF
        if (p.size < HEADER) return null
        val count = p[HEADER - 1].toInt() and 0xFF
        if (p.size < HEADER + count * BAND) return null
        val bands = List(count) { i ->
            val at = HEADER + i * BAND
            val q = (p[at + 9].toInt() and 0xFF).toFloat()
            JblEqBand(p[at].toInt() and 0xFF, float(p, at + 5), float(p, at + 1), if (sub == JblEqWire.REPLY_Q10) q / Q_TENTHS else q)
        }
        return JblEqCurve(category, float(p, 2), p[6].toInt() and 0xFF, float(p, 7), float(p, 11), bands)
    }

    /** A curve of the user's own: JBL's ten peak bands at Q 1 with [gains] in dB (band type 2 on Airoha's chips). */
    public fun custom(gains: List<Float>, airoha: Boolean = false): List<JblEqBand> =
        BANDS_HZ.mapIndexed { i, hz -> JblEqBand(if (airoha) AIROHA_PEAK else PEAK, hz.toFloat(), gains.getOrElse(i) { 0f }, 1f) }

    /** Four bytes, low first. */
    private fun float(v: Float): List<Byte> {
        val bits = v.toRawBits()
        return listOf(bits.toByte(), (bits shr 8).toByte(), (bits shr 16).toByte(), (bits shr 24).toByte())
    }

    private fun float(p: ByteArray, at: Int): Float =
        Float.fromBits((p[at].toInt() and 0xFF) or ((p[at + 1].toInt() and 0xFF) shl 8) or ((p[at + 2].toInt() and 0xFF) shl 16) or ((p[at + 3].toInt() and 0xFF) shl 24))

    /** The sub-command, category, calibration, sample rate, two gains and band count; then ten bytes a band. */
    private const val HEADER = 16
    private const val BAND = 10
    private const val Q_TENTHS = 10f
    private const val PEAK = 1
    private const val AIROHA_PEAK = 2
}
