package io.github.omegadestroyer.pairfree.protocol.soundcore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin

class SleepSoundTest {
    private fun tone(rate: Int, seconds: Double, hz: Double) = ShortArray((rate * seconds).toInt()) { (8000 * sin(2 * PI * hz * it / rate)).toInt().toShort() }

    @Test
    fun aSoundIsCutAtSixtySecondsAndMakesTheContainer() {
        val sound = SleepSound.prepare(tone(16000, 75.0, 440.0), 0x1122334455667788L, "Rain", 2)
        assertTrue(sound.clipped)
        assertEquals(60, sound.seconds)
        assertEquals(128000, sound.bitRate)
        // 60 s at 16 kHz is 7,500 frames of 128 bytes behind the 256-byte header, under the app's size limit.
        assertEquals(256 + 7500 * 128, sound.file.size)
        assertEquals(0x36363131, (0 until 4).sumOf { (sound.file[it].toInt() and 0xFF) shl (8 * it) })
        assertEquals(16000, (0 until 4).sumOf { (sound.file[96 + it].toInt() and 0xFF) shl (8 * it) })
    }

    @Test
    fun soundTypeThreeIsThirtyTwoKilohertz() {
        val sound = SleepSound.prepare(tone(32000, 2.0, 1000.0), 5, "Hum", SleepSound.soundType(true))
        assertEquals(3, sound.file[16].toInt())
        assertEquals(32000, (0 until 4).sumOf { (sound.file[96 + it].toInt() and 0xFF) shl (8 * it) })
        assertEquals(256 + 1000 * 64 / 1000 * 0 + ((64000 + 127) / 128) * 64, sound.file.size)
    }

    @Test
    fun resamplingKeepsALowToneAndDropsAHighOne() {
        fun rms(s: ShortArray) = Math.sqrt(s.drop(200).take(s.size - 400).sumOf { it.toDouble() * it } / (s.size - 400))
        val low = SleepSound.resample(tone(48000, 1.0, 440.0), 48000, 16000)
        val high = SleepSound.resample(tone(48000, 1.0, 20000.0), 48000, 16000)
        assertEquals(16000, low.size)
        assertTrue(rms(low) > 4500, "440 Hz survives: ${rms(low)}")
        assertTrue(rms(high) < 1500, "20 kHz does not fold into the band: ${rms(high)}")
        assertEquals(32000, SleepSound.resample(tone(16000, 1.0, 440.0), 16000, 32000).size)
    }
}
