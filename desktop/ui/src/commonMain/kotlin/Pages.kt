package io.github.omegadestroyer.pairfree.ui

import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.brands.modelOf
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.FitResult
import io.github.omegadestroyer.pairfree.eq.EqState
import io.github.omegadestroyer.pairfree.protocol.hearing.HearingEq
import io.github.omegadestroyer.pairfree.protocol.hearing.HearingTable
import io.github.omegadestroyer.pairfree.protocol.hearing.SealCheck
import io.github.omegadestroyer.pairfree.protocol.hearing.StaircaseTest
import io.github.omegadestroyer.pairfree.shell.ArtDrawing
import io.github.omegadestroyer.pairfree.shell.DesktopStrings
import io.github.omegadestroyer.pairfree.shell.deviceArt
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/** A screen that takes the place of the tabs until it is closed: a test that runs through steps. */
internal abstract class Page(protected val s: DesktopStrings, protected val theme: Theme, private val onClose: () -> Unit) {
    /** True while the page shows something that moves with time, so the window keeps drawing. */
    open val animating: Boolean get() = false

    abstract fun build(state: EarbudState): Widget

    /** Stops what the page started (a tone). */
    open fun stop() = Unit

    fun close() {
        stop()
        onClose()
    }

    protected fun head(titleKey: String): Widget =
        Split(Label(s.text(titleKey), theme.title), Button(s.text("capture_close"), "page_close") { close() }, 90f)

    protected fun note(text: String, color: Int = theme.secondaryText) = Label(text, theme.small, color)
}

/** The ear tip fit test the earbuds run themselves (docs/DESIGN.md, Device); the result per earbud arrives in their state. */
internal class FitTestPage(
    private val device: Paired,
    s: DesktopStrings,
    theme: Theme,
    private val tones: TonePlayer,
    private val start: (keepAnc: Boolean) -> Unit,
    private val invalidate: () -> Unit,
    onClose: () -> Unit,
) : Page(s, theme, onClose) {
    private var runStarted: kotlin.time.TimeMark? = null
    private var switchingAnc = false

    private val testing get() = runStarted?.let { it.elapsedNow().inWholeMilliseconds < NO_RESULT_MS } == true && !gotResult
    private var gotResult = false
    /** Whether sound was playing the last time it was asked (every half second), so the warning shows as soon as something starts. */
    private var soundPlaying = false
    private var soundAsked: kotlin.time.TimeMark? = null

    private fun pollSound() {
        val asked = soundAsked
        if (asked == null || asked.elapsedNow().inWholeMilliseconds > 500) {
            soundPlaying = runStarted == null && tones.musicActive
            soundAsked = TimeSource.Monotonic.markNow()
        }
    }

    /** The page keeps drawing while a test runs, and before one starts to follow the sound on the computer. */
    override val animating: Boolean get() = runStarted == null || testing

    override fun build(state: EarbudState): Widget {
        val model = modelOf(device, state)
        val ancOn = (state.number(Command.ANC_MODE) ?: 0) != 0 && model?.fitKeepsAnc != true
        val run = runStarted
        val result = state.fit.takeIf { run != null }
        gotResult = result != null
        val elapsed = run?.elapsedNow()?.inWholeMilliseconds ?: 0L
        val timedOut = run != null && result == null && elapsed >= NO_RESULT_MS
        val busy = run != null && result == null && !timedOut
        val art = deviceArt(state.firmware?.code, device.name).bud
        fun bud(sideKey: String, mirrored: Boolean, fit: FitResult.Fit?): Widget {
            val (text, color) = when {
                busy -> s.text("fit_measuring") to theme.secondaryText
                fit == FitResult.Fit.GOOD -> s.text("fit_good") to theme.accent
                fit == FitResult.Fit.NOT_FIT -> s.text("fit_not_fit") to theme.destructive
                fit == FitResult.Fit.UNKNOWN -> s.text("fit_unknown") to theme.secondaryText
                else -> s.text("fit_not_tested") to theme.label
            }
            return Column(
                listOf(ArtView(ArtDrawing.of(art), 72f, mirrored), Label(s.text(sideKey), theme.labelFont, theme.label, Align.CENTER, caps = true), Label(text, theme.heading, color, Align.CENTER)),
                8f,
            )
        }
        val parts = mutableListOf<Widget>(head("fit_test"), Label(s.text("fit_test_intro"), theme.body, theme.secondaryText))
        pollSound()
        if (run == null) parts += if (soundPlaying) Label(s.text("desktop_fit_sound_playing"), theme.body, theme.destructive) else note(s.text("desktop_fit_quiet"))
        if (ancOn || (busy && switchingAnc)) parts += note(s.text("fit_anc_note"))
        val panel = mutableListOf<Widget>(Row(listOf(bud("left", false, result?.left), bud("right", true, result?.right)), 12f))
        if (busy) {
            val total = TEST_MS + if (switchingAnc) 3000L else 0L
            panel += LevelBar((elapsed.toFloat() / total).coerceIn(0f, 1f))
            panel += note(s.text("fit_testing"))
        }
        parts += Panel(Column(panel, 12f))
        when {
            result != null -> {
                val good = result.left == FitResult.Fit.GOOD && result.right == FitResult.Fit.GOOD
                parts += Label(s.text(if (good) "fit_advice_good" else "fit_advice_poor"))
            }
            timedOut -> parts += Label(s.text("fit_no_result"), theme.body, theme.destructive)
        }
        parts += Button(s.text(if (run == null) "fit_start" else "fit_again"), "fit_start", primary = true, enabled = !busy) {
            switchingAnc = ancOn
            runStarted = TimeSource.Monotonic.markNow()
            start(model?.fitKeepsAnc == true)
            invalidate()
        }
        return Column(parts, 16f)
    }

    private companion object {
        /** The H3 reported about 9 s after Start. */
        const val TEST_MS = 9_000L

        /** Give up waiting for a result after this long. */
        const val NO_RESULT_MS = 20_000L
    }
}

