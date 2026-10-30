package io.github.omegadestroyer.pairfree.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** The app's report lines must read exactly like the probe's. */
class DeviceReportTest {
    /** The probe's lines at the top of the H3 report: `name  GET  description  raw=..`, or `no reply`. */
    private val probeLines = File(dataDir, "reports/S5_0.4.9.txt").readLines()
        .takeWhile { !it.startsWith("----") }
        .filter { Regex("""^[a-z_]+ +[0-9A-F]{4}  """).containsMatchIn(it) }

    @Test
    fun sameLinesAsTheProbe() {
        assertTrue(probeLines.size >= 29)
        for (expected in probeLines) {
            val command = Command.valueOf(expected.substringBefore(' ').uppercase())
            val payload = expected.substringAfter(" raw=", "").takeIf { it.isNotEmpty() }?.replace(" ", "")?.hexBytes()
            assertEquals(expected, DeviceReport.line(command, payload))
        }
    }

    @Test
    fun gaiaLineIsTheOneInTheReport() {
        // The line tools/gen_models_table.py reads, as recorded for the H3.
        val features = EarbudState().update(Frame(Gaia.VENDOR, 0x0101, "000d010c010901050108070103060207010005".hexBytes())).gaiaFeatures!!
        val recorded = File(dataDir, "reports/S5_0.4.9.txt").readLines().single { it.startsWith("gaia_features") }
        assertEquals(recorded, DeviceReport.gaiaLine(features))
    }

    @Test
    fun errorsAndEdgeCasesReadLikeTheProbe() {
        // tools/test_probe.py, class Describe
        assertEquals("'S99_xx_1.2.3'", DeviceReport.describe(Command.FIRMWARE_VERSION, "\u0000S99_xx_1.2.3".encodeToByteArray()))
        assertEquals("1", DeviceReport.describe(Command.GAME_MODE, "0001".hexBytes()))
        assertEquals("status 1 (not supported or error)", DeviceReport.describe(Command.GAME_MODE, "0100".hexBytes()))
        assertEquals("double L -> next, hold L -> ANC", DeviceReport.describe(Command.CUSTOM_KEYS, "0003060707".hexBytes()))
        assertTrue(DeviceReport.describe(Command.ANC_LEVEL, "0012001301".hexBytes()).startsWith("0x12, 0x13*"))
        assertTrue(DeviceReport.describe(Command.ANC_LEVEL, "12001301".hexBytes()).startsWith("0x12, 0x13*"))
        assertEquals("(empty)", DeviceReport.describe(Command.BATTERY_LEFT, ByteArray(0)))
    }

    @Test
    fun oneLinePerAskedCommand() {
        val lines = DeviceReport.lines(mapOf(Command.BATTERY_LEFT to "0064".hexBytes()))
        assertEquals(Requests.ON_CONNECT.size, lines.size)
        assertEquals("battery_left         0306  100                                      raw=00 64", lines[1])
        assertEquals("phone_codec          0377  no reply", lines.last())
    }

    @Test
    fun repliesWithoutAStatusByteDecode() {
        // GitHub issues #5 and #6: the C30 and Clip C10 send the firmware and the custom keys with no status byte.
        val c30Firmware = "53 32 32 5f 32 30 32 35 31 32 30 39 5f 76 30 2e 37 2e 35".hexBytes(" ")
        val text = Replies.decode(Command.FIRMWARE_VERSION, c30Firmware) as Value.Text
        assertEquals("S22", FirmwareVersion(text.text).code)
        assertEquals("0.7.5", FirmwareVersion(text.text).version)
        assertEquals("T2", FirmwareVersion("T2_20250506_V0.5.5").code)
        assertEquals("0.5.5", FirmwareVersion("T2_20250506_V0.5.5").version)

        val c30Keys = "01 02 02 01 03 07 04 03 05 04 06 08 07 05 08 06".hexBytes(" ")
        assertEquals(
            "tap L -> vol-, tap R -> vol+, double L -> ANC, double R -> play/pause, triple L -> game mode, " +
                "triple R -> assistant, hold L -> prev, hold R -> next",
            DeviceReport.describe(Command.CUSTOM_KEYS, c30Keys),
        )
        val keys = (Replies.decode(Command.CUSTOM_KEYS, c30Keys) as Value.Keys).keys
        assertEquals(KeyBinding(1, 2), keys.first(), "the app reads the same pairs")
        // The H3's reply, with its status byte, still reads as before.
        assertEquals("tap L -> vol+", DeviceReport.describe(Command.CUSTOM_KEYS, "00 01 01".hexBytes(" ")))
    }
}

private fun String.hexBytes(separator: String): ByteArray = split(separator).map { it.toInt(16).toByte() }.toByteArray()
