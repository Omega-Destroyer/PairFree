package io.github.omegadestroyer.pairfree.protocol.cambridge

/**
 * Melomania Connect 2.6.0's GAIA v3 frames (A100, M100, P100, X100; docs/cambridge/PROTOCOL.md section 9): `FF`, version 3, flags (1: XOR
 * byte, 2: two length bytes), the payload length, the vendor, the command word and the payload. The command word is
 * feature << 9 | type << 7 | command.
 */
public data class ConnectPacket(val feature: Int, val type: Int, val command: Int, val payload: ByteArray = byteArrayOf()) {
    public val word: Int get() = feature shl 9 or (type shl 7) or command
    public val isResponse: Boolean get() = type == CambridgeConnectWire.TYPE_RESPONSE
    public val isError: Boolean get() = type == CambridgeConnectWire.TYPE_ERROR

    public fun bytes(checksum: Boolean = false): ByteArray {
        require(feature in 0..0x7f && command in 0..0x7f && payload.size <= MAX_PAYLOAD)
        val extended = payload.size > 0xff
        val flags = (if (checksum) 1 else 0) or (if (extended) 2 else 0)
        val head = byteArrayOf(0xFF.toByte(), CambridgeConnectWire.VERSION.toByte(), flags.toByte()) +
            (if (extended) byteArrayOf((payload.size shr 8).toByte(), payload.size.toByte()) else byteArrayOf(payload.size.toByte())) +
            byteArrayOf((CambridgeConnectWire.VENDOR shr 8).toByte(), CambridgeConnectWire.VENDOR.toByte(), (word shr 8).toByte(), word.toByte())
        val frame = head + payload
        return if (checksum) frame + frame.fold(0) { x, b -> x xor (b.toInt() and 0xff) }.toByte() else frame
    }

    override fun equals(other: Any?): Boolean = other is ConnectPacket && feature == other.feature && type == other.type &&
        command == other.command && payload.contentEquals(other.payload)

    override fun hashCode(): Int = (word * 31) + payload.contentHashCode()

    public companion object {
        public const val MAX_PAYLOAD: Int = 1024
    }
}

/** Bounded stream decoder for [ConnectPacket]s: fragmented or joined frames, the optional XOR, resynchronising after bad data. */
public class ConnectParser {
    private var pending: ByteArray = byteArrayOf()

    public fun feed(bytes: ByteArray): List<ConnectPacket> {
        val result = mutableListOf<ConnectPacket>()
        for (byte in bytes) {
            pending += byte
            while (pending.isNotEmpty()) {
                if (pending[0].toInt() and 0xff != 0xFF) { pending = pending.copyOfRange(1, pending.size); continue }
                if (pending.size < 4) break
                val flags = pending[2].toInt() and 0xff
                val extended = flags and 2 != 0
                if (pending[1].toInt() and 0xff != CambridgeConnectWire.VERSION || flags and 0xfc != 0) { pending = pending.copyOfRange(1, pending.size); continue }
                val lengthBytes = if (extended) 2 else 1
                if (pending.size < 3 + lengthBytes) break
                val length = if (extended) (pending[3].toInt() and 0xff shl 8) or (pending[4].toInt() and 0xff) else pending[3].toInt() and 0xff
                if (length > ConnectPacket.MAX_PAYLOAD) { pending = pending.copyOfRange(1, pending.size); continue }
                val head = 3 + lengthBytes + 4
                val size = head + length + (flags and 1)
                if (pending.size < size) break
                if (flags and 1 != 0 && pending.take(size).fold(0) { x, b -> x xor (b.toInt() and 0xff) } != 0) { pending = pending.copyOfRange(1, pending.size); continue }
                val at = 3 + lengthBytes
                val vendor = (pending[at].toInt() and 0xff shl 8) or (pending[at + 1].toInt() and 0xff)
                if (vendor == CambridgeConnectWire.VENDOR) {
                    val word = (pending[at + 2].toInt() and 0xff shl 8) or (pending[at + 3].toInt() and 0xff)
                    result += ConnectPacket(word shr 9, (word shr 7) and 3, word and 0x7f, pending.copyOfRange(head, head + length))
                }
                pending = pending.copyOfRange(size, pending.size)
            }
        }
        return result
    }
}
