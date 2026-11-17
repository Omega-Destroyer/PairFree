@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.omegadestroyer.pairfree.windows

import io.github.omegadestroyer.pairfree.shell.DesktopSettings
import io.github.omegadestroyer.pairfree.ui.DesktopApp
import io.github.omegadestroyer.pairfree.ui.SystemColors
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.invoke
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import platform.gdiplus.GdiplusShutdown
import platform.gdiplus.GdiplusStartup
import platform.gdiplus.GdiplusStartupInput
import platform.gdiplus.GdipCreateFromHDC
import platform.gdiplus.GdipDeleteGraphics
import platform.gdiplus.GdipScaleWorldTransform
import platform.gdiplus.MatrixOrderPrepend
import platform.windows.*

/** A message another thread posts to ask the window for a new frame. */
private const val WM_REDRAW: UInt = 0x8001u

/** The window's state: Windows calls back into plain functions, so what they need is kept here. */
private object Host {
    var app: DesktopApp? = null
    var window: HWND? = null
    var settings: DesktopSettings? = null
    var tracking = false
    var width = 0
    var height = 0
    var scale = 1f
}

private inline fun <reified F : Function<*>> user32(name: String): CPointer<CFunction<F>>? =
    GetProcAddress(GetModuleHandleW("user32.dll"), name)?.reinterpret()

