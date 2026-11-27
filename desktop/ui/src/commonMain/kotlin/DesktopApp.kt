package io.github.omegadestroyer.pairfree.ui

import io.github.omegadestroyer.pairfree.bluetooth.EarbudsConnection
import io.github.omegadestroyer.pairfree.bluetooth.LinkStatus
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.brands.Brands
import io.github.omegadestroyer.pairfree.eq.EqActions
import io.github.omegadestroyer.pairfree.eq.EqProfile
import io.github.omegadestroyer.pairfree.eq.EqOpener
import io.github.omegadestroyer.pairfree.eq.Equalizer
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.brands.ReportInfo
import io.github.omegadestroyer.pairfree.brands.SoundcoreDriver
import io.github.omegadestroyer.pairfree.brands.SoundpeatsDriver
import io.github.omegadestroyer.pairfree.brands.modelOf
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreState
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreTouch
import io.github.omegadestroyer.pairfree.shell.Side
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.protocol.SoundpeatsBandEq
import io.github.omegadestroyer.pairfree.protocol.hearing.HearingTable
import io.github.omegadestroyer.pairfree.protocol.hearing.HearingTables
import io.github.omegadestroyer.pairfree.eq.EqState
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.shell.SettingWords
import io.github.omegadestroyer.pairfree.shell.APP_VERSION
import io.github.omegadestroyer.pairfree.shell.ReleaseInfo
import io.github.omegadestroyer.pairfree.shell.Updates
import io.github.omegadestroyer.pairfree.shell.BUILT_IN_LOOKS
import io.github.omegadestroyer.pairfree.shell.CloseAction
import io.github.omegadestroyer.pairfree.shell.DesktopLook
import io.github.omegadestroyer.pairfree.shell.DesktopSettings
import io.github.omegadestroyer.pairfree.shell.DesktopStrings
import io.github.omegadestroyer.pairfree.shell.SystemTones
import kotlin.math.max
import kotlin.math.min

/** What the system says about its colours: its accent colour (opaque ARGB) and whether it is in dark mode. */
data class SystemColors(val accent: Int, val night: Boolean)

enum class Tab(val titleKey: String) { HOME("tab_home"), SOUND("tab_sound"), CONTROLS("tab_controls"), DEVICE("tab_device"), SETTINGS("desktop_settings") }

/** What only the system can do for the app. */
interface Host {
    fun openBluetoothSettings()

    /** Brings the window back from the tray or the taskbar. */
    fun showWindow()

    /** Ends the program. */
    fun exit()

    /** Where the settings file is, for the settings screen to say. */
    val settingsPath: String

    /** Lets the user pick a text file and returns its text, or null when none was picked or it cannot be read. */
    fun openTextFile(): String?

    /** Lets the user choose where to save [text], suggesting [name]; false when none was chosen or it could not be written. */
    fun saveTextFile(name: String, text: String): Boolean

    /** Opens a web page in the user's browser. */
    fun openUrl(url: String)

    /** Puts [text] on the clipboard. */
    fun copyText(text: String)

    /** Plays the tests' tones. */
    val tones: TonePlayer

    /** Runs [work] off the window's thread; it hands its result back through [runOnUi]. */
    fun background(work: () -> Unit)

    /** Runs [work] on the window's thread. */
    fun runOnUi(work: () -> Unit)

    /** True when this copy has a key to check a downloaded update with, so it may install one. */
    val canInstallUpdates: Boolean

    /** Asks GitHub for the latest release ([Updates.LATEST_URL]); null when it could not be read. Blocks: call from [background]. */
    fun fetchLatestRelease(): ReleaseInfo?

    /** Downloads, checks and installs [release] and restarts the program; returns why not (text for the screen), or null when it is restarting. Blocks. */
    fun installUpdate(release: ReleaseInfo): String?
}

/** Where the update check is. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data object Installing : UpdateState
    data class Failed(val why: String) : UpdateState
}

/** Keys the app acts on besides characters. */
enum class Key { BACKSPACE, DELETE, LEFT, RIGHT, HOME, END, ENTER, ESCAPE }

/** An entry of the tray icon's menu, which the system draws. */
sealed interface TrayItem {
    class Action(val text: String, val checked: Boolean = false, val enabled: Boolean = true, val run: () -> Unit) : TrayItem

    /** An entry that opens another menu beside it. */
    class Submenu(val text: String, val items: List<Action>) : TrayItem

