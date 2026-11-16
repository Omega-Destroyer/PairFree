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
 * A Sennheiser GAIA v3 product (docs/sennheiser/PROTOCOL.md sections 3 to 6), spoken the way Smart Control 4.9.4 speaks it: one command at a time, each
 * answered by a reply (type 2) or an error (type 3) with the same command, notifications of changes registered per feature. A row shows once its read has
 * been answered. [model] is the product when its name is known (its product file lists which properties it has); with none, every property is read.
 * Never sent: anything that is not in data/sennheiser/commands.json (factory reset, firmware update, the panic log...); [allowed] is a closed list.
 */
public class SennheiserSession(public val model: SennheiserModel?) : SessionTalk<ByteArray>() {
    private var current: EarbudState = EarbudState()
    override val state: EarbudState get() = current
    override val pacing: Pacing = Pacing(replyMs = REPLY_MS, gapMs = GAP_MS)

    private val parser = Gaia3Parser()
    private val commands: List<SennheiserCommand> = SennheiserCommands.ALL.filter { model?.properties == null || it.id in model.properties }
    private val byKey: Map<Int, SennheiserCommand> = SennheiserCommands.ALL.filter { it.get != null }.associateBy { Gaia3Packet.keyOf(it.vendor, it.get!!) }
    private val asked = mutableSetOf<Int>()
    private val answered = mutableMapOf<String, ByteArray>()
    private var readBacks = emptyList<ByteArray>()

    /** What the earbuds last answered for each property, as bytes: for the device report. */
    public val replies: Map<String, ByteArray> get() = answered

    private fun command(id: String): SennheiserCommand? = SennheiserCommands.ALL.firstOrNull { it.id == id }

    private fun frame(vendor: Int, word: Int, vararg payload: Int): ByteArray {
        val packet = Gaia3Packet(vendor, word, ByteArray(payload.size) { payload[it].toByte() })
        asked += packet.key
        return packet.bytes()
    }

    private fun read(c: SennheiserCommand): ByteArray = frame(c.vendor, c.get!!)

    override fun start(): List<ByteArray> = buildList {
        for (feature in REGISTERED) SennheiserWire.FEATURES[feature]?.let { add(frame(SennheiserWire.REGISTER_VENDOR, SennheiserWire.REGISTER_COMMAND, it)) }
        for (c in commands) if (c.get != null) add(read(c))
    }

    override fun poll(): List<ByteArray> = command(SennheiserCommands.BATTERY_PERCENT)?.let { listOf(read(it)) } ?: emptyList()

    /** Only the commands of data/sennheiser/commands.json go out: the reads and the writes of its properties, and the notification registration. */
    override fun allowed(message: ByteArray): Boolean {
        val packet = Gaia3Parser().feed(message).singleOrNull() ?: return false
        if (packet.vendor == SennheiserWire.REGISTER_VENDOR && packet.word == SennheiserWire.REGISTER_COMMAND) return true
        return SennheiserCommands.ALL.any { it.vendor == packet.vendor && (it.get == packet.word || it.set == packet.word) }
    }

    override fun bytes(message: ByteArray): ByteArray = message

    /** An answer is a reply or an error with the sent command's vendor and command. */
    override fun answers(sent: ByteArray, got: Any): Boolean = got is Int && Gaia3Parser().feed(sent).singleOrNull()?.key == got

    override fun takeReadBacks(): List<ByteArray> = readBacks.also { readBacks = emptyList() }

    override fun feed(bytes: ByteArray): Fed {
        var changed = false
        val keys = mutableListOf<Any>()
        for (packet in parser.feed(bytes)) guarded {
            log?.invoke("recv $packet")
            if (packet.key !in asked && !packet.isNotification) return@guarded
            if (packet.isReply || packet.isError) keys += packet.key
            if (packet.isError) return@guarded
            if (!packet.isReply && !packet.isNotification) return@guarded
            val c = byKey[packet.key] ?: return@guarded
            // A reply and a notification of the same property carry the same bytes (the product file's Event is its Response).
            if (decode(c, packet.payload)) changed = true
        }
        return Fed(changed, keys)
    }

    private val values = mutableMapOf<String, ByteArray>()

