package io.github.omegadestroyer.pairfree.protocol.sennheiser

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

private fun String.bytes() = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }

/**
 * Sennheiser's equalizers (docs/sennheiser/PROTOCOL.md section 6). The expected numbers come from a line-by-line port of Smart Control 4.9.4's JavaScript
 * (`Eq5BandGen1`, `Eq3Band`) run outside the repo, with the same IEEE arithmetic; the rest builds the earbuds' replies as the app reads them. Not tried on earbuds.
 */
class SennheiserEqTest {
    private val m4 = SennheiserEqTables.GEN1.getValue("momentum4")

    private fun reply(word: Int, payload: String) = Gaia3Packet(0x0495, word + 0x100, payload.bytes()).bytes()
    private fun words(frames: List<ByteArray>) = frames.map { f -> Gaia3Parser().feed(f).single().let { "%04x:%s".format(it.word, it.payload.joinToString("") { b -> "%02x".format(b) }) } }
    private fun model(name: String) = SennheiserModels.ALL.first { it.name == name }

    @Test
    fun theShelfSetIsWhatTheAppCalculates() {
        val flat = SennheiserEq.shelfConfig(List(5) { 0.0 }, m4, false)
        assertEquals(0.0, flat.levelShift)
        assertEquals(listOf(17, 257, 0, 272, 11, 273, 360, 274, 0, 275, 2896, 288, 11, 289, 1200, 290, 0, 291, 2896, 304, 11, 305, 9000, 306, 0, 307, 2896, 320, 11, 321, 18000, 322, 0, 323, 2896, 0), SennheiserEq.shelfParams(flat, m4))
        val gains = listOf(1.0, -2.0, 3.0, 0.0, -1.5)
        val curve = SennheiserEq.shelfConfig(gains, m4, false)
        assertEquals(-0.9, curve.levelShift)
        assertEquals(listOf(17, 257, 5, 272, 11, 273, 360, 274, 65356, 275, 2896, 288, 11, 289, 1200, 290, 300, 291, 2896, 304, 11, 305, 9000, 306, 65356, 307, 2896, 320, 11, 321, 18000, 322, 65446, 323, 2896, 0), SennheiserEq.shelfParams(curve, m4))
        val bass = SennheiserEq.shelfConfig(gains, m4, true)
        assertEquals(-1.3, bass.levelShift)
        assertEquals(282, SennheiserEq.shelfParams(bass, m4)[2])
        assertEquals(65447, SennheiserEq.shelfParams(bass, m4)[32])
    }

    @Test
    fun theShelfSetReadsBackAsTheGainsThatMadeIt() {
        for (bass in listOf(false, true)) {
            val gains = listOf(1.0, -2.0, 3.0, 0.0, -1.5)
            val c = SennheiserEq.shelfConfig(gains, m4, bass)
            val params = SennheiserEq.shelfParams(c, m4)
            // The answer to the refresh read: the count, then the address and the value of each band, signed.
            val reply = listOf(5, 257, params[2].toShort().toInt(), 274, params[8].toShort().toInt(), 290, params[16].toShort().toInt(), 306, params[24].toShort().toInt(), 322, params[32].toShort().toInt())
            val (back, flag) = SennheiserEq.shelfGains(c.levelShift, SennheiserEq.shelfKnobY(bass), reply, m4)!!
            assertEquals(bass, flag)
            gains.zip(back).forEach { (a, b) -> assertTrue(abs(a - b) <= 0.1, "$a vs $b with bass $bass") }
        }
    }

