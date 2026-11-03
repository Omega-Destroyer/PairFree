package io.github.omegadestroyer.pairfree.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoggedReadsTest {
    private fun read(time: Long, command: Int) = SnoopScanner.Found(time, false, 3, Frame(Wire.VENDOR_MAIN, command, ByteArray(0)))
    private fun reply(time: Long, command: Int, payload: ByteArray) =
        SnoopScanner.Found(time, true, 3, Frame(Wire.VENDOR_MAIN, command or Wire.REPLY_MASK, payload))

    /** GitHub issue #10: SOUNDPEATS 2.5.3 read the C30's list of phones, and the recording posted it. */
    @Test
    fun theOfficialAppsReadOfOtherPhonesIsLeftOut() {
        val history = Command.DEVICE_HISTORY.get!!
        // Made-up entry: number, connected, address, name.
        val phones = byteArrayOf(1, 1, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66) + "phone".encodeToByteArray() + 0
        val frames = listOf(
            read(1, 0x0309),
            reply(2, 0x0309, "S22_20251209_v0.7.5".encodeToByteArray()),
            read(3, 0x0306),
            reply(4, 0x0306, byteArrayOf(0, 0x64)),
            read(5, history),
            reply(6, history, phones),
        )
        val earbuds = LoggedReads.of(frames).single()
        assertTrue(Command.BATTERY_LEFT in earbuds.replies)
        assertFalse(Command.DEVICE_HISTORY in earbuds.replies)
        assertFalse(earbuds.lines().any { "device_history" in it || "11 22 33" in it })
        assertEquals(listOf(Command.DEVICE_HISTORY), Command.entries.filter { it.private })
    }
}
