package io.github.omegadestroyer.pairfree.protocol.skullcandy

/**
 * Skullcandy's own RACE commands, as its app builds them (docs/skullcandy/PROTOCOL.md section 11; `data/skullcandy/race_commands.json`, where the model-dependent
 * forms were worked out by running each class's `buildCommand` for each of the app's 56 models). A message is the request as hex bytes, `05 5A <length u16 LE> <command u16 LE> <payload>`;
 * the answer is a `05 5B` response or a `05 5D` notification whose last byte is the value (a write's answer ends in the status, `00`).
 */
public object SkullcandyRace {
    /** Multipoint is written three ways, by the product's enum name in the app: NV key `14 00`, NV key `11 00` with `BB` or `BC`, or the custom `2C 82` command. */
    private val MULTIPOINT_NV14 = setOf("hesh360", "crusherEvoRC", "crusher540ActiveRC", "session360", "session540")
    private val MULTIPOINT_NV11 = setOf("dimeEvo", "dime360", "rail", "railANC", "session720")

    /** And read four ways. */
    private val MULTIPOINT_GET_E301 = setOf("dimeEvo", "dime360", "railANC", "session360", "session540", "session720")
    private val MULTIPOINT_GET_F500 = setOf("hesh360", "crusherEvoRC", "crusher540ActiveRC")

    public fun multipointGet(enumName: String): String = when (enumName) {
        in MULTIPOINT_GET_E301 -> "05 5A 06 00 00 0A 01 E3 01 00"
        in MULTIPOINT_GET_F500 -> "05 5A 06 00 00 0A 00 F5 01 00"
        "rail" -> "05 5A 02 00 14 00"
        else -> "05 5A 04 00 83 2C 10 00"
    }

    public fun multipointSet(enumName: String, on: Boolean): String = when (enumName) {
        in MULTIPOINT_NV14 -> "05 5A 03 00 01 14 " + (if (on) "01" else "00")
        in MULTIPOINT_NV11 -> "05 5A 04 00 01 11 " + (if (on) "BB" else "BC") + " 00"
        else -> "05 5A 05 00 82 2C 10 00 " + (if (on) "01" else "00")
    }

    /** Wear detection: an NV key read (`F4 02`) and a keyed write. */
    public const val WEAR_DETECT_GET: String = "05 5A 06 00 00 0A 02 F4 01 00"

    public fun wearDetectSet(on: Boolean): String = "05 5A 03 00 02 F4 " + (if (on) "01" else "00")

    /** The Crusher PLYR 720's bass boost: `2C D8` to read and `2C D7` to write. */
    public const val BASS_BOOST_GET: String = "05 5A 02 00 D8 2C"

    public fun bassBoostSet(on: Boolean): String = "05 5A 03 00 D7 2C " + (if (on) "01" else "00")

    /** The request bytes of a hex message ("05 5A 03 00 ..."). */
    public fun bytes(hex: String): ByteArray = hex.split(' ').filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()

    /** The requests PairFree sends: reads and the writes of the features above, for any product (the forms checked by the products' own functions). */
    public fun isAllowed(hex: String): Boolean {
        val b = hex.trim()
        return b == WEAR_DETECT_GET || b == BASS_BOOST_GET || b.startsWith("05 5A 03 00 02 F4 ") || b.startsWith("05 5A 03 00 D7 2C ") ||
            b == "05 5A 04 00 83 2C 10 00" || b == "05 5A 02 00 14 00" || b == "05 5A 06 00 00 0A 01 E3 01 00" || b == "05 5A 06 00 00 0A 00 F5 01 00" ||
            b.startsWith("05 5A 05 00 82 2C 10 00 ") || b.startsWith("05 5A 03 00 01 14 ") || b.startsWith("05 5A 04 00 01 11 ")
    }
}
