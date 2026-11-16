package io.github.omegadestroyer.pairfree.protocol.sennheiser

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
private fun String.bytes() = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }

/**
 * Sennheiser's GAIA v3 (docs/sennheiser/PROTOCOL.md): the frames are those Smart Control's `SPPGaiaFramer` writes, the command words are the ones in the app's
 * own product files (data/sennheiser/commands.json), and the replies below are built as the app's `Gaia3Command.handleIncomingData` reads them. Not tried on earbuds.
 */
class SennheiserTest {
    private fun reply(vendor: Int, word: Int, payload: String) = Gaia3Packet(vendor, word + 0x100, payload.bytes()).bytes()
    private fun notification(vendor: Int, word: Int, payload: String) = Gaia3Packet(vendor, word + 0x80, payload.bytes()).bytes()
    private fun tracked(m: SennheiserModel?): SennheiserSession = SennheiserSession(m).also { it.start() }

    @Test
    fun framesAreTheAppsBytes() {
        // The battery read: FF, version 3, no flags, no payload, vendor 0495, word 0603.
        assertEquals("ff030000049506 03".replace(" ", ""), Gaia3Packet(0x0495, 0x0603).bytes().hex())
        // A write with a payload; the vendor and the word are big-endian.
        assertEquals("ff03000104950 80c01".replace(" ", ""), Gaia3Packet(0x0495, 0x080C, byteArrayOf(1)).bytes().hex())
        // The word is feature << 9 | type << 7 | command.
        val p = Gaia3Packet(0x0495, 0x1B85)
        assertEquals(13, p.feature)
        assertTrue(p.isError)
        assertEquals(5, p.command)
    }

    @Test
    fun theParserCutsJoinsAndSkipsWhatItShould() {
        val parser = Gaia3Parser()
        val a = reply(0x0495, 0x0603, "504b")
        val b = reply(0x0495, 0x0602, "0102")
        // Junk first, then two frames split in the middle of each, another vendor's frame dropped, and a checksum frame.
        val other = Gaia3Packet(0x1234, 0x0603, byteArrayOf(1)).bytes()
        val stream = byteArrayOf(0x11, 0x22) + a + other + b
        val out = mutableListOf<Gaia3Packet>()
        for (chunk in stream.toList().chunked(5)) out += parser.feed(chunk.toByteArray())
        assertEquals(listOf(Gaia3Packet(0x0495, 0x0703, "504b".bytes()), Gaia3Packet(0x0495, 0x0702, "0102".bytes())), out)
        val body = byteArrayOf(0xFF.toByte(), 3, 1, 1, 0x04, 0x95.toByte(), 0x07, 0x03, 0x50)
        val checked = body + body.fold(0) { x, v -> x xor (v.toInt() and 0xFF) }.toByte()
        assertEquals(1, Gaia3Parser().feed(checked).size)
        assertEquals(0, Gaia3Parser().feed(checked.copyOf().also { it[8] = 0x51 }).size, "a wrong checksum is dropped")
    }

    @Test
    fun theTablesAreTheAppsAndNamesFindTheirModels() {
        val battery = SennheiserCommands.ALL.first { it.id == SennheiserCommands.BATTERY_PERCENT }
        assertEquals(0x0603, battery.get)
        assertEquals(0x0683, battery.notification)
        assertEquals(0x1A05, SennheiserCommands.ALL.first { it.id == SennheiserCommands.ANC_STATUS }.get)
        assertEquals(0x1A04, SennheiserCommands.ALL.first { it.id == SennheiserCommands.ANC_STATUS }.set)
        assertEquals(0x1804, SennheiserCommands.ALL.first { it.id == SennheiserCommands.TRANSPARENCY_STATUS }.set)
        assertEquals("Momentum 4 Wireless", SennheiserModels.fromName("MARS")?.name)
        assertEquals("Momentum 4 Wireless", SennheiserModels.fromName("m4aebt black")?.name)
        assertEquals("Accentum Plus", SennheiserModels.fromName("APHRODITE")?.name)
        assertEquals("Momentum True Wireless 4", SennheiserModels.fromName("MTW4 Black Copper")?.name)
        assertNull(SennheiserModels.fromName("Some Other Headphones"))
        assertNull(SennheiserModels.fromName("MOMENTUM 3"), "the Momentum 3 speaks Nautilus, not GAIA 3")
        // The Momentum 5 is only in Smart Control Plus: no product file, so it reads everything.
        assertNull(SennheiserModels.fromName("M5AEBT")!!.properties)
        assertTrue(SennheiserModels.fromName("ACAEBT")!!.properties!!.contains(SennheiserCommands.ANC_STATUS))
        assertFalse(SennheiserModels.fromName("CX200TW1")!!.properties!!.contains(SennheiserCommands.ANC_STATUS), "the CX 200 has no noise control")
    }

