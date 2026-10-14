package io.github.omegadestroyer.pairfree.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.EarbudsConnection
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.FitResult
import io.github.omegadestroyer.pairfree.protocol.Models
import io.github.omegadestroyer.pairfree.ui.theme.Studio
import kotlinx.coroutines.delay

/**
 * The ear tip fit test (docs/DESIGN.md, Device): how to wear the earbuds, Start, then a
 * result per earbud with what to do about a poor seal. The earbuds run the test themselves
 * and report about 9 s after it starts.
 */
@Composable
internal fun FitTestScreen(
    device: Paired,
    state: EarbudState,
    actions: EarbudsActions,
    onClose: () -> Unit,
    /** Previews start after a run, to show a result. */
    initialRun: Int = 0,
) {
    BackHandler(onBack = onClose)
    // A run counts from Start; its result is the first one to arrive after it.
    var run by rememberSaveable { mutableIntStateOf(initialRun) }
    var timedOut by rememberSaveable { mutableStateOf(false) }
    // Noise control goes off before the test on most models (EarbudsConnection.startFitTest).
    val model = Models.find(state.firmware?.code, device.name)
    val ancOn = (state.number(Command.ANC_MODE) ?: 0) != 0 && model?.fitKeepsAnc != true
    var switchingAnc by rememberSaveable { mutableStateOf(false) }
    val result = state.fit.takeIf { run > 0 }
    val testing = run > 0 && result == null && !timedOut
    val progress = remember { Animatable(0f) }
    LaunchedEffect(run) {
        if (run == 0) return@LaunchedEffect
        progress.snapTo(0f)
        val total = TEST_MS + if (switchingAnc) EarbudsConnection.ANC_OFF_TYPICAL_MS else 0L
        progress.animateTo(1f, tween(total.toInt(), easing = LinearEasing))
    }
    LaunchedEffect(run) {
        if (run == 0) return@LaunchedEffect
        delay(NO_RESULT_MS)
        timedOut = true
    }
    val art = deviceArt(state.firmware?.code, device.name)
    val c = Studio.colors

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.fit_test), Modifier.weight(1f), style = Studio.type.title)
            StudioButton(stringResource(R.string.capture_close), onClose)
        }
        Text(stringResource(R.string.fit_test_intro), color = c.secondaryText)
        if (ancOn || (testing && switchingAnc)) {
            Text(stringResource(R.string.fit_anc_note), style = Studio.type.small, color = c.secondaryText)
        }

        Panel {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Earbud(R.string.left, art.bud, mirrored = false, result?.left, testing, Modifier.weight(1f))
                Earbud(R.string.right, art.bud, mirrored = true, result?.right, testing, Modifier.weight(1f))
            }
            if (testing) {
                LevelBar((progress.value * 100).toInt())
                Text(stringResource(R.string.fit_testing), style = Studio.type.small, color = c.secondaryText)
            }
        }

        when {
            result != null -> {
                val good = result.left == FitResult.Fit.GOOD && result.right == FitResult.Fit.GOOD
                Text(stringResource(if (good) R.string.fit_advice_good else R.string.fit_advice_poor))
            }
            timedOut && run > 0 -> Text(stringResource(R.string.fit_no_result), color = c.destructive)
        }

        StudioButton(
            stringResource(if (run == 0) R.string.fit_start else R.string.fit_again),
            {
                timedOut = false
                switchingAnc = ancOn
                run++
                actions.startFitTest()
            },
            Modifier.fillMaxWidth(),
            primary = true,
            enabled = !testing,
        )
    }
}

@Composable
private fun Earbud(@StringRes side: Int, drawing: Int, mirrored: Boolean, fit: FitResult.Fit?, testing: Boolean, modifier: Modifier) {
    val c = Studio.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(
            painterResource(drawing),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .graphicsLayer(scaleX = if (mirrored) -1f else 1f),
        )
        Label(stringResource(side))
        val (text, color) = when {
            testing -> stringResource(R.string.fit_measuring) to c.secondaryText
            fit == FitResult.Fit.GOOD -> stringResource(R.string.fit_good) to c.accent
            fit == FitResult.Fit.NOT_FIT -> stringResource(R.string.fit_not_fit) to c.destructive
            fit == FitResult.Fit.UNKNOWN -> stringResource(R.string.fit_unknown) to c.secondaryText
            else -> stringResource(R.string.fit_not_tested) to c.label
        }
        Text(text, style = Studio.type.heading, color = color, textAlign = TextAlign.Center)
    }
}

/** The H3 reported about 9 s after Start. */
private const val TEST_MS = 9_000L

/** Give up waiting for a result after this long. */
private const val NO_RESULT_MS = 20_000L
