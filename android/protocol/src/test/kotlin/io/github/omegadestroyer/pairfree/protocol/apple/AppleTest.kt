package io.github.omegadestroyer.pairfree.protocol.apple

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.hexBytes
import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun load(name: String): JsonObject = Json.parseToJsonElement(repoFile("data/apple/$name").readText()).jsonObject

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

private fun hex(bytes: ByteArray) = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

/** Packets and a session, against the packets LibrePods documents from AirPods Pro 2. */
class AppleTest {
    private val vectors = load("test_vectors.json")
    private val requests = vectors.getValue("requests").jsonArray.associate { it.jsonObject.str("name") to it.jsonObject.str("hex") }
    private val replies = vectors.getValue("replies").jsonArray.associate { it.jsonObject.str("name") to it.jsonObject }

    private fun reply(name: String) = replies.getValue(name).str("hex").hexBytes()

    private fun setting(s: AppleSession, key: String) = s.groups.flatMap { it.settings }.firstOrNull { it.key == key }

    private fun anc(name: String): Int = Command.ANC_MODE.labels.entries.first { it.value == name }.key

    @Test
    fun theOpeningFramesAreLibrePodsOwn() {
        val start = AppleSession().start()
        assertEquals(listOf(requests["handshake"], requests["feature_flags"], requests["notifications"]), start.map(::hex))
        assertTrue(start.all(AppleSession()::allowed))
    }

    @Test
    fun controlCommandsAreBuiltByteForByte() {
        assertEquals(requests["listening_mode_transparency"], hex(AapPacket.control(AppleControl.LISTENING_MODE, 3).bytes()))
        assertEquals(requests["conversation_awareness_on"], hex(AapPacket.control(AppleControl.CONVERSATION_AWARENESS, AppleWire.ON).bytes()))
        assertEquals(requests["adaptive_level_50"], hex(AapPacket.control(AppleControl.ADAPTIVE_LEVEL, 50).bytes()))
        val parsed = AapPacket.parse(requests.getValue("adaptive_level_50").hexBytes())!!
        assertEquals(AppleControl.ADAPTIVE_LEVEL, parsed.control!!.first)
        assertArrayEquals(byteArrayOf(50), parsed.control!!.second)
    }

    @Test
    fun theBatteryNamesEachSide() {
        val s = AppleSession()
        assertTrue(s.receive(reply("battery")))
        val r = replies.getValue("battery")
        assertEquals(r.getValue("left").jsonPrimitive.int, s.state.batteryLeft)
        assertEquals(r.getValue("right").jsonPrimitive.int, s.state.batteryRight)
        assertEquals(r.getValue("case").jsonPrimitive.int, s.state.batteryCase)
    }

    @Test
    fun aDisconnectedBatteryIsUnknownAndOneBatteryIsHeadphones() {
        val s = AppleSession()
        // Two batteries: right 80 % disconnected, case 50 % charging.
        s.receive("0400040004000202015004010801320101".hexBytes())
        assertNull(s.state.batteryRight)
        assertEquals(50, s.state.batteryCase)
        val max = AppleSession()
        max.receive("04000400040001010146020 1".replace(" ", "").hexBytes())
        assertEquals(0x46, max.state.batteryLeft)
        assertFalse(max.state.supports(Command.BATTERY_RIGHT))
    }

    @Test
    fun theListeningModeGoesToTheSharedNoiseControl() {
        val s = AppleSession()
        assertFalse(s.state.supports(Command.ANC_MODE))
        s.receive(reply("listening_mode_transparency"))
        assertEquals(anc("transparency"), s.state.number(Command.ANC_MODE))
        assertEquals("0400040009000d02000000", hex(s.set(Command.ANC_MODE, anc("anc"))!!.single().bytes()))
        assertEquals(anc("anc"), s.state.number(Command.ANC_MODE))
        // No Adaptive switch before the model is known to have it.
        assertNull(setting(s, "adaptive"))
    }

