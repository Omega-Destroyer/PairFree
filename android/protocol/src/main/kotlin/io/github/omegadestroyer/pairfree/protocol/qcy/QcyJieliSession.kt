package io.github.omegadestroyer.pairfree.protocol.qcy

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.GattEnvelope
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup

/**
 * One connection to QCY earbuds on a JieLi chip (docs/qcy/PROTOCOL.md section 6). QCY's app talks to them
 * through JieLi's RCSP, and most of what it sends is QCY's own items inside RCSP's custom command: noise
 * control and the switches. The older JieLi models keep the battery, game mode, in-ear detection, sleep mode
 * and balance among JieLi's own settings attributes (ADV) instead, and give their firmware in JieLi's target
 * info; the newer ones (QcyProtocol.JIELI_CUSTOM) keep only in-ear detection and balance there, and give the
 * battery and firmware as QCY's items.
 *
 * So the settings logic is [QcySession]'s, and this turns its messages into RCSP frames and RCSP answers back
 * into QCY items. No authentication is involved: JieLi's (RcspAuth) is in its firmware-update and watch SDKs
 * only, which QCY's app uses for nothing else.
 */
public class QcyJieliSession(override val model: QcyModel?, advert: QcyAdvert? = null) : QcyControl {
    private val inner = QcySession(model, advert)
    private val customOnly = model?.protocol == QcyProtocol.JIELI_CUSTOM
    private val jieliNoise = model?.jieliNoise == true
    private var keyTable: Map<Int, Int> = emptyMap()   // the touch controls as last read or written, key to QCY's action number
    private var noiseRange: List<Int>? = null            // the last noise mode attribute after its mode byte: length, then the minimum, maximum and current gain
    private val parser = RcspParser()
    private var sn = 0
    private val outbox = mutableListOf<ByteArray>()
    private var tracked = 0   // the attributes read on opening, read again when the earbuds ask
    private val _replies = linkedMapOf<String, ByteArray>()

    override val state: EarbudState get() = inner.state
    override val groups: List<SettingGroup> get() = inner.groups
    override val firmware: String? get() = inner.firmware
    override val replies: Map<String, ByteArray> get() = _replies + inner.replies
    override val service: String get() = JieliWire.SERVICE

    private fun nextSn(): Int = sn.also { sn = (sn + 1) and 0xFF }

    private fun frame(opcode: Int, payload: ByteArray): ByteArray =
        GattEnvelope.write(JieliWire.WRITE_SHORT, RcspFrame(command = true, wantsAnswer = true, opcode = opcode, sn = nextSn(), payload = payload).bytes())

    private fun bit(type: Int): Int = 1 shl type

    private fun advGet(mask: Int): ByteArray = frame(JieliWire.ADV_GET, byteArrayOf((mask shr 24).toByte(), (mask shr 16).toByte(), (mask shr 8).toByte(), mask.toByte()))

    /** On opening: what QCY's session asks for, its reads of the attributes in one. */
    override fun start(): List<ByteArray> = translate(inner.start(), opening = true)

    override fun batteryRequests(): List<ByteArray> = translate(inner.batteryRequests())

    override fun takeReadBacks(): List<ByteArray> = translate(inner.takeReadBacks())

    override fun takeOutbox(): List<ByteArray> = outbox.toList().also { outbox.clear() }

    override fun set(command: Command, value: Int): List<ByteArray>? = inner.set(command, value)?.let(::translate)

    override fun change(change: SettingChange): List<ByteArray>? = inner.change(change)?.let(::translate)

    /** The parametric EQ only: QCY's items in the custom command; JieLi's own EQ setting is not covered. */
    override val eq: QcyEq? get() = inner.eq?.takeIf { inner.parametricOnly }

    override fun eqCustom(gains: List<Int>): List<ByteArray>? = inner.eqCustom(gains)?.takeIf { inner.parametricOnly }?.let(::translate)

    /** The JieLi settings attribute that holds a QCY item on this model, or null when it goes in the custom command. */
    private fun attribute(item: Int): Int? = when (item) {
        QcyItem.IN_EAR, QcyItem.WEARING -> JieliWire.ADV_IN_EAR
        QcyItem.GAME_MODE -> JieliWire.ADV_WORK_MODE.takeUnless { customOnly }
        QcyItem.SLEEP_MODE -> JieliWire.ADV_SLEEP_MODE.takeUnless { customOnly }
        QcyItem.BALANCE -> JieliWire.ADV_BALANCE   // on every JieLi model (BtRcspOpImpl.setVolumeBalance)
        QcyItem.ANC_SETTING -> JieliWire.ADV_NOISE_MODE.takeIf { jieliNoise }   // the control panel's older noise screen: JieLi's own noise mode
        else -> null
    }

