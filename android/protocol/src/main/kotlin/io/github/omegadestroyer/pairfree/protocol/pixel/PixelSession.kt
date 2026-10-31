package io.github.omegadestroyer.pairfree.protocol.pixel

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value

/** What a message waits for: on Maestro the reply to its method, on the plain frames a code in group 135. */
public data class PixelReply(val maestro: Boolean, val id: Int)

/**
 * One message to the earbuds, as bytes, with the reply the connection waits for (none for a subscription or a
 * plain-frame write). [readField] is the setting a read asks for; [probe] marks a channel probe.
 */
public class PixelMessage internal constructor(
    public val bytes: ByteArray,
    public val reply: PixelReply?,
    internal val readField: Int? = null,
    internal val probe: Boolean = false,
    internal val writeField: Int? = null,
    private val label: String,
) {
    override fun toString(): String = label
}

/** What a chunk from the earbuds changed: whether the screens' view did, and the replies it carried. */
public class PixelFeed(public val changed: Boolean, public val replies: List<PixelReply>)

/**
 * One connection to Pixel Buds (docs/pixel/PROTOCOL.md), shown on PairFree's screens. [transport] is Maestro on the
 * Pixel Buds Pro, Pro 2 and 2a, plain frames on the Pixel Buds (2020) and A-Series; both carry the same messages. The
 * device type the earbuds report picks the model and its settings message. A setting shows once its read has
 * answered: Maestro answers a setting the earbuds lack with an error status, the plain frames not at all. The battery,
 * firmware, noise mode, in-ear detection and multipoint go to [state]; everything else to [groups], the EQ to [eq].
 *
 * A change shows at once; on Maestro [takeReadBacks] gives the reads that confirm it, which the connection sends a
 * little later, and on the plain frames the read goes straight after the write, as Google's app sends it.
 *
 * @param hint the model the Bluetooth name gave, used until the earbuds report their device type.
 */
public class PixelSession(public val transport: PixelTransport, private val hint: PixelModel? = null) {
    public var state: EarbudState = EarbudState()
        private set

    /** The device type from the earbuds' hardware info; their serial numbers there are never kept. */
    public var deviceType: Int? = null
        private set

    public var firmware: String? = null
        private set

    /** The model, once the earbuds reported their device type; before then, the one the name gave. */
    public val model: PixelModel? get() = deviceType?.let(PixelModels::of) ?: hint.takeIf { deviceType == null }

    /** The settings message the earbuds take: Allegro over Maestro; on the plain frames the model's, once known. */
    private val family: PixelFamily? get() = model?.family ?: if (transport == PixelTransport.MAESTRO) PixelFamily.ALLEGRO else null

    /** The RPC channel that answered; null until one has (Maestro). */
    public var channel: Int? = null
        private set

    /** The last payload of each read PairFree sends, by a short name, for the device report. Never the serial numbers. */
    public val replies: Map<String, ByteArray> get() = _replies
    private val _replies = linkedMapOf<String, ByteArray>()

    /** Settings the earbuds refused to read (Maestro's error status): they do not have them. */
    public val refused: Set<Int> get() = _refused
    private val _refused = linkedSetOf<Int>()

    private val values = mutableMapOf<Int, ProtoField>()
    private val outbox = mutableListOf<PixelMessage>()
    private val readBacks = mutableListOf<PixelMessage>()
    private var readsSent = false
    private var pendingRead: Int? = null
    private var pendingWrite: Int? = null
    private val hdlc = PixelHdlc.Decoder()
    private val tlv = PixelTlvParser()

    // ---- Opening ----

    /**
     * The first messages: on Maestro GetSoftwareInfo on each channel, the one that answers carries the rest
     * ([afterChannel]); on the plain frames the start message, then the reads once the model is known.
     */
    public fun start(): List<PixelMessage> = when (transport) {
        PixelTransport.MAESTRO -> PixelWire.CHANNELS.keys.map { ch ->
            // Sent one after another without waiting: only the right channel answers.
            rpc(PixelWire.METHOD_SOFTWARE_INFO, ByteArray(0), ch, PixelWire.PROBE_CALL_ID, reply = false, probe = true, label = "probe channel $ch")
        }
        PixelTransport.TLV -> listOf(tlvMessage(PixelWire.CODE_START, ByteArray(0), null, label = "start")) + reads()
    }

