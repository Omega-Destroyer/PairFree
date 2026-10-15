package io.github.omegadestroyer.pairfree.protocol.jbl

/** A frame to or from JBL earbuds: Harman's AA commands ([JblPacket]) or protocol 4's ([JblP4Packet]). */
public interface JblFrame {
    public fun bytes(): ByteArray
}

/**
 * One frame of JBL's protocol 4 (docs/jbl/PROTOCOL.md section 6): the identifier (DD00, or DD01 for the
 * case), the command, the packet count and index, the payload's length and the payload; the two-byte
 * fields low byte first. PairFree's payloads fit one packet; a reply in several is joined by [JblP4Parser].
 */
public class JblP4Packet(
    public val command: Int,
    public val payload: ByteArray = ByteArray(0),
    public val identifier: Int = JblP4.IDENTIFIER,
) : JblFrame {
    override fun bytes(): ByteArray {
        require(payload.size <= MAX_PAYLOAD) { "payload too long: ${payload.size} bytes" }
        val n = payload.size
        return byteArrayOf(
            identifier.toByte(), (identifier shr 8).toByte(), command.toByte(), (command shr 8).toByte(), 1, 0, n.toByte(), (n shr 8).toByte(),
        ) + payload
    }

    /** A read's or notification's status code (0 is fine), from the first two payload bytes; null for other commands. */
    public val status: Int?
        get() = if ((command == JblP4.GET || command == JblP4.NOTIFICATION || command == JblP4.SET) && payload.size >= 2) u16(payload, 0) else null

    /**
     * The feature records of a read's answer or a notification, after the status code: each feature's id,
     * its value's length (both two bytes, low first) and the value. Empty for anything else, or when the
     * status is not 0, as JBL's app drops those.
     */
    public fun features(): Map<Int, ByteArray> {
        if ((command != JblP4.GET && command != JblP4.NOTIFICATION) || status != 0) return emptyMap()
        val out = linkedMapOf<Int, ByteArray>()
        var at = 2
        while (at + 4 <= payload.size) {
            val id = u16(payload, at)
            val length = u16(payload, at + 2)
            val end = at + 4 + length
            if (end > payload.size) break
            out[id] = payload.copyOfRange(at + 4, end)
            at = end
        }
        return out
    }

    override fun equals(other: Any?): Boolean =
        other is JblP4Packet && command == other.command && identifier == other.identifier && payload.contentEquals(other.payload)

    override fun hashCode(): Int = (command * 31 + identifier) * 31 + payload.contentHashCode()

    override fun toString(): String = "JblP4Packet(${command.toString(16).padStart(4, '0')}, ${payload.hex()})"

    public companion object {
        /** JBL's app splits payloads at its MTU of 512 less 15. */
        private const val MAX_PAYLOAD = 497

        /** A read of [features], each id two bytes, low first. */
        public fun get(features: List<Int>): JblP4Packet =
            JblP4Packet(JblP4.GET, features.flatMap { listOf(it.toByte(), (it shr 8).toByte()) }.toByteArray())

        /** A write of [records], each a feature id and its value. */
        public fun set(vararg records: Pair<Int, ByteArray>): JblP4Packet =
            JblP4Packet(
                JblP4.SET,
                records.fold(ByteArray(0)) { acc, (id, value) ->
                    acc + byteArrayOf(id.toByte(), (id shr 8).toByte(), value.size.toByte(), (value.size shr 8).toByte()) + value
                },
            )

        /** A one-byte status and level, as JBL's StatusLevelSize1Feature writes it: bit 7 the status, bits 0 to 6 the level. */
        public fun statusLevel(on: Boolean, level: Int): ByteArray = byteArrayOf(((if (on) 0x80 else 0) or (level and 0x7F)).toByte())

        internal fun u16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
    }
}

/**
 * Joins protocol 4 packets into frames: each notification is one packet, and a reply in several packets
 * is complete once every index has come, as JBL's app reads them (DeviceInfoList4Cmd.parse). A packet whose
 * length does not match is dropped.
 */
public class JblP4Parser {
    private val parts = sortedMapOf<Int, ByteArray>()
    private var expected = 0

    public fun feed(bytes: ByteArray): List<JblP4Packet> {
        if (bytes.size < HEADER) return emptyList()
        val identifier = JblP4Packet.u16(bytes, 0)
        val command = JblP4Packet.u16(bytes, 2)
        val count = bytes[4].toInt() and 0xFF
        val index = bytes[5].toInt() and 0xFF
        val length = JblP4Packet.u16(bytes, 6)
        if (length != bytes.size - HEADER) return emptyList()
        if (count <= 1) return listOf(JblP4Packet(command, bytes.copyOfRange(HEADER, bytes.size), identifier))
        if (index == 0 || count != expected) {
            parts.clear()
            expected = count
        }
        parts[index] = bytes.copyOfRange(HEADER, bytes.size)
        if (parts.size < count) return emptyList()
        val payload = parts.values.fold(ByteArray(0)) { acc, p -> acc + p }
        parts.clear()
        return listOf(JblP4Packet(command, payload, identifier))
    }

    private companion object {
        const val HEADER = 8
    }
}

/** Reads what JBL earbuds send: protocol 4 frames by their identifier, Harman's AA commands otherwise. */
public class JblReader {
    private val aa = JblParser()
    private val p4 = JblP4Parser()

    public fun feed(bytes: ByteArray, newMessage: Boolean = false): List<JblFrame> =
        if (bytes.size >= 2 && (bytes[1].toInt() and 0xFF) == P4_HIGH && (bytes[0].toInt() and 0xFF) <= 1) p4.feed(bytes) else aa.feed(bytes, newMessage)

    private companion object {
        /** The high byte of DD00 and DD01. */
        const val P4_HIGH = 0xDD
    }
}
