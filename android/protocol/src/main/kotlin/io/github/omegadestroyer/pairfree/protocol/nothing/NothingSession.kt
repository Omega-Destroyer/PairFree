package io.github.omegadestroyer.pairfree.protocol.nothing

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value

/**
 * One connection to Nothing or CMF earbuds or headphones (docs/nothing/PROTOCOL.md), shown on PairFree's
 * screens. It reads what Nothing X reads; the product id the earbuds report picks the model, whose entry
 * in data/nothing/devices.json says which settings it has. A setting shows once its read has answered,
 * and only where the model has it: the earbuds answer a read they do not have with an error, an empty
 * payload or nothing at all. The battery, firmware, noise mode, in-ear detection, low lag mode and
 * finding the earbuds go to [state]; everything else to [groups].
 *
 * A change shows at once; the earbuds acknowledge it, and [takeReadBacks] gives the reads that confirm
 * it, which the connection sends a little later, so the screen ends up showing what the earbuds hold.
 *
 * @param hint the model the Bluetooth name gave, used until the earbuds report their product id: it
 *   decides whether frames carry a CRC, which the Ear (1) does without.
 */
public class NothingSession(private val hint: NothingModel? = null) {
    public var state: EarbudState = EarbudState()
        private set

    public var productId: String? = null
        private set

    public var firmware: String? = null
        private set

    /** The model, once the earbuds have reported their product id; before then, the one the name gave. */
    public val model: NothingModel? get() = productId?.let(NothingModels::of) ?: hint.takeIf { productId == null }

    /** Whether frames to these earbuds carry a CRC. */
    public val crc: Boolean get() = model?.crc ?: true

    /**
     * The last payload of each read PairFree sends, and of the battery and noise events, for the device
     * report. Nothing else: the earbuds' other events can hold other devices' addresses.
     */
    public val replies: Map<String, ByteArray> get() = _replies
    private val _replies = linkedMapOf<String, ByteArray>()

    private val config: NothingConfig? get() = model?.config(firmware)
    private val form: Form get() = model?.form ?: Form.EARBUDS

    private var fsn = 0
    private var activationSent = false
    private val outbox = mutableListOf<NothingFrame>()
    private val readBacks = mutableListOf<NothingFrame>()

    private var noiseMode: Int? = null      // the mode in effect (group 1 of the noise report)
    private var noiseLevel: Int? = null     // the cancelling strength to go back to (group 2)
    private var eqMode: Int? = null
    private var customEq: Map<Int, Int>? = null   // the custom EQ's gain by filter type, whole dB
    private var bass: Pair<Boolean, Int>? = null
    private var spatial: ByteArray? = null
    private var codec: Int? = null
    private var earDetection: Boolean? = null
    private var lowLag: Boolean? = null
    private var keys: Map<Triple<Int, Int, Int>, Int> = emptyMap()
    private var keysRead = false
    private val ringing = mutableSetOf<Int>()

    /** A frame to the earbuds with the next sequence number, with a CRC unless the model takes none. */
    public fun frame(command: Int, vararg payload: Int): NothingFrame =
        NothingFrame(command, ByteArray(payload.size) { payload[it].toByte() }, NothingFrame.control(crc), nextFsn())

    private fun nextFsn(): Int = fsn.also { fsn = if (fsn + 1 >= NothingWire.FSN_WRAP) 0 else fsn + 1 }

    /** The reads on opening the channel: the protocol version first where Nothing X sends it (activate). */
    public fun start(): List<NothingFrame> = buildList {
        if (hint?.activate != false) add(frame(NothingCommand.GET_PROTOCOL_VERSION))
        addAll(reads())
    }

    private fun reads(): List<NothingFrame> = buildList {
        add(frame(NothingCommand.GET_DEVICE_MODEL))
        add(frame(NothingCommand.GET_HOST_VERSION_DEVICE))
        add(frame(NothingCommand.GET_REMOTE_BATTERY_LEVEL))
        // Nothing X asks for the noise report's three groups.
        add(frame(NothingCommand.GET_CURRENT_NOISE_REDUCTION, NOISE_GROUPS))
        add(frame(NothingCommand.GET_EQ_MODE))
        if (hasSimpleEq) add(frame(NothingCommand.GET_CUSTOM_EQ_VALUE))
        add(frame(NothingCommand.GET_EXTRA_FEATURE_STATUS))
        add(frame(NothingCommand.GET_HOST_LAG_MODE))
        add(frame(NothingCommand.GET_BASS_BOOST))
        add(frame(NothingCommand.GET_SPATIAL_AUDIO))
        add(frame(NothingCommand.GET_LHDC_COMMANDS))
        add(frame(NothingCommand.GET_KEY_CONFIGURATION))
    }

