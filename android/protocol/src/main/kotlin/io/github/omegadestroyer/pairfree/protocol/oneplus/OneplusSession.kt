package io.github.omegadestroyer.pairfree.protocol.oneplus

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value

/**
 * One connection to OnePlus or OPPO earbuds over HeyMelody's protocol (docs/oneplus/PROTOCOL.md). Source analysis of HeyMelody 117.6.6
 * and a virtual run of that app only: no earbuds have answered PairFree. A setting shows once its read has been answered, and a read is
 * sent only for a feature the model's catalog entry lists. Reads are `0100` capabilities, `0106` battery, `010C` current noise mode,
 * `010F` equalizer preset and `010D` the game mode switch; writes are `0404` (noise mode as a bitmap of the mode's protocol index),
 * `0406` (preset index) and `0403` (a switch's feature id and 0 or 1).
 */
public class OneplusSession(public val model: OneplusModel?) {
    public var state: EarbudState = EarbudState()
        private set

    private val parser = OneplusParser()
    private var sequence = -1
    private var noiseIndex: Int? = null
    private var noiseType = BITMAP_TYPE
    private var presetIndex: Int? = null
    private var gameMode: Boolean? = null

    /** The last key assignments read, by (device type, button action byte): the function number. */
    private val keys = linkedMapOf<Pair<Int, Int>, Int>()

    /** A custom equalizer the earbuds keep: HeyMelody's `EqInfo` (id, whether it is the one in use, name, limits, bands in Hz and gains in dB). */
    public class CustomEq(
        public val id: Int,
        public var selected: Boolean,
        public var name: String,
        public val min: Int,
        public val max: Int,
        public val frequencies: List<Int>,
        public val gains: MutableList<Int>,
    )

    private var customEqs: List<CustomEq>? = null

    /** The product id the earbuds reported, as hex, for the device report. */
    public var productId: String? = null
        private set

    /** The capability bits the earbuds listed, for the device report. */
    public var capabilities: Set<Int>? = null
        private set

    private fun next(command: Int, vararg payload: Int): ByteArray {
        sequence = (sequence + 1) % SEQUENCE_WRAP
        return OneplusPacket(command, sequence, ByteArray(payload.size) { payload[it].toByte() }).bytes()
    }

    /** The reads that open the conversation, in order. The vendor id is FFFF as HeyMelody sends it on every profile. */
    public fun start(): List<ByteArray> = buildList {
        add(next(OneplusCommand.CAPABILITIES))
        add(next(OneplusCommand.VENDOR, 0xFF, 0xFF))
        add(next(OneplusCommand.BATTERY))
        add(next(OneplusCommand.PRODUCT))
        add(next(OneplusCommand.MTU, 0x00, 0x02))
        if (model?.noise?.isNotEmpty() == true) add(next(OneplusCommand.TYPED_NOISE, 1, 1))
        if (model?.presets?.isNotEmpty() == true) add(next(OneplusCommand.EQ_PRESET))
        if (model?.gameMode == true) add(next(OneplusCommand.SWITCHES, 1, GAME_MODE_ID))
        if (model?.touch?.isNotEmpty() == true) add(readKeys())
        if (model?.customSlots?.let { it > 0 } == true) add(next(OneplusCommand.CUSTOM_EQ))
    }

    /** The key assignment read HeyMelody sends: a count, then the buttons it asks about (3 and 1, as in the virtual run). */
    private fun readKeys(): ByteArray = next(OneplusCommand.KEYS, 2, 3, 1)

    /** Applies bytes from the earbuds (any number of frames or part of one). True when [state] or [groups] changed. */
    public fun receive(bytes: ByteArray): Boolean {
        val before = state to groups
        for (packet in parser.feed(bytes)) apply(packet)
        refresh()
        return (state to groups) != before
    }

