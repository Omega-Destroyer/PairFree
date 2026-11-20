package io.github.omegadestroyer.pairfree.ui

import io.github.omegadestroyer.pairfree.shell.ArtDrawing
import kotlin.math.max
import kotlin.math.min

/** What a widget draws with and what the pointer is over. */
class Ctx(val canvas: Canvas, val theme: Theme, val hover: String? = null, val pressed: String? = null)

/**
 * The toolkit's widgets. A screen builds a tree of them from the current state each time it is drawn (cheap: a screen is some dozens of
 * widgets), the tree is laid out for the width it has and drawn, and its clickable widgets are kept to find what the pointer is over.
 * State that must outlive a frame (scroll, the pointer, a drag) lives in the app, never in a widget.
 */
abstract class Widget {
    var x = 0f
    var y = 0f
    var w = 0f
    var h = 0f

    /** The height this widget takes at [width]. */
    abstract fun height(ctx: Ctx, width: Float): Float

    open fun place(ctx: Ctx, x: Float, y: Float, width: Float) {
        this.x = x
        this.y = y
        w = width
        h = height(ctx, width)
    }

    abstract fun draw(ctx: Ctx)

    /** Adds this widget and its children, back to front. */
    open fun collect(into: MutableList<Widget>) {
        into += this
    }

    fun contains(px: Float, py: Float) = px >= x && px < x + w && py >= y && py < y + h
}

/** A widget the pointer can press. [key] names it from one frame to the next. */
abstract class Clickable(val key: String, var enabled: Boolean = true, val onClick: () -> Unit) : Widget() {
    /** The pointer is down on this widget at ([px], [py]) and moves: a slider or a graph follows it. Called on the press too. */
    open fun drag(px: Float, py: Float) = Unit

    /** The pointer went down on this widget; by default the same as a move. */
    open fun press(px: Float, py: Float) = drag(px, py)
}

class Spacer(private val size: Float) : Widget() {
    override fun height(ctx: Ctx, width: Float) = size

    override fun draw(ctx: Ctx) = Unit
}

enum class Align { START, CENTER, END }

/** Text, wrapped to its width. */
class Label(
    private val text: String,
    private val font: Font? = null,
    private val color: Int? = null,
    private val align: Align = Align.START,
    private val caps: Boolean = false,
) : Widget() {
    private var lines: List<String> = emptyList()

    private fun shown() = if (caps) text.uppercase() else text

    private fun wrap(ctx: Ctx, width: Float): List<String> {
        val f = font ?: ctx.theme.body
        val out = mutableListOf<String>()
        for (paragraph in shown().split('\n')) {
            var line = ""
            for (word in paragraph.split(' ')) {
                val tryLine = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && ctx.canvas.textWidth(tryLine, f) > width) {
                    out += line
                    line = word
                } else {
                    line = tryLine
                }
            }
            out += line
        }
        return out
    }

    override fun height(ctx: Ctx, width: Float): Float {
        val f = font ?: ctx.theme.body
        return wrap(ctx, width).size * ctx.canvas.lineHeight(f)
    }

    override fun place(ctx: Ctx, x: Float, y: Float, width: Float) {
        super.place(ctx, x, y, width)
        lines = wrap(ctx, width)
    }

    override fun draw(ctx: Ctx) {
        val f = font ?: ctx.theme.body
        val c = color ?: ctx.theme.text
        val lh = ctx.canvas.lineHeight(f)
        for ((i, line) in lines.withIndex()) {
            val tw = ctx.canvas.textWidth(line, f)
            val lx = when (align) {
                Align.START -> x
                Align.CENTER -> x + (w - tw) / 2f
                Align.END -> x + w - tw
            }
            ctx.canvas.text(line, lx, y + i * lh, f, c)
        }
    }
}

/** Children one under the other, [gap] apart. */
class Column(private val children: List<Widget>, private val gap: Float = 8f) : Widget() {
    override fun height(ctx: Ctx, width: Float): Float =
        children.sumOf { it.height(ctx, width).toDouble() }.toFloat() + gap * max(0, children.size - 1)