    private fun afterChannel(): List<PixelMessage> = listOf(
        rpc(PixelWire.METHOD_HARDWARE_INFO, ByteArray(0), reply = true, label = "hardware info"),
        rpc(PixelWire.METHOD_RUNTIME_INFO, ByteArray(0), reply = false, label = "subscribe runtime info"),
        rpc(PixelWire.METHOD_SETTINGS_CHANGES, ByteArray(0), reply = false, label = "subscribe settings changes"),
    ) + reads()

    /** A read of every setting the family has that PairFree shows, once the family is known; only once. */
    private fun reads(): List<PixelMessage> {
        val f = family ?: return emptyList()
        if (readsSent) return emptyList()
        readsSent = true
        return PixelModels.fields(f).filter { it.read && !it.neverSend }.map { read(it.field) }
    }

    /** Nothing to poll: Maestro streams the battery, and the plain frames send it as it changes. */
    public fun batteryRequests(): List<PixelMessage> = emptyList()

    public fun takeOutbox(): List<PixelMessage> = outbox.toList().also { outbox.clear() }

    /** The reads that confirm the last changes, for the connection to send a little later (Maestro). */
    public fun takeReadBacks(): List<PixelMessage> = readBacks.distinctBy { it.readField }.also { readBacks.clear() }

    /**
     * Called just before [message] goes out: false for what is not sent after all (a channel probe once a channel
     * answered, anything never sent). Notes which setting a read or write is for, to match its answer.
     */
    public fun willSend(message: PixelMessage): Boolean {
        if (message.probe && channel != null) return false
        if (!allowed(message)) return false
        message.readField?.let { pendingRead = it }
        message.writeField?.let { pendingWrite = it }
        return true
    }

    /**
     * False for what PairFree never sends: firmware update, crash reports, statistics, and writes of the setup,
     * update and diagnostics settings (data/pixel/commands.json).
     */
    public fun allowed(message: PixelMessage): Boolean {
        message.writeField?.let { f -> if (fieldOf(f)?.neverSend != false) return false }
        if (transport == PixelTransport.TLV) {
            val group = message.bytes.firstOrNull()?.toInt()?.and(0xFF) ?: return false
            if (group in PixelWire.NEVER_SEND_GROUPS || group != PixelWire.TLV_GROUP) return false
        }
        return true
    }

    /** True when [reply] answers [sent]. */
    public fun answers(sent: PixelMessage, reply: PixelReply): Boolean = sent.reply == reply

    // ---- Receiving ----

    /** Applies a chunk from the earbuds. */
    public fun feed(bytes: ByteArray): PixelFeed {
        val before = state to groups
        val got = mutableListOf<PixelReply>()
        when (transport) {
            PixelTransport.MAESTRO -> for (frame in hdlc.feed(bytes)) {
                if (frame.control != PixelWire.HDLC_CONTROL) continue
                val packet = PixelRpcPacket.parse(frame.data) ?: continue
                receive(packet)?.let { got += it }
            }
            PixelTransport.TLV -> for (frame in tlv.feed(bytes)) receive(frame)?.let { got += it }
        }
        refresh()
        return PixelFeed((state to groups) != before, got)
    }

    /** The decoded frames in a chunk, for the log, without applying them (a second decoder). */
    public fun describe(bytes: ByteArray): List<String> = when (transport) {
        PixelTransport.MAESTRO -> logHdlc.feed(bytes).mapNotNull { PixelRpcPacket.parse(it.data)?.toString() }
        PixelTransport.TLV -> logTlv.feed(bytes).map { it.toString() }
    }

    private val logHdlc = PixelHdlc.Decoder()
    private val logTlv = PixelTlvParser()

