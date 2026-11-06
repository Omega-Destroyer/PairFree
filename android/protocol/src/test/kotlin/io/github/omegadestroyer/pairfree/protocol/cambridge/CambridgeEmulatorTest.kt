package io.github.omegadestroyer.pairfree.protocol.cambridge

import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.hexBytes
import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Real vendor-app TX against synthetic Bluetooth replies. These are explicitly not hardware recordings. */
class CambridgeEmulatorTest {
    private fun vectors() = Json.parseToJsonElement(repoFile("data/cambridge/emulator_vectors.json").readText()).jsonObject
    private fun ack(id: Int, data: ByteArray) = CambridgePacket(id or 0x8000, data).bytes()
    private fun session(model: String, mask: Int = 0x03ff): CambridgeSession {
        val session = CambridgeSession(CambridgeModels.fromName(model)!!)
        session.start()
        val state = ByteArray(33).apply {
            this[2] = 1; this[3] = 2; this[8] = 80; this[9] = 85; this[10] = 50
            this[11] = 2; this[12] = 1; this[14] = 2; this[15] = 1; this[32] = 3
        }
        session.feed(ack(0x0780, state))
        session.feed(ack(0x0783, byteArrayOf(0, (mask shr 8).toByte(), mask.toByte())))
        session.feed(ack(0x0294, byteArrayOf(0, 0)))
        return session
    }

    @Test fun capturedVendorCommandsMatchPairfreeBuilders() {
        val data = vectors()
        val provenance = data.getValue("source").jsonObject
        assertTrue(provenance.getValue("device_virtual").jsonPrimitive.boolean)
        assertTrue(provenance.getValue("replies_synthetic").jsonPrimitive.boolean)
        assertFalse(provenance.getValue("hardware_tested").jsonPrimitive.boolean)
        val cases = data.getValue("cases").jsonArray
        assertTrue(cases.size >= 70)
        for (raw in cases) {
            val case = raw.jsonObject
            val session = session(case.getValue("model").jsonPrimitive.content, case["initial_controls"]?.jsonPrimitive?.int ?: 0x03ff)
            val key = case.getValue("key").jsonPrimitive.content
            val change = when (case.getValue("kind").jsonPrimitive.content) {
                "choice" -> SettingChange.Choice(key, case.getValue("option").jsonPrimitive.content)
                "toggle" -> SettingChange.Toggle(key, case.getValue("on").jsonPrimitive.boolean)
                "level" -> SettingChange.Level(key, case.getValue("value").jsonPrimitive.int)
                else -> error("unknown capture kind")
            }
            val frames = session.change(change)
            assertNotNull(frames, case.getValue("action").jsonPrimitive.content)
            val expected = case.getValue("frame").jsonPrimitive.content.hexBytes()
            assertTrue(frames!!.any { it.contentEquals(expected) }, case.getValue("action").jsonPrimitive.content)
            assertTrue(frames.all(session::allowed))
        }
    }

    @Test fun completeCustomRestoreMatchesTheRunningVendorApp() {
        val capture = vectors().getValue("custom_restore").jsonObject
        val session = session(capture.getValue("model").jsonPrimitive.content)
        val gains = capture.getValue("gains").jsonArray.map { it.jsonPrimitive.int }
        var frames = emptyList<ByteArray>()
        for ((band, gain) in gains.withIndex()) frames = session.change(SettingChange.Level("ca_band_${band + 1}", gain))!!
        val expected = capture.getValue("frames").jsonArray.map { it.jsonPrimitive.content.hexBytes() }
        assertEquals(expected.size, frames.size)
        expected.zip(frames).forEach { (vendor, pairfree) -> assertArrayEquals(vendor, pairfree) }
    }
}