/** The ear tip seal check with tones from the computer (after JBL's): a low tone in one earbud at a time, normal and then softer. */
internal class SealCheckPage(
    s: DesktopStrings,
    theme: Theme,
    private val tones: TonePlayer,
    private val invalidate: () -> Unit,
    onClose: () -> Unit,
) : Page(s, theme, onClose) {
    private val check = SealCheck()
    private var started = false
    private var busy = false

    override fun stop() = tones.end()

    private fun playCurrent() {
        val tone = check.current ?: return
        // The tone repeats until the user answers: a low tone is easy to miss in one short burst.
        tones.play(SealCheck.HZ, tone.levelDb, SealCheck.SECONDS, if (tone.left) TonePlayer.LEFT else TonePlayer.RIGHT, loop = true)
    }

    override fun build(state: EarbudState): Widget {
        val parts = mutableListOf<Widget>(head("seal_check"))
        when {
            !started -> {
                parts += Label(s.text("seal_check_intro"), theme.body, theme.secondaryText)
                if (busy) parts += Label(s.text("hearing_test_music"), theme.body, theme.destructive)
                parts += Button(s.text("hearing_test_start"), "seal_start", primary = true) {
                    if (tones.musicActive) busy = true else { busy = false; started = true; tones.begin(); playCurrent() }
                    invalidate()
                }
            }
            !check.finished -> {
                val tone = check.current
                val side = s.text(if (tone?.left == true) "samsung_hearing_left" else "samsung_hearing_right")
                parts += Panel(
                    Column(
                        listOf(
                            Label(s.text(if (tone?.softer == true) "seal_check_softer" else "seal_check_normal", side), theme.heading),
                            LevelBar(check.progress),
                            note(s.text("seal_check_listen")),
                        ),
                        10f,
                    ),
                )
                parts += Row(
                    listOf(true, false).map { heard ->
                        Button(s.text(if (heard) "samsung_hearing_yes" else "samsung_hearing_no"), "seal_$heard", primary = heard) {
                            tones.stop()
                            check.answer(heard)
                            if (check.finished) tones.end() else playCurrent()
                            invalidate()
                        } as Widget
                    },
                    12f,
                )
            }
            else -> {
                fun seal(sideKey: String, seal: SealCheck.Seal): Widget = Split(
                    Label(s.text(sideKey)),
                    Label(
                        s.text(when (seal) { SealCheck.Seal.GOOD -> "seal_check_good"; SealCheck.Seal.FAIR -> "seal_check_fair"; else -> "seal_check_poor" }),
                        theme.bigNumber.copy(size = 14f), if (seal == SealCheck.Seal.GOOD) theme.accent else theme.destructive, Align.END,
                    ),
                    140f,
                )
                parts += Panel(Column(listOf(seal("samsung_hearing_left", check.left), seal("samsung_hearing_right", check.right)), 8f))
                parts += note(s.text("seal_check_advice"))
                parts += Button(s.text("capture_close"), "seal_close", primary = true) { close() }
            }
        }
        return Column(parts, 16f)
    }
}