    @Test
    fun theInformationPacketNamesTheModelAndAdaptiveFollows() {
        val s = AppleSession()
        s.receive(reply("information"))
        val info = replies.getValue("information")
        assertEquals(info.str("model_number"), s.modelNumber)
        assertEquals("AirPods Pro 2 (USB-C)", s.model?.name)
        assertEquals(info.str("firmware"), s.firmware)
        assertEquals("AirPods Pro 2 (USB-C)", s.state.firmware?.code)
        s.receive(reply("listening_mode_transparency"))
        assertEquals(Setting.Toggle("adaptive", false), setting(s, "adaptive"))
        assertEquals("0400040009000d04000000", hex(s.change(SettingChange.Toggle("adaptive", true))!!.single().bytes()))
        // Switching Adaptive off goes back to the mode before it.
        assertEquals("0400040009000d03000000", hex(s.change(SettingChange.Toggle("adaptive", false))!!.single().bytes()))
    }

    @Test
    fun reportedSettingsBecomeRows() {
        val s = AppleSession()
        assertTrue(s.groups.isEmpty())
        s.receive(reply("conversation_awareness_on"))
        s.receive(AapPacket.control(AppleControl.PRESS_SPEED, 1).bytes())
        s.receive(AapPacket.control(AppleControl.EAR_DETECTION, AppleWire.OFF).bytes())
        assertEquals(Setting.Toggle("conversation_awareness", true), setting(s, "conversation_awareness"))
        assertEquals(Setting.Choice("press_speed", "slower", listOf("default", "slower", "slowest")), setting(s, "press_speed"))
        assertEquals(Setting.Toggle("ear_detection", false), setting(s, "ear_detection"))
        assertEquals("0400040009002802000000", hex(s.change(SettingChange.Toggle("conversation_awareness", false))!!.single().bytes()))
        assertEquals("0400040009001702000000", hex(s.change(SettingChange.Choice("press_speed", "slowest"))!!.single().bytes()))
        assertNull(s.change(SettingChange.Toggle("volume_swipe", true)), "not reported, so not offered")
    }

    @Test
    fun whatIsNeverSentIsRefused() {
        val s = AppleSession()
        assertFalse(s.allowed(AapPacket(0x30, byteArrayOf(5)).bytes()), "the keys request")
        assertFalse(s.allowed(AapPacket(0x1A).bytes()), "renaming")
        assertFalse(s.allowed(AapPacket.control(0x06, 1).bytes()), "owning the connection")
        assertTrue(s.allowed(AapPacket.control(AppleControl.LISTENING_MODE, 2).bytes()))
        assertFalse(s.allowed("0102".hexBytes()), "anything without the header but the handshake")
    }

    @Test
    fun librePodsOtherControlsBecomeRows() {
        val s = AppleSession()
        s.receive(AapPacket.control(AppleControl.ALLOW_OFF, AppleWire.ON).bytes())
        // Press and hold cycles through noise cancelling and transparency.
        s.receive(AapPacket.control(AppleControl.LISTENING_MODE_CONFIGS, 0x06).bytes())
        s.receive(AapPacket.control(AppleControl.MIC_MODE, 2).bytes())
        s.receive(AapPacket.control(AppleControl.CALL_MANAGEMENT, 0, 3).bytes())
        s.receive(AapPacket.control(AppleControl.CHIME_VOLUME, 0x46, 0x50).bytes())
        s.receive(AapPacket.control(AppleControl.SLEEP_DETECTION, AppleWire.ON).bytes())

        assertEquals(Setting.Toggle("cycle_off", false), setting(s, "cycle_off"), "off is allowed")
        assertEquals(Setting.Toggle("cycle_anc", true), setting(s, "cycle_anc"))
        assertNull(setting(s, "cycle_adaptive"), "no Adaptive on an unknown model")
        assertNull(s.change(SettingChange.Toggle("cycle_anc", false)), "the cycle keeps two modes")
        assertEquals("0400040009001a07000000", hex(s.change(SettingChange.Toggle("cycle_off", true))!!.single().bytes()))

        assertEquals(Setting.Choice("microphone", "left", listOf("automatic", "right", "left")), setting(s, "microphone"))
        assertEquals("0400040009000100000000", hex(s.change(SettingChange.Choice("microphone", "automatic"))!!.single().bytes()))
        assertEquals(Setting.Choice("call_press_once", "mute", listOf("mute", "hang_up")), setting(s, "call_press_once"))
        assertEquals("0400040009002400020000", hex(s.change(SettingChange.Choice("call_press_once", "hang_up"))!!.single().bytes()))
        assertEquals(Setting.Level("chime_volume", 70, 15, 100), setting(s, "chime_volume"))
        assertEquals("0400040009001f28500000", hex(s.change(SettingChange.Level("chime_volume", 40))!!.single().bytes()))
        assertEquals("0400040009001f0f500000", hex(s.change(SettingChange.Level("chime_volume", 3))!!.single().bytes()), "no lower than 15")
        assertEquals("0400040009003502000000", hex(s.change(SettingChange.Toggle("sleep_detection", false))!!.single().bytes()))
        s.receive(AapPacket.control(AppleControl.CROWN_REVERSED, AppleWire.OFF).bytes())
        assertEquals("0400040009001c01000000", hex(s.change(SettingChange.Toggle("crown_reversed", true))!!.single().bytes()))
        assertNull(setting(s, "in_case_tone"), "not reported")
    }

