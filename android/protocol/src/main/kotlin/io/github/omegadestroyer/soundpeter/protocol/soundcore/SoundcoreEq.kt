package io.github.omegadestroyer.soundpeter.protocol.soundcore

import kotlin.math.floor

/**
 * Soundcore EQ values: one byte per band, 120 (`0x78`) for 0 dB and 0.1 dB per step. Gains here
 * are in tenths of a dB. See docs/soundcore/PROTOCOL.md section 6.
 */
public object SoundcoreEq {
    /** The preset number of a custom curve. */
    public const val CUSTOM_PRESET: Int = 0xFEFE
    public const val BANDS: Int = 10

    private const val MIN_TENTHS = -120
    private const val MAX_TENTHS = 255 - 121

    public fun toByte(tenths: Int): Byte = (tenths.coerceIn(MIN_TENTHS, MAX_TENTHS) - MIN_TENTHS).toByte()

    public fun toTenths(byte: Byte): Int = (byte.toInt() and 0xFF) + MIN_TENTHS

    public fun bytes(tenths: IntArray): ByteArray = ByteArray(tenths.size) { toByte(tenths[it]) }

    /**
     * The corrected curve the official app sends after every EQ curve. Only the first 8 bands
     * change; band 9 is sent as 0 dB and band 10 as -12 dB.
     *
     * Ported from OpenSCQ30's `VolumeAdjustments::apply_drc`
     * (lib/src/devices/soundcore/common/structures/volume_adjustments.rs), GPL-3.0-or-later, the
     * same licence as SoundPeter. The recorded writes in data/soundcore/test_vectors.json pin it.
     */
    public fun smooth(tenths: IntArray): IntArray {
        require(tenths.size == BANDS) { "need $BANDS bands, got ${tenths.size}" }
        return drc(tenths, 1, MIN_TENTHS, MAX_TENTHS)
    }

    /**
     * [smooth] for any model: [values] has 8 or more bands with [digits] decimal digits, and the
     * result is clamped to [min]..[max]. Bands 9 and 10 come back as 0 and -120, as in OpenSCQ30.
     */
    public fun drc(values: IntArray, digits: Int, min: Int, max: Int): IntArray {
        require(values.size >= 8) { "need 8 bands or more, got ${values.size}" }
        var scale = 1
        repeat(digits) { scale *= 10 }
        val db = DoubleArray(values.size) { values[it].toDouble() / scale.toDouble() }
        val lows = db[2] * 0.81 * SMALLER
        val highs = db[5] * 0.81 * SMALLER
        return IntArray(values.size) { band ->
            if (band < 8) {
                var sum = 0.0
                for (index in 0 until 8) {
                    sum = when {
                        (band == 1 || band == 3) && index == 2 -> sum - lows
                        (band == 4 || band == 6) && index == 5 -> sum - highs
                        else -> sum + db[index] * COEFFICIENTS[band][index]
                    }
                }
                roundHalfAwayFromZero(sum / 10.0 * scale.toDouble()).coerceIn(min, max)
            } else {
                (if (band == 9) MIN_TENTHS else 0).coerceIn(min, max)
            }
        }
    }

    // Rust's f64::round, which OpenSCQ30 uses: halves go away from zero.
    private fun roundHalfAwayFromZero(x: Double): Int = if (x >= 0) floor(x + 0.5).toInt() else -floor(-x + 0.5).toInt()

    private const val SMALLER = 0.85
    private const val LARGER = 0.95

    private val COEFFICIENTS: Array<DoubleArray> = arrayOf(
        doubleArrayOf(1.26, -0.71 * SMALLER, 0.177, -0.0494, 0.0345, -0.0197, 0.0075, -0.00217),
        doubleArrayOf(-0.71 * SMALLER, 1.73 * LARGER, 0.0, 0.204, -0.068, 0.045, -0.0235, 0.0075),
        doubleArrayOf(0.177, -0.81 * SMALLER, 1.73 * LARGER, -0.81 * SMALLER, 0.208, -0.07, 0.045, -0.0197),
        doubleArrayOf(-0.0494, 0.204, 0.0, 1.73 * LARGER, -0.82 * SMALLER, 0.208, -0.068, 0.0345),
        doubleArrayOf(0.0345, -0.068, 0.208, -0.82 * SMALLER, 1.73 * LARGER, 0.0, 0.204, -0.0494),
        doubleArrayOf(-0.0197, 0.045, -0.07, 0.208, -0.81 * SMALLER, 1.73 * LARGER, -0.81 * SMALLER, 0.177),
        doubleArrayOf(0.0075, -0.0235, 0.045, -0.068, 0.204, 0.0, 1.83 * LARGER, -0.71 * SMALLER),
        doubleArrayOf(-0.00217, 0.0075, -0.0197, 0.0345, -0.0494, 0.177, -0.71 * SMALLER, 1.5),
    )
}