    private fun receive(packet: PixelRpcPacket): PixelReply? {
        if (packet.serviceId != PixelRpc.SERVICE) return null
        val method = PixelRpc.methodName(packet.methodId) ?: return null
        val ok = packet.status == 0 && packet.type != PixelWire.PACKET_SERVER_ERROR
        val reply = PixelReply(true, packet.methodId).takeIf { packet.type == PixelWire.PACKET_RESPONSE || packet.type == PixelWire.PACKET_SERVER_ERROR }
        val p = packet.payload
        when (method) {
            PixelWire.METHOD_SOFTWARE_INFO -> if (ok) {
                if (channel == null) {
                    channel = packet.channelId
                    outbox += afterChannel()
                }
                _replies["software info"] = p.copyOf()
                softwareInfo(p)
            }
            PixelWire.METHOD_HARDWARE_INFO -> if (ok) hardwareInfo(p)
            PixelWire.METHOD_RUNTIME_INFO -> if (ok) runtimeInfo(p)
            PixelWire.METHOD_READ_SETTING -> {
                val asked = pendingRead
                if (ok) settingValue(p) else if (asked != null) {
                    _refused += asked
                    _replies["setting $asked"] = byteArrayOf(packet.status.toByte())
                }
                if (packet.type != PixelWire.PACKET_SERVER_STREAM) pendingRead = null
            }
            PixelWire.METHOD_WRITE_SETTING -> {
                // A refused write: read the setting again, so the screen shows what the earbuds hold.
                if (!ok) pendingWrite?.let { outbox += read(it) }
                pendingWrite = null
            }
            PixelWire.METHOD_SETTINGS_CHANGES -> if (ok) settingValue(p)
        }
        return reply
    }

    private fun receive(frame: PixelTlvFrame): PixelReply? {
        if (frame.group != PixelWire.TLV_GROUP) return null
        val p = frame.payload
        when (frame.code) {
            PixelWire.CODE_SOFTWARE_INFO -> {
                _replies["software info"] = p.copyOf()
                softwareInfo(p)
            }
            PixelWire.CODE_HARDWARE_INFO -> {
                hardwareInfo(p)
                outbox += reads()
            }
            PixelWire.CODE_RUNTIME_INFO -> runtimeInfo(p)
            PixelWire.CODE_SETTING_VALUE -> settingValue(p)
        }
        return PixelReply(false, frame.code)
    }

    /** Software info: field 4 (or 3) holds the case's, right's and left's firmware (1, 2, 3), each {2: version}. */
    private fun softwareInfo(p: ByteArray) {
        val f = Proto.parse(p) ?: return
        val parts = (f.field(SOFTWARE_FIRMWARE) ?: f.field(SOFTWARE_FIRMWARE_OLD))?.message() ?: return
        fun version(part: Int) = parts.field(part)?.message()?.field(FIRMWARE_VERSION)?.text()?.trim()?.takeIf { it.isNotEmpty() }
        firmware = version(PART_LEFT_FIRMWARE) ?: version(PART_RIGHT_FIRMWARE) ?: version(PART_CASE) ?: firmware
    }

    /** Hardware info: field 1 is the device type. The serial numbers in it are never kept. */
    private fun hardwareInfo(p: ByteArray) {
        val f = Proto.parse(p) ?: return
        val type = f.field(HARDWARE_DEVICE_TYPE)?.int ?: return
        deviceType = type
        _replies["device type"] = byteArrayOf(type.toByte())
    }

    /** Runtime info: the battery in field 6 (or 4), case, left and right {1 level, 2 state}; placement in 7 (or 5). */
    private fun runtimeInfo(p: ByteArray) {
        val f = Proto.parse(p) ?: return
        val battery = (f.field(RUNTIME_BATTERY) ?: f.field(RUNTIME_BATTERY_OLD))?.message() ?: return
        _replies["battery"] = (f.field(RUNTIME_BATTERY) ?: f.field(RUNTIME_BATTERY_OLD))?.bytes ?: ByteArray(0)
        val v = state.values.toMutableMap()
        fun level(part: Int) = battery.field(part)?.message()?.field(BATTERY_LEVEL)?.int?.takeIf { it in 0..PERCENT }
        battery.field(PART_LEFT)?.let { v[Command.BATTERY_LEFT] = Value.Number(level(PART_LEFT)) }
        battery.field(PART_RIGHT)?.let { v[Command.BATTERY_RIGHT] = Value.Number(level(PART_RIGHT)) }
        battery.field(PART_CASE)?.let { v[Command.BATTERY_CASE] = Value.Number(level(PART_CASE)) }
        state = state.copy(values = v)
    }

