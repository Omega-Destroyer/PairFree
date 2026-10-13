package io.github.omegadestroyer.soundpeter.protocol

/**
 * One frame: `FF 04 00 LEN VENDOR(2) COMMAND(2) PAYLOAD`. Big-endian, no checksum.
 * See docs/soundpeats/PROTOCOL.md section 2.
 */
public class Frame(
    public val vendor: Int,
    public val command: Int,
    public val payload: ByteArray,
) {
    /** True for a reply: the GET number with the top bit set. */
    public val isReply: Boolean get() = command and Wire.REPLY_MASK != 0

    /** For a reply, the GET it answers. The earbuds also send replies unasked, as state updates. */
    public val answers: Int get() = command and Wire.REPLY_MASK.inv()

    override fun equals(other: Any?): Boolean =
        other is Frame && vendor == other.vendor && command == other.command && payload.contentEquals(other.payload)

    override fun hashCode(): Int = (vendor * 31 + command) * 31 + payload.contentHashCode()

    override fun toString(): String = "Frame(vendor=${vendor.hex4()}, command=${command.hex4()}, payload=${payload.hex()})"

    public companion object {
        public const val HEADER_SIZE: Int = 8
        public const val MAX_PAYLOAD: Int = 255

        /** Builds a frame to send over SPP. */
        public fun build(command: Int, payload: ByteArray = ByteArray(0), vendor: Int = Wire.VENDOR_MAIN): ByteArray {
            require(command in 0..0xFFFF) { "command out of range: $command" }
            require(vendor in 0..0xFFFF) { "vendor out of range: $vendor" }
            require(payload.size <= MAX_PAYLOAD) { "payload too long: ${payload.size} bytes" }
            val out = ByteArray(HEADER_SIZE + payload.size)
            out[0] = Wire.SOF.toByte()
            out[1] = Wire.VERSION.toByte()
            out[2] = Wire.FLAGS.toByte()
            out[3] = payload.size.toByte()
            out.putU16(4, vendor)
            out.putU16(6, command)
            payload.copyInto(out, HEADER_SIZE)
            return out
        }

        /** Qualcomm over BLE: the frame without SOF, version, flags and length (docs/soundpeats/PROTOCOL.md section 6). */
        public fun toBlePacket(frame: ByteArray): ByteArray {
            require(frame.size >= HEADER_SIZE) { "frame too short: ${frame.size} bytes" }
            return frame.copyOfRange(4, frame.size)
        }

        /** Reads a Qualcomm BLE notification: vendor, command, payload. */
        public fun fromBlePacket(packet: ByteArray): Frame {
            require(packet.size >= 4) { "BLE packet too short: ${packet.size} bytes" }
            return Frame(packet.u16(0), packet.u16(2), packet.copyOfRange(4, packet.size))
        }
    }
}

internal fun ByteArray.u16(at: Int): Int = ((this[at].toInt() and 0xFF) shl 8) or (this[at + 1].toInt() and 0xFF)

internal fun ByteArray.putU16(at: Int, value: Int) {
    this[at] = (value shr 8).toByte()
    this[at + 1] = value.toByte()
}

internal fun ByteArray.hex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

internal fun Int.hex4(): String = toString(16).uppercase().padStart(4, '0')