    override fun place(ctx: Ctx, x: Float, y: Float, width: Float) {
        super.place(ctx, x, y, width)
        var cy = y
        for (c in children) {
            c.place(ctx, x, cy, width)
            cy += c.h + gap
        }
    }

    override fun draw(ctx: Ctx) = children.forEach { it.draw(ctx) }

    override fun collect(into: MutableList<Widget>) {
        into += this
        children.forEach { it.collect(into) }
    }
}

/** Children side by side, sharing the width by [weights] (all alike when not given). */
class Row(private val children: List<Widget>, private val gap: Float = 8f, private val weights: List<Float>? = null) : Widget() {
    private fun widths(width: Float): List<Float> {
        val ws = weights ?: children.map { 1f }
        val free = width - gap * max(0, children.size - 1)
        val total = ws.sum().coerceAtLeast(0.0001f)
        return ws.map { free * it / total }
    }

    override fun height(ctx: Ctx, width: Float): Float = widths(width).withIndex().maxOfOrNull { (i, cw) -> children[i].height(ctx, cw) } ?: 0f

    override fun place(ctx: Ctx, x: Float, y: Float, width: Float) {
        super.place(ctx, x, y, width)
        var cx = x
        for ((i, cw) in widths(width).withIndex()) {
            children[i].place(ctx, cx, y, cw)
            cx += cw + gap
        }
    }

    override fun draw(ctx: Ctx) = children.forEach { it.draw(ctx) }

    override fun collect(into: MutableList<Widget>) {
        into += this
        children.forEach { it.collect(into) }
    }
}

/** Two widgets side by side when [width] allows (each at least [minEach] wide), else one above the other: a left and a right earbud's settings. */
class Beside(private val first: Widget, private val second: Widget, private val minEach: Float = 380f, private val gap: Float = 12f) : Widget() {
    private fun sideBySide(width: Float) = width >= 2 * minEach + gap

    override fun height(ctx: Ctx, width: Float): Float =
        if (sideBySide(width)) {
            val each = (width - gap) / 2f
            max(first.height(ctx, each), second.height(ctx, each))
        } else {
            first.height(ctx, width) + gap + second.height(ctx, width)
        }

    override fun place(ctx: Ctx, x: Float, y: Float, width: Float) {
        super.place(ctx, x, y, width)
        if (sideBySide(width)) {
            val each = (width - gap) / 2f
            first.place(ctx, x, y, each)
            second.place(ctx, x + each + gap, y, each)
        } else {
            first.place(ctx, x, y, width)
            second.place(ctx, x, y + first.h + gap, width)
        }
    }

    override fun draw(ctx: Ctx) {
        first.draw(ctx)
        second.draw(ctx)
    }

    override fun collect(into: MutableList<Widget>) {
        into += this
        first.collect(into)
        second.collect(into)
    }
}

/** A raised area with the look's panel colour, its outline when the look has one, and [pad] inside. */
class Panel(private val child: Widget, private val pad: Float = 16f) : Widget() {
    override fun height(ctx: Ctx, width: Float) = child.height(ctx, width - 2 * pad) + 2 * pad

    override fun place(ctx: Ctx, x: Float, y: Float, width: Float) {
        super.place(ctx, x, y, width)
        child.place(ctx, x + pad, y + pad, width - 2 * pad)
    }

    override fun draw(ctx: Ctx) {
        val t = ctx.theme
        val look = t.look
        if (look.shadow > 0f) ctx.canvas.fillRoundRect(x + look.shadow, y + look.shadow, w, h, t.corner, t.shadow)
        ctx.canvas.fillRoundRect(x, y, w, h, t.corner, t.panel)
        if (look.panelBorder) ctx.canvas.strokeRoundRect(x + 0.5f, y + 0.5f, w - 1f, h - 1f, t.corner, t.outline, look.hairline)
        child.draw(ctx)
    }

    override fun collect(into: MutableList<Widget>) {
        into += this
        child.collect(into)
    }
}

