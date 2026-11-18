package io.github.omegadestroyer.pairfree.protocol.sennheiser

import io.github.omegadestroyer.pairfree.protocol.PMath
import io.github.omegadestroyer.pairfree.protocol.toHex2
import io.github.omegadestroyer.pairfree.protocol.oneDecimal

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.PowerActions
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
public class SennheiserSession(public val model: SennheiserModel?, private val ble: Boolean = false) : SessionTalk<ByteArray>(), SennheiserEqSource {
    private var current: EarbudState = EarbudState()
    override val state: EarbudState get() = current
    override val pacing: Pacing = Pacing(replyMs = REPLY_MS, gapMs = GAP_MS)

    private val parser = Gaia3Parser()
    private fun isEq(c: SennheiserCommand): Boolean = c.vendor == SennheiserWire.VENDOR && ((c.get ?: c.set ?: 0) shr 9) == FEATURE_USER_EQ
    private val commands: List<SennheiserCommand> = SennheiserCommands.ALL.filter { (model?.properties == null || it.id in model.properties) && it.open && !isEq(it) && usedByScreens(it.id) }

    /** A property the app's screen for this product does not use is not read (the Momentum 4's `TransparentHearing` is the older switch; its screen is the slider). */
    private fun usedByScreens(id: String): Boolean = when (id) {
        SennheiserCommands.SIDETONE -> model?.sidetoneMax != null
        SennheiserCommands.ANC_LEVEL -> model?.transparency == "slider"
        SennheiserCommands.TRANSPARENCY_LEVEL -> model?.transparency == "levels"
        else -> true
    }
    private val byKey: Map<Int, SennheiserCommand> = SennheiserCommands.ALL.filter { it.get != null && !isEq(it) }.associateBy { Gaia3Packet.keyOf(it.vendor, it.get!!) }
    private val asked = mutableSetOf<Int>()
    private val answered = mutableMapOf<String, ByteArray>()
    private var readBacks = emptyList<ByteArray>()

    /** What the earbuds last answered for each property, as bytes: for the device report. */
    public val replies: Map<String, ByteArray> get() = answered

    private fun command(id: String): SennheiserCommand? = SennheiserCommands.ALL.firstOrNull { it.id == id }

    private fun frame(vendor: Int, word: Int, vararg payload: Int): ByteArray = frameOf(vendor, word, ByteArray(payload.size) { payload[it].toByte() })

    private fun frameOf(vendor: Int, word: Int, payload: ByteArray): ByteArray {
        val packet = Gaia3Packet(vendor, word, payload)
        asked += packet.key
        return packet.bytes()
    }

    private fun read(c: SennheiserCommand): ByteArray = frame(c.vendor, c.get!!)

    override fun start(): List<ByteArray> = buildList {
        for (feature in registered()) SennheiserWire.FEATURES[feature]?.let { add(frame(SennheiserWire.REGISTER_VENDOR, SennheiserWire.REGISTER_COMMAND, it)) }
        for (c in commands) if (c.get != null) add(read(c))
        addAll(eqStart())
        addAll(moreStart())
    }

    /** The notifications to register: the common ones, and those of the features this product has (the app registers each feature as it sets it up). */
    private fun registered(): List<String> = buildList {
        addAll(REGISTERED)
        if (model?.peers == true) add("device_management")
        if (model?.fitTest == true) add("fit_test")
        if (hasFeature("find-device-1")) add("find_device")
        if (hasFeature("sound-personalization-1") || hasFeature("sound-personalization-2")) add("personalized_sound")
        if (hasFeature("auracast-1")) add("auracast")
    }

    private fun hasFeature(name: String): Boolean = model?.properties != null && model.features.containsKey(name)

    /** The reads that need a payload: the auto power-off timer (timer 0) and, where the product has them, each touch gesture. */
    private fun moreStart(): List<ByteArray> = buildList {
        if (has(SennheiserCommands.AUTO_OFF)) add(readWith(SennheiserCommands.AUTO_OFF, byteArrayOf(0)))
        addAll(touchReads())
        // The audio mode (podcast, personalised sound) is read for every product that has the equalizer's reads already.
        if (hasFeature("audio-mode-1") && Gaia3Packet.keyOf(SennheiserWire.VENDOR, command(SennheiserCommands.AUDIO_MODE)!!.get!!) !in asked) add(read(command(SennheiserCommands.AUDIO_MODE)!!))
        if (hasFeature("find-device-1")) for (side in 0..1) add(readWith(SennheiserCommands.FIND_QUERY, byteArrayOf(side.toByte())))
        if (hasFeature("sound-personalization-1") || hasFeature("sound-personalization-2")) add(read(command(SennheiserCommands.PERSONALIZED_MODE)!!))
        if (model?.peers == true && model.properties != null) {
            add(read(command(SennheiserCommands.PEER_COUNT)!!))
            add(read(command(SennheiserCommands.PEER_OWN)!!))
        }
        if (model?.sensors == true && model.properties != null) add(readWith(SennheiserCommands.SENSOR_STATE, byteArrayOf(SENSOR_HEART_RATE.toByte())))
        if (hasFeature("auracast-1")) add(read(command(SennheiserCommands.AURACAST_IDS)!!))
    }

    /** One read per gesture of each earbud, and whether they are the defaults. */
    private fun touchReads(): List<ByteArray> = buildList {
        if (model?.touch == null || !has(SennheiserCommands.MMI)) return@buildList
        for (side in listOf("left", "right")) for (gesture in TOUCH_GESTURE_ORDER) add(readWith(SennheiserCommands.MMI, byteArrayOf(touchSide(side).toByte(), touchPattern(gesture).toByte())))
        if (has(SennheiserCommands.MMI_IS_DEFAULT)) add(read(command(SennheiserCommands.MMI_IS_DEFAULT)!!))
    }

    private fun has(id: String): Boolean = command(id) != null && (model?.properties == null || id in model.properties)

    private fun readWith(id: String, payload: ByteArray): ByteArray {
        val c = command(id)!!
        return frameOf(c.vendor, c.get!!, payload)
    }

    override fun poll(): List<ByteArray> = (command(SennheiserCommands.BATTERY_PERCENT)?.let { listOf(read(it)) } ?: emptyList()) + (if (sensorsOn == true) sensorReads() else emptyList())

    /** Only the commands of data/sennheiser/commands.json go out: the reads and the writes of its properties, and the notification registration. */
    override fun allowed(message: ByteArray): Boolean {
        val packet = Gaia3Parser().feed(message).singleOrNull() ?: return false
        if (packet.vendor == SennheiserWire.REGISTER_VENDOR && packet.word == SennheiserWire.REGISTER_COMMAND) return true
        return SennheiserCommands.ALL.any { it.vendor == packet.vendor && (it.get == packet.word || it.set == packet.word) }
    }

    /** Over Bluetooth LE a packet is the vendor, the word and the payload with no frame (docs/sennheiser/PROTOCOL.md section 3). */
    override fun bytes(message: ByteArray): ByteArray = if (ble) message.copyOfRange(4, message.size) else message

    /** An answer is a reply or an error with the sent command's vendor and command. */
    override fun answers(sent: ByteArray, got: Any): Boolean = got is Int && Gaia3Parser().feed(sent).singleOrNull()?.key == got

    override fun takeReadBacks(): List<ByteArray> = readBacks.also { readBacks = emptyList() }

    override fun takeOutbox(): List<ByteArray> = (eqOutbox + moreOutbox).also {
        eqOutbox = emptyList()
        moreOutbox = emptyList()
    }

    override fun feed(bytes: ByteArray): Fed {
        var changed = false
        val keys = mutableListOf<Any>()
        val framed = if (ble && bytes.size >= 4) byteArrayOf(0xFF.toByte(), 3, 0, (bytes.size - 4).toByte()) + bytes else bytes
        for (packet in parser.feed(framed)) guarded {
            log?.invoke("recv $packet")
            if (packet.key !in asked && !packet.isNotification) return@guarded
            if (packet.isReply || packet.isError) keys += packet.key
            if (packet.vendor == SennheiserWire.VENDOR && packet.feature == FEATURE_USER_EQ) {
                if (eqFeed(packet)) changed = true
                return@guarded
            }
            if (packet.isError) return@guarded
            if (!packet.isReply && !packet.isNotification) return@guarded
            val c = byKey[packet.key] ?: return@guarded
            // A reply and a notification of the same property carry the same bytes (the product file's Event is its Response).
            if (decode(c, packet.payload)) changed = true
        }
        return Fed(changed, keys)
    }

