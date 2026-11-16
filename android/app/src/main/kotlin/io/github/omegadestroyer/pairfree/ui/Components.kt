package io.github.omegadestroyer.pairfree.ui

import io.github.omegadestroyer.pairfree.Languages
import io.github.omegadestroyer.pairfree.look.Looks
import androidx.compose.runtime.collectAsState
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.ui.theme.Look
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A panel's or a control's surface in the app's look: under it the look's hard shadow where it has one ([raised], and
 * only under a fill, since a shadow would show through an empty shape), then the fill clipped to [shape], then the edge.
 * Every outlined shape in the app goes through this, so a look's outline and shadow reach all of them.
 */
@Composable
fun Modifier.surface(shape: Shape, fill: Color, edge: Color?, raised: Boolean = true, width: Dp = Look.shape.hairline): Modifier {
    val offset = Look.shape.shadow
    val shadow = Look.colors.shadow
    val under = if (raised && offset > 0.dp && fill.alpha > 0f) {
        Modifier.drawBehind {
            translate(offset.toPx(), offset.toPx()) { drawOutline(shape.createOutline(size, layoutDirection, this), shadow) }
        }
    } else {
        Modifier
    }
    return this.then(under).clip(shape).background(fill).then(if (edge != null) Modifier.border(width, edge, shape) else Modifier)
}

/** The fill of a control that is not chosen: none, or the raised colour in a look whose controls cast a shadow. */
private val restingFill: Color
    @Composable get() = if (Look.shape.shadow > 0.dp) Look.colors.raised else Color.Transparent

/** Text in the look's type. Defaults to body text in the text colour. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Look.type.body,
    color: Color = Look.colors.text,
    textAlign: TextAlign? = null,
) {
    BasicText(text, modifier, style.copy(color = color, textAlign = textAlign ?: style.textAlign))
}

/** A small label, in capitals where the look sets its labels so (Studio's are, in Plex Mono). */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Look.colors.label) {
    Text(if (Look.type.labelCaps) text.uppercase() else text, modifier, Look.type.label, color)
}

/** A panel: the look's corners, and a hairline border where the look outlines its panels. No shadow. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = Look.shape.panel
    Column(
        modifier
            .fillMaxWidth()
            .surface(shape, Look.colors.panel, if (Look.shape.panelBorder) Look.colors.outline else null)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** A hairline across a panel, between rows. */
@Composable
fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(Look.shape.hairline)
            .background(Look.colors.hairline),
    )
}

/**
 * A button. Primary: amber fill with dark text. Secondary: amber outline and amber text.
 * Destructive: outline and text in the destructive colour. They differ in fill as well as
 * colour, so the choice never rests on colour alone.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val c = Look.colors
    val shape = Look.shape.control
    val fill = if (primary && enabled) c.accent else restingFill
    val edge = when {
        !enabled -> c.hairline
        destructive -> c.destructive
        else -> c.accent
    }
    val ink = when {
        !enabled -> c.label
        primary -> c.onAccent
        destructive -> c.destructive
        else -> c.accent
    }
    Box(
        modifier
            .heightIn(min = Look.shape.touch)
            .surface(shape, fill, edge, raised = enabled)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Look.type.heading, color = ink, textAlign = TextAlign.Center)
    }
}

/** A level bar: hairline track, amber fill. Empty when the level is unknown. */
@Composable
fun LevelBar(percent: Int?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(shape)
            .background(Look.colors.hairline),
    ) {
        if (percent != null && percent > 0) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                    .clip(shape)
                    .background(Look.colors.accent),
            )
        }
    }
}

/**
 * A level in whole steps, such as a battery in fifths: one segment per step, amber up to [level]
 * and a hairline track after it. All track when the level is unknown.
 */
@Composable
fun StepBar(level: Int?, steps: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(2.dp)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (step in 1..steps) {
            Box(
                Modifier
                    .weight(1f)
                    .height(12.dp)
                    .clip(shape)
                    .background(if (level != null && step <= level) Look.colors.accent else Look.colors.hairline),
            )
        }
    }
}

