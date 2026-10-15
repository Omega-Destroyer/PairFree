package io.github.omegadestroyer.pairfree.protocol.apple

/**
 * One packet of Apple's accessory protocol (docs/apple/PROTOCOL.md section 2): the header, a two-byte
 * opcode (low byte first) and its data. The AirPods send one packet per L2CAP read. Ported from
 * LibrePods (github.com/kavishdevar/librepods, LibrePods contributors, GPL-3.0-or-later), AACPManager.
 */
public class AapPacket(public val opcode: Int, public val data: ByteArray = ByteArray(0)) {
    public fun bytes(): ByteArray = AppleWire.HEADER + byteArrayOf(opcode.toByte(), (opcode shr 8).toByte()) + data

    /**
     * For a control command (opcode 09), its id and value: the four bytes after the id with trailing
     * zeros dropped, or a single 0 when all are zero, as LibrePods' ControlCommand reads them. Null for
     * any other packet.
     */
    public val control: Pair<Int, ByteArray>?
        get() {
            if (opcode != AppleOpcode.CONTROL || data.isEmpty()) return null
            val value = data.copyOfRange(1, minOf(data.size, CONTROL_SIZE)).dropLastWhile { it == 0.toByte() }.toByteArray()
            return (data[0].toInt() and 0xFF) to (if (value.isEmpty()) byteArrayOf(0) else value)
        }

    override fun equals(other: Any?): Boolean = other is AapPacket && opcode == other.opcode && data.contentEquals(other.data)

    override fun hashCode(): Int = opcode * 31 + data.contentHashCode()

    override fun toString(): String = "AapPacket(${opcode.toString(16).padStart(2, '0')}, ${data.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }})"

    public companion object {
        /** The id and up to four value bytes. */
        private const val CONTROL_SIZE = 5

        /** A control command: its id and value, zero-filled to four bytes (LibrePods' createControlCommandPacket). */
        public fun control(id: Int, vararg value: Int): AapPacket =
            AapPacket(AppleOpcode.CONTROL, ByteArray(CONTROL_SIZE).also { d ->
                d[0] = id.toByte()
                value.take(CONTROL_SIZE - 1).forEachIndexed { i, v -> d[i + 1] = v.toByte() }
            })

        /** A packet whose opcode and data are given together, as [AppleWire.FEATURE_FLAGS] and [AppleWire.NOTIFICATIONS] are. */
        public fun of(opcodeAndData: ByteArray): AapPacket =
            AapPacket((opcodeAndData[0].toInt() and 0xFF) or ((opcodeAndData[1].toInt() and 0xFF) shl 8), opcodeAndData.copyOfRange(2, opcodeAndData.size))

        /** One packet as the AirPods send it, or null for anything without the header. */
        public fun parse(bytes: ByteArray): AapPacket? {
            val header = AppleWire.HEADER
            if (bytes.size < header.size + 2) return null
            for (i in header.indices) if (bytes[i] != header[i]) return null
            val opcode = (bytes[header.size].toInt() and 0xFF) or ((bytes[header.size + 1].toInt() and 0xFF) shl 8)
            return AapPacket(opcode, bytes.copyOfRange(header.size + 2, bytes.size))
        }
    }
}
