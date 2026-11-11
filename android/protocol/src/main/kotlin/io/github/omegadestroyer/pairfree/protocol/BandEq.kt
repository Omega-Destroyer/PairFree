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
}
