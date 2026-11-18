package io.github.omegadestroyer.pairfree

import io.github.omegadestroyer.pairfree.eq.Equalizer
import io.github.omegadestroyer.pairfree.eq.EqProfile
import io.github.omegadestroyer.pairfree.eq.Stored
import io.github.omegadestroyer.pairfree.eq.TextStore
import io.github.omegadestroyer.pairfree.protocol.EqBand
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * The equalizer keeps its saved profiles as JSON texts in the settings file. Every release before the code became shared with the
 * desktop apps wrote them with Android's own JSON classes; these are such texts, which must still be read.
 */
class EqualizerStorageTest {
    private class MapStore(val map: MutableMap<String, String> = mutableMapOf()) : TextStore {
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
    }

    private val layout = EqLayout(
        command = 0, masterGainDb = 0.0, userRangeDb = 6,
        bands = listOf(EqBand(100, 1.0, 0.0), EqBand(1000, 1.0, 0.0), EqBand(8000, 1.0, 0.0)),
    )

    private fun equalizer(store: TextStore) = Equalizer(store, CoroutineScope(Dispatchers.Unconfined), {})

    @Test
    fun savedProfilesWrittenByAndroidsJsonClassesAreRead() {
        // Whole gains were written without a fraction, halves with one; a profile with the wrong number of bands is left out.
        val store = MapStore(mutableMapOf("eq_profiles_H3" to """[{"name":"Warm","gains":[2,0,-1.5]},{"name":"Old","gains":[1,2]},{"name":"Flat","gains":[0,0,0]}]"""))
        val read = equalizer(store).loadProfiles("H3", layout)
        assertEquals(listOf("Warm", "Flat"), read.map { it.name })
        assertEquals(listOf(2.0, 0.0, -1.5), read[0].gains)
        assertEquals(EqProfile.USER + "Warm", read[0].id)
    }

    @Test
    fun aTextThatIsNotWhatItShouldBeReadsAsNoProfiles() {
        for (bad in listOf("not json", "{}", """[{"name":"x"}]""", "[1,2]", """[{"gains":[1,2,3]}]""")) {
            val store = MapStore(mutableMapOf("eq_profiles_H3" to bad))
            assertEquals(emptyList<EqProfile>(), equalizer(store).loadProfiles("H3", layout), bad)
        }
    }

    @Test
    fun anAbsentKeyIsNoProfiles() {
        assertEquals(emptyList<EqProfile>(), equalizer(MapStore()).loadProfiles("H3", layout))
    }

    @Test
    fun aProfileThatWasSavedIsReadBackAsItWas() {
        val store = MapStore()
        val eq = equalizer(store)
        val text = Stored.arrayOf("""[{"name":"Mine \"quoted\"","gains":[1.5,-2,0]}]""")
        store.put("eq_profiles_X", text.toString())
        assertEquals(listOf(listOf(1.5, -2.0, 0.0)), eq.loadProfiles("X", layout).map { it.gains })
        assertEquals("Mine \"quoted\"", eq.loadProfiles("X", layout).single().name)
    }

    @Test
    fun numbersAreWrittenAsAndroidsClassesWroteThem() {
        assertEquals("2", Stored.number(2.0).toString())
        assertEquals("-1.5", Stored.number(-1.5).toString())
        assertEquals("0", Stored.number(0.0).toString())
    }

    @Test
    fun numbersWrittenAsTextAreStillNumbers() {
        // Android's getDouble and optInt accepted a number written as a string.
        val o = Stored.objectOf("""{"preamp":"-3","gains":["1.5",2],"profile":7}""")
        with(Stored) {
            assertEquals(-3, o.optInt("preamp", 0))
            assertEquals(listOf(1.5, 2.0), o.array("gains").doubles())
            assertEquals("7", o.optString("profile"))
            assertEquals(0, o.optInt("absent", 0))
            assertEquals("", o.optString("absent"))
        }
        assertThrows(IllegalArgumentException::class.java) { Stored.objectOf("[1]") }
        assertTrue(runCatching { Stored.objectOf("{broken") }.exceptionOrNull() is IllegalArgumentException)
    }
}
