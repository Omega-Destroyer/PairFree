@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, DelicateCoroutinesApi::class)

package io.github.omegadestroyer.pairfree.windows

import io.github.omegadestroyer.pairfree.shell.CloseAction
import io.github.omegadestroyer.pairfree.shell.DesktopSettings
import io.github.omegadestroyer.pairfree.ui.DesktopApp
import io.github.omegadestroyer.pairfree.ui.Key
import io.github.omegadestroyer.pairfree.ui.SystemColors
import io.github.omegadestroyer.pairfree.ui.TrayItem
import kotlinx.cinterop.CFunction
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.get
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import platform.gdiplus.GdipCreateFromHDC
import platform.gdiplus.GdipDeleteGraphics
import platform.gdiplus.GdipScaleWorldTransform
import platform.gdiplus.GdiplusShutdown
import platform.gdiplus.GdiplusStartup
import platform.gdiplus.GdiplusStartupInput
import platform.gdiplus.MatrixOrderPrepend
import platform.windows.*

/** A message another thread posts to ask the window for a new frame. */
private const val WM_REDRAW: UInt = 0x8001u

/** The tray icon's callback message. */
private const val WM_TRAY: UInt = 0x8002u

/** Asks the window to end the program; a second instance asks the first to show itself. */
private const val WM_QUIT_APP: UInt = 0x8003u
private const val WM_SHOW_APP: UInt = 0x8004u

private const val CLASS_NAME = "PairFreeWindow"

/** Functions other threads ask the window's thread to run, and the message that tells it to. */
private const val WM_RUN: UInt = 0x8005u
private val pendingRuns = kotlin.concurrent.AtomicReference<List<() -> Unit>>(emptyList())

private fun runOnUi(block: () -> Unit) {
    while (true) {
        val old = pendingRuns.value
        if (pendingRuns.compareAndSet(old, old + block)) break
    }
    PostMessageW(State.window, WM_RUN, 0u, 0)
}

/** The window's state: Windows calls back into plain functions, so what they need is kept here. */
private object State {
    var app: DesktopApp? = null
    var window: HWND? = null
    var settings: DesktopSettings? = null
    var tracking = false
    var width = 0
    var height = 0
    var scale = 1f
    var smallIcon: HICON? = null
    var trayTip = ""
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
    val app = State.app ?: return@memScoped
    val ps = alloc<PAINTSTRUCT>()
    val dc = BeginPaint(window, ps.ptr)
    val w = State.width
    val h = State.height
    // Draw into a bitmap and copy it over at once, so the window never shows a half-drawn frame.
    val memory = CreateCompatibleDC(dc)
    val bitmap = CreateCompatibleBitmap(dc, w, h)
    val old = SelectObject(memory, bitmap)
    val g = alloc<COpaquePointerVar>()
    GdipCreateFromHDC(memory, g.ptr)
    GdipScaleWorldTransform(g.value, State.scale, State.scale, MatrixOrderPrepend)
    app.render(GdiCanvas(g.value), w / State.scale, h / State.scale)
    GdipDeleteGraphics(g.value)
    BitBlt(dc, 0, 0, w, h, memory, 0, 0, SRCCOPY.convert())
    SelectObject(memory, old)
    DeleteObject(bitmap)
    DeleteDC(memory)
    EndPaint(window, ps.ptr)
}

private fun x(l: LPARAM) = (l.toInt() and 0xFFFF).toShort().toFloat() / State.scale

private fun y(l: LPARAM) = ((l.toInt() shr 16) and 0xFFFF).toShort().toFloat() / State.scale

// ---- the tray ----

private const val TRAY_ID = 1u

private fun trayCall(action: UInt, tip: String? = null) = memScoped {
    val data = alloc<NOTIFYICONDATAW>()
    data.cbSize = sizeOf<NOTIFYICONDATAW>().convert()
    data.hWnd = State.window
    data.uID = TRAY_ID
    data.uFlags = (NIF_MESSAGE or NIF_ICON or NIF_TIP).convert()
    data.uCallbackMessage = WM_TRAY
    data.hIcon = State.smallIcon
    val text = (tip ?: "PairFree").take(127)
    for ((i, c) in text.withIndex()) data.szTip[i] = c.code.toUShort()
    data.szTip[text.length] = 0u
    Shell_NotifyIconW(action, data.ptr)
}

