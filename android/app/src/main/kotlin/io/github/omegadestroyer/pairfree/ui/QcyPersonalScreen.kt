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
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyPersonal
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyPersonalTest
import io.github.omegadestroyer.pairfree.ui.theme.Look

/**
 * QCY's personalised listening test (docs/qcy/PROTOCOL.md section 8), as QCY's app runs it: the earbuds are put in their test mode, a tone of each band plays in one earbud at
 * a time (the phone makes it), and the user says whether they hear it until each band is settled, left earbud first. The two curves it finds go to the earbuds as their left
 * and right equalizer. The steps are [QcyPersonalTest]'s and go out through [send].
 */
@Composable
internal fun QcyPersonalScreen(spec: QcyPersonal, send: (List<ByteArray>) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val player = remember { TonePlayer(context) }
    var run by remember { mutableIntStateOf(0) }
    val test = remember(run) { QcyPersonalTest(spec) }
    var started by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var applied by remember { mutableStateOf(false) }
    // Bumped after every answer, so the next tone starts and the screen follows the test.
    var step by remember { mutableIntStateOf(0) }
    val close = {
        player.stop()
        onClose()
    }
    BackHandler(onBack = close)
    // The earbuds stay in their test mode while the screen is open, and leave it when it closes, as QCY's app does.
    DisposableEffect(started) {
        if (started) send(listOf(QcyPersonal.testMode(true)))
        onDispose {
            player.stop()
            if (started) send(listOf(QcyPersonal.testMode(false)))
        }
    }
    DisposableEffect(started, step, run) {
        if (started && !test.finished) player.playSteady(test.toneHz, test.toneDb, if (test.ear == 0) TonePlayer.LEFT else TonePlayer.RIGHT) else player.stop()
        onDispose { }
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
            Text(stringResource(R.string.hearing_test), Modifier.weight(1f), style = Look.type.title)
            AppButton(stringResource(R.string.capture_close), close)
        }
        when {
            !started -> {
                Text(stringResource(R.string.qcy_personal_intro), color = c.secondaryText)
                if (busy) Text(stringResource(R.string.hearing_test_music), color = c.destructive)
                AppButton(
                    stringResource(R.string.hearing_test_start),
                    {
                        busy = player.musicActive
                        if (!busy) started = true
                    },
                    Modifier.fillMaxWidth(),
                    primary = true,
                )
            }
            !test.finished -> {
                Panel {
                    val side = if (test.ear == 0) R.string.left else R.string.right
                    Text(
                        stringResource(R.string.qcy_personal_tone, stringResource(side), test.toneHz, test.done + 1, test.total),
                        style = Look.type.number,
                    )
                    LevelBar(test.done * 100 / test.total)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppButton(stringResource(R.string.qcy_personal_heard), {
                        test.heard()
                        step++
                    }, Modifier.weight(1f), primary = true)
                    AppButton(stringResource(R.string.qcy_personal_not_heard), {
                        test.notHeard()
                        step++
                    }, Modifier.weight(1f))
                }
            }
            else -> {
                Text(stringResource(if (applied) R.string.qcy_personal_used else R.string.qcy_personal_done), color = c.secondaryText)
                Panel {
                    spec.bands.forEachIndexed { i, band ->
                        Row {
                            Text(stringResource(R.string.hearing_test_tone, band.hz), Modifier.weight(1f))
                            Text(signed("${test.left[i]} / ${test.right[i]}"), style = Look.type.number, color = c.accent)
                        }
                    }
                }
                if (!applied) {
                    AppButton(
                        stringResource(R.string.qcy_personal_use),
                        {
                            send(test.frames())
                            applied = true
                        },
                        Modifier.fillMaxWidth(),
                        primary = true,
                    )
                }
                AppButton(
                    stringResource(R.string.qcy_personal_again),
                    {
                        applied = false
                        started = false
                        run++
                    },
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
