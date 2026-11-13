package io.github.omegadestroyer.pairfree.eq

import io.github.omegadestroyer.pairfree.brands.EqEvent
import io.github.omegadestroyer.pairfree.bluetooth.EarbudsConnection
import io.github.omegadestroyer.pairfree.bluetooth.LinkStatus
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.ui.modelOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What earbuds that report their EQ hold now: the preset's profile id (null for a curve of the user's own) and the gains in whole dB. */
class EqReported(val profileId: String?, val gains: List<Int>, val resendCustom: Boolean = false)

/**
 * One brand's equalizer, ready for the Sound tab: its [layout] and [target], a [key] that says when the screen must be opened again
 * (the earbuds listed other bands, a saved curve arrived), and [reported], which reads what the earbuds hold now from the connection.
 * Every brand is one of these, built by its driver ([io.github.omegadestroyer.pairfree.brands.BrandDriver.eqSource]); nothing in the opener is specific to a brand.
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

    /** The brand's EQ for [device], or null when the model or the earbuds' answers so far give none: its driver says. */
    private fun source(device: Paired, model: Model?): EqSource? = connection.driver?.eqSource(device, model)

    init {
        // Earbuds that report their EQ say so as they answer ([EqEvent]): a brand whose EQ opens from its model's table only
        // updates a screen that is open, every other brand opens its EQ once its first answer is in (and again when what it
        // listed changes).
        scope.launch {
            connection.eqEvents.collect { event ->
                try {
                    when (event) {
                        EqEvent.OPEN -> connectedDevice()?.let { open(it, modelOf(it, connection.state.value)) }
                        EqEvent.OPEN_IF_OPEN -> if (open != null) connectedDevice()?.let { open(it, modelOf(it, connection.state.value)) }
                        EqEvent.REPORT -> report()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: RuntimeException) {
                    // A fault in one brand's EQ code leaves the Sound tab as it was; it must not end the app or this collector.
                }
            }
        }
        scope.launch { connection.status.collect { if (it !is LinkStatus.Connected) samsungCurveResent = null } }
    }

    private fun connectedDevice(): Paired? = (connection.status.value as? LinkStatus.Connected)?.device
}
