package io.github.omegadestroyer.pairfree.protocol.jabra

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

/**
 * Jabra's GNP session (docs/jabra/PROTOCOL.md). The earbuds' replies are built from the layouts in the app's own schema (data/jabra/research/gnp_commands.json), not
 * recorded: no Jabra product has been tried. The writes are checked against the encodings the native functions produced (research/test_vectors.json).
 */
class JabraSessionTest {
    private val elite10 = JabraModels.ALL.single { it.name == "Jabra Elite 10" }

    private fun reply(main: Int, vararg payload: Int): ByteArray = JabraPacket(9, 4, 1, JabraPacket.REPLY, main, ByteArray(payload.size) { payload[it].toByte() }).bytes()

    /** A session that has been told the product id and firmware of an Elite 10, with the reads it then asks for. */
    private fun opened(): Pair<JabraSession, List<JabraPacket>> {
        val s = JabraSession(elite10)
        s.start()
        s.feed(reply(0x02, 0x11, 0x46, 0x2E))
        s.feed(reply(0x02, 0x03, 5, '1'.code, '.'.code, '2'.code, '.'.code, '4'.code))
        return s to s.takeOutbox()
    }

    private fun setting(session: JabraSession, name: String): JabraSetting = session.profile!!.settings.map { JabraSettings.BY_ID.getValue(it) }.first { it.name == name }

    @Test
    fun theConversationOpensWithTheProductAndTheVersion() {
        val s = JabraSession(elite10)
        val start = s.start()
        assertEquals(listOf("02/11", "02/03", "12/02"), start.map { "%02x/%02x".format(it.main, it.sub) })
        assertTrue(start.all { it.isRead && it.destination == 4 && it.source == 9 })
    }

    @Test
    fun theProductIdAndTheFirmwarePickTheSnapshotAndTheReadsFollow() {
        val (s, reads) = opened()
        assertEquals(11846, s.pid)
        assertEquals("1.2.4", s.firmware)
        assertEquals("11846_01-92_1.2.4", s.profile?.snapshot)
        // The ambience command's five reads, then each setting's read once; the sidetone's two settings share one read at the DSP's address.
        assertEquals(listOf("00", "01", "02", "03", "04"), reads.filter { it.main == 0x13 && it.sub == 0xBE }.map { "%02x".format(it.payload[1]) })
        assertEquals(1, reads.count { it.main == 0x13 && it.sub == 0x7C && it.destination == JabraWire.DSP })
        // Six buttons' gestures in three contexts: eighteen reads of one command, each with its three selector bytes.
        val buttons = reads.filter { it.main == 0x13 && it.sub == 0x9A }
        assertEquals(18, buttons.size)
        assertEquals(18, buttons.map { it.payload.toList() }.toSet().size)
        assertTrue(reads.all { it.isRead })
    }

    @Test
    fun anOlderFirmwareTakesTheNearestEarlierSnapshot() {
        val s = JabraSession(JabraModels.ALL.single { it.name == "Jabra Elite 10 Gen 2" })
        s.start()
        s.feed(reply(0x02, 0x11, 0x59, 0x2E))
        s.feed(reply(0x02, 0x03, 5, '1'.code, '.'.code, '1'.code, '.'.code, '9'.code))
        assertEquals("11865_01-8F_1.1.4", s.profile?.snapshot)
        val old = JabraSession(JabraModels.ALL.single { it.name == "Jabra Elite 10 Gen 2" })
        old.start()
        old.feed(reply(0x02, 0x11, 0x59, 0x2E))
        old.feed(reply(0x02, 0x03, 5, '0'.code, '.'.code, '9'.code, '.'.code, '0'.code))
        assertEquals("11865_01-8F_1.0.8", old.profile?.snapshot)
    }

