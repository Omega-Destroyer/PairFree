package io.github.omegadestroyer.pairfree.protocol.skullcandy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The second channel's session carries Skullcandy's custom RACE commands only, never an AT line. */
class SkullcandyRaceSessionTest {
    @Test
    fun theRaceSessionHoldsNoAtLinesAndNoPolling() {
        val p = SkullcandyModels.fromName("Hesh 360")!!
        val race = SkullcandyAtSession(p, withRace = true)
        assertTrue(race.hasRaceFeatures)
        assertTrue(race.start().isNotEmpty() && race.start().all { it.startsWith("RACE:") })
        assertTrue(race.poll().isEmpty())
        val at = SkullcandyAtSession(p)
        assertFalse(at.hasRaceFeatures)
        assertTrue(at.start().none { it.startsWith("RACE:") })
        assertFalse(at.allowed("RACE:05 5A 03 00 02 F4 01"))
    }

    @Test
    fun onlyTheModelsWithARaceSettingOpenTheSecondChannel() {
        val with = SkullcandyProducts.ALL.filter { SkullcandyAtSession(it, withRace = true).hasRaceFeatures }
        assertTrue(with.isNotEmpty())
        // the speakers and the models off the AT path never get one of these sessions' rows
        assertEquals(emptyList<String>(), with.filter { it.chip != "airoha" }.map { it.name })
    }
}
