package io.github.omegadestroyer.pairfree.protocol.jbl

/**
 * One frame of Airoha's RACE commands, which JBL's Airoha models speak (docs/jbl/PROTOCOL.md section 10): 05, the
 * type, the length of the id and payload, the id and the payload, the two-byte fields low byte first. There is no
 * checksum. A response or indication starts its payload with a status byte (0 is fine); module reads and writes
 * ([JblRace.MMI_GET], [JblRace.MMI_SET]) put the module id before it, and NV key reads ([JblRace.NV_READ]) answer
 * with the data's length and the data instead.
 */
public class RacePacket(
    public val id: Int,
    public val payload: ByteArray = ByteArray(0),
    public val type: Int = JblRace.TYPE_COMMAND,
) : JblFrame {
    override fun bytes(): ByteArray {
        val n = payload.size + 2
        require(n <= 0xFFFF) { "payload too long: ${payload.size} bytes" }
        return byteArrayOf(JblRace.START.toByte(), type.toByte(), n.toByte(), (n shr 8).toByte(), id.toByte(), (id shr 8).toByte()) + payload
    }

    private val moduled: Boolean get() = id == JblRace.MMI_GET || id == JblRace.MMI_SET

    /** The module id of a module read or write, or of the setting a config read or write names; -1 for other commands. */
    public val module: Int
        get() = when {
            moduled -> u16(0)
            (id == JblRace.CONFIG_GET || id == JblRace.CONFIG_SET) && type != JblRace.TYPE_COMMAND -> u16(1)
            id == JblRace.CONFIG_GET || id == JblRace.CONFIG_SET -> u16(0)
            else -> -1
        }

    /** A response's or indication's status (0 is fine), or -1 when it has none. */
    public val status: Int
        get() = when {
            type == JblRace.TYPE_COMMAND || type == JblRace.TYPE_COMMAND_NO_RESPONSE || id == JblRace.NV_READ -> -1
            moduled -> u8(2)
            else -> u8(0)
        }

    /** What follows the status and, where there is one, the module id; for an NV key read the key's data. */
    public val data: ByteArray
        get() = when {
            type == JblRace.TYPE_COMMAND || type == JblRace.TYPE_COMMAND_NO_RESPONSE -> payload
            id == JblRace.NV_READ -> {
                val n = u16(0).coerceAtMost(payload.size - 2).coerceAtLeast(0)
                payload.copyOfRange(2, 2 + n)
            }
            moduled -> payload.copyOfRange(minOf(3, payload.size), payload.size)
            id == JblRace.CONFIG_GET || id == JblRace.CONFIG_SET -> payload.copyOfRange(minOf(3, payload.size), payload.size)
            else -> payload.copyOfRange(minOf(1, payload.size), payload.size)
        }

    private fun u8(at: Int): Int = if (at < payload.size) payload[at].toInt() and 0xFF else -1
    private fun u16(at: Int): Int = if (at + 1 < payload.size) u8(at) or (u8(at + 1) shl 8) else -1

    override fun equals(other: Any?): Boolean = other is RacePacket && id == other.id && type == other.type && payload.contentEquals(other.payload)

    override fun hashCode(): Int = (id * 31 + type) * 31 + payload.contentHashCode()

    override fun toString(): String = "RacePacket(${type.toString(16)}, ${id.toString(16).padStart(4, '0')}, ${payload.hex()})"

    public companion object {
        /** A command [id] with the bytes [bytes]. */
        public fun of(id: Int, vararg bytes: Int): RacePacket = RacePacket(id, ByteArray(bytes.size) { bytes[it].toByte() })

        /** A read of module [module] (id 0901), as JBL's app reads noise control. */
        public fun mmiGet(module: Int): RacePacket = of(JblRace.MMI_GET, module and 0xFF, module shr 8)

        /** A read of the common config setting [module] (id 2C83). */
        public fun configGet(module: Int): RacePacket = of(JblRace.CONFIG_GET, module and 0xFF, module shr 8)

        /** A write of the common config setting [module] (id 2C82): its id, then [value]. */
        public fun configSet(module: Int, vararg value: Int): RacePacket = of(JblRace.CONFIG_SET, module and 0xFF, module shr 8, *value)

        /** A read of NV key [key], [length] bytes of it (id 0A00), as Airoha's SDK reads it. */
        public fun nvRead(key: Int, length: Int): RacePacket = of(JblRace.NV_READ, key and 0xFF, key shr 8, length and 0xFF, length shr 8)
    }
}

