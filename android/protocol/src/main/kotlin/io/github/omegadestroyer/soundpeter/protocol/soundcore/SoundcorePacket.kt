package io.github.omegadestroyer.soundpeter.protocol.soundcore

/**
 * One Soundcore packet: a command ([SoundcoreCommand], two bytes as one number) and its body.
 * On the wire: a 5-byte header (one per direction), the command, the total length (2 bytes,
 * little-endian), the body and a checksum, the sum of every byte before it.
 * See docs/soundcore/PROTOCOL.md section 2.
 */
public class SoundcorePacket(
    public val command: Int,
    public val body: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is SoundcorePacket && command == other.command && body.contentEquals(other.body)

    override fun hashCode(): Int = command * 31 + body.contentHashCode()

    override fun toString(): String =
        "SoundcorePacket(command=${command.toString(16).padStart(4, '0')}, body=${body.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }})"

    public companion object {
        /** Builds a packet to send to the earbuds. */
        public fun build(command: Int, body: ByteArray = ByteArray(0)): ByteArray =
            frame(SoundcoreWire.TO_EARBUDS_HEADER, command, body)

        /** A packet as the earbuds send it, for tests and recordings. */
        public fun buildFromEarbuds(command: Int, body: ByteArray = ByteArray(0)): ByteArray =
            frame(SoundcoreWire.FROM_EARBUDS_HEADER, command, body)

        private fun frame(header: ByteArray, command: Int, body: ByteArray): ByteArray {
            require(command in 0..0xFFFF) { "command out of range: $command" }
            val total = SoundcoreWire.HEADER_SIZE + body.size + 1
            require(total <= 0xFFFF) { "body too long: ${body.size} bytes" }
            val out = ByteArray(total)
            header.copyInto(out)
            out[5] = (command shr 8).toByte()
            out[6] = command.toByte()
            out[7] = total.toByte()
            out[8] = (total shr 8).toByte()
            body.copyInto(out, SoundcoreWire.HEADER_SIZE)
            out[total - 1] = checksum(out, total - 1).toByte()
            return out
        }

        /** The sum of the first [count] bytes, modulo 256. */
        internal fun checksum(bytes: ByteArray, count: Int): Int {
            var sum = 0
            for (i in 0 until count) sum += bytes[i].toInt() and 0xFF
            return sum and 0xFF
        }
    }
}

/**
 * Reassembles packets from a byte stream; one read is not one packet. Bytes before a header
 * are dropped, a partial packet waits for the rest, and a packet whose checksum does not match
 * is dropped and counted in [rejected]. By default it reads what the earbuds send.
 */
public class SoundcoreParser(private val header: ByteArray = SoundcoreWire.FROM_EARBUDS_HEADER) {
    private var buffer = ByteArray(0)

    /** Packets dropped because their checksum or length was wrong. */
    public var rejected: Int = 0
        private set

    public fun feed(bytes: ByteArray): List<SoundcorePacket> {
        var buf = buffer + bytes
        val packets = ArrayList<SoundcorePacket>()
        while (true) {
            val start = buf.indexOf(header)
            if (start < 0) {
                // Keep a tail that may be the start of a header split across reads.
                buf = buf.copyOfRange(maxOf(0, buf.size - (header.size - 1)), buf.size)
                break
            }
            if (start > 0) buf = buf.copyOfRange(start, buf.size)
            if (buf.size < SoundcoreWire.HEADER_SIZE) break
            val total = (buf[7].toInt() and 0xFF) or ((buf[8].toInt() and 0xFF) shl 8)
            if (total <= SoundcoreWire.HEADER_SIZE) {
                rejected++
                buf = buf.copyOfRange(1, buf.size)
                continue
            }
            if (buf.size < total) break
            if (SoundcorePacket.checksum(buf, total - 1) == (buf[total - 1].toInt() and 0xFF)) {
                val command = ((buf[5].toInt() and 0xFF) shl 8) or (buf[6].toInt() and 0xFF)
                packets += SoundcorePacket(command, buf.copyOfRange(SoundcoreWire.HEADER_SIZE, total - 1))
                buf = buf.copyOfRange(total, buf.size)
            } else {
                rejected++
                buf = buf.copyOfRange(1, buf.size)
            }
        }
        buffer = buf
        return packets
    }

    /** Drops anything held back, for example after reconnecting. */
    public fun reset() {
        buffer = ByteArray(0)
    }
}

private fun ByteArray.indexOf(sub: ByteArray): Int {
    outer@ for (i in 0..size - sub.size) {
        for (j in sub.indices) if (this[i + j] != sub[j]) continue@outer
        return i
    }
    return -1
}
