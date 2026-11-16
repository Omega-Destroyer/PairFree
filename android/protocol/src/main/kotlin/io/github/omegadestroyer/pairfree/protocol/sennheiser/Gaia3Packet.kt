package io.github.omegadestroyer.pairfree.protocol.sennheiser

/**
 * Sennheiser's GAIA v3 frames, as Smart Control 4.9.4 and Smart Control Plus 1.6.3 send them (docs/sennheiser/PROTOCOL.md section 3): `FF`, version 3,
 * flags (bit 0: an XOR byte follows, bit 1: two length bytes), the payload's length, the vendor id (`0495` Sennheiser's own, `001D` Qualcomm's), the
 * command word and the payload, all big-endian. The word is feature << 9 | type << 7 | command; a reply has type 2, a notification type 1, an error type 3.
 */
public class Gaia3Packet(public val vendor: Int, public val word: Int, public val payload: ByteArray = ByteArray(0)) {
    public val feature: Int get() = word shr 9
    public val type: Int get() = (word shr 7) and 3
    public val command: Int get() = word and 0x7F
    public val isReply: Boolean get() = type == TYPE_RESPONSE
    public val isNotification: Boolean get() = type == TYPE_NOTIFICATION
    public val isError: Boolean get() = type == TYPE_ERROR

    /** What identifies the command this answers or is: the vendor and the word of the command (type 0). */
    public val key: Int get() = (vendor shl 16) or (word and 0x7F or (feature shl 9))

    /** The frame for the channel (no checksum, a one-byte length: the products' own app sends nothing else on classic Bluetooth). */
    public fun bytes(): ByteArray {
        require(payload.size <= MAX_PAYLOAD) { "a GAIA frame holds at most $MAX_PAYLOAD bytes of payload" }
        return byteArrayOf(START.toByte(), VERSION.toByte(), 0, payload.size.toByte(), (vendor shr 8).toByte(), vendor.toByte(), (word shr 8).toByte(), word.toByte()) + payload
    }

    override fun equals(other: Any?): Boolean = other is Gaia3Packet && vendor == other.vendor && word == other.word && payload.contentEquals(other.payload)

    override fun hashCode(): Int = (vendor * 31 + word) * 31 + payload.contentHashCode()

    override fun toString(): String = "Gaia3Packet(%04x:%04x %s)".format(vendor, word, payload.joinToString("") { "%02x".format(it) })

    public companion object {
        public const val START: Int = 0xFF
        public const val VERSION: Int = 3
        public const val MAX_PAYLOAD: Int = 255
        public const val TYPE_COMMAND: Int = 0
        public const val TYPE_NOTIFICATION: Int = 1
        public const val TYPE_RESPONSE: Int = 2
        public const val TYPE_ERROR: Int = 3

        /** The key of the command with [word] on [vendor], whatever type [word] has. */
        public fun keyOf(vendor: Int, word: Int): Int = (vendor shl 16) or (word and 0x7F or ((word shr 9) shl 9))
    }
}

/**
 * Cuts what the channel gives into [Gaia3Packet]s of the two vendors Smart Control accepts (the app drops anything else, as it drops versions other than 3 and 4
 * and flags other than the checksum and length bits). Fragmented and joined frames, a checksum when the flag says so, and resynchronising after bad data.
 */
public class Gaia3Parser {
    private var pending = ByteArray(0)

    public fun feed(bytes: ByteArray): List<Gaia3Packet> {
        val out = mutableListOf<Gaia3Packet>()
        pending += bytes
        while (pending.isNotEmpty()) {
            if ((pending[0].toInt() and 0xFF) != Gaia3Packet.START) {
                skip(1)
                continue
            }
            if (pending.size < 4) break
            val version = pending[1].toInt() and 0xFF
            val flags = pending[2].toInt() and 0xFF
            if ((version != 3 && version != 4) || flags and 0xFC != 0) {
                skip(1)
                continue
            }
            val extended = flags and 2 != 0
            val lengthBytes = if (extended) 2 else 1
            if (pending.size < 3 + lengthBytes) break
            val length = if (extended) ((pending[3].toInt() and 0xFF) shl 8) or (pending[4].toInt() and 0xFF) else pending[3].toInt() and 0xFF
            if (length > MAX_ACCEPTED) {
                skip(1)
                continue
            }
            val head = 3 + lengthBytes + 4
            val size = head + length + (flags and 1)
            if (pending.size < size) break
            if (flags and 1 != 0 && pending.copyOfRange(0, size).fold(0) { x, b -> x xor (b.toInt() and 0xFF) } != 0) {
                skip(1)
                continue
            }
            val at = 3 + lengthBytes
            val vendor = ((pending[at].toInt() and 0xFF) shl 8) or (pending[at + 1].toInt() and 0xFF)
            val word = ((pending[at + 2].toInt() and 0xFF) shl 8) or (pending[at + 3].toInt() and 0xFF)
            if (vendor == 0x0495 || vendor == 0x001D) out += Gaia3Packet(vendor, word, pending.copyOfRange(head, head + length))
            skip(size)
        }
        return out
    }

    private fun skip(n: Int) {
        pending = pending.copyOfRange(n, pending.size)
    }

    private companion object {
        const val MAX_ACCEPTED = 1024
    }
}
