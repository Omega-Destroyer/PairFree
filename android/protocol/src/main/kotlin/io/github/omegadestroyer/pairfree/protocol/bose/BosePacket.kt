package io.github.omegadestroyer.pairfree.protocol.bose

/**
 * One BMAP packet (docs/bose/PROTOCOL.md section 2): a function ([BoseFunction], its block and number),
 * an [operator] ([BoseOperator]) and a payload. On the wire it is the block, the function, a flags byte
 * whose low four bits are the operator (the rest, device and port, are 0 from a phone), the payload's
 * length and the payload. There is no start byte and no checksum.
 */
public class BosePacket(public val function: Int, public val operator: Int, public val payload: ByteArray = ByteArray(0)) {
    public val block: Int get() = BoseFunction.block(function)

    public fun bytes(): ByteArray {
        require(payload.size <= MAX_PAYLOAD) { "payload too long: ${payload.size} bytes" }
        return byteArrayOf(block.toByte(), BoseFunction.function(function).toByte(), (operator and OPERATOR_MASK).toByte(), payload.size.toByte()) + payload
    }

    override fun equals(other: Any?): Boolean =
        other is BosePacket && function == other.function && operator == other.operator && payload.contentEquals(other.payload)

    override fun hashCode(): Int = (function * 31 + operator) * 31 + payload.contentHashCode()

    override fun toString(): String =
        "BosePacket(${block}.${BoseFunction.function(function)} ${BoseFunction.NAMES[function] ?: "?"}, op=$operator, payload=${payload.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }})"

    public companion object {
        internal const val HEADER = 4
        internal const val OPERATOR_MASK = 0x0F
        private const val MAX_PAYLOAD = 0xFF

        public fun get(function: Int): BosePacket = BosePacket(function, BoseOperator.GET)

        public fun setGet(function: Int, vararg bytes: Int): BosePacket = BosePacket(function, BoseOperator.SETGET, ByteArray(bytes.size) { bytes[it].toByte() })

        public fun start(function: Int, vararg bytes: Int): BosePacket = BosePacket(function, BoseOperator.START, ByteArray(bytes.size) { bytes[it].toByte() })
    }
}

/**
 * Splits the byte stream into [BosePacket]s by each one's length byte. Reads come in any size, so
 * bytes are kept until a whole packet is there. A packet whose operator is not one of BMAP's eight
 * means the stream is out of step; the bytes kept are dropped, as bosectl stops reading there.
 */
public class BoseParser {
    private var buf = ByteArray(0)

    /** Bytes kept for a packet not yet complete. */
    public val pending: Int get() = buf.size

    public fun feed(bytes: ByteArray): List<BosePacket> {
        buf += bytes
        val out = mutableListOf<BosePacket>()
        while (buf.size >= BosePacket.HEADER) {
            val operator = buf[2].toInt() and BosePacket.OPERATOR_MASK
            if (operator > BoseOperator.PROCESSING) {
                buf = ByteArray(0)
                break
            }
            val length = buf[3].toInt() and 0xFF
            if (buf.size < BosePacket.HEADER + length) break
            val function = BoseFunction.of(buf[0].toInt() and 0xFF, buf[1].toInt() and 0xFF)
            out += BosePacket(function, operator, buf.copyOfRange(BosePacket.HEADER, BosePacket.HEADER + length))
            buf = buf.copyOfRange(BosePacket.HEADER + length, buf.size)
        }
        return out
    }
}
