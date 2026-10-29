package io.github.omegadestroyer.pairfree.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.ui.theme.Studio
import kotlinx.coroutines.delay

/**
 * A brand's own settings for [tab] ([SettingGroup]), one panel per group: switches, choices that
 * open a picker, levels as steps or a slider, and buttons. Each change goes out through [onChange];
 * a slider's once it settles.
 */
@Composable
internal fun SettingGroups(groups: List<SettingGroup>, tab: SettingTab, onChange: (SettingChange) -> Unit) {
    var picking by remember { mutableStateOf<Setting.Choice?>(null) }
    for (group in groups.filter { it.tab == tab }) {
        Panel {
            Label(groupTitle(group.key))
            group.settings.forEachIndexed { i, setting ->
                if (i > 0) Divider()
                when (setting) {
                    is Setting.Toggle -> SwitchRow(
                        settingTitle(setting.key),
                        setting.on,
                        { onChange(SettingChange.Toggle(setting.key, it)) },
                        note = settingNote(setting.key),
                        enabled = setting.enabled,
                    )
                    is Setting.Choice -> ValueRow(
                        settingTitle(setting.key),
                        setting.value?.let { optionName(setting.key, it) } ?: stringResource(R.string.unknown),
                        { picking = setting },
                    )
                    is Setting.Level -> LevelRow(setting, onChange)
                    is Setting.Action -> StudioButton(
                        stringResource(if (setting.active) actionStop(setting.key) else actionStart(setting.key)),
                        { onChange(SettingChange.Press(setting.key)) },
                        Modifier.fillMaxWidth(),
                        primary = setting.active,
                    )
                }
            }
            groupNote(group.key)?.let { Text(it, style = Studio.type.small, color = Studio.colors.secondaryText) }
        }
    }
    picking?.let { choice ->
        PickerDialog(
            title = settingTitle(choice.key),
            options = choice.options,
            selected = choice.value,
            label = { optionName(choice.key, it) },
            onPick = { onChange(SettingChange.Choice(choice.key, it)) },
            onDismiss = { picking = null },
        )
    }
}

/** A level: up to five steps as a segmented row, more as a slider with minus and plus that sends once it settles. */
@Composable
private fun LevelRow(setting: Setting.Level, onChange: (SettingChange) -> Unit) {
    val title = settingTitle(setting.key)
    val steps = (setting.min..setting.max).toList()
    if (steps.size <= 5) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title)
            Segmented(steps, setting.value, { levelName(setting.key, it, setting.max) }, { onChange(SettingChange.Level(setting.key, it)) }, primary = false)
        }
        return
    }
    var pending by remember { mutableStateOf<Int?>(null) }
    val current = pending ?: setting.value
    LaunchedEffect(pending) {
        val value = pending ?: return@LaunchedEffect
        delay(SEND_AFTER_MS)
        onChange(SettingChange.Level(setting.key, value))
        pending = null
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f))
            Text(levelName(setting.key, current, setting.max), style = Studio.type.number, color = Studio.colors.accent)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StepButton(plus = false, { pending = (current - 1).coerceAtLeast(setting.min) }, enabled = current > setting.min)
            StudioSlider(current, setting.min..setting.max, { pending = it }, Modifier.weight(1f), description = title, origin = levelOrigin(setting))
            StepButton(plus = true, { pending = (current + 1).coerceAtMost(setting.max) }, enabled = current < setting.max)
        }
    }
}

/** The slider's centre for levels that run from one side to the other, such as balance. */
private fun levelOrigin(setting: Setting.Level): Int =
    if (setting.key == "balance" || setting.key == "clear_bass" || setting.key.startsWith("eq_")) (setting.min + setting.max) / 2 else setting.min

private const val SEND_AFTER_MS = 300L

@Composable
private fun groupTitle(key: String): String = stringResource(
    when (key) {
        "noise" -> R.string.setting_group_noise
        "sound" -> R.string.setting_group_sound
        "touch" -> R.string.setting_group_touch
        "ambient_customize" -> R.string.setting_group_ambient_customize
        "calls" -> R.string.setting_group_calls
        "connection" -> R.string.connection
        "other" -> R.string.setting_group_other
        "sound_effects" -> R.string.setting_group_sound_effects
        "noise_adaptive" -> R.string.setting_group_noise_adaptive
        "find" -> R.string.find_earbuds
        else -> R.string.setting_group_other
    },
)

