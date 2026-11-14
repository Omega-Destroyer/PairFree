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
import io.github.omegadestroyer.pairfree.eq.EqActions
import io.github.omegadestroyer.pairfree.eq.EqState
import io.github.omegadestroyer.pairfree.protocol.hearing.EqFinder
import io.github.omegadestroyer.pairfree.ui.theme.Studio
import kotlin.math.roundToInt

/**
 * PairFree's own A/B sound finder ([EqFinder]): the user plays their own music and switches between two curves on the earbuds, then keeps the one they
 * prefer; at the end the best curve can be saved as "My sound". Works for any earbuds with a custom curve. Leaving early puts the earbuds back as they were.
 */
@Composable
internal fun EqFinderScreen(eq: EqState, actions: EqActions, resultName: String, onClose: () -> Unit) {
    val finder = remember { EqFinder(eq.layout.bands.map { it.hz }, eq.layout.userRangeDb, eq.setting.gains.map { it.roundToInt() }) }
    val original = remember { eq.selected to eq.setting.gains }
    var started by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(0) }
    var listeningB by remember { mutableStateOf(false) }
    var kept by remember { mutableStateOf(false) }
    val restore = {
        val (profile, gains) = original
        if (profile != null) actions.select(profile) else actions.previewDb(gains)
    }
    val close = {
        if (!kept) restore()
        onClose()
    }
    BackHandler(onBack = close)
    val c = Studio.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.finder_title), Modifier.weight(1f), style = Studio.type.title)
            StudioButton(stringResource(R.string.capture_close), close)
        }
        when {
            !started -> {
                Text(stringResource(R.string.finder_intro), color = c.secondaryText)
                StudioButton(
                    stringResource(R.string.hearing_test_start),
                    {
                        started = true
                        listeningB = false
                        actions.preview(finder.best)
                    },
                    Modifier.fillMaxWidth(),
                    primary = true,
                )
            }
            !finder.finished -> {
                // `step` makes this recompose after an answer, since the finder holds its own state.
                step.let {
                    Panel {
                        Text(stringResource(R.string.finder_round, finder.answered + 1, EqFinder.MAX_TRIALS), style = Studio.type.heading)
                        LevelBar((finder.progress * 100).toInt())
                        Text(stringResource(R.string.finder_listen), style = Studio.type.small, color = c.secondaryText)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StudioButton(
                        stringResource(R.string.finder_a),
                        {
                            listeningB = false
                            actions.preview(finder.best)
                        },
                        Modifier.weight(1f),
                        primary = !listeningB,
                    )
                    StudioButton(
                        stringResource(R.string.finder_b),
                        {
                            listeningB = true
                            finder.candidate?.let(actions::preview)
                        },
                        Modifier.weight(1f),
                        primary = listeningB,
                    )
                }
                StudioButton(
                    stringResource(R.string.finder_keep),
                    {
                        finder.choose(listeningB)
                        listeningB = false
                        step++
                        // The next pair starts on A, which is the best curve so far.
                        actions.preview(finder.best)
                    },
                    Modifier.fillMaxWidth(),
                    primary = true,
                )
            }
            else -> {
                Text(stringResource(R.string.finder_done), color = c.secondaryText)
                StudioButton(
                    stringResource(R.string.finder_save, resultName),
                    {
                        actions.applyCurve(resultName, finder.best)
                        kept = true
                        close()
                    },
                    Modifier.fillMaxWidth(),
                    primary = true,
                )
                StudioButton(stringResource(R.string.finder_discard), close, Modifier.fillMaxWidth())
            }
        }
    }
}
