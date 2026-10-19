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
                }
                continue
            }
            if (o.short != QcyWire.COMMAND_SHORT) continue
            for (entry in QcyPacket.parse(o.value)) {
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

        /** The settings attributes PairFree writes. */
        val WRITABLE = setOf(JieliWire.ADV_IN_EAR, JieliWire.ADV_WORK_MODE, JieliWire.ADV_SLEEP_MODE, JieliWire.ADV_BALANCE)

        /** The other commands PairFree sends. */
        val SENT = setOf(JieliWire.GET_TARGET_INFO, JieliWire.ADV_GET)
    }
}