    private fun apply(p: OneplusPacket) {
        if (!p.response) {
            // The earbuds announce a changed custom equalizer list unasked (command 0506, no status byte).
            if (p.command == OneplusCommand.CUSTOM_EQ_NOTIFY) customEqs = parseCustomEqs(p.payload, 0) ?: customEqs
            return
        }
        val d = p.payload
        if (d.firstOrNull()?.toInt() != 0) return
        when (p.id) {
            OneplusCommand.CAPABILITIES -> capabilities = OneplusReads.capabilityBits(p)
            OneplusCommand.BATTERY -> OneplusBattery.of(p)?.let(::battery)
            OneplusCommand.PRODUCT -> if (d.size >= PRODUCT_SIZE) productId = "%02X%02X%02X".format(d[3].toInt() and 0xFF, d[2].toInt() and 0xFF, d[1].toInt() and 0xFF)
            OneplusCommand.TYPED_NOISE -> noise(d)
            OneplusCommand.EQ_PRESET -> if (d.size >= 2) presetIndex = d[1].toInt() and 0xFF
            OneplusCommand.SWITCHES -> switches(d)
            OneplusCommand.KEYS -> keyRecords(d)
            OneplusCommand.CUSTOM_EQ -> customEqs = parseCustomEqs(d, 1) ?: customEqs
        }
    }

    /** Device types 1, 2 and 3 are left, right and the case, as HeyMelody's screen showed them in the virtual run. */
    private fun battery(list: List<OneplusBattery>) {
        val values = mutableMapOf<Command, Value>()
        for (b in list) when (b.type) {
            1 -> values[Command.BATTERY_LEFT] = Value.Number(b.percent)
            2 -> values[Command.BATTERY_RIGHT] = Value.Number(b.percent)
            3 -> values[Command.BATTERY_CASE] = Value.Number(b.percent)
        }
        state = state.copy(values = state.values + values)
    }

    /** `status, 01, type, bitmap...`: the first set bit is the current mode's protocol index (HeyMelody's CurrentNoiseModeInfo). */
    private fun noise(d: ByteArray) {
        if (d.size < NOISE_MIN || d[1].toInt() != 1) return
        val type = d[2].toInt() and 0xFF
        if (type != BITMAP_TYPE && type != WIDE_BITMAP_TYPE) return
        noiseType = type
        val bits = d.copyOfRange(3, d.size)
        val index = (0 until bits.size * 8).firstOrNull { bits[it / 8].toInt() and (1 shl (it % 8)) != 0 }
        noiseIndex = index
    }

    /** `status, count, (device type, button, button action, function)...`: only the stem button's records for the left and right earbud are kept. */
    private fun keyRecords(d: ByteArray) {
        if (d.size < 2) return
        val count = d[1].toInt() and 0xFF
        if (d.size < 2 + count * 4) return
        for (i in 0 until count) {
            val at = 2 + i * 4
            val type = d[at].toInt() and 0xFF
            val button = d[at + 1].toInt() and 0xFF
            if (button == STEM_BUTTON && (type == LEFT || type == RIGHT)) keys[type to (d[at + 2].toInt() and 0xFF)] = d[at + 3].toInt() and 0xFF
        }
    }

    /**
     * `count`, then per equalizer: selected (1 byte), minimum and maximum (signed), id, the name's length and UTF-8 bytes, the band count, and for
     * each band its frequency (two bytes, low first) and its gain (signed): HeyMelody's `CommandUtil.parseAllEqData`. [offset] is 1 after a reply's status byte.
     */
    private fun parseCustomEqs(d: ByteArray, offset: Int): List<CustomEq>? {
        if (d.size <= offset) return null
        var at = offset
        val count = d[at++].toInt() and 0xFF
        val out = mutableListOf<CustomEq>()
        repeat(count) {
            if (at + 5 > d.size) return null
            val selected = d[at].toInt() != 0
            val min = d[at + 1].toInt()
            val max = d[at + 2].toInt()
            val id = d[at + 3].toInt() and 0xFF
            val nameLength = d[at + 4].toInt() and 0xFF
            at += 5
            if (at + nameLength + 1 > d.size) return null
            val name = d.copyOfRange(at, at + nameLength).decodeToString()
            at += nameLength
            val bands = d[at++].toInt() and 0xFF
            if (bands < 1 || at + bands * 3 > d.size) return null
            val freqs = mutableListOf<Int>()
            val gains = mutableListOf<Int>()
            repeat(bands) {
                freqs += (d[at].toInt() and 0xFF) or ((d[at + 1].toInt() and 0xFF) shl 8)
                gains += d[at + 2].toInt()
                at += 3
            }
            out += CustomEq(id, selected, name, min, max, freqs, gains)
        }
        return out
    }

