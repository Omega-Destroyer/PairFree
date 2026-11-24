package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * PersoniFi's hearing test as a session runs it (docs/jbl/PROTOCOL.md section 4.14): the order of the tones and the extra ones, the A1 and protocol 4 messages around
 * them, and the curve's write as category 201. The curve itself is checked against the library in [JblPersonifiTest]. No JBL earbuds have answered any of this.
 */
class JblPersonifiRunTest {
    private fun model(name: String) = JblModels.ALL.first { it.name == name }

    private fun JblFrame.text() = bytes().joinToString("") { "%02x".format(it) }

    @Test
    fun theTonesComeInTheAppsOrderLeftEarFirst() {
        val run = JblPersonifiRun()
        assertEquals(18, run.tones.size)
        assertEquals(listOf(1000, 500, 250, 2000, 4000, 6000, 8000, 10000, 12000), run.tones.take(9).map { it.hz })
        assertTrue(run.tones.take(9).all { it.left })
        assertTrue(run.tones.drop(9).none { it.left })
        assertFalse(run.finished)
        assertNull(run.curve())
    }

    @Test
    fun anExtraToneFollowsAResultMoreThanTwentyDecibelsAway() {
        val run = JblPersonifiRun()
        run.heard(1) // 1 kHz, left: 40 dB
        run.heard(14) // 500 Hz: 1 dB, 39 away from the 1 kHz result, so 750 Hz follows
        assertEquals(750, run.current!!.hz)
        assertTrue(run.current!!.left)
        run.heard(1)
        assertEquals(250, run.current!!.hz)
        run.heard(1)
        run.heard(1) // 2 kHz at 40 dB as 1 kHz: no 1.5 kHz
        assertEquals(4000, run.current!!.hz)
        run.heard(10) // 4 kHz at 13 dB: 27 from the 2 kHz result, so 3 kHz follows
        assertEquals(3000, run.current!!.hz)
        assertEquals(20, run.tones.size)
        // Going back takes the extra tone away again.
        run.back()
        assertEquals(4000, run.current!!.hz)
        assertEquals(19, run.tones.size)
    }

    @Test
    fun theEndOfTheTestGivesTheCurveTheLibraryWorksOut() {
        val run = JblPersonifiRun()
        while (!run.finished) run.heard(5) // 28 dB everywhere: no extra tones
        assertEquals(18, run.tones.size)
        val curve = run.curve()!!
        val direct = JblPersonifi.curve(JblPersonifi.TEST_FREQS.associateWith { 28f }, JblPersonifi.TEST_FREQS.associateWith { 28f })
        assertEquals(direct.master, curve.master, 0.0)
        assertEquals(direct.left.map { it.type }, curve.left.map { it.type })
        val eq = curve.eq()
        assertEquals(JblEqWire.PERSONIFI, eq.category)
        assertEquals(24, eq.bands.size)
        assertEquals(0f, eq.calibration)
        assertEquals(curve.master.toFloat(), eq.leftGain)
        assertEquals(curve.master.toFloat(), eq.rightGain)
    }

    @Test
    fun theOpenFitModelsLeaveOutTheLowestTone() {
        val levels = JblPersonifi.TEST_FREQS.associateWith { 30f }
        val open = JblPersonifi.curve(levels, levels, openFit = true)
        val closed = JblPersonifi.curve(levels, levels)
        assertFalse(open.left.map { it.type } == closed.left.map { it.type } && open.left.map { it.gain } == closed.left.map { it.gain })
    }

    @Test
    fun modelsHaveTheTestWhereTheirEqCanTakeIt() {
        for (name in listOf("JBL LIVE FLEX", "JBL Tour One M3", "JBL Tune Beam 2")) assertTrue("personifi" in model(name).extras, name)
        assertTrue("personifi_open" in model("JBL Sense Pro").extras)
        assertTrue("personifi_open" in model("JBL Tune Flex 3").extras)
        assertFalse("personifi_open" in model("JBL LIVE FLEX").extras)
    }

    @Test
    fun theHarmanModelsSendA1() {
        val s = JblSession(model("JBL LIVE FLEX"))
        assertTrue(s.personifiTest)
        assertTrue(s.start().any { it is JblPacket && it.command == JblCommand.PERSONIFI && it.sub == 1 })
        s.personifiBegin()
        assertEquals(listOf("aaa10500" + "0201" + "0301"), s.takeEqWrite().map { it.text() })
        s.personifiTone(left = true, hz = 1000)
        assertEquals(listOf("aaa10800" + "0501" + "06e8030000"), s.takeEqWrite().map { it.text() })
        s.personifiTone(left = false, hz = 12000)
        assertEquals(listOf("aaa10800" + "0502" + "06e02e0000"), s.takeEqWrite().map { it.text() })
        s.personifiEnd()
        assertEquals(listOf("aaa10500" + "0200" + "0300"), s.takeEqWrite().map { it.text() })
    }

    @Test
    fun theSwitchIsReadFromTheReplyAndWrittenAsId1() {
        val s = JblSession(model("JBL LIVE FLEX"))
        s.receive(JblPacket(JblCommand.PERSONIFI, byteArrayOf(2, 1, 1, 2, 0, 3, 0, 5, 1, 6, 0xE8.toByte(), 3, 0, 0)))
        val row = s.groups.flatMap { it.settings }.first { it.key == "jbl_personifi" }
        assertEquals(Setting.Toggle("jbl_personifi", true), row)
        assertEquals("aaa10300" + "0100", s.change(SettingChange.Toggle("jbl_personifi", false))!!.single().text())
    }

