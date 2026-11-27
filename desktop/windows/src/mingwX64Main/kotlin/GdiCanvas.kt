@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.omegadestroyer.pairfree.windows

import io.github.omegadestroyer.pairfree.shell.PathCommand
import io.github.omegadestroyer.pairfree.ui.Canvas
import io.github.omegadestroyer.pairfree.ui.Font
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import platform.gdiplus.*

/**
 * The toolkit's canvas on GDI+: what the first Windows build draws with. Created for one paint, over a [graphics] whose transform
 * already holds the display's scale; the fonts live as long as the program.
 */
internal class GdiCanvas(private val graphics: COpaquePointer?, private val scale: Float = 1f) : Canvas {
    private val saved = ArrayList<UInt>()

    init {
        GdipSetSmoothingMode(graphics, SmoothingModeAntiAlias)
        // Windows' own text look: hinted glyphs with ClearType, drawn at whole pixels (see [text]), as every other Windows program's text is.
        GdipSetTextRenderingHint(graphics, TextRenderingHintClearTypeGridFit)
        GdipSetPixelOffsetMode(graphics, PixelOffsetModeHalf)
    }

    private fun argb(color: Int): UInt = color.toUInt()

    private inline fun <T> withBrush(color: Int, block: (COpaquePointer?) -> T): T = memScoped {
        val b = alloc<COpaquePointerVar>()
        GdipCreateSolidFill(argb(color), b.ptr)
        try {
            block(b.value)
        } finally {
            GdipDeleteBrush(b.value)
        }
    }

    private inline fun <T> withPen(color: Int, width: Float, block: (COpaquePointer?) -> T): T = memScoped {
        val p = alloc<COpaquePointerVar>()
        GdipCreatePen1(argb(color), width, UnitWorld, p.ptr)
        GdipSetPenLineCap197819(p.value, LineCapRound, LineCapRound, DashCapRound)
        GdipSetPenLineJoin(p.value, LineJoinRound)
        try {
            block(p.value)
        } finally {
            GdipDeletePen(p.value)
        }
    }

    /** A rounded rectangle as a path; a radius of 0 is a plain rectangle. */
    private fun roundRect(x: Float, y: Float, w: Float, h: Float, radius: Float): COpaquePointer? = memScoped {
        val out = alloc<COpaquePointerVar>()
        GdipCreatePath(FillModeWinding, out.ptr)
        val path = out.value
        val r = minOf(radius, w / 2f, h / 2f)
        if (r <= 0.5f) {
            GdipAddPathRectangle(path, x, y, w, h)
        } else {
            val d = r * 2f
            GdipAddPathArc(path, x, y, d, d, 180f, 90f)
            GdipAddPathArc(path, x + w - d, y, d, d, 270f, 90f)
            GdipAddPathArc(path, x + w - d, y + h - d, d, d, 0f, 90f)
            GdipAddPathArc(path, x, y + h - d, d, d, 90f, 90f)
            GdipClosePathFigure(path)
        }
        path
    }

    override fun fillRoundRect(x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int) {
        val path = roundRect(x, y, w, h, radius)
        withBrush(color) { GdipFillPath(graphics, it, path) }
        GdipDeletePath(path)
    }

