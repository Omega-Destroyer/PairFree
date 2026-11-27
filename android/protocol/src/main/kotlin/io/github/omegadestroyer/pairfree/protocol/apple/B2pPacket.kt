package io.github.omegadestroyer.pairfree.protocol.apple

/**
 * One frame of the Beats' B2P protocol (docs/apple/B2P.md), read from Apple's Beats app for Android (libB2pAdapter.so):
 * `FF B2`, a two-byte length (low byte first, the whole frame), a sequence number, a two-byte opcode (low byte first),
 * the payload and a CRC-32 (low byte first) over everything before it.
 */
public class B2pFrame(public val sequence: Int, public val opcode: Int, public val payload: ByteArray = ByteArray(0)) {
    public fun bytes(): ByteArray {
        val length = payload.size + OVERHEAD
        val head = byteArrayOf(0xFF.toByte(), 0xB2.toByte(), length.toByte(), (length shr 8).toByte(), sequence.toByte(), opcode.toByte(), (opcode shr 8).toByte())
        val body = head + payload
        val crc = crc32(body)
        return body + byteArrayOf(crc.toByte(), (crc shr 8).toByte(), (crc shr 16).toByte(), (crc shr 24).toByte())
    }

    override fun equals(other: Any?): Boolean = other is B2pFrame && sequence == other.sequence && opcode == other.opcode && payload.contentEquals(other.payload)

    override fun hashCode(): Int = (sequence * 31 + opcode) * 31 + payload.contentHashCode()

    override fun toString(): String = "B2pFrame(seq $sequence, ${opcode.toString(16).padStart(4, '0')}, ${payload.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }})"

    public companion object {
        /** Header, length, sequence, opcode and CRC: 2 + 2 + 1 + 2 + 4. */
        public const val OVERHEAD: Int = 11

        /** Most-significant-bit-first CRC-32, polynomial 04C11DB7, starting at 0, result inverted (b2p_crc32_*). */
        public fun crc32(data: ByteArray): Int {
            var crc = 0
            for (b in data) {
                crc = crc xor ((b.toInt() and 0xFF) shl 24)
                repeat(8) { crc = if (crc < 0) (crc shl 1) xor POLY else crc shl 1 }
            }
            return crc.inv()
        }

        /** One frame, or null when the header, length or CRC is wrong. [bytes] may hold more after it; see [split]. */
        public fun parse(bytes: ByteArray): B2pFrame? {
            if (bytes.size < OVERHEAD || bytes[0] != 0xFF.toByte() || bytes[1] != 0xB2.toByte()) return null
            val length = (bytes[2].toInt() and 0xFF) or ((bytes[3].toInt() and 0xFF) shl 8)
            if (length < OVERHEAD || length > bytes.size) return null
            val body = bytes.copyOfRange(0, length - 4)
            val crc = (0..3).fold(0) { acc, i -> acc or ((bytes[length - 4 + i].toInt() and 0xFF) shl (8 * i)) }
            if (crc != crc32(body)) return null
            val opcode = (bytes[5].toInt() and 0xFF) or ((bytes[6].toInt() and 0xFF) shl 8)
            return B2pFrame(bytes[4].toInt() and 0xFF, opcode, bytes.copyOfRange(7, length - 4))
        }

        /**
         * The whole frames at the start of [buffer] and the bytes left over (a frame not yet complete). A frame starts with
         * both header bytes, as [parse] wants them: a stray `FF` alone would have its next bytes read as a length of up to
         * 64 KB, and everything the Beats sent after it held back until that many bytes had come.
         */
        public fun split(buffer: ByteArray): Pair<List<ByteArray>, ByteArray> {
            val frames = mutableListOf<ByteArray>()
            var at = 0
            while (buffer.size - at >= 4) {
                if (buffer[at] != 0xFF.toByte() || buffer[at + 1] != 0xB2.toByte()) { at++; continue }
                val length = (buffer[at + 2].toInt() and 0xFF) or ((buffer[at + 3].toInt() and 0xFF) shl 8)
                if (length < OVERHEAD) { at++; continue }
                if (buffer.size - at < length) break
                frames += buffer.copyOfRange(at, at + length)
                at += length
            }
            return frames to buffer.copyOfRange(at, buffer.size)
        }

        private const val POLY = 0x04C11DB7
    }
}