    @Test
    fun theCurveGoesAsCategory201WithTwentyFourBands() {
        val levels = JblPersonifi.TEST_FREQS.associateWith { 28f }
        val curve = JblPersonifi.curve(levels, levels)
        val s = JblSession(model("JBL LIVE FLEX")) // Q in tenths
        assertTrue(s.personifiApply(curve))
        val frames = s.takeEqWrite()
        val write = frames.first() as JblPacket
        assertEquals(JblCommand.EQ, write.command)
        val parsed = JblEq.parse(write.payload.also { it[0] = 4 })
        assertNotNull(parsed)
        assertEquals(24, parsed!!.bands.size)
        assertEquals(JblEqWire.PERSONIFI, parsed.category)
        // PersoniFi is switched on after the curve.
        assertEquals("aaa10300" + "0101", frames.last().text())
    }

    @Test
    fun protocol4SendsTheFeatures() {
        val s = JblSession(model("JBL Tour One M3"))
        assertTrue(s.personifiTest)
        s.personifiBegin()
        assertEquals(listOf(JblP4Packet.set(7681 to byteArrayOf(0xFF.toByte()), 7682 to byteArrayOf(0xFF.toByte()))), s.takeEqWrite())
        s.personifiTone(left = false, hz = 250)
        assertEquals(listOf(JblP4Packet.set(7683 to byteArrayOf(0xFF.toByte()), 7684 to byteArrayOf(0xFA.toByte(), 0, 0, 0))), s.takeEqWrite())
        s.personifiTone(left = true, hz = 250)
        assertEquals(listOf(JblP4Packet.set(7683 to byteArrayOf(0x7F), 7684 to byteArrayOf(0xFA.toByte(), 0, 0, 0))), s.takeEqWrite())
        s.personifiEnd()
        assertEquals(listOf(JblP4Packet.set(7681 to byteArrayOf(0x7F), 7682 to byteArrayOf(0x7F))), s.takeEqWrite())
    }

    @Test
    fun protocol4WritesTheCurveWithoutKeepingIt() {
        val levels = JblPersonifi.TEST_FREQS.associateWith { 28f }
        val s = JblSession(model("JBL Tour One M3"))
        assertTrue(s.personifiApply(JblPersonifi.curve(levels, levels)))
        val write = s.takeEqWrite().first() as JblP4Packet
        // One record: the feature id and the value's length, two bytes each, then the value.
        assertEquals(JblP4.FEATURE_EQ_INFO, (write.payload[0].toInt() and 0xFF) or ((write.payload[1].toInt() and 0xFF) shl 8))
        val value = write.payload.copyOfRange(4, write.payload.size)
        assertEquals(201, value[0].toInt() and 0xFF)
        assertEquals(0, value[1].toInt())
        assertEquals(24, value[2].toInt())
    }

    @Test
    fun theClubOneSendsPersonifisCategoryThroughThe41Transfer() {
        val levels = JblPersonifi.TEST_FREQS.associateWith { 28f }
        val s = JblSession(model("JBL CLUB ONE"))
        assertTrue(s.personifiTest)
        assertTrue(s.personifiApply(JblPersonifi.curve(levels, levels)))
        val frames = s.takeEqWrite()
        assertEquals("aa4001" + "04", frames.first().text().take(8)) // the user's slot is picked first
        val header = frames.filterIsInstance<JblRaw>().first().bytes()
        assertEquals(0x41, header[1].toInt())
        // The first part of the payload: the user's index, then category 3.
        val part = frames.filterIsInstance<JblRaw>()[1].bytes()
        assertEquals(4, part[1].toInt())
        assertEquals(3, part[2].toInt())
    }

    @Test
    fun theAirohaModelsSendRaceFrames() {
        val s = JblSession(model("JBL Live Buds 3"))
        assertTrue(s.personifiTest)
        s.personifiBegin()
        val begin = s.takeEqWrite().map { it.race() }
        assertEquals(listOf(0x73, 0x63), begin.map { it.id })
        assertEquals("ff01ffffffff", begin[1].payload.joinToString("") { "%02x".format(it) })
        s.personifiTone(left = true, hz = 1000)
        assertEquals("ffffff01e803", s.takeEqWrite().single().race().also { assertEquals(0x63, it.id) }.payload.joinToString("") { "%02x".format(it) })
        s.personifiEnd()
        val end = s.takeEqWrite().map { it.race() }
        assertEquals(listOf(0x73, 0x63), end.map { it.id })
        assertEquals("ff00ffffffff", end[1].payload.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun theAirohaCurveIsTwentyFourBandsOfThirteenBytesAndTheRightGain() {
        val levels = JblPersonifi.TEST_FREQS.associateWith { 28f }
        val curve = JblPersonifi.curve(levels, levels)
        val s = JblSession(model("JBL Tour Pro 3"))
        assertTrue(s.personifiApply(curve))
        val write = s.takeEqWrite().first().race()
        assertEquals(0x71, write.id)
        assertEquals(24 * 13 + 9, write.payload.size)
        assertEquals(24, write.payload[0].toInt())
        val left = (0 until 4).sumOf { (write.payload[1 + it].toInt() and 0xFF) shl (8 * it) }
        assertEquals((curve.master.toFloat() * 100f).toInt(), left)
    }

    private fun JblFrame.race() = this as RacePacket
}
