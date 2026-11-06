package io.github.omegadestroyer.pairfree.protocol.huawei

/** One ordered TLV; duplicates are preserved and lengths are unsigned bytes. */
public data class HuaweiTlv(val tag: Int, val value: ByteArray = byteArrayOf()) {
    public fun bytes(): ByteArray {
        require(tag in 0..255 && value.size <= 255)
        return byteArrayOf(tag.toByte(), value.size.toByte()) + value
    }
}

/** Control-zero MBB frame. No global status byte is removed from its TLV body. */
public data class HuaweiPacket(val service: Int, val command: Int, val fields: List<HuaweiTlv>) {
    public fun bytes(): ByteArray {
        require(service in 0..255 && command in 0..255)
        val payload = fields.fold(byteArrayOf()) { bytes, field -> bytes + field.bytes() }
        val length = payload.size + 3
        require(length <= MAX_BODY)
        val frame = byteArrayOf(HuaweiWire.START.toByte(), (length shr 8).toByte(), length.toByte(), HuaweiWire.CONTROL.toByte(),
            service.toByte(), command.toByte()) + payload
        val crc = crc16(frame)
        return frame + byteArrayOf((crc shr 8).toByte(), crc.toByte())
    }

    public companion object {
        /** Local bound for settings traffic, deliberately smaller than the wire's u16 maximum. Not an OTA codec. */
        public const val MAX_BODY: Int = 4096

        public fun crc16(bytes: ByteArray): Int {
            var crc = HuaweiWire.CRC_INITIAL
            for (byte in bytes) {
                crc = crc xor (byte.u() shl 8)
                repeat(8) { crc = ((crc shl 1) xor if (crc and 0x8000 != 0) HuaweiWire.CRC_POLYNOMIAL else 0) and 0xffff }
            }
            return crc
        }
    }
}

/** Bounded Classic stream reassembly, strict CRC, control and complete-TLV validation. */
public class HuaweiParser {
    private var pending: ByteArray = byteArrayOf()

    public fun feed(bytes: ByteArray): List<HuaweiPacket> {
        val out = mutableListOf<HuaweiPacket>()
        for (byte in bytes) {
            pending += byte
            while (pending.isNotEmpty()) {
                if (pending[0].u() != HuaweiWire.START) { discard(); continue }
                if (pending.size < 4) break
                val length = pending[1].u() shl 8 or pending[2].u()
                if (length !in 3..HuaweiPacket.MAX_BODY || pending[3].u() != HuaweiWire.CONTROL) { discard(); continue }
                val total = length + 5
                if (pending.size < total) break
                val frame = pending.copyOf(total)
                val expected = frame[total - 2].u() shl 8 or frame[total - 1].u()
                if (HuaweiPacket.crc16(frame.copyOf(total - 2)) != expected) { discard(); continue }
                fields(frame, total - 2)?.let { out += HuaweiPacket(frame[4].u(), frame[5].u(), it) }
                pending = pending.drop(total).toByteArray()
            }
        }
        return out
    }

    public fun reset() { pending = byteArrayOf() }
    private fun discard() { pending = pending.drop(1).toByteArray() }

    private fun fields(frame: ByteArray, end: Int): List<HuaweiTlv>? {
        var index = 6
        val out = mutableListOf<HuaweiTlv>()
        while (index < end) {
            if (index + 2 > end) return null
            val length = frame[index + 1].u()
            if (index + 2 + length > end) return null
            out += HuaweiTlv(frame[index].u(), frame.copyOfRange(index + 2, index + 2 + length))
            index += length + 2
        }
        return out
    }
}

/** Primary-source battery query, without broad identity/capability or big-data requests. */
public object HuaweiReads {
    public fun battery(): HuaweiPacket = HuaweiPacket(HuaweiCommand.BATTERY_SERVICE, HuaweiCommand.BATTERY_READ,
        listOf(HuaweiTlv(HuaweiCommand.GLOBAL_TAG)))
}

/** Battery tags only. Component ordering is additionally checked against OpenFreebuds, facts only. */
public data class HuaweiBattery(val global: Int?, val left: Int?, val right: Int?, val case: Int?) {
    public companion object {
        public fun of(packet: HuaweiPacket): HuaweiBattery? {
            if (packet.service != HuaweiCommand.BATTERY_SERVICE || packet.command !in listOf(HuaweiCommand.BATTERY_READ, HuaweiCommand.BATTERY_NOTIFY)) return null
            fun field(tag: Int): ByteArray? = packet.fields.singleOrNull { it.tag == tag }?.value
            fun percent(byte: Byte?): Int? = byte?.u()?.takeIf { it in 0..100 }
            val components = field(HuaweiCommand.COMPONENT_TAG)?.takeIf { it.size == 3 }
            return HuaweiBattery(percent(field(HuaweiCommand.GLOBAL_TAG)?.singleOrNull()),
                percent(components?.get(0)), percent(components?.get(1)), percent(components?.get(2)))
        }
    }
}

private fun Byte.u(): Int = toInt() and 0xff