private fun updateTray() {
    val tip = State.app?.trayTip() ?: return
    if (tip != State.trayTip) {
        State.trayTip = tip
        trayCall(NIM_MODIFY.convert(), tip)
    }
}

private fun showMenu(window: HWND?) = memScoped {
    val items = State.app?.trayItems() ?: return@memScoped
    val menu = CreatePopupMenu()
    val actions = HashMap<Int, () -> Unit>()
    var id = 1
    for (item in items) when (item) {
        is TrayItem.Action -> {
            var flags = MF_STRING
            if (item.checked) flags = flags or MF_CHECKED
            if (!item.enabled) flags = flags or MF_GRAYED
            actions[id] = item.run
            AppendMenuW(menu, flags.convert(), id.convert(), item.text)
            id++
        }
        is TrayItem.Info -> { AppendMenuW(menu, (MF_STRING or MF_GRAYED).convert(), 0u, item.text) }
        TrayItem.Separator -> { AppendMenuW(menu, MF_SEPARATOR.convert(), 0u, null) }
    }
    val at = alloc<POINT>()
    GetCursorPos(at.ptr)
    // The menu closes when it loses the focus, which a program that is not in front never has: take it first (Microsoft's documented way).
    SetForegroundWindow(window)
    val chosen = TrackPopupMenu(menu, (TPM_RETURNCMD or TPM_RIGHTBUTTON).convert(), at.x, at.y, 0, window, null)
    PostMessageW(window, WM_NULL.convert(), 0u, 0)
    DestroyMenu(menu)
    actions[chosen]?.invoke()
    Unit
}

private fun showWindow(window: HWND?) {
    ShowWindow(window, SW_SHOW)
    if (IsIconic(window) != 0) ShowWindow(window, SW_RESTORE)
    SetForegroundWindow(window)
}

private fun saveBounds(window: HWND?) {
    // Remember where the window was, unless it is minimised or hidden (Windows then parks it far off screen).
    if (IsIconic(window) != 0 || IsWindowVisible(window) == 0) return
    memScoped {
        val r = alloc<RECT>()
        GetWindowRect(window, r.ptr)
        State.settings?.windowBounds = listOf(r.left, r.top, r.right - r.left, r.bottom - r.top)
    }
}

/** The close button: what the user chose, or the question on the first close. */
private fun onClose(window: HWND?) {
    val settings = State.settings
    var action = settings?.closeAction ?: CloseAction.EXIT
    if (action == CloseAction.ASK) {
        val answer = MessageBoxW(window, State.app?.closeQuestion ?: "", "PairFree", (MB_YESNOCANCEL or MB_ICONQUESTION).convert())
        when (answer) {
            IDYES -> { action = CloseAction.TRAY; settings?.closeAction = CloseAction.TRAY }
            IDNO -> { action = CloseAction.EXIT; settings?.closeAction = CloseAction.EXIT }
            else -> return
        }
    }
    saveBounds(window)
    if (action == CloseAction.TRAY) ShowWindow(window, SW_HIDE) else DestroyWindow(window)
}

