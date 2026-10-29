package io.github.omegadestroyer.pairfree.protocol.sony

import io.github.omegadestroyer.pairfree.protocol.Setting

/**
 * Automatic power-off (docs/sony/PROTOCOL.md section 7), as Sony's app reads and writes it: the earbuds list the
 * choices they offer, and the setting holds the active one and a time, which they keep for when a time is chosen
 * again. Set 1 sends it as SYSTEM type 4, its two ids after a parameter type of 1; set 2 as POWER type 4, or 5 on
 * earbuds that can also turn off when taken off.
 */
public data class SonyAutoOff(
    val tableSet: Int,
    val type: Int,
    val choices: List<Int> = emptyList(),
    val active: Int? = null,
    val time: Int? = null,
) {
    /** The reads to send: what the earbuds offer, then what is set. */
    public val requests: List<SonyOut>
        get() = if (tableSet == 1) {
            listOf(SonyOut.table1(S1.SYSTEM_GET_CAPABILITY, type), SonyOut.table1(S1.SYSTEM_GET_PARAM, type))
        } else {
            listOf(SonyOut.table1(S2.POWER_GET_CAPABILITY, type), SonyOut.table1(S2.POWER_GET_PARAM, type))
        }

    /** This setting with [p] applied, or null when [p] is not about it. */
    public fun read(p: ByteArray): SonyAutoOff? {
        if (p.size < 2 || p.u8(1) != type) return null
        val capability = if (tableSet == 1) S1.SYSTEM_RET_CAPABILITY else S2.POWER_RET_CAPABILITY
        val params = if (tableSet == 1) setOf(S1.SYSTEM_RET_PARAM, S1.SYSTEM_NTFY_PARAM) else setOf(S2.POWER_RET_PARAM, S2.POWER_NTFY_PARAM)
        return when (p.u8(0)) {
            capability -> {
                if (p.size < 3 || p.size != 3 + p.u8(2)) return null
                copy(choices = (0 until p.u8(2)).map { p.u8(3 + it) }.filter { it in OPTIONS })
            }
            in params -> {
                // Set 1: parameter type 1, then the active id and the time; set 2: the two ids alone.
                val at = if (tableSet == 1) 3 else 2
                if (p.size != at + 2 || (tableSet == 1 && p.u8(2) != ACTIVE_AND_TIME)) return null
                copy(active = p.u8(at), time = p.u8(at + 1))
            }
            else -> null
        }
    }

    /** The row, once the earbuds have said both what they offer and what is set. */
    public val setting: Setting.Choice?
        get() {
            val a = active ?: return null
            if (choices.isEmpty()) return null
            val shown = (choices + a).distinct().filter { it in OPTIONS }.sortedBy { ORDER.indexOf(it) }
            return Setting.Choice(KEY, OPTIONS[a], shown.map { OPTIONS.getValue(it) })
        }

    /** The message for [option] and the setting as it will be, as Sony's app writes it. */
    public fun set(option: String): Pair<SonyOut, SonyAutoOff>? {
        val id = OPTIONS.entries.firstOrNull { it.value == option }?.key ?: return null
        if (id !in choices && id != active) return null
        // A time is both the active id and the time; the other choices keep the time the earbuds hold.
        val t = if (id in TIMES) id else time ?: choices.firstOrNull { it in TIMES } ?: return null
        val out = if (tableSet == 1) {
            SonyOut.table1(S1.SYSTEM_SET_PARAM, type, ACTIVE_AND_TIME, id, t)
        } else {
            SonyOut.table1(S2.POWER_SET_PARAM, type, id, t)
        }
        return out to copy(active = id, time = t)
    }

    public companion object {
        private val S1 = SonyCommand.Set1
        private val S2 = SonyCommand.Set2
        public const val KEY: String = "auto_off"

        /** Set 1's SYSTEM type, and set 2's POWER types, for automatic power-off. */
        public const val SET1_TYPE: Int = 4
        public const val SET2_TYPE: Int = 4
        public const val SET2_TYPE_WEARING: Int = 5
        private const val ACTIVE_AND_TIME = 1

        // Sony's AutoPowerOffElementId and AutoPowerOffElements: times, then when taken off and never.
        private const val WHEN_TAKEN_OFF = 0x10
        private const val NEVER = 0x11
        private val TIMES = mapOf(0 to 5, 4 to 15, 1 to 30, 2 to 60, 3 to 180)
        private val ORDER = TIMES.keys.toList() + WHEN_TAKEN_OFF + NEVER

        /** Each id by the option the shared row shows: minutes, `when_taken_off` or `never` (as JBL's row names it). */
        private val OPTIONS: Map<Int, String> = TIMES.mapValues { it.value.toString() } + (WHEN_TAKEN_OFF to "when_taken_off") + (NEVER to "never")

        /** The setting for what the earbuds list, or null when they have none. */
        public fun of(tableSet: Int, functions: Set<Int>): SonyAutoOff? = when {
            tableSet == 1 -> SonyAutoOff(1, SET1_TYPE).takeIf { SonyFunction.Set1.AUTO_POWER_OFF in functions }
            SonyFunction.Set2.AUTO_POWER_OFF_WITH_WEARING_DETECTION in functions -> SonyAutoOff(2, SET2_TYPE_WEARING)
            SonyFunction.Set2.AUTO_POWER_OFF in functions -> SonyAutoOff(2, SET2_TYPE)
            else -> null
        }
    }
}

