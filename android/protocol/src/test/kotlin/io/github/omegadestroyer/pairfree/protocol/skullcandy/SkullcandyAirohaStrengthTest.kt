package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.jbl.JblRace
import io.github.omegadestroyer.pairfree.protocol.jbl.RacePacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The strength of noise cancelling and transparency on the Airoha models without an SDK configuration (docs/skullcandy/PROTOCOL.md section 9.7), against the Airoha SDK's gain stages.
 */
class SkullcandyAirohaStrengthTest {
    private fun hex(b: ByteArray) = b.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }
    private fun unhex(s: String) = s.split(' ').filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()
    private fun talk(enumName: String) = SkullcandyRaceTalk(SkullcandyProducts.ALL.first { it.enumName == enumName })
    private fun rows(t: SkullcandyRaceTalk) = t.groups.flatMap { it.settings }
    private fun nv(data: ByteArray) = RacePacket(JblRace.NV_READ, byteArrayOf((data.size and 0xFF).toByte(), (data.size shr 8).toByte()) + data, JblRace.TYPE_RESPONSE).bytes()

    /** A talk of [enumName] whose chip is [chipName], whose ANC state key says [ancDb] and [passDb], and whose module read says [filter]. */
    private fun ready(enumName: String, chipName: String, ancDb: Int, passDb: Int, filter: Int): SkullcandyRaceTalk {
        val t = talk(enumName)
        val start = t.start()
        val key = start.first { hex(it.bytes()) == "05 5A 06 00 00 0A E0 E0 0C 00" }
        t.bytes(key)
        val state = ByteArray(12)
        state[0] = 1
        state[1] = filter.toByte()
        state[4] = (ancDb * 100).toByte(); state[5] = ((ancDb * 100) shr 8).toByte()
        state[6] = (passDb * 100).toByte(); state[7] = ((passDb * 100) shr 8).toByte()
        t.feed(nv(state))
        t.feed(byteArrayOf(0x05, 0x5B, 0x06, 0x00, 0x01, 0x09, JblRace.MMI_ANC.toByte(), 0x00, 0x00, filter.toByte()))
        val chipRead = start.first { it.id == JblRace.NV_READ && hex(it.bytes()).startsWith("05 5A 06 00 00 0A 02 10") }
        t.bytes(chipRead)
        t.feed(nv(chipName.encodeToByteArray() + ByteArray(8)))
        return t
    }

    @Test
    fun theProductsThatHaveIt() {
        fun with(f: (SkullcandyAirohaStrength) -> Boolean) = SkullcandyProducts.ALL.filter { f(SkullcandyAirohaStrength(it)) }.map { it.enumName }.toSet()
        assertTrue("hanc" in with { it.anc })
        assertTrue("hanc" in with { it.transparency })
        assertTrue(with { it.has }.none { it == "railANC" || it == "rail" })
    }

    @Test
    fun noiseCancellingStrengthIsTheGainOfTheNoiseCancellingFilter() {
        val t = ready("hanc", "AB1568", -10, -20, 1)
        // -10 dB is 50 percent, which is 5 of 10 on the slider
        assertEquals(5, (rows(t).first { it.key == SkullcandyRaceTalk.ANC_LEVEL } as Setting.Level).value)
        // 8 of 10 is 80 percent: -4 dB, 0xFE70 low byte first; the AB1568 takes no trailing byte
        val out = t.change(SettingChange.Level(SkullcandyRaceTalk.ANC_LEVEL, 8))!!
        // the app sends the filter first (ANC1 with mode 0), then the gain
        assertEquals(listOf("05 5A 06 00 06 0E 00 0A 01 00", "05 5A 06 00 06 0E 00 0F 70 FE"), out.take(2).map { hex(it.bytes()) })
        assertTrue(out.all { t.allowed(it) })
    }

    @Test
    fun theAb1562AddsItsTrailingByte() {
        val t = ready("hanc", "AB1562", -20, -20, 1)
        assertEquals(listOf("05 5A 07 00 06 0E 00 0A 01 00 01", "05 5A 07 00 06 0E 00 0F 00 00 01"), t.change(SettingChange.Level(SkullcandyRaceTalk.ANC_LEVEL, 10))!!.take(2).map { hex(it.bytes()) })
    }

    @Test
    fun transparencyStrengthFollowsTheTransparencyFilterOnEveryChip() {
        for (raw in listOf(9, 5, 12)) {
            val t = ready("hanc", "AB158X", -20, -4, raw)
            // -4 dB is 80 percent
            assertEquals(8, (rows(t).first { it.key == SkullcandyRaceTalk.TRANSPARENCY_LEVEL } as Setting.Level).value, "filter $raw")
            // 5 of 10 is -10 dB: -1000 is 0xFC18, low byte first
            assertEquals("05 5A 06 00 06 0E 00 0F 18 FC", hex(t.change(SettingChange.Level(SkullcandyRaceTalk.TRANSPARENCY_LEVEL, 5))!![1].bytes()))
        }
    }

    @Test
    fun noWriteBeforeTheChipIsKnown() {
        val t = talk("hanc")
        assertNull(t.change(SettingChange.Level(SkullcandyRaceTalk.ANC_LEVEL, 5)))
    }

    @Test
    fun theNoiseModeUsesTheAppsFiltersForTheChip() {
        // hanc: noise cancelling is ANC1 (1), transparency vivid pass-through 1, which only the AB158x has (raw 12); without capabilities the Push ANC Active's mode byte is 18
        val t158 = ready("hanc", "AB158X", -10, -20, 1)
        val anc = t158.set(io.github.omegadestroyer.pairfree.protocol.Command.ANC_MODE, io.github.omegadestroyer.pairfree.protocol.Command.ANC_MODE.labels.entries.first { it.value == "anc" }.key)!!.single()
        assertEquals("05 5A 06 00 06 0E 00 0A 01 00", hex(anc.bytes()))
        val tr = t158.set(io.github.omegadestroyer.pairfree.protocol.Command.ANC_MODE, io.github.omegadestroyer.pairfree.protocol.Command.ANC_MODE.labels.entries.first { it.value == "transparency" }.key)!!.single()
        assertEquals("05 5A 06 00 06 0E 00 0A 0C 12", hex(tr.bytes()))
        // the AB1562 has no vivid filter, so no write; its noise cancelling carries the trailing 01
        val t1562 = ready("hanc", "AB1562", -10, -20, 1)
        assertNull(t1562.set(io.github.omegadestroyer.pairfree.protocol.Command.ANC_MODE, io.github.omegadestroyer.pairfree.protocol.Command.ANC_MODE.labels.entries.first { it.value == "transparency" }.key))
        assertEquals("05 5A 07 00 06 0E 00 0A 01 00 01", hex(t1562.set(io.github.omegadestroyer.pairfree.protocol.Command.ANC_MODE, io.github.omegadestroyer.pairfree.protocol.Command.ANC_MODE.labels.entries.first { it.value == "anc" }.key)!!.single().bytes()))
    }

    @Test
    fun aChangeIsSavedToTheStateKeyLikeTheApp() {
        // a headset has no other earbud to relay to; the AB1568 reclaims and writes the key 0E0E0 after the gain
        val t = ready("crusher900ANC", "AB1568", -10, -20, 9)
        val out = t.change(SettingChange.Level(SkullcandyRaceTalk.TRANSPARENCY_LEVEL, 5))!!.map { hex(it.bytes()) }
        assertEquals(
            listOf(
                "05 5A 06 00 06 0E 00 0A 09 00",
                "05 5A 06 00 06 0E 00 0F 18 FC",
                "05 5A 04 00 03 0A 0C 00",
                "05 5A 10 00 01 0A E0 E0 01 09 00 00 18 FC 18 FC 00 00 00 00",
            ),
            out,
        )
    }

    @Test
    fun theAb158xReportsItsCapabilitiesAndTheModeByteFollowsThem() {
        val t = ready("hanc", "AB158X", -10, -20, 1)
        val asked = t.takeOutbox().first { hex(it.bytes()) == "05 5A 08 00 30 0E 00 01 00 00 00 00" }
        t.bytes(asked)
        // the ANC level 1, the pass-through level 2 (hybrid, no vivid)
        t.feed(unhex("05 5B 0A 00 30 0E 00 01 01 02 00 00 00 00"))
        val cmd = io.github.omegadestroyer.pairfree.protocol.Command.ANC_MODE
        val tr = t.set(cmd, cmd.labels.entries.first { it.value == "transparency" }.key)!!.single()
        // hybrid pass-through 1 is ordinal 8, raw 5, mode byte 12
        assertEquals("05 5A 06 00 06 0E 00 0A 05 0C", hex(tr.bytes()))
    }
}
