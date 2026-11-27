package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The AeroFit 2 pair's state, whose button block carries its own length, laid out as the Soundcore app 5.0.35 reads it; not tried. */
class SoundcoreAeroFitTest {
    /** A reply: the head up to the case's level (36 bytes), the equalizer (22), a button block of [block] bytes, then [tail]. */
    private fun reply(block: Int, tail: ByteArray) =
        ByteArray(36) + ByteArray(22) + byteArrayOf(block.toByte()) + ByteArray(block - 1) { 9 } + tail

    private fun session(model: String, body: ByteArray) = SoundcoreSession(model).also { it.receive(SoundcorePacket(SoundcoreCommand.STATE, body)) }

    @Test
    fun theFieldsFollowTheBlockWhateverItsLength() {
        // colour, LDAC, two devices, auto off, follow, balance, 3D, touch lock, touch tone, low battery, noise switch, game mode
        val tail = byteArrayOf(0, 1, 0, 1, 2, 0, 103, 0, 0, 1, 1, 0, 1)
        for (block in listOf(9, 13)) {
            val s = session("A3874", reply(block, tail))
            assertEquals(true, s.state.switch(Command.LDAC))
            assertEquals(false, s.state.switch(Command.MULTIPOINT))
            assertEquals(true, s.state.switch(Command.GAME_MODE))
            val rows = s.groups.flatMap { it.settings }.associateBy { it.key }
            assertEquals(Setting.Level("sc_volume_balance", 3, -6, 6), rows["sc_volume_balance"])
            assertEquals(Setting.Toggle("sc_low_battery_prompt", true), rows["sc_low_battery_prompt"])
        }
    }

    @Test
    fun theProsNoiseModeAndSensor() {
        // noise mode, wind, colour, LDAC, dual, auto off (2), balance, touch lock, touch tone, low battery, game, spatial (3), form (3), music, wake,
        // language, MFi, in-ear tone, smart, count, sensor
        val tail = byteArrayOf(2, 0, 0, 0, 1, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 2)
        val s = session("A3875", reply(11, tail))
        val rows = s.groups.flatMap { it.settings }.associateBy { it.key }
        assertEquals(Setting.Toggle("sc_anc_sensor", false), rows["sc_anc_sensor"])
        assertEquals(Setting.Toggle("sc_voice_wake", true), rows["sc_voice_wake"])
        val write = s.change(SettingChange.Toggle("sc_anc_sensor", true))!!.single()
        assertEquals(SoundcoreCommand.ANC_SENSOR, write.command)
        assertEquals(1, write.body.single().toInt())
    }
}
