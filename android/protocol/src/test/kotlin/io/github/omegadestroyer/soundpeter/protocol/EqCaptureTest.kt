package io.github.omegadestroyer.soundpeter.protocol

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

/** A snoop log built here, holding the H3's real EQ writes, must give them back byte for byte. */
class EqCaptureTest {
    private val vectors = loadData("test_vectors.json")
    private val reset = vectors.getValue("eq_layout").jsonArray.map { it.jsonObject }
        .first { it.str("model") == "S5" && it.str("name").startsWith("Reset") }
        .getValue("payloads").jsonArray.map { it.jsonPrimitive.content }
    private val bassBoost = vectors.getValue("eq_preset").jsonArray.map { it.jsonObject }
        .first { it.str("model") == "S5" && it.str("preset") == "Bass Boost" }
        .getValue("payloads").jsonArray.map { it.jsonPrimitive.content }

    private val firmware = "S5_20250605_V0.4.9"
    private val t0 = 1_760_000_000_000_000L   // Unix microseconds

    @Test
    fun findsEveryWriteInALogReadInSmallPieces() {
        val log = SnoopLog()
        log.acl(t0, fromEarbuds = false, handle = 3, spp(0x0309, ""))
        log.acl(t0 + 20_000, fromEarbuds = true, handle = 3, spp(0x8309, "00" + firmware.encodeToByteArray().hex(), version = 3))
        log.audio(t0 + 30_000, handle = 3)
        reset.forEachIndexed { i, p -> log.acl(t0 + 2_000_000 + i * 50_000L, false, 3, spp(Wire.EQ_COMMAND_QUALCOMM, p, Wire.VENDOR_EQ)) }
        // Bass Boost with every frame split over two ACL packets, as a small MTU would.
        bassBoost.forEachIndexed { i, p -> log.acl(t0 + 9_000_000 + i * 50_000L, false, 3, spp(Wire.EQ_COMMAND_QUALCOMM, p, Wire.VENDOR_EQ), splitAt = 7) }

        val scanner = SnoopScanner()
        val bytes = log.bytes()
        for (at in bytes.indices step 7) scanner.feed(bytes, at, minOf(7, bytes.size - at))
        assertTrue(scanner.isSnoopLog)
        assertNull(scanner.problem)

        val writes = EqCapture.writes(scanner.frames())
        assertEquals(2, writes.size)
        assertEquals(reset, writes[0].payloads.map { it.hex() })
        assertEquals(bassBoost, writes[1].payloads.map { it.hex() })
        assertEquals(firmware, writes[0].firmware?.text)
        assertEquals(t0 + 2_000_000, writes[0].timeMicros)
        assertEquals(-2.0, writes[0].masterGainDb, 1e-9)
        assertEquals(50.0, writes[0].bands[0].hz, 1e-9)
        assertEquals(3.5, writes[0].bands[0].gainDb, 0.02)
    }

    @Test
    fun leavesOutAWriteCutShort() {
        val log = SnoopLog()
        reset.take(6).forEachIndexed { i, p -> log.acl(t0 + i * 50_000L, false, 1, spp(Wire.EQ_COMMAND_QUALCOMM, p, Wire.VENDOR_EQ)) }
        bassBoost.forEachIndexed { i, p -> log.acl(t0 + 5_000_000 + i * 50_000L, false, 1, spp(Wire.EQ_COMMAND_QUALCOMM, p, Wire.VENDOR_EQ)) }
        val scanner = SnoopScanner().apply { feed(log.bytes()) }
        val writes = EqCapture.writes(scanner.frames())
        assertEquals(listOf(bassBoost), writes.map { w -> w.payloads.map { it.hex() } })
        assertNull(writes.single().firmware)
    }

    @Test
    fun saysWhenAFileIsNotASnoopLog() {
        val scanner = SnoopScanner().apply { feed("PK\u0003\u0004 not a log at all".encodeToByteArray()) }
        assertFalse(scanner.isSnoopLog)
        assertNotNull(scanner.problem)
        assertTrue(scanner.frames().isEmpty())
    }

    @Test
    fun reportListsEveryBandsExactBytes() {
        val log = SnoopLog()
        log.acl(t0, true, 3, spp(0x8309, "00" + firmware.encodeToByteArray().hex(), version = 3))
        reset.forEachIndexed { i, p -> log.acl(t0 + 1_000_000 + i * 50_000L, false, 3, spp(Wire.EQ_COMMAND_QUALCOMM, p, Wire.VENDOR_EQ)) }
        val write = EqCapture.writes(SnoopScanner().apply { feed(log.bytes()) }.frames()).single()
        val lines = EqCapture.reportLines(listOf("SOUNDPEATS Classic" to write))
        assertEquals("Firmware:  $firmware", lines[0])
        assertEquals("EQ:        command 0E01, 10 bands, master gain -2 dB", lines[1])
        assertTrue("1. SOUNDPEATS Classic" in lines)
        assertTrue(lines.any { it.trim().startsWith("Hz") && it.contains("50 160 350 800 1600 2800 4600 8000 12000 17000") })
        for (p in reset) assertTrue("   $p" in lines, p)
    }