/**
 * One of a few choices, side by side. A primary choice selects with an amber fill and dark
 * text; a secondary one with an amber outline and amber text. Either way the selected
 * label is also set heavier, so the state never rests on colour alone.
 */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
) {
    val c = Look.colors
    val shape = Look.shape.control
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .surface(shape, restingFill, c.outline)
            .selectableGroup(),
    ) {
        options.forEachIndexed { i, option ->
            if (i > 0) {
                Box(
                    Modifier
                        .width(Look.shape.hairline)
                        .fillMaxHeight()
                        .background(c.outline),
                )
            }
            val on = option == selected
            val inner = Look.shape.control
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = Look.shape.touch)
                    .then(
                        when {
                            on && primary -> Modifier.background(c.accent)
                            on && Look.shape.selectedFill -> Modifier.background(c.highlight)
                            on -> Modifier.border(1.5.dp, c.accent, inner)
                            else -> Modifier
                        },
                    )
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(option) }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = Look.type.body.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                    color = when {
                        on && primary -> c.onAccent
                        on && Look.shape.selectedFill -> c.text
                        on -> c.accent
                        else -> c.secondaryText
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The switch graphic: the knob moves and the track fills, so on and off differ in more than colour. */
@Composable
fun SwitchMark(on: Boolean, enabled: Boolean = true) {
    val c = Look.colors
    val track = if (on && enabled) c.accent else Color.Transparent
    val edge = when {
        !enabled -> c.hairline
        on -> c.accent
        else -> c.label
    }
    val knob = when {
        !enabled -> c.hairline
        on -> c.onAccent
        else -> c.label
    }
    Box(
        Modifier
            .size(width = 40.dp, height = 24.dp)
            .clip(CircleShape)
            .background(track)
            .border(Look.shape.hairline, edge, CircleShape)
            .padding(4.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(knob),
        )
    }
}

/** A small "i" beside a setting's title: tapping it shows or hides the setting's short explanation. */
@Composable
fun InfoButton(open: Boolean, onClick: () -> Unit) {
    val c = Look.colors
    val description = stringResource(R.string.info_button)
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(20.dp).border(1.dp, if (open) c.accent else c.label, CircleShape), contentAlignment = Alignment.Center) {
            Text("i", style = Look.type.small, color = if (open) c.accent else c.label)
        }
    }
}

/** A setting that is on or off: title, the switch, and an optional explanation behind an info button. The whole row toggles. */
@Composable
fun SwitchRow(
    title: String,
    on: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    enabled: Boolean = true,
) {
    val c = Look.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = on, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        var open by remember { mutableStateOf(false) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f, fill = false), color = if (enabled) c.text else c.secondaryText)
                if (note != null) InfoButton(open) { open = !open }
            }
            if (note != null && open) Text(note, style = Look.type.small, color = c.secondaryText)
        }
        SwitchMark(on, enabled)
    }
}

/** A row that shows a current value and opens a choice when tapped. */
@Composable
fun ValueRow(title: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier, note: String? = null) {
    val c = Look.colors
    var open by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f, fill = false))
                if (note != null) InfoButton(open) { open = !open }
            }
            Text(value, color = c.accent, textAlign = TextAlign.End)
            Chevron()
        }
        if (note != null && open) Text(note, style = Look.type.small, color = c.secondaryText)
    }
}

/**
 * A secondary choice in a row of several: amber outline, amber text and heavier type when
 * selected, a hairline otherwise.
 */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Look.colors
    val shape = Look.shape.control
    Box(
        Modifier
            .heightIn(min = Look.shape.touch)
            .then(
                if (Look.shape.selectedFill) Modifier.surface(shape, if (selected) c.highlight else restingFill, c.outline, raised = selected)
                else Modifier.surface(shape, Color.Transparent, if (selected) c.accent else c.outline, width = if (selected) 1.5.dp else Look.shape.hairline),
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Look.type.body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = when {
                selected && Look.shape.selectedFill -> c.text
                selected -> c.accent
                else -> c.secondaryText
            },
        )
    }
}

