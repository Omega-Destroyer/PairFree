package io.github.omegadestroyer.pairfree.eq

import io.github.omegadestroyer.pairfree.protocol.BandEq
import io.github.omegadestroyer.pairfree.protocol.BandEqReport
import io.github.omegadestroyer.pairfree.protocol.earfun.EarfunEqSpec
import io.github.omegadestroyer.pairfree.protocol.earfun.EarfunSession
import io.github.omegadestroyer.pairfree.protocol.earfun.EarfunValues
import io.github.omegadestroyer.pairfree.protocol.jbl.JblEq
import io.github.omegadestroyer.pairfree.protocol.jbl.JblEqCurve
import io.github.omegadestroyer.pairfree.protocol.jbl.JblEqWire
import io.github.omegadestroyer.pairfree.protocol.jbl.JblFrame
import io.github.omegadestroyer.pairfree.protocol.jbl.JblSession
import io.github.omegadestroyer.pairfree.protocol.sony.SonyEq
import io.github.omegadestroyer.pairfree.protocol.sony.SonyOut
import io.github.omegadestroyer.pairfree.protocol.sony.SonySession
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreEqConfig
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreEqScreen
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreEqSpec
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreEqualizer
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcorePacket
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreState
import kotlin.math.roundToInt

/*
 * The brands whose equalizer has more to it than a preset list and a curve (queued messages, a calibration, a state the earbuds cannot report, a layout
 * that comes from the model's table), still as a [BandEq]: the bands, the range, the presets and the frames, and what the earbuds report. The screen,
 * the profiles and the opener are [BandEqTarget]'s and [EqOpener]'s, the same as for every other brand.
 */

/**
 * Sony earbuds and headphones (docs/sony/PROTOCOL.md section 6): the presets they list, which they apply themselves, and on models that take one a curve
 * of the user's own over the frequency bands they report. Clear Bass is a setting of its own on the Sound tab. They report what they hold.
 *
 * Sony's messages carry sequence numbers and wait for acknowledgements, so the frames are not frames: each is a message payload led by its table number
 * ([encode]), which the connection queues on the Sony link ([decode]).
 */
class SonyBandEq(private val session: SonySession, eq: SonyEq) : BandEq {
    private val presetIds: Map<String, Int> = eq.offered.associate { (id, name) -> name to id }
    private val frequencies: List<Int> = eq.frequencies

    override val eqBandsHz: List<Int> = if (eq.customizable) frequencies else emptyList()
    override val eqPresetOnlyBandsHz: List<Int> = frequencies.ifEmpty { super.eqPresetOnlyBandsHz }
    override val eqRangeDb: Int = eq.center
    override val eqPresetNames: List<String> = eq.offered.map { it.second }
    override val eqReport: BandEqReport? = null

    override fun eqPresetFrames(name: String): List<ByteArray>? = presetIds[name]?.let(session::eqPreset)?.map(::encode)
    override fun eqCustomFrames(gains: List<Int>): List<ByteArray>? = session.eqCurve(gains)?.map(::encode)

    /** What [eq] says the earbuds hold: a preset of theirs, or a curve of the user's own; null while the curve is not the bands the screen shows. */
    fun report(eq: SonyEq): BandEqReport? {
        val gains = eq.gains.takeIf { it.size == frequencies.size && it.isNotEmpty() } ?: return null
        val name = eq.presets.firstOrNull { it.first == eq.preset }?.second
        return BandEqReport(if (eq.holdsCustom || name == null || name !in presetIds) null else name, gains)
    }

    companion object {
        fun encode(out: SonyOut): ByteArray = byteArrayOf(out.table.toByte()) + out.payload

        fun decode(bytes: ByteArray): SonyOut? = if (bytes.size < 2) null else SonyOut(bytes[0].toInt(), bytes.copyOfRange(1, bytes.size))
    }
}

/**
 * JBL headphones and earbuds whose EQ PairFree writes (docs/jbl/PROTOCOL.md section 11): JBL's presets for the model, with their curves, and a curve of the
 * user's own on JBL's ten bands, in the model's range. Most models report the EQ they hold; on the older BES models the screen shows what this phone last
 * set, as JBL's app does. Each write carries the calibration JBL's app works out on its BES models. JBL's app does not put the EQ back when it connects, and
 * nothing has confirmed the writes on a device yet. The frames are the session's, which the connection queues on the JBL link.
 */
class JblBandEq(private val session: JblSession) : BandEq {
    private val spec = requireNotNull(session.model.eq)

    /** JBL's preset names are in capitals ("EXTREME BASS"); the screen writes them as a sentence. */
    private val names: List<String> = spec.presets.map { it.name.lowercase().replaceFirstChar(Char::uppercase) }

    override val eqBandsHz: List<Int> = JblEq.BANDS_HZ
    override val eqRangeDb: Int = spec.rangeDb
    override val eqPresetNames: List<String> = names
    override val eqPresetGains: Map<String, List<Double>> = spec.presets.mapIndexedNotNull { i, p ->
        p.bands.takeIf { it.size == JblEq.BANDS_HZ.size }?.let { names[i] to it.map { b -> b.gainDb.toDouble() } }
    }.toMap()
    override val eqRestoresOnConnect: Boolean = false
    override val eqConfirmed: Boolean = false
    override val eqReportsState: Boolean = spec.reports
    override val eqReport: BandEqReport? = null

