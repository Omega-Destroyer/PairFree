package io.github.omegadestroyer.pairfree.eq

import io.github.omegadestroyer.pairfree.protocol.BandEq
import io.github.omegadestroyer.pairfree.protocol.BandEqReport
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.EqPreset
import io.github.omegadestroyer.pairfree.protocol.toLayout

/**
 * The Sound tab's equalizer for any session that implements [BandEq]: the graph and its draggable bands are drawn over the bands the session
 * reports (so each model shows what it supports), its presets are the ones the earbuds apply, and the frames are the session's own. Nothing here
 * is specific to a brand; a brand gets its graphic EQ by implementing [BandEq] (docs/DESIGN.md, Sound).
 */
class BandEqTarget(val source: BandEq) : EqTarget {
    private val hz = source.eqBandsHz

    override val reportsEq: Boolean = source.eqReportsState
    override val reportsCustom: Boolean = source.eqReportsCustom
    override val hasPreamp: Boolean = source.eqHasPreamp
    override val hasStock: Boolean = source.eqHasStock
    override val hasCustom: Boolean = hz.isNotEmpty()
    override val restoresOnConnect: Boolean = source.eqRestoresOnConnect
    override val confirmed: Boolean = source.eqConfirmed
    override val gainStepDb: Double = source.eqGainStepDb

    /** Presets are drawn where the session gives their curves; otherwise the earbuds apply them themselves and their curves are not known. */
    override val presetCurves: Boolean = source.eqPresetGains.isNotEmpty() || source.eqPresetBands.isNotEmpty()

    val layout: EqLayout = source.toLayout()

    override fun frames(layout: EqLayout, setting: EqSetting, preset: EqPreset?): List<ByteArray> =
        (if (preset != null) source.eqPresetFrames(preset.name, setting.preampDb) else source.eqCustomFramesDb(setting.gains, setting.preampDb)).orEmpty()

    /** [reported] as what the opener hands the screen. */
    fun reportedAs(report: BandEqReport, current: List<Double> = emptyList()): EqReported? = reported(report, current)?.let { EqReported(it.first, it.second) }

    /** What the earbuds hold, as the screen's profile (null for the custom curve) and gains; null while they have not said. */
    fun reported(report: BandEqReport, current: List<Double>): Pair<String?, List<Double>>? =
        if (report.preset != null) EqProfile.PRESET + report.preset to (report.gainsDb ?: current) else null to (report.gainsDb ?: return null)
}
