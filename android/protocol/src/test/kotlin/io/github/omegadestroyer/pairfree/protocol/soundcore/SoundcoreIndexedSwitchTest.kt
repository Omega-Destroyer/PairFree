package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The newest models take a switch as `41 87` or `41 88` with its bit in the state's records (Soundcore 5.0.35, `CmmBtCmdService.h5` and `z6`).
 * Built from the app's code; none recorded on earbuds.
 */
class SoundcoreIndexedSwitchTest {
    private fun record(tag: Int, vararg v: Int) = byteArrayOf(tag.toByte(), v.size.toByte()) + ByteArray(v.size) { v[it].toByte() }

    private fun session(model: String, vararg records: ByteArray) = SoundcoreSession(model).also {
        it.receive(SoundcorePacket(SoundcoreCommand.STATE, records.fold(ByteArray(0)) { all, r -> all + r }))
    }

    /** The command and body of the frame that goes out, without the header, length and checksum. */
    private fun sent(frame: ByteArray): String =
        (listOf(frame[5], frame[6]) + frame.copyOfRange(9, frame.size - 1).toList()).joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private fun SoundcoreSession.write(change: SettingChange) = sent(frames(change(change)!!).single())

    private fun SoundcoreSession.rows() = groups.flatMap { it.settings }.associateBy { it.key }

    @Test
    fun theLibertyBuds2WritesItsSwitchesByBit() {
        val s = session("D1206", record(5, 0x30, 0x31, 0x2E, 0x38, 0x30), record(6, 0x30, 0x31, 0x2E, 0x38, 0x30), record(2, 1), record(26, 0b1111), record(27, 0b1110, 0b0001, 0, 0, 0), record(58, 1))
        assertEquals("41 88 08 00", s.write(SettingChange.Toggle("sc_wearing_detection", false)))
        assertEquals("41 87 01 00", s.write(SettingChange.Toggle("sc_low_battery_prompt", false)))
        assertEquals("41 87 03 00", s.write(SettingChange.Toggle("sc_in_ear_tone", false)))
        assertEquals("41 88 02 00", sent(s.set(Command.LDAC, 0)!!))
        assertEquals("41 88 01 00", sent(s.set(Command.GAME_MODE, 0)!!))
        // Two devices keeps its own command here; only the Liberty 5 Pro pair sends it by bit.
        assertEquals("0b 84 00", sent(s.set(Command.MULTIPOINT, 0)!!))
        assertEquals("41 87 00 00", sent(s.frames(s.touch!!.setTouchTone(false)!!.packets).single()))
        // Press sensitivity: three levels, on firmware 1.77 to 1.99 and 2.77 to 2.99 only.
        assertEquals("medium", (s.rows()["sc_press_sensitivity"] as Setting.Choice).value)
        assertEquals("04 85 02", s.write(SettingChange.Choice("sc_press_sensitivity", "high")))
        val older = session("D1206", record(5, 0x30, 0x31, 0x2E, 0x35, 0x30), record(6, 0x30, 0x31, 0x2E, 0x35, 0x30), record(58, 1))
        assertNull(older.rows()["sc_press_sensitivity"])
        assertEquals(true, older.startFitTest() != null)
    }

    @Test
    fun theLiberty5ProPair() {
        for (model in listOf("D1203", "D1204")) {
            val s = session(model, record(25, 1, 2), record(26, 0b1011), record(27, 0b1100, 0b0100_0001, 0, 0b10_0000, 0, 0, 0, 0b10))
            assertEquals("41 88 03 00", sent(s.set(Command.MULTIPOINT, 0)!!))
            assertEquals("41 88 0e 00", s.write(SettingChange.Toggle("sc_easy_chat", false)))
            assertEquals("41 88 1d 00", s.write(SettingChange.Toggle("sc_sound_field", false)))
            assertEquals("41 88 39 00", s.write(SettingChange.Toggle("sc_voice_pickup", false)))
            assertEquals("41 88 08 00", s.write(SettingChange.Toggle("sc_wearing_detection", false)))
            // A reply without the record shows no switch rather than one that reads off.
            val short = session(model, record(27, 0b1100, 0b0001))
            assertNull(short.rows()["sc_voice_pickup"])
            assertNull(short.rows()["sc_sound_field"])
        }
    }

