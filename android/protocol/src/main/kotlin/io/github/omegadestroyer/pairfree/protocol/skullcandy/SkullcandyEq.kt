package io.github.omegadestroyer.pairfree.protocol.skullcandy

/**
 * The custom equaliser's layout as Skullcandy's app sets it (docs/skullcandy/PROTOCOL.md section 13.1): the bands' frequencies by the product's enum name
 * (`app/skullcandy/s2.java`, `setCustomEQBands`), the gain range (the Dart `SKDYProductModelInfo.ceqRange`), the fixed Q and the custom slot's category.
 */
public object SkullcandyEq {
    /** The Airoha category of the custom curve: the app's `customEqualizerIndex` defaults to 101. */
    public const val CATEGORY: Int = 101

    /** Every band's Q, as the app writes it. */
    public const val Q: Float = 0.91f

    /** The app's short names of its equalizer modes (its English strings `*_mode_short_label` and `product_*_mode`). */
    private val MODE_NAMES = mapOf(
        "musicMode" to "Music", "podcastMode" to "Podcast", "movieMode" to "Movie", "bassBoostMode" to "Bass Boost", "supremeSoundMode" to "Supreme Sound",
        "fpsMode" to "FPS", "rpgMode" to "RPG", "mmoMode" to "MMO", "signatureSoundMode" to "Signature", "outdoorMode" to "Outdoor", "indoorMode" to "Indoor",
    )

    /** The presets the earbuds hold for a product, by the app's names and the number each is picked by (1 to 99; the custom curves, 101 and up, are not presets). */
    public fun presets(enumName: String): List<Pair<String, Int>> =
        SkullcandyEqModes.BY_ENUM[enumName].orEmpty().filter { it.second in 1..99 }.mapNotNull { (mode, n) -> MODE_NAMES[mode]?.let { it to n } }.sortedBy { it.second }

    private val TEN_BANDS = setOf("plyr", "slyrProXbox", "slyrPro", "crusherPlyr", "crusherPlyrXbox")
    private val SEVEN_BANDS = setOf("session720", "session900")

    /** The bands' centre frequencies in Hz: 10 on the gaming headsets, 7 on the two larger speakers, 5 on the rest. */
    public fun bandsHz(enumName: String): List<Int> = when (enumName) {
        in TEN_BANDS -> listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        in SEVEN_BANDS -> listOf(60, 150, 400, 1000, 2500, 6000, 15000)
        else -> listOf(60, 250, 1000, 4000, 12000)
    }

    private val WIDE = setOf("plyr", "slyrProXbox", "slyrPro", "crusherPlyr", "crusherPlyrXbox", "pushActive", "grind", "grindFuel", "rail", "railANC", "crusherANC2")

    /** The most a band may be raised or lowered, in dB: 12 on the models the SDK configures and the gaming headsets, 6 on the rest (the Crusher 1080 ANC's range is 0 to +20, a Bose curve not built here). */
    public fun rangeDb(enumName: String): Int = if (enumName in WIDE) 12 else 6
}
