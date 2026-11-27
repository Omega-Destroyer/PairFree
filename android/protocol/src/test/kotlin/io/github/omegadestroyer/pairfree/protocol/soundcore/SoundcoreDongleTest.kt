package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The VR P10's dongle list, laid out as the Soundcore app 5.0.35 reads it; not tried. */
class SoundcoreDongleTest {
    private fun record(name: String, connected: Boolean, type: Int, last: Int) =
        name.encodeToByteArray().copyOf(20) + byteArrayOf(if (connected) 1 else 0, type.toByte()) + "01.00".encodeToByteArray() + byteArrayOf(1, 2, 3, 4, 5, last.toByte())

    @Test
    fun theListAndItsWrites() {
        val dongle = SoundcoreDongle()
        dongle.receive(SoundcoreCommand.DONGLE_INFO, byteArrayOf(2) + record("Desk", true, 2, 6) + record("Console", false, 1, 7))
        dongle.receive(SoundcoreCommand.DONGLE_LOW_POWER_STATE, byteArrayOf(0))
        val rows = dongle.rows().associateBy { it.key }
        assertEquals(listOf("Console"), (rows[SoundcoreDongle.CONNECT] as Setting.Choice).options)
        assertEquals("pc", (rows[SoundcoreDongle.TYPE] as Setting.Choice).value)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 7), dongle.change(SettingChange.Choice(SoundcoreDongle.CONNECT, "Console"))!!.single().body)
        assertArrayEquals(byteArrayOf(4), dongle.change(SettingChange.Choice(SoundcoreDongle.TYPE, "phone"))!!.single().body)
        assertEquals(SoundcoreCommand.DONGLE_DISCONNECT, dongle.change(SettingChange.Press(SoundcoreDongle.DISCONNECT))!!.single().command)
        assertArrayEquals(byteArrayOf(1), dongle.change(SettingChange.Toggle(SoundcoreDongle.LOW_POWER, true))!!.single().body)
        assertEquals(SoundcoreCommand.DONGLE_REMOVE, dongle.change(SettingChange.Choice(SoundcoreDongle.REMOVE, "Desk"))!!.single().command)
    }
}
