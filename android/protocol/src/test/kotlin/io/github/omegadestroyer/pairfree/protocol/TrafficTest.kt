package io.github.omegadestroyer.pairfree.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrafficTest {
    private fun sent(frame: ByteArray) = FrameParser().feed(frame).single().summary()

    @Test
    fun namesGetsSetsAndReplies() {
        assertEquals("000A 0310 anc_mode get", sent(Requests.get(Command.ANC_MODE)))
        assertEquals("000A 034D transparency_level set 02", sent(Requests.set(Command.TRANSPARENCY_LEVEL, 2)))
        assertEquals("000A 834C transparency_level reply 00 01 00 02 01", Frame.reply(0x834C, "0001000201").summary())
    }

    @Test
    fun eqAndUnknownFrames() {
        val eq = Frame.build(Wire.EQ_COMMAND_QUALCOMM, "910000".hexBytes(), Wire.VENDOR_EQ)
        assertEquals("001D 0E01 eq 91 00 00", sent(eq))
        assertEquals("000A 83F0 unknown 00", Frame.reply(0x83F0, "00").summary())
    }
}
