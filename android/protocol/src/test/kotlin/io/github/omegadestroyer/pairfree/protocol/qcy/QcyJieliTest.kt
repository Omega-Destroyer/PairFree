package io.github.omegadestroyer.pairfree.protocol.qcy

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.GattEnvelope
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.Value
import io.github.omegadestroyer.pairfree.protocol.hexBytes
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun hex(bytes: ByteArray) = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

/** The RCSP frame each message carries, taken apart. */
private fun frame(envelope: ByteArray): RcspFrame {
    val o = GattEnvelope.out(envelope)!!
    assertEquals(JieliWire.WRITE_SHORT, o.short)
    return RcspParser().feed(o.value).single()
}

/** An answer from the earbuds, as a notification. */
private fun answer(opcode: Int, payload: ByteArray, sn: Int = 0) =
    GattEnvelope.received(JieliWire.NOTIFY_SHORT, RcspFrame(command = false, wantsAnswer = false, opcode = opcode, sn = sn, payload = payload).bytes())

/** JieLi's RCSP framing and the session that carries QCY's items through it (docs/qcy/PROTOCOL.md section 6). */
class QcyJieliTest {
    /** An older JieLi model: game mode, sleep and balance among JieLi's attributes, noise in the custom command. */
    private val t13 = QcyModels.of(18294)!!

    /** A newer JieLi model, which takes QCY's items for its switches too. */
    private val ht16 = QcyModels.of(32883)!!

    @Test
    fun framesAsJieLisSdkPacksThem() {
        // Ask for the battery attribute: command with an answer wanted, opcode C1, length 5, sn 0, mask 1.
        val battery = QcyJieliSession(t13).batteryRequests().single()
        assertEquals("fedcbac0c1000500" + "00000001" + "ef", hex(GattEnvelope.out(battery)!!.value))
        // An answer: status, then sn, then the payload; split across notifications and after stray bytes.
        val bytes = RcspFrame(command = false, wantsAnswer = false, opcode = JieliWire.ADV_GET, sn = 7, payload = "0400bc602b".hexBytes()).bytes()
        assertEquals("fedcba00c1000700070400bc602bef", hex(bytes))
        val p = RcspParser()
        assertTrue(p.feed(byteArrayOf(0x11) + bytes.copyOfRange(0, 5)).isEmpty())
        val f = p.feed(bytes.copyOfRange(5, bytes.size)).single()
        assertEquals(7, f.sn)
        assertEquals(0, f.status)
        assertFalse(f.command)
        assertEquals(mapOf(JieliWire.ADV_BATTERY to "bc602b"), RcspFrame.attributes(f.payload).mapValues { hex(it.value) })
        assertEquals("0305" + "1234", hex(RcspFrame.ltv(5, "1234".hexBytes())))
    }

    @Test
    fun anOlderJieLiModel() {
        assertEquals(QcyProtocol.JIELI, t13.protocol)
        val s = QcyJieliSession(t13)
        assertEquals(JieliWire.SERVICE, s.service)
        val start = s.start().map(::frame)
        // The firmware alone first, so the earbuds' address stays out of the answer.
        assertEquals(JieliWire.GET_TARGET_INFO, start.first().opcode)
        assertEquals("0000002000", hex(start.first().payload))
        // Game mode and sleep are JieLi attributes on this model, asked for with the battery and balance in one read.
        val adv = start.last()
        assertEquals(JieliWire.ADV_GET, adv.opcode)
        val mask = (1 shl JieliWire.ADV_BATTERY) or (1 shl JieliWire.ADV_WORK_MODE) or (1 shl JieliWire.ADV_SLEEP_MODE) or (1 shl JieliWire.ADV_BALANCE)
        assertEquals(mask, hex(adv.payload).toInt(16))
        assertTrue(start.all { s.allowed(GattEnvelope.write(JieliWire.WRITE_SHORT, it.bytes())) })
        // The answer: left 60 % charging, right 96 %, case 43 %; game mode on (work mode 2), sleep on, balance centred.
        val attrs = RcspFrame.ltv(JieliWire.ADV_BATTERY, "bc602b".hexBytes()) + RcspFrame.ltv(JieliWire.ADV_WORK_MODE, byteArrayOf(2)) +
            RcspFrame.ltv(JieliWire.ADV_SLEEP_MODE, byteArrayOf(1)) + RcspFrame.ltv(JieliWire.ADV_BALANCE, byteArrayOf(50))
        assertTrue(s.receive(answer(JieliWire.ADV_GET, attrs)))
        assertEquals(listOf(60, 96, 43), listOf(s.state.batteryLeft, s.state.batteryRight, s.state.batteryCase))
        assertEquals(true, s.state.switch(Command.GAME_MODE))
        assertEquals(Setting.Toggle("sleep_mode", true), s.groups.flatMap { it.settings }.first { it.key == "sleep_mode" })
        // Game mode off goes back as JieLi's work mode 1.
        val off = frame(s.set(Command.GAME_MODE, 0)!!.single())
        assertEquals(JieliWire.ADV_SET, off.opcode)
        assertEquals("020501", hex(off.payload))
        // The firmware, as JieLi's SDK names it.
        s.receive(answer(JieliWire.GET_TARGET_INFO, RcspFrame.ltv(JieliWire.TARGET_FIRMWARE, "1234".hexBytes())))
        assertEquals("1.2.3.4", s.firmware)
        assertEquals("4776_1.2.3.4", (s.state.values[Command.FIRMWARE_VERSION] as Value.Text).text)
        // When the earbuds ask (C4), the answer and the same read as on opening.
        val ask = RcspFrame(command = true, wantsAnswer = true, opcode = JieliWire.ADV_REQUEST_OPERATION, sn = 3, payload = byteArrayOf(0))
        s.receive(GattEnvelope.received(JieliWire.NOTIFY_SHORT, ask.bytes()))
        val outbox = s.takeOutbox().map(::frame)
        assertEquals(RcspFrame.answer(ask), outbox.first())
        assertEquals(mask, hex(outbox.last().payload).toInt(16))
    }

