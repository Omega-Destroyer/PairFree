package io.github.omegadestroyer.pairfree.protocol.samsung

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Buds4 family as Samsung's own Galaxy Buds plugin (budsunitemgr 9.0.10) reads and writes it
 * (docs/samsung/PROTOCOL.md section 12). No reply from real earbuds is known yet, so the state here
 * is built byte by byte from the plugin's parser.
 */
class SamsungBuds4Test {
    private val pro = SamsungModels.of("SM-R640")!!
    private val buds4 = SamsungModels.of("SM-R540")!!

    /** A Buds4 Pro state at revision 5, every byte set as the plugin reads it. */
    private fun state(): ByteArray {
        val b = IntArray(65)
        b[0] = 5; b[2] = 80; b[3] = 70; b[4] = 1; b[5] = 1; b[6] = 0x11; b[7] = 50; b[9] = 2
        b[10] = 0x8F                // touch on, single, double, triple, hold
        b[11] = 0x22                // touch and hold: noise control on both sides
        b[12] = 1; b[19] = 1        // noise cancelling; seamless connection on (1 is on here)
        b[21] = 0x5C                // cycle: right ANC + off (8|4), left ambient + off (1|4) << 4
        b[23] = 2; b[24] = 1; b[25] = 16; b[26] = 1; b[27] = 1; b[29] = 1; b[30] = 0x21; b[31] = 2
        b[34] = 0                   // in-ear detection for calls on
        b[36] = 1; b[37] = 2; b[40] = 1; b[42] = 1
        b[43] = 0x10                // the left earbud is charging
        b[44] = 1; b[46] = 1; b[52] = 1; b[54] = 1; b[55] = 2; b[56] = 3
        b[60] = 1; b[61] = 2        // Buds4 Pro: loud noise reduction, adaptive response level
        return ByteArray(b.size) { b[it].toByte() }
    }

    private fun connected(model: SamsungModel = pro) =
        SamsungSession(model).also { it.receive(SamsungPacket(SamsungMessage.EXTENDED_STATUS_UPDATED, state())) }

    private fun packet(id: Int, vararg values: Int) = SamsungPacket(id, ByteArray(values.size) { values[it].toByte() })

    private fun setting(s: SamsungSession, key: String) = s.groups.flatMap { it.settings }.firstOrNull { it.key == key }

    @Test
    fun readsTheStateAsSamsungsPluginDoes() {
        val s = SamsungStatus.parse(pro, state())!!
        assertEquals(true, s.flag("seamless_connection_enabled"), "1 is on in Samsung's plugin")
        assertEquals(true, s.flag("call_path_control"))
        assertEquals(true, s.flag("customize_conversation_boost"))
        assertEquals(true, s.flag("noise_reduction_amplify"))
        assertEquals(false, s.flag("auto_adjust_sound"), "byte 41, which GalaxyBudsClient does not have")
        assertEquals(true, s.flag("spatial_audio_head_tracking"))
        assertEquals(true, s.flag("is_left_charging"))
        assertEquals(false, s.flag("is_right_charging"))
        assertEquals(true, s.flag("extra_clear_call_sound"))
        assertEquals(true, s.flag("auto_pause_resume"))
        assertEquals(true, s.flag("siren_detect"))
        assertEquals("1.2.3", s.values["hot_command_version"])
        assertEquals(true, s.flag("loud_noise_reduction"))
        assertEquals(2, s.int("adaptive_response_level"))
        assertEquals(true, s.flag("noise_control_touch_anc"))
        assertEquals(true, s.flag("noise_control_touch_off"))
        assertEquals(true, s.flag("noise_control_touch_left_ambient"))
    }

