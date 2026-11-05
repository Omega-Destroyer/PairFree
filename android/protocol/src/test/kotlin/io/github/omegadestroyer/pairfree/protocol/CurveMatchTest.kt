package io.github.omegadestroyer.pairfree.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Recorded curves are named from the known presets, byte for byte, and slider curves are recognised. */
class CurveMatchTest {
    private val vectors = loadData("test_vectors.json")

    private fun write(case: JsonObject, firmware: String?): EqCapture.Write =
        EqCapture.Write(
            0,
            Models.find(case.str("model"), null)!!.eq!!.command,
            case.getValue("payloads").jsonArray.map { it.jsonPrimitive.content.hexBytes() },
            firmware?.let(::FirmwareVersion),
        )

    @Test
    fun everyKnownPresetIsNamed() {
        for (case in vectors.getValue("eq_preset").jsonArray.map { it.jsonObject }) {
            // Models can share a curve; a capture carries its firmware code, which is tried first.
            val model = Models.find(case.str("model"), null)!!
            val match = CurveMatch.of(write(case, "${case.str("model")}_x_V1")) as CurveMatch.Preset
            assertEquals(model, match.model, case.str("preset"))
            // Some models list one curve under two names, or the classic curve again as a preset; any of them is right.
            val eq = model.eq!!
            val bytes = { name: String ->
                (if (name == EqCapture.CLASSIC) eq.payloads(List(eq.bands.size) { 0 }) else eq.payloads(eq.presets.first { it.name == name }))
                    .map { it.toList() }
            }
            assertEquals(bytes(case.str("preset")), bytes(match.name), case.str("preset"))
        }
    }

    @Test
    fun theBaseCurveIsClassicAndSliderCurvesAreCustom() {
        for (case in vectors.getValue("eq_layout").jsonArray.map { it.jsonObject }) {
            val model = Models.find(case.str("model"), null)!!
            val gains = case.getValue("user_gains_db").jsonArray.map { it.jsonPrimitive.int }
            val expected = if (gains.all { it == 0 }) CurveMatch.Preset(model, EqCapture.CLASSIC) else CurveMatch.Custom(model, gains)
            assertEquals(expected, CurveMatch.of(write(case, "${case.str("model")}_x_V1")), case.str("name"))
        }
    }

    @Test
    fun aNewCurveIsNotMatched() {
        val case = vectors.getValue("eq_preset").jsonArray.map { it.jsonObject }.first()
        val changed = write(case, null).let { w ->
            EqCapture.Write(0, w.command, w.payloads.mapIndexed { i, p -> if (i == 3) p.copyOf().also { it[6] = (it[6] + 1).toByte() } else p }, null)
        }
        assertNull(CurveMatch.of(changed))
    }

    @Test
    fun recordingStartsWhereTheUserOpenedItOrAtTheLastConnect() {
        fun read(time: Long, command: Int) = SnoopScanner.Found(time, false, 3, Frame(Wire.VENDOR_MAIN, command, ByteArray(0)))
        val frames = listOf(read(1_000_000_000, 0x0309), read(2_000_000_000, 0x0309), read(2_000_050_000, 0x0309), read(2_100_000_000, 0x0310))
        assertEquals(1_500_000_000L, RecordingReport.start(frames, openedAtMicros = 1_500_000_000))
        assertEquals(2_000_050_000L - 3_000_000, RecordingReport.start(frames, openedAtMicros = null))
        // Opened after everything in the log: fall back to the last connect.
        assertEquals(2_000_050_000L - 3_000_000, RecordingReport.start(frames, openedAtMicros = 3_000_000_000))
        assertNull(RecordingReport.start(emptyList(), null))
    }
}
