package io.github.omegadestroyer.pairfree.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.brands.SoundcoreHearingHost
import io.github.omegadestroyer.pairfree.hearing.TonePlayer
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreHearingRun
import io.github.omegadestroyer.pairfree.ui.theme.Look
import kotlinx.coroutines.delay

/**
 * Soundcore's HearID hearing test (docs/soundcore/PROTOCOL.md, HearID), run as its app runs it: a tone sounds in one earbud, the user says whether they hear it, and
 * the next one is louder or quieter until the threshold is found, eight tones for each ear. The earbuds are told the test runs, and the tones start 1.5 s later.
 * Leaving the screen, or the app, ends the test and puts the media volume back.
 */
@Composable
internal fun SoundcoreHearingScreen(host: SoundcoreHearingHost, onClose: () -> Unit) {
    val context = LocalContext.current
    val player = remember { TonePlayer(context) }
    var run by remember { mutableStateOf(SoundcoreHearingRun(host.spec)) }
    var testing by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var silent by remember { mutableStateOf(false) }
    var applied by remember { mutableStateOf<Boolean?>(null) }
    // Bumped after every answer: the run is not state, and each bump starts the tone it now asks for.
    var step by remember { mutableIntStateOf(0) }

    fun stopTest() {
        if (testing) {
            testing = false
            ready = false
            player.end()
            host.testing(false)
        }
    }

    fun leave() {
        stopTest()
        onClose()
    }
    BackHandler(onBack = ::leave)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) stopTest() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            stopTest()
        }
    }
    LaunchedEffect(testing) {
        if (testing) {
            delay(START_MS)
            ready = true
        }
    }
    LaunchedEffect(ready, step) {
        if (!ready) return@LaunchedEffect
        if (run.finished) {
            stopTest()
            return@LaunchedEffect
        }
        player.stop()
        delay(GAP_MS)
        // The earbuds can go while the test runs; the volume is at the top, so nothing may play without them.
        if (!player.bluetoothOutput) {
            silent = true
            stopTest()
            return@LaunchedEffect
        }
        player.begin()
        val side = if (run.left) TonePlayer.LEFT else TonePlayer.RIGHT
        if (host.spec.pulsed) player.playBeeps(run.hz, run.level, side) else player.playSteady(run.hz, run.level, side)
    }

    val c = Look.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sc_hearing_title), Modifier.weight(1f), style = Look.type.title)
            AppButton(stringResource(R.string.capture_close), ::leave)
        }
        val result = step.let { run.result() }
        when {
            result != null -> {
                Text(stringResource(R.string.sc_hearing_done), color = c.secondaryText)
                Panel {
                    for ((label, values) in listOf(R.string.personifi_left_levels to result.left, R.string.personifi_right_levels to result.right)) {
                        Text(stringResource(label), color = c.secondaryText)
                        for ((band, value) in values.withIndex()) {
                            Row {
                                Text(stringResource(R.string.hearing_test_tone, HZ[band]), Modifier.weight(1f))
                                val change = value - FLAT
                                Text(signed(if (change > 0) "+$change dB" else "$change dB"), style = Look.type.number, color = c.accent)
                            }
                        }
                    }
                }
                when (applied) {
                    true -> Text(stringResource(R.string.sc_hearing_applied), color = c.accent)
                    false -> Text(stringResource(R.string.sc_hearing_failed), color = c.destructive)
                    null -> Unit
                }
                if (applied != true) {
                    AppButton(stringResource(R.string.sc_hearing_apply), { applied = host.save(result) }, Modifier.fillMaxWidth(), primary = true)
                }
                AppButton(stringResource(R.string.capture_close), ::leave, Modifier.fillMaxWidth())
            }
            !testing -> {
                Text(stringResource(R.string.sc_hearing_intro), color = c.secondaryText)
                if (busy) Text(stringResource(R.string.hearing_test_music), color = c.destructive)
                if (silent) Text(stringResource(R.string.sc_hearing_no_output), color = c.destructive)
                AppButton(
                    stringResource(R.string.hearing_test_start),
                    {
                        busy = player.musicActive
                        silent = !player.bluetoothOutput
                        if (!busy && !silent) {
                            run = SoundcoreHearingRun(host.spec)
                            applied = null
                            host.testing(true)
                            testing = true
                        }
                    },
                    Modifier.fillMaxWidth(),
                    primary = true,
                )
            }
            else -> {
                val at = step.let { run.done }
                val ear = stringResource(if (run.left) R.string.personifi_left else R.string.personifi_right)
                Panel {
                    Text(stringResource(R.string.personifi_tone_of, at + 1, run.total, ear), style = Look.type.number)
                    Text(stringResource(R.string.hearing_test_tone, run.hz), color = c.secondaryText)
                    LevelBar(at * 100 / run.total)
                }
                Text(stringResource(if (ready) R.string.sc_hearing_question else R.string.sc_hearing_wait), color = c.secondaryText)
                fun answer(heard: Boolean) {
                    run.answer(heard)
                    step++
                }
                AppButton(stringResource(R.string.sc_hearing_yes), { answer(true) }, Modifier.fillMaxWidth(), enabled = ready, primary = true)
                AppButton(stringResource(R.string.sc_hearing_no), { answer(false) }, Modifier.fillMaxWidth(), enabled = ready)
                AppButton(stringResource(R.string.sc_hearing_again), { run.repeat(); step++ }, Modifier.fillMaxWidth(), enabled = ready)
            }
        }
    }
}

private val HZ = listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800)

/** The value that changes nothing, in the app's scale. */
private const val FLAT = 12
private const val START_MS = 1500L
private const val GAP_MS = 100L