/**
 * The touch sensors' and buttons' functions (Sony's assignable settings, docs/sony/PROTOCOL.md section 7): the
 * earbuds list each key with the presets it can take, and the setting holds one preset per key, in that order. Set 1
 * sends it as SYSTEM type 6, set 2 as type 3, or 14 on models with limitations, whose list starts with one more byte.
 * PairFree offers the presets that need nothing set up elsewhere; a key set to another (an assistant, Quick Access)
 * shows it and keeps it until the user picks another.
 */
public data class SonyKeys(
    val tableSet: Int,
    val type: Int,
    val keys: List<Key> = emptyList(),
    val presets: List<Int>? = null,
) {
    /** One key: Sony's key id and the presets it can take. */
    public data class Key(val id: Int, val offered: List<Int>)

    public val requests: List<SonyOut>
        get() = listOf(SonyOut.table1(SYSTEM_GET_CAPABILITY, type), SonyOut.table1(SYSTEM_GET_PARAM, type))

    public fun read(p: ByteArray): SonyKeys? {
        if (p.size < 3 || p.u8(1) != type) return null
        return when (p.u8(0)) {
            SYSTEM_RET_CAPABILITY -> capability(p)?.let { copy(keys = it) }
            SYSTEM_RET_PARAM, SYSTEM_NTFY_PARAM -> {
                val n = p.u8(2)
                if (n < 1 || p.size != 3 + n) return null
                copy(presets = (0 until n).map { p.u8(3 + it) })
            }
            else -> null
        }
    }

    /** The keys the capability lists, each with its presets; null when the list does not parse. */
    private fun capability(p: ByteArray): List<Key>? {
        var at = if (tableSet == 2 && type == SET2_TYPE_LIMITED) 3 else 2
        fun next(): Int? = if (at < p.size) p.u8(at++) else null
        val count = next() ?: return null
        val out = mutableListOf<Key>()
        repeat(count) {
            val id = next() ?: return null
            next() ?: return null // touch sensor or button
            next() ?: return null // its default preset
            val presets = next() ?: return null
            val offered = mutableListOf<Int>()
            repeat(presets) {
                offered += next() ?: return null
                // Read each count before skipping: `at += next()` would add to `at` as it was before the read.
                if (tableSet == 1) {
                    val pairs = next() ?: return null
                    at += 2 * pairs // action and function pairs
                } else {
                    val pairs = next() ?: return null
                    val lists = next() ?: return null
                    at += 2 * pairs
                    repeat(lists) {
                        at += 2 // action and function
                        val functions = next() ?: return null
                        at += functions // then the functions it can switch between
                    }
                }
                if (at > p.size) return null
            }
            out += Key(id, offered)
        }
        return out.takeIf { at == p.size }
    }

    /** One row per key, once the earbuds have said both what each key can take and what it holds. */
    public val settings: List<Setting.Choice>
        get() {
            val held = presets ?: return emptyList()
            if (held.size != keys.size) return emptyList()
            return keys.mapIndexed { i, k ->
                val options = (k.offered.filter { it in SELECTABLE } + held[i]).distinct()
                Setting.Choice(keyName(k.id), presetName(held[i]), options.map(::presetName))
            }.distinctBy { it.key }
        }

    /** The message that gives the key behind [key] the preset named [option], and the setting as it will be. */
    public fun set(key: String, option: String): Pair<SonyOut, SonyKeys>? {
        val held = presets ?: return null
        val i = keys.indexOfFirst { keyName(it.id) == key }.takeIf { it >= 0 && it < held.size } ?: return null
        val preset = (listOf(held[i]) + keys[i].offered.filter { it in SELECTABLE }).firstOrNull { presetName(it) == option } ?: return null
        val next = held.toMutableList().also { it[i] = preset }
        return SonyOut.table1(SYSTEM_SET_PARAM, type, next.size, *next.toIntArray()) to copy(presets = next)
    }

    public companion object {
        // Both table sets number the SYSTEM commands alike.
        private const val SYSTEM_GET_CAPABILITY = SonyCommand.Set1.SYSTEM_GET_CAPABILITY
        private const val SYSTEM_RET_CAPABILITY = SonyCommand.Set1.SYSTEM_RET_CAPABILITY
        private const val SYSTEM_GET_PARAM = SonyCommand.Set1.SYSTEM_GET_PARAM
        private const val SYSTEM_RET_PARAM = SonyCommand.Set1.SYSTEM_RET_PARAM
        private const val SYSTEM_SET_PARAM = SonyCommand.Set1.SYSTEM_SET_PARAM
        private const val SYSTEM_NTFY_PARAM = SonyCommand.Set1.SYSTEM_NTFY_PARAM

        public const val SET1_TYPE: Int = 6
        public const val SET2_TYPE: Int = 3
        public const val SET2_TYPE_LIMITED: Int = 14

        /** Sony's key ids (AssignableSettingsKey, Key) by the row each gets. */
        private fun keyName(id: Int): String = when (id) {
            0 -> "assign_left"
            1 -> "assign_right"
            2 -> "assign_custom"
            3 -> "assign_c"
            4, 5 -> "assign_nc_amb"
            else -> "assign_$id"
        }

        /** Sony's presets (AssignableSettingsPreset, Preset) by name; the classic-only variants share their name. */
        private val PRESETS = mapOf(
            0x00 to "noise_control", 0x10 to "volume", 0x20 to "playback", 0x21 to "track", 0x22 to "playback_limited",
            0x30 to "voice_assistant", 0x31 to "google_assistant", 0x39 to "google_assistant", 0x32 to "alexa", 0x40 to "alexa",
            0x35 to "noise_control_quick_access", 0x43 to "noise_control_quick_access", 0x36 to "quick_access",
            0x42 to "quick_access", 0x45 to "noise_control_mic", 0x46 to "listening_mode_quick_access",
            0x47 to "noise_control_listening_mode", 0xFF to "none",
        )

        /** What PairFree lets the user pick: the presets that need no assistant or app set up first. */
        private val SELECTABLE = setOf(0x00, 0x10, 0x20, 0x21, 0x22, 0x30, 0x45, 0x47, 0xFF)

        private fun presetName(id: Int): String = PRESETS[id] ?: ("other_" + id.toString(16).padStart(2, '0'))

        public fun of(tableSet: Int, functions: Set<Int>): SonyKeys? = when {
            tableSet == 1 -> SonyKeys(1, SET1_TYPE).takeIf { SonyFunction.Set1.ASSIGNABLE_SETTINGS in functions }
            SonyFunction.Set2.ASSIGNABLE_SETTING_WITH_LIMITATION in functions -> SonyKeys(2, SET2_TYPE_LIMITED)
            SonyFunction.Set2.ASSIGNABLE_SETTING in functions -> SonyKeys(2, SET2_TYPE)
            else -> null
        }
    }
}
