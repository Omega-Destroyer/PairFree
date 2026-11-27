package io.github.omegadestroyer.pairfree.ui

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A text box. The app keeps the text and the caret; this draws them and takes the focus when pressed. */
class TextField(
    private val text: String,
    private val caret: Int,
    private val hint: String,
    private val focused: Boolean,
    key: String,
    onClick: () -> Unit,
) : Clickable(key, true, onClick) {
    override fun height(ctx: Ctx, width: Float) = 36f

    override fun draw(ctx: Ctx) {
        val t = ctx.theme
        val c = ctx.canvas
        c.fillRoundRect(x, y, w, h, t.smallCorner, t.raised)
        c.strokeRoundRect(x + 0.5f, y + 0.5f, w - 1f, h - 1f, t.smallCorner, if (focused) t.accent else t.outline, if (focused) 1.5f else t.look.hairline)
        val f = t.body
        val ty = y + (h - c.lineHeight(f)) / 2f
        c.pushClip(x + 6f, y, w - 12f, h)
        if (text.isEmpty() && !focused) c.text(hint, x + 10f, ty, f, t.secondaryText)
        c.text(text, x + 10f, ty, f, t.text)
        if (focused) {
            val cx = x + 10f + c.textWidth(text.substring(0, caret.coerceIn(0, text.length)), f)
            c.line(cx, ty, cx, ty + c.lineHeight(f), t.accent, 1.5f)
        }
        c.popClip()
    }
}

/** A value on a line, dragged with the pointer: whole [steps] from [min] to [max]. */
class Slider(private val value: Int, private val min: Int, private val max: Int, key: String, private val onChange: (Int) -> Unit) :
    Clickable(key, true, {}) {
    override fun height(ctx: Ctx, width: Float) = 28f

    private fun trackLeft() = x + 10f

    private fun trackWidth() = w - 20f

    override fun drag(px: Float, py: Float) {
        val f = ((px - trackLeft()) / trackWidth()).coerceIn(0f, 1f)
        val v = (min + f * (max - min)).roundToInt()
        if (v != value) onChange(v)
    }

    override fun draw(ctx: Ctx) {
        val t = ctx.theme
        val c = ctx.canvas
        val cy = y + h / 2f
        c.fillRoundRect(trackLeft(), cy - 2f, trackWidth(), 4f, 2f, t.raised)
        val f = if (max == min) 0f else (value - min).toFloat() / (max - min)
        val mid = if (min < 0 && max > 0) (0 - min).toFloat() / (max - min) else 0f
        val a = min(f, mid)
        val b = max(f, mid)
        c.fillRoundRect(trackLeft() + trackWidth() * a, cy - 2f, trackWidth() * (b - a), 4f, 2f, t.accent)
        c.fillCircle(trackLeft() + trackWidth() * f, cy, 9f, t.accent)
        c.fillCircle(trackLeft() + trackWidth() * f, cy, 3.5f, t.onAccent)
    }
}

/** A small round-cornered button with one symbol: the minus and plus beside a slider. */
class StepButton(private val symbol: String, key: String, enabled: Boolean, onClick: () -> Unit) : Clickable(key, enabled, onClick) {
    override fun height(ctx: Ctx, width: Float) = 36f

    override fun draw(ctx: Ctx) {
        val t = ctx.theme
        val c = ctx.canvas
        val hot = enabled && ctx.hover == key
        c.fillRoundRect(x, y, w, h, t.smallCorner, if (hot) t.highlight else t.raised)
        c.strokeRoundRect(x + 0.5f, y + 0.5f, w - 1f, h - 1f, t.smallCorner, if (hot) t.accent else t.outline, t.look.hairline)
        val f = t.heading
        c.text(symbol, x + (w - c.textWidth(symbol, f)) / 2f, y + (h - c.lineHeight(f)) / 2f, f, if (enabled) t.text else t.secondaryText)
    }
}

/** A band of the equalizer as a button: its frequency over its gain. */
class BandCell(private val label: String, private val gain: String, private val selected: Boolean, key: String, onClick: () -> Unit) : Clickable(key, true, onClick) {
    override fun height(ctx: Ctx, width: Float) = 48f

