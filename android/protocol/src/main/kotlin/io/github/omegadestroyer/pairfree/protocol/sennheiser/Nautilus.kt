package io.github.omegadestroyer.pairfree.protocol.sennheiser

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value

/**
 * A Nautilus packet as Smart Control 4.9.4 writes it (docs/sennheiser/PROTOCOL.md section 4.10): `EB FE 01 00 01 00 00 00`, the length of the header and the
 * payload (two bytes, little-endian), a CRC-8 over those (polynomial 07, initial FF; two bytes, little-endian), the header (module, optional submodule and command, each two
 * bytes little-endian) and the payload. On the serial port it is wrapped in `2A` and `2A` with `2A` and `3F` escaped.
 */
public class NautilusPacket(public val module: Int, public val submodule: Int?, public val command: Int, public val payload: ByteArray = ByteArray(0)) {
    public val key: Int get() = key(module, submodule, command)

    private fun le(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())

    /** The packet's bytes, before the serial port's escaping. */
    public fun packet(): ByteArray {
        val body = le(module) + (submodule?.let { le(it) } ?: ByteArray(0)) + le(command) + payload
        return PREFIX + le(body.size) + le(crc8(body)) + body
    }

    /** What goes to the serial port: the packet escaped between two delimiters. */
    public fun bytes(): ByteArray = frame(packet())

    public companion object {
        public val PREFIX: ByteArray = byteArrayOf(0xEB.toByte(), 0xFE.toByte(), 1, 0, 1, 0, 0, 0)
        private const val DELIMITER = 0x2A
        private const val ESCAPE = 0x3F
        private const val ESCAPED_DELIMITER = 0x2F

        public fun key(module: Int, submodule: Int?, command: Int): Int = ((module and 0xFFF) shl 20) or (((submodule ?: 0xFFF) and 0xFFF) shl 8) or (command and 0xFF)

        /** The app's CRC-8 (`accumulateCrc8`): polynomial 07, initial FF, most significant bit first. */
        public fun crc8(data: ByteArray): Int {
            var crc = 0xFF
            for (b in data) {
                for (bit in 7 downTo 0) {
                    val x = ((crc shr 7) xor ((b.toInt() shr bit) and 1)) and 1
                    crc = (crc shl 1) and 0xFF
                    if (x != 0) crc = crc xor 0x07
                }
            }
            return crc
        }

        public fun frame(packet: ByteArray): ByteArray {
            val out = ArrayList<Byte>(packet.size + 4)
            out += DELIMITER.toByte()
            for (b in packet) {
                val v = b.toInt() and 0xFF
                when (v) {
                    DELIMITER -> {
                        out += ESCAPE.toByte()
                        out += ESCAPED_DELIMITER.toByte()
                    }
                    ESCAPE -> {
                        out += ESCAPE.toByte()
                        out += ESCAPE.toByte()
                    }
                    else -> out += b
                }
            }
            out += DELIMITER.toByte()
            return out.toByteArray()
        }
    }
}

/** What a Nautilus device answered: its header and payload (the app's `getHeaderOffset` is 12 bytes: the prefix, the length and the checksum). */
public class NautilusReply(public val header: ByteArray, public val payload: ByteArray)

/** Cuts the serial port's bytes into packets (the app's `SPPDeframer`) and checks each one's length and checksum. */
public class NautilusParser {
    private var data = ArrayList<Byte>()
    private var inFrame = false
    private var escaped = false

    /** The packets of [bytes], each as the bytes after the 12 byte prefix: header and payload. */
    public fun feed(bytes: ByteArray): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (!inFrame) {
                if (v == 0x2A) inFrame = true
                continue
            }
            if (escaped) {
                escaped = false
                when (v) {
                    0x3F -> data += b
                    0x2F -> data += 0x2A.toByte()
                    else -> {
                        data = ArrayList()
                        inFrame = false
                    }
                }
                continue
            }
            when (v) {
                0x3F -> escaped = true
                0x2A -> {
                    if (data.isNotEmpty()) decoded(data.toByteArray())?.let { out += it }
                    data = ArrayList()
                }
                else -> data += b
            }
        }
        return out
    }

    private fun decoded(p: ByteArray): ByteArray? {
        if (p.size < 12 || !p.copyOfRange(0, 8).contentEquals(NautilusPacket.PREFIX)) return null
        val length = (p[8].toInt() and 0xFF) or ((p[9].toInt() and 0xFF) shl 8)
        val crc = (p[10].toInt() and 0xFF) or ((p[11].toInt() and 0xFF) shl 8)
        val body = p.copyOfRange(12, p.size)
        if (body.size != length || NautilusPacket.crc8(body) != crc) return null
        return body
    }
}