    /** The custom equalizers' rows: the one in use (or none, when a preset is), and its bands, with an action that adds one while there is room. */
    private fun customEqRows(): List<Setting> {
        val eqs = customEqs ?: return emptyList()
        val slots = model?.customSlots ?: 0
        return buildList {
            if (eqs.isNotEmpty()) add(Setting.Choice(CUSTOM_EQ_KEY, eqs.firstOrNull { it.selected }?.name, eqs.map { it.name }))
            eqs.firstOrNull { it.selected }?.let { eq -> eq.frequencies.forEachIndexed { i, hz -> add(Setting.Level("eq_$hz", eq.gains[i].coerceIn(eq.min, eq.max), eq.min, eq.max)) } }
            if (eqs.size < slots) add(Setting.Action(NEW_EQ_KEY))
        }
    }

    /** `0418` with an operation (1 add, 2 save and use, 3 delete) and one record: HeyMelody's `setEqInfo` layout. */
    private fun eqWrite(operation: Int, eq: CustomEq): ByteArray {
        val name = eq.name.encodeToByteArray()
        val head = intArrayOf(operation, eq.min and 0xFF, eq.max and 0xFF, eq.id, name.size) + name.map { it.toInt() and 0xFF } + eq.frequencies.size
        val bands = eq.frequencies.indices.flatMap { listOf(eq.frequencies[it] and 0xFF, (eq.frequencies[it] shr 8) and 0xFF, eq.gains[it] and 0xFF) }
        return next(OneplusCommand.SET_CUSTOM_EQ, *(head + bands))
    }

    private fun side(type: Int) = if (type == LEFT) "left" else "right"

    /** A touch row for each earbud and each stem gesture the model lists and the earbuds reported, with the functions the model allows. */
    private fun touchRows(side: String): List<Setting> = buildList {
        for (t in model?.touch.orEmpty().filter { it.gesture in TAP_GESTURES }) for (type in listOf(if (side == "left") LEFT else RIGHT)) {
            val current = keys[type to t.actionByte] ?: continue
            val name = t.functions.firstOrNull { it.wire == current }?.key ?: continue
            add(Setting.Choice("touch_${t.gesture}_${side(type)}", name, t.functions.map { it.key }.distinct()))
        }
    }

    /** `status, count, (feature id, state)...`. */
    private fun switches(d: ByteArray) {
        if (d.size < 2) return
        val count = d[1].toInt() and 0xFF
        if (d.size < 2 + count * 2) return
        for (i in 0 until count) if ((d[2 + i * 2].toInt() and 0xFF) == GAME_MODE_ID) gameMode = d[3 + i * 2].toInt() != 0
    }

    private fun refresh() {
        val index = noiseIndex ?: return
        val type = model?.noise?.firstOrNull { it.index == index }?.type ?: model?.noise?.flatMap { it.children + it }?.firstOrNull { it.index == index }?.type
        val mode = when (type) {
            "off" -> "off"
            "noise_cancelling", "light", "strong", "moderate", "smart" -> "anc"
            "transparency", "voice_focus" -> "transparency"
            else -> null
        } ?: return
        state = state.copy(values = state.values + (Command.ANC_MODE to Value.Number(ancNumber(mode))))
    }

