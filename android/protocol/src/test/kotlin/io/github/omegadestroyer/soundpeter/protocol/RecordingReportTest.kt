package io.github.omegadestroyer.soundpeter.protocol

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** A full recording of an H3 must fit in a GitHub new-issue link once compacted, and stay readable by the tools. */
class RecordingReportTest {
    private val firmware = FirmwareVersion("S5_20250605_V0.4.9")

    /** Every reply PairFree got from the H3 on connect (the last section of its report). */
    private val h3Replies: Map<Command, ByteArray> = File(dataDir, "reports/S5_0.4.9.txt").readLines()
        .dropWhile { !it.startsWith("---- Every read SoundPeter sends") }
        .mapNotNull { Regex("""^([a-z_]+) +[0-9A-F]{4}  raw=(.*)$""").find(it) }
        .associate { m -> Command.valueOf(m.groupValues[1].uppercase()) to m.groupValues[2].replace(" ", "").hexBytes() }

    private val gaia = setOf(0x00, 0x01, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0C, 0x0D)

    /** The H3's base curve and its eleven presets, as PeatsAudio sent them. */
    private val curves: List<Pair<String, EqCapture.Write>> = run {
        val v = loadData("test_vectors.json")
        val reset = v.getValue("eq_layout").jsonArray.map { it.jsonObject }.first { it.str("model") == "S5" && it.str("name").startsWith("Reset") }
        val presets = v.getValue("eq_preset").jsonArray.map { it.jsonObject }.filter { it.str("model") == "S5" }
        (listOf("SOUNDPEATS Classic" to reset) + presets.map { it.str("preset") to it }).map { (name, case) ->
            name to EqCapture.Write(0, Wire.EQ_COMMAND_QUALCOMM, case.getValue("payloads").jsonArray.map { it.jsonPrimitive.content.hexBytes() }, firmware)
        }
    }

    private fun worstCase(compact: Boolean): List<String> {
        // PeatsAudio's reads as if it asked everything, plus PairFree's own report.
        val logged = LoggedReads.Earbuds(firmware, Requests.ON_CONNECT.associateWith { h3Replies[it] }, gaia)
        val own = RecordingReport.OwnReads(firmware, h3Replies, gaia)
        return RecordingReport.sections(listOf(logged), own, curves, compact)
    }

    /** Length once form-encoded as a query parameter: spaces become `+`, other symbols `%XX`. */
    private fun encodedLength(text: String): Int = text.sumOf { ch ->
        when {
            ch.isLetterOrDigit() || ch in "-_.*" || ch == ' ' -> 1
            else -> 3
        }.toInt()
    }

    @Test
    fun compactFitsInAnIssueLink() {
        assertEquals(12, curves.size)
        val body = worstCase(compact = true).joinToString("\n")
        // GitHub refuses links much over 8000 characters; leave room for the address, title and header.
        assertTrue(encodedLength(body) < 7000, "compact body encodes to ${encodedLength(body)} characters")
        assertTrue(encodedLength(worstCase(compact = false).joinToString("\n")) > 8000, "the full report should not need compacting otherwise")
    }

    @Test
    fun compactLinesStayReadableByTheTools() {
        val lines = worstCase(compact = true)
        // tools/gen_models_table.py: LINE and GAIA_LINE.
        val settings = lines.filter { Regex("""^[a-z_]+ [0-9A-F]{4}  """).containsMatchIn(it) }
        assertEquals(2 * Requests.ON_CONNECT.size + 2, settings.size)
        for (line in settings) {
            assertTrue(Regex("""^[a-z_]+ +[0-9A-F]{4}  +(no reply|raw=[0-9a-f ]+|[0-9a-f ]+)$""").matches(line), line)
        }
        // tools/import_eq_capture.py: a numbered name, then the write's payloads on one line.
        val first = lines.indexOf("1. SOUNDPEATS Classic")
        assertEquals(curves[0].second.payloads.joinToString(" ") { it.hex() }, lines[first + 1])
        assertTrue(lines.any { it.startsWith("Firmware:  S5_20250605_V0.4.9") })
        assertFalse(lines.any { it.trimStart().startsWith("Hz") })
    }

    @Test
    fun fullReportKeepsTheDecodedValues() {
        val lines = worstCase(compact = false)
        assertTrue(lines.any { it.trimStart().startsWith("Hz") })
        assertTrue(lines.contains(DeviceReport.line(Command.ANC_MODE, h3Replies.getValue(Command.ANC_MODE))))
    }
}
