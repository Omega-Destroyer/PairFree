package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.hexBytes
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BragiTest {
    @Test fun analyticalDeviceInfoVectorAndEverySplit() {
        val vector = "7eff809400062d1002010008005480".hexBytes()
        assertArrayEquals(vector, BragiReads.deviceInfo(0).frame().bytes())
        assertEquals(0x2d, BragiFrame.crc8(vector.copyOf(6)))
        assertEquals(0x5480, BragiFrame.crc16(vector.copyOfRange(1, vector.size - 2)))
        for (split in 0..vector.size) {
            val parser = BragiParser()
            val frames = parser.feed(vector.copyOfRange(0, split)) + parser.feed(vector.copyOfRange(split, vector.size))
            assertEquals(1, frames.size)
            assertArrayEquals(vector, frames.single().bytes())
        }
    }

    @Test fun statusRequiresExplicitSuccessAndFullCorrelation() {
        val request = BragiReads.battery(17)
        val status = BragiRpc(2, 2, 3, 17, byteArrayOf(0))
        assertTrue(status.successfulStatus)
        assertTrue(status.matches(request))
        assertFalse(status.copy(body = byteArrayOf()).successfulStatus)
        assertFalse(status.copy(body = byteArrayOf(0x64)).successfulStatus)
        assertFalse(status.copy(type = 1).successfulStatus)
        assertFalse(status.copy(tag = 18).matches(request))
        assertFalse(status.copy(service = 4).matches(request))
        assertFalse(status.copy(method = 4).matches(request))
        assertFalse(request.matches(request))
        for (type in 0..3) {
            val message = BragiRpc(type, 2, 3, 17, byteArrayOf(8, 1), final = false)
            val decoded = message.frame().rpc()!!
            assertEquals(type, decoded.type)
            assertFalse(decoded.final)
            assertArrayEquals(message.body, decoded.body)
        }
        assertNull(BragiRpc.of(byteArrayOf(0x11, 2, 3, 17)))
        for (size in 0..3) assertNull(BragiRpc.of(ByteArray(size)))
    }

    @Test fun crcCorruptionBoundsCoalescingResetAndOpaqueSecureFrames() {
        val good = BragiReads.battery(3).frame().bytes()
        val header = good.copyOf().apply { this[6] = (this[6].toInt() xor 1).toByte() }
        val body = good.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertEquals(2, BragiParser().feed(byteArrayOf(0, 1) + header + body + good + good).size)
        val noFcs = BragiReads.battery(3).frame().copy(checksum = false)
        assertArrayEquals(noFcs.bytes(), BragiParser().feed(noFcs.bytes()).single().bytes())
        val opaque = BragiFrame(0x95, BragiReads.deviceInfo(0).bytes())
        assertNull(BragiParser().feed(opaque.bytes()).single().rpc())
        val max = BragiFrame(0x94, ByteArray(4087))
        assertEquals(4096, max.bytes().size)
        assertEquals(4087, BragiParser().feed(max.bytes()).single().payload.size)
        assertThrows(IllegalArgumentException::class.java) { BragiFrame(0x94, ByteArray(4088)).bytes() }
        assertThrows(IllegalArgumentException::class.java) { BragiRpc(0, 2, 3, 256).bytes() }
        val parser = BragiParser(); parser.feed(good.copyOf(8)); parser.reset()
        assertEquals(1, parser.feed(good).size)
    }

    @Test fun protobufUnknownFieldsUnsigned64AndMalformedValues() {
        val bytes = BragiProto.unsigned(1, ULong.MAX_VALUE) + "12036162631d01020304210807060504030201".hexBytes()
        val fields = BragiProto.parse(bytes)!!
        assertEquals(ULong.MAX_VALUE, fields[0].unsigned())
        assertEquals(listOf(0, 2, 5, 1), fields.map { it.wireType })
        assertEquals("abc", fields[1].data.decodeToString())
        assertNull(fields[1].unsigned())
        for (bad in listOf("00", "0e", "0b", "0a04ff", "08ff", "08ffffffffffffffffff02", "090102", "0d0102")) {
            assertNull(BragiProto.parse(bad.hexBytes()), bad)
        }
        assertThrows(IllegalArgumentException::class.java) { BragiProto.unsigned(0, 1uL) }
    }

    @Test fun authenticationFlagMissingOrAmbiguousNeverBecomesPermission() {
        val reply = BragiRpc(1, 2, 1, 0, "6001".hexBytes())
        assertEquals(true, BragiReads.authenticatedAccessRequired(reply))
        assertEquals(false, BragiReads.authenticatedAccessRequired(reply.copy(body = "6000".hexBytes())))
        for (bad in listOf("", "6002", "60016000", "620100", "60ff")) {
            assertNull(BragiReads.authenticatedAccessRequired(reply.copy(body = bad.hexBytes())), bad)
        }
        assertNull(BragiReads.authenticatedAccessRequired(reply.copy(type = 2)))
        assertNull(BragiReads.authenticatedAccessRequired(reply.copy(method = 3)))
    }
}
