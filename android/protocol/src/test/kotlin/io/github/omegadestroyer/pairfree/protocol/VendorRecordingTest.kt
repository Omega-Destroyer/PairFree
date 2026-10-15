package io.github.omegadestroyer.pairfree.protocol

import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungMessage
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungPacket
import io.github.omegadestroyer.pairfree.protocol.sony.SonyFrame
import io.github.omegadestroyer.pairfree.protocol.sony.SonyWire
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreCommand
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcorePacket
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

/** Soundcore, Galaxy Buds and Sony traffic in a snoop log built here: found, matched to its connection, and shown without personal data. */
class VendorRecordingTest {
    private val t0 = 1_760_000_000_000_000L
    private val address = "AA:BB:CC:DD:EE:F1"
    private val addressText = address.replace(":", "")

    /** The address as the connection event carries it: least significant byte first. */
    private val addressBytes = addressText.hexBytes().reversedArray()

    @Test
    fun findsSoundcorePacketsSplitAcrossRfcommFrames() {
        val log = SnoopLog()
        log.connected(t0, handle = 0x0B, addressBytes)
        log.rfcomm(t0 + 1_000_000, false, 0x0B, dlci = 10, SoundcorePacket.build(SoundcoreCommand.STATE))
        // The state reply in two RFCOMM frames, the second with a credit byte, as a busy link sends it.
        val state = SoundcorePacket.buildFromEarbuds(SoundcoreCommand.STATE, stateBody())
        log.rfcomm(t0 + 1_050_000, true, 0x0B, dlci = 10, state.copyOfRange(0, 20))
        log.rfcomm(t0 + 1_060_000, true, 0x0B, dlci = 10, state.copyOfRange(20, state.size), credit = true)
        log.audio(t0 + 1_070_000, 0x0B)

        val scanner = SnoopScanner().apply { feed(log.bytes()) }
        val packets = scanner.packets()
        assertEquals(listOf(SoundcoreCommand.STATE, SoundcoreCommand.STATE), packets.map { it.id })
        assertEquals(listOf(false, true), packets.map { it.fromEarbuds })
        assertTrue(packets.all { it.brand == RecordedBrand.SOUNDCORE && it.channel == 10 && it.connectedAtMicros == t0 })
        assertTrue(packets[0].address!!.contentEquals(addressBytes))
        assertEquals(stateBody().hex(), packets[1].payload.hex())
        assertTrue(scanner.frames().isEmpty(), "no SOUNDPEATS frames")
    }

    @Test
    fun theReportMasksTheAddressAndSerialAndLeavesOutPrivateMessages() {
        val log = SnoopLog()
        log.connected(t0, 0x0B, addressBytes)
        log.rfcomm(t0 + 1_000_000, false, 0x0B, 10, SoundcorePacket.build(SoundcoreCommand.STATE))
        log.rfcomm(t0 + 1_050_000, true, 0x0B, 10, SoundcorePacket.buildFromEarbuds(SoundcoreCommand.STATE, stateBody()))
        log.rfcomm(t0 + 2_000_000, false, 0x0B, 10, SoundcorePacket.build(SoundcoreCommand.SET_SOUND_MODES, "010001000000".hexBytes()))
        log.rfcomm(t0 + 2_100_000, false, 0x0B, 10, SoundcorePacket.build(SoundcoreCommand.SET_SOUND_MODES, "010001000000".hexBytes()))
        log.rfcomm(t0 + 3_000_000, true, 0x0B, 10, SoundcorePacket.buildFromEarbuds(SoundcoreCommand.DUAL_CONNECTIONS_DEVICES, ByteArray(42) { 0x41 }))
        log.rfcomm(t0 + 4_000_000, false, 0x0B, 10, SoundcorePacket.build(0x0F81, "0102".hexBytes()))
        log.rfcomm(t0 + 4_500_000, false, 0x0B, 10, SoundcorePacket.build(0x0F82, ByteArray(10) { 7 }))

        val earbuds = VendorRecording.of(SnoopScanner().apply { feed(log.bytes()) }.packets()).single()
        assertEquals(RecordedBrand.SOUNDCORE, earbuds.brand)
        assertEquals(5, earbuds.fromApp)
        assertEquals(listOf("state" to 1, "set_sound_modes" to 2, "0F81" to 1, "0F82" to 1), earbuds.sent)
        val lines = VendorRecording.lines(earbuds)
        val text = lines.joinToString("\n")
        assertFalse(text.contains(addressText.encodeToByteArray().hex()), "the address as text")
        assertFalse(text.contains(addressBytes.spaced()), "the address as bytes")
        val reply = lines.single { it.contains("buds") && it.contains("0101") }
        // The serial (model digits, then the address as text) and the bare address are masked; the firmware is not.
        assertTrue(reply.contains(List(16) { "XX" }.joinToString(" ")), reply)
        assertTrue(reply.contains("30 36 2e 34 33"), "firmware 06.43: $reply")
        assertTrue(lines.any { it.contains("0681 set_sound_modes") && it.endsWith("01 00 01 00 00 00 (x2)") }, text)
        assertTrue(lines.any { it.contains("0B01") && it.endsWith("(42 bytes left out)") }, text)
        assertTrue(lines.any { it.contains("0F81 ?") && it.endsWith("01 02") }, "a short unknown message is shown")
        assertTrue(lines.any { it.contains("0F82 ?") && it.endsWith("(10 bytes left out)") }, "a longer unknown one is not")
        assertTrue(lines[1].trimStart().startsWith("0.000 app"), lines[1])
    }

