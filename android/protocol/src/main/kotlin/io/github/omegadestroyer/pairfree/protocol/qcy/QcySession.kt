package io.github.omegadestroyer.pairfree.protocol.qcy

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.GattEnvelope
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value

/**
 * One connection to QCY earbuds on QCY's own protocol (docs/qcy/PROTOCOL.md), shown on PairFree's screens.
 * The model comes from the vendor id the earbuds advertise, which the connection found before opening the
 * channel; its entry in data/qcy/devices.json (QCY's control panel for it) says which noise modes,
 * switches and touch controls it has. Each setting shows once the earbuds report it.
 *
 * Messages go out and come in as [GattEnvelope]s: items in QCY packets on the command characteristic, the
 * earbuds' reports on the settings characteristic, and the version, battery and key table on their own.
 * A change shows at once; [takeReadBacks] then gives the requests that confirm it.
 *
 * @param model the model the advert named, or null when the earbuds were not found by their advert or
 *   advertise a vendor id PairFree does not know: then every common setting is asked for.
 */
public class QcySession(public val model: QcyModel?, advert: QcyAdvert? = null) {
    public var state: EarbudState = EarbudState()
        private set

    public var firmware: String? = null
        private set

    /** The last value of each item and characteristic PairFree reads, for the device report. */
    public val replies: Map<String, ByteArray> get() = _replies
    private val _replies = linkedMapOf<String, ByteArray>()

    private val form: Form get() = model?.form ?: Form.EARBUDS
    private val readBacks = mutableListOf<ByteArray>()
    private val values = mutableMapOf<Int, ByteArray>()
    private var noise: Int? = null
    private var lastCancelling: Int? = null
    private var lastTransparency: Int? = null
    private var keys: Map<Int, Int>? = null

    init {
        advert?.let { battery(byteArrayOf(raw(it.left), raw(it.right), raw(it.case))) }
    }

    private fun raw(level: Int?): Byte = (level ?: 0).toByte()

    /** What is read on opening the channel: the version, battery and settings, then each item the model has. */
    public fun start(): List<ByteArray> = buildList {
        add(GattEnvelope.read(QcyWire.VERSION_SHORT))
        add(GattEnvelope.read(QcyWire.BATTERY_SHORT))
        add(GattEnvelope.read(QcyWire.SETTINGS_SHORT))
        for (item in items()) add(request(item))
        if (model == null || model.touch != null) add(GattEnvelope.read(QcyWire.KEYS_SHORT))
    }

    /** The items asked for: the model's noise item and switches, or for an unknown model the common ones. */
    private fun items(): List<Int> {
        val m = model ?: return UNKNOWN_MODEL_ITEMS
        return buildList {
            if (m.anc.isNotEmpty()) add(if (m.anc.all { it.legacy }) QcyItem.NOISE_MODE else QcyItem.ANC_SETTING)
            addAll(m.settings.map { it.item })
            if (m.balance) add(QcyItem.BALANCE)
        }.distinct()
    }

    private fun request(item: Int) = GattEnvelope.write(QcyWire.COMMAND_SHORT, QcyPacket.request(item))

    private fun command(item: Int, vararg value: Int) = GattEnvelope.write(QcyWire.COMMAND_SHORT, QcyPacket.of(item, *value))

    /** The battery read, to send again now and then. */
    public fun batteryRequests(): List<ByteArray> = listOf(GattEnvelope.read(QcyWire.BATTERY_SHORT))

    /** The requests that confirm the last changes, for the connection to send a little later. */
    public fun takeReadBacks(): List<ByteArray> = readBacks.distinctBy { it.toList() }.also { readBacks.clear() }

    /** False for what PairFree never sends: resets, pairing, test and firmware traffic, renaming, clock and alarm. */
    public fun allowed(envelope: ByteArray): Boolean {
        val out = GattEnvelope.out(envelope) ?: return false
        if (out.operation == GattEnvelope.READ) return out.short in READABLE
        return when (out.short) {
            QcyWire.COMMAND_SHORT -> QcyPacket.parse(out.value).none { it.item in QcyItem.NEVER_SEND }
            QcyWire.KEYS_SHORT -> true
            else -> false
        }
    }

