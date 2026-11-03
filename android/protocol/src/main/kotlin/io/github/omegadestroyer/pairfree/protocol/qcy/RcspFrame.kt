package io.github.omegadestroyer.pairfree.protocol.qcy

/**
 * One JieLi RCSP frame (docs/qcy/PROTOCOL.md section 6): `FE DC BA`, a flags byte (bit 7 a command, bit 6
 * an answer wanted), the opcode ([JieliWire]), the parameters' length (two bytes, high first), the
 * parameters and `EF`. A command's parameters start with its sequence number [sn]; an answer's with a
 * [status] (0 is success) and the sequence number of the command it answers. [payload] is what follows.
 */
public class RcspFrame(
    public val command: Boolean,
    public val wantsAnswer: Boolean,
    public val opcode: Int,
    public val sn: Int,
    public val payload: ByteArray = ByteArray(0),
    public val status: Int = 0,
) {
    public fun bytes(): ByteArray {
        val params = (if (command) byteArrayOf(sn.toByte()) else byteArrayOf(status.toByte(), sn.toByte())) + payload
        val flags = (if (command) COMMAND else 0) or (if (wantsAnswer) ANSWER_WANTED else 0)
        return HEAD + byteArrayOf(flags.toByte(), opcode.toByte(), (params.size shr 8).toByte(), params.size.toByte()) + params + byteArrayOf(END.toByte())
    }

    override fun equals(other: Any?): Boolean =
        other is RcspFrame && command == other.command && wantsAnswer == other.wantsAnswer && opcode == other.opcode && sn == other.sn &&
            status == other.status && payload.contentEquals(other.payload)

    override fun hashCode(): Int = ((opcode * 31 + sn) * 31 + status) * 31 + payload.contentHashCode()

    override fun toString(): String =
        "RcspFrame(${if (command) "command" else "answer"} ${opcode.toString(16)}, sn=$sn, status=$status, " +
            "payload=${payload.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }})"

    public companion object {
        internal val HEAD = byteArrayOf(0xFE.toByte(), 0xDC.toByte(), 0xBA.toByte())
        internal const val END = 0xEF
        internal const val COMMAND = 0x80
        internal const val ANSWER_WANTED = 0x40

        /** The answer to a command the earbuds sent: success, with its sequence number. */
        public fun answer(to: RcspFrame): RcspFrame = RcspFrame(command = false, wantsAnswer = false, opcode = to.opcode, sn = to.sn)

        /** A length-type-value attribute, as the ADV settings take them (ParseDataUtil.packLTVPacket). */
        public fun ltv(type: Int, value: ByteArray): ByteArray = byteArrayOf((value.size + 1).toByte(), type.toByte()) + value

        /** The attributes in an ADV or target info answer: type to value. */
        public fun attributes(payload: ByteArray): Map<Int, ByteArray> {
            val out = linkedMapOf<Int, ByteArray>()
            var at = 0
            while (at + 2 <= payload.size) {
                val length = payload[at].toInt() and 0xFF
                if (length < 2 || at + 1 + length > payload.size) break
                out[payload[at + 1].toInt() and 0xFF] = payload.copyOfRange(at + 2, at + 1 + length)
                at += 1 + length
            }
            return out
        }
    }
}

/**
 * Splits what the earbuds send into [RcspFrame]s. A frame can span several notifications, so bytes are kept
 * until it is whole; bytes before `FE DC BA`, and a frame that does not end with `EF`, are dropped.
 */
public class RcspParser {
    private var buf = ByteArray(0)

    public val pending: Int get() = buf.size

    public fun feed(bytes: ByteArray): List<RcspFrame> {
        buf += bytes
        val out = mutableListOf<RcspFrame>()
        while (true) {
            val start = (0..buf.size - RcspFrame.HEAD.size).firstOrNull { i -> RcspFrame.HEAD.indices.all { buf[i + it] == RcspFrame.HEAD[it] } }
            if (start == null) {
                // Keep a possible start of a head for the next read.
                buf = buf.copyOfRange(maxOf(0, buf.size - (RcspFrame.HEAD.size - 1)), buf.size)
                break
            }
            if (start > 0) buf = buf.copyOfRange(start, buf.size)
            if (buf.size < HEADER) break
            val flags = buf[3].toInt() and 0xFF
            val opcode = buf[4].toInt() and 0xFF
            val length = ((buf[5].toInt() and 0xFF) shl 8) or (buf[6].toInt() and 0xFF)
            if (length > MAX_LENGTH) {
                buf = buf.copyOfRange(1, buf.size)
                continue
            }
            val total = HEADER + length + 1
            if (buf.size < total) break
            if (buf[total - 1].toInt() and 0xFF != RcspFrame.END) {
                buf = buf.copyOfRange(1, buf.size)
                continue
            }
            val params = buf.copyOfRange(HEADER, HEADER + length)
            val command = flags and RcspFrame.COMMAND != 0
            val wants = flags and RcspFrame.ANSWER_WANTED != 0
            val frame = when {
                command && params.isNotEmpty() -> RcspFrame(true, wants, opcode, params[0].toInt() and 0xFF, params.copyOfRange(1, params.size))
                !command && params.size >= 2 -> RcspFrame(false, wants, opcode, params[1].toInt() and 0xFF, params.copyOfRange(2, params.size), params[0].toInt() and 0xFF)
                else -> null
            }
            frame?.let(out::add)
            buf = buf.copyOfRange(total, buf.size)
        }
        return out
    }

    private companion object {
        const val HEADER = 7
        const val MAX_LENGTH = 0x1000
    }
}