    @Test
    fun theOpeningRegistersThenReadsOnlyWhatTheModelHas() {
        val frames = SennheiserSession(SennheiserModels.fromName("CX200TW1")).start().map { Gaia3Parser().feed(it).single() }
        // Registrations of notifications come first: vendor 0495, word 0007, the feature id.
        assertTrue(frames.take(7).all { it.vendor == 0x0495 && it.word == 0x0007 && it.payload.size == 1 })
        assertEquals(listOf(0, 2, 3, 4, 9, 12, 13), frames.take(7).map { it.payload[0].toInt() })
        val reads = frames.drop(7).map { it.word }
        assertTrue(0x0603 in reads, "battery")
        assertFalse(0x1A05 in reads, "no noise cancelling on the CX 200")
        // An unknown model (the Momentum 5) reads every property.
        val all = SennheiserSession(SennheiserModels.fromName("M5AEBT")).start().map { Gaia3Parser().feed(it).single().word }
        assertTrue(0x1A05 in all && 0x1805 in all && 0x0818 in all)
    }

    @Test
    fun batteryAndNoiseControlFollowTheReplies() {
        val s = tracked(SennheiserModels.fromName("ATW1"))
        // Left 80 %, right 75 %, case 60 %.
        var fed = s.feed(reply(0x0495, 0x0603, "504b3c"))
        assertTrue(fed.changed)
        assertEquals(listOf(Gaia3Packet.keyOf(0x0495, 0x0603)), fed.answers)
        assertEquals(80, s.state.batteryLeft)
        assertEquals(75, s.state.batteryRight)
        assertEquals(60, s.state.batteryCase)
        // A notification of a change carries the same bytes.
        s.feed(notification(0x0495, 0x0603, "4f4a3c"))
        assertEquals(79, s.state.batteryLeft)
        // 255 is unknown.
        s.feed(reply(0x0495, 0x0603, "ff4bff"))
        assertNull(s.state.batteryLeft)
        // Noise cancelling on, transparency off.
        s.feed(reply(0x0495, 0x1A05, "01"))
        s.feed(reply(0x0495, 0x1805, "00"))
        fun mode() = s.state.number(Command.ANC_MODE)?.let { Command.ANC_MODE.labels[it] }
        assertEquals("anc", mode())
        s.feed(notification(0x0495, 0x1805, "01"))
        assertEquals("transparency", mode())
        s.feed(notification(0x0495, 0x1805, "00"))
        s.feed(notification(0x0495, 0x1A05, "00"))
        assertEquals("off", mode())
    }

    @Test
    fun anErrorAnswersTheQuestionWithoutAValue() {
        val s = tracked(SennheiserModels.fromName("M5AEBT"))
        val fed = s.feed(Gaia3Packet(0x0495, 0x1B85, byteArrayOf(0x03)).bytes())
        assertEquals(listOf(Gaia3Packet.keyOf(0x0495, 0x1A05)), fed.answers)
        assertFalse(fed.changed)
        assertNull(s.state.number(Command.ANC_MODE))
    }

