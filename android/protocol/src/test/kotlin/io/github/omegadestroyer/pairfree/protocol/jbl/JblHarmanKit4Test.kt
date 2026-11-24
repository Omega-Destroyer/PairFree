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
 * The protocol 4 versions of [JblHarmanKit]'s settings (docs/jbl/PROTOCOL.md section 4.12): the `DeviceInfoFeature5` records of `General4Control` for PSAP, the ear tips,
 * the noise and seal checks, the case's clock, screen and pages. The values are built the way `StatusLevelSize1Feature`, `StatusLevelSize2Feature`, `Occupy1ByteEachIntFeature`
 * and `CasePageConfigFeature` write them. No JBL earbuds have answered them.
 */
class JblHarmanKit4Test {
    private fun model(name: String) = JblModels.ALL.first { it.name == name }

    /** A notification as JBL's app reads one: identifier, command, one packet, length, status 0, then the records. */
    private fun reply(records: String): JblP4Packet {
        val bytes = JblP4Packet(JblP4.NOTIFICATION, "0000$records".hexBytes()).bytes()
        return JblReader().feed(bytes).single() as JblP4Packet
    }

    private fun setting(s: JblSession, key: String) = s.groups.flatMap { it.settings }.firstOrNull { it.key == key }

    private fun set(id: Int, vararg value: Int) = JblP4Packet.set(id to ByteArray(value.size) { value[it].toByte() })

    @Test
    fun modelsHaveTheCardsOfTheirDashboard() {
        val m3 = model("JBL Tour One M3")
        for (id in listOf("psap", "case_pages", "case_brightness", "case_notifications", "case_screen_saver", "case_language", "case_wake", "case_clock", "case_info")) assertTrue(id in m3.extras, id)
        assertTrue("fit_test" in model("JBL Live Buds 4").extras)
        assertTrue("ear_tips" in model("JBL Tune Flex 3").extras)
        assertFalse("auracast" in m3.extras, "the Auracast receiver is B0, a Harman frame")
    }

    @Test
    fun theFeaturesAreReadOnConnectingAndTheClockIsSent() {
        val s = JblSession(model("JBL Tour One M3"))
        s.clock = { JblWallClock(2026, 11, 24, 2, 14, 5, 9) }
        val start = s.start()
        val get = start.filterIsInstance<JblP4Packet>().first { it.command == JblP4.GET && it.payload.size > 20 }.payload
        val ids = (get.indices step 2).map { (get[it].toInt() and 0xFF) or ((get[it + 1].toInt() and 0xFF) shl 8) }
        for (id in listOf(3200, 3201, 3202, 3203, 3204, 7940, 7941, 7943, 7946, 7948, 8064, 8079)) assertTrue(id in ids, id.toString())
        // Seconds, minutes, hours, weekday, day, month, the year low byte first, then the format (0).
        assertTrue(set(49, 9, 5, 14, 2, 24, 11, 0xEA, 0x07, 0) in start)
    }

    @Test
    fun psapIsReadAndWrittenAsFeatures() {
        val s = JblSession(model("JBL Tour One M3"))
        // On, balance 50 (the middle), gain 3, noise reduction on, voice focus on at level 1.
        s.receive(reply("800c0100ff" + "810c010032" + "820c010003" + "830c010080" + "840c010081"))
        assertEquals(Setting.Toggle("jbl_psap", true), setting(s, "jbl_psap"))
        assertEquals(Setting.Level("jbl_psap_balance", 5, 0, 10), setting(s, "jbl_psap_balance"))
        assertEquals(Setting.Level("jbl_psap_gain", 3, 0, 5), setting(s, "jbl_psap_gain"))
        assertEquals(Setting.Toggle("jbl_psap_noise", true), setting(s, "jbl_psap_noise"))
        assertEquals(Setting.Choice("jbl_psap_voice", "medium", listOf("off", "medium", "high")), setting(s, "jbl_psap_voice"))
        assertEquals(set(3202, 4), s.change(SettingChange.Level("jbl_psap_gain", 4))!!.single())
        assertEquals(set(3201, 55), s.change(SettingChange.Level("jbl_psap_balance", 10))!!.single())
        assertEquals(set(3200, 0x7F), s.change(SettingChange.Toggle("jbl_psap", false))!!.single())
        assertEquals(set(3203, 0x00), s.change(SettingChange.Toggle("jbl_psap_noise", false))!!.single())
        assertEquals(set(3204, 0x82), s.change(SettingChange.Choice("jbl_psap_voice", "high"))!!.single())
        assertEquals(JblP4Packet.get(listOf(3200)), s.takeReadBacks().first())
    }

