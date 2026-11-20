package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.jbl.RacePacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** The Airoha RACE gain of the hearing mode in force (docs/skullcandy/PROTOCOL.md section 14). */
class SkullcandyGainTest {
    @Test
    fun theSliderMapsToDbThroughThePowerRatioAndTheWireIsABigEndianShort() {
        assertEquals(-20.0, SkullcandyGain.db(0.0, SkullcandyGain.ANC), 1e-9)
        assertEquals(0.0, SkullcandyGain.db(1.0, SkullcandyGain.ANC), 1e-9)
        assertEquals(listOf(0xF8, 0x30), SkullcandyGain.wire(0.0, SkullcandyGain.ANC).map { it.toInt() and 0xFF })
        assertEquals(listOf(0x00, 0x00), SkullcandyGain.wire(1.0, SkullcandyGain.ANC).map { it.toInt() and 0xFF })
        for (s in listOf(0.0, 0.1, 0.5, 0.9, 1.0)) {
            val back = SkullcandyGain.sliderOf(SkullcandyGain.wire(s, SkullcandyGain.TRANSPARENCY), SkullcandyGain.TRANSPARENCY)!!
            assertTrue(abs(back - s) < 0.02, "$s -> $back")
        }
        assertNull(SkullcandyGain.sliderOf(byteArrayOf(1), SkullcandyGain.ANC))
    }

    @Test
    fun theGainCommandsAreTheSdksFrames() {
        assertEquals("05 5A 04 00 06 0E 00 0D", SkullcandyRace.GAIN_GET)
        assertEquals("05 5A 0C 00 06 0E 00 0E F8 30 F8 30 F8 30 F8 30", SkullcandyRace.gainSet(SkullcandyGain.wire(0.0, SkullcandyGain.ANC)))
        assertTrue(SkullcandyRace.isAllowed(SkullcandyRace.GAIN_GET))
        assertTrue(SkullcandyRace.isAllowed(SkullcandyRace.gainSet(byteArrayOf(0, 0))))
    }

    @Test
    fun theCrusherAnc2ShowsTheStrengthOfTheModeInForce() {
        val s = SkullcandyRaceTalk(SkullcandyModels.fromName("Crusher ANC 2")!!)
        assertTrue(s.hasGain)
        var mode: String? = "anc"
        s.mode = { mode }
        val read = s.gainRead()
        s.bytes(read)
        // status 00, sub-command 0D, gain 0 dB (slider 1.0) in noise cancelling's range
        assertTrue(s.feed(RacePacket(0x0E06, byteArrayOf(0x00, 0x0D, 0x00, 0x00), 0x5B).bytes()).changed)
        val row = s.groups.single { it.key == "noise" }.settings.single() as Setting.Level
        assertEquals(SkullcandyRaceTalk.ANC_LEVEL, row.key)
        assertEquals(10, row.value)
        // the row belongs to the mode it was read for
        mode = "transparency"
        assertTrue(s.groups.none { it.key == "noise" })
        mode = "anc"
        val out = s.change(SettingChange.Level(SkullcandyRaceTalk.ANC_LEVEL, 5))!!
        assertTrue(s.allowed(out.single()))
        assertEquals(12 + 4, out.single().bytes().size)
    }
}