    /** Applies one message from the earbuds. Returns true when [state] or [groups] changed. */
    public fun receive(envelope: ByteArray): Boolean {
        val message = GattEnvelope.parse(envelope) ?: return false
        val before = state to groups
        val v = message.value
        when (message.short) {
            QcyWire.SETTINGS_SHORT, QcyWire.COMMAND_SHORT -> QcyPacket.parse(v).forEach(::entry)
            QcyWire.BATTERY_SHORT -> {
                _replies["battery"] = v.copyOf()
                battery(v)
            }
            QcyWire.VERSION_SHORT -> {
                _replies["version"] = v.copyOf()
                version(v)
            }
            QcyWire.KEYS_SHORT -> {
                _replies["keys"] = v.copyOf()
                keys = (0 until v.size / 2).associate { (v[it * 2].toInt() and 0xFF) to (v[it * 2 + 1].toInt() and 0xFF) }
            }
        }
        refresh()
        return (state to groups) != before
    }

    private fun entry(e: QcyEntry) {
        if (e.item == QcyItem.PAIR_NAME) return
        _replies[e.item.toString(16).uppercase().padStart(2, '0')] = e.value.copyOf()
        if (e.value.isEmpty()) return
        when (e.item) {
            QcyItem.ANC_SETTING -> if (e.value.size >= 3) noise((e.u8(0) shl 16) or (e.u8(1) shl 8) or e.u8(2))
            QcyItem.NOISE_MODE -> noise(e.u8(0))
            QcyItem.BATTERY -> battery(e.value)
            QcyItem.VERSION -> version(e.value)
            QcyItem.KEY_FUNCTION -> keys = (0 until e.value.size / 2).associate { e.u8(it * 2) to e.u8(it * 2 + 1) }
            else -> values[e.item] = e.value.copyOf()
        }
    }

    private fun noise(v: Int) {
        noise = v
        when (modeOf(v)) {
            CANCELLING -> lastCancelling = v
            TRANSPARENCY -> lastTransparency = v
        }
    }

    /** The noise mode a packed value is: by the model's ranges, else by the mode byte (or the older one-byte ranges). */
    private fun modeOf(v: Int): String? {
        model?.anc?.firstOrNull { it.covers(v) }?.let { return it.mode }
        return if (v > 0xFF) {
            when (v shr 16) {
                1 -> CANCELLING
                2 -> OFF
                3, 4 -> TRANSPARENCY
                else -> null
            }
        } else {
            when (v) {
                LEGACY_OFF -> OFF
                in LEGACY_CANCELLING -> CANCELLING
                in LEGACY_TRANSPARENCY -> TRANSPARENCY
                else -> null
            }
        }
    }

    /** The battery: left, right and case, each with bit 7 for charging; 0 or over 100 is unknown. */
    private fun battery(v: ByteArray) {
        if (v.size < 3) return
        fun level(i: Int) = (v[i].toInt() and 0x7F).takeIf { it in 1..100 }
        val values = if (form == Form.EARBUDS) {
            mapOf(Command.BATTERY_LEFT to Value.Number(level(0)), Command.BATTERY_RIGHT to Value.Number(level(1)), Command.BATTERY_CASE to Value.Number(level(2)))
        } else {
            mapOf(Command.BATTERY_LEFT to Value.Number(level(0) ?: level(1)))
        }
        state = state.copy(values = state.values + values)
    }

    /** The version: ASCII on some models, else three bytes (or six, left then right). */
    private fun version(v: ByteArray) {
        if (v.isEmpty()) return
        val text = if (v.all { it in PRINTABLE }) {
            v.decodeToString().trim()
        } else if (v.size >= 3) {
            "${v[0].toInt() and 0xFF}.${v[1].toInt() and 0xFF}.${v[2].toInt() and 0xFF}"
        } else {
            null
        }
        firmware = text?.ifEmpty { null } ?: firmware
    }

    private fun on(item: Int): Boolean? = values[item]?.firstOrNull()?.let { it.toInt() == 1 }

    /** Puts what the earbuds reported where the shared screens read it. */
    private fun refresh() {
        val out = state.values.toMutableMap()
        firmware?.let { out[Command.FIRMWARE_VERSION] = Value.Text("${model?.code ?: BRAND}_$it") }
        if (sharedNoise) noise?.let { v -> modeOf(v)?.let { out[Command.ANC_MODE] = Value.Number(ancNumber(if (it == CANCELLING) "anc" else it)) } }
        on(QcyItem.GAME_MODE)?.let { out[Command.GAME_MODE] = Value.Switch(it) }
        on(QcyItem.IN_EAR)?.let { out[Command.IN_EAR_DETECTION] = Value.Switch(it) }
        on(QcyItem.WEARING)?.let { out[Command.IN_EAR_DETECTION] = Value.Switch(it) }
        on(QcyItem.LDAC)?.let { out[Command.LDAC] = Value.Switch(it) }
        on(QcyItem.MULTIPOINT)?.let { out[Command.MULTIPOINT] = Value.Switch(it) }
        state = state.copy(values = out)
    }

