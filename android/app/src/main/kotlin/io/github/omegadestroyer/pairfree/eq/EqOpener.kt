package io.github.omegadestroyer.pairfree.eq

import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.EarbudsConnection
import io.github.omegadestroyer.pairfree.bluetooth.LinkStatus
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.protocol.CapsuleBandEq
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.SoundpeatsBandEq
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungModels
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreEqSpec
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreLayout
import io.github.omegadestroyer.pairfree.ui.modelOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** What earbuds that report their EQ hold now: the preset's profile id (null for a curve of the user's own) and the gains in whole dB. */
class EqReported(val profileId: String?, val gains: List<Int>, val resendCustom: Boolean = false)

/**
 * One brand's equalizer, ready for the Sound tab: its [layout] and [target], a [key] that says when the screen must be opened again
 * (the earbuds listed other bands, a saved curve arrived), and [reported], which reads what the earbuds hold now from the connection.
 * Every brand is one of these, built in [EqOpener.source]; nothing else in the opener is specific to a brand.
 */
class EqSource(
    val key: Any,
    val layout: EqLayout?,
    val target: EqTarget,
    /** The gains the screen shows now, for brands that report a preset only and keep the curve as it is. */
    val reported: (current: List<Int>) -> EqReported? = { null },
)

/**
 * Opens the equalizer for the connected earbuds of every brand: asks [source] for the brand's [EqSource] from the model and the
 * session, hands its layout and target to the [Equalizer], and follows what earbuds that report their EQ say. It lives in the Hub, so the
 * Sound tab and the home screen widget share it (docs/DESIGN.md, Widget).
 */
