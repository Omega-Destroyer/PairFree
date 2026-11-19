package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.BandEq
import io.github.omegadestroyer.pairfree.protocol.BandEqReport
import kotlin.math.roundToInt

/**
 * Skullcandy's custom equaliser for the Sound tab (docs/skullcandy/PROTOCOL.md section 13): the bands, the range and the Q are the app's, the write is [SkullcandyRaceTalk.eqCurve]. The presets
 * the earbuds hold (categories 1 to 3) are not offered: their curves are not known and the app applies them by number, which PairFree has not built yet.
 */
public class SkullcandyBandEq(private val talk: SkullcandyRaceTalk) : BandEq {
    override val eqBandsHz: List<Int> = talk.eqBandsHz
    override val eqRangeDb: Int = talk.eqRangeDb
    override val eqPresetNames: List<String> = emptyList()

    override fun eqPresetFrames(name: String): List<ByteArray>? = null

    override fun eqCustomFrames(gains: List<Int>): List<ByteArray>? = talk.eqCurve(gains.map { it.toDouble() })

    /** What PairFree last wrote in this connection; the earbuds' own state is not read back. */
    override val eqReport: BandEqReport?
        get() = talk.eqGains?.let { g -> BandEqReport(null, g.map { it.roundToInt() }, g) }
}
