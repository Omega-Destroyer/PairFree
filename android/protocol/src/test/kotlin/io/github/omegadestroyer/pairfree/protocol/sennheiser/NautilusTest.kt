package io.github.omegadestroyer.pairfree.protocol.sennheiser

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.Value
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

/**
 * Sennheiser's Nautilus headphones (docs/sennheiser/PROTOCOL.md section 4.10). The packet vectors come from a port of Smart Control 4.9.4's `accumulateCrc8` run outside
 * the repo; the replies are built as packets of the same layout. Not tried on headphones.
 */
class NautilusTest {
    private fun model(name: String) = NautilusModels.ALL.first { it.name == name }
    private fun reply(module: Int, sub: Int?, command: Int, payload: ByteArray = ByteArray(0)) = NautilusPacket(module, sub, command, payload).bytes()
    private fun NautilusSession.row(key: String): Setting? = groups.flatMap { it.settings }.firstOrNull { it.key == key }

    /** The header and payload of each frame: what follows the prefix, the length and the checksum. */
    private fun bodies(frames: List<ByteArray>) = frames.map { NautilusParser().feed(it).single().hex() }

    @Test
    fun aPacketIsTheAppsPrefixLengthCrcAndHeader() {
        assertEquals("ebfe0100010000000400690017000900", NautilusPacket(0x17, null, 0x09).packet().hex())
        assertEquals("ebfe01000100000006006100170008000300", NautilusPacket(0x17, null, 0x08, byteArrayOf(3, 0)).packet().hex())
        assertEquals("ebfe01000100000008004e000800ef0001000200", NautilusPacket(0x08, 0xEF, 0x01, byteArrayOf(2, 0)).packet().hex())
        assertEquals("ebfe0100010000000400a50012000300", NautilusPacket(0x12, null, 0x03).packet().hex())
        assertEquals(0x69, NautilusPacket.crc8(byteArrayOf(0x17, 0, 9, 0)))
    }

    @Test
    fun theSerialPortWrapsAPacketAndEscapesTheTwoSpecialBytes() {
        val p = NautilusPacket(0x0A, null, 0x0E, byteArrayOf(0x2A, 0x3F, 0x01))
        val framed = p.bytes()
        assertEquals(0x2A, framed.first().toInt())
        assertEquals(0x2A, framed.last().toInt())
        // Inside, 2A becomes 3F 2F and 3F becomes 3F 3F.
        assertTrue("3f2f3f3f01" in framed.hex(), framed.hex())
        val parser = NautilusParser()
        val got = parser.feed(framed.copyOfRange(0, 7)) + parser.feed(framed.copyOfRange(7, framed.size))
        assertEquals(1, got.size)
        assertEquals(p.packet().copyOfRange(12, p.packet().size).hex(), got.single().hex())
        // A damaged checksum is dropped.
        val bad = framed.copyOf().also { it[12] = (it[12] + 1).toByte() }
        assertTrue(NautilusParser().feed(bad).isEmpty())
    }

    @Test
    fun theMomentum3ShowsItsNoiseCancellingAmbientAndSmartPause() {
        val s = NautilusSession(model("Momentum 3 Wireless"))
        val start = bodies(s.start())
        assertTrue("12000300" in start, "battery percent: $start")
        assertTrue("17001000" in start, "the control type: $start")
        assertTrue("0800ef0001000200" in start, "the smart pause is read as its write with the mode STATUS: $start")
        s.feed(reply(0x12, null, 0x03, byteArrayOf(0x55, 0)))
        assertEquals(Value.Number(85), s.state.values[Command.BATTERY_LEFT])
        s.feed(reply(0x17, null, 0x10, byteArrayOf(1, 3, 0)))
        s.feed(reply(0x17, null, 0x09, byteArrayOf(3, 0)))
        s.feed(reply(0x17, null, 0x0B, byteArrayOf(0)))
        s.feed(reply(0x08, 0xEF, 0x01, byteArrayOf(1)))
        assertEquals(Setting.Choice(NautilusSession.KEY_ANC, "outdoor", listOf("off", "airplane", "outdoor", "home")), s.row(NautilusSession.KEY_ANC))
        assertEquals(Setting.Toggle(NautilusSession.KEY_AMBIENT, false), s.row(NautilusSession.KEY_AMBIENT))
        assertEquals(Setting.Toggle(SennheiserSession.KEY_PAUSE_TRANSPARENT, false), s.row(SennheiserSession.KEY_PAUSE_TRANSPARENT))
        assertEquals(Setting.Toggle(SennheiserSession.KEY_SMART_PAUSE, true), s.row(SennheiserSession.KEY_SMART_PAUSE))
        assertEquals(listOf("170008000400"), bodies(s.change(SettingChange.Choice(NautilusSession.KEY_ANC, "home"))!!))
        assertEquals(listOf("17000900"), bodies(s.takeReadBacks()))
        assertEquals(listOf("17001100"), bodies(s.change(SettingChange.Toggle(NautilusSession.KEY_AMBIENT, true))!!))
        assertEquals(listOf("0800ef0001000000"), bodies(s.change(SettingChange.Toggle(SennheiserSession.KEY_SMART_PAUSE, false))!!))
        assertNull(s.change(SettingChange.Choice(NautilusSession.KEY_ANC, "sideways")))
    }