    /** The preset and the game mode as brand rows. */
    public val groups: List<SettingGroup>
        get() = listOfNotNull(
            presetIndex?.let { i ->
                val presets = model?.presets.orEmpty()
                SettingGroup("oneplus_sound", SettingTab.SOUND, listOf(Setting.Choice("op_eq_preset", presets.firstOrNull { it.index == i && customEqs?.any { c -> c.selected } != true }?.name, presets.map { it.name })))
            },
            strengthRow()?.let { SettingGroup("noise", SettingTab.HOME, listOf(it)) },
            touchRows("left").takeIf { it.isNotEmpty() }?.let { SettingGroup("touch_left", SettingTab.CONTROLS, it) },
            touchRows("right").takeIf { it.isNotEmpty() }?.let { SettingGroup("touch_right", SettingTab.CONTROLS, it) },
            customEqRows().takeIf { it.isNotEmpty() }?.let { SettingGroup("oneplus_custom_eq", SettingTab.SOUND, it) },
            gameMode?.let { SettingGroup("oneplus_other", SettingTab.DEVICE, listOf(Setting.Toggle("game_mode", it))) },
        )

    /**
     * The sub-modes of the mode that is on (noise cancelling's smart, strong, moderate and light; the auto mode's range): HeyMelody selects one by
     * writing the same noise bitmap with the sub-mode's own protocol index (`m0` calls `l0(index, 1)`), so the row is a choice among their kinds.
     */
    private fun strengthRow(): Setting? {
        val index = noiseIndex ?: return null
        val top = model?.noise?.firstOrNull { it.index == index || it.children.any { c -> c.index == index } } ?: return null
        val kinds = top.children.filter { it.type != top.type && it.type != "off" && it.type != "transparency" }
        if (kinds.size < 2) return null
        val current = kinds.firstOrNull { it.index == index }?.type
        return Setting.Choice(STRENGTH_KEY, current, kinds.map { it.type })
    }

    /** The commands PairFree sends: reads above and these writes. Nothing else, in particular none of the `0Fxx` debug commands. */
    public fun allowed(frame: ByteArray): Boolean {
        val packet = OneplusParser().feed(frame).singleOrNull() ?: return false
        return packet.command in ALLOWED
    }

    /** The noise mode from the shared screens: off, noise cancelling or transparency, as the bit of the model's mode of that kind. */
    public fun set(command: Command, value: Int): List<ByteArray>? {
        if (command != Command.ANC_MODE || noiseIndex == null) return null
        val kind = when (value) {
            ancNumber("off") -> "off"
            ancNumber("anc") -> "noise_cancelling"
            ancNumber("transparency") -> "transparency"
            else -> return null
        }
        val mode = model?.noise?.firstOrNull { it.type == kind } ?: return null
        noiseIndex = mode.index
        refresh()
        return listOf(noiseWrite(mode.index))
    }

    /** `0404`: action 01, the bitmap type, then the bitmap with only [index] set (one byte for indices below 8, else more). */
    private fun noiseWrite(index: Int): ByteArray {
        val bitmap = IntArray(index / 8 + 1).also { it[index / 8] = 1 shl (index % 8) }
        return next(OneplusCommand.SET_NOISE, 1, noiseType, *bitmap)
    }

