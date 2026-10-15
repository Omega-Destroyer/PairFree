package io.github.omegadestroyer.pairfree.protocol.jbl

/**
 * One Harman command frame (docs/jbl/PROTOCOL.md section 2): AA, the command, the payload's length
 * and the payload. The commands in [JblCommand.WIDE] have a two-byte length, low byte first. There is
 * no checksum.
 */
public class JblPacket(public val command: Int, public val payload: ByteArray = ByteArray(0)) : JblFrame {
    public val wide: Boolean get() = command in JblCommand.WIDE

    override fun bytes(): ByteArray {
        val n = payload.size
        require(n <= if (wide) 0xFFFF else 0xFF) { "payload too long: $n bytes" }
        val header = if (wide) byteArrayOf(START, command.toByte(), n.toByte(), (n shr 8).toByte()) else byteArrayOf(START, command.toByte(), n.toByte())
        return header + payload
    }

    /** The first payload byte, which many commands use as a type or sub-command; -1 when there is none. */
    public val sub: Int get() = if (payload.isEmpty()) -1 else payload[0].toInt() and 0xFF

    /**
     * The records of a feature reply (command 06, type 02): each feature id with its value. Empty for
     * any other packet. A record cut short ends the list, as JBL's app reads it.
     */
    public fun features(): Map<Int, ByteArray> {
        if (command != JblCommand.FEATURE || sub != JblFeature.TYPE_REPLY) return emptyMap()
        val out = linkedMapOf<Int, ByteArray>()
        var at = 1
        while (at + 3 <= payload.size) {
            val id = ((payload[at].toInt() and 0xFF) shl 8) or (payload[at + 1].toInt() and 0xFF)
            val length = payload[at + 2].toInt() and 0xFF
            val end = at + 3 + length
            if (end > payload.size) break
            out[id] = payload.copyOfRange(at + 3, end)
            at = end
        }
        return out
    }

    override fun equals(other: Any?): Boolean = other is JblPacket && command == other.command && payload.contentEquals(other.payload)

    override fun hashCode(): Int = command * 31 + payload.contentHashCode()

    override fun toString(): String = "JblPacket(${command.toString(16).padStart(2, '0')}, ${payload.hex()})"

    public companion object {
        private const val START = JblCommand.START.toByte()

        public fun of(command: Int, vararg bytes: Int): JblPacket = JblPacket(command, ByteArray(bytes.size) { bytes[it].toByte() })

        /** A read of the feature records [ids], each id in two bytes, high byte first. */
        public fun featureGet(ids: List<Int>): JblPacket =
            JblPacket(JblCommand.FEATURE, byteArrayOf(JblFeature.TYPE_GET.toByte()) + ids.flatMap { listOf((it shr 8).toByte(), it.toByte()) })

        /** A write of feature [id]: its id, the value's length and the value. */
        public fun featureSet(id: Int, vararg value: Int): JblPacket =
            JblPacket(
                JblCommand.FEATURE,
                byteArrayOf(JblFeature.TYPE_SET.toByte(), (id shr 8).toByte(), id.toByte(), value.size.toByte()) + ByteArray(value.size) { value[it].toByte() },
            )
    }
}

/**
 * Splits what the earbuds send into [JblPacket]s by each one's length. Over GATT every notification
 * starts a new frame, as JBL's app reads them; pass [newMessage] then, and a frame left incomplete is
 * dropped. Over the serial channel reads come in any size and bytes are kept until a frame is whole.
 * Bytes before an AA are skipped.
 */
public class JblParser {
    private var buf = ByteArray(0)

    /** Bytes kept for a frame not yet complete. */
    public val pending: Int get() = buf.size

    public fun feed(bytes: ByteArray, newMessage: Boolean = false): List<JblPacket> {
        if (newMessage && bytes.isNotEmpty() && bytes[0] == START) buf = ByteArray(0)
        buf += bytes
        val out = mutableListOf<JblPacket>()
        while (true) {
            val start = buf.indexOf(START)
            if (start < 0) {
                buf = ByteArray(0)
                break
            }
            if (start > 0) buf = buf.copyOfRange(start, buf.size)
            if (buf.size < 3) break
            val command = buf[1].toInt() and 0xFF
            val wide = command in JblCommand.WIDE
            val header = if (wide) 4 else 3
            if (buf.size < header) break
            val length = if (wide) (buf[2].toInt() and 0xFF) or ((buf[3].toInt() and 0xFF) shl 8) else buf[2].toInt() and 0xFF
            if (buf.size < header + length) break
            out += JblPacket(command, buf.copyOfRange(header, header + length))
            buf = buf.copyOfRange(header + length, buf.size)
        }
        return out
    }

    private companion object {
        const val START = JblCommand.START.toByte()
    }
}

internal fun ByteArray.hex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