    /** Takes one property's bytes; true when what the screens show changed. */
    private fun decode(c: SennheiserCommand, payload: ByteArray): Boolean {
        val before = values[c.id]
        if (before != null && before.contentEquals(payload)) return false
        values[c.id] = payload.copyOf()
        answered[c.id] = payload.copyOf()
        refresh()
        return true
    }

    private fun u8(id: String, at: Int = 0): Int? = values[id]?.getOrNull(at)?.toInt()?.and(0xFF)

    private fun on(id: String): Boolean? = u8(id)?.let { it != 0 }

    /** Rebuilds the shared state from the stored bytes. */
    private fun refresh() {
        val next = current.values.toMutableMap()
        fun put(c: Command, v: Value?) {
            if (v == null) next.remove(c) else next[c] = v
        }
        values[SennheiserCommands.BATTERY_PERCENT]?.let { b ->
            val p = b.map { it.toInt() and 0xFF }
            val levels = batteryLevels(p)
            put(Command.BATTERY_LEFT, levels.getOrNull(0)?.let { Value.Number(it) })
            put(Command.BATTERY_RIGHT, levels.getOrNull(1)?.let { Value.Number(it) })
            put(Command.BATTERY_CASE, if (p.size == 3) p[2].takeIf { it in 0..100 }?.let { Value.Number(it) } else null)
        }
        // Noise control: Smart Control's own exclusivity (setting one turns the other off), read back as the three modes the screens know.
        val anc = on(SennheiserCommands.ANC_STATUS)
        val transparent = on(SennheiserCommands.TRANSPARENCY_STATUS)
        if (anc != null || transparent != null) {
            val name = when {
                transparent == true -> "transparency"
                anc == true -> "anc"
                else -> "off"
            }
            put(Command.ANC_MODE, Value.Number(Command.ANC_MODE.labels.entries.first { it.value == name }.key))
        }
        values[SennheiserCommands.FIRMWARE_VERSION]?.let { b ->
            if (b.size >= 6) {
                val v = (0 until 3).map { ((b[it * 2].toInt() and 0xFF) shl 8) or (b[it * 2 + 1].toInt() and 0xFF) }
                firmware = v.joinToString(".")
                put(Command.FIRMWARE_VERSION, Value.Text("${model?.name ?: "Sennheiser"}_$firmware"))
            }
        }
        current = current.copy(values = next)
    }

    /** The model number string the product reports (`Versions_GetModelId`), for the device report. */
    public val modelId: String? get() = values[SennheiserCommands.MODEL_ID]?.let { b -> String(b.filter { it.toInt() in 0x20..0x7E }.toByteArray(), Charsets.US_ASCII).trim().ifEmpty { null } }

    /** The charging state of each earbud (0 not, 1 charging, 2 complete; `Battery_ChargingStatus`), for the device report. */
    public val charging: List<Int> get() = values[SennheiserCommands.BATTERY_CHARGING]?.map { it.toInt() and 0xFF }.orEmpty()

    /** The version as the earbuds report it, for the device report. */
    public var firmware: String? = null
        private set

    /**
     * The levels Smart Control shows for the battery's bytes (`mapBatteryValue`): one value is a headband's (or a single earbud's); two or three are the left and
     * right earbud and the case; 255 is unknown, and a pair with a zero shows the larger on the left.
     */
    private fun batteryLevels(p: List<Int>): List<Int?> {
        fun level(v: Int) = v.takeIf { it in 0..100 }
        return when {
            p.isEmpty() -> emptyList()
            p.size == 1 -> listOf(level(p[0]))
            else -> {
                val left = if ((p[0] == 0 || p[1] == 0) && p[0] != 255 && p[1] != 255) maxOf(p[0], p[1]) else p[0]
                listOf(level(left), level(p[1]))
            }
        }
    }

    /** One or two bytes name a codec (the app's table); 255 and anything unknown are not shown. */
    private fun codec(): String? = u8(SennheiserCommands.CODEC)?.let { code -> command(SennheiserCommands.CODEC)?.values?.entries?.firstOrNull { it.value == code }?.key?.takeIf { it.isNotEmpty() } }