    public fun change(change: SettingChange): List<ByteArray>? {
        val eqs = customEqs
        if (eqs != null) {
            if (change is SettingChange.Choice && change.key == CUSTOM_EQ_KEY) {
                val eq = eqs.firstOrNull { it.name == change.option } ?: return null
                eqs.forEach { it.selected = it === eq }
                presetIndex = null
                return listOf(eqWrite(OP_SAVE, eq))
            }
            if (change is SettingChange.Level && change.key.startsWith("eq_")) {
                val eq = eqs.firstOrNull { it.selected } ?: return null
                val band = eq.frequencies.indexOf(change.key.removePrefix("eq_").toIntOrNull() ?: return null)
                if (band < 0) return null
                eq.gains[band] = change.value.coerceIn(eq.min, eq.max)
                return listOf(eqWrite(OP_SAVE, eq))
            }
            if (change is SettingChange.Press && change.key == NEW_EQ_KEY) {
                val slots = model?.customSlots ?: 0
                val freqs = model?.customFrequencies.orEmpty()
                if (eqs.size >= slots || freqs.isEmpty()) return null
                val name = "Custom ${(1..slots).first { n -> eqs.none { it.name == "Custom $n" } }}"
                val fresh = CustomEq(0, true, name, DEFAULT_MIN, DEFAULT_MAX, freqs, MutableList(freqs.size) { 0 })
                return listOf(eqWrite(OP_ADD, fresh), next(OneplusCommand.CUSTOM_EQ))
            }
        }
        if (change is SettingChange.Choice && change.key.startsWith("touch_")) return changeKey(change)
        when {
            change is SettingChange.Choice && change.key == STRENGTH_KEY && noiseIndex != null -> {
                val index = noiseIndex
                val top = model?.noise?.firstOrNull { it.index == index || it.children.any { c -> c.index == index } } ?: return null
                val child = top.children.firstOrNull { it.type == change.option } ?: return null
                noiseIndex = child.index
                refresh()
                return listOf(noiseWrite(child.index))
            }
            change is SettingChange.Choice && change.key == "op_eq_preset" && presetIndex != null -> {
                val preset = model?.presets?.firstOrNull { it.name == change.option } ?: return null
                presetIndex = preset.index
                return listOf(next(OneplusCommand.SET_EQ_PRESET, preset.index))
            }
            change is SettingChange.Toggle && change.key == "game_mode" && gameMode != null -> {
                gameMode = change.on
                return listOf(next(OneplusCommand.SET_SWITCH, GAME_MODE_ID, if (change.on) 1 else 0))
            }
        }
        return null
    }

    /** `touch_<gesture>_<left|right>`: `0401` with one record, then the read HeyMelody sends after every key write. */
    private fun changeKey(change: SettingChange.Choice): List<ByteArray>? {
        val parts = change.key.removePrefix("touch_").split('_')
        if (parts.size != 2) return null
        val type = when (parts[1]) { "left" -> LEFT; "right" -> RIGHT; else -> return null }
        val touch = model?.touch?.firstOrNull { it.gesture == parts[0] } ?: return null
        if (keys[type to touch.actionByte] == null) return null
        val function = touch.functions.firstOrNull { it.key == change.option } ?: return null
        keys[type to touch.actionByte] = function.wire
        return listOf(next(OneplusCommand.SET_KEYS, 1, type, STEM_BUTTON, touch.actionByte, function.wire), readKeys())
    }

    private fun ancNumber(name: String): Int = Command.ANC_MODE.labels.entries.first { it.value == name }.key

    public companion object {
        /** Feature id of the game mode switch, as the virtual run wrote it (`0403 06 01`). */
        public const val GAME_MODE_ID: Int = 6

        /** The key of the brand row that chooses among the custom equalizers, and of the action that adds one. */
        public const val CUSTOM_EQ_KEY: String = "op_custom_eq"
        public const val NEW_EQ_KEY: String = "op_eq_new"

        /** The key of the brand row that chooses among the on mode's sub-modes. */
        public const val STRENGTH_KEY: String = "op_noise_level"
        private const val SEQUENCE_WRAP = 256
        private const val OP_ADD = 1
        private const val OP_SAVE = 2
        private const val DEFAULT_MIN = -6
        private const val DEFAULT_MAX = 6
        private val TAP_GESTURES = setOf("single", "double", "triple")
        private const val LEFT = 1
        private const val RIGHT = 2
        private const val STEM_BUTTON = 1
        private const val BITMAP_TYPE = 1
        private const val WIDE_BITMAP_TYPE = 5
        private const val NOISE_MIN = 4
        private const val PRODUCT_SIZE = 4

        public val ALLOWED: Set<Int> = setOf(
            OneplusCommand.CAPABILITIES, OneplusCommand.VENDOR, OneplusCommand.BATTERY, OneplusCommand.PRODUCT, OneplusCommand.MTU,
            OneplusCommand.TYPED_NOISE, OneplusCommand.EQ_PRESET, OneplusCommand.SWITCHES,
            OneplusCommand.SET_NOISE, OneplusCommand.SET_EQ_PRESET, OneplusCommand.SET_SWITCH, OneplusCommand.KEYS, OneplusCommand.SET_KEYS, OneplusCommand.CUSTOM_EQ, OneplusCommand.SET_CUSTOM_EQ,
        )
    }
}
