package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The Liberty 4's readings, laid out as the Soundcore app 5.0.35 reads them; not tried. */
class SoundcoreHealthTest {
    @Test
    fun measuringShowsTheReadings() {
        val health = SoundcoreHealth()
        val body = ByteArray(43).also { it[2] = 72; it[24] = 97; it[26] = 0x10; it[27] = 0x27 }
        assertTrue(!health.receive(body))
        assertArrayEquals(byteArrayOf(1), health.change(SettingChange.Toggle(SoundcoreHealth.MEASURE, true))!!.single().body)
        assertTrue(health.receive(body))
        val rows = health.rows().filterIsInstance<Setting.Information>().associate { it.key to it.value }
        assertEquals("72", rows[SoundcoreHealth.RATE])
        assertEquals("97 %", rows[SoundcoreHealth.OXYGEN_KEY])
        assertEquals("10000", rows[SoundcoreHealth.STEPS_KEY])
        health.change(SettingChange.Toggle(SoundcoreHealth.MEASURE, false))
        assertEquals(1, health.rows().size)
    }
}
