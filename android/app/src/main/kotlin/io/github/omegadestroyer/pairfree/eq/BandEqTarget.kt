package io.github.omegadestroyer.pairfree.eq

import io.github.omegadestroyer.pairfree.protocol.BandEq
import io.github.omegadestroyer.pairfree.protocol.BandEqReport
import io.github.omegadestroyer.pairfree.protocol.EqBand
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.EqPreset

/**
 * The Sound tab's equalizer for any session that implements [BandEq]: the graph and its draggable bands are drawn over the bands the session
 * reports (so each model shows what it supports), its presets are the ones the earbuds apply, and the frames are the session's own. Nothing here
 * is specific to a brand; a brand gets its graphic EQ by implementing [BandEq] (docs/DESIGN.md, Sound).
 */
class BandEqTarget(val source: BandEq) : EqTarget {
    private val hz = source.eqBandsHz

    override val reportsEq: Boolean = true
    override val reportsCustom: Boolean = true
    override val hasPreamp: Boolean = false
    override val hasStock: Boolean = false
    override val hasCustom: Boolean = hz.isNotEmpty()

    /** The earbuds apply their presets themselves and their curves are not known, so none is drawn. */
    override val presetCurves: Boolean = false

    /** A model with presets only still needs a band list for the screen's profile lists. */
    private val layoutHz: List<Int> = hz.ifEmpty { PRESET_ONLY_HZ }

    val layout: EqLayout = EqLayout(
        command = 0,
        masterGainDb = 0.0,
        userRangeDb = source.eqRangeDb,
        bands = layoutHz.map { EqBand(it, 0.0, 0.0) },
        presets = source.eqPresetNames.map { name -> EqPreset(name, layoutHz.map { EqBand(it, 0.0, 0.0) }) },
    )

    override fun frames(layout: EqLayout, setting: EqSetting, preset: EqPreset?): List<ByteArray> =
        (if (preset != null) source.eqPresetFrames(preset.name) else source.eqCustomFrames(setting.gains)).orEmpty()

    /** What the earbuds hold, as the screen's profile (null for the custom curve) and gains; null while they have not said. */
    fun reported(report: BandEqReport, current: List<Int>): Pair<String?, List<Int>>? =
        if (report.preset != null) EqProfile.PRESET + report.preset to current else null to (report.gains ?: return null)

    private companion object {
        val PRESET_ONLY_HZ = listOf(62, 250, 1000, 4000, 8000, 16000)
    }
}
