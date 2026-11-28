package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.SettingChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The Frames, the VR P10 and the voice prompt language, by the Soundcore app 5.0.35's parsers and writes; none tried. */
class SoundcoreOtherModelsTest {
    /** A session fed an empty state with each of [fields] set. */
    private fun session(model: String, vararg fields: Pair<String, ByteArray>) = SoundcoreSession(model).also {
        val layout = SoundcoreLayout.of(model)!!
        val body = ByteArray(255)
        for ((name, value) in fields) value.copyInto(body, layout.locate(name)!!.first)
        it.receive(SoundcorePacket(SoundcoreCommand.STATE, body))
    }

    private fun SoundcoreSession.write(change: SettingChange): String = frames(change(change)!!).single().let { frame ->
        (listOf(frame[5], frame[6]) + frame.copyOfRange(9, frame.size - 1).toList()).joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    @Test
    fun theFrames() {
        val s = session("A3600", "voice_control" to byteArrayOf(0, 2), "stereo_expansion" to byteArrayOf(1, 3))
        assertEquals("0a 81 01 02", s.write(SettingChange.Toggle("sc_voice_commands", true)))
        assertEquals("02 85 01 04", s.write(SettingChange.Level("sc_open_surround_level", 4)))
        assertEquals("02 85 00 04", s.write(SettingChange.Toggle("sc_open_surround", false)))
        assertEquals("0a 82 01", s.write(SettingChange.Toggle("sc_super_volume", true)))
        assertEquals("01 8f 01", s.write(SettingChange.Toggle("sc_manual_shutdown", true)))
    }

    @Test
    fun theLightAndTheLanguage() {
        val vr = session("A3850", "light" to byteArrayOf(1, 0))
        assertEquals("0e 81 01 02", vr.write(SettingChange.Choice("sc_light_mode", "saving")))
        assertEquals("0e 81 04 02", vr.write(SettingChange.Choice("sc_light_colour", "purple")))
        for (model in listOf("A3040", "A3952")) {
            assertEquals("01 8d 01", session(model).write(SettingChange.Choice("sc_voice_language", "chinese")))
        }
    }
}
