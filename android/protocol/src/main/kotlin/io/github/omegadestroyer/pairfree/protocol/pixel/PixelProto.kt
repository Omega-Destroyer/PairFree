package io.github.omegadestroyer.pairfree.protocol.pixel

/**
 * Protobuf's wire format, as much as the Pixel Buds' messages need: varints, 32- and 64-bit values and
 * length-delimited fields (docs/pixel/PROTOCOL.md section 2). Fields are written in the order given.
 */
public class ProtoWriter {
    private var buf = ByteArray(32)
    private var size = 0

    public fun varint(field: Int, value: Long): ProtoWriter = apply {
        tag(field, VARINT)
        raw(value)
    }

    public fun bool(field: Int, on: Boolean): ProtoWriter = varint(field, if (on) 1 else 0)

    /** An int32 or enum: negative values take ten bytes, as protobuf sends them. */
    public fun int32(field: Int, value: Int): ProtoWriter = varint(field, value.toLong())

    /** A sint32, zigzagged. */
    public fun sint32(field: Int, value: Int): ProtoWriter = varint(field, ((value shl 1) xor (value shr 31)).toLong() and 0xFFFFFFFFL)

    public fun fixed32(field: Int, value: Int): ProtoWriter = apply {
        tag(field, FIXED32)
        for (i in 0 until 4) put((value ushr (8 * i)).toByte())
    }

    public fun float(field: Int, value: Float): ProtoWriter = fixed32(field, value.toRawBits())

    public fun bytes(field: Int, value: ByteArray): ProtoWriter = apply {
        tag(field, LEN)
        raw(value.size.toLong())
        for (b in value) put(b)
    }

    public fun message(field: Int, value: ProtoWriter): ProtoWriter = bytes(field, value.bytes())

    public fun bytes(): ByteArray = buf.copyOf(size)

    private fun tag(field: Int, wire: Int) = raw(((field shl 3) or wire).toLong())

    private fun raw(value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            put(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        put(v.toByte())
    }

    private fun put(b: Byte) {
        if (size == buf.size) buf = buf.copyOf(buf.size * 2)
        buf[size++] = b
    }

    public companion object {
        public const val VARINT: Int = 0
        public const val FIXED64: Int = 1
        public const val LEN: Int = 2
        public const val FIXED32: Int = 5
    }
}

/** One field as read: its number and wire type, and its value as a number ([long]) or bytes ([bytes]). */
public class ProtoField(public val number: Int, public val wire: Int, public val long: Long, public val bytes: ByteArray?) {
    public val int: Int get() = long.toInt()
    public val bool: Boolean get() = long != 0L

    /** A sint32's value. */
    public val sint32: Int get() = (long.toInt() ushr 1) xor -(long.toInt() and 1)

    public val float: Float get() = Float.fromBits(long.toInt())

    /** The fields of an embedded message, or null when the bytes are not one. */
    public fun message(): List<ProtoField>? = bytes?.let(Proto::parse)

    public fun text(): String? = bytes?.decodeToString()
}

public object Proto {
    /** The fields of [bytes] in order, or null when they are not a well-formed message. */
    public fun parse(bytes: ByteArray): List<ProtoField>? {
        val out = mutableListOf<ProtoField>()
        var i = 0
        fun varint(): Long? {
            var r = 0L
            var shift = 0
            while (i < bytes.size && shift < 64) {
                val b = bytes[i++].toInt() and 0xFF
                r = r or ((b and 0x7F).toLong() shl shift)
                if (b < 0x80) return r
                shift += 7
            }
            return null
        }
        while (i < bytes.size) {
            val key = varint() ?: return null
            val number = (key ushr 3).toInt()
            if (number <= 0) return null
            when (val wire = (key and 7).toInt()) {
                ProtoWriter.VARINT -> out += ProtoField(number, wire, varint() ?: return null, null)
                ProtoWriter.FIXED32 -> {
                    if (i + 4 > bytes.size) return null
                    out += ProtoField(number, wire, le(bytes, i, 4), null)
                    i += 4
                }
                ProtoWriter.FIXED64 -> {
                    if (i + 8 > bytes.size) return null
                    out += ProtoField(number, wire, le(bytes, i, 8), null)
                    i += 8
                }
                ProtoWriter.LEN -> {
                    val n = varint() ?: return null
                    if (n < 0 || i + n > bytes.size) return null
                    out += ProtoField(number, wire, n, bytes.copyOfRange(i, i + n.toInt()))
                    i += n.toInt()
                }
                else -> return null
            }
        }
        return out
    }

    private fun le(b: ByteArray, at: Int, n: Int): Long {
        var r = 0L
        for (k in 0 until n) r = r or ((b[at + k].toLong() and 0xFF) shl (8 * k))
        return r
    }
}

/** The last field numbered [number], or null. */
public fun List<ProtoField>.field(number: Int): ProtoField? = lastOrNull { it.number == number }
