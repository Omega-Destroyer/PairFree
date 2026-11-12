package io.github.omegadestroyer.pairfree.protocol.sony

/**
 * The sound settings that share Sony's EQEBB command (docs/sony/PROTOCOL.md section 6) besides the equalizer, as Sony's app 13.3.0 reads them (its `jn0`
 * message classes and `kn0` parameters, read 2026-11-12). None has been seen on real earbuds or in a recording: the layouts are the app's parsers', and
 * what a write does is as far as the app's own writer shows.
 */

/** Sony's sound effect ids (`SoundEffectType`) by the name PairFree shows. */
public object SonyEffectNames {
    public val BY_ID: Map<Int, String> = linkedMapOf(0 to "off", 1 to "ult", 2 to "ult_1", 3 to "ult_2", 4 to "custom", 5 to "flat", 6 to "live")
}

/**
 * The extra bass level (EBB, type 1 in set 2, 2 in set 1): a signed level between a lowest and a highest value the earbuds give. Capability
 * `51 type min max`, setting `57 type level` (`59` when the earbuds change it), set `58 type level`.
 */
public data class SonyEbb(val type: Int, val min: Int? = null, val max: Int? = null, val level: Int? = null) {
    val known: Boolean get() = min != null && max != null && level != null

    public fun read(p: ByteArray): SonyEbb? {
        if (p.size < 3 || p.u8(1) != type) return null
        return when (p.u8(0)) {
            RET_CAPABILITY -> if (p.size == 4) copy(min = p.s8(2), max = p.s8(3)) else null
            RET_PARAM, NTFY_PARAM -> if (p.size == 3) copy(level = p.s8(2)) else null
            else -> null
        }
    }

    /** The SET that puts [value] (kept within the earbuds' range) on the earbuds. */
    public fun set(value: Int): SonyOut? {
        val lo = min ?: return null
        val hi = max ?: return null
        return SonyOut.table1(SET, type, value.coerceIn(lo, hi) and 0xFF)
    }

    public fun after(value: Int): SonyEbb = copy(level = value.coerceIn(min ?: value, max ?: value))

    public companion object {
        private const val RET_CAPABILITY = 0x51
        private const val RET_PARAM = 0x57
        private const val SET = 0x58
        private const val NTFY_PARAM = 0x59
        public const val TYPE_SET2: Int = 1
        public const val TYPE_SET1: Int = 2
    }
}

/**
 * The sound effect (EQEBB type `30`, function SOUND_EFFECT): which of off, ULT, ULT 1, ULT 2, custom, flat or live the earbuds apply. Capability
 * `51 30 count effects... exclusive-count exclusive-ids...`, setting `57 30 effect` (`59` when the earbuds change it), set `58 30 effect`.
 */
public data class SonyEffect(val offered: List<Int> = emptyList(), val current: Int? = null) {
    public fun read(p: ByteArray): SonyEffect? {
        if (p.size < 3 || p.u8(1) != TYPE) return null
        return when (p.u8(0)) {
            RET_CAPABILITY -> {
                val n = p.u8(2)
                if (p.size < 3 + n) null else copy(offered = (0 until n).map { p.u8(3 + it) }.filter { it in SonyEffectNames.BY_ID })
            }
            RET_PARAM, NTFY_PARAM -> if (p.size == 3 && p.u8(2) in SonyEffectNames.BY_ID) copy(current = p.u8(2)) else null
            else -> null
        }
    }

    /** The SET that selects [effect], when the earbuds offered it. */
    public fun set(effect: Int): SonyOut? = if (effect in offered) SonyOut.table1(SET, TYPE, effect) else null

    public companion object {
        public const val TYPE: Int = 0x30
        private const val RET_CAPABILITY = 0x51
        private const val RET_PARAM = 0x57
        private const val SET = 0x58
        private const val NTFY_PARAM = 0x59
    }
}

/**
 * What the ULT button on the earbuds or headphones cycles through (EQEBB type `40`, function ULT_SOUND_EFFECT_ASSIGN): sound effects, each enabled (0)
 * or disabled (1). Capability `51 40 min max` (how many may be enabled), setting `57 40 count (effect status)...`, set `58 40 count (effect status)...`.
 */
public data class SonyUltAssign(val min: Int? = null, val max: Int? = null, val items: List<Pair<Int, Boolean>> = emptyList()) {
    public fun read(p: ByteArray): SonyUltAssign? {
        if (p.size < 3 || p.u8(1) != TYPE) return null
        return when (p.u8(0)) {
            RET_CAPABILITY -> if (p.size == 4) copy(min = p.u8(2), max = p.u8(3)) else null
            RET_PARAM, NTFY_PARAM -> {
                val n = p.u8(2)
                if (p.size != 3 + 2 * n) return null
                val list = (0 until n).map { p.u8(3 + 2 * it) to (p.u8(4 + 2 * it) == ENABLED) }
                if (list.any { it.first !in SonyEffectNames.BY_ID }) null else copy(items = list)
            }
            else -> null
        }
    }

    /** The SET with [effect] turned on or off, or null when it is not in the list or the number enabled would leave the earbuds' range. */
    public fun set(effect: Int, on: Boolean): SonyOut? {
        val next = items.map { if (it.first == effect) it.first to on else it }
        if (next == items || items.none { it.first == effect }) return null
        val enabled = next.count { it.second }
        if (enabled < (min ?: 0) || enabled > (max ?: next.size)) return null
        return SonyOut(1, (listOf(SET, TYPE, next.size) + next.flatMap { listOf(it.first, if (it.second) ENABLED else DISABLED) }).map { it.toByte() }.toByteArray())
    }

    public fun after(effect: Int, on: Boolean): SonyUltAssign = copy(items = items.map { if (it.first == effect) it.first to on else it })

    public companion object {
        public const val TYPE: Int = 0x40
        private const val ENABLED = 0
        private const val DISABLED = 1
        private const val RET_CAPABILITY = 0x51
        private const val RET_PARAM = 0x57
        private const val SET = 0x58
        private const val NTFY_PARAM = 0x59
    }
}

private fun ByteArray.s8(at: Int): Int = this[at].toInt()
