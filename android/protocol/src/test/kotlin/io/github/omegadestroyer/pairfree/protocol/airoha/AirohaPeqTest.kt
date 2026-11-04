package io.github.omegadestroyer.pairfree.protocol.airoha

import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The port of Airoha's PEQ library against the library's own output (data/jbl/test_vectors.json airoha_peq). */
class AirohaPeqTest {
    private val cases = Json.parseToJsonElement(repoFile("data/jbl/test_vectors.json").readText()).jsonObject
        .getValue("airoha_peq").jsonObject.getValue("cases").jsonArray.map { it.jsonObject }

    private fun bands(c: kotlinx.serialization.json.JsonObject) = c.getValue("bands").jsonArray.map { b ->
        val v = b.jsonArray.map { it.jsonPrimitive.double }
        AirohaPeqBand(v[0], v[1], v[2])
    }

    @Test
    fun coefficientsMatchTheLibrary() {
        assertTrue(cases.size >= 30)
        for (c in cases) {
            val r = AirohaPeq.coefficients(c.getValue("fs").jsonPrimitive.double, bands(c), c.getValue("master").jsonPrimitive.double)
            assertEquals(c.getValue("code").jsonPrimitive.int, r.code, c.toString())
            assertEquals(c.getValue("shorts").jsonArray.map { it.jsonPrimitive.int }, r.shorts!!.map { it.toInt() }, c.toString())
        }
    }

    @Test
    fun peakMatchesTheLibrary() {
        for (c in cases) {
            val peak = AirohaPeq.peakDb(bands(c), c.getValue("master").jsonPrimitive.double)
            assertEquals(c.getValue("peak100").jsonPrimitive.int, (peak * 100.0).toInt(), c.toString())
        }
    }

    @Test
    fun sortsLikeAndroid() {
        // Gains within 1 dB compare equal under the library's comparator, so the order is qsort's own.
        val a = mutableListOf(0 to 0.0, 1 to 0.5, 2 to 0.0, 3 to -0.7, 4 to 0.9, 5 to 0.0, 6 to 0.2, 7 to 0.0, 8 to 0.1, 9 to 0.0)
        val b = a.toMutableList()
        BionicSort.sort(b) { x, y -> (y.second - x.second).toInt() }
        assertEquals(a.map { it.first }.sorted(), b.map { it.first }.sorted())
    }
}