    /** A setting's value: the family's settings message in its slot, with one field set. */
    private fun settingValue(p: ByteArray) {
        val f = family ?: return
        val outer = Proto.parse(p) ?: return
        val inner = outer.field(slot(f))?.message() ?: return
        for (field in inner) {
            values[field.number] = field
            _refused -= field.number
            _replies["setting ${field.number}"] = encodeValue(field)
        }
    }

    private fun encodeValue(f: ProtoField): ByteArray = f.bytes ?: ProtoWriter().varint(f.number, f.long).bytes()

    // ---- What the screens show ----

    private fun fieldOf(number: Int): PixelField? = family?.let { PixelModels.fields(it) }?.firstOrNull { it.field == number }

    private fun fieldNamed(name: String): PixelField? = family?.let { PixelModels.fields(it) }?.firstOrNull { it.name == name }

    private fun value(name: String): ProtoField? = fieldNamed(name)?.let { values[it.field] }

    private fun bool(name: String): Boolean? = value(name)?.bool

    private fun refresh() {
        val v = state.values.toMutableMap()
        firmware?.let { v[Command.FIRMWARE_VERSION] = Value.Text("${deviceType ?: BRAND}_$it") }
        bool("in_ear_detection")?.let { v[Command.IN_EAR_DETECTION] = Value.Switch(it) }
        bool("multipoint")?.let { v[Command.MULTIPOINT] = Value.Switch(it) }
        value("anc_state")?.int?.let { wire -> ancShared(wire)?.let { v[Command.ANC_MODE] = Value.Number(it) } }
        state = state.copy(values = v)
    }

    private val hasAdaptive: Boolean get() = model?.adaptive == true || value("anc_state")?.int == PixelValues.ANC_STATES["adaptive"]

    /** Every setting the earbuds answered for, grouped by heading and tab. */
    public val groups: List<SettingGroup>
        get() = when (family) {
            PixelFamily.ALLEGRO -> allegroGroups()
            PixelFamily.PRESTO, PixelFamily.STRETTO -> olderGroups()
            else -> emptyList()
        }

    private fun allegroGroups(): List<SettingGroup> = listOfNotNull(
        group("noise", SettingTab.HOME, listOfNotNull(value("anc_state")?.takeIf { hasAdaptive }?.let { Setting.Toggle("adaptive_noise", it.int == PixelValues.ANC_STATES["adaptive"]) })),
        group(
            "sound", SettingTab.SOUND,
            listOfNotNull(
                toggle("volume_eq"),
                toggle("conversation_detection"),
                toggle("mono"),
                value("balance")?.let { Setting.Level("balance", it.sint32.coerceIn(PixelValues.BALANCE_MIN, PixelValues.BALANCE_MAX) - PixelValues.BALANCE_MIN, 0, PixelValues.BALANCE_MAX - PixelValues.BALANCE_MIN) },
            ),
        ),
        group("touch", SettingTab.CONTROLS, listOfNotNull(toggle("gestures")) + holdRows() + cycleRows() + listOfNotNull(toggle("call_mute"), headGestures())),
        group(
            "other", SettingTab.DEVICE,
            listOfNotNull(toggle("case_sounds"), toggle("bud_return_sound"), toggle("volume_notifications"), translateAnc()),
        ),
    )

