package io.github.omegadestroyer.pairfree.protocol.hearing

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue

class EqFinderTest {
    private val hz = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    /** A listener whose ideal is [ideal]: prefers the curve closer to it. */
    private fun run(ideal: List<Int>, finder: EqFinder): Int {
        var n = 0
        while (!finder.finished) {
            val a = finder.best.zip(ideal).sumOf { (g, t) -> (g - t) * (g - t) }
            val b = finder.candidate!!.zip(ideal).sumOf { (g, t) -> (g - t) * (g - t) }
            finder.choose(b < a)
            n++
        }
        return n
    }

    @Test
    fun alwaysKeepingAStaysAtTheStart() {
        val f = EqFinder(hz, 6, List(10) { 0 })
        var n = 0
        while (!f.finished) {
            assertNotEquals(f.best, f.candidate)
            f.choose(false)
            n++
        }
        assertEquals(List(10) { 0 }, f.best)
        assertTrue(n <= EqFinder.MAX_TRIALS)
        assertEquals(1f, f.progress)
    }

    @Test
    fun aBassLoverEndsWithMoreBass() {
        val ideal = hz.map { if (it <= 125) 4 else 0 }
        val f = EqFinder(hz, 6, List(10) { 0 })
        val n = run(ideal, f)
        assertTrue(n <= EqFinder.MAX_TRIALS)
        assertTrue(f.best[0] > 0, "bass raised: ${f.best}")
        assertTrue(f.best[0] > f.best[9], "more bass than treble: ${f.best}")
    }

    @Test
    fun aTrebleLoverEndsWithMoreTreble() {
        val ideal = hz.map { if (it >= 4000) 4 else 0 }
        val f = EqFinder(hz, 6, List(10) { 0 })
        run(ideal, f)
        assertTrue(f.best[9] > f.best[0], "treble over bass: ${f.best}")
    }

    @Test
    fun gainsStayInRangeOnAnyBandCount() {
        for (bands in listOf(listOf(100, 1000, 8000), listOf(60, 250, 1000, 4000, 12000), hz)) {
            val f = EqFinder(bands, 3, List(bands.size) { 0 })
            // Always prefers the candidate: pushes every shape as far as it goes.
            while (!f.finished) f.choose(true)
            assertTrue(f.best.all { it in -3..3 }, "${f.best}")
        }
    }

    @Test
    fun aWrongSizedStartIsFlat() {
        val f = EqFinder(hz, 6, listOf(1, 2))
        assertEquals(List(10) { 0 }, f.best)
    }
}
