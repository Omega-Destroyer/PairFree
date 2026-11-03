package io.github.omegadestroyer.pairfree.protocol

import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * One band as sent: centre frequency, Q and gain. [label] names a band whose brand gives it a name rather than a
 * frequency (the Pixel Buds' low bass to upper treble); the screens show the name, and [hz] then only orders the bands.
 */
public data class EqBand(val hz: Int, val q: Double, val gainDb: Double, val label: String? = null)

/** A complete curve, sent as it is. */
public data class EqPreset(val name: String, val bands: List<EqBand>)

/**
 * The EQ a model expects, as the official app writes it (docs/soundpeats/PROTOCOL.md section 4):
 * every band in every write and the same master gain on each. A user curve is the base
 * curve [bands] plus the user's gain per band; with no user gains it is the base curve
 * alone, which is the model's stock tuning. [presets] are complete curves of their own.
 * On models where the official app appends the user's bands to the classic curve's
 * (`append_after_classic`), [fixedBands] are the classic bands, sent first and as they are.
 *
 * @property command the EQ command, [Wire.EQ_COMMAND_QUALCOMM] or [Wire.EQ_COMMAND_OTHER].
 * @property masterGainDb the master gain sent with every band.
 * @property userRangeDb user gains go from minus this to plus this, in whole dB.
 */
public data class EqLayout(
    val command: Int,
    val masterGainDb: Double,
    val userRangeDb: Int,
    val bands: List<EqBand>,
    val presets: List<EqPreset> = emptyList(),
    val fixedBands: List<EqBand> = emptyList(),
) {
    /** Every write's band count: [fixedBands], then [bands]. */
    public val bandCount: Int get() = fixedBands.size + bands.size

    /** The bands of [preset] that stand where [bands] do: those after [fixedBands], when the preset starts with them. */
    public fun ownBands(preset: EqPreset): List<EqBand> =
        if (fixedBands.isNotEmpty() && preset.bands.take(fixedBands.size) == fixedBands) preset.bands.drop(fixedBands.size) else preset.bands

    /**
     * One payload per band, in order, for [userGainsDb] (whole dB, one per band) on top of
     * the base curve, and a preamp of [preampDb] added to the master gain. Gains are added
     * in tenths of a dB, so the curves' one-decimal values stay exact.
     */
    public fun payloads(userGainsDb: List<Int>, preampDb: Int = 0): List<ByteArray> {
        require(userGainsDb.size == bands.size) { "need ${bands.size} gains, got ${userGainsDb.size}" }
        return encode(
            fixedBands.map { it to tenths(it.gainDb) } + bands.mapIndexed { i, band -> band to tenths(band.gainDb) + userGainsDb[i] * 10 },
            preampDb,
        )
    }

    /** One payload per band of [preset], with a preamp of [preampDb] added to the master gain. */
    public fun payloads(preset: EqPreset, preampDb: Int = 0): List<ByteArray> =
        encode(preset.bands.map { it to tenths(it.gainDb) }, preampDb)

    /** The frames to send, vendor [Wire.VENDOR_EQ], one per band. The official app leaves 50 ms between them. */
    public fun frames(userGainsDb: List<Int>, preampDb: Int = 0): List<ByteArray> = wrap(payloads(userGainsDb, preampDb))

    public fun frames(preset: EqPreset, preampDb: Int = 0): List<ByteArray> = wrap(payloads(preset, preampDb))

    private fun encode(points: List<Pair<EqBand, Int>>, preampDb: Int): List<ByteArray> {
        val master = db(tenths(masterGainDb) + preampDb * 10)
        return points.mapIndexed { i, (band, gainTenths) ->
            val gain = db(gainTenths)
            when (command) {
                Wire.EQ_COMMAND_QUALCOMM -> Eq.band0E01(points.size, i + 1, band.hz.toDouble(), gain, band.q, master)
                else -> Eq.band0E02(points.size, i + 1, band.hz.toDouble(), gain, band.q, master)
            }
        }
    }

    private fun wrap(payloads: List<ByteArray>): List<ByteArray> = payloads.map { Frame.build(command, it, Wire.VENDOR_EQ) }

    private fun tenths(db: Double): Int = (db * 10).roundToInt()

    /**
     * [tenths] of a dB, as a gain [Eq] scales by 60 and truncates to exactly tenths x 6: half a unit
     * further from zero, so 4.1 dB is sent as 246 and not as 4.1 x 60 = 245.99999 (the C30's +3.1 dB band).
     */
    private fun db(tenths: Int): Double = (tenths * 6 + sign(tenths.toDouble()) * 0.5) / 60.0
}
