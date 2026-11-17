package io.github.omegadestroyer.pairfree.ui

import io.github.omegadestroyer.pairfree.bluetooth.EarbudsConnection
import io.github.omegadestroyer.pairfree.bluetooth.LinkStatus
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.brands.Brands
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.shell.APP_VERSION
import io.github.omegadestroyer.pairfree.shell.BUILT_IN_LOOKS
import io.github.omegadestroyer.pairfree.shell.DesktopLook
import io.github.omegadestroyer.pairfree.shell.DesktopSettings
import io.github.omegadestroyer.pairfree.shell.DesktopStrings
import io.github.omegadestroyer.pairfree.shell.SystemTones
import kotlin.math.max
import kotlin.math.min

/** What the system says about its colours: its accent colour (opaque ARGB) and whether it is in dark mode. */
data class SystemColors(val accent: Int, val night: Boolean)

enum class Tab(val titleKey: String) { HOME("tab_home"), SOUND("tab_sound"), CONTROLS("tab_controls"), DEVICE("tab_device") }

/**
 * The desktop app's window content, the same on every system: the sidebar (the earbuds, the tabs, the look and the version) and the
 * current tab at its right. The system's window code feeds it the pointer and asks it to draw ([render]); [invalidate] is how it asks
 * for another frame. Everything it shows comes from the shared connection, strings, looks and settings.
 */
