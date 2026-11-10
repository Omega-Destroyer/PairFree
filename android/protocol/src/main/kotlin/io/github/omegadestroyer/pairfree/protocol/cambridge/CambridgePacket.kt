package io.github.omegadestroyer.pairfree.protocol.cambridge

/** GAIA v1 Classic. Payload includes the leading status on acknowledgements. */
public data class CambridgePacket(val command: Int, val payload: ByteArray = byteArrayOf()) {
    public val ack: Boolean get() = command and CambridgeWire.ACK != 0
    public val id: Int get() = command and CambridgeWire.ACK.inv()

    public fun bytes(checksum: Boolean = false): ByteArray {
        require(command in 0..0xffff && payload.size <= CambridgeWire.MAX_PAYLOAD)
        val frame = byteArrayOf(CambridgeWire.START.toByte(), CambridgeWire.VERSION.toByte(), if (checksum) 1 else 0,
            payload.size.toByte(), (CambridgeWire.VENDOR shr 8).toByte(), CambridgeWire.VENDOR.toByte(),
            (command shr 8).toByte(), command.toByte()) + payload
        return if (checksum) frame + frame.fold(0) { xor, b -> xor xor b.u() }.toByte() else frame
    }
}

/** Bounded stream decoder: fragmented/coalesced frames, optional XOR, resynchronisation after malformed data. */
public class CambridgeParser {
    private var pending: ByteArray = byteArrayOf()

    public fun feed(bytes: ByteArray): List<CambridgePacket> {
        val result = mutableListOf<CambridgePacket>()
        // Consume one byte at a time so even an arbitrarily large input never expands the pending buffer unboundedly.
        for (byte in bytes) {
            pending += byte
            while (pending.isNotEmpty()) {
                if (pending[0].u() != CambridgeWire.START) { pending = pending.drop(1).toByteArray(); continue }
                if (pending.size < 4) break
                val flags = pending[2].u()
                val length = pending[3].u()
                if (pending[1].u() != CambridgeWire.VERSION || flags and 0xfe != 0 || length > CambridgeWire.MAX_PAYLOAD) {
                    pending = pending.drop(1).toByteArray(); continue
                }
                val size = 8 + length + (flags and 1)
                if (pending.size < size) break
                if (flags and 1 != 0 && pending.take(size).fold(0) { xor, b -> xor xor b.u() } != 0) {
                    pending = pending.drop(1).toByteArray(); continue
                }
                if ((pending[4].u() shl 8 or pending[5].u()) == CambridgeWire.VENDOR) {
                    result += CambridgePacket(pending[6].u() shl 8 or pending[7].u(), pending.copyOfRange(8, 8 + length))
                }
                pending = pending.drop(size).toByteArray()
            }
        }
        return result
    }
}

internal fun Byte.u(): Int = toInt() and 0xff
