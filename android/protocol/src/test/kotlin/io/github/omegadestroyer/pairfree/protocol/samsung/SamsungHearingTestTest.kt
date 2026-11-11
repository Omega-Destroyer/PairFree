package io.github.omegadestroyer.pairfree.protocol.samsung

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** Galaxy Buds' Adapt Sound test (docs/samsung/PROTOCOL.md section 14) against what Samsung's plugin does. */
class SamsungHearingTestTest {
    private fun payload(frame: ByteArray): ByteArray = SamsungPacket.parse(frame)!!.payload

    @Test
    fun theLevelsMoveAsSamsungsSetTestResultDoes() {
        val heard = mapOf(5 to 5, 10 to 5, 15 to 15, 20 to 10, 25 to 25, 30 to 25, 35 to 35, 40 to 40)
        val missed = mapOf(5 to 10, 10 to 15, 15 to 20, 20 to 30, 25 to 30, 30 to 35, 35 to 40, 40 to 40)
        for ((level, to) in heard) assertEquals(to, SamsungHearingTest.next(level, true), "heard $level")
        for ((level, to) in missed) assertEquals(to, SamsungHearingTest.next(level, false), "missed $level")
    }

    @Test
    fun everyFrequencyOfEveryEarbudIsAskedThreeTimesThenItEnds() {
        val t = SamsungHearingTest(false, Random(1))
        val asked = mutableListOf<Pair<Boolean, Int>>()
        while (!t.finished) {
            val tone = t.current!!
            asked += tone.left to tone.frequency
            t.answer(true)
        }
        assertEquals(36, asked.size)
        assertEquals(setOf(true, false).flatMap { side -> (0..5).map { side to it } }.associateWith { 3 }, asked.groupingBy { it }.eachCount())
        assertNull(t.current)
    }

    @Test
    fun theToneMessageIsTheBlockSamsungSends() {
        val t = SamsungHearingTest(false, Random(2))
        assertEquals(0, payload(t.start().single())[1].toInt())
        // 1000 Hz (slot 3) in the left earbud at the starting level 20: attenuation code 13 - 4 = 9.
        val frame = t.play(SamsungHearingTest.Tone(left = true, frequency = 2, gain = 9)).single()
        val p = payload(frame)
        assertEquals(SamsungHearingTest.BLOCK, p.size)
        assertEquals(listOf(0, 1, 1, 3, 9), p.take(5).map { it.toInt() })
        assertEquals(SamsungHearingTest.MESSAGE, SamsungPacket.parse(frame)!!.id)
        // The right earbud at 8000 Hz (slot 9).
        assertEquals(listOf(0, 1, 0, 9, 5), payload(t.play(SamsungHearingTest.Tone(false, 5, 5)).single()).take(5).map { it.toInt() })
        assertEquals(2, payload(t.stop().single())[1].toInt())
        assertEquals(3, payload(t.cancel().single())[1].toInt())
    }

    @Test
    fun theResultGoesIntoTheProfileSlotsAndTurnsAdaptSoundOn() {
        val t = SamsungHearingTest(false, Random(3))
        t.left[0] = 5; t.left[1] = 10; t.left[2] = 15; t.left[3] = 20; t.left[4] = 25; t.left[5] = 30
        t.right[0] = 35; t.right[1] = 40; t.right[2] = 30; t.right[3] = 25; t.right[4] = 20; t.right[5] = 15
        val (profile, adapt) = t.apply().map(::payload)
        assertEquals(4, profile[1].toInt())
        // Left levels at 5 + slot, right ones at 15 + slot, slots 0, 1, 3, 5, 7 and 9.
        assertEquals(listOf(5, 10, 0, 15, 0, 20, 0, 25, 0, 30), (5..14).map { profile[it].toInt() })
        assertEquals(listOf(35, 40, 0, 30, 0, 25, 0, 20, 0, 15), (15..24).map { profile[it].toInt() })
        assertEquals(listOf(1, 5), adapt.take(2).map { it.toInt() })
        assertTrue(adapt.drop(5).toList() == profile.drop(5).toList())
    }
}