    @Test
    fun callControlIsWrittenInTheLayoutTheAirPodsReported() {
        // MagicPodsCore's layout: 21, mute on a single press (23), end call on a double press (02).
        val s = AppleSession()
        s.receive(AapPacket.control(AppleControl.CALL_MANAGEMENT, 0x21, 0x23, 0x02).bytes())
        assertEquals(Setting.Choice("call_press_once", "mute", listOf("mute", "hang_up")), setting(s, "call_press_once"))
        assertEquals("0400040009002421220300", hex(s.change(SettingChange.Choice("call_press_once", "hang_up"))!!.single().bytes()))
        assertEquals("hang_up", (setting(s, "call_press_once") as Setting.Choice).value)
        // The other: 20, then 02 (press once to hang up).
        val t = AppleSession()
        t.receive(AapPacket.control(AppleControl.CALL_MANAGEMENT, 0x20, 0x02).bytes())
        assertEquals("hang_up", (setting(t, "call_press_once") as Setting.Choice).value)
        assertEquals("0400040009002420030000", hex(t.change(SettingChange.Choice("call_press_once", "mute"))!!.single().bytes()))
    }

    @Test
    fun theTablesMatchTheData() {
        val devices = load("devices.json").getValue("devices").jsonArray.map { it.jsonObject }
        assertEquals(devices.size, AppleModels.ALL.size)
        assertEquals(Form.HEADPHONES, AppleModels.ALL.first { it.name == "AirPods Max" }.form)
        assertTrue(AppleModels.identify("Kitchen", listOf("74EC2172-0BAD-4D01-8F77-997B2BE0722A")))
        assertTrue(AppleModels.identify("Sam's AirPods Pro", emptyList()))
        assertFalse(AppleModels.identify("Beats Studio Buds", emptyList()), "Beats only by the service")
        assertEquals(4097, AppleWire.PSM)
    }

    @Test
    fun theCustomEqOnNewerFirmware() {
        val s = AppleSession()
        assertTrue(s.groups.none { it.key == "sound" }, "nothing before the AirPods report it")
        // As LibrePods reads it: a length of 5, 01, on (2), then low 60, mid 50 and high 30 of 100.
        s.receive(AapPacket(AppleOpcode.CUSTOM_EQ, byteArrayOf(5, 0, 1, 2, 60, 50, 30)).bytes())
        val rows = s.groups.single { it.key == "sound" }.settings
        assertEquals(Setting.Toggle("custom_eq", true), rows[0])
        assertEquals(listOf(10, 0, -20), rows.drop(1).map { (it as Setting.Level).value })
        // Mid up by 5: the whole packet again, as LibrePods' CustomEq.toPacket writes it.
        val w = s.change(SettingChange.Level("eq_mid", 5))!!.single()
        assertEquals("04000400" + "6300" + "050001023c371e", w.bytes().joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') })
        assertTrue(s.allowed(w.bytes()))
        // Off: the bands stay in the packet and leave the screen.
        val off = s.change(SettingChange.Toggle("custom_eq", false))!!.single()
        assertEquals("050001013c371e", off.data.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') })
        assertEquals(1, s.groups.single { it.key == "sound" }.settings.size)
    }
}
