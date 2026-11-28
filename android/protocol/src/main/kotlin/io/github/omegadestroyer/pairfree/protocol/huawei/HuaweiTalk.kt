package io.github.omegadestroyer.pairfree.protocol.huawei

import io.github.omegadestroyer.pairfree.protocol.BandEq
import io.github.omegadestroyer.pairfree.protocol.BandEqReport
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value

/** One AI Life MBB conversation. Settings become writable after the matching model returns their read. */
public class HuaweiTalk(private val product: HuaweiProduct) : SessionTalk<HuaweiPacket>(), BandEq {
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
            add(huaweiRead(read, *tags))
        }
        addAll(features.map(HuaweiFeature::read))
        addAll(pinch.map(HuaweiPinch::read))
        if (product.has("find")) addAll((0..1).map { huaweiWrite(HuaweiCommand.RING_STATE, 1 to it) })
        if (product.has("language")) add(huaweiRead(HuaweiCommand.LANGUAGE_READ, 1, 2, 3))
        if (product.mayHaveEq && !product.has("eq_write_only")) add(huaweiRead(HuaweiCommand.EQ_READ, 2))
    }

    override val pacing: Pacing = Pacing(replyMs = 650, gapMs = 35)
    override val state: EarbudState get() = EarbudState(values.toMap(), noiseModeValues = product.noiseModes)
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
            if (switches.isNotEmpty()) add(SettingGroup("other", SettingTab.HOME, switches + headActions))
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
            "${hex(field.tag)}=" + reported(packet.op, field).joinToString("") { hex(it.toInt() and 0xff) }
        }
    }

    private fun hex(value: Int): String = value.toString(16).padStart(2, '0')

    private fun reported(op: Int, field: HuaweiTlv): ByteArray = when {
        op == HuaweiCommand.VERSION -> if (field.tag == 7) field.value.copyOf(minOf(field.value.size, 24)) else byteArrayOf()
        op == HuaweiCommand.EQ_READ && field.tag == 8 -> HuaweiCustomEq.unnamed(field.value)
        else -> field.value.copyOf()
    }

    private fun remember(packet: HuaweiPacket) {
        if (opening.none { it.op == packet.op } && packet.op != HuaweiCommand.BATTERY_NOTIFY) return
        val selectors = when (packet.op) {
            HuaweiFeature.OP, HuaweiCommand.PINCH_READ ->
                packet.fields.filter { it.tag == 1 || (packet.op == HuaweiCommand.PINCH_READ && it.tag == 2) }
                    .joinToString("") { field -> "." + field.value.joinToString("") { hex(it.toInt() and 0xff) } }
            else -> ""
        }
        answered[packet.op.toString(16).padStart(4, '0') + selectors] = packet
    }

    override fun start(): List<HuaweiPacket> = opening
    override fun poll(): List<HuaweiPacket> = listOf(batteryRead)
    override fun bytes(message: HuaweiPacket): ByteArray =
        message.bytes(emptyLastTlvDeclaresValue = message.op in BARE_READS && isRead(message))
    override fun answers(sent: HuaweiPacket, got: Any): Boolean {
        if (got !is HuaweiPacket) return false
        if (sent.op == HuaweiCommand.BATTERY && got.op == HuaweiCommand.BATTERY_NOTIFY) return true
        if (sent.op != got.op) return false
        return when (sent.op) {
            HuaweiFeature.OP -> sent.scalar(1) == got.scalar(1)
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
            (isRead(message) || (!reportOnly && (isEqWrite(message) || permittedSettingsWrite(message))))

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
            } else if (op == HuaweiCommand.NOISE_EVENT && noiseRead != null && packet.scalar(1) in product.noiseModes) {
                refreshNoise = true
            } else if (opening.any { it.op == op } && accept(packet)) {
                reports[op] = if (op == HuaweiCommand.VERSION) packet.copy(fields = emptyList()) else packet
                changed = true
            }
        }
        return Fed(changed, packets)
    }

    private fun accept(packet: HuaweiPacket): Boolean = when (packet.op) {
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
        if (!refreshNoise) return emptyList()
        refreshNoise = false
        return opening.filter { it.op == HuaweiCommand.NOISE_READ || it.op == HuaweiCommand.LEGACY_NOISE_READ }
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
    /** The sounds the maker's app makes itself, offered where PairFree can write a curve of the user's own. */
    private val curveNames: List<String> get() = if (customSlot != null) product.curves.keys.toList() else emptyList()
    override val eqPresetNames: List<String> get() = if (reportOnly) emptyList() else selections.values.toList() + curveNames
    override val eqReport: BandEqReport? get() = reports[HuaweiCommand.EQ_READ]?.scalar(2)?.let { active ->
        val slot = slots?.singleOrNull { it.id == active }
        if (slot != null && slot.id == customSlot) slot.report()
        else selections[active]?.let { BandEqReport(it, null) }
    }
    override val eqPresetGains: Map<String, List<Double>>
        get() = if (eqBandsHz.isEmpty()) emptyMap() else slots.orEmpty().associate { slot ->
            namedSlots.getValue(slot.id) to slot.gains.map { it / 10.0 }
        } + product.curves.mapValues { (_, gains) -> gains.map { it / 10.0 } }
    override fun eqPresetFrames(name: String): List<ByteArray>? = if (name in curveNames)
        eqCustomFramesDb(product.curves.getValue(name).map { it / 10.0 }, 0)
    else selections.entries.firstOrNull { it.value == name }?.key?.let { listOf(huaweiWrite(HuaweiCommand.EQ_SET, 1 to it).bytes()) }
    override fun eqCustomFrames(gains: List<Int>): List<ByteArray>? = eqCustomFramesDb(gains.map(Int::toDouble), 0)
    override fun eqCustomFramesDb(gains: List<Double>, preampDb: Int): List<ByteArray>? {
        val slot = customSlot ?: return null
        val operation = customOperation ?: return null
        return HuaweiCustomEq.frame(HuaweiCommand.EQ_SET, slot, operation, gains)?.let { listOf(it.bytes()) }
    }

    public fun isEqWrite(packet: HuaweiPacket): Boolean {
        if (packet.op != HuaweiCommand.EQ_SET) return false
        if (packet.fields.size == 1) return packet.scalar(1) in selections
        val slot = customSlot ?: return false
        val operation = customOperation ?: return false
        return HuaweiCustomEq.allows(packet, slot, operation)
    }
    public fun eqReadBack(): List<HuaweiPacket> = opening.filter { it.op == HuaweiCommand.EQ_READ }

    private companion object {
        val HEAD_ACTIONS = mapOf("hw_nod" to 3, "hw_shake" to 4)
        val HEAD_CHOICES = mapOf(0 to "hw_gesture_off", 1 to "hw_answer_call", 2 to "hw_reject_call")
        val VERSION_NUMBER = Regex("^[vV]?[0-9]{1,4}(?:\\.[0-9]{1,4}){1,4}(?![.0-9])")
        val LOCALE = Regex("[a-zA-Z]{2,3}([-_][a-zA-Z0-9]{2,8}){0,3}")
        val STANDARD_PRESETS = mapOf(1 to "Default", 2 to "Bass boost", 3 to "Treble boost", 9 to "Voices")

        /** Reads whose request declares one byte it does not carry, as the vendor app sends them (the Studio's touch switch; LibreBuds). */
        val BARE_READS = setOf(HuaweiCommand.TOUCH_ENABLE_READ, HuaweiCommand.HD_CALL_READ, HuaweiCommand.PICKUP_READ)

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