    private val values = mutableMapOf<String, ByteArray>()
    private val touchState = mutableMapOf<Pair<Int, Int>, Int>()
    private val findRinging = mutableMapOf<Int, Boolean>()
    private var findKnown = false
    private var fitStage = 0
    private var fitLeft: Int? = null
    private var fitRight: Int? = null
    private var fitNotReady = false
    private val peers = mutableMapOf<Int, SennheiserPeer>()
    private var peerCount: Int? = null
    private var peerOwn: Int? = null
    private val sensorData = mutableMapOf<Int, ByteArray>()
    private var sensorsOn: Boolean? = null
    private var moreOutbox = emptyList<ByteArray>()
    private var auracastKnown = false
    private var auracastScanning = false
    private val auracastStates = mutableMapOf<Int, AuracastSource>()
    private val auracastScan = linkedMapOf<String, AuracastBroadcast>()

    private fun markFind(): Boolean {
        if (findKnown) return false
        findKnown = true
        return true
    }

    private fun sensorReads(): List<ByteArray> = listOf(SENSOR_HEART_RATE, SENSOR_BODY_TEMPERATURE).map { readWith(SennheiserCommands.SENSOR_DATA, byteArrayOf(it.toByte())) }

    /** The listed paired devices (multipoint): index, connection status and the name each reports. */
    public val pairedDevices: List<SennheiserPeer> get() = peers.values.sortedBy { it.index }
    private var autoOffSeconds: Int? = null