    /** A QCY item's value as its JieLi attribute takes it: game mode is JieLi's work mode, 2 for game and 1 for normal. */
    private fun attributeValue(e: QcyEntry): ByteArray = when (e.item) {
        QcyItem.GAME_MODE -> byteArrayOf(if (e.u8(0) == 1) WORK_GAME else WORK_NORMAL)
        else -> byteArrayOf(e.value[0])
    }

    /**
     * QCY's session's messages as RCSP frames: items in the custom command, or JieLi's attributes, all reads of
     * attributes in one. Its read of QCY's version characteristic becomes JieLi's target info, which JieLi's SDK
     * reads on every connection, and on the newer models QCY's version item as well; its battery read becomes
     * JieLi's battery attribute on the older models and QCY's battery item on the newer ones, as QCY's app asks.
     */
    private fun translate(envelopes: List<ByteArray>, opening: Boolean = false): List<ByteArray> {
        var mask = 0
        val out = mutableListOf<ByteArray>()
        for (e in envelopes) {
            val o = GattEnvelope.out(e) ?: continue
            if (o.operation == GattEnvelope.READ) {
                when (o.short) {
                    QcyWire.VERSION_SHORT -> {
                        out += targetInfo()
                        if (customOnly) out += request(QcyItem.VERSION)
                    }
                    QcyWire.BATTERY_SHORT -> if (customOnly) out += request(QcyItem.BATTERY) else mask = mask or bit(JieliWire.ADV_BATTERY)
                    QcyWire.KEYS_SHORT -> if (customOnly) out += request(QcyItem.KEY_FUNCTION) else mask = mask or bit(JieliWire.ADV_KEY_SETTINGS)
                }
                continue
            }
            if (o.short == QcyWire.KEYS_SHORT) {
                out += keyWrites(o.value)
                continue
            }
            if (o.short != QcyWire.COMMAND_SHORT) continue
            for (entry in QcyPacket.parse(o.value)) {
                if (jieliNoise && entry.item == QcyItem.ANC_SETTING && entry.value.size >= 3) {
                    noiseWrite(entry.u8(0))?.let { out += it }
                    continue
                }
                if (entry.item == QcyItem.REQUEST && entry.value.isNotEmpty()) {
                    val type = attribute(entry.u8(0))
                    if (type != null) mask = mask or bit(type) else out += custom(entry)
                    continue
                }
                if (entry.value.isEmpty()) continue
                val type = attribute(entry.item)
                out += if (type != null) frame(JieliWire.ADV_SET, RcspFrame.ltv(type, attributeValue(entry))) else custom(entry)
            }
        }
        if (opening) tracked = mask
        if (mask != 0) out += advGet(mask)
        return out
    }

    /**
     * The touch controls QCY's session wrote as a whole table, as the keys that changed: a new JieLi device takes one key and action in
     * the custom command (item 2B), an older one the JieLi key settings attribute with the earbud, the gesture and the action
     * (QCY's action 0 is 127 there), as QCY's app writes (JLDeviceImpl.setBtnClickFunction).
     */
    private fun keyWrites(table: ByteArray): List<ByteArray> {
        val next = (0 until table.size / 2).associate { (table[it * 2].toInt() and 0xFF) to (table[it * 2 + 1].toInt() and 0xFF) }
        val out = next.filter { (k, a) -> keyTable[k] != a }.map { (k, a) ->
            if (customOnly) {
                custom(QcyEntry(QcyItem.KEY_FUNCTION, byteArrayOf(k.toByte(), a.toByte())))
            } else {
                val side = if (k % 2 == 1) 1 else 2
                frame(JieliWire.ADV_SET, RcspFrame.ltv(JieliWire.ADV_KEY_SETTINGS, byteArrayOf(side.toByte(), ((k + 1) / 2).toByte(), (if (a == 0) KEY_NONE else a).toByte())))
            }
        }
        keyTable = keyTable + next
        return out
    }

