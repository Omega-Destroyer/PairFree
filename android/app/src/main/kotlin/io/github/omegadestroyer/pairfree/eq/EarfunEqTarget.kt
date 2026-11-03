package io.github.omegadestroyer.pairfree.eq

import io.github.omegadestroyer.pairfree.protocol.EqBand
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.EqPreset
import io.github.omegadestroyer.pairfree.protocol.earfun.EarfunEqSpec
import io.github.omegadestroyer.pairfree.protocol.earfun.EarfunSession
import io.github.omegadestroyer.pairfree.protocol.earfun.EarfunValues
import kotlin.math.roundToInt

/**
 * EarFun earbuds and headphones (docs/earfun/PROTOCOL.md section 6): a curve of the user's own over the model's
 * bands, and EarFun's preset curves, all written band by band as EarFun's app writes them. The earbuds cannot report
 * their EQ, so the screen shows what this phone last sent; nothing goes back on connect, as EarFun's app sends the
 * EQ only when the user changes it.
 *
 * EarFun's sliders have 21 positions: on the 10-band models half-dB steps up to 6 dB, so the screen's whole dB from
 * -6 to 6 land on them exactly; on the 6-band models whole dB from -10 to 10.
 *
 * [frames] are the session's frames as bytes; the connection queues them on the EarFun link.
 */
class EarfunEqTarget(val session: EarfunSession, spec: EarfunEqSpec) : EqTarget {
    override val reportsEq: Boolean = false
    override val hasPreamp: Boolean = false
    override val hasStock: Boolean = true
    override val restoresOnConnect: Boolean = false

    private val presetPositions: Map<String, List<Int>> = EarfunValues.PRESETS[spec.presets].orEmpty().toMap()

    /** The screen's view: the model's bands, and EarFun's presets for that band count. */
    val layout: EqLayout = EqLayout(
        command = 0,
        masterGainDb = 0.0,
        userRangeDb = if (spec.positionGain) EarfunSession.POSITIONS else WHOLE_DB_RANGE,
        bands = spec.hz.map { EqBand(it.roundToInt(), 0.0, 0.0) },
        presets = presetPositions.map { (name, positions) ->
            EqPreset(name, spec.hz.mapIndexed { i, hz -> EqBand(hz.roundToInt(), 0.0, session.gainDb(positions[i])) })
        },
    )

    override fun frames(layout: EqLayout, setting: EqSetting, preset: EqPreset?): List<ByteArray> {
        val positions = if (preset != null) presetPositions[preset.name] else session.positions(setting.gains.map { it.toDouble() })
        return positions?.let(session::eqFrames).orEmpty()
    }

    private companion object {
        /** The 10-band models' whole dB, every one a slider position. */
        const val WHOLE_DB_RANGE = 6
    }
}