    @Test
    fun theBatteryPlacesTheCaseByItsAddressAndTheOtherEarbudBesideTheLocalOne() {
        val (s, _) = opened()
        // Flags 0, local level 80, two ADC bytes, two extra units: the right earbud (address 4) at 70 and the cradle (address 0x15) at 50.
        s.feed(reply(0x12, 0x02, 0, 80, 0, 0, 2, 4, 0, 70, 0x15, 0, 50))
        assertEquals(80, s.state.batteryLeft)
        assertEquals(70, s.state.batteryRight)
        assertEquals(50, s.state.batteryCase)
        // The other way: the extra unit is the left earbud (address 5).
        s.feed(reply(0x12, 0x02, 0, 40, 0, 0, 1, 5, 0, 90))
        assertEquals(90, s.state.batteryLeft)
        assertEquals(40, s.state.batteryRight)
    }

    @Test
    fun noiseControlIsTheAmbienceCommandAndItsLevelsRunStrongestFirst() {
        val (s, _) = opened()
        s.feed(reply(0x13, 0xBE, 0x00, 0x07, 0x04, 0x04, 0x00, 0x00, 0x00))
        s.feed(reply(0x13, 0xBE, 0x01, 0x04))
        s.feed(reply(0x13, 0xBE, 0x03, 0x00))
        assertEquals(Command.ANC_MODE.labels.entries.first { it.value == "anc" }.key, s.state.number(Command.ANC_MODE))
        val noise = s.groups.first { it.key == "jb_noise" }.settings
        // ANC level 0 is the strongest on the wire; the screen shows it as the highest step.
        assertEquals(Setting.Level("jb_anc_level", 5, 1, 5), noise.single { it.key == "jb_anc_level" })
        // Choosing off writes the ambience command with its selector, as the native setter does (`be 01 01`).
        val off = Command.ANC_MODE.labels.entries.first { it.value == "off" }.key
        val out = s.set(Command.ANC_MODE, off)!!.single()
        assertEquals("be0101", out.payload.hex())
        assertTrue(out.isWrite)
        assertEquals(off, s.state.number(Command.ANC_MODE))
        val level = s.change(SettingChange.Level("jb_anc_level", 2))!!.single()
        assertEquals("be0303", level.payload.hex())
    }

    @Test
    fun aSwitchOfASharedPayloadChangesOnlyItsOwnByte() {
        val (s, _) = opened()
        // The sidetone: on is 00, off is 01, and the level is the second byte of the same payload.
        s.feed(reply(0x13, 0x7C, 0x01, 0xF7))
        val toggle = s.groups.flatMap { it.settings }.filterIsInstance<Setting.Toggle>().single { it.key == "jb_${setting(s, "SideTone").id}" }
        assertFalse(toggle.on)
        val write = s.change(SettingChange.Toggle(toggle.key, true))!!.single()
        assertEquals(JabraWire.DSP, write.destination)
        assertEquals("7c00f7", write.payload.hex())
        // The level choice keeps the switch as the earbuds last said it.
        val level = setting(s, "SideTone_level")
        val write2 = s.change(SettingChange.Choice("jb_${level.id}", "db_6"))!!.single()
        assertEquals("7c0006", write2.payload.hex())
    }

    @Test
    fun aButtonWriteCarriesItsThreeSelectorsAndTheAction() {
        val (s, _) = opened()
        val tap = setting(s, "Music_context_left_button_tap_action")
        s.feed(reply(0x13, 0x9A, 0, 0, 0, 1))
        val row = s.groups.first { it.key == "jb_buttons_music" }.settings.filterIsInstance<Setting.Choice>().single { it.key == "jb_${tap.id}" }
        assertEquals("Action_Play_Pause", row.value)
        val write = s.change(SettingChange.Choice(row.key, "Action_Next_Track"))!!.single()
        assertEquals("9a00000002", write.payload.hex())
        assertEquals(4, write.destination)
    }

