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
    /** The length field when it is not the payload's: 1MORE's app reads the 12-band EQ with a length of 20 (BluetoothOrderConstents). */
    public val declaredLength: Int = payload.size,
) {
    /**
     * True for a frame the earbuds sent: its first byte is `01` ([OneMoreWire.REPLY]'s), the phone's is `11`. The second byte is
     * not checked, as neither of 1MORE's apps checks it (section 2).
     */
    public val fromEarbuds: Boolean get() = head shr 8 == OneMoreWire.REPLY shr 8

    public fun bytes(): ByteArray {
        require(payload.size <= MAX_PAYLOAD) { "payload too long: ${payload.size} bytes" }
        val header = byteArrayOf(
            (head shr 8).toByte(), head.toByte(),
            (command shr 8).toByte(), command.toByte(),
            (declaredLength shr 8).toByte(), declaredLength.toByte(),
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
 * so bytes are kept until a whole frame is there; bytes before a reply's head are dropped. 1MORE's apps
 * never check a reply's first two bytes (the Music app splits by a fixed length per command, Life reads the
 * command at offset 3 of whatever arrives), and Life sends the Q30 and its other newer models `11 01` where
 * it sends older ones `11 00`; so a head is `01` and any second byte (section 2). With `01 00` or `01 01`
 * a frame whose XOR does not match is still taken when its command is one PairFree knows, as 1MORE's apps
 * never check the XOR; with any other second byte the XOR must match. Anything else means the stream is out
 * of step, and the next head starts over.
 */
public class OneMoreParser {
    private var buf = ByteArray(0)

    /** Bytes kept for a frame not yet complete. */
    public val pending: Int get() = buf.size

    /** Every byte that arrived on the channel, for the device report: it tells silent earbuds from replies PairFree could not read. */
    public var received: Long = 0L
        private set

    /** Bytes dropped because they were no frame PairFree reads. */
    public var skipped: Long = 0L
        private set

    /** The first four bytes that arrived (at most four, as the reports' privacy rule allows for a message PairFree does not know). */
    public var firstBytes: ByteArray = ByteArray(0)
        private set

    public fun feed(bytes: ByteArray): List<OneMoreFrame> {
        received += bytes.size
        if (firstBytes.size < 4) firstBytes += bytes.copyOfRange(0, minOf(bytes.size, 4 - firstBytes.size))
        buf += bytes
        val out = mutableListOf<OneMoreFrame>()
        while (true) {
            val start = (0 until buf.size - 1).firstOrNull { u8(it) == HEAD_HIGH }
            if (start == null) {
                // Keep a last 01 for the next read.
                val keep = buf.isNotEmpty() && u8(buf.size - 1) == HEAD_HIGH
                skipped += buf.size - (if (keep) 1 else 0)
                buf = if (keep) byteArrayOf(buf.last()) else ByteArray(0)
                break
            }
            if (start > 0) {
                skipped += start
                buf = buf.copyOfRange(start, buf.size)
            }
            if (buf.size < OneMoreWire.HEADER) break
            val command = (u8(2) shl 8) or u8(3)
            val length = (u8(4) shl 8) or u8(5)
            val checked = OneMoreFrame.xor(buf, 0, OneMoreWire.HEADER - 1) == u8(OneMoreWire.HEADER - 1)
            val known = u8(1) <= 1 && command in OneMoreCommand.NAMES
            if (length > OneMoreFrame.MAX_PAYLOAD || !checked && !known) {
                skipped += 1
                buf = buf.copyOfRange(1, buf.size)
                continue
            }
            val total = OneMoreWire.HEADER + length
            if (buf.size < total) break
            out += OneMoreFrame(command, buf.copyOfRange(OneMoreWire.HEADER, total), (u8(6) shl 8) or u8(7), (u8(0) shl 8) or u8(1))
            buf = buf.copyOfRange(total, buf.size)
        }
        return out
    }

    private fun u8(at: Int): Int = buf[at].toInt() and 0xFF

    private companion object {
        val HEAD_HIGH = OneMoreWire.REPLY shr 8
    }
}
