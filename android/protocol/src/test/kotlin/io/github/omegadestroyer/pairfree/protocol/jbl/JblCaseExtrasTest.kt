package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The smart case's built-in screen saver, the custom picture's removal, the unlock indicator and find my case (docs/jbl/PROTOCOL.md section 4.11): `A3` ids 3, 6, 9 and 13, protocol 4 features
 * 7942, 7947, 7951, 7954 and 7955, and the Airoha display array's bytes 3, 5, 11 and 19. Not tried on a case.
 */
class JblCaseExtrasTest {
    private fun model(name: String) = JblModels.ALL.first { it.name == name }

    private fun JblFrame.text() = bytes().joinToString("") { "%02x".format(it) }

    private fun row(s: JblSession, key: String) = s.groups.flatMap { it.settings }.firstOrNull { it.key == key }

    @Test
    fun theModelsHaveTheCardsOfTheirDashboard() {
        for (name in listOf("JBL Live Beam 3", "JBL Tour One M3", "JBL Live Buds 3")) assertTrue("case_find" in model(name).extras, name)
        assertTrue("case_unlock" in model("JBL Live Buds 4").extras)
        assertFalse("case_unlock" in model("JBL Live Beam 3").extras)
    }

    @Test
    fun harmanModelsUseTheDisplayCommand() {
        val s = JblSession(model("JBL Live Beam 3"))
        // The screen saver 3 (the third picture), a custom picture there, find my case playing.
        s.receive(JblPacket(JblCommand.CHARGING_CASE_DISPLAY, byteArrayOf(2, 1, 2, 3, 2, 6, 1, 13, 1)))
        assertEquals(Setting.Level("jbl_case_saver", 3, 1, 5), row(s, "jbl_case_saver"))
        assertEquals(Setting.Action("jbl_case_saver_remove"), row(s, "jbl_case_saver_remove"))
        assertEquals(Setting.Toggle("jbl_case_find", true), row(s, "jbl_case_find"))
        assertEquals("aaa303000303", s.change(SettingChange.Level("jbl_case_saver", 4))!!.single().text())
        assertEquals("aaa303000d02", s.change(SettingChange.Toggle("jbl_case_find", false))!!.single().text())
        assertEquals("aaa303" + "0306c1", s.change(SettingChange.Press("jbl_case_saver_remove"))!!.single().text())
        assertNull(row(s, "jbl_case_saver_remove"), "the picture is gone")
    }

    @Test
    fun protocol4UsesTheCaseFeatures() {
        val s = JblSession(model("JBL Tour One M3"))
        // 7942 (the saver, 1), 7954 (a custom picture), 7947 (unlock on), 7951 (find my case playing).
        val records = "061f010001" + "121f010001" + "0b1f01009f" + "0f1f010081"
        s.receive(JblReader().feed(JblP4Packet(JblP4.NOTIFICATION, ("0000" + records).chunked(2).map { it.toInt(16).toByte() }.toByteArray()).bytes()).single())
        assertEquals(Setting.Level("jbl_case_saver", 2, 1, 5), row(s, "jbl_case_saver"))
        assertEquals(Setting.Toggle("jbl_case_unlock", true), row(s, "jbl_case_unlock"))
        assertEquals(Setting.Toggle("jbl_case_find", true), row(s, "jbl_case_find"))
        assertEquals(JblP4Packet.set(7942 to byteArrayOf(3)), s.change(SettingChange.Level("jbl_case_saver", 4))!!.single())
        assertEquals(JblP4Packet.set(7951 to byteArrayOf(2)), s.change(SettingChange.Toggle("jbl_case_find", false))!!.single())
        assertEquals(JblP4Packet.set(7947 to byteArrayOf(0x7F)), s.change(SettingChange.Toggle("jbl_case_unlock", false))!!.single())
        assertEquals(JblP4Packet.set(7955 to byteArrayOf(193.toByte())), s.change(SettingChange.Press("jbl_case_saver_remove"))!!.single())
    }

    @Test
    fun theAirohaDisplayArrayHasThemAtTheirBytes() {
        val s = JblSession(model("JBL Live Buds 3"))
        val d = ByteArray(26) { 0xFF.toByte() }
        d[3] = 0
        d[5] = 1
        d[11] = 0
        d[19] = 2
        s.receive(RacePacket(JblRace.CASE_DISPLAY_GET, byteArrayOf(0) + d, JblRace.TYPE_RESPONSE))
        assertEquals(Setting.Level("jbl_case_saver", 1, 1, 5), row(s, "jbl_case_saver"))
        assertNull(row(s, "jbl_case_unlock"), "the Live Buds 3 have no lock screen card")
        assertEquals(Setting.Toggle("jbl_case_find", false), row(s, "jbl_case_find"))
        val find = s.change(SettingChange.Toggle("jbl_case_find", true))!!.single() as RacePacket
        assertEquals(JblRace.CASE_DISPLAY_SET, find.id)
        assertEquals(1, find.payload[19].toInt())
        assertEquals(0xFF, find.payload[3].toInt() and 0xFF)
        val remove = s.change(SettingChange.Press("jbl_case_saver_remove"))!!.single() as RacePacket
        assertEquals(0x89, remove.id)
    }
}
