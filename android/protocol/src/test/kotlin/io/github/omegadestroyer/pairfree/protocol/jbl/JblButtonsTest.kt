package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The function button (78), the touch panel's tap and hold (79) and the Quantum headsets' dials (77), docs/jbl/PROTOCOL.md section 4.18. Not tried on earbuds. */
class JblButtonsTest {
    private fun model(name: String) = JblModels.ALL.first { it.name == name }

    private fun JblFrame.text() = bytes().joinToString("") { "%02x".format(it) }

    private fun row(s: JblSession, key: String) = s.groups.flatMap { it.settings }.firstOrNull { it.key == key }

    @Test
    fun theFunctionButtonAndTheTouchPanelAreReadAndWritten() {
        val s = JblSession(model("JBL TOUR ONE"))
        val start = s.start().map { it.text() }
        assertEquals(true, "aa780101" in start && "aa790101" in start)
        s.receive(JblPacket(0x78, byteArrayOf(2, 1, 3)))
        s.receive(JblPacket(0x79, byteArrayOf(2, 1, 1)))
        assertEquals(Setting.Choice("jbl_function_button", "active_mode", listOf("voice_assistant", "ambient_sound", "active_mode")), row(s, "jbl_function_button"))
        assertEquals(Setting.Choice("jbl_touch_panel", "voice_assistant", listOf("voice_assistant", "ambient_sound", "talk_thru")), row(s, "jbl_touch_panel"))
        assertEquals("aa7803000102", s.change(SettingChange.Choice("jbl_function_button", "ambient_sound"))!!.single().text())
        assertEquals("aa7903000103", s.change(SettingChange.Choice("jbl_touch_panel", "talk_thru"))!!.single().text())
    }

    @Test
    fun theQuantumDialsUseGestures55And56AndTheMicButton64() {
        val s = JblSession(model("JBL Quantum 360X"))
        // Dial 1 on the volume (AA), dial 2 on the game and chat balance (A9), the button on the light (AC).
        s.receive(JblPacket(JblCommand.GESTURES, byteArrayOf(2, 0x10, 0xAA.toByte(), 0x11, 0xA9.toByte(), 0x13, 0xAC.toByte())))
        assertEquals("volume", (row(s, "jbl_dial_1") as Setting.Choice).value)
        assertEquals("game_chat", (row(s, "jbl_dial_2") as Setting.Choice).value)
        assertEquals("led", (row(s, "jbl_mic_button") as Setting.Choice).value)
        assertEquals("aa77030013ab", s.change(SettingChange.Choice("jbl_mic_button", "mic_mute"))!!.single().text())
        assertNull(s.change(SettingChange.Choice("jbl_dial_1", "game_chat")), "dial 2 has it")
        assertEquals("aa7703001000", s.change(SettingChange.Choice("jbl_dial_1", "none"))!!.single().text())
    }

    @Test
    fun theTouchSensitivityIsAStepForEachEarbud() {
        val s = JblSession(model("JBL Wave Beam 3"))
        // 4033 (left) at step 1, 4034 (right) at step 0; the Wave Beam 3 has two steps.
        s.receive(JblReader().feed(JblP4Packet(JblP4.NOTIFICATION, byteArrayOf(0, 0, 0xC1.toByte(), 0x0F, 1, 0, 0x81.toByte(), 0xC2.toByte(), 0x0F, 1, 0, 0x80.toByte())).bytes()).single())
        assertEquals(Setting.Level("jbl_sens_left", 1, 0, 1), row(s, "jbl_sens_left"))
        assertEquals(Setting.Level("jbl_sens_right", 0, 0, 1), row(s, "jbl_sens_right"))
        assertEquals(JblP4Packet.set(4034 to byteArrayOf(0x81.toByte())), s.change(SettingChange.Level("jbl_sens_right", 1))!!.single())
    }
}
