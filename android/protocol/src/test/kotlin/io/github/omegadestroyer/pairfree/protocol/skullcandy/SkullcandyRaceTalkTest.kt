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

    /** An NV read's answer: the length, then [data]. */
    private fun nvData(data: ByteArray) = RacePacket(JblRace.NV_READ, byteArrayOf(data.size.toByte(), (data.size shr 8).toByte()) + data, JblRace.TYPE_RESPONSE).bytes()

    private fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    /** A PEQ subset of [keys]: the count, then one entry of one record per group. */
    private fun subset(keys: List<Int>) = le16(keys.size) + keys.flatMap { (le16(1) + le16(0) + le16(it)).toList() }.toByteArray()

    /** Answers what the read-back asks, in order: the audio path, the subset, the running group, and for a custom group its band data. */
    private fun readBack(s: SkullcandyRaceTalk, keys: List<Int>, running: Int, uiData: ByteArray?, chip: String = "AB1550") {
        s.bytes(s.start().first())
        s.feed(nvAnswer(chip))
        // the read the chip answer starts is one the session lets through
        s.takeOutbox().forEach { assertTrue(s.allowed(it), it.toString()) }
        // the AB1568 reads its NVDM version first, which decides the keys
        if (chip.startsWith("AB1568")) s.feed(nvData(byteArrayOf(1)))
        s.feed(nvData(ByteArray(4)))
        s.feed(nvData(subset(keys)))
        s.feed(RacePacket(JblRace.MMI_GET, byteArrayOf(0, 0, 0, running.toByte()), JblRace.TYPE_RESPONSE).bytes())
        if (uiData != null) s.feed(nvData(uiData))
    }

    @Test
    fun whatTheEarbudsHoldIsReadBackOnceTheChipIsKnown() {
        // a preset: the second group runs, which is the number 2 (Podcast on a Grind)
        val custom0 = JblRace.PEQ_COEF_BASE + JblRace.PEQ_CUSTOM_OFFSET
        val presets = talk("Grind")
        readBack(presets, listOf(0xF300, 0xF301, 0xF302, custom0), 2, null)
        assertEquals("Podcast", presets.eqPresetName)
        val dual = talk("Grind")
        readBack(dual, listOf(0xF300, 0xF301, 0xF302, custom0), 3, null, "AB1568")
        assertEquals("Movie", dual.eqPresetName)
        // the custom curve: the fourth group runs; PeqUiDataStru holds 5 header bytes and 18 per band (enable, type, Hz, gain, bandwidth, Q in hundredths)
        val curve = talk("Grind")
        val bands = listOf(60 to 3.0, 250 to 0.0, 1000 to -2.0, 4000 to 1.5, 12000 to 4.0)
        val ui = byteArrayOf(1, 0, 0, 0, 0) + bands.flatMap { (hz, g) ->
            (byteArrayOf(1, 2) + le32(hz * 100) + le32((g * 100).toInt()) + le32((hz / 0.91 * 100).toInt()) + le32(91)).toList()
        }.toByteArray() + ByteArray(5 * 18)
        readBack(curve, listOf(0xF300, 0xF301, 0xF302, custom0), 4, ui)
        assertNull(curve.eqPresetName)
        assertEquals(listOf(3.0, 0.0, -2.0, 1.5, 4.0), curve.eqGains)
    }

    private fun response(id: Int, vararg payload: Int) = RacePacket(id, ByteArray(payload.size) { payload[it].toByte() }, JblRace.TYPE_RESPONSE).bytes()
    private fun indication(id: Int, payload: ByteArray) = RacePacket(id, payload, JblRace.TYPE_INDICATION).bytes()

    /** A broadcast the earbuds report: status 0, the address type and address (as sent), the set, the signal and the advertising data (the public broadcast announcement and the name). */
    private fun broadcast(name: String, addr: List<Int>, encrypted: Boolean): ByteArray {
        val data = byteArrayOf(4, 0x16, 0x56, 0x18, if (encrypted) 1 else 0) + byteArrayOf((name.length + 1).toByte(), 0x30) + name.encodeToByteArray()
        return byteArrayOf(0, 1) + addr.map { it.toByte() }.toByteArray() + byteArrayOf(5, -50, data.size.toByte()) + data
    }

    @Test
    fun theCrusher1080sAuracastReceiverScansPicksAndPlaysOverRace() {
        val s = talk("Crusher 1080 ANC")
        assertTrue(s.hasAuracast)
        assertTrue(s.hasRaceFeatures)
        assertTrue(s.groups.none { it.key == "sh_auracast" })
        val start = s.start()
        assertTrue(start.any { it.id == 0x2208 })
        start.forEach { assertTrue(s.allowed(it), it.toString()) }
        s.bytes(start.first { it.id == 0x2208 })
        s.feed(response(0x2208, 0, 1))
        val rows = { s.groups.first { it.key == "sh_auracast" }.settings }
        assertEquals(Setting.Action("sh_auracast_scan", false), rows().first())
        // scanning
        val scan = s.change(SettingChange.Press("sh_auracast_scan"))!!.single()
        assertEquals("05 5A 03 00 00 22 03", scan.bytes().joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() })
        assertTrue(s.allowed(scan))
        s.feed(indication(0x2200, broadcast("Cafe", listOf(1, 2, 3, 4, 5, 6), false)))
        s.feed(indication(0x2200, broadcast("Locked", listOf(9, 8, 7, 6, 5, 4), true)))
        assertEquals(Setting.Choice("sh_auracast_add", null, listOf("Cafe", "Locked")), rows().first { it.key == "sh_auracast_add" })
        // an open broadcast: the source is picked with its address as it came, then its first subgroup is played
        val pick = s.change(SettingChange.Choice("sh_auracast_add", "Cafe"))!!.single()
        assertEquals(0x2201, pick.id)
        assertEquals("010203040506" + "01" + "05", pick.payload.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') })
        s.bytes(pick)
        s.feed(response(0x2201, 0))
        val play = s.takeOutbox().single()
        assertEquals(0x2202, play.id)
        assertTrue(s.allowed(play))
        s.feed(response(0x2202, 0))
        s.feed(indication(0x2202, byteArrayOf(0, 1)))
        assertEquals(Setting.Information("sh_auracast_source", "receiving:Cafe"), rows().first { it.key == "sh_auracast_source" })
        val stop = s.change(SettingChange.Choice("sh_auracast_remove", "Cafe"))!!.single()
        assertEquals(0x2203, stop.id)
        // a coded broadcast waits for its code, which goes as sixteen bytes, and a wrong one is reported by the end of the stream
        s.feed(response(0x2203, 0))
        s.change(SettingChange.Choice("sh_auracast_add", "Locked"))
        s.feed(response(0x2201, 0))
        assertEquals(Setting.Information("sh_auracast_source", "code_required:Locked"), rows().first { it.key == "sh_auracast_source" })
        assertTrue(rows().any { it is Setting.Text && it.key == "sh_auracast_code" })
        val code = s.change(SettingChange.Choice("sh_auracast_code", "secret"))!!.single()
        assertEquals(0x2209, code.id)
        assertEquals("736563726574" + "00".repeat(10), code.payload.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') })
        s.feed(indication(0x2203, byteArrayOf(1)))
        assertEquals(Setting.Information("sh_auracast_source", "bad_code:Locked"), rows().first { it.key == "sh_auracast_source" })
        // another model has none of this
        assertFalse(talk("Rail ANC").hasAuracast)
    }

    @Test
    fun theTileRowIsOnTheProductsThatHaveIt() {
        val rail = talk("Rail ANC")
        assertEquals(listOf("sk_tile"), rail.groups.filter { it.key == "sk_tile" }.map { it.key })
        assertTrue(talk("Crusher 1080 ANC").groups.none { it.key == "sk_tile" })
        assertTrue(SkullcandyProducts.ALL.count { "findWithTile" in it.features } >= 22)
    }
}