    private fun olderGroups(): List<SettingGroup> = listOfNotNull(
        group(
            "sound", SettingTab.SOUND,
            listOfNotNull(
                toggle("adaptive_sound"),
                value("eq")?.let { Setting.Toggle("bass_boost", it.int == PixelValues.EQ_MODE["bass_boost"]) },
                value("bass_level")?.let { Setting.Level("bass_level", (it.int - PixelValues.BASS_OFFSET).coerceIn(PixelValues.BASS_MIN, PixelValues.BASS_MAX), PixelValues.BASS_MIN, PixelValues.BASS_MAX) },
                toggle("loudness_compensation"),
            ),
        ),
        group("attention", SettingTab.SOUND, attentionRows()),
        group("touch", SettingTab.CONTROLS, listOfNotNull(toggle("gestures"))),
        // Sharing detection needs in-ear detection on, as the app says.
        group("other", SettingTab.DEVICE, listOfNotNull(toggle("shared_mode")?.let { it.copy(enabled = bool("in_ear_detection") != false) })),
    )

    /** A switch row for the field named [name], under its key, once read. */
    private fun toggle(name: String): Setting.Toggle? {
        val f = fieldNamed(name) ?: return null
        val v = values[f.field] ?: return null
        return Setting.Toggle(f.key ?: return null, v.bool)
    }

    /** Press and hold on each earbud: the app's three actions; another the earbuds hold shows as it is. */
    private fun holdRows(): List<Setting> {
        val sides = value("hold_actions")?.message() ?: return emptyList()
        return PixelValues.HOLD_SIDES.mapNotNull { (side, number) ->
            val action = sides.field(number)?.message()?.field(HOLD_ACTION)?.message() ?: return@mapNotNull null
            // An action set in Google's app that is neither in the table nor an id (Translate, Duo, a playlist) shows as Google's.
            val name = action.field(ACTION_ID)?.int?.let { id -> PixelValues.TOUCH_ACTIONS.entries.firstOrNull { it.value == id }?.key } ?: OTHER_ACTION
            Setting.Choice("hold_$side", name, PixelValues.HOLD_OPTIONS)
        }
    }

    /** The modes press and hold steps through; adaptive only where the model has it. */
    private fun cycleRows(): List<Setting> {
        val cycle = value("noise_cycle")?.message() ?: return emptyList()
        return PixelValues.NOISE_CYCLE.filter { (mode, _) -> mode != "adaptive" || hasAdaptive }
            .map { (mode, number) -> Setting.Toggle("cycle_$mode", cycle.field(number)?.bool == true) }
    }

    private fun headGestures(): Setting.Toggle? = value("head_gestures")?.let { Setting.Toggle("head_gestures", it.int == PixelValues.HEAD_GESTURES["on"]) }

    private fun translateAnc(): Setting.Toggle? = value("live_translate_anc")?.message()?.let { Setting.Toggle("translate_anc", it.field(TRANSLATE_ANC_ON)?.bool == true) }

    /** Attention alerts (the 2020 model): a switch, then each sound, which works while the switch is on. */
    private fun attentionRows(): List<Setting> {
        val alerts = value("attention_alerts")?.message() ?: return emptyList()
        val on = alerts.field(PixelValues.ATTENTION_ALERTS.getValue("on"))?.bool == true
        return listOf(Setting.Toggle("attention_alerts", on)) + PixelValues.ATTENTION_ALERTS.filterKeys { it != "on" }.map { (sound, number) ->
            Setting.Toggle(sound, alerts.field(number)?.bool == true, enabled = on)
        }
    }

    private fun group(key: String, tab: SettingTab, settings: List<Setting>) = settings.takeIf { it.isNotEmpty() }?.let { SettingGroup(key, tab, it) }

    // ---- The EQ (Allegro) ----

    /** The EQ in use, the five bands in dB (low bass to upper treble); null before it was read or on models without it. */
    public val eq: List<Float>? get() = value("user_eq")?.message()?.let(::bands)

    /** The curve the user last saved on the earbuds, Google's "Last saved" preset; null when they hold none. */
    public val savedEq: List<Float>? get() = value("saved_eq")?.message()?.let(::bands)

    /** True when the earbuds take the five-band EQ: an Allegro model whose EQ read answered. */
    public val hasEq: Boolean get() = family == PixelFamily.ALLEGRO && value("user_eq") != null