@Composable
private fun groupNote(key: String): String? = when (key) {
    "find" -> stringResource(R.string.setting_find_note)
    else -> null
}

@Composable
private fun settingTitle(key: String): String = when (key) {
    "ambient_sound" -> stringResource(R.string.setting_ambient_sound)
    "anc" -> stringResource(R.string.setting_anc)
    "ambient_aware" -> stringResource(R.string.setting_ambient_aware)
    "talk_thru" -> stringResource(R.string.setting_talk_thru)
    "adaptive" -> stringResource(R.string.setting_adaptive_audio)
    "adaptive_level" -> stringResource(R.string.setting_adaptive_level)
    "conversation_awareness" -> stringResource(R.string.setting_conversation_awareness)
    "personalized_volume" -> stringResource(R.string.setting_personalized_volume)
    "press_speed" -> stringResource(R.string.setting_press_speed)
    "hold_duration" -> stringResource(R.string.setting_hold_duration)
    "volume_swipe" -> stringResource(R.string.setting_volume_swipe)
    "volume_swipe_length" -> stringResource(R.string.setting_volume_swipe_length)
    "ear_detection" -> stringResource(R.string.setting_ear_detection)
    "allow_off" -> stringResource(R.string.setting_allow_off)
    "auto_off" -> stringResource(R.string.auto_shutdown)
    "relieve_pressure" -> stringResource(R.string.setting_relieve_pressure)
    "ambient_volume" -> stringResource(R.string.setting_ambient_volume)
    "voice_focus" -> stringResource(R.string.setting_voice_focus)
    "extra_loud" -> stringResource(R.string.setting_extra_loud)
    "anc_level" -> stringResource(R.string.setting_anc_level)
    "noise_one_earbud" -> stringResource(R.string.setting_noise_one_earbud)
    "voice_detect" -> stringResource(R.string.setting_voice_detect)
    "voice_detect_wait" -> stringResource(R.string.setting_voice_detect_wait)
    "balance" -> stringResource(R.string.setting_balance)
    "ambient_custom" -> stringResource(R.string.setting_ambient_custom)
    "ambient_custom_left" -> stringResource(R.string.setting_ambient_custom_left)
    "ambient_custom_right" -> stringResource(R.string.setting_ambient_custom_right)
    "ambient_custom_tone" -> stringResource(R.string.setting_ambient_custom_tone)
    "touch_controls" -> stringResource(R.string.touch_controls)
    "touch_single" -> stringResource(R.string.setting_touch_single)
    "touch_double" -> stringResource(R.string.setting_touch_double)
    "touch_triple" -> stringResource(R.string.setting_touch_triple)
    "touch_hold" -> stringResource(R.string.setting_touch_hold)
    "touch_double_call" -> stringResource(R.string.setting_touch_double_call)
    "touch_hold_call" -> stringResource(R.string.setting_touch_hold_call)
    "hold_left" -> stringResource(R.string.setting_hold_side, stringResource(R.string.left))
    "hold_right" -> stringResource(R.string.setting_hold_side, stringResource(R.string.right))
    "hold_cycle" -> stringResource(R.string.setting_hold_cycle)
    "hold_cycle_left" -> stringResource(R.string.setting_hold_cycle_side, stringResource(R.string.left))
    "hold_cycle_right" -> stringResource(R.string.setting_hold_cycle_side, stringResource(R.string.right))
    "edge_double_tap" -> stringResource(R.string.setting_edge_double_tap)
    "extra_clear_call" -> stringResource(R.string.setting_extra_clear_call)
    "sidetone" -> stringResource(R.string.setting_sidetone)
    "call_path" -> stringResource(R.string.setting_call_path)
    "seamless_connection" -> stringResource(R.string.setting_seamless_connection)
    "voice_wakeup" -> stringResource(R.string.setting_voice_wakeup)
    "auto_pause" -> stringResource(R.string.setting_auto_pause)
    "siren_detect" -> stringResource(R.string.setting_siren_detect)
    "adaptive_volume" -> stringResource(R.string.setting_adaptive_volume)
    "conversation_boost" -> stringResource(R.string.setting_conversation_boost)
    "wind_reduction" -> stringResource(R.string.setting_wind_reduction)
    "speak_to_chat" -> stringResource(R.string.setting_speak_to_chat)
    "clear_bass" -> stringResource(R.string.setting_clear_bass)
    "dsee" -> stringResource(R.string.setting_dsee)
    "pause_taken_off" -> stringResource(R.string.setting_pause_taken_off)
    "voice_guidance" -> stringResource(R.string.setting_voice_guidance)
    "audio_mode" -> stringResource(R.string.setting_audio_mode)
    "anr" -> stringResource(R.string.setting_anr)
    "noise_level" -> stringResource(R.string.setting_noise_level)
    "eq_bass" -> stringResource(R.string.setting_eq_bass)
    "eq_low_mid" -> stringResource(R.string.setting_eq_low_mid)
    "eq_mid" -> stringResource(R.string.setting_eq_mid)
    "eq_high_mid" -> stringResource(R.string.setting_eq_high_mid)
    "eq_treble" -> stringResource(R.string.setting_eq_treble)
    "multipoint" -> stringResource(R.string.setting_multipoint)
    "auto_answer" -> stringResource(R.string.setting_auto_answer)
    "voice_prompts" -> stringResource(R.string.setting_voice_prompts)
    "sidetone_level" -> stringResource(R.string.setting_sidetone)
    "find_mute_left" -> stringResource(R.string.setting_find_mute, stringResource(R.string.left))
    "find_mute_right" -> stringResource(R.string.setting_find_mute, stringResource(R.string.right))
    // SOUNDPEATS (SoundpeatsSettings)
    "sp_dynamic_eq" -> stringResource(R.string.setting_sp_dynamic_eq)
    "sp_dynamic_bass" -> stringResource(R.string.setting_dynamic_bass)
    "sp_movie_mode" -> stringResource(R.string.setting_movie_mode)
    "sp_spatial_audio" -> stringResource(R.string.setting_sp_spatial_audio)
    "sp_dolby" -> stringResource(R.string.setting_dolby)
    "sp_personal_volume" -> stringResource(R.string.setting_personalized_volume)
    "sp_panoramic_sound" -> stringResource(R.string.setting_panoramic_sound)
    "sp_volume_balance" -> stringResource(R.string.setting_balance)
    "sp_hearing_protection" -> stringResource(R.string.setting_hearing_protection)
    "sp_anc_env_adaptive" -> stringResource(R.string.setting_anc_env_adaptive)
    "sp_privacy_mode" -> stringResource(R.string.setting_privacy_mode)
    "sp_fall_detection" -> stringResource(R.string.setting_fall_detection)
    "sp_light_effect" -> stringResource(R.string.setting_light_effect)
    else -> key
}

