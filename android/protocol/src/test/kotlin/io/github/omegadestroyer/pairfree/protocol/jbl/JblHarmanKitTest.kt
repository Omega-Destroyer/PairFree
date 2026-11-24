package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.hexBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun JblFrame.hex() = bytes().joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

/**
 * PSAP, the smart case, the clock, the noise check, the ear canal test and the ear tips (docs/jbl/PROTOCOL.md section 4.9): the frames are
 * written out from the command classes of JBL Headphones 5.37.10 (`PSAPCmd`, `ChargingCaseDisplayStatusCmd`, `ChargingCaseFeatureCmd`,
 * `SyncTimeToDeviceCmd`, `EnvironmentNoiseCheckCmd`, `EarCanalTestingCmd`, `TipsTypeCmd`). No JBL earbuds have answered them.
 */
class JblHarmanKitTest {
    private fun model(name: String) = JblModels.ALL.first { it.name == name }

    private fun packet(hex: String): JblPacket = JblParser().feed(hex.hexBytes()).single() as JblPacket

    private fun setting(s: JblSession, key: String) = s.groups.flatMap { it.settings }.firstOrNull { it.key == key }

    @Test
    fun modelsHaveWhatTheirDashboardAndControlClassBothHave() {
        val tourPro2 = model("JBL TOUR PRO 2")
        for (id in listOf("psap", "case_pages", "case_brightness", "case_notifications", "case_screen_saver", "case_clock", "fit_test")) assertTrue(id in tourPro2.extras, id)
        assertFalse("case_language" in tourPro2.extras, "the Tour Pro 2's dashboard has no case language")
        assertTrue("case_language" in model("JBL Live Beam 3").extras)
        assertTrue("ear_tips" in model("JBL Tune Flex 2").extras)
        // Not on a model with the setting's card on another transport, or with no such control class.
        assertFalse("psap" in model("JBL Live Buds 3").extras)
        assertFalse("psap" in model("JBL Tune 520BT").extras)
    }

    @Test
    fun theReadsAreRequestedOnConnectingAndTheClockIsSentOnce() {
        val s = JblSession(model("JBL TOUR PRO 2"))
        s.clock = { JblWallClock(2026, 11, 23, 1, 14, 5, 9) }
        val start = s.start().map { it.hex() }
        for (read in listOf("aaa00101", "aaa30101", "aaa40101")) assertTrue(read in start, read)
        // Hour, minute, second, weekday (0 Sunday), day, month and the year low byte first: SyncTimeToDeviceCmd.toBytesSet.
        assertTrue("aaa70a00010e050901170bea07" in start, start.toString())
    }

    @Test
    fun psapReadsItsThreeValuesAndWritesThemTogether() {
        val s = JblSession(model("JBL TOUR PRO 2"))
        s.receive(packet("aaa00702010102660302"))
        assertEquals(Setting.Toggle("jbl_psap", true), setting(s, "jbl_psap"))
        assertEquals(Setting.Level("jbl_psap_balance", 7, 0, 10), setting(s, "jbl_psap_balance"))
        assertEquals(Setting.Level("jbl_psap_gain", 2, 0, 5), setting(s, "jbl_psap_gain"))
        assertEquals("aaa00700010102660304", s.change(SettingChange.Level("jbl_psap_gain", 4))!!.single().hex())
        assertEquals("aaa00700010102690304", s.change(SettingChange.Level("jbl_psap_balance", 10))!!.single().hex())
        assertEquals("aaa00700010002690304", s.change(SettingChange.Toggle("jbl_psap", false))!!.single().hex())
        assertEquals("aaa00101", s.takeReadBacks().last().hex())
    }

    @Test
    fun theShortPsapReplyHoldsTheOnByteAndTheBalance() {
        val s = JblSession(model("JBL Live 670NC"))
        s.receive(packet("aaa003020164"))
        assertEquals(Setting.Toggle("jbl_psap", true), setting(s, "jbl_psap"))
        assertEquals(Setting.Level("jbl_psap_balance", 5, 0, 10), setting(s, "jbl_psap_balance"))
    }

