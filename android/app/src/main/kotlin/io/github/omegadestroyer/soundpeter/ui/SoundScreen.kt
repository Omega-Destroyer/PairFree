package io.github.omegadestroyer.soundpeter.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.omegadestroyer.soundpeter.R
import io.github.omegadestroyer.soundpeter.eq.EqActions
import io.github.omegadestroyer.soundpeter.eq.EqProfile
import io.github.omegadestroyer.soundpeter.eq.EqState
import io.github.omegadestroyer.soundpeter.eq.Equalizer
import io.github.omegadestroyer.soundpeter.ui.theme.Studio
import io.github.omegadestroyer.soundpeter.ui.theme.StudioShape
import java.io.IOException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The equalizer (docs/DESIGN.md, Sound): profiles, the curve, one band at a time, preamp,
 * and profile files. Gains are the user's, on top of the model's base curve; 0 dB on
 * every band is the earbuds' own tuning.
 */
@Composable
internal fun SoundScreen(eq: EqState?, actions: EqActions, onRecord: (() -> Unit)? = {}) {
    if (eq == null) {
        TabContent {
            Text(stringResource(R.string.tab_sound), style = Studio.type.title)
            if (onRecord != null) {
                Text(stringResource(R.string.eq_unavailable), color = Studio.colors.secondaryText)
                StudioButton(stringResource(R.string.capture_open), onRecord, Modifier.fillMaxWidth(), primary = true)
            } else {
                Text(stringResource(R.string.eq_soundcore_later), color = Studio.colors.secondaryText)
            }
        }
        return
    }
    var band by rememberSaveable { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<EqProfile?>(null) }
    var message by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            message = try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(actions.exportJson().encodeToByteArray()) }
                R.string.eq_exported
            } catch (_: IOException) {
                R.string.eq_export_failed
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            message = try {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                if (text != null && actions.importJson(text)) R.string.eq_imported else R.string.eq_import_wrong
            } catch (_: IOException) {
                R.string.eq_import_wrong
            }
        }
    }
    val r = eq.layout.userRangeDb
    val gains = eq.setting.gains
    val selected = band.coerceIn(gains.indices)

    TabContent {
        Text(stringResource(R.string.tab_sound), style = Studio.type.title)

        // Profiles: Stock, presets, saved, and New. The row opens at the one on the earbuds.
        val chips = rememberLazyListState()
        LaunchedEffect(Unit) {
            val on = eq.profiles.indexOf(eq.selected ?: eq.basis)
            if (on > 0) chips.scrollToItem(on)
        }
        LazyRow(state = chips, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(eq.profiles, key = { it.id }) { profile ->
                Chip(profileName(profile), eq.selected == profile) { actions.select(profile) }
            }
            item { Chip(stringResource(R.string.eq_new), selected = false) { actions.startNew() } }
        }
        Label(stringResource(R.string.eq_on_earbuds, currentProfileName(eq)))
        if (!eq.known) Text(stringResource(R.string.eq_unknown_hint), style = Studio.type.small, color = Studio.colors.secondaryText)

        val editable = eq.preset == null
        Panel {
            EqGraph(eq, selected, editable, onSelect = { band = it }) { b, g ->
                band = b
                actions.setGain(b, g)
            }
        }

        if (editable) {
            // One button per band, with its gain.
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                eq.layout.bands.forEachIndexed { i, b -> BandButton(shortHz(b.hz), gains[i], i == selected) { band = i } }
            }

            Panel {
                val gain = gains[selected]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(bandName(eq.layout.bands[selected].hz), Modifier.weight(1f), style = Studio.type.heading)
                    Text(dbText(gain), style = Studio.type.number, color = Studio.colors.accent)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepButton(plus = false, { actions.setGain(selected, gain - 1) }, enabled = gain > -r)
                    StudioSlider(gain, -r..r, { actions.setGain(selected, it) }, Modifier.weight(1f), bandName(eq.layout.bands[selected].hz), origin = 0)
                    StepButton(plus = true, { actions.setGain(selected, gain + 1) }, enabled = gain < r)
                }
            }
        } else {
            Text(stringResource(R.string.eq_preset_fixed), style = Studio.type.small, color = Studio.colors.secondaryText)
        }

        Panel {
            val preamp = eq.setting.preampDb
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.preamp), Modifier.weight(1f))
                StepButton(plus = false, { actions.setPreamp(preamp - 1) }, enabled = preamp > Equalizer.PREAMP_MIN_DB)
                Text(dbText(preamp), Modifier.width(56.dp), style = Studio.type.number, color = Studio.colors.accent, textAlign = TextAlign.Center)
                StepButton(plus = true, { actions.setPreamp(preamp + 1) }, enabled = preamp < 0)
            }
        }

        if (editable) StudioButton(stringResource(R.string.eq_save), { saving = true }, Modifier.fillMaxWidth(), primary = true)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StudioButton(
                stringResource(R.string.eq_import),
                { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                Modifier.weight(1f),
            )
            if (editable) {
                val fileName = (eq.basis?.takeIf { !it.builtIn }?.name ?: "soundpeter-eq") + ".json"
                StudioButton(stringResource(R.string.eq_export), { exporter.launch(fileName) }, Modifier.weight(1f))
            }
        }
        eq.basis?.takeIf { !it.builtIn }?.let { profile ->
            StudioButton(stringResource(R.string.eq_delete, profile.name), { deleting = profile }, Modifier.fillMaxWidth(), destructive = true)
        }
        message?.let { Text(stringResource(it), style = Studio.type.small, color = Studio.colors.secondaryText) }
        Text(stringResource(R.string.eq_note), style = Studio.type.small, color = Studio.colors.label)
    }

    if (saving) {
        SaveDialog(eq.basis?.takeIf { !it.builtIn }?.name.orEmpty(), onSave = actions::save, onDismiss = { saving = false })
    }
    deleting?.let { profile ->
        ConfirmDialog(
            title = stringResource(R.string.eq_delete_title, profile.name),
            body = stringResource(R.string.eq_delete_body),
            confirm = stringResource(R.string.delete),
            onConfirm = { actions.delete(profile) },
            onDismiss = { deleting = null },
            destructive = true,
        )
    }
}

