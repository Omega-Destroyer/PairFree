package io.github.omegadestroyer.pairfree.protocol.jabra

import io.github.omegadestroyer.pairfree.protocol.hexBytes
import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun ByteArray.hex() = joinToString(" ") { "%02x".format(it) }
private fun String.bytes() = replace(" ", "").hexBytes()

/**
 * Jabra's GNP framing (docs/jabra/PROTOCOL.md). The vectors are the original native encoder's output under emulation with a stub transport
 * (data/jabra/research/test_vectors.json): synthetic inputs, never captured from earbuds.
 */
class JabraPacketTest {
    private val vectors: JsonArray = Json.parseToJsonElement(repoFile("data/jabra/research/test_vectors.json").readText()).jsonObject.getValue("native_packets").jsonArray

    @Test
    fun theNativeEncodersBytesAreReproduced() {
        var checked = 0
        for (v in vectors) {
            val o = v.jsonObject
            if (o.getValue("kind").jsonPrimitive.content != "native_encoder_execution") continue
            val payload = o.getValue("payload").jsonPrimitive.content.bytes()
            val expected = o.getValue("output").jsonPrimitive.content
            val attribute = o.getValue("packet_type").jsonPrimitive.content.toInt()
            val main = o.getValue("main").jsonPrimitive.content.toInt()
            if (expected.isEmpty()) {
                // A frame of 64 bytes or more is refused.
                assertThrows(IllegalArgumentException::class.java) { JabraPacket(4, 9, 0x2A, attribute, main, payload) }
                continue
            }
            val packet = JabraPacket(4, 9, 0x2A, attribute, main, payload)
            assertEquals(expected, packet.bytes().hex(), o.toString())
            checked++
        }
        assertTrue(checked >= 8, "only $checked vectors were checked")
    }

    @Test
    fun theParserCutsAndJoins() {
        val read = JabraPacket.of(4, 0x2A, JabraPacket.READ, 0x13, 0xBE, 0x01).bytes()
        val reply = JabraPacket.of(9, 0x2A, JabraPacket.REPLY, 0x13, 0xBE, 0x01, 0x02).bytes()
        assertEquals("04 09 2a 47 13 be 01", read.hex())
        val parser = JabraParser()
        // Cut in the middle of the header, then the rest and a second packet in one read.
        assertEquals(0, parser.feed(read.copyOfRange(0, 2)).size)
        val both = parser.feed(read.copyOfRange(2, read.size) + reply)
        assertEquals(2, both.size)
        assertEquals(0x13BE, both[0].key)
        assertTrue(both[1].isReply)
        assertEquals(2, both[1].body[1].toInt())
    }

    @Test
    fun groupsWithoutASubcommandHaveNoKey() {
        val ack = JabraPacket(9, 4, 5, JabraPacket.REPLY, 0xFF, ByteArray(0))
        assertEquals(null, ack.sub)
        assertEquals(5, ack.bytes().size)
    }

    @Test
    fun theGeneratedTablesAreWhatTheImporterWrote() {
        assertEquals(34, JabraModels.ALL.size)
        assertEquals(46, JabraProfiles.ALL.size)
        assertTrue(JabraSettings.ALL.size > 280)
        // Every setting a profile lists exists, and an Elite 10's sidetone shares one payload at the DSP's address.
        for (p in JabraProfiles.ALL) for (id in p.settings) assertTrue(id in JabraSettings.BY_ID, "${p.snapshot}: $id")
        val elite10 = JabraProfiles.ALL.single { it.snapshot == "11846_01-92_1.2.4" }
        val sidetone = elite10.settings.map { JabraSettings.BY_ID.getValue(it) }.filter { it.command == "DSP_SIDETONE" }
        assertEquals(2, sidetone.size)
        assertTrue(sidetone.all { it.shared && it.destination == JabraWire.DSP })
        // The six presets of the Sound+ app.
        assertEquals(listOf("JABRA_DEFAULT", "VOICE", "BASS_BOOST", "TREBLE_BOOST", "SMOOTH", "ENERGIZE"), JabraPresets.ALL.map { it.name })
    }
}
