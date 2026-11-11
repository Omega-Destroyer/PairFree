package io.github.omegadestroyer.pairfree.protocol.oneplus

import io.github.omegadestroyer.pairfree.protocol.BandEq
import io.github.omegadestroyer.pairfree.protocol.BandEqReport
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
public class OneplusSession(public val model: OneplusModel?) : BandEq {
    public var state: EarbudState = EarbudState()
        private set

    private val parser = OneplusParser()
    private var sequence = -1
    private var noiseIndex: Int? = null
    private var noiseType = BITMAP_TYPE
    private var presetIndex: Int? = null
    private var gameMode: Boolean? = null

    /** The plain switches the earbuds listed in their `010D` reply, by feature id. */
    private val switchStates = linkedMapOf<Int, Boolean>()

    /** The prompt volume, the tap sensitivity and which head gesture answers a call, as the earbuds reported them. */
    private var promptVolume: Int? = null
    private var tapLevel: Int? = null
    private var headAnswer: Int? = null

    /** Another device the earbuds are paired with for dual connection (HeyMelody's `MultiConnectInformationElement`). */
    private class Peer(val address: ByteArray, val state: Int, val flags: Int, val name: String) {
        val current get() = flags and 1 != 0
        val connected get() = state == PEER_CONNECTED
    }

    private var peers: List<Peer>? = null

    /** The last key assignments read, by (device type, button action byte): the function number. */
    private val keys = linkedMapOf<Triple<Int, Int, Int>, Int>()

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

    /** The long press's noise cycle: a bit for each noise mode index that is in it (`NoiseReductionInfo` action 2, type 1). */
    private var cycleMask: Int? = null

    /** The spatial audio type the earbuds report (`012A`: status, type) and whether the find sound is playing. */
    private var spatial: Int? = null
    private var finding = false

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
        if (model?.noiseCycle == true) add(next(OneplusCommand.TYPED_NOISE, 2, CYCLE_TYPE))
        if (model?.presets?.isNotEmpty() == true) add(next(OneplusCommand.EQ_PRESET))
        val ids = listOfNotNull(GAME_MODE_ID.takeIf { model?.gameMode == true }) + model?.switches.orEmpty().filter { it != GAME_MODE_ID }
        if (ids.isNotEmpty()) add(next(OneplusCommand.SWITCHES, ids.size, *ids.toIntArray()))
        if (model?.promptVolume?.isNotEmpty() == true) add(next(OneplusCommand.VOLUME))
        if (model?.tapLevel == true) add(next(OneplusCommand.TAP_LEVEL))
        if (HEAD_MOTION_ID in model?.switches.orEmpty()) add(next(OneplusCommand.HEAD_MOTION))
        if (MULTI_ID in model?.switches.orEmpty()) add(next(OneplusCommand.MULTI_CONNECT))
        if (model?.touch?.isNotEmpty() == true) add(readKeys())
        if (model?.customSlots?.let { it > 0 } == true) add(next(OneplusCommand.CUSTOM_EQ))
        if (model?.spatialTypes?.isNotEmpty() == true) add(next(OneplusCommand.SPATIAL))
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
            OneplusCommand.SPATIAL -> if (d.size >= 2) spatial = d[1].toInt() and 0xFF
            OneplusCommand.VOLUME -> if (d.size >= 2) promptVolume = d[1].toInt() and 0xFF
            OneplusCommand.TAP_LEVEL -> if (d.size >= 2) tapLevel = d[1].toInt() and 0xFF
            OneplusCommand.HEAD_MOTION -> if (d.size >= 2) headAnswer = d[1].toInt() and 0xFF
            OneplusCommand.MULTI_CONNECT -> peers = parsePeers(d) ?: peers
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
        if (d.size >= NOISE_MIN && d[1].toInt() == 2 && (d[2].toInt() and 0xFF) == CYCLE_TYPE) {
            // `status, 02, type, value...`: the value is little endian, a bit for each mode index (HeyMelody's NoiseReductionInfo).
            cycleMask = (3 until d.size).fold(0) { acc, i -> acc or ((d[i].toInt() and 0xFF) shl (8 * (i - 3))) }
            return
        }
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
            val stem = button == STEM_BUTTON && (type == LEFT || type == RIGHT)
            val call = button == CALL_BUTTON && type == CALL_TYPE
            if (stem || call) keys[Triple(type, button, d[at + 2].toInt() and 0xFF)] = d[at + 3].toInt() and 0xFF
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

    /** One assignable gesture: the row's key, the earbud (device type) and button it is written for, and the model's entry for it. */
    private class Slot(val key: String, val type: Int, val side: String, val touch: OneplusTouch)

