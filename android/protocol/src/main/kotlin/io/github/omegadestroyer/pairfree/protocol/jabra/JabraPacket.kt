package io.github.omegadestroyer.pairfree.protocol.jabra

/**
 * One GNP packet of Jabra's Sound+ service over RFCOMM (docs/jabra/PROTOCOL.md, "GNP transport and framing"): destination, source,
 * a sequence byte, `(attribute << 6) | total length`, the main command, then the payload, which normally starts with the subcommand.
 * There is no checksum and no escaping; the total length counts the five header bytes and is at most 63.
 */
public class JabraPacket(
    public val destination: Int,
    public val source: Int,
    public val sequence: Int,
    public val attribute: Int,
    public val main: Int,
    public val payload: ByteArray,
) {
    init {
        require(attribute in 0..3) { "attribute $attribute" }
        require(HEADER + payload.size <= MAX_LENGTH) { "a packet of ${HEADER + payload.size} bytes" }
    }

    /** The subcommand, or null for the groups that have none (ACK, ECHO, POLL). */
    public val sub: Int? get() = payload.firstOrNull()?.toInt()?.and(0xFF)

    /** The payload after the subcommand byte. */
    public val body: ByteArray get() = if (payload.isEmpty()) payload else payload.copyOfRange(1, payload.size)

    public val isEvent: Boolean get() = attribute == EVENT
    public val isRead: Boolean get() = attribute == READ
    public val isWrite: Boolean get() = attribute == WRITE
    public val isReply: Boolean get() = attribute == REPLY

    /** main and subcommand as one number, to match a reply to the request it answers. */
    public val key: Int get() = (main shl 8) or (sub ?: 0xFF)

    public fun bytes(): ByteArray {
        val total = HEADER + payload.size
        val out = ByteArray(total)
        out[0] = destination.toByte()
        out[1] = source.toByte()
        out[2] = sequence.toByte()
        out[3] = ((attribute shl 6) or total).toByte()
        out[4] = main.toByte()
        payload.copyInto(out, HEADER)
        return out
    }

    override fun toString(): String = "GNP ${NAMES[attribute]} ${hex(main)}/${sub?.let { hex(it) } ?: "--"} seq ${hex(sequence)} ${destination}<-${source} ${payload.drop(1).joinToString("") { hex(it.toInt() and 0xFF) }}"

    public companion object {
        public const val EVENT: Int = 0
        public const val READ: Int = 1
        public const val WRITE: Int = 2
        public const val REPLY: Int = 3
        public const val HEADER: Int = 5
        public const val MAX_LENGTH: Int = 63
        private val NAMES = listOf("event", "read", "write", "reply")

        private fun hex(v: Int): String = v.toString(16).padStart(2, '0')

        /** A packet for the earbuds: the app's own address as the source (the Sound+ bridge rewrites the SDK's 1 and 0 to 4 and 9). */
        public fun of(destination: Int, sequence: Int, attribute: Int, main: Int, vararg payload: Int): JabraPacket =
            JabraPacket(destination, JabraWire.SOURCE, sequence, attribute, main, ByteArray(payload.size) { payload[it].toByte() })
    }
}

/**
 * Splits the RFCOMM stream into packets by byte 3's length, allowing reads that cut a packet or hold several, as Jabra's own parser does.
 * The frame has no start byte, so after a wrong length the stream cannot be found again; a length below the header size is dropped a byte at a time.
 */
public class JabraParser {
    private var buffer = ByteArray(0)

    public fun feed(bytes: ByteArray): List<JabraPacket> {
        buffer += bytes
        val out = mutableListOf<JabraPacket>()
        while (buffer.size >= 4) {
            val total = buffer[3].toInt() and 0x3F
            if (total < JabraPacket.HEADER) {
                buffer = buffer.copyOfRange(1, buffer.size)
                continue
            }
            if (buffer.size < total) break
            val b = buffer
            out += JabraPacket(
                destination = b[0].toInt() and 0xFF,
                source = b[1].toInt() and 0xFF,
                sequence = b[2].toInt() and 0xFF,
                attribute = (b[3].toInt() and 0xFF) shr 6,
                main = b[4].toInt() and 0xFF,
                payload = b.copyOfRange(JabraPacket.HEADER, total),
            )
            buffer = b.copyOfRange(total, b.size)
        }
        return out
    }
}