    override fun eqPresetFrames(name: String): List<ByteArray>? {
        val preset = spec.presets.getOrNull(names.indexOf(name)) ?: return null
        return session.eqWrite(preset, List(JblEq.BANDS_HZ.size) { 0f })?.map(JblFrame::bytes)
    }

    override fun eqCustomFrames(gains: List<Int>): List<ByteArray>? = session.eqWrite(null, gains.map { it.toFloat() })?.map(JblFrame::bytes)

    /** What [eq] says the earbuds hold: their custom curve, or the preset whose category they report; null for neither. */
    fun report(eq: JblEqCurve): BandEqReport? {
        if (eq.category == JblEqWire.CUSTOM) {
            if (eq.bands.size != JblEq.BANDS_HZ.size) return null
            return BandEqReport(null, eq.bands.map { it.gainDb.roundToInt() })
        }
        val i = spec.presets.indexOfFirst { it.category == eq.category }.takeIf { it >= 0 } ?: return null
        return BandEqReport(names[i], null)
    }
}

/**
 * EarFun earbuds and headphones (docs/earfun/PROTOCOL.md section 6): a curve of the user's own over the model's bands, and EarFun's preset curves, all
 * written band by band as EarFun's app writes them. The earbuds cannot report their EQ, so the screen shows what this phone last sent; nothing goes back
 * on connect, as EarFun's app sends the EQ only when the user changes it.
 *
 * EarFun's sliders have 21 positions: on the 10-band models half-dB steps up to 6 dB, so the screen's whole dB from -6 to 6 land on them exactly; on the
 * 6-band models whole dB from -10 to 10. The frames are the session's, which the connection queues on the EarFun link.
 */
class EarfunBandEq(private val session: EarfunSession, spec: EarfunEqSpec) : BandEq {
    private val presetPositions: Map<String, List<Int>> = EarfunValues.PRESETS[spec.presets].orEmpty().toMap()

    override val eqBandsHz: List<Int> = spec.hz.map { it.roundToInt() }
    override val eqRangeDb: Int = if (spec.positionGain) EarfunSession.POSITIONS else WHOLE_DB_RANGE
    override val eqPresetNames: List<String> = presetPositions.keys.toList()
    override val eqPresetGains: Map<String, List<Double>> = presetPositions.mapValues { (_, positions) -> positions.map(session::gainDb) }
    override val eqHasStock: Boolean = true
    override val eqRestoresOnConnect: Boolean = false
    override val eqReportsState: Boolean = false
    override val eqReport: BandEqReport? = null

    override fun eqPresetFrames(name: String): List<ByteArray>? = presetPositions[name]?.let(session::eqFrames)

    override fun eqCustomFrames(gains: List<Int>): List<ByteArray>? = session.positions(gains.map { it.toDouble() })?.let(session::eqFrames)

    private companion object {
        /** The 10-band models' whole dB, every one a slider position. */
        const val WHOLE_DB_RANGE = 6
    }
}

/**
 * Soundcore earbuds (docs/soundcore/PROTOCOL.md section 8): a preset sends its number and curve, a custom curve its bands, and HearID models pass on the
 * HearID block from [state], the latest state the earbuds sent. They report their EQ with every state, so the screen follows what they hold. Models whose
 * preset set has no custom preset (the P20i) take their presets only; a model's EQ is confirmed when a recording of the Soundcore app or OpenSCQ30's tests
 * pin it, else it is from OpenSCQ30's code alone, and the Sound tab says so.
 */
class SoundcoreBandEq(private val spec: SoundcoreEqSpec, private val state: () -> SoundcoreState?, private val checksum: Boolean) : BandEq {
    private val set = spec.presetSet
    private val layout = SoundcoreEqScreen.layout(spec)

    override val eqBandsHz: List<Int> = if (set.customPreset != null) set.bandHz else emptyList()
    override val eqPresetOnlyBandsHz: List<Int> = set.bandHz
    override val eqRangeDb: Int = layout.userRangeDb
    override val eqPresetNames: List<String> = layout.presets.map { it.name }
    override val eqPresetGains: Map<String, List<Double>> = layout.presets.associate { p -> p.name to p.bands.map { it.gainDb } }
    override val eqConfirmed: Boolean = spec.checked != null
    override val eqReport: BandEqReport? = null

    override fun eqPresetFrames(name: String): List<ByteArray>? =
        set.presets.firstOrNull { it.name == name }?.let { SoundcoreEqualizer.forPreset(spec, it) }?.let(::write)

    override fun eqCustomFrames(gains: List<Int>): List<ByteArray>? = SoundcoreEqScreen.custom(spec, gains)?.let(::write)

    override val eqGainStepDb: Double = SoundcoreEqScreen.stepDb(spec)

    override fun eqCustomFramesDb(gains: List<Double>, preampDb: Int): List<ByteArray>? = SoundcoreEqScreen.custom(spec, gains)?.let(::write)

    private fun write(config: SoundcoreEqConfig): List<ByteArray> =
        SoundcoreEqualizer.write(spec, state(), config).map { SoundcorePacket.build(it.command, it.body, checksum) }

    /** What [state] says the earbuds hold: the preset by its name, or a custom curve, with the gains; null when it holds no EQ. */
    fun report(state: SoundcoreState): BandEqReport? {
        val config = SoundcoreEqualizer.read(spec, state) ?: return null
        return BandEqReport(SoundcoreEqScreen.preset(spec, config)?.name, SoundcoreEqScreen.gainsDb(spec, config), SoundcoreEqScreen.gainsDbFine(spec, config))
    }
}
