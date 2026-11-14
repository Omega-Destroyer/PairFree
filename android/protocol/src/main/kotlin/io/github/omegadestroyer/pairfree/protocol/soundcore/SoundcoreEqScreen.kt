package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.EqBand
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.EqPreset
import kotlin.math.roundToInt

/**
 * The Sound tab works in whole dB over a model's visible bands; these turn a Soundcore model's
 * EQ into that and back. A custom curve set elsewhere in finer steps shows rounded to the dB.
 */
public object SoundcoreEqScreen {
    /** One dB in the model's units: 10 where values are tenths of a dB, 1 where they are whole dB. */
    public fun unitsPerDb(spec: SoundcoreEqSpec): Int {
        var u = 1
        repeat(spec.digits) { u *= 10 }
        return u
    }

    /**
     * The screen's description of [spec]: the visible bands at 0 dB, the custom slider range in
     * whole dB, and each preset's visible bands in dB. [EqLayout.command] and the base curve are
     * SoundPEATS terms with no meaning here.
     */
    public fun layout(spec: SoundcoreEqSpec): EqLayout {
        val u = unitsPerDb(spec)
        val set = spec.presetSet
        val range = minOf(set.customMax, spec.max, -set.customMin, -spec.min) / u
        return EqLayout(
            command = SoundcoreCommand.EQUALIZER,
            masterGainDb = 0.0,
            userRangeDb = range,
            bands = set.bandHz.map { EqBand(it, 0.0, 0.0) },
            presets = set.presets.map { p ->
                EqPreset(p.name, set.bandHz.mapIndexed { i, hz -> EqBand(hz, 0.0, p.values.getOrElse(i) { 0 }.toDouble() / u) })
            },
        )
    }

    /** The custom curve for whole-dB [gainsDb], one per visible band, or null when the model takes none. */
    public fun custom(spec: SoundcoreEqSpec, gainsDb: List<Int>): SoundcoreEqConfig? {
        val u = unitsPerDb(spec)
        return SoundcoreEqualizer.forCustom(spec, IntArray(spec.presetSet.bandHz.size) { gainsDb.getOrElse(it) { 0 } * u })
    }

    /** The step the screen moves a band by: half a dB where the model's values are tenths of a dB, a whole dB where they are whole. */
    public fun stepDb(spec: SoundcoreEqSpec): Double = if (unitsPerDb(spec) >= 2) 0.5 else 1.0

    /** The custom curve for [gainsDb] that move in half steps, one per visible band, or null when the model takes none. */
    @JvmName("customDb")
    public fun custom(spec: SoundcoreEqSpec, gainsDb: List<Double>): SoundcoreEqConfig? {
        val u = unitsPerDb(spec)
        return SoundcoreEqualizer.forCustom(spec, IntArray(spec.presetSet.bandHz.size) { (gainsDb.getOrElse(it) { 0.0 } * u).roundToInt() })
    }

    /** The visible bands of [config]'s first curve in dB, to the screen's [stepDb]; zeros when no curve is there. */
    public fun gainsDbFine(spec: SoundcoreEqSpec, config: SoundcoreEqConfig): List<Double> {
        val u = unitsPerDb(spec).toDouble()
        val step = stepDb(spec)
        val curve = config.channels.firstOrNull { it != null }
        return spec.presetSet.bandHz.indices.map { i -> curve?.getOrNull(i)?.let { (it / u / step).roundToInt() * step } ?: 0.0 }
    }

    /** The preset [config] is, or null for a custom curve or a preset number the model's set does not list. */
    public fun preset(spec: SoundcoreEqSpec, config: SoundcoreEqConfig): SoundcorePreset? =
        if (config.preset == spec.presetSet.customPreset) null else spec.presetSet.presets.firstOrNull { it.id == config.preset }

    /** The visible bands of [config]'s first curve in whole dB; zeros when no curve is there. */
    public fun gainsDb(spec: SoundcoreEqSpec, config: SoundcoreEqConfig): List<Int> {
        val u = unitsPerDb(spec).toDouble()
        val curve = config.channels.firstOrNull { it != null }
        return spec.presetSet.bandHz.indices.map { i -> curve?.getOrNull(i)?.let { (it / u).roundToInt() } ?: 0 }
    }
}
