package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.hexBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Airoha models' PSAP, smart case and clock (docs/jbl/PROTOCOL.md section 4.13): RACE commands `0077` to `0082` and `0090` of JBL's Airoha adapter. The replies are written out
 * the way `MmiStage_GetPsapStatus` and the case stages read them (the status byte, then the values). No JBL earbuds have answered them.
 */
class JblHarmanKitRaceTest {
    private fun model(name: String) = JblModels.ALL.first { it.name == name }

    private fun packet(hex: String): RacePacket = JblReader(race = true).feed(hex.hexBytes()).single() as RacePacket

    private fun setting(s: JblSession, key: String) = s.groups.flatMap { it.settings }.firstOrNull { it.key == key }

    private fun ffs(n: Int) = "ff".repeat(n)

    @Test
    fun theAiroha3ModelsHaveTheCardsOfTheirDashboard() {
        for (name in listOf("JBL Live Buds 3", "JBL Tour Pro 3")) {
            val m = model(name)
            for (id in listOf("psap", "case_pages", "case_brightness", "case_clock")) assertTrue(id in m.extras, "$name $id")
        }
        assertFalse("psap" in model("JBL LIVE FREE 2 TWS").extras)
    }

    @Test
    fun theReadsAndTheClockGoOutOnConnecting() {
        val s = JblSession(model("JBL Live Buds 3"))
        s.clock = { JblWallClock(2026, 11, 24, 2, 14, 5, 9) }
        val start = s.start()
        for (id in listOf(0x77, 0x79, 0x81)) assertTrue(RacePacket(id) in start, id.toString(16))
        // The Harman frame A7 (hour, minute, second, weekday, day, month, the year low byte first), passed through 0093 as the app writes it.
        assertTrue(RacePacket(0x93, "aaa70a00010e050902180bea07".hexBytes()) in start, start.toString())
        assertTrue(s.allowed(RacePacket(0x93, "aaa70a00010e050902180bea07".hexBytes())))
        assertFalse(s.allowed(RacePacket(0x93, "aa950101".hexBytes())), "any other passed-through frame stays never sent")
        assertFalse(s.allowed(RacePacket(0x83, byteArrayOf(1))), "the charging case action (firmware update) stays never sent")
    }

    @Test
    fun psapIsThreeBytes() {
        val s = JblSession(model("JBL Live Buds 3"))
        // On, balance 102, gain step 3.
        s.receive(packet("055b0600" + "7700" + "00" + "016603"))
        assertEquals(Setting.Toggle("jbl_psap", true), setting(s, "jbl_psap"))
        assertEquals(Setting.Level("jbl_psap_balance", 7, 0, 10), setting(s, "jbl_psap_balance"))
        assertEquals(Setting.Level("jbl_psap_gain", 3, 0, 5), setting(s, "jbl_psap_gain"))
        assertEquals(RacePacket(0x78, byteArrayOf(1, 0x66, 4)), s.change(SettingChange.Level("jbl_psap_gain", 4))!!.single())
        assertEquals(RacePacket(0x78, byteArrayOf(1, 0x69, 4)), s.change(SettingChange.Level("jbl_psap_balance", 10))!!.single())
        assertEquals(RacePacket(0x78, byteArrayOf(0, 0x69, 4)), s.change(SettingChange.Toggle("jbl_psap", false))!!.single())
        assertEquals(RacePacket(0x77), s.takeReadBacks().first())
    }

