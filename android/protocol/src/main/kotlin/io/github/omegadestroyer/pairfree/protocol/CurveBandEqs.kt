package io.github.omegadestroyer.pairfree.protocol

import io.github.omegadestroyer.pairfree.protocol.qcy.Capsule

/**
 * A [BandEq] for earbuds that take a whole parametric curve in every write: a base curve of the model (each band's frequency, Q and gain), a master gain
 * sent with every band, bands that are fixed ahead of the user's, complete presets of their own, and a preamp on the master gain. The earbuds cannot report
 * the curve, so the screen shows what this phone last sent and sends it again after each connect.
 *
 * A brand drops in by giving the model's [layout] and saying how a complete curve reaches the earbuds ([writeCurve]); the screen, the profiles, the
 * preamp and the opener come with it. SoundPEATS and the Capsule are the two so far.
 */
public abstract class CurveBandEq(protected val layout: EqLayout) : BandEq {
    public override val eqBandsHz: List<Int> = layout.bands.map { it.hz }
    public override val eqBaseBands: List<EqBand> = layout.bands
    public override val eqFixedBands: List<EqBand> = layout.fixedBands
    public override val eqMasterGainDb: Double = layout.masterGainDb
    public override val eqRangeDb: Int = layout.userRangeDb
    public override val eqPresetNames: List<String> = layout.presets.map { it.name }
    public override val eqPresetBands: Map<String, List<EqBand>> = layout.presets.associate { it.name to it.bands }
    public override val eqHasStock: Boolean = true
    public override val eqReportsState: Boolean = false
    public override val eqReport: BandEqReport? = null

    /** The frames that put the complete [curve] on the earbuds, with [masterGainDb] on every band (the preamp already added). */
    protected abstract fun writeCurve(masterGainDb: Double, curve: List<EqBand>): List<ByteArray>

    public override fun eqPresetFrames(name: String): List<ByteArray>? = eqPresetFrames(name, 0)
    public override fun eqCustomFrames(gains: List<Int>): List<ByteArray>? = eqCustomFrames(gains, 0)

    public override fun eqPresetFrames(name: String, preampDb: Int): List<ByteArray>? =
        layout.presets.firstOrNull { it.name == name }?.let { writeCurve(layout.masterGainDb + preampDb, it.bands) }

    public override fun eqCustomFrames(gains: List<Int>, preampDb: Int): List<ByteArray>? =
        if (gains.size != layout.bands.size) null else writeCurve(layout.masterGainDb + preampDb, layout.curve(gains))

    public override fun eqCustomFramesDb(gains: List<Double>, preampDb: Int): List<ByteArray>? =
        if (gains.size != layout.bands.size) null else writeCurve(layout.masterGainDb + preampDb, layout.curve(gains))
}

/** SoundPEATS: every band in every write, on top of the model's base curve (docs/soundpeats/PROTOCOL.md section 4), with the preamp the models offer. */
public class SoundpeatsBandEq(layout: EqLayout) : CurveBandEq(layout) {
    public override val eqHasPreamp: Boolean = true

    /** The band frames carry the gain in hundredths of a dB (scaled by 60 on the wire), so half steps go through. */
    public override val eqGainStepDb: Double = 0.5

    // The layout encodes its own frames (byte for byte as data/soundpeats/test_vectors.json pins them), so the model's layout writes, not a rebuilt curve.
    public override fun eqPresetFrames(name: String, preampDb: Int): List<ByteArray>? =
        layout.presets.firstOrNull { it.name == name }?.let { layout.frames(it, preampDb) }

    public override fun eqCustomFrames(gains: List<Int>, preampDb: Int): List<ByteArray>? =
        if (gains.size != layout.bands.size) null else layout.frames(gains, preampDb)

    public override fun eqCustomFramesDb(gains: List<Double>, preampDb: Int): List<ByteArray>? =
        if (gains.size != layout.bands.size) null else layout.frames(gains, preampDb)

    public override fun writeCurve(masterGainDb: Double, curve: List<EqBand>): List<ByteArray> = emptyList()
}

/**
 * SOUNDPEATS' QCY-built Capsule3 Pro and Pro+ (docs/soundpeats/PROTOCOL.md section 10.5): the model's curve from data/soundpeats/devices.json, as on
 * SOUNDPEATS' other models, but sent whole in one write on QCY's EQ characteristic, which the connection queues on the QCY link. The earbuds' EQ read is
 * not used, as PeatsAudio does not use it. There is no preamp.
 */
public class CapsuleBandEq(layout: EqLayout) : CurveBandEq(layout) {
    public override fun writeCurve(masterGainDb: Double, curve: List<EqBand>): List<ByteArray> = listOf(Capsule.eqMessage(masterGainDb, curve))
}
