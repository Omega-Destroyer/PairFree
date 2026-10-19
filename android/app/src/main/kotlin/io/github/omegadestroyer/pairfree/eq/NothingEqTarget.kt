package io.github.omegadestroyer.pairfree.eq

import io.github.omegadestroyer.pairfree.protocol.EqBand
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.EqPreset
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingCommand
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingEq
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingFrame
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingSession
import kotlin.math.roundToInt

/**
 * Nothing and CMF earbuds and headphones (docs/nothing/PROTOCOL.md section 5): the presets the model has, which
 * the earbuds apply themselves, and where the model has one the custom EQ Nothing X calls simple: bass, mid and
 * treble filters from -6 to 6 dB. The earbuds report which they hold and the custom curve, so the screen
 * follows them.
 *
 * [frames] are the session's frames as bytes, with their sequence numbers; the connection queues them on the
 * Nothing link.
 */
class NothingEqTarget(val session: NothingSession) : EqTarget {
    override val reportsEq: Boolean = true
    override val hasPreamp: Boolean = false
    override val hasStock: Boolean = false
    override val hasCustom: Boolean = session.simpleEqFilters != null

    /** The earbuds apply their presets themselves, and Nothing does not publish the curves. */
    override val presetCurves: Boolean = false

    private val bandsHz: List<Int> = session.simpleEqFilters?.let { listOf(it.lowHz, it.peakHz, it.highHz).map(Double::roundToInt) }
        ?: listOf(DEFAULT_LOW, DEFAULT_PEAK, DEFAULT_HIGH)

    /** The screen's view: the three filters, and the presets by the names Nothing X gives them. */
    val layout: EqLayout = EqLayout(
        command = NothingCommand.SET_CUSTOM_EQ,
        masterGainDb = 0.0,
        userRangeDb = NothingSession.SIMPLE_EQ_RANGE,
        bands = bandsHz.map { EqBand(it, 0.0, 0.0) },
        presets = session.eqPresets.map { EqPreset(NAMES.getValue(it), bandsHz.map { hz -> EqBand(hz, 0.0, 0.0) }) },
    )

    override fun frames(layout: EqLayout, setting: EqSetting, preset: EqPreset?): List<ByteArray> {
        val out = if (preset != null) {
            session.eqPreset(NAMES.entries.firstOrNull { it.value == preset.name }?.key ?: return emptyList())
        } else {
            session.eqCustom(setting.gains)
        }
        return out.orEmpty().map(NothingFrame::bytes)
    }

    /** What [eq] says the earbuds hold, as the screen's profile (null for the custom curve) and gains; null while it says neither. */
    fun reported(eq: NothingEq, current: List<Int>): Pair<String?, List<Int>>? {
        if (eq.custom) return null to (eq.gains ?: return null)
        val name = NAMES[eq.preset ?: return null] ?: return null
        return EqProfile.PRESET + name to current
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