    @Test
    fun theAeroClip2PairAndSpace2Pro() {
        val pro = session("D1102", record(26, 0b11111), record(27, 0, 0b0100_0001), record(60, 103))
        assertEquals("41 87 02 00", pro.write(SettingChange.Toggle("sc_power_off_tone", false)))
        assertEquals("41 87 04 00", pro.write(SettingChange.Toggle("sc_connect_tone", false)))
        assertEquals("41 88 0e 00", pro.write(SettingChange.Toggle("sc_easy_chat", false)))
        assertEquals(Setting.Level("sc_volume_balance", 3, -6, 6), pro.rows()["sc_volume_balance"])
        assertEquals("10 8c 5e", pro.write(SettingChange.Level("sc_volume_balance", -6)))
        // No record, no slider.
        assertNull(session("D1105", record(26, 0b10)).rows()["sc_volume_balance"])
        val space = session("D1407", record(26, 0b100_0010), record(27, 0, 1, 0, 0, 0, 0, 0, 1))
        assertEquals("41 87 06 00", space.write(SettingChange.Toggle("sc_ambient_sound_prompt", false)))
        assertEquals("41 88 38 00", space.write(SettingChange.Toggle("sc_call_noise", false)))
    }

    @Test
    fun theSpace2ProButtons() {
        val s = session("D1406", record(72, 4, 255), record(73, 5))
        assertEquals("04 8c 00 00 07 00", s.write(SettingChange.Choice("sc_nc_double_press", "bass")))
        assertEquals("none", (s.rows()["sc_mfb_hold"] as Setting.Choice).value)
        assertEquals("04 61 00 01 05", s.write(SettingChange.Choice("sc_mfb_hold", "assistant")))
        assertEquals("04 61 00 01 0d", s.write(SettingChange.Choice("sc_mfb_hold", "nap")))
        assertEquals(null, session("D1406", record(27, 0)).rows()["sc_mfb_hold"])
    }

    /** The Sleep Earbuds 4 pair's tones are the Sleep A30's four switches, with their own commands. */
    @Test
    fun theSleepEarbuds4Tones() {
        for (model in listOf("D1302", "D1303")) {
            val s = session(model, record(26, 0b110_0100), record(27, 0, 0, 0b1000_0000))
            assertEquals("10 9e 00", s.write(SettingChange.Toggle("sleep_auto_power_off_prompt", false)))
            assertEquals("10 9f 00", s.write(SettingChange.Toggle("sleep_listening_mode_prompt", false)))
            assertEquals("10 77 00", s.write(SettingChange.Toggle("sleep_noise_canceling_prompt", false)))
            assertEquals("10 75 00", s.write(SettingChange.Toggle("sleep_calls_during_bluetooth", false)))
        }
    }

    /** The prompt switches of the Space headphones and the V20i, by the app's prompt screens (and OpenSCQ30 for `01 AE` and `01 AF`). */
    @Test
    fun voicePromptsAndTheBatteryNotice() {
        fun write(model: String, key: String): String {
            val s = SoundcoreSession(model)
            s.receive(SoundcorePacket(SoundcoreCommand.STATE, ByteArray(255)))
            return s.write(SettingChange.Toggle(key, true))
        }
        assertEquals("01 ae 01", write("A3035", "sc_mode_voice_prompt"))
        assertEquals("01 af 01", write("A3035", "sc_battery_alert"))
        assertEquals("01 ae 01", write("A3040", "sc_voice_prompt"))
        assertEquals("01 af 01", write("A3040", "sc_battery_alert"))
        assertEquals("01 ae 01", write("A3062", "sc_mode_voice_prompt"))
        assertEquals("01 ae 01", write("D1402", "sc_mode_voice_prompt"))
        assertEquals("01 83 01", write("A3876", "sc_voice_tone"))
    }

    /** The older models keep their own commands. */
    @Test
    fun otherModelsAreUntouched() {
        val s = SoundcoreSession("A3957")
        val packet = SoundcorePacket(SoundcoreCommand.WEARING_DETECTION, byteArrayOf(1))
        assertEquals("01 81 01", sent(s.frames(listOf(packet)).single()))
    }
}