    @Test
    fun theCaseScreenIsReadAndWrittenByIds() {
        val s = JblSession(model("JBL TOUR PRO 2"))
        // Brightness 2, notifications on, 60 seconds, no preview, and the case's version 1.2.3.
        s.receive(packet("aaa30e02" + "0102" + "0201" + "043c00" + "0500" + "0c010203"))
        assertEquals(Setting.Level("jbl_case_brightness", 2, 0, 3), setting(s, "jbl_case_brightness"))
        assertEquals(Setting.Toggle("jbl_case_notifications", true), setting(s, "jbl_case_notifications"))
        assertEquals(Setting.Toggle("jbl_case_preview", false), setting(s, "jbl_case_preview"))
        assertEquals(Setting.Level("jbl_case_screen_time", 12, 0, 30), setting(s, "jbl_case_screen_time"))
        assertEquals("aaa303000103", s.change(SettingChange.Level("jbl_case_brightness", 3))!!.single().hex())
        assertEquals("aaa303000200", s.change(SettingChange.Toggle("jbl_case_notifications", false))!!.single().hex())
        assertEquals("aaa30400045a00", s.change(SettingChange.Level("jbl_case_screen_time", 18))!!.single().hex())
        assertEquals("aaa303000501", s.change(SettingChange.Toggle("jbl_case_preview", true))!!.single().hex())
        assertNull(setting(s, "jbl_case_language"), "the Tour Pro 2 has no case language")
    }

    @Test
    fun theCaseLanguageAndQuickWakeUpOfTheLiveBeam3() {
        val s = JblSession(model("JBL Live Beam 3"))
        // German (4) and quick wake-up on with a double tap (0x80 and 2), the case's software 1.0.5 and the earbuds' latest 1.2.3.
        s.receive(packet("aaa30902" + "0804" + "0a82" + "0b010203"))
        assertEquals("german", (setting(s, "jbl_case_language") as Setting.Choice).value)
        assertEquals(Setting.Toggle("jbl_case_wake", true), setting(s, "jbl_case_wake"))
        assertEquals("double_tap", (setting(s, "jbl_case_wake_tap") as Setting.Choice).value)
        assertEquals("aaa303000802", s.change(SettingChange.Choice("jbl_case_language", "dutch"))!!.single().hex())
        assertEquals("aaa303000a83", s.change(SettingChange.Choice("jbl_case_wake_tap", "triple_tap"))!!.single().hex())
        assertEquals("aaa303000a00", s.change(SettingChange.Toggle("jbl_case_wake", false))!!.single().hex())
        assertNull(s.change(SettingChange.Choice("jbl_case_language", "klingon")))
    }

    @Test
    fun theCasePagesAreSwitchedByTheirIds() {
        val s = JblSession(model("JBL TOUR PRO 2"))
        s.receive(packet("aaa409" + "02" + "0101" + "0200" + "c0010203"))
        assertEquals(Setting.Toggle("jbl_case_page_ambient", true), setting(s, "jbl_case_page_ambient"))
        assertEquals(Setting.Toggle("jbl_case_page_equalizer", false), setting(s, "jbl_case_page_equalizer"))
        assertNull(setting(s, "jbl_case_page_timer"), "a page the case did not report")
        assertEquals("aaa403000201", s.change(SettingChange.Toggle("jbl_case_page_equalizer", true))!!.single().hex())
        assertNull(s.change(SettingChange.Toggle("jbl_case_page_timer", true)))
    }

    @Test
    fun theNoiseCheckAndTheEarCanalTestAreStartedAndAnswered() {
        val s = JblSession(model("JBL TOUR PRO 2"))
        assertEquals("aa990101", s.change(SettingChange.Press("jbl_noise_check"))!!.single().hex())
        assertEquals("aa9a0101", s.change(SettingChange.Press("jbl_canal_test"))!!.single().hex())
        assertEquals(Setting.Action("jbl_canal_test", true), setting(s, "jbl_canal_test"))
        // 54 on the left and 65 on the right, two bytes each, low first.
        s.receive(packet("aa990502" + "3600" + "4100"))
        assertEquals(Setting.Information("jbl_fit_noise", "54 / 65"), setting(s, "jbl_fit_noise"))
        s.receive(packet("aa9a020203"))
        assertEquals(Setting.Information("jbl_canal_result", "done"), setting(s, "jbl_canal_result"))
        assertEquals(Setting.Action("jbl_canal_test", false), setting(s, "jbl_canal_test"))
        s.receive(packet("aa9a020202"))
        assertEquals(Setting.Information("jbl_canal_result", "failed"), setting(s, "jbl_canal_result"))
    }