    /** Off, cancelling and transparency on the shared control, where the model has all three (or is not known). */
    private val sharedNoise: Boolean
        get() = model == null || listOf(OFF, CANCELLING, TRANSPARENCY).all { model.mode(it) != null }

    /** Every setting the earbuds have, grouped by heading and tab. */
    public val groups: List<SettingGroup>
        get() = listOfNotNull(
            group("noise", SettingTab.HOME, noiseRows()),
            group(
                "sound", SettingTab.SOUND,
                listOfNotNull(
                    values[QcyItem.BALANCE]?.takeIf { model?.balance != false }?.let { Setting.Level("balance", (it[0].toInt() and 0xFF).coerceIn(0, BALANCE_MAX), 0, BALANCE_MAX) },
                    toggle(QcyItem.SPATIAL_AUDIO, "spatial_audio"),
                    toggle(QcyItem.ADAPTIVE_EQ, "dynamic_eq"),
                    toggle(QcyItem.ADAPTIVE_VOLUME, "adaptive_volume"),
                ),
            ),
            group(
                "other", SettingTab.DEVICE,
                listOfNotNull(
                    toggle(QcyItem.SLEEP_MODE, "sleep_mode"),
                    toggle(QcyItem.WIND_DETECTION, "wind_detection"),
                    toggle(QcyItem.LED_MODE, "led"),
                ),
            ),
        ) + touchGroups()

    private fun toggle(item: Int, key: String): Setting.Toggle? = on(item)?.let { Setting.Toggle(key, it) }

    private fun noiseRows(): List<Setting> = buildList {
        val v = noise ?: return@buildList
        val mode = modeOf(v)
        val m = model
        if (!sharedNoise && m != null && m.mode(OFF) != null) {
            // A model with two of the three modes gets a switch: noise cancelling, or else transparency.
            if (m.mode(CANCELLING) != null) add(Setting.Toggle("anc", mode == CANCELLING))
            else if (m.mode(TRANSPARENCY) != null) add(Setting.Toggle("ambient_sound", mode == TRANSPARENCY))
        }
        when (mode) {
            CANCELLING -> {
                val scenes = m?.mode(CANCELLING)?.scenes.orEmpty()
                val scene = scenes.firstOrNull { v in it.from..it.to }
                if (scenes.size > 1) add(Setting.Choice("anc_scene", scene?.scene, scenes.map { it.scene }))
                if (scene != null && scene.to > scene.from) add(Setting.Level("anc_scene_level", v - scene.from + 1, 1, scene.to - scene.from + 1))
            }
            TRANSPARENCY -> m?.mode(TRANSPARENCY)?.takeIf { it.to > it.from }?.let { t ->
                add(Setting.Level("transparency_strength", (v - t.from + 1).coerceIn(1, t.to - t.from + 1), 1, t.to - t.from + 1))
            }
        }
    }

    /** The touch controls: one group per earbud, each gesture the model offers with the actions PairFree can set. */
    private fun touchGroups(): List<SettingGroup> {
        val table = keys ?: return emptyList()
        val touch = model?.touch ?: return emptyList()
        val options = touch.actions.filter { a -> QcyKeys.ACTIONS.containsValue(a) }
        if (options.size < 2 || form != Form.EARBUDS) return emptyList()
        return listOf("left", "right").mapNotNull { side ->
            val rows = touch.gestures.mapNotNull { g ->
                val key = QcyKeys.KEYS.entries.firstOrNull { it.value == "${g}_$side" }?.key ?: return@mapNotNull null
                val action = table[key] ?: return@mapNotNull null
                Setting.Choice("touch_${g}_$side", QcyKeys.ACTIONS[action] ?: "other", options)
            }
            rows.takeIf { it.isNotEmpty() }?.let { SettingGroup("touch_$side", SettingTab.CONTROLS, it) }
        }
    }

    private fun group(key: String, tab: SettingTab, settings: List<Setting>) = settings.takeIf { it.isNotEmpty() }?.let { SettingGroup(key, tab, it) }