/** A push button: the accent one is the screen's main action. */
class Button(
    private val text: String,
    key: String,
    private val primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) : Clickable(key, enabled, onClick) {
    override fun height(ctx: Ctx, width: Float) = 36f

    override fun draw(ctx: Ctx) {
        val t = ctx.theme
        val hot = enabled && ctx.hover == key
        val down = enabled && ctx.pressed == key
        val fill = when {
            primary && !enabled -> t.withAlpha(t.accent, 0.35f)
            primary -> if (down) t.withAlpha(t.accent, 0.8f) else t.accent
            down -> t.highlight
            hot -> t.highlight
            else -> t.raised
        }
        val c = ctx.canvas
        if (t.look.shadow > 0f && !down) c.fillRoundRect(x + t.look.shadow, y + t.look.shadow, w, h, t.smallCorner, t.shadow)
        c.fillRoundRect(x, y, w, h, t.smallCorner, fill)
        if (!primary) c.strokeRoundRect(x + 0.5f, y + 0.5f, w - 1f, h - 1f, t.smallCorner, if (hot) t.accent else t.outline, t.look.hairline)
        val color = when {
            !enabled -> if (primary) t.text else t.secondaryText
            primary -> t.onAccent
            else -> t.text
        }
        val f = t.body
        c.text(text, x + (w - c.textWidth(text, f)) / 2f, y + (h - c.lineHeight(f)) / 2f, f, color)
    }
}

/** One choice of a segmented control: the trough ([Trough]) is behind it, so it draws only what marks it. */
private class Choice(private val text: String, key: String, private val selected: Boolean, private val primary: Boolean, enabled: Boolean, onClick: () -> Unit) :
    Clickable(key, enabled, onClick) {
    override fun height(ctx: Ctx, width: Float) = if (primary) 40f else 34f

    override fun draw(ctx: Ctx) {
        val t = ctx.theme
        val c = ctx.canvas
        val hot = enabled && ctx.hover == key
        val inset = 3f
        val ix = x + inset
        val iy = y + inset
        val iw = w - 2 * inset
        val ih = h - 2 * inset
        val r = max(2f, t.smallCorner - 2f)
        when {
            selected -> {
                c.fillRoundRect(ix, iy, iw, ih, r, if (primary) t.accent else if (t.look.selectedFill) t.highlight else t.panel)
                if (!primary) c.strokeRoundRect(ix + 0.75f, iy + 0.75f, iw - 1.5f, ih - 1.5f, r, t.accent, 1.5f)
            }
            hot -> c.fillRoundRect(ix, iy, iw, ih, r, t.highlight)
        }
        val color = when {
            !enabled -> t.secondaryText
            selected && primary -> t.onAccent
            selected -> t.accent
            else -> t.text
        }
        val f = t.body
        val shown = fitted(c, text, f, w - 12f)
        c.text(shown, x + (w - c.textWidth(shown, f)) / 2f, y + (h - c.lineHeight(f)) / 2f, f, color)
    }
}

/** The track a segmented control's choices sit in: one outlined, rounded bar, so they read as one control and not as separate buttons. */
private class Trough(private val child: Widget) : Widget() {
    override fun height(ctx: Ctx, width: Float) = child.height(ctx, width)

    override fun place(ctx: Ctx, x: Float, y: Float, width: Float) {
        super.place(ctx, x, y, width)
        child.place(ctx, x, y, width)
    }

    override fun draw(ctx: Ctx) {
        val t = ctx.theme
        ctx.canvas.fillRoundRect(x, y, w, h, t.smallCorner, t.raised)
        ctx.canvas.strokeRoundRect(x + 0.5f, y + 0.5f, w - 1f, h - 1f, t.smallCorner, t.outline, t.look.hairline)
        child.draw(ctx)
    }

    override fun collect(into: MutableList<Widget>) {
        into += this
        child.collect(into)
    }
}

private fun fitted(c: Canvas, text: String, font: Font, width: Float): String {
    if (c.textWidth(text, font) <= width) return text
    var s = text
    while (s.length > 1 && c.textWidth("$s…", font) > width) s = s.dropLast(1)
    return "$s…"
}

