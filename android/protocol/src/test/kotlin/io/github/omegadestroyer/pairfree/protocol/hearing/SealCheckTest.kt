package io.github.omegadestroyer.pairfree.protocol.hearing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SealCheckTest {
    @Test
    fun leftNormalSofterThenRightNormalSofterAndEachEarbudsSealFollowsItsAnswers() {
        val c = SealCheck()
        val order = mutableListOf<SealCheck.Step>()
        val answers = listOf(true, true, true, false)
        for (a in answers) {
            order += c.current!!
            c.answer(a)
        }
        assertEquals(listOf(SealCheck.Step(true, false), SealCheck.Step(true, true), SealCheck.Step(false, false), SealCheck.Step(false, true)), order)
        assertNull(c.current)
        assertEquals(SealCheck.Seal.GOOD, c.left)
        assertEquals(SealCheck.Seal.FAIR, c.right)
    }

    @Test
    fun noToneHeardIsAPoorSealAndTheSofterToneIsTwentyDbBelowTheNormal() {
        val c = SealCheck()
        assertEquals(SealCheck.Seal.UNKNOWN, c.left)
        repeat(4) { c.answer(false) }
        assertEquals(SealCheck.Seal.POOR, c.left)
        assertEquals(SealCheck.Seal.POOR, c.right)
        assertEquals(20f, SealCheck.NORMAL_DB - SealCheck.SOFTER_DB)
    }
}
