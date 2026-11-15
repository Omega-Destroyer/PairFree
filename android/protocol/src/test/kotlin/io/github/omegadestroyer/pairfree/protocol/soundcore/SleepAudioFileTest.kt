package io.github.omegadestroyer.pairfree.protocol.soundcore

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

/** The container and the BES messages of docs/soundcore/PROTOCOL.md section 14 (Sleep A30). */
class SleepAudioFileTest {
    @Test
    fun theChecksumsAreTheStandardOnes() {
        // The check values of CRC-16/XMODEM and CRC-32 over "123456789".
        val text = "123456789".encodeToByteArray()
        assertEquals(0x31C3, SleepAudioFile.crc16(text))
        assertEquals(0xCBF43926L, SleepAudioFile.crc32(text))
    }

    @Test
    fun theContainerHasTheAppsHeader() {
        val payload = ByteArray(1000) { (it * 7).toByte() }
        val file = SleepAudioFile.build(payload, 0x0102030405060708L, "Rain", soundType = 3, sampleRate = 32000)
        assertEquals(256 + 1000, file.size)
        assertEquals("31313636", file.copyOfRange(0, 4).hex())
        assertEquals(0, file[4].toInt())
        assertEquals("1001", file.copyOfRange(5, 7).hex())
        assertEquals("0807060504030201", file.copyOfRange(8, 16).hex())
        assertEquals(3, file[16].toInt())
        assertEquals("Rain", file.copyOfRange(24, 28).decodeToString())
        assertEquals(0, file[28].toInt())
        assertEquals("e8030000", file.copyOfRange(88, 92).hex(), "the payload's length")
        assertEquals("80000000", file.copyOfRange(92, 96).hex(), "field 92 stays 128 on a 256-byte header")
        assertEquals("007d0000", file.copyOfRange(96, 100).hex(), "32,000 Hz")
        assertEquals("00010000", file.copyOfRange(104, 108).hex(), "the header's size")
        val payloadCrc = SleepAudioFile.crc16(payload)
        assertEquals(payloadCrc, (file[108].toInt() and 0xFF) or ((file[109].toInt() and 0xFF) shl 8))
        val headerCrc = SleepAudioFile.crc16(file, 0, 110)
        assertEquals(headerCrc, (file[110].toInt() and 0xFF) or ((file[111].toInt() and 0xFF) shl 8))
        assertArrayEquals(payload, file.copyOfRange(256, file.size))
        assertEquals(0, file.copyOfRange(112, 256).count { it.toInt() != 0 }, "the rest of the header is zero")
    }

    @Test
    fun aLongNameIsCutAtSixtyFourBytes() {
        val file = SleepAudioFile.build(ByteArray(0), 1, "x".repeat(100), 0, 16000, headerSize = 128)
        assertEquals(128, file.size)
        assertEquals(64, file.copyOfRange(24, 88).count { it == 'x'.code.toByte() })
    }

    @Test
    fun theBesMessagesAreTheAppsBytes() {
        assertEquals("8e0400000042455354", BesTransferMessages.version().hex())
        assertEquals("9d0100000000", BesTransferMessages.side(0).hex())
        assertEquals("97010000000a", BesTransferMessages.user(0).hex())
        assertEquals("97010000000b", BesTransferMessages.user(1).hex())
        assertEquals("900100000000", BesTransferMessages.begin().hex())
        assertEquals("8800000000", BesTransferMessages.end().hex())
        assertEquals("920400000042455354", BesTransferMessages.activate().hex())
        assertEquals("800c00000042455354" + "10270000" + "78563412", BesTransferMessages.announce(10000, 0x12345678).hex())
        assertEquals("820800000042455354" + "78563412", BesTransferMessages.segmentCrc(0x12345678).hex())
    }
}