    override fun draw(ctx: Ctx) {
        val t = ctx.theme
        val c = ctx.canvas
        val hot = ctx.hover == key
        c.fillRoundRect(x, y, w, h, t.smallCorner, if (selected) t.accent else if (hot) t.highlight else t.raised)
        c.strokeRoundRect(x + 0.5f, y + 0.5f, w - 1f, h - 1f, t.smallCorner, if (selected) t.accent else t.outline, t.look.hairline)
        val ink = if (selected) t.onAccent else t.secondaryText
        c.text(label, x + (w - c.textWidth(label, t.labelFont)) / 2f, y + 7f, t.labelFont, ink)
        c.text(gain, x + (w - c.textWidth(gain, t.bigNumber.copy(size = 14f))) / 2f, y + 24f, t.bigNumber.copy(size = 14f), if (selected) t.onAccent else t.text)
    }
}

/**
 * The equalizer's curve: each band's difference from stock, bands evenly spaced. When [editable], a press picks a band and dragging
 * sets its gain in steps of [stepDb]; the picked band's point is larger and its label in the accent colour.
 */
class EqGraph(
    private val gains: List<Double>,
    private val labels: List<String>,
    private val range: Int,
    private val limit: Int,
    private val stepDb: Double,
    private val selected: Int,
    private val editable: Boolean,
    key: String,
    private val onSelect: (Int) -> Unit,
    private val onGain: (Int, Double) -> Unit,
) : Clickable(key, editable, {}) {
    private val left = 32f
    private val top = 10f

    override fun height(ctx: Ctx, width: Float) = 220f

    private val n get() = gains.size

    private fun column() = (w - left) / n

    private fun bandAt(px: Float) = ((px - x - left) / column()).toInt().coerceIn(0, n - 1)

    private fun xOf(i: Int) = x + left + column() * (i + 0.5f)

    private fun bottom() = y + h - 26f

    private fun yOf(db: Double) = y + top + ((range - db) / (2.0 * range)).toFloat() * (bottom() - y - top)

    private fun dbAt(py: Float): Double {
        val raw = range - (py - y - top) / (bottom() - y - top) * 2 * range
        return (raw / stepDb).roundToInt() * stepDb
    }

    /** A press picks the band under the pointer and sets its gain; the widget is rebuilt every frame, so the band lives in the app as [selected]. */
    override fun press(px: Float, py: Float) {
        val b = bandAt(px)
        onSelect(b)
        onGain(b, dbAt(py).coerceIn(-limit.toDouble(), limit.toDouble()))
    }

    override fun drag(px: Float, py: Float) {
        onGain(selected, dbAt(py).coerceIn(-limit.toDouble(), limit.toDouble()))
    }

    override fun draw(ctx: Ctx) {
        val t = ctx.theme
        val c = ctx.canvas
        if (editable) c.fillRoundRect(x + left + column() * selected, y + top, column(), bottom() - y - top, 0f, t.raised)
        for (db in listOf(range, range / 2, 0, -range / 2, -range)) {
            val ly = yOf(db.toDouble())
            c.line(x + left, ly, x + w, ly, if (db == 0) t.label else t.hairline, 1f)
            val text = when {
                db > 0 -> "+$db"
                db < 0 -> "−${-db}"
                else -> "0"
            }
            c.text(text, x + left - 6f - c.textWidth(text, t.labelFont), ly - c.lineHeight(t.labelFont) / 2f, t.labelFont, t.label)
        }
        for (i in 0 until n - 1) c.line(xOf(i), yOf(gains[i]), xOf(i + 1), yOf(gains[i + 1]), t.accent, 2f)
        for (i in 0 until n) {
            if (editable && i == selected) {
                c.fillCircle(xOf(i), yOf(gains[i]), 8f, t.accent)
                c.fillCircle(xOf(i), yOf(gains[i]), 3f, t.onAccent)
            } else {
                c.fillCircle(xOf(i), yOf(gains[i]), 5f, t.accent)
                c.fillCircle(xOf(i), yOf(gains[i]), 3.5f, t.panel)
            }
        }
        for ((i, label) in labels.withIndex()) {
            val ink = if (editable && i == selected) t.accent else t.label
            c.text(label, xOf(i) - c.textWidth(label, t.labelFont) / 2f, y + h - c.lineHeight(t.labelFont), t.labelFont, ink)
        }
    }

    internal companion object {
        /** The dB scale: the user range, widened in steps of 6 dB when a preset reaches further from stock, so the grid at half the scale stays on whole steps. */
        fun scale(curve: List<Double>, userRange: Int): Int {
            val reach = curve.maxOfOrNull { abs(it) } ?: 0.0
            return max(userRange, (kotlin.math.ceil(reach / 6) * 6).toInt())
        }
    }
}