    @Test
    fun connectingAsksForTheLongVersionAndTheEqCurves() {
        val s = SamsungSession(pro)
        s.receive(SamsungPacket(SamsungMessage.EXTENDED_STATUS_UPDATED, state()))
        assertEquals(
            listOf(SamsungMessage.MANAGER_INFO, SamsungMessage.VERSION_INFO_LONG, SamsungMessage.CUSTOM_EQUALIZE_RECV),
            s.takeOutbox().map { it.id },
        )
        // hwL, hwR, then 20 bytes of text per earbud, then two more.
        val text = "R640XXU0AZJ1".encodeToByteArray()
        val reply = byteArrayOf(1, 1) + text + ByteArray(20 - text.size) + text + ByteArray(20 - text.size) + byteArrayOf(0, 0)
        s.receive(SamsungPacket(SamsungMessage.VERSION_INFO_LONG, reply))
        assertEquals("R640XXU0AZJ1", s.state.firmware?.version)
    }

    @Test
    fun theEqCurvesComeWithAFlatOneFirst() {
        val s = connected()
        val reply = byteArrayOf(6, 9) + ByteArray(6 * 9) { (it / 9 + 1).toByte() }
        s.receive(SamsungPacket(SamsungMessage.CUSTOM_EQUALIZE_RECV, reply))
        assertEquals(7, s.eqCurves.size, "Balanced, five presets, custom")
        assertEquals(List(9) { 0 }, s.eqCurves[0])
        assertEquals(List(9) { 6 }, s.eqCurves[6])
        // The custom curve is number 6 on these models, selected before its bands go out.
        assertEquals(
            listOf(packet(SamsungMessage.EQUALIZER, 6), packet(SamsungMessage.CUSTOM_EQUALIZE_SEND, 9, 1, 2, 3, 4, 5, 6, 7, 8, 9)),
            SamsungEq.custom(pro, listOf(1, 2, 3, 4, 5, 6, 7, 8, 9)),
        )
        val custom = SamsungStatus.parse(pro, state().also { it[9] = 6 })!!
        assertEquals(SamsungEq.CUSTOM, SamsungEq.reported(custom))
    }

    @Test
    fun writesAsSamsungsPluginDoes() {
        val s = connected()
        // Seamless connection: 1 is on.
        assertEquals(listOf(packet(SamsungMessage.SET_SEAMLESS_CONNECTION, 0)), s.change(SettingChange.Toggle("seamless_connection", false)))
        assertEquals(listOf(packet(SamsungMessage.SET_SEAMLESS_CONNECTION, 1)), s.change(SettingChange.Toggle("seamless_connection", true)))
        // Ambient sound per earbud: on, left, right, conversation boost, noise reduction amplify, its level.
        assertEquals(
            listOf(packet(SamsungMessage.CUSTOMIZE_AMBIENT_SOUND, 1, 2, 1, 0, 1, 2)),
            s.change(SettingChange.Toggle("conversation_boost", false)),
        )
        assertNull(setting(s, "ambient_custom_tone"), "no tone on these models")
        // Touch lock: ten bytes.
        assertEquals(
            listOf(packet(SamsungMessage.LOCK_TOUCHPAD, 1, 0, 1, 1, 1, 0, 0, 0, 1, 1)),
            s.change(SettingChange.Toggle("touch_single", false)),
        )
        // Touch and hold: the codes, then each side's assistant.
        assertEquals(listOf(packet(SamsungMessage.SET_TOUCHPAD_OPTION, 6, 2, 0, 0)), s.change(SettingChange.Choice("hold_left", "volume")))
        assertEquals(
            listOf(packet(SamsungMessage.SET_TOUCHPAD_OPTION, 6, 3, 0, 1)),
            s.change(SettingChange.Choice("hold_right", "digital_assistant")),
        )
    }

    @Test
    fun theTouchAndHoldCycleIsOfferedAndWritten() {
        val s = connected()
        val left = setting(s, "hold_cycle_left") as Setting.Choice
        assertEquals("ambient_off", left.value)
        assertEquals("anc_off", (setting(s, "hold_cycle_right") as Setting.Choice).value)
        // Left then right, each 1 ambient + 4 off + 8 noise cancelling.
        assertEquals(
            listOf(packet(SamsungMessage.SET_TOUCH_AND_HOLD_NOISE_CONTROLS, 8 or 1, 8 or 4)),
            s.change(SettingChange.Choice("hold_cycle_left", "anc_ambient")),
        )
    }

