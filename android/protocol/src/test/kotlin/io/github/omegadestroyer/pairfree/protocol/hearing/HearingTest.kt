package io.github.omegadestroyer.pairfree.protocol.hearing

import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** SOUNDPEATS' hearing test (docs/soundpeats/PROTOCOL.md section 13), from SOUNDPEATS' app. */
class HearingTest {
    private val table = HearingTables.SOUNDPEATS

    /** Runs the test, answering each tone with [hears] (its index at its frequency, from 0, loudest). */
    private fun run(hears: (frequency: Int, level: Int) -> Boolean): StaircaseTest {
        val t = StaircaseTest(table)
        while (!t.finished) {
            val tone = t.current!!
            val i = table.tones.indexOf(tone)
            t.answer(hears(i / table.levels, i % table.levels))
        }
        return t
    }

    @Test
    fun tableMatchesTheData() {
        val json = Json.parseToJsonElement(repoFile("data/soundpeats/hearing_test.json").readText()).jsonObject
        val tones = json.getValue("tones").jsonArray.map { r -> r.jsonArray.map { it.jsonPrimitive.float } }
        assertEquals(tones.map { HearingTone(it[0], it[1], it[2]) }, table.tones)
        assertEquals(listOf(125f, 250f, 500f, 800f, 1200f, 2000f, 4000f, 6000f), table.frequencies)
        assertEquals(10, table.levels)
        assertEquals(7, table.start)
    }

    @Test
    fun hearingEverythingEndsAtTheQuietest() {
        val t = run { _, _ -> true }
        assertEquals(List(8) { 9 }, t.results.map { table.tones.indexOf(it) % 10 })
        assertEquals(listOf(-4f, -4f, -4f, -4f, -3f, -3f, -3f, -3f), t.results.map { it.gainDb })
        assertNull(t.current)
    }

    @Test
    fun hearingNothingEndsAtTheLoudest() {
        val t = run { _, _ -> false }
        assertEquals(List(8) { 6f }, t.results.map { it.gainDb })
    }

    @Test
    fun thresholds() {
        // Heard down to level 7 (the start), missed at 8: the result is 7. Missed at 7, heard from 5 up: the result is 5.
        val t = run { f, level -> if (f % 2 == 0) level <= 7 else level <= 5 }
        assertEquals(listOf(7, 5, 7, 5, 7, 5, 7, 5), t.results.map { table.tones.indexOf(it) % 10 })
        assertEquals(listOf(0f, 3f, 0f, 3f, 0f, 2f, 0f, 2f), t.results.map { it.gainDb })
    }

    @Test
    fun startsLoudEnoughAndStepsOneLevelAtATime() {
        val t = StaircaseTest(table)
        assertEquals(HearingTone(125f, -75f, 0f), t.current)
        t.answer(heard = true)
        assertEquals(HearingTone(125f, -80f, -2f), t.current)
        t.answer(heard = false)
        // 125 Hz is done at -75 dB; 250 Hz starts at its own level 7.
        assertEquals(HearingTone(250f, -75f, 0f), t.current)
        assertTrue(t.progress > 0f)
    }

    @Test
    fun mergedOntoTheModelsBands() {
        val results = listOf(HearingTone(125f, 0f, 6f), HearingTone(250f, 0f, 2f), HearingTone(6000f, 0f, -3f))
        // 125 and 250 Hz both land on the 160 Hz band, 6 kHz on 5 kHz; within the model's range.
        assertEquals(listOf(0, 8, 0, -3), HearingEq.onBands(results, listOf(40, 160, 1000, 5000), 10))
        assertEquals(listOf(0, 6, 0, -3), HearingEq.onBands(results, listOf(40, 160, 1000, 5000), 6))
    }
}