    private fun bands(f: List<ProtoField>): List<Float> = PixelValues.EQ_BANDS.indices.map { f.field(it + 1)?.float ?: 0f }

    /** The messages that put [gains] (dB, low bass to upper treble, -6 to 6) on the earbuds as the EQ in use. */
    public fun eqWrite(gains: List<Float>): List<PixelMessage>? {
        if (gains.size != PixelValues.EQ_BANDS.size) return null
        val r = PixelValues.EQ_RANGE_DB.toFloat()
        val curve = ProtoWriter()
        gains.forEachIndexed { i, g -> curve.float(i + 1, g.coerceIn(-r, r)) }
        return write("user_eq") { field, w -> w.message(field, curve) }?.also { lastEqWrite = it }
    }

    private var lastEqWrite: List<PixelMessage> = emptyList()

    /** The messages of the last [eqWrite] whose bytes are [frames] (the Sound tab hands the bytes back). */
    public fun eqMessages(frames: List<ByteArray>): List<PixelMessage> = lastEqWrite.filter { m -> frames.any { it === m.bytes } }

    // ---- Changes ----

    /** The messages for a shared setting from the screens, or null when the earbuds do not have it. */
    public fun set(command: Command, value: Int): List<PixelMessage>? {
        if (command !in state.values) return null
        val out = when (command) {
            Command.ANC_MODE -> {
                val name = Command.ANC_MODE.labels[value] ?: return null
                val wire = PixelValues.ANC_STATES[name] ?: return null
                if (name == "adaptive" && !hasAdaptive) return null
                write("anc_state") { f, w -> w.int32(f, wire) }
            }
            Command.IN_EAR_DETECTION -> write("in_ear_detection") { f, w -> w.bool(f, value == 1) }
            Command.MULTIPOINT -> write("multipoint") { f, w -> w.bool(f, value == 1) }
            else -> null
        }
        refresh()
        return out
    }

    /** The messages for a change to one of the [groups], or null when the earbuds do not have the setting. */
    public fun change(change: SettingChange): List<PixelMessage>? {
        val setting = groups.flatMap { it.settings }.firstOrNull { it.key == change.key } ?: return null
        val out = when (change) {
            is SettingChange.Toggle -> {
                if (setting !is Setting.Toggle || !setting.enabled) return null
                toggleChange(change.key, change.on)
            }
            is SettingChange.Level -> when (change.key) {
                "balance" -> write("balance") { f, w -> w.sint32(f, (change.value + PixelValues.BALANCE_MIN).coerceIn(PixelValues.BALANCE_MIN, PixelValues.BALANCE_MAX)) }
                "bass_level" -> write("bass_level") { f, w -> w.int32(f, change.value.coerceIn(PixelValues.BASS_MIN, PixelValues.BASS_MAX) + PixelValues.BASS_OFFSET) }
                else -> null
            }
            is SettingChange.Choice -> {
                if (setting !is Setting.Choice || change.option !in setting.options) return null
                val side = PixelValues.HOLD_SIDES[change.key.removePrefix("hold_")] ?: return null
                val action = PixelValues.TOUCH_ACTIONS[change.option] ?: return null
                // One side at a time, as the app writes it: {side: {4: {1: action}}}; the other side stays as shown.
                val one = ProtoWriter().message(HOLD_ACTION, ProtoWriter().int32(ACTION_ID, action)).bytes()
                val sides = ProtoWriter().bytes(side, one)
                write("hold_actions", shown = merged("hold_actions", messages = mapOf(side to one))) { f, w -> w.message(f, sides) }
            }
            is SettingChange.Press -> null
        }
        refresh()
        return out
    }

