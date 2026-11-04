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
        return JblParser().feed(JblPacket(JblCommand.EQ, p).bytes()).single() as JblPacket
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
    fun protocol4() {
        val s = JblSession(model("JBL Live 680NC"))
        // The query for the EQ in use: feature 0E01, one byte, FF.
        assertTrue("00dd020001000500010e0100ff" in s.start().map { it.hex() })
        val jazz = s.model.eq!!.presets.first { it.name == "JAZZ" }
        val inUse = JblEqCurve(jazz.category, 0f, 48, 0f, 0f, jazz.bands)
        val write = JblEq.p4Write(inUse)
        // The EQ info: id 0E02, its length, then the category, kept, ten bands, and 19 + 13 a band bytes in all.
        assertEquals("020e" + "9500", write.payload.copyOfRange(0, 4).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') })
        val notification = JblP4Packet(JblP4.NOTIFICATION, byteArrayOf(0, 0) + write.payload)
        assertTrue(s.receive(notification))
        assertEquals(inUse, s.eq)
        // A curve of the user's own goes as 193; no design EQ is kept on protocol 4, so the calibration is -1 as JBL's app sends.
        val out = s.eqWrite(null, List(10) { 1f })!!.single() as JblP4Packet
        val value = out.payload.copyOfRange(4, out.payload.size)
        assertEquals(JblP4.EQ_CUSTOM, value[0].toInt() and 0xFF)
        val sent = JblEq.p4Parse(value)!!
        assertEquals(JblEqWire.CUSTOM, sent.category)
        assertEquals(-1f, sent.calibration)
        // The Airoha-library models wait for Airoha's PEQ.
        assertNull(JblSession(model("JBL Tune 680NC")).eqWrite(null, List(10) { 0f }))
    }

    @Test
    fun oldTune135FirmwareHalvesCustomCurves() {
        val s = JblSession(model("JBL Tune 135BT"))
        // Firmware not yet known counts as old, as in JBL's app.
        var out = s.eqWrite(null, List(10) { 4f })!!.single() as JblP4Packet
        assertEquals(2f, JblEq.p4Parse(out.payload.copyOfRange(4, out.payload.size))!!.bands[0].gainDb)
        // What the earbuds report is doubled back.
        s.receive(JblP4Packet(JblP4.NOTIFICATION, byteArrayOf(0, 0) + out.payload))
        assertEquals(4f, s.eq!!.bands[0].gainDb)
        // Firmware 4.2.1 and later take the gains as they are.
        s.receive(JblP4Packet(JblP4.NOTIFICATION, "0000".hexBytes() + JblP4Packet.set(JblP4.FEATURE_FIRMWARE to byteArrayOf(4, 2, 1)).payload))
        out = s.eqWrite(null, List(10) { 4f })!!.single() as JblP4Packet
        assertEquals(4f, JblEq.p4Parse(out.payload.copyOfRange(4, out.payload.size))!!.bands[0].gainDb)
    }

    @Test
    fun olderBesTransfer() {
        val s = JblSession(model("JBL LIVE PRO 2 TWS"))
        // The design EQ alone is read, with 42 and category 0.
        assertTrue("aa420100" in s.start().map { it.hex() })
        // It comes back as a header with the part count, which is acknowledged twice more than there are parts.
        val design = JblEq.write41(flat().copy(calibration = 0f), graphic = false)
        val payload = design.drop(1).dropLast(1).joinToString("") { it.hex().substring(2) }.hexBytes()
        // The reply has no calibration: drop it from the write's payload to make one.
        val reply = payload.copyOfRange(0, 2) + payload.copyOfRange(6, payload.size)
        val partsOut = reply.toList().chunked(79)
        s.receive(JblPacket.of(JblEq41.REPLY, partsOut.size))
        assertEquals(List(partsOut.size + 2) { "aa01024300" }, s.takeOutbox().map { it.hex() })
        val reader = JblReader(p4 = false)
        partsOut.forEachIndexed { i, part -> reader.feed(byteArrayOf(i.toByte()) + part.toByteArray(), newMessage = true).forEach { s.receive(it) } }
        reader.feed("aa0443".hexBytes(), newMessage = true).forEach { s.receive(it) }
        // A curve of the user's own: header, parts, the app's closing frame; calibrated against the design EQ just read.
        val out = s.eqWrite(null, listOf(0f, 0f, 0f, 0f, 0f, 6f, 0f, 0f, 0f, 0f))!!
        assertEquals(out, s.takeEqWrite())
        assertTrue(out.all { it is JblRaw && s.allowed(it) })
        val sent = out.drop(1).dropLast(1).joinToString("") { it.hex().substring(2) }.hexBytes()
        assertEquals("aa41b303", out.first().hex())
        assertEquals("aa0541", out.last().hex())
        assertEquals(4, sent[0].toInt())
        assertEquals(JblEq41.GRAPHIC, sent[1].toInt())
        val calibration = Float.fromBits((sent[2].toInt() and 0xFF) or ((sent[3].toInt() and 0xFF) shl 8) or ((sent[4].toInt() and 0xFF) shl 16) or ((sent[5].toInt() and 0xFF) shl 24))
        assertTrue(abs(calibration - 7f) < 0.05f, "calibration $calibration")
        // Raw bytes PairFree did not build are never sent.
        assertTrue(!s.allowed(JblRaw(byteArrayOf(1, 2, 3))))
    }

    @Test
    fun clubOnePresets() {
        val s = JblSession(model("JBL CLUB ONE"))
        val preset = s.model.eq!!.presets.first { it.category != 0 }
        assertEquals(listOf("aa4001" + "%02x".format(preset.category)), s.eqWrite(preset, emptyList())!!.map { it.hex() })
        val custom = s.eqWrite(null, List(10) { 0f })!!
        assertEquals("aa400104", custom.first().hex())
        assertEquals("aa0541", custom.last().hex())
    }

    @Test
    fun modelsPairFreeDoesNotWriteYet() {
        // Airoha's PEQ, on the Airoha models and protocol 4's models with Airoha's EQ library.
        for (name in listOf("JBL Live Buds 3", "JBL Tune 680NC")) {
            val s = JblSession(model(name))
            assertTrue(s.start().none { it.hex().startsWith("aaa2") || it.hex().endsWith("0e0100ff") }, name)
            assertNull(s.eqWrite(null, List(10) { 0f }), name)
        }
    }
}
