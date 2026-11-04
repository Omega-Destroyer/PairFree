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
import io.github.omegadestroyer.pairfree.eq.EqState
import io.github.omegadestroyer.pairfree.hearing.TonePlayer
import io.github.omegadestroyer.pairfree.protocol.hearing.HearingEq
import io.github.omegadestroyer.pairfree.protocol.hearing.HearingTable
import io.github.omegadestroyer.pairfree.protocol.hearing.StaircaseTest
import io.github.omegadestroyer.pairfree.ui.theme.Studio
import kotlin.math.roundToInt

/**
 * A hearing test as the brand's own app runs it (docs/DESIGN.md, Sound): tones from loud to quiet at each frequency, the
 * user saying which they hear, then the result as an EQ on the model's bands, saved under [resultName] and applied. So
 * far SOUNDPEATS' (docs/soundpeats/PROTOCOL.md section 13).
 */
@Composable
internal fun HearingTestScreen(
    eq: EqState,
    table: HearingTable,
    resultName: String,
    onApply: (name: String, gains: List<Int>) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember { TonePlayer(context) }
    val test = remember { StaircaseTest(table) }
    var started by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // Bumped after every answer, so the next tone plays and the screen follows the test.
    var step by remember { mutableIntStateOf(0) }
    val close = {
        player.end()
        onClose()
    }
    BackHandler(onBack = close)
    DisposableEffect(Unit) { onDispose { player.end() } }
    LaunchedEffect(started, step) {
        if (!started) return@LaunchedEffect
        val tone = test.current
        if (tone == null) {
            player.end()
            return@LaunchedEffect
        }
        player.play(tone.hz, tone.levelDb, table.seconds) {
            test.answer(heard = false)
            step++
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
                Text(stringResource(R.string.hearing_test_intro), color = c.secondaryText)
                if (busy) Text(stringResource(R.string.hearing_test_music), color = c.destructive)
                StudioButton(
                    stringResource(R.string.hearing_test_start),
                    {
                        if (player.musicActive) {
                            busy = true
                        } else {
                            busy = false
                            player.begin()
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
                    Text(stringResource(R.string.hearing_test_tone, tone?.hz?.roundToInt() ?: 0), style = Studio.type.number)
                    LevelBar((test.progress * 100).toInt())
                    Text(stringResource(R.string.hearing_test_listen), style = Studio.type.small, color = c.secondaryText)
                }
                StudioButton(
                    stringResource(R.string.hearing_test_heard),
                    {
                        player.stop()
                        test.answer(heard = true)
                        step++
                    },
                    Modifier.fillMaxWidth(),
                    primary = true,
                )
            }
            else -> {
                val gains = HearingEq.onBands(test.results, eq.layout.bands.map { it.hz }, eq.layout.userRangeDb)
                Text(stringResource(R.string.hearing_test_done), color = c.secondaryText)
                Panel {
                    test.results.forEach { r ->
                        Row {
                            Text(stringResource(R.string.hearing_test_tone, r.hz.roundToInt()), Modifier.weight(1f))
                            Text(stringResource(R.string.db, "%+d".format(r.gainDb.roundToInt())), style = Studio.type.number, color = c.accent)
                        }
                    }
                }
                StudioButton(
                    stringResource(R.string.hearing_test_apply, resultName),
                    {
                        onApply(resultName, gains)
                        close()
                    },
                    Modifier.fillMaxWidth(),
                    primary = true,
                )
            }
        }
    }
}