    @Test
    fun readsAndRepliesComeOutPerPairOfEarbuds() {
        val air5 = "S2_20250816_V1.2"
        val log = SnoopLog()
        // The H3 on connection 3: the firmware read first, as PeatsAudio does.
        log.acl(t0, false, 3, spp(0x0309, ""))
        log.acl(t0 + 10_000, true, 3, spp(0x8309, "00" + firmware.encodeToByteArray().hex(), version = 3))
        log.acl(t0 + 20_000, false, 3, spp(0x0310, ""))
        log.acl(t0 + 30_000, true, 3, spp(0x8310, "0001", version = 3))
        log.acl(t0 + 40_000, false, 3, spp(0x0350, ""))                         // no reply: the H3 has no water drain
        log.acl(t0 + 50_000, false, 3, spp(0x0311, "00"))                       // a SET is not a read
        log.acl(t0 + 60_000, true, 3, spp(0x8314, "0001", version = 3))        // unasked
        log.acl(t0 + 70_000, true, 3, spp(0x0101, "000d010c010901050108070103060207010005", Wire.VENDOR_EQ, version = 3))
        // Later, the Air5 Pro+ on connection 5.
        log.acl(t0 + 5_000_000, false, 5, spp(0x0309, ""))
        log.acl(t0 + 5_010_000, true, 5, spp(0x8309, "00" + air5.encodeToByteArray().hex(), version = 3))
        log.acl(t0 + 5_020_000, false, 5, spp(0x0350, ""))
        log.acl(t0 + 5_030_000, true, 5, spp(0x8350, "0000", version = 3))

        val pairs = LoggedReads.of(SnoopScanner().apply { feed(log.bytes()) }.frames())
        assertEquals(listOf(firmware, air5), pairs.map { it.firmware?.text })

        val h3 = pairs[0]
        assertEquals(listOf(Command.FIRMWARE_VERSION, Command.ANC_MODE, Command.MULTIPOINT, Command.WATER_DRAIN), h3.replies.keys.toList())
        assertNull(h3.replies.getValue(Command.WATER_DRAIN))
        assertTrue(Gaia.FIT_FEATURE in h3.gaiaFeatures!!)
        val lines = h3.lines()
        assertEquals(DeviceReport.line(Command.ANC_MODE, "0001".hexBytes()), lines[1])
        assertTrue(lines.any { it.startsWith("water_drain") && it.endsWith("no reply") })
        assertEquals("gaia_features        0001  00 01 05 06 07 08 09 0c 0d", lines.last())

        val second = pairs[1]
        assertEquals(listOf(Command.FIRMWARE_VERSION, Command.WATER_DRAIN), second.replies.keys.toList())
        assertNull(second.gaiaFeatures)
    }

    /** Builds a btsnoop file (H4 datalink) the way Android writes one. */
    private class SnoopLog {
        private val out = ByteArrayOutputStream().apply {
            write("btsnoop".encodeToByteArray() + 0)
            write(int(1))
            write(int(1002))
        }

        fun acl(time: Long, fromEarbuds: Boolean, handle: Int, data: ByteArray, splitAt: Int = 0) {
            // L2CAP: length, channel; then an RFCOMM-like header and check byte around the data
            val rfcomm = byteArrayOf(0x0B, 0xEF.toByte(), ((data.size shl 1) or 1).toByte()) + data + byteArrayOf(0x9A.toByte())
            val l2cap = le16(rfcomm.size) + le16(0x0041) + rfcomm
            val parts = if (splitAt in 1 until l2cap.size) listOf(l2cap.copyOfRange(0, splitAt), l2cap.copyOfRange(splitAt, l2cap.size)) else listOf(l2cap)
            parts.forEachIndexed { i, part ->
                val boundary = if (i == 0) 2 else 1
                record(time, if (fromEarbuds) 1 else 0, byteArrayOf(0x02) + le16(handle or (boundary shl 12)) + le16(part.size) + part)
            }
        }

        /** An A2DP media packet with an FF in it, to show noise is skipped. */
        fun audio(time: Long, handle: Int) {
            val l2cap = le16(12) + le16(0x0042) + byteArrayOf(0x80.toByte(), 0x60, 0, 1, 0xFF.toByte(), 0x04, 0, 0x55, 1, 2, 3, 4)
            record(time, 0, byteArrayOf(0x02) + le16(handle or (2 shl 12)) + le16(l2cap.size) + l2cap)
        }

        private fun record(time: Long, flags: Int, data: ByteArray) {
            out.write(int(data.size))
            out.write(int(data.size))
            out.write(int(flags))
            out.write(int(0))
            out.write(long(time + 0x00DCDDB30F2F8000L))
            out.write(data)
        }

        fun bytes(): ByteArray = out.toByteArray()

        private fun int(v: Int) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
        private fun long(v: Long) = int((v shr 32).toInt()) + int(v.toInt())
        private fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
    }

    private fun spp(command: Int, payloadHex: String, vendor: Int = Wire.VENDOR_MAIN, version: Int = 4): ByteArray =
        Frame.build(command, payloadHex.hexBytes(), vendor).also { it[1] = version.toByte() }
}
