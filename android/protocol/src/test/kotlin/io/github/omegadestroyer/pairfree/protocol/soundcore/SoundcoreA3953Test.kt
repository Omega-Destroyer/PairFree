package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.Value
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The Liberty 4 (A3953), whose state reply is read from Soundcore 3.3.0's `A3953AnalysisService` (docs/soundcore/PROTOCOL.md): no recording of it exists yet. */
class SoundcoreA3953Test {
    /** A reply laid out as that parser reads it: the offsets in the document count from the packet's start, so the body starts at 9. */
    private fun reply(longer: Boolean = false): ByteArray {
        val packet = ByteArray(if (longer) 172 else 163)
        fun at(offset: Int, vararg v: Int) = v.forEachIndexed { i, b -> packet[offset + i] = b.toByte() }
        at(9, 0, 1)
        at(11, 4, 3, 0, 1)
        "01.23".toByteArray().copyInto(packet, 15)
        "01.24".toByteArray().copyInto(packet, 20)
        at(112, 26)
        at(137, 7) // the sound mode cycle
        at(138, 0, 0x12, 0, 0, 0, 0) // the ANC block: noise cancelling
        at(150, 1) // wear detection
        at(153, 5, 1, 1, 1, 1, 2) // case battery, bass up, LDAC, two devices, auto power off on, index 2
        at(160, 0, 1, 0) // in-ear beep off, low battery alert on, ambient prompt off
        if (longer) at(163, 1, 2, 3, 0, 0, 0, 0, 0x41, 2) // spatial audio; the colour letter and press sensitivity 2
        return packet.copyOfRange(9, packet.size)
    }

    @Test fun theLayoutReadsTheReplyWhereTheAppsParserDoes() {
        val layout = SoundcoreLayout.of("A3953")!!
        assertEquals(154, layout.minSize)
        assertEquals(0 to 2, layout.locate("tws_status"))
        assertEquals(129, layout.locate("sound_modes")!!.first)
        assertEquals(141, layout.locate("wearing_detection")!!.first)
        assertEquals(153, layout.locate("ambient_sound_prompt")!!.first)
        val session = SoundcoreSession("A3953")
        assertTrue(session.receive(SoundcorePacket(SoundcoreCommand.STATE, reply())))
        assertEquals(Value.Steps(4, 5), session.state.values[Command.BATTERY_LEFT])
        assertEquals(Value.Steps(3, 5), session.state.values[Command.BATTERY_RIGHT])
        assertEquals(Value.Steps(5, 5), session.state.values[Command.BATTERY_CASE])
        assertEquals(Value.Text("A3953_01.23"), session.state.values[Command.FIRMWARE_VERSION])
        assertEquals(Value.Switch(true), session.state.values[Command.LDAC])
        assertEquals(Value.Switch(true), session.state.values[Command.MULTIPOINT])
        assertNotNull(session.state.values[Command.ANC_MODE])
    }

    @Test fun theSwitchesShowAndWriteTheAppsCommandsKeepingTheOtherBytes() {
        val session = SoundcoreSession("A3953")
        session.receive(SoundcorePacket(SoundcoreCommand.STATE, reply(longer = true)))
        val rows = session.groups.single { it.key == "sc_device" }.settings.associateBy { it.key }
        assertEquals(Setting.Toggle("sc_wearing_detection", true), rows["sc_wearing_detection"])
        assertEquals(Setting.Toggle("sc_bass_up", true), rows["sc_bass_up"])
        assertEquals(Setting.Toggle("sc_in_ear_tone", false), rows["sc_in_ear_tone"])
        assertEquals(Setting.Toggle("sc_low_battery_prompt", true), rows["sc_low_battery_prompt"])
        assertEquals(Setting.Toggle("sc_spatial_audio", true), rows["sc_spatial_audio"])
        assertNull(rows["sc_side_tone"], "the A3953's state has no side tone")
        assertEquals("normal", (rows["sc_press_sensitivity"] as Setting.Choice).value)
        val press = session.change(SettingChange.Choice("sc_press_sensitivity", "firm"))!!.single()
        assertEquals(0x0485, press.command)
        assertArrayEquals(byteArrayOf(3), press.body)
        // Wear detection off: 01 81 with one byte.
        val wear = session.change(SettingChange.Toggle("sc_wearing_detection", false))!!.single()
        assertEquals(0x0181, wear.command)
        assertArrayEquals(byteArrayOf(0), wear.body)
        // Auto power off keeps its duration index: 01 86 with the switch and the index 2.
        val off = session.change(SettingChange.Toggle("sc_auto_power_off", false))!!.single()
        assertEquals(0x0186, off.command)
        assertArrayEquals(byteArrayOf(0, 2), off.body)
        // Spatial audio keeps its effect and mode: 10 81 with the switch, the effect 2 and the mode 3.
        val spatial = session.change(SettingChange.Toggle("sc_spatial_audio", false))!!.single()
        assertEquals(0x1081, spatial.command)
        assertArrayEquals(byteArrayOf(0, 2, 3), spatial.body)
        assertEquals(Setting.Toggle("sc_wearing_detection", false), session.groups.single { it.key == "sc_device" }.settings.first { it.key == "sc_wearing_detection" })
        assertNull(session.change(SettingChange.Toggle("sc_side_tone", true)))
    }

    @Test fun theSleepA10AndVrP10AreReadFromTheirParsersToo() {
        // A6610: the reply is at least 87 bytes (76 after the header); smart play at packet offset 77, adaptive noise at 79.
        val a10 = ByteArray(87).also { it[77] = 1; it[79] = 0; it[11] = 3; it[12] = 2 }.copyOfRange(9, 87)
        val s10 = SoundcoreSession("A6610")
        assertTrue(s10.receive(SoundcorePacket(SoundcoreCommand.STATE, a10)))
        val rows = s10.groups.single { it.key == "sc_device" }.settings.associateBy { it.key }
        assertEquals(Setting.Toggle("sc_smart_play", true), rows["sc_smart_play"])
        assertEquals(Setting.Toggle("sc_adaptive_noise", false), rows["sc_adaptive_noise"])
        val on = s10.change(SettingChange.Toggle("sc_adaptive_noise", true))!!.single()
        assertEquals(0x01AC, on.command)
        assertArrayEquals(byteArrayOf(1), on.body)
        // A3850: super hearing 78, call mix 79, microphone 80 (reply at least 89 bytes).
        val p10 = ByteArray(89).also { it[78] = 1; it[80] = 1 }.copyOfRange(9, 89)
        val s3850 = SoundcoreSession("A3850")
        s3850.receive(SoundcorePacket(SoundcoreCommand.STATE, p10))
        val vr = s3850.groups.single { it.key == "sc_device" }.settings.associateBy { it.key }
        assertEquals(Setting.Toggle("sc_super_hearing", true), vr["sc_super_hearing"])
        assertEquals(Setting.Toggle("sc_call_mix", false), vr["sc_call_mix"])
        assertEquals(0x01A3, s3850.change(SettingChange.Toggle("sc_microphone", false))!!.single().command)
    }
}
