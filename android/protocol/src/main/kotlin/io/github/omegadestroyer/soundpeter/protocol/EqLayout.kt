package io.github.omegadestroyer.soundpeter.protocol

import kotlin.math.roundToInt

/** One band as sent: centre frequency, Q and gain. */
public data class EqBand(val hz: Int, val q: Double, val gainDb: Double)

/** A complete curve, sent as it is. */
public data class EqPreset(val name: String, val bands: List<EqBand>)

/**
 * The EQ a model expects, as the official app writes it (docs/soundpeats/PROTOCOL.md section 4):
 * every band in every write and the same master gain on each. A user curve is the base
 * curve [bands] plus the user's gain per band; with no user gains it is the base curve
 * alone, which is the model's stock tuning. [presets] are complete curves of their own.
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
) {
    /**
     * One payload per band, in order, for [userGainsDb] (whole dB, one per band) on top of
     * the base curve, and a preamp of [preampDb] added to the master gain. Gains are added
     * in tenths of a dB, so the curves' one-decimal values stay exact.
     */
    public fun payloads(userGainsDb: List<Int>, preampDb: Int = 0): List<ByteArray> {
        require(userGainsDb.size == bands.size) { "need ${bands.size} gains, got ${userGainsDb.size}" }
        return encode(bands.mapIndexed { i, band -> band to tenths(band.gainDb) + userGainsDb[i] * 10 }, preampDb)
    }

    /** One payload per band of [preset], with a preamp of [preampDb] added to the master gain. */
    public fun payloads(preset: EqPreset, preampDb: Int = 0): List<ByteArray> =
        encode(preset.bands.map { it to tenths(it.gainDb) }, preampDb)

    /** The frames to send, vendor [Wire.VENDOR_EQ], one per band. The official app leaves 50 ms between them. */
    public fun frames(userGainsDb: List<Int>, preampDb: Int = 0): List<ByteArray> = wrap(payloads(userGainsDb, preampDb))

    public fun frames(preset: EqPreset, preampDb: Int = 0): List<ByteArray> = wrap(payloads(preset, preampDb))

    private fun encode(points: List<Pair<EqBand, Int>>, preampDb: Int): List<ByteArray> {
        val master = (tenths(masterGainDb) + preampDb * 10) / 10.0
        return points.mapIndexed { i, (band, gainTenths) ->
            val gain = gainTenths / 10.0
            when (command) {
                Wire.EQ_COMMAND_QUALCOMM -> Eq.band0E01(points.size, i + 1, band.hz.toDouble(), gain, band.q, master)
                else -> Eq.band0E02(points.size, i + 1, band.hz.toDouble(), gain, band.q, master)
            }
        }
    }

    private fun wrap(payloads: List<ByteArray>): List<ByteArray> = payloads.map { Frame.build(command, it, Wire.VENDOR_EQ) }

    private fun tenths(db: Double): Int = (db * 10).roundToInt()
}
