package io.github.omegadestroyer.pairfree.eq

import io.github.omegadestroyer.pairfree.protocol.BandEq
import io.github.omegadestroyer.pairfree.protocol.BandEqReport
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingEq
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingFrame
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingSession
import io.github.omegadestroyer.pairfree.protocol.onemore.OneMoreEq
import io.github.omegadestroyer.pairfree.protocol.onemore.OneMoreFrame
import io.github.omegadestroyer.pairfree.protocol.onemore.OneMoreSession
import io.github.omegadestroyer.pairfree.protocol.pixel.PixelSession
import io.github.omegadestroyer.pairfree.protocol.pixel.PixelValues
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyControl
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyEq
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The brands that put their equalizer through [BandEqTarget] (docs/DESIGN.md, Sound): each class says only what its earbuds have, as a [BandEq]: the bands
 * (any number: three filters, five named bands, whatever a QCY model reports), the range, the presets and the frames. What the earbuds report comes
 * through `report`, which turns a brand's own answer into a [BandEqReport]; the screen, the profiles and the opener are the same for all of them.
 */

/**
 * QCY earbuds and headphones (docs/qcy/PROTOCOL.md section 7): a curve of the user's own over the bands the earbuds reported, through the parametric EQ where
 * they answered for it, else the EQ characteristic. QCY's presets are curves its servers send, which PairFree does not have, so there are none; Stock is
 * every band at 0. The frames are the session's GATT messages, which the connection queues on the QCY link.
 */
class QcyBandEq(private val session: QcyControl, eq: QcyEq) : BandEq {
    override val eqBandsHz: List<Int> = eq.freqs
    override val eqRangeDb: Int = eq.maxDb
    override val eqPresetNames: List<String> = emptyList()
    override val eqHasStock: Boolean = true
    override val eqReport: BandEqReport? = null

    override fun eqPresetFrames(name: String): List<ByteArray>? = null
    override fun eqCustomFrames(gains: List<Int>): List<ByteArray>? = session.eqCustom(gains)

    /** What [eq] says the earbuds hold; null while they hold one of QCY's presets, whose curve is unknown, or another band set. */
    fun report(eq: QcyEq): BandEqReport? = eq.gains.takeIf { eq.custom && eq.freqs == eqBandsHz }?.let { BandEqReport(null, it) }
}

/**
 * 1MORE earbuds and headphones (docs/1more/PROTOCOL.md section 4): the preset sounds the model lists, which the earbuds apply themselves, and on the models
 * with one the custom EQ, from -3 to 3 dB a band. The frames carry sequence numbers; the connection queues them on the 1MORE link.
 */
class OneMoreBandEq(private val session: OneMoreSession) : BandEq {
    private val keys: List<String> = session.model?.eqPresets.orEmpty()
    override val eqBandsHz: List<Int> = session.model?.eqBands?.hz.orEmpty()
    override val eqRangeDb: Int = OneMoreSession.EQ_RANGE
    override val eqPresetNames: List<String> = keys.map { NAMES[it] ?: it }
    override val eqPresetOnlyBandsHz: List<Int> = DEFAULT_HZ
    override val eqReport: BandEqReport? = null

    override fun eqPresetFrames(name: String): List<ByteArray>? =
        keys.firstOrNull { (NAMES[it] ?: it) == name }?.let { session.eqPreset(it) }?.map(OneMoreFrame::bytes)

    override fun eqCustomFrames(gains: List<Int>): List<ByteArray>? = session.eqCustom(gains)?.map(OneMoreFrame::bytes)

    /** What [eq] says the earbuds hold: their custom curve, or the preset they apply. */
    fun report(eq: OneMoreEq): BandEqReport? {
        if (eq.custom) return BandEqReport(null, eq.gains ?: return null)
        val name = eq.preset ?: return null
        return BandEqReport(NAMES[name] ?: name, null)
    }

    private companion object {
        /** The preset sounds as 1MORE's app names them in English; "loud" is its Voice Enhancement. */
        val NAMES = mapOf(
            "studio" to "Studio", "bass_reducer" to "Bass reducer", "bass_booster" to "Bass booster", "acoustic" to "Acoustic",
            "luca" to "Sound by Luca", "classical" to "Classical", "podcast" to "Podcast", "deep" to "Deep", "electronic" to "Electronic",
            "hiphop" to "Hip-hop", "lounge" to "Lounge", "pop" to "Pop", "loud" to "Voice enhancement",
        )

        /** For the profile lists of a model with presets only: 1MORE's usual bands. */
        val DEFAULT_HZ = listOf(69, 282, 682, 1234, 2013, 3107, 4641, 6794, 9815, 14053)
    }
}

