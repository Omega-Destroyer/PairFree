package io.github.omegadestroyer.pairfree.protocol.cambridge

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.hexBytes
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Analytical vectors from Melomania 2.8.12's builders; synthetic replies, never presented as recordings. */
class CambridgeTest {
    private fun session(name: String = "Melomania Touch") = CambridgeSession(CambridgeModels.fromName(name)!!).also { it.start() }
    private fun ack(id: Int, payload: ByteArray) = CambridgePacket(id or 0x8000, payload).bytes()
    private fun composite(): ByteArray = ByteArray(33).apply {
        this[2] = 1; this[3] = 2; this[8] = 75; this[9] = 88; this[10] = 42
        this[11] = 1; this[12] = 2; this[13] = 3; this[14] = 4; this[15] = 5; this[16] = 6
        for (i in 17..28) this[i] = 0x6b
        this[31] = 1; this[32] = 3
    }
    private fun rows(s: CambridgeSession) = s.groups.flatMap { it.settings }

    @Test fun analyticalFramesAndAllStreamBoundaries() {
        assertArrayEquals("ff010000000a0780".hexBytes(), CambridgePacket(CambridgeCommand.STATE).bytes())
        val bytes = "ff010005000a021a0612007801".hexBytes()
        assertArrayEquals(bytes, CambridgePacket(CambridgeCommand.SET_BAND, "0612007801".hexBytes()).bytes())
        for (split in 0..bytes.size) {
            val parser = CambridgeParser()
            val frames = parser.feed(bytes.copyOfRange(0, split)) + parser.feed(bytes.copyOfRange(split, bytes.size))
            assertEquals(1, frames.size, "split $split")
            assertArrayEquals(bytes, frames.single().bytes())
        }
        val check = CambridgePacket(0x8780, composite()).bytes(checksum = true)
        val bad = check.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        val parser = CambridgeParser()
        val parsed = parser.feed(byteArrayOf(0, 1, 2) + bad + check + bytes)
        assertEquals(listOf(0x8780, 0x021a), parsed.map { it.command })
        assertThrows(IllegalArgumentException::class.java) { CambridgePacket(1, ByteArray(255)).bytes() }
        assertTrue(CambridgeParser().feed("ff040000000a0780".hexBytes()).isEmpty())
        assertTrue(CambridgeParser().feed("ff010000001d0780".hexBytes()).isEmpty())
    }

    @Test fun modelsStayWithinTheirEvidenceAndSharedServicesDoNotIdentifyThem() {
        assertNull(CambridgeModels.fromName("Melomania A100"))
        assertNull(CambridgeModels.fromName("Melomania 1"))
        assertNull(CambridgeModels.fromName("Melomania Touch Extra"))
        assertEquals("Melomania Touch", CambridgeModels.fromName(" melomania touch ")?.name)
        assertEquals(CambridgeWire.GAIA_UUID.lowercase(), CambridgeModels.service(listOf(CambridgeWire.GAIA_UUID.lowercase())))
        assertEquals(CambridgeWire.SPP_UUID, CambridgeModels.service(emptyList()))
    }

    @Test fun stateFieldsPrivacyAndEveryTruncatedComposite() {
        val s = session()
        assertTrue(rows(s).isEmpty())
        assertNull(s.change(SettingChange.Toggle("ca_ambient", true)))
        s.feed(ack(0x0780, composite()))
        assertEquals(75, s.state.batteryLeft)
        assertEquals(88, s.state.batteryRight)
        assertEquals("L 1.2.3 / R 4.5.6", s.firmware)
        assertEquals(42, (rows(s).single { it.key == "ca_ambient_level" } as Setting.Level).value)
        assertTrue(s.replies.getValue("780").slice(17..28).all { it == 0.toByte() })
        s.feed(ack(0x0780, composite() + ByteArray(20) { 0x6c }))
        assertEquals(33, s.replies.getValue("780").size)
        // Notifications and replies we did not ask for cannot become a report.
        s.feed(CambridgePacket(0x0780, composite()).bytes())
        s.feed(ack(0x1234, byteArrayOf(0, 42)))
        assertEquals(setOf("780"), s.replies.keys)
        for (size in 0..33) {
            val short = session()
            short.feed(ack(0x0780, composite().copyOf(size)))
            assertEquals(size > 8, short.state.batteryLeft != null, "left at length $size")
            assertEquals(size > 9, short.state.batteryRight != null, "right at length $size")
            assertEquals(size > 32, rows(short).any { it.key == "ca_codec" }, "codec at length $size")
        }
        s.feed(ack(0x0780, composite().apply { this[8] = -1; this[9] = 101 }))
        assertNull(s.state.batteryLeft)
        assertNull(s.state.batteryRight)
        assertFalse(rows(s).any { it.key == "ca_language" })
    }