    /**
     * The gestures the model lists with the key the screens know them by: taps, a hold, a slide and a long press for each earbud (`touch_<gesture>_<side>`),
     * and the call controls for both earbuds (`touch_<gesture>_call_whole`). A long press (byte 6) shares the hold's row where the model has no hold of its own.
     */
    private fun slots(): List<Slot> = buildList {
        val all = model?.touch.orEmpty()
        val hasHold = all.any { it.button == STEM_BUTTON && it.gesture == "hold" }
        for (t in all) {
            if (t.button == STEM_BUTTON) {
                val gesture = when (t.gesture) {
                    "single", "double", "triple", "hold" -> t.gesture
                    "slide" -> "single_slider"
                    "long_press" -> if (hasHold) null else "hold"
                    else -> null
                } ?: continue
                for (type in listOf(LEFT, RIGHT)) add(Slot("touch_${gesture}_${side(type)}", type, side(type), t))
            } else if (t.button == CALL_BUTTON) {
                val gesture = when (t.gesture) { "double" -> "double_call"; "triple" -> "triple_call"; "long_press" -> "hold_call"; else -> null } ?: continue
                add(Slot("touch_${gesture}_whole", CALL_TYPE, "whole", t))
            }
        }
    }

    /** A touch row for each gesture and earbud the earbuds reported, among the functions the model allows; [side] picks "left", "right" or "whole". */
    private fun touchRows(side: String): List<Setting> = buildList {
        for (slot in slots().filter { it.side == side }) {
            val t = slot.touch
            val current = keys[Triple(slot.type, t.button, t.actionByte)] ?: continue
            val name = t.functions.firstOrNull { it.wire == current }?.key ?: continue
            add(Setting.Choice(slot.key, name, t.functions.map { it.key }.distinct()))
        }
    }

    /** `status, count, (feature id, state)...`. */
    private fun switches(d: ByteArray) {
        if (d.size < 2) return
        val count = d[1].toInt() and 0xFF
        if (d.size < 2 + count * 2) return
        for (i in 0 until count) {
            val id = d[2 + i * 2].toInt() and 0xFF
            val on = d[3 + i * 2].toInt() != 0
            if (id == GAME_MODE_ID) gameMode = on else if (id in model?.switches.orEmpty()) switchStates[id] = on
        }
    }

    /**
     * `status, count`, then per device: its address (6 bytes), a length (the bytes that follow it), the connection state, a flag byte (bit 0 this phone,
     * bit 1 the main audio device, bit 2 audio playing), the name's length, the name and any further bytes: HeyMelody's `CommandUtil.parseMultiConnectStateInfo`.
     */
    private fun parsePeers(d: ByteArray): List<Peer>? {
        if (d.size < 2) return null
        var at = 2
        val out = mutableListOf<Peer>()
        repeat(d[1].toInt() and 0xFF) {
            if (at + 10 > d.size) return null
            val length = d[at + 6].toInt() and 0xFF
            val nameLength = d[at + 9].toInt() and 0xFF
            if (at + 10 + nameLength > d.size) return null
            val address = d.copyOfRange(at, at + 6)
            if (address.any { it.toInt() != 0 }) out += Peer(address, d[at + 7].toInt() and 0xFF, d[at + 8].toInt() and 0xFF, d.copyOfRange(at + 10, at + 10 + nameLength).decodeToString())
            at += 7 + length
        }
        return out
    }

