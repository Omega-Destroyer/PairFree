package io.github.omegadestroyer.pairfree.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.blockedByLdac
import io.github.omegadestroyer.pairfree.protocol.offers
import io.github.omegadestroyer.pairfree.ui.theme.Studio
import kotlinx.coroutines.delay

/** Wait this long after the last change of a slider or stepper before sending it. */
private const val SEND_AFTER_MS = 400L

private enum class Picker { LANGUAGE, SHUTDOWN }

/** The earbuds themselves (docs/DESIGN.md, Device). Sections show only for what the earbuds answered. */
@Composable
internal fun DeviceScreen(device: Paired, state: EarbudState, model: Model?, actions: EarbudsActions, onFitTest: () -> Unit = {}) {
    var picker by remember { mutableStateOf<Picker?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val c = Studio.colors

    TabContent {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(device.name, style = Studio.type.title)
            state.firmware?.let {
                Label(stringResource(R.string.firmware, it.version))
                Label(it.text)
            }
        }

        val connection = listOf(
            Command.MULTIPOINT to R.string.two_devices,
            Command.LDAC to R.string.ldac,
            Command.LE_AUDIO to R.string.le_audio,
        ).filter { state.offers(it.first, model) }
        if (connection.isNotEmpty()) {
            Panel {
                Label(stringResource(R.string.connection))
                connection.forEachIndexed { i, (command, title) ->
                    if (i > 0) Divider()
                    val blocked = state.blockedByLdac(command, model)
                    SwitchRow(
                        stringResource(title),
                        on = state.switch(command) == true,
                        onChange = { actions.set(command, if (it) 1 else 0) },
                        note = when {
                            blocked -> stringResource(R.string.needs_ldac_off)
                            command == Command.LE_AUDIO -> stringResource(R.string.le_audio_note)
                            else -> null
                        },
                        enabled = !blocked,
                    )
                }
                if (model?.ldacOrMultipoint == true) {
                    Text(stringResource(R.string.ldac_or_multipoint), style = Studio.type.small, color = c.secondaryText)
                }
            }
        }

        if (state.hasFitTest) {
            Panel {
                Label(stringResource(R.string.ear_tips))
                Text(stringResource(R.string.fit_test_about), style = Studio.type.small, color = c.secondaryText)
                StudioButton(stringResource(R.string.fit_test_open), onFitTest, Modifier.fillMaxWidth())
            }
        }

        if (state.supports(Command.WATER_DRAIN)) {
            Panel {
                Label(stringResource(R.string.water_drain))
                val draining = state.switch(Command.WATER_DRAIN) == true
                Text(
                    stringResource(if (draining) R.string.water_draining else R.string.water_drain_about),
                    style = Studio.type.small,
                    color = c.secondaryText,
                )
                StudioButton(
                    stringResource(R.string.water_drain_start),
                    { actions.set(Command.WATER_DRAIN, 1) },
                    Modifier.fillMaxWidth(),
                    enabled = !draining,
                )
            }
        }

        val volume = state.supports(Command.PROMPT_VOLUME)
        val language = state.number(Command.VOICE_LANGUAGE)
        val shutdown = state.number(Command.AUTO_SHUTDOWN)
        if (volume || language != null || shutdown != null) {
            Panel {
                Label(stringResource(R.string.prompts_and_power))
                if (volume) PromptVolume(state, actions)
                if (language != null) {
                    if (volume) Divider()
                    ValueRow(stringResource(R.string.prompt_language), languageName(language), { picker = Picker.LANGUAGE })
                }
                if (shutdown != null) {
                    if (volume || language != null) Divider()
                    ValueRow(stringResource(R.string.auto_shutdown), shutdownName(shutdown), { picker = Picker.SHUTDOWN })
                }
            }
        }

        val finds = listOf(Command.FIND_LEFT to Side.LEFT, Command.FIND_RIGHT to Side.RIGHT).filter { state.supports(it.first) }
        if (finds.isNotEmpty()) {
            Panel {
                Label(stringResource(R.string.find_earbuds))
                Text(stringResource(R.string.find_warning), style = Studio.type.small, color = c.secondaryText)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((command, side) in finds) {
                        val beeping = state.switch(command) == true
                        StudioButton(
                            stringResource(if (beeping) R.string.stop_beep else R.string.beep, sideName(side)),
                            { actions.set(command, numberOf(command, if (beeping) "stop" else "beep")) },
                            Modifier.weight(1f),
                            primary = beeping,
                        )
                    }
                }
            }
        }

        Panel {
            Label(stringResource(R.string.maintenance))
            val copyReport = stringResource(R.string.copy_report)
            StudioButton(
                copyReport,
                {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText(copyReport, actions.deviceReport()))
                    copied = true
                },
                Modifier.fillMaxWidth(),
            )
            if (copied) Text(stringResource(R.string.report_copied), style = Studio.type.small, color = c.secondaryText)
            // Recording from the official app and factory reset are known for SoundPEATS only so far.
            if (device.brand == Brand.SOUNDPEATS) {
                StudioButton(stringResource(R.string.capture_open), actions::openCapture, Modifier.fillMaxWidth())
                StudioButton(stringResource(R.string.factory_reset), { confirmReset = true }, Modifier.fillMaxWidth(), destructive = true)
            }
        }

        Text(
            stringResource(R.string.privacy_line),
            Modifier.fillMaxWidth(),
            style = Studio.type.small,
            color = c.label,
            textAlign = TextAlign.Center,
        )
    }

    when (picker) {
        Picker.LANGUAGE -> PickerDialog(
            stringResource(R.string.prompt_language),
            Command.VOICE_LANGUAGE.labels.keys.sorted(),
            state.number(Command.VOICE_LANGUAGE),
            { languageName(it) },
            { actions.set(Command.VOICE_LANGUAGE, it) },
            { picker = null },
        )
        Picker.SHUTDOWN -> PickerDialog(
            stringResource(R.string.auto_shutdown),
            Command.AUTO_SHUTDOWN.labels.keys.sorted(),
            state.number(Command.AUTO_SHUTDOWN),
            { shutdownName(it) },
            { actions.set(Command.AUTO_SHUTDOWN, it) },
            { picker = null },
        )
        null -> Unit
    }
    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.factory_reset_title),
            body = stringResource(R.string.factory_reset_body),
            confirm = stringResource(R.string.factory_reset),
            onConfirm = actions::factoryResetConfirmedByUser,
            onDismiss = { confirmReset = false },
            destructive = true,
        )
    }
}

/**
 * Prompt volume, 0 to 100 as docs/soundpeats/PROTOCOL.md gives it, with a slider and minus and plus.
 * The value goes out once the user stops changing it.
 */
@Composable
private fun PromptVolume(state: EarbudState, actions: EarbudsActions) {
    var pending by remember { mutableStateOf<Int?>(null) }
    val current = pending ?: state.number(Command.PROMPT_VOLUME)
    LaunchedEffect(pending) {
        val value = pending ?: return@LaunchedEffect
        delay(SEND_AFTER_MS)
        actions.set(Command.PROMPT_VOLUME, value)
        pending = null
    }
    val title = stringResource(R.string.prompt_volume)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f))
            Text(current?.toString() ?: stringResource(R.string.unknown), style = Studio.type.number, color = Studio.colors.accent)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val value = current ?: 0
            StepButton(plus = false, { pending = (value - 1).coerceAtLeast(0) }, enabled = value > 0)
            StudioSlider(value, 0..100, { pending = it }, Modifier.weight(1f), description = title)
            StepButton(plus = true, { pending = (value + 1).coerceAtMost(100) }, enabled = value < 100)
        }
    }
}
