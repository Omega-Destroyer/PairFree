package io.github.omegadestroyer.pairfree.protocol.nothing

/**
 * One Nothing frame (docs/nothing/PROTOCOL.md section 2): `55`, the control field, the command, the
 * payload's length (each two bytes, low byte first), the frame sequence number [fsn], the payload, and
 * a CRC-16 when the control field says so. The control field carries the response code (bits 0 to 4;
 * 0 is success), the CRC bit (0x20), the multi-frame bit (0x40) and the device type (bits 8 to 11).
 */
public class NothingFrame(
    public val command: Int,
    public val payload: ByteArray = ByteArray(0),
    public val control: Int = NothingWire.CONTROL_SENT,
    public val fsn: Int = 0,
) {
    /** The response code: 0 success, 1 failure, 2 not activated, 3 not supported, 4 invalid parameter. */
    public val responseCode: Int get() = control and RESPONSE_CODE

    public val ok: Boolean get() = responseCode == 0

    public val hasCrc: Boolean get() = control and CRC_BIT != 0

    /** True for a frame the earbuds start, such as a battery event: the command keeps bit 15. */
    public val isRequest: Boolean get() = command and REQUEST_BIT != 0

    /** The command this frame answers: a reply carries it with bit 15 cleared ([NothingWire.REPLY_MASK]). */
    public val answers: Int get() = command or REQUEST_BIT

    public fun bytes(): ByteArray {
        require(payload.size <= MAX_PAYLOAD) { "payload too long: ${payload.size} bytes" }
        val head = byteArrayOf(
            NothingWire.START.toByte(),
            control.toByte(), (control shr 8).toByte(),
            command.toByte(), (command shr 8).toByte(),
            payload.size.toByte(), (payload.size shr 8).toByte(),
            fsn.toByte(),
        ) + payload
        if (!hasCrc) return head
        val crc = crc16(head)
        return head + byteArrayOf(crc.toByte(), (crc shr 8).toByte())
    }

    override fun equals(other: Any?): Boolean =
        other is NothingFrame && command == other.command && control == other.control && fsn == other.fsn && payload.contentEquals(other.payload)

    override fun hashCode(): Int = ((command * 31 + control) * 31 + fsn) * 31 + payload.contentHashCode()

    override fun toString(): String =
        "NothingFrame(${command.toString(16).padStart(4, '0')} ${NothingCommand.NAMES[command] ?: NothingCommand.NAMES[answers] ?: "?"}, " +
            "rsp=$responseCode, fsn=$fsn, payload=${payload.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }})"

    public companion object {
        internal const val HEADER = 8
        internal const val CRC_SIZE = 2
        internal const val RESPONSE_CODE = 0x1F
        internal const val CRC_BIT = 0x20
        internal const val REQUEST_BIT = 0x8000

        /** Longer than anything the earbuds send; a length past it means the stream is out of step. */
        internal const val MAX_PAYLOAD = 0x0800

        /** The control field for frames from the phone, with or without a CRC (the Ear (1) takes none). */
        public fun control(crc: Boolean): Int = if (crc) NothingWire.CONTROL_SENT else NothingWire.CONTROL_SENT and CRC_BIT.inv()

        /** CRC-16/MODBUS, as Nothing X's Utils.obtainCrc16 computes it: reflected polynomial 0xA001, initial 0xFFFF. */
        public fun crc16(bytes: ByteArray, from: Int = 0, to: Int = bytes.size): Int {
            var crc = 0xFFFF
            for (i in from until to) {
                crc = crc xor (bytes[i].toInt() and 0xFF)
                repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
            }
            return crc
        }
    }
}

/**
 * Splits the byte stream into [NothingFrame]s by each one's length field. Reads come in any size, so
 * bytes are kept until a whole frame is there. Bytes before a `55` are dropped, and so is a frame whose
 * CRC does not match or whose length is past any real one: the stream is out of step, and the next `55`
 * starts over.
 */
public class NothingParser {
    private var buf = ByteArray(0)

    /** Bytes kept for a frame not yet complete. */
    public val pending: Int get() = buf.size

    public fun feed(bytes: ByteArray): List<NothingFrame> {
        buf += bytes
        val out = mutableListOf<NothingFrame>()
        while (true) {
            val start = buf.indexOf(NothingWire.START.toByte())
            if (start < 0) {
                buf = ByteArray(0)
                break
            }
            if (start > 0) buf = buf.copyOfRange(start, buf.size)
            if (buf.size < NothingFrame.HEADER) break
            val control = u8(1) or (u8(2) shl 8)
            val command = u8(3) or (u8(4) shl 8)
            val length = u8(5) or (u8(6) shl 8)
            if (length > NothingFrame.MAX_PAYLOAD) {
                buf = buf.copyOfRange(1, buf.size)
                continue
            }
            val crc = control and NothingFrame.CRC_BIT != 0
            // Without a CRC nothing checks the frame: take it only with a control field Nothing's devices use.
            if (!crc && !plausible(control)) {
                buf = buf.copyOfRange(1, buf.size)
                continue
            }
            val total = NothingFrame.HEADER + length + if (crc) NothingFrame.CRC_SIZE else 0
            if (buf.size < total) break
            if (crc) {
                val got = u8(total - 2) or (u8(total - 1) shl 8)
                if (got != NothingFrame.crc16(buf, 0, total - 2)) {
                    buf = buf.copyOfRange(1, buf.size)
                    continue
                }
            }
            out += NothingFrame(command, buf.copyOfRange(NothingFrame.HEADER, NothingFrame.HEADER + length), control, u8(7))
            buf = buf.copyOfRange(total, buf.size)
        }
        return out
    }

    private fun u8(at: Int): Int = buf[at].toInt() and 0xFF

    /** A control field with a device type of earbuds or a watch, a known response code and no other bits. */
    private fun plausible(control: Int): Boolean =
        control and UNUSED_BITS == 0 && (control shr 8) and 0x0F in DEVICE_TYPES && control and NothingFrame.RESPONSE_CODE <= MAX_RESPONSE_CODE

    private companion object {
        const val UNUSED_BITS = 0xF080
        val DEVICE_TYPES = 1..2
        const val MAX_RESPONSE_CODE = 12
    }
}