public object NautilusModels {
    public val ALL: List<NautilusModel> get() = NAUTILUS_MODELS

    public fun fromName(name: String): NautilusModel? = ALL.firstOrNull { m -> m.names.any { it.equals(name.trim(), ignoreCase = true) } }

    public fun model(name: String): io.github.omegadestroyer.pairfree.protocol.Model? = fromName(name)?.let { io.github.omegadestroyer.pairfree.protocol.Model(it.name, listOf(it.name), "Sennheiser", fitKeepsAnc = true, form = it.form) }
}

/**
 * A Sennheiser Nautilus headphone (Momentum 3, PXC 550, PXC 550-II; docs/sennheiser/PROTOCOL.md section 4.10): one command at a time over the serial port, the answer
 * carrying the module and command of the command's answer in the product file. The rows are the ones Smart Control's classes for these products set up: battery,
 * noise cancelling (and the Momentum 3's ambient audio), smart pause, voice prompts, call enhancement and the sound presets. Not tried on headphones.
 */
public class NautilusSession(public val model: NautilusModel) : SessionTalk<ByteArray>(), SennheiserEqSource {
    private var current: EarbudState = EarbudState()
    override val state: EarbudState get() = current
    override val pacing: Pacing = Pacing(replyMs = 3_000L, gapMs = 50L)

    private val parser = NautilusParser()
    private val asked = mutableSetOf<Int>()
    private val values = mutableMapOf<String, ByteArray>()
    private var readBacks = emptyList<ByteArray>()

    /** The last answer to each property (header stripped), for the device report. */
    public val replies: Map<String, ByteArray> get() = values

    public var firmware: String? = null
        private set

    private fun prop(id: String): NautilusProp? = model.props[id]

    private fun send(cmd: NautilusCmd, payload: ByteArray = ByteArray(0)): ByteArray {
        val p = NautilusPacket(cmd.module, cmd.submodule, cmd.command, payload)
        // The answer comes back under the module and command of its own definition.
        asked += NautilusPacket.key(cmd.replyModule ?: cmd.module, cmd.replySubmodule ?: cmd.submodule, cmd.replyCommand ?: cmd.command)
        return p.bytes()
    }

