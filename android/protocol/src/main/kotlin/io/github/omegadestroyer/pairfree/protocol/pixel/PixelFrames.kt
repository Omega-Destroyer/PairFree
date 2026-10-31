package io.github.omegadestroyer.pairfree.protocol.pixel

/**
 * Pigweed's HDLC frames, which carry Maestro's RPC packets (docs/pixel/PROTOCOL.md section 2), as pbpctrl's
 * libmaestro writes and reads them (github.com/qzed/pbpctrl, Maximilian Luz, MIT): 7E, the address as a varint
 * whose last byte has bit 0 set, the control byte, the data, a CRC-32 of address to data (little-endian), 7E;
 * 7D escapes 7D and 7E, xor 20.
 */
public object PixelHdlc {
    public const val FLAG: Int = 0x7E
    public const val ESCAPE: Int = 0x7D
    private const val ESCAPE_MASK = 0x20
    private const val CRC_SIZE = 4

    /** One frame as read. */
    public class Frame(public val address: Int, public val control: Int, public val data: ByteArray)

    /** The bytes of a frame to [address] with [data]. */
    public fun encode(address: Int, data: ByteArray, control: Int = PixelWire.HDLC_CONTROL): ByteArray {
        val body = address(address) + byteArrayOf(control.toByte()) + data
        val crc = crc32(body)
        val raw = body + ByteArray(CRC_SIZE) { (crc ushr (8 * it)).toByte() }
        val out = ArrayList<Byte>(raw.size + 8)
        out += FLAG.toByte()
        for (b in raw) {
            val v = b.toInt() and 0xFF
            if (v == FLAG || v == ESCAPE) {
                out += ESCAPE.toByte()
                out += (v xor ESCAPE_MASK).toByte()
            } else {
                out += b
            }
        }
        out += FLAG.toByte()
        return out.toByteArray()
    }

    /** HDLC's address: seven bits a byte from the lowest, shifted left one, the last byte's bit 0 set. */
    public fun address(value: Int): ByteArray {
        val out = mutableListOf<Byte>()
        var v = value.toLong() and 0xFFFFFFFFL
        while (v ushr 7 != 0L) {
            out += ((v and 0x7F) shl 1).toByte()
            v = v ushr 7
        }
        out += (((v and 0x7F) shl 1) or 1).toByte()
        return out.toByteArray()
    }

    /** CRC-32 (IEEE, reflected), as pw_hdlc and pbpctrl compute it. */
    public fun crc32(bytes: ByteArray): Int {
        var crc = -1
        for (b in bytes) crc = TABLE[(crc xor b.toInt()) and 0xFF] xor (crc ushr 8)
        return crc.inv()
    }

    private val TABLE = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor CRC_POLY else c ushr 1 }
        c
    }
    private const val CRC_POLY = 0xEDB88320.toInt()

    /** Splits a byte stream into frames; frames with a bad checksum or address are dropped. */
    public class Decoder {
        private val buf = mutableListOf<Byte>()
        private var inFrame = false
        private var escaped = false

        public fun feed(bytes: ByteArray): List<Frame> {
            val out = mutableListOf<Frame>()
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                when {
                    v == FLAG -> {
                        if (inFrame && buf.isNotEmpty()) frame(buf.toByteArray())?.let { out += it }
                        buf.clear()
                        inFrame = true
                        escaped = false
                    }
                    !inFrame -> Unit
                    v == ESCAPE -> escaped = true
                    else -> {
                        buf += if (escaped) (v xor ESCAPE_MASK).toByte() else b
                        escaped = false
                        if (buf.size > MAX_FRAME) {
                            buf.clear()
                            inFrame = false
                        }
                    }
                }
            }
            return out
        }

        private fun frame(raw: ByteArray): Frame? {
            if (raw.size < 2 + CRC_SIZE) return null
            val body = raw.copyOfRange(0, raw.size - CRC_SIZE)
            val crc = (0 until CRC_SIZE).fold(0) { acc, k -> acc or ((raw[body.size + k].toInt() and 0xFF) shl (8 * k)) }
            if (crc != crc32(body)) return null
            var address = 0L
            var i = 0
            while (i < body.size) {
                val a = body[i].toInt() and 0xFF
                address = address or ((a ushr 1).toLong() shl (7 * i))
                i++
                if (a and 1 == 1) break
                if (i > MAX_ADDRESS_BYTES) return null
            }
            if (i >= body.size) return null
            return Frame(address.toInt(), body[i].toInt() and 0xFF, body.copyOfRange(i + 1, body.size))
        }

        private companion object {
            const val MAX_FRAME = 4096
            const val MAX_ADDRESS_BYTES = 5
        }
    }
}