    /** The battery read, to send again now and then. */
    public fun batteryRequests(): List<NothingFrame> = listOf(frame(NothingCommand.GET_REMOTE_BATTERY_LEVEL))

    /** What the session wants sent next: the activation and the reads again, or a read after a refused write. */
    public fun takeOutbox(): List<NothingFrame> = outbox.toList().also { outbox.clear() }

    /** The reads that confirm the last changes, for the connection to send a little later. */
    public fun takeReadBacks(): List<NothingFrame> = readBacks.distinctBy { it.command }.also { readBacks.clear() }

    /** True when [reply] answers [sent]: the same command with bit 15 cleared. */
    public fun answers(sent: NothingFrame, reply: NothingFrame): Boolean = !reply.isRequest && reply.answers == sent.command

    /** False for what PairFree never sends: firmware update, factory reset, calibration, debug, and reads of serial numbers. */
    public fun allowed(frame: NothingFrame): Boolean = frame.command !in NothingCommand.NEVER_SEND && frame.command !in NothingCommand.PRIVATE

    /** Applies one frame from the earbuds. Returns true when [state] or [groups] changed. */
    public fun receive(frame: NothingFrame): Boolean {
        val before = state to groups
        if (frame.isRequest) event(frame) else reply(frame)
        refresh()
        return (state to groups) != before
    }

    private fun event(frame: NothingFrame) {
        val p = frame.payload
        when (frame.command) {
            NothingCommand.EVENT_BATTERY_CHANGED -> {
                _replies[key(frame.command)] = p.copyOf()
                battery(p)
            }
            NothingCommand.EVENT_NOISE_REDUCTION_LEVEL_CHANGED -> {
                _replies[key(frame.command)] = p.copyOf()
                noise(p)
            }
            // A change made on the earbuds: read it again.
            NothingCommand.EVENT_GAME_MODE_CHANGED -> outbox += frame(NothingCommand.GET_HOST_LAG_MODE)
        }
    }

