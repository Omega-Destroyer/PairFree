package io.github.omegadestroyer.pairfree.ui

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.shell.DesktopLook
import io.github.omegadestroyer.pairfree.shell.DesktopStrings
import io.github.omegadestroyer.pairfree.shell.PathCommand
import io.github.omegadestroyer.pairfree.shell.SettingWords
import io.github.omegadestroyer.pairfree.shell.SystemTones
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A canvas that only records the text it was asked to draw, with fixed-width letters. */
private class RecordingCanvas : Canvas {
    val texts = mutableListOf<String>()

    override fun fillRoundRect(x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int) = Unit
    override fun strokeRoundRect(x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int, width: Float) = Unit
    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) = Unit
    override fun fillCircle(cx: Float, cy: Float, r: Float, color: Int) = Unit
    override fun text(text: String, x: Float, y: Float, font: Font, color: Int) { texts += text }
    override fun textWidth(text: String, font: Font) = text.length * font.size * 0.5f
    override fun lineHeight(font: Font) = font.size * 1.4f
    override fun path(commands: List<PathCommand>, x: Float, y: Float, scale: Float, mirror: Boolean, boxWidth: Float, fill: Int?, stroke: Int?, strokeWidth: Float) = Unit
    override fun pushClip(x: Float, y: Float, w: Float, h: Float) = Unit
    override fun popClip() = Unit
}

class RowsTest {
    private val strings = DesktopStrings("en")
    private val theme = Theme(DesktopLook.byId("studio"), true, SystemTones(0xFF0078D4.toInt()))
    private val changes = mutableListOf<SettingChange>()
    private val call = RowCallbacks(
        change = { changes += it },
        confirm = { _, _, _, run -> run() },
        askText = { _, _, _, _, done -> done("code") },
        openUrl = {},
        changed = {},
    )

    private fun layout(groups: List<SettingGroup>, state: RowState = RowState()): Pair<List<Widget>, RecordingCanvas> {
        val panels = SettingRows(groups, SettingTab.DEVICE, SettingWords(strings), strings, theme, state, call).panels()
        val canvas = RecordingCanvas()
        val ctx = Ctx(canvas, theme)
        var y = 0f
        for (p in panels) {
            p.place(ctx, 0f, y, 600f)
            p.draw(ctx)
            y += p.h + 16f
        }
        return panels to canvas
    }

    @Test
    fun everyKindOfRowLaysOutAndDrawsItsWords() {
        val groups = listOf(
            SettingGroup(
                "noise", SettingTab.DEVICE,
                listOf(
                    Setting.Toggle("find_hub", true),
                    Setting.Choice("sound_mode", "sleep_mode", listOf("sleep_mode", "normal_mode")),
                    Setting.Level("balance", 3, 0, 6),
                    Setting.Level("sensitivity", 2, 1, 3),
                    Setting.Action("restart"),
                    Setting.Information("some_reading", "42"),
                    Setting.Text("sh_auracast_code", "abc", 16),
                ),
            ),
        )
        val (panels, canvas) = layout(groups)
        assertEquals(1, panels.size)
        assertTrue(panels[0].h > 150f, "a panel with seven rows is tall: ${panels[0].h}")
        assertTrue(canvas.texts.any { it.equals("42") }, "the reading shows: ${canvas.texts}")
        assertTrue(canvas.texts.any { it.contains("abc") })
    }

    @Test
    fun aTabWithNoGroupsHasNoPanels() {
        assertTrue(SettingRows(emptyList(), SettingTab.DEVICE, SettingWords(strings), strings, theme, RowState(), call).panels().isEmpty())
    }

    @Test
    fun anOpenChoiceShowsItsOptionsAndPickingOneSendsIt() {
        val state = RowState()
        val groups = listOf(SettingGroup("noise", SettingTab.DEVICE, listOf(Setting.Choice("sound_mode", "sleep_mode", listOf("sleep_mode", "normal_mode")))))
        state.expanded = "sound_mode"
        val (panels, _) = layout(groups, state)
        val all = mutableListOf<Widget>()
        panels.forEach { it.collect(all) }
        val options = all.filterIsInstance<Clickable>().filter { it.key.startsWith("opt_sound_mode_") }
        assertEquals(2, options.size)
        options.last().onClick()
        assertEquals(listOf<SettingChange>(SettingChange.Choice("sound_mode", "normal_mode")), changes)
    }

    @Test
    fun aLevelWithManyStepsKeepsTheDraggedValueUntilItIsLetGo() {
        val state = RowState()
        val groups = listOf(SettingGroup("sound", SettingTab.DEVICE, listOf(Setting.Level("balance", 3, 0, 6))))
        val (panels, _) = layout(groups, state)
        val all = mutableListOf<Widget>()
        panels.forEach { it.collect(all) }
        val slider = all.filterIsInstance<Clickable>().first { it.key == "ls_balance" }
        slider.press(slider.x + slider.w, slider.y)
        assertEquals(6, state.pending["balance"])
        state.senders.getValue("balance")(6)
        assertEquals(listOf<SettingChange>(SettingChange.Level("balance", 6)), changes)
    }
}
