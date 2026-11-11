package io.github.omegadestroyer.pairfree.protocol.oneplus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** HeyMelody's hearing enhancement test (docs/oneplus/PROTOCOL.md section 12) against the bytes the app builds. */
class OneplusHearingTestTest {
    private fun packets(frames: List<ByteArray>) = frames.flatMap { OneplusParser().feed(it) }

    private fun test(): OneplusHearingTest {
        val session = OneplusSession(OneplusModels.ALL.first { OneplusHearingTest.ENHANCEMENT_FEATURE in it.switches })
        return session.hearingTest("T")!!
    }

    @Test
    fun onlyModelsWithTheEnhancementSwitchOfferIt() {
        assertNotNull(OneplusSession(OneplusModels.ALL.first { 11 in it.switches }).hearingTest("T"))
        assertNull(OneplusSession(OneplusModels.ALL.first { 11 !in it.switches }).hearingTest("T"))
    }

    @Test
    fun theToneIsThreeOneAndThePointsThreeBytes() {
        val t = test()
        val open = packets(t.start()).single()
        assertEquals(OneplusCommand.SET_HEARING_STATUS, open.command)
        assertEquals(listOf(2, 1), open.payload.map { it.toInt() })
        // The first point: left earbud, type 1, slider at position 5 = -19 dB (0xED).
        val tone = packets(t.tone()).single()
        assertEquals(OneplusCommand.SET_HEARING_DETECT, tone.command)
        assertEquals(listOf(3, 1, 1, 1, 0xED), tone.payload.map { it.toInt() and 0xFF })
        // Moving the slider to the loudest position sends -55 dB.
        assertEquals(-55 and 0xFF, packets(t.setLevel(0)).single().payload.last().toInt() and 0xFF)
        // Next pauses and plays the following point.
        val next = packets(t.next())
        assertEquals(listOf(4), next[0].payload.map { it.toInt() })
        assertEquals(listOf(3, 1, 1, 2, 0xED), next[1].payload.map { it.toInt() and 0xFF })
    }

    @Test
    fun saveSendsTheTwelvePointsTheIdAndTheNameThenTurnsTheEnhancementOn() {
        val t = test()
        repeat(12) { t.next() }
        assertTrue(t.finished)
        val frames = packets(t.save())
        assertEquals(listOf(OneplusCommand.SET_HEARING_DETECT, OneplusCommand.SET_HEARING_DETECT, OneplusCommand.SET_HEARING_STATUS, OneplusCommand.SET_SWITCH), frames.map { it.command })
        val save = frames[1].payload
        assertEquals(3, save[0].toInt())
        assertEquals(12, save[1].toInt())
        assertEquals(2 + 36 + 4 + 1, save.size)
        // The right earbud's points are device 2, types 1 to 6.
        assertEquals(listOf(2, 1), listOf(save[2 + 18].toInt(), save[2 + 19].toInt()))
        assertEquals('T'.code, save.last().toInt())
        assertEquals(listOf(2, 0), frames[2].payload.map { it.toInt() })
        assertEquals(listOf(11, 1), frames[3].payload.map { it.toInt() })
        assertTrue(frames.all { it.command in OneplusSession.ALLOWED })
    }
}