    @Test
    fun thePluginsOtherSettingsAreOfferedWhereItsListsHaveThem() {
        val s = connected(pro)
        // 360 audio off (byte 35), head tracking on (42): head tracking waits for 360 audio.
        assertEquals(Setting.Toggle("audio_360", false), setting(s, "audio_360"))
        assertEquals(Setting.Toggle("head_tracking", true, enabled = false), setting(s, "head_tracking"))
        assertNull(s.change(SettingChange.Toggle("head_tracking", false)))
        assertEquals(listOf(packet(SamsungMessage.SET_SPATIAL_AUDIO, 1, 1)), s.change(SettingChange.Toggle("audio_360", true)))
        assertEquals(listOf(packet(SamsungMessage.SET_SPATIAL_AUDIO, 1, 0)), s.change(SettingChange.Toggle("head_tracking", false)))

        assertEquals(listOf(packet(SamsungMessage.ADAPTIVE_EQ_CONTROL, 1)), s.change(SettingChange.Toggle("adjust_sound_fit", true)))
        // Voice boost and volume normalisation go together in message 106.
        assertEquals(listOf(packet(SamsungMessage.SET_BOOST_LOUDNESS, 1, 0)), s.change(SettingChange.Toggle("voice_boost", true)))
        assertEquals(listOf(packet(SamsungMessage.SET_BOOST_LOUDNESS, 1, 1)), s.change(SettingChange.Toggle("volume_normalization", true)))
        // Loud noise reduction keeps the adaptive response level the earbuds reported (2).
        assertEquals(Setting.Toggle("loud_noise_reduction", true), setting(s, "loud_noise_reduction"))
        assertEquals(listOf(packet(SamsungMessage.ADAPTIVE_RESPONSE_LEVEL, 2, 0, 0)), s.change(SettingChange.Toggle("loud_noise_reduction", false)))
        assertEquals(listOf(packet(SamsungMessage.NECK_STRETCH, 1)), s.change(SettingChange.Toggle("neck_stretch", true)))
        assertNull(setting(s, "auto_off"), "no timer on the Buds4 Pro")

        assertNull(setting(connected(buds4), "loud_noise_reduction"), "the Buds4 Pro's alone")
    }

    @Test
    fun theH1HasAnAutoPowerOffTimer() {
        val b = state()
        b[60] = 60  // the H1's tail: the timer, in minutes
        val s = SamsungSession(SamsungModels.of("SM-U700")!!).also { it.receive(SamsungPacket(SamsungMessage.EXTENDED_STATUS_UPDATED, b)) }
        assertEquals(Setting.Choice("auto_off", "60", listOf("30", "60", "120")), setting(s, "auto_off"))
        assertEquals(listOf(packet(SamsungMessage.AUTO_POWER_OFF_TIMER, 120)), s.change(SettingChange.Choice("auto_off", "120")))
        assertNull(setting(s, "neck_stretch"), "the H1 has no neck stretch reminder")
    }

    @Test
    fun theBuds4OffersWhatSamsungsListsGiveIt() {
        val s = connected(buds4)
        assertNull(setting(s, "ambient_custom"), "no ambient sound per earbud on the Buds4")
        assertNull(setting(s, "siren_detect"))
        assertFalse(s.state.hasFitTest, "no fit test on the Buds4")
        val p = connected(pro)
        assertTrue(p.state.hasFitTest)
        assertEquals(listOf(packet(SamsungMessage.SET_SIREN_DETECT, 0)), p.change(SettingChange.Toggle("siren_detect", false)))
        assertEquals(listOf(packet(SamsungMessage.SET_PAUSE_MEDIA_ONE_BUD_REMOVED, 0)), p.change(SettingChange.Toggle("auto_pause", false)))
    }

    @Test
    fun aHeadsetHasOneBattery() {
        val h1 = SamsungModels.of("SM-U700")!!
        val s = connected(h1)
        assertEquals(80, s.state.batteryLeft)
        assertNull(s.state.batteryRight)
        assertNull(s.state.batteryCase)
        assertEquals(true, s.state.supports(Command.ANC_MODE))
    }
}
