package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.hexBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

private fun JblFrame.hex() = bytes().joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

/** JBL's EQ command A2, its calibration and a session's EQ (docs/jbl/PROTOCOL.md section 11), from JBL's app. */
class JblEqTest {
    private fun model(name: String) = JblModels.ALL.first { it.name == name }

    private fun flat(gainDb: Float = 0f) = JblEqCurve(JblEqWire.DESIGN, 0f, 48, gainDb, gainDb, JblEq.custom(List(10) { 0f }))

    /** A reply as the earbuds send it: the write's layout under sub-command 02, or 04 with Q in tenths. */
    private fun reply(curve: JblEqCurve, q10: Boolean): JblPacket {
        val p = JblEq.write(curve, q10).payload.copyOf()
        p[0] = (if (q10) JblEqWire.REPLY_Q10 else JblEqWire.REPLY).toByte()
        return JblParser().feed(JblPacket(JblCommand.EQ, p).bytes()).single()
    }

    @Test
    fun readsWithAOneByteLength() {
        assertEquals("aaa20201ca", JblEq.read(JblEqWire.DESIGN).hex())
        assertEquals("aaa20201ff", JblEq.read(JblEqWire.CURRENT).hex())
    }

    @Test
    fun writeLayout() {
        val curve = JblEqCurve(230, 0f, 48, 0f, 0f, listOf(JblEqBand(1, 32f, 3f, 1f)))
        // AA A2, a two-byte length, 00, the category, the calibration, 48 kHz, two gains, one band: type, gain, frequency, Q.
        assertEquals("aaa21a0000e60000000030000000000000000001" + "01" + "00004040" + "00000042" + "01", JblEq.write(curve, q10 = false).hex())
        // In tenths the sub-command is 03 and Q 1 is sent as 10.
        val q10 = JblEq.write(curve, q10 = true).hex()
        assertTrue(q10.startsWith("aaa21a0003e6"), q10)
        assertTrue(q10.endsWith("0a"), q10)
    }

    @Test
    fun repliesRoundTrip() {
        val curve = JblEqCurve(1, 2.5f, 48, -1f, -1f, JblEq.custom(listOf(4f, 2f, 1f, 2.5f, -1.5f, -1.5f, 0f, 1f, 2f, 4f)))
        for (q10 in listOf(false, true)) assertEquals(curve, JblEq.parse(reply(curve, q10).payload))
        assertNull(JblEq.parse("0201".hexBytes() + ByteArray(3)))
    }

    @Test
    fun calibration() {
        val boost = JblEq.custom(listOf(0f, 0f, 0f, 0f, 0f, 6f, 0f, 0f, 0f, 0f))
        // Nothing above the design EQ: none needed.
        assertEquals(0f, JblCalibration.of(flat(), JblEq.custom(List(10) { 0f })))
        // A curve that only cuts meets the design EQ at 0 Hz, where every peak filter is 1: rounding there decides between
        // none and the library's 1 dB, in the library as here.
        val cuts = JblCalibration.of(flat(), JblEq.custom(List(10) { -3f }))
        assertTrue(cuts == 0f || abs(cuts - 1f) < 0.001f, "cuts $cuts")
        // A 6 dB peak on a flat design EQ: 6 dB and the library's 1 dB.
        val c = JblCalibration.of(flat(), boost)
        assertTrue(abs(c - 7f) < 0.05f, "calibration $c")
        // A design EQ whose own gain covers it needs none.
        assertEquals(0f, JblCalibration.of(flat(-8f), boost))
        // Shelves and the pass filters are designed too: a 6 dB low shelf peaks near 6 dB.
        val shelf = JblCalibration.of(flat(), listOf(JblEqBand(0, 100f, 6f, 0.7f)))
        assertTrue(abs(shelf - 7f) < 0.2f, "low shelf $shelf")
    }

    @Test
    fun everyModelHasItsEq() {
        for (m in JblModels.ALL) {
            val eq = m.eq
            assertNotNull(eq, m.name)
            eq!!
            assertTrue(eq.presets.isNotEmpty(), m.name)
            assertTrue(eq.rangeDb in 6..12, m.name)
        }
        val flex2 = model("JBL Tune Flex 2").eq!!
        assertEquals(JblEqSpec.Path.A2_Q10, flex2.path)
        // The Tune series' presets after Off, as standard_preset_eq.json groups them.
        assertEquals(listOf("Off", "STUDIO", "BASS", "CLUB", "EXTREME BASS", "VOCAL", "JAZZ"), flex2.presets.map { it.name })
        assertEquals(JblEqSpec.Path.A2, model("JBL WAVE FLEX").eq!!.path)
        assertEquals(11, model("JBL WAVE FLEX").eq!!.rangeDb)
        // The Quantum 360's class never reads the design EQ.
        assertTrue(!model("JBL Quantum 360").eq!!.readsDesign)
    }

    @Test
    fun session() {
        val s = JblSession(model("JBL Tune Flex 2"))
        val reads = s.start().map { it.hex() }
        assertTrue("aaa20201ca" in reads && "aaa20201ff" in reads, reads.toString())
        assertNull(s.eq)
        // Without the design EQ JBL's app sends -1.
        val early = JblEq.parse((s.eqWrite(null, List(10) { 0f })!!.single() as JblPacket).payload.also { it[0] = JblEqWire.REPLY_Q10.toByte() })!!
        assertEquals(-1f, early.calibration)
        s.receive(reply(flat(), q10 = true))
        val jazz = s.model.eq!!.presets.first { it.name == "JAZZ" }
        val inUse = JblEqCurve(jazz.category, 0f, 48, 0f, 0f, jazz.bands)
        assertTrue(s.receive(reply(inUse, q10 = true)))
        assertEquals(jazz.category, s.eq?.category)
        // A curve of the user's own goes as category 230 with Q in tenths and a calibration from the design EQ.
        val out = s.eqWrite(null, listOf(0f, 0f, 0f, 0f, 0f, 6f, 0f, 0f, 0f, 0f))!!.single() as JblPacket
        assertEquals(JblEqWire.WRITE_Q10, out.payload[0].toInt() and 0xFF)
        val sent = JblEq.parse(out.payload.copyOf().also { it[0] = JblEqWire.REPLY_Q10.toByte() })!!
        assertEquals(JblEqWire.CUSTOM, sent.category)
        assertTrue(abs(sent.calibration - 7f) < 0.05f, "calibration ${sent.calibration}")
        assertEquals(JblEqWire.CUSTOM, s.eq?.category)
        assertEquals(listOf("aaa20201ff"), s.takeReadBacks().map { it.hex() }.filter { it.startsWith("aaa2") }.distinct())
    }

    @Test
    fun modelsPairFreeDoesNotWriteYet() {
        val s = JblSession(model("JBL LIVE PRO 2 TWS"))
        assertTrue(s.start().none { it.hex().startsWith("aaa2") })
        assertNull(s.eqWrite(null, List(10) { 0f }))
    }
}
