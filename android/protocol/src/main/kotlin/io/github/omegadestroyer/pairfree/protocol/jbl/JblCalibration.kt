package io.github.omegadestroyer.pairfree.protocol.jbl

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The calibration JBL's app sends with an EQ on its BES models (docs/jbl/PROTOCOL.md section 11): how far the design EQ
 * and the new curve together rise above the design EQ alone, so the earbuds can turn the level down and not clip. Ported
 * from what JBL's calibration library does (EqCalibration.calculateCalib in libcalibrationLib): each band becomes a biquad
 * from the Audio EQ Cookbook at 48 kHz, the magnitude is taken at 4096 points from 0 to half the sample rate, and when the
 * combined peak is higher than the design EQ's, the calibration is that peak in dB plus 1 dB, unless the design EQ's
 * own gain already covers it. The library's mix of single and double precision is kept.
 */
public object JblCalibration {
    /** What the library returns for [user] on top of [design]; JBL's app sends it as it is. */
    public fun of(design: JblEqCurve, user: List<JblEqBand>): Float {
        val designResponse = response(design.bands)
        val total = response(user).let { u -> DoubleArray(POINTS) { designResponse[it] * u[it] } }
        val peakDesign = designResponse.max()
        val peakTotal = total.max()
        if (peakTotal <= peakDesign) return 0f
        val needed = abs(DB * log10(peakTotal)) + ERR_THRESHOLD_DB
        return if (abs(design.leftGain.toDouble()) < needed) needed.toFloat() else 0f
    }

    /** The product of [bands]' magnitudes at each point (freqz into a curve that starts at 1). */
    internal fun response(bands: List<JblEqBand>): DoubleArray {
        val out = DoubleArray(POINTS) { 1.0 }
        for (band in bands) {
            val c = design(band)
            val b = doubleArrayOf(c[B0], c[B1], c[B2])
            val a = doubleArrayOf(1.0, c[A1], c[A2])
            for (i in 0 until POINTS) {
                var numRe = 0.0
                var numIm = 0.0
                var denRe = 0.0
                var denIm = 0.0
                for (k in 0..2) {
                    // The angle in single precision, as the library works it out: k * pi * i / N.
                    val angle = k.toFloat() * PI_F * i.toFloat() / POINTS.toFloat()
                    val re = cos(angle).toDouble()
                    val im = (-sin(angle)).toDouble()
                    numRe += re * b[k]
                    numIm += im * b[k]
                    denRe += re * a[k]
                    denIm += im * a[k]
                }
                val den = denRe * denRe + denIm * denIm
                val qRe = (numRe * denRe + numIm * denIm) / den
                val qIm = (numIm * denRe - numRe * denIm) / den
                out[i] *= sqrt(qRe * qRe + qIm * qIm)
            }
        }
        return out
    }

