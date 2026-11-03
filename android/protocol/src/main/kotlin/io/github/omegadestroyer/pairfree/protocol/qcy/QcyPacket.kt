package io.github.omegadestroyer.pairfree.protocol.qcy

/** One item of a QCY packet: its id ([QcyItem]) and its value. */
public class QcyEntry(public val item: Int, public val value: ByteArray) {
    public fun u8(at: Int): Int = value[at].toInt() and 0xFF

    override fun equals(other: Any?): Boolean = other is QcyEntry && item == other.item && value.contentEquals(other.value)
    override fun hashCode(): Int = item * 31 + value.contentHashCode()
    override fun toString(): String =
        "${QcyItem.NAMES[item] ?: item.toString(16)}(${value.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }})"
}

/**
 * QCY's packets on the command and settings characteristics (docs/qcy/PROTOCOL.md section 3): `FF`, the
 * length of everything after it, then one or more items, each its id, the value's length and the value.
 */
public object QcyPacket {
    public const val START: Int = 0xFF

    /** A packet of [entries]. */
    public fun build(vararg entries: QcyEntry): ByteArray {
        val body = entries.fold(ByteArray(0)) { acc, e -> acc + byteArrayOf(e.item.toByte(), e.value.size.toByte()) + e.value }
        require(body.size <= 0xFF) { "packet too long: ${body.size} bytes" }
        return byteArrayOf(START.toByte(), body.size.toByte()) + body
    }

    /** A packet of one item with [value]. */
    public fun of(item: Int, vararg value: Int): ByteArray = build(QcyEntry(item, ByteArray(value.size) { value[it].toByte() }))

    /** The request for [item]'s current value: the earbuds then report it. */
    public fun request(item: Int): ByteArray = of(QcyItem.REQUEST, item)

    /**
     * The items in a packet. A value that runs past the end ends the list there; bytes without the `FF`
     * start are read as items from the first byte, as the settings characteristic can hold them.
     */
    public fun parse(bytes: ByteArray): List<QcyEntry> {
        var at = 0
        var end = bytes.size
        if (bytes.size >= 2 && bytes[0].toInt() and 0xFF == START) {
            at = 2
            end = minOf(bytes.size, 2 + (bytes[1].toInt() and 0xFF))
        }
        val out = mutableListOf<QcyEntry>()
        while (at + 2 <= end) {
            val item = bytes[at].toInt() and 0xFF
            val length = bytes[at + 1].toInt() and 0xFF
            if (at + 2 + length > end) break
            out += QcyEntry(item, bytes.copyOfRange(at + 2, at + 2 + length))
            at += 2 + length
        }
        return out
    }
}

/**
 * What QCY earbuds advertise under company id [QcyWire.COMPANY] (docs/qcy/PROTOCOL.md section 2): the
 * vendor id that names the model, each earbud's classic address (the one the phone paired with can be
 * either), and the batteries. QCY's app reads it in Devicebind.refreshInfoFromAVData.
 */
public data class QcyAdvert(
    val vendorId: Int,
    val classicAddress: String,
    val otherAddress: String,
    val left: Int?,
    val right: Int?,
    val case: Int?,
) {
    /** True when the advert comes from the earbuds paired at [address]. */
    public fun matches(address: String): Boolean = classicAddress.equals(address, ignoreCase = true) || otherAddress.equals(address, ignoreCase = true)

    public companion object {
        private const val MIN_SIZE = 20
        private const val FULL_SIZE = 24

        /** The advert's manufacturer data, or null when it is too short to be QCY's. */
        public fun parse(data: ByteArray): QcyAdvert? {
            if (data.size < MIN_SIZE) return null
            val d = if (data.size >= FULL_SIZE) data else data.copyOf(FULL_SIZE)
            fun hex(i: Int) = (d[i].toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
            fun address(vararg at: Int) = at.joinToString(":") { hex(it) }
            val classic = address(12, 11, 13, 16, 15, 14)
            val other = address(19, 18, 20, 23, 22, 21).takeIf { it != "00:00:00:00:00:00" } ?: classic
            fun battery(i: Int) = (d[i].toInt() and 0x7F).takeIf { it in 1..100 }
            return QcyAdvert(((d[0].toInt() and 0xFF) shl 8) or (d[1].toInt() and 0xFF), classic, other, battery(5), battery(6), battery(7))
        }
    }
}
