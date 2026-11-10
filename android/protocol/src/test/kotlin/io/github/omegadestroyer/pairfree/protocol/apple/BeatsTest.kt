package io.github.omegadestroyer.pairfree.protocol.apple

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.Value
import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The B2P frames and a session, from source analysis of Apple's Beats app (docs/apple/B2P.md); no Beats has answered yet. */
class BeatsTest {
    private fun anc(name: String) = Command.ANC_MODE.labels.entries.first { it.value == name }.key

    @Test
    fun theCrcIsCksumsCheckValue() {
        // MSB-first, polynomial 04C11DB7, start 0, inverted result is POSIX cksum's CRC: "123456789" gives 765E7680.
        assertEquals(0x765E7680, B2pFrame.crc32("123456789".toByteArray()))
    }

    @Test
    fun aFrameRoundTripsAndCountsItsWholeLength() {
        val f = B2pFrame(1, 0x015A, byteArrayOf(2, 0x64))
        val b = f.bytes()
        assertEquals(13, b.size)
        assertEquals(13, b[2].toInt())
        assertEquals(0xFF, b[0].toInt() and 0xFF)
        assertEquals(0xB2, b[1].toInt() and 0xFF)
        assertEquals(0x5A, b[5].toInt())
        assertEquals(f, B2pFrame.parse(b))
        b[7] = 1
        assertNull(B2pFrame.parse(b))
    }

    @Test
    fun splitsFramesThatArriveTogetherOrInPieces() {
        val a = B2pFrame(1, 0x0153, byteArrayOf(0, 0, 80, 0, 0)).bytes()
        val b = B2pFrame(2, 0x013B, byteArrayOf(0x16, 0x20)).bytes()
        val (frames, rest) = B2pFrame.split(a + b + a.copyOf(5))
        assertEquals(2, frames.size)
        assertEquals(5, rest.size)
        assertTrue(B2pFrame.split(rest + a.copyOfRange(5, a.size)).first.size == 1)
    }

    @Test
    fun opcodesAreInTheOpcodeTable() {
        val names = Json.parseToJsonElement(repoFile("data/apple/b2p_opcodes.json").readText()).jsonObject.getValue("opcodes").jsonObject
        fun name(op: Int) = names.getValue("%04X".format(op)).jsonPrimitive.content
        assertEquals("B2P_CMD_BATT_GET", name(BeatsSession.BATTERY_GET))
        assertEquals("B2P_CMD_PRODUCT_ID_GET", name(BeatsSession.PRODUCT_ID_GET))
        assertEquals("B2P_CMD_LISTENING_MODE_LEVEL_GET", name(BeatsSession.LISTENING_MODE_GET))
        assertEquals("B2P_CMD_LISTENING_MODE_LEVEL_SET", name(BeatsSession.LISTENING_MODE_SET))
        assertEquals("B2P_CMD_LISTENING_MODE_LEVEL_NOTIFY", name(BeatsSession.LISTENING_MODE_NOTIFY))
        for (op in BeatsSession.ALLOWED) assertFalse(name(op).contains("FWUP") || name(op).contains("FACTORY") || name(op).contains("DFU"), name(op))
    }

    @Test
    fun theSessionShowsWhatTheBeatsAnswered() {
        val s = BeatsSession(AppleModels.b2p("Beats Fit Pro"))
        val start = s.startFrames()
        assertEquals(listOf(0x013A, 0x0152, 0x0158, 0x0158, 0x014C, 0x047C, 0x0150), start.map { it.opcode })
        assertEquals(listOf(1, 2), start.filter { it.opcode == 0x0158 }.map { it.payload[0].toInt() })
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), start.map { it.sequence })
        assertTrue(start.all { s.allowed(it) })
        assertTrue(s.receive(B2pFrame(2, 0x0153, byteArrayOf(0, 0, 77, 0, 0))))
        assertEquals(Value.Number(77), s.state.values[Command.BATTERY_LEFT])
        assertTrue(s.groups.isEmpty())
        s.receive(B2pFrame(3, 0x0159, byteArrayOf(0)))
        s.receive(B2pFrame(4, 0x0159, byteArrayOf(2)))
        assertEquals(Value.Number(anc("transparency")), s.state.values[Command.ANC_MODE])
        s.receive(B2pFrame(5, 0x014D, byteArrayOf(2)))
        val toggle = s.groups.flatMap { it.settings }.single() as Setting.Toggle
        assertTrue(toggle.on)
        assertEquals(listOf(B2pFrame(8, 0x014A, byteArrayOf(1))), s.changeFrames(SettingChange.Toggle("ear_detection", false)))
        val set = s.setFrames(Command.ANC_MODE, anc("anc"))
        assertNotNull(set)
        assertArrayEquals(byteArrayOf(1, 0x64), set!!.single().payload)
        assertEquals(0x015A, set.single().opcode)
        assertEquals(Value.Number(anc("anc")), s.state.values[Command.ANC_MODE])
        assertFalse(s.allowed(B2pFrame(1, 0x0154)))
        assertFalse(s.allowed(B2pFrame(1, 0x0008)))
    }

    @Test
    fun readsOnlyWhatTheModelListsAndShowsTheOtherSettings() {
        // Studio Buds +: ANC and a microphone, but no in-ear detection or one-bud noise cancelling in Apple's feature list.
        val buds = BeatsSession(AppleModels.b2p("Beats Studio Buds +"))
        assertEquals(listOf(0x013A, 0x0152, 0x0158, 0x0158, 0x0150), buds.startFrames().map { it.opcode })
        // Solo 4 lists no setting at all.
        assertEquals(listOf(0x013A, 0x0152), BeatsSession(AppleModels.b2p("Beats Solo 4")).startFrames().map { it.opcode })
        val s = BeatsSession(AppleModels.b2p("Powerbeats Pro 2"))
        assertEquals(listOf(0x013A, 0x0152, 0x0158, 0x0158, 0x014C, 0x047C, 0x0150, 0x050C), s.startFrames().map { it.opcode })
        s.receive(B2pFrame(1, 0x047D, byteArrayOf(1)))
        s.receive(B2pFrame(2, 0x0151, byteArrayOf(2)))
        s.receive(B2pFrame(3, 0x050D, byteArrayOf(0)))
        val keys = s.groups.flatMap { it.settings }
        assertEquals(listOf("noise_one_earbud", "microphone", "in_case_tone"), keys.map { it.key })
        assertEquals("left", (keys[1] as Setting.Choice).value)
        assertEquals(listOf(B2pFrame(1, 0x014E, byteArrayOf(1))).map { it.opcode }, s.changeFrames(SettingChange.Choice("microphone", "right"))!!.map { it.opcode })
        assertEquals(0x047E, s.changeFrames(SettingChange.Toggle("noise_one_earbud", false))!!.single().opcode)
        assertEquals(0x050A, s.changeFrames(SettingChange.Toggle("in_case_tone", true))!!.single().opcode)
    }

    @Test
    fun namesOfBeatsThatSpeakB2p() {
        assertEquals("Beats Studio Buds +", AppleModels.b2p("Beats Studio Buds+")?.name)
        assertEquals("Beats Studio Buds", AppleModels.b2p("Nima's Beats Studio Buds")?.name)
        assertEquals("Powerbeats Pro 2", AppleModels.b2p("Powerbeats Pro 2")?.name)
        assertEquals("Powerbeats Pro", AppleModels.b2p("Powerbeats Pro")?.name)
        assertNull(AppleModels.b2p("Beats Flex"))
        assertNull(AppleModels.b2p("AirPods Pro"))
    }
}
