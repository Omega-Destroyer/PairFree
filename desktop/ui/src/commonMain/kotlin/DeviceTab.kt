package io.github.omegadestroyer.pairfree.ui

import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.brands.Brands
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.blockedByLdac
import io.github.omegadestroyer.pairfree.protocol.offers
import io.github.omegadestroyer.pairfree.shell.DesktopStrings
import io.github.omegadestroyer.pairfree.shell.LabelWords
import io.github.omegadestroyer.pairfree.shell.Side

/** What the Device tab asks the app for. */
internal class DeviceCallbacks(
    val set: (Command, IntArray) -> Unit,
    val confirm: (title: String, body: String, confirm: String, onConfirm: () -> Unit) -> Unit,
    val copyReport: () -> Unit,
    val factoryReset: () -> Unit,
    val openFit: () -> Unit,
    val openSeal: () -> Unit,
    val changed: () -> Unit,
)

/** The earbuds themselves (docs/DESIGN.md, Device). Sections show only for what the earbuds answered. */
internal class DeviceTab(
    private val device: Paired,
    private val state: EarbudState,
    private val model: Model?,
    private val rows: List<Widget>,
    private val reportCopied: Boolean,
    private val rowState: RowState,
    private val s: DesktopStrings,
    private val theme: Theme,
    private val call: DeviceCallbacks,
) {
    private val words = LabelWords(s)

    private fun label(text: String) = Label(text, theme.labelFont, theme.label, caps = true)

    private fun note(text: String) = Label(text, theme.small, theme.secondaryText)

    fun build(): Widget {
        val parts = mutableListOf<Widget>()
        parts += Column(
            listOfNotNull(
                Label(device.name, theme.title),
                state.firmware?.let { Label(s.text("firmware", it.version), theme.labelFont, theme.label, caps = true) },
                state.firmware?.let { Label(it.text, theme.labelFont, theme.label) },
            ),
            4f,
        )

        val connection = listOf(Command.MULTIPOINT to "two_devices", Command.LDAC to "ldac", Command.LE_AUDIO to "le_audio").filter { state.offers(it.first, model) }
        if (connection.isNotEmpty()) {
            val out = mutableListOf<Widget>(label(s.text("connection")))
            connection.forEachIndexed { i, (command, title) ->
                if (i > 0) out += Divider()
                val blocked = state.blockedByLdac(command, model)
                val on = state.switch(command) == true
                val noteText = when {
                    blocked -> s.text("needs_ldac_off")
                    command == Command.LE_AUDIO -> s.text("le_audio_note")
                    command == Command.LDAC && on -> s.text("ldac_phone_note")
                    else -> null
                }
                val titleWidget = Column(listOfNotNull<Widget>(Label(s.text(title), color = if (blocked) theme.secondaryText else null), noteText?.let(::note)), 2f)
                out += switchRow("conn_${command.name}", titleWidget, on, enabled = !blocked) { v -> call.set(command, intArrayOf(if (v) 1 else 0)) }
            }
            if (model?.ldacOrMultipoint == true) out += note(s.text("ldac_or_multipoint"))
            parts += Panel(Column(out, 8f))
        }

        // Earbuds without a fit test of their own can still use the tone check (PairFree's version, for every brand). A test that needs a sound from the phone (QCY's) is left to
        // Android until the desktop plays it: without the sound the earbuds' result would mean nothing.
        val ownFit = state.hasFitTest && !state.fitSound
        parts += Panel(
            Column(
                listOf(
                    label(s.text("ear_tips")),
                    note(s.text(if (ownFit) "fit_test_about" else "seal_check_about")),
                    if (ownFit) Button(s.text("fit_test_open"), "fit_open") { call.openFit() } else Button(s.text("seal_check_open"), "seal_open") { call.openSeal() },
                ),
                8f,
            ),
        )

        if (state.supports(Command.WATER_DRAIN)) {
            val draining = state.switch(Command.WATER_DRAIN) == true
            parts += Panel(
                Column(
                    listOf(
                        label(s.text("water_drain")),
                        note(s.text(if (draining) "water_draining" else "water_drain_about")),
                        Button(s.text("water_drain_start"), "water_drain", enabled = !draining) { call.set(Command.WATER_DRAIN, intArrayOf(1)) },
                    ),
                    8f,
                ),
            )
        }

        val volume = state.supports(Command.PROMPT_VOLUME)
        val language = state.number(Command.VOICE_LANGUAGE)
        val shutdown = state.number(Command.AUTO_SHUTDOWN)
        if (volume || language != null || shutdown != null) {
            val out = mutableListOf<Widget>(label(s.text("prompts_and_power")))
            if (volume) {
                val current = rowState.pending["prompt_volume"] ?: state.number(Command.PROMPT_VOLUME) ?: 0
                rowState.senders["prompt_volume"] = { v -> call.set(Command.PROMPT_VOLUME, intArrayOf(v)) }
                out += Column(
                    listOf(
                        Split(Label(s.text("prompt_volume")), Label(current.toString(), theme.bigNumber.copy(size = 14f), theme.accent, Align.END), 60f),
                        Row(
                            listOf(
                                StepButton("−", "pv_minus", current > 0) { call.set(Command.PROMPT_VOLUME, intArrayOf((current - 1).coerceAtLeast(0))) },
                                Slider(current, 0, 100, "pv_slider") { rowState.pending["prompt_volume"] = it; call.changed() },
                                StepButton("+", "pv_plus", current < 100) { call.set(Command.PROMPT_VOLUME, intArrayOf((current + 1).coerceAtMost(100))) },
                            ),
                            10f,
                            listOf(0.1f, 1f, 0.1f),
                        ),
                    ),
                    6f,
                )
            }
            if (language != null) {
                if (volume) out += Divider()
                val codes = Command.VOICE_LANGUAGE.labels.keys.sorted()
                out += choiceRow(rowState, "prompt_language", Label(s.text("prompt_language")), words.languageName(language), codes.map(words::languageName), codes.indexOf(language).takeIf { it >= 0 }, theme, call.changed) {
                    call.set(Command.VOICE_LANGUAGE, intArrayOf(codes[it]))
                }
            }
            if (shutdown != null) {
                if (volume || language != null) out += Divider()
                val codes = Command.AUTO_SHUTDOWN.labels.keys.sorted()
                out += choiceRow(rowState, "auto_shutdown", Label(s.text("auto_shutdown")), words.shutdownName(shutdown), codes.map(words::shutdownName), codes.indexOf(shutdown).takeIf { it >= 0 }, theme, call.changed) {
                    call.set(Command.AUTO_SHUTDOWN, intArrayOf(codes[it]))
                }
            }
            parts += Panel(Column(out, 8f))
        }

        val finds = listOf(Command.FIND_LEFT to Side.LEFT, Command.FIND_RIGHT to Side.RIGHT).filter { state.supports(it.first) }
        if (finds.isNotEmpty()) {
            val buttons = finds.map { (command, side) ->
                val beeping = state.switch(command) == true
                Button(s.text(if (beeping) "stop_beep" else "beep", words.sideName(side)), "find_${side.name}", primary = beeping) {
                    call.set(command, intArrayOf(command.labels.entries.first { it.value == (if (beeping) "stop" else "beep") }.key))
                } as Widget
            }
            parts += Panel(Column(listOf(label(s.text("find_earbuds")), note(s.text("find_warning")), Row(buttons, 8f)), 8f))
        }

        parts += rows

        val maintenance = mutableListOf<Widget>(label(s.text("maintenance")), Button(s.text("copy_report"), "copy_report") { call.copyReport() })
        if (reportCopied) maintenance += note(s.text("report_copied"))
        if (Brands[device.brand].hasFactoryReset) {
            maintenance += Button(s.text("factory_reset"), "factory_reset") {
                call.confirm(s.text("factory_reset_title"), s.text("factory_reset_body"), s.text("factory_reset")) { call.factoryReset() }
            }
        }
        parts += Panel(Column(maintenance, 8f))
        parts += Label(s.text("privacy_line"), theme.small, theme.label, Align.CENTER)
        return Column(parts, 16f)
    }
}
