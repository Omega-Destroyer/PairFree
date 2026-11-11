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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.protocol.oneplus.OneplusHearingTest
import io.github.omegadestroyer.pairfree.ui.theme.Studio

/**
 * HeyMelody's hearing enhancement test on OnePlus and OPPO earbuds (docs/oneplus/PROTOCOL.md section 12): the earbuds play a tone for each of 12 points (six
 * pitches per earbud) and the user moves a slider to the quietest level they still hear. Every move sends that point's tone ([OneplusHearingTest]); at the
 * end the earbuds are given the points and the enhancement is switched on.
 */
@Composable
internal fun OneplusHearingTestScreen(test: OneplusHearingTest, send: (List<ByteArray>) -> Unit, onClose: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    // Read after every change so the screen follows the test, which holds its own state.
    var tick by remember { mutableIntStateOf(0) }
    val close = {
        if (started && !done) send(test.cancel())
        onClose()
    }
    BackHandler(onBack = close)
    DisposableEffect(Unit) { onDispose { if (started && !done) send(test.cancel()) } }
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
                Text(stringResource(R.string.oneplus_hearing_intro), color = c.secondaryText)
                StudioButton(
                    stringResource(R.string.hearing_test_start),
                    {
                        send(test.start() + test.tone())
                        started = true
                    },
                    Modifier.fillMaxWidth(),
                    primary = true,
                )
            }
            !done -> {
                val point = test.current
                val last = test.points.indexOf(point) == test.points.size - 1
                // `tick` makes this recompose after the slider or Next changed the test's state.
                tick.let {
                    Panel {
                        val side = if (point?.left == true) R.string.samsung_hearing_left else R.string.samsung_hearing_right
                        Text(stringResource(R.string.oneplus_hearing_point, stringResource(side), point?.type ?: 0, OneplusHearingTest.TYPES), style = Studio.type.heading)
                        LevelBar((test.progress * 100).toInt())
                        Text(stringResource(R.string.oneplus_hearing_listen), style = Studio.type.small, color = c.secondaryText)
                        StudioSlider(
                            test.level,
                            0..OneplusHearingTest.LEVELS_DB.size - 1,
                            { send(test.setLevel(it)); tick++ },
                            Modifier.fillMaxWidth(),
                            stringResource(R.string.db, "%+d".format(OneplusHearingTest.LEVELS_DB[test.level])),
                        )
                    }
                }
                StudioButton(
                    stringResource(if (last) R.string.oneplus_hearing_finish else R.string.oneplus_hearing_next),
                    {
                        send(test.next())
                        if (last) {
                            send(test.save())
                            done = true
                        }
                        tick++
                    },
                    Modifier.fillMaxWidth(),
                    primary = true,
                )
            }
            else -> {
                Text(stringResource(R.string.oneplus_hearing_done), color = c.secondaryText)
                StudioButton(stringResource(R.string.capture_close), close, Modifier.fillMaxWidth(), primary = true)
            }
        }
    }
}
