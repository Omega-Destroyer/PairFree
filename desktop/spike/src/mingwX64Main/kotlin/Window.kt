@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.omegadestroyer.pairfree.spike

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import platform.gdiplus.*
import platform.windows.*

// Studio's colours (data/looks/studio.json), typed here only for the spike: the real app reads the look.
private const val BACKGROUND = 0xFF101214u
private const val PANEL = 0xFF181B1Fu
private const val HAIRLINE = 0xFF2B3037u
private const val TEXT = 0xFFE8E6E1u
private const val LABEL = 0xFF8A9099u
private const val ACCENT = 0xFFE6A23Cu

/** The lines the window shows; the Bluetooth thread changes them and asks for a repaint. */
@kotlin.concurrent.Volatile
var statusLine: String = "Connecting…"

@kotlin.concurrent.Volatile
var linkedLine: String = ""

const val WM_STATUS: UInt = 0x8001u

/** An Android vector path (only M, L, C and Z occur in the generated drawings, bar two arcs) as a GDI+ path. */
private fun pathOf(data: String): COpaquePointer? = memScoped {
    val out = alloc<COpaquePointerVar>()
    GdipCreatePath(FillModeWinding, out.ptr)
    val path = out.value
    val tokens = Regex("[MLCZz]|-?[0-9]*\\.?[0-9]+").findAll(data).map { it.value }.toList()
    var i = 0
    var x = 0f
    var y = 0f
    var command = "M"
    fun next() = tokens[i++].toFloat()
    while (i < tokens.size) {
        if (tokens[i][0].isLetter()) command = tokens[i++]
        when (command) {
            "M" -> { GdipStartPathFigure(path); x = next(); y = next(); command = "L" }
            "L" -> { val nx = next(); val ny = next(); GdipAddPathLine(path, x, y, nx, ny); x = nx; y = ny }
            "C" -> {
                val a = next(); val b = next(); val c = next(); val d = next(); val nx = next(); val ny = next()
                GdipAddPathBezier(path, x, y, a, b, c, d, nx, ny); x = nx; y = ny
            }
            "Z", "z" -> GdipClosePathFigure(path)
        }
    }
    path
}

private fun draw(graphics: COpaquePointer?, art: List<ArtShape>, left: Float, top: Float, scale: Float) = memScoped {
    GdipTranslateWorldTransform(graphics, left, top, MatrixOrderPrepend)
    GdipScaleWorldTransform(graphics, scale, scale, MatrixOrderPrepend)
    for (shape in art) {
        val path = pathOf(shape.path)
        shape.fill?.let {
            val brush = alloc<COpaquePointerVar>()
            GdipCreateSolidFill(it.toUInt(), brush.ptr)
            GdipFillPath(graphics, brush.value, path)
            GdipDeleteBrush(brush.value)
        }
        shape.stroke?.let {
            val pen = alloc<COpaquePointerVar>()
            GdipCreatePen1(it.toUInt(), shape.width, UnitWorld, pen.ptr)
            GdipSetPenLineCap197819(pen.value, LineCapRound, LineCapRound, DashCapRound)
            GdipSetPenLineJoin(pen.value, LineJoinRound)
            GdipDrawPath(graphics, pen.value, path)
            GdipDeletePen(pen.value)
        }
        GdipDeletePath(path)
    }
    GdipResetWorldTransform(graphics)
}

private fun text(graphics: COpaquePointer?, s: String, x: Float, y: Float, size: Float, colour: UInt, family: String = "Segoe UI") = memScoped {
    val fam = alloc<COpaquePointerVar>()
    GdipCreateFontFamilyFromName(family.wcstr.ptr, null, fam.ptr)
    val font = alloc<COpaquePointerVar>()
    GdipCreateFont(fam.value, size, 0, UnitPixel, font.ptr)
    val brush = alloc<COpaquePointerVar>()
    GdipCreateSolidFill(colour, brush.ptr)
    val box = alloc<RectF>()
    box.X = x; box.Y = y; box.Width = 0f; box.Height = 0f
    GdipDrawString(graphics, s.wcstr.ptr, -1, font.value, box.ptr, null, brush.value)
    GdipDeleteBrush(brush.value)
    GdipDeleteFont(font.value)
    GdipDeleteFontFamily(fam.value)
}

