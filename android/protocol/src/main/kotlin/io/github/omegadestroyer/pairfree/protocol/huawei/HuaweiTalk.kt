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

    private val batteryRead = if (product.has("modern_battery") || product.id == product.name) huaweiRead(BATTERY, 1, 2, 3) else HuaweiReads.battery()
    private val noiseRead = when {
        product.has("anc") -> huaweiRead(ANC, 1)
        product.has("legacy_anc") -> huaweiRead(LEGACY_ANC, 1)
        else -> null
    }
    private val opening = buildList {
        add(batteryRead)
        add(huaweiRead(VERSION, 7))
        noiseRead?.let(::add)
        if (product.has("anc") && product.has("legacy_anc")) add(huaweiRead(LEGACY_ANC, 1))
        if (product.has("legacy_index")) add(huaweiRead(LEGACY_INDEX, 1))
        for ((read, rows) in controls.groupBy(HuaweiControl::read)) {
            val tags = when (read) {
                0x0120 -> when {
                    product.has("eyewear") -> intArrayOf(3)
                    product.has("walrus_tap") || product.has("power_tap_list") -> intArrayOf(1, 2, 3)
                    else -> intArrayOf(1, 2)
                }
                0x2b21 -> intArrayOf(1, 2, 3)
                0x2b1f -> if (product.has("fiji")) intArrayOf(1, 2, 3) else intArrayOf(1, 2)
                0x0126, 0x2b19 -> intArrayOf(1, 2)
                0x2b17 -> if (product.has("walrus_hold") || product.has("single_hold") ||
                    product.has("fiji") || product.has("mermaid_hold")) intArrayOf(1, 2, 3) else intArrayOf(1, 2)
                else -> rows.flatMap(HuaweiControl::readTags).distinct().toIntArray()
            }
            add(huaweiRead(read, *tags))
        }
        addAll(features.map(HuaweiFeature::read))
        addAll(pinch.map(HuaweiPinch::read))
        if (product.has("find")) addAll((0..1).map { huaweiWrite(RING_STATE, 1 to it) })
        if (product.has("language")) add(huaweiRead(LANGUAGE, 1, 2, 3))
        if (product.mayHaveEq && !product.has("eq_write_only")) add(huaweiRead(EQ_READ, 2))
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
                featureStates[feature]?.scalar(2)?.let { Setting.Toggle(feature.row, it == 1) }
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
                if (levels.isNotEmpty()) reports[ANC]?.field(1)?.getOrNull(0)?.let {
                    add(Setting.Choice(if (mode == 1) "noise_strength" else "transparency_mode", levels[it.toInt() and 0xff], levels.values.toList()))
                }
                awarenessLevel()?.let { add(Setting.Level("hw_awareness_level", it, 0, 10)) }
            }
            if (noise.isNotEmpty()) add(SettingGroup("noise", SettingTab.HOME, noise))
            val languages = languages()
            if (languages.isNotEmpty()) {
                val current = reports[LANGUAGE]?.field(1)?.decodeToString()?.trimEnd('\u0000')?.takeIf { it in languages }
                add(SettingGroup("hw_voice", SettingTab.DEVICE, listOf(Setting.Choice("hw_language", current, languages))))
            }
        }

    /** Command IDs only. Device reports contain no raw payload, addresses, serials or custom names. */
    public val readsAnswered: List<String> get() = buildList {
        addAll(reports.keys.map { it.toString(16).padStart(4, '0') })
        if (featureStates.isNotEmpty()) add("2bb4")
        if (ringing.isNotEmpty()) add("2b5e")
        if (pinchReports.isNotEmpty()) add("2b93")
    }

    override fun start(): List<HuaweiPacket> = opening
    override fun poll(): List<HuaweiPacket> = listOf(batteryRead)
    override fun bytes(message: HuaweiPacket): ByteArray =
        message.bytes(emptyLastTlvDeclaresValue = message.op == STUDIO_TOUCH_READ && isRead(message))
    override fun answers(sent: HuaweiPacket, got: Any): Boolean {
        if (got !is HuaweiPacket) return false
        if (sent.op == BATTERY && got.op == BATTERY_NOTIFY) return true
        if (sent.op != got.op) return false
        return when (sent.op) {
            HuaweiFeature.OP -> sent.scalar(1) == got.scalar(1)
            HuaweiPinch.READ -> sent.scalar(1) == got.scalar(1) && sent.scalar(2) == got.scalar(2)
            RING_STATE -> sent.scalar(1) == got.field(2)?.firstOrNull()?.toInt()
            else -> true
        }
    }

    private fun isRead(message: HuaweiPacket): Boolean = opening.any { read ->
        read.op == message.op && read.fields.size == message.fields.size &&
            read.fields.zip(message.fields).all { (a, b) -> a.tag == b.tag && a.value.contentEquals(b.value) }
    }

    override fun replyMs(message: HuaweiPacket): Long = if (isRead(message)) pacing.replyMs else 0

    override fun allowed(message: HuaweiPacket): Boolean =
        isRead(message) || isEqWrite(message) || permittedSettingsWrite(message)

    override fun feed(bytes: ByteArray): Fed {
        val packets = parser.feed(bytes)
        var changed = false
        for (packet in packets) {
            val op = packet.op
            if (op == BATTERY || op == BATTERY_NOTIFY) {
                if (updateBattery(packet)) {
                    reports[op] = packet
                    changed = true
                }
            } else if (op == HuaweiFeature.OP) {
                val feature = features.firstOrNull { it.code == packet.scalar(1) }
                val value = packet.scalar(2)
                if (feature != null && value in 0..1) {
                    featureStates[feature] = packet
                    changed = true
                }
            } else if (op == HuaweiPinch.READ) {
                val slot = pinch.firstOrNull { it.matches(packet) }
                if (slot != null && slot.rows(packet).isNotEmpty()) {
                    pinchReports[slot] = packet
                    changed = true
                }
            } else if (op == RING_STATE && product.has("find")) {
                val data = packet.field(2)
                if (data?.size == 2 && data[0].toInt() in 0..1 && data[1].toInt() in 0..1) {
                    ringing[data[0].toInt()] = data[1].toInt() == 0
                    changed = true
                }
            } else if (op == ANC_EVENT && noiseRead != null && packet.scalar(1) in product.noiseModes) {
                refreshNoise = true
            } else if (opening.any { it.op == op } && accept(packet)) {
                reports[op] = if (op == VERSION) packet.copy(fields = emptyList()) else packet
                changed = true
            }
        }
        return Fed(changed, packets)
    }

    private fun accept(packet: HuaweiPacket): Boolean = when (packet.op) {
        VERSION -> {
            val raw = packet.field(7)?.takeIf { it.size <= 40 }?.decodeToString()?.trimEnd('\u0000')
            val version = raw?.let(VERSION_NUMBER::find)?.value
            if (version == null) false else {
                values[Command.FIRMWARE_VERSION] = Value.Text(version)
                true
            }
        }
        ANC -> packet.field(1)?.takeIf { it.size == 2 }?.get(1)?.toInt()?.let(::setNoiseReported) == true
        LEGACY_ANC -> reports[ANC] == null && packet.scalar(1)?.let(::setNoiseReported) == true
        LEGACY_INDEX -> legacyIndex(packet) != null
        LANGUAGE -> packet.field(3)?.isNotEmpty() == true
        EQ_READ -> packet.scalar(2) != null && packet.scalar(1) != 0
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
        val report = reports[ANC] ?: return null
        if (report.field(1)?.firstOrNull()?.toInt() != 4) return null
        return report.scalar(2)?.takeIf { it in 0..10 }
    }

    private fun legacyIndex(packet: HuaweiPacket? = reports[LEGACY_INDEX]): Int? {
        val mode = packet?.scalar(1)
        val tag = when (mode) { 0 -> 2; 1 -> 3; else -> return null }
        return packet.scalar(tag)?.takeIf { it in 0..8 }
    }

    private fun languages(): List<String> = reports[LANGUAGE]?.field(3)?.decodeToString()
        ?.substringBefore('\u0000')?.split(',')?.map(String::trim)?.filter { LOCALE.matches(it) }?.distinct().orEmpty()

    override fun takeOutbox(): List<HuaweiPacket> {
        if (!refreshNoise) return emptyList()
        refreshNoise = false
        return opening.filter { it.op == ANC || it.op == LEGACY_ANC }
    }

    override fun takeReadBacks(): List<HuaweiPacket> = readBacks.also { readBacks = emptyList() }

    override fun set(command: Command, value: Int): List<HuaweiPacket>? {
        val read = activeNoiseRead() ?: return null
        if (command != Command.ANC_MODE || !state.supports(command) || value !in product.noiseModes) return null
        val data = if (read == ANC) byteArrayOf(value.toByte(), 0xff.toByte()) else byteArrayOf(value.toByte())
        return respond(huaweiPacket(ANC_WRITE, listOf(HuaweiTlv(1, data))), read)
    }

    override fun change(change: SettingChange): List<HuaweiPacket>? {
        val key = change.key
        val mode = state.number(Command.ANC_MODE)
        if (change is SettingChange.Toggle) {
            val feature = features.firstOrNull { it.row == key && it in featureStates }
            if (feature != null) {
                readBacks = listOf(feature.read())
                return listOf(feature.write(change.on))
            }
        }
        if (change is SettingChange.Press) {
            val side = when (key) { "hw_ring_left" -> 0; "hw_ring_right" -> 1; else -> return null }
            val isRinging = ringing[side] ?: return null
            readBacks = listOf(huaweiWrite(RING_STATE, 1 to side))
            return listOf(huaweiPacket(RING, listOf(HuaweiTlv(1, byteArrayOf(side.toByte(), if (isRinging) 1 else 0)))))
        }
        if (change is SettingChange.Level && key == "hw_anc_adjust" && legacyIndex() != null && mode == 1 && change.value in 0..8) {
            return respond(huaweiWrite(INDEX_WRITE, 1 to change.value), LEGACY_INDEX)
        }
        if (change is SettingChange.Level && key == "hw_awareness_level" && awarenessLevel() != null && change.value in 0..10) {
            return respond(huaweiPacket(ANC_WRITE, listOf(
                HuaweiTlv(1, byteArrayOf(2, 4)), HuaweiTlv(3, byteArrayOf(1)), HuaweiTlv(4, byteArrayOf(change.value.toByte())),
            )), ANC)
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
                return respond(languageWrite(change.option), LANGUAGE)
            }
            if ((key == "noise_strength" && mode == 1) || (key == "transparency_mode" && mode == 2)) {
                if (reports[ANC] == null) return null
                val level = noiseLevels(mode).entries.firstOrNull { it.value == change.option }?.key ?: return null
                return respond(huaweiPacket(ANC_WRITE, listOf(HuaweiTlv(1, byteArrayOf(mode.toByte(), level.toByte())))), ANC)
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

    private fun languageWrite(locale: String): HuaweiPacket = huaweiPacket(LANGUAGE_WRITE, buildList {
        add(HuaweiTlv(1, locale.encodeToByteArray()))
        if (!product.has("eyewear")) add(HuaweiTlv(2, byteArrayOf(1)))
    })

    private fun respond(write: HuaweiPacket, read: Int): List<HuaweiPacket> {
        readBacks = opening.filter { it.op == read }
        return listOf(write)
    }

    private fun activeNoiseRead(): Int? = when {
        ANC in reports -> ANC
        LEGACY_ANC in reports -> LEGACY_ANC
        else -> null
    }

    private fun permittedSettingsWrite(packet: HuaweiPacket): Boolean {
        val mode = state.number(Command.ANC_MODE)
        return when (packet.op) {
            ANC_WRITE -> {
                val data = packet.field(1) ?: return false
                if (packet.fields.map(HuaweiTlv::tag) == listOf(1, 3, 4)) {
                    return awarenessLevel() != null && data.contentEquals(byteArrayOf(2, 4)) &&
                        packet.scalar(3) == 1 && packet.scalar(4) in 0..10
                }
                if (packet.fields.size != 1 || mode == null || data.isEmpty()) return false
                val next = data[0].toInt()
                next in product.noiseModes && when (activeNoiseRead()) {
                    ANC -> data.size == 2 && ((data[1].toInt() == -1) || (next == mode && data[1].toInt() in noiseLevels(mode)))
                    LEGACY_ANC -> data.size == 1
                    else -> false
                }
            }
            HuaweiFeature.OP -> {
                val feature = features.firstOrNull { it.code == packet.scalar(1) } ?: return false
                val report = featureStates[feature] ?: return false
                val tags = packet.fields.map(HuaweiTlv::tag)
                if (tags == listOf(1, 2)) packet.scalar(2) in 0..1
                else feature == HuaweiFeature.HEAD && report.scalar(2) == 1 &&
                    HEAD_ACTIONS.values.any { tags == listOf(1, it) && report.scalar(it) != null && packet.scalar(it) in HEAD_CHOICES }
            }
            HuaweiPinch.WRITE -> pinchReports.any { (slot, report) -> slot.allows(packet, report) }
            RING -> packet.fields.map(HuaweiTlv::tag) == listOf(1) && packet.field(1)?.let {
                it.size == 2 && ringing[it[0].toInt()] != null && it[1].toInt() in 0..1
            } == true
            INDEX_WRITE -> packet.fields.size == 1 && mode == 1 && legacyIndex() != null && packet.scalar(1) in 0..8
            LANGUAGE_WRITE -> {
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
            val report = reports[EQ_READ]
            if (report == null) return if (product.has("eq_write_only") && reports[BATTERY] != null) product.presets else emptyMap()
            val candidates = product.presets.ifEmpty { STANDARD_PRESETS }
            val offered = report.field(3) ?: return product.presets
            return candidates.filterKeys { it.toByte() in offered }
        }

    private val customOperation: Int? = when {
        product.has("custom_save") -> 1
        product.has("custom_preview") -> 0
        else -> null
    }
    private val slots: List<HuaweiCustomEq.Slot>? get() = HuaweiCustomEq.slots(reports[EQ_READ])
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
    override val eqPresetNames: List<String> get() = selections.values.toList()
    override val eqReport: BandEqReport? get() = reports[EQ_READ]?.scalar(2)?.let { active ->
        val slot = slots?.singleOrNull { it.id == active }
        if (slot != null && slot.id == customSlot) slot.report()
        else selections[active]?.let { BandEqReport(it, null) }
    }
    override val eqPresetGains: Map<String, List<Double>>
        get() = if (eqBandsHz.isEmpty()) emptyMap() else slots.orEmpty().associate { slot ->
            namedSlots.getValue(slot.id) to slot.gains.map { it / 10.0 }
        }
    override fun eqPresetFrames(name: String): List<ByteArray>? =
        selections.entries.firstOrNull { it.value == name }?.key?.let { listOf(huaweiWrite(EQ_WRITE, 1 to it).bytes()) }
    override fun eqCustomFrames(gains: List<Int>): List<ByteArray>? = eqCustomFramesDb(gains.map(Int::toDouble), 0)
    override fun eqCustomFramesDb(gains: List<Double>, preampDb: Int): List<ByteArray>? {
        val slot = customSlot ?: return null
        val operation = customOperation ?: return null
        return HuaweiCustomEq.frame(EQ_WRITE, slot, operation, gains)?.let { listOf(it.bytes()) }
    }

    public fun isEqWrite(packet: HuaweiPacket): Boolean {
        if (packet.op != EQ_WRITE) return false
        if (packet.fields.size == 1) return packet.scalar(1) in selections
        val slot = customSlot ?: return false
        val operation = customOperation ?: return false
        return HuaweiCustomEq.allows(packet, slot, operation)
    }
    public fun eqReadBack(): List<HuaweiPacket> = opening.filter { it.op == EQ_READ }

    private companion object {
        const val VERSION = 0x0107
        const val STUDIO_TOUCH_READ = 0x012d
        const val BATTERY = 0x0108
        const val BATTERY_NOTIFY = 0x0127
        const val ANC_EVENT = 0x2b03
        const val ANC = 0x2b2a
        const val ANC_WRITE = 0x2b04
        const val LEGACY_ANC = 0x2b05
        const val LEGACY_INDEX = 0x2b07
        const val INDEX_WRITE = 0x2b08
        const val LANGUAGE = 0x0c02
        const val LANGUAGE_WRITE = 0x0c01
        const val RING = 0x2b5d
        const val RING_STATE = 0x2b5e
        const val EQ_WRITE = 0x2b49
        const val EQ_READ = 0x2b4a
        val HEAD_ACTIONS = mapOf("hw_nod" to 3, "hw_shake" to 4)
        val HEAD_CHOICES = mapOf(0 to "hw_gesture_off", 1 to "hw_answer_call", 2 to "hw_reject_call")
        val VERSION_NUMBER = Regex("^[vV]?[0-9]{1,4}(?:\\.[0-9]{1,4}){1,4}(?![.0-9])")
        val LOCALE = Regex("[a-zA-Z]{2,3}([-_][a-zA-Z0-9]{2,8}){0,3}")
        val STANDARD_PRESETS = mapOf(1 to "Default", 2 to "Bass boost", 3 to "Treble boost", 9 to "Voices")
    }
}
