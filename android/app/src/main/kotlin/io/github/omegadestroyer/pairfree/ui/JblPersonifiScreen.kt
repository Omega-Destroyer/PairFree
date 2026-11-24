package io.github.omegadestroyer.pairfree.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.brands.PersonifiHost
import io.github.omegadestroyer.pairfree.hearing.TonePlayer
import io.github.omegadestroyer.pairfree.protocol.jbl.JblPersonifiRun
import io.github.omegadestroyer.pairfree.protocol.jbl.JblWarble
import io.github.omegadestroyer.pairfree.ui.theme.Look
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * JBL's PersoniFi hearing test (docs/jbl/PROTOCOL.md section 4.14), run as JBL's app runs it: a tone fades in one earbud at a time, the user holds a button while they
 * can hear it and lets go when it is gone, and how long they held says how quiet a tone they heard. The earbuds are told which tone is next and PairFree waits for them to say
 * they have it before the tone plays, as JBL's app does. The tone is PairFree's own warble ([JblWarble]).
 */
@Composable
internal fun JblPersonifiScreen(host: PersonifiHost, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val run = remember { JblPersonifiRun(host.openFit) }
    val player = remember { TonePlayer(context) }
    var started by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var applied by remember { mutableStateOf<Boolean?>(null) }
    var waiting by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf(false) }
    var silent by remember { mutableStateOf(false) }
    // Bumped after every tone, so the screen follows the run (which is not state).
    var step by remember { mutableIntStateOf(0) }

    fun leave() {
        if (started) {
            player.end()
            host.end()
            started = false
        }
        onClose()
    }
    BackHandler(onBack = ::leave)
    DisposableEffect(Unit) {
        onDispose {
            if (started) {
                player.end()
                host.end()
            }
        }
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
            Text(stringResource(R.string.personifi_test), Modifier.weight(1f), style = Look.type.title)
            AppButton(stringResource(R.string.capture_close), ::leave)
        }
        val tone = step.let { run.current }
        when {
            !started -> {
                Text(stringResource(R.string.personifi_intro), color = c.secondaryText)
                if (busy) Text(stringResource(R.string.hearing_test_music), color = c.destructive)
                AppButton(
                    stringResource(R.string.hearing_test_start),
                    {
                        if (player.musicActive) {
                            busy = true
                        } else {
                            busy = false
                            player.begin()
                            host.begin()
                            started = true
                        }
                    },
                    Modifier.fillMaxWidth(),
                    primary = true,
                )
            }
            tone != null -> {
                val ear = stringResource(if (tone.left) R.string.personifi_left else R.string.personifi_right)
                Panel {
                    Text(stringResource(R.string.personifi_tone_of, run.tones.indexOf(tone) + 1, run.tones.size, ear), style = Look.type.number)
                    Text(stringResource(R.string.hearing_test_tone, tone.hz), color = c.secondaryText)
                    LevelBar((run.progress * 100).toInt())
                }
                Text(
                    stringResource(
                        when {
                            problem -> R.string.personifi_problem
                            silent -> R.string.personifi_silent
                            waiting -> R.string.personifi_waiting
                            else -> R.string.personifi_listen
                        },
                    ),
                    color = if (problem) c.destructive else c.secondaryText,
                )
                // The hold button: the tone starts once the earbuds are ready, and its length is how long the finger stays down.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .surface(Look.shape.panel, if (holding) c.accent else c.panel, c.outline)
                        .pointerInput(tone) {
                            detectTapGestures(
                                onPress = {
                                    problem = false
                                    silent = false
                                    holding = true
                                    waiting = true
                                    var startedAt = 0L
                                    var done = false
                                    val side = tone.left
                                    host.tone(side, tone.hz)
                                    val job: Job = scope.launch {
                                        var waited = 0
                                        while (host.readyHz != tone.hz) {
                                            if (host.status.let { it == 3 || it == 4 }) {
                                                problem = true
                                                waiting = false
                                                return@launch
                                            }
                                            delay(POLL_MS)
                                            waited += POLL_MS.toInt()
                                            if (waited > READY_WAIT_MS) problem = true
                                        }
                                        problem = false
                                        waiting = false
                                        startedAt = SystemClock.elapsedRealtime()
                                        player.playWarble(JblWarble.samples(tone.hz, TonePlayer.WARBLE_RATE), tone.left) {
                                            // The tone faded out while the finger was still down: the whole scale was heard.
                                            if (!done) {
                                                done = true
                                                holding = false
                                                run.heard(JblWarble.SECONDS + 1L)
                                                step++
                                            }
                                        }
                                    }
                                    tryAwaitRelease()
                                    job.cancel()
                                    holding = false
                                    waiting = false
                                    if (!done && startedAt != 0L) {
                                        done = true
                                        player.stop()
                                        run.heard((SystemClock.elapsedRealtime() - startedAt) / 1000)
                                        step++
                                    } else if (!done) {
                                        // Let go before the tone came: nothing was heard to measure; the next press starts it again.
                                        silent = true
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.personifi_hold), style = Look.type.title)
                }
                AppButton(stringResource(R.string.personifi_back), { run.back(); step++ }, Modifier.fillMaxWidth(), enabled = run.tones.indexOf(tone) > 0)
            }
            else -> {
                val curve = run.curve()
                Text(stringResource(R.string.personifi_done), color = c.secondaryText)
                Panel {
                    for (left in listOf(true, false)) {
                        Text(stringResource(if (left) R.string.personifi_left_levels else R.string.personifi_right_levels), color = c.secondaryText)
                        for (t in run.tones.filter { it.left == left }) {
                            Row {
                                Text(stringResource(R.string.hearing_test_tone, t.hz), Modifier.weight(1f))
                                Text("${t.level?.toInt() ?: 0} dB", style = Look.type.number, color = c.accent)
                            }
                        }
                    }
                }
                when (applied) {
                    true -> Text(stringResource(R.string.personifi_applied), color = c.accent)
                    false -> Text(stringResource(R.string.personifi_cannot), color = c.destructive)
                    null -> Unit
                }
                if (applied != true && curve != null) {
                    AppButton(
                        stringResource(R.string.personifi_apply),
                        { applied = host.apply(curve) },
                        Modifier.fillMaxWidth(),
                        primary = true,
                    )
                }
                AppButton(stringResource(R.string.capture_close), ::leave, Modifier.fillMaxWidth())
            }
        }
    }
}

private const val POLL_MS = 100L
private const val READY_WAIT_MS = 4000
