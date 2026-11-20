package io.github.omegadestroyer.pairfree.protocol.skullcandy

/** Protobuf field data, preserving order and unknown fields for later schema-specific decoding. Not for raw logging. */
public data class BragiField(val number: Int, val wireType: Int, val data: ByteArray) {
    public fun unsigned(): ULong? = if (wireType == 0) BragiProto.readVarint(data, 0)?.takeIf { it.second == data.size }?.first else null

    /** The field as a whole number, or null when it is not a varint. */
    public fun int(): Int? = unsigned()?.toInt()

    /** The field as a boolean (a varint of 0 or 1), or null. */
    public fun bool(): Boolean? = when (unsigned()) { 0uL -> false; 1uL -> true; else -> null }

    /** The field as text (a length-delimited field of UTF-8), or null. */
    public fun text(): String? = if (wireType == 2) data.decodeToString() else null

    /** The field as a 32-bit float (a fixed32 field), or null. */
    public fun float(): Float? = if (wireType == 5 && data.size == 4) {
        Float.fromBits((data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8) or ((data[2].toInt() and 0xFF) shl 16) or ((data[3].toInt() and 0xFF) shl 24))
    } else null

    /** The field as a nested message, or null when it is not one. */
    public fun message(): List<BragiField>? = if (wireType == 2) BragiProto.parse(data) else null
}

/** The first field numbered [number], or null. */
public fun List<BragiField>.field(number: Int): BragiField? = firstOrNull { it.number == number }

/** Every field numbered [number], in order (a repeated field). */
public fun List<BragiField>.fields(number: Int): List<BragiField> = filter { it.number == number }

/** Small independently written protobuf wire reader; no vendor schema sources or generated vendor classes. */
public object BragiProto {
    public fun unsigned(field: Int, value: ULong): ByteArray {
        require(field in 1..0x1fffffff)
        return varint(field.toULong() shl 3) + varint(value)
    }

    /** A boolean field (written even when false: the schemas' optional fields need explicit presence). */
    public fun bool(field: Int, value: Boolean): ByteArray = unsigned(field, if (value) 1uL else 0uL)

    /** A length-delimited field holding [bytes] (a nested message, text or raw bytes). */
    public fun bytes(field: Int, bytes: ByteArray): ByteArray = varint(((field.toULong() shl 3) or 2uL)) + varint(bytes.size.toULong()) + bytes

    /** A text field. */
    public fun text(field: Int, value: String): ByteArray = bytes(field, value.encodeToByteArray())

    /** A 32-bit float field. */
    public fun float(field: Int, value: Float): ByteArray {
        val bits = value.toRawBits()
        return varint(((field.toULong() shl 3) or 5uL)) + ByteArray(4) { (bits ushr (8 * it)).toByte() }
    }

    public fun parse(bytes: ByteArray): List<BragiField>? {
        if (bytes.size > BragiFrame.MAX_PAYLOAD) return null
        var index = 0
        val fields = mutableListOf<BragiField>()
        while (index < bytes.size) {
            val key = readVarint(bytes, index) ?: return null
            index = key.second
            val field = key.first shr 3
            if (field == 0uL || field > 0x1fffffffuL) return null
            val wire = (key.first and 7uL).toInt()
            val data: ByteArray
            when (wire) {
                0 -> {
                    val value = readVarint(bytes, index) ?: return null
                    data = bytes.copyOfRange(index, value.second)
                    index = value.second
                }
                1, 5 -> {
                    val size = if (wire == 1) 8 else 4
                    if (index + size > bytes.size) return null
                    data = bytes.copyOfRange(index, index + size)
                    index += size
                }
                2 -> {
                    val length = readVarint(bytes, index) ?: return null
                    index = length.second
                    if (length.first > (bytes.size - index).toULong()) return null
                    val end = index + length.first.toInt()
                    data = bytes.copyOfRange(index, end)
                    index = end
                }
                else -> return null // deprecated groups/reserved types are not part of these schemas
            }
            fields += BragiField(field.toInt(), wire, data)
        }
        return fields
    }

    internal fun readVarint(bytes: ByteArray, from: Int): Pair<ULong, Int>? {
        var value = 0uL
        for (index in 0 until 10) {
            val byte = bytes.getOrNull(from + index)?.u() ?: return null
            if (index == 9 && byte > 1) return null
            value = value or ((byte and 0x7f).toULong() shl (7 * index))
            if (byte and 0x80 == 0) return value to (from + index + 1)
        }
        return null
    }

    private fun varint(input: ULong): ByteArray {
        var value = input
        var out = byteArrayOf()
        do {
            val low = (value and 0x7fuL).toInt()
            value = value shr 7
            out += (low or if (value != 0uL) 0x80 else 0).toByte()
        } while (value != 0uL)
        return out
    }
}
