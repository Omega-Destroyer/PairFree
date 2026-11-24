package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The noise cancelling and Ambient Aware levels (docs/jbl/PROTOCOL.md section 4.17): command 91's pairs 04 and A1, and the mode features' levels on protocol 4. Not tried on earbuds. */
class JblNoiseLevelTest {
    private fun model(name: String) = JblModels.ALL.first { it.name == name }

    private fun JblFrame.text() = bytes().joinToString("") { "%02x".format(it) }

    private fun row(s: JblSession, key: String) = s.groups.flatMap { it.settings }.firstOrNull { it.key == key }

    @Test
    fun theModelsWhoseCardHasTheSlidersHaveThem() {
        for (name in listOf("JBL Tune Buds 2", "JBL Endurance Peak 4", "JBL Vibe Buds 3")) assertTrue("anc_level" in model(name).extras && "aa_level" in model(name).extras, name)
        assertTrue("aa_level" in model("JBL Vibe Flex 3").extras)
        assertTrue("anc_level" !in model("JBL Vibe Flex 3").extras)
    }

    @Test
    fun harmanLevelsComeFromCommand91AndAreWrittenInTheirMode() {
        val s = JblSession(model("JBL Tune Buds 2"))
        // Noise cancelling on (the shared control), and the advanced reply: manual level 5, Ambient Aware level 2.
        s.receive(JblPacket(JblCommand.NOISE_MODES, byteArrayOf(0x12, 1, 1, 2, 0, 3, 0)))
        s.receive(JblPacket(JblCommand.NOISE_MODES, byteArrayOf(0x22, 4, 5, 0xA1.toByte(), 2)))
        assertEquals(Setting.Level("jbl_anc_level", 5, 0, 7, true), row(s, "jbl_anc_level"))
        assertEquals(Setting.Level("jbl_aa_level", 2, 0, 7, false), row(s, "jbl_aa_level"))
        assertEquals("aa9103200406", s.change(SettingChange.Level("jbl_anc_level", 6))!!.single().text())
        assertNull(s.change(SettingChange.Level("jbl_aa_level", 3)), "Ambient Aware is not on")
    }

    @Test
    fun protocol4WritesTheModesFeatureWithTheStep() {
        val s = JblSession(model("JBL Endurance Peak 4"))
        // Noise cancelling on at level 3, Ambient Aware off at level 1.
        s.receive(JblReader().feed(JblP4Packet(JblP4.NOTIFICATION, byteArrayOf(0, 0, 0x18, 0, 1, 0, 0x83.toByte(), 0x19, 0, 1, 0, 1)).bytes()).single())
        assertEquals(Setting.Level("jbl_anc_level", 3, 0, 7, true), row(s, "jbl_anc_level"))
        assertEquals(JblP4Packet.set(JblP4.FEATURE_ANC to byteArrayOf(0x86.toByte())), s.change(SettingChange.Level("jbl_anc_level", 6))!!.single())
    }

    @Test
    fun airohaLevelsAreRead0901AndWrittenWith0E061E() {
        val s = JblSession(model("JBL Live Buds 3"))
        assertTrue(s.start().contains(RacePacket.mmiGet(7)))
        // The filter (noise cancelling, 1), the level 4 in two bytes; the Ambient Aware module's level 2.
        s.receive(RacePacket(JblRace.MMI_GET, byteArrayOf(5, 0, 0, 1, 4, 0), JblRace.TYPE_RESPONSE))
        s.receive(RacePacket(JblRace.MMI_GET, byteArrayOf(7, 0, 0, 2, 0), JblRace.TYPE_RESPONSE))
        assertEquals(Setting.Level("jbl_anc_level", 4, 0, 7, true), row(s, "jbl_anc_level"))
        assertEquals(Setting.Level("jbl_aa_level", 2, 0, 7, false), row(s, "jbl_aa_level"))
        assertEquals(RacePacket.of(JblRace.ANC, 0, 0x1E, 6, 0), s.change(SettingChange.Level("jbl_anc_level", 6))!!.single())
    }

    @Test
    fun airohaAdaptiveNoiseCancellingIsTheEnvironmentDetectionStatus() {
        val s = JblSession(model("JBL Live Buds 3"))
        assertTrue(s.start().contains(RacePacket(JblRace.ENV_STATUS, byteArrayOf(3, 2))))
        s.receive(RacePacket(JblRace.ENV_STATUS, byteArrayOf(0, 3, 1), JblRace.TYPE_RESPONSE))
        assertEquals(Setting.Toggle("jbl_adaptive_anc", true), row(s, "jbl_adaptive_anc"))
        assertEquals(RacePacket(JblRace.ENV_STATUS, byteArrayOf(3, 0)), s.change(SettingChange.Toggle("jbl_adaptive_anc", false))!!.single())
    }
}