@Composable
private fun settingNote(key: String): String? = when (key) {
    "relieve_pressure" -> stringResource(R.string.setting_relieve_pressure_note)
    "voice_focus" -> stringResource(R.string.setting_voice_focus_note)
    "extra_loud" -> stringResource(R.string.setting_extra_loud_note)
    "noise_one_earbud" -> stringResource(R.string.setting_noise_one_earbud_note)
    "voice_detect" -> stringResource(R.string.setting_voice_detect_note)
    "touch_controls" -> stringResource(R.string.touch_controls_note)
    "edge_double_tap" -> stringResource(R.string.setting_edge_double_tap_note)
    "sidetone" -> stringResource(R.string.setting_sidetone_note)
    "call_path" -> stringResource(R.string.setting_call_path_note)
    "seamless_connection" -> stringResource(R.string.setting_seamless_connection_note)
    "wind_reduction" -> stringResource(R.string.setting_wind_reduction_note)
    "speak_to_chat" -> stringResource(R.string.setting_speak_to_chat_note)
    "dsee" -> stringResource(R.string.setting_dsee_note)
    "noise_level" -> stringResource(R.string.setting_noise_level_note)
    "talk_thru" -> stringResource(R.string.setting_talk_thru_note)
    "adaptive" -> stringResource(R.string.setting_adaptive_audio_note)
    "adaptive_level" -> stringResource(R.string.setting_adaptive_level_note)
    "conversation_awareness" -> stringResource(R.string.setting_conversation_awareness_note)
    "personalized_volume" -> stringResource(R.string.setting_personalized_volume_note)
    "allow_off" -> stringResource(R.string.setting_allow_off_note)
    else -> null
}