    @Test
    fun theThreeKnobsAreWhatTheAppCalculates() {
        val smooth = SennheiserEq.threeSmooth(listOf(2.0, -1.0, 3.5), 4)
        assertEquals(listOf(0.5, -2.5, 2.0), smooth)
        assertEquals(listOf(9, 257, 65386, 272, 10, 273, 840, 274, 180, 275, 2896, 288, 11, 289, 4800, 290, 270, 291, 2896, 0), SennheiserEq.threeParams(smooth, false))
        val (x, y) = SennheiserEq.threeKnobs(listOf(2.0, -1.0, 3.5), true)
        val back = SennheiserEq.threeFromKnobs(x, y)!!
        assertTrue(back.second)
        listOf(2.0, -1.0, 3.5).zip(back.first).forEach { (a, b) -> assertTrue(abs(a - b) <= 0.1, "$a vs $b") }
        assertNull(SennheiserEq.threeFromKnobs(0, 0), "an older storage is not read as gains")
    }

    @Test
    fun theNewestEqualizerIsReadBandByBandAndWrittenAsTenths() {
        val s = SennheiserSession(model("Momentum 4 Wireless"))
        val start = words(s.start())
        assertTrue("1000:" in start, "the configuration is asked for: $start")
        assertTrue("1009:" in start, "the sub-mode is asked for")
        assertNull(s.eqKind)
        s.feed(reply(0x1000, "05c43c0000"))
        assertEquals(SennheiserEqKind.GEN2, s.eqKind)
        val asks = words(s.takeOutbox())
        assertEquals(listOf("1002:00", "1002:01", "1002:02", "1002:03", "1002:04"), asks)
        s.feed(reply(0x1009, "00"))
        for ((i, g) in listOf("0a", "00", "fb", "14", "ec").withIndex()) s.feed(reply(0x1002, g))
        assertTrue(s.eqKnown)
        assertEquals(listOf(1.0, 0.0, -0.5, 2.0, -2.0), s.eqGains)
        assertEquals(listOf(63, 250, 1000, 4000, 8000), s.eqBandsHz)
        assertEquals(6.0, s.eqMaxDb)
        // The audio mode was not read here, so no mode change goes with the curve.
        val sent = words(s.eqCurve(listOf(0.5, 0.0, 0.0, 1.5, -6.5))!!)
        assertEquals(listOf("1008:00", "1001:0005", "1001:0100", "1001:0200", "1001:030f", "1001:04c4"), sent)
        assertEquals(-6.0, s.eqGains!![4], "a gain is held to the earbuds' own range")
        assertNull(s.eqCurve(listOf(0.0)), "a curve of the wrong number of bands is not sent")
        val bass = (s.groups.single { it.key == "sound" }.settings.single() as Setting.Toggle)
        assertTrue(!bass.on)
        assertEquals(listOf("1008:01"), words(s.change(SettingChange.Toggle(SennheiserSession.KEY_BASS_BOOST, true))!!))
    }

    @Test
    fun theAudioModeIsTurnedToTheUserEqualizerWithACurve() {
        val s = SennheiserSession(model("Momentum 4 Wireless"))
        val start = s.start()
        val mode = SennheiserCommands.ALL.first { it.id == SennheiserCommands.AUDIO_MODE }
        assertTrue(words(start).any { it.startsWith("%04x:".format(mode.get)) })
        s.feed(reply(mode.get!!, "0000"))
        s.feed(reply(0x1000, "05c43c0000"))
        s.takeOutbox()
        for (g in List(5) { "00" }) s.feed(reply(0x1002, g))
        val sent = words(s.eqCurve(List(5) { 0.0 })!!)
        assertEquals("%04x:0001".format(mode.set), sent.first())
        // Once it is on, a second curve does not write it again.
        assertTrue(words(s.eqCurve(List(5) { 1.0 })!!).none { it.startsWith("%04x:".format(mode.set)) })
    }

    private fun firmware(s: SennheiserSession, major: Int, minor: Int, patch: Int) {
        val c = SennheiserCommands.ALL.first { it.id == SennheiserCommands.FIRMWARE_VERSION }
        s.feed(reply(c.get!!, "%04x%04x%04x".format(major, minor, patch)))
    }