private fun rect(graphics: COpaquePointer?, x: Float, y: Float, w: Float, h: Float, fill: UInt, outline: UInt?) = memScoped {
    val brush = alloc<COpaquePointerVar>()
    GdipCreateSolidFill(fill, brush.ptr)
    GdipFillRectangle(graphics, brush.value, x, y, w, h)
    GdipDeleteBrush(brush.value)
    if (outline != null) {
        val pen = alloc<COpaquePointerVar>()
        GdipCreatePen1(outline, 1f, UnitPixel, pen.ptr)
        GdipDrawRectangle(graphics, pen.value, x, y, w, h)
        GdipDeletePen(pen.value)
    }
}

private fun paint(window: HWND?) = memScoped {
    val ps = alloc<PAINTSTRUCT>()
    val dc = BeginPaint(window, ps.ptr)
    val g = alloc<COpaquePointerVar>()
    GdipCreateFromHDC(dc, g.ptr)
    val graphics = g.value
    GdipSetSmoothingMode(graphics, SmoothingModeAntiAlias)
    GdipSetTextRenderingHint(graphics, TextRenderingHintClearTypeGridFit)
    rect(graphics, 0f, 0f, 4000f, 4000f, BACKGROUND, null)
    text(graphics, "PairFree", 24f, 18f, 26f, TEXT)
    text(graphics, "DESKTOP SPIKE", 26f, 54f, 11f, LABEL, "Consolas")
    rect(graphics, 24f, 84f, 432f, 190f, PANEL, HAIRLINE)
    draw(graphics, H3_BUD, 40f, 96f, 1.7f)
    text(graphics, "SOUNDPEATS H3", 220f, 110f, 11f, LABEL, "Consolas")
    text(graphics, statusLine, 220f, 132f, 18f, ACCENT)
    text(graphics, linkedLine, 24f, 290f, 13f, LABEL)
    GdipDeleteGraphics(graphics)
    EndPaint(window, ps.ptr)
}

private fun windowProc(window: HWND?, message: UInt, w: WPARAM, l: LPARAM): LRESULT {
    when (message) {
        WM_PAINT.toUInt() -> { paint(window); return 0 }
        WM_ERASEBKGND.toUInt() -> return 1
        WM_STATUS -> { InvalidateRect(window, null, 0); return 0 }
        WM_DESTROY.toUInt() -> { PostQuitMessage(0); return 0 }
    }
    return DefWindowProcW(window, message, w, l)
}

/** Opens the window and runs its messages until it is closed; [opened] gets the window so another thread can ask for a repaint. */
fun runWindow(opened: (HWND?) -> kotlin.Unit): kotlin.Unit = memScoped {
    val token = alloc<ULONG_PTRVar>()
    val input = alloc<GdiplusStartupInput>()
    input.GdiplusVersion = 1u
    GdiplusStartup(token.ptr, input.ptr, null)
    val instance = GetModuleHandleW(null)
    val name = "PairFreeSpike".wcstr.ptr
    val wc = alloc<WNDCLASSEXW>()
    wc.cbSize = sizeOf<WNDCLASSEXW>().convert()
    wc.lpfnWndProc = staticCFunction(::windowProc)
    wc.hInstance = instance
    wc.lpszClassName = name
    wc.hCursor = LoadCursorW(null, IDC_ARROW)
    RegisterClassExW(wc.ptr)
    val window = CreateWindowExW(0u, name, "PairFree".wcstr.ptr, WS_OVERLAPPEDWINDOW.toUInt(), CW_USEDEFAULT, CW_USEDEFAULT, 500, 380, null, null, instance, null)
    ShowWindow(window, SW_SHOW)
    UpdateWindow(window)
    opened(window)
    val msg = alloc<MSG>()
    while (GetMessageW(msg.ptr, null, 0u, 0u) > 0) {
        TranslateMessage(msg.ptr)
        DispatchMessageW(msg.ptr)
    }
    GdiplusShutdown(token.value)
}
