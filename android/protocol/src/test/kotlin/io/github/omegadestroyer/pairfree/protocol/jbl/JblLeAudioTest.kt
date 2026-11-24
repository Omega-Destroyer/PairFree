package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The LE Audio switch (docs/jbl/PROTOCOL.md section 4.15): `GetSetFeatureCmd`'s key 1 on the Harman models, feature 2976 on protocol 4, the passthrough on Airoha. Not tried on earbuds. */
class JblLeAudioTest {
    private fun model(name: String) = JblModels.ALL.first { it.name == name }

    private fun JblFrame.text() = bytes().joinToString("") { "%02x".format(it) }

    private fun row(s: JblSession) = s.groups.flatMap { it.settings }.firstOrNull { it.key == "jbl_le_audio" }

    @Test
    fun theModelsWhoseControlWritesItHaveTheSwitch() {
        for (name in listOf("JBL LIVE FLEX", "JBL Tour One M3", "JBL Tune 770NC", "JBL Tour Pro 3")) assertTrue("le_audio" in model(name).extras, name)
        assertFalse("le_audio" in model("JBL Tune 670NC").extras, "its control class has no setFeatures")
    }

    @Test
    fun theHarmanModelsUseB1() {
        val s = JblSession(model("JBL LIVE FLEX"))
        assertTrue(s.start().any { it.text() == "aab1030001" + "00" })
        s.receive(JblPacket(JblCommand.GET_SET_FEATURE, byteArrayOf(2, 1, 1, 1)))
        assertEquals(Setting.Toggle("jbl_le_audio", true), row(s))
        assertEquals("aab104" + "01010100", s.change(SettingChange.Toggle("jbl_le_audio", false))!!.single().text())
    }

    @Test
    fun protocol4UsesFeature2976() {
        val s = JblSession(model("JBL Tour One M3"))
        // The earbuds answer the read first (feature 2976, off): a status 0, then the record.
        s.receive(JblReader().feed(JblP4Packet(JblP4.NOTIFICATION, byteArrayOf(0, 0, 0xA0.toByte(), 0x0B, 1, 0, 0x7F)).bytes()).single())
        assertEquals(JblP4Packet.set(2976 to byteArrayOf(0xFF.toByte())), s.change(SettingChange.Toggle("jbl_le_audio", true))!!.single())
    }

    @Test
    fun airohaSendsB1ThroughThePassthroughAndOnlyForKey1() {
        val s = JblSession(model("JBL Tune 770NC"))
        // The passthrough's answer: a status 0, then the Harman frame (off).
        val answer = JblPacket(JblCommand.GET_SET_FEATURE, byteArrayOf(2, 1, 1, 0)).bytes()
        s.receive(RacePacket(JblRace.HM_PASSTHROUGH, byteArrayOf(0) + answer, JblRace.TYPE_RESPONSE))
        val frame = s.change(SettingChange.Toggle("jbl_le_audio", true))!!.single() as RacePacket
        assertEquals(JblRace.HM_PASSTHROUGH, frame.id)
        assertEquals("aab104" + "01010101", frame.payload.joinToString("") { "%02x".format(it) })
        assertTrue(JblLeAudio.allowed(frame.payload))
        assertFalse(JblLeAudio.allowed(byteArrayOf(0xAA.toByte(), 0xB1.toByte(), 4, 1, 2, 1, 1)), "another key")
    }
}