    @Test
    fun theNoiseModeIsWrittenAsTheAppWritesIt() {
        val s = tracked(SennheiserModels.fromName("ATW1"))
        s.feed(reply(0x0495, 0x1A05, "00"))
        s.feed(reply(0x0495, 0x1805, "00"))
        fun frames(name: String) = s.set(Command.ANC_MODE, Command.ANC_MODE.labels.entries.first { it.value == name }.key)!!.map { Gaia3Parser().feed(it).single().let { p -> "%04x:%s".format(p.word, p.payload.hex()) } }
        // Turning one on turns the other off first (Smart Control's own order).
        assertEquals(listOf("1804:00", "1a04:01"), frames("anc"))
        assertEquals(listOf("1a04:00", "1804:01"), frames("transparency"))
        assertEquals(listOf("1a04:00", "1804:00"), frames("off"))
        // The shared screens' state already shows it, and the reads that confirm go out after.
        assertEquals("off", Command.ANC_MODE.labels[s.state.number(Command.ANC_MODE)])
        assertEquals(6, s.takeReadBacks().size)
    }

    @Test
    fun rowsShowOnceTheirReadAnsweredAndWriteTheAppsBytes() {
        val s = tracked(SennheiserModels.fromName("MARS"))
        assertTrue(s.groups.isEmpty())
        s.feed(reply(0x0495, 0x080D, "01"))
        s.feed(reply(0x0495, 0x0401, "00"))
        // Anti-wind and adaptive are pairs in the ANC property: [1, state, 2, state, 3, state].
        s.feed(reply(0x0495, 0x1A01, "010002010301"))
        s.feed(reply(0x0495, 0x1A05, "01"))
        val rows = s.groups.flatMap { it.settings }.associateBy { it.key }
        assertEquals(Setting.Toggle(SennheiserSession.KEY_SMART_PAUSE, true), rows[SennheiserSession.KEY_SMART_PAUSE])
        assertEquals(Setting.Toggle(SennheiserSession.KEY_ON_HEAD, false), rows[SennheiserSession.KEY_ON_HEAD])
        assertEquals(Setting.Toggle(SennheiserSession.KEY_ANTI_WIND, false), rows[SennheiserSession.KEY_ANTI_WIND])
        assertEquals(Setting.Toggle(SennheiserSession.KEY_ADAPTIVE, true), rows[SennheiserSession.KEY_ADAPTIVE])
        fun write(change: SettingChange) = s.change(change)!!.map { Gaia3Parser().feed(it).single().let { p -> "%04x:%s".format(p.word, p.payload.hex()) } }
        assertEquals(listOf("080c:00"), write(SettingChange.Toggle(SennheiserSession.KEY_SMART_PAUSE, false)))
        assertEquals(listOf("0400:01"), write(SettingChange.Toggle(SennheiserSession.KEY_ON_HEAD, true)))
        // Anti-wind is the mode 1 and its state, written by the same command as the others (1A00).
        assertEquals(listOf("1a00:0101"), write(SettingChange.Toggle(SennheiserSession.KEY_ANTI_WIND, true)))
        assertEquals(listOf("1a00:0300"), write(SettingChange.Toggle(SennheiserSession.KEY_ADAPTIVE, false)))
        // A row the earbuds did not answer cannot be written.
        assertNull(s.change(SettingChange.Toggle(SennheiserSession.KEY_LOW_LATENCY, true)))
        assertNotNull(s.takeReadBacks().firstOrNull())
    }

    @Test
    fun onlyTheTableGoesOut() {
        val s = SennheiserSession(null)
        assertTrue(s.allowed(Gaia3Packet(0x0495, 0x0603).bytes()))
        assertTrue(s.allowed(Gaia3Packet(0x0495, 0x1A04, byteArrayOf(1)).bytes()))
        assertTrue(s.allowed(Gaia3Packet(0x0495, 0x0007, byteArrayOf(13)).bytes()), "the notification registration")
        // The factory reset (0495:0040) and anything else not in the table.
        assertFalse(s.allowed(Gaia3Packet(0x0495, 0x0040).bytes()))
        assertFalse(s.allowed(Gaia3Packet(0x001D, 0x0006).bytes()))
        assertFalse(s.allowed(Gaia3Packet(0x0495, 0x1A02, byteArrayOf(50)).bytes()))
        assertFalse(s.allowed(byteArrayOf(1, 2, 3)))
    }
}