class DesktopApp(
    private val connection: EarbudsConnection,
    private val device: Paired,
    val settings: DesktopSettings,
    private val system: () -> SystemColors,
    private val invalidate: () -> Unit,
    /** Runs a call on the connection where it lives: its own thread, since the connection is not thread safe. */
    private val onConnection: (() -> Unit) -> Unit,
) {
    private fun act(block: EarbudsConnection.() -> Unit) = onConnection { connection.block() }

    private var tab = Tab.HOME
    private var scroll = 0f
    private var contentHeight = 0f
    private var viewHeight = 0f
    private var hover: String? = null
    private var pressed: String? = null
    private var hits: List<Clickable> = emptyList()

    /** Strings and the look follow the settings; changing either asks for a new frame. */
    private var strings = DesktopStrings(settings.language)

    private fun theme(): Theme {
        val sys = system()
        return Theme(DesktopLook.byId(settings.look), sys.night, SystemTones(sys.accent))
    }

    fun render(canvas: Canvas, width: Float, height: Float) {
        val theme = theme()
        val ctx = Ctx(canvas, theme, hover, pressed)
        canvas.fillRoundRect(0f, 0f, width, height, 0f, theme.background)
        val sideWidth = min(216f, width * 0.3f)
        val all = mutableListOf<Widget>()

        val side = sidebar(theme)
        side.place(ctx, 16f, 16f, sideWidth - 32f)
        canvas.fillRoundRect(0f, 0f, sideWidth, height, 0f, theme.panel)
        canvas.line(sideWidth - 0.5f, 0f, sideWidth - 0.5f, height, theme.outline, 1f)
        side.draw(ctx)
        side.collect(all)

        val contentX = sideWidth + 24f
        val contentW = max(200f, width - contentX - 24f)
        viewHeight = height
        val content = content(theme, contentW >= 640f)
        content.place(ctx, contentX, 24f - scroll, contentW)
        contentHeight = content.h + 48f
        scroll = scroll.coerceIn(0f, max(0f, contentHeight - viewHeight))
        canvas.pushClip(sideWidth, 0f, width - sideWidth, height)
        content.draw(ctx)
        canvas.popClip()
        // Only what shows takes the pointer.
        content.collect(all)
        hits = all.filterIsInstance<Clickable>().filter { it.enabled }
        if (contentHeight > viewHeight) scrollbar(canvas, theme, width, height)
    }

    private fun scrollbar(canvas: Canvas, theme: Theme, width: Float, height: Float) {
        val track = height - 8f
        val thumb = max(30f, track * viewHeight / contentHeight)
        val top = 4f + (track - thumb) * (scroll / (contentHeight - viewHeight))
        canvas.fillRoundRect(width - 8f, top, 4f, thumb, 2f, theme.withAlpha(theme.label, 0.5f))
    }

    private fun sidebar(theme: Theme): Widget {
        val state = connection.state.value
        val status = connection.status.value
        val s = strings
        val live = status is LinkStatus.Connected
        val line = when (status) {
            is LinkStatus.Connected -> s.text("connected")
            is LinkStatus.Reconnecting -> s.text("reconnecting")
            is LinkStatus.Failed -> s.text("failed_lost", device.name)
            else -> s.text("connecting")
        }
        val batteries = batteryLine(state)
        val deviceCard = Panel(
            Column(
                listOfNotNull(
                    Label(device.name, theme.heading),
                    Label(line, theme.labelFont, if (live) theme.label else theme.accent, caps = true),
                    batteries?.let { Label(it, theme.small, theme.secondaryText) },
                ),
                4f,
            ),
            pad = 12f,
        )
        val tabs = Tab.entries.map { t -> Button(s.text(t.titleKey), "tab_${t.name}", primary = t == tab) { tab = t; scroll = 0f; invalidate() } as Widget }
        val look = DesktopLook.byId(settings.look)
        val lookButton = Button(s.text(look.nameKey), "look", false) { nextLook() }
        return Column(
            listOf(
                Label("PairFree", theme.title),
                Spacer(8f),
                deviceCard,
                Spacer(8f),
                Column(tabs, 6f),
                Spacer(16f),
                Label(s.text("app_look_title"), theme.labelFont, theme.label, caps = true),
                lookButton,
                Label("v$APP_VERSION", theme.labelFont, theme.label),
            ),
            6f,
        )
    }

    private fun batteryLine(state: EarbudState): String? {
        fun part(prefix: String, v: Int?) = v?.let { "$prefix ${strings.text("percent", it)}" }
        return listOfNotNull(part("L", state.batteryLeft), part("R", state.batteryRight), part("C", state.batteryCase)).takeIf { it.isNotEmpty() }?.joinToString("  ")
    }

    private fun nextLook() {
        val i = BUILT_IN_LOOKS.indexOfFirst { it.id == settings.look }
        settings.look = BUILT_IN_LOOKS[(i + 1) % BUILT_IN_LOOKS.size].id
        invalidate()
    }

    private fun content(theme: Theme, wide: Boolean): Widget {
        val status = connection.status.value
        val state = connection.state.value
        val s = strings
        if (status is LinkStatus.Failed) {
            val reason = when (status.reason) {
                LinkStatus.Reason.BLUETOOTH_OFF -> s.text("failed_bluetooth_off")
                LinkStatus.Reason.LOST -> s.text("failed_lost", device.name)
                else -> s.text("failed_could_not_open", device.name, Brands[device.brand].officialApp)
            }
            return Panel(Column(listOf(Label(reason, theme.body), Button(s.text("try_again"), "retry", true) { act { connect(device) } }), 12f))
        }
        if (status !is LinkStatus.Connected && status !is LinkStatus.Reconnecting) return Label(s.text("connecting"), theme.heading)
        return when (tab) {
            Tab.HOME -> HomeScreen(::act, device, state, status is LinkStatus.Connected, s, theme).build(wide)
            else -> Label(s.text(tab.titleKey), theme.title)
        }
    }

    // ---- the pointer ----

    private fun hitAt(x: Float, y: Float): String? = hits.lastOrNull { it.contains(x, y) }?.key

    fun pointerMoved(x: Float, y: Float) {
        val h = hitAt(x, y)
        if (h != hover) {
            hover = h
            invalidate()
        }
    }

    fun pointerLeft() {
        if (hover != null || pressed != null) {
            hover = null
            pressed = null
            invalidate()
        }
    }

    fun pointerDown(x: Float, y: Float) {
        pressed = hitAt(x, y)
        invalidate()
    }

    fun pointerUp(x: Float, y: Float) {
        val k = pressed
        pressed = null
        if (k != null && hitAt(x, y) == k) hits.lastOrNull { it.key == k }?.onClick?.invoke()
        invalidate()
    }

    /** The wheel: [lines] positive scrolls the content up (the way down the page). */
    fun wheel(lines: Float) {
        val old = scroll
        scroll = (scroll + lines * 48f).coerceIn(0f, max(0f, contentHeight - viewHeight))
        if (scroll != old) invalidate()
    }

    /** The connection or its state changed. */
    fun changed() = invalidate()
}
