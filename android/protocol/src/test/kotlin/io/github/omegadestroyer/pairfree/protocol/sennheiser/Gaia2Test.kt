package io.github.omegadestroyer.pairfree.protocol.sennheiser

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.Value
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

private fun String.bytes() = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }

/**
 * Sennheiser's GAIA v2 headphones (docs/sennheiser/PROTOCOL.md section 4.9): the frame, the status byte of a response, and what each product's file lists. The
 * frames are the product files' own command numbers; the headphones' answers are built as Smart Control reads them. Not tried on headphones.
 */
class Gaia2Test {
    private fun model(name: String) = Gaia2Models.ALL.first { it.name == name }
    private fun response(vendor: Int, command: Int, payload: String) = Gaia2Packet(vendor, command or 0x8000, payload.bytes()).bytes()
    private fun words(frames: List<ByteArray>) = frames.map { f -> Gaia2Parser().feed(f).single().let { "%04x:%04x:%s".format(it.vendor, it.command, it.payload.joinToString("") { b -> "%02x".format(b) }) } }
    private fun Gaia2Session.row(key: String): Setting? = groups.flatMap { it.settings }.firstOrNull { it.key == key }

    @Test
    fun aFrameIsFF01AndTheStatusByteStartsAResponse() {
        val frame = Gaia2Packet(0x0494, 0x0706, byteArrayOf(0, 2)).bytes()
        assertEquals("ff01000204940706" + "0002", frame.joinToString("") { "%02x".format(it) })
        val parser = Gaia2Parser()
        // A frame in two pieces, with noise before it.
        assertTrue(parser.feed("0000ff01".bytes()).isEmpty())
        val got = parser.feed("000204940706".bytes() + "0002".bytes()).single()
        assertEquals(0x0706, got.command)
        assertFalse(got.isResponse)
        assertEquals(listOf<Byte>(0, 2), got.payload.toList())
        val answer = Gaia2Parser().feed(response(0x0494, 0x0182, "005a")).single()
        assertTrue(answer.isResponse)
        assertEquals(Gaia2Packet.keyOf(0x0494, 0x0182), answer.key)
    }

    @Test
    fun theHd450ReadsItsBatteryItsModesAndItsKnobs() {
        val s = Gaia2Session(model("HD 450BT"))
        val start = words(s.start())
        assertTrue("0494:0182:" in start, "battery: $start")
        assertTrue("0494:0185:" in start && "0494:0280:" in start && "0494:0786:" in start && "0494:0787:" in start, "charging, version, audio mode and sidetone: $start")
        assertEquals(listOf("0494:0685:0000", "0494:0685:0001"), start.filter { it.startsWith("0494:0685") })
        s.feed(response(0x0494, 0x0182, "0055"))
        assertEquals(Value.Number(85), s.state.values[Command.BATTERY_LEFT])
        s.feed(response(0x0494, 0x0280, "00" + "0001" + "0002" + "0003"))
        assertEquals(Value.Text("HD 450BT_1.2.3"), s.state.values[Command.FIRMWARE_VERSION])
        s.feed(response(0x0494, 0x0786, "000001"))
        s.feed(response(0x0494, 0x0787, "0002"))
        assertEquals(Setting.Toggle(SennheiserSession.KEY_PODCAST, false), s.row(SennheiserSession.KEY_PODCAST))
        assertEquals(Setting.Level(SennheiserSession.KEY_SIDETONE, 2, 0, 2), s.row(SennheiserSession.KEY_SIDETONE))
        assertEquals(listOf("0494:0706:0002"), words(s.change(SettingChange.Toggle(SennheiserSession.KEY_PODCAST, true))!!))
        assertEquals(listOf("0494:0786:"), words(s.takeReadBacks()))
        assertEquals(listOf("0494:0707:01"), words(s.change(SettingChange.Level(SennheiserSession.KEY_SIDETONE, 1))!!))
        // An error status is an answer with nothing in it.
        val fed = s.feed(response(0x0494, 0x0185, "01"))
        assertEquals(listOf<Any>(Gaia2Packet.keyOf(0x0494, 0x0185)), fed.answers)
    }

    @Test
    fun theThreeBandEqualizerIsTheParameterGroupAndTwoKnobs() {
        val s = Gaia2Session(model("HD 350BT"))
        s.start()
        assertNull(s.eqGains)
        val (x, y) = SennheiserEq.threeKnobs(listOf(2.0, -1.0, 3.5), true)
        s.feed(response(0x0494, 0x0685, "00" + "%04x".format(x)))
        s.feed(response(0x0494, 0x0685, "00" + "%04x".format(y)))
        assertTrue(s.eqKnown)
        assertTrue(s.eqBass)
        listOf(2.0, -1.0, 3.5).zip(s.eqGains!!).forEach { (a, b) -> assertTrue(abs(a - b) <= 0.1, "$a vs $b") }
        assertEquals(listOf(50, 1000, 8000), s.eqBandsHz)
        s.feed(response(0x0494, 0x0786, "000002"))
        val sent = words(s.eqCurve(listOf(2.0, -1.0, 3.5))!!)
        // The equalizer sounds in the user equalizer mode: the mode first, then the parameter group (Qualcomm's vendor) and the two knobs.
        assertEquals("0494:0706:0001", sent[0])
        assertTrue(sent[1].startsWith("000a:021b:0009"), sent[1])
        assertEquals("0494:0605:0000%04x".format(x), sent[2])
        assertEquals("0494:0605:0001%04x".format(y), sent[3])
        assertNull(s.eqCurve(listOf(1.0)))
        assertNotNull(Gaia2Session(model("HD 458BT")).eqCurve(listOf(0.0, 0.0, 0.0)), "the HD 458 has the same three bands")
        assertNull(Gaia2Session(model("IE 80S BT")).eqCurve(listOf(0.0, 0.0, 0.0)), "the IE 80 has five")
    }

