package io.github.omegadestroyer.pairfree.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.capture.Capture
import io.github.omegadestroyer.pairfree.capture.CaptureResult
import io.github.omegadestroyer.pairfree.protocol.CurveMatch
import io.github.omegadestroyer.pairfree.protocol.EqCapture
import io.github.omegadestroyer.pairfree.protocol.Models
import io.github.omegadestroyer.pairfree.ui.theme.Studio
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.ln

/** What the capture screen can do. The view model does it; previews pass a stand-in. */
interface CaptureActions {
    fun closeCapture()
    fun importBugReport(uri: android.net.Uri)
    fun nameCapturedWrite(index: Int, name: String?)
    fun captureReport(): String
    fun captureIssueUri(): android.net.Uri
    fun showWholeLog(whole: Boolean)
}

/**
 * Records a model from its official app (docs/DESIGN.md, Record from the official app): the
 * steps, then what the bug report held (for SOUNDPEATS earbuds the settings PeatsAudio read and
 * the EQ curves it sent, each curve named by the user; for other brands what their app and
 * earbuds sent each other), then a report to send. [connectedCode] is the firmware code of the
 * SOUNDPEATS earbuds PairFree is connected to, whose own device report then goes in too.
 */
@Composable
internal fun CaptureScreen(capture: Capture, actions: CaptureActions, connectedCode: String? = null) {
    BackHandler { actions.closeCapture() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) actions.importBugReport(uri)
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.capture_title), Modifier.weight(1f), style = Studio.type.title)
            StudioButton(stringResource(R.string.capture_close), actions::closeCapture)
        }
        Privacy()
        when (capture) {
            Capture.Closed -> Unit
            Capture.Guide -> Guide { picker.launch(arrayOf("*/*")) }
            Capture.Reading -> Panel { Text(stringResource(R.string.capture_reading)) }
            is Capture.Done -> Done(capture, actions, connectedCode) { picker.launch(arrayOf("*/*")) }
        }
    }
}