    /** Takes one property's bytes; true when what the screens show changed. */
    private fun decode(c: SennheiserCommand, payload: ByteArray): Boolean {
        if (c.id == SennheiserCommands.MMI) {
            // A gesture's answer names its own button and gesture: button, gesture, function.
            if (payload.size < 3) return false
            val at = (payload[0].toInt() and 0xFF) to (payload[1].toInt() and 0xFF)
            val function = payload[2].toInt() and 0xFF
            if (touchState[at] == function) return false
            touchState[at] = function
            return true
        }
        when (c.id) {
            SennheiserCommands.FIND_QUERY -> {
                // The answer to a question about one earbud is an event naming it: whether it is ringing, then which.
                if (payload.size < 2) return markFind()
                findRinging[payload[1].toInt() and 0xFF] = payload[0].toInt() != 0
                findKnown = true
                return true
            }
            SennheiserCommands.FIT_PRECONDITION -> {
                if (payload.size < 2 || fitStage != 1) return false
                // 1 is a ready earbud; the test then starts (`FitTester.onPreconditionResults`).
                if ((payload[0].toInt() and 0xFF) == 1 && (payload[1].toInt() and 0xFF) == 1) {
                    fitStage = 2
                    moreOutbox += frameOf(c.vendor, command(SennheiserCommands.FIT_CHECK)!!.get!!, ByteArray(0))
                } else {
                    fitStage = 0
                    fitNotReady = true
                }
                return true
            }
            SennheiserCommands.FIT_CHECK -> {
                if (payload.size < 2 || fitStage != 2) return false
                fitLeft = payload[0].toInt() and 0xFF
                fitRight = payload[1].toInt() and 0xFF
                fitStage = 3
                return true
            }
            SennheiserCommands.PEER_COUNT -> {
                if (payload.size < 2) return false
                val n = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
                if (peerCount == n) return false
                peerCount = n
                // The app reads the entries from the product's start index (0) until it has them all, at most `defaultListSize` (8) of them.
                for (i in 0 until minOf(8, maxOf(n, 1) + 1)) moreOutbox += readWith(SennheiserCommands.PEER_INFO, byteArrayOf(i.toByte()))
                return true
            }
            SennheiserCommands.PEER_OWN -> {
                peerOwn = payload.firstOrNull()?.toInt()?.and(0xFF)
                return true
            }
            SennheiserCommands.PEER_INFO -> {
                if (payload.size < 3) return false
                val index = payload[0].toInt() and 0xFF
                val status = payload[2].toInt() and 0xFF
                if (status == PEER_NO_DEVICE || status == PEER_PAIRING_CANCELLED) return peers.remove(index) != null
                val name = payload.copyOfRange(3, payload.size).filter { it.toInt() in 0x20..0x7E || it.toInt() < 0 }.toByteArray().decodeToString().trim()
                peers[index] = SennheiserPeer(index, status, name.ifEmpty { "#$index" })
                return true
            }
            SennheiserCommands.PEER_STATUS -> {
                if (payload.size < 2) return false
                val index = payload[0].toInt() and 0xFF
                val known = peers[index]
                val status = payload[1].toInt() and 0xFF
                if (known == null) {
                    moreOutbox += readWith(SennheiserCommands.PEER_INFO, byteArrayOf(index.toByte()))
                    return false
                }
                if (known.status == status) return false
                peers[index] = SennheiserPeer(index, status, known.name)
                return true
            }
            SennheiserCommands.AURACAST_IDS -> {
                // The count of sources the earbuds receive from, then their ids (the app's `Auracast_GetSourceIds`).
                if (payload.isEmpty()) return false
                auracastKnown = true
                val ids = payload.drop(1).map { it.toInt() and 0xFF }.take(payload[0].toInt() and 0xFF)
                auracastStates.keys.retainAll(ids.toSet())
                for (id in ids) moreOutbox += readWith(SennheiserCommands.AURACAST_STATE, byteArrayOf(id.toByte()))
                return true
            }
            SennheiserCommands.AURACAST_STATE -> {
                val st = AuracastSource.of(payload) ?: return false
                auracastKnown = true
                if (auracastStates[st.sourceId]?.same(st) == true) return false
                auracastStates[st.sourceId] = st
                return true
            }
            SennheiserCommands.AURACAST_SCAN_STATUS -> {
                // 0 stopped, 1 started, 2 in progress, 3 timeout, 4 failed (`Auracast_StatusSourceScan`).
                val state = payload.firstOrNull()?.toInt()?.and(0xFF) ?: return false
                val scanning = state == 1 || state == 2
                if (scanning == auracastScanning) return false
                auracastScanning = scanning
                return true
            }
            SennheiserCommands.AURACAST_RESULT -> {
                val r = AuracastBroadcast.of(payload) ?: return false
                auracastKnown = true
                auracastScan[r.addressKey] = r
                return true
            }
            SennheiserCommands.SENSOR_STATE -> {
                if (payload.size < 2) return false
                val on = payload[1].toInt() != 0
                if (sensorsOn == on) return false
                sensorsOn = on
                if (on) moreOutbox += sensorReads()
                return true
            }
            SennheiserCommands.SENSOR_DATA -> {
                if (payload.size < 2) return false
                sensorData[payload[0].toInt() and 0xFF] = payload.copyOfRange(1, payload.size)
                return true
            }
        }
        if (c.id == SennheiserCommands.AUTO_OFF) {
            // The answer to the read of timer 0: the timer's number, then its value in seconds.
            if (payload.size < 3 || payload[0].toInt() != 0) return false
            val seconds = ((payload[1].toInt() and 0xFF) shl 8) or (payload[2].toInt() and 0xFF)
            if (autoOffSeconds == seconds) return false
            autoOffSeconds = seconds
            return true
        }
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
    public val modelId: String? get() = values[SennheiserCommands.MODEL_ID]?.let { b -> b.filter { it.toInt() in 0x20..0x7E }.toByteArray().decodeToString().trim().ifEmpty { null } }

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
                // The transparency level: the Momentum 4 and Accentum Plus have the app's slider between transparency (0) and noise cancelling (100); the earbuds with
                // transparent hearing take three levels (what the app writes: 1, 50 and 100).
                if (model?.transparency == "slider") values[SennheiserCommands.ANC_LEVEL]?.firstOrNull()?.let { add(Setting.Level(KEY_TRANSPARENCY_SLIDER, (it.toInt() and 0xFF).coerceAtMost(100), 0, 100)) }
                if (model?.transparency == "levels") u8(SennheiserCommands.TRANSPARENCY_LEVEL)?.let { v ->
                    add(Setting.Choice(KEY_TRANSPARENCY_LEVEL, TRANSPARENCY_NAMES.entries.firstOrNull { it.value == v }?.key, TRANSPARENCY_NAMES.keys.toList()))
                }
            }
            if (noise.isNotEmpty()) add(SettingGroup("noise", SettingTab.HOME, noise))
            val sound = buildList<Setting> {
                if (eqKnown) add(Setting.Toggle(KEY_BASS_BOOST, eqBass))
                audioMode()?.let { add(Setting.Toggle(KEY_PODCAST, it == AUDIO_SPEECH)) }
                personalizedOn()?.let { add(Setting.Toggle(KEY_PERSONALIZED, it)); add(Setting.Action(KEY_PERSONALIZED_RESET)) }
            }
            if (sound.isNotEmpty()) add(SettingGroup("sound", SettingTab.SOUND, sound))
            val calls = buildList<Setting> {
                on(SennheiserCommands.AUTO_CALL)?.let { add(Setting.Toggle(KEY_AUTO_CALL, it)) }
                on(SennheiserCommands.COMFORT_CALL)?.let { add(Setting.Toggle(KEY_COMFORT_CALL, it)) }
                val max = model?.sidetoneMax
                if (max != null) u8(SennheiserCommands.SIDETONE)?.let { add(Setting.Level(KEY_SIDETONE, it.coerceAtMost(max), 0, max)) }
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
                autoOffSeconds?.let { sec -> add(Setting.Choice(KEY_AUTO_OFF, (sec / 60).toString(), SennheiserTouchTables.AUTO_OFF_SECONDS.map { (it / 60).toString() })) }
                on(SennheiserCommands.BT_COMPAT)?.let { add(Setting.Toggle(KEY_BT_COMPAT, it)) }
                on(SennheiserCommands.OPEN_EARTIP)?.let { add(Setting.Toggle(KEY_OPEN_EARTIP, it)) }
                on(SennheiserCommands.TOUCH_AREA)?.let { add(Setting.Toggle(KEY_TOUCH_AREA, it)) }
                on(SennheiserCommands.LOSSLESS)?.let { add(Setting.Toggle(KEY_LOSSLESS, it)) }
                u8(SennheiserCommands.TAP_SENSITIVITY)?.let { v -> command(SennheiserCommands.TAP_SENSITIVITY)?.values?.entries?.firstOrNull { it.value == v }?.let { e -> add(Setting.Choice(KEY_TAP_SENSITIVITY, e.key.lowercase(), TAP_OPTIONS)) } }
                codec()?.let { add(Setting.Information(KEY_CODEC, it)) }
                sampleRate()?.let { add(Setting.Information(KEY_SAMPLE_RATE, "$it Hz")) }
                promptLanguage()?.let { add(Setting.Information(KEY_PROMPT_LANGUAGE, it.lowercase().replaceFirstChar { c -> c.uppercase() })) }
                wearStates().forEachIndexed { i, state -> add(Setting.Information(if (model?.form == io.github.omegadestroyer.pairfree.protocol.Form.EARBUDS) (if (i == 0) KEY_WEAR_LEFT else KEY_WEAR_RIGHT) else KEY_WEAR, state)) }
            }
            if (other.isNotEmpty()) add(SettingGroup("other", SettingTab.DEVICE, other))
            addAll(touchGroups())
            if (model?.features?.containsKey("reboot") == true && model.properties != null) add(SettingGroup(PowerActions.GROUP, SettingTab.DEVICE, listOf(Setting.Action(PowerActions.RESTART))))
            if (findKnown) add(SettingGroup("find", SettingTab.DEVICE, listOf(
                Setting.Action(KEY_FIND_LEFT, findRinging[SIDE_LEFT] == true),
                Setting.Action(KEY_FIND_RIGHT, findRinging[SIDE_RIGHT] == true),
            )))
            if (model?.fitTest == true && model.properties != null) add(SettingGroup("sh_fit", SettingTab.DEVICE, buildList {
                add(Setting.Action(KEY_FIT_TEST, fitStage in 1..2))
                if (fitNotReady) add(Setting.Information(KEY_FIT_LEFT, "not_ready"))
                else if (fitStage == 3) {
                    add(Setting.Information(KEY_FIT_LEFT, fitWord(fitLeft)))
                    add(Setting.Information(KEY_FIT_RIGHT, fitWord(fitRight)))
                }
            }))
            if (auracastKnown) add(SettingGroup("sh_auracast", SettingTab.DEVICE, buildList {
                add(Setting.Action(KEY_AURACAST_SCAN, auracastScanning))
                val added = auracastStates.values.map { it.addressKey }.toSet()
                val offered = auracastScan.values.filter { it.addressKey !in added }
                if (offered.isNotEmpty()) add(Setting.Choice(KEY_AURACAST_ADD, null, offered.map { it.label }))
                for (src in auracastStates.values.sortedBy { it.sourceId }) add(Setting.Information(KEY_AURACAST_SOURCE, src.state + ":" + (auracastScan[src.addressKey]?.name ?: src.addressKey)))
                // A source that wants a code (or got a wrong one) gets a line to type it in.
                if (auracastStates.values.any { it.state == "code_required" || it.state == "bad_code" }) add(Setting.Text(KEY_AURACAST_CODE, "", AURACAST_CODE_LENGTH))
                if (auracastStates.isNotEmpty()) add(Setting.Choice(KEY_AURACAST_REMOVE, null, auracastStates.values.sortedBy { it.sourceId }.map { auracastScan[it.addressKey]?.name ?: it.addressKey }))
            }))
            val others = pairedDevices.filter { it.index != peerOwn }
            if (others.isNotEmpty()) {
                fun label(p: SennheiserPeer) = if (others.count { it.name == p.name } > 1) "${p.name} (${p.index})" else p.name
                val off = others.filter { it.status and 0x7F == PEER_DISCONNECTED }.map(::label)
                val on = others.filter { it.status and 0x7F != PEER_DISCONNECTED }.map(::label)
                add(SettingGroup("connection", SettingTab.DEVICE, buildList {
                    if (off.isNotEmpty()) add(Setting.Choice(KEY_PEER_CONNECT, null, off))
                    if (on.isNotEmpty()) add(Setting.Choice(KEY_PEER_DISCONNECT, null, on))
                    add(Setting.Choice(KEY_PEER_DELETE, null, others.map(::label)))
                }))
            }
            if (model?.sensors == true && sensorsOn != null) add(SettingGroup("sh_sensors", SettingTab.DEVICE, buildList {
                add(Setting.Toggle(KEY_SENSORS, sensorsOn == true))
                if (sensorsOn == true) {
                    heartRate()?.let { add(Setting.Information(KEY_HEART_RATE, "$it")) }
                    bodyTemperature()?.let { add(Setting.Information(KEY_BODY_TEMPERATURE, it)) }
                }
            }))
        }

    /** The current audio mode (`AudioMode`: 1 user equalizer, 2 speech, 3 flat or personalised sound), or null while unread. */
    private fun audioMode(): Int? {
        val b = values[SennheiserCommands.AUDIO_MODE] ?: return null
        return if (b.size >= 2) ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF) else b.firstOrNull()?.toInt()?.and(0xFF)
    }

    /**
     * The generation of personalised sound the product's firmware has: 2 when the firmware reaches the second feature file's version, else 1 when it
     * reaches the first's (the product files' `sound-personalization-1` and `-2`), or null while the firmware is not known.
     */
    private fun personalizedVersion(): Int? {
        val m = model ?: return null
        val have = SennheiserEq.version(firmware) ?: return null
        fun reaches(name: String) = m.features[name]?.let { v -> SennheiserEq.version(v)?.let { SennheiserEq.atLeast(have, it) } } == true
        return when {
            reaches("sound-personalization-2") -> 2
            reaches("sound-personalization-1") -> 1
            else -> null
        }
    }

    /** Whether personalised sound is on, once the earbuds hold a personalisation; null when the product has none or has none set up. */
    private fun personalizedOn(): Boolean? {
        val version = personalizedVersion() ?: return null
        val mode = u8(SennheiserCommands.PERSONALIZED_MODE) ?: return null
        if (mode == 0) return null
        return if (version == 1) mode == PERSONALIZED_V1_ENABLED else audioMode() == AUDIO_PERSONALIZED
    }

    private fun fitWord(v: Int?): String = when {
        v == null || v == 255 -> "none"
        v == 254 -> "invalid"
        v <= 29 -> "bad"
        v <= 59 -> "acceptable"
        else -> "good"
    }

    /** The heart rate sensor's value in beats per minute (the second byte of its data, as the app reads it). */
    private fun heartRate(): Int? = sensorData[SENSOR_HEART_RATE]?.getOrNull(1)?.toInt()?.and(0xFF)?.takeIf { it in 1..254 }

    /** The body temperature as the app reads it (`parseBodyTemperatureSensorData`): a value with a power of ten, celsius or fahrenheit by the flags. */
    private fun bodyTemperature(): String? {
        val d = sensorData[SENSOR_BODY_TEMPERATURE] ?: return null
        if (d.size < 5 || (d[3].toInt() and 0xFF) == 255) return null
        val celsius = (5 and d[0].toInt().inv()) != 0
        val value = (((d[2].toInt() and 0xFF) shl 8) + (d[3].toInt() and 0xFF)) * PMath.pow(10.0, d[4].toInt().toDouble())
        return value.oneDecimal() + " " + (if (celsius) "°C" else "°F")
    }

    private fun sampleRate(): Int? = values[SennheiserCommands.SAMPLE_RATE]?.takeIf { it.size >= 4 }?.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }?.takeIf { it in 1..1_000_000 }?.toInt()

    /** The language of the voice prompts the earbuds read out (`VoicePrompt_Language`: the app's names, 0 English to 7 Korean). */
    private fun promptLanguage(): String? = u8(SennheiserCommands.PROMPT_LANGUAGE)?.let { code -> command(SennheiserCommands.PROMPT_LANGUAGE)?.values?.entries?.firstOrNull { it.value == code }?.key }

    /** Where each earbud (or the headphones) is: `PhysicalDevice_State` in the app's words, as keys the screens turn into words. */
    private fun wearStates(): List<String> {
        val b = values[SennheiserCommands.PHYSICAL_STATE] ?: return emptyList()
        val names = mapOf(1 to "in_case", 2 to "not_on_head", 3 to "on_head")
        val count = if (model?.form == io.github.omegadestroyer.pairfree.protocol.Form.EARBUDS) 2 else 1
        return b.take(count).map { names[it.toInt() and 0xFF] ?: "unknown" }
    }

    // Touch and button controls (docs/sennheiser/PROTOCOL.md section 4.3) -------------------------------------------------------------------

    private fun touchSide(name: String): Int = SennheiserTouchTables.BUTTONS.getValue(name)

    private fun touchPattern(gesture: String): Int = SennheiserTouchTables.GESTURES.getValue(gesture)

    /** The functions this product's screen offers for [gesture], with the firmware its class demands. */
    private fun touchChoices(gesture: String): List<SennheiserTouchAction> {
        val list = model?.touch ?: return emptyList()
        val have = SennheiserEq.version(firmware)
        return list.filter { a ->
            (a.gestures.isEmpty() || gesture in a.gestures) &&
                (a.minFirmware == null || (have != null && SennheiserEq.version(a.minFirmware)?.let { SennheiserEq.atLeast(have, it) } == true))
        }
    }

    private fun touchGroups(): List<SettingGroup> {
        if (model?.touch == null || touchState.isEmpty()) return emptyList()
        val out = mutableListOf<SettingGroup>()
        for (side in listOf("left", "right")) {
            val rows = buildList<Setting> {
                for (gesture in TOUCH_GESTURE_ORDER) {
                    val function = touchState[touchSide(side) to touchPattern(gesture)] ?: continue
                    // The app shows a gesture only when the earbuds' answer is one of the functions its screen offers.
                    val current = model.touch.firstOrNull { SennheiserTouchTables.ACTIONS[it.action] == function } ?: continue
                    val options = touchChoices(gesture)
                    if (options.isEmpty()) continue
                    add(Setting.Choice(touchKey(gesture, side), TOUCH_OPTION.getValue(current.action), options.map { TOUCH_OPTION.getValue(it.action) }))
                }
            }
            if (rows.isNotEmpty()) out += SettingGroup("touch_$side", SettingTab.CONTROLS, rows)
        }
        if (out.isNotEmpty() && touchDefault == false) out += SettingGroup("touch_default", SettingTab.CONTROLS, listOf(Setting.Action(KEY_TOUCH_DEFAULT)))
        return out
    }

    private val touchDefault: Boolean? get() = values[SennheiserCommands.MMI_IS_DEFAULT]?.firstOrNull()?.let { it.toInt() != 0 }

    private fun touchKey(gesture: String, side: String) = "touch_${TOUCH_KEY_GESTURE.getValue(gesture)}_$side"

    /** A choice in one of the touch rows: the key is `touch_<gesture>_<side>`. */
    private fun changeTouch(change: SettingChange.Choice): List<ByteArray>? {
        val side = change.key.substringAfterLast('_')
        val gestureKey = change.key.removePrefix("touch_").removeSuffix("_$side")
        val gesture = TOUCH_KEY_GESTURE.entries.firstOrNull { it.value == gestureKey }?.key ?: return null
        if (side != "left" && side != "right") return null
        val action = TOUCH_OPTION.entries.firstOrNull { it.value == change.option }?.key ?: return null
        if (touchChoices(gesture).none { it.action == action }) return null
        val function = SennheiserTouchTables.ACTIONS.getValue(action)
        val c = command(SennheiserCommands.MMI) ?: return null
        touchState[touchSide(side) to touchPattern(gesture)] = function
        readBacks += readWith(SennheiserCommands.MMI, byteArrayOf(touchSide(side).toByte(), touchPattern(gesture).toByte()))
        return listOf(frameOf(c.vendor, c.set!!, byteArrayOf(touchSide(side).toByte(), touchPattern(gesture).toByte(), function.toByte())))
    }

    /** Restores the gestures to the product's own defaults (`SetDefaultMMIConfig`, as the app's reset of the touch controls does), then reads them again. */
    private fun restoreTouch(): List<ByteArray>? {
        val c = command(SennheiserCommands.MMI_DEFAULT) ?: return null
        values[SennheiserCommands.MMI_IS_DEFAULT] = byteArrayOf(1)
        val out = listOf(frameOf(c.vendor, c.set!!, ByteArray(0)))
        readBacks += touchReads()
        return out
    }

    private fun audioModeFrame(mode: Int): ByteArray? {
        val audio = command(SennheiserCommands.AUDIO_MODE) ?: return null
        values[SennheiserCommands.AUDIO_MODE] = u16(mode)
        readBacks += read(audio)
        return frameOf(audio.vendor, audio.set!!, u16(mode))
    }

    private fun press(key: String): List<ByteArray>? {
        when (key) {
            KEY_FIND_LEFT, KEY_FIND_RIGHT -> {
                if (!findKnown) return null
                val side = if (key == KEY_FIND_LEFT) SIDE_LEFT else SIDE_RIGHT
                val ring = command(SennheiserCommands.FIND_RING) ?: return null
                val stop = command(SennheiserCommands.FIND_STOP) ?: return null
                val wasRinging = findRinging[side] == true
                readBacks += readWith(SennheiserCommands.FIND_QUERY, byteArrayOf(side.toByte()))
                if (wasRinging) {
                    findRinging.keys.toList().forEach { findRinging[it] = false }
                    return listOf(frameOf(stop.vendor, stop.set!!, ByteArray(0)))
                }
                findRinging[side] = true
                return listOf(frameOf(ring.vendor, ring.set!!, byteArrayOf(side.toByte())))
            }
            KEY_FIT_TEST -> {
                if (model?.fitTest != true) return null
                if (fitStage in 1..2) {
                    fitStage = 0
                    return emptyList()
                }
                fitStage = 1
                fitLeft = null
                fitRight = null
                fitNotReady = false
                val c = command(SennheiserCommands.FIT_PRECONDITION) ?: return null
                return listOf(frameOf(c.vendor, c.get!!, ByteArray(0)))
            }
            KEY_AURACAST_SCAN -> {
                // The earbuds scan themselves, for five seconds as the app asks (`AURACAST_SCANNING_TIME_SECS`); the results come as events.
                val c = command(SennheiserCommands.AURACAST_SCAN) ?: return null
                if (!auracastKnown) return null
                if (auracastScanning) {
                    val stop = command(SennheiserCommands.AURACAST_SCAN_STOP) ?: return null
                    auracastScanning = false
                    return listOf(frameOf(stop.vendor, stop.set!!, ByteArray(0)))
                }
                auracastScan.clear()
                auracastScanning = true
                readBacks += read(command(SennheiserCommands.AURACAST_SCAN_STATUS)!!)
                return listOf(frameOf(c.vendor, c.set!!, u16(AURACAST_SCAN_SECONDS)))
            }
            KEY_PERSONALIZED_RESET -> {
                val c = command(SennheiserCommands.PERSONALIZED_RESET) ?: return null
                readBacks += read(command(SennheiserCommands.PERSONALIZED_MODE)!!)
                return listOf(frameOf(c.vendor, c.set!!, ByteArray(0)))
            }
        }
        return null
    }

    private fun toggleMore(change: SettingChange.Toggle): List<ByteArray>? {
        when (change.key) {
            KEY_PODCAST -> {
                if (audioMode() == null) return null
                return audioModeFrame(if (change.on) AUDIO_SPEECH else AUDIO_USER_EQ)?.let { listOf(it) }
            }
            KEY_PERSONALIZED -> {
                val version = personalizedVersion() ?: return null
                if (version == 2) return audioModeFrame(if (change.on) AUDIO_PERSONALIZED else AUDIO_USER_EQ)?.let { listOf(it) }
                // The first generation: on is the mode `enabled`; off sends the user equalizer back (`SoundPersonalizationImpl.send_v1`).
                if (change.on) {
                    val c = command(SennheiserCommands.PERSONALIZED_MODE) ?: return null
                    values[SennheiserCommands.PERSONALIZED_MODE] = byteArrayOf(PERSONALIZED_V1_ENABLED.toByte())
                    readBacks += read(c)
                    return listOf(frameOf(c.vendor, c.set!!, byteArrayOf(PERSONALIZED_V1_ENABLED.toByte())))
                }
                return audioModeFrame(AUDIO_USER_EQ)?.let { listOf(it) }?.also { values[SennheiserCommands.PERSONALIZED_MODE] = byteArrayOf(PERSONALIZED_V1_STANDBY.toByte()) }
            }
            KEY_SENSORS -> {
                val c = command(SennheiserCommands.SENSOR_ENABLE) ?: return null
                if (sensorsOn == null) return null
                sensorsOn = change.on
                val flag = if (change.on) 1 else 0
                val frames = listOf(SENSOR_HEART_RATE, SENSOR_BODY_TEMPERATURE).map { frameOf(c.vendor, c.set!!, byteArrayOf(it.toByte(), flag.toByte())) }
                if (change.on) moreOutbox += sensorReads()
                return frames
            }
        }
        return null
    }

    private fun auracastChange(change: SettingChange.Choice): List<ByteArray>? {
        if (change.key == KEY_AURACAST_ADD) {
            val b = auracastScan.values.firstOrNull { it.label == change.option } ?: return null
            val c = command(SennheiserCommands.AURACAST_ADD) ?: return null
            // The app's `BassAddSourceOperation`: the broadcaster's address and set, its id, "synchronise without past info", an unknown interval (0xFFFF),
            // one subgroup that takes every stream, and no metadata.
            val payload = byteArrayOf(b.addressType.toByte()) + b.address + byteArrayOf(b.advSid.toByte()) + b.broadcastId + byteArrayOf(PA_SYNC_WITHOUT_PAST.toByte(), -1, -1, 1, -1, -1, -1, -1, 0)
            readBacks += read(command(SennheiserCommands.AURACAST_IDS)!!)
            return listOf(frameOf(c.vendor, c.set!!, payload))
        }
        val target = auracastStates.values.firstOrNull { (auracastScan[it.addressKey]?.name ?: it.addressKey) == change.option } ?: return null
        val c = command(SennheiserCommands.AURACAST_REMOVE) ?: return null
        auracastStates.remove(target.sourceId)
        readBacks += read(command(SennheiserCommands.AURACAST_IDS)!!)
        return listOf(frameOf(c.vendor, c.set!!, byteArrayOf(target.sourceId.toByte())))
    }

    /** The code of the source that waits for one: up to sixteen characters, padded with zero bytes as the app does (`BassSetBroadcastCodeOperation`). */
    private fun auracastCode(code: String): List<ByteArray>? {
        val target = auracastStates.values.firstOrNull { it.state == "code_required" || it.state == "bad_code" } ?: return null
        val c = command(SennheiserCommands.AURACAST_CODE) ?: return null
        val bytes = code.encodeToByteArray().take(AURACAST_CODE_LENGTH).toByteArray()
        if (bytes.isEmpty()) return null
        readBacks += readWith(SennheiserCommands.AURACAST_STATE, byteArrayOf(target.sourceId.toByte()))
        return listOf(frameOf(c.vendor, c.set!!, byteArrayOf(target.sourceId.toByte()) + bytes + ByteArray(AURACAST_CODE_LENGTH - bytes.size)))
    }

    /** Deletes an entry of the earbuds' paired devices list (`DeviceList_DeleteEntry`); the screen confirms first. Never the phone's own entry. */
    private fun peerDelete(change: SettingChange.Choice): List<ByteArray>? {
        val target = pairedDevices.filter { it.index != peerOwn }.firstOrNull { it.name == change.option || "${it.name} (${it.index})" == change.option } ?: return null
        val c = command(SennheiserCommands.PEER_DELETE) ?: return null
        peers.remove(target.index)
        readBacks += read(command(SennheiserCommands.PEER_COUNT)!!)
        return listOf(frameOf(c.vendor, c.set!!, byteArrayOf(target.index.toByte())))
    }

    private fun peerChange(change: SettingChange.Choice): List<ByteArray>? {
        val others = pairedDevices.filter { it.index != peerOwn }
        val target = others.firstOrNull { it.name == change.option || "${it.name} (${it.index})" == change.option } ?: return null
        val connect = change.key == KEY_PEER_CONNECT
        val c = command(if (connect) SennheiserCommands.PEER_CONNECT else SennheiserCommands.PEER_DISCONNECT) ?: return null
        readBacks += readWith(SennheiserCommands.PEER_STATUS, byteArrayOf(target.index.toByte()))
        return listOf(frameOf(c.vendor, c.set!!, byteArrayOf(target.index.toByte())))
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
        if (change is SettingChange.Toggle && change.key in setOf(KEY_PODCAST, KEY_PERSONALIZED, KEY_SENSORS)) return toggleMore(change)
        if (change is SettingChange.Toggle) {
            val flag = if (change.on) 1 else 0
            if (change.key == KEY_BASS_BOOST) return eqBassBoost(change.on)
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
        if (change is SettingChange.Level) {
            val (id, max) = when (change.key) {
                KEY_SIDETONE -> SennheiserCommands.SIDETONE to (model?.sidetoneMax ?: return null)
                KEY_TRANSPARENCY_SLIDER -> SennheiserCommands.ANC_LEVEL to 100
                else -> return null
            }
            if (values[id] == null) return null
            val v = change.value.coerceIn(0, max)
            values[id] = byteArrayOf(v.toByte())
            return write(id, v)
        }
        if (change is SettingChange.Press && change.key == KEY_TOUCH_DEFAULT) return restoreTouch()
        if (change is SettingChange.Press && change.key == PowerActions.RESTART) {
            // The app restarts the earbuds after some settings (eco mode) with `Reboot`; the screen confirms first.
            if (model?.features?.containsKey("reboot") != true) return null
            val c = command(SennheiserCommands.REBOOT) ?: return null
            return listOf(frameOf(c.vendor, c.set!!, ByteArray(0)))
        }
        if (change is SettingChange.Press) return press(change.key)
        if (change is SettingChange.Choice && (change.key == KEY_PEER_CONNECT || change.key == KEY_PEER_DISCONNECT)) return peerChange(change)
        if (change is SettingChange.Choice && (change.key == KEY_AURACAST_ADD || change.key == KEY_AURACAST_REMOVE)) return auracastChange(change)
        if (change is SettingChange.Choice && change.key == KEY_AURACAST_CODE) return auracastCode(change.option)
        if (change is SettingChange.Choice && change.key == KEY_PEER_DELETE) return peerDelete(change)
        if (change is SettingChange.Choice && change.key.startsWith("touch_")) return changeTouch(change)
        if (change is SettingChange.Choice && change.key == KEY_AUTO_OFF) {
            val minutes = change.option.toIntOrNull() ?: return null
            val seconds = minutes * 60
            if (seconds !in SennheiserTouchTables.AUTO_OFF_SECONDS) return null
            val c = command(SennheiserCommands.AUTO_OFF) ?: return null
            autoOffSeconds = seconds
            readBacks += readWith(SennheiserCommands.AUTO_OFF, byteArrayOf(0))
            return listOf(frameOf(c.vendor, c.set!!, byteArrayOf(0, (seconds shr 8).toByte(), seconds.toByte())))
        }
        if (change is SettingChange.Choice && change.key == KEY_TRANSPARENCY_LEVEL) {
            val v = TRANSPARENCY_NAMES[change.option] ?: return null
            if (values[SennheiserCommands.TRANSPARENCY_LEVEL] == null) return null
            values[SennheiserCommands.TRANSPARENCY_LEVEL] = byteArrayOf(v.toByte())
            return write(SennheiserCommands.TRANSPARENCY_LEVEL, v)
        }
        if (change is SettingChange.Choice && change.key == KEY_TAP_SENSITIVITY) {
            val v = command(SennheiserCommands.TAP_SENSITIVITY)?.values?.entries?.firstOrNull { it.key.equals(change.option, ignoreCase = true) }?.value ?: return null
            if (values[SennheiserCommands.TAP_SENSITIVITY] == null) return null
            values[SennheiserCommands.TAP_SENSITIVITY] = byteArrayOf(v.toByte())
            return write(SennheiserCommands.TAP_SENSITIVITY, v)
        }
        if (change is SettingChange.Choice && change.key == KEY_PROMPTS) {
            val v = PROMPTS[change.option] ?: return null
            if (values[SennheiserCommands.PROMPT_MODE] == null) return null
            values[SennheiserCommands.PROMPT_MODE] = byteArrayOf(v.toByte())
            return write(SennheiserCommands.PROMPT_MODE, v)
        }
        return null
    }

    // The equalizer (docs/sennheiser/PROTOCOL.md section 6) ------------------------------------------------------------------------------------

    private class EqPending(val key: Int, val tag: String)

    private val eqPending = ArrayDeque<EqPending>()
    private var eqOutbox = emptyList<ByteArray>()

    /** Which equalizer the product answered as, or null until one did (or when it has none). */
    public var eqKind: SennheiserEqKind? = null
        private set

    /** The band gains the earbuds hold (or this phone last sent), in dB. */
    override var eqGains: List<Double>? = null
        private set

    /** Bass boost, the app's `bassBoostEnabled` (user-eq-2's sub-mode 1; the knob's flag on the older two). */
    public var eqBass: Boolean = false
        private set

    private var gen2Min = -SennheiserEqTables.GEN2_RANGE_DB.toDouble()
    private var gen2Max = SennheiserEqTables.GEN2_RANGE_DB.toDouble()
    private var gen2Bands = 0
    private val gen2Read = mutableMapOf<Int, Double>()
    private var levelShift: Double? = null
    private var knobX: Int? = null
    private var knobY: Int? = null
    private var shelfReply: List<Int>? = null
    private var knobAsked = false

    /** What the earbuds last said of their EQ, for the Sound tab to follow. */
    override val eq: Any? get() = if (eqKind == null) null else Triple(eqKind, eqGains, eqBass)

    private val shelf: SennheiserShelfEq? get() = model?.eq?.gen1?.let { SennheiserEqTables.GEN1[it] }

    private fun eqFrame(c: SennheiserCommand, set: Boolean, payload: ByteArray, tag: String = ""): ByteArray {
        val word = (if (set) c.set else c.get)!!
        val packet = Gaia3Packet(c.vendor, word, payload)
        eqPending += EqPending(packet.key, tag)
        asked += packet.key
        return packet.bytes()
    }

    private fun u16(v: Int): ByteArray = byteArrayOf((v shr 8).toByte(), v.toByte())

    private fun shortList(values: List<Int>): ByteArray = values.flatMap { listOf((it shr 8).toByte(), it.toByte()) }.toByteArray()

    private fun eqRead(id: String, tag: String, payload: ByteArray = ByteArray(0)): ByteArray = eqFrame(command(id)!!, false, payload, tag)

    /** What the opening asks for: the audio mode, and the first read of whichever equalizer the model has. */
    private fun eqStart(): List<ByteArray> {
        val m = model?.eq ?: return emptyList()
        if (!m.gen2 && m.gen1 == null && m.threeBandLimit == 0) return emptyList()
        return buildList {
            command(SennheiserCommands.AUDIO_MODE)?.let { add(read(it)) }
            if (m.gen2) {
                add(eqRead(SennheiserCommands.EQ2_CONFIG, "config"))
                add(eqRead(SennheiserCommands.EQ_SUBMODE, "sub"))
            } else {
                add(askKnob())
            }
        }
    }

    private fun askKnob(): ByteArray {
        knobAsked = true
        return eqRead(SennheiserCommands.EQ1_KNOB, "knobY", u16(1))
    }

    private fun int16At(p: List<Int>, i: Int): Int? = if (p.size >= 2 * i + 2) (p[2 * i] shl 8) or p[2 * i + 1] else null

    /** Takes one answer of the equalizer feature. True when what the Sound tab shows changed. */
    private fun eqFeed(packet: Gaia3Packet): Boolean {
        val m = model?.eq ?: return false
        if (packet.isNotification) {
            if (packet.key == Gaia3Packet.keyOf(SennheiserWire.VENDOR, 0x1009) && eqKind == SennheiserEqKind.GEN2 && packet.payload.isNotEmpty()) {
                eqBass = (packet.payload[0].toInt() and 0xFF) == 1
                return true
            }
            return false
        }
        val at = eqPending.indexOfFirst { it.key == packet.key }
        if (at < 0) return false
        val tag = eqPending.removeAt(at).tag
        val p = packet.payload.map { it.toInt() and 0xFF }
        if (packet.isError) {
            if (tag == "config") eqOutbox += legacyReads()
            return false
        }
        if (tag.isEmpty()) return false
        when {
            tag == "config" -> {
                if (p.size < 5) {
                    eqOutbox += legacyReads()
                    return false
                }
                gen2Bands = p[0]
                gen2Min = p[1].toByte().toInt() / 10.0
                gen2Max = p[2].toByte().toInt() / 10.0
                eqKind = SennheiserEqKind.GEN2
                eqOutbox += (0 until gen2Bands).map { eqRead(SennheiserCommands.EQ2_SLIDER, "slider$it", byteArrayOf(it.toByte())) }
                return false
            }
            tag == "sub" -> {
                eqBass = p.firstOrNull() == 1
                return eqKnown
            }
            tag.startsWith("slider") -> {
                val gain = p.firstOrNull()?.toByte()?.toInt() ?: return false
                gen2Read[tag.removePrefix("slider").toInt()] = gain / 10.0
                if (gen2Read.size < gen2Bands) return false
                eqGains = (0 until gen2Bands).map { gen2Read.getValue(it) }
                return true
            }
            tag == "knobY" -> {
                knobY = int16At(p, 0) ?: return false
                if (eqKind == null) {
                    // The app takes its five-band equalizer from the firmware its product file names (`Eq5Band_Gen1_MinFwVersion`); older firmware has the three knobs.
                    val min = SennheiserEq.version(m.gen1MinFirmware)
                    val have = SennheiserEq.version(firmware)
                    if (m.gen1 != null && have != null && min != null && SennheiserEq.atLeast(have, min)) {
                        eqKind = SennheiserEqKind.GEN1
                        eqOutbox += eqRead(SennheiserCommands.EQ1_LEVEL_SHIFT, "shift")
                        eqOutbox += eqRead(SennheiserCommands.EQ1_PARAMS, "params", shortList(SennheiserEq.SHELF_REFRESH))
                    } else if (m.threeBandLimit > 0) {
                        eqKind = SennheiserEqKind.THREE_BAND
                        eqOutbox += eqRead(SennheiserCommands.EQ1_KNOB, "knobX", u16(0))
                    }
                    return false
                }
                return if (eqKind == SennheiserEqKind.GEN1) decodeShelf() else false
            }
            tag == "knobX" -> {
                knobX = int16At(p, 0) ?: return false
                return decodeThree()
            }
            tag == "shift" -> {
                levelShift = (p.firstOrNull()?.toByte()?.toInt() ?: return false) / 10.0
                return decodeShelf()
            }
            tag == "params" -> {
                shelfReply = (0 until p.size / 2).map { int16At(p, it)!!.toShort().toInt() }
                return decodeShelf()
            }
        }
        return false
    }

    /** The reads of the older equalizers, once the newer one did not answer. */
    private fun legacyReads(): List<ByteArray> = if (knobAsked) emptyList() else listOf(askKnob())

    private fun decodeShelf(): Boolean {
        val p = shelf ?: return false
        val shift = levelShift ?: return false
        val y = knobY ?: return false
        val reply = shelfReply ?: return false
        val (gains, bass) = SennheiserEq.shelfGains(shift, y, reply, p) ?: return false
        eqGains = gains
        eqBass = bass
        return true
    }

    private fun decodeThree(): Boolean {
        val (gains, bass) = SennheiserEq.threeFromKnobs(knobX ?: return false, knobY ?: return false) ?: return false
        eqGains = gains
        eqBass = bass
        return true
    }

    /** The bands in Hz the Sound tab draws, for the equalizer the earbuds answered as; empty while none did. */
    override val eqBandsHz: List<Int>
        get() = when (eqKind) {
            SennheiserEqKind.GEN2 -> if (gen2Bands == SennheiserEqTables.GEN2_HZ.size) SennheiserEqTables.GEN2_HZ else List(gen2Bands) { it + 1 }
            SennheiserEqKind.GEN1 -> shelf?.hz.orEmpty()
            SennheiserEqKind.THREE_BAND -> SennheiserEqTables.THREE_BAND_HZ
            null -> emptyList()
        }

    /** The range of one band in dB: the earbuds' own for the newest equalizer, the app's six for the others. */
    override val eqMaxDb: Double
        get() = when (eqKind) {
            SennheiserEqKind.GEN2 -> gen2Max
            SennheiserEqKind.GEN1 -> (shelf?.rangeDb ?: 6).toDouble()
            else -> SennheiserEqTables.THREE_BAND_RANGE_DB.toDouble()
        }
    override val eqMinDb: Double get() = if (eqKind == SennheiserEqKind.GEN2) gen2Min else -eqMaxDb

    /** The presets the app offers the product, as curves over [eqBandsHz]. */
    override val eqPresets: List<Pair<String, List<Double>>>
        get() {
            val m = model?.eq ?: return emptyList()
            val key = if (eqKind == SennheiserEqKind.THREE_BAND) "default3" else m.presets
            return SennheiserEqTables.PRESETS[key].orEmpty().filter { it.second.size == eqBandsHz.size }
        }

    /** Whether the earbuds answered as an equalizer whose gains are known. */
    override val eqKnown: Boolean get() = eqKind != null && eqGains != null

    private fun setAudioMode(): List<ByteArray> {
        val audio = command(SennheiserCommands.AUDIO_MODE) ?: return emptyList()
        val b = values[SennheiserCommands.AUDIO_MODE] ?: return emptyList()
        val now = if (b.size >= 2) ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF) else b[0].toInt() and 0xFF
        // The equalizer only sounds in the user equalizer mode (`AudioMode`'s USEREQ); the app turns it on with the equalizer.
        if (now == AUDIO_USER_EQ) return emptyList()
        values[SennheiserCommands.AUDIO_MODE] = u16(AUDIO_USER_EQ)
        return listOf(frameOf(audio.vendor, audio.set!!, u16(AUDIO_USER_EQ)))
    }

    /** The messages that set the band [gains] (dB), or null while the earbuds have not said which equalizer they have. */
    override fun eqCurve(gains: List<Double>): List<ByteArray>? {
        val kind = eqKind ?: return null
        if (gains.size != eqBandsHz.size) return null
        val m = model?.eq ?: return null
        val clipped = gains.map { it.coerceIn(eqMinDb, eqMaxDb) }
        val out = mutableListOf<ByteArray>()
        out += setAudioMode()
        when (kind) {
            SennheiserEqKind.GEN2 -> {
                out += eqFrame(command(SennheiserCommands.EQ_SUBMODE)!!, true, byteArrayOf(if (eqBass) 1 else 0))
                val slider = command(SennheiserCommands.EQ2_SLIDER)!!
                clipped.forEachIndexed { i, g -> out += eqFrame(slider, true, byteArrayOf(i.toByte(), PMath.round(g * 10).toInt().toByte())) }
            }
            SennheiserEqKind.GEN1 -> {
                val p = shelf ?: return null
                val c = SennheiserEq.shelfConfig(clipped, p, eqBass)
                out += eqFrame(command(SennheiserCommands.EQ1_PARAMS)!!, true, shortList(SennheiserEq.shelfParams(c, p)))
                out += eqFrame(command(SennheiserCommands.EQ1_LEVEL_SHIFT)!!, true, byteArrayOf((10 * c.levelShift).toInt().toByte()))
                out += eqFrame(command(SennheiserCommands.EQ1_KNOB)!!, true, u16(1) + u16(SennheiserEq.shelfKnobY(eqBass)))
            }
            SennheiserEqKind.THREE_BAND -> {
                val t = clipped.toMutableList()
                if (eqBass) t[0] += SennheiserEqTables.THREE_BAND_BASS_BOOST
                val smooth = SennheiserEq.threeSmooth(t, m.threeBandLimit)
                out += eqFrame(command(SennheiserCommands.EQ1_PARAMS)!!, true, shortList(SennheiserEq.threeParams(smooth, eqBass)))
                val (x, y) = SennheiserEq.threeKnobs(clipped, eqBass)
                out += eqFrame(command(SennheiserCommands.EQ1_KNOB)!!, true, u16(0) + u16(x))
                out += eqFrame(command(SennheiserCommands.EQ1_KNOB)!!, true, u16(1) + u16(y))
            }
        }
        eqGains = clipped
        return out
    }

    private fun eqBassBoost(on: Boolean): List<ByteArray>? {
        val gains = eqGains ?: return null
        if (eqKind == null) return null
        eqBass = on
        // user-eq-2 holds bass boost as the sub-mode, written alone; the others carry it in the curve and the knob.
        if (eqKind == SennheiserEqKind.GEN2) return setAudioMode() + eqFrame(command(SennheiserCommands.EQ_SUBMODE)!!, true, byteArrayOf(if (on) 1 else 0))
        return eqCurve(gains)
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
        public const val KEY_BASS_BOOST: String = "sh_bass_boost"
        public const val KEY_SIDETONE: String = "sh_sidetone"
        public const val KEY_AUTO_OFF: String = "sh_auto_off"
        public const val KEY_BT_COMPAT: String = "sh_bt_compat"
        public const val KEY_TRANSPARENCY_SLIDER: String = "sh_transparency_slider"
        public const val KEY_TRANSPARENCY_LEVEL: String = "sh_transparency_level"
        public const val KEY_TAP_SENSITIVITY: String = "sh_tap_sensitivity"
        public const val KEY_OPEN_EARTIP: String = "sh_open_eartip"
        public const val KEY_LOSSLESS: String = "sh_lossless"
        public const val KEY_TOUCH_AREA: String = "sh_touch_area"
        public const val KEY_SAMPLE_RATE: String = "sh_sample_rate"
        public const val KEY_PROMPT_LANGUAGE: String = "sh_prompt_language"
        public const val KEY_WEAR: String = "sh_wear"
        public const val KEY_WEAR_LEFT: String = "sh_wear_left"
        public const val KEY_WEAR_RIGHT: String = "sh_wear_right"
        public const val KEY_TOUCH_DEFAULT: String = "sh_touch_default"
        public const val KEY_PODCAST: String = "sh_podcast"
        public const val KEY_PERSONALIZED: String = "sh_personalized"
        public const val KEY_PERSONALIZED_RESET: String = "sh_personalized_reset"
        public const val KEY_FIND_LEFT: String = "sh_find_left"
        public const val KEY_FIND_RIGHT: String = "sh_find_right"
        public const val KEY_FIT_TEST: String = "sh_fit_test"
        public const val KEY_FIT_LEFT: String = "sh_fit_left"
        public const val KEY_FIT_RIGHT: String = "sh_fit_right"
        public const val KEY_PEER_CONNECT: String = "sh_peer_connect"
        public const val KEY_PEER_DISCONNECT: String = "sh_peer_disconnect"
        public const val KEY_SENSORS: String = "sh_sensors"
        public const val KEY_HEART_RATE: String = "sh_heart_rate"
        public const val KEY_BODY_TEMPERATURE: String = "sh_body_temperature"
        public const val KEY_AURACAST_SCAN: String = "sh_auracast_scan"
        public const val KEY_AURACAST_ADD: String = "sh_auracast_add"
        public const val KEY_AURACAST_REMOVE: String = "sh_auracast_remove"
        public const val KEY_AURACAST_SOURCE: String = "sh_auracast_source"
        public const val KEY_AURACAST_CODE: String = "sh_auracast_code"
        public const val KEY_PEER_DELETE: String = "sh_peer_delete"
        private const val AURACAST_CODE_LENGTH = 16

        /** How long the earbuds scan for broadcasts (the app's `AURACAST_SCANNING_TIME_SECS`), and `BassSyncOperation`'s "synchronise without past info". */
        private const val AURACAST_SCAN_SECONDS = 5
        private const val PA_SYNC_WITHOUT_PAST = 2

        /** `AudioMode` values (`audio-mode-1` and `-2`): 2 is speech (the app's podcast mode), 3 the personalised sound. */
        private const val AUDIO_SPEECH = 2
        private const val AUDIO_PERSONALIZED = 3
        private const val PERSONALIZED_V1_ENABLED = 3
        private const val PERSONALIZED_V1_STANDBY = 2

        /** `FindDeviceRingSeparateDevice`'s sides ("Right Earbud - 0, Left Earbud - 1"). */
        private const val SIDE_RIGHT = 0
        private const val SIDE_LEFT = 1

        /** The app's sensor types (`Sensor_SensorData`). */
        private const val SENSOR_HEART_RATE = 0
        private const val SENSOR_BODY_TEMPERATURE = 1

        /** The connection status values of a paired device (`DISCONNECTED` 0, classic 1, LE 2, both 3; 128 is a flag; 254 and 255 no device). */
        private const val PEER_DISCONNECTED = 0
        private const val PEER_PAIRING_CANCELLED = 254
        private const val PEER_NO_DEVICE = 255

        /** The feature id of the equalizers (Sennheiser's `user_eq`). */
        internal const val FEATURE_USER_EQ: Int = 8

        /** The audio mode that turns the user equalizer on (`AudioMode`'s USEREQ). */
        private const val AUDIO_USER_EQ = 1

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
            KEY_BT_COMPAT to SennheiserCommands.BT_COMPAT,
            KEY_OPEN_EARTIP to SennheiserCommands.OPEN_EARTIP,
            KEY_TOUCH_AREA to SennheiserCommands.TOUCH_AREA,
            KEY_LOSSLESS to SennheiserCommands.LOSSLESS,
        )

        /** The gestures a product's earbuds are asked about, in the order the app reads them. */
        private val TOUCH_GESTURE_ORDER = listOf("single", "double", "triple", "long_press")

        /** The rows' keys for the gestures (the screens' shared touch rows call a long press "hold"). */
        private val TOUCH_KEY_GESTURE = mapOf("single" to "single", "double" to "double", "triple" to "triple", "long_press" to "hold")

        /** The words (option keys) the screens have for each function: the shared touch ones where they exist, else `sh_`. */
        private val TOUCH_OPTION = mapOf(
            "none" to "none", "play_pause" to "play_pause", "next_song" to "next", "previous_song" to "previous", "volume_up" to "volume_up", "volume_down" to "volume_down",
            "voice_assistant" to "voice_assistant", "anc" to "sh_anc", "transparent_hearing" to "sh_transparency", "toggle_lock" to "sh_lock",
            "toggle_open_ear_tip" to "sh_open_ear_tip", "anc_th_toggle" to "sh_anc_transparency", "toggle_bass_boost" to "bass_boost", "sound_mode_toggle" to "sh_sound_mode",
        )

        /** The three transparency levels the app's screens write for the earbuds with transparent hearing. */
        private val TRANSPARENCY_NAMES = linkedMapOf("low" to 1, "medium" to 50, "high" to 100)

        private val TAP_OPTIONS = listOf("low", "mid", "high")

        /** The app's `Setting_AudioPromptMode` values. */
        private val PROMPTS = linkedMapOf("tone_voice_off" to 0, "tone_on" to 1, "tone_voice_on" to 2)
    }
}