    /** The messages for a shared setting from the screens, or null when the earbuds do not have it. */
    public fun set(command: Command, value: Int): List<ByteArray>? {
        if (command !in state.values) return null
        val out: ByteArray = when (command) {
            Command.ANC_MODE -> {
                val target = when (value) {
                    ancNumber("off") -> OFF
                    ancNumber("anc") -> CANCELLING
                    ancNumber("transparency") -> TRANSPARENCY
                    else -> return null
                }
                noiseTo(target) ?: return null
            }
            Command.GAME_MODE -> switch(QcyItem.GAME_MODE, value == 1)
            Command.LDAC -> switch(QcyItem.LDAC, value == 1)
            Command.MULTIPOINT -> switch(QcyItem.MULTIPOINT, value == 1)
            Command.IN_EAR_DETECTION -> inEar(value == 1) ?: return null
            else -> return null
        }
        refresh()
        return listOf(out)
    }

    /** Turns a switch item on (01) or off (02), as QCY's toggles take them. */
    private fun switch(item: Int, on: Boolean): ByteArray {
        values[item] = byteArrayOf(if (on) ON else OFF_VALUE)
        readBacks += request(item)
        return command(item, if (on) ON.toInt() else OFF_VALUE.toInt())
    }

    /** In-ear detection: item 06, or the wearing item 2C with its other bytes as the earbuds reported them. */
    private fun inEar(on: Boolean): ByteArray? {
        values[QcyItem.WEARING]?.let { w ->
            if (w.size < 3) return null
            val next = w.copyOf().also { it[0] = if (on) ON else OFF_VALUE }
            values[QcyItem.WEARING] = next
            readBacks += request(QcyItem.WEARING)
            return GattEnvelope.write(QcyWire.COMMAND_SHORT, QcyPacket.build(QcyEntry(QcyItem.WEARING, next)))
        }
        if (values[QcyItem.IN_EAR] == null) return null
        return switch(QcyItem.IN_EAR, on)
    }

    /** Sets the noise mode to [target], at the scene or level last used in it, else the model's default. */
    private fun noiseTo(target: String, value: Int? = null): ByteArray? {
        val mode = model?.mode(target)
        val v = value ?: when (target) {
            CANCELLING -> lastCancelling
            TRANSPARENCY -> lastTransparency
            else -> null
        }?.takeIf { mode == null || mode.covers(it) } ?: mode?.default ?: fallback()[target] ?: return null
        noise(v)
        val legacy = mode?.legacy ?: (v <= 0xFF)
        readBacks += request(if (legacy) QcyItem.NOISE_MODE else QcyItem.ANC_SETTING)
        if (legacy) return command(QcyItem.NOISE_MODE, v)
        var scene = (v shr 8) and 0xFF
        val level = v and 0xFF
        // QCY's app sends transparency at scene 1 and value 0 as scene 2 (QCYHeadsetClient.setNoiseMode).
        if (v shr 16 == 3 && scene == 1 && level == 0) scene = 2
        return command(QcyItem.ANC_SETTING, v shr 16, scene, level)
    }

    /** Values for a model PairFree does not know, in the form its last report took. */
    private fun fallback(): Map<String, Int> = if ((noise ?: 0x10000) <= 0xFF) LEGACY_FALLBACK else FALLBACK

