package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.hexBytes
import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun JblFrame.hex() = bytes().joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

private fun ByteArray.hex() = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

/**
 * Realtek's segment EQ on the Tune 520BT family (docs/jbl/PROTOCOL.md section 11.1): the port of DspConfig against the
 * library's own output (data/jbl/test_vectors.json segment_eq, from libDspConfig.so run in an emulator), and the frames.
 */
class JblRealtekEqTest {
    private val vectors = Json.parseToJsonElement(repoFile("data/jbl/test_vectors.json").readText()).jsonObject.getValue("segment_eq").jsonObject

    private fun curve(o: JsonObject) = JblEqCurve(
        o.getValue("category").jsonPrimitive.int, 0f, 48, o.getValue("gain").jsonPrimitive.float, o.getValue("gain").jsonPrimitive.float,
        o.getValue("bands").jsonArray.map { b ->
            val v = b.jsonArray.map { it.jsonPrimitive.float }
            JblEqBand(v[0].toInt(), v[1], v[2], v[3])
        },
    )

    @Test
    fun calculateMatchesTheLibrary() {
        val cases = vectors.getValue("calculate").jsonArray
        assertTrue(cases.size >= 5)
        for (c in cases.map { it.jsonObject }) {
            fun doubles(k: String) = c.getValue(k).jsonArray.map { it.jsonPrimitive.double }
            val lib = c.getValue("library").jsonPrimitive.content.hexBytes()
            val (maxDb, data) = RealtekDsp.calculate(
                c.getValue("fs").jsonPrimitive.double, c.getValue("gain").jsonPrimitive.double, doubles("gains"), doubles("freqs"),
                doubles("qs"), c.getValue("types").jsonArray.map { it.jsonPrimitive.int }, c.getValue("design_gain").jsonPrimitive.double,
                c.getValue("design_count").jsonPrimitive.int,
            )!!
            // The library's answer: the largest gain (two bytes, low first), 12 bytes of module header, then the stages.
            assertEquals(((lib[1].toInt() shl 8) or (lib[0].toInt() and 0xFF)).toShort().toInt(), maxDb, c.toString())
            assertEquals(lib.copyOfRange(14, lib.size).hex(), data.hex(), c.toString())
        }
    }

    @Test
    fun writeFrames() {
        for (w in vectors.getValue("writes").jsonArray.map { it.jsonObject }) {
            val frames = JblRealtekEq.write(curve(w.getValue("custom").jsonObject), curve(w.getValue("design").jsonObject), w.getValue("mtu").jsonPrimitive.int)!!
            assertEquals(w.getValue("frames").jsonArray.map { it.jsonPrimitive.content }, frames.map { it.hex() })
        }
    }

    @Test
    fun harmanCurveRoundTrips() {
        val design = curve(vectors.getValue("writes").jsonArray.first().jsonObject.getValue("design").jsonObject)
        val bytes = vectors.getValue("design_harman").jsonPrimitive.content
        assertEquals(bytes, JblRealtekEq.harman(design).hex())
        assertEquals(design.copy(sampleRate = 48000), JblRealtekEq.parse(bytes.hexBytes()))
        // One byte or none is JBL's default: ten flat peaks.
        assertEquals(10, JblRealtekEq.parse(byteArrayOf(0))!!.bands.size)
        assertNull(JblRealtekEq.parse("ca0b00".hexBytes()))
    }

    @Test
    fun session() {
        val s = JblSession(JblModels.ALL.first { it.name == "JBL Tune 520BT" })
        val reads = s.start().map { it.hex() }
        assertTrue("aaa60201ca" in reads && "aaa60201ff" in reads, reads.toString())
        // Without the design EQ there is nothing to work the coefficients out from, and JBL's app sends nothing.
        assertNull(s.eqWrite(null, List(10) { 0f }))
        // The design EQ in two parts: the first is acknowledged, the second completes it.
        val design = vectors.getValue("design_harman").jsonPrimitive.content.hexBytes()
        val half = design.size / 2
        for ((i, part) in listOf(design.copyOfRange(0, half), design.copyOfRange(half, design.size)).withIndex()) {
            val p = byteArrayOf(JblSegmentEq.REPLY.toByte(), JblSegmentEq.PROVIDER.toByte(), 2, i.toByte()) + part
            s.receive(JblParser().feed(JblPacket(JblCommand.SEGMENT_EQ, p).bytes()).single())
            assertEquals(if (i == 0) listOf("aa0102a600") else emptyList(), s.takeOutbox().map { it.hex() })
        }
        val w = vectors.getValue("writes").jsonArray.first().jsonObject
        val gains = w.getValue("custom").jsonObject.getValue("bands").jsonArray.map { it.jsonArray[2].jsonPrimitive.float }
        assertEquals(w.getValue("frames").jsonArray.map { it.jsonPrimitive.content }, s.eqWrite(null, gains)!!.map { it.hex() })
        assertEquals(JblEqWire.CUSTOM, s.eq?.category)
        assertTrue("aaa60201ff" in s.takeReadBacks().map { it.hex() })
    }
}
