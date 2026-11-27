package io.github.omegadestroyer.pairfree.ui

import io.github.omegadestroyer.pairfree.bluetooth.EarbudsConnection
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.brands.modelOf
import io.github.omegadestroyer.pairfree.protocol.BatteryLevel
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.shell.ArtDrawing
import io.github.omegadestroyer.pairfree.shell.DesktopStrings
import io.github.omegadestroyer.pairfree.shell.deviceArt

/** The Home screen: what the earbuds report and the settings used most, as two columns when the window is wide enough (docs/DESKTOP.md). */
internal class HomeScreen(
    private val act: (EarbudsConnection.() -> Unit) -> Unit,
    private val device: Paired,
    private val state: EarbudState,
    private val live: Boolean,
    private val s: DesktopStrings,
    private val theme: Theme,
) {
    private val model = modelOf(device, state)
    private val form = model?.form ?: Form.EARBUDS
    private val art = deviceArt(state.firmware?.code, device.name, form)

    private fun label(text: String) = Label(text, theme.labelFont, theme.label, caps = true)

    private fun mode(name: String): Int = Command.ANC_MODE.labels.entries.first { it.value == name }.key

    fun build(wide: Boolean, extra: List<Widget> = emptyList()): Widget {
        val header = header()
        val battery = battery()
        val noise = if (state.supports(Command.ANC_MODE) && model?.code != "A3954") noiseControl() else null
        val switches = quickSwitches()
        val left = listOfNotNull(battery)
        val right = listOfNotNull(noise, switches) + extra
        val body: Widget = if (wide && left.isNotEmpty() && right.isNotEmpty()) {
            Row(listOf(Column(left, 16f), Column(right, 16f)), gap = 16f, weights = listOf(1f, 1f))
        } else {
            Column(left + right, 16f)
        }
        return Column(listOf(header, body) + listOfNotNull(if (battery == null && noise == null) Label(s.text("reading"), color = theme.secondaryText) else null), 16f)
    }

    private fun header(): Widget {
        val link = s.text(if (live) "connected" else "reconnecting")
        val firmware = state.firmware?.let { s.text("firmware", it.version) }
        return Column(
            listOf(
                Label(device.name, theme.title),
                Label(listOfNotNull(link, firmware).joinToString("  ·  "), theme.labelFont, if (live) theme.label else theme.accent, caps = true),
            ),
            4f,
        )
    }

    private class Battery(val label: String, val reading: BatteryLevel?, val drawing: String?, val mirrored: Boolean)

    private fun battery(): Widget? {
        val items = when (form) {
            Form.EARBUDS -> listOfNotNull(
                Battery(s.text("left"), state.battery(Command.BATTERY_LEFT), art.bud, false).takeIf { state.supports(Command.BATTERY_LEFT) },
                Battery(s.text("case_label"), state.battery(Command.BATTERY_CASE), art.case, false).takeIf { state.supports(Command.BATTERY_CASE) },
                Battery(s.text("right"), state.battery(Command.BATTERY_RIGHT), art.bud, true).takeIf { state.supports(Command.BATTERY_RIGHT) },
            )
            Form.HEADPHONES, Form.SPEAKER -> listOfNotNull(
                Battery(s.text(if (form == Form.SPEAKER) "speaker" else "headphones"), state.battery(Command.BATTERY_LEFT), art.bud, false).takeIf { state.supports(Command.BATTERY_LEFT) },
                Battery(s.text("case_label"), state.battery(Command.BATTERY_CASE), art.case, false).takeIf { state.supports(Command.BATTERY_CASE) },
            )
        }
        if (items.isEmpty()) return null
        return Panel(Column(listOf(label(s.text("battery")), Row(items.map(::batteryItem), 12f)), 12f))
    }

    private fun batteryItem(b: Battery): Widget {
        val reading = b.reading
        val level = reading?.level
        val number: Widget
        val bar: Widget
        if (reading == null || level == null || reading.isPercent) {
            val percent = reading?.percent
            number = Label(percent?.let { s.text("percent", it) } ?: s.text("unknown"), theme.bigNumber, align = Align.CENTER)
            bar = LevelBar(percent?.let { it / 100f }, warnWhenLow = true)
        } else {
            number = Spacer(0f)
            bar = StepBar(level, reading.levels)
        }
        return Column(
            listOf(ArtView(b.drawing?.let(ArtDrawing::of), 56f, b.mirrored), Label(b.label, theme.labelFont, theme.label, Align.CENTER, caps = true), number, bar),
            6f,
        )
    }

    private fun noiseControl(): Widget {
        val modes = listOf("off" to "anc_off", "anc" to "anc_cancelling", "transparency" to "anc_transparency")
            .filter { state.noiseModeValues?.contains(mode(it.first)) != false }
        val current = modes.indexOfFirst { mode(it.first) == state.number(Command.ANC_MODE) }.takeIf { it >= 0 }
        val levels = when (current?.let { modes[it].first }) {
            "anc" -> Command.ANC_LEVEL
            "transparency" -> Command.TRANSPARENCY_LEVEL
            else -> null
        }
        val choices = levels?.let(state::levels).orEmpty()
        val parts = mutableListOf<Widget>(
            label(s.text("noise_control")),
            segmented("anc", modes.map { s.text(it.second) }, current) { act { set(Command.ANC_MODE, mode(modes[it].first)) } },
        )
        if (levels != null && choices.isNotEmpty()) {
            parts += label(s.text(if (levels == Command.ANC_LEVEL) "scene" else "transparency_level"))
            parts += segmented(
                "level", choices.map { if (levels == Command.ANC_LEVEL) scene(it.code) else transparency(it.code) }, choices.indexOfFirst { it.active }.takeIf { it >= 0 },
                primary = false, perRow = 3,
            ) { act { set(levels, choices[it].code) } }
        }
        return Panel(Column(parts, 10f))
    }

    private fun code(code: Int) = s.text("code_unknown", "0x" + code.toString(16).uppercase().padStart(2, '0'))

    private fun scene(code: Int): String = when (val name = Command.ANC_LEVEL.labels[code]) {
        "normal", "adaptive" -> s.text("scene_adaptive")
        "indoor" -> s.text("scene_indoor")
        "outdoor" -> s.text("scene_outdoor")
        "traffic" -> s.text("scene_traffic")
        "anti_wind" -> s.text("scene_wind")
        else -> if (name != null && name.startsWith("level_")) s.text("scene_level", code) else code(code)
    }

    private fun transparency(code: Int): String = when (Command.TRANSPARENCY_LEVEL.labels[code]) {
        "voice_enhance" -> s.text("transparency_voice")
        "standard" -> s.text("transparency_standard")
        else -> code(code)
    }

    private fun quickSwitches(): Widget? {
        val switches = listOf(
            Command.GAME_MODE to "game_mode", Command.MULTIPOINT to "two_devices", Command.IN_EAR_DETECTION to "in_ear_detection", Command.LDAC to "ldac",
        ).filter { state.supports(it.first) }
        if (switches.isEmpty()) return null
        val rows = switches.chunked(2).map { row ->
            Row(row.map { (command, title) -> switchTile(command, s.text(title)) } + if (row.size == 1) listOf(Spacer(0f)) else emptyList(), 8f)
        }
        return Panel(Column(listOf(label(s.text("quick_switches"))) + rows, 10f))
    }

    private fun switchTile(command: Command, title: String): Widget {
        val on = state.switch(command) == true
        return Button("$title  ·  ${s.text(if (on) "on" else "off")}", "switch_${command.name}", primary = on) { act { set(command, if (on) 0 else 1) } }
    }
}