/** A profile's name: Stock in words, presets and saved profiles by their own names. */
@Composable
internal fun profileName(profile: EqProfile): String =
    if (profile.id == EqProfile.STOCK) stringResource(R.string.eq_stock) else profile.name

/** What is on the earbuds, as a short name for Home. */
@Composable
internal fun currentProfileName(eq: EqState): String =
    if (!eq.known) stringResource(R.string.eq_unknown)
    else eq.selected?.let { profileName(it) }
        ?: eq.basis?.let { stringResource(R.string.eq_edited, profileName(it)) }
        ?: stringResource(R.string.eq_new_unsaved)

/** `50`, `1.6k`, `17k`: band labels under the curve. */
internal fun shortHz(hz: Int): String = when {
    hz < 1000 -> "$hz"
    hz % 1000 == 0 -> "${hz / 1000}k"
    else -> "${hz / 1000}.${hz % 1000 / 100}k"
}

@Composable
private fun bandName(hz: Int): String =
    if (hz < 1000) stringResource(R.string.hz, "$hz") else stringResource(R.string.khz, shortHz(hz).removeSuffix("k"))

/** `+2 dB`, `0 dB`, `−3 dB`, with a true minus sign. */
@Composable
internal fun dbText(db: Int): String = stringResource(
    R.string.db,
    when {
        db > 0 -> "+$db"
        db < 0 -> "−${-db}"
        else -> "0"
    },
)

