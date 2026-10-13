package io.github.omegadestroyer.soundpeter.protocol

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
}
