package io.github.omegadestroyer.pairfree.eq

import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.EarbudsConnection
import io.github.omegadestroyer.pairfree.bluetooth.LinkStatus
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.jbl.JblEqCurve
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingEq
import io.github.omegadestroyer.pairfree.protocol.onemore.OneMoreEq
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyEq
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungModels
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungStatus
import io.github.omegadestroyer.pairfree.protocol.sony.SonyEq
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreEqSpec
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreLayout
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreState
import io.github.omegadestroyer.pairfree.ui.modelOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Opens the equalizer for the connected earbuds of every brand: builds the brand's [EqTarget] and layout from the model and the
 * session, hands them to the [Equalizer], and follows what earbuds that report their EQ say. It lives in the Hub, so the Sound tab
 * and the home screen widget share it (docs/DESIGN.md, Widget).
 */
class EqOpener(
    private val connection: EarbudsConnection,
    private val equalizer: Equalizer,
    private val scope: CoroutineScope,
) {
    /** The Soundcore EQ while the Sound tab shows Soundcore earbuds whose EQ write is confirmed. */
    private var soundcoreEq: SoundcoreEqTarget? = null

    /**
     * Opens the Sound tab's EQ for [device]: SoundPEATS models from their table, Soundcore models
     * whose EQ write a recording or OpenSCQ30's tests confirm (docs/soundcore/PROTOCOL.md section 8).
     */
    fun open(device: Paired, model: Model?) {
        if (device.brand == Brand.CAMBRIDGE) {
            // Legacy Melomania's five gain controls have no established frequency grid. They use Sound-tab rows.
            equalizer.open(device.address, model, null)
            return
        }
        if (device.brand != Brand.PIXEL) pixelEq = null
        if (device.brand == Brand.PIXEL) {
            nothingEq = null
            soundcoreEq = null
            samsungEq = null
            sonyEq = null
            openPixelEq(device, model)
            return
        }
        if (device.brand != Brand.NOTHING) nothingEq = null
        if (device.brand != Brand.QCY) qcyEq = null
        if (device.brand != Brand.ONEMORE) oneMoreEq = null
        if (device.brand != Brand.EARFUN) earfunEq = null
        if (device.brand != Brand.JBL) jblEq = null
        if (device.brand == Brand.JBL) {
            soundcoreEq = null
            samsungEq = null
            sonyEq = null
            openJblEq(device, model)
            return
        }
        if (device.brand == Brand.EARFUN) {
            soundcoreEq = null
            samsungEq = null
            sonyEq = null
            openEarfunEq(device, model)
            return
        }
        if (device.brand == Brand.ONEMORE) {
            soundcoreEq = null
            samsungEq = null
            sonyEq = null
            openOneMoreEq(device, model)
            return
        }
        if (device.brand == Brand.QCY) {
            soundcoreEq = null
            samsungEq = null
            sonyEq = null
            openQcyEq(device, model)
            return
        }
        if (device.brand == Brand.NOTHING) {
            soundcoreEq = null
            samsungEq = null
            sonyEq = null
            openNothingEq(device, model)
            return
        }
        if (device.brand == Brand.SONY) {
            soundcoreEq = null
            samsungEq = null
            openSonyEq(device, model)
            return
        }
        sonyEq = null
        if (device.brand == Brand.SAMSUNG) {
            soundcoreEq = null
            val target = SamsungModels.of(device.model)?.let { SamsungEqTarget(it, connection.samsungEqCurves.value) }
            samsungEq = target
            equalizer.open(device.address, model, target?.layout, target ?: SoundpeatsEqTarget)
            connection.samsungStatus.value?.let(::reportSamsungEq)
            return
        }
        samsungEq = null
        if (device.brand != Brand.SOUNDCORE) {
            soundcoreEq = null
            equalizer.open(device.address, model)
            return
        }
        // Every model with an EQ in data/soundcore/devices.json (owner, 2026-10-29); the screen says when its write is not confirmed.
        val spec = SoundcoreEqSpec.of(device.model)
        val target = spec?.let { SoundcoreEqTarget(it, { connection.soundcoreState.value }, SoundcoreLayout.of(it.model)?.checksum ?: true) }
        soundcoreEq = target
        equalizer.open(device.address, model, target?.layout, target ?: SoundpeatsEqTarget)
        connection.soundcoreState.value?.let(::reportEq)
    }

    /** The Pixel Buds' EQ while the Sound tab shows a Pixel Buds Pro, Pro 2 or 2a whose EQ read answered. */
    private var pixelEq: PixelEqTarget? = null

    /**
     * Pixel Buds report their EQ once read; the Sound tab's EQ opens then, with Google's presets and the earbuds'
     * saved curve where they hold one, and follows what they report from then on. The 2020 model and the A-Series
     * have bass boost alone, which is a row on the Sound tab.
     */
    private fun openPixelEq(device: Paired, model: Model?) {
        val session = connection.pixelSession
        if (session == null || !session.hasEq) {
            pixelEq = null
            equalizer.open(device.address, null)
            return
        }
        val open = pixelEq
        if (open != null && open.session === session && open.hasSaved == (session.savedEq != null) && equalizer.state.value != null) return
        // Open again when the saved curve arrived after the EQ: Last saved joins the presets.
        if (open?.session === session) equalizer.open(device.address, null)
        val target = PixelEqTarget(session)
        pixelEq = target
        equalizer.open(device.address, model, target.layout, target)
        connection.pixelEq.value?.let(::reportPixelEq)
    }

    private fun reportPixelEq(gains: List<Float>) {
        val target = pixelEq ?: return
        val (profile, whole) = target.reported(gains)
        equalizer.reported(profile, whole)
    }

    /** The JBL EQ while the Sound tab shows JBL earbuds whose EQ PairFree writes and which have reported theirs. */
    private var jblEq: JblEqTarget? = null

    /** JBL models report their EQ once read; the Sound tab opens it then, with JBL's presets, and follows what they hold. */
    private fun openJblEq(device: Paired, model: Model?) {
        val session = connection.jblSession
        val spec = session?.model?.eq
        if (session == null || spec?.written != true || (spec.reports && session.eq == null)) {
            jblEq = null
            equalizer.open(device.address, null)
            return
        }
        if (jblEq?.session === session && equalizer.state.value != null) return
        val target = JblEqTarget(session)
        jblEq = target
        equalizer.open(device.address, model, target.layout, target)
        connection.jblEq.value?.let(::reportJblEq)
    }

    private fun reportJblEq(eq: JblEqCurve) {
        val target = jblEq ?: return
        val current = equalizer.state.value?.setting?.gains ?: return
        val (profile, gains) = target.reported(eq, current) ?: return
        equalizer.reported(profile, gains)
    }

    /** The 1MORE EQ while the Sound tab shows 1MORE earbuds whose model has presets or a custom EQ. */
    private var oneMoreEq: OneMoreEqTarget? = null

    /** 1MORE models' EQ is in their table; the Sound tab opens it at once and follows what the earbuds report. */
    private fun openOneMoreEq(device: Paired, model: Model?) {
        val session = connection.oneMoreSession
        val m = session?.model
        if (session == null || m == null || (m.eqPresets.isEmpty() && m.eqBands == null)) {
            oneMoreEq = null
            equalizer.open(device.address, null)
            return
        }
        if (oneMoreEq?.session === session && equalizer.state.value != null) return
        val target = OneMoreEqTarget(session)
        oneMoreEq = target
        equalizer.open(device.address, model, target.layout, target)
        connection.oneMoreEq.value?.let(::reportOneMoreEq)
    }

    private fun reportOneMoreEq(eq: OneMoreEq) {
        val target = oneMoreEq ?: return
        val current = equalizer.state.value?.setting?.gains ?: return
        val (profile, gains) = target.reported(eq, current) ?: return
        equalizer.reported(profile, gains)
    }

    /** The EarFun EQ while the Sound tab shows EarFun earbuds whose model has one. */
    private var earfunEq: EarfunEqTarget? = null

    /** EarFun models' EQ is in their table and cannot be read; the Sound tab opens it at once, showing what this phone last sent. */
    private fun openEarfunEq(device: Paired, model: Model?) {
        val session = connection.earfunSession
        val spec = session?.model?.eq
        if (session == null || spec == null) {
            earfunEq = null
            equalizer.open(device.address, null)
            return
        }
        if (earfunEq?.session === session && equalizer.state.value != null) return
        val target = EarfunEqTarget(session, spec)
        earfunEq = target
        equalizer.open(device.address, model, target.layout, target)
    }

    /** The QCY EQ while the Sound tab shows QCY earbuds that reported an EQ PairFree can set. */
    private var qcyEq: QcyEqTarget? = null

    /** QCY earbuds report their EQ after they connect; the Sound tab's EQ opens once they have, over the bands they report. */
    private fun openQcyEq(device: Paired, model: Model?) {
        // SOUNDPEATS' QCY-built models: their curve from SOUNDPEATS' data, not QCY's EQ.
        if (device.soldAs == Brand.SOUNDPEATS) {
            qcyEq = null
            equalizer.open(device.address, model, model?.eq, CapsuleEqTarget)
            return
        }
        val session = connection.qcySession
        val eq = connection.qcyEq.value
        if (session == null || eq == null) {
            qcyEq = null
            equalizer.open(device.address, null)
            return
        }
        qcyEq?.let { if (it.session === session && it.freqs == eq.freqs && equalizer.state.value != null) return }
        val target = QcyEqTarget(session, eq)
        qcyEq = target
        equalizer.open(device.address, model, target.layout, target)
        reportQcyEq(eq)
    }

    private fun reportQcyEq(eq: QcyEq) {
        val gains = qcyEq?.reported(eq) ?: return
        equalizer.reported(null, gains)
    }

    /** The Nothing EQ while the Sound tab shows Nothing or CMF earbuds whose model PairFree knows. */
    private var nothingEq: NothingEqTarget? = null

    /**
     * Nothing earbuds name their model in their first answers; the Sound tab's EQ opens once they have, with the
     * model's presets and custom curve, and follows what they report from then on.
     */
    private fun openNothingEq(device: Paired, model: Model?) {
        val session = connection.nothingSession
        if (session == null || session.model == null || (session.eqPresets.isEmpty() && session.simpleEqFilters == null)) {
            nothingEq = null
            equalizer.open(device.address, null)
            return
        }
        if (nothingEq?.session === session && equalizer.state.value != null) return
        val target = NothingEqTarget(session)
        nothingEq = target
        equalizer.open(device.address, model, target.layout, target)
        connection.nothingEq.value?.let(::reportNothingEq)
    }

    private fun reportNothingEq(eq: NothingEq) {
        val target = nothingEq ?: return
        val current = equalizer.state.value?.setting?.gains ?: return
        val (profile, gains) = target.reported(eq, current) ?: return
        equalizer.reported(profile, gains)
    }

    /** The Sony EQ while the Sound tab shows Sony earbuds, with the EQ and the earbuds it was opened for. */
    private var sonyEq: Triple<SonyEqTarget, SonyEq, String>? = null

    /**
     * Sony earbuds list their presets and bands as they answer; the Sound tab opens once they have, and
     * again if what they listed changes. Until then it shows no EQ.
     */
    private fun openSonyEq(device: Paired, model: Model?) {
        val session = connection.sonySession
        val eq = connection.sonyEq.value
        if (session == null || eq == null || eq.steps == null || eq.bands == null) {
            sonyEq = null
            equalizer.open(device.address, null)
            return
        }
        // The same earbuds, connection and layout: the screen stays as it is, and follows their reports.
        sonyEq?.let { (target, shape, address) ->
            val same = address == device.address && target.session === session &&
                shape.presets == eq.presets && shape.bands == eq.bands && shape.steps == eq.steps
            if (same && equalizer.state.value != null) return
        }
        val target = SonyEqTarget(session, eq)
        sonyEq = Triple(target, eq, device.address)
        // A layout of its own per band set, so profiles saved for one model's bands stay with it.
        equalizer.open(device.address, model, target.layout, target)
        reportSonyEq(eq)
    }

    private fun reportSonyEq(eq: SonyEq) {
        val (profile, gains) = sonyEq?.first?.reported(eq) ?: return
        equalizer.reported(profile, gains)
    }

    private fun reportEq(state: SoundcoreState) {
        val (profile, gains) = soundcoreEq?.reported(state) ?: return
        equalizer.reported(profile, gains)
    }

    /** The Galaxy Buds EQ while the Sound tab shows Galaxy Buds. */
    private var samsungEq: SamsungEqTarget? = null

    /** Galaxy Buds whose custom curve went out again on this connection, so it goes once. */
    private var samsungCurveResent: String? = null

    /**
     * Galaxy Buds report which preset they hold, or that they hold a custom curve, but not the curve;
     * the Sound tab shows the curve this phone last sent. A custom curve goes out again once per
     * connection, as the earbuds forget it when they power off.
     */
    private fun reportSamsungEq(status: SamsungStatus) {
        val reported = samsungEq?.reported(status) ?: return
        val gains = equalizer.state.value?.setting?.gains ?: return
        equalizer.reported(reported.profileId, gains)
        val address = (connection.status.value as? LinkStatus.Connected)?.device?.address ?: return
        if (reported.custom && samsungCurveResent != address) {
            samsungCurveResent = address
            equalizer.resendCustom(address)
        }
    }

    init {
        // Soundcore earbuds report their EQ with every state; the Sound tab follows it.
        scope.launch { connection.soundcoreState.collect { s -> s?.let(::reportEq) } }
        scope.launch { connection.samsungStatus.collect { s -> s?.let(::reportSamsungEq) } }
        // The Galaxy Buds' preset curves arrive after the screen opened: draw them then.
        scope.launch {
            connection.samsungEqCurves.collect { curves ->
                val target = samsungEq ?: return@collect
                val device = (connection.status.value as? LinkStatus.Connected)?.device ?: return@collect
                if (curves.isNotEmpty()) open(device, SamsungModels.model(target.model.model))
            }
        }
        scope.launch { connection.status.collect { if (it !is LinkStatus.Connected) samsungCurveResent = null } }
        // Pixel Buds report their EQ once read: open it then, and follow what they hold.
        scope.launch {
            connection.pixelEq.collect { eq ->
                val device = connectedDevice() ?: return@collect
                if (device.brand != Brand.PIXEL || eq == null) return@collect
                openPixelEq(device, modelOf(device, connection.state.value))
                reportPixelEq(eq)
            }
        }
        // JBL earbuds report their EQ once read: open it then, and follow what they hold.
        scope.launch {
            connection.jblEq.collect { eq ->
                val device = connectedDevice() ?: return@collect
                if (device.brand != Brand.JBL || eq == null) return@collect
                openJblEq(device, modelOf(device, connection.state.value))
                reportJblEq(eq)
            }
        }
        // 1MORE earbuds report their preset and curve after connecting: follow what they hold.
        scope.launch {
            connection.oneMoreEq.collect { eq ->
                val device = connectedDevice() ?: return@collect
                if (device.brand != Brand.ONEMORE || eq == null) return@collect
                openOneMoreEq(device, modelOf(device, connection.state.value))
                reportOneMoreEq(eq)
            }
        }
        // QCY earbuds report their EQ after connecting: open it once they have, then follow what they hold.
        scope.launch {
            connection.qcyEq.collect { eq ->
                val device = connectedDevice() ?: return@collect
                if (device.brand != Brand.QCY || eq == null) return@collect
                openQcyEq(device, modelOf(device, connection.state.value))
                reportQcyEq(eq)
            }
        }
        // Nothing earbuds name their model, then report their EQ: open it once they have, then follow what they hold.
        scope.launch {
            connection.nothingEq.collect { eq ->
                val device = connectedDevice() ?: return@collect
                if (device.brand != Brand.NOTHING || eq == null) return@collect
                openNothingEq(device, modelOf(device, connection.state.value))
                reportNothingEq(eq)
            }
        }
        // Sony earbuds tell their EQ bit by bit: open it once they have, then follow what they hold.
        scope.launch {
            connection.sonyEq.collect { eq ->
                val device = connectedDevice() ?: return@collect
                if (device.brand != Brand.SONY || eq == null) return@collect
                openSonyEq(device, modelOf(device, connection.state.value))
                reportSonyEq(eq)
            }
        }
    }

    private fun connectedDevice(): Paired? = (connection.status.value as? LinkStatus.Connected)?.device
}