/**
 * Nothing and CMF earbuds and headphones (docs/nothing/PROTOCOL.md section 5): the presets the model has, which the earbuds apply themselves, and where
 * the model has one the custom EQ Nothing X calls simple: three filters (bass, mid and treble) from -6 to 6 dB. The frames carry sequence numbers; the
 * connection queues them on the Nothing link.
 */
class NothingBandEq(private val session: NothingSession) : BandEq {
    override val eqBandsHz: List<Int> = session.simpleEqFilters?.let { listOf(it.lowHz, it.peakHz, it.highHz).map(Double::roundToInt) }.orEmpty()
    override val eqRangeDb: Int = NothingSession.SIMPLE_EQ_RANGE
    override val eqPresetNames: List<String> = session.eqPresets.map { NAMES.getValue(it) }
    override val eqPresetOnlyBandsHz: List<Int> = listOf(DEFAULT_LOW, DEFAULT_PEAK, DEFAULT_HIGH)
    override val eqReport: BandEqReport? = null

    override fun eqPresetFrames(name: String): List<ByteArray>? =
        NAMES.entries.firstOrNull { it.value == name }?.key?.let { session.eqPreset(it) }?.map(NothingFrame::bytes)

    override fun eqCustomFrames(gains: List<Int>): List<ByteArray>? = session.eqCustom(gains)?.map(NothingFrame::bytes)

    /** What [eq] says the earbuds hold: their custom curve, or the preset they apply. */
    fun report(eq: NothingEq): BandEqReport? {
        if (eq.custom) return BandEqReport(null, eq.gains ?: return null)
        return BandEqReport(NAMES[eq.preset ?: return null] ?: return null, null)
    }

    private companion object {
        /** The presets as Nothing X names them. */
        val NAMES = linkedMapOf("balanced" to "Balanced", "bass" to "More bass", "treble" to "More treble", "voice" to "Voice")

        // Nothing X's defaults (IOTProductDevice.FREQ_*), for a model whose table has none.
        const val DEFAULT_LOW = 140
        const val DEFAULT_PEAK = 980
        const val DEFAULT_HIGH = 6900
    }
}

/**
 * The Pixel Buds Pro, Pro 2 and 2a (docs/pixel/PROTOCOL.md section 6): five bands, low bass to upper treble, from -6 to 6 dB, and Google's six presets,
 * which the app writes as curves like any other; the curve the user saved on the earbuds shows as Last saved where they hold one. Google's sliders move in half
 * steps; the screen's in whole ones, so a curve set in Google's app shows rounded until it is changed here. The frames are the session's messages as bytes.
 */
class PixelBandEq(private val session: PixelSession) : BandEq {
    private val presets: List<Pair<String, List<Float>>> =
        PixelValues.EQ_PRESETS.map { it.name to it.gains } + listOfNotNull(session.savedEq?.let { LAST_SAVED to it })

    /** The bands are named, not given in Hz: the number only orders them. */
    override val eqBandsHz: List<Int> = PixelValues.EQ_BANDS.indices.map { it + 1 }
    override val eqBandLabels: List<String> = PixelValues.EQ_BANDS
    override val eqRangeDb: Int = PixelValues.EQ_RANGE_DB
    override val eqPresetNames: List<String> = presets.map { it.first }
    override val eqPresetGains: Map<String, List<Double>> = presets.associate { (name, gains) -> name to gains.map(Float::toDouble) }
    override val eqReport: BandEqReport? = null

    override fun eqPresetFrames(name: String): List<ByteArray>? =
        presets.firstOrNull { it.first == name }?.let { (_, gains) -> session.eqWrite(gains)?.map { it.bytes } }

    override fun eqCustomFrames(gains: List<Int>): List<ByteArray>? = session.eqWrite(gains.map { it.toFloat() })?.map { it.bytes }

    /** What [gains] say the earbuds hold: a preset's name where one matches, and the gains in whole dB. */
    fun report(gains: List<Float>): BandEqReport {
        val match = presets.firstOrNull { (_, p) -> p.zip(gains).all { (a, b) -> abs(a - b) < SAME_DB } }
        return BandEqReport(match?.first, gains.map { it.roundToInt() })
    }

    /** True when the earbuds' saved curve is among the presets as Last saved. */
    val hasSaved: Boolean = session.savedEq != null

    private companion object {
        /** Google's name for the curve the user saved on the earbuds. */
        const val LAST_SAVED = "Last saved"
        const val SAME_DB = 0.01f
    }
}
