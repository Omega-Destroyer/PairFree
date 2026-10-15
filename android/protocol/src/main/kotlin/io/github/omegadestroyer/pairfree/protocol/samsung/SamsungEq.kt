package io.github.omegadestroyer.pairfree.protocol.samsung

/**
 * The Galaxy Buds equalizer (docs/samsung/PROTOCOL.md section 6), as GalaxyBudsClient sends it:
 * off (Normal) or one of five presets on every model, and on models with the custom_equalizer
 * feature a nine-band custom curve. The earbuds apply the presets themselves. Their curves come
 * from the earbuds' EQ data reply ([curves]) where PairFree asks for it (the Buds4 family, as
 * Samsung's plugin does); elsewhere they are not known.
 */
public object SamsungEq {
    /** The presets in the order the earbuds number them, after Normal (EQ off). */
    public val PRESETS: List<String> = listOf("bass_boost", "soft", "dynamic", "clear", "treble_boost")
    public const val NORMAL: String = "normal"

    /** The custom curve's bands, as GalaxyBudsClient labels them. */
    public val CUSTOM_BANDS_HZ: List<Int> = listOf(63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    public const val CUSTOM_RANGE: Int = 10

    /** GalaxyBudsClient's preset number for the custom curve; the earbuds report it as that + 1. */
    private const val CUSTOM_PRESET = 6

    /** The custom curve's equalizer number on the Buds4 family (Samsung's plugin: Balanced 0, the presets, Custom 6). */
    private const val BUDS4_CUSTOM = 6

    /** The equalizer number that selects the custom curve on [model]. */
    private fun customNumber(model: SamsungModel): Int = if (model.buds4Layout) BUDS4_CUSTOM else CUSTOM_PRESET + 1

    public fun hasCustom(model: SamsungModel): Boolean = model.supports("custom_equalizer", null)

    /** The packets that select [preset] ([NORMAL] or one of [PRESETS]). */
    public fun preset(model: SamsungModel, preset: String): SamsungPacket {
        val index = PRESETS.indexOf(preset)
        val payload = if (model.legacy) {
            // The Galaxy Buds (2019): on, then the preset + 5 (5 to 9 without Dolby).
            byteArrayOf(if (index >= 0) 1 else 0, ((if (index >= 0) index else 0) + 5).toByte())
        } else {
            byteArrayOf(if (index >= 0) (index + 1).toByte() else 0)
        }
        return SamsungPacket(SamsungMessage.EQUALIZER, payload)
    }

    /** The packets that put a custom curve of [gains] (one per band, -10 to 10) on the earbuds, or null when the model takes none. */
    public fun custom(model: SamsungModel, gains: List<Int>): List<SamsungPacket>? {
        if (!hasCustom(model) || gains.size != CUSTOM_BANDS_HZ.size) return null
        val bands = gains.map { it.coerceIn(-CUSTOM_RANGE, CUSTOM_RANGE).toByte() }
        val curve = SamsungPacket(SamsungMessage.CUSTOM_EQUALIZE_SEND, byteArrayOf(bands.size.toByte()) + bands.toByteArray())
        val select = SamsungPacket(SamsungMessage.EQUALIZER, byteArrayOf(customNumber(model).toByte()))
        // Samsung's plugin selects the custom curve first, then sends it; GalaxyBudsClient the other way round.
        return if (model.buds4Layout) listOf(select, curve) else listOf(curve, select)
    }

    /** What the earbuds hold: [NORMAL], one of [PRESETS], CUSTOM, or null when the state does not say. */
    public fun reported(status: SamsungStatus): String? {
        val mode = status.int("equalizer_mode") ?: return null
        if (status.model.legacy) {
            if (status.flag("equalizer_enabled") != true) return NORMAL
            return PRESETS.getOrNull(mode - 5) ?: PRESETS.getOrNull(mode)
        }
        return when (mode) {
            0 -> NORMAL
            customNumber(status.model) -> CUSTOM
            else -> PRESETS.getOrNull(mode - 1)
        }
    }

    /**
     * The curves in the earbuds' EQ data reply (message 105), by equalizer number: preset count, band
     * count, then each preset's bands as signed steps. With six presets the reply leaves out the flat
     * Normal curve, which goes first, as Samsung's plugin does. Empty when the reply does not add up.
     */
    public fun curves(payload: ByteArray): List<List<Int>> {
        if (payload.size < 2) return emptyList()
        val presets = payload[0].toInt() and 0xFF
        val bands = payload[1].toInt() and 0xFF
        if (bands == 0 || 2 + presets * bands > payload.size) return emptyList()
        val out = mutableListOf<List<Int>>()
        if (presets == WITHOUT_FLAT) out += List(bands) { 0 }
        for (i in 0 until presets) out += List(bands) { payload[2 + i * bands + it].toInt() }
        return out
    }

    private const val WITHOUT_FLAT = 6

    public const val CUSTOM: String = "custom"
}