    @Test
    fun aNewerModelReportsBatteryAndFirmwareAsQcyItems() {
        val s = QcyJieliSession(ht16)
        val start = s.start().map(::frame)
        // Target info, as JieLi's SDK reads it on connecting; the firmware and battery are QCY's items 30 and 2F.
        assertEquals(JieliWire.GET_TARGET_INFO, start.first().opcode)
        val custom = start.filter { it.opcode == JieliWire.CUSTOM }.map { hex(it.payload) }
        assertEquals(listOf("fe0130", "fe012f"), custom.take(2))
        // In-ear detection and balance stay JieLi attributes on these models too.
        val adv = start.single { it.opcode == JieliWire.ADV_GET }
        assertEquals((1 shl JieliWire.ADV_BALANCE), hex(adv.payload).toInt(16) and (1 shl JieliWire.ADV_BALANCE))
        assertEquals(0, hex(adv.payload).toInt(16) and (1 shl JieliWire.ADV_BATTERY))
        assertEquals(listOf("fe012f"), s.batteryRequests().map { hex(frame(it).payload) })
        // The answers: left 60 % charging, right 96 %, case 43 %; firmware 1.2.3 on the left, 1.2.4 on the right.
        s.receive(answer(JieliWire.CUSTOM, "2f03bc602b".hexBytes()))
        assertEquals(listOf(60, 96, 43), listOf(s.state.batteryLeft, s.state.batteryRight, s.state.batteryCase))
        s.receive(answer(JieliWire.CUSTOM, "3006010203010204".hexBytes()))
        s.receive(answer(JieliWire.GET_TARGET_INFO, RcspFrame.ltv(JieliWire.TARGET_FIRMWARE, "1234".hexBytes())))
        assertEquals("1.2.3", s.firmware)
        s.receive(answer(JieliWire.ADV_GET, RcspFrame.ltv(JieliWire.ADV_BALANCE, byteArrayOf(50))))
        val level = frame(s.change(SettingChange.Level("balance", 40))!!.single())
        assertEquals(JieliWire.ADV_SET, level.opcode)
        assertEquals("020c28", hex(level.payload))
    }

    @Test
    fun noiseControlInTheCustomCommand() {
        val s = QcyJieliSession(ht16)
        assertEquals(QcyProtocol.JIELI_CUSTOM, ht16.protocol)
        // The earbuds answer the request for item 17 with QCY's three bytes: adaptive noise cancelling.
        s.receive(answer(JieliWire.CUSTOM, "1703010500".hexBytes()))
        assertEquals(Command.ANC_MODE.labels.entries.first { it.value == "anc" }.key, s.state.number(Command.ANC_MODE))
        val write = frame(s.set(Command.ANC_MODE, Command.ANC_MODE.labels.entries.first { it.value == "off" }.key)!!.single())
        assertEquals(JieliWire.CUSTOM, write.opcode)
        assertEquals("1703020000", hex(write.payload))
        // The read-back asks for item 17 inside the custom command too.
        assertEquals("fe0117", hex(frame(s.takeReadBacks().single()).payload))
        // A newer model's game mode is QCY's item, not JieLi's attribute.
        s.receive(answer(JieliWire.CUSTOM, "090102".hexBytes()))
        assertEquals("090101", hex(frame(s.set(Command.GAME_MODE, 1)!!.single()).payload))
    }

    @Test
    fun theEarbudsOwnCommandsAreAnswered() {
        val s = QcyJieliSession(ht16)
        // A change made on the earbuds, sent as a custom command that wants an answer.
        val pushed = RcspFrame(command = true, wantsAnswer = true, opcode = JieliWire.CUSTOM, sn = 9, payload = "1703030104".hexBytes())
        s.receive(GattEnvelope.received(JieliWire.NOTIFY_SHORT, pushed.bytes()))
        assertEquals(Command.ANC_MODE.labels.entries.first { it.value == "transparency" }.key, s.state.number(Command.ANC_MODE))
        val reply = frame(s.takeOutbox().single())
        assertEquals(RcspFrame.answer(pushed), reply)
        assertArrayEquals("fedcba00ff00020009ef".hexBytes(), reply.bytes())
    }

    @Test
    fun whatIsNeverSent() {
        val s = QcyJieliSession(t13)
        fun cmd(opcode: Int, payload: String) = GattEnvelope.write(JieliWire.WRITE_SHORT, RcspFrame(true, true, opcode, 0, payload.hexBytes()).bytes())
        assertFalse(s.allowed(cmd(JieliWire.REBOOT, "")))
        assertFalse(s.allowed(cmd(JieliWire.CUSTOM, "0300")), "QCY's factory reset inside the custom command")
        assertFalse(s.allowed(cmd(JieliWire.ADV_SET, "0501" + "41424344")), "renaming the earbuds")
        assertTrue(s.allowed(cmd(JieliWire.ADV_SET, "020801")))
        assertFalse(s.allowed(GattEnvelope.write(QcyWire.COMMAND_SHORT, QcyPacket.request(QcyItem.GAME_MODE))), "QCY's own characteristic is not JieLi's")
    }
}
