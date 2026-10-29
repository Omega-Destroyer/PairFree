package io.github.omegadestroyer.pairfree.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SoundpeatsSettingsTest {
    @Test
    fun onlyWhatTheEarbudsAnswered() {
        assertTrue(SoundpeatsSettings.groups(EarbudState(), null).isEmpty(), "nothing answered, nothing shown")
        val state = EarbudState(
            mapOf(
                Command.DYNAMIC_BASS to Value.Switch(true),
                Command.PANORAMIC_SOUND to Value.Number(2),
                Command.VOLUME_BALANCE to Value.Number(40),
                Command.HEARING_PROTECTION to Value.Number(20),
                Command.PRIVACY_MODE to Value.Switch(false),
                Command.ANC_ENV_ADAPTIVE to Value.Switch(true),
            ),
        )
        val groups = SoundpeatsSettings.groups(state, null).associateBy { it.key }
        assertEquals(setOf("sound_effects", "noise_adaptive", "other"), groups.keys)
        val sound = groups.getValue("sound_effects")
        assertEquals(SettingTab.SOUND, sound.tab)
        assertEquals(
            listOf(
                Setting.Toggle("sp_dynamic_bass", true),
                Setting.Choice("sp_panoramic_sound", "movie", listOf("normal", "music", "movie")),
                Setting.Level("sp_volume_balance", 40, 0, 100),
                Setting.Level("sp_hearing_protection", 50, 50, 100),
            ),
            sound.settings,
        )
        assertEquals(SettingTab.HOME, groups.getValue("noise_adaptive").tab)
        assertEquals(listOf(Setting.Toggle("sp_privacy_mode", false)), groups.getValue("other").settings)
    }

    @Test
    fun changesAreTheOfficialAppsCommands() {
        assertEquals(Command.DYNAMIC_BASS to 0, SoundpeatsSettings.change(SettingChange.Toggle("sp_dynamic_bass", false)))
        assertEquals(Command.PANORAMIC_SOUND to 1, SoundpeatsSettings.change(SettingChange.Choice("sp_panoramic_sound", "music")))
        assertEquals(Command.VOLUME_BALANCE to 55, SoundpeatsSettings.change(SettingChange.Level("sp_volume_balance", 55)))
        assertNull(SoundpeatsSettings.change(SettingChange.Toggle("sp_factory_reset", true)), "only the rows it builds")
        assertNull(SoundpeatsSettings.change(SettingChange.Toggle("dynamic_bass", true)), "another brand's row")
    }
}