    @Test
    fun theFiveBandShelfEqualizerIsReadAndWrittenInTheAppsOwnNumbers() {
        // The Momentum True Wireless 3 has no user-eq-2: from firmware 2.10.5 the app uses the shelf set.
        val s = SennheiserSession(model("Momentum True Wireless 3"))
        val start = words(s.start())
        assertTrue("1005:0001" in start, "the knob's Y value is the first read: $start")
        firmware(s, 2, 11, 0)
        s.feed(reply(0x1005, "4000"))
        assertEquals(SennheiserEqKind.GEN1, s.eqKind)
        val asks = words(s.takeOutbox())
        assertEquals(listOf("1007:", "1003:00050101000001120000012200000132000001420000"), asks)
        s.feed(reply(0x1007, "f7"))
        s.feed(reply(0x1003, "0005010100050112ff4c0122012c0132ff4c0142ffa6"))
        assertTrue(s.eqKnown)
        listOf(1.0, -2.0, 3.0, 0.0, -1.5).zip(s.eqGains!!).forEach { (a, b) -> assertTrue(abs(a - b) <= 0.1, "$a vs $b") }
        assertEquals(listOf(63, 250, 1000, 4000, 8000), s.eqBandsHz)
        // The curve goes as the shelf parameters, the level shift in tenths of a dB and the knob's flags.
        val c = SennheiserEq.shelfConfig(listOf(1.0, -2.0, 3.0, 0.0, -1.5), SennheiserEqTables.GEN1.getValue("mtw3"), false)
        val sent = words(s.eqCurve(listOf(1.0, -2.0, 3.0, 0.0, -1.5))!!)
        assertEquals(3, sent.size)
        assertEquals("1002:" + SennheiserEq.shelfParams(c, SennheiserEqTables.GEN1.getValue("mtw3")).joinToString("") { "%04x".format(it) }, sent[0])
        assertEquals("1006:%02x".format((10 * c.levelShift).toInt() and 0xFF), sent[1])
        assertEquals("1004:00014000", sent[2])
    }

    @Test
    fun theOlderFirmwareKeepsTheThreeKnobs() {
        val s = SennheiserSession(model("CX 200 True Wireless"))
        assertTrue("1005:0001" in words(s.start()))
        val (x, y) = SennheiserEq.threeKnobs(listOf(2.0, -1.0, 3.5), true)
        s.feed(reply(0x1005, "%04x".format(y)))
        assertEquals(SennheiserEqKind.THREE_BAND, s.eqKind)
        assertEquals(listOf("1005:0000"), words(s.takeOutbox()))
        s.feed(reply(0x1005, "%04x".format(x)))
        assertTrue(s.eqKnown)
        assertTrue(s.eqBass)
        assertEquals(listOf(50, 1000, 8000), s.eqBandsHz)
        assertEquals(listOf("Rock", "Pop", "Dance", "Hip hop", "Classical", "Movie"), s.eqPresets.map { it.first })
        val sent = words(s.eqCurve(listOf(2.0, -1.0, 3.5))!!)
        assertEquals(3, sent.size)
        assertTrue(sent[0].startsWith("1002:0009"))
        assertEquals("1004:0000%04x".format(x), sent[1])
        assertEquals("1004:0001%04x".format(y), sent[2])
    }

    @Test
    fun aModelWithoutAnEqualizerAsksForNone() {
        // the Plus-only products that Smart Control Plus's feature layer covers are asked for the second-generation equalizer (issue #47); the others none
        assertTrue(words(SennheiserSession(model("Momentum 5 Wireless")).start()).any { it.startsWith("1000") })
        assertTrue(words(SennheiserSession(model("Momentum True Wireless 5")).start()).any { it.startsWith("1000") })
        assertTrue(words(SennheiserSession(model("HDB 630")).start()).none { it.startsWith("10") })
        assertTrue(words(SennheiserSession(model("HDR 275 / 255")).start()).none { it.startsWith("10") })
        assertTrue(words(SennheiserSession(model("CX Sport True Wireless")).start()).none { it.startsWith("10") })
        assertNotNull(model("CX Sport True Wireless").eq)
    }
}
