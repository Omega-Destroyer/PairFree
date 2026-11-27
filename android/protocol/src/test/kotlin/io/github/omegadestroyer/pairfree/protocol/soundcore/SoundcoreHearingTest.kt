package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.hexBytes
import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SoundcoreHearingTest {
    private fun a40(): SoundcoreState {
        val vectors = Json.parseToJsonElement(repoFile("data/soundcore/test_vectors.json").readText()).jsonObject
        val body = vectors.getValue("a3936_state").jsonObject.getValue("body").jsonPrimitive.content
        return SoundcoreState.parse(SoundcoreLayout.of("A3936")!!, body.hexBytes())!!
    }

    private fun answers(run: SoundcoreHearingRun, heard: (SoundcoreHearingRun) -> Boolean) {
        var count = 0
        while (!run.finished) {
            run.answer(heard(run))
            assertTrue(++count < 400, "the staircase ends")
        }
    }

    @Test
    fun theTonesGoInTheAppsOrderAndNeverAboveTheLimit() {
        for (model in listOf("A3936", "A3951", "A3930", "A3952")) {
            val spec = SoundcoreHearingSpec.of(model)!!
            assertEquals(listOf(3200, 1600, 6400, 800, 400, 200, 100, 12800), List(spec.count) { spec.hz(it) })
            val run = SoundcoreHearingRun(spec)
            answers(run) {
                assertTrue(it.level <= -27.0, "$model ${it.hz} Hz at ${it.level}")
                false
            }
            assertEquals(16, run.done)
            assertNotNull(run.result())
        }
        assertNull(SoundcoreHearingSpec.of("A3040"))
        // The A40's first tone: 3.2 kHz at -75 dB plus 6.
        assertEquals(-69.0, SoundcoreHearingRun(SoundcoreHearingSpec.of("A3936")!!).level)
        assertTrue(SoundcoreHearingRun(SoundcoreHearingSpec.of("A3936")!!).left)
    }

    @Test
    fun hearId3() {
        for (model in listOf("A3035", "A3947", "A3955", "A3968")) {
            val run = SoundcoreHearingRun(SoundcoreHearingSpec.of(model)!!)
            assertEquals(8, run.total)
            // 3.2 kHz: the recording at 0 dB, played at -38 dB.
            assertEquals(-87.8, run.level, 0.001)
            repeat(9) { run.louder(true) }
            assertEquals(4, run.steps)
            run.keep()
            run.back()
            assertEquals(0, run.done)
            assertEquals(4, run.steps)
            run.keep()
            // 1.6 kHz one step under, then the rest as they start.
            run.louder(false)
            while (!run.finished) run.keep()
            // 3.2 kHz needed 12 dB more than the rest: half of it, in tenths. The tones below the starting level count as it.
            assertArrayEquals(intArrayOf(0, 0, 0, 0, 0, 60, 0, 0), run.result()!!.left)
            assertArrayEquals(run.result()!!.left, run.result()!!.right)

            val spec = SoundcoreEqSpec.of(model)!!
            val state = blank(model)
            val body = SoundcoreHearingId.save(model, state, run.result()!!, 5)!!.body
            val curves = spec.hearId!!.channels
            // Preset, genre, the EQ, gender, age, the switch, the curves, time, type, the curves again, what plays, and the noise byte.
            assertEquals(4 + spec.channels * 10 + 3 + curves * 10 + 4 + 1 + curves * 10 + spec.channels * 10 + 1, body.size, model)
            assertEquals(0, body.last().toInt())
        }
    }

    /** A state of [model] with every field its layout names zeroed: the A40 has a recorded one, these models none. */
    private fun blank(model: String): SoundcoreState {
        val layout = SoundcoreLayout.of(model)!!
        return SoundcoreState.parse(layout, ByteArray(512))!!
    }

    @Test
    fun theStaircase() {
        // Never heard: louder three times, then given up as 24.
        val never = SoundcoreStaircase(-70, 6)
        repeat(3) { never.answer(false) }
        assertEquals(24, never.result)
        // Always heard: down to 12 dB under the starting level.
        val always = SoundcoreStaircase(-70, 6)
        var n = 0
        while (always.result == null) {
            always.answer(true)
            assertTrue(++n < 20)
        }
        assertEquals(0, always.result)
        // Heard above a threshold of -70 dB: the result is the middle.
        val middle = SoundcoreStaircase(-70, 6)
        n = 0
        while (middle.result == null) {
            middle.answer(-70 + middle.step >= -70)
            assertTrue(++n < 20)
        }
        assertEquals(12, middle.result)
    }

    @Test
    fun theCorrection() {
        // Both ears flat: everything is 12.
        assertArrayEquals(IntArray(16) { 12 }, SoundcoreHearingRun.correct(IntArray(16) { 12 }))
        // The right ear hears 1.6 kHz best (6): the others are moved up by 6, then held to each band's range.
        val raw = IntArray(16) { 24 }
        raw[4] = 9
        raw[12] = 6
        val out = SoundcoreHearingRun.correct(raw)
        assertArrayEquals(intArrayOf(15, 15, 18, 18, 12, 18, 18, 18), out.copyOfRange(0, 8))
        assertArrayEquals(out.copyOfRange(0, 8), out.copyOfRange(8, 16))
        assertArrayEquals(IntArray(16) { 12 }, SoundcoreHearingRun.unify(IntArray(16) { 12 }))
        assertEquals(18, SoundcoreHearingRun.unify(IntArray(16) { if (it == 0) 0 else 24 })[1])
    }

    @Test
    fun aResultIsWrittenAsTheAppWritesIt() {
        val state = a40()
        val result = SoundcoreHearingResult(IntArray(8) { (it % 3) * 10 }, IntArray(8))
        val packet = SoundcoreHearingId.save("A3936", state, result, 0x01020304L)!!
        assertEquals(SoundcoreCommand.EQUALIZER_HEAR_ID, packet.command)
        val body = packet.body
        assertEquals(92, body.size, "as the recorded A40 write")
        // After preset, genre and the EQ's two curves: gender 0, the state's age, HearID on.
        assertEquals(0, body[24].toInt())
        assertEquals(1, body[26].toInt())
        // The stored curve: the result in tenths of the app's scale, the last two bands at their lowest.
        assertArrayEquals(byteArrayOf(120, -126, -116, 120, -126, -116, 120, -126, 0, 0), body.copyOfRange(27, 37))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 0), body.copyOfRange(47, 52))
        assertArrayEquals(body.copyOfRange(27, 47), body.copyOfRange(52, 72))
        // A flat right ear plays flat, with the ninth band at -1.2 dB and the tenth at -12 dB.
        assertArrayEquals(byteArrayOf(120, 120, 120, 120, 120, 120, 120, 120, 108, 0), body.copyOfRange(82, 92))
        assertNull(SoundcoreHearingId.save("A3040", state, result, 0))
        assertNull(SoundcoreHearingId.save("A3936", null, result, 0))
    }

    @Test
    fun switchingItOffSendsTheOrdinaryEqWrite() {
        val spec = SoundcoreEqSpec.of("A3936")!!
        var state = a40()
        val saved = SoundcoreHearingId.save("A3936", state, SoundcoreHearingResult(IntArray(8) { 20 }, IntArray(8) { 30 }), 1_700_000_000L)!!.body
        // The state as it reads once the earbuds hold that: the switch, curve, time, type, custom and genre.
        val field = spec.hearId!!.field
        val bytes = state.field(field)!!
        saved.copyOfRange(26, 72).copyInto(bytes)
        state = state.with(field, bytes)
        assertEquals(2, SoundcoreHearingId.rows("A3936", state).size)
        val off = SoundcoreHearingId.change("A3936", state, SettingChange.Toggle(SoundcoreHearingId.SWITCH, false))!!
        val ordinary = SoundcoreEqualizer.write(spec, state, SoundcoreEqualizer.read(spec, state)!!).single()
        assertArrayEquals(ordinary.body, off.body)
        val on = SoundcoreHearingId.change("A3936", state, SettingChange.Toggle(SoundcoreHearingId.SWITCH, true))!!
        assertArrayEquals(saved.copyOfRange(72, 92), on.body.copyOfRange(72, 92), "the same curve plays as when it was saved")
    }
}
