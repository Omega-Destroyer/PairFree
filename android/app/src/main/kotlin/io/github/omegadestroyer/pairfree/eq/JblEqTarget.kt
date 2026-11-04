package io.github.omegadestroyer.pairfree.eq

import io.github.omegadestroyer.pairfree.protocol.EqBand
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.EqPreset
import io.github.omegadestroyer.pairfree.protocol.jbl.JblEq
import io.github.omegadestroyer.pairfree.protocol.jbl.JblEqCurve
import io.github.omegadestroyer.pairfree.protocol.jbl.JblEqWire
import io.github.omegadestroyer.pairfree.protocol.jbl.JblFrame
import io.github.omegadestroyer.pairfree.protocol.jbl.JblSession
import kotlin.math.roundToInt

/**
 * JBL headphones and earbuds whose EQ PairFree writes (docs/jbl/PROTOCOL.md section 11): JBL's presets for the model,
 * with their curves, and a curve of the user's own on JBL's ten bands, in the model's range. Most models report the EQ
 * they hold, so the screen follows them; on the older BES models the screen shows what this phone last set, as JBL's app
 * does. Each write carries the calibration JBL's app works out on its BES models.
 *
 * [frames] are the session's frames as bytes; the connection queues them on the JBL link.
 */
class JblEqTarget(val session: JblSession) : EqTarget {
    private val spec = requireNotNull(session.model.eq)

    override val reportsEq: Boolean = spec.reports
    override val hasPreamp: Boolean = false

    /** JBL's app does not put the EQ back when it connects. */
    override val restoresOnConnect: Boolean = false
    override val hasStock: Boolean = false

    /** Nothing has confirmed JBL's EQ writes on a device yet. */
    override val confirmed: Boolean = false

    private val names: List<String> = spec.presets.map { display(it.name) }

    val layout: EqLayout = EqLayout(
        command = 0,
        masterGainDb = 0.0,
        userRangeDb = spec.rangeDb,
        bands = JblEq.BANDS_HZ.map { EqBand(it, 1.0, 0.0) },
        presets = spec.presets.mapIndexed { i, p -> EqPreset(names[i], p.bands.map { EqBand(it.hz.roundToInt(), it.q.toDouble(), it.gainDb.toDouble()) }) },
    )

    override fun frames(layout: EqLayout, setting: EqSetting, preset: EqPreset?): List<ByteArray> {
        val jblPreset = preset?.let { p -> spec.presets.getOrNull(names.indexOf(p.name)) ?: return emptyList() }
        return session.eqWrite(jblPreset, setting.gains.map { it.toFloat() }).orEmpty().map(JblFrame::bytes)
    }

    /** What [eq] says the earbuds hold, as the screen's profile (null for a curve of the user's own) and gains; null for neither. */
    fun reported(eq: JblEqCurve, current: List<Int>): Pair<String?, List<Int>>? {
        if (eq.category == JblEqWire.CUSTOM) {
            if (eq.bands.size != JblEq.BANDS_HZ.size) return null
            return null to eq.bands.map { it.gainDb.roundToInt() }
        }
        val i = spec.presets.indexOfFirst { it.category == eq.category }.takeIf { it >= 0 } ?: return null
        return EqProfile.PRESET + names[i] to current
    }

    /** JBL's preset names are in capitals ("EXTREME BASS"); the screen writes them as a sentence. */
    private fun display(name: String): String = name.lowercase().replaceFirstChar { it.uppercase() }
}