    /** The messages for a change to one of the [groups], or null when the earbuds do not have the setting. */
    public fun change(change: SettingChange): List<ByteArray>? {
        val setting = groups.flatMap { it.settings }.firstOrNull { it.key == change.key } ?: return null
        val out: ByteArray = when (change) {
            is SettingChange.Toggle -> {
                if (setting !is Setting.Toggle) return null
                when (change.key) {
                    "anc" -> noiseTo(if (change.on) CANCELLING else OFF) ?: return null
                    "ambient_sound" -> noiseTo(if (change.on) TRANSPARENCY else OFF) ?: return null
                    else -> switch(TOGGLES[change.key] ?: return null, change.on)
                }
            }
            is SettingChange.Level -> {
                if (setting !is Setting.Level) return null
                val level = change.value.coerceIn(setting.min, setting.max)
                when (change.key) {
                    "balance" -> {
                        values[QcyItem.BALANCE] = byteArrayOf(level.toByte())
                        readBacks += request(QcyItem.BALANCE)
                        command(QcyItem.BALANCE, level)
                    }
                    "anc_scene_level" -> {
                        val v = noise ?: return null
                        val scene = model?.mode(CANCELLING)?.scenes?.firstOrNull { v in it.from..it.to } ?: return null
                        noiseTo(CANCELLING, scene.from + level - 1) ?: return null
                    }
                    "transparency_strength" -> {
                        val t = model?.mode(TRANSPARENCY) ?: return null
                        noiseTo(TRANSPARENCY, t.from + level - 1) ?: return null
                    }
                    else -> return null
                }
            }
            is SettingChange.Choice -> {
                if (setting !is Setting.Choice || change.option !in setting.options) return null
                when {
                    change.key == "anc_scene" -> {
                        val scene = model?.mode(CANCELLING)?.scenes?.firstOrNull { it.scene == change.option } ?: return null
                        // Keep the strength step where the new scene has one, else its strongest.
                        val step = noise?.let { v -> model?.mode(CANCELLING)?.scenes?.firstOrNull { v in it.from..it.to }?.let { v - it.from } } ?: 0
                        noiseTo(CANCELLING, (scene.from + step).coerceAtMost(scene.to)) ?: return null
                    }
                    change.key.startsWith("touch_") -> touch(change.key, change.option) ?: return null
                    else -> return null
                }
            }
            is SettingChange.Press -> return null
        }
        refresh()
        return listOf(out)
    }

    /** One gesture's action: the whole key table with the change, in key order, written to its characteristic. */
    private fun touch(key: String, option: String): ByteArray? {
        val table = keys ?: return null
        val gestureSide = key.removePrefix("touch_")
        val id = QcyKeys.KEYS.entries.firstOrNull { it.value == gestureSide }?.key ?: return null
        val action = QcyKeys.ACTIONS.entries.firstOrNull { it.value == option }?.key ?: return null
        val next = (table + (id to action)).toSortedMap()
        keys = next
        readBacks += GattEnvelope.read(QcyWire.KEYS_SHORT)
        return GattEnvelope.write(QcyWire.KEYS_SHORT, next.flatMap { (k, a) -> listOf(k.toByte(), a.toByte()) }.toByteArray())
    }

    public companion object {
        private const val BRAND = "QCY"
        private const val CANCELLING = "cancelling"
        private const val TRANSPARENCY = "transparency"
        private const val OFF = "off"
        private const val ON: Byte = 1
        private const val OFF_VALUE: Byte = 2
        private const val BALANCE_MAX = 100

        // The older one-byte noise values (PeatsAudio's copy of QCY's library and QCY's control panels).
        private const val LEGACY_OFF = 0x02
        private val LEGACY_CANCELLING = 0x41..0x9F
        private val LEGACY_TRANSPARENCY = 0xA0..0xAF

        /** For a model PairFree does not know: OpenQCY's values on a MeloBuds N70. */
        private val FALLBACK = mapOf(CANCELLING to 0x010500, TRANSPARENCY to 0x030104, OFF to 0x020000)

        /** ... and on one that reports one-byte values: those PeatsAudio sends the Capsule3 Pro. */
        private val LEGACY_FALLBACK = mapOf(CANCELLING to 0x63, TRANSPARENCY to 0xA5, OFF to LEGACY_OFF)

        /** What an unknown model is asked for: the noise item and the common switches. */
        private val UNKNOWN_MODEL_ITEMS = listOf(
            QcyItem.ANC_SETTING, QcyItem.GAME_MODE, QcyItem.SLEEP_MODE, QcyItem.IN_EAR, QcyItem.LDAC, QcyItem.MULTIPOINT, QcyItem.WIND_DETECTION,
        )

        /** Brand switches by their key. */
        private val TOGGLES = mapOf(
            "spatial_audio" to QcyItem.SPATIAL_AUDIO, "dynamic_eq" to QcyItem.ADAPTIVE_EQ, "adaptive_volume" to QcyItem.ADAPTIVE_VOLUME,
            "sleep_mode" to QcyItem.SLEEP_MODE, "wind_detection" to QcyItem.WIND_DETECTION, "led" to QcyItem.LED_MODE,
        )

        private val READABLE = setOf(QcyWire.VERSION_SHORT, QcyWire.BATTERY_SHORT, QcyWire.SETTINGS_SHORT, QcyWire.KEYS_SHORT)
        private val PRINTABLE = 0x20.toByte()..0x7E.toByte()

        private fun ancNumber(name: String): Int = Command.ANC_MODE.labels.entries.first { it.value == name }.key
    }
}
