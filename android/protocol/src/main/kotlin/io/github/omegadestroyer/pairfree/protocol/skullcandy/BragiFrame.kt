package io.github.omegadestroyer.pairfree.protocol.skullcandy

/** Bragi PPP wrapper. Secure RPC stays opaque; framing is not decryption or a trust decision. */
public data class BragiFrame(val protocol: Int, val payload: ByteArray, val checksum: Boolean = true) {
    public fun bytes(): ByteArray {
        require(protocol in listOf(SkullcandyWire.RPC, SkullcandyWire.SECURE_RPC) && payload.size <= MAX_PAYLOAD)
        val header = byteArrayOf(SkullcandyWire.START.toByte(), SkullcandyWire.ADDRESS.toByte(),
            if (checksum) SkullcandyWire.FCS_FLAG.toByte() else 0, protocol.toByte(), (payload.size shr 8).toByte(), payload.size.toByte())
        val body = header + crc8(header).toByte() + payload
        if (!checksum) return body
        val crc = crc16(body.copyOfRange(1, body.size))
        return body + byteArrayOf((crc shr 8).toByte(), crc.toByte())
    }

    public fun rpc(): BragiRpc? = if (protocol == SkullcandyWire.RPC) BragiRpc.of(payload) else null

    public companion object {
        /** Settings-only cap: the complete frame fits in the vendor reader's 4096-byte buffer. */
        public const val MAX_PAYLOAD: Int = 4087
        public fun crc8(bytes: ByteArray): Int {
            var crc = SkullcandyWire.CRC8_INITIAL
            for (byte in bytes) {
                crc = crc xor byte.u()
                repeat(8) { crc = (crc ushr 1) xor if (crc and 1 != 0) SkullcandyWire.CRC8_POLYNOMIAL else 0 }
            }
            return crc
        }
        public fun crc16(bytes: ByteArray): Int {
            var crc = SkullcandyWire.CRC16_INITIAL
            for (byte in bytes) {
                crc = crc xor byte.u()
                repeat(8) { crc = (crc ushr 1) xor if (crc and 1 != 0) SkullcandyWire.CRC16_POLYNOMIAL else 0 }
            }
            return crc
        }
    }
}

/** Header CRC validated before trusting the bounded length; payload CRC spans header bytes 1..6 and payload. */
public class BragiParser {
    private var pending: ByteArray = byteArrayOf()
    public fun reset() { pending = byteArrayOf() }
    private fun discard() { pending = pending.drop(1).toByteArray() }
    public fun feed(bytes: ByteArray): List<BragiFrame> {
        val out = mutableListOf<BragiFrame>()
        for (byte in bytes) {
            pending += byte
            while (pending.isNotEmpty()) {
                if (pending[0].u() != SkullcandyWire.START) { discard(); continue }
                if (pending.size < 7) break
                val flags = pending[2].u()
                val protocol = pending[3].u()
                val length = pending[4].u() shl 8 or pending[5].u()
                if (pending[1].u() != SkullcandyWire.ADDRESS || flags !in listOf(0, SkullcandyWire.FCS_FLAG) ||
                    protocol !in listOf(SkullcandyWire.RPC, SkullcandyWire.SECURE_RPC) || length > BragiFrame.MAX_PAYLOAD ||
                    BragiFrame.crc8(pending.copyOf(6)) != pending[6].u()) { discard(); continue }
                val fcs = flags and SkullcandyWire.FCS_FLAG != 0
                val total = 7 + length + if (fcs) 2 else 0
                if (pending.size < total) break
                if (fcs) {
                    val crc = pending[total - 2].u() shl 8 or pending[total - 1].u()
                    if (BragiFrame.crc16(pending.copyOfRange(1, total - 2)) != crc) { discard(); continue }
                }
                out += BragiFrame(protocol, pending.copyOfRange(7, 7 + length), fcs)
                pending = pending.drop(total).toByteArray()
            }
        }
        return out
    }
}

/** RPC correlation keeps service, method and tag. Responses and subscription status are distinct message types. */
public data class BragiRpc(val type: Int, val service: Int, val method: Int, val tag: Int, val body: ByteArray = byteArrayOf(), val final: Boolean = true) {
    public val successfulStatus: Boolean get() = type == SkullcandyRpc.STATUS && body.firstOrNull()?.u() == SkullcandyRpc.SUCCESS
    public fun matches(request: BragiRpc): Boolean = type != SkullcandyRpc.REQUEST && request.type == SkullcandyRpc.REQUEST &&
        service == request.service && method == request.method && tag == request.tag

    public fun bytes(): ByteArray {
        require(type in SkullcandyRpc.REQUEST..SkullcandyRpc.ABORT && service in 0..255 && method in 0..255 && tag in 0..255 && body.size + 4 <= BragiFrame.MAX_PAYLOAD)
        return byteArrayOf(((type shl SkullcandyRpc.TYPE_SHIFT) or if (final) SkullcandyRpc.FINAL else 0).toByte(),
            service.toByte(), method.toByte(), tag.toByte()) + body
    }

    public fun frame(): BragiFrame = BragiFrame(SkullcandyWire.RPC, bytes())

    public companion object {
        public fun of(bytes: ByteArray): BragiRpc? {
            if (bytes.size !in 4..BragiFrame.MAX_PAYLOAD) return null
            val flags = bytes[0].u()
            // Bits outside the SDK's type/final fields are not interpreted as a valid RPC header.
            val mask = (SkullcandyRpc.TYPE_MASK shl SkullcandyRpc.TYPE_SHIFT) or SkullcandyRpc.FINAL
            if (flags and mask.inv() != 0) return null
            return BragiRpc((flags ushr SkullcandyRpc.TYPE_SHIFT) and SkullcandyRpc.TYPE_MASK, bytes[1].u(), bytes[2].u(), bytes[3].u(),
                bytes.copyOfRange(4, bytes.size), flags and SkullcandyRpc.FINAL != 0)
        }
    }
}

/** Harmless RPC builders only. Device-info bytes can contain serials and must not be copied to a report. */
public object BragiReads {
    public fun deviceInfo(tag: Int): BragiRpc = BragiRpc(SkullcandyRpc.REQUEST, SkullcandyCommand.DEVICE_SERVICE,
        SkullcandyCommand.DEVICE_INFO, tag, BragiProto.unsigned(SkullcandyCommand.GENDER_FIELD, 0uL))
    public fun battery(tag: Int): BragiRpc = BragiRpc(SkullcandyRpc.REQUEST, SkullcandyCommand.DEVICE_SERVICE, SkullcandyCommand.BATTERY_INFO, tag)

    /** Missing/ambiguous/invalid field means unknown, never permission to skip authenticated access. */
    public fun authenticatedAccessRequired(reply: BragiRpc): Boolean? {
        if (reply.type != SkullcandyRpc.RESPONSE || reply.service != SkullcandyCommand.DEVICE_SERVICE || reply.method != SkullcandyCommand.DEVICE_INFO) return null
        val fields = BragiProto.parse(reply.body) ?: return null
        val flag = fields.singleOrNull { it.number == SkullcandyCommand.AUTH_REQUIRED_FIELD }?.unsigned() ?: return null
        return when (flag) { 0uL -> false; 1uL -> true; else -> null }
    }
}

internal fun Byte.u(): Int = toInt() and 0xff
