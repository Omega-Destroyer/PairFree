package io.github.omegadestroyer.pairfree.protocol.sony

/**
 * One message from Sony earbuds or headphones: its [dataType] ([SonyWire.TABLE1], [SonyWire.TABLE2] or
 * [SonyWire.ACK]), its sequence number [seq] and its [payload], whose first byte is the command
 * (docs/sony/PROTOCOL.md section 2).
 */
public class SonyMessage(public val dataType: Int, public val seq: Int, public val payload: ByteArray) {
    /** The command, the payload's first byte; -1 for an ACK, which has none. */
    public val command: Int get() = if (payload.isEmpty()) -1 else payload[0].toInt() and 0xFF

    override fun equals(other: Any?): Boolean =
        other is SonyMessage && dataType == other.dataType && seq == other.seq && payload.contentEquals(other.payload)

    override fun hashCode(): Int = (dataType * 31 + seq) * 31 + payload.contentHashCode()

    override fun toString(): String = "SonyMessage(type=$dataType, seq=$seq, payload=${payload.hex()})"
}

/** What PairFree sends: a payload for table 1 or table 2 of the earbuds' table set. */
public class SonyOut(public val table: Int, public val payload: ByteArray) {
    public val command: Int get() = payload[0].toInt() and 0xFF

    override fun equals(other: Any?): Boolean = other is SonyOut && table == other.table && payload.contentEquals(other.payload)

    override fun hashCode(): Int = table * 31 + payload.contentHashCode()

    override fun toString(): String = "SonyOut(table=$table, payload=${payload.hex()})"

    public companion object {
        public fun table1(vararg bytes: Int): SonyOut = SonyOut(1, ByteArray(bytes.size) { bytes[it].toByte() })

        public fun table2(vararg bytes: Int): SonyOut = SonyOut(2, ByteArray(bytes.size) { bytes[it].toByte() })
    }
}

/**
 * Sony's frame, as its app writes it: 3E, then the data type, the sequence number, the payload's
 * length (4 bytes, big-endian), the payload and a checksum (the low byte of the sum of everything
 * after 3E), then 3C. Between the two, 3C, 3D and 3E are sent as 3D followed by the byte less 0x10.
 */
public object SonyFrame {
    internal const val START = 0x3E
    internal const val END = 0x3C
    internal const val ESCAPE = 0x3D
    private const val ESCAPED_BIT = 0x10

    /** The frame that sends [payload] as [dataType] with sequence number [seq]. */
    public fun build(dataType: Int, seq: Int, payload: ByteArray): ByteArray {
        val body = ArrayList<Byte>(payload.size + 7)
        body += dataType.toByte()
        body += seq.toByte()
        for (shift in intArrayOf(24, 16, 8, 0)) body += (payload.size shr shift).toByte()
        body += payload.toList()
        body += checksum(body).toByte()
        val out = ArrayList<Byte>(body.size + 4)
        out += START.toByte()
        for (b in body) {
            val v = b.toInt() and 0xFF
            if (v == START || v == END || v == ESCAPE) {
                out += ESCAPE.toByte()
                out += (v and ESCAPED_BIT.inv()).toByte()
            } else {
                out += b
            }
        }
        out += END.toByte()
        return out.toByteArray()
    }

    /** The acknowledgement of a message that came with sequence number [seq]: it carries the other number. */
    public fun ack(seq: Int): ByteArray = build(SonyWire.ACK, 1 - seq, ByteArray(0))

    internal fun checksum(bytes: List<Byte>): Int = bytes.sumOf { it.toInt() and 0xFF } and 0xFF
}

/**
 * Splits the byte stream into [SonyMessage]s. Reads come in any size, so bytes are kept until a
 * frame's end byte is there. A frame whose checksum or length is wrong is dropped.
 */
public class SonyParser {
    private var buf = ByteArray(0)

    /** Bytes kept for a frame not yet complete. */
    public val pending: Int get() = buf.size

    public fun feed(bytes: ByteArray): List<SonyMessage> {
        buf += bytes
        val out = mutableListOf<SonyMessage>()
        while (true) {
            val start = buf.indexOf(SonyFrame.START.toByte())
            if (start < 0) {
                buf = ByteArray(0)
                break
            }
            val end = (start + 1 until buf.size).firstOrNull { buf[it] == SonyFrame.END.toByte() } ?: run {
                if (start > 0) buf = buf.copyOfRange(start, buf.size)
                return out
            }
            decode(buf.copyOfRange(start + 1, end))?.let(out::add)
            buf = buf.copyOfRange(end + 1, buf.size)
        }
        return out
    }

    private fun decode(escaped: ByteArray): SonyMessage? {
        val body = ArrayList<Byte>(escaped.size)
        var i = 0
        while (i < escaped.size) {
            val b = escaped[i]
            if (b == SonyFrame.ESCAPE.toByte()) {
                if (i + 1 >= escaped.size) return null
                body += ((escaped[i + 1].toInt() and 0xFF) or 0x10).toByte()
                i += 2
            } else {
                body += b
                i++
            }
        }
        if (body.size < HEADER + 1) return null
        val length = (2..5).fold(0) { acc, j -> (acc shl 8) or (body[j].toInt() and 0xFF) }
        if (length != body.size - HEADER - 1) return null
        if (SonyFrame.checksum(body.subList(0, body.size - 1)) != (body.last().toInt() and 0xFF)) return null
        return SonyMessage(body[0].toInt() and 0xFF, body[1].toInt() and 0xFF, body.subList(HEADER, body.size - 1).toByteArray())
    }

    private companion object {
        /** Data type, sequence number and length. */
        const val HEADER = 6
    }
}

/**
 * The sequence numbers on one channel, as Sony's app keeps them (docs/sony/PROTOCOL.md section 2).
 * Each message carries the sender's number and waits for its ACK, which carries the other number;
 * the next message then goes with that one. Every message received is acknowledged the same way, and
 * one that repeats the last number received is a resend and is not handled twice.
 */
public class SonyLink {
    private var sendSeq = 0
    private var lastReceived = -1

    /** The frame for [out], with the current sequence number. Send it again unchanged until [acked]. */
    public fun frame(out: SonyOut): ByteArray =
        SonyFrame.build(if (out.table == 2) SonyWire.TABLE2 else SonyWire.TABLE1, sendSeq, out.payload)

    /** Applies an ACK. True when it acknowledges the message in flight, so the next can go. */
    public fun acked(ack: SonyMessage): Boolean {
        if (ack.seq == sendSeq) return false
        sendSeq = ack.seq
        return true
    }

    /** For a data message: the ACK to send back, and whether the message is new (false for a resend). */
    public fun received(message: SonyMessage): Pair<ByteArray, Boolean> {
        val fresh = message.seq != lastReceived
        lastReceived = message.seq
        return SonyFrame.ack(message.seq) to fresh
    }
}

internal fun ByteArray.hex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

internal fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF
