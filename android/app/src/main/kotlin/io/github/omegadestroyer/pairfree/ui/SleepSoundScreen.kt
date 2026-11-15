package io.github.omegadestroyer.pairfree.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.sleep.SleepUploadUi
import io.github.omegadestroyer.pairfree.sleep.SleepUploader
import io.github.omegadestroyer.pairfree.ui.theme.Studio

/**
 * Puts a sound from the phone on the Soundcore Sleep A30 for its local mode (issue #23, docs/soundcore/PROTOCOL.md section 14). Untested on an A30, which the
 * screen says first; nothing goes out before the opt-in switch and the confirmation.
 */
@Composable
internal fun SleepSoundScreen(address: String, uploader: SleepUploader, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val ui by uploader.state.collectAsState()
    var understood by rememberSaveable { mutableStateOf(false) }
    var picked by rememberSaveable { mutableStateOf<Uri?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var slot by rememberSaveable { mutableIntStateOf(0) }
    var high by rememberSaveable { mutableStateOf(false) }
    // What the confirmation dialog will send: the sound's name and the action.
    var pending by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val catalogue by uploader.catalogue.collectAsState()
    val c = Studio.colors
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            picked = uri
            name = uploader.nameOf(uri)
        }
    }
    val busy = ui is SleepUploadUi.Preparing || ui is SleepUploadUi.Connecting || ui is SleepUploadUi.Sending

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(stringResource(R.string.sleep_upload_title), Modifier.weight(1f), style = Studio.type.title)
            StudioButton(stringResource(R.string.capture_close), onClose)
        }
        Text(stringResource(R.string.sleep_upload_intro), color = c.secondaryText)

        Panel {
            Label(stringResource(R.string.sleep_upload_warning_title), color = c.destructive)
            Text(stringResource(R.string.sleep_upload_warning), color = c.destructive)
            SwitchRow(stringResource(R.string.sleep_upload_understood), understood, { understood = it }, enabled = !busy)
        }

        Panel {
            Label(stringResource(R.string.sleep_upload_sound))
            StudioButton(stringResource(if (picked == null) R.string.sleep_upload_pick else R.string.sleep_upload_pick_again), { picker.launch(arrayOf("audio/*")) }, Modifier.fillMaxWidth(), enabled = !busy)
            if (picked != null) Text(name, color = c.text)
            Label(stringResource(R.string.sleep_upload_slot))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StudioButton(stringResource(R.string.sleep_upload_slot_n, 1), { slot = 0 }, Modifier.weight(1f), primary = slot == 0, enabled = !busy)
                StudioButton(stringResource(R.string.sleep_upload_slot_n, 2), { slot = 1 }, Modifier.weight(1f), primary = slot == 1, enabled = !busy)
            }
            Text(
                (0..1).map { n ->
                    val held = catalogue.slots.firstOrNull { it.address == address && it.slot == n }?.let { s -> catalogue.entries.firstOrNull { it.hash == s.hash } }
                    stringResource(R.string.sleep_upload_slot_holds, n + 1, held?.name ?: stringResource(R.string.sleep_upload_slot_unknown))
                }.joinToString("\n"),
                style = Studio.type.small,
                color = c.secondaryText,
            )
            SwitchRow(stringResource(R.string.sleep_upload_quality), high, { high = it }, note = stringResource(R.string.sleep_upload_quality_note), enabled = !busy)
        }

        when (val s = ui) {
            SleepUploadUi.Idle -> Unit
            SleepUploadUi.Preparing -> Text(stringResource(R.string.sleep_upload_preparing), color = c.secondaryText)
            SleepUploadUi.Connecting -> Text(stringResource(R.string.sleep_upload_connecting), color = c.secondaryText)
            is SleepUploadUi.Sending -> Panel {
                LevelBar(if (s.total > 0) s.done * 100 / s.total else 0)
                Text(stringResource(R.string.sleep_upload_sending, if (s.total > 0) s.done * 100 / s.total else 0), color = c.secondaryText)
            }
            is SleepUploadUi.Done -> Panel {
                Text(stringResource(R.string.sleep_upload_done, s.seconds), color = c.accent)
                if (s.clipped) Text(stringResource(R.string.sleep_upload_clipped), style = Studio.type.small, color = c.secondaryText)
                Text(stringResource(R.string.sleep_upload_next), style = Studio.type.small, color = c.secondaryText)
            }
            is SleepUploadUi.AlreadyThere -> Panel {
                Text(stringResource(R.string.sleep_upload_already, s.name, s.slot + 1), color = c.accent)
                Text(stringResource(R.string.sleep_upload_already_note), style = Studio.type.small, color = c.secondaryText)
                StudioButton(stringResource(R.string.sleep_upload_send_anyway), { pending = s.name to { uploader.reset(); picked?.let { u -> uploader.start(address, u, slot, high, force = true) } } }, Modifier.fillMaxWidth(), enabled = picked != null)
            }
            is SleepUploadUi.Failed -> Panel {
                Text(stringResource(R.string.sleep_upload_failed, s.reason), color = c.destructive)
                Text(stringResource(R.string.sleep_upload_failed_next), style = Studio.type.small, color = c.secondaryText)
            }
        }

        if (busy) {
            StudioButton(stringResource(R.string.cancel), uploader::cancel, Modifier.fillMaxWidth(), destructive = true)
        } else {
            StudioButton(
                stringResource(R.string.sleep_upload_start),
                { pending = name to { uploader.reset(); picked?.let { u -> uploader.start(address, u, slot, high) } } },
                Modifier.fillMaxWidth(),
                primary = true,
                enabled = understood && picked != null,
            )
        }

        if (catalogue.entries.isNotEmpty()) {
            Panel {
                Label(stringResource(R.string.sleep_upload_catalogue))
                Text(stringResource(R.string.sleep_upload_catalogue_note, slot + 1), style = Studio.type.small, color = c.secondaryText)
                catalogue.entries.forEachIndexed { i, entry ->
                    if (i > 0) Divider()
                    val on = catalogue.slots.filter { it.address == address && it.hash == entry.hash }.map { it.slot + 1 }.sorted()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(entry.name, color = c.text)
                        Text(
                            stringResource(R.string.sleep_upload_entry, entry.seconds) + if (on.isEmpty()) "" else "  ·  " + stringResource(R.string.sleep_upload_entry_on, on.joinToString(", ")),
                            style = Studio.type.small,
                            color = c.secondaryText,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StudioButton(
                                stringResource(R.string.sleep_upload_entry_send, slot + 1),
                                { pending = entry.name to { uploader.reset(); uploader.startFromCatalogue(address, entry.hash, slot) } },
                                Modifier.weight(1f),
                                enabled = understood && !busy,
                            )
                            StudioButton(stringResource(R.string.sleep_upload_entry_forget), { uploader.forget(entry.hash) }, Modifier.weight(1f), destructive = true, enabled = !busy)
                        }
                    }
                }
            }
        }
    }

    pending?.let { (label, action) ->
        ConfirmDialog(
            title = stringResource(R.string.sleep_upload_confirm_title),
            body = stringResource(R.string.sleep_upload_confirm, label, slot + 1),
            confirm = stringResource(R.string.sleep_upload_confirm_yes),
            onConfirm = action,
            onDismiss = { pending = null },
        )
    }
}
