package io.github.omegadestroyer.pairfree.protocol.skullcandy

/** Protobuf field data, preserving order and unknown fields for later schema-specific decoding. Not for raw logging. */
public data class BragiField(val number: Int, val wireType: Int, val data: ByteArray) {
    public fun unsigned(): ULong? = if (wireType == 0) BragiProto.readVarint(data, 0)?.takeIf { it.second == data.size }?.first else null
}

/** Small independently written protobuf wire reader; no vendor schema sources or generated vendor classes. */
public object BragiProto {
    public fun unsigned(field: Int, value: ULong): ByteArray {
        require(field in 1..0x1fffffff)
        return varint(field.toULong() shl 3) + varint(value)
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