@Composable
fun Chevron() {
    val color = Look.colors.label
    // Points the way forward: right on a left-to-right screen, left on a right-to-left one.
    val flip = if (isRtl()) -1f else 1f
    Canvas(Modifier.size(width = 8.dp, height = 14.dp).graphicsLayer(scaleX = flip)) {
        val w = 1.5.dp.toPx()
        drawLine(color, Offset(w, w), Offset(size.width - w, size.height / 2), w, StrokeCap.Round)
        drawLine(color, Offset(size.width - w, size.height / 2), Offset(w, size.height - w), w, StrokeCap.Round)
    }
}

/**
 * A horizontal slider over [range]: hairline track, amber fill from [origin] to the knob.
 * Drag or tap anywhere along it. Screen readers can set it directly.
 */
@Composable
fun AppSlider(
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    origin: Int = range.first,
) {
    val c = Look.colors
    val change by rememberUpdatedState(onChange)
    var width by remember { mutableIntStateOf(1) }
    val knob = 10.dp
    val span = (range.last - range.first).coerceAtLeast(1)
    val fraction = ((value - range.first).toFloat() / span).coerceIn(0f, 1f)
    val start = ((origin - range.first).toFloat() / span).coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(Look.shape.touch)
            .onSizeChanged { width = it.width }
            .pointerInput(range) {
                val inset = knob.toPx()
                fun at(x: Float): Int {
                    val f = ((x - inset) / (width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    return range.first + (f * span).roundToInt()
                }
                detectTapGestures { change(at(it.x)) }
            }
            .pointerInput(range) {
                val inset = knob.toPx()
                fun at(x: Float): Int {
                    val f = ((x - inset) / (width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    return range.first + (f * span).roundToInt()
                }
                detectHorizontalDragGestures { pointer, _ ->
                    pointer.consume()
                    change(at(pointer.position.x))
                }
            }
            .semantics {
                if (description != null) contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range.first.toFloat()..range.last.toFloat(), span - 1)
                setProgress { target ->
                    change(target.roundToInt().coerceIn(range))
                    true
                }
            }
            .drawBehind {
                val inset = knob.toPx()
                val track = 4.dp.toPx()
                val y = size.height / 2
                val left = inset
                val right = size.width - inset
                val x = left + (right - left) * fraction
                val from = left + (right - left) * start
                drawRoundRect(c.hairline, Offset(left, y - track / 2), Size(right - left, track), CornerRadius(track / 2))
                drawRoundRect(c.accent, Offset(minOf(from, x), y - track / 2), Size(abs(x - from), track), CornerRadius(track / 2))
                drawCircle(c.accent, inset, Offset(x, y))
                drawCircle(c.onAccent, inset * 0.4f, Offset(x, y))
            },
    )
}

/** A square minus or plus button for stepping a value. */
@Composable
fun StepButton(plus: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val c = Look.colors
    val shape = Look.shape.control
    val ink = if (enabled) c.accent else c.hairline
    val description = stringResource(if (plus) R.string.increase else R.string.decrease)
    Box(
        Modifier
            .size(Look.shape.touch)
            .surface(shape, restingFill, if (enabled) c.accent else c.hairline, raised = enabled)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(14.dp)) {
            val w = 1.5.dp.toPx()
            drawLine(ink, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), w, StrokeCap.Round)
            if (plus) drawLine(ink, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), w, StrokeCap.Round)
        }
    }
}

/** A dialog in the app's look: raised panel, hairline, title. */
@Composable
fun AppDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Look.colors
    val shape = Look.shape.panel
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .surface(shape, c.raised, c.outline)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = Look.type.title)
            content()
        }
    }
}

/**
 * After LDAC is turned on in the earbuds: the phone still picks the codec, and Samsung phones (and maybe others)
 * also need LDAC turned on for the earbuds in their own Bluetooth settings. Android does not let an app read the
 * codec in use, so PairFree cannot tell whether the phone followed (docs/soundpeats/PROTOCOL.md section 12).
 */
@Composable
fun LdacPhoneDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AppDialog(stringResource(R.string.ldac_on_title), onDismiss) {
        Text(stringResource(R.string.ldac_on_body), color = Look.colors.secondaryText)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton(stringResource(R.string.close), onDismiss, Modifier.weight(1f))
            AppButton(
                stringResource(R.string.bluetooth_settings),
                {
                    onDismiss()
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                },
                Modifier.weight(1f),
                primary = true,
            )
        }
    }
}

