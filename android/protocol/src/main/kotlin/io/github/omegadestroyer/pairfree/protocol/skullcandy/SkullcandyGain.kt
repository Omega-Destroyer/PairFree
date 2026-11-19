package io.github.omegadestroyer.pairfree.protocol.skullcandy

import kotlin.math.log10
import kotlin.math.pow

/**
 * The gain of the hearing mode in force as Skullcandy's SDK writes it over Airoha RACE (docs/skullcandy/PROTOCOL.md section 14): a slider from 0 to 1 maps to dB through the
 * decimal (power) ratio between the range's ends, and the wire holds the dB times 100 as a big endian short (`GainRange`, convert value 100).
 */
public object SkullcandyGain {
    /** Noise cancelling's range, -20 to 0 dB. */
    public val ANC: ClosedFloatingPointRange<Double> = -20.0..0.0

    /** Transparency's range, -90 to +6 dB. */
    public val TRANSPARENCY: ClosedFloatingPointRange<Double> = -90.0..6.0

    private fun ratio(db: Double): Double = 10.0.pow(db / 10.0)

    /** The dB of [slider] (0 to 1) in [range]. */
    public fun db(slider: Double, range: ClosedFloatingPointRange<Double>): Double {
        val lo = ratio(range.start)
        val hi = ratio(range.endInclusive)
        return 10.0 * log10(slider.coerceIn(0.0, 1.0) * (hi - lo) + lo)
    }

    /** The slider (0 to 1) of [db] in [range]. */
    public fun slider(db: Double, range: ClosedFloatingPointRange<Double>): Double {
        val lo = ratio(range.start)
        val hi = ratio(range.endInclusive)
        return ((ratio(db) - lo) / (hi - lo)).coerceIn(0.0, 1.0)
    }

    /** The two wire bytes of [slider]: dB times 100 as a big endian short. */
    public fun wire(slider: Double, range: ClosedFloatingPointRange<Double>): ByteArray {
        val v = (db(slider, range) * 100.0).toInt().toShort().toInt()
        return byteArrayOf((v shr 8).toByte(), v.toByte())
    }

    /** The slider of two wire bytes, null when there are not two. */
    public fun sliderOf(wire: ByteArray, range: ClosedFloatingPointRange<Double>): Double? {
        if (wire.size != 2) return null
        val v = ((wire[0].toInt() shl 8) or (wire[1].toInt() and 0xFF)).toShort().toInt()
        return slider(v / 100.0, range)
    }
}
