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
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.hearing.TonePlayer
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungHearingTest
import io.github.omegadestroyer.pairfree.ui.theme.Studio
import kotlinx.coroutines.delay

/**
 * Galaxy Buds' Adapt Sound hearing test (docs/samsung/PROTOCOL.md section 14), as Samsung's app runs it: the earbuds make the tones, one earbud and one
 * pitch at a time, and the user says whether they hear it. The messages for each step come from [SamsungHearingTest] and go out through [send]; the same
 * message is sent again every 800 ms while a tone plays, as Samsung's app does, and the result is put on the earbuds as their Adapt Sound profile.
 */
@Composable
internal fun SamsungHearingTestScreen(legacy: Boolean, send: (List<ByteArray>) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val test = remember { SamsungHearingTest(legacy) }
    var started by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var applied by remember { mutableStateOf(false) }
    // Bumped after every answer, so the next tone starts and the screen follows the test.
    var step by remember { mutableIntStateOf(0) }
    val close = {
        if (started && !applied) send(test.cancel())
        onClose()
    }
    BackHandler(onBack = close)
    DisposableEffect(Unit) { onDispose { if (started && !applied) send(test.cancel()) } }
    LaunchedEffect(started, step) {
        if (!started) return@LaunchedEffect
        val tone = test.current
        if (tone == null) {
            send(test.apply())
            applied = true
            return@LaunchedEffect
        }
        send(test.play(tone))
        while (true) {
            delay(REPEAT_MS)
            send(test.again())
        }
    }
    val c = Studio.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.hearing_test), Modifier.weight(1f), style = Studio.type.title)
            StudioButton(stringResource(R.string.capture_close), close)
        }
        when {
            !started -> {
                Text(stringResource(R.string.samsung_hearing_intro), color = c.secondaryText)
                if (busy) Text(stringResource(R.string.hearing_test_music), color = c.destructive)
                StudioButton(
                    stringResource(R.string.hearing_test_start),
                    {
                        if (TonePlayer(context).musicActive) {
                            busy = true
                        } else {
                            busy = false
                            send(test.start())
                            started = true
                        }
                    },
                    Modifier.fillMaxWidth(),
                    primary = true,
                )
            }
            !test.finished -> {
                val tone = test.current
                Panel {
                    val side = if (tone?.left == true) R.string.samsung_hearing_left else R.string.samsung_hearing_right
                    Text(stringResource(R.string.samsung_hearing_tone, stringResource(side)), style = Studio.type.number)
                    LevelBar((test.progress * 100).toInt())
                    Text(stringResource(R.string.samsung_hearing_listen), style = Studio.type.small, color = c.secondaryText)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (heard in listOf(true, false)) {
                        StudioButton(
                            stringResource(if (heard) R.string.samsung_hearing_yes else R.string.samsung_hearing_no),
                            {
                                send(test.stop())
                                test.answer(heard)
                                step++
                            },
                            Modifier.weight(1f),
                            primary = heard,
                        )
                    }
                }
            }
            else -> {
                Text(stringResource(R.string.samsung_hearing_done), color = c.secondaryText)
                Panel {
                    SamsungHearingTest.FREQUENCIES.forEachIndexed { i, hz ->
                        Row {
                            Text(stringResource(R.string.hearing_test_tone, hz), Modifier.weight(1f))
                            Text("${test.left[i]} / ${test.right[i]}", style = Studio.type.number, color = c.accent)
                        }
                    }
                }
                StudioButton(stringResource(R.string.capture_close), close, Modifier.fillMaxWidth(), primary = true)
            }
        }
    }
}

private const val REPEAT_MS = 800L
