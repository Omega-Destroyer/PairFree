package io.github.omegadestroyer.pairfree.protocol.jbl

/**
 * The packets of Avnera's "LightX" protocol, which the Everest Elite range and the Reflect Aware speak over the standard serial port (docs/jbl/PROTOCOL.md section 12; read from
 * JBL Headphones 5.37.10's `a7` package). A frame is `FF 5A 00 00`, its length in two bytes (high first, the whole frame), a 12-byte packet header and a buffer, and a check byte:
 * the negated sum of every byte before it. The header is the source port (15, Bluetooth) and the destination (64, the application), the opcode (1 read, 2 write; 0x80 says a buffer
 * follows), the status and type, then two little-endian words: the key in the top byte with a 24-bit offset below, and the buffer's length.
 */
public class AvneraPacket(
    public val opcode: Int,
    public val key: Int,
    public val buffer: ByteArray? = null,
    public val status: Int = 0,
    public val offset: Int = 0,
    private val source: Int = BLUETOOTH,
    private val destination: Int = APPLICATION,
) {
    /** The frame as it goes over the serial channel. */
    public fun bytes(): ByteArray {
        val data = buffer ?: ByteArray(0)
        val total = HEADER + HEADER_PACKET + data.size + 1
        val out = ByteArray(total)
        out[0] = 0xFF.toByte()
        out[1] = 0x5A
        out[4] = (total shr 8).toByte()
        out[5] = total.toByte()
        val at = HEADER
        out[at] = source.toByte()
        out[at + 1] = destination.toByte()
        out[at + 2] = (opcode or (if (buffer != null) BUFFER_FLAG else 0)).toByte()
        out[at + 3] = status.toByte()
        val word = (key shl 24) or (offset and 0xFFFFFF)
        for (i in 0 until 4) out[at + 4 + i] = (word shr (8 * i)).toByte()
        for (i in 0 until 4) out[at + 8 + i] = (data.size shr (8 * i)).toByte()
        data.copyInto(out, at + HEADER_PACKET)
        out[total - 1] = check(out, total - 1)
        return out
    }

    /** The buffer's first four bytes as a little-endian number (the app's `x.c`), or 0 without a buffer. */
    public fun number(at: Int = 0): Int {
        val b = buffer ?: return 0
        if (at + 4 > b.size) return 0
        return (0 until 4).sumOf { (b[at + it].toInt() and 0xFF) shl (8 * it) }
    }

    override fun toString(): String = "AvneraPacket(opcode=$opcode key=$key status=$status buffer=${buffer?.joinToString("") { "%02x".format(it) }})"

    public companion object {
        public const val READ: Int = 1
        public const val WRITE: Int = 2
        public const val BLUETOOTH: Int = 15
        public const val APPLICATION: Int = 64
        private const val BUFFER_FLAG = 0x80
        private const val MASK = 0x3F
        private const val HEADER = 6
        private const val HEADER_PACKET = 12

        /** A read of [key]: the app sends 256 zero bytes after the header (`q.m`), or the request's own buffer. */
        public fun read(key: Int, request: ByteArray = ByteArray(256)): AvneraPacket = AvneraPacket(READ, key, request)

        /** A write of [key] with [values] as little-endian words (the app's `k1`). */
        public fun write(key: Int, vararg values: Int): AvneraPacket {
            val data = ByteArray(values.size * 4)
            for ((i, v) in values.withIndex()) for (j in 0 until 4) data[i * 4 + j] = (v shr (8 * j)).toByte()
            return AvneraPacket(WRITE, key, data)
        }

        /** The check byte over [bytes] before [end]. */
        public fun check(bytes: ByteArray, end: Int): Byte {
            var sum = 0
            for (i in 0 until end) sum += bytes[i].toInt()
            return (-sum).toByte()
        }

        /** One packet from the 12-byte header and what follows (`q.b`); null when it is cut short. */
        internal fun parse(b: ByteArray, from: Int, to: Int): AvneraPacket? {
            if (to - from < HEADER_PACKET) return null
            val flags = b[from + 2].toInt() and 0xFF
            val word = (0 until 4).sumOf { (b[from + 4 + it].toLong() and 0xFF) shl (8 * it) }
            val length = (0 until 4).sumOf { (b[from + 8 + it].toLong() and 0xFF) shl (8 * it) }.toInt()
            val buffer = if (flags and BUFFER_FLAG != 0) {
                val n = minOf(length, to - from - HEADER_PACKET).coerceAtLeast(0)
                b.copyOfRange(from + HEADER_PACKET, from + HEADER_PACKET + n)
            } else null
            return AvneraPacket(flags and MASK, (word shr 24).toInt() and 0xFF, buffer, b[from + 3].toInt() and MASK, (word and 0xFFFFFF).toInt(), b[from].toInt() and 0xFF, b[from + 1].toInt() and 0xFF)
        }
    }
}

/** Reassembles [AvneraPacket]s from the serial channel's bytes (`o.j`): frames start with FF 5A 00 00, and a failed check drops the frame. */
public class AvneraReader {
    private var pending = ByteArray(0)

    public fun feed(bytes: ByteArray): List<AvneraPacket> {
        pending += bytes
        val out = ArrayList<AvneraPacket>()
        var at = 0
        while (true) {
            while (at < pending.size && pending[at] != 0xFF.toByte()) at++
            if (pending.size - at < 6) break
            if (pending[at + 1] != 0x5A.toByte() || pending[at + 2] != 0.toByte() || pending[at + 3] != 0.toByte()) {
                at++
                continue
            }
            val total = ((pending[at + 4].toInt() and 0xFF) shl 8) or (pending[at + 5].toInt() and 0xFF)
            if (total < 19) {
                at++
                continue
            }
            if (pending.size - at < total) break
            val end = at + total
            if (AvneraPacket.check(pending.copyOfRange(at, end), total - 1) == pending[end - 1]) AvneraPacket.parse(pending, at + 6, end - 1)?.let { out += it }
            at = end
        }
        pending = pending.copyOfRange(at, pending.size)
        return out
    }
}
