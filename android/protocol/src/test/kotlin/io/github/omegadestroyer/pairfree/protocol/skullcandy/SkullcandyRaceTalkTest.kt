package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.jbl.JblRace
import io.github.omegadestroyer.pairfree.protocol.jbl.RacePacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The second channel's conversation: Skullcandy's custom RACE commands and the custom equaliser (docs/skullcandy/PROTOCOL.md sections 11 and 13). */
class SkullcandyRaceTalkTest {
    private fun talk(name: String) = SkullcandyRaceTalk(SkullcandyModels.fromName(name)!!)

    /** The NV read's answer holding [text]: the length, then the text. */
    private fun nvAnswer(text: String): ByteArray {
        val b = text.encodeToByteArray()
        return RacePacket(JblRace.NV_READ, byteArrayOf(b.size.toByte(), 0) + b, JblRace.TYPE_RESPONSE).bytes()
    }

    @Test
    fun theRaceSessionHoldsNoAtLines() {
        val s = talk("Hesh 360")
        assertTrue(s.hasMultipoint)
        assertTrue(s.start().isNotEmpty())
        assertTrue(s.poll().isEmpty())
    }

    @Test
    fun onlyAirohaModelsWithAFeatureOfTheChannelOpenIt() {
        val with = SkullcandyProducts.ALL.filter { SkullcandyRaceTalk(it).hasRaceFeatures }
        assertTrue(with.isNotEmpty())
        assertEquals(emptyList<String>(), with.filter { it.chip != "airoha" }.map { it.name })
    }

    @Test
    fun aMultipointReadAndWriteGoThroughTheRaceChannel() {
        val s = talk("Hesh 360")
        // read first, in the form of the app's own class for this model (NV key F5 00)
        val read = s.start().first { it.bytes().toList() == SkullcandyRace.bytes(SkullcandyRace.multipointGet("hesh360")).toList() }
        s.bytes(read)
        // 05 5B 05 00 00 0A 01 00 01: the NV read's answer, the value last
        assertTrue(s.feed(RacePacket(0x0A00, byteArrayOf(0x01, 0x00, 0x01), 0x5B).bytes()).changed)
        val on = s.groups.single { it.key == "connection" }.settings.single() as Setting.Toggle
        assertTrue(on.on)
        val out = s.change(SettingChange.Toggle(SkullcandyRaceTalk.MULTIPOINT, false))!!
        assertEquals(SkullcandyRace.bytes("05 5A 03 00 01 14 00").toList(), out.single().bytes().toList())
        assertTrue(s.allowed(out.single()))
        assertEquals(listOf(read), s.takeReadBacks())
    }

    @Test
    fun theCustomCurveNeedsTheChipFirstThenWritesAsAirohaDoes() {
        val s = talk("Grind")
        assertTrue(s.hasCustomEq)
        assertEquals(5, s.eqBandsHz.size)
        assertEquals(12, s.eqRangeDb)
        assertNull(s.eqCurve(List(5) { 1.0 }))
        val chipRead = s.start().first()
        assertTrue(s.allowed(chipRead))
        s.bytes(chipRead)
        s.feed(nvAnswer("AB1565"))
        assertNull(s.eqCurve(List(4) { 0.0 }))
        val frames = s.eqCurve(listOf(3.0, 0.0, -2.0, 1.0, 4.0))
        assertNotNull(frames)
        assertTrue(frames!!.isNotEmpty())
        assertEquals(listOf(3.0, 0.0, -2.0, 1.0, 4.0), s.eqGains)
        val parser = io.github.omegadestroyer.pairfree.protocol.jbl.RaceParser()
        for (f in frames) for (p in parser.feed(f)) assertTrue(s.allowed(p), p.toString())
    }

    @Test
    fun theBandLayoutsAndRangesAreTheAppsOwn() {
        assertEquals(listOf(60, 250, 1000, 4000, 12000), SkullcandyEq.bandsHz("grind"))
        assertEquals(10, SkullcandyEq.bandsHz("crusherPlyr").size)
        assertEquals(7, SkullcandyEq.bandsHz("session720").size)
        assertEquals(6, SkullcandyEq.rangeDb("hesh360"))
        assertEquals(12, SkullcandyEq.rangeDb("crusherANC2"))
        assertFalse(SkullcandyRaceTalk(SkullcandyModels.fromName("Icon 180")!!).hasRaceFeatures)
    }

    @Test
    fun thePresetsAreTheAppsModesByTheirNumbers() {
        assertEquals(listOf("Music" to 1, "Podcast" to 2, "Movie" to 3), SkullcandyEq.presets("grind"))
        assertEquals(listOf("Supreme Sound" to 1, "FPS" to 2, "RPG" to 3, "Music" to 4), SkullcandyEq.presets("crusherPlyr"))
        assertEquals(listOf("Music" to 1, "Podcast" to 2, "Bass Boost" to 3), SkullcandyEq.presets("hesh360"))
        assertEquals(emptyList<Pair<String, Int>>(), SkullcandyEq.presets("terrain"))
    }

    @Test
    fun aPresetIsPickedByItsGroupOnAnyChip() {
        val s = talk("Grind")
        assertNull(s.eqPreset("Music"))
        val chipRead = s.start().first()
        s.bytes(chipRead)
        s.feed(nvAnswer("AB1568"))
        assertNull(s.eqPreset("Not a preset"))
        val frames = s.eqPreset("Podcast")
        assertNotNull(frames)
        assertEquals(1, frames!!.size)
        assertEquals("Podcast", s.eqPresetName)
        val parser = io.github.omegadestroyer.pairfree.protocol.jbl.RaceParser()
        for (f in frames) for (p in parser.feed(f)) assertTrue(s.allowed(p), p.toString())
        // a curve of the user's own takes the preset's place
        s.eqCurve(listOf(0.0, 1.0, 0.0, 0.0, 0.0))
        assertNull(s.eqPresetName)
    }
}