/** What is read and what is sent, first, since the user is handing over a bug report. */
@Composable
private fun Privacy() {
    val context = LocalContext.current
    Panel {
        Label(stringResource(R.string.capture_privacy_title))
        Text(stringResource(R.string.capture_privacy), style = Studio.type.small, color = Studio.colors.secondaryText)
        StudioButton(
            stringResource(R.string.capture_source),
            {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE.toUri()))
                } catch (_: ActivityNotFoundException) {
                    // No browser to open it in.
                }
            },
            Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Guide(onChoose: () -> Unit) {
    val c = Studio.colors
    Text(stringResource(R.string.capture_intro), color = c.secondaryText)
    Panel {
        val steps = listOf(
            R.string.capture_step_developer,
            R.string.capture_step_log,
            R.string.capture_step_app,
            R.string.capture_step_peats_eq,
            R.string.capture_step_report,
            R.string.capture_step_share,
            R.string.capture_step_name,
            R.string.capture_step_cleanup,
        )
        steps.forEachIndexed { i, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${i + 1}", Modifier.width(20.dp), style = Studio.type.heading, color = c.accent)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(step))
                    // Android shows nothing while it builds the bug report, so people think it failed.
                    if (step == R.string.capture_step_report) {
                        Text(stringResource(R.string.capture_step_report_wait), style = Studio.type.body.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
    StudioButton(stringResource(R.string.capture_choose), onChoose, Modifier.fillMaxWidth(), primary = true)
}

@Composable
private fun Done(done: Capture.Done, actions: CaptureActions, connectedCode: String?, onChoose: () -> Unit) {
    val c = Studio.colors
    val context = LocalContext.current
    val problem = when (val result = done.result) {
        CaptureResult.NoBluetoothLog -> R.string.capture_no_log
        CaptureResult.NotABugReport -> R.string.capture_not_report
        CaptureResult.Unreadable -> R.string.capture_unreadable
        is CaptureResult.Found -> if (result.isEmpty) R.string.capture_none else null
    }
    if (problem != null) {
        Panel { Text(stringResource(problem), color = c.destructive) }
        StudioButton(stringResource(R.string.capture_choose), onChoose, Modifier.fillMaxWidth(), primary = true)
        return
    }

    var naming by rememberSaveable { mutableStateOf<Int?>(null) }
    var typing by rememberSaveable { mutableStateOf<Int?>(null) }
    var copied by remember { mutableStateOf(false) }
    val writes = done.writes
    val firmware = (done.reads.mapNotNull { it.firmware?.text } + writes.mapNotNull { it.firmware?.text }).distinct()
    Panel {
        Label(stringResource(R.string.capture_earbuds))
        if (firmware.isEmpty() && (done.hasSoundpeats || done.vendor.isEmpty())) {
            Text(stringResource(R.string.capture_no_firmware), color = c.secondaryText)
        }
        for (f in firmware) {
            val model = Models.find(f.substringBefore('_'), null)?.names?.firstOrNull()
            Text(if (model != null) "$model ($f)" else stringResource(R.string.capture_new_model, f))
            val settings = done.reads.firstOrNull { it.firmware?.text == f }?.replies?.size ?: 0
            Text(pluralStringResource(R.plurals.capture_settings_read, settings, settings), style = Studio.type.small, color = c.secondaryText)
        }
        if (done.hasSoundpeats) {
            val included = connectedCode != null && firmware.any { it.substringBefore('_') == connectedCode }
            Text(
                stringResource(if (included) R.string.capture_own_report else R.string.capture_own_report_missing),
                style = Studio.type.small,
                color = c.secondaryText,
            )
        }
        done.vendor.forEachIndexed { i, earbuds ->
            Text(done.vendorNames.getOrElse(i) { "" })
            Text(
                pluralStringResource(R.plurals.capture_vendor_messages, earbuds.fromApp, earbuds.fromApp) + ", " +
                    pluralStringResource(R.plurals.capture_vendor_replies, earbuds.fromEarbuds, earbuds.fromEarbuds),
                style = Studio.type.small,
                color = c.secondaryText,
            )
            val sent = earbuds.sent.joinToString(", ") { (name, times) -> if (times > 1) "$name ($times)" else name }
            if (sent.isNotEmpty()) Text(stringResource(R.string.capture_vendor_sent, sent), style = Studio.type.small, color = c.secondaryText)
        }
        if (done.vendor.isNotEmpty()) Text(stringResource(R.string.capture_vendor_hint), style = Studio.type.small, color = c.secondaryText)
    }
    // The log holds hours of earlier traffic; the recording starts where the user opened this screen.
    val since = done.since
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (since != null) stringResource(R.string.capture_since, clock(since)) else stringResource(R.string.capture_whole_log),
            Modifier.weight(1f),
            style = Studio.type.small,
            color = c.secondaryText,
        )
        if (since != null || done.wholeLog) {
            StudioButton(
                stringResource(if (done.wholeLog) R.string.capture_show_recording else R.string.capture_show_all),
                { actions.showWholeLog(!done.wholeLog) },
            )
        }
    }
    if (writes.isEmpty()) {
        if (done.reads.isNotEmpty()) Text(stringResource(R.string.capture_no_curves), color = c.secondaryText)
    } else {
        val unnamed = done.unnamedNew
        Text(
            pluralStringResource(R.plurals.capture_found, writes.size, writes.size) + " " +
                if (unnamed == 0) stringResource(R.string.capture_all_known) else pluralStringResource(R.plurals.capture_need_names, unnamed, unnamed),
            color = c.secondaryText,
        )
        Panel {
            writes.forEachIndexed { i, write ->
                if (i > 0) Divider()
                val same = writes.subList(0, i).indexOfFirst { it.sameAs(write) }
                WriteRow(i + 1, write, done.names[i], done.matches.getOrNull(i), same.takeIf { it >= 0 }?.plus(1), showFirmware = firmware.size > 1) { naming = i }
            }
        }
    }

    val ready = done.ready
    val clipLabel = stringResource(R.string.capture_title)
    StudioButton(
        stringResource(R.string.capture_send),
        {
            val report = actions.captureReport()
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText(clipLabel, report))
            copied = true
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, actions.captureIssueUri()))
            } catch (_: ActivityNotFoundException) {
                // No browser: the report is on the clipboard to paste wherever the user likes.
            }
        },
        Modifier.fillMaxWidth(),
        primary = true,
        enabled = ready,
    )
    StudioButton(
        stringResource(R.string.capture_share),
        {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, actions.captureReport())
            context.startActivity(Intent.createChooser(send, null))
        },
        Modifier.fillMaxWidth(),
        enabled = ready,
    )
    Text(
        stringResource(if (copied) R.string.capture_copied else if (ready) R.string.capture_send_hint else R.string.capture_name_first),
        style = Studio.type.small,
        color = c.secondaryText,
    )
    Text(stringResource(R.string.capture_step_cleanup), style = Studio.type.small, color = c.secondaryText)

    naming?.let { index ->
        val options = remember { nameOptions() }
        PickerDialog(
            title = stringResource(R.string.capture_name_title, index + 1),
            options = options,
            selected = done.names[index]?.let { name -> options.firstOrNull { it is NameOption.Named && it.name == name } },
            label = { option ->
                when (option) {
                    is NameOption.Named -> option.name
                    NameOption.Other -> stringResource(R.string.capture_other_name)
                    NameOption.LeaveOut -> stringResource(R.string.capture_leave_out)
                }
            },
            onPick = { option ->
                when (option) {
                    is NameOption.Named -> actions.nameCapturedWrite(index, option.name)
                    NameOption.Other -> typing = index
                    NameOption.LeaveOut -> actions.nameCapturedWrite(index, null)
                }
            },
            onDismiss = { naming = null },
        )
    }
    typing?.let { index ->
        TextEntryDialog(
            title = stringResource(R.string.capture_name_title, index + 1),
            initial = done.names[index].orEmpty(),
            hint = stringResource(R.string.eq_name_hint),
            confirm = stringResource(R.string.save),
            maxLength = 40,
            onDone = { actions.nameCapturedWrite(index, it) },
            onDismiss = { typing = null },
        )
    }
}

