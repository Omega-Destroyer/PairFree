package io.github.omegadestroyer.pairfree.protocol.oneplus

import io.github.omegadestroyer.pairfree.protocol.hexBytes
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OneplusTest {
    @Test fun analyticalCapabilityVectorAndReplyKeys() {
        val bytes = "aa0700000001000000".hexBytes()
        assertArrayEquals(bytes, OneplusReads.capabilities(0).bytes())
        for (split in 0..bytes.size) {
            val parser = OneplusParser()
            val frames = parser.feed(bytes.copyOfRange(0, split)) + parser.feed(bytes.copyOfRange(split, bytes.size))
            assertEquals(1, frames.size)
            assertArrayEquals(bytes, frames.single().bytes())
        }
        assertEquals(OneplusPacket(0x0106, 42).key, OneplusPacket(0x8106, 42).key)
        assertNotEquals(OneplusPacket(0x0106, 42).key, OneplusPacket(0x8106, 43).key)
        assertFalse(OneplusPacket(0x0106, 42).response)
        assertTrue(OneplusPacket(0x8106, 42).response)
    }

    @Test fun logicalFragmentsAndMtuChunksAreIndependent() {
        val packet = OneplusPacket(0x8106, 255, ByteArray(300) { (it % 251).toByte() })
        for (chunk in listOf(7, 20, 125, 126, 127, 128, 150, 400)) {
            val bytes = packet.fragments(chunk).fold(byteArrayOf()) { a, b -> a + b }
            val parser = OneplusParser()
            val frames = bytes.toList().chunked(3).flatMap { parser.feed(it.toByteArray()) }
            assertEquals(1, frames.size, "fragment payload $chunk")
            assertArrayEquals(packet.inner(), frames.single().inner())
        }
        val parts = packet.fragments(100)
        assertTrue(OneplusParser().feed(parts[1] + parts.last()).isEmpty(), "orphan fragments")
        assertTrue(OneplusParser().feed(parts[0] + parts[2] + parts.last()).isEmpty(), "missing fragment")
        val parser = OneplusParser()
        parser.feed(parts[0]); parser.reset()
        assertTrue(parser.feed(parts.last()).isEmpty())
    }

    @Test fun invalidVarintsLengthsAndTruncationRecover() {
        val good = OneplusReads.battery(7).bytes()
        val parser = OneplusParser()
        assertEquals(2, parser.feed("0011aa808080aa810000aa0700000601070100".hexBytes() + good + good).size)
        assertThrows(IllegalArgumentException::class.java) { OneplusPacket(0x10000, 0).bytes() }
        assertThrows(IllegalArgumentException::class.java) { OneplusPacket(1, 256).bytes() }
        assertThrows(IllegalArgumentException::class.java) { OneplusPacket(1, 0, ByteArray(4096)).bytes() }
        assertThrows(IllegalArgumentException::class.java) { OneplusPacket(1, 0, ByteArray(300)).fragments(1) }
    }

    @Test fun batteryStatusCountChargeAndCapabilityBitOrder() {
        val battery = OneplusPacket(0x8106, 2, "000201b202ff".hexBytes())
        assertEquals(listOf(OneplusBattery(1, 50, true), OneplusBattery(2, null, true)), OneplusBattery.of(battery))
        assertNull(OneplusBattery.of(battery.copy(payload = "0100".hexBytes())))
        assertNull(OneplusBattery.of(battery.copy(payload = "00020132".hexBytes())))
        assertNull(OneplusBattery.of(battery.copy(command = 0x0106)))
        assertEquals(setOf(0, 7, 8), OneplusReads.capabilityBits(OneplusPacket(0x8100, 0, "008101".hexBytes())))
        val bits = OneplusReads.capabilityBits(OneplusPacket(0x8100, 0, byteArrayOf(0) + ByteArray(30) { -1 }))!!
        assertEquals(69, bits.size)
        assertNull(OneplusReads.capabilityBits(OneplusPacket(0x8100, 0)))
    }
}