    /** ANC's modes as pairs of mode and state (1 anti-wind, 2 comfort, 3 adaptive; the app's note on `ANC`). */
    private fun ancState(mode: Int): Boolean? {
        val b = values[SennheiserCommands.ANC_MODE] ?: return null
        var i = 0
        while (i + 1 < b.size) {
            if ((b[i].toInt() and 0xFF) == mode) return (b[i + 1].toInt() and 0xFF) != 0
            i += 2
        }
        return null
    }

    override val groups: List<SettingGroup>
        get() = buildList {
            val noise = buildList<Setting> {
                ancState(ANTI_WIND)?.let { add(Setting.Toggle(KEY_ANTI_WIND, it, enabled = on(SennheiserCommands.ANC_STATUS) != false)) }
                ancState(ADAPTIVE)?.let { add(Setting.Toggle(KEY_ADAPTIVE, it, enabled = on(SennheiserCommands.ANC_STATUS) != false)) }
                on(SennheiserCommands.TRANSPARENCY_PAUSE)?.let { add(Setting.Toggle(KEY_PAUSE_TRANSPARENT, it)) }
            }
            if (noise.isNotEmpty()) add(SettingGroup("noise", SettingTab.HOME, noise))
            val calls = buildList<Setting> {
                on(SennheiserCommands.AUTO_CALL)?.let { add(Setting.Toggle(KEY_AUTO_CALL, it)) }
                on(SennheiserCommands.COMFORT_CALL)?.let { add(Setting.Toggle(KEY_COMFORT_CALL, it)) }
            }
            if (calls.isNotEmpty()) add(SettingGroup("calls", SettingTab.DEVICE, calls))
            val other = buildList<Setting> {
                on(SennheiserCommands.ON_HEAD)?.let { add(Setting.Toggle(KEY_ON_HEAD, it)) }
                on(SennheiserCommands.SMART_PAUSE)?.let { add(Setting.Toggle(KEY_SMART_PAUSE, it)) }
                on(SennheiserCommands.LOW_LATENCY)?.let { add(Setting.Toggle(KEY_LOW_LATENCY, it)) }
                // Smart Control's `Setting_AutoLockMode` is "to turn touch control on/off": 1 locks the touch controls.
                on(SennheiserCommands.TOUCH_LOCK)?.let { add(Setting.Toggle(KEY_TOUCH_LOCK, it)) }
                on(SennheiserCommands.ECO_MODE)?.let { add(Setting.Toggle(KEY_ECO, it)) }
                on(SennheiserCommands.BATTERY_PROTECTION)?.let { add(Setting.Toggle(KEY_BATTERY_PROTECTION, it)) }
                u8(SennheiserCommands.PROMPT_MODE)?.let { add(Setting.Choice(KEY_PROMPTS, PROMPTS.entries.firstOrNull { e -> e.value == it }?.key, PROMPTS.keys.toList())) }
                codec()?.let { add(Setting.Information(KEY_CODEC, it)) }
            }
            if (other.isNotEmpty()) add(SettingGroup("other", SettingTab.DEVICE, other))
        }

    private fun write(id: String, vararg payload: Int): List<ByteArray>? {
        val c = command(id) ?: return null
        val set = c.set ?: return null
        val get = c.get
        if (get != null) readBacks += read(c)
        return listOf(frame(c.vendor, set, *payload))
    }

    override fun set(command: Command, value: Int): List<ByteArray>? {
        if (command != Command.ANC_MODE) return null
        val mode = Command.ANC_MODE.labels[value] ?: return null
        // Smart Control turns the other one off whenever it turns one on.
        return when (mode) {
            "anc" -> concat(write(SennheiserCommands.TRANSPARENCY_STATUS, 0), write(SennheiserCommands.ANC_STATUS, 1))
            "transparency" -> concat(write(SennheiserCommands.ANC_STATUS, 0), write(SennheiserCommands.TRANSPARENCY_STATUS, 1))
            "off" -> concat(write(SennheiserCommands.ANC_STATUS, 0), write(SennheiserCommands.TRANSPARENCY_STATUS, 0))
            else -> null
        }?.also {
            values[SennheiserCommands.ANC_STATUS] = byteArrayOf(if (mode == "anc") 1 else 0)
            values[SennheiserCommands.TRANSPARENCY_STATUS] = byteArrayOf(if (mode == "transparency") 1 else 0)
            refresh()
        }
    }

