package io.github.omegadestroyer.pairfree.protocol.jbl

/**
 * What JBL earbuds put in their Bluetooth LE adverts, for finding the paired earbuds' LE side when it
 * does not answer on the classic address (docs/jbl/PROTOCOL.md section 3). The manufacturer data under
 * Harman's company id holds the product id (two bytes, low first), a colour, the CRCs of the Bluetooth
 * names of the two phones the earbuds are connected to, and a CRC JBL's app stores as the classic
 * address's, each two bytes, low first. The CRC is CRC-16/ARC, as JBL's app computes it.
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
        val mac = macBytes(address)
        if (mac != null && data.u16(ADDRESS_CRC) == crc16(mac)) return 2
        val name = phoneName?.takeIf { it.isNotEmpty() }?.encodeToByteArray() ?: return 0
        // JBL's app compares the CRC of the name's first 31 bytes and of its first 50.
        val crcs = setOf(crc16(name, NAME_SHORT), crc16(name, NAME_LONG))
        return if (data.u16(SOURCE_1) in crcs || data.u16(SOURCE_2) in crcs) 1 else 0
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
    private const val NAME_SHORT = 31
    private const val NAME_LONG = 50
    private const val MAC_BYTES = 6
    private const val HEX = 16
}