    @Test
    fun theCaseScreenIsA26ByteStructWithFFWhereNothingChanges() {
        val s = JblSession(model("JBL Live Buds 3"))
        // Brightness 2, notifications on, no preview, 60 seconds, German, case versions, quick wake-up on with a double tap.
        val data = "02" + "01" + "00" + "00" + "3c" + "00" + "00000000" + "04" + "00" + "ff" + "010203" + "040506" + "ff" + "ffffffff" + "01" + "02"
        s.receive(packet("055b1d00" + "7900" + "00" + data))
        assertEquals(Setting.Level("jbl_case_brightness", 2, 0, 3), setting(s, "jbl_case_brightness"))
        assertEquals(Setting.Toggle("jbl_case_notifications", true), setting(s, "jbl_case_notifications"))
        assertEquals(Setting.Toggle("jbl_case_preview", false), setting(s, "jbl_case_preview"))
        assertEquals(Setting.Level("jbl_case_screen_time", 12, 0, 30), setting(s, "jbl_case_screen_time"))
        assertEquals("german", (setting(s, "jbl_case_language") as Setting.Choice).value)
        assertEquals("double_tap", (setting(s, "jbl_case_wake_tap") as Setting.Choice).value)
        assertEquals(Setting.Information("jbl_case_latest", "4.5.6"), setting(s, "jbl_case_latest"))
        fun frame(vararg set: Pair<Int, Int>): RacePacket {
            val b = ByteArray(26) { 0xFF.toByte() }
            for ((at, v) in set) b[at] = v.toByte()
            return RacePacket(0x80, b)
        }
        assertEquals(frame(0 to 3), s.change(SettingChange.Level("jbl_case_brightness", 3))!!.single())
        assertEquals(frame(1 to 0), s.change(SettingChange.Toggle("jbl_case_notifications", false))!!.single())
        assertEquals(frame(4 to 90), s.change(SettingChange.Level("jbl_case_screen_time", 18))!!.single())
        assertEquals(frame(10 to 2), s.change(SettingChange.Choice("jbl_case_language", "dutch"))!!.single())
        assertEquals(frame(24 to 1, 25 to 3), s.change(SettingChange.Choice("jbl_case_wake_tap", "triple_tap"))!!.single())
        assertNull(s.change(SettingChange.Choice("jbl_case_language", "klingon")))
    }

    @Test
    fun theCasePagesAreA27ByteStructThatKeepsTheCasesVersionAndAddress() {
        val s = JblSession(model("JBL Tour Pro 3"))
        // Ambient shown, equalizer hidden, the timer shown; the case's version 1.0.2 and its address.
        val data = "01" + "00" + ffs(7) + "010002" + "aabbccddeeff" + "ff" + "01" + ffs(7)
        assertEquals(27, data.length / 2)
        s.receive(packet("055b1e00" + "8100" + "00" + data))
        assertEquals(Setting.Toggle("jbl_case_page_ambient", true), setting(s, "jbl_case_page_ambient"))
        assertEquals(Setting.Toggle("jbl_case_page_equalizer", false), setting(s, "jbl_case_page_equalizer"))
        assertEquals(Setting.Toggle("jbl_case_page_timer", true), setting(s, "jbl_case_page_timer"))
        assertNull(setting(s, "jbl_case_page_flashlight"), "a page the case did not report")
        val out = ByteArray(27) { 0xFF.toByte() }
        "010002aabbccddeeff".hexBytes().copyInto(out, 9)
        out[1] = 1
        assertEquals(RacePacket(0x82, out), s.change(SettingChange.Toggle("jbl_case_page_equalizer", true))!!.single())
    }

    @Test
    fun theNoiseCheckReadsTheDetectionAndTheSealCheckTheLeakageIndication() {
        val s = JblSession(model("JBL Live Buds 3"))
        assertTrue("fit_test" in model("JBL Live Buds 3").extras)
        // The status read (type 3, action 2), then the information (type 3), on the id the AB1562 does not have.
        assertEquals(listOf(RacePacket(0x0E17, byteArrayOf(3, 2)), RacePacket(0x0E15, byteArrayOf(3))), s.change(SettingChange.Press("jbl_noise_check")))
        // The agent's noise -5000, the partner's 0: the Live Buds 3's line is (noise x 45 + 1133800) / 11210 - 9, 72 and 92.
        s.receive(packet("055b0d00" + "150e" + "00" + "03" + "0000" + "0000" + "01" + "78ec" + "0000"))
        assertEquals(Setting.Information("jbl_fit_noise", "72 / 92"), setting(s, "jbl_fit_noise"))
        assertEquals(listOf(RacePacket(0x0E13, byteArrayOf(0, 1))), s.change(SettingChange.Press("jbl_seal_check")))
        // The indication: the mode 0 and, at 12 and 13, the first earbud sealed (0) and the second not (1).
        s.receive(packet("055d1100" + "130e" + "00" + "00" + "00".repeat(11) + "00" + "01"))
        assertEquals(Setting.Information("jbl_seal_left", "sealed"), setting(s, "jbl_seal_left"))
        assertEquals(Setting.Information("jbl_seal_right", "not_sealed"), setting(s, "jbl_seal_right"))
    }
}