    @Test
    fun nothingOnTheNeverSendListsGoesOut() {
        val (s, _) = opened()
        val factory = JabraPacket.of(4, 1, JabraPacket.WRITE, 0x0D, 0x13)
        assertFalse(s.allowed(factory))
        assertFalse(s.allowed(JabraPacket.of(4, 1, JabraPacket.WRITE, 0x0F, 0x00)))
        assertFalse(s.allowed(JabraPacket.of(4, 1, JabraPacket.READ, 0x07, 0x00)))
        assertTrue(s.allowed(JabraPacket.of(4, 1, JabraPacket.READ, 0x13, 0xBE, 0x01)))
        assertNotNull(s.start().firstOrNull())
        assertNull(s.set(Command.BATTERY_LEFT, 1))
    }
    private fun hexBytes(text: String): ByteArray = text.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun theEqualizerIsWrittenAsTheNativeSetterWritesIt() {
        val (s, reads) = opened()
        assertTrue(reads.any { it.main == 0x13 && it.sub == 0x7D } && reads.any { it.main == 0x13 && it.sub == 0x7E })
        // The descriptor of the native test (research/test_vectors.json): five bands, 6 dB each way, 60 Hz to 7.6 kHz, Q 1.
        val descriptor = hexBytes("05 00 00 18 00 b4 00 00 10 00 18 02 ee 00 00 10 00 18 0b b8 00 00 10 00 18 2e e0 00 00 10 00 18 59 10 00 00 10 00")
        s.feed(JabraPacket(9, 4, 1, JabraPacket.REPLY, 0x13, byteArrayOf(0x7D) + descriptor).bytes())
        assertEquals(listOf(60, 250, 1000, 4000, 7600), s.eqBandsHz)
        assertEquals(6.0, s.eqMaxDb, 0.0)
        // Bass boost is 60 % and -33 % of 6 dB on the first two bands: the second case of the native vectors.
        val bass = s.eqPresets.single { it.first == "BASS_BOOST" }.second
        assertEquals(3.6, bass[0], 1e-9)
        val frames = s.eqCurve(listOf(3.6, -1.98, 0.0, 0.0, 0.0))!!
        // The equalizer is switched on first, then the bands go as the native setter wrote them.
        assertEquals("7e01", JabraParser().feed(frames[0]).single().payload.hex())
        assertEquals("7d05000000" + "00b400d8" + "1000" + "02eeff8a" + "1000" + "0bb8" + "0000" + "1000" + "2ee0" + "0000" + "1000" + "5910" + "0000" + "1000", JabraParser().feed(frames[1]).single().payload.hex())
        val second = s.eqCurve(listOf(-3.6, 0.0, 0.0, 1.02, -1.2))!!.single()
        assertEquals("7d05000000" + "00b4ff28" + "1000" + "02ee0000" + "1000" + "0bb80000" + "1000" + "2ee0003d" + "1000" + "5910ffb8" + "1000", JabraParser().feed(second).single().payload.hex())
        // More than a band's maximum sends nothing.
        assertNull(s.eqCurve(listOf(7.0, 0.0, 0.0, 0.0, 0.0)))
        // A flat curve switches the equalizer off.
        assertEquals("7e00", JabraParser().feed(s.eqCurve(listOf(0.0, 0.0, 0.0, 0.0, 0.0))!!.first()).single().payload.hex())
    }
    @Test
    fun theButtonsSoundModeLoopAndTheMusicMixAreWrittenWhole() {
        val (s, reads) = opened()
        assertTrue(reads.any { it.main == 0x13 && it.sub == 0xBF })
        // Loop of HearThrough and ANC: the SDK's ordinals (off 0, HearThrough 1, ANC 2) after the count, not the selected-mode bits.
        s.feed(reply(0x13, 0xBF, 2, 1, 2))
        s.feed(reply(0x13, 0xBE, 0x00, 0x07, 0x04, 0x04, 0x01, 0x00, 0x00))
        s.feed(reply(0x13, 0xBE, 0x04, 0x20))
        val noise = s.groups.first { it.key == "jb_noise" }.settings
        assertEquals("HT_ANC", (noise.single { it.key == "jb_loop" } as Setting.Choice).value)
        val loop = s.change(SettingChange.Choice("jb_loop", "HT_ANC_Off"))!!.single()
        assertEquals("bf03010200", loop.payload.hex())
        // The mix is bit 0 of the extended settings; the submode bits in the same byte stay.
        assertEquals(false, (noise.single { it.key == "jb_ht_mix" } as Setting.Toggle).on)
        assertEquals("be0421", s.change(SettingChange.Toggle("jb_ht_mix", true))!!.single().payload.hex())
    }
}
