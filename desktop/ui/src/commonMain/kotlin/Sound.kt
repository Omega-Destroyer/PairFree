package io.github.omegadestroyer.pairfree.ui

import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EqActions
import io.github.omegadestroyer.pairfree.eq.EqProfile
import io.github.omegadestroyer.pairfree.eq.EqState
import io.github.omegadestroyer.pairfree.eq.Equalizer
import io.github.omegadestroyer.pairfree.eq.SonyBandEq
import io.github.omegadestroyer.pairfree.protocol.EqBand
import io.github.omegadestroyer.pairfree.shell.DesktopStrings
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** What the Sound screen asks the app for, beyond the equalizer's own actions. */
internal class SoundCallbacks(
    val selectBand: (Int) -> Unit,
    val askSave: (suggested: String) -> Unit,
    val askDelete: (EqProfile) -> Unit,
    val importFile: () -> Unit,
    val exportFile: (fileName: String) -> Unit,
)

/** The Sound tab: profiles, the curve, one band at a time, the preamp, and profile files (docs/DESKTOP.md). */
internal class SoundScreen(
    private val eq: EqState?,
    private val actions: EqActions,
    private val band: Int,
    private val message: String?,
    private val s: DesktopStrings,
    private val theme: Theme,
    private val call: SoundCallbacks,
    private val onHearingTest: (() -> Unit)? = null,
) {
    private fun label(text: String) = Label(text, theme.labelFont, theme.label, caps = true)

    private fun note(text: String) = Label(text, theme.small, theme.secondaryText)

    fun profileName(p: EqProfile): String = when {
        p.id == EqProfile.STOCK -> s.text("eq_stock")
        p.preset != null -> s.presetLabel(p.name)
        else -> p.name
    }

    private fun currentName(e: EqState): String =
        if (!e.known) s.text("eq_unknown")
        else e.selected?.let(::profileName) ?: e.basis?.let { s.text("eq_edited", profileName(it)) } ?: s.text("eq_new_unsaved")

    /** `+2 dB`, `0 dB`, `−3 dB`: whole numbers as they are, half steps with one decimal. */
    fun db(value: Double): String = s.text("db", signed(value))

    /** A gain with its sign and without a unit, for a band's button. */
    private fun signed(value: Double): String {
        val n = abs(value)
        val number = if (n == floor(n)) n.toInt().toString() else "${(n * 10).roundToInt() / 10.0}"
        return if (value > 0) "+$number" else if (value < 0) "−$number" else "0"
    }

    private fun hz(hz: Int): String = when {
        hz < 1000 -> "$hz"
        hz % 1000 == 0 -> "${hz / 1000}k"
        else -> "${hz / 1000}.${hz % 1000 / 100}k"
    }

    private fun bandShort(b: EqBand): String = b.label?.let { bandLabel(it, true) } ?: hz(b.hz)

    private fun bandName(b: EqBand): String {
        b.label?.let { return bandLabel(it, false) }
        return if (b.hz < 1000) s.text("hz", "${b.hz}") else s.text("khz", hz(b.hz).removeSuffix("k"))
    }

    private fun bandLabel(key: String, short: Boolean): String = s.text(
        when (key) {
            "low_bass" -> if (short) "eq_band_low_bass_short" else "eq_band_low_bass"
            "bass" -> "eq_band_bass"
            "mid" -> "eq_band_mid"
            "treble" -> "eq_band_treble"
            "upper_treble" -> if (short) "eq_band_upper_treble_short" else "eq_band_upper_treble"
            else -> "unknown"
        },
    )

    fun build(): Widget {
        val title = Label(s.text("tab_sound"), theme.title)
        if (eq == null) return Column(listOf(title, Label(s.text("eq_unavailable"), theme.body, theme.secondaryText)), 16f)
        val gains = eq.setting.gains
        val selected = band.coerceIn(gains.indices)
        val parts = mutableListOf<Widget>(title)

        val names = eq.profiles.map(::profileName) + if (eq.target.hasCustom) listOf(s.text("eq_new")) else emptyList()
        val on = eq.profiles.indexOf(eq.selected)
        parts += segmented("profile", names, on.takeIf { it >= 0 }, primary = false, perRow = if (names.any { it.length > 14 }) 3 else 4) { i ->
            if (i < eq.profiles.size) actions.select(eq.profiles[i]) else actions.startNew()
        }
        parts += label(s.text("eq_on_earbuds", currentName(eq)))
        if (onHearingTest != null) parts += Button(s.text("hearing_test"), "hearing_open") { onHearingTest() }
        if (!eq.known) parts += note(s.text(if (eq.target.reportsEq) "eq_reading" else "eq_unknown_hint"))
        if (!eq.target.confirmed) parts += note(s.text("eq_unconfirmed"))

        val editable = eq.preset == null && eq.target.hasCustom
        if (editable || eq.target.presetCurves) {
            val curve = eq.curve
            val range = EqGraph.scale(curve, eq.layout.userRangeDb)
            parts += Panel(
                EqGraph(curve, eq.curveBands.map(::bandShort), range, eq.layout.userRangeDb, eq.target.gainStepDb, selected, editable, "eqgraph", call.selectBand) { b, g ->
                    actions.setGain(b, g)
                },
                pad = 12f,
            )
        }

        if (editable) {
            val cells = eq.layout.bands.mapIndexed { i, b -> BandCell(bandShort(b), signed(gains[i]), i == selected, "band$i") { call.selectBand(i) } as Widget }
            parts += Row(cells, 6f)
            val gain = gains[selected]
            val step = eq.target.gainStepDb
            val r = eq.layout.userRangeDb
            val steps = (r / step).roundToInt()
            parts += Panel(
                Column(
                    listOf(
                        Stack(listOf(Label(bandName(eq.layout.bands[selected]), theme.heading), Label(db(gain), theme.bigNumber, theme.accent, Align.END))),
                        Row(
                            listOf(
                                StepButton("−", "gain_minus", gain > -r) { actions.setGain(selected, gain - step) },
                                Slider((gain / step).roundToInt(), -steps, steps, "gain_slider") { actions.setGain(selected, it * step) },
                                StepButton("+", "gain_plus", gain < r) { actions.setGain(selected, gain + step) },
                            ),
                            10f,
                            listOf(0.1f, 1f, 0.1f),
                        ),
                    ),
                    10f,
                ),
            )
        } else {
            parts += note(
                s.text(
                    when {
                        !eq.target.presetCurves -> "eq_preset_applied_by_earbuds"
                        eq.target.hasStock -> "eq_preset_fixed"
                        else -> "eq_preset_fixed_no_stock"
                    },
                ),
            )
        }

        if (eq.target.hasPreamp) {
            val preamp = eq.setting.preampDb
            parts += Panel(
                Row(
                    listOf(
                        Label(s.text("preamp")),
                        StepButton("−", "pre_minus", preamp > Equalizer.PREAMP_MIN_DB) { actions.setPreamp(preamp - 1) },
                        Label(db(preamp.toDouble()), theme.bigNumber, theme.accent, Align.CENTER),
                        StepButton("+", "pre_plus", preamp < 0) { actions.setPreamp(preamp + 1) },
                    ),
                    10f,
                    listOf(1.4f, 0.4f, 0.8f, 0.4f),
                ),
            )
        }

        val buttons = mutableListOf<Widget>()
        if (editable) buttons += Button(s.text("eq_save"), "eq_save", primary = true) { call.askSave(eq.basis?.takeIf { !it.builtIn }?.name.orEmpty()) }
        if (eq.target.hasCustom) {
            buttons += Button(s.text("eq_import"), "eq_import") { call.importFile() }
            if (editable) buttons += Button(s.text("eq_export"), "eq_export") { call.exportFile((eq.basis?.takeIf { !it.builtIn }?.name ?: "pairfree-eq") + ".json") }
        }
        if (buttons.isNotEmpty()) parts += Row(buttons, 8f)
        eq.basis?.takeIf { !it.builtIn }?.let { p -> parts += Button(s.text("eq_delete", p.name), "eq_delete") { call.askDelete(p) } }
        message?.let { parts += note(it) }
        val noteKey = when {
            (eq.target as? BandEqTarget)?.source is SonyBandEq -> "eq_note_sony"
            !eq.target.presetCurves && !eq.target.reportsCustom -> "eq_note_samsung"
            eq.target.reportsEq -> "eq_note_reported"
            else -> "eq_note"
        }
        parts += Label(s.text(noteKey), theme.small, theme.label)
        return Column(parts, 14f)
    }
}