/** Choices side by side, one of them selected: noise control's modes, a scene. Several rows when they do not fit one. */
fun segmented(prefix: String, items: List<String>, selected: Int?, primary: Boolean = true, perRow: Int = items.size, onSelect: (Int) -> Unit): Widget {
    // Rows of nearly equal length, so that seven choices at three a row are 3, 2 and 2 and not 3, 3 and 1.
    val lines = ((items.size + perRow.coerceAtLeast(1) - 1) / perRow.coerceAtLeast(1)).coerceAtLeast(1)
    val each = (items.size + lines - 1) / lines
    val rows = items.withIndex().chunked(each.coerceAtLeast(1)).map { chunk ->
        val cells = chunk.map { (i, text) -> Choice(text, "$prefix$i", i == selected, primary, true) { onSelect(i) } as Widget }
        Trough(Row(cells, gap = 0f)) as Widget
    }
    return Column(rows, gap = 6f)
}

/** A level as a bar: [fraction] 0..1, or null while unknown. */
class LevelBar(private val fraction: Float?, private val warnWhenLow: Boolean = false) : Widget() {
    override fun height(ctx: Ctx, width: Float) = 6f

    override fun draw(ctx: Ctx) {
        val t = ctx.theme
        ctx.canvas.fillRoundRect(x, y, w, h, 3f, t.raised)
        if (fraction != null) {
            val f = min(1f, max(0f, fraction))
            val low = warnWhenLow && fraction < 0.2f
            ctx.canvas.fillRoundRect(x, y, max(h, w * f), h, 3f, if (low) t.destructive else t.accent)
        }
    }
}

/** One bar per step, [level] of them filled, for earbuds that report a level in steps (fifths on the Space A40). */
class StepBar(private val level: Int, private val steps: Int) : Widget() {
    override fun height(ctx: Ctx, width: Float) = 22f

    override fun draw(ctx: Ctx) {
        val t = ctx.theme
        val gap = 4f
        val bw = (w - gap * (steps - 1)) / steps
        for (i in 0 until steps) ctx.canvas.fillRoundRect(x + i * (bw + gap), y, bw, h, 2f, if (i < level) t.accent else t.raised)
    }
}

/** A device drawing in the look's colours, as large as fits the [boxHeight] and centred. */
class ArtView(private val drawing: ArtDrawing?, private val boxHeight: Float, private val mirrored: Boolean = false) : Widget() {
    override fun height(ctx: Ctx, width: Float) = boxHeight

    override fun draw(ctx: Ctx) {
        val d = drawing ?: return
        val scale = min(w / d.width, h / d.height)
        val dw = d.width * scale
        val ox = x + (w - dw) / 2f
        val oy = y + (h - d.height * scale) / 2f
        for (p in d.paths) {
            ctx.canvas.path(p.commands, ox, oy, scale, mirrored, dw, p.fill?.let(ctx.theme::art), p.stroke?.let(ctx.theme::art), p.strokeWidth * scale)
        }
    }
}

/** Puts [child] at a fixed width, at the start. */
class Sized(private val child: Widget, private val width: Float) : Widget() {
    override fun height(ctx: Ctx, width: Float) = child.height(ctx, this.width)

    override fun place(ctx: Ctx, x: Float, y: Float, width: Float) {
        super.place(ctx, x, y, width)
        child.place(ctx, x, y, this.width)
    }

    override fun draw(ctx: Ctx) = child.draw(ctx)

    override fun collect(into: MutableList<Widget>) {
        into += this
        child.collect(into)
    }
}

/** Children laid over each other in the same box: the last is drawn on top. Used for a title line with a button at its end. */
class Stack(private val children: List<Widget>) : Widget() {
    override fun height(ctx: Ctx, width: Float) = children.maxOfOrNull { it.height(ctx, width) } ?: 0f

    override fun place(ctx: Ctx, x: Float, y: Float, width: Float) {
        super.place(ctx, x, y, width)
        children.forEach { it.place(ctx, x, y, width) }
    }

    override fun draw(ctx: Ctx) = children.forEach { it.draw(ctx) }

    override fun collect(into: MutableList<Widget>) {
        into += this
        children.forEach { it.collect(into) }
    }
}