private fun windowProc(window: HWND?, message: UInt, w: WPARAM, l: LPARAM): LRESULT {
    val app = State.app
    when (message) {
        WM_PAINT.toUInt() -> { paint(window); return 0 }
        WM_ERASEBKGND.toUInt() -> return 1
        WM_REDRAW -> { updateTray(); InvalidateRect(window, null, 0); return 0 }
        WM_SIZE.toUInt() -> {
            if (w.toInt() == SIZE_MINIMIZED && State.settings?.minimizeToTray == true) { ShowWindow(window, SW_HIDE); return 0 }
            State.width = (l.toInt() and 0xFFFF)
            State.height = ((l.toInt() shr 16) and 0xFFFF)
            InvalidateRect(window, null, 0)
            return 0
        }
        WM_MOUSEMOVE.toUInt() -> {
            if (!State.tracking) {
                memScoped {
                    val t = alloc<TRACKMOUSEEVENT>()
                    t.cbSize = sizeOf<TRACKMOUSEEVENT>().convert()
                    t.dwFlags = TME_LEAVE.convert()
                    t.hwndTrack = window
                    TrackMouseEvent(t.ptr)
                }
                State.tracking = true
            }
            app?.pointerMoved(x(l), y(l))
            return 0
        }
        WM_MOUSELEAVE.toUInt() -> { State.tracking = false; app?.pointerLeft(); return 0 }
        WM_LBUTTONDOWN.toUInt() -> { SetCapture(window); app?.pointerDown(x(l), y(l)); return 0 }
        WM_LBUTTONUP.toUInt() -> { ReleaseCapture(); app?.pointerUp(x(l), y(l)); return 0 }
        WM_MOUSEWHEEL.toUInt() -> {
            val delta = ((w.toLong() shr 16) and 0xFFFF).toShort().toInt()
            app?.wheel(-delta / 120f)
            return 0
        }
        WM_DPICHANGED.toUInt() -> {
            State.scale = (w.toInt() and 0xFFFF) / 96f
            val r = l.toLong().toCPointer<RECT>()?.pointed
            if (r != null) SetWindowPos(window, null, r.left, r.top, r.right - r.left, r.bottom - r.top, (SWP_NOZORDER or SWP_NOACTIVATE).convert())
            return 0
        }
        WM_TRAY -> {
            when ((l.toInt() and 0xFFFF).toUInt()) {
                WM_LBUTTONUP.toUInt() -> if (IsWindowVisible(window) != 0 && IsIconic(window) == 0) ShowWindow(window, SW_HIDE) else showWindow(window)
                WM_RBUTTONUP.toUInt() -> showMenu(window)
            }
            return 0
        }
        WM_CHAR.toUInt() -> { app?.keyChar(w.toInt().toChar()); return 0 }
        WM_KEYDOWN.toUInt() -> {
            val key = when (w.toInt()) {
                VK_BACK -> Key.BACKSPACE
                VK_DELETE -> Key.DELETE
                VK_LEFT -> Key.LEFT
                VK_RIGHT -> Key.RIGHT
                VK_HOME -> Key.HOME
                VK_END -> Key.END
                VK_RETURN -> Key.ENTER
                VK_ESCAPE -> Key.ESCAPE
                else -> null
            }
            if (key != null) { app?.keyDown(key); return 0 }
        }
        WM_RUN -> {
            val runs = pendingRuns.getAndSet(emptyList())
            runs.forEach { it() }
            return 0
        }
        WM_TIMER.toUInt() -> {
            if (app?.animating == true) InvalidateRect(window, null, 0)
            return 0
        }
        WM_SHOW_APP -> { showWindow(window); return 0 }
        WM_QUIT_APP -> { saveBounds(window); DestroyWindow(window); return 0 }
        WM_CLOSE.toUInt() -> { onClose(window); return 0 }
        WM_DESTROY.toUInt() -> {
            trayCall(NIM_DELETE.convert())
            PostQuitMessage(0)
            return 0
        }
    }
    return DefWindowProcW(window, message, w, l)
}

/**
 * Opens PairFree's window and runs it until the program ends. A second start brings the first one to the front instead. [create] makes
 * the app once the window exists, given what only Windows can do for it and the function that asks the window for a new frame (safe to
 * call from any thread).
 */
