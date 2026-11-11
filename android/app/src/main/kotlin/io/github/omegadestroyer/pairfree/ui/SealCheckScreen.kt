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
import io.github.omegadestroyer.pairfree.protocol.hearing.SealCheck
import io.github.omegadestroyer.pairfree.ui.theme.Studio

/**
 * The ear tip seal check with tones from the phone ([SealCheck], after JBL's): a low tone in one earbud at a time, normal and then softer, and the user
 * says whether they hear it. Works with any earbuds; the phone's media volume goes to the top for the check and back after, as the hearing test does.
 */
@Composable
internal fun SealCheckScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val player = remember { TonePlayer(context) }
    val check = remember { SealCheck() }
    var started by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(0) }
    val close = {
        player.end()
        onClose()
    }
    BackHandler(onBack = close)
    DisposableEffect(Unit) { onDispose { player.end() } }
    LaunchedEffect(started, step) {
        if (!started) return@LaunchedEffect
        val tone = check.current
        if (tone == null) {
            player.end()
            return@LaunchedEffect
        }
        // The tone repeats until the user answers: a low tone is easy to miss in one short burst.
        player.playOnSide(SealCheck.HZ, tone.levelDb, SealCheck.SECONDS, if (tone.left) TonePlayer.LEFT else TonePlayer.RIGHT, loop = true)
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
            Text(stringResource(R.string.seal_check), Modifier.weight(1f), style = Studio.type.title)
            StudioButton(stringResource(R.string.capture_close), close)
        }
        when {
            !started -> {
                Text(stringResource(R.string.seal_check_intro), color = c.secondaryText)
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
            !check.finished -> {
                val tone = check.current
                Panel {
                    val side = if (tone?.left == true) R.string.samsung_hearing_left else R.string.samsung_hearing_right
                    Text(
                        stringResource(if (tone?.softer == true) R.string.seal_check_softer else R.string.seal_check_normal, stringResource(side)),
                        style = Studio.type.heading,
                    )
                    LevelBar((check.progress * 100).toInt())
                    Text(stringResource(R.string.seal_check_listen), style = Studio.type.small, color = c.secondaryText)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (heard in listOf(true, false)) {
                        StudioButton(
                            stringResource(if (heard) R.string.samsung_hearing_yes else R.string.samsung_hearing_no),
                            {
                                player.stop()
                                check.answer(heard)
                                step++
                            },
                            Modifier.weight(1f),
                            primary = heard,
                        )
                    }
                }
            }
            else -> {
                Panel {
                    for ((side, seal) in listOf(R.string.samsung_hearing_left to check.left, R.string.samsung_hearing_right to check.right)) {
                        Row {
                            Text(stringResource(side), Modifier.weight(1f))
                            Text(
                                stringResource(
                                    when (seal) {
                                        SealCheck.Seal.GOOD -> R.string.seal_check_good
                                        SealCheck.Seal.FAIR -> R.string.seal_check_fair
                                        else -> R.string.seal_check_poor
                                    },
                                ),
                                style = Studio.type.number,
                                color = if (seal == SealCheck.Seal.GOOD) c.accent else c.destructive,
                            )
                        }
                    }
                }
                Text(stringResource(R.string.seal_check_advice), style = Studio.type.small, color = c.secondaryText)
                StudioButton(stringResource(R.string.capture_close), close, Modifier.fillMaxWidth(), primary = true)
            }
        }
    }
}
