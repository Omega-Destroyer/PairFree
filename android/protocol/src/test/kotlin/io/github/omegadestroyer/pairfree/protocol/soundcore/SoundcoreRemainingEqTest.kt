package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.SettingChange
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SoundcoreRemainingEqTest {
    @Test
    fun theFramesWrite() {
        val spec = SoundcoreEqSpec.of("A3600")!!
        val acoustic = SoundcoreEqualizer.write(spec, null, SoundcoreEqualizer.forPreset(spec, spec.preset("Acoustic")!!)).single()
        assertEquals(SoundcoreCommand.EQUALIZER, acoustic.command)
        assertArrayEquals(byteArrayOf(1, 0, 102, 98, 112, 95, 102, 94, 95, 100, 95, 100, 100, 100, 100, 100, 100, 100, 100, 100, 88), acoustic.body)
        val custom = SoundcoreEqualizer.write(spec, null, SoundcoreEqualizer.forCustom(spec, IntArray(8) { 10 })!!).single()
        assertArrayEquals(byteArrayOf(-2, -2, 102, 98, 112, 95, 102, 94, 100, 100, 100, -126, -126, -126, -126, -126, -126, -126, -126, 120, 88), custom.body)
    }

    @Test
    fun theAeroFit2ProGetsTheCurveTwice() {
        val spec = SoundcoreEqSpec.of("A3875")!!
        val body = SoundcoreEqualizer.write(spec, null, SoundcoreEqualizer.forPreset(spec, spec.preset("Balanced")!!)).single().body
        assertEquals(42, body.size)
        assertArrayEquals(body.copyOfRange(2, 22), body.copyOfRange(22, 42))
    }

    @Test
    fun theLifeNcsPresets() {
        val session = SoundcoreSession("A3201")
        assertEquals(1, session.requestLists().size)
        session.receive(SoundcorePacket(SoundcoreCommand.EQUALIZER_PRESET, byteArrayOf(1)))
        assertNull(session.change(SettingChange.Choice("sc_life_nc_eq", "sc_life_nc_office")), "nothing before the state")
        assertEquals(0, session.groups.size)
        assertEquals(0, SoundcoreSession("A3936").requestLists().size)
    }
}