    private fun reply(frame: NothingFrame) {
        val request = frame.answers
        if (request in NothingCommand.PRIVATE) return
        if (!frame.ok) {
            when {
                // Before activation the earbuds refuse everything; activate once, then read again.
                frame.responseCode == NOT_ACTIVATED && !activationSent -> {
                    activationSent = true
                    outbox += frame(NothingCommand.SET_PROTOCOL_ACTIVATED)
                    outbox += reads()
                }
                // A refused write: read the setting again, so the screen shows what the earbuds hold.
                request in WRITES -> WRITES[request]?.let { outbox += frame(it.first, *it.second) }
            }
            return
        }
        if (request in WRITES || request == NothingCommand.SET_PROTOCOL_ACTIVATED) return
        val p = frame.payload
        _replies[key(request)] = p.copyOf()
        if (p.isEmpty()) return
        when (request) {
            NothingCommand.GET_DEVICE_MODEL -> productId = p.reversed().joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }.uppercase()
            NothingCommand.GET_HOST_VERSION_DEVICE -> firmware = p.takeWhile { it != 0.toByte() }.toByteArray().decodeToString().trim().ifEmpty { null }
            NothingCommand.GET_REMOTE_BATTERY_LEVEL -> battery(p)
            NothingCommand.GET_CURRENT_NOISE_REDUCTION -> noise(p)
            NothingCommand.GET_EQ_MODE -> eqMode = le(p)
            NothingCommand.GET_CUSTOM_EQ_VALUE -> customEq = simpleEq(p)
            NothingCommand.GET_EXTRA_FEATURE_STATUS -> pairs(p).firstOrNull { it.first == FEATURE_WEAR_DETECT }?.let { earDetection = it.second == 1 }
            NothingCommand.GET_HOST_LAG_MODE -> lowLag = p.u8(0) == LAG_ON
            NothingCommand.GET_BASS_BOOST -> bass = (p.u8(0) == 1) to (if (p.size > 1) p.u8(1) else BASS_MIN).coerceIn(BASS_MIN, BASS_MAX)
            NothingCommand.GET_SPATIAL_AUDIO -> spatial = p.copyOf()
            NothingCommand.GET_LHDC_COMMANDS -> codec = p.u8(0)
            NothingCommand.GET_KEY_CONFIGURATION -> {
                keys = keyTable(p)
                keysRead = true
            }
        }
    }

    /** The battery: a count, then the part (2 left, 3 right, 4 case, 6 both, 1 a watch) and its level; bit 7 is charging. */
    private fun battery(p: ByteArray) {
        val values = mutableMapOf<Command, Value>()
        for ((part, raw) in pairs(p)) {
            val level = (raw and LEVEL_MASK).takeIf { it <= PERCENT }
            when (part) {
                PART_LEFT -> values[Command.BATTERY_LEFT] = Value.Number(level)
                PART_RIGHT -> values[Command.BATTERY_RIGHT] = Value.Number(level)
                PART_CASE -> values[Command.BATTERY_CASE] = Value.Number(level)
                PART_BOTH -> {
                    values[Command.BATTERY_LEFT] = Value.Number(level)
                    if (form == Form.EARBUDS) values[Command.BATTERY_RIGHT] = Value.Number(level)
                }
            }
        }
        state = state.copy(values = state.values + values)
    }

    /** The noise report: three-byte groups of type, value and 0; type 1 the mode in effect, type 2 the strength to go back to. */
    private fun noise(p: ByteArray) {
        for (i in 0 until p.size / 3) {
            val type = p.u8(i * 3)
            val value = p.u8(i * 3 + 1)
            when (type) {
                NOISE_MODE -> {
                    noiseMode = value
                    if (value in LEVELS.values) noiseLevel = value
                }
                NOISE_LEVEL -> if (value in LEVELS.values) noiseLevel = value
            }
        }
    }

    /**
     * The custom EQ as Nothing X reads it (CustomEQ): a count, the total gain (a float), then per filter its
     * type (0 low shelf, 1 peak, 2 high shelf) and its gain, frequency and Q as little-endian floats.
     */
    private fun simpleEq(p: ByteArray): Map<Int, Int>? {
        val n = p.u8(0)
        if (p.size < SIMPLE_EQ_HEAD + n * SIMPLE_EQ_FILTER) return null
        return (0 until n).associate { i ->
            val at = SIMPLE_EQ_HEAD + i * SIMPLE_EQ_FILTER
            p.u8(at) to floatLe(p, at + 1).roundToIntSafe()
        }
    }

    private fun Float.roundToIntSafe(): Int = if (isNaN()) 0 else (if (this < 0) this - 0.5f else this + 0.5f).toInt()

    private fun floatLe(p: ByteArray, at: Int): Float = Float.fromBits(le(p.copyOfRange(at, at + 4)))

    private fun floatBytes(f: Float): List<Int> = f.toRawBits().let { b -> (0 until 4).map { (b shr (8 * it)) and 0xFF } }

    /** True when the model has the custom EQ Nothing X calls simple. */
    private val hasSimpleEq: Boolean get() = model?.configs?.any { it.simpleEq != null } == true

    /**
     * The EQ the earbuds last reported: the mode (0 balanced, 1 voice, 2 more treble, 3 more bass, 5 the
     * custom curve) and the custom curve's gains in whole dB, bass, mid and treble, where they have one.
     */
    public val eq: NothingEq? get() = eqMode?.let { NothingEq(EQ_PRESETS[it], it == EQ_CUSTOM, customEq?.let { c -> SIMPLE_ORDER.map { t -> c[t] ?: 0 } }) }

    /** The presets the model has, in the order Nothing X lists them, by [EQ_ORDER]'s names. */
    public val eqPresets: List<String> get() = if (config?.eqPresets != false) EQ_ORDER else emptyList()

    /** The custom EQ's filters (docs/nothing/PROTOCOL.md section 5), or null where the model has none. */
    public val simpleEqFilters: NothingSimpleEq? get() = config?.simpleEq

    /** The frames that put preset [name] on the earbuds. */
    public fun eqPreset(name: String): List<NothingFrame>? {
        val v = EQ_PRESETS.entries.firstOrNull { it.value == name }?.key ?: return null
        eqMode = v
        readBacks += frame(NothingCommand.GET_EQ_MODE)
        // The mode as two bytes, low byte first, as Nothing X writes it.
        return listOf(frame(NothingCommand.SET_EQ_MODE, v, 0))
    }

    /**
     * The frames that put the custom curve [gains] (bass, mid, treble in whole dB, from -6 to 6) on the earbuds,
     * as Nothing X's SimpleEqualizerViewModel writes them: the custom mode first where it is not on, then the
     * three filters with a total gain of minus the highest gain, so the curve does not clip.
     */
    public fun eqCustom(gains: List<Int>): List<NothingFrame>? {
        val f = config?.simpleEq ?: return null
        if (gains.size != SIMPLE_ORDER.size) return null
        val byType = SIMPLE_ORDER.zip(gains.map { it.coerceIn(-SIMPLE_EQ_RANGE, SIMPLE_EQ_RANGE) }).toMap()
        val out = mutableListOf<NothingFrame>()
        if (eqMode != EQ_CUSTOM) out += frame(NothingCommand.SET_EQ_MODE, EQ_CUSTOM, 0)
        eqMode = EQ_CUSTOM
        customEq = byType
        val total = -(byType.values.maxOrNull() ?: 0).coerceAtLeast(0).toFloat()
        val filters = mapOf(LOW_SHELF to (f.lowHz to f.lowQ), PEAK to (f.peakHz to f.peakQ), HIGH_SHELF to (f.highHz to f.highQ))
        val payload = mutableListOf(SIMPLE_WIRE_ORDER.size)
        payload += floatBytes(total)
        for (type in SIMPLE_WIRE_ORDER) {
            val (hz, q) = filters.getValue(type)
            payload += listOf(type) + floatBytes(byType.getValue(type).toFloat()) + floatBytes(hz.toFloat()) + floatBytes(q.toFloat())
        }
        // Nothing X's CustomEQ sizes its buffer at 16 bytes a filter and fills 13: the rest goes out as zeros.
        while (payload.size < SIMPLE_EQ_HEAD + SIMPLE_WIRE_ORDER.size * SIMPLE_EQ_BUFFER) payload += 0
        out += frame(NothingCommand.SET_CUSTOM_EQ, *payload.toIntArray())
        readBacks += frame(NothingCommand.GET_EQ_MODE)
        readBacks += frame(NothingCommand.GET_CUSTOM_EQ_VALUE)
        return out
    }

    /** The key table: a count, then the device, button, gesture and action of each. */
    private fun keyTable(p: ByteArray): Map<Triple<Int, Int, Int>, Int> {
        val n = p.u8(0)
        return (0 until n).filter { 1 + it * 4 + 3 < p.size }.associate { i ->
            val at = 1 + i * 4
            Triple(p.u8(at), p.u8(at + 1), p.u8(at + 2)) to p.u8(at + 3)
        }
    }

    /** Puts what the earbuds reported where the shared screens read it. */
    private fun refresh() {
        val c = config
        val values = state.values.toMutableMap()
        firmware?.let { values[Command.FIRMWARE_VERSION] = Value.Text("${productId ?: BRAND}_$it") }
        val mode = noiseMode
        if (sharedNoise && mode != null) {
            values[Command.ANC_MODE] = Value.Number(
                when (mode) {
                    MODE_OFF -> ancNumber("off")
                    MODE_TRANSPARENCY -> ancNumber("transparency")
                    else -> ancNumber("anc")
                },
            )
        }
        earDetection?.takeIf { c?.earDetection != false }?.let { values[Command.IN_EAR_DETECTION] = Value.Switch(it) }
        lowLag?.takeIf { c?.lowLatency != false }?.let { values[Command.GAME_MODE] = Value.Switch(it) }
        if (form == Form.EARBUDS && productId != null) {
            values[Command.FIND_LEFT] = Value.Switch(PART_LEFT in ringing)
            values[Command.FIND_RIGHT] = Value.Switch(PART_RIGHT in ringing)
        }
        state = state.copy(values = values)
    }

    /** The cancelling strengths the model has, in Nothing X's order. */
    private val levels: List<String> get() = config?.anc?.let { a -> LEVELS.keys.filter { it in a } } ?: LEVELS.keys.toList()

    /** Off, cancelling and transparency on the shared control; the Ear (stick), which has no transparency, gets a switch. */
    private val sharedNoise: Boolean get() = config?.anc?.let { "transparency" in it && levels.isNotEmpty() } ?: true

    /** Every setting the earbuds have, grouped by heading and tab. */
    public val groups: List<SettingGroup>
        get() {
            val c = config
            return listOfNotNull(
                group("noise", SettingTab.HOME, noiseRows(c)),
                group(
                    "sound", SettingTab.SOUND,
                    listOfNotNull(
                        // A known model's presets are on the Sound tab's equalizer; this row is for the others.
                        eqMode?.takeIf { c == null }?.let { Setting.Choice("eq_preset", EQ_PRESETS[it] ?: EQ_OTHER[it], EQ_ORDER) },
                        bass?.takeIf { c?.bassBoost != false }?.let { Setting.Toggle("bass_boost", it.first) },
                        bass?.takeIf { c?.bassBoost != false && it.first }?.let { Setting.Level("bass_level", it.second, BASS_MIN, BASS_MAX) },
                        spatial?.takeIf { c?.spatialAudio != false }?.let { Setting.Toggle("spatial_audio", it.u8(0) == 1) },
                        spatial?.takeIf { c?.spatialAudio != false && model?.headTracking == true && it.size > 1 }
                            ?.let { Setting.Toggle("head_tracking", it.u8(1) == 1, enabled = it.u8(0) == 1) },
                    ),
                ),
                group(
                    "connection", SettingTab.DEVICE,
                    listOfNotNull(
                        codec?.let { v ->
                            val options = listOf(CODEC_STANDARD) + (c?.codecs ?: CODECS.values.filter { it != CODEC_STANDARD })
                            Setting.Choice("codec", CODECS[v], options).takeIf { options.size > 1 }
                        },
                    ),
                ),
                group("find", SettingTab.DEVICE, listOfNotNull(Setting.Action("find", PART_WHOLE in ringing).takeIf { productId != null && form != Form.EARBUDS })),
            ) + touchGroups(c)
        }

    private fun noiseRows(c: NothingConfig?): List<Setting> = buildList {
        val mode = noiseMode ?: return@buildList
        if (c != null && levels.isEmpty()) return@buildList
        if (!sharedNoise) add(Setting.Toggle("anc", mode != MODE_OFF))
        val cancelling = mode != MODE_OFF && mode != MODE_TRANSPARENCY
        if (cancelling && levels.size > 1) {
            val current = LEVELS.entries.firstOrNull { it.value == mode }?.key ?: LEVELS.entries.firstOrNull { it.value == noiseLevel }?.key
            add(Setting.Choice("noise_strength", current, levels))
        }
    }

    /**
     * The touch controls, one group per side (or the whole product, or the CMF Buds Pro 2's case dial), with the
     * actions Nothing X offers for each gesture, and for what a gesture does during a call where Nothing X lets
     * the user set that too (button 9). Only once the earbuds reported their key table, and only for gestures the
     * user can set.
     */
    private fun touchGroups(c: NothingConfig?): List<SettingGroup> {
        if (!keysRead || c == null) return emptyList()
        val sides = mutableMapOf<String, MutableList<Setting>>()
        for (g in c.gestures) {
            if (!g.custom || g.button != BUTTON_TOUCH && g.button != BUTTON_CALL) continue
            val name = (GESTURES[g.gesture] ?: continue) + if (g.button == BUTTON_CALL) CALL_SUFFIX else ""
            val options = g.operations.mapNotNull { ACTIONS[it] }.distinct()
            if (options.size < 2) continue
            val targets = when (g.device) {
                DEVICE_EARBUD -> listOf(PART_LEFT to "left", PART_RIGHT to "right")
                PART_CASE -> listOf(PART_CASE to "case")
                PART_WHOLE -> listOf(PART_WHOLE to "whole")
                else -> emptyList()
            }
            for ((device, side) in targets) {
                val current = keys[Triple(device, g.button, g.gesture)] ?: continue
                sides.getOrPut(side) { mutableListOf() } += Setting.Choice("touch_${name}_$side", ACTIONS[current] ?: ACTION_SHOWN[current] ?: "other", options)
            }
        }
        return sides.map { (side, rows) -> SettingGroup(if (side == "whole") "touch" else "touch_$side", SettingTab.CONTROLS, rows) }
    }

    private fun group(key: String, tab: SettingTab, settings: List<Setting>) = settings.takeIf { it.isNotEmpty() }?.let { SettingGroup(key, tab, it) }

    /** The frames for a shared setting from the screens, or null when the earbuds do not have it. */
    public fun set(command: Command, value: Int): List<NothingFrame>? {
        if (command !in state.values) return null
        val out = when (command) {
            Command.ANC_MODE -> {
                if (!sharedNoise) return null
                val mode = when (value) {
                    ancNumber("off") -> MODE_OFF
                    ancNumber("transparency") -> MODE_TRANSPARENCY
                    ancNumber("anc") -> noiseLevel?.takeIf { l -> LEVELS.entries.any { it.value == l && it.key in levels } } ?: LEVELS.getValue(levels.first())
                    else -> return null
                }
                noiseMode = mode
                readBacks += frame(NothingCommand.GET_CURRENT_NOISE_REDUCTION, NOISE_GROUPS)
                frame(NothingCommand.SET_CURRENT_NOISE_REDUCTION, NOISE_MODE, mode, 0)
            }
            Command.IN_EAR_DETECTION -> {
                earDetection = value == 1
                readBacks += frame(NothingCommand.GET_EXTRA_FEATURE_STATUS)
                // A count of one, the wear detection feature, on or off.
                frame(NothingCommand.SET_EXTRA_FEATURE_STATUS, 1, FEATURE_WEAR_DETECT, if (value == 1) 1 else 0)
            }
            Command.GAME_MODE -> {
                lowLag = value == 1
                readBacks += frame(NothingCommand.GET_HOST_LAG_MODE)
                frame(NothingCommand.SET_LAG_MODE, if (value == 1) LAG_ON else LAG_OFF, 0)
            }
            Command.FIND_LEFT, Command.FIND_RIGHT -> {
                val part = if (command == Command.FIND_LEFT) PART_LEFT else PART_RIGHT
                if (value == 1) ringing += part else ringing -= part
                frame(NothingCommand.SET_WHERE_AM_I, part, if (value == 1) 1 else 0)
            }
            else -> return null
        }
        refresh()
        return listOf(out)
    }

    /** The frames for a change to one of the [groups], or null when the earbuds do not have the setting. */
    public fun change(change: SettingChange): List<NothingFrame>? {
        val setting = groups.flatMap { it.settings }.firstOrNull { it.key == change.key } ?: return null
        val out: NothingFrame = when (change) {
            is SettingChange.Toggle -> {
                if (setting !is Setting.Toggle || !setting.enabled) return null
                val on = change.on
                when (change.key) {
                    "anc" -> {
                        val mode = if (on) LEVELS.getValue(levels.first()) else MODE_OFF
                        noiseMode = mode
                        readBacks += frame(NothingCommand.GET_CURRENT_NOISE_REDUCTION, NOISE_GROUPS)
                        frame(NothingCommand.SET_CURRENT_NOISE_REDUCTION, NOISE_MODE, mode, 0)
                    }
                    "bass_boost" -> {
                        val level = bass?.second ?: BASS_MIN
                        bass = on to level
                        readBacks += frame(NothingCommand.GET_BASS_BOOST)
                        frame(NothingCommand.SET_BASS_BOOST, if (on) 1 else 0, level)
                    }
                    "spatial_audio", "head_tracking" -> {
                        val s = spatial ?: return null
                        val next = s.copyOf().also { it[if (change.key == "spatial_audio") 0 else 1] = (if (on) 1 else 0).toByte() }
                        spatial = next
                        readBacks += frame(NothingCommand.GET_SPATIAL_AUDIO)
                        // On, then head tracking where the model has it.
                        val payload = if (model?.headTracking == true && next.size > 1) intArrayOf(next.u8(0), next.u8(1)) else intArrayOf(next.u8(0))
                        frame(NothingCommand.SET_SPATIAL_AUDIO, *payload)
                    }
                    else -> return null
                }
            }
            is SettingChange.Level -> {
                if (setting !is Setting.Level || change.key != "bass_level") return null
                val level = change.value.coerceIn(BASS_MIN, BASS_MAX)
                bass = true to level
                readBacks += frame(NothingCommand.GET_BASS_BOOST)
                frame(NothingCommand.SET_BASS_BOOST, 1, level)
            }
            is SettingChange.Choice -> {
                if (setting !is Setting.Choice || change.option !in setting.options) return null
                when {
                    change.key == "noise_strength" -> {
                        val mode = LEVELS[change.option] ?: return null
                        noiseMode = mode
                        noiseLevel = mode
                        readBacks += frame(NothingCommand.GET_CURRENT_NOISE_REDUCTION, NOISE_GROUPS)
                        frame(NothingCommand.SET_CURRENT_NOISE_REDUCTION, NOISE_MODE, mode, 0)
                    }
                    change.key == "eq_preset" -> {
                        val v = EQ_PRESETS.entries.firstOrNull { it.value == change.option }?.key ?: return null
                        eqMode = v
                        readBacks += frame(NothingCommand.GET_EQ_MODE)
                        // The mode as two bytes, low byte first, as Nothing X writes it.
                        frame(NothingCommand.SET_EQ_MODE, v, 0)
                    }
                    change.key == "codec" -> {
                        val v = CODECS.entries.firstOrNull { it.value == change.option }?.key ?: return null
                        codec = v
                        readBacks += frame(NothingCommand.GET_LHDC_COMMANDS)
                        frame(NothingCommand.SET_LHDC_COMMANDS, v, 0)
                    }
                    change.key.startsWith("touch_") -> touch(change.key, change.option) ?: return null
                    else -> return null
                }
            }
            is SettingChange.Press -> {
                if (change.key != "find") return null
                // The whole product rings without a side byte.
                val on = PART_WHOLE !in ringing
                if (on) ringing += PART_WHOLE else ringing -= PART_WHOLE
                frame(NothingCommand.SET_WHERE_AM_I, if (on) 1 else 0)
            }
        }
        refresh()
        return listOf(out)
    }

    /** One gesture's action: a count of one, then the device, button, gesture and action, as Nothing X writes it. */
    private fun touch(key: String, option: String): NothingFrame? {
        val c = config ?: return null
        val side = key.substringAfterLast('_')
        val name = key.removePrefix("touch_").removeSuffix("_$side")
        val button = if (name.endsWith(CALL_SUFFIX)) BUTTON_CALL else BUTTON_TOUCH
        val gesture = GESTURES.entries.firstOrNull { it.value == name.removeSuffix(CALL_SUFFIX) }?.key ?: return null
        val device = when (side) {
            "left" -> PART_LEFT
            "right" -> PART_RIGHT
            "case" -> PART_CASE
            "whole" -> PART_WHOLE
            else -> return null
        }
        val g = c.gestures.firstOrNull {
            it.gesture == gesture && it.button == button && it.custom &&
                (it.device == device || it.device == DEVICE_EARBUD && (device == PART_LEFT || device == PART_RIGHT))
        } ?: return null
        val action = g.operations.firstOrNull { ACTIONS[it] == option } ?: return null
        keys = keys + (Triple(device, g.button, gesture) to action)
        readBacks += frame(NothingCommand.GET_KEY_CONFIGURATION)
        return frame(NothingCommand.SET_KEY_CONFIGURATION, 1, device, g.button, gesture, action)
    }

    private fun key(command: Int): String = command.toString(16).uppercase().padStart(4, '0')

    private fun pairs(p: ByteArray): List<Pair<Int, Int>> {
        if (p.isEmpty()) return emptyList()
        val n = p.u8(0)
        return (0 until n).filter { 2 + it * 2 < p.size }.map { p.u8(1 + it * 2) to p.u8(2 + it * 2) }
    }

    private fun le(p: ByteArray): Int = p.take(4).foldIndexed(0) { i, acc, b -> acc or ((b.toInt() and 0xFF) shl (8 * i)) }

    private fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF

    public companion object {
        private const val BRAND = "Nothing"
        private const val NOT_ACTIVATED = 2
        private const val NOISE_GROUPS = 3
        private const val NOISE_MODE = 1
        private const val NOISE_LEVEL = 2

        // Noise modes (Nothing X's DeviceNoiseReduction and DeviceConstant).
        private const val MODE_OFF = 5
        private const val MODE_TRANSPARENCY = 7

        /** The cancelling strengths and their modes, in the order Nothing X shows them. */
        private val LEVELS = linkedMapOf("adaptive" to 4, "high" to 1, "mid" to 2, "low" to 3)

        private const val PART_LEFT = 2
        private const val PART_RIGHT = 3
        private const val PART_CASE = 4
        private const val PART_WHOLE = 6
        private const val PART_BOTH = 6
        private const val DEVICE_EARBUD = 2
        private const val LEVEL_MASK = 0x7F
        private const val PERCENT = 100

        private const val FEATURE_WEAR_DETECT = 1
        private const val LAG_ON = 1
        private const val LAG_OFF = 2
        private const val BASS_MIN = 1
        private const val BASS_MAX = 5

        /** Nothing X's EQModeEntity: the presets by value. 4 is Dirac's EQ and 5 the custom one. */
        private val EQ_PRESETS = mapOf(0 to "balanced", 1 to "voice", 2 to "treble", 3 to "bass")
        private val EQ_OTHER = mapOf(4 to "dirac", 5 to "custom")
        private const val EQ_CUSTOM = 5

        // The custom EQ's filter types (CustomEQ), the order PairFree shows them in (bass, mid, treble) and the
        // order Nothing X sends them in (its radar: mid, treble, bass).
        private const val LOW_SHELF = 0
        private const val PEAK = 1
        private const val HIGH_SHELF = 2
        private val SIMPLE_ORDER = listOf(LOW_SHELF, PEAK, HIGH_SHELF)
        private val SIMPLE_WIRE_ORDER = listOf(PEAK, HIGH_SHELF, LOW_SHELF)
        private const val SIMPLE_EQ_HEAD = 5
        private const val SIMPLE_EQ_FILTER = 13
        private const val SIMPLE_EQ_BUFFER = 16

        /** Nothing X's radar goes from -6 to 6 dB in whole steps (EQLabelItem.convertToGain). */
        public const val SIMPLE_EQ_RANGE: Int = 6

        /** The order Nothing X lists them in. */
        private val EQ_ORDER = listOf("balanced", "bass", "treble", "voice")

        /** The codec setting's values: nadamas found 2 is LDAC on an Ear (3a), and 1 LHDC. */
        private const val CODEC_STANDARD = "standard"
        private val CODECS = mapOf(0 to CODEC_STANDARD, 1 to "lhdc", 2 to "ldac")

        private const val BUTTON_TOUCH = 1

        /** The key table's button for what a gesture does during a call (Nothing X's callControlsButton). */
        private const val BUTTON_CALL = 9
        private const val CALL_SUFFIX = "_call"

        /** 10 is turning the CMF Buds Pro 2's case dial. */
        private val GESTURES = mapOf(1 to "single", 2 to "double", 3 to "triple", 7 to "hold", 9 to "double_hold", 10 to "turn")

        /**
         * The actions PairFree offers on a gesture, by Nothing X's operation number (its control_* and case_* labels).
         * 31, Nothing's news summary on Nothing phones, is left out.
         */
        private val ACTIONS = mapOf(
            1 to "none", 2 to "play_pause", 3 to "answer_call", 8 to "previous", 9 to "next", 10 to "noise_control",
            11 to "voice_assistant", 17 to "game_mode", 18 to "volume_up", 19 to "volume_down", 23 to "volume_control",
            25 to "answer_mute", 26 to "hang_up", 27 to "spatial_audio", 29 to "mic_mute", 34 to "eq_preset",
            35 to "bass_boost", 36 to "treble_boost",
        )

        /** Actions the earbuds can hold that PairFree shows but does not offer: the default noise cycle (0x16). */
        private val ACTION_SHOWN = mapOf(22 to "noise_control")

        /** The shared noise mode's number for [name] (data/soundpeats/commands.json). */
        private fun ancNumber(name: String): Int = Command.ANC_MODE.labels.entries.first { it.value == name }.key

        /** Each write, and the read that shows its value again after the earbuds refused it. */
        private val WRITES: Map<Int, Pair<Int, IntArray>> = mapOf(
            NothingCommand.SET_CURRENT_NOISE_REDUCTION to (NothingCommand.GET_CURRENT_NOISE_REDUCTION to intArrayOf(NOISE_GROUPS)),
            NothingCommand.SET_EQ_MODE to (NothingCommand.GET_EQ_MODE to intArrayOf()),
            NothingCommand.SET_CUSTOM_EQ to (NothingCommand.GET_CUSTOM_EQ_VALUE to intArrayOf()),
            NothingCommand.SET_EXTRA_FEATURE_STATUS to (NothingCommand.GET_EXTRA_FEATURE_STATUS to intArrayOf()),
            NothingCommand.SET_LAG_MODE to (NothingCommand.GET_HOST_LAG_MODE to intArrayOf()),
            NothingCommand.SET_BASS_BOOST to (NothingCommand.GET_BASS_BOOST to intArrayOf()),
            NothingCommand.SET_SPATIAL_AUDIO to (NothingCommand.GET_SPATIAL_AUDIO to intArrayOf()),
            NothingCommand.SET_LHDC_COMMANDS to (NothingCommand.GET_LHDC_COMMANDS to intArrayOf()),
            NothingCommand.SET_KEY_CONFIGURATION to (NothingCommand.GET_KEY_CONFIGURATION to intArrayOf()),
            NothingCommand.SET_WHERE_AM_I to (NothingCommand.GET_REMOTE_BATTERY_LEVEL to intArrayOf()),
        )
    }
}

/**
 * Nothing earbuds' EQ as they last reported it: the [preset] by [NothingSession.eqPresets]' names, or null for
 * another mode; [custom] when the custom curve is on; [gains] that curve (bass, mid, treble), where they hold one.
 */
public data class NothingEq(val preset: String?, val custom: Boolean, val gains: List<Int>?)