    private fun concat(a: List<ByteArray>?, b: List<ByteArray>?): List<ByteArray>? = if (a == null || b == null) null else a + b

    override fun change(change: SettingChange): List<ByteArray>? {
        if (change is SettingChange.Toggle) {
            val flag = if (change.on) 1 else 0
            val id = TOGGLES[change.key]
            if (id != null) {
                if (values[id] == null) return null
                values[id] = byteArrayOf(flag.toByte())
                return write(id, flag)?.also { refresh() }
            }
            val mode = when (change.key) {
                KEY_ANTI_WIND -> ANTI_WIND
                KEY_ADAPTIVE -> ADAPTIVE
                else -> return null
            }
            val b = values[SennheiserCommands.ANC_MODE] ?: return null
            var i = 0
            while (i + 1 < b.size) {
                if ((b[i].toInt() and 0xFF) == mode) b[i + 1] = flag.toByte()
                i += 2
            }
            return write(SennheiserCommands.ANC_MODE, mode, flag)?.also { refresh() }
        }
        if (change is SettingChange.Choice && change.key == KEY_PROMPTS) {
            val v = PROMPTS[change.option] ?: return null
            if (values[SennheiserCommands.PROMPT_MODE] == null) return null
            values[SennheiserCommands.PROMPT_MODE] = byteArrayOf(v.toByte())
            return write(SennheiserCommands.PROMPT_MODE, v)
        }
        return null
    }

    override fun unanswered(sent: ByteArray): Boolean = false

    public companion object {
        // The rows' keys (strings in the app).
        public const val KEY_ANTI_WIND: String = "sh_anti_wind"
        public const val KEY_ADAPTIVE: String = "sh_adaptive_anc"
        public const val KEY_PAUSE_TRANSPARENT: String = "sh_pause_transparent"
        public const val KEY_AUTO_CALL: String = "sh_auto_call"
        public const val KEY_COMFORT_CALL: String = "sh_comfort_call"
        public const val KEY_ON_HEAD: String = "sh_on_head"
        public const val KEY_SMART_PAUSE: String = "sh_smart_pause"
        public const val KEY_LOW_LATENCY: String = "sh_low_latency"
        public const val KEY_TOUCH_LOCK: String = "sh_touch_lock"
        public const val KEY_ECO: String = "sh_eco_mode"
        public const val KEY_BATTERY_PROTECTION: String = "sh_battery_protection"
        public const val KEY_PROMPTS: String = "sh_prompts"
        public const val KEY_CODEC: String = "sh_codec"

        private const val REPLY_MS = 3_000L
        private const val GAP_MS = 50L

        /** ANC's mode numbers inside the `ANC` property (the app's note: 1 anti-wind, 2 comfort, 3 adaptive). */
        private const val ANTI_WIND = 1
        private const val ADAPTIVE = 3

        /** The features whose notifications Smart Control registers on opening. */
        private val REGISTERED = listOf("core", "device", "battery", "generic_audio", "versions", "transparent_hearing", "anc")

        private val TOGGLES = mapOf(
            KEY_PAUSE_TRANSPARENT to SennheiserCommands.TRANSPARENCY_PAUSE,
            KEY_AUTO_CALL to SennheiserCommands.AUTO_CALL,
            KEY_COMFORT_CALL to SennheiserCommands.COMFORT_CALL,
            KEY_ON_HEAD to SennheiserCommands.ON_HEAD,
            KEY_SMART_PAUSE to SennheiserCommands.SMART_PAUSE,
            KEY_LOW_LATENCY to SennheiserCommands.LOW_LATENCY,
            KEY_TOUCH_LOCK to SennheiserCommands.TOUCH_LOCK,
            KEY_ECO to SennheiserCommands.ECO_MODE,
            KEY_BATTERY_PROTECTION to SennheiserCommands.BATTERY_PROTECTION,
        )

        /** The app's `Setting_AudioPromptMode` values. */
        private val PROMPTS = linkedMapOf("tone_voice_off" to 0, "tone_on" to 1, "tone_voice_on" to 2)
    }
}