    @Test
    fun theSealCheckAsksForStatusTypeFortyTwo() {
        val s = JblSession(model("JBL Live Beam 3"))
        assertEquals("aa210142", s.change(SettingChange.Press("jbl_seal_check"))!!.single().hex())
        assertEquals(Setting.Action("jbl_seal_check", true), setting(s, "jbl_seal_check"))
        // The left earbud is sealed, the right is not (HandleParse.parseDevStatus, status `42`).
        s.receive(packet("aa22034201" + "00"))
        assertEquals(Setting.Information("jbl_seal_left", "sealed"), setting(s, "jbl_seal_left"))
        assertEquals(Setting.Information("jbl_seal_right", "not_sealed"), setting(s, "jbl_seal_right"))
        assertEquals(Setting.Action("jbl_seal_check", false), setting(s, "jbl_seal_check"))
    }

    @Test
    fun theEarbudsScanForAuracastBroadcastsAndListenToOne() {
        val s = JblSession(model("JBL TOUR PRO 2"))
        assertTrue("aab00100" in s.start().map { it.hex() }, "what they receive is asked on connecting")
        // Not receiving (status 1): the rows are there once the earbuds have answered.
        s.receive(packet("aab0021001"))
        assertEquals(Setting.Action("jbl_auracast_scan", false), setting(s, "jbl_auracast_scan"))
        assertEquals("aab00301011e", s.change(SettingChange.Press("jbl_auracast_scan"))!!.single().hex())
        assertEquals(Setting.Action("jbl_auracast_scan", true), setting(s, "jbl_auracast_scan"))
        // One source: 11:22:33:44:55:66, "Gate", a password needed.
        val value = "0106" + "112233445566" + "0204" + "47617465" + "040101"
        s.receive(packet("aab015" + "1100" + "0011" + value))
        assertEquals(Setting.Choice("jbl_auracast_add", null, listOf("Gate")), setting(s, "jbl_auracast_add"))
        assertEquals(Setting.Text("jbl_auracast_code", "", 16), setting(s, "jbl_auracast_code"))
        // The password goes after the six address bytes, which go back in the reverse order.
        assertEquals(emptyList<JblFrame>(), s.change(SettingChange.Choice("jbl_auracast_code", "abc")))
        assertEquals("aab00b" + "2001" + "665544332211" + "616263", s.change(SettingChange.Choice("jbl_auracast_add", "Gate"))!!.single().hex())
        assertEquals("aab00100", s.takeReadBacks().last().hex())
        // Receiving it, with one sub-group on in high quality.
        val sub = "200100" + "210101" + "230102"
        s.receive(packet("aab020" + "1000" + "0011" + value.replace("040101", "040101") + "0109" + sub))
        assertEquals(Setting.Information("jbl_auracast_source", "Gate"), setting(s, "jbl_auracast_source"))
        assertEquals(Setting.Action("jbl_auracast_stop", false), setting(s, "jbl_auracast_stop"))
        assertEquals("aab0022100", s.change(SettingChange.Press("jbl_auracast_stop"))!!.single().hex())
        s.receive(packet("aab0021001"))
        assertNull(setting(s, "jbl_auracast_source"), "nothing is received after the earbuds say so")
    }

    @Test
    fun theEarTipsAreReadAndWritten() {
        val s = JblSession(model("JBL Tune Flex 2"))
        assertTrue("aa9c0101" in s.start().map { it.hex() })
        s.receive(packet("aa9c020201"))
        assertEquals(Setting.Choice("jbl_ear_tips", "sealing", listOf("open", "sealing")), setting(s, "jbl_ear_tips"))
        assertEquals("aa9c020000", s.change(SettingChange.Choice("jbl_ear_tips", "open"))!!.single().hex())
    }

    @Test
    fun noneOfItsCommandsIsOnTheNeverSentList() {
        for (c in listOf(JblCommand.PSAP, JblCommand.CHARGING_CASE_DISPLAY, JblCommand.CHARGING_CASE_FEATURE, JblCommand.SYNC_TIME, JblCommand.EAR_CANAL_TEST, JblCommand.ENVIRONMENT_NOISE, JblCommand.EAR_TIPS)) {
            assertFalse(c in JblCommand.NEVER_SEND, c.toString(16))
        }
    }
}