    @Test
    fun findsGalaxyBudsAndDropsALoneChanceMatch() {
        // The Galaxy Buds2 Pro's state from GalaxyBudsClient's tests.
        val frame = Json.parseToJsonElement(repoFile("data/samsung/test_vectors.json").readText()).jsonObject
            .getValue("galaxybudsclient_extended_status").jsonArray.map { it.jsonObject }
            .first { it.str("model") == "SM-R510" }.str("frame").hexBytes()
        val log = SnoopLog()
        log.connected(t0, 0x0C, addressBytes)
        log.rfcomm(t0 + 500_000, true, 0x0C, dlci = 4, frame)
        log.rfcomm(t0 + 600_000, false, 0x0C, 4, SamsungPacket.build(SamsungMessage.MANAGER_INFO, "010222".hexBytes()))
        log.rfcomm(t0 + 900_000, false, 0x0C, 4, SamsungPacket.build(SamsungMessage.NOISE_CONTROLS, "02".hexBytes()))
        // One valid frame alone on another channel: as likely chance as a message.
        log.rfcomm(t0 + 950_000, true, 0x0C, dlci = 6, SamsungPacket.build(0x11, "00".hexBytes()))

        val packets = SnoopScanner().apply { feed(log.bytes()) }.packets()
        assertEquals(listOf(SamsungMessage.EXTENDED_STATUS_UPDATED, SamsungMessage.MANAGER_INFO, SamsungMessage.NOISE_CONTROLS), packets.map { it.id })
        val earbuds = VendorRecording.of(packets).single()
        assertEquals(RecordedBrand.SAMSUNG, earbuds.brand)
        val lines = VendorRecording.lines(earbuds)
        assertTrue(lines.any { it.contains(" 97 extended_status_updated") }, lines.joinToString("\n"))
        assertTrue(lines.last().contains("120 noise_controls") && lines.last().endsWith(" 02"), lines.last())
        val compact = VendorRecording.lines(earbuds, compact = true)
        assertTrue(compact[0].startsWith("0.000 buds  97 "), compact[0])
        assertEquals(listOf("0.100 app 136 01 02 22", "0.400 app 120 02"), compact.drop(1))
    }

    @Test
    fun findsSonyMessagesAndLearnsTheTableSet() {
        val log = SnoopLog()
        log.connected(t0, 0x0D, addressBytes)
        // Sony's app asks for the protocol; the earbuds acknowledge, then answer with set 2's 8 bytes.
        log.rfcomm(t0 + 100_000, false, 0x0D, dlci = 8, SonyFrame.build(SonyWire.TABLE1, 0, "0000".hexBytes()))
        log.rfcomm(t0 + 120_000, true, 0x0D, 8, SonyFrame.ack(0))
        log.rfcomm(t0 + 150_000, true, 0x0D, 8, SonyFrame.build(SonyWire.TABLE1, 0, "0100030000000000".hexBytes()))
        // Then a set 2 battery request (22), which set 1 numbers as power off, and a noise control change.
        log.rfcomm(t0 + 200_000, false, 0x0D, 8, SonyFrame.build(SonyWire.TABLE1, 1, "2201".hexBytes()))
        log.rfcomm(t0 + 900_000, false, 0x0D, 8, SonyFrame.build(SonyWire.TABLE1, 0, "681701010000 0c".replace(" ", "").hexBytes()))

        val packets = SnoopScanner().apply { feed(log.bytes()) }.packets()
        assertEquals(4, packets.size, "the acknowledgement carries nothing")
        val earbuds = VendorRecording.of(packets).single()
        assertEquals(RecordedBrand.SONY, earbuds.brand)
        val lines = VendorRecording.lines(earbuds)
        assertTrue(lines[1].contains("app  1.00 connect_get_protocol_info"), "a name both sets share: ${lines[1]}")
        assertTrue(lines.any { it.contains("1.22 power_get_status") && it.endsWith(" 01") }, lines.joinToString("\n"))
        assertTrue(lines.last().contains("1.68 ncasm_set_param") && lines.last().endsWith("17 01 01 00 00 0c"), lines.last())
        assertEquals(listOf("connect_get_protocol_info" to 1, "power_get_status" to 1, "ncasm_set_param" to 1), earbuds.sent)
    }