/** The system's accent colour and its dark or light mode, read from the registry where Windows keeps them. */
fun windowsColors(): SystemColors {
    fun dword(subKey: String, name: String): UInt? = memScoped {
        val data = alloc<UIntVar>()
        val size = alloc<UIntVar>()
        size.value = 4u
        val r = RegGetValueW(HKEY_CURRENT_USER, subKey.wcstr.ptr, name.wcstr.ptr, RRF_RT_DWORD.convert(), null, data.ptr, size.ptr)
        if (r == 0) data.value else null
    }
    val accent = dword("Software\\Microsoft\\Windows\\DWM", "ColorizationColor") ?: 0xFF0078D4u
    val light = dword("Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "AppsUseLightTheme") ?: 0u
    return SystemColors((accent or 0xFF000000u).toInt(), night = light == 0u)
}

private fun paint(window: HWND?) = memScoped {
    val app = Host.app ?: return@memScoped
    val ps = alloc<PAINTSTRUCT>()
    val dc = BeginPaint(window, ps.ptr)
    val w = Host.width
    val h = Host.height
    // Draw into a bitmap and copy it over at once, so the window never shows a half-drawn frame.
    val memory = CreateCompatibleDC(dc)
    val bitmap = CreateCompatibleBitmap(dc, w, h)
    val old = SelectObject(memory, bitmap)
    val g = alloc<COpaquePointerVar>()
    GdipCreateFromHDC(memory, g.ptr)
    GdipScaleWorldTransform(g.value, Host.scale, Host.scale, MatrixOrderPrepend)
    app.render(GdiCanvas(g.value), w / Host.scale, h / Host.scale)
    GdipDeleteGraphics(g.value)
    BitBlt(dc, 0, 0, w, h, memory, 0, 0, SRCCOPY.convert())
    SelectObject(memory, old)
    DeleteObject(bitmap)
    DeleteDC(memory)
    EndPaint(window, ps.ptr)
}

private fun x(l: LPARAM) = (l.toInt() and 0xFFFF).toShort().toFloat() / Host.scale

private fun y(l: LPARAM) = ((l.toInt() shr 16) and 0xFFFF).toShort().toFloat() / Host.scale

private fun windowProc(window: HWND?, message: UInt, w: WPARAM, l: LPARAM): LRESULT {
    val app = Host.app
    when (message) {
        WM_PAINT.toUInt() -> { paint(window); return 0 }
        WM_ERASEBKGND.toUInt() -> return 1
        WM_REDRAW -> { InvalidateRect(window, null, 0); return 0 }
        WM_SIZE.toUInt() -> {
            Host.width = (l.toInt() and 0xFFFF)
            Host.height = ((l.toInt() shr 16) and 0xFFFF)
            InvalidateRect(window, null, 0)
            return 0
        }
        WM_MOUSEMOVE.toUInt() -> {
            if (!Host.tracking) {
                memScoped {
                    val t = alloc<TRACKMOUSEEVENT>()
                    t.cbSize = sizeOf<TRACKMOUSEEVENT>().convert()
                    t.dwFlags = TME_LEAVE.convert()
                    t.hwndTrack = window
                    TrackMouseEvent(t.ptr)
                }
                Host.tracking = true
            }
            app?.pointerMoved(x(l), y(l))
            return 0
        }
        WM_MOUSELEAVE.toUInt() -> { Host.tracking = false; app?.pointerLeft(); return 0 }
        WM_LBUTTONDOWN.toUInt() -> { SetCapture(window); app?.pointerDown(x(l), y(l)); return 0 }
        WM_LBUTTONUP.toUInt() -> { ReleaseCapture(); app?.pointerUp(x(l), y(l)); return 0 }
        WM_MOUSEWHEEL.toUInt() -> {
            val delta = ((w.toLong() shr 16) and 0xFFFF).toShort().toInt()
            app?.wheel(-delta / 120f)
            return 0
        }
        WM_DPICHANGED.toUInt() -> {
            Host.scale = (w.toInt() and 0xFFFF) / 96f
            val r = l.toLong().toCPointer<RECT>()?.pointed
            if (r != null) SetWindowPos(window, null, r.left, r.top, r.right - r.left, r.bottom - r.top, (SWP_NOZORDER or SWP_NOACTIVATE).convert())
            return 0
        }
        WM_CLOSE.toUInt() -> {
            // Remember where the window was, unless it is minimised (Windows then parks it far off screen).
            if (IsIconic(window) == 0) memScoped {
                val r = alloc<RECT>()
                GetWindowRect(window, r.ptr)
                Host.settings?.windowBounds = listOf(r.left, r.top, r.right - r.left, r.bottom - r.top)
            }
            DestroyWindow(window)
            return 0
        }
        WM_DESTROY.toUInt() -> { PostQuitMessage(0); return 0 }
    }
    return DefWindowProcW(window, message, w, l)
}

/**
 * Opens PairFree's window and runs it until it is closed. [create] makes the app once the window exists, given the function that
 * asks the window for a new frame (safe to call from any thread).
 */
fun runAppWindow(title: String, settings: DesktopSettings, create: (invalidate: () -> Unit) -> DesktopApp) = memScoped {
    // Windows 10 1607's calls, looked up by name: the headers the toolchain ships do not declare them.
    user32<(COpaquePointer?) -> Int>("SetProcessDpiAwarenessContext")?.invoke((-4L).toCPointer<CPointed>())
    val token = alloc<ULONG_PTRVar>()
    val input = alloc<GdiplusStartupInput>()
    input.GdiplusVersion = 1u
    GdiplusStartup(token.ptr, input.ptr, null)
    val instance = GetModuleHandleW(null)
    val name = "PairFreeWindow".wcstr.ptr
    val wc = alloc<WNDCLASSEXW>()
    wc.cbSize = sizeOf<WNDCLASSEXW>().convert()
    wc.lpfnWndProc = staticCFunction(::windowProc)
    wc.hInstance = instance
    wc.lpszClassName = name
    wc.hCursor = LoadCursorW(null, IDC_ARROW)
    RegisterClassExW(wc.ptr)
    // The size is the one the design gives (840 x 720 logical pixels) at this display's scale, or where the user last left the window.
    val dpi = (user32<() -> UInt>("GetDpiForSystem")?.invoke()?.toInt() ?: 96).coerceAtLeast(96)
    Host.scale = dpi / 96f
    val saved = settings.windowBounds
    val (wx, wy, ww, wh) = saved ?: listOf(CW_USEDEFAULT, CW_USEDEFAULT, (840 * Host.scale).toInt(), (720 * Host.scale).toInt())
    val window = CreateWindowExW(0u, name, title.wcstr.ptr, WS_OVERLAPPEDWINDOW.toUInt(), wx, wy, ww, wh, null, null, instance, null)
    Host.window = window
    Host.settings = settings
    Host.app = create { PostMessageW(window, WM_REDRAW, 0u, 0) }
    ShowWindow(window, SW_SHOW)
    UpdateWindow(window)
    val msg = alloc<MSG>()
    while (GetMessageW(msg.ptr, null, 0u, 0u) > 0) {
        TranslateMessage(msg.ptr)
        DispatchMessageW(msg.ptr)
    }
    GdiplusShutdown(token.value)
}