    @Test fun writesPreserveControlBitsAndRespectModelsAndReadBacks() {
        val s = session()
        s.feed(ack(0x0783, "00a5f0".hexBytes()))
        val on = s.change(SettingChange.Toggle("ca_control_play_pause", true))!!.single()
        assertArrayEquals("ff010002000a0782a5f1".hexBytes(), on)
        val next = s.change(SettingChange.Toggle("ca_control_next", true))!!.single()
        assertArrayEquals("ff010002000a0782a5f3".hexBytes(), next)
        assertArrayEquals("ff010000000a0783".hexBytes(), s.takeReadBacks().single())
        assertTrue(s.takeReadBacks().isEmpty())
        assertNull(s.change(SettingChange.Choice("ca_control_next", "yes")))
        val m1 = session("Melomania 1+")
        m1.feed(ack(0x0780, composite()))
        m1.feed(ack(0x0783, "00ffff".hexBytes()))
        assertEquals(9, rows(m1).filter { it.key.startsWith("ca_control_") }.size)
        assertFalse(rows(m1).any { it.key.startsWith("ca_ambient") })
        assertNull(m1.change(SettingChange.Toggle("ca_ambient", true)))
        assertFalse(m1.allowed(CambridgePacket(0x0798, byteArrayOf(1)).bytes()))
        s.feed(ack(0x0780, composite().apply { this[31] = 3 }))
        assertFalse(rows(s).any { it.key == "ca_codec" })
    }

    @Test fun eqDoesNotInventCustomStateAndUsesTheVendorBandScaling() {
        val s = session()
        s.feed(ack(0x0294, byteArrayOf(0, 8)))
        assertFalse(rows(s).any { it.key.startsWith("ca_band_") })
        assertNull(s.change(SettingChange.Level("ca_band_1", 2)))
        s.feed(ack(0x0294, byteArrayOf(0, 0)))
        val frames = s.change(SettingChange.Level("ca_band_1", 2))!!
        assertEquals(7, frames.size)
        assertArrayEquals("ff010001000a021400".hexBytes(), frames[0])
        assertArrayEquals("ff010001000a021406".hexBytes(), frames[1])
        assertArrayEquals("ff010005000a021a0612007801".hexBytes(), frames[2])
        assertTrue(frames.all(s::allowed))
        assertNull(s.change(SettingChange.Level("ca_band_1", 7)))
        s.feed(ack(0x021a, byteArrayOf(1)))
        assertFalse(rows(s).any { it.key.startsWith("ca_band_") })
        assertFalse(s.allowed(frames[2]), "failed write must stop remaining queued gains")
        s.feed(ack(0x0294, byteArrayOf(0, 0)))
        assertTrue(s.allowed(frames[2]))
        s.failed(0x0214)
        assertFalse(s.allowed(frames[2]), "missing bank ACK must stop queued gains too")
        assertFalse(s.allowed(frames[1]), "failed Neutral selection must stop queued custom-mode selection")
        assertEquals(listOf(0x0214), s.feed(ack(0x0214, byteArrayOf(1))))
    }

    @Test fun allowlistRefusesUnknownCommandsMalformedPayloadsAndAllFirmwareTraffic() {
        val s = session()
        assertTrue(s.start().all(s::allowed))
        for (id in listOf(0x0200, 0x0301, 0x0640, 0x0641, 0x0700, 0x0781, 0xffff)) {
            assertFalse(s.allowed(CambridgePacket(id).bytes()), "command ${id.toString(16)}")
        }
        assertFalse(s.allowed(CambridgePacket(0x0798, byteArrayOf(3)).bytes()))
        assertFalse(s.allowed(CambridgePacket(0x0799, byteArrayOf(101)).bytes()))
        assertFalse(s.allowed(CambridgePacket(0x020b, byteArrayOf(8)).bytes()))
        assertFalse(s.allowed(CambridgePacket(0x07b8, byteArrayOf(4)).bytes()))
        assertFalse(s.allowed(CambridgePacket(0x021a, "0602007801".hexBytes()).bytes()))
        assertFalse(s.allowed(CambridgePacket(0x021a, "06127fff01".hexBytes()).bytes()))
        assertFalse(s.allowed(CambridgePacket(0x0780).bytes() + CambridgePacket(0x0783).bytes()))
        assertFalse(s.allowed(CambridgePacket(0x8780, byteArrayOf(0)).bytes()))
    }
}