    override fun strokeRoundRect(x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int, width: Float) {
        val path = roundRect(x, y, w, h, radius)
        withPen(color, width) { GdipDrawPath(graphics, it, path) }
        GdipDeletePath(path)
    }

    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) {
        withPen(color, width) { GdipDrawLine(graphics, it, x1, y1, x2, y2) }
    }

    override fun fillCircle(cx: Float, cy: Float, r: Float, color: Int) {
        withBrush(color) { GdipFillEllipse(graphics, it, cx - r, cy - r, r * 2, r * 2) }
    }

    /**
     * Text is drawn in device pixels, not through the display's scale: the font is a whole number of pixels tall and the first glyph sits on a
     * whole pixel, so the hinting has something to work with. A font scaled by a transform, or placed between pixels, comes out thin and blurred.
     */
    override fun text(text: String, x: Float, y: Float, font: Font, color: Int) {
        if (text.isEmpty()) return
        memScoped {
            val box = alloc<RectF>()
            box.X = kotlin.math.round(x * scale)
            box.Y = kotlin.math.round(y * scale)
            box.Width = 0f
            box.Height = 0f
            GdipResetWorldTransform(graphics)
            withBrush(color) { GdipDrawString(graphics, text.wcstr.ptr, -1, Fonts.of(font, scale), box.ptr, Fonts.format, it) }
            GdipScaleWorldTransform(graphics, scale, scale, MatrixOrderPrepend)
        }
    }

    override fun textWidth(text: String, font: Font): Float {
        if (text.isEmpty()) return 0f
        return memScoped {
            val layout = alloc<RectF>()
            layout.X = 0f; layout.Y = 0f; layout.Width = 100000f; layout.Height = 100000f
            val bounds = alloc<RectF>()
            GdipResetWorldTransform(graphics)
            GdipMeasureString(graphics, text.wcstr.ptr, -1, Fonts.of(font, scale), layout.ptr, Fonts.format, bounds.ptr, null, null)
            GdipScaleWorldTransform(graphics, scale, scale, MatrixOrderPrepend)
            bounds.Width / scale
        }
    }

    override fun lineHeight(font: Font): Float = font.size * 1.4f

    override fun path(commands: List<PathCommand>, x: Float, y: Float, scale: Float, mirror: Boolean, boxWidth: Float, fill: Int?, stroke: Int?, strokeWidth: Float) {
        memScoped {
            val out = alloc<COpaquePointerVar>()
            GdipCreatePath(FillModeWinding, out.ptr)
            val path = out.value
            var cx = 0f
            var cy = 0f
            for (c in commands) when (c) {
                is PathCommand.MoveTo -> { GdipStartPathFigure(path); cx = c.x; cy = c.y }
                is PathCommand.LineTo -> { GdipAddPathLine(path, cx, cy, c.x, c.y); cx = c.x; cy = c.y }
                is PathCommand.CubicTo -> { GdipAddPathBezier(path, cx, cy, c.x1, c.y1, c.x2, c.y2, c.x, c.y); cx = c.x; cy = c.y }
                PathCommand.Close -> GdipClosePathFigure(path)
            }
            val state = alloc<UIntVar>()
            GdipSaveGraphics(graphics, state.ptr)
            GdipTranslateWorldTransform(graphics, if (mirror) x + boxWidth else x, y, MatrixOrderPrepend)
            GdipScaleWorldTransform(graphics, if (mirror) -scale else scale, scale, MatrixOrderPrepend)
            if (fill != null) withBrush(fill) { GdipFillPath(graphics, it, path) }
            if (stroke != null) withPen(stroke, strokeWidth / scale) { GdipDrawPath(graphics, it, path) }
            GdipRestoreGraphics(graphics, state.value)
            GdipDeletePath(path)
        }
    }

    override fun pushClip(x: Float, y: Float, w: Float, h: Float) {
        memScoped {
            val state = alloc<UIntVar>()
            GdipSaveGraphics(graphics, state.ptr)
            saved += state.value
            GdipSetClipRect(graphics, x, y, w, h, CombineModeIntersect)
        }
    }

    override fun popClip() {
        if (saved.isNotEmpty()) GdipRestoreGraphics(graphics, saved.removeAt(saved.size - 1))
    }
}

/** The fonts, made once. The look's own typefaces come with the toolkit's fonts; until then Segoe UI and Consolas stand in. */
internal object Fonts {
    private val cache = HashMap<Pair<Font, Int>, COpaquePointer?>()
    private var families = HashMap<String, COpaquePointer?>()

    val format: COpaquePointer? by lazy {
        memScoped {
            val f = alloc<COpaquePointerVar>()
            GdipStringFormatGetGenericTypographic(f.ptr)
            val copy = alloc<COpaquePointerVar>()
            GdipCloneStringFormat(f.value, copy.ptr)
            // Keep spaces at the end of a measured string, and never wrap: the toolkit wraps text itself.
            GdipSetStringFormatFlags(copy.value, (StringFormatFlagsMeasureTrailingSpaces or StringFormatFlagsNoWrap or StringFormatFlagsNoClip).toInt())
            copy.value
        }
    }

    private fun family(name: String): COpaquePointer? = families.getOrPut(name) {
        memScoped {
            val fam = alloc<COpaquePointerVar>()
            GdipCreateFontFamilyFromName(name.wcstr.ptr, null, fam.ptr)
            fam.value
        }
    }

    /** The font at [scale] times its size, in whole pixels. */
    fun of(font: Font, scale: Float): COpaquePointer? {
        val pixels = kotlin.math.round(font.size * scale).toInt().coerceAtLeast(1)
        return cache.getOrPut(font to pixels) {
            memScoped {
                val out = alloc<COpaquePointerVar>()
                val style = if (font.bold) 1 else 0
                GdipCreateFont(family(if (font.mono) "Consolas" else "Segoe UI"), pixels.toFloat(), style, UnitPixel, out.ptr)
                out.value
            }
        }
    }
}
