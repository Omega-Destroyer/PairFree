package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.SettingChange
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The Sleep A10's player, by the Soundcore app 5.0.35's parser and writes; not tried. */
class SoundcoreSleepPlayerTest {
    @Test
    fun theAlarm() {
        // It is 23:00:00 and 7:30 is asked for: eight and a half hours from now.
        val (clock, ring) = SoundcoreSleepPlayer.alarm("7:30", 0x01000000L, 23 * 3600)!!
        assertArrayEquals(byteArrayOf(1, 0, 0, 0), clock.body)
        val wait = 8 * 3600 + 30 * 60
        assertArrayEquals(byteArrayOf(1, 0, (wait shr 8).toByte(), wait.toByte()), ring.body)
        assertEquals(null, SoundcoreSleepPlayer.alarm("24:00", 0, 0))
        assertEquals(null, SoundcoreSleepPlayer.alarm("7:5", 0, 0))
        assertArrayEquals(ByteArray(4), SoundcoreSleepPlayer.clearAlarm().body)
    }

    @Test
    fun theStateAndTheWrites() {
        val layout = SoundcoreLayout.of("A6610")!!
        val body = ByteArray(255)
        byteArrayOf(1).copyInto(body, layout.locate("has_sleep_music")!!.first)
        byteArrayOf(20).copyInto(body, layout.locate("music_volume")!!.first)
        byteArrayOf(-1).copyInto(body, layout.locate("play_time")!!.first)
        byteArrayOf(0, 60).copyInto(body, layout.locate("play_countdown")!!.first)
        val state = SoundcoreState.parse(layout, body)!!
        fun body(change: SettingChange) = SoundcoreSleepPlayer.change(state, change)!!.first.body
        assertEquals(5, SoundcoreSleepPlayer.rows(state).size)
        assertArrayEquals(byteArrayOf(1), body(SettingChange.Press(SoundcoreSleepPlayer.PLAY)))
        assertArrayEquals(byteArrayOf(0, 30), body(SettingChange.Level(SoundcoreSleepPlayer.VOLUME, 30)))
        assertArrayEquals(byteArrayOf(30), body(SettingChange.Choice(SoundcoreSleepPlayer.PLAY_TIME, "30")))
        assertArrayEquals(byteArrayOf(-1, -1), body(SettingChange.Choice(SoundcoreSleepPlayer.COUNTDOWN, "keep")))
        assertArrayEquals(byteArrayOf(1, -32), body(SettingChange.Choice(SoundcoreSleepPlayer.COUNTDOWN, "480")))
    }
}