class EqOpener(
    private val connection: EarbudsConnection,
    private val equalizer: Equalizer,
    private val scope: CoroutineScope,
) {
    /** The source whose screen is open, and the earbuds it was opened for; null while none is. */
    private var open: Pair<String, EqSource>? = null

    /** Galaxy Buds whose custom curve went out again on this connection, so it goes once. */
    private var samsungCurveResent: String? = null

    /**
     * Opens the Sound tab's EQ for [device]: SoundPEATS models from their table, Soundcore models
     * whose EQ write a recording or OpenSCQ30's tests confirm (docs/soundcore/PROTOCOL.md section 8), and every other brand through
     * its [EqSource].
     */
    fun open(device: Paired, model: Model?) {
        val source = source(device, model)
        val current = open
        if (source == null) {
            open = null
            equalizer.open(device.address, null)
            return
        }
        if (current != null && current.first == device.address && current.second.key == source.key && equalizer.state.value != null) {
            equalizer.open(device.address, model, source.layout, source.target)
            return
        }
        // Another band set, or a curve that arrived later: the screen starts again over the new layout.
        if (current != null && current.first == device.address) equalizer.open(device.address, null)
        open = device.address to source
        equalizer.open(device.address, model, source.layout, source.target)
        report()
    }

    /** Follows what the earbuds hold, for the open screen. */
    private fun report() {
        val (address, source) = open ?: return
        val state = equalizer.state.value ?: return
        val reported = source.reported(state.setting.gains) ?: return
        equalizer.reported(reported.profileId, reported.gains)
        if (reported.resendCustom && samsungCurveResent != address) {
            // The earbuds hold a custom curve but forget its values when they power off: it goes out again, once per connection.
            samsungCurveResent = address
            equalizer.resendCustom(address)
        }
    }

    /** The brand's EQ for [device], or null when the model or the earbuds' answers so far give none. */
    private fun source(device: Paired, model: Model?): EqSource? {
        // Any session that implements the brand-neutral graphic equalizer gets the Sound tab's graph, presets and profiles here.
        connection.bandEq?.let { band ->
            if (band.eqBandsHz.isEmpty() && band.eqPresetNames.isEmpty()) return null
            val target = BandEqTarget(band)
            return EqSource(band, target.layout, target) { current -> connection.bandEqReport.value?.let { target.reportedAs(it, current) } }
        }
        return when (device.brand) {
            // Legacy Melomania's five gain controls have no established frequency grid. They use Sound-tab rows.
            Brand.CAMBRIDGE -> null
            Brand.PIXEL -> pixel()
            Brand.JBL -> jbl()
            Brand.EARFUN -> earfun()
            Brand.ONEMORE -> oneMore()
            Brand.QCY -> qcy(device, model)
            Brand.NOTHING -> nothing()
            Brand.SONY -> sony(device)
            Brand.SAMSUNG -> samsung(device)
            // Every model with an EQ in data/soundcore/devices.json (owner, 2026-10-29); the screen says when its write is not confirmed.
            Brand.SOUNDCORE -> soundcore(device)
            // SoundPEATS, and the brands whose model has no EQ of its own: the model's table, if any.
            else -> model?.eq?.let { layout -> BandEqTarget(SoundpeatsBandEq(layout)).let { EqSource("plain", it.layout, it) } }
        }
    }

    /** Pixel Buds Pro, Pro 2 and 2a once their EQ read answered; the 2020 model and the A-Series have bass boost alone, a row on the Sound tab. */
    private fun pixel(): EqSource? {
        val session = connection.pixelSession ?: return null
        if (!session.hasEq) return null
        val band = PixelBandEq(session)
        val target = BandEqTarget(band)
        // Open again when the saved curve arrived after the EQ: Last saved joins the presets.
        return EqSource(session to band.hasSaved, target.layout, target) { current -> connection.pixelEq.value?.let { band.report(it) }?.let { target.reportedAs(it, current) } }
    }

    /** JBL models whose EQ PairFree writes, once the ones that report their EQ have reported it. */
    private fun jbl(): EqSource? {
        val session = connection.jblSession ?: return null
        val spec = session.model.eq
        if (spec?.written != true || (spec.reports && session.eq == null)) return null
        val band = JblBandEq(session)
        val target = BandEqTarget(band)
        return EqSource(session, target.layout, target) { current -> connection.jblEq.value?.let { band.report(it) }?.let { target.reportedAs(it, current) } }
    }

    /** EarFun models' EQ is in their table and cannot be read: the screen shows what this phone last sent. */
    private fun earfun(): EqSource? {
        val session = connection.earfunSession ?: return null
        val spec = session.model?.eq ?: return null
        val target = BandEqTarget(EarfunBandEq(session, spec))
        return EqSource(session, target.layout, target)
    }

    /** 1MORE models' EQ is in their table; it opens at once and follows what the earbuds report. */
    private fun oneMore(): EqSource? {
        val session = connection.oneMoreSession ?: return null
        val m = session.model ?: return null
        if (m.eqPresets.isEmpty() && m.eqBands == null) return null
        val band = OneMoreBandEq(session)
        val target = BandEqTarget(band)
        return EqSource(session, target.layout, target) { current -> connection.oneMoreEq.value?.let { band.report(it) }?.let { target.reportedAs(it, current) } }
    }

    /** QCY earbuds report their EQ after they connect, and it opens over the bands they report. SOUNDPEATS' QCY-built models use SOUNDPEATS' own curve. */
    private fun qcy(device: Paired, model: Model?): EqSource? {
        if (device.soldAs == Brand.SOUNDPEATS) return model?.eq?.let { layout -> BandEqTarget(CapsuleBandEq(layout)).let { EqSource("capsule", it.layout, it) } }
        val session = connection.qcySession ?: return null
        val eq = connection.qcyEq.value ?: return null
        val band = QcyBandEq(session, eq)
        val target = BandEqTarget(band)
        return EqSource(session to eq.freqs, target.layout, target) { current -> connection.qcyEq.value?.let { band.report(it) }?.let { target.reportedAs(it, current) } }
    }

    /** Nothing earbuds name their model in their first answers; the EQ opens then, with the model's presets and custom curve. */
    private fun nothing(): EqSource? {
        val session = connection.nothingSession ?: return null
        if (session.model == null || (session.eqPresets.isEmpty() && session.simpleEqFilters == null)) return null
        val band = NothingBandEq(session)
        val target = BandEqTarget(band)
        return EqSource(session, target.layout, target) { current -> connection.nothingEq.value?.let { band.report(it) }?.let { target.reportedAs(it, current) } }
    }

    /** Sony earbuds list their presets and bands as they answer; the EQ opens once they have, and again if what they listed changes. */
    private fun sony(device: Paired): EqSource? {
        val session = connection.sonySession ?: return null
        val eq = connection.sonyEq.value ?: return null
        if (eq.steps == null || eq.bands == null) return null
        val band = SonyBandEq(session, eq)
        val target = BandEqTarget(band)
        // A layout of its own per band set, so profiles saved for one model's bands stay with it.
        return EqSource(listOf(device.address, session, eq.presets, eq.bands, eq.steps), target.layout, target) { current ->
            connection.sonyEq.value?.let { band.report(it) }?.let { target.reportedAs(it, current) }
        }
    }

    /**
     * Galaxy Buds report which preset they hold, or that they hold a custom curve, but not the curve; the screen shows the curve this phone
     * last sent. Where the earbuds reported every preset's curve (the Buds4 family) they arrive after the screen opened, so the screen opens again.
     */
    private fun samsung(device: Paired): EqSource? {
        val model = SamsungModels.of(device.model) ?: return null
        val curves = connection.samsungEqCurves.value
        val band = SamsungBandEq(model, curves)
        val target = BandEqTarget(band)
        return EqSource(model to curves.isNotEmpty(), target.layout, target) { current ->
            connection.samsungStatus.value?.let { band.report(it, current) }?.let { (report, custom) ->
                target.reportedAs(report, current)?.let { EqReported(it.profileId, it.gains, resendCustom = custom) }
            }
        }
    }

    /** Soundcore earbuds report their EQ with every state; the Sound tab follows it. */
    private fun soundcore(device: Paired): EqSource? {
        val spec = SoundcoreEqSpec.of(device.model) ?: return null
        val band = SoundcoreBandEq(spec, { connection.soundcoreState.value }, SoundcoreLayout.of(spec.model)?.checksum ?: true)
        val target = BandEqTarget(band)
        return EqSource(spec, target.layout, target) { current -> connection.soundcoreState.value?.let { band.report(it) }?.let { target.reportedAs(it, current) } }
    }

    init {
        // Earbuds that report their EQ say so as they answer: Soundcore and Galaxy Buds only update a screen that is open,
        // every other brand opens its EQ once its first answer is in (and again when what it listed changes).
        val opening: List<Flow<Unit>> = listOf(
            connection.pixelEq.map { },
            connection.jblEq.map { },
            connection.bandEqReport.map { },
            connection.oneMoreEq.map { },
            connection.qcyEq.map { },
            connection.nothingEq.map { },
            connection.sonyEq.map { },
            connection.samsungEqCurves.map { },
        )
        scope.launch {
            merge(*opening.toTypedArray()).collect {
                val device = connectedDevice() ?: return@collect
                if (device.brand == Brand.SOUNDCORE || device.brand == Brand.SAMSUNG) {
                    // The Galaxy Buds' preset curves arrive after the screen opened: draw them then.
                    if (open != null && connection.samsungEqCurves.value.isNotEmpty()) open(device, modelOf(device, connection.state.value))
                } else {
                    open(device, modelOf(device, connection.state.value))
                }
            }
        }
        scope.launch {
            merge(connection.soundcoreState.map { }, connection.samsungStatus.map { }).collect { report() }
        }
        scope.launch { connection.status.collect { if (it !is LinkStatus.Connected) samsungCurveResent = null } }
    }

    private fun connectedDevice(): Paired? = (connection.status.value as? LinkStatus.Connected)?.device
}
