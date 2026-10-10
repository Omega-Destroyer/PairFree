package io.github.omegadestroyer.soundpeter.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Replays parts of the H3 session in data/reports/S5_0.4.9.txt. */
class EarbudStateTest {
    private fun EarbudState.apply(vararg replies: Pair<Command, String>): EarbudState =
        replies.fold(this) { s, (command, payload) -> s.update(Frame.reply(command.get!! or Wire.REPLY_MASK, payload)) }

    private val connected = EarbudState().apply(
        Command.FIRMWARE_VERSION to "0053355f32303235303630355f56302e342e39",
        Command.BATTERY_LEFT to "0064",
        Command.BATTERY_RIGHT to "0064",
        Command.BATTERY_CASE to "002f",
        Command.ANC_MODE to "0000",
        Command.ANC_LEVEL to "001101120013001400",
        Command.LDAC to "0001",
    )

    @Test
    fun repliesFillTheState() {
        assertEquals("S5", connected.firmware?.code)
        assertEquals(100, connected.batteryLeft)
        assertEquals(47, connected.batteryCase)
        assertEquals(0, connected.number(Command.ANC_MODE))
        assertEquals(true, connected.switch(Command.LDAC))
        assertEquals(Level(0x11, true), connected.levels(Command.ANC_LEVEL).first())
    }

    @Test
    fun onlyCommandsThatAnsweredAreSupported() {
        assertTrue(connected.supports(Command.BATTERY_CASE))
        assertFalse(connected.supports(Command.IN_EAR_DETECTION))
    }

    @Test
    fun unaskedRepliesUpdateTheState() {
        // after SET anc_mode 1 the H3 sent 83 10 00 01 on its own, twice
        val after = connected.apply(Command.ANC_MODE to "0001", Command.ANC_MODE to "0001")
        assertEquals(1, after.number(Command.ANC_MODE))
    }

    @Test
    fun unknownBatteryIsKeptAsUnknown() {
        val during = connected.apply(Command.BATTERY_RIGHT to "00ff")
        assertTrue(during.supports(Command.BATTERY_RIGHT))
        assertNull(during.batteryRight)
    }

    @Test
    fun otherFramesAreIgnored() {
        val eq = FrameParser().feed(Frame.build(Wire.EQ_COMMAND_QUALCOMM, "a10000005d00b41ccc".hexBytes(), Wire.VENDOR_EQ)).single()
        val request = FrameParser().feed(Requests.get(Command.ANC_MODE)).single()
        val unknown = Frame.reply(0x83F0, "0001")
        assertEquals(connected, connected.update(eq).update(request).update(unknown))
    }
}