    @Test
    fun theIe80HasTheCallsTheTimerAndTheButtonButNoBattery() {
        val s = Gaia2Session(model("IE 80S BT"))
        val start = words(s.start())
        assertTrue("000a:0189:00" in start, "the timer is read for timer 0: $start")
        s.feed(response(0x000a, 0x0189, "00" + "00" + "0708"))
        assertEquals(Setting.Choice(SennheiserSession.KEY_AUTO_OFF, "30", listOf("0", "15", "30", "60")), s.row(SennheiserSession.KEY_AUTO_OFF))
        assertEquals(listOf("000a:0109:000e10"), words(s.change(SettingChange.Choice(SennheiserSession.KEY_AUTO_OFF, "60"))!!))
        s.feed(response(0x0494, 0x0186, "0001"))
        assertEquals(Setting.Choice(Gaia2Session.KEY_MULTI_FUNCTION, "EQModes", listOf("VoiceAssistant", "EQModes")), s.row(Gaia2Session.KEY_MULTI_FUNCTION))
        assertEquals(listOf("0494:0106:00"), words(s.change(SettingChange.Choice(Gaia2Session.KEY_MULTI_FUNCTION, "VoiceAssistant"))!!))
        s.feed(response(0x000a, 0x0292, "0001"))
        assertEquals(Setting.Choice(SennheiserSession.KEY_PROMPT_LANGUAGE, "German", listOf("English", "German", "Chinese", "Japanese", "Korean")), s.row(SennheiserSession.KEY_PROMPT_LANGUAGE))
        assertEquals(listOf("000a:0212:04"), words(s.change(SettingChange.Choice(SennheiserSession.KEY_PROMPT_LANGUAGE, "Korean"))!!))
    }

    @Test
    fun onlyTheProductsOwnTableGoesOutAndTheBleOnesAreMarked() {
        val s = Gaia2Session(model("HD 250BT"))
        assertTrue(s.allowed(Gaia2Packet(0x0494, 0x0182).bytes()))
        assertTrue(s.allowed(Gaia2Packet(0x0494, 0x0706, byteArrayOf(0, 1)).bytes()))
        assertFalse(s.allowed(Gaia2Packet(0x0494, 0x0701, byteArrayOf(1)).bytes()), "the audio prompt mode is not in the HD 250's table")
        assertFalse(s.allowed(Gaia2Packet(0x000a, 0x0000).bytes()))
        assertFalse(s.allowed(byteArrayOf(1, 2, 3)))
        assertTrue(Gaia2Models.ALL.filter { it.ble }.map { it.name }.containsAll(listOf("CX 400BT True Wireless", "Momentum True Wireless 2")))
        assertNotNull(Gaia2Models.fromName("hd 450bt"))
        assertEquals(7, Gaia2Models.ALL.count { !it.ble })
    }

    @Test
    fun theIe80HasFiveBandsSetUpByTheAppsOwnWrites() {
        val s = Gaia2Session(model("IE 80S BT"))
        val start = words(s.start())
        assertTrue("000a:0294:" in start, start.toString())
        assertEquals(listOf("000a:029a:0112", "000a:029a:0122", "000a:029a:0132", "000a:029a:0142", "000a:029a:0152"), start.filter { it.startsWith("000a:029a") })
        s.feed(response(0x000a, 0x0294, "0001"))
        // Each answer is the parameter id and a value in sixtieths of a dB, a step above the band below: 2, 2, 1, 4 and 4 dB.
        for ((id, v) in listOf("0112" to 120, "0122" to 0, "0132" to 0xFFC4, "0142" to 240, "0152" to 0xFF4C)) s.feed(response(0x000a, 0x029a, "00" + id + "%04x".format(v)))
        assertTrue(s.eqKnown)
        // The app turns a 16 bit negative into a number with 65535, not 65536 (`r > 32767 && (r -= 65535)`): minus one dB reads as minus 59 sixtieths, and PairFree keeps that.
        val held = s.eqGains!!
        assertEquals(listOf(2.0, 2.0), held.take(2))
        assertEquals(2.0 - 59 / 60.0, held[2], 1e-9)
        assertEquals(listOf(63, 250, 1000, 4000, 16000), s.eqBandsHz)
        val sent = words(s.eqCurve(listOf(2.0, 0.0, -1.0, 3.0, 0.0))!!)
        // The first curve sets each band's filter, Q and frequency (15 writes), then the group (numbers from a port of calculateEqParams) and the store.
        assertEquals("000a:021a:0110000500", sent[0])
        assertEquals("000a:021a:01130b5000", sent[1])
        assertEquals("000a:021a:0111006000", sent[2])
        assertEquals("000a:021b:0006" + "0101fe57" + "01120078" + "0122ff88" + "0132ffc4" + "014200f0" + "0152ff4c" + "0000", sent[15])
        assertEquals("0494:0606:", sent[16])
        assertEquals(17, sent.size)
        // A later curve does not set the filters up again.
        assertEquals(2, words(s.eqCurve(listOf(0.0, 0.0, 0.0, 0.0, 0.0))!!).size)
    }
}
