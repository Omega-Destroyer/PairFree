package io.github.omegadestroyer.pairfree.protocol.earfun

/**
 * One frame of EarFun's own protocol (docs/earfun/PROTOCOL.md section 3): `EF`, the [command] (two bytes, high
 * first), the payload's length, the [payload], (length + the payload's bytes) & FF, then `FE`. Both ways alike.
 */
public class EarfunFrame(public val command: Int, public val payload: ByteArray = ByteArray(0)) {
    public fun bytes(): ByteArray {
        require(payload.size <= MAX_PAYLOAD) { "payload too long: ${payload.size} bytes" }
        return byteArrayOf(EarfunCommand.START.toByte(), (command shr 8).toByte(), command.toByte(), payload.size.toByte()) +
            payload + byteArrayOf(checksum(payload).toByte(), EarfunCommand.END.toByte())
    }

    override fun equals(other: Any?): Boolean = other is EarfunFrame && command == other.command && payload.contentEquals(other.payload)

    override fun hashCode(): Int = command * 31 + payload.contentHashCode()

    override fun toString(): String =
        "EarfunFrame(${command.toString(16).padStart(4, '0')} ${EarfunCommand.NAMES[command] ?: "?"}, " +
            "payload=${payload.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }})"

    public companion object {
        /** The length is one byte. */
        internal const val MAX_PAYLOAD = 0xFF

        /** Start, command, length; then the checksum and end after the payload. */
        internal const val OVERHEAD = 6

        /** The length plus every payload byte, as EarFun's app sums them (buildEarfunCommond). */
        public fun checksum(payload: ByteArray): Int = (payload.size + payload.sumOf { it.toInt() and 0xFF }) and 0xFF
    }
}

/**
 * Splits the earbuds' byte stream into [EarfunFrame]s by each one's length byte. Reads come in any size, so bytes
 * are kept until a whole frame is there. A frame whose end byte or checksum does not match means the stream is out
 * of step: the next `EF` starts over, as EarFun's app looks for the next start byte (EarfunProtocolParser.checkData).
 */
public class EarfunParser {
    private var buf = ByteArray(0)

    public fun feed(bytes: ByteArray): List<EarfunFrame> {
        buf += bytes
        val out = mutableListOf<EarfunFrame>()
        while (true) {
            val start = buf.indexOfFirst { (it.toInt() and 0xFF) == EarfunCommand.START }
            if (start < 0) {
                buf = ByteArray(0)
                break
            }
            if (start > 0) buf = buf.copyOfRange(start, buf.size)
            if (buf.size < 4) break
            val length = u8(3)
            val total = length + EarfunFrame.OVERHEAD
            if (buf.size < total) break
            val payload = buf.copyOfRange(4, 4 + length)
            if (u8(total - 1) != EarfunCommand.END || u8(total - 2) != EarfunFrame.checksum(payload)) {
                buf = buf.copyOfRange(1, buf.size)
                continue
            }
            out += EarfunFrame((u8(1) shl 8) or u8(2), payload)
            buf = buf.copyOfRange(total, buf.size)
        }
        return out
    }

    private fun u8(at: Int): Int = buf[at].toInt() and 0xFF
}
