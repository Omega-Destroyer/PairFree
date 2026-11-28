package io.github.omegadestroyer.pairfree.protocol.huawei

import io.github.omegadestroyer.pairfree.protocol.BandEq
import io.github.omegadestroyer.pairfree.protocol.BandEqReport
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.FitResult
import io.github.omegadestroyer.pairfree.protocol.FitTest
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value

/** One AI Life MBB conversation. Settings become writable after the matching model returns their read. */
public class HuaweiTalk(private val product: HuaweiProduct, clock: () -> HuaweiClock? = { null }) :
    SessionTalk<HuaweiPacket>(), BandEq, FitTest {
    private val parser = HuaweiParser()
    private val controls = product.controls()
    private val pinch = if (product.has("pinch")) HuaweiPinch.entries.filter { it != HuaweiPinch.HOLD || product.has("pinch_hold") } else emptyList()
    private val pinchReports = mutableMapOf<HuaweiPinch, HuaweiPacket>()
    private val features = HuaweiFeature.entries.filter { product.has(it.feature) }
    private val featureStates = mutableMapOf<HuaweiFeature, HuaweiPacket>()
    private val ringing = mutableMapOf<Int, Boolean>()
    private val reports = mutableMapOf<Int, HuaweiPacket>()
    private val values = mutableMapOf<Command, Value>()
    private var readBacks = emptyList<HuaweiPacket>()
    private var refreshNoise = false

    private val batteryRead = if (product.has("modern_battery") || product.id == product.name) huaweiRead(HuaweiCommand.BATTERY, 1, 2, 3) else HuaweiReads.battery()
    private val noiseRead = when {
        product.has("anc") -> huaweiRead(HuaweiCommand.NOISE_READ, 1)
        product.has("legacy_anc") -> huaweiRead(HuaweiCommand.LEGACY_NOISE_READ, 1)
        else -> null
    }
    private val reportOnly = product.has("report_only")
    private val peers = HuaweiPeers()
    private var refreshPeers = false
    private var fit: FitResult? = null
    private var fitLeft: FitResult.Fit? = null
    private var fitRight: FitResult.Fit? = null
    private var fitRunning = false
    private var fitDone = false
    private var lights: ByteArray? = null
    private var cycles: Pair<Int, Int>? = null
    private val scenes = if (product.has("smart_scenario") && !reportOnly) HuaweiScenes(clock) else null

    private val opening = buildList {
        add(batteryRead)
        add(huaweiRead(HuaweiCommand.VERSION, 7))
        // A catalogue model has no profile: ask what the others answer, for its device report. Reads only; nothing is shown or written.
        if (reportOnly) addAll(PROBE.map { (op, tags) -> huaweiRead(op, *tags) })
        noiseRead?.let(::add)
        if (product.has("anc") && product.has("legacy_anc")) add(huaweiRead(HuaweiCommand.LEGACY_NOISE_READ, 1))
        if (product.has("legacy_index")) add(huaweiRead(HuaweiCommand.LEGACY_INDEX_READ, 1))
        for ((read, rows) in controls.groupBy(HuaweiControl::read)) {
            val tags = when (read) {
                HuaweiCommand.DOUBLE_TAP_READ -> when {
                    product.has("eyewear") -> intArrayOf(3)
                    product.has("walrus_tap") || product.has("power_tap_list") -> intArrayOf(1, 2, 3)
                    else -> intArrayOf(1, 2)
                }
                HuaweiCommand.SHORT_PRESS_READ -> intArrayOf(1, 2, 3)
                HuaweiCommand.SWIPE_READ -> if (product.has("fiji")) intArrayOf(1, 2, 3) else intArrayOf(1, 2)
                HuaweiCommand.TRIPLE_TAP_READ, HuaweiCommand.HOLD_CYCLE_READ -> intArrayOf(1, 2)
                HuaweiCommand.HOLD_READ -> if (product.has("walrus_hold") || product.has("single_hold") ||
                    product.has("fiji") || product.has("mermaid_hold")) intArrayOf(1, 2, 3) else intArrayOf(1, 2)
                else -> rows.flatMap(HuaweiControl::readTags).distinct().toIntArray()
            }
            // The voice wake-up read asks with a value, as the maker's app does.
            add(if (read == HuaweiCommand.VOICE_RECOGNITION_READ) huaweiWrite(read, 1 to 0) else huaweiRead(read, *tags))
        }
        addAll(features.map(HuaweiFeature::read))
        if (product.has("soh")) add(CYCLES_READ)
        if (product.has("light")) add(huaweiRead(HuaweiCommand.LIGHT, 1))
        if (product.has("pair_new")) add(huaweiRead(HuaweiCommand.PAIR_NEW_READ, 1))
        addAll(pinch.map(HuaweiPinch::read))
        if (product.has("smart_scenario") && !reportOnly) {
            add(huaweiRead(HuaweiCommand.LOCAL_ADDRESS, 3))
            add(HuaweiScenes.GAME_QUERY)
            add(HuaweiScenes.SLEEP_QUERY)
        }
        if (product.has("find")) addAll((0..1).map { huaweiWrite(HuaweiCommand.RING_STATE, 1 to it) })
        if (product.has("language")) add(huaweiRead(HuaweiCommand.LANGUAGE_READ, 1, 2, 3))
        if (product.mayHaveEq && !product.has("eq_write_only")) add(huaweiRead(HuaweiCommand.EQ_READ, 2))
        if (product.has("multipoint")) add(huaweiRead(HuaweiCommand.PEER_LIST, 1))
    }

    override val pacing: Pacing = Pacing(replyMs = 650, gapMs = 35)
    override val state: EarbudState
        get() = EarbudState(values.toMap(), noiseModeValues = product.noiseModes, fit = fit, fitTest = hasFit, fitSound = hasFit)

    private val hasFit: Boolean get() = product.has("fit") && !reportOnly
    override val fitTest: FitTest? get() = if (hasFit) this else null

    /** The maker's app plays a sound, then starts the earbuds' own check; each earbud's answer comes on its own (`2B 27`). */
    override fun startFitTest(): List<ByteArray>? {
        if (!hasFit) return null
        fit = null
        fitLeft = null
        fitRight = null
        fitRunning = true
        return listOf(fitStart.bytes())
    }

    private fun fitResult(value: Int) {
        if (!fitRunning) return
        when (value) {
            0 -> fitLeft = FitResult.Fit.GOOD
            1 -> fitLeft = FitResult.Fit.NOT_FIT
            2 -> fitRight = FitResult.Fit.GOOD
            3 -> fitRight = FitResult.Fit.NOT_FIT
            4 -> fitLeft = FitResult.Fit.UNKNOWN
            5 -> fitRight = FitResult.Fit.UNKNOWN
            else -> return
        }
        val left = fitLeft ?: return
        val right = fitRight ?: return
        fit = FitResult(left, right)
        fitRunning = false
        fitDone = true
    }
    override val groups: List<SettingGroup>
        get() = buildList {
            for ((tab, rows) in controls.groupBy(HuaweiControl::tab)) {
                val items = rows.mapNotNull { current -> reports[current.read]?.let(current::row) }
                if (items.isNotEmpty()) add(SettingGroup(if (tab == SettingTab.CONTROLS) "touch" else "other", tab, items))
            }
            if (pinchReports.isNotEmpty()) add(SettingGroup("hw_pinch", SettingTab.CONTROLS,
                pinch.flatMap { slot -> pinchReports[slot]?.let(slot::rows).orEmpty() }))
            val switches = features.mapNotNull { feature ->
                featureStates[feature]?.scalar(2)?.let { value ->
                    if (feature.choices.isEmpty()) Setting.Toggle(feature.row, value == 1)
                    else Setting.Choice(feature.row, feature.choices[value], feature.choices.values.toList())
                }
            }
            val headActions = HEAD_ACTIONS.mapNotNull { (key, tag) ->
                featureStates[HuaweiFeature.HEAD]?.takeIf { it.scalar(2) == 1 }?.scalar(tag)?.let {
                    Setting.Choice(key, HEAD_CHOICES[it], HEAD_CHOICES.values.toList())
                }
            }
            val scene = featureStates[HuaweiFeature.SPATIAL]?.takeIf { product.has("spatial_scene") }?.scalar(3)
                ?.takeIf { it in SCENES }?.let { Setting.Choice(SCENE, SCENES[it], SCENES.values.toList()) }
            if (switches.isNotEmpty()) add(SettingGroup("other", SettingTab.HOME, switches + headActions + listOfNotNull(scene)))
            if (ringing.isNotEmpty()) add(SettingGroup("hw_find", SettingTab.DEVICE, listOfNotNull(
                ringing[0]?.let { Setting.Action("hw_ring_left", it) },
                ringing[1]?.let { Setting.Action("hw_ring_right", it) },
            )))
            val noise = buildList {
                val mode = state.number(Command.ANC_MODE)
                val index = legacyIndex()
                if (mode == 1 && index != null) add(Setting.Level("hw_anc_adjust", index, 0, 8))
                val levels = noiseLevels(mode)
                if (levels.isNotEmpty()) reports[HuaweiCommand.NOISE_READ]?.field(1)?.getOrNull(0)?.let {
                    add(Setting.Choice(if (mode == 1) "noise_strength" else "transparency_mode", levels[it.toInt() and 0xff], levels.values.toList()))
                }
                awarenessLevel()?.let { add(Setting.Level("hw_awareness_level", it, 0, 10)) }
            }
            if (noise.isNotEmpty()) add(SettingGroup("noise", SettingTab.HOME, noise))
            val saved = deletable()
            if (saved.isNotEmpty()) add(SettingGroup("hw_saved_sounds", SettingTab.SOUND,
                listOf(Setting.Choice(DELETE_SOUND, null, saved.keys.toList()))))
            lights?.let {
                add(SettingGroup("light", SettingTab.DEVICE, listOf(
                    Setting.Toggle(LIGHT_CALL, it[1].toInt() == 0), Setting.Toggle(LIGHT_MEDIA, it[3].toInt() == 0))))
            }
            cycles?.let { (left, right) ->
                add(SettingGroup("hw_battery_health", SettingTab.DEVICE, listOf(
                    Setting.Information("hw_cycles_left", left.toString()), Setting.Information("hw_cycles_right", right.toString()))))
            }
            val sensitivity = featureStates[HuaweiFeature.ADAPTIVE_VOLUME]?.takeIf { it.scalar(2) == 1 }?.scalar(3)
            if (sensitivity != null && sensitivity in SENSITIVITIES) add(SettingGroup("other", SettingTab.SOUND,
                listOf(Setting.Choice(SENSITIVITY, SENSITIVITIES[sensitivity], SENSITIVITIES.values.toList()))))
            if (HuaweiCommand.PAIR_NEW_READ in reports) add(SettingGroup("other", SettingTab.DEVICE, listOf(Setting.Action(PAIR_NEW))))
            val modes = scenes?.rows().orEmpty()
            if (modes.isNotEmpty()) add(SettingGroup("hw_scenario", SettingTab.HOME, modes))
            val hosts = peers.rows()
            if (hosts.isNotEmpty()) add(SettingGroup("hw_peers", SettingTab.DEVICE, hosts))
            val languages = languages()
            if (languages.isNotEmpty()) {
                val current = reports[HuaweiCommand.LANGUAGE_READ]?.field(1)?.decodeToString()?.trimEnd('\u0000')?.takeIf { it in languages }
                add(SettingGroup("hw_voice", SettingTab.DEVICE, listOf(Setting.Choice("hw_language", current, languages))))
            }
        }

    /** The last answer to each of PairFree's reads, by command and the read's own selectors. */
    private val answered = LinkedHashMap<String, HuaweiPacket>()

    /**
     * The device report's lines: command, then each field as tag=bytes. Only answers to PairFree's own reads are kept, none of
     * which names a device: of the version reply the version number alone, and the custom equalizer slots with their names blanked.
     */
    public val reportLines: List<String> get() = answered.map { (key, packet) ->
        "$key: " + packet.fields.joinToString(" ") { field ->
            "${hex(field.tag)}=" + reported(packet, field).joinToString("") { hex(it.toInt() and 0xff) }
        }
    }

    private fun hex(value: Int): String = value.toString(16).padStart(2, '0')

    private fun reported(packet: HuaweiPacket, field: HuaweiTlv): ByteArray = when {
        // The two modes' reports carry the address of the phone that started them.
        isScene(packet) && field.tag == 4 -> byteArrayOf()
        // The battery cycle reply also carries each battery's serial number.
        packet.op == HuaweiFeature.OP && packet.scalar(1) == CYCLES_KEY && field.tag > 3 -> byteArrayOf()
        packet.op == HuaweiCommand.VERSION -> if (field.tag == 7) field.value.copyOf(minOf(field.value.size, 24)) else byteArrayOf()
        packet.op == HuaweiCommand.EQ_READ && field.tag == 8 -> HuaweiCustomEq.unnamed(field.value)
        else -> field.value.copyOf()
    }

    private fun remember(packet: HuaweiPacket) {
        // The paired devices' list holds other devices' names and addresses; the other reply is the phone's own address.
        if (packet.op == HuaweiCommand.PEER_LIST || packet.op == HuaweiCommand.LOCAL_ADDRESS) return
        if (opening.none { it.op == packet.op } && packet.op != HuaweiCommand.BATTERY_NOTIFY) return
        val selectors = when (packet.op) {
            HuaweiFeature.OP, HuaweiCommand.PINCH_READ ->
                packet.fields.filter { it.tag == 1 || (it.tag == 2 && (packet.op == HuaweiCommand.PINCH_READ || isScene(packet))) }
                    .joinToString("") { field -> "." + field.value.joinToString("") { hex(it.toInt() and 0xff) } }
            else -> ""
        }
        answered[packet.op.toString(16).padStart(4, '0') + selectors] = packet
    }

    override fun start(): List<HuaweiPacket> = opening
    override fun poll(): List<HuaweiPacket> = listOf(batteryRead) + scenes?.heartbeat().orEmpty()
    private fun isScene(packet: HuaweiPacket): Boolean = packet.op == HuaweiFeature.OP && packet.scalar(1) == HuaweiScenes.KEY
    override fun bytes(message: HuaweiPacket): ByteArray =
        message.bytes(emptyLastTlvDeclaresValue = message.op in BARE_READS && isRead(message))
    override fun answers(sent: HuaweiPacket, got: Any): Boolean {
        if (got !is HuaweiPacket) return false
        if (sent.op == HuaweiCommand.BATTERY && got.op == HuaweiCommand.BATTERY_NOTIFY) return true
        if (sent.op != got.op) return false
        return when (sent.op) {
            HuaweiFeature.OP -> sent.scalar(1) == got.scalar(1) && (!isScene(sent) || sent.scalar(2) == got.scalar(2))
            HuaweiPinch.READ -> sent.scalar(1) == got.scalar(1) && sent.scalar(2) == got.scalar(2)
            HuaweiCommand.RING_STATE -> sent.scalar(1) == got.field(2)?.firstOrNull()?.toInt()
            else -> true
        }
    }

    private fun isRead(message: HuaweiPacket): Boolean = opening.any { read ->
        read.op == message.op && read.fields.size == message.fields.size &&
            read.fields.zip(message.fields).all { (a, b) -> a.tag == b.tag && a.value.contentEquals(b.value) }
    }

    override fun replyMs(message: HuaweiPacket): Long = if (isRead(message)) pacing.replyMs else 0

    override fun allowed(message: HuaweiPacket): Boolean =
        message.op !in HuaweiNever.ALL && message.service !in HuaweiNeverservice.ALL &&
            (isRead(message) || (!reportOnly && (isEqWrite(message) || isFitMessage(message) ||
                scenes?.allows(message) == true || permittedSettingsWrite(message))))

    private fun isFitMessage(message: HuaweiPacket): Boolean = hasFit && message.op == HuaweiCommand.FIT_SET &&
        message.fields.size == 1 && message.fields[0].tag in setOf(1, 3) && message.fields[0].value.isEmpty()

    override fun feed(bytes: ByteArray): Fed {
        val packets = parser.feed(bytes)
        var changed = false
        for (packet in packets) {
            val op = packet.op
            remember(packet)
            if (reportOnly && op != HuaweiCommand.BATTERY && op != HuaweiCommand.BATTERY_NOTIFY && op != HuaweiCommand.VERSION) continue
            if (op == HuaweiCommand.BATTERY || op == HuaweiCommand.BATTERY_NOTIFY) {
                if (updateBattery(packet)) {
                    reports[op] = packet
                    changed = true
                }
            } else if (op == HuaweiCommand.LOCAL_ADDRESS || isScene(packet)) {
                if (scenes?.receive(packet) == true) changed = true
            } else if (op == HuaweiFeature.OP && packet.scalar(1) == CYCLES_KEY) {
                val left = packet.field(2)?.takeIf { it.size == 2 }
                val right = packet.field(3)?.takeIf { it.size == 2 }
                if (product.has("soh") && left != null && right != null) {
                    cycles = number(left) to number(right)
                    changed = true
                }
            } else if (op == HuaweiCommand.LIGHT) {
                val value = packet.field(1)
                if (product.has("light") && value?.size == 4 && value[0].toInt() == 0 && value[2].toInt() == 1) {
                    lights = value.copyOf()
                    changed = true
                }
            } else if (op == HuaweiFeature.OP) {
                val feature = features.firstOrNull { it.code == packet.scalar(1) }
                val value = packet.scalar(2)
                if (feature != null && feature.accepts(value)) {
                    featureStates[feature] = packet
                    changed = true
                }
            } else if (op == HuaweiPinch.READ) {
                val slot = pinch.firstOrNull { it.matches(packet) }
                if (slot != null && slot.rows(packet).isNotEmpty()) {
                    pinchReports[slot] = packet
                    changed = true
                }
            } else if (op == HuaweiCommand.RING_STATE && product.has("find")) {
                val data = packet.field(2)
                if (data?.size == 2 && data[0].toInt() in 0..1 && data[1].toInt() in 0..1) {
                    ringing[data[0].toInt()] = data[1].toInt() == 0
                    changed = true
                }
            } else if (op == HuaweiCommand.PEER_LIST && product.has("multipoint")) {
                if (peers.receive(packet)) changed = true
            } else if (op == HuaweiCommand.FIT_RESULT && hasFit) {
                packet.scalar(1)?.let { fitResult(it); changed = true }
            } else if (op == HuaweiCommand.PEER_EVENT && product.has("multipoint")) {
                refreshPeers = true
            } else if (op == HuaweiCommand.NOISE_EVENT && noiseRead != null && packet.scalar(1) in product.noiseModes) {
                refreshNoise = true
            } else if (opening.any { it.op == op } && accept(packet)) {
                reports[op] = if (op == HuaweiCommand.VERSION) packet.copy(fields = emptyList()) else packet
                changed = true
            }
        }
        return Fed(changed, packets)
    }

    private fun number(data: ByteArray): Int = (data[0].toInt() and 0xff) shl 8 or (data[1].toInt() and 0xff)

    private fun accept(packet: HuaweiPacket): Boolean = when (packet.op) {
        HuaweiCommand.PAIR_NEW_READ -> product.has("pair_new") && packet.fields.isNotEmpty()
        HuaweiCommand.VERSION -> {
            val raw = packet.field(7)?.takeIf { it.size <= 40 }?.decodeToString()?.trimEnd('\u0000')
            val version = raw?.let(VERSION_NUMBER::find)?.value
            if (version == null) false else {
                values[Command.FIRMWARE_VERSION] = Value.Text(version)
                true
            }
        }
        HuaweiCommand.NOISE_READ -> packet.field(1)?.takeIf { it.size == 2 }?.get(1)?.toInt()?.let(::setNoiseReported) == true
        HuaweiCommand.LEGACY_NOISE_READ -> reports[HuaweiCommand.NOISE_READ] == null && packet.scalar(1)?.let(::setNoiseReported) == true
        HuaweiCommand.LEGACY_INDEX_READ -> legacyIndex(packet) != null
        HuaweiCommand.LANGUAGE_READ -> packet.field(3)?.isNotEmpty() == true
        HuaweiCommand.EQ_READ -> packet.scalar(2) != null && packet.scalar(1) != 0
        else -> controls.any { it.read == packet.op && it.row(packet) != null }
    }

    private fun setNoiseReported(mode: Int): Boolean {
        if (mode !in product.noiseModes) return false
        values[Command.ANC_MODE] = Value.Number(mode)
        return true
    }

    private fun updateBattery(packet: HuaweiPacket): Boolean {
        val data = HuaweiBattery.of(packet) ?: return false
        if (data.global == null && data.left == null && data.right == null && data.case == null) return false
        fun show(command: Command, value: Int?) {
            if (value == null) values.remove(command) else values[command] = Value.Number(value)
        }
        if (product.has("eyewear") && packet.field(2)?.size == 3) {
            show(Command.BATTERY_LEFT, listOfNotNull(data.left, data.right).minOrNull() ?: data.global)
            if (product.has("eyewear_case")) show(Command.BATTERY_CASE, data.case)
        } else if (product.form == Form.EARBUDS && packet.field(2)?.size == 3) {
            show(Command.BATTERY_LEFT, data.left)
            show(Command.BATTERY_RIGHT, data.right)
            show(Command.BATTERY_CASE, data.case)
        } else if (data.global != null && Command.BATTERY_RIGHT !in values) {
            show(Command.BATTERY_LEFT, data.global)
        }
        return true
    }

    private fun noiseLevels(mode: Int?): Map<Int, String> = when {
        mode == 1 && product.has("alt_levels") -> mapOf(1 to "hw_dynamic", 0 to "hw_cozy", 2 to "hw_general", 3 to "hw_ultra")
        mode == 1 && product.has("anc_levels") -> buildMap {
            if (product.has("dynamic")) put(3, if (product.name == "FreeBuds Pro 5") "hw_dual_engine" else "hw_dynamic")
            put(1, "hw_cozy")
            put(0, "hw_general")
            if (!product.has("limited_levels")) put(2, "hw_ultra")
        }
        mode == 2 && product.has("voice") -> buildMap {
            put(2, "hw_general")
            put(1, "hw_voice_boost")
            if (product.has("adaptive_awareness")) put(4, "hw_adaptive_awareness")
        }
        else -> emptyMap()
    }

    private fun awarenessLevel(): Int? {
        if (!product.has("adaptive_awareness") || state.number(Command.ANC_MODE) != 2) return null
        val report = reports[HuaweiCommand.NOISE_READ] ?: return null
        if (report.field(1)?.firstOrNull()?.toInt() != 4) return null
        return report.scalar(2)?.takeIf { it in 0..10 }
    }

    private fun legacyIndex(packet: HuaweiPacket? = reports[HuaweiCommand.LEGACY_INDEX_READ]): Int? {
        val mode = packet?.scalar(1)
        val tag = when (mode) { 0 -> 2; 1 -> 3; else -> return null }
        return packet.scalar(tag)?.takeIf { it in 0..8 }
    }

    private fun languages(): List<String> = reports[HuaweiCommand.LANGUAGE_READ]?.field(3)?.decodeToString()
        ?.substringBefore('\u0000')?.split(',')?.map(String::trim)?.filter { LOCALE.matches(it) }?.distinct().orEmpty()

    override fun takeOutbox(): List<HuaweiPacket> {
        val ops = buildSet {
            if (refreshNoise) addAll(listOf(HuaweiCommand.NOISE_READ, HuaweiCommand.LEGACY_NOISE_READ))
            if (refreshPeers) add(HuaweiCommand.PEER_LIST)
        }
        refreshNoise = false
        refreshPeers = false
        // Both earbuds have answered: end the check, as the maker's app does.
        val end = if (fitDone) listOf(fitEnd) else emptyList()
        fitDone = false
        return end + opening.filter { it.op in ops }
    }

    override fun takeReadBacks(): List<HuaweiPacket> = readBacks.also { readBacks = emptyList() }

    override fun set(command: Command, value: Int): List<HuaweiPacket>? {
        val read = activeNoiseRead() ?: return null
        if (command != Command.ANC_MODE || !state.supports(command) || value !in product.noiseModes) return null
        val data = if (read == HuaweiCommand.NOISE_READ) byteArrayOf(value.toByte(), 0xff.toByte()) else byteArrayOf(value.toByte())
        return respond(huaweiPacket(HuaweiCommand.NOISE_SET, listOf(HuaweiTlv(1, data))), read)
    }

    override fun change(change: SettingChange): List<HuaweiPacket>? {
        val key = change.key
        val mode = state.number(Command.ANC_MODE)
        scenes?.change(change)?.let { writes ->
            if (writes.isEmpty()) return writes
            val scene = writes.first().scalar(2)
            // Before the sleep mode starts, the app sends the noise mode it should run with: here the one in use.
            val noise = if (scenes.startsSleep(change) && mode != null) set(Command.ANC_MODE, mode).orEmpty() else emptyList()
            val back = opening.filter { isScene(it) && it.scalar(2) == scene }
            readBacks = if (noise.isEmpty()) back else readBacks + back
            return noise + writes
        }
        if (change is SettingChange.Toggle && (key == LIGHT_CALL || key == LIGHT_MEDIA)) {
            val now = lights ?: return null
            val value = if (change.on) 0 else 1
            val call = if (key == LIGHT_CALL) value else now[1].toInt()
            val media = if (key == LIGHT_MEDIA) value else now[3].toInt()
            readBacks = opening.filter { it.op == HuaweiCommand.LIGHT }
            return listOf(lightWrite(call, media))
        }
        if (change is SettingChange.Press && key == PAIR_NEW) {
            return if (HuaweiCommand.PAIR_NEW_READ in reports) listOf(pairNew) else null
        }
        if (change is SettingChange.Choice && key == SENSITIVITY) {
            if (featureStates[HuaweiFeature.ADAPTIVE_VOLUME]?.scalar(3) == null) return null
            val value = SENSITIVITIES.entries.firstOrNull { it.value == change.option }?.key ?: return null
            readBacks = listOf(HuaweiFeature.ADAPTIVE_VOLUME.read())
            return listOf(huaweiWrite(HuaweiFeature.OP, 1 to HuaweiFeature.ADAPTIVE_VOLUME.code, 3 to value))
        }
        if (change is SettingChange.Choice && key == DELETE_SOUND) {
            val slot = deletable()[change.option] ?: return null
            readBacks = eqReadBack()
            return listOf(HuaweiCustomEq.delete(HuaweiCommand.EQ_SET, slot))
        }
        if (change is SettingChange.Choice && key in HuaweiPeers.KEYS) {
            val write = peers.change(key, change.option) ?: return null
            // The change shows in the list a moment later; the earbuds also say when it has.
            readBacks = opening.filter { it.op == HuaweiCommand.PEER_LIST }
            return listOf(write)
        }
        if (change is SettingChange.Choice && key == SCENE) {
            if (!product.has("spatial_scene") || featureStates[HuaweiFeature.SPATIAL]?.scalar(3) == null) return null
            val value = SCENES.entries.firstOrNull { it.value == change.option }?.key ?: return null
            readBacks = listOf(HuaweiFeature.SPATIAL.read())
            return listOf(huaweiWrite(HuaweiFeature.OP, 1 to HuaweiFeature.SPATIAL.code, 3 to value))
        }
        if (change is SettingChange.Choice) {
            val feature = features.firstOrNull { it.row == key && it in featureStates && it.choices.isNotEmpty() }
            val value = feature?.choices?.entries?.firstOrNull { it.value == change.option }?.key
            if (feature != null && value != null) {
                readBacks = listOf(feature.read())
                return listOf(feature.write(value))
            }
        }
        if (change is SettingChange.Toggle) {
            val feature = features.firstOrNull { it.row == key && it in featureStates && it.choices.isEmpty() }
            if (feature != null) {
                readBacks = listOf(feature.read())
                return listOf(feature.write(change.on))
            }
        }
        if (change is SettingChange.Press) {
            val side = when (key) { "hw_ring_left" -> 0; "hw_ring_right" -> 1; else -> return null }
            val isRinging = ringing[side] ?: return null
            readBacks = listOf(huaweiWrite(HuaweiCommand.RING_STATE, 1 to side))
            return listOf(huaweiPacket(HuaweiCommand.RING, listOf(HuaweiTlv(1, byteArrayOf(side.toByte(), if (isRinging) 1 else 0)))))
        }
        if (change is SettingChange.Level && key == "hw_anc_adjust" && legacyIndex() != null && mode == 1 && change.value in 0..8) {
            return respond(huaweiWrite(HuaweiCommand.LEGACY_INDEX_SET, 1 to change.value), HuaweiCommand.LEGACY_INDEX_READ)
        }
        if (change is SettingChange.Level && key == "hw_awareness_level" && awarenessLevel() != null && change.value in 0..10) {
            return respond(huaweiPacket(HuaweiCommand.NOISE_SET, listOf(
                HuaweiTlv(1, byteArrayOf(2, 4)), HuaweiTlv(3, byteArrayOf(1)), HuaweiTlv(4, byteArrayOf(change.value.toByte())),
            )), HuaweiCommand.NOISE_READ)
        }
        if (change is SettingChange.Choice) {
            HEAD_ACTIONS[key]?.let { tag ->
                val head = featureStates[HuaweiFeature.HEAD]?.takeIf { it.scalar(2) == 1 } ?: return null
                if (head.scalar(tag) == null) return null
                val action = HEAD_CHOICES.entries.firstOrNull { it.value == change.option }?.key ?: return null
                readBacks = listOf(HuaweiFeature.HEAD.read())
                return listOf(huaweiWrite(HuaweiFeature.OP, 1 to HuaweiFeature.HEAD.code, tag to action))
            }
            val slot = pinch.firstOrNull { it.keys.contains(key) }
            if (slot != null) {
                val report = pinchReports[slot] ?: return null
                if (slot.rows(report).none { it.key == key }) return null
                val value = slot.choices.entries.firstOrNull { it.value == change.option }?.key ?: return null
                readBacks = listOf(slot.read())
                return listOfNotNull(slot.write(key, value))
            }
            if (key == "hw_language" && change.option in languages()) {
                return respond(languageWrite(change.option), HuaweiCommand.LANGUAGE_READ)
            }
            if ((key == "noise_strength" && mode == 1) || (key == "transparency_mode" && mode == 2)) {
                if (reports[HuaweiCommand.NOISE_READ] == null) return null
                val level = noiseLevels(mode).entries.firstOrNull { it.value == change.option }?.key ?: return null
                return respond(huaweiPacket(HuaweiCommand.NOISE_SET, listOf(HuaweiTlv(1, byteArrayOf(mode.toByte(), level.toByte())))), HuaweiCommand.NOISE_READ)
            }
        }
        val control = controls.firstOrNull { it.key == key } ?: return null
        val report = reports[control.read] ?: return null
        val row = control.row(report) ?: return null
        val value = when {
            row is Setting.Toggle && change is SettingChange.Toggle -> if (change.on) 1 else 0
            row is Setting.Choice && change is SettingChange.Choice -> control.options(report).entries.firstOrNull { it.value == change.option }?.key
            else -> null
        } ?: return null
        return respond(huaweiWrite(control.write, *control.writeTags.map { it to value }.toTypedArray()), control.read)
    }

    private fun languageWrite(locale: String): HuaweiPacket = huaweiPacket(HuaweiCommand.LANGUAGE_SET, buildList {
        add(HuaweiTlv(1, locale.encodeToByteArray()))
        if (!product.has("eyewear")) add(HuaweiTlv(2, byteArrayOf(1)))
    })

    private fun respond(write: HuaweiPacket, read: Int): List<HuaweiPacket> {
        readBacks = opening.filter { it.op == read }
        return listOf(write)
    }

    private fun activeNoiseRead(): Int? = when {
        HuaweiCommand.NOISE_READ in reports -> HuaweiCommand.NOISE_READ
        HuaweiCommand.LEGACY_NOISE_READ in reports -> HuaweiCommand.LEGACY_NOISE_READ
        else -> null
    }

    private fun permittedSettingsWrite(packet: HuaweiPacket): Boolean {
        val mode = state.number(Command.ANC_MODE)
        return when (packet.op) {
            HuaweiCommand.PEER_PREFERRED, HuaweiCommand.PEER_EXECUTE -> peers.allows(packet)
            HuaweiCommand.LIGHT -> lights != null && packet.fields.size == 1 && packet.field(2)?.let {
                it.size == 4 && it[0].toInt() == 0 && it[1].toInt() in 0..1 && it[2].toInt() == 1 && it[3].toInt() in 0..1
            } == true
            HuaweiCommand.PAIR_NEW -> HuaweiCommand.PAIR_NEW_READ in reports && packet.bytes().contentEquals(pairNew.bytes())
            HuaweiCommand.NOISE_SET -> {
                val data = packet.field(1) ?: return false
                if (packet.fields.map(HuaweiTlv::tag) == listOf(1, 3, 4)) {
                    return awarenessLevel() != null && data.contentEquals(byteArrayOf(2, 4)) &&
                        packet.scalar(3) == 1 && packet.scalar(4) in 0..10
                }
                if (packet.fields.size != 1 || mode == null || data.isEmpty()) return false
                val next = data[0].toInt()
                next in product.noiseModes && when (activeNoiseRead()) {
                    HuaweiCommand.NOISE_READ -> data.size == 2 && ((data[1].toInt() == -1) || (next == mode && data[1].toInt() in noiseLevels(mode)))
                    HuaweiCommand.LEGACY_NOISE_READ -> data.size == 1
                    else -> false
                }
            }
            HuaweiFeature.OP -> {
                val feature = features.firstOrNull { it.code == packet.scalar(1) } ?: return false
                val report = featureStates[feature] ?: return false
                val tags = packet.fields.map(HuaweiTlv::tag)
                if (tags == listOf(1, 2)) feature.accepts(packet.scalar(2))
                else if (feature == HuaweiFeature.ADAPTIVE_VOLUME && tags == listOf(1, 3)) {
                    report.scalar(3) != null && packet.scalar(3) in SENSITIVITIES
                }
                else if (feature == HuaweiFeature.SPATIAL && tags == listOf(1, 3)) {
                    product.has("spatial_scene") && report.scalar(3) != null && packet.scalar(3) in SCENES
                }
                else feature == HuaweiFeature.HEAD && report.scalar(2) == 1 &&
                    HEAD_ACTIONS.values.any { tags == listOf(1, it) && report.scalar(it) != null && packet.scalar(it) in HEAD_CHOICES }
            }
            HuaweiPinch.WRITE -> pinchReports.any { (slot, report) -> slot.allows(packet, report) }
            HuaweiCommand.RING -> packet.fields.map(HuaweiTlv::tag) == listOf(1) && packet.field(1)?.let {
                it.size == 2 && ringing[it[0].toInt()] != null && it[1].toInt() in 0..1
            } == true
            HuaweiCommand.LEGACY_INDEX_SET -> packet.fields.size == 1 && mode == 1 && legacyIndex() != null && packet.scalar(1) in 0..8
            HuaweiCommand.LANGUAGE_SET -> {
                val locale = packet.field(1)?.decodeToString() ?: return false
                locale in languages() && packet.bytes().contentEquals(languageWrite(locale).bytes())
            }
            else -> controls.any { control ->
                val report = reports[control.read] ?: return@any false
                if (control.write != packet.op || control.row(report) == null || packet.fields.map(HuaweiTlv::tag) != control.writeTags) return@any false
                val value = packet.scalar(control.writeTags.first()) ?: return@any false
                control.writeTags.all { packet.scalar(it) == value } &&
                    if (control.choices.isEmpty()) value in 0..1 else value in control.options(report)
            }
        }
    }

    private val presets: Map<Int, String>
        get() {
            val report = reports[HuaweiCommand.EQ_READ]
            if (report == null) return if (product.has("eq_write_only") && reports[HuaweiCommand.BATTERY] != null) product.presets else emptyMap()
            val candidates = product.presets.ifEmpty { STANDARD_PRESETS }
            val offered = report.field(3) ?: return product.presets
            return candidates.filterKeys { it.toByte() in offered }
        }

    private val customOperation: Int? = when {
        product.has("custom_save") -> 1
        product.has("custom_preview") -> 0
        else -> null
    }
    private val slots: List<HuaweiCustomEq.Slot>? get() = HuaweiCustomEq.slots(reports[HuaweiCommand.EQ_READ])
    private val customSlot: Int? get() = if (customOperation == null) null else slots?.let(HuaweiCustomEq::slotFor)

    private val namedSlots: Map<Int, String>
        get() = slots.orEmpty().associate { slot ->
            val name = slot.name.filterNot(Char::isISOControl).trim().take(24)
            slot.id to if (name.isEmpty()) "Custom ${slot.id - 99}" else "Custom ${slot.id - 99}: $name"
        }

    override val eq: Any? get() = listOf(eqPresetNames, customSlot, slots, eqReport)
    override val eqBandsHz: List<Int> get() = if (customSlot != null) HuaweiCustomEq.bands else emptyList()
    override val eqRangeDb: Int = HuaweiCustomEq.rangeDb
    override val eqGainStepDb: Double = HuaweiCustomEq.stepDb
    override val eqRestoresOnConnect: Boolean = false
    override val eqReportsState: Boolean get() = !product.has("eq_write_only")
    private val selections: Map<Int, String> get() = presets + namedSlots
    /** The sounds the maker's app makes itself, each saved in the slot the app uses for it. */
    private val curveNames: List<String> get() = if (EQ_READ_SEEN in reports) product.curves.keys.toList() else emptyList()

    private fun curveFrame(name: String): HuaweiPacket? {
        val id = product.curveIds[name] ?: return null
        val gains = product.curves[name] ?: return null
        return HuaweiCustomEq.frame(HuaweiCommand.EQ_SET, id, SAVE, gains.map { it / 10.0 }, id.toString())
    }
    override val eqPresetNames: List<String> get() = if (reportOnly) emptyList() else selections.values.toList() + curveNames
    override val eqReport: BandEqReport? get() = reports[HuaweiCommand.EQ_READ]?.scalar(2)?.let { active ->
        val slot = slots?.singleOrNull { it.id == active }
        val curve = curveNames.firstOrNull { product.curveIds[it] == active }
        if (slot != null && slot.id == customSlot) slot.report()
        else curve?.let { BandEqReport(it, null) } ?: selections[active]?.let { BandEqReport(it, null) }
    }
    override val eqPresetGains: Map<String, List<Double>>
        get() = if (eqBandsHz.isEmpty()) emptyMap() else slots.orEmpty().associate { slot ->
            namedSlots.getValue(slot.id) to slot.gains.map { it / 10.0 }
        } + product.curves.mapValues { (_, gains) -> gains.map { it / 10.0 } }
    override fun eqPresetFrames(name: String): List<ByteArray>? = if (name in curveNames)
        curveFrame(name)?.let { listOf(it.bytes()) }
    else selections.entries.firstOrNull { it.value == name }?.key?.let { listOf(huaweiWrite(HuaweiCommand.EQ_SET, 1 to it).bytes()) }
    override fun eqCustomFrames(gains: List<Int>): List<ByteArray>? = eqCustomFramesDb(gains.map(Int::toDouble), 0)
    override fun eqCustomFramesDb(gains: List<Double>, preampDb: Int): List<ByteArray>? {
        val slot = customSlot ?: return null
        val operation = customOperation ?: return null
        return HuaweiCustomEq.frame(HuaweiCommand.EQ_SET, slot, operation, gains)?.let { listOf(it.bytes()) }
    }

    /** The curves saved on the earbuds that can be removed, by the names the equalizer shows them under. */
    private fun deletable(): Map<String, HuaweiCustomEq.Slot> =
        if (reportOnly || customOperation == null) emptyMap()
        else slots.orEmpty().associateBy { namedSlots.getValue(it.id) }

    public fun isEqWrite(packet: HuaweiPacket): Boolean {
        if (packet.op != HuaweiCommand.EQ_SET) return false
        if (deletable().values.any { slot -> HuaweiCustomEq.delete(HuaweiCommand.EQ_SET, slot).bytes().contentEquals(packet.bytes()) }) return true
        if (packet.fields.size == 1) return packet.scalar(1) in selections
        if (curveNames.any { curveFrame(it)?.bytes()?.contentEquals(packet.bytes()) == true }) return true
        val slot = customSlot ?: return false
        val operation = customOperation ?: return false
        return HuaweiCustomEq.allows(packet, slot, operation)
    }
    public fun eqReadBack(): List<HuaweiPacket> = opening.filter { it.op == HuaweiCommand.EQ_READ }

    private companion object {
        val EQ_READ_SEEN = HuaweiCommand.EQ_READ
        const val SAVE = 1
        const val LIGHT_CALL = "hw_light_call"
        const val LIGHT_MEDIA = "hw_light_media"
        const val SENSITIVITY = "hw_adaptive_sensitivity"
        const val PAIR_NEW = "hw_pair_new"
        const val CYCLES_KEY = 13
        val SENSITIVITIES = mapOf(0 to "hw_sensitivity_low", 1 to "hw_sensitivity_medium", 2 to "hw_sensitivity_high")
        val CYCLES_READ = huaweiPacket(HuaweiFeature.OP, listOf(HuaweiTlv(1, byteArrayOf(CYCLES_KEY.toByte())), HuaweiTlv(2)))
        val pairNew = huaweiWrite(HuaweiCommand.PAIR_NEW, 1 to 1)
        fun lightWrite(call: Int, media: Int): HuaweiPacket =
            huaweiPacket(HuaweiCommand.LIGHT, listOf(HuaweiTlv(2, byteArrayOf(0, call.toByte(), 1, media.toByte()))))
        val fitStart = huaweiRead(HuaweiCommand.FIT_SET, 1)
        val fitEnd = huaweiRead(HuaweiCommand.FIT_SET, 3)
        val HEAD_ACTIONS = mapOf("hw_nod" to 3, "hw_shake" to 4)
        val HEAD_CHOICES = mapOf(0 to "hw_gesture_off", 1 to "hw_answer_call", 2 to "hw_reject_call")
        val VERSION_NUMBER = Regex("^[vV]?[0-9]{1,4}(?:\\.[0-9]{1,4}){1,4}(?![.0-9])")
        val LOCALE = Regex("[a-zA-Z]{2,3}([-_][a-zA-Z0-9]{2,8}){0,3}")
        val STANDARD_PRESETS = mapOf(1 to "Default", 2 to "Bass boost", 3 to "Treble boost", 9 to "Voices")

        const val SCENE = "hw_spatial_scene"
        const val DELETE_SOUND = "hw_eq_delete"
        val SCENES = mapOf(0 to "hw_scene_default", 1 to "hw_scene_theater", 2 to "hw_scene_cinema", 3 to "hw_scene_hall")

        /** Reads whose request declares one byte it does not carry, as the vendor app sends them. */
        val BARE_READS = setOf(HuaweiCommand.TOUCH_ENABLE_READ, HuaweiCommand.HD_CALL_READ, HuaweiCommand.PICKUP_READ,
            HuaweiCommand.QUALITY_READ, HuaweiCommand.WIND_READ, HuaweiCommand.LIGHT)

        /** What a catalogue model is asked for its report: the settings' reads alone. */
        val PROBE: List<Pair<Int, IntArray>> = listOf(
            HuaweiCommand.NOISE_READ to intArrayOf(1), HuaweiCommand.LEGACY_NOISE_READ to intArrayOf(1),
            HuaweiCommand.DOUBLE_TAP_READ to intArrayOf(1, 2, 3), HuaweiCommand.TRIPLE_TAP_READ to intArrayOf(1, 2),
            HuaweiCommand.HOLD_READ to intArrayOf(1, 2, 3), HuaweiCommand.HOLD_CYCLE_READ to intArrayOf(1, 2),
            HuaweiCommand.SWIPE_READ to intArrayOf(1, 2), HuaweiCommand.WEAR_READ to intArrayOf(1),
            HuaweiCommand.LANGUAGE_READ to intArrayOf(1, 2, 3), HuaweiCommand.EQ_READ to intArrayOf(2),
            HuaweiCommand.MULTIPOINT_READ to intArrayOf(1), HuaweiCommand.LOW_LATENCY to intArrayOf(2),
            HuaweiCommand.QUALITY_READ to intArrayOf(1),
        )
    }
}
