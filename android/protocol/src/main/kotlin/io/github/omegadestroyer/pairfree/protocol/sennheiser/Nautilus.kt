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
public class NautilusSession(public val model: NautilusModel) : SessionTalk<ByteArray>() {
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
        // The smart pause's read is its write with the mode STATUS (2).
        val payload = if (id == PROXIMITY) u16(PROXIMITY_STATUS) else ByteArray(0)
        return send(get, payload)
    }

    override fun start(): List<ByteArray> = READ_ON_OPEN.mapNotNull { read(it) }

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
            if (noise.isNotEmpty()) add(SettingGroup("noise", SettingTab.HOME, noise))
            le16(SOUND_PRESET)?.let { s -> prop(SOUND_PRESET)?.values?.entries?.firstOrNull { it.value == s }?.let { add(SettingGroup("sound", SettingTab.SOUND, listOf(Setting.Choice(KEY_PRESET, it.key, prop(SOUND_PRESET)!!.values.keys.toList())))) } }
            val other = buildList<Setting> {
                u8(PROXIMITY)?.let { add(Setting.Toggle(SennheiserSession.KEY_SMART_PAUSE, it == PROXIMITY_ENABLED)) }
                le16(CALL_ENHANCEMENT)?.let { add(Setting.Toggle(KEY_CALL_ENHANCEMENT, it == 1)) }
                u8(PROMPT_MODE)?.let { m -> PROMPTS.entries.firstOrNull { it.value == m }?.let { add(Setting.Choice(SennheiserSession.KEY_PROMPTS, it.key, PROMPTS.keys.toList())) } }
                le16(PROMPT_LANGUAGE)?.let { l -> prop(PROMPT_LANGUAGE)?.values?.entries?.firstOrNull { it.value == l }?.let { add(Setting.Information(SennheiserSession.KEY_PROMPT_LANGUAGE, it.key.lowercase().replaceFirstChar { c -> c.uppercase() })) } }
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

    public companion object {
        public const val KEY_ANC: String = "sh_nautilus_anc"
        public const val KEY_AMBIENT: String = "sh_nautilus_ambient"
        public const val KEY_ANC_STATE: String = "sh_nautilus_anc_state"
        public const val KEY_ANC_MIDDLE: String = "sh_nautilus_anc_middle"
        public const val KEY_PRESET: String = "sh_nautilus_preset"
        public const val KEY_CALL_ENHANCEMENT: String = "sh_call_enhancement"

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

        private val READ_ON_OPEN = listOf(BATTERY_PERCENT, BATTERY_LEVEL, ANC_CONTROL, ANC_MODE, AMBIENT_AUDIO, ANC_STATE, ANC_MIDDLE, PROXIMITY, PROMPT_MODE, PROMPT_LANGUAGE, CODEC, CALL_ENHANCEMENT, SOUND_PRESET, FIRMWARE, SOFTWARE)
    }
}
