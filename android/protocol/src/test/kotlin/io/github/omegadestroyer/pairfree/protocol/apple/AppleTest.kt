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
    fun theTablesMatchTheData() {
        val devices = load("devices.json").getValue("devices").jsonArray.map { it.jsonObject }
        assertEquals(devices.size, AppleModels.ALL.size)
        assertEquals(Form.HEADPHONES, AppleModels.ALL.first { it.name == "AirPods Max" }.form)
        assertTrue(AppleModels.identify("Kitchen", listOf("74EC2172-0BAD-4D01-8F77-997B2BE0722A")))
        assertTrue(AppleModels.identify("Sam's AirPods Pro", emptyList()))
        assertFalse(AppleModels.identify("Beats Studio Buds", emptyList()), "Beats only by the service")
        assertEquals(4097, AppleWire.PSM)
    }
}
