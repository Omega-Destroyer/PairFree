package io.github.omegadestroyer.pairfree.protocol.jbl

/**
 * What JBL earbuds put in their Bluetooth LE adverts, for finding the paired earbuds' LE side when it
 * does not answer on the classic address (docs/jbl/PROTOCOL.md section 3). The manufacturer data under
 * Harman's company id holds the product id (two bytes, low first), a colour, the CRCs of the Bluetooth
 * names of the two phones the earbuds are connected to, and a CRC JBL's app stores as the classic
 * address's, each two bytes, low first. Protocol 4 models send a "p5" advert instead: the product id, a
 * two-byte word whose bits say which fields follow (bit 15 colour, 14 and 13 the phones' name CRCs, 12 the
 * classic address's CRC, and so on), then those fields in that order (JBL's BroadcastParse). The CRC is
 * CRC-16/ARC, as JBL's app computes it.
 */
public object JblAdvert {
    /** Harman International's Bluetooth company id, under which the adverts carry their data. */
    public const val COMPANY: Int = 3787

    /** CRC-16/ARC of the first [length] bytes. */
    public fun crc16(bytes: ByteArray, length: Int = bytes.size): Int {
        var crc = 0
        for (i in 0 until minOf(length, bytes.size)) {
            crc = crc xor (bytes[i].toInt() and 0xFF)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor CRC_POLY else crc ushr 1 }
        }
        return crc
    }

    /** The product id an advert carries, or null when it is too short. */
    public fun pid(data: ByteArray): Int? = data.u16(0)

    /**
     * How sure it is that the advert [data] comes from the paired earbuds of [model] at classic address
     * [address], on a phone called [phoneName]: 0 when its product id is not the model's; 2 when it
     * carries the CRC of [address]; 1 when it carries the CRC of [phoneName], meaning the earbuds are
     * connected to this phone, which is how JBL's app accepts such an advert; else 0.
     */
    public fun score(data: ByteArray, model: JblModel, address: String, phoneName: String?): Int {
        if (pid(data)?.let { it in model.pids } != true) return 0
        val fields = if (model.family == JblModel.Family.P4) p5(data) else Fields(data.u16(SOURCE_1), data.u16(SOURCE_2), data.u16(ADDRESS_CRC))
        val mac = macBytes(address)
        if (mac != null && fields.addressCrc == crc16(mac)) return 2
        val name = phoneName?.takeIf { it.isNotEmpty() }?.encodeToByteArray() ?: return 0
        // JBL's app compares the CRC of the name's first 31 bytes and of its first 50.
        val crcs = setOf(crc16(name, NAME_SHORT), crc16(name, NAME_LONG))
        return if (fields.source1 in crcs || fields.source2 in crcs) 1 else 0
    }

    /** The name CRCs and the address CRC an advert carries, where it has them. */
    private class Fields(val source1: Int?, val source2: Int?, val addressCrc: Int?)

    /** The fields of a p5 advert up to the address CRC: each is there only where its bit in the flags word is set. */
    private fun p5(data: ByteArray): Fields {
        val flags = data.u16(P5_FLAGS) ?: return Fields(null, null, null)
        var at = P5_FIELDS
        if (flags and P5_COLOUR != 0) at += 1
        val source1 = if (flags and P5_SOURCE_1 != 0) data.u16(at).also { at += 2 } else null
        val source2 = if (flags and P5_SOURCE_2 != 0) data.u16(at).also { at += 2 } else null
        val addressCrc = if (flags and P5_ADDRESS_CRC != 0) data.u16(at) else null
        return Fields(source1, source2, addressCrc)
    }

    /** "AA:BB:CC:DD:EE:FF" as its six bytes in that order, or null. */
    public fun macBytes(address: String): ByteArray? {
        val parts = address.split(':')
        if (parts.size != MAC_BYTES) return null
        return ByteArray(MAC_BYTES) { parts[it].toIntOrNull(HEX)?.toByte() ?: return null }
    }

    private fun ByteArray.u16(at: Int): Int? = if (size >= at + 2) (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8) else null

    private const val CRC_POLY = 0xA001
    private const val SOURCE_1 = 3
    private const val SOURCE_2 = 5
    private const val ADDRESS_CRC = 7
    private const val P5_FLAGS = 2
    private const val P5_FIELDS = 4
    private const val P5_COLOUR = 1 shl 15
    private const val P5_SOURCE_1 = 1 shl 14
    private const val P5_SOURCE_2 = 1 shl 13
    private const val P5_ADDRESS_CRC = 1 shl 12
    private const val NAME_SHORT = 31
    private const val NAME_LONG = 50
    private const val MAC_BYTES = 6
    private const val HEX = 16
}
