package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SoundcoreCaseTest {
    private val clock = { SoundcoreCaseClock(0x01020304L, 3600, true) }

    private fun record(tag: Int, vararg value: Int) = byteArrayOf(tag.toByte(), value.size.toByte()) + ByteArray(value.size) { value[it].toByte() }

    private fun reply(vararg records: ByteArray): ByteArray = SoundcorePacket.buildFromEarbuds(SoundcoreCommand.STATE, records.fold(ByteArray(0)) { a, b -> a + b })

    /** A frame's body: after the header, command and length, before the checksum. */
    private fun body(frame: ByteArray) = frame.copyOfRange(9, frame.size - 1)

    @Test
    fun theCaseIsReachedAtTheAddressTheEarbudsReport() {
        assertTrue(SoundcoreCaseSpec.of("D1302")!!.le)
        assertFalse(SoundcoreCaseSpec.of("D1204")!!.le)
        assertNull(SoundcoreCaseSpec.of("D1303"))
        for (model in listOf("D1203", "D1204", "D1302")) {
            val layout = SoundcoreLayout.of(model)!!
            val state = SoundcoreState.parse(layout, ByteArray(0) + record(41, 0xE8, 0xEE, 0xCC, 1, 2, 3))
            assertEquals("E8:EE:CC:01:02:03", SoundcoreCaseSpec.address(state), model)
            assertTrue(state!!.reportHex().endsWith("XX XX XX XX XX XX"), model)
            assertNull(SoundcoreCaseSpec.address(SoundcoreState.parse(layout, record(1, 1))), model)
        }
    }

    @Test
    fun theClockIsSetInEachCasesForm() {
        // One hour east of UTC is 26 half hours from 12 west.
        val pro = SoundcoreCaseTalk("D1204", clock).clockFrame()!!
        assertArrayEquals(byteArrayOf(4, 3, 2, 1, 26, -1, -1, -1, -1, 26), body(pro))
        val sleep = SoundcoreCaseTalk("D1302", clock).clockFrame()!!
        assertArrayEquals(byteArrayOf(70, 11, 4, 3, 2, 1, 26, -1, -1, -1, -1, 26, 0, 71, 2, 0, 0), body(sleep))
        assertEquals(2, SoundcoreCaseTalk("D1302", clock).start().size)
        assertEquals(1, SoundcoreCaseTalk("D1302") { null }.start().size)
    }

    @Test
    fun theSleepCasesSwitchesAndSound() {
        val talk = SoundcoreCaseTalk("D1302", clock)
        // Bit 33 on, the rest off; the agreement for BioSync (50) accepted, not for sleep detection (51).
        val switches = record(27, 0, 0, 0, 0, 0x02, 0, 0x04)
        val sound = record(98, 1, 9, 0, 0, 0, 0, 0, 0, 0, 2)
        assertTrue(talk.feed(reply(record(9, '0'.code, '1'.code, '.'.code, '2'.code), switches, sound, record(7, 'S'.code, 'N'.code))).changed)
        val rows = talk.groups.single().settings
        assertEquals(true, (rows.first { it.key == "sc_case_notifications" } as Setting.Toggle).on)
        assertEquals(false, (rows.first { it.key == "sc_case_biosync" } as Setting.Toggle).on)
        assertNull(rows.firstOrNull { it.key == "sc_case_sleep_check" })
        assertEquals("01.2", (rows.first { it.key == SoundcoreCaseTalk.FIRMWARE } as Setting.Information).value)

        val on = talk.change(SettingChange.Toggle("sc_case_noise_alert", true))!!.single()
        assertTrue(talk.allowed(on))
        assertArrayEquals(byteArrayOf(38, 1), body(on))
        assertEquals(1, talk.takeReadBacks().size)
        assertNull(talk.change(SettingChange.Toggle("sc_case_sleep_check", true)))
        val pause = talk.change(SettingChange.Toggle(SoundcoreCaseTalk.SOUND, false))!!.single()
        assertArrayEquals(byteArrayOf(0, 9, 0, 0, 0, 0, 0, 0, 0, 2), body(pause))
        assertTrue(talk.reportHex()!!.endsWith("07 02 XX XX"))
        // Nothing else goes out: not the file transfer's command, nor a restart.
        assertFalse(talk.allowed(SoundcorePacket.build(0x1875, ByteArray(0))))
    }

    @Test
    fun theLibertyCasesOnlyReport() {
        val talk = SoundcoreCaseTalk("D1203", clock)
        talk.feed(reply(record(27, 0, 0, 0, 0, 0xFF), record(9, '1'.code)))
        assertEquals(listOf(SoundcoreCaseTalk.FIRMWARE), talk.groups.single().settings.map { it.key })
        assertNull(talk.change(SettingChange.Toggle("sc_case_notifications", true)))
    }
}
