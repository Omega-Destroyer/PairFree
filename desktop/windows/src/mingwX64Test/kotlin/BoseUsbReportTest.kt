package io.github.omegadestroyer.pairfree.windows

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class BoseUsbReportTest {
    @Test
    fun wrapsTheQcUltra2BatteryReadWithItsBigEndianLength() {
        val query = byteArrayOf(0x02, 0x02, 0x01, 0x00)
        val report = BoseUsbReport.wrap(query, 1023)
        assertContentEquals(byteArrayOf(0x0c, 0x00, 0x04, 0x02, 0x02, 0x01, 0x00).copyOf(1023), report)
        assertContentEquals(byteArrayOf(0x0c, 0x01, 0x03), BoseUsbReport.wrap(ByteArray(259), 1023).copyOf(3))
    }

    @Test
    fun stripsTheInputReportIdAndPadding() {
        val reply = byteArrayOf(0x02, 0x02, 0x03, 0x04, 100, -1, -1, 0)
        val report = (byteArrayOf(0x0d) + reply).copyOf(676)
        assertContentEquals(reply, BoseUsbReport.unwrap(report, 676))
        assertNull(BoseUsbReport.unwrap(report, 8), "truncated payload")
        report[0] = 0x01
        assertNull(BoseUsbReport.unwrap(report, 676), "another kind of report")
    }
}
