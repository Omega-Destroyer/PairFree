package io.github.omegadestroyer.pairfree.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.CustomKeys
import io.github.omegadestroyer.pairfree.protocol.soundcore.TouchPress

// Words for the values in data/soundpeats/commands.json. The numbers come from the generated
// tables; only their names are matched here.

/** The value of [name] in [command]'s table, for example `anc` in ANC mode. */
fun numberOf(command: Command, name: String): Int = command.labels.entries.first { it.value == name }.key

/** A noise-cancelling scene. The official app shows `normal` (0x11) with its adaptive label (docs/soundpeats/PROTOCOL.md 3.1). */
@Composable
fun sceneName(code: Int): String = when (val name = Command.ANC_LEVEL.labels[code]) {
    "normal", "adaptive" -> stringResource(R.string.scene_adaptive)
    "indoor" -> stringResource(R.string.scene_indoor)
    "outdoor" -> stringResource(R.string.scene_outdoor)
    "traffic" -> stringResource(R.string.scene_traffic)
    "anti_wind" -> stringResource(R.string.scene_wind)
    else -> if (name != null && name.startsWith("level_")) stringResource(R.string.scene_level, code) else unknownCode(code)
}

@Composable
fun transparencyName(code: Int): String = when (Command.TRANSPARENCY_LEVEL.labels[code]) {
    "voice_enhance" -> stringResource(R.string.transparency_voice)
    "standard" -> stringResource(R.string.transparency_standard)
    else -> unknownCode(code)
}

/** Tap, double tap, triple tap or press and hold, from a gesture name such as `double_left`. */
enum class GestureKind(val prefix: String) { TAP("tap"), DOUBLE("double"), TRIPLE("triple"), HOLD("hold") }

enum class Side(val suffix: String) { LEFT("left"), RIGHT("right") }

/** The gesture number for [kind] on [side], or null if the table has none. */
fun gesture(kind: GestureKind, side: Side): Int? =
    CustomKeys.GESTURES.entries.firstOrNull { it.value == "${kind.prefix}_${side.suffix}" }?.key

@Composable
fun gestureName(kind: GestureKind): String = stringResource(
    when (kind) {
        GestureKind.TAP -> R.string.gesture_tap
        GestureKind.DOUBLE -> R.string.gesture_double
        GestureKind.TRIPLE -> R.string.gesture_triple
        GestureKind.HOLD -> R.string.gesture_hold
    },
)

@Composable
fun sideName(side: Side): String = stringResource(if (side == Side.LEFT) R.string.left else R.string.right)

@Composable
fun actionName(action: Int): String = when (CustomKeys.ACTIONS[action]) {
    "none" -> stringResource(R.string.action_none)
    "volume_up" -> stringResource(R.string.action_volume_up)
    "volume_down" -> stringResource(R.string.action_volume_down)
    "play_pause" -> stringResource(R.string.action_play_pause)
    "game_mode" -> stringResource(R.string.action_game_mode)
    "previous" -> stringResource(R.string.action_previous)
    "next" -> stringResource(R.string.action_next)
    "anc_cycle" -> stringResource(R.string.action_anc_cycle)
    "voice_assistant" -> stringResource(R.string.action_voice_assistant)
    else -> unknownCode(action)
}

/** A Soundcore press (data/soundcore/devices.json buttons). */
@Composable
fun pressName(press: TouchPress): String = stringResource(
    when (press) {
        TouchPress.SINGLE -> R.string.gesture_tap
        TouchPress.DOUBLE -> R.string.gesture_double
        TouchPress.TRIPLE -> R.string.gesture_triple
        TouchPress.LONG -> R.string.gesture_hold
        TouchPress.SLIDE_UP -> R.string.gesture_slide_up
        TouchPress.SLIDE_DOWN -> R.string.gesture_slide_down
    },
)

/** A Soundcore touch action by its name in data/soundcore/button_actions.json. */
@Composable
fun soundcoreActionName(name: String): String = when (name) {
    "volume_up" -> stringResource(R.string.action_volume_up)
    "volume_down" -> stringResource(R.string.action_volume_down)
    "previous_song" -> stringResource(R.string.action_previous)
    "next_song" -> stringResource(R.string.action_next)
    "play_pause" -> stringResource(R.string.action_play_pause)
    "ambient_sound_mode" -> stringResource(R.string.action_anc_cycle)
    "voice_assistant" -> stringResource(R.string.action_voice_assistant)
    "gaming_mode" -> stringResource(R.string.action_game_mode)
    "answer_call" -> stringResource(R.string.action_answer_call)
    "decline_call" -> stringResource(R.string.action_decline_call)
    "bass_up" -> stringResource(R.string.action_bass_up)
    "lights" -> stringResource(R.string.action_lights)
    "noise_canceling" -> stringResource(R.string.action_noise_cancelling)
    "change_mode" -> stringResource(R.string.action_change_mode)
    else -> name
}

/** Prompt languages in their own words. */
@Composable
fun languageName(code: Int): String = when (Command.VOICE_LANGUAGE.labels[code]) {
    "en" -> stringResource(R.string.language_en)
    "zh" -> stringResource(R.string.language_zh)
    "de" -> stringResource(R.string.language_de)
    "ja" -> stringResource(R.string.language_ja)
    "fr" -> stringResource(R.string.language_fr)
    "es" -> stringResource(R.string.language_es)
    "it" -> stringResource(R.string.language_it)
    else -> unknownCode(code)
}

@Composable
fun shutdownName(minutes: Int): String =
    if (minutes == 0) stringResource(R.string.shutdown_never) else stringResource(R.string.shutdown_minutes, minutes)

@Composable
private fun unknownCode(code: Int): String = stringResource(R.string.code_unknown, "0x" + code.toString(16).uppercase().padStart(2, '0'))