    /** A line that only shows something (the earbuds' battery). */
    class Info(val text: String) : TrayItem

    data object Separator : TrayItem
}

/**
 * The desktop app's window content, the same on every system: the sidebar (the earbuds, the tabs, the look and the version) and the
 * current tab at its right. The system's window code feeds it the pointer and asks it to draw ([render]); [invalidate] is how it asks
 * for another frame. Everything it shows comes from the shared connection, strings, looks and settings.
 */
class DesktopApp(
    private val connection: EarbudsConnection,
    private val equalizer: Equalizer,
    val settings: DesktopSettings,
    private val host: Host,
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

    @kotlin.concurrent.Volatile
    private var devices: List<Paired> = emptyList()

    @kotlin.concurrent.Volatile
    private var device: Paired? = null

    @kotlin.concurrent.Volatile
    private var scanned = false

    /** The earbuds shown, for the code that opens their equalizer. */
    val currentDevice: Paired? get() = device

    private var eqBand = 0

    @kotlin.concurrent.Volatile
    private var update: UpdateState = UpdateState.Idle

    /** Looks for a newer release on GitHub, in the background. Only when the user allowed it or pressed Check now. */
    fun checkForUpdate() {
        if (update == UpdateState.Checking || update == UpdateState.Installing) return
        update = UpdateState.Checking
        invalidate()
        host.background {
            val release = host.fetchLatestRelease()
            host.runOnUi {
                update = when {
                    release == null -> UpdateState.Failed("GitHub")
                    Updates.isNewer(release.version, APP_VERSION) -> UpdateState.Available(release)
                    else -> UpdateState.UpToDate
                }
                invalidate()
            }
        }
    }

    private fun installUpdate(release: ReleaseInfo) {
        update = UpdateState.Installing
        invalidate()
        host.background {
            val why = host.installUpdate(release)
            host.runOnUi {
                if (why != null) update = UpdateState.Failed(why)
                invalidate()
            }
        }
    }

    /** Called once when the window is up: asks the question on the first run, else checks if the user said so. */
    fun started() {
        when (settings.checkForUpdates) {
            null -> {
                dialog = Dialog(
                    strings.text("desktop_update_question_title"), strings.text("desktop_update_question"), strings.text("desktop_update_yes"), false, "",
                    cancel = strings.text("desktop_update_no"), onCancel = { settings.checkForUpdates = false },
                ) {
                    settings.checkForUpdates = true
                    checkForUpdate()
                }
                invalidate()
            }
            true -> checkForUpdate()
            false -> Unit
        }
    }
    private var page: Page? = null

    /** True while a screen shows something that moves with time. */
    val animating: Boolean get() = page?.animating == true

    private fun openPage(p: Page) {
        page?.stop()
        page = p
        scroll = 0f
        invalidate()
    }

    private fun closePage() {
        page = null
        invalidate()
    }

    /** The hearing test PairFree runs for [d], where its brand's app has one that becomes an EQ (SOUNDPEATS'), or any custom curve of three bands or more. */
    private fun hearingTable(d: Paired, eq: EqState?): HearingTable? = when {
        eq == null -> null
        d.brand == io.github.omegadestroyer.pairfree.protocol.Maker.SOUNDPEATS && (eq.target as? BandEqTarget)?.source is SoundpeatsBandEq -> HearingTables.SOUNDPEATS
        eq.target.hasCustom && eq.layout.bands.size >= 3 -> HearingTables.SOUNDPEATS
        else -> null
    }
    private var controlsSide = Side.LEFT
    private var reportCopied = false
    private val rowState = RowState()
    private val rowCallbacks = RowCallbacks(
        change = { change -> act { changeSetting(change) } },
        confirm = { title, body, confirm, onConfirm ->
            dialog = Dialog(title, body, confirm, false, "") { onConfirm() }
            invalidate()
        },
        askText = { title, hint, max, confirm, onDone ->
            dialog = Dialog(title, null, confirm, true, "", hint, max) { onDone(it) }
            invalidate()
        },
        openUrl = { host.openUrl(it) },
        changed = { invalidate() },
    )

    /** The brand's own settings for [tab], as panels. */
    private fun rows(tab: SettingTab, theme: Theme): List<Widget> =
        SettingRows(connection.settings.value, tab, SettingWords(strings), strings, theme, rowState, rowCallbacks).panels()
    private var eqMessage: String? = null
    private var dialog: Dialog? = null

    /** A question over the window: a name to type, or a confirmation. */
    private class Dialog(val title: String, val body: String?, val confirm: String, val field: Boolean, var text: String, val hint: String = "", val max: Int = 24, val cancel: String? = null, val onCancel: () -> Unit = {}, val onConfirm: (String) -> Unit) {
        var caret = text.length
    }

    /** The equalizer's actions, run where the connection lives. */
    private val eqActions = object : EqActions {
        override fun select(profile: EqProfile) = onConnection { equalizer.select(profile) }
        override fun startNew() = onConnection { equalizer.startNew() }
        override fun setGain(band: Int, db: Double) = onConnection { equalizer.setGain(band, db) }
        override fun setPreamp(db: Int) = onConnection { equalizer.setPreamp(db) }
        override fun save(name: String) = onConnection { equalizer.save(name) }
        override fun applyCurve(name: String, gains: List<Int>) = onConnection { equalizer.applyCurve(name, gains) }
        override fun preview(gains: List<Int>) = onConnection { equalizer.preview(gains) }
        override fun previewDb(gains: List<Double>) = onConnection { equalizer.previewDb(gains) }
        override fun delete(profile: EqProfile) = onConnection { equalizer.delete(profile) }
        override fun exportJson(): String = equalizer.exportJson()
        override fun importJson(text: String): Boolean = false
    }

    /** Strings follow the settings. */
    private var strings = DesktopStrings(settings.language)

    private fun theme(): Theme {
        val sys = system()
        return Theme(DesktopLook.byId(settings.look), sys.night, SystemTones(sys.accent))
    }

    /**
     * The earbuds Windows has paired, as the system last listed them. The earbuds shown stay while they are in the list; with none shown,
     * the ones used last are opened, else the first. Called from any thread.
     */
    fun setDevices(list: List<Paired>) {
        devices = list
        scanned = true
        val current = device
        if (current == null || list.none { it.address == current.address }) {
            val next = list.firstOrNull { it.address.equals(settings.lastDevice, ignoreCase = true) } ?: list.firstOrNull()
            device = next
            if (next != null) act { connect(next) } else act { disconnect() }
        }
        invalidate()
    }

    private fun select(d: Paired) {
        if (d.address == device?.address) return
        page?.stop()
        page = null
        device = d
        settings.lastDevice = d.address
        tab = Tab.HOME
        scroll = 0f
        act { connect(d) }
        invalidate()
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
        content.collect(all)
        hits = all.filterIsInstance<Clickable>().filter { it.enabled }
        if (contentHeight > viewHeight) scrollbar(canvas, theme, width, height)
        dialog?.let { hits = drawDialog(ctx, it, width, height) }
    }

    /** The open dialog over a dimmed window; returns what can be pressed while it is open. */
    private fun drawDialog(ctx: Ctx, d: Dialog, width: Float, height: Float): List<Clickable> {
        val t = ctx.theme
        val s = strings
        ctx.canvas.fillRoundRect(0f, 0f, width, height, 0f, t.withAlpha(t.background, 0.75f))
        val parts = mutableListOf<Widget>(Label(d.title, t.heading))
        d.body?.let { parts += Label(it, t.body, t.secondaryText) }
        if (d.field) parts += TextField(d.text, d.caret, d.hint, true, "dialog_field") {}
        parts += Row(listOf(Button(d.cancel ?: s.text("cancel"), "dialog_cancel") { d.onCancel(); closeDialog() }, Button(d.confirm, "dialog_ok", primary = true) { confirmDialog() }), 8f)
        val box = Panel(Column(parts, 14f), 20f)
        val w = min(420f, width - 48f)
        box.place(ctx, (width - w) / 2f, 0f, w)
        box.place(ctx, (width - w) / 2f, max(24f, (height - box.h) / 2f), w)
        box.draw(ctx)
        val all = mutableListOf<Widget>()
        box.collect(all)
        return all.filterIsInstance<Clickable>()
    }

    private fun closeDialog() {
        dialog = null
        invalidate()
    }

    private fun confirmDialog() {
        val d = dialog ?: return
        dialog = null
        d.onConfirm(d.text)
        invalidate()
    }

    // ---- the keyboard ----

    /** A typed character; only a dialog's text box takes them. */
    fun keyChar(c: Char) {
        val d = dialog ?: return
        if (!d.field || c.code < 32 || c.code == 127 || d.text.length >= d.max) return
        d.text = d.text.substring(0, d.caret) + c + d.text.substring(d.caret)
        d.caret++
        invalidate()
    }

    fun keyDown(key: Key) {
        val d = dialog ?: return
        when (key) {
            Key.ESCAPE -> { d.onCancel(); closeDialog(); return }
            Key.ENTER -> { confirmDialog(); return }
            Key.BACKSPACE -> if (d.caret > 0) { d.text = d.text.removeRange(d.caret - 1, d.caret); d.caret-- }
            Key.DELETE -> if (d.caret < d.text.length) d.text = d.text.removeRange(d.caret, d.caret + 1)
            Key.LEFT -> d.caret = max(0, d.caret - 1)
            Key.RIGHT -> d.caret = min(d.text.length, d.caret + 1)
            Key.HOME -> d.caret = 0
            Key.END -> d.caret = d.text.length
        }
        invalidate()
    }

    private fun scrollbar(canvas: Canvas, theme: Theme, width: Float, height: Float) {
        val track = height - 8f
        val thumb = max(30f, track * viewHeight / contentHeight)
        val top = 4f + (track - thumb) * (scroll / (contentHeight - viewHeight))
        canvas.fillRoundRect(width - 8f, top, 4f, thumb, 2f, theme.withAlpha(theme.label, 0.5f))
    }

    private fun linkLine(status: LinkStatus): String = when (status) {
        is LinkStatus.Connected -> strings.text("connected")
        is LinkStatus.Reconnecting -> strings.text("reconnecting")
        is LinkStatus.Idle -> strings.text("disconnected")
        is LinkStatus.Failed -> strings.text("failed_lost", status.device.name)
        else -> strings.text("connecting")
    }

    private fun sidebar(theme: Theme): Widget {
        val s = strings
        val state = connection.state.value
        val status = connection.status.value
        val current = device
        val cards = devices.map { d ->
            val selected = d.address == current?.address
            val lines = mutableListOf<Widget>(Label(d.name, theme.heading))
            if (selected) {
                lines += Label(linkLine(status), theme.labelFont, if (status is LinkStatus.Connected) theme.label else theme.accent, caps = true)
                batteryLine(state)?.let { lines += Label(it, theme.small, theme.secondaryText) }
            }
            Card(Column(lines, 4f), "device_${d.address}", selected) { select(d) } as Widget
        }
        val tabs = Tab.entries.map { t ->
            Button(s.text(t.titleKey), "tab_${t.name}", primary = t == tab, enabled = current != null || t == Tab.SETTINGS) { tab = t; scroll = 0f; invalidate() } as Widget
        }
        return Column(
            listOf(Label("PairFree", theme.title), Spacer(8f), Column(cards, 8f), Spacer(8f), Column(tabs, 6f), Spacer(8f), Label("v$APP_VERSION", theme.labelFont, theme.label)) +
                (if (update is UpdateState.Available) listOf<Widget>(Button(s.text("desktop_update_sidebar"), "sidebar_update", primary = true) { tab = Tab.SETTINGS; scroll = 0f; invalidate() }) else emptyList()),
            6f,
        )
    }

    private fun batteryLine(state: EarbudState): String? {
        fun part(prefix: String, v: Int?) = v?.let { "$prefix ${strings.text("percent", it)}" }
        return listOfNotNull(part("L", state.batteryLeft), part("R", state.batteryRight), part("C", state.batteryCase)).takeIf { it.isNotEmpty() }?.joinToString("  ")
    }

    private fun content(theme: Theme, wide: Boolean): Widget {
        val s = strings
        if (tab == Tab.SETTINGS) return settingsScreen(theme)
        val current = device
        if (current == null) {
            if (!scanned) return Label(s.text("connecting"), theme.heading)
            return Panel(
                Column(listOf(Label(s.text("no_paired"), theme.body), Button(s.text("bluetooth_settings"), "bt_settings", true) { host.openBluetoothSettings() }), 12f),
            )
        }
        val status = connection.status.value
        val state = connection.state.value
        if (status is LinkStatus.Failed) {
            val reason = when (status.reason) {
                LinkStatus.Reason.BLUETOOTH_OFF -> s.text("failed_bluetooth_off")
                LinkStatus.Reason.LOST -> s.text("failed_lost", current.name)
                else -> s.text("failed_could_not_open", current.name, Brands[current.brand].officialApp)
            }
            return Panel(Column(listOf(Label(reason, theme.body), Button(s.text("try_again"), "retry", true) { act { connect(current) } }), 12f))
        }
        if (status is LinkStatus.Idle) {
            return Panel(Column(listOf(Label(s.text("disconnected"), theme.body), Button(s.text("connect"), "connect_again", true) { act { connect(current) } }), 12f))
        }
        if (status !is LinkStatus.Connected && status !is LinkStatus.Reconnecting) return Label(s.text("connecting"), theme.heading)
        page?.let { return it.build(state) }
        return when (tab) {
            Tab.HOME -> HomeScreen(::act, current, state, status is LinkStatus.Connected, s, theme).build(wide, rows(SettingTab.HOME, theme))
            Tab.SOUND -> Column(listOf(SoundScreen(equalizer.state.value, eqActions, eqBand, eqMessage, s, theme, soundCallbacks, hearingOpener(current)).build()) + rows(SettingTab.SOUND, theme), 16f)
            Tab.CONTROLS -> ControlsTab(
                current, state, (connection.extra.value as? SoundcoreState)?.let(SoundcoreTouch::of), rows(SettingTab.CONTROLS, theme), controlsSide, rowState, s, theme,
                ControlsCallbacks(
                    set = { command, payload -> act { set(command, *payload) } },
                    changeTouch = { make -> act { (driver as? SoundcoreDriver)?.changeTouch(make) } },
                    resetTouch = { act { (driver as? SoundcoreDriver)?.resetTouch() } },
                    confirm = rowCallbacks.confirm,
                    selectSide = { controlsSide = it; invalidate() },
                    changed = { invalidate() },
                ),
            ).build()
            Tab.DEVICE -> DeviceTab(
                current, state, modelOf(current, state), rows(SettingTab.DEVICE, theme), reportCopied, rowState, s, theme,
                DeviceCallbacks(
                    set = { command, payload -> act { set(command, *payload) } },
                    confirm = rowCallbacks.confirm,
                    copyReport = { host.copyText(deviceReport()); reportCopied = true; invalidate() },
                    factoryReset = { act { factoryResetConfirmedByUser() } },
                    openFit = { openPage(FitTestPage(current, strings, theme(), host.tones, { keep -> act { startFitTest(keep) } }, { invalidate() }, { closePage() })) },
                    openSeal = { openPage(SealCheckPage(strings, theme(), host.tones, { invalidate() }, { closePage() })) },
                    changed = { invalidate() },
                ),
            ).build()
            else -> Label(s.text(tab.titleKey), theme.title)
        }
    }

    private fun hearingOpener(d: Paired): (() -> Unit)? {
        val eq = equalizer.state.value ?: return null
        val table = hearingTable(d, eq) ?: return null
        return {
            openPage(
                HearingTestPage(
                    eq, table, (eq.target as? BandEqTarget)?.source is SoundpeatsBandEq, strings, theme(), host.tones,
                    apply = { name, gains -> eqActions.applyCurve(name, gains) }, invalidate = { invalidate() }, onClose = { closePage() },
                ),
            )
        }
    }

    private val soundCallbacks = SoundCallbacks(
        selectBand = { eqBand = it; invalidate() },
        askSave = { suggested ->
            dialog = Dialog(strings.text("eq_save"), null, strings.text("save"), true, suggested, strings.text("eq_name_hint")) { name -> if (name.isNotBlank()) eqActions.save(name.trim().take(24)) }
            invalidate()
        },
        askDelete = { p ->
            dialog = Dialog(strings.text("eq_delete_title", p.name), strings.text("eq_delete_body"), strings.text("delete"), false, "") { eqActions.delete(p) }
            invalidate()
        },
        importFile = {
            val text = host.openTextFile()
            if (text != null) onConnection {
                eqMessage = strings.text(if (equalizer.importJson(text)) "eq_imported" else "eq_import_wrong")
                invalidate()
            }
        },
        exportFile = { name ->
            eqMessage = strings.text(if (host.saveTextFile(name, equalizer.exportJson())) "eq_exported" else "eq_export_failed")
            invalidate()
        },
    )

    private fun settingsScreen(theme: Theme): Widget {
        val s = strings
        fun label(text: String) = Label(text, theme.labelFont, theme.label, caps = true)
        val tags = DesktopStrings.languages
        val languageNames = tags.map { s.text("language_" + it.replace('-', '_')) }
        val currentTag = settings.language?.let { DesktopStrings.resolve(it) }
        val looks = BUILT_IN_LOOKS
        val closeChoices = listOf(CloseAction.TRAY to "desktop_close_tray", CloseAction.EXIT to "desktop_close_exit")
        fun toggle(key: String, titleKey: String, on: Boolean, set: (Boolean) -> Unit) =
            Button(s.text("desktop_on_off", s.text(titleKey), s.text(if (on) "on" else "off")), key, primary = on) { set(!on); invalidate() }
        return Column(
            listOf(
                Label(s.text("desktop_settings"), theme.title),
                Panel(
                    Column(
                        listOf(
                            label(s.text("app_language_title")),
                            segmented("lang", listOf(s.text("app_language_system")) + languageNames, if (currentTag == null) 0 else tags.indexOf(currentTag) + 1, primary = false, perRow = 3) {
                                settings.language = if (it == 0) null else tags[it - 1]
                                strings = DesktopStrings(settings.language)
                                invalidate()
                            },
                            label(s.text("app_look_title")),
                            segmented("look", looks.map { s.text(it.nameKey) }, looks.indexOfFirst { it.id == settings.look }.takeIf { it >= 0 }, primary = false, perRow = 2) {
                                settings.look = looks[it].id
                                invalidate()
                            },
                        ),
                        10f,
                    ),
                ),
                Panel(
                    Column(
                        listOf(
                            label(s.text("desktop_close_title")),
                            segmented("close", closeChoices.map { s.text(it.second) }, closeChoices.indexOfFirst { it.first == settings.closeAction }.takeIf { it >= 0 }, primary = false, perRow = 2) {
                                settings.closeAction = closeChoices[it].first
                                invalidate()
                            },
                            label(s.text("desktop_minimize_title")),
                            segmented("minimize", listOf(s.text("desktop_minimize_tray"), s.text("desktop_minimize_taskbar")), if (settings.minimizeToTray) 0 else 1, primary = false, perRow = 2) {
                                settings.minimizeToTray = it == 0
                                invalidate()
                            },
                            toggle("start_min", "desktop_start_minimized", settings.startMinimized) { settings.startMinimized = it },
                        ),
                        10f,
                    ),
                ),
                updatesPanel(theme),
                Label(s.text("desktop_settings_file", host.settingsPath), theme.small, theme.secondaryText),
            ),
            16f,
        )
    }

    /** The report to paste into an issue: what the earbuds answered, as their brand's driver writes it. */
    private fun deviceReport(): String {
        val now = kotlin.time.Clock.System.now().toString()
        val date = now.take(16).replace('T', ' ') + " UTC"
        val d = (connection.status.value as? LinkStatus.Connected)?.device
        val info = ReportInfo(d, APP_VERSION, date, "https://github.com/Omega-Destroyer/PairFree/issues", connection.platform)
        return connection.driver?.report(info) ?: SoundpeatsDriver.plainReport(info, connection.lastReplies, connection.state.value)
    }

    private fun updatesPanel(theme: Theme): Widget {
        val s = strings
        val checking = update == UpdateState.Checking || update == UpdateState.Installing
        val rows = mutableListOf<Widget>(
            Label(s.text("desktop_update_title"), theme.labelFont, theme.label, caps = true),
            Button(s.text("desktop_on_off", s.text("desktop_update_toggle"), s.text(if (settings.checkForUpdates == true) "on" else "off")), "check_updates", primary = settings.checkForUpdates == true) {
                settings.checkForUpdates = settings.checkForUpdates != true
                if (settings.checkForUpdates == true) checkForUpdate() else invalidate()
            },
            Button(s.text("desktop_update_check_now"), "check_now", enabled = !checking) { checkForUpdate() },
        )
        when (val u = update) {
            UpdateState.Idle -> Unit
            UpdateState.Checking -> rows += Label(s.text("desktop_update_checking"), theme.small, theme.secondaryText)
            UpdateState.UpToDate -> rows += Label(s.text("desktop_update_latest"), theme.small, theme.secondaryText)
            UpdateState.Installing -> rows += Label(s.text("desktop_update_installing"), theme.small, theme.secondaryText)
            is UpdateState.Failed -> rows += Label(s.text("desktop_update_failed", u.why), theme.small, theme.destructive)
            is UpdateState.Available -> {
                rows += Label(s.text("desktop_update_available", u.release.version), theme.body, theme.accent)
                if (host.canInstallUpdates && u.release.programUrl != null && u.release.signatureUrl != null) {
                    rows += Button(s.text("desktop_update_install"), "install_update", primary = true) { installUpdate(u.release) }
                } else if (!host.canInstallUpdates) {
                    rows += Label(s.text("desktop_update_no_key"), theme.small, theme.secondaryText)
                }
                rows += Button(s.text("desktop_update_open_page"), "update_page") { host.openUrl(u.release.pageUrl) }
            }
        }
        return Panel(Column(rows, 10f))
    }

    // ---- the tray ----

    /** The tray icon's tooltip: the earbuds and their batteries. */
    fun trayTip(): String {
        val d = device ?: return "PairFree"
        return listOfNotNull(d.name, batteryLine(connection.state.value)).joinToString(" · ")
    }

    /** The tray icon's menu, built from what the earbuds report now. */
    fun trayItems(): List<TrayItem> {
        val s = strings
        val items = mutableListOf<TrayItem>()
        val d = device
        val state = connection.state.value
        if (d != null) {
            items += TrayItem.Info(d.name)
            batteryLine(state)?.let { items += TrayItem.Info(it) }
            if (state.supports(Command.ANC_MODE)) {
                items += TrayItem.Separator
                items += TrayItem.Info(s.text("desktop_tray_noise"))
                val current = state.number(Command.ANC_MODE)
                for ((name, key) in listOf("off" to "anc_off", "anc" to "anc_cancelling", "transparency" to "anc_transparency")) {
                    val value = Command.ANC_MODE.labels.entries.first { it.value == name }.key
                    if (state.noiseModeValues?.contains(value) == false) continue
                    items += TrayItem.Action(s.text(key), checked = current == value) { act { set(Command.ANC_MODE, value) } }
                }
            }
            equalizer.state.value?.let { eq ->
                if (eq.profiles.isNotEmpty()) {
                    items += TrayItem.Separator
                    items += TrayItem.Submenu(s.text("desktop_tray_sound"), eq.profiles.map { p -> TrayItem.Action(profileLabel(p), checked = eq.selected == p) { eqActions.select(p) } })
                }
            }
            items += TrayItem.Separator
            if (connection.status.value is LinkStatus.Connected) items += TrayItem.Action(s.text("desktop_tray_disconnect")) { act { disconnect() } }
            else items += TrayItem.Action(s.text("connect")) { act { connect(d) } }
            items += TrayItem.Separator
        }
        items += TrayItem.Action(s.text("desktop_tray_open")) { host.showWindow() }
        items += TrayItem.Action(s.text("desktop_tray_exit")) { host.exit() }
        return items
    }

    /** An equalizer profile's name in this language. */
    private fun profileLabel(p: EqProfile): String = when {
        p.id == EqProfile.STOCK -> strings.text("eq_stock")
        p.preset != null -> strings.presetLabel(p.name)
        else -> p.name
    }

    /** The question asked the first time the window is closed (settings.closeAction is null until then). */
    val closeQuestion: String get() = strings.text("desktop_close_question")

    // ---- the pointer ----

    private fun hitAt(x: Float, y: Float): String? = hits.lastOrNull { it.contains(x, y) }?.key

    fun pointerMoved(x: Float, y: Float) {
        pressed?.let { k ->
            hits.lastOrNull { it.key == k }?.drag(x, y)
            invalidate()
            return
        }
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
        val hit = hits.lastOrNull { it.contains(x, y) }
        pressed = hit?.key
        hit?.press(x, y)
        invalidate()
    }

    fun pointerUp(x: Float, y: Float) {
        val k = pressed
        pressed = null
        if (k != null && hitAt(x, y) == k) hits.lastOrNull { it.key == k }?.onClick?.invoke()
        // A level dragged with the pointer goes to the earbuds once it is let go.
        if (rowState.pending.isNotEmpty()) {
            val sent = rowState.pending.toMap()
            rowState.pending.clear()
            for ((key, value) in sent) rowState.senders[key]?.invoke(value)
        }
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
