package io.github.omegadestroyer.pairfree.protocol.samsung

/**
 * One Galaxy Buds message: an [id] ([SamsungMessage]) and its payload. On the wire
 * (docs/samsung/PROTOCOL.md section 2), as GalaxyBudsClient frames it:
 * - standard: FD, length (2 bytes, little-endian, bits 0 to 9), id, payload, CRC, DD;
 * - legacy (the Galaxy Buds 2019): FE, type, length (1 byte), id, payload, CRC, EE.
 *
 * The length counts the id, the payload and the CRC. The CRC is CRC-16/XMODEM over the id and the
 * payload, low byte first.
 */
public class SamsungPacket(public val id: Int, public val payload: ByteArray) {
    override fun equals(other: Any?): Boolean = other is SamsungPacket && id == other.id && payload.contentEquals(other.payload)

    override fun hashCode(): Int = id * 31 + payload.contentHashCode()

    override fun toString(): String =
        "SamsungPacket(id=$id, payload=${payload.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }})"

    public companion object {
        internal const val SOM = 0xFD
        internal const val EOM = 0xDD
        internal const val LEGACY_SOM = 0xFE
        internal const val LEGACY_EOM = 0xEE
        private const val LENGTH_MASK = 0x3FF

        /** The frame that sends [id] with [payload] to earbuds using the [legacy] framing or not. */
        public fun build(id: Int, payload: ByteArray = ByteArray(0), legacy: Boolean = false): ByteArray {
            require(id in 0..0xFF) { "message id out of range: $id" }
            val size = 1 + payload.size + 2
            require(size <= LENGTH_MASK) { "payload too long: ${payload.size} bytes" }
            val crc = crc16(byteArrayOf(id.toByte()) + payload)
            val out = ArrayList<Byte>(size + 5)
            if (legacy) {
                out += LEGACY_SOM.toByte()
                out += 0.toByte() // a request
                out += size.toByte()
            } else {
                out += SOM.toByte()
                out += size.toByte()
                out += (size shr 8).toByte()
            }
            out += id.toByte()
            out += payload.toList()
            out += crc.toByte()
            out += (crc shr 8).toByte()
            out += (if (legacy) LEGACY_EOM else EOM).toByte()
            return out.toByteArray()
        }

        /** CRC-16/XMODEM: polynomial 0x1021, start 0, no reflection. */
        public fun crc16(bytes: ByteArray): Int {
            var crc = 0
            for (b in bytes) {
                crc = crc xor ((b.toInt() and 0xFF) shl 8)
                repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
                crc = crc and 0xFFFF
            }
            return crc
        }

        /** The packet in one complete [frame], or null when it is not one valid frame. */
        public fun parse(frame: ByteArray, legacy: Boolean = false): SamsungPacket? {
            val p = SamsungParser(legacy)
            return p.feed(frame).singleOrNull()?.takeIf { p.pending == 0 }
        }
    }
}

/**
 * Splits the byte stream from the earbuds into [SamsungPacket]s. Reads come in any size, so
 * bytes are kept until a whole frame is there. A frame whose CRC or end byte is wrong is
 * dropped, and the search goes on from the next start byte, as GalaxyBudsClient does.
 */
public class SamsungParser(private val legacy: Boolean = false) {
    private var buf = ByteArray(0)

    /** Bytes kept for a frame not yet complete. */
    public val pending: Int get() = buf.size

    public fun feed(bytes: ByteArray): List<SamsungPacket> {
        buf += bytes
        val out = mutableListOf<SamsungPacket>()
        val som = (if (legacy) SamsungPacket.LEGACY_SOM else SamsungPacket.SOM).toByte()
        val eom = (if (legacy) SamsungPacket.LEGACY_EOM else SamsungPacket.EOM).toByte()
        while (true) {
            val start = buf.indexOf(som)
            if (start < 0) {
                buf = ByteArray(0)
                break
            }
            if (start > 0) buf = buf.copyOfRange(start, buf.size)
            if (buf.size < 3) break
            val size = if (legacy) buf[2].toInt() and 0xFF else ((buf[1].toInt() and 0xFF) or ((buf[2].toInt() and 0xFF) shl 8)) and 0x3FF
            val total = 3 + size + 1
            if (size < 3) {
                buf = buf.copyOfRange(1, buf.size)
                continue
            }
            if (buf.size < total) break
            val body = buf.copyOfRange(3, 3 + size)
            val id = body[0].toInt() and 0xFF
            val payload = body.copyOfRange(1, size - 2)
            val crc = (body[size - 2].toInt() and 0xFF) or ((body[size - 1].toInt() and 0xFF) shl 8)
            if (buf[total - 1] == eom && crc == SamsungPacket.crc16(body.copyOfRange(0, size - 2))) {
                out += SamsungPacket(id, payload)
                buf = buf.copyOfRange(total, buf.size)
            } else {
                buf = buf.copyOfRange(1, buf.size)
            }
        }
        return out
    }
}