/** One entry of the earbuds' paired devices list: its index, the connection status the earbuds report (0 disconnected, 1 classic, 2 LE, 3 both) and its name. */
public class SennheiserPeer(public val index: Int, public val status: Int, public val name: String)

/**
 * A broadcast the earbuds found (Smart Control's `AuracastScanResult`): its name, the broadcaster's address and set, the broadcast id and whether it needs a code.
 * The layout is the app's: the signal strength, the name's length and bytes, the address type, six address bytes, the set, three id bytes and the encryption flag.
 */
public class AuracastBroadcast(public val name: String, public val addressType: Int, public val address: ByteArray, public val advSid: Int, public val broadcastId: ByteArray, public val encrypted: Boolean) {
    public val addressKey: String get() = address.joinToString(":") { it.toHex2().uppercase() }
    public val label: String get() = name.ifEmpty { addressKey }

    public companion object {
        public fun of(p: ByteArray): AuracastBroadcast? {
            if (p.size < 2) return null
            val len = p[1].toInt() and 0xFF
            if (p.size < 14 + len) return null
            val name = p.copyOfRange(2, 2 + len).decodeToString()
            return AuracastBroadcast(name, p[2 + len].toInt() and 0xFF, p.copyOfRange(3 + len, 9 + len), p[9 + len].toInt() and 0xFF, p.copyOfRange(10 + len, 13 + len), (p[13 + len].toInt() and 0xFF) != 0)
        }
    }
}