    @Test
    fun theAmbientSwitchDisablesTheChoiceWhileTheHeadphonesAreOff() {
        val s = NautilusSession(model("Momentum 3 Wireless"))
        s.start()
        s.feed(reply(0x17, null, 0x10, byteArrayOf(0, 0, 0)))
        s.feed(reply(0x17, null, 0x09, byteArrayOf(0, 0)))
        assertEquals(Setting.Toggle(NautilusSession.KEY_AMBIENT, false, enabled = false), s.row(NautilusSession.KEY_AMBIENT))
        assertNull(s.change(SettingChange.Choice(NautilusSession.KEY_ANC, "home")), "the noise cancelling switch is off")
    }

    @Test
    fun thePxcsHaveABatteryOfFiveStepsAndTheirOwnNoiseStates() {
        val s = NautilusSession(model("PXC 550"))
        val start = bodies(s.start())
        assertTrue("12000100" in start, "battery level: $start")
        assertTrue("17000400" in start, "the noise cancelling state: $start")
        s.feed(reply(0x12, null, 0x01, byteArrayOf(0x10, 0x0E, 3, 0)))
        assertEquals(Value.Number(60), s.state.values[Command.BATTERY_LEFT])
        s.feed(reply(0x17, null, 0x04, byteArrayOf(2, 0)))
        assertEquals(Setting.Choice(NautilusSession.KEY_ANC_STATE, "adaptive", listOf("off", "on", "adaptive")), s.row(NautilusSession.KEY_ANC_STATE))
        assertEquals(listOf("170003000100"), bodies(s.change(SettingChange.Choice(NautilusSession.KEY_ANC_STATE, "on"))!!))
        s.feed(reply(0x0A, null, 0x04, byteArrayOf(1, 0)))
        assertEquals(Setting.Toggle(NautilusSession.KEY_CALL_ENHANCEMENT, true), s.row(NautilusSession.KEY_CALL_ENHANCEMENT))
        s.feed(reply(0x0A, null, 0x08, byteArrayOf(4, 0)))
        assertEquals(Setting.Choice(NautilusSession.KEY_PRESET, "DIRECTOR", listOf("NEUTRAL", "CLUB", "MOVIE", "SPEECH", "DIRECTOR")), s.row(NautilusSession.KEY_PRESET))
    }

    @Test
    fun onlyTheProductsOwnTableGoesOut() {
        val s = NautilusSession(model("PXC 550-II"))
        assertTrue(s.allowed(NautilusPacket(0x17, null, 0x09).bytes()))
        assertTrue(s.allowed(NautilusPacket(0x08, 0xEF, 0x01, byteArrayOf(1, 0)).bytes()))
        // Service_GetBtStackVersion and anything with another header are not in the table.
        assertFalse(s.allowed(NautilusPacket(0x01, null, 0x15).bytes()))
        assertFalse(s.allowed(NautilusPacket(0x19, null, 0x01).bytes()))
        assertFalse(s.allowed(byteArrayOf(1, 2, 3)))
        assertEquals(3, NautilusModels.ALL.size)
    }
}
