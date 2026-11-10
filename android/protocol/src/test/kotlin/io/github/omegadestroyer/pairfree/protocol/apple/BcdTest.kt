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

/** The BCD frames and a session, from source analysis of libBcdAdapter.so (docs/apple/B2P.md); no Beats has answered yet. */
class BcdTest {
    private fun anc(name: String) = Command.ANC_MODE.labels.entries.first { it.value == name }.key

    @Test
    fun theCrcIsSaeJ1850WithoutInversion() {
        // Table-driven, polynomial 1D, start 0: the table's first entries in the library are 00 1D 3A 27 74 69 4E 53.
        assertEquals(0x1D, BcdFrame.crc8(byteArrayOf(1)))
        assertEquals(0x3A, BcdFrame.crc8(byteArrayOf(2)))
        assertEquals(0x27, BcdFrame.crc8(byteArrayOf(3)))
        assertEquals(0x74, BcdFrame.crc8(byteArrayOf(4)))
    }

    @Test
    fun aFrameRoundTripsAndAnAckIsFourBytes() {
        val f = BcdFrame(7, 0x0226, byteArrayOf(2))
        val b = f.bytes()
        assertEquals(0xAA, b[0].toInt() and 0xFF)
        assertEquals(3, b[1].toInt())
        assertEquals(7, b[2].toInt())
        assertEquals(0x02, b[3].toInt())
        assertEquals(0x26, b[4].toInt())
        assertEquals(f, BcdFrame.parse(b))
        val ack = BcdFrame(7, 0, ack = true).bytes()
        assertEquals(4, ack.size)
        assertEquals(BcdFrame(7, 0, ack = true), BcdFrame.parse(ack))
        b[5] = 9
        assertNull(BcdFrame.parse(b))
        val (frames, rest) = BcdFrame.split(b.copyOf() + ack + ack.copyOf(2))
        assertEquals(2, frames.size)
        assertEquals(2, rest.size)
    }

    @Test
    fun opcodesAreInTheOpcodeTable() {
        val names = Json.parseToJsonElement(repoFile("data/apple/bcd_opcodes.json").readText()).jsonObject.getValue("opcodes").jsonObject
        fun name(op: Int) = names.getValue("%04X".format(op)).jsonPrimitive.content
        assertEquals("BCD_RSP_GET_PRODUCT_ID", name(BcdSession.PRODUCT_ID_GET + 1))
        assertEquals("BCD_CMD_GET_LISTENING_MODE", name(BcdSession.LISTENING_MODE_GET))
        assertEquals("BCD_CMD_SET_LISTENING_MODE", name(BcdSession.LISTENING_MODE_SET))
        assertEquals("BCD_CMD_GET_AUTO_ANSWER_MODE", name(BcdSession.AUTO_ANSWER_GET))
        assertEquals("BCD_CMD_SET_AUTO_ANSWER_MODE", name(BcdSession.AUTO_ANSWER_SET))
    }

    @Test
    fun theSessionShowsWhatTheBeatsAnswered() {
        val studio = BcdSession(AppleModels.bcd("Beats Studio3 Wireless"))
        assertEquals("Beats Studio3", studio.model?.name)
        val start = studio.start().map { BcdFrame.parse(it)!! }
        assertEquals(listOf(0x0000, 0x01A4), start.map { it.command })
        // The reply to the listening mode read: 2 is on. Every frame from the Beats is acknowledged.
        assertFalse(studio.receiveBytes(BcdFrame(1, 0x0001, byteArrayOf(0x09, 0x20)).bytes()).also { assertEquals(1, studio.outgoing.size) })
        assertTrue(studio.receiveBytes(BcdFrame(2, 0x01A5, byteArrayOf(2)).bytes()))
        assertEquals(BcdFrame(2, 0, ack = true), BcdFrame.parse(studio.outgoing.single()))
        assertEquals(Value.Number(anc("anc")), studio.state.values[Command.ANC_MODE])
        val off = studio.set(Command.ANC_MODE, anc("off"))!!.map { BcdFrame.parse(it)!! }.single()
        assertEquals(0x0226, off.command)
        assertArrayEquals(byteArrayOf(1), off.payload)
        assertEquals(Value.Number(anc("off")), studio.state.values[Command.ANC_MODE])
        assertNull(studio.set(Command.ANC_MODE, anc("transparency")))
        assertTrue(studio.groups.isEmpty())
    }

    @Test
    fun theFlexAlsoReadsAutoAnswer() {
        val flex = BcdSession(AppleModels.bcd("Beats Flex"))
        assertEquals(listOf(0x0000, 0x01A4, 0x024A), flex.start().map { BcdFrame.parse(it)!!.command })
        flex.receiveBytes(BcdFrame(1, 0x024B, byteArrayOf(1)).bytes())
        val toggle = flex.groups.flatMap { it.settings }.single() as Setting.Toggle
        assertTrue(toggle.on)
        val off = BcdFrame.parse(flex.change(SettingChange.Toggle("auto_answer", false))!!.single())!!
        assertEquals(0x0248, off.command)
        assertArrayEquals(byteArrayOf(2), off.payload)
        assertNotNull(AppleModels.bcd("Powerbeats3 Wireless"))
        assertNull(AppleModels.bcd("Powerbeats Pro"))
        assertEquals("Powerbeats Pro", AppleModels.b2p("Powerbeats Pro")?.name)
    }
}