/** A clickable card holding other widgets: an earbuds entry in the sidebar. The selected one has the accent outline. */
class Card(private val child: Widget, key: String, private val selected: Boolean, onClick: () -> Unit) : Clickable(key, true, onClick) {
    private val pad = 12f

    override fun height(ctx: Ctx, width: Float) = child.height(ctx, width - 2 * pad) + 2 * pad

    override fun place(ctx: Ctx, x: Float, y: Float, width: Float) {
        super.place(ctx, x, y, width)
        child.place(ctx, x + pad, y + pad, width - 2 * pad)
    }

    override fun draw(ctx: Ctx) {
        val t = ctx.theme
        val hot = ctx.hover == key
        ctx.canvas.fillRoundRect(x, y, w, h, t.corner, if (hot) t.highlight else t.raised)
        ctx.canvas.strokeRoundRect(x + 0.5f, y + 0.5f, w - 1f, h - 1f, t.corner, if (selected) t.accent else t.outline, if (selected) 1.5f else t.look.hairline)
        child.draw(ctx)
    }

    override fun collect(into: MutableList<Widget>) {
        into += this
        child.collect(into)
    }
}

/** [left] takes the width that [right] leaves, [right] a fixed [rightWidth]; both centred on the taller one's line. */
class Split(private val left: Widget, private val right: Widget, private val rightWidth: Float, private val gap: Float = 12f) : Widget() {
    override fun height(ctx: Ctx, width: Float) = max(left.height(ctx, width - rightWidth - gap), right.height(ctx, rightWidth))

    override fun place(ctx: Ctx, x: Float, y: Float, width: Float) {
        super.place(ctx, x, y, width)
        val lw = width - rightWidth - gap
        left.place(ctx, x, y + (h - left.height(ctx, lw)) / 2f, lw)
        right.place(ctx, x + width - rightWidth, y + (h - right.height(ctx, rightWidth)) / 2f, rightWidth)
    }

    override fun draw(ctx: Ctx) {
        left.draw(ctx)
        right.draw(ctx)
    }

    override fun collect(into: MutableList<Widget>) {
        into += this
        left.collect(into)
        right.collect(into)
    }
}

/** A switch's pill: on is the accent colour with the knob at the end. */
class SwitchMark(private val on: Boolean, private val enabled: Boolean = true) : Widget() {
    override fun height(ctx: Ctx, width: Float) = 22f

    override fun draw(ctx: Ctx) {
        val t = ctx.theme
        val c = ctx.canvas
        val track = if (on) t.accent else t.raised
        c.fillRoundRect(x + w - 40f, y, 40f, h, 11f, if (enabled) track else t.withAlpha(track, 0.4f))
        if (!on) c.strokeRoundRect(x + w - 39.5f, y + 0.5f, 39f, h - 1f, 11f, t.outline, t.look.hairline)
        c.fillCircle(x + w - (if (on) 11f else 29f), y + h / 2f, 7f, if (on) t.onAccent else t.label)
    }
}

/** A whole row that can be pressed, holding other widgets; the line under the pointer lights up. */
class Tappable(private val child: Widget, key: String, enabled: Boolean = true, onClick: () -> Unit) : Clickable(key, enabled, onClick) {
    override fun height(ctx: Ctx, width: Float) = child.height(ctx, width - 16f) + 16f

    override fun place(ctx: Ctx, x: Float, y: Float, width: Float) {
        super.place(ctx, x, y, width)
        child.place(ctx, x + 8f, y + 8f, width - 16f)
    }

    override fun draw(ctx: Ctx) {
        if (enabled && ctx.hover == key) ctx.canvas.fillRoundRect(x, y, w, h, ctx.theme.smallCorner, ctx.theme.highlight)
        child.draw(ctx)
    }

    override fun collect(into: MutableList<Widget>) {
        into += this
        child.collect(into)
    }
}

/** A hairline between rows. */
class Divider : Widget() {
    override fun height(ctx: Ctx, width: Float) = 1f

    override fun draw(ctx: Ctx) = ctx.canvas.line(x, y + 0.5f, x + w, y + 0.5f, ctx.theme.hairline, 1f)
}
