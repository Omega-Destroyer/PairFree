package io.github.omegadestroyer.pairfree.protocol.soundcore

/** One filter of a parametric preset: the type byte the earbuds take, the gain in dB, the centre frequency in Hz and the Q. */
public data class SoundcoreFilter(val type: Int, val gainDb: Float, val hz: Float, val q: Float)

/** A preset of a normalised equalizer: its command position, the key and name the app gives it, and its filters. */
public class SoundcoreNormalizedPreset(
    public val command: Int,
    public val key: String,
    public val name: String,
    public val filters: List<SoundcoreFilter>,
)

/**
 * The parametric equalizer of the models that write it as `03 8A` (docs/soundcore/PROTOCOL.md section 14; data/soundcore/eq_filters.json, devices.json
 * `eq_normalized`): [table] names the presets in the order the Soundcore app 5.0.35 lists them. A model whose list depends on its firmware (the Liberty 5 Pro
 * pair: a Balanced preset on some versions, the Signature at another position) names a second table, [tableWhen], used when the firmware's digits without
 * the dot fall in one of [firmwareRanges].
 */
public class SoundcoreNormalizedSpec(
    public val model: String,
    private val table: String,
    private val tableWhen: String? = null,
    private val firmwareRanges: List<IntRange> = emptyList(),
) {
    /** The presets for the earbuds' [firmware] (such as `03.48`); the first table when it is not known. */
    public fun presets(firmware: String?): List<SoundcoreNormalizedPreset> {
        val n = firmware?.replace(".", "")?.toIntOrNull()
        val useWhen = tableWhen != null && n != null && firmwareRanges.any { n in it }
        return SOUNDCORE_FILTER_TABLES.getValue(if (useWhen) tableWhen else table)
    }

    public companion object {
        public val ALL: List<SoundcoreNormalizedSpec> get() = SOUNDCORE_NORMALIZED_EQ

        public fun of(model: String?): SoundcoreNormalizedSpec? = SOUNDCORE_NORMALIZED_EQ.firstOrNull { it.model == model }
    }
}

/**
 * Writing and reading the normalised equalizer: the app's `CmmBtCmdService.m6` and `j5` (Soundcore 5.0.35). A custom curve is the command position 0xFEFE with up
 * to eight peaking filters (type 1, Q 0.7) at 100 Hz to 12.8 kHz whose gains are the curve's; a preset is its own command position with its own filters, and every
 * write carries the earbuds' HearID switch (off), state and time as they last reported them.
 */
public object SoundcoreNormalizedEqualizer {
    public const val CUSTOM: Int = 0xFEFE
    public val CUSTOM_HZ: List<Int> = listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800)
    private const val CUSTOM_Q = 0.7f

    /** The gain range of a custom curve, in dB each way (the app's own HearID curve limit, 7 dB less one). */
    public const val RANGE_DB: Int = 6

    /** The payload of `03 8A`: the position, the HearID switch, state and time, the filter count and each filter (type, then gain, Hz and Q as little-endian floats). */
    public fun body(index: Int, hearIdState: Int, hearIdTime: Long, filters: List<SoundcoreFilter>): ByteArray {
        val out = ByteArray(9 + 13 * filters.size)
        out[0] = index.toByte()
        out[1] = (index shr 8).toByte()
        out[2] = 0 // the HearID switch: the app sends false
        out[3] = hearIdState.toByte()
        for (i in 0 until 4) out[4 + i] = (hearIdTime shr (8 * i)).toByte()
        out[8] = filters.size.toByte()
        filters.forEachIndexed { n, f ->
            val at = 9 + 13 * n
            out[at] = f.type.toByte()
            putFloat(out, at + 1, f.gainDb)
            putFloat(out, at + 5, f.hz)
            putFloat(out, at + 9, f.q)
        }
        return out
    }

    private fun putFloat(to: ByteArray, at: Int, v: Float) {
        val bits = v.toRawBits()
        for (i in 0 until 4) to[at + i] = (bits shr (8 * i)).toByte()
    }

    private fun hearIdState(state: SoundcoreState?): Int = state?.field("hear_id_state")?.firstOrNull()?.toInt()?.and(0xFF) ?: 0

    private fun hearIdTime(state: SoundcoreState?): Long {
        val b = state?.field("hear_id_time") ?: return 0
        return b.foldIndexed(0L) { i, acc, v -> acc or ((v.toLong() and 0xFF) shl (8 * i)) }
    }

    /** The packet that selects [preset]. */
    public fun preset(preset: SoundcoreNormalizedPreset, state: SoundcoreState?): SoundcorePacket =
        SoundcorePacket(SoundcoreCommand.EQ_NORMALIZED, body(preset.command, hearIdState(state), hearIdTime(state), preset.filters))

    /** The packet that sets a custom curve of [gainsDb], one per [CUSTOM_HZ] band (missing bands are flat). */
    public fun custom(gainsDb: List<Double>, state: SoundcoreState?): SoundcorePacket {
        val filters = CUSTOM_HZ.mapIndexed { i, hz -> SoundcoreFilter(1, (gainsDb.getOrElse(i) { 0.0 }).toFloat(), hz.toFloat(), CUSTOM_Q) }
        return SoundcorePacket(SoundcoreCommand.EQ_NORMALIZED, body(CUSTOM, hearIdState(state), hearIdTime(state), filters))
    }

    /** What the earbuds hold: the preset by its command position, or [CUSTOM]; null when the reply has no position. */
    public fun position(state: SoundcoreState): Int? = state.field("eq_index")?.takeIf { it.size >= 2 }?.let { (it[0].toInt() and 0xFF) or ((it[1].toInt() and 0xFF) shl 8) }
}
