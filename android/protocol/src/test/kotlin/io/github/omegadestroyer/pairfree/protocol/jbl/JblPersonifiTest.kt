package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** [JblPersonifi] against `libpersonifiAlgorithm.so` run in an emulator (data/jbl/personifi_vectors.json). */
class JblPersonifiTest {
    private val cases = Json.parseToJsonElement(repoFile("data/jbl/personifi_vectors.json").readText()).jsonObject.getValue("cases").jsonArray

    private fun bands(a: kotlinx.serialization.json.JsonElement) = a.jsonArray.map { b -> b.jsonArray.let { doubleArrayOf(it[0].jsonPrimitive.double, 0.0, it[1].jsonPrimitive.double, 2.0, it[2].jsonPrimitive.double) } }

    @Test
    fun theLibrarysOutputIsReproducedForEveryCase() {
        for ((n, c) in cases.withIndex()) {
            val o = c.jsonObject
            val curve = JblPersonifi.calCompensation(bands(o.getValue("left")), bands(o.getValue("right")))
            assertEquals(o.getValue("master").jsonPrimitive.double, curve.master, 1e-12, "master of case $n")
            for ((side, mine) in listOf("out_left" to curve.left, "out_right" to curve.right)) {
                val expected = o.getValue(side).jsonArray
                for (i in 0 until 12) {
                    val e = expected[i].jsonArray
                    val b = mine[i]
                    assertEquals(e[0].jsonPrimitive.int, b.type, "$side[$i] type of case $n")
                    assertEquals(e[1].jsonPrimitive.double, b.gain, 1e-12, "$side[$i] gain of case $n")
                    assertEquals(e[2].jsonPrimitive.double, b.freq, 1e-9, "$side[$i] frequency of case $n")
                    assertEquals(e[3].jsonPrimitive.double, b.q, 1e-12, "$side[$i] q of case $n")
                }
            }
        }
    }

    @Test
    fun theLevelOfAToneFollowsTheSecondsItWasHeardFor() {
        assertEquals(40.0, JblPersonifi.levelDb(1))
        assertEquals(31.0, JblPersonifi.levelDb(4))
        assertEquals(1.0, JblPersonifi.levelDb(14))
        assertEquals(1.0, JblPersonifi.levelDb(30), "after the fade's end")
        assertEquals(40.0, JblPersonifi.levelDb(0), "before it starts")
    }

    @Test
    fun aTestGivesTwelveBandsForEachEar() {
        val heard = JblPersonifi.TEST_FREQS.associateWith { f -> JblPersonifi.levelDb(f / 1000L + 3).toFloat() }
        val c = JblPersonifi.curve(heard, heard)
        assertEquals(12, c.left.size)
        assertEquals(12, c.right.size)
        assertEquals(c.left.map { it.gain }, c.right.map { it.gain }, "the same test in both ears")
        assert(c.master <= 0.0)
    }
}