    @Test
    fun theCaseScreenAndPages() {
        val s = JblSession(model("JBL Tour One M3"))
        // Brightness 2, notifications on with the permission, 60 seconds, German, quick wake-up on with a double tap, version 1.2.3.
        s.receive(reply("041f010082" + "051f0100c0" + "071f02003c00" + "0a1f010004" + "0c1f010082" + "011f0300010203"))
        assertEquals(Setting.Level("jbl_case_brightness", 2, 0, 3), setting(s, "jbl_case_brightness"))
        assertEquals(Setting.Toggle("jbl_case_notifications", true), setting(s, "jbl_case_notifications"))
        assertEquals(Setting.Level("jbl_case_screen_time", 12, 0, 30), setting(s, "jbl_case_screen_time"))
        assertEquals("german", (setting(s, "jbl_case_language") as Setting.Choice).value)
        assertEquals(Setting.Toggle("jbl_case_wake", true), setting(s, "jbl_case_wake"))
        assertEquals("double_tap", (setting(s, "jbl_case_wake_tap") as Setting.Choice).value)
        assertEquals(Setting.Information("jbl_case_version", "1.2.3"), setting(s, "jbl_case_version"))
        assertEquals(set(7940, 0x83), s.change(SettingChange.Level("jbl_case_brightness", 3))!!.single())
        assertEquals(set(7941, 0x40), s.change(SettingChange.Toggle("jbl_case_notifications", false))!!.single(), "the permission stays")
        assertEquals(set(7943, 0x5A, 0x80), s.change(SettingChange.Level("jbl_case_screen_time", 18))!!.single(), "90 seconds, the status bit on")
        assertEquals(set(7943, 0x96, 0x00), s.change(SettingChange.Level("jbl_case_screen_time", 30))!!.single(), "150 seconds, the status bit off")
        assertEquals(set(7946, 2), s.change(SettingChange.Choice("jbl_case_language", "dutch"))!!.single())
        assertEquals(set(7948, 0x83), s.change(SettingChange.Choice("jbl_case_wake_tap", "triple_tap"))!!.single())
        assertEquals(set(7948, 0x7F), s.change(SettingChange.Toggle("jbl_case_wake", false))!!.single())

        // Two pages: the ambient sound control shown and settable, the equalizer hidden and settable.
        s.receive(reply("801f0100c0" + "811f010040"))
        assertEquals(Setting.Toggle("jbl_case_page_ambient", true, true), setting(s, "jbl_case_page_ambient"))
        assertEquals(Setting.Toggle("jbl_case_page_equalizer", false, true), setting(s, "jbl_case_page_equalizer"))
        assertEquals(set(8065, 0xC0), s.change(SettingChange.Toggle("jbl_case_page_equalizer", true))!!.single())
        assertNull(setting(s, "jbl_case_page_timer"))
    }

    @Test
    fun theNoiseAndSealChecksWriteZeroAndReadBits() {
        val s = JblSession(model("JBL Live Buds 4"))
        assertEquals(set(4100, 0), s.change(SettingChange.Press("jbl_noise_check"))!!.single())
        assertEquals(set(4099, 0), s.change(SettingChange.Press("jbl_seal_check"))!!.single())
        // Noise: left quiet (bit set), right noisy; the seal check: both earbuds sealed.
        s.receive(reply("04100100" + "80" + "03100100" + "c0"))
        assertEquals(Setting.Information("jbl_noise_left", "quiet"), setting(s, "jbl_noise_left"))
        assertEquals(Setting.Information("jbl_noise_right", "noisy"), setting(s, "jbl_noise_right"))
        assertEquals(Setting.Information("jbl_seal_left", "sealed"), setting(s, "jbl_seal_left"))
        assertEquals(Setting.Information("jbl_seal_right", "sealed"), setting(s, "jbl_seal_right"))
        assertEquals(Setting.Action("jbl_noise_check", false), setting(s, "jbl_noise_check"))
    }

    @Test
    fun theEarTipsAreTheStatusBit() {
        val s = JblSession(model("JBL Tune Flex 3"))
        s.receive(reply("0510010080"))
        assertEquals(Setting.Choice("jbl_ear_tips", "sealing", listOf("open", "sealing")), setting(s, "jbl_ear_tips"))
        assertEquals(set(4101, 0x7F), s.change(SettingChange.Choice("jbl_ear_tips", "open"))!!.single())
    }
}
