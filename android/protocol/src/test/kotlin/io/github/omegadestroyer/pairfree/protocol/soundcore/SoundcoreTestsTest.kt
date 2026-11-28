package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The wear calibration and the personal noise cancelling test, by the Soundcore app 5.0.35's writes; neither tried. */
class SoundcoreTestsTest {
    private fun SoundcoreTests.row(key: String) = rows().firstOrNull { it.key == key }

    @Test
    fun theWearCalibration() {
        val tests = SoundcoreTests(setOf(SoundcoreTests.CALIBRATION))
        val first = tests.change(SettingChange.Press(SoundcoreTests.ON_TABLE))!!.single()
        assertEquals(SoundcoreCommand.WEAR_CALIBRATION, first.command)
        assertArrayEquals(byteArrayOf(0), first.body)
        assertTrue(tests.receive(SoundcoreCommand.WEAR_CALIBRATION, byteArrayOf(0, 1, 0)))
        assertEquals(SoundcoreTests.PASSED, (tests.row(SoundcoreTests.ON_TABLE_RESULT) as Setting.Information).value)
        assertArrayEquals(byteArrayOf(1), tests.change(SettingChange.Press(SoundcoreTests.WORN))!!.single().body)
        assertTrue(tests.receive(SoundcoreCommand.WEAR_CALIBRATION, byteArrayOf(1, 0, 0)))
        assertEquals(SoundcoreTests.FAILED, (tests.row(SoundcoreTests.WORN_RESULT) as Setting.Information).value)
    }

    @Test
    fun thePersonalNoiseCancelling() {
        val tests = SoundcoreTests(setOf(SoundcoreTests.PERSONAL_ANC)) { 0x01020304L }
        val start = tests.change(SettingChange.Press(SoundcoreTests.TUNE))!!.single()
        assertArrayEquals(byteArrayOf(1, 1, 2, 3, 4, -1), start.body)
        assertTrue((tests.row(SoundcoreTests.TUNE) as Setting.Action).active)
        assertArrayEquals(byteArrayOf(0, 1, 2, 3, 4, -1), tests.change(SettingChange.Press(SoundcoreTests.TUNE))!!.single().body)
        tests.change(SettingChange.Press(SoundcoreTests.TUNE))
        assertTrue(tests.receive(SoundcoreCommand.PERSONAL_ANC_TEST, byteArrayOf(2)))
        assertEquals(SoundcoreTests.PASSED, (tests.row(SoundcoreTests.TUNE_RESULT) as Setting.Information).value)
        assertEquals(SoundcoreCommand.PERSONAL_ANC_RESET, tests.change(SettingChange.Press(SoundcoreTests.RESET))!!.single().command)
    }
}
