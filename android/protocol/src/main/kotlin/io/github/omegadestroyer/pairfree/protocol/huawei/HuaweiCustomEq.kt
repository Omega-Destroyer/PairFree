package io.github.omegadestroyer.pairfree.protocol.huawei

import io.github.omegadestroyer.pairfree.protocol.BandEqReport
import kotlin.math.roundToInt

/** Custom EQ slots use signed tenths of a dB. Slot availability comes from the device's TLV 8. */
internal object HuaweiCustomEq {
    val bands = listOf(60, 125, 250, 500, 1000, 2000, 4000, 8000, 12000, 16000)
    const val rangeDb = 6
    const val stepDb = 0.1
    private const val name = "PairFree"
    private const val recordSize = 36
    private const val nameOffset = 12
    private val slotIds = 100..102

    internal data class Slot(val id: Int, val name: String, val gains: List<Int>) {
        fun report(): BandEqReport = BandEqReport(null, null, gains.map { it / 10.0 })
    }

    fun slots(packet: HuaweiPacket?): List<Slot>? {
        val records = packet?.field(8) ?: return null
        if (records.size % recordSize != 0) return null
        val slots = (records.indices step recordSize).map { start ->
            val id = records[start].toInt() and 0xff
            val count = records[start + 1].toInt()
            if (id !in slotIds || count != bands.size) return null
            val label = records.copyOfRange(start + nameOffset, start + recordSize).decodeToString().substringBefore('\u0000')
            val gains = records.copyOfRange(start + 2, start + nameOffset).map(Byte::toInt)
            if (gains.any { it !in -60..60 }) return null
            Slot(id, label, gains)
        }
        return slots.takeIf { it.map(Slot::id).distinct().size == it.size }
    }

    /** The slot records with their names blanked, for the device report: a name is the user's own words. */
    fun unnamed(records: ByteArray): ByteArray {
        val out = records.copyOf()
        for (start in out.indices step recordSize) for (i in start + nameOffset until minOf(start + recordSize, out.size)) out[i] = 0
        return out
    }

    fun slotFor(slots: List<Slot>): Int? =
        slots.firstOrNull { it.name == name }?.id ?: slotIds.firstOrNull { id -> slots.none { it.id == id } }

    fun frame(op: Int, slot: Int, operation: Int, gains: List<Double>): HuaweiPacket? {
        if (gains.size != bands.size || gains.any { !it.isFinite() || it !in -6.0..6.0 }) return null
        val raw = ByteArray(gains.size) { (gains[it] * 10).roundToInt().toByte() }
        return huaweiPacket(op, listOf(
            HuaweiTlv(1, byteArrayOf(slot.toByte())), HuaweiTlv(2, byteArrayOf(bands.size.toByte())),
            HuaweiTlv(5, byteArrayOf(operation.toByte())), HuaweiTlv(3, raw), HuaweiTlv(4, name.encodeToByteArray()),
        ))
    }

    fun allows(packet: HuaweiPacket, slot: Int, operation: Int): Boolean =
        packet.fields.map(HuaweiTlv::tag) == listOf(1, 2, 5, 3, 4) && packet.scalar(1) == slot &&
            packet.scalar(2) == bands.size && packet.scalar(5) == operation &&
            packet.field(3)?.let { data -> data.size == bands.size && data.all { it.toInt() in -60..60 } } == true &&
            packet.field(4)?.contentEquals(name.encodeToByteArray()) == true
}
