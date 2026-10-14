package io.github.omegadestroyer.pairfree.protocol.samsung

import io.github.omegadestroyer.pairfree.protocol.hexBytes
import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun load(name: String): JsonObject = Json.parseToJsonElement(repoFile("data/samsung/$name").readText()).jsonObject

private val vectors = load("test_vectors.json")

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

/** Framing, the model table and the state parser, against GalaxyBudsClient's tests. */
class SamsungProtocolTest {
    private val devices = load("devices.json").getValue("devices").jsonArray.map { it.jsonObject }
    private val cases = vectors.getValue("galaxybudsclient_extended_status").jsonArray.map { it.jsonObject }

    @Test
    fun crcIsXmodem() {
        assertEquals(0x31C3, SamsungPacket.crc16("123456789".encodeToByteArray()), "CRC-16/XMODEM check value")
    }

    @Test
    fun builtFramesParseBack() {
        for (legacy in listOf(false, true)) {
            val frame = SamsungPacket.build(SamsungMessage.NOISE_CONTROLS, byteArrayOf(1), legacy)
            assertEquals(SamsungPacket(SamsungMessage.NOISE_CONTROLS, byteArrayOf(1)), SamsungPacket.parse(frame, legacy))
        }
        // Standard: FD, length 4 (id, payload, CRC) little-endian, id 120, payload, CRC low first, DD.
        val frame = SamsungPacket.build(SamsungMessage.NOISE_CONTROLS, byteArrayOf(1))
        assertEquals(listOf(0xFD, 4, 0, 120, 1), frame.take(5).map { it.toInt() and 0xFF })
        assertEquals(0xDD, frame.last().toInt() and 0xFF)
        assertEquals(8, frame.size)
    }

    @Test
    fun theParserSplitsAStreamAndSkipsBrokenFrames() {
        val a = SamsungPacket.build(SamsungMessage.AMBIENT_VOLUME, byteArrayOf(2))
        val b = SamsungPacket.build(SamsungMessage.TOUCH_UPDATED, byteArrayOf(1))
        val broken = a.copyOf().also { it[4] = 9 }
        val parser = SamsungParser()
        val stream = byteArrayOf(0x00, 0x11) + broken + a + b
        val out = parser.feed(stream.copyOfRange(0, 9)) + parser.feed(stream.copyOfRange(9, stream.size))
        assertEquals(listOf(SamsungMessage.AMBIENT_VOLUME, SamsungMessage.TOUCH_UPDATED), out.map { it.id })
        assertEquals(0, parser.pending)
    }

    @Test
    fun modelsMatchTheData() {
        assertEquals(devices.map { it.str("model") }, SamsungModels.ALL.map { it.model })
        assertEquals(13, SamsungModels.ALL.size)
        for (d in devices) {
            val m = SamsungModels.of(d.str("model"))!!
            val g = d.getValue("galaxybudsclient").jsonObject
            assertEquals(g.str("base_name"), m.baseName)
            assertEquals(g.str("framing") == "legacy", m.legacy)
            assertEquals(g.getValue("features").jsonObject.keys, m.features.keys, m.model)
        }
        assertTrue(SamsungModels.of("SM-R170")!!.legacy, "the Galaxy Buds (2019)")
        assertEquals("00001102-0000-1000-8000-00805f9b34fd", SamsungModels.of("SM-R170")!!.serviceUuid)
        assertEquals("2e73a4ad-332d-41fc-90e2-16bef06523f2", SamsungModels.of("SM-R630")!!.serviceUuid)
        assertTrue(SamsungModels.of("SM-R630")!!.supports("hot_command_language_update", 2))
        assertTrue(!SamsungModels.of("SM-R630")!!.supports("hot_command_language_update", 1), "from revision 2")
        assertTrue(!SamsungModels.of("SM-R175")!!.supports("anc", 13), "the Buds+ have no noise cancelling")
    }

    @Test
    fun modelsFromTheirBluetoothNames() {
        assertEquals("SM-R630", SamsungModels.fromName("Galaxy Buds3 Pro (A1B2)")?.model)
        assertEquals("SM-R530", SamsungModels.fromName("Galaxy Buds3 (A1B2)")?.model)
        assertEquals("SM-R510", SamsungModels.fromName("Galaxy Buds2 Pro (A1B2)")?.model)
        assertEquals("SM-R177", SamsungModels.fromName("Galaxy Buds2 (A1B2)")?.model)
        assertEquals("SM-R170", SamsungModels.fromName("Galaxy Buds (A1B2)")?.model)
        assertEquals("SM-R175", SamsungModels.fromName("Galaxy Buds+ (A1B2)")?.model)
        assertEquals("SM-R180", SamsungModels.fromName("Galaxy Buds Live (A1B2)")?.model)
        assertEquals("SM-R420", SamsungModels.fromName("Galaxy Buds3 FE (A1B2)")?.model)
        assertNull(SamsungModels.fromName("Soundcore Space A40"))
    }

    @Test
    fun readsEveryStateInGalaxyBudsClientsTests() {
        assertEquals(7, cases.size)
        var checked = 0
        for (c in cases) {
            val model = SamsungModels.of(c.str("model"))!!
            val where = c.str("galaxybudsclient_test")
            val packet = SamsungPacket.parse(c.str("frame").hexBytes(), model.legacy)
            assertNotNull(packet, "frame and CRC: $where")
            assertEquals(SamsungMessage.EXTENDED_STATUS_UPDATED, packet!!.id)
            val status = SamsungStatus.parse(model, packet.payload)!!
            assertEquals(c.getValue("revision").jsonPrimitive.int, status.revision, where)
            for ((name, want) in c.getValue("expect").jsonObject) {
                val p = want as JsonPrimitive
                val have = status.values[name]
                val expected: Any = p.booleanOrNull ?: p.intOrNull ?: p.content
                // Fields GalaxyBudsClient leaves at their defaults are not sent by the model: absent here.
                if (have == null) {
                    assertTrue(expected == false || expected == 0 || (name == "detect_conversations" && expected == true), "$where $name = $expected")
                } else {
                    assertEquals(expected, have, "$where $name")
                    checked++
                }
            }
        }
        assertTrue(checked > 250, "values checked: $checked")
    }

    @Test
    fun aShortStateKeepsWhatItHolds() {
        val c = cases.first { it.str("model") == "SM-R510" }
        val model = SamsungModels.of("SM-R510")!!
        val payload = SamsungPacket.parse(c.str("frame").hexBytes())!!.payload
        val short = SamsungStatus.parse(model, payload.copyOfRange(0, 30))!!
        assertEquals(100, short.int("battery_l"))
        assertNull(short.flag("side_tone_enabled"))
        assertArrayEquals(payload, SamsungPacket.parse(c.str("frame").hexBytes())!!.payload)
    }
}