    private fun toggleChange(key: String, on: Boolean): List<PixelMessage>? = when (key) {
        "adaptive_noise" -> {
            val wire = PixelValues.ANC_STATES.getValue(if (on) "adaptive" else "anc")
            write("anc_state") { f, w -> w.int32(f, wire) }
        }
        "bass_boost" -> write("eq") { f, w -> w.int32(f, PixelValues.EQ_MODE.getValue(if (on) "bass_boost" else "default")) }
        "head_gestures" -> write("head_gestures") { f, w -> w.int32(f, PixelValues.HEAD_GESTURES.getValue(if (on) "on" else "off")) }
        "translate_anc" -> write("live_translate_anc") { f, w -> w.message(f, ProtoWriter().bool(TRANSLATE_ANC_ON, on)) }
        "attention_alerts" -> write("attention_alerts") { f, w -> w.message(f, ProtoWriter().bool(PixelValues.ATTENTION_ALERTS.getValue("on"), on)) }
        in PixelValues.ATTENTION_ALERTS.keys -> {
            // One sound, with the alerts on, as the app writes it.
            val onField = PixelValues.ATTENTION_ALERTS.getValue("on")
            val sound = PixelValues.ATTENTION_ALERTS.getValue(key)
            val alerts = ProtoWriter().bool(onField, true).bool(sound, on)
            val shown = merged("attention_alerts", varints = mapOf(onField to 1L, sound to if (on) 1L else 0L))
            write("attention_alerts", shown = shown) { f, w -> w.message(f, alerts) }
        }
        else -> if (key.startsWith("cycle_")) cycleChange(key.removePrefix("cycle_"), on) else boolField(key, on)
    }

    /** Press and hold's modes, all four written, as the app does; at least two stay on. */
    private fun cycleChange(mode: String, on: Boolean): List<PixelMessage>? {
        val current = value("noise_cycle")?.message() ?: return null
        val next = PixelValues.NOISE_CYCLE.mapValues { (m, number) -> if (m == mode) on else current.field(number)?.bool == true }
        if (next.values.count { it } < MIN_CYCLE) return null
        val w = ProtoWriter()
        PixelValues.NOISE_CYCLE.forEach { (m, number) -> w.bool(number, next.getValue(m)) }
        return write("noise_cycle") { f, out -> out.message(f, w) }
    }

    /** A plain switch, by its row key. */
    private fun boolField(key: String, on: Boolean): List<PixelMessage>? {
        val f = family?.let { PixelModels.fields(it) }?.firstOrNull { it.key == key && it.type == PixelFieldType.BOOL } ?: return null
        return write(f.name) { field, w -> w.bool(field, on) }
    }

    /**
     * A write of the field named [name], which [put] fills, shown at once: on Maestro a WriteSetting, with a read back
     * for later; on the plain frames code 9, then straight away the read, as Google's app sends them.
     */
    private fun write(name: String, shown: ProtoField? = null, put: (Int, ProtoWriter) -> Unit): List<PixelMessage>? {
        val fam = family ?: return null
        val f = fieldNamed(name)?.takeIf { !it.neverSend } ?: return null
        val inner = ProtoWriter().also { put(f.field, it) }
        (shown ?: Proto.parse(inner.bytes())?.field(f.field))?.let { values[f.field] = it }
        val payload = ProtoWriter().message(slot(fam), inner).bytes()
        return when (transport) {
            PixelTransport.MAESTRO -> {
                readBacks += read(f.field)
                listOf(rpc(PixelWire.METHOD_WRITE_SETTING, payload, reply = true, writeField = f.field, label = "write ${f.name}"))
            }
            PixelTransport.TLV -> listOf(tlvMessage(PixelWire.CODE_WRITE_SETTING, payload, null, writeField = f.field, label = "write ${f.name}"), read(f.field))
        }
    }

    /**
     * The message field [name] as shown after a write of part of it: its fields as held, with [varints] and [messages]
     * (field number to value) put in their place.
     */
    private fun merged(name: String, varints: Map<Int, Long> = emptyMap(), messages: Map<Int, ByteArray> = emptyMap()): ProtoField? {
        val f = fieldNamed(name) ?: return null
        val w = ProtoWriter()
        for (h in value(name)?.message().orEmpty()) {
            if (h.number in varints || h.number in messages) continue
            if (h.bytes != null) w.bytes(h.number, h.bytes) else w.varint(h.number, h.long)
        }
        for ((number, v) in varints) w.varint(number, v)
        for ((number, b) in messages) w.bytes(number, b)
        val all = w.bytes()
        return ProtoField(f.field, ProtoWriter.LEN, all.size.toLong(), all)
    }

