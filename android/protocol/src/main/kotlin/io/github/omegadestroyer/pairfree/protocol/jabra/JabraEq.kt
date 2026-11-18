package io.github.omegadestroyer.pairfree.protocol.jabra

/**
 * What the Sound tab needs of Jabra's music equalizer (docs/jabra/PROTOCOL.md, Music EQ and presets): the bands the earbuds describe, the gains they hold, the
 * six presets of the Sound+ app, and the frames that set a curve. Each band has its own maximum, so the range is the smallest of them.
 */
public interface JabraEqSource {
    /** True once the earbuds have described their bands. */
    public val eqKnown: Boolean

    /** The bands' centre frequencies in Hz, low to high. */
    public val eqBandsHz: List<Int>

    /** The gain of each band as the earbuds hold it, in dB. */
    public val eqGains: List<Double>?

    /** The most gain every band takes, either way, in dB. */
    public val eqMaxDb: Double

    /** The Sound+ app's presets as gains in dB for each band. */
    public val eqPresets: List<Pair<String, List<Double>>>

    /** The frames that set [gains] (dB per band) on the earbuds, or null when they do not fit. */
    public fun eqCurve(gains: List<Double>): List<ByteArray>?
}
