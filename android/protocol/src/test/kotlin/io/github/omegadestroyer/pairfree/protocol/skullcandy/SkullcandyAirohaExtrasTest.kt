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
 * The settings of Skullcandy's Airoha models that go through its own RACE commands (docs/skullcandy/PROTOCOL.md section 9.6), against what the app's Airoha SDK stages and race command classes build.
 */
class SkullcandyAirohaExtrasTest {
    private fun hex(b: ByteArray) = b.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }
    private fun unhex(s: String) = s.split(' ').filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()
    private fun talk(enumName: String) = SkullcandyRaceTalk(SkullcandyProducts.ALL.first { it.enumName == enumName })
    private fun rows(t: SkullcandyRaceTalk) = t.groups.flatMap { it.settings }

    @Test
    fun theProductsThatHaveThem() {
        fun with(f: (SkullcandyAirohaExtras) -> Boolean) = SkullcandyProducts.ALL.filter { f(SkullcandyAirohaExtras(it)) }.map { it.enumName }.toSet()
        assertEquals(setOf("plyr", "slyrProXbox", "slyrPro", "aviatorANC", "hesh540ANC", "crusherPlyr", "crusherPlyrXbox", "crusher720", "crusher900ANC"), with { it.sidetone })
        assertEquals(setOf("plyr", "slyrProXbox", "slyrPro", "crusherPlyr", "crusherPlyrXbox"), with { it.chatMix })
        assertEquals(setOf("slyrProXbox", "slyrPro", "crusherPlyr", "crusherPlyrXbox"), with { it.micGain })
        assertEquals(setOf("push540Open"), with { it.touch })
        assertEquals(setOf("aviatorANC"), with { it.adaptiveAnc })
        assertEquals(setOf("crusherPlyr", "crusherPlyrXbox"), with { it.crusher })
        assertEquals(setOf("aviatorANC", "crusher720", "crusher900ANC", "crusherPlyr", "crusherPlyrXbox", "hesh540ANC"), with { it.thx })
        assertEquals(setOf("aviatorANC", "crusherPlyr", "crusherPlyrXbox"), with { it.thxHead })
        assertEquals(setOf("crusherPlyr", "crusherPlyrXbox"), with { it.led })
    }

    @Test
    fun sidetoneIsSetting7AndItsLevelSetting6() {
        val t = talk("slyrPro")
        val start = t.start().map { hex(it.bytes()) }
        assertTrue("05 5A 04 00 83 2C 07 00" in start)
        assertTrue("05 5A 04 00 83 2C 06 00" in start)
        // answers: status, setting, value; the level is a short, low byte first (-10 is F6 FF, which is 52 on the app's 0 to 100 slider)
        t.feed(unhex("05 5B 06 00 83 2C 00 07 00 01"))
        t.feed(unhex("05 5B 07 00 83 2C 00 06 00 F6 FF"))
        assertEquals(true, (rows(t).first { it.key == "sk_sidetone" } as Setting.Toggle).on)
        assertEquals(5, (rows(t).first { it.key == "sk_sidetone_level" } as Setting.Level).value)
        // the level 5 of 10 is 50: trunc(0.5 * 57) - 40 = -12, which is F4 FF; the app sends the state with it
        val out = t.change(SettingChange.Level("sk_sidetone_level", 5))!!
        assertEquals(listOf("05 5A 05 00 82 2C 07 00 01", "05 5A 06 00 82 2C 06 00 F4 FF"), out.map { hex(it.bytes()) })
        assertTrue(out.all { t.allowed(it) })
        assertEquals("05 5A 05 00 82 2C 07 00 00", hex(t.change(SettingChange.Toggle("sk_sidetone", false))!!.first().bytes()))
    }

    @Test
    fun theGameAndChatMixAndTheMicrophoneVolumeAreSettings0BAnd0C() {
        val t = talk("slyrPro")
        val start = t.start().map { hex(it.bytes()) }
        assertTrue("05 5A 04 00 83 2C 0B 00" in start)
        assertTrue("05 5A 04 00 83 2C 0C 00" in start)
        t.feed(unhex("05 5B 06 00 83 2C 00 0B 00 32"))
        t.feed(unhex("05 5B 06 00 83 2C 00 0C 00 1E"))
        assertEquals(5, (rows(t).first { it.key == "sk_chat_mix" } as Setting.Level).value)
        assertEquals(3, (rows(t).first { it.key == "sk_mic_gain" } as Setting.Level).value)
        assertEquals("05 5A 05 00 82 2C 0B 00 3C", hex(t.change(SettingChange.Level("sk_chat_mix", 6))!!.single().bytes()))
        assertEquals("05 5A 05 00 82 2C 0C 00 64", hex(t.change(SettingChange.Level("sk_mic_gain", 10))!!.single().bytes()))
    }

    @Test
    fun theTouchControlsReadKeyF514AndWriteScenarioTwoBackwards() {
        val t = talk("push540Open")
        val read = t.start().first { hex(it.bytes()) == "05 5A 06 00 00 0A 14 F5 23 00" }
        assertTrue(t.allowed(read))
        t.bytes(read)
        // byte 0 is 00 when the touch controls are on, byte 2 is 01 when scenario 1 is
        val data = ByteArray(0x23).also { it[2] = 1 }
        t.feed(RacePacket(JblRace.NV_READ, byteArrayOf(0x23, 0) + data, JblRace.TYPE_RESPONSE).bytes())
        assertEquals(true, (rows(t).first { it.key == "sk_touch_control" } as Setting.Toggle).on)
        val off = t.change(SettingChange.Toggle("sk_touch_control", false))!!
        assertEquals(listOf("05 5A 03 00 08 00 01"), off.map { hex(it.bytes()) })
        // with scenario 1 off the app turns it on first
        val t2 = talk("push540Open")
        val r2 = t2.start().first { hex(it.bytes()) == "05 5A 06 00 00 0A 14 F5 23 00" }
        t2.bytes(r2)
        t2.feed(RacePacket(JblRace.NV_READ, byteArrayOf(0x23, 0) + ByteArray(0x23).also { it[0] = 1 }, JblRace.TYPE_RESPONSE).bytes())
        assertEquals(false, (rows(t2).first { it.key == "sk_touch_control" } as Setting.Toggle).on)
        val on = t2.change(SettingChange.Toggle("sk_touch_control", true))!!
        assertEquals(listOf("05 5A 03 00 07 00 01", "05 5A 03 00 08 00 00"), on.map { hex(it.bytes()) })
        assertTrue(on.all { t2.allowed(it) })
    }

    @Test
    fun adaptiveNoiseCancellingIsModule1FAndTheAncCommand0022() {
        val t = talk("aviatorANC")
        val read = t.start().first { it.id == JblRace.MMI_GET && it.module == 0x1F }
        assertEquals("05 5A 04 00 01 09 1F 00", hex(read.bytes()))
        t.feed(unhex("05 5B 06 00 01 09 1F 00 00 01"))
        assertEquals(true, (rows(t).first { it.key == "sk_adaptive_anc" } as Setting.Toggle).on)
        val off = t.change(SettingChange.Toggle("sk_adaptive_anc", false))!!.single()
        assertEquals("05 5A 05 00 06 0E 00 22 00", hex(off.bytes()))
        assertTrue(t.allowed(off))
    }

    @Test
    fun theCrusherIntensityIs2CD0And2CD1() {
        val t = talk("crusherPlyr")
        assertTrue("05 5A 02 00 D1 2C" in t.start().map { hex(it.bytes()) })
        t.feed(unhex("05 5B 04 00 D1 2C 00 32"))
        assertEquals(5, (rows(t).first { it.key == "sk_crusher_intensity" } as Setting.Level).value)
        assertEquals("05 5A 03 00 D0 2C 50", hex(t.change(SettingChange.Level("sk_crusher_intensity", 8))!!.single().bytes()))
        assertNull(talk("mod").change(SettingChange.Level("sk_crusher_intensity", 8)))
    }

    @Test
    fun thxIsRaceId01F4WithSubCommands() {
        val t = talk("crusherPlyr")
        val start = t.start().map { hex(it.bytes()) }
        assertTrue("05 5A 04 00 01 F4 00 0C" in start)
        assertTrue("05 5A 04 00 01 F4 00 11" in start)
        assertTrue("05 5A 04 00 01 F4 00 0F" in start)
        // a read of spatial audio answers with the flag, the intensity with a big endian short
        t.feed(unhex("05 5B 05 00 01 F4 00 0C 01"))
        t.feed(unhex("05 5B 06 00 01 F4 00 11 7F FF"))
        t.feed(unhex("05 5B 05 00 01 F4 00 0F 00"))
        assertEquals(true, (rows(t).first { it.key == "sk_thx_spatial" } as Setting.Toggle).on)
        assertEquals(5, (rows(t).first { it.key == "sk_thx_intensity" } as Setting.Level).value)
        assertEquals(false, (rows(t).first { it.key == "sk_thx_head" } as Setting.Toggle).on)
        // a write's answer carries the state too: the byte after the sub-command is 1 for on
        t.feed(unhex("05 5B 05 00 01 F4 00 0B 00"))
        assertEquals(false, (rows(t).first { it.key == "sk_thx_spatial" } as Setting.Toggle).on)
        t.feed(unhex("05 5B 05 00 01 F4 00 0A 01"))
        assertEquals(true, (rows(t).first { it.key == "sk_thx_spatial" } as Setting.Toggle).on)
        assertEquals("05 5A 04 00 01 F4 00 0B", hex(t.change(SettingChange.Toggle("sk_thx_spatial", false))!!.single().bytes()))
        assertEquals("05 5A 04 00 01 F4 00 0D", hex(t.change(SettingChange.Toggle("sk_thx_head", true))!!.single().bytes()))
        assertEquals("05 5A 06 00 01 F4 00 10 FF FF", hex(t.change(SettingChange.Level("sk_thx_intensity", 10))!!.single().bytes()))
        assertTrue(t.allowed(t.change(SettingChange.Toggle("sk_thx_spatial", true))!!.single()))
    }

    @Test
    fun theLedCustomizerReadsAttributesAndWritesModeBrightnessAndColour() {
        val t = talk("crusherPlyr")
        val reads = t.start().filter { hex(it.bytes()).startsWith("05 5A 05 00 83 2C 23 00 0") }
        assertEquals(listOf("05 5A 05 00 83 2C 23 00 00", "05 5A 05 00 83 2C 23 00 01", "05 5A 05 00 83 2C 23 00 02"), reads.map { hex(it.bytes()) })
        // the answers do not name their attribute, so each follows the read asked
        t.bytes(reads[0]); t.feed(unhex("05 5B 08 00 83 2C 00 23 00 FF 00 00"))
        t.bytes(reads[1]); t.feed(unhex("05 5B 08 00 83 2C 00 23 00 80 80 80"))
        t.bytes(reads[2]); t.feed(unhex("05 5B 08 00 83 2C 00 23 00 03 03 02"))
        assertEquals("solid", (rows(t).first { it.key == "sk_led_mode" } as Setting.Choice).value)
        assertEquals(5, (rows(t).first { it.key == "sk_led_brightness" } as Setting.Level).value)
        assertEquals("red", (rows(t).first { it.key == "sk_led_color" } as Setting.Choice).value)
        assertEquals("05 5A 05 00 85 2C 03 02 09", hex(t.change(SettingChange.Choice("sk_led_mode", "random"))!!.single().bytes()))
        assertEquals("05 5A 05 00 85 2C 03 01 01", hex(t.change(SettingChange.Choice("sk_led_mode", "breathing"))!!.single().bytes()))
        assertEquals("05 5A 05 00 86 2C FF FF FF", hex(t.change(SettingChange.Level("sk_led_brightness", 10))!!.single().bytes()))
        assertEquals("05 5A 06 00 88 2C 02 00 00 FF", hex(t.change(SettingChange.Choice("sk_led_color", "blue"))!!.single().bytes()))
    }
}