    /** The names the screens show for the devices that can be connected or disconnected, with a number added to a repeated name. */
    private fun peerNames(list: List<Peer>): List<Pair<String, Peer>> {
        val seen = mutableMapOf<String, Int>()
        return list.map { p ->
            val n = seen.merge(p.name, 1, Int::plus)!!
            (if (n == 1) p.name else "${p.name} ($n)") to p
        }
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
            (listOfNotNull(strengthRow()) + cycleRows()).takeIf { it.isNotEmpty() }?.let { SettingGroup("noise", SettingTab.HOME, it) },
            touchRows("left").takeIf { it.isNotEmpty() }?.let { SettingGroup("touch_left", SettingTab.CONTROLS, it) },
            touchRows("right").takeIf { it.isNotEmpty() }?.let { SettingGroup("touch_right", SettingTab.CONTROLS, it) },
            touchRows("whole").takeIf { it.isNotEmpty() }?.let { SettingGroup("calls", SettingTab.CONTROLS, it) },
            customEqRows().takeIf { it.isNotEmpty() }?.let { SettingGroup("oneplus_custom_eq", SettingTab.SOUND, it) },
            spatialRow()?.let { SettingGroup("oneplus_spatial", SettingTab.SOUND, listOf(it)) },
            if (model?.find == true && state.values.isNotEmpty()) SettingGroup("find", SettingTab.DEVICE, listOf(Setting.Action(FIND_KEY, finding))) else null,
            gameMode?.let { SettingGroup("oneplus_other", SettingTab.DEVICE, listOf(Setting.Toggle("game_mode", it))) },
            switchRows(SettingTab.SOUND).takeIf { it.isNotEmpty() }?.let { SettingGroup("oneplus_sound_switches", SettingTab.SOUND, it) },
            switchRows(SettingTab.DEVICE).takeIf { it.isNotEmpty() }?.let { SettingGroup("oneplus_switches", SettingTab.DEVICE, it) },
            promptVolume?.let { v -> model?.promptVolume?.takeIf { it.size == 2 }?.let { SettingGroup("oneplus_volume", SettingTab.DEVICE, listOf(Setting.Level(PROMPT_KEY, v.coerceIn(it[0], it[1]), it[0], it[1]))) } },
            headMotionRows().takeIf { it.isNotEmpty() }?.let { SettingGroup("oneplus_head_motion", SettingTab.CONTROLS, it) },
            tapLevel?.let { SettingGroup("oneplus_tap_level", SettingTab.CONTROLS, listOf(Setting.Level(TAP_KEY, it.coerceIn(TAP_MIN, TAP_MAX), TAP_MIN, TAP_MAX))) },
            peerRows().takeIf { it.isNotEmpty() }?.let { SettingGroup("oneplus_peers", SettingTab.DEVICE, it) },
        )

    /** The plain switches the earbuds listed, on the tab each belongs to (sound features on Sound, the rest on Device). */
    private fun switchRows(tab: SettingTab): List<Setting> =
        switchStates.filter { (id, _) -> (id in SOUND_SWITCHES) == (tab == SettingTab.SOUND) }.map { (id, on) -> Setting.Toggle("$SWITCH_PREFIX$id", on) }

    /** Which head gesture answers a call (the other declines): shown once the switch is on and the earbuds said which. */
    private fun headMotionRows(): List<Setting> {
        val on = switchStates[HEAD_MOTION_ID] == true
        val answer = headAnswer ?: return emptyList()
        return if (on) listOf(Setting.Choice(HEAD_KEY, HEAD_NAMES.getOrNull(answer), HEAD_NAMES)) else emptyList()
    }

    /** The other devices the earbuds know: one choice connects a disconnected one, another disconnects a connected one (never this phone). */
    private fun peerRows(): List<Setting> {
        val list = peers?.takeIf { switchStates[MULTI_ID] == true } ?: return emptyList()
        val others = peerNames(list).filter { !it.second.current }
        return listOfNotNull(
            others.filter { !it.second.connected }.map { it.first }.takeIf { it.isNotEmpty() }?.let { Setting.Choice(PEER_CONNECT_KEY, null, it) },
            others.filter { it.second.connected }.map { it.first }.takeIf { it.isNotEmpty() }?.let { Setting.Choice(PEER_DISCONNECT_KEY, null, it) },
        )
    }

    /** Spatial audio: off, fixed or head tracking, among the types the model lists (`012A` read, `0422` write). */
    private fun spatialRow(): Setting? {
        val current = spatial ?: return null
        val kinds = model?.spatialTypes.orEmpty().mapNotNull { SPATIAL_NAMES[it] }
        if (kinds.size < 2) return null
        return Setting.Choice(SPATIAL_KEY, SPATIAL_NAMES[current], kinds)
    }

    /** The modes a long press cycles through, as switches (off, noise cancelling, transparency): at least two stay on. */
    private fun cycleRows(): List<Setting> {
        val mask = cycleMask ?: return emptyList()
        return CYCLE_MODES.mapNotNull { (key, type) ->
            model?.noise?.firstOrNull { it.type == type }?.let { Setting.Toggle(key, mask and (1 shl it.index) != 0) }
        }
    }

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

    // The graphic equalizer (the Sound tab's graph, presets and saved profiles) reads and writes through [BandEq].
    override val eqBandsHz: List<Int> get() = model?.customFrequencies.orEmpty()
    override val eqRangeDb: Int get() = DEFAULT_MAX
    override val eqPresetNames: List<String> get() = model?.presets.orEmpty().map { it.name }

    override fun eqPresetFrames(name: String): List<ByteArray>? {
        val preset = model?.presets?.firstOrNull { it.name == name } ?: return null
        presetIndex = preset.index
        customEqs?.forEach { it.selected = false }
        return listOf(next(OneplusCommand.SET_EQ_PRESET, preset.index))
    }

