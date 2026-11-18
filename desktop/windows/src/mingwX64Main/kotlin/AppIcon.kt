@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.omegadestroyer.pairfree.windows

import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.gdiplus.*
import platform.windows.HICON
import platform.windows.HICONVar

/**
 * PairFree's icon, the same as the Android launcher's (a dot with two sound arcs, Studio amber, on the dark Studio background),
 * drawn at [size] pixels with GDI+: no image file is shipped for it.
 */
internal fun appIcon(size: Int): HICON? = memScoped {
    val bitmap = alloc<COpaquePointerVar>()
    GdipCreateBitmapFromScan0(size, size, 0, 0x26200A /* PixelFormat32bppARGB */, null, bitmap.ptr)
    val g = alloc<COpaquePointerVar>()
    GdipGetImageGraphicsContext(bitmap.value, g.ptr)
    GdipSetSmoothingMode(g.value, SmoothingModeAntiAlias)
    // The launcher icon is drawn on a 108 unit canvas whose middle 66 units show; this takes those.
    val unit = size / 66f
    GdipTranslateWorldTransform(g.value, -21f * unit, -21f * unit, MatrixOrderPrepend)
    GdipScaleWorldTransform(g.value, unit, unit, MatrixOrderPrepend)
    val back = alloc<COpaquePointerVar>()
    GdipCreateSolidFill(0xFF101214u, back.ptr)
    GdipFillRectangle(g.value, back.value, 21f, 21f, 66f, 66f)
    GdipDeleteBrush(back.value)
    val amber = 0xFFE6A23Cu
    val dot = alloc<COpaquePointerVar>()
    GdipCreateSolidFill(amber, dot.ptr)
    GdipFillEllipse(g.value, dot.value, 40f, 48f, 12f, 12f)
    GdipDeleteBrush(dot.value)
    val pen = alloc<COpaquePointerVar>()
    GdipCreatePen1(amber, 3.5f, UnitWorld, pen.ptr)
    GdipSetPenLineCap197819(pen.value, LineCapRound, LineCapRound, DashCapRound)
    GdipDrawArc(g.value, pen.value, 46f - 13f, 54f - 13f, 26f, 26f, -60f, 120f)
    GdipDrawArc(g.value, pen.value, 46f - 23f, 54f - 23f, 46f, 46f, -60f, 120f)
    GdipDeletePen(pen.value)
    GdipDeleteGraphics(g.value)
    val icon = alloc<HICONVar>()
    GdipCreateHICONFromBitmap(bitmap.value, icon.ptr)
    GdipDisposeImage(bitmap.value)
    icon.value
}