/**
 * The curve: each band's difference from stock, bands evenly spaced. When [editable], tap a
 * band to pick it and drag up or down to set its gain in whole dB; the picked band's point
 * is larger and its label amber. A preset is drawn the same way, on a wider scale if it needs one.
 */
@Composable
private fun EqGraph(eq: EqState, selected: Int, editable: Boolean, onSelect: (Int) -> Unit, onGain: (Int, Int) -> Unit) {
    val c = Studio.colors
    val measurer = rememberTextMeasurer()
    val axis = Studio.type.label.copy(letterSpacing = 0.sp, color = c.label)
    val gains = eq.curve
    val r = scaleFor(eq)
    val user = eq.layout.userRangeDb
    val n = gains.size
    val labels = eq.curveHz.map { shortHz(it) }
    val select by rememberUpdatedState(onSelect)
    val gain by rememberUpdatedState(onGain)
    val description = stringResource(R.string.eq_graph_description)

    // Plot area inside the canvas: a gutter on the left for dB, one below for frequencies.
    class Geo(val width: Float, val height: Float, val left: Float, val top: Float, val bottom: Float) {
        val column = (width - left) / n
        fun x(i: Int) = left + column * (i + 0.5f)
        fun y(db: Double) = top + ((r - db) / (2.0 * r)).toFloat() * (bottom - top)
        fun band(x: Float) = ((x - left) / column).toInt().coerceIn(0, n - 1)
        fun db(y: Float) = (r - (y - top) / (bottom - top) * 2 * r).roundToInt().coerceIn(-r, r)
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .semantics { contentDescription = description }
            .pointerInput(n, r, editable) {
                if (!editable) return@pointerInput
                val geo = Geo(size.width.toFloat(), size.height.toFloat(), 28.dp.toPx(), 10.dp.toPx(), size.height - 24.dp.toPx())
                detectTapGestures { select(geo.band(it.x)) }
            }
            .pointerInput(n, r, editable) {
                if (!editable) return@pointerInput
                val geo = Geo(size.width.toFloat(), size.height.toFloat(), 28.dp.toPx(), 10.dp.toPx(), size.height - 24.dp.toPx())
                var dragged = 0
                detectDragGestures(onDragStart = { dragged = geo.band(it.x); select(dragged) }) { change, _ ->
                    change.consume()
                    gain(dragged, geo.db(change.position.y).coerceIn(-user, user))
                }
            },
    ) {
        val geo = Geo(size.width, size.height, 28.dp.toPx(), 10.dp.toPx(), size.height - 24.dp.toPx())
        val hairline = 1.dp.toPx()

        // The picked band's column.
        if (editable) drawRect(c.raised, Offset(geo.left + geo.column * selected, geo.top), Size(geo.column, geo.bottom - geo.top))

        // dB lines and labels: the range, half of it, and 0.
        for (db in listOf(r, r / 2, 0, -r / 2, -r)) {
            val y = geo.y(db.toDouble())
            drawLine(if (db == 0) c.label else c.hairline, Offset(geo.left, y), Offset(size.width, y), hairline)
            val text = when {
                db > 0 -> "+$db"
                db < 0 -> "−${-db}"
                else -> "0"
            }
            val t = measurer.measure(text, axis)
            drawText(t, topLeft = Offset(geo.left - 6.dp.toPx() - t.size.width, y - t.size.height / 2f))
        }

        // The curve and its points.
        for (i in 0 until n - 1) {
            drawLine(c.accent, Offset(geo.x(i), geo.y(gains[i])), Offset(geo.x(i + 1), geo.y(gains[i + 1])), 2.dp.toPx(), StrokeCap.Round)
        }
        for (i in 0 until n) {
            val at = Offset(geo.x(i), geo.y(gains[i]))
            if (editable && i == selected) {
                drawCircle(c.accent, 8.dp.toPx(), at)
                drawCircle(c.onAccent, 3.dp.toPx(), at)
            } else {
                drawCircle(c.panel, 4.dp.toPx(), at)
                drawCircle(c.accent, 4.dp.toPx(), at, style = Stroke(1.5.dp.toPx()))
            }
        }

        // Band frequencies along the bottom; the picked one in amber and heavier.
        labels.forEachIndexed { i, label ->
            val style = if (editable && i == selected) axis.copy(color = c.accent, fontWeight = FontWeight.SemiBold) else axis
            val t = measurer.measure(label, style)
            drawText(t, topLeft = Offset(geo.x(i) - t.size.width / 2f, size.height - t.size.height.toFloat()))
        }
    }
}

