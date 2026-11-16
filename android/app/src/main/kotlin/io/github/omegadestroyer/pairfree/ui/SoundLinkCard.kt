package io.github.omegadestroyer.pairfree.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.omegadestroyer.pairfree.PairFreeApplication
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.SoundLink
import io.github.omegadestroyer.pairfree.ui.theme.Look
import kotlinx.coroutines.delay

/**
 * Shown on the Home tab while PairFree's channel to the earbuds is open but the phone is not playing through them
 * (issue #48: AirPods shared with other devices, which Android lists as connected and will not make active). One
 * button asks Android to connect them for sound and play through them ([io.github.omegadestroyer.pairfree.bluetooth.AudioLinks.useForSound]);
 * where Android does not take that, or nothing comes of it, Bluetooth settings are a tap away. Nothing shows while the
 * phone plays through the earbuds, or while PairFree cannot tell.
 */
@Composable
fun SoundLinkCard(address: String) {
    val context = LocalContext.current
    // Previews have no application of PairFree's.
    val audio = (context.applicationContext as? PairFreeApplication)?.hub?.audio ?: return
    var link by remember(address) { mutableStateOf(SoundLink.UNKNOWN) }
    /** Null until the button was tapped, then whether Android took the request. */
    var taken by remember(address) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(address) {
        while (true) {
            link = audio.sound(address)
            delay(POLL_MS)
        }
    }
    if (link != SoundLink.NOT_CONNECTED && link != SoundLink.CONNECTED) return
    Panel {
        Text(stringResource(R.string.sound_link_title), style = Look.type.heading)
        Text(
            stringResource(if (link == SoundLink.CONNECTED) R.string.sound_link_connected else R.string.sound_link_none),
            style = Look.type.small,
            color = Look.colors.secondaryText,
        )
        AppButton(stringResource(R.string.sound_link_use), { taken = audio.useForSound(address) }, Modifier.fillMaxWidth(), primary = true)
        taken?.let { ok ->
            Text(
                stringResource(if (ok) R.string.sound_link_asked else R.string.sound_link_refused),
                style = Look.type.small,
                color = Look.colors.secondaryText,
            )
            AppButton(
                stringResource(R.string.bluetooth_settings),
                { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val POLL_MS = 2_000L