    /** Saves the curve into the custom equalizer in use (else the first), or adds one named "Custom 1" while the earbuds hold none. */
    override fun eqCustomFrames(gains: List<Int>): List<ByteArray>? {
        val bands = eqBandsHz
        if (bands.isEmpty() || gains.size != bands.size) return null
        val eqs = customEqs ?: return null
        val eq = eqs.firstOrNull { it.selected } ?: eqs.firstOrNull()
        if (eq == null || eq.frequencies != bands) {
            if (eqs.size >= (model?.customSlots ?: 0) && eq != null) return null
            val fresh = CustomEq(0, true, "Custom 1", -DEFAULT_MAX, DEFAULT_MAX, bands, gains.map { it.coerceIn(-DEFAULT_MAX, DEFAULT_MAX) }.toMutableList())
            return listOf(eqWrite(OP_ADD, fresh), next(OneplusCommand.CUSTOM_EQ))
        }
        eqs.forEach { it.selected = it === eq }
        presetIndex = null
        gains.forEachIndexed { i, g -> eq.gains[i] = g.coerceIn(eq.min, eq.max) }
        return listOf(eqWrite(OP_SAVE, eq))
    }

    override val eqReport: BandEqReport?
        get() {
            customEqs?.firstOrNull { it.selected && it.frequencies == eqBandsHz }?.let { return BandEqReport(null, it.gains.toList()) }
            val index = presetIndex ?: return null
            return model?.presets?.firstOrNull { it.index == index }?.let { BandEqReport(it.name, null) }
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
        if (change is SettingChange.Toggle && change.key in CYCLE_MODES.map { it.first } && cycleMask != null) {
            val mode = model?.noise?.firstOrNull { it.type == CYCLE_MODES.first { m -> m.first == change.key }.second } ?: return null
            val next = if (change.on) cycleMask!! or (1 shl mode.index) else cycleMask!! and (1 shl mode.index).inv()
            // HeyMelody keeps at least two modes in the cycle (a control's minSelectCount); so does PairFree.
            if (next.countOneBits() < MIN_CYCLE) return null
            cycleMask = next
            val bytes = generateSequence(next) { it ushr 8 }.takeWhile { it != 0 }.map { it and 0xFF }.toList().ifEmpty { listOf(0) }
            return listOf(next(OneplusCommand.SET_NOISE, 2, CYCLE_TYPE, *bytes.toIntArray()))
        }
        if (change is SettingChange.Toggle && change.key.startsWith(SWITCH_PREFIX)) {
            val id = change.key.removePrefix(SWITCH_PREFIX).toIntOrNull() ?: return null
            if (id !in model?.switches.orEmpty() || switchStates[id] == null) return null
            switchStates[id] = change.on
            return listOf(next(OneplusCommand.SET_SWITCH, id, if (change.on) 1 else 0))
        }
        if (change is SettingChange.Level && change.key == PROMPT_KEY && promptVolume != null) {
            val range = model?.promptVolume?.takeIf { it.size == 2 } ?: return null
            promptVolume = change.value.coerceIn(range[0], range[1])
            return listOf(next(OneplusCommand.SET_VOLUME, promptVolume!!))
        }
        if (change is SettingChange.Level && change.key == TAP_KEY && tapLevel != null) {
            tapLevel = change.value.coerceIn(TAP_MIN, TAP_MAX)
            return listOf(next(OneplusCommand.SET_TAP_LEVEL, tapLevel!!))
        }
        if (change is SettingChange.Choice && change.key == HEAD_KEY && headAnswer != null) {
            val type = HEAD_NAMES.indexOf(change.option).takeIf { it >= 0 } ?: return null
            headAnswer = type
            return listOf(next(OneplusCommand.SET_HEAD_MOTION, type))
        }
        if (change is SettingChange.Choice && (change.key == PEER_CONNECT_KEY || change.key == PEER_DISCONNECT_KEY)) {
            val peer = peerNames(peers ?: return null).firstOrNull { it.first == change.option && !it.second.current }?.second ?: return null
            // HeyMelody's `operateMultiConnectHandheldDevice`: operation 1 connects the device, 2 disconnects it, with the address as HeyMelody writes it (the bytes in the order its text shows them, the reverse of the read), then the list is read again.
            val op = if (change.key == PEER_CONNECT_KEY) PEER_CONNECT else PEER_DISCONNECT
            return listOf(next(OneplusCommand.SET_MULTI_CONNECT, op, *peer.address.reversed().map { it.toInt() and 0xFF }.toIntArray()), next(OneplusCommand.MULTI_CONNECT))
        }
        if (change is SettingChange.Choice && change.key == SPATIAL_KEY && spatial != null) {
            val type = SPATIAL_NAMES.entries.firstOrNull { it.value == change.option && it.key in model?.spatialTypes.orEmpty() }?.key ?: return null
            spatial = type
            return listOf(next(OneplusCommand.SET_SPATIAL, type))
        }
        if (change is SettingChange.Press && change.key == FIND_KEY && model?.find == true) {
            // The earbuds play a sound until it is stopped (`0400` with 1 or 0): the Device tab's find button toggles it.
            finding = !finding
            return listOf(next(OneplusCommand.FIND, if (finding) 1 else 0))
        }
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
        val slot = slots().firstOrNull { it.key == change.key } ?: return null
        val t = slot.touch
        val id = Triple(slot.type, t.button, t.actionByte)
        if (keys[id] == null) return null
        val function = t.functions.firstOrNull { it.key == change.option } ?: return null
        keys[id] = function.wire
        return listOf(next(OneplusCommand.SET_KEYS, 1, slot.type, t.button, t.actionByte, function.wire), readKeys())
    }

