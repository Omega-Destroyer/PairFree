package io.github.omegadestroyer.pairfree.protocol.sony

/** One of Sony's EQ presets from data/sony/eq_presets.json. */
public data class SonyEqPreset(val id: Int, val name: String, val label: String, val custom: Boolean)

/**
 * Sony's equalizer (EQEBB, docs/sony/PROTOCOL.md section 6) as far as the earbuds have told it: their
 * presets, how many values a curve has and how many steps each, which value is Clear Bass and the
 * others' frequencies, and what they hold now. Set 1 reads it with type 1 (3 when the curve cannot be
 * changed), set 2 with type 0 (2).
 *
 * @property type the EQEBB type it is read and written with.
 * @property customizable false for models that take their presets only.
 * @property steps how many steps a value has; values run from 0 to steps - 1 with the middle flat.
 * @property presets each preset's id and the name the earbuds gave it, in their order.
 * @property bands each value's frequency in Hz, or [CLEAR_BASS]; null until the earbuds said or [values] tells.
 * @property preset the preset the earbuds hold, or [SONY_EQ_UNSPECIFIED].
 * @property values the curve they hold, in steps.
 */
public data class SonyEq(
    val tableSet: Int,
    val type: Int,
    val customizable: Boolean,
    val steps: Int? = null,
    val presets: List<Pair<Int, String>> = emptyList(),
    val bandInfo: List<Int>? = null,
    val preset: Int? = null,
    val values: List<Int> = emptyList(),
) {
    /** The flat value, the middle step. */
    public val center: Int get() = ((steps ?: DEFAULT_STEPS) - 1) / 2

    /**
     * Each value's frequency or [CLEAR_BASS]: as the earbuds reported, else by how many values there
     * are, as Sony's app draws them (six: Clear Bass and five bands; ten: ten bands).
     */
    public val bands: List<Int>?
        get() = bandInfo?.takeIf { values.isEmpty() || it.size == values.size } ?: when (values.size) {
            STANDARD.size -> STANDARD
            TEN_BAND.size -> TEN_BAND
            else -> null
        }

    /** The frequency bands without Clear Bass. */
    public val frequencies: List<Int> get() = bands.orEmpty().filter { it != CLEAR_BASS }

    /** Where Clear Bass is among the values, or null when the curve has none. */
    public val clearBassIndex: Int? get() = bands?.indexOf(CLEAR_BASS)?.takeIf { it >= 0 }

    /** Clear Bass from flat, in steps. */
    public val clearBass: Int? get() = clearBassIndex?.let { values.getOrNull(it) }?.minus(center)

    /** The frequency bands' gains from flat, in steps, in the order of [frequencies]. */
    public val gains: List<Int>
        get() {
            val b = bands ?: return emptyList()
            if (b.size != values.size) return emptyList()
            return values.filterIndexed { i, _ -> b[i] != CLEAR_BASS }.map { it - center }
        }

    /** True when the preset held is one the user sets the curve of (Custom, a user setting) or none. */
    public val holdsCustom: Boolean get() = preset == null || preset == SONY_EQ_UNSPECIFIED || SonyEqs.of(preset)?.custom == true

    /** The presets to offer: the earbuds' own, without the ones that are the user's curve. */
    public val offered: List<Pair<Int, String>> get() = presets.filter { SonyEqs.of(it.first)?.custom != true }

    /** The SET that selects [id]. */
    public fun selectPreset(id: Int): SonyOut = SonyOut.table1(SET, type, id, 0)

    /**
     * The SET that puts [gains] (from flat, one per frequency band) and [clearBass] on the earbuds,
     * keeping what is not given. Set 1 names no preset with it, as Sony's app does; set 2 names
     * Custom, as Gadgetbridge does. Null while the curve's layout is not known.
     */
    public fun setCurve(gains: List<Int>? = null, clearBass: Int? = null): SonyOut? {
        val b = bands ?: return null
        if (!customizable || b.size != values.size) return null
        val max = (steps ?: DEFAULT_STEPS) - 1
        val next = values.toMutableList()
        var g = 0
        for (i in b.indices) {
            if (b[i] == CLEAR_BASS) {
                if (clearBass != null) next[i] = (clearBass + center).coerceIn(0, max)
            } else {
                gains?.getOrNull(g)?.let { next[i] = (it + center).coerceIn(0, max) }
                g++
            }
        }
        val id = if (tableSet == 1) SONY_EQ_UNSPECIFIED else CUSTOM
        return SonyOut(1, (listOf(SET, type, id, next.size) + next).map { it.toByte() }.toByteArray())
    }

    /** This EQ after [out], one of its SETs, so the change shows before the earbuds confirm it. */
    public fun after(out: SonyOut): SonyEq {
        val p = out.payload
        val count = p.u8(3)
        val id = p.u8(2)
        return if (count == 0) copy(preset = id) else copy(preset = if (id == SONY_EQ_UNSPECIFIED) preset else id, values = (0 until count).map { p.u8(4 + it) })
    }

    /** Applies a reply or notification from the earbuds; null when it is not this EQ's. */
    public fun read(payload: ByteArray): SonyEq? {
        if (payload.size < 2 || payload.u8(1) != type) return null
        return when (payload.u8(0)) {
            RET_CAPABILITY -> capability(payload)
            RET_EXTENDED_INFO -> extended(payload)
            RET_PARAM, NTFY_PARAM -> param(payload)
            else -> null
        }
    }

    /** The capability: band count, steps, then each preset as its id, a name length and the name. */
    private fun capability(p: ByteArray): SonyEq? {
        if (p.size < 5) return null
        val count = p.u8(4)
        val presets = mutableListOf<Pair<Int, String>>()
        var i = 5
        repeat(count) {
            if (i + 1 >= p.size) return null
            val id = p.u8(i)
            val length = p.u8(i + 1)
            if (i + 2 + length > p.size) return null
            val name = p.copyOfRange(i + 2, i + 2 + length).decodeToString()
            presets += id to name.ifEmpty { SonyEqs.of(id)?.label ?: id.toString(16).uppercase().padStart(2, '0') }
            i += 2 + length
        }
        return copy(steps = p.u8(3), presets = presets)
    }

    /** The band information: a count, then per value its kind (1 Hz, 2 kHz, 0x10 special) and a 2-byte number. */
    private fun extended(p: ByteArray): SonyEq? {
        if (p.size < 3) return null
        val count = p.u8(2)
        if (p.size != 3 + count * 3) return null
        val bands = (0 until count).map { k ->
            val at = 3 + k * 3
            val number = (p.u8(at + 1) shl 8) or p.u8(at + 2)
            when (p.u8(at)) {
                HZ -> number
                KHZ -> number * 1000
                SPECIFIC -> if (number == SPECIFIC_CLEAR_BASS) CLEAR_BASS else return null
                else -> return null
            }
        }
        return copy(bandInfo = bands)
    }

    /** The setting: the preset, a count, then the values. */
    private fun param(p: ByteArray): SonyEq? {
        if (p.size < 4) return null
        val count = p.u8(3)
        if (p.size != 4 + count) return null
        return copy(preset = p.u8(2), values = if (count == 0) values else (0 until count).map { p.u8(4 + it) })
    }

    public companion object {
        /** Stands for Clear Bass in [bands]. */
        public const val CLEAR_BASS: Int = 0

        // Both table sets number these alike (data/sony/commands.json).
        private const val SET = SonyCommand.Set1.EQEBB_SET_PARAM
        private const val RET_CAPABILITY = SonyCommand.Set1.EQEBB_RET_CAPABILITY
        private const val RET_PARAM = SonyCommand.Set1.EQEBB_RET_PARAM
        private const val NTFY_PARAM = SonyCommand.Set1.EQEBB_NTFY_PARAM
        private const val RET_EXTENDED_INFO = SonyCommand.Set1.EQEBB_RET_EXTENDED_INFO
        private const val HZ = 1
        private const val KHZ = 2
        private const val SPECIFIC = 0x10
        private const val SPECIFIC_CLEAR_BASS = 1

        /** Gadgetbridge's steps: values from 0 to 20, flat at 10. Used until the earbuds say. */
        private const val DEFAULT_STEPS = 21

        /** Sony's Custom preset, which set 2 names with the user's own curve. */
        private const val CUSTOM = 0xA0

        /** Six values: Clear Bass, then 400 Hz, 1, 2.5, 6.3 and 16 kHz (Sony's app, EqBandStepsStandard). */
        public val STANDARD: List<Int> = listOf(CLEAR_BASS, 400, 1000, 2500, 6300, 16000)

        /** Ten values: 31 Hz to 16 kHz (Sony's app, EqBandSteps10band). */
        public val TEN_BAND: List<Int> = listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

        /** The EQ to ask for, by the functions the earbuds listed; null when they list none. */
        public fun of(tableSet: Int, functions: Set<Int>): SonyEq? = if (tableSet == 1) {
            when {
                SonyFunction.Set1.PRESET_EQ in functions -> SonyEq(1, SET1_PRESET_EQ, customizable = true)
                SonyFunction.Set1.PRESET_EQ_NONCUSTOMIZABLE in functions -> SonyEq(1, SET1_NONCUSTOMIZABLE, customizable = false)
                else -> null
            }
        } else {
            when {
                SonyFunction.Set2.PRESET_EQ in functions -> SonyEq(2, SET2_PRESET_EQ, customizable = true)
                SonyFunction.Set2.PRESET_EQ_NON_CUSTOMIZABLE in functions -> SonyEq(2, SET2_NONCUSTOMIZABLE, customizable = false)
                else -> null
            }
        }

        private const val SET1_PRESET_EQ = 1
        private const val SET1_NONCUSTOMIZABLE = 3
        private const val SET2_PRESET_EQ = 0
        private const val SET2_NONCUSTOMIZABLE = 2
    }
}

/** Sony's EQ presets by id. */
public object SonyEqs {
    public val ALL: List<SonyEqPreset> get() = SONY_EQ_PRESETS

    public fun of(id: Int): SonyEqPreset? = SONY_EQ_PRESETS.firstOrNull { it.id == id }
}