fun runAppWindow(settings: DesktopSettings, settingsPath: String, create: (io.github.omegadestroyer.pairfree.ui.Host, invalidate: () -> Unit) -> DesktopApp) = memScoped {
    val mutex = CreateMutexW(null, 1, "Local\\PairFree-single-instance")
    if (GetLastError() == ERROR_ALREADY_EXISTS.toUInt()) {
        val other = FindWindowW(CLASS_NAME, null)
        if (other != null) PostMessageW(other, WM_SHOW_APP, 0u, 0)
        return@memScoped
    }
    // Windows 10 1607's calls, looked up by name: the headers the toolchain ships do not declare them.
    user32<(COpaquePointer?) -> Int>("SetProcessDpiAwarenessContext")?.invoke((-4L).toCPointer<CPointed>())
    val token = alloc<ULONG_PTRVar>()
    val input = alloc<GdiplusStartupInput>()
    input.GdiplusVersion = 1u
    GdiplusStartup(token.ptr, input.ptr, null)
    val instance = GetModuleHandleW(null)
    val name = CLASS_NAME.wcstr.ptr
    val dpi = (user32<() -> UInt>("GetDpiForSystem")?.invoke()?.toInt() ?: 96).coerceAtLeast(96)
    State.scale = dpi / 96f
    val bigIcon = appIcon((32 * State.scale).toInt())
    State.smallIcon = appIcon((16 * State.scale).toInt())
    val wc = alloc<WNDCLASSEXW>()
    wc.cbSize = sizeOf<WNDCLASSEXW>().convert()
    wc.lpfnWndProc = staticCFunction(::windowProc)
    wc.hInstance = instance
    wc.lpszClassName = name
    wc.hCursor = LoadCursorW(null, IDC_ARROW)
    wc.hIcon = bigIcon
    wc.hIconSm = State.smallIcon
    RegisterClassExW(wc.ptr)
    // The size is the one the design gives (840 x 720 logical pixels) at this display's scale, or where the user last left the window.
    val saved = settings.windowBounds
    val (wx, wy, ww, wh) = saved ?: listOf(CW_USEDEFAULT, CW_USEDEFAULT, (840 * State.scale).toInt(), (720 * State.scale).toInt())
    val window = CreateWindowExW(0u, name, "PairFree".wcstr.ptr, WS_OVERLAPPEDWINDOW.toUInt(), wx, wy, ww, wh, null, null, instance, null)
    State.window = window
    State.settings = settings
    SetTimer(window, 1u, 50u, null)
    trayCall(NIM_ADD.convert())
    val host = object : io.github.omegadestroyer.pairfree.ui.Host {
        override fun openBluetoothSettings() {
            ShellExecuteW(null, "open", "ms-settings:bluetooth", null, null, SW_SHOWNORMAL)
        }

        override fun showWindow() {
            PostMessageW(window, WM_SHOW_APP, 0u, 0)
        }

        override fun exit() {
            PostMessageW(window, WM_QUIT_APP, 0u, 0)
        }

        override val settingsPath: String = settingsPath

        override fun openTextFile(): String? = FileDialogs.openText(window)

        override fun saveTextFile(name: String, text: String): Boolean = FileDialogs.saveText(window, name, text)

        override fun copyText(text: String) {
            if (OpenClipboard(window) == 0) return
            EmptyClipboard()
            val bytes = (text.length + 1) * 2
            val memory = GlobalAlloc(GMEM_MOVEABLE.convert(), bytes.convert())
            val target = GlobalLock(memory)?.reinterpret<UShortVar>()
            if (target != null) {
                for ((i, c) in text.withIndex()) target[i] = c.code.toUShort()
                target[text.length] = 0u
                GlobalUnlock(memory)
                SetClipboardData(13u /* CF_UNICODETEXT */, memory)
            }
            CloseClipboard()
        }

        override val tones = WaveOutPlayer { run -> runOnUi(run) }

        override fun background(work: () -> Unit) {
            GlobalScope.launch(Dispatchers.Default) { work() }
        }

        override fun runOnUi(work: () -> Unit) = io.github.omegadestroyer.pairfree.windows.runOnUi(work)

        override val canInstallUpdates: Boolean get() = io.github.omegadestroyer.pairfree.shell.UpdateKey.present

        override fun fetchLatestRelease() =
            SelfUpdate.get(io.github.omegadestroyer.pairfree.shell.Updates.LATEST_URL, "application/vnd.github+json")?.decodeToString()?.let { io.github.omegadestroyer.pairfree.shell.Updates.parseLatest(it) }

        override fun installUpdate(release: io.github.omegadestroyer.pairfree.shell.ReleaseInfo): String? {
            val why = SelfUpdate.install(release.programUrl ?: return "no program in the release", release.signatureUrl)
            // Installed and started: this copy ends.
            if (why == null) PostMessageW(window, WM_QUIT_APP, 0u, 0)
            return why
        }

        override fun openUrl(url: String) {
            ShellExecuteW(null, "open", url, null, null, SW_SHOWNORMAL)
        }
    }
    State.app = create(host) { PostMessageW(window, WM_REDRAW, 0u, 0) }
    State.app?.started()
    ShowWindow(window, if (settings.startMinimized) (if (settings.minimizeToTray) SW_HIDE else SW_SHOWMINNOACTIVE) else SW_SHOW)
    UpdateWindow(window)
    val msg = alloc<MSG>()
    while (GetMessageW(msg.ptr, null, 0u, 0u) > 0) {
        TranslateMessage(msg.ptr)
        DispatchMessageW(msg.ptr)
    }
    GdiplusShutdown(token.value)
    ReleaseMutex(mutex)
}
