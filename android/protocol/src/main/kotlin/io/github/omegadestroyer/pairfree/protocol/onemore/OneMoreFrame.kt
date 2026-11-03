package io.github.omegadestroyer.pairfree.protocol.onemore

/**
 * One 1MORE frame (docs/1more/PROTOCOL.md section 2): two bytes that say which way it goes ([OneMoreWire.SENT]
 * from the phone, [OneMoreWire.REPLY] from the earbuds), the command, the payload's length and the
 * [sequence] number (each two bytes, high byte first), the XOR of those eight bytes, then the payload.
 */
public class OneMoreFrame(
    public val command: Int,
    public val payload: ByteArray = ByteArray(0),
    public val sequence: Int = 0,
    public val head: Int = OneMoreWire.SENT,
) {
    /** True for a frame the earbuds sent. */
    public val fromEarbuds: Boolean get() = head == OneMoreWire.REPLY

    public fun bytes(): ByteArray {
        require(payload.size <= MAX_PAYLOAD) { "payload too long: ${payload.size} bytes" }
        val header = byteArrayOf(
            (head shr 8).toByte(), head.toByte(),
            (command shr 8).toByte(), command.toByte(),
            (payload.size shr 8).toByte(), payload.size.toByte(),
            (sequence shr 8).toByte(), sequence.toByte(),
        )
        return header + byteArrayOf(xor(header, 0, header.size).toByte()) + payload
    }

    override fun equals(other: Any?): Boolean =
        other is OneMoreFrame && command == other.command && sequence == other.sequence && head == other.head && payload.contentEquals(other.payload)

    override fun hashCode(): Int = ((command * 31 + sequence) * 31 + head) * 31 + payload.contentHashCode()

    override fun toString(): String =
        "OneMoreFrame(${command.toString(16).padStart(4, '0')} ${OneMoreCommand.NAMES[command] ?: "?"}, seq=$sequence, " +
            "payload=${payload.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }})"

    public companion object {
        /** Longer than anything the earbuds send (1MORE's app reads at most 93 bytes); a length past it means the stream is out of step. */
        internal const val MAX_PAYLOAD = 0x0400

        /** The sequence number counts up to this, then starts over at 0, as 1MORE's app does (Cmd.kt, makeData). */
        public const val SEQUENCE_WRAP: Int = 0x10000

        /** The XOR of [bytes] from [from] to [to], as 1MORE's app checks a frame's header. */
        public fun xor(bytes: ByteArray, from: Int, to: Int): Int {
            var x = 0
            for (i in from until to) x = x xor (bytes[i].toInt() and 0xFF)
            return x
        }
    }
}

/**
 * Splits the earbuds' byte stream into [OneMoreFrame]s by each one's length field. Reads come in any size,
 * so bytes are kept until a whole frame is there; bytes before a reply's `01 00` are dropped. 1MORE's app
 * itself splits by a fixed length per command and never checks the XOR, so a frame whose XOR does not match
 * is still taken when its command is one PairFree knows; anything else means the stream is out of step,
 * and the next `01 00` starts over.
 */
public class OneMoreParser {
    private var buf = ByteArray(0)

    /** Bytes kept for a frame not yet complete. */
    public val pending: Int get() = buf.size

    public fun feed(bytes: ByteArray): List<OneMoreFrame> {
        buf += bytes
        val out = mutableListOf<OneMoreFrame>()
        while (true) {
            val start = (0 until buf.size - 1).firstOrNull { u8(it) == HEAD_HIGH && u8(it + 1) == HEAD_LOW }
            if (start == null) {
                // Keep a last 01 for the next read.
                buf = if (buf.isNotEmpty() && u8(buf.size - 1) == HEAD_HIGH) byteArrayOf(buf.last()) else ByteArray(0)
                break
            }
            if (start > 0) buf = buf.copyOfRange(start, buf.size)
            if (buf.size < OneMoreWire.HEADER) break
            val command = (u8(2) shl 8) or u8(3)
            val length = (u8(4) shl 8) or u8(5)
            val checked = OneMoreFrame.xor(buf, 0, OneMoreWire.HEADER - 1) == u8(OneMoreWire.HEADER - 1)
            if (length > OneMoreFrame.MAX_PAYLOAD || !checked && command !in OneMoreCommand.NAMES) {
                buf = buf.copyOfRange(1, buf.size)
                continue
            }
            val total = OneMoreWire.HEADER + length
            if (buf.size < total) break
            out += OneMoreFrame(command, buf.copyOfRange(OneMoreWire.HEADER, total), (u8(6) shl 8) or u8(7), OneMoreWire.REPLY)
            buf = buf.copyOfRange(total, buf.size)
        }
        return out
    }

    private fun u8(at: Int): Int = buf[at].toInt() and 0xFF

    private companion object {
        val HEAD_HIGH = OneMoreWire.REPLY shr 8
        val HEAD_LOW = OneMoreWire.REPLY and 0xFF
    }
}