    /** A noise mode (the panel's number: 1 on, 2 off, 3 transparency) as JieLi's noise mode attribute, with the gains the earbuds last reported (NoiseMode.toData). */
    private fun noiseWrite(mode: Int): ByteArray? {
        val range = noiseRange ?: return null
        fun two(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())
        return frame(JieliWire.ADV_SET, RcspFrame.ltv(JieliWire.ADV_NOISE_MODE, byteArrayOf(mode.toByte(), range[0].toByte()) + two(range[1]) + two(range[2]) + two(range[3])))
    }

    /** The firmware version from JieLi's target info: only that attribute, so not the earbuds' address. */
    private fun targetInfo(): ByteArray {
        val mask = bit(JieliWire.TARGET_FIRMWARE)
        return frame(JieliWire.GET_TARGET_INFO, byteArrayOf((mask shr 24).toByte(), (mask shr 16).toByte(), (mask shr 8).toByte(), mask.toByte(), 0))
    }

    private fun request(item: Int): ByteArray = custom(QcyEntry(QcyItem.REQUEST, byteArrayOf(item.toByte())))

    /** One QCY item in RCSP's custom command, without QCY's own header (JLDeviceImpl.sendCustomCMD). */
    private fun custom(e: QcyEntry): ByteArray = frame(JieliWire.CUSTOM, byteArrayOf(e.item.toByte(), e.value.size.toByte()) + e.value)

    /** False for what PairFree never sends: JieLi's reboot, function and system writes, and QCY's forbidden items in the custom command. */
    override fun allowed(envelope: ByteArray): Boolean {
        val o = GattEnvelope.out(envelope) ?: return false
        if (o.operation != GattEnvelope.WRITE || o.short != JieliWire.WRITE_SHORT) return false
        val f = RcspParser().feed(o.value).singleOrNull() ?: return false
        if (!f.command) return true
        return when (f.opcode) {
            in JieliWire.NEVER_SEND -> false
            JieliWire.CUSTOM -> QcyPacket.parse(f.payload).none { it.item in QcyItem.NEVER_SEND }
            JieliWire.ADV_SET -> RcspFrame.attributes(f.payload).keys.all { it in WRITABLE }
            else -> f.opcode in SENT
        }
    }

    override fun receive(envelope: ByteArray): Boolean {
        val message = GattEnvelope.parse(envelope) ?: return false
        if (message.short != JieliWire.NOTIFY_SHORT) return false
        var changed = false
        for (f in parser.feed(message.value)) {
            if (f.command) {
                // The earbuds' own commands: answer those that want it, read QCY's items in a custom one, and
                // on the older models read the attributes again when the earbuds ask for it (C4), as QCY's app does.
                if (f.wantsAnswer) outbox += GattEnvelope.write(JieliWire.WRITE_SHORT, RcspFrame.answer(f).bytes())
                if (f.opcode == JieliWire.CUSTOM) changed = items(f.payload) || changed
                if (f.opcode == JieliWire.ADV_REQUEST_OPERATION && !customOnly && tracked != 0) outbox += advGet(tracked)
                continue
            }
            if (f.status != 0) continue
            when (f.opcode) {
                // The newer models' firmware is QCY's version item, as QCY's app shows it; the target info's is kept for the report.
                JieliWire.GET_TARGET_INFO -> RcspFrame.attributes(f.payload)[JieliWire.TARGET_FIRMWARE]?.takeIf { it.size >= 2 }?.let { v ->
                    _replies["target ${JieliWire.TARGET_FIRMWARE}"] = v.copyOf()
                    if (customOnly) return@let
                    val code = ((v[0].toInt() and 0xFF) shl 8) or (v[1].toInt() and 0xFF)
                    val version = "${code shr 12 and 0xF}.${code shr 8 and 0xF}.${code shr 4 and 0xF}.${code and 0xF}"
                    changed = inner.receive(GattEnvelope.received(QcyWire.VERSION_SHORT, version.encodeToByteArray())) || changed
                }
                JieliWire.ADV_GET -> changed = attributes(RcspFrame.attributes(f.payload)) || changed
                JieliWire.CUSTOM -> changed = items(f.payload) || changed
            }
        }
        return changed
    }