/** A band's button: frequency over gain. The picked one is filled amber. */
@Composable
private fun BandButton(label: String, gain: Int, selected: Boolean, onClick: () -> Unit) {
    val c = Studio.colors
    val shape = RoundedCornerShape(StudioShape.smallCorner)
    Column(
        Modifier
            .width(56.dp)
            .heightIn(min = StudioShape.touch)
            .clip(shape)
            .background(if (selected) c.accent else c.panel)
            .border(StudioShape.hairline, if (selected) c.accent else c.hairline, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val ink = if (selected) c.onAccent else c.secondaryText
        Text(label, style = Studio.type.label.copy(letterSpacing = 0.sp), color = ink)
        Text(dbText(gain).removeSuffix(" dB"), style = Studio.type.number, color = if (selected) c.onAccent else c.text)
    }
}

/** Names the current gains. Saving under an existing name replaces that profile. */
@Composable
private fun SaveDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    TextEntryDialog(
        title = stringResource(R.string.eq_save),
        initial = initial,
        hint = stringResource(R.string.eq_name_hint),
        confirm = stringResource(R.string.save),
        maxLength = 24,
        onDone = onSave,
        onDismiss = onDismiss,
    )
}

/**
 * The dB scale for [eq]: the user range, widened in steps of 6 dB when a preset reaches
 * further from stock, so the grid at half the scale stays on whole steps.
 */
internal fun scaleFor(eq: EqState): Int {
    val reach = eq.curve.maxOfOrNull { abs(it) } ?: 0.0
    return maxOf(eq.layout.userRangeDb, (ceil(reach / 6) * 6).toInt())
}

/** The current curve as a small line, for Home. */
@Composable
internal fun MiniCurve(eq: EqState, modifier: Modifier = Modifier) {
    val c = Studio.colors
    val r = scaleFor(eq).toFloat()
    val gains = eq.curve
    Canvas(modifier) {
        val step = size.width / (gains.size - 1).coerceAtLeast(1)
        fun y(db: Double) = size.height / 2 - db.toFloat() / r * (size.height / 2 - 2.dp.toPx())
        drawLine(c.hairline, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
        for (i in 0 until gains.size - 1) {
            drawLine(c.accent, Offset(step * i, y(gains[i])), Offset(step * (i + 1), y(gains[i + 1])), 1.5.dp.toPx(), StrokeCap.Round)
        }
    }
}

/** Home's sound line: the profile on the earbuds and its curve. Opens the equalizer. */
@Composable
internal fun SoundCard(eq: EqState, onOpen: () -> Unit) {
    Panel(Modifier.clickable(role = Role.Button, onClick = onOpen)) {
        Label(stringResource(R.string.sound_profile))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(currentProfileName(eq), Modifier.weight(1f), style = Studio.type.heading)
            MiniCurve(eq, Modifier.width(96.dp).height(28.dp))
            Chevron()
        }
    }
}

/** Does nothing. For previews. */
internal object NoEqActions : EqActions {
    override fun select(profile: EqProfile) = Unit
    override fun startNew() = Unit
    override fun setGain(band: Int, db: Int) = Unit
    override fun setPreamp(db: Int) = Unit
    override fun save(name: String) = Unit
    override fun delete(profile: EqProfile) = Unit
    override fun exportJson(): String = ""
    override fun importJson(text: String): Boolean = false
}