/** A source the earbuds receive a broadcast from (`BassReceiveState`): its id, the broadcaster's address and the synchronisation and encryption states. */
public class AuracastSource(public val sourceId: Int, public val address: ByteArray, public val paSync: Int, public val encryption: Int, public val playing: Boolean) {
    public val addressKey: String get() = address.joinToString(":") { it.toHex2().uppercase() }

    /** The state as a key for the screens: receiving, synced, not synced, code required or bad code. */
    public val state: String get() = when {
        encryption == 1 -> "code_required"
        encryption == 3 -> "bad_code"
        playing -> "receiving"
        paSync == 2 -> "synced"
        paSync == 3 -> "failed"
        else -> "not_synced"
    }

    internal fun same(o: AuracastSource): Boolean = sourceId == o.sourceId && address.contentEquals(o.address) && paSync == o.paSync && encryption == o.encryption && playing == o.playing

    public companion object {
        public fun of(p: ByteArray): AuracastSource? {
            if (p.size < 14) return null
            val encryption = p[13].toInt() and 0xFF
            // After the encryption state a wrong code adds the 16 bytes of the code that failed; then the number of subgroups and each one's synchronisation bits.
            var at = if (encryption == 3) 30 else 14
            if (p.size <= at) return AuracastSource(p[0].toInt() and 0xFF, p.copyOfRange(2, 8), p[12].toInt() and 0xFF, encryption, false)
            val groups = p[at].toInt() and 0xFF
            at += 1
            var playing = false
            for (g in 0 until groups) {
                val from = at + g
                val to = minOf(p.size, from + 4)
                if (from < to && p.copyOfRange(from, to).any { it.toInt() != 0 }) playing = true
            }
            return AuracastSource(p[0].toInt() and 0xFF, p.copyOfRange(2, 8), p[12].toInt() and 0xFF, encryption, playing)
        }
    }
}
