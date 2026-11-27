package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.Value
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Avnera's LightX protocol on the Everest Elite range and the Reflect Aware (docs/jbl/PROTOCOL.md section 12), read from JBL Headphones 5.37.10. Never tried on headphones. */
class AvneraTalkTest {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    private fun answer(key: Int, vararg words: Int): ByteArray {
        val data = ByteArray(words.size * 4)
        for ((i, w) in words.withIndex()) for (j in 0 until 4) data[i * 4 + j] = (w shr (8 * j)).toByte()
        return AvneraPacket(AvneraPacket.READ, key, data, source = AvneraPacket.APPLICATION, destination = AvneraPacket.BLUETOOTH).bytes()
    }

    @Test
    fun aWriteIsAFrameWithAHeaderABufferAndANegatedSum() {
        // ANC on: key 133 (0x85) in the top byte, the buffer one word, 0x82 for a write with a buffer.
        val frame = AvneraPacket.write(133, 1).bytes()
        assertEquals("ff5a00000017" + "0f40820000000085" + "04000000" + "01000000", hex(frame).dropLast(2))
        assertEquals(0, frame.sumOf { it.toInt() } and 0xFF)
    }

    @Test
    fun aReadCarriesTwoHundredAndFiftySixZeroBytes() {
        val frame = AvneraPacket.read(176).bytes()
        assertEquals(6 + 12 + 256 + 1, frame.size)
        assertEquals("ff5a0000", hex(frame).take(8))
        assertEquals(0x0113, ((frame[4].toInt() and 0xFF) shl 8) or (frame[5].toInt() and 0xFF))
        assertEquals(0x81, frame[8].toInt() and 0xFF)
    }

    @Test
    fun theReaderSplitsFramesAndDropsABadCheck() {
        val reader = AvneraReader()
        val a = answer(176, 80)
        val b = answer(133, 1)
        assertEquals(emptyList<AvneraPacket>(), reader.feed(a.copyOf(10)))
        val got = reader.feed(a.copyOfRange(10, a.size) + b)
        assertEquals(listOf(176, 133), got.map { it.key })
        assertEquals(80, got[0].number())
        val bad = b.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertTrue(AvneraReader().feed(bad).isEmpty())
    }

    @Test
    fun theSessionShowsTheSettingsTheHeadphonesAnswerAndWritesThem() {
        val talk = AvneraTalk(AvneraModels.fromName("JBL EVEREST ELITE 750NC")!!)
        assertEquals(listOf(135, 176, 133, 134, 146, 139, 137, 138, 182, 183, 181, 148), talk.start().map { it.key })
        talk.feed(answer(176, 87) + answer(133, 1) + answer(134, 2) + answer(146, 4) + answer(182, 0) + answer(183, 1) + answer(139, 8) + answer(137, 3) + answer(138, 5))
        assertEquals(Setting.Level("jbl_av_raw_left", 3, 0, 7), talk.groups.flatMap { it.settings }.first { it.key == "jbl_av_raw_left" })
        assertEquals(AvneraPacket.write(138, 6).bytes().toList(), talk.change(SettingChange.Level("jbl_av_raw_right", 6))!!.single().bytes().toList())
        val rows = talk.groups.flatMap { it.settings }
        assertEquals(Setting.Toggle("jbl_av_anc", true), rows.first { it.key == "jbl_av_anc" })
        assertEquals(Setting.Choice("jbl_av_ambient", "medium", listOf("low", "medium", "high")), rows.first { it.key == "jbl_av_ambient" })
        assertEquals("user", (rows.first { it.key == "jbl_av_eq" } as Setting.Choice).value)
        assertTrue(talk.state.values.values.any { it is Value.Number && it.value == 87 })
        assertEquals(AvneraPacket.write(133, 0).bytes().toList(), talk.change(SettingChange.Toggle("jbl_av_anc", false))!!.single().bytes().toList())
        assertEquals(AvneraPacket.write(134, 3).bytes().toList(), talk.change(SettingChange.Choice("jbl_av_ambient", "high"))!!.single().bytes().toList())
        assertEquals(AvneraPacket.write(146, 1).bytes().toList(), talk.change(SettingChange.Choice("jbl_av_eq", "jazz"))!!.single().bytes().toList())
    }

    @Test
    fun theUserBandsAreReadAndWrittenAsSignedWords() {
        val talk = AvneraTalk(AvneraModels.fromName("JBL EVEREST ELITE 300")!!)
        val bands = IntArray(12)
        bands[0] = 4
        bands[1] = 10
        for (i in 0 until 10) bands[2 + i] = if (i == 3) -5 else 0
        talk.feed(answer(146, 4) + answer(148, *bands))
        val level = talk.groups.flatMap { it.settings }.first { it.key == "jbl_av_band_3" }
        assertEquals(Setting.Level("jbl_av_band_3", -5, -10, 10), level)
        assertEquals(AvneraPacket.write(149, 4, 3, -2).bytes().toList(), talk.change(SettingChange.Level("jbl_av_band_3", -2))!!.single().bytes().toList())
    }

    @Test
    fun theBootAndConfigurationKeysNeverGoOutAndTheNamesAreMatched() {
        val talk = AvneraTalk(AvneraModels.ALL.first())
        assertTrue(!talk.allowed(AvneraPacket.write(42, 1)), "BootJumpToApplication")
        assertTrue(!talk.allowed(AvneraPacket.write(39, 1)), "BootWriteImage")
        assertTrue(talk.allowed(AvneraPacket.write(133, 1)))
        assertEquals("JBL Everest Elite 150NC", AvneraModels.fromName("JBL EVEREST ELITE 150NC")?.name)
        assertEquals("JBL Everest Elite 750NC", AvneraModels.fromName("Everest Elite 750NC")?.name)
        assertEquals("JBL Reflect Aware", AvneraModels.fromName("JBL Reflect Aware")?.name)
        assertNull(AvneraModels.fromName("JBL Live 670NC"))
    }

    @Test
    fun theOneFiftyNcWrapsTheSameKeysInTheAccessoryHeader() {
        val talk = AvneraTalk(AvneraModels.fromName("JBL Everest Elite 150NC")!!)
        // ANC on: F085 is key 0x85 of class 0xF0, module 0x43 (z6.s, z6.f0.a): header 0f 43 82 00, the word 00 00 f0 85, one word of buffer.
        val on = talk.change(SettingChange.Toggle("jbl_av_anc", true))!!.single()
        assertEquals("ff5a00000017" + "0f43820000" + "00f085" + "04000000" + "01000000", hex(talk.bytes(on)).dropLast(2))
        // A read carries the answer's size (one word), not 256 bytes.
        val read = talk.start().first { it.key == 133 }
        assertEquals("ff5a00000017" + "0f43810000" + "00f085" + "04000000" + "00000000", hex(talk.bytes(read)).dropLast(2))
        // The band write is class F3, three words.
        assertEquals(0xF3, talk.bytes(AvneraPacket.write(149, 4, 0, 1))[6 + 6].toInt() and 0xFF)
    }
}