    private fun ancNumber(name: String): Int = Command.ANC_MODE.labels.entries.first { it.value == name }.key

    public companion object {
        /** Feature id of the game mode switch, as the virtual run wrote it (`0403 06 01`). */
        public const val GAME_MODE_ID: Int = 6

        /** The keys of the spatial audio choice and of the find button. */
        public const val SPATIAL_KEY: String = "op_spatial"
        public const val FIND_KEY: String = "find"

        /** Plain switches are rows `op_sw_<feature id>`; the other rows of this part have their own keys. */
        public const val SWITCH_PREFIX: String = "op_sw_"
        public const val PROMPT_KEY: String = "op_prompt_volume"
        public const val TAP_KEY: String = "op_tap_level"
        public const val HEAD_KEY: String = "op_head_motion"
        public const val PEER_CONNECT_KEY: String = "op_peer_connect"
        public const val PEER_DISCONNECT_KEY: String = "op_peer_disconnect"

        /** Feature ids that are about sound and so sit on the Sound tab: hearing enhancement, high-quality audio, automatic volume and the bass engine. */
        public val SOUND_SWITCHES: Set<Int> = setOf(11, 24, 28, 29)
        private const val HEAD_MOTION_ID = 59
        private const val MULTI_ID = 17
        private val HEAD_NAMES = listOf("nod", "shake")
        private const val TAP_MIN = 1
        private const val TAP_MAX = 5
        private const val PEER_CONNECTED = 2
        private const val PEER_CONNECT = 1
        private const val PEER_DISCONNECT = 2
        private val SPATIAL_NAMES = mapOf(0 to "off", 1 to "fixed", 2 to "head_tracking")

        /** The key of the brand row that chooses among the custom equalizers, and of the action that adds one. */
        public const val CUSTOM_EQ_KEY: String = "op_custom_eq"
        public const val NEW_EQ_KEY: String = "op_eq_new"

        /** The key of the brand row that chooses among the on mode's sub-modes. */
        public const val STRENGTH_KEY: String = "op_noise_level"
        private const val SEQUENCE_WRAP = 256
        private const val CYCLE_TYPE = 1
        private const val MIN_CYCLE = 2
        private val CYCLE_MODES = listOf("cycle_off" to "off", "cycle_anc" to "noise_cancelling", "cycle_transparency" to "transparency")
        private const val OP_ADD = 1
        private const val OP_SAVE = 2
        private const val DEFAULT_MIN = -6
        private const val DEFAULT_MAX = 6
        private const val CALL_BUTTON = 6
        private const val CALL_TYPE = 4
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
            OneplusCommand.SET_NOISE, OneplusCommand.SET_EQ_PRESET, OneplusCommand.SET_SWITCH, OneplusCommand.KEYS, OneplusCommand.SET_KEYS, OneplusCommand.CUSTOM_EQ, OneplusCommand.SET_CUSTOM_EQ, OneplusCommand.SPATIAL, OneplusCommand.SET_SPATIAL, OneplusCommand.FIND,
            OneplusCommand.MULTI_CONNECT, OneplusCommand.VOLUME, OneplusCommand.TAP_LEVEL, OneplusCommand.HEAD_MOTION,
            OneplusCommand.SET_MULTI_CONNECT, OneplusCommand.SET_VOLUME, OneplusCommand.SET_TAP_LEVEL, OneplusCommand.SET_HEAD_MOTION,
        )
    }
}