    /**
     * QCY's items as the earbuds sent them in a custom command or answer. The battery and version items carry
     * what QCY's own characteristics do (QCY's app reads them alike, BatteryDataBean and VersionDataBean).
     */
    private fun items(raw: ByteArray): Boolean {
        if (raw.isEmpty() || raw.size > 0xFF) return false
        var changed = false
        val rest = mutableListOf<QcyEntry>()
        for (e in QcyPacket.parse(raw)) {
            when (e.item) {
                QcyItem.BATTERY -> changed = inner.receive(GattEnvelope.received(QcyWire.BATTERY_SHORT, e.value)) || changed
                QcyItem.VERSION -> changed = inner.receive(GattEnvelope.received(QcyWire.VERSION_SHORT, e.value)) || changed
                QcyItem.KEY_FUNCTION -> {
                    keyTable = keyTable + (0 until e.value.size / 2).associate { e.u8(it * 2) to e.u8(it * 2 + 1) }
                    rest += e
                }
                else -> rest += e
            }
        }
        if (rest.isNotEmpty()) changed = inner.receive(GattEnvelope.received(QcyWire.SETTINGS_SHORT, QcyPacket.build(*rest.toTypedArray()))) || changed
        return changed
    }

    /** JieLi's settings attributes as the QCY items QCY's session reads. */
    private fun attributes(attrs: Map<Int, ByteArray>): Boolean {
        var changed = false
        val entries = mutableListOf<QcyEntry>()
        for ((type, v) in attrs) {
            if (type == JieliWire.ADV_DEVICE_NAME || type == JieliWire.ADV_PRODUCT || v.isEmpty()) continue
            _replies["adv $type"] = v.copyOf()
            when (type) {
                JieliWire.ADV_BATTERY -> changed = inner.receive(GattEnvelope.received(QcyWire.BATTERY_SHORT, v.copyOf(3))) || changed
                JieliWire.ADV_WORK_MODE -> entries += QcyEntry(QcyItem.GAME_MODE, byteArrayOf(if (v[0] == WORK_GAME) 1 else 2))
                JieliWire.ADV_NOISE_MODE -> if (jieliNoise) {
                    // Mode, the length of what follows, then the minimum, maximum and current gain in two bytes each (NoiseMode.parseData).
                    fun two(at: Int) = if (at + 1 < v.size) ((v[at].toInt() and 0xFF) shl 8) or (v[at + 1].toInt() and 0xFF) else 0
                    val length = if (v.size > 1) v[1].toInt() and 0xFF else 0
                    noiseRange = listOf(length, two(2), two(4), two(6))
                    entries += QcyEntry(QcyItem.ANC_SETTING, byteArrayOf(v[0], 0, 0))
                }
                JieliWire.ADV_KEY_SETTINGS -> {
                    // Triples of earbud (1 left, 2 right), gesture (1 tap, 2 double, 3 triple, 4, 5 hold) and action (127 is none), as QCY's key numbers.
                    val table = sortedMapOf<Int, Int>()
                    for (at in 0 until v.size / 3) {
                        val side = v[at * 3].toInt() and 0xFF
                        val gesture = v[at * 3 + 1].toInt() and 0xFF
                        val action = v[at * 3 + 2].toInt() and 0xFF
                        if (side in 1..2 && gesture in 1..5) table[gesture * 2 - 2 + side] = if (action == KEY_NONE) 0 else action
                    }
                    keyTable = keyTable + table
                    entries += QcyEntry(QcyItem.KEY_FUNCTION, table.flatMap { (k, a) -> listOf(k.toByte(), a.toByte()) }.toByteArray())
                }
                JieliWire.ADV_IN_EAR -> entries += QcyEntry(QcyItem.IN_EAR, byteArrayOf(v[0]))
                JieliWire.ADV_SLEEP_MODE -> entries += QcyEntry(QcyItem.SLEEP_MODE, byteArrayOf(v[0]))
                JieliWire.ADV_BALANCE -> entries += QcyEntry(QcyItem.BALANCE, byteArrayOf(v[0]))
            }
        }
        if (entries.isNotEmpty()) changed = inner.receive(GattEnvelope.received(QcyWire.SETTINGS_SHORT, QcyPacket.build(*entries.toTypedArray()))) || changed
        return changed
    }

    private companion object {
        const val WORK_NORMAL: Byte = 1
        const val WORK_GAME: Byte = 2
        const val KEY_NONE = 127

        /** The settings attributes PairFree writes. */
        val WRITABLE = setOf(JieliWire.ADV_IN_EAR, JieliWire.ADV_WORK_MODE, JieliWire.ADV_SLEEP_MODE, JieliWire.ADV_BALANCE, JieliWire.ADV_KEY_SETTINGS, JieliWire.ADV_NOISE_MODE)

        /** The other commands PairFree sends. */
        val SENT = setOf(JieliWire.GET_TARGET_INFO, JieliWire.ADV_GET)
    }
}