/** A hearing test as the vendor's app runs it: tones from loud to quiet at each frequency, then the result as an EQ on the model's bands. */
internal class HearingTestPage(
    private val eq: EqState,
    private val table: HearingTable,
    private val vendorMerge: Boolean,
    s: DesktopStrings,
    theme: Theme,
    private val tones: TonePlayer,
    private val apply: (name: String, gains: List<Int>) -> Unit,
    private val invalidate: () -> Unit,
    onClose: () -> Unit,
) : Page(s, theme, onClose) {
    private val test = StaircaseTest(table)
    private var started = false
    private var busy = false

    override fun stop() = tones.end()

    private fun playCurrent() {
        val tone = test.current ?: return
        tones.play(tone.hz, tone.levelDb, table.seconds, TonePlayer.BOTH, loop = false) {
            test.answer(heard = false)
            if (!test.finished) playCurrent() else tones.end()
            invalidate()
        }
    }

    override fun build(state: EarbudState): Widget {
        val parts = mutableListOf<Widget>(head("hearing_test"))
        when {
            !started -> {
                parts += Label(s.text("hearing_test_intro"), theme.body, theme.secondaryText)
                if (busy) parts += Label(s.text("hearing_test_music"), theme.body, theme.destructive)
                parts += Button(s.text("hearing_test_start"), "hearing_start", primary = true) {
                    if (tones.musicActive) busy = true else { busy = false; started = true; tones.begin(); playCurrent() }
                    invalidate()
                }
            }
            !test.finished -> {
                val tone = test.current
                parts += Panel(
                    Column(
                        listOf(
                            Label(s.text("hearing_test_tone", tone?.hz?.roundToInt() ?: 0), theme.bigNumber),
                            LevelBar(test.progress),
                            note(s.text("hearing_test_listen")),
                        ),
                        10f,
                    ),
                )
                parts += Button(s.text("hearing_test_heard"), "hearing_heard", primary = true) {
                    tones.stop()
                    test.answer(heard = true)
                    if (!test.finished) playCurrent() else tones.end()
                    invalidate()
                }
            }
            else -> {
                val hz = eq.layout.bands.map { it.hz }
                val gains = if (vendorMerge) HearingEq.onBands(test.results, hz, eq.layout.userRangeDb) else HearingEq.interpolated(test.results, hz, eq.layout.userRangeDb)
                parts += Label(s.text("hearing_test_done"), theme.body, theme.secondaryText)
                parts += Panel(
                    Column(
                        test.results.map { r ->
                            val g = r.gainDb.roundToInt()
                            Split(Label(s.text("hearing_test_tone", r.hz.roundToInt())), Label(s.text("db", if (g > 0) "+$g" else if (g < 0) "−${-g}" else "0"), theme.bigNumber.copy(size = 14f), theme.accent, Align.END), 100f) as Widget
                        },
                        6f,
                    ),
                )
                val name = s.text("hearing_test_result")
                parts += Button(s.text("hearing_test_apply", name), "hearing_apply", primary = true) {
                    apply(name, gains)
                    close()
                }
            }
        }
        return Column(parts, 16f)
    }
}
