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

    /** True when a Stock profile (every band at 0) belongs in the list. */
    public val eqHasStock: Boolean get() = false

    /** False when the brand's own app does not put the EQ back each time the channel opens, so PairFree does not either. */
    public val eqRestoresOnConnect: Boolean get() = true

    /** False when nothing has confirmed the frames for this model yet: the screen says so. */
    public val eqConfirmed: Boolean get() = true

    /** True when the earbuds report the custom curve they hold, not only the preset. */
    public val eqReportsCustom: Boolean get() = true
}
