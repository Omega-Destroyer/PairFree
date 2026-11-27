package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.BandEq
import io.github.omegadestroyer.pairfree.protocol.BandEqReport
import kotlin.math.roundToInt

/**
 * The BES models' equaliser for the Sound tab (docs/skullcandy/PROTOCOL.md section 12.3): the presets the earbuds hold by the app's numbers and a custom curve over the bands the earbuds report, in whole dB
 * within the wire's range of minus 5 to plus 5. Unlike the Airoha models the earbuds say what they hold, so the screen opens on that.
 */
public class SkullcandyBesBandEq(private val talk: SkullcandyBesTalk) : BandEq {
    override val eqBandsHz: List<Int> = if (talk.hasEq && talk.curveKnown) talk.eqBandsHz else emptyList()
    override val eqRangeDb: Int = talk.eqRangeDb
    override val eqPresetNames: List<String> = talk.eqPresets.map { it.first }

    override fun eqPresetFrames(name: String): List<ByteArray>? = talk.eqPreset(name)

    override fun eqCustomFrames(gains: List<Int>): List<ByteArray>? = talk.eqCurve(gains.map { it.toDouble() })

    override val eqReport: BandEqReport?
        get() = talk.eqPresetName?.let { BandEqReport(it, null) } ?: talk.eqGains?.let { g -> BandEqReport(null, g.map { it.roundToInt() }, g) }
}