/**
 * Pigweed's RPC packet (pw.rpc.packet.RpcPacket): [type], [channelId], the service and method ids (pw_rpc's 65599
 * hash of their names), the [payload], the [status] of a reply and the [callId].
 */
public class PixelRpcPacket(
    public val type: Int,
    public val channelId: Int,
    public val serviceId: Int,
    public val methodId: Int,
    public val payload: ByteArray = ByteArray(0),
    public val status: Int = 0,
    public val callId: Int = PixelWire.CALL_ID,
) {
    /** The packet as protobuf, without the fields that are zero, as proto3 leaves them out. */
    public fun encode(): ByteArray {
        val w = ProtoWriter()
        if (type != 0) w.varint(TYPE, type.toLong())
        if (channelId != 0) w.varint(CHANNEL, channelId.toLong())
        w.fixed32(SERVICE, serviceId)
        w.fixed32(METHOD, methodId)
        if (payload.isNotEmpty()) w.bytes(PAYLOAD, payload)
        if (status != 0) w.varint(STATUS, status.toLong())
        if (callId != 0) w.varint(CALL, callId.toLong() and 0xFFFFFFFFL)
        return w.bytes()
    }

    override fun toString(): String =
        "rpc type $type ch $channelId ${PixelRpc.methodName(methodId) ?: methodId.toUInt().toString(16)} status $status" +
            if (payload.isEmpty()) "" else " " + payload.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    public companion object {
        private const val TYPE = 1
        private const val CHANNEL = 2
        private const val SERVICE = 3
        private const val METHOD = 4
        private const val PAYLOAD = 5
        private const val STATUS = 6
        private const val CALL = 7

        public fun parse(bytes: ByteArray): PixelRpcPacket? {
            val f = Proto.parse(bytes) ?: return null
            return PixelRpcPacket(
                type = f.field(TYPE)?.int ?: 0,
                channelId = f.field(CHANNEL)?.int ?: 0,
                serviceId = f.field(SERVICE)?.int ?: return null,
                methodId = f.field(METHOD)?.int ?: return null,
                payload = f.field(PAYLOAD)?.bytes ?: ByteArray(0),
                status = f.field(STATUS)?.int ?: 0,
                callId = f.field(CALL)?.int ?: 0,
            )
        }
    }
}

/** Maestro's ids: pw_rpc names services and methods by the 65599 hash of their names. */
public object PixelRpc {
    /** pw_rpc's hash (pw_tokenizer's 65599 hash, unlimited length): the length, plus each character times 65599^(i+1). */
    public fun hash(name: String): Int {
        var hash = name.length
        var coefficient = HASH_CONSTANT
        for (ch in name) {
            hash += coefficient * ch.code
            coefficient *= HASH_CONSTANT
        }
        return hash
    }

    private const val HASH_CONSTANT = 65599

    public val SERVICE: Int = hash(PixelWire.SERVICE)

    private val METHODS: Map<Int, String> = listOf(
        PixelWire.METHOD_SOFTWARE_INFO, PixelWire.METHOD_HARDWARE_INFO, PixelWire.METHOD_RUNTIME_INFO,
        PixelWire.METHOD_READ_SETTING, PixelWire.METHOD_WRITE_SETTING, PixelWire.METHOD_SETTINGS_CHANGES,
    ).associateBy(::hash)

    public fun methodName(id: Int): String? = METHODS[id]
}

/** A frame of the plain protocol the Pixel Buds (2020) and A-Series use: group, code, a big-endian length, payload. */
public class PixelTlvFrame(public val group: Int, public val code: Int, public val payload: ByteArray) {
    public fun bytes(): ByteArray = byteArrayOf(group.toByte(), code.toByte(), (payload.size ushr 8).toByte(), payload.size.toByte()) + payload

    override fun toString(): String =
        "tlv $group/$code" + if (payload.isEmpty()) "" else " " + payload.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

/** Splits the plain protocol's byte stream into frames by their length, as the app's reader does. */
public class PixelTlvParser {
    private var buf = ByteArray(0)

    public fun feed(bytes: ByteArray): List<PixelTlvFrame> {
        buf += bytes
        val out = mutableListOf<PixelTlvFrame>()
        while (buf.size >= HEADER) {
            val length = ((buf[2].toInt() and 0xFF) shl 8) or (buf[3].toInt() and 0xFF)
            if (length > PixelWire.TLV_MAX_LENGTH) {
                // Out of step: the app gives up on the channel; drop what is held and wait for the next frame.
                buf = ByteArray(0)
                break
            }
            if (buf.size < HEADER + length) break
            out += PixelTlvFrame(buf[0].toInt() and 0xFF, buf[1].toInt() and 0xFF, buf.copyOfRange(HEADER, HEADER + length))
            buf = buf.copyOfRange(HEADER + length, buf.size)
        }
        return out
    }

    private companion object {
        const val HEADER = 4
    }
}
