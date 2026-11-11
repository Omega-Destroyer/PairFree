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

    override val reportsEq: Boolean = source.eqReportsState
    override val reportsCustom: Boolean = source.eqReportsCustom
    override val hasPreamp: Boolean = false
    override val hasStock: Boolean = source.eqHasStock
    override val hasCustom: Boolean = hz.isNotEmpty()
    override val restoresOnConnect: Boolean = source.eqRestoresOnConnect
    override val confirmed: Boolean = source.eqConfirmed

    /** Presets are drawn where the session gives their curves; otherwise the earbuds apply them themselves and their curves are not known. */
    override val presetCurves: Boolean = source.eqPresetGains.isNotEmpty()

    /** A model with presets only still needs a band list for the screen's profile lists. */
    private val layoutHz: List<Int> = hz.ifEmpty { source.eqPresetOnlyBandsHz }

    /** The bands as the screen draws them: by frequency, or by the name a brand gives them (then the frequency only orders them). */
    private val bands: List<EqBand> = layoutHz.mapIndexed { i, f -> EqBand(f, 0.0, 0.0, source.eqBandLabels.getOrNull(i)) }

    val layout: EqLayout = EqLayout(
        command = 0,
        masterGainDb = 0.0,
        userRangeDb = source.eqRangeDb,
        bands = bands,
        presets = source.eqPresetNames.map { name ->
            val curve = source.eqPresetGains[name]?.takeIf { it.size == bands.size }
            EqPreset(name, bands.mapIndexed { i, b -> b.copy(gainDb = curve?.get(i) ?: 0.0) })
        },
    )

    override fun frames(layout: EqLayout, setting: EqSetting, preset: EqPreset?): List<ByteArray> =
        (if (preset != null) source.eqPresetFrames(preset.name) else source.eqCustomFrames(setting.gains)).orEmpty()

    /** [reported] as what the opener hands the screen. */
    fun reportedAs(report: BandEqReport, current: List<Int> = emptyList()): EqReported? = reported(report, current)?.let { EqReported(it.first, it.second) }

    /** What the earbuds hold, as the screen's profile (null for the custom curve) and gains; null while they have not said. */
    fun reported(report: BandEqReport, current: List<Int>): Pair<String?, List<Int>>? =
        if (report.preset != null) EqProfile.PRESET + report.preset to (report.gains ?: current) else null to (report.gains ?: return null)
}