    /** A read of setting [field]: the field's number in the family's slot. */
    private fun read(field: Int): PixelMessage {
        val fam = family ?: PixelFamily.ALLEGRO
        val payload = ProtoWriter().int32(slot(fam), field).bytes()
        return when (transport) {
            PixelTransport.MAESTRO -> rpc(PixelWire.METHOD_READ_SETTING, payload, reply = true, readField = field, label = "read setting $field")
            PixelTransport.TLV -> tlvMessage(PixelWire.CODE_READ_SETTING, payload, PixelReply(false, PixelWire.CODE_SETTING_VALUE), readField = field, label = "read setting $field")
        }
    }

    private fun rpc(
        method: String,
        payload: ByteArray,
        channelId: Int? = null,
        callId: Int = PixelWire.CALL_ID,
        reply: Boolean,
        probe: Boolean = false,
        readField: Int? = null,
        writeField: Int? = null,
        label: String,
    ): PixelMessage {
        val ch = channelId ?: channel ?: PixelWire.CHANNELS.keys.first()
        val methodId = PixelRpc.hash(method)
        val packet = PixelRpcPacket(PixelWire.PACKET_REQUEST, ch, PixelRpc.SERVICE, methodId, payload, callId = callId)
        val bytes = PixelHdlc.encode(PixelWire.CHANNELS.getValue(ch), packet.encode())
        return PixelMessage(bytes, if (reply) PixelReply(true, methodId) else null, readField, probe, writeField, label)
    }

    private fun tlvMessage(code: Int, payload: ByteArray, reply: PixelReply?, readField: Int? = null, writeField: Int? = null, label: String): PixelMessage =
        PixelMessage(PixelTlvFrame(PixelWire.TLV_GROUP, code, payload).bytes(), reply, readField, writeField = writeField, label = label)

    private fun slot(f: PixelFamily): Int = when (f) {
        PixelFamily.COMMON -> PixelWire.SLOT_COMMON
        PixelFamily.PRESTO -> PixelWire.SLOT_PRESTO
        PixelFamily.STRETTO -> PixelWire.SLOT_STRETTO
        PixelFamily.ALLEGRO -> PixelWire.SLOT_ALLEGRO
    }

    /** The shared noise mode's number for the earbuds' state [wire] (off 1, cancelling 2, transparency 3, adaptive 4). */
    private fun ancShared(wire: Int): Int? {
        val name = PixelValues.ANC_STATES.entries.firstOrNull { it.value == wire }?.key ?: return null
        return Command.ANC_MODE.labels.entries.firstOrNull { it.value == name }?.key
    }

    public companion object {
        private const val BRAND = "Pixel"
        private const val PERCENT = 100
        private const val MIN_CYCLE = 2

        // Message fields (docs/pixel/PROTOCOL.md section 4).
        private const val SOFTWARE_FIRMWARE = 4
        private const val SOFTWARE_FIRMWARE_OLD = 3
        private const val FIRMWARE_VERSION = 2
        private const val HARDWARE_DEVICE_TYPE = 1
        private const val RUNTIME_BATTERY = 6
        private const val RUNTIME_BATTERY_OLD = 4
        private const val BATTERY_LEVEL = 1

        // The parts in battery and firmware messages: the case, the left and the right earbud (pbpctrl's order).
        private const val PART_CASE = 1
        private const val PART_LEFT = 2
        private const val PART_RIGHT = 3

        // Firmware lists them case, right, left (pbpctrl's SoftwareInfo).
        private const val PART_RIGHT_FIRMWARE = 2
        private const val PART_LEFT_FIRMWARE = 3

        // Press and hold: each side {4: {1: action}}; Live Translate's noise control {1: on}.
        private const val HOLD_ACTION = 4
        private const val ACTION_ID = 1
        private const val TRANSLATE_ANC_ON = 1

        /** A press-and-hold action PairFree does not name, set in Google's app. */
        public const val OTHER_ACTION: String = "set_in_google_app"
    }
}