    /** One band's biquad (iirfilt_design): a1, a2, b0, b1, b2, already divided by a0. */
    internal fun design(band: JblEqBand): DoubleArray {
        val type = band.type.takeIf { it < MAX_TYPE } ?: OTHER_TYPE
        val q = band.q.toDouble()
        val gain = band.gainDb.toDouble()
        // Every type but the peak works out w0 from whole numbers in single precision; the peak in double, then rounds it.
        val w0 = if (type == PEAK) (band.hz.toDouble() * TWO_PI_D / FS).toFloat() else band.hz.toDouble().toInt().toFloat() * TWO_PI_F / FS.toFloat()
        val s = sin(w0)
        val c = cos(w0)
        val alpha = s.toDouble() / (q + q)
        val minus2Cos = (c * -2f).toDouble()
        return when (type) {
            LOW_SHELF, HIGH_SHELF -> {
                val aF = 10f.pow((gain / GAIN_DIVISOR).toFloat())
                val twoSqrtA = (sqrt(aF) + sqrt(aF)).toDouble()
                val a = aF.toDouble()
                val cd = c.toDouble()
                val p = a + 1
                val m = a - 1
                if (type == LOW_SHELF) {
                    val d5 = p - m * cd
                    val d6 = p + m * cd
                    val a0 = d6 + twoSqrtA * alpha
                    doubleArrayOf(-2 * (p * cd + m) / a0, (d6 - twoSqrtA * alpha) / a0, (d5 + twoSqrtA * alpha) * a / a0, (a + a) * (m - p * cd) / a0, (d5 - twoSqrtA * alpha) * a / a0)
                } else {
                    val d5 = p + m * cd
                    val d6 = p - m * cd
                    val a0 = d6 + twoSqrtA * alpha
                    doubleArrayOf(2 * (m - p * cd) / a0, (d6 - twoSqrtA * alpha) / a0, (d5 + twoSqrtA * alpha) * a / a0, a * -2 * (p * cd + m) / a0, (d5 - twoSqrtA * alpha) * a / a0)
                }
            }
            PEAK -> {
                val a = 10f.pow((gain / GAIN_DIVISOR).toFloat()).toDouble()
                val a0 = alpha / a + 1
                doubleArrayOf(minus2Cos / a0, (1 - alpha / a) / a0, (alpha * a + 1) / a0, minus2Cos / a0, (1 - alpha * a) / a0)
            }
            LOW_PASS, HIGH_PASS -> {
                val onePlusOrMinusCos = if (type == LOW_PASS) 1f - c else c + 1f
                val half = (onePlusOrMinusCos * 0.5f).toDouble()
                val mid = if (type == LOW_PASS) onePlusOrMinusCos.toDouble() else (-onePlusOrMinusCos).toDouble()
                val a0 = alpha + 1
                doubleArrayOf(minus2Cos / a0, (1 - alpha) / a0, half / a0, mid / a0, half / a0)
            }
            BAND_PASS -> {
                val a0 = alpha + 1
                doubleArrayOf(minus2Cos / a0, (1 - alpha) / a0, (s * 0.5f).toDouble() / a0, 0.0, (s * -0.5f).toDouble() / a0)
            }
            // The library writes b2 as +alpha here, where the cookbook has -alpha; kept as it is.
            BAND_PASS_PEAK -> {
                val a0 = alpha + 1
                doubleArrayOf(minus2Cos / a0, (1 - alpha) / a0, alpha / a0, 0.0, alpha / a0)
            }
            NOTCH -> {
                val a0 = alpha + 1
                doubleArrayOf(minus2Cos / a0, (1 - alpha) / a0, 1 / a0, minus2Cos / a0, 1 / a0)
            }
            ALL_PASS -> {
                val a0 = alpha + 1
                doubleArrayOf(minus2Cos / a0, (1 - alpha) / a0, (1 - alpha) / a0, minus2Cos / a0, a0 / a0)
            }
            else -> doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.0)
        }
    }

    private const val POINTS = 4096
    private const val FS = 48000
    private const val PI_F = 3.1415927f
    private const val TWO_PI_F = 6.2831855f
    private const val TWO_PI_D = 6.2831854820251465
    private const val GAIN_DIVISOR = 40.0
    private const val DB = 20.0
    private const val ERR_THRESHOLD_DB = 1.0

    // Coefficient order: a1, a2, b0, b1, b2.
    private const val A1 = 0
    private const val A2 = 1
    private const val B0 = 2
    private const val B1 = 3
    private const val B2 = 4

    private const val LOW_SHELF = 0
    private const val PEAK = 1
    private const val HIGH_SHELF = 2
    private const val LOW_PASS = 3
    private const val HIGH_PASS = 4
    private const val BAND_PASS = 6
    private const val BAND_PASS_PEAK = 7
    private const val NOTCH = 8
    private const val ALL_PASS = 9

    /** Types from 11 up count as 5, which like 10 has no filter of its own: every coefficient 1. */
    private const val MAX_TYPE = 11
    private const val OTHER_TYPE = 5
}
