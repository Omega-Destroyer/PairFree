package io.github.omegadestroyer.soundpeter.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.omegadestroyer.soundpeter.AppViewModel
import io.github.omegadestroyer.soundpeter.R
import io.github.omegadestroyer.soundpeter.bluetooth.LinkStatus
import io.github.omegadestroyer.soundpeter.bluetooth.Paired
import io.github.omegadestroyer.soundpeter.capture.Capture
import io.github.omegadestroyer.soundpeter.ui.theme.Studio

@Composable
fun SoundPeterApp(vm: AppViewModel = viewModel()) {
    LifecycleStartEffect(vm) {
        vm.onStart()
        onStopOrDispose { vm.onStop() }
    }
    val hasPermission by vm.hasPermission.collectAsStateWithLifecycle()
    val status by vm.connection.status.collectAsStateWithLifecycle()
    val capture by vm.capture.collectAsStateWithLifecycle()
    val earbuds by vm.connection.state.collectAsStateWithLifecycle()
    Box(
        Modifier
            .fillMaxSize()
            .background(Studio.colors.background)
            .safeDrawingPadding(),
    ) {
        val current = status
        // One call site for both, so the open tab survives a reconnect.
        val shown = (current as? LinkStatus.Connected)?.device ?: (current as? LinkStatus.Reconnecting)?.device
        when {
            capture != Capture.Closed -> CaptureScreen(capture, vm, earbuds.firmware?.code.takeIf { status is LinkStatus.Connected })
            !hasPermission -> PermissionScreen(vm::onPermissionResult)
            shown != null -> ConnectedScreen(vm, shown, live = current is LinkStatus.Connected)
            else -> NotConnectedScreen(vm, current)
        }
    }
}

@Composable
private fun PermissionScreen(onResult: (Boolean) -> Unit) {
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        onResult(granted)
    }
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.permission_title), style = Studio.type.title)
        Text(stringResource(R.string.permission_body), color = Studio.colors.secondaryText)
        StudioButton(
            stringResource(R.string.permission_allow),
            { launcher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
            Modifier.fillMaxWidth(),
            primary = true,
        )
        if (denied) {
            Text(stringResource(R.string.permission_denied), color = Studio.colors.secondaryText)
            StudioButton(
                stringResource(R.string.permission_settings),
                {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                },
                Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NotConnectedScreen(vm: AppViewModel, status: LinkStatus) {
    val paired by vm.paired.collectAsStateWithLifecycle()
    val bluetoothOn by vm.bluetoothOn.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painterResource(R.drawable.device_h3_case),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 150.dp, height = 120.dp),
        )
        Text(stringResource(R.string.connect_title), style = Studio.type.title)
        Text(stringResource(R.string.connect_hint), style = Studio.type.small, color = Studio.colors.secondaryText)
        failure(status)?.let { message ->
            Panel { Text(message, color = Studio.colors.destructive) }
        }
        when {
            !bluetoothOn -> Text(stringResource(R.string.bluetooth_off))
            paired.isEmpty() -> Text(stringResource(R.string.no_paired))
            else -> Panel {
                for (device in paired) {
                    PairedRow(device, connecting = (status as? LinkStatus.Connecting)?.device == device) {
                        vm.connect(device)
                    }
                }
            }
        }
        StudioButton(
            stringResource(R.string.bluetooth_settings),
            { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
            Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.privacy_line),
            Modifier.fillMaxWidth(),
            style = Studio.type.small,
            color = Studio.colors.label,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PairedRow(device: Paired, connecting: Boolean, onConnect: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(device.name, Modifier.weight(1f), style = Studio.type.heading)
        if (connecting) {
            Label(stringResource(R.string.connecting))
        } else {
            StudioButton(stringResource(R.string.connect), onConnect)
        }
    }
}

@Composable
private fun failure(status: LinkStatus): String? {
    if (status !is LinkStatus.Failed) return null
    val name = status.device.name
    return when (status.reason) {
        LinkStatus.Reason.COULD_NOT_OPEN -> stringResource(R.string.failed_could_not_open, name)
        LinkStatus.Reason.LOST -> stringResource(R.string.failed_lost, name)
        LinkStatus.Reason.BLUETOOTH_OFF -> stringResource(R.string.failed_bluetooth_off)
        LinkStatus.Reason.NO_PERMISSION -> stringResource(R.string.failed_no_permission)
    }
}