/**
 * The Airoha chip family, as Airoha's SDK tells it from the chip name the earbuds keep in NV key 1002 (AirohaConnector).
 * Noise control's numbers, and where auto power-off and in-ear detection are kept, differ between them.
 */
internal enum class RaceChip {
    AB155X, AB1562, AB1568, AB158X;

    /** Whether the chip keeps auto power-off and in-ear detection in the common config (the AB1568 and later). */
    val config: Boolean get() = this == AB1568 || this == AB158X

    companion object {
        /** In the SDK's order: AB1562E and AB1563 count as the AB1562, the dual-chip AB1565 and AB1568 as the AB1568. */
        fun of(name: String): RaceChip {
            val n = name.uppercase()
            return when {
                "AB1562" in n || "AB1563" in n -> AB1562
                "AB1565_DUAL_CHIP" in n || "AB1568_DUAL_CHIP" in n -> AB1568
                n.startsWith("AB158") || n.startsWith("AB157") || n.startsWith("MT283") -> AB158X
                "AB1565" in n || "AB1568" in n || n.startsWith("MT") -> AB1568
                else -> AB155X
            }
        }
    }
}

/**
 * Splits what JBL's Airoha models send into [RacePacket]s by each one's length. Bytes before a start byte with a
 * response or indication type are skipped, so a lost byte costs one frame at most. Over GATT a notification can hold
 * a whole frame or part of one, so the bytes are kept until it is whole either way.
 */
public class RaceParser {
    private var buf = ByteArray(0)

    public fun feed(bytes: ByteArray): List<RacePacket> {
        buf += bytes
        val out = mutableListOf<RacePacket>()
        while (true) {
            val start = (0 until buf.size).firstOrNull { startsFrame(it) }
            if (start == null) {
                // Keep a trailing start byte whose type has not come yet.
                buf = if (buf.isNotEmpty() && buf.last() == START) byteArrayOf(START) else ByteArray(0)
                break
            }
            if (start > 0) buf = buf.copyOfRange(start, buf.size)
            if (buf.size < HEADER) break
            val length = (buf[2].toInt() and 0xFF) or ((buf[3].toInt() and 0xFF) shl 8)
            if (length < 2 || length > MAX_LENGTH) {
                buf = buf.copyOfRange(1, buf.size)
                continue
            }
            if (buf.size < 4 + length) break
            val id = (buf[4].toInt() and 0xFF) or ((buf[5].toInt() and 0xFF) shl 8)
            out += RacePacket(id, buf.copyOfRange(HEADER, 4 + length), buf[1].toInt() and 0xFF)
            buf = buf.copyOfRange(4 + length, buf.size)
        }
        return out
    }

    private fun startsFrame(at: Int): Boolean {
        if (buf[at] != START) return false
        if (at + 1 >= buf.size) return true
        val type = buf[at + 1].toInt() and 0xFF
        return type == JblRace.TYPE_RESPONSE || type == JblRace.TYPE_INDICATION || type == JblRace.TYPE_COMMAND_NO_RESPONSE || type == JblRace.TYPE_COMMAND
    }

    private companion object {
        val START = JblRace.START.toByte()
        const val HEADER = 6

        /** Longer than anything PairFree reads: JBL's app asks for at most 1000 bytes of an NV key. */
        const val MAX_LENGTH = 1100
    }
}