@Composable
private fun optionName(key: String, option: String): String = when {
    key == "sp_panoramic_sound" -> stringResource(
        when (option) {
            "music" -> R.string.setting_panoramic_music
            "movie" -> R.string.setting_panoramic_movie
            else -> R.string.setting_panoramic_normal
        },
    )
    key == "anc_level" -> stringResource(if (option == "high") R.string.setting_anc_level_high else R.string.setting_anc_level_low)
    key == "voice_detect_wait" -> stringResource(R.string.setting_seconds, option.toIntOrNull() ?: 0)
    key == "press_speed" || key == "hold_duration" || key == "volume_swipe_length" -> stringResource(
        when (option) {
            "slower" -> R.string.setting_option_slower
            "slowest" -> R.string.setting_option_slowest
            "shorter" -> R.string.setting_option_shorter
            "shortest" -> R.string.setting_option_shortest
            "longer" -> R.string.setting_option_longer
            "longest" -> R.string.setting_option_longest
            else -> R.string.setting_option_default
        },
    )
    key == "auto_off" -> option.toIntOrNull()?.let { stringResource(R.string.shutdown_minutes, it) } ?: stringResource(R.string.shutdown_never)
    key == "anr" || key == "sidetone_level" -> stringResource(
        when (option) {
            "off" -> R.string.setting_level_off
            "low" -> R.string.setting_level_low
            "medium" -> R.string.setting_level_medium
            "wind" -> R.string.setting_anr_wind
            else -> R.string.setting_level_high
        },
    )
    key.startsWith("hold_cycle") -> stringResource(
        when (option) {
            "anc_ambient" -> R.string.setting_cycle_anc_ambient
            "anc_off" -> R.string.setting_cycle_anc_off
            else -> R.string.setting_cycle_ambient_off
        },
    )
    key.startsWith("hold_") -> stringResource(
        when (option) {
            "voice_assistant" -> R.string.action_voice_assistant
            "bixby" -> R.string.setting_hold_bixby
            "digital_assistant" -> R.string.setting_hold_digital_assistant
            "noise_control" -> R.string.action_anc_cycle
            "volume" -> R.string.setting_hold_volume
            "spotify" -> R.string.setting_hold_spotify
            "ambient_sound" -> R.string.setting_ambient_sound
            "anc" -> R.string.setting_anc
            "quick_ambient_sound" -> R.string.setting_hold_quick_ambient
            else -> R.string.setting_hold_custom
        },
    )
    else -> option
}

/** A level's value in words where it has some: balance from the middle, the tone from soft to clear. */
@Composable
private fun levelName(key: String, value: Int, max: Int): String = when (key) {
    "balance" -> {
        val middle = max / 2
        when {
            value < middle -> stringResource(R.string.setting_balance_left, middle - value)
            value > middle -> stringResource(R.string.setting_balance_right, value - middle)
            else -> stringResource(R.string.setting_balance_middle)
        }
    }
    "clear_bass", "eq_bass", "eq_low_mid", "eq_mid", "eq_high_mid", "eq_treble" -> if (value > 0) "+$value" else value.toString()
    "ambient_custom_tone" -> when (value - max / 2) {
        -2 -> stringResource(R.string.setting_tone_softer)
        -1 -> stringResource(R.string.setting_tone_soft)
        0 -> stringResource(R.string.setting_tone_neutral)
        1 -> stringResource(R.string.setting_tone_clear)
        2 -> stringResource(R.string.setting_tone_clearer)
        else -> value.toString()
    }
    else -> value.toString()
}

private fun actionStart(key: String): Int = when (key) {
    "find" -> R.string.setting_find_start
    else -> R.string.setting_action_start
}

private fun actionStop(key: String): Int = when (key) {
    "find" -> R.string.setting_find_stop
    else -> R.string.setting_action_stop
}