    private fun u16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())

    private val byKey: Map<Int, NautilusProp> = model.props.values.filter { it.get != null }.associateBy { p ->
        val g = p.get!!
        NautilusPacket.key(g.replyModule ?: g.module, g.replySubmodule ?: g.submodule, g.replyCommand ?: g.command)
    }

    private fun read(id: String): ByteArray? {
        val p = prop(id) ?: return null
        val get = p.get ?: return null
        // The smart pause's read is its write with the mode STATUS (2); the director mode's settings are read at index 4.
        val payload = if (id == PROXIMITY) u16(PROXIMITY_STATUS) else if (id in DIRECTOR_SETTINGS) u16(DIRECTOR_INDEX) else ByteArray(0)
        return send(get, payload)
    }

    override fun start(): List<ByteArray> = READ_ON_OPEN.mapNotNull { read(it) } + (if (hasEq) knobReads() else emptyList())

    private val knobQueue = ArrayDeque<Int>()
    private val knobs = mutableMapOf<Int, Int>()

    private fun knobReads(): List<ByteArray> = (0..1).mapNotNull { index ->
        prop(EQ_KNOB)?.get?.let { send(it, u16(index)) }?.also { knobQueue.addLast(index) }
    }

    override fun poll(): List<ByteArray> = listOfNotNull(read(if (prop(BATTERY_PERCENT)?.get?.command == 0x03) BATTERY_PERCENT else BATTERY_LEVEL))

    override fun allowed(message: ByteArray): Boolean {
        val body = NautilusParser().feed(message).singleOrNull() ?: return false
        if (body.size < 4) return false
        val module = (body[0].toInt() and 0xFF) or ((body[1].toInt() and 0xFF) shl 8)
        return model.props.values.any { p -> listOfNotNull(p.get, p.set).any { c -> sameHeader(c, body, module) } }
    }

    private fun sameHeader(c: NautilusCmd, body: ByteArray, module: Int): Boolean {
        if (c.module != module) return false
        val sub = c.submodule
        val at = if (sub == null) 2 else 4
        if (body.size < at + 2) return false
        if (sub != null && ((body[2].toInt() and 0xFF) or ((body[3].toInt() and 0xFF) shl 8)) != sub) return false
        return ((body[at].toInt() and 0xFF) or ((body[at + 1].toInt() and 0xFF) shl 8)) == c.command
    }

    override fun bytes(message: ByteArray): ByteArray = message

    override fun answers(sent: ByteArray, got: Any): Boolean {
        val body = NautilusParser().feed(sent).singleOrNull() ?: return false
        return got is Int && headerKey(body, byRequest = true) == got
    }

    /** The key of the answer to the request in [body]: the answer's own module and command when the file gives them. */
    private fun headerKey(body: ByteArray, byRequest: Boolean): Int? {
        if (body.size < 4) return null
        val module = (body[0].toInt() and 0xFF) or ((body[1].toInt() and 0xFF) shl 8)
        for (p in model.props.values) for (c in listOfNotNull(p.get, p.set)) {
            if (!sameHeader(c, body, module)) continue
            return if (byRequest) NautilusPacket.key(c.replyModule ?: c.module, c.replySubmodule ?: c.submodule, c.replyCommand ?: c.command) else NautilusPacket.key(c.module, c.submodule, c.command)
        }
        return null
    }

    override fun takeReadBacks(): List<ByteArray> = readBacks.also { readBacks = emptyList() }

    override fun unanswered(sent: ByteArray): Boolean = false

    override fun feed(bytes: ByteArray): Fed {
        var changed = false
        val keys = mutableListOf<Any>()
        for (body in parser.feed(bytes)) guarded {
            if (body.size < 4) return@guarded
            val module = (body[0].toInt() and 0xFF) or ((body[1].toInt() and 0xFF) shl 8)
            // A header is four bytes, or six when the module has a submodule: every property of the product whose answer this is takes the bytes (two can share a command).
            for (p in model.props.values) {
                val g = p.get ?: continue
                if (!answerMatches(g, body, module)) continue
                val headerLength = if ((g.replySubmodule ?: g.submodule) != null) 6 else 4
                val key = NautilusPacket.key(g.replyModule ?: g.module, g.replySubmodule ?: g.submodule, g.replyCommand ?: g.command)
                if (key in asked && key !in keys) keys += key
                if (decode(p, body.copyOfRange(headerLength, body.size))) changed = true
            }
        }
        return Fed(changed, keys)
    }

    private fun answerMatches(g: NautilusCmd, body: ByteArray, module: Int): Boolean {
        val m = g.replyModule ?: g.module
        val sub = g.replySubmodule ?: g.submodule
        val c = g.replyCommand ?: g.command
        if (m != module) return false
        val at = if (sub == null) 2 else 4
        if (body.size < at + 2) return false
        if (sub != null && ((body[2].toInt() and 0xFF) or ((body[3].toInt() and 0xFF) shl 8)) != sub) return false
        return ((body[at].toInt() and 0xFF) or ((body[at + 1].toInt() and 0xFF) shl 8)) == c
    }

    private fun decode(p: NautilusProp, data: ByteArray): Boolean {
        if (p.id == EQ_KNOB) {
            val index = knobQueue.removeFirstOrNull() ?: return false
            if (data.size < 2) return false
            knobs[index] = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
            val x = knobs[0] ?: return false
            val y = knobs[1] ?: return false
            val (g, peak) = SennheiserEq.threeKnobsPeakState(x, y)
            gains = g
            highEnd = peak
            return true
        }
        val before = values[p.id]
        if (before != null && before.contentEquals(data)) return false
        values[p.id] = data.copyOf()
        refresh()
        return true
    }

    private fun le16(id: String, at: Int = 0): Int? = values[id]?.takeIf { it.size >= at + 2 }?.let { (it[at].toInt() and 0xFF) or ((it[at + 1].toInt() and 0xFF) shl 8) }

    private fun u8(id: String, at: Int = 0): Int? = values[id]?.getOrNull(at)?.toInt()?.and(0xFF)

    private fun refresh() {
        val next = current.values.toMutableMap()
        // The Momentum 3 answers a percentage; the PXC 550s a voltage and a level of five steps.
        val percent = if (prop(BATTERY_PERCENT)?.get?.command == 0x03) le16(BATTERY_PERCENT) else le16(BATTERY_LEVEL, 2)?.let { it * 20 }
        percent?.takeIf { it in 0..100 }?.let { next[Command.BATTERY_LEFT] = Value.Number(it) }
        val v = values[FIRMWARE] ?: values[SOFTWARE]
        if (v != null && v.size >= 6) {
            firmware = (0 until 3).joinToString(".") { (le16(if (values[FIRMWARE] != null) FIRMWARE else SOFTWARE, it * 2) ?: 0).toString() }
            next[Command.FIRMWARE_VERSION] = Value.Text("${model.name}_$firmware")
        }
        current = current.copy(values = next)
    }

    // The rows ---------------------------------------------------------------------------------------------------------------------------------

    override val groups: List<SettingGroup>
        get() = buildList {
            val noise = buildList<Setting> {
                // The Momentum 3: the control type says whether the noise cancelling switch is off (0), on (1) or ambient (2).
                val control = u8(ANC_CONTROL)
                le16(ANC_MODE)?.let { mode -> if (control != 2) ANC_MODES.entries.firstOrNull { it.value == mode }?.let { add(Setting.Choice(KEY_ANC, it.key, ANC_MODES.keys.toList())) } }
                if (prop(AMBIENT_TOGGLE)?.set != null && control != null) add(Setting.Toggle(KEY_AMBIENT, control == 2, enabled = control != 0))
                u8(AMBIENT_AUDIO)?.let { add(Setting.Toggle(SennheiserSession.KEY_PAUSE_TRANSPARENT, it == 1)) }
                le16(ANC_STATE)?.let { s -> prop(ANC_STATE)?.values?.entries?.firstOrNull { it.value == s }?.let { add(Setting.Choice(KEY_ANC_STATE, it.key.lowercase(), prop(ANC_STATE)!!.values.keys.map { k -> k.lowercase() })) } }
                u8(ANC_MIDDLE)?.let { s -> prop(ANC_MIDDLE)?.values?.entries?.firstOrNull { it.value == s }?.let { add(Setting.Choice(KEY_ANC_MIDDLE, it.key, prop(ANC_MIDDLE)!!.values.keys.toList())) } }
            }
            // The adaptive strength: the app's slider runs 0 to 8 and is used while the state is adaptive.
            le16(ANC_LEVEL)?.let { v -> add(SettingGroup("noise_level", SettingTab.HOME, listOf(Setting.Level(KEY_ANC_LEVEL, v.coerceIn(0, ANC_LEVEL_MAX), 0, ANC_LEVEL_MAX, enabled = le16(ANC_STATE) == ADAPTIVE)))) }
            if (noise.isNotEmpty()) add(SettingGroup("noise", SettingTab.HOME, noise))
            if (eqKnown) add(SettingGroup("sound", SettingTab.SOUND, listOf(Setting.Toggle(KEY_HIGH_END, highEnd))))
            le16(SOUND_PRESET)?.let { s ->
                prop(SOUND_PRESET)?.values?.entries?.firstOrNull { it.value == s }?.let {
                    add(SettingGroup("sound_preset", SettingTab.SOUND, buildList {
                        add(Setting.Choice(KEY_PRESET, it.key, prop(SOUND_PRESET)!!.values.keys.toList()))
                        // The director preset (4) has four more settings.
                        if (s == DIRECTOR_INDEX) for ((id, options) in DIRECTOR_SETTINGS) le16(id)?.let { v -> add(Setting.Choice("sh_nautilus_$id", options.getOrNull(v), options)) }
                    }))
                }
            }
            val other = buildList<Setting> {
                u8(PROXIMITY)?.let { add(Setting.Toggle(SennheiserSession.KEY_SMART_PAUSE, it == PROXIMITY_ENABLED)) }
                le16(CALL_ENHANCEMENT)?.let { add(Setting.Toggle(KEY_CALL_ENHANCEMENT, it == 1)) }
                u8(ASSISTANT)?.let { add(Setting.Toggle(KEY_ASSISTANT, it == ALEXA)) }
                u8(PROMPT_MODE)?.let { m -> PROMPTS.entries.firstOrNull { it.value == m }?.let { add(Setting.Choice(SennheiserSession.KEY_PROMPTS, it.key, PROMPTS.keys.toList())) } }
                le16(PROMPT_LANGUAGE)?.let { l ->
                    val names = prop(PROMPT_LANGUAGE)?.values.orEmpty()
                    val title = { k: String -> k.lowercase().replaceFirstChar { c -> c.uppercase() } }
                    names.entries.firstOrNull { it.value == l }?.let { e ->
                        if (prop(PROMPT_LANGUAGE)?.set != null) add(Setting.Choice(SennheiserSession.KEY_PROMPT_LANGUAGE, title(e.key), names.keys.map(title)))
                        else add(Setting.Information(SennheiserSession.KEY_PROMPT_LANGUAGE, title(e.key)))
                    }
                }
                u8(CODEC)?.let { c -> prop(CODEC)?.values?.entries?.firstOrNull { it.value == c }?.let { add(Setting.Information(SennheiserSession.KEY_CODEC, it.key.removePrefix("CODEC_"))) } }
            }
            if (other.isNotEmpty()) add(SettingGroup("other", SettingTab.DEVICE, other))
        }

    private fun write(id: String, payload: ByteArray, readId: String? = id): List<ByteArray>? {
        val p = prop(id) ?: return null
        val set = p.set ?: return null
        readId?.let { read(it)?.let { frame -> readBacks += frame } }
        return listOf(send(set, payload))
    }

    override fun change(change: SettingChange): List<ByteArray>? {
        when {
            change is SettingChange.Level && change.key == KEY_ANC_LEVEL -> {
                if (le16(ANC_STATE) != ADAPTIVE) return null
                val v = change.value.coerceIn(0, ANC_LEVEL_MAX)
                values[ANC_LEVEL] = u16(v)
                return write(ANC_LEVEL, u16(v))
            }
            change is SettingChange.Toggle && change.key == KEY_ASSISTANT -> {
                if (values[ASSISTANT] == null) return null
                val v = if (change.on) ALEXA else 0
                values[ASSISTANT] = byteArrayOf(v.toByte())
                return write(ASSISTANT, byteArrayOf(v.toByte()), ASSISTANT)
            }
            change is SettingChange.Choice && change.key == SennheiserSession.KEY_PROMPT_LANGUAGE -> {
                val v = prop(PROMPT_LANGUAGE)?.values?.entries?.firstOrNull { it.key.equals(change.option, ignoreCase = true) }?.value ?: return null
                if (prop(PROMPT_LANGUAGE)?.set == null) return null
                values[PROMPT_LANGUAGE] = u16(v)
                return write(PROMPT_LANGUAGE, u16(v))
            }
            change is SettingChange.Choice && change.key.startsWith("sh_nautilus_") && DIRECTOR_SETTINGS.containsKey(change.key.removePrefix("sh_nautilus_")) -> {
                val id = change.key.removePrefix("sh_nautilus_")
                val v = DIRECTOR_SETTINGS.getValue(id).indexOf(change.option)
                val p = prop(id) ?: return null
                if (v < 0 || p.set == null || le16(SOUND_PRESET) != DIRECTOR_INDEX) return null
                values[id] = u16(v)
                p.get?.let { g -> read(id)?.let { readBacks += it } }
                return listOf(send(p.set, u16(DIRECTOR_INDEX) + u16(v)))
            }
            change is SettingChange.Toggle && change.key == KEY_HIGH_END -> {
                val g = gains ?: return null
                highEnd = change.on
                return eqCurve(g)
            }
            change is SettingChange.Choice && change.key == KEY_ANC -> {
                val mode = ANC_MODES[change.option] ?: return null
                if (u8(ANC_CONTROL) == 0) return null
                values[ANC_MODE] = u16(mode)
                return write(ANC_MODE, u16(mode))
            }
            change is SettingChange.Toggle && change.key == KEY_AMBIENT -> {
                // The headphones toggle the ambient mode themselves: one command, no parameter.
                val control = u8(ANC_CONTROL) ?: return null
                if (control == 0 || (control == 2) == change.on) return null
                values[ANC_CONTROL] = byteArrayOf(if (change.on) 2 else 1, 0, 0)
                return write(AMBIENT_TOGGLE, ByteArray(0), ANC_CONTROL)
            }
            change is SettingChange.Toggle && change.key == SennheiserSession.KEY_PAUSE_TRANSPARENT -> {
                if (values[AMBIENT_AUDIO] == null) return null
                values[AMBIENT_AUDIO] = byteArrayOf(if (change.on) 1 else 0)
                return write(AMBIENT_AUDIO, byteArrayOf(if (change.on) 1 else 0))
            }
            change is SettingChange.Choice && change.key == KEY_ANC_STATE -> {
                val v = prop(ANC_STATE)?.values?.entries?.firstOrNull { it.key.equals(change.option, ignoreCase = true) }?.value ?: return null
                values[ANC_STATE] = u16(v)
                return write(ANC_STATE, u16(v))
            }
            change is SettingChange.Choice && change.key == KEY_ANC_MIDDLE -> {
                val v = prop(ANC_MIDDLE)?.values?.get(change.option) ?: return null
                values[ANC_MIDDLE] = byteArrayOf(v.toByte())
                return write(ANC_MIDDLE, byteArrayOf(v.toByte()))
            }
            change is SettingChange.Choice && change.key == KEY_PRESET -> {
                val v = prop(SOUND_PRESET)?.values?.get(change.option) ?: return null
                values[SOUND_PRESET] = u16(v)
                return write(SOUND_PRESET, u16(v))
            }
            change is SettingChange.Toggle && change.key == SennheiserSession.KEY_SMART_PAUSE -> {
                if (values[PROXIMITY] == null) return null
                val v = if (change.on) PROXIMITY_ENABLED else PROXIMITY_DISABLED
                values[PROXIMITY] = byteArrayOf(v.toByte())
                return write(PROXIMITY, u16(v))
            }
            change is SettingChange.Toggle && change.key == KEY_CALL_ENHANCEMENT -> {
                if (values[CALL_ENHANCEMENT] == null) return null
                values[CALL_ENHANCEMENT] = u16(if (change.on) 1 else 0)
                return write(CALL_ENHANCEMENT, u16(if (change.on) 1 else 0))
            }
            change is SettingChange.Choice && change.key == SennheiserSession.KEY_PROMPTS -> {
                val v = PROMPTS[change.option] ?: return null
                if (values[PROMPT_MODE] == null) return null
                values[PROMPT_MODE] = byteArrayOf(v.toByte())
                return write(PROMPT_MODE, byteArrayOf(v.toByte()))
            }
        }
        return null
    }

    // The Momentum 3's equalizer (docs/sennheiser/PROTOCOL.md section 4.10): three parameter groups of biquad coefficients, then the two knobs ----------------

    private val hasEq: Boolean get() = prop(EQ_PARAMS)?.set != null && prop(EQ_KNOB)?.get != null
    private var gains: List<Double>? = null
    private var highEnd = false

    override val eqGains: List<Double>? get() = gains
    override val eqKnown: Boolean get() = hasEq && gains != null
    override val eqBandsHz: List<Int> get() = if (hasEq) SennheiserEqTables.THREE_BAND_HZ else emptyList()
    override val eqMaxDb: Double get() = SennheiserEqTables.THREE_BAND_RANGE_DB.toDouble()
    override val eqMinDb: Double get() = -eqMaxDb
    override val eqPresets: List<Pair<String, List<Double>>> get() = if (hasEq) SennheiserEqTables.PRESETS["default3"].orEmpty() else emptyList()
    override val eq: Any? get() = if (eqKnown) gains to highEnd else null

    private fun le32(v: Long) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    override fun eqCurve(gains: List<Double>): List<ByteArray>? {
        if (!hasEq || gains.size != 3) return null
        val params = prop(EQ_PARAMS)!!.set!!
        val knob = prop(EQ_KNOB)!!.set ?: return null
        val clipped = gains.map { it.coerceIn(eqMinDb, eqMaxDb) }
        val out = SennheiserEq.m3Groups(clipped, highEnd).map { g ->
            send(params, byteArrayOf(g[0].toByte(), g[1].toByte(), g[2].toByte()) + g.drop(3).fold(ByteArray(0)) { acc, v -> acc + le32(v) })
        }.toMutableList()
        val (x, y) = SennheiserEq.threeKnobsPeak(clipped, highEnd)
        out += send(knob, u16(0) + u16(x))
        out += send(knob, u16(1) + u16(y))
        this.gains = clipped
        return out
    }

    public companion object {
        public const val KEY_HIGH_END: String = "sh_high_end"
        public const val KEY_ANC_LEVEL: String = "sh_nautilus_anc_level"
        public const val KEY_ASSISTANT: String = "sh_assistant"
        public const val KEY_ANC: String = "sh_nautilus_anc"
        public const val KEY_AMBIENT: String = "sh_nautilus_ambient"
        public const val KEY_ANC_STATE: String = "sh_nautilus_anc_state"
        public const val KEY_ANC_MIDDLE: String = "sh_nautilus_anc_middle"
        public const val KEY_PRESET: String = "sh_nautilus_preset"
        public const val KEY_CALL_ENHANCEMENT: String = "sh_call_enhancement"

        private const val ANC_LEVEL = "anc_level"
        private const val ASSISTANT = "assistant"
        private const val ANC_LEVEL_MAX = 8
        private const val ADAPTIVE = 2
        private const val ALEXA = 3

        /** The director preset's number (`Sound_Preset` DIRECTOR), at which the boost, reverb, dynamic loudness and virtual speaker position are read and set. */
        private const val DIRECTOR_INDEX = 4

        /** The choices of each director setting, in the app's order (`getOptionsBoost` and the others): the answer is an index into them. */
        private val DIRECTOR_SETTINGS = linkedMapOf(
            "boost" to listOf("off", "rumble", "thump", "voice"),
            "spatial" to listOf("off", "near", "medium", "far"),
            "reverb" to listOf("off", "low", "medium", "high"),
            "dlc" to listOf("off", "on"),
        )
        private const val EQ_PARAMS = "eq_params"
        private const val EQ_KNOB = "eq_knob"
        private const val BATTERY_PERCENT = "battery_percent"
        private const val BATTERY_LEVEL = "battery_level"
        private const val ANC_MODE = "anc_mode"
        private const val ANC_CONTROL = "anc_control"
        private const val AMBIENT_AUDIO = "ambient_audio"
        private const val AMBIENT_TOGGLE = "ambient_toggle"
        private const val ANC_STATE = "anc_state"
        private const val ANC_MIDDLE = "anc_middle"
        private const val PROXIMITY = "proximity"
        private const val PROMPT_MODE = "prompt_mode"
        private const val PROMPT_LANGUAGE = "prompt_language"
        private const val CODEC = "codec"
        private const val CALL_ENHANCEMENT = "call_enhancement"
        private const val SOUND_PRESET = "sound_preset"
        private const val FIRMWARE = "firmware_version"
        private const val SOFTWARE = "software_version"

        /** `Input_Proximity_Mode`'s values (the app's `_h`): disabled 0, enabled 1, status 2 (a read). */
        private const val PROXIMITY_DISABLED = 0
        private const val PROXIMITY_ENABLED = 1
        private const val PROXIMITY_STATUS = 2

        /** The Momentum 3's noise cancelling modes (`getAncModeFromValue`): 0 off, 1 airplane, 3 outdoor, 4 home. */
        private val ANC_MODES = linkedMapOf("off" to 0, "airplane" to 1, "outdoor" to 3, "home" to 4)

        /** `Setting_AudioPromptMode`: all disabled 0, voice 1, tone 2 (the app's `ED`). */
        private val PROMPTS = linkedMapOf("tone_voice_off" to 0, "tone_voice_on" to 1, "tone_on" to 2)

        private val READ_ON_OPEN = listOf(ANC_LEVEL, ASSISTANT, "boost", "spatial", "reverb", "dlc", BATTERY_PERCENT, BATTERY_LEVEL, ANC_CONTROL, ANC_MODE, AMBIENT_AUDIO, ANC_STATE, ANC_MIDDLE, PROXIMITY, PROMPT_MODE, PROMPT_LANGUAGE, CODEC, CALL_ENHANCEMENT, SOUND_PRESET, FIRMWARE, SOFTWARE)
    }
}
