package io.github.omegadestroyer.pairfree.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.status.CompanionLink
import io.github.omegadestroyer.pairfree.status.StatusChoice
import io.github.omegadestroyer.pairfree.status.StatusPrompt
import io.github.omegadestroyer.pairfree.status.StatusUi
import io.github.omegadestroyer.pairfree.ui.theme.Studio

/**
 * The Device tab's choice of battery notification (docs/DESIGN.md, Notification): PairFree's,
 * Google's Fast Pair (on earbuds that have it) or none, with what each needs from Android.
 */
@Composable
internal fun StatusSection(status: StatusUi, actions: EarbudsActions) {
    val context = LocalContext.current
    val c = Studio.colors
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { actions.statusChanged() }
    val link = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { actions.statusChanged() }
    Panel {
        Label(stringResource(R.string.status_title))
        Segmented(status.options, status.choice, { stringResource(optionName(it)) }, actions::chooseStatus, primary = false)
        Text(stringResource(note(status.choice)), style = Studio.type.small, color = c.secondaryText)
        if (status.choice == StatusChoice.PAIRFREE && !status.canNotify) {
            Text(stringResource(R.string.status_needs_permission), style = Studio.type.small, color = c.secondaryText)
            StudioButton(stringResource(R.string.status_allow), { askNotifications(context) { notify.launch(it) } }, Modifier.fillMaxWidth())
        }
        if (status.choice == StatusChoice.PAIRFREE && status.canNotify && !status.linked) {
            Text(stringResource(R.string.status_link_note), style = Studio.type.small, color = c.secondaryText)
            StudioButton(
                stringResource(R.string.status_link),
                { CompanionLink.associate(context, status.address, { link.launch(IntentSenderRequest.Builder(it).build()) }, actions::statusChanged) },
                Modifier.fillMaxWidth(),
            )
        }
        // PairFree cannot hide Google's notification; it can only open where the user turns it off.
        if (status.fastPair && status.choice != StatusChoice.FAST_PAIR) {
            Text(stringResource(R.string.status_google_also), style = Studio.type.small, color = c.secondaryText)
            StudioButton(stringResource(R.string.status_google_settings), { openGoogleNotificationSettings(context) }, Modifier.fillMaxWidth())
        }
    }
}

/**
 * The first time earbuds connect with PairFree's notification chosen, asks Android for what it
 * needs, one prompt after the other: notifications, then the companion link. Android's link
 * prompt speaks of syncing a caller's name, so PairFree says first what it uses the link for.
 * [onDone] gets each prompt as it closes, whatever the answer.
 */
@Composable
internal fun StatusPrompts(prompt: StatusPrompt?, address: String, onDone: (StatusPrompt) -> Unit) {
    val context = LocalContext.current
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone(StatusPrompt.NOTIFICATIONS) }
    val link = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { onDone(StatusPrompt.LINK) }
    LaunchedEffect(prompt, address) {
        if (prompt == StatusPrompt.NOTIFICATIONS && !askNotifications(context) { notify.launch(it) }) onDone(StatusPrompt.NOTIFICATIONS)
    }
    if (prompt == StatusPrompt.LINK) {
        StudioDialog(stringResource(R.string.status_link_title), { onDone(StatusPrompt.LINK) }) {
            Text(stringResource(R.string.status_link_body), color = Studio.colors.secondaryText)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StudioButton(stringResource(R.string.status_link_later), { onDone(StatusPrompt.LINK) }, Modifier.weight(1f))
                StudioButton(
                    stringResource(R.string.status_link_continue),
                    { CompanionLink.associate(context, address, { link.launch(IntentSenderRequest.Builder(it).build()) }) { onDone(StatusPrompt.LINK) } },
                    Modifier.weight(1f),
                    primary = true,
                )
            }
        }
    }
}

/** Asks for the notification permission where Android has one (13 and later). False when there is nothing to ask. */
private fun askNotifications(context: Context, launch: (String) -> Unit): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    launch(Manifest.permission.POST_NOTIFICATIONS)
    return true
}

/** Google Play services' notification settings, where its Fast Pair notifications are turned off. */
private fun openGoogleNotificationSettings(context: Context) {
    val gms = "com.google.android.gms"
    val intents = listOf(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, gms),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$gms".toUri()),
    )
    for (intent in intents) {
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next way in.
        }
    }
}

private fun optionName(choice: StatusChoice): Int = when (choice) {
    StatusChoice.PAIRFREE -> R.string.status_option_pairfree
    StatusChoice.FAST_PAIR -> R.string.status_option_fast_pair
    StatusChoice.NONE -> R.string.status_option_none
}

private fun note(choice: StatusChoice): Int = when (choice) {
    StatusChoice.PAIRFREE -> R.string.status_note_pairfree
    StatusChoice.FAST_PAIR -> R.string.status_note_fast_pair
    StatusChoice.NONE -> R.string.status_note_none
}
