package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.BandEq
import io.github.omegadestroyer.pairfree.protocol.BandEqReport
import kotlin.math.roundToInt

/**
 * Skullcandy's equaliser for the Sound tab (docs/skullcandy/PROTOCOL.md section 13): the bands, the range and the Q are the app's, the curve's write is [SkullcandyRaceTalk.eqCurve]. The presets
 * the earbuds hold (the app's modes: Music, Podcast, Movie, Bass Boost, the gaming ones) are picked by their number ([SkullcandyRaceTalk.eqPreset]); their curves are the earbuds' own and are not drawn.
 */
public class SkullcandyBandEq(private val talk: SkullcandyRaceTalk) : BandEq {
    override val eqBandsHz: List<Int> = talk.eqBandsHz
    override val eqRangeDb: Int = talk.eqRangeDb
    override val eqPresetNames: List<String> = talk.eqPresets.map { it.first }

    override fun eqPresetFrames(name: String): List<ByteArray>? = talk.eqPreset(name)

    override fun eqCustomFrames(gains: List<Int>): List<ByteArray>? = talk.eqCurve(gains.map { it.toDouble() })

    /** What the earbuds hold (read once the chip is known, docs/skullcandy/PROTOCOL.md section 13.4), or what PairFree last wrote in this connection. */
    override val eqReport: BandEqReport?
        get() = talk.eqPresetName?.let { BandEqReport(it, null) } ?: talk.eqGains?.let { g -> BandEqReport(null, g.map { it.roundToInt() }, g) }
}