@Composable
private fun WriteRow(number: Int, write: EqCapture.Write, name: String?, match: CurveMatch?, sameAs: Int?, showFirmware: Boolean, onClick: () -> Unit) {
    val c = Studio.colors
    val time = remember(write.timeMicros) { clock(write.timeMicros) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("$number", Modifier.width(24.dp), style = Studio.type.heading, color = c.accent)
        Column(Modifier.weight(1f)) {
            when {
                name != null -> Text(name)
                match is CurveMatch.Custom -> Text(stringResource(R.string.capture_custom_curve), color = c.label)
                match != null -> Text(stringResource(R.string.capture_left_out), color = c.label)
                // A curve PairFree does not know: the one that needs the user.
                else -> Text(stringResource(R.string.capture_needs_name), color = c.accent, style = Studio.type.heading)
            }
            val details = listOfNotNull(
                time,
                when (match) {
                    is CurveMatch.Preset -> stringResource(R.string.capture_known_preset, (match.model.names.firstOrNull() ?: match.model.code).removePrefix("SOUNDPEATS "))
                    is CurveMatch.Custom -> stringResource(R.string.capture_custom_detail)
                    null -> null
                },
                sameAs?.let { stringResource(R.string.capture_same_as, it) },
                write.firmware?.code?.takeIf { showFirmware },
            )
            Text(details.joinToString(" · "), style = Studio.type.small, color = c.secondaryText)
        }
        MiniCurve(write, Modifier.width(72.dp).height(28.dp))
        Chevron()
    }
}

/** A snoop log time as the phone's clock showed it (SnoopScanner.Found). */
private fun clock(timeMicros: Long): String =
    DateTimeFormatter.ofPattern("HH:mm:ss").format(Instant.ofEpochMilli(timeMicros / 1000).atOffset(ZoneOffset.UTC))

/** The write's gains against log frequency: enough to tell one curve from another. */
@Composable
private fun MiniCurve(write: EqCapture.Write, modifier: Modifier) {
    val color = Studio.colors.accent
    val bands = remember(write) { write.bands.sortedBy { it.hz } }
    Canvas(modifier) {
        if (bands.size < 2) return@Canvas
        val lo = ln(20.0)
        val hi = ln(20_000.0)
        fun x(hz: Double) = ((ln(hz.coerceIn(20.0, 20_000.0)) - lo) / (hi - lo)).toFloat() * size.width
        fun y(db: Double) = (0.5 - db.coerceIn(-12.0, 12.0) / 24.0).toFloat() * size.height
        val path = Path()
        bands.forEachIndexed { i, b -> if (i == 0) path.moveTo(x(b.hz), y(b.gainDb)) else path.lineTo(x(b.hz), y(b.gainDb)) }
        drawLine(color.copy(alpha = 0.3f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    }
}

private sealed interface NameOption {
    data class Named(val name: String) : NameOption
    data object Other : NameOption
    data object LeaveOut : NameOption
}

/** The preset names of every model PairFree knows, SOUNDPEATS Classic first, then Other and Leave out. */
private fun nameOptions(): List<NameOption> {
    val known = listOf(EqCapture.CLASSIC) + Models.ALL.flatMap { m -> m.eq?.presets.orEmpty().map { it.name } }
    return known.distinct().map { NameOption.Named(it) } + NameOption.Other + NameOption.LeaveOut
}

private const val SOURCE = "https://github.com/Omega-Destroyer/PairFree"
