package io.github.omegadestroyer.pairfree.protocol.huawei

import io.github.omegadestroyer.pairfree.protocol.hexBytes
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Analytical vendor-builder vector and synthetic streams, not hardware captures. */
class HuaweiTest {
    @Test fun vendorBatteryVectorCrcAndEverySplit() {
        val bytes = "5a00050001080100f53d".hexBytes()
        assertArrayEquals(bytes, HuaweiReads.battery().bytes())
        assertEquals(0x31c3, HuaweiPacket.crc16("123456789".encodeToByteArray()))
        for (split in 0..bytes.size) {
            val parser = HuaweiParser()
            val frames = parser.feed(bytes.copyOfRange(0, split)) + parser.feed(bytes.copyOfRange(split, bytes.size))
            assertEquals(1, frames.size)
            assertArrayEquals(bytes, frames.single().bytes())
        }
    }

    @Test fun malformedLengthsControlsCrcAndTlvsDoNotLeakPartialState() {
        val good = HuaweiReads.battery().bytes()
        val badCrc = good.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        val parser = HuaweiParser()
        val frames = parser.feed("00115affff005a000301".hexBytes() + badCrc + good + good)
        assertEquals(2, frames.size)
        assertThrows(IllegalArgumentException::class.java) { HuaweiTlv(256).bytes() }
        assertThrows(IllegalArgumentException::class.java) { HuaweiTlv(1, ByteArray(256)).bytes() }
        assertThrows(IllegalArgumentException::class.java) { HuaweiPacket(-1, 1, emptyList()).bytes() }
        // Valid CRC with an incomplete TLV must still be rejected as a whole.
        val malformed = "5a0006000108010342".hexBytes()
        val crc = HuaweiPacket.crc16(malformed)
        assertTrue(HuaweiParser().feed(malformed + byteArrayOf((crc shr 8).toByte(), crc.toByte())).isEmpty())
        parser.feed(good.copyOf(5))
        parser.reset()
        assertEquals(1, parser.feed(good).size)
    }

    @Test fun unsignedBatteryValuesAndUnknownComponents() {
        val frame = HuaweiPacket(1, 8, listOf(HuaweiTlv(1, byteArrayOf(70)), HuaweiTlv(2, byteArrayOf(75, 80, -1))))
        val decoded = HuaweiParser().feed(frame.bytes()).single()
        assertEquals(HuaweiBattery(70, 75, 80, null), HuaweiBattery.of(decoded))
        assertNull(HuaweiBattery.of(frame.copy(command = 7)))
        assertEquals(HuaweiBattery(70, null, null, null), HuaweiBattery.of(frame.copy(fields = listOf(HuaweiTlv(1, byteArrayOf(70)), HuaweiTlv(2, byteArrayOf(80))))))
        val duplicate = frame.copy(fields = listOf(HuaweiTlv(1, byteArrayOf(70)), HuaweiTlv(1, byteArrayOf(80))))
        assertNull(HuaweiBattery.of(duplicate)?.global)
        assertEquals(2, HuaweiParser().feed(duplicate.bytes()).single().fields.size)
    }
}
