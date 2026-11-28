package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The Liberty 4's neck posture, by the Soundcore app 5.0.35's writes and parsers; not tried. */
class SoundcoreNeckTest {
    private fun SoundcoreNeck.info(key: String) = (rows().firstOrNull { it.key == key } as Setting.Information?)?.value

    @Test
    fun calibrationAndHistory() {
        val neck = SoundcoreNeck()
        assertArrayEquals(byteArrayOf(1, 3), neck.change(SettingChange.Press(SoundcoreNeck.CALIBRATE_NECK))!!.single().body)
        neck.receive(SoundcoreCommand.POSTURE_CALIBRATE, byteArrayOf(1, 3, 0))
        assertEquals(SoundcoreTests.PASSED, neck.info(SoundcoreNeck.NECK_RESULT))
        val body = ByteArray(144)
        // Front to back: 3 counts in the band 90 to 85 degrees, 1 in the band 5 to 0. Sideways: all upright.
        body[0] = 3
        body[17 * 2] = 1
        body[(36 + 17) * 2] = 5
        neck.receive(SoundcoreCommand.POSTURE_HISTORY, body)
        assertEquals("75 %", neck.info(SoundcoreNeck.TILT_FRONT))
        assertEquals("0 %", neck.info(SoundcoreNeck.TILT_SIDE))
        assertEquals(SoundcoreCommand.POSTURE_HISTORY_CLEAR, neck.change(SettingChange.Press(SoundcoreNeck.CLEAR_HISTORY))!!.single().command)
        assertArrayEquals(byteArrayOf(1), neck.change(SettingChange.Choice(SoundcoreNeck.REPORT, SoundcoreNeck.ON))!!.single().body)
    }
}