/** Asks before doing something that cannot be undone from the app. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AppDialog(title, onDismiss) {
        Text(body, color = Look.colors.secondaryText)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton(stringResource(R.string.cancel), onDismiss, Modifier.weight(1f))
            AppButton(
                confirm,
                {
                    onDismiss()
                    onConfirm()
                },
                Modifier.weight(1f),
                primary = !destructive,
                destructive = destructive,
            )
        }
    }
}

/** Picks one of [options]. The current one has a filled ring and heavier text. */
@Composable
fun <T> PickerDialog(
    title: String,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Look.colors
    AppDialog(title, onDismiss) {
        Column(
            Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .selectableGroup(),
        ) {
            for (option in options) {
                val on = option == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(selected = on, role = Role.RadioButton) {
                            onDismiss()
                            onPick(option)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioMark(on)
                    Text(
                        label(option),
                        style = Look.type.body.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (on) c.accent else c.text,
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioMark(on: Boolean) {
    val c = Look.colors
    Box(
        Modifier
            .size(20.dp)
            .border(1.5.dp, if (on) c.accent else c.label, CircleShape)
            .padding(5.dp),
    ) {
        if (on) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(c.accent),
            )
        }
    }
}

/** A dialog with one line of text, for a name. [onDone] gets the text when it is not blank. */
@Composable
fun TextEntryDialog(
    title: String,
    initial: String,
    hint: String,
    confirm: String,
    maxLength: Int,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val c = Look.colors
    val shape = Look.shape.control
    fun done() {
        if (text.isBlank()) return
        onDismiss()
        onDone(text)
    }
    AppDialog(title, onDismiss) {
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(maxLength) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .border(Look.shape.hairline, c.accent, shape)
                .padding(12.dp),
            textStyle = Look.type.body.copy(color = c.text),
            cursorBrush = SolidColor(c.accent),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { done() }),
            decorationBox = { field ->
                Box {
                    if (text.isEmpty()) Text(hint, color = c.label)
                    field()
                }
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton(stringResource(R.string.cancel), onDismiss, Modifier.weight(1f))
            AppButton(confirm, { done() }, Modifier.weight(1f), primary = true, enabled = text.isNotBlank())
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/**
 * The app's language, where more than one is built in: its own name on a button, and the built-in languages to pick
 * from, each by its own name (docs/TRANSLATING.md). "Same as the phone" lets Android's language decide again.
 */
@Composable
fun LanguageButton(modifier: Modifier = Modifier) {
    if (Languages.ALL.size < 2) return
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val chosen = Languages.chosen(context)
    val system = stringResource(R.string.app_language_system)
    AppButton(stringResource(R.string.app_language, chosen?.let(Languages::name) ?: system), { picking = true }, modifier)
    if (picking) {
        PickerDialog(
            title = stringResource(R.string.app_language_title),
            options = listOf<String?>(null) + Languages.ALL,
            selected = chosen,
            label = { it?.let(Languages::name) ?: system },
            onPick = { tag -> Languages.activityOf(context)?.let { Languages.choose(it, tag) } },
            onDismiss = { picking = false },
        )
    }
}

/**
 * The app's look, where more than one is built in: its name on a button, and the built-in looks to pick from
 * (docs/LOOKS.md). The screens are drawn again in the chosen one at once.
 */
@Composable
fun LookButton(modifier: Modifier = Modifier) {
    if (Looks.ALL.size < 2) return
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val chosen by Looks.flow(context).collectAsState()
    AppButton(stringResource(R.string.app_look, stringResource(chosen.name)), { picking = true }, modifier)
    if (picking) {
        PickerDialog(
            title = stringResource(R.string.app_look_title),
            options = Looks.ALL,
            selected = chosen,
            label = { stringResource(it.name) },
            onPick = { Looks.choose(context, it) },
            onDismiss = { picking = false },
        )
    }
}
