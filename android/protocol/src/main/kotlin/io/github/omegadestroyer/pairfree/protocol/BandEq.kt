package io.github.omegadestroyer.pairfree.protocol

/** What a brand's earbuds hold of their equalizer: the preset they apply, or the gains (whole dB, one per band) of the custom curve. */
public data class BandEqReport(val preset: String?, val gains: List<Int>?)

/**
 * A brand-neutral graphic equalizer, for any earbuds that offer presets they apply themselves and/or a custom curve on named bands.
 * A session that implements it gets the Sound tab's graph with draggable bands, the preset list and the saved profiles, with no
 * brand-specific screen code: the bands drawn are [eqBandsHz], so each model shows what it supports.
 */
public interface BandEq {
    /** The custom curve's bands in Hz, low to high; empty when the earbuds take presets only. */
    public val eqBandsHz: List<Int>

    /** The user's gain range: from minus this to plus this, in whole dB. */
    public val eqRangeDb: Int

    /** The presets the earbuds apply themselves, by the names the screen shows. */
    public val eqPresetNames: List<String>

    /** The frames that select the preset [name], or null when there is no such preset. */
    public fun eqPresetFrames(name: String): List<ByteArray>?

    /** The frames that put a custom curve of [gains] (one per band) on the earbuds, or null when they cannot take one. */
    public fun eqCustomFrames(gains: List<Int>): List<ByteArray>?

    /** What the earbuds hold now, or null until they have said. */
    public val eqReport: BandEqReport?

    /** The frames of preset [name] with a preamp of [preampDb] (zero or less) on the master gain; for earbuds that take one ([eqHasPreamp]). */
    public fun eqPresetFrames(name: String, preampDb: Int): List<ByteArray>? = eqPresetFrames(name)

    /** The frames of a custom curve of [gains] with a preamp of [preampDb] on the master gain. */
    public fun eqCustomFrames(gains: List<Int>, preampDb: Int): List<ByteArray>? = eqCustomFrames(gains)

    /*
     * Everything below has a default, so a brand states only where its earbuds differ. The band count is whatever [eqBandsHz] holds (three for Nothing's
     * filters, five for the Pixel Buds, nine for Galaxy Buds, ten for 1MORE and JBL, and any number a QCY or Sony model reports): nothing here assumes one.
     */

    /** A key for each band's name where the brand names its bands instead of giving frequencies (the Pixel Buds' low bass to upper treble); empty otherwise. */
    public val eqBandLabels: List<String> get() = emptyList()

    /**
     * The curve of each preset, in dB per band, where the earbuds or their app say what it is (Google's presets, Galaxy Buds4's reported curves); the screen draws
     * these. Empty where the earbuds apply a preset themselves and its curve is not known.
     */
    public val eqPresetGains: Map<String, List<Double>> get() = emptyMap()

    /** The bands the profile lists use on a model that takes presets only (nothing is drawn on them). */
    public val eqPresetOnlyBandsHz: List<Int> get() = listOf(62, 250, 1000, 4000, 8000, 16000)

    /**
     * Earbuds whose curve is a base curve of the model plus the user's gains, written whole each time (SoundPEATS' and the Capsule's): each band's
     * frequency, Q and base gain. Empty for earbuds whose bands start flat; then [eqBandsHz] gives the bands.
     */
    public val eqBaseBands: List<EqBand> get() = emptyList()

    /** Bands sent first in every write, as they are, ahead of the user's (models that append to a classic curve). */
    public val eqFixedBands: List<EqBand> get() = emptyList()

    /** The master gain every band is sent with, in dB, before the preamp. */
    public val eqMasterGainDb: Double get() = 0.0

    /** True when the screen offers a preamp (a step from 0 down) that the frames with a preamp carry. */
    public val eqHasPreamp: Boolean get() = false

    /** Presets that are complete curves of their own (every band, fixed ones too), where they do not sit on the same bands as the user's curve; wins over [eqPresetGains]. */
    public val eqPresetBands: Map<String, List<EqBand>> get() = emptyMap()

    /** True when a Stock profile (every band at 0) belongs in the list. */
    public val eqHasStock: Boolean get() = false

    /** False when the brand's own app does not put the EQ back each time the channel opens, so PairFree does not either. */
    public val eqRestoresOnConnect: Boolean get() = true

    /** False when nothing has confirmed the frames for this model yet: the screen says so. */
    public val eqConfirmed: Boolean get() = true

    /** True when the earbuds report the custom curve they hold, not only the preset. */
    public val eqReportsCustom: Boolean get() = true

    /** False when the earbuds cannot report their EQ at all (EarFun's): the screen then shows what this phone last sent, and nothing is put back on connect unless [eqRestoresOnConnect]. */
    public val eqReportsState: Boolean get() = true
}

/**
 * The Sound tab's description of [this] equalizer: the bands (by the frequencies it gives, or the base curve's with their Q), the presets (with their curves
 * where known) and the fixed bands, with the master gain and range. A model with presets only has its profile lists drawn over [BandEq.eqPresetOnlyBandsHz].
 */
public fun BandEq.toLayout(): EqLayout {
    val hz = eqBandsHz.ifEmpty { eqPresetOnlyBandsHz }
    val bands = eqBaseBands.ifEmpty { hz.mapIndexed { i, f -> EqBand(f, 0.0, 0.0, eqBandLabels.getOrNull(i)) } }
    return EqLayout(
        command = 0,
        masterGainDb = eqMasterGainDb,
        userRangeDb = eqRangeDb,
        bands = bands,
        presets = eqPresetNames.map { name ->
            val curve = eqPresetGains[name]?.takeIf { it.size == bands.size }
            EqPreset(name, eqPresetBands[name] ?: bands.mapIndexed { i, b -> b.copy(gainDb = curve?.get(i) ?: 0.0) })
        },
        fixedBands = eqFixedBands,
    )
}