    @Test
    fun aRecordingStartsWhenTheEarbudsLastConnected() {
        val log = SnoopLog()
        log.connected(t0, 0x0B, addressBytes)
        log.rfcomm(t0 + 1_000_000, false, 0x0B, 10, SoundcorePacket.build(SoundcoreCommand.STATE))
        log.disconnected(t0 + 5_000_000, 0x0B)
        log.connected(t0 + 60_000_000, 0x0B, addressBytes)
        log.rfcomm(t0 + 61_000_000, false, 0x0B, 10, SoundcorePacket.build(SoundcoreCommand.STATE))
        val packets = SnoopScanner().apply { feed(log.bytes()) }.packets()
        assertEquals(t0 + 60_000_000, RecordingReport.start(emptyList(), null, packets))
        assertEquals(t0 + 500_000, RecordingReport.start(emptyList(), t0 + 500_000, packets), "the screen was opened first")
        assertNull(RecordingReport.start(emptyList(), null, emptyList()))
        assertEquals(1, VendorRecording.of(packets, since = t0 + 60_000_000).single().packets.size)
    }

    @Test
    fun withoutTheConnectionEventThereIsNoAddressToMask() {
        val log = SnoopLog()
        log.rfcomm(t0, false, 0x0B, 10, SoundcorePacket.build(SoundcoreCommand.STATE))
        val p = SnoopScanner().apply { feed(log.bytes()) }.packets().single()
        assertNull(p.address)
        assertNull(p.connectedAtMicros)
        assertEquals(t0, RecordingReport.start(emptyList(), null, listOf(p)))
        // The serial is still masked by its look.
        val body = stateBody()
        assertTrue(VendorRecording.shown(RecordedBrand.SOUNDCORE, SoundcoreCommand.STATE, body, null).contains(List(16) { "XX" }.joinToString(" ")))
    }

    /** A state reply: a few setting bytes, the serial (model digits and the address as text), the bare address, the firmware. */
    private fun stateBody(): ByteArray =
        "0102030405".hexBytes() + "3936$addressText".encodeToByteArray() + "11".hexBytes() + addressBytes + "06.43".encodeToByteArray() + "000102".hexBytes()

    private fun ByteArray.spaced() = joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    /** Builds a btsnoop file (H4 datalink): connection events and RFCOMM data, as Android writes them. */
    private class SnoopLog {
        private val out = ByteArrayOutputStream().apply {
            write("btsnoop".encodeToByteArray() + 0)
            write(int(1))
            write(int(1002))
        }

        fun connected(time: Long, handle: Int, address: ByteArray) {
            val params = byteArrayOf(0) + le16(handle) + address + byteArrayOf(0x01, 0x00)
            record(time, 3, byteArrayOf(0x04, 0x03, params.size.toByte()) + params)
        }

        fun disconnected(time: Long, handle: Int) {
            val params = byteArrayOf(0) + le16(handle) + byteArrayOf(0x13)
            record(time, 3, byteArrayOf(0x04, 0x05, params.size.toByte()) + params)
        }

        fun rfcomm(time: Long, fromEarbuds: Boolean, handle: Int, dlci: Int, data: ByteArray, credit: Boolean = false) {
            val length = if (data.size < 128) byteArrayOf(((data.size shl 1) or 1).toByte()) else byteArrayOf((data.size shl 1).toByte(), (data.size shr 7).toByte())
            val control = if (credit) 0xFF else 0xEF
            val frame = byteArrayOf(((dlci shl 2) or 3).toByte(), control.toByte()) + length + (if (credit) byteArrayOf(1) else ByteArray(0)) + data + byteArrayOf(0x5C)
            val l2cap = le16(frame.size) + le16(0x0045) + frame
            record(time, if (fromEarbuds) 1 else 0, byteArrayOf(0x02) + le16(handle or (2 shl 12)) + le16(l2cap.size) + l2cap)
        }

        /** An A2DP media packet that is no RFCOMM frame. */
        fun audio(time: Long, handle: Int) {
            val l2cap = le16(12) + le16(0x0042) + byteArrayOf(0x80.toByte(), 0x60, 0, 1, 0x08, 0xEE.toByte(), 0, 0, 0, 1, 2, 3)
            record(time, 0, byteArrayOf(0x02) + le16(handle or (2 shl 12)) + le16(l2cap.size) + l2cap)
        }

        private fun record(time: Long, flags: Int, data: ByteArray) {
            out.write(int(data.size))
            out.write(int(data.size))
            out.write(int(flags))
            out.write(int(0))
            out.write(long(time + 0x00DCDDB30F2F8000L))
            out.write(data)
        }

        fun bytes(): ByteArray = out.toByteArray()

        private fun int(v: Int) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
        private fun long(v: Long) = int((v shr 32).toInt()) + int(v.toInt())
        private fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
    }
}
