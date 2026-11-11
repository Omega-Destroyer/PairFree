package io.github.omegadestroyer.pairfree.protocol.oneplus

import io.github.omegadestroyer.pairfree.protocol.BandEqReport
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.Value
import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * PairFree's own frames against the ones HeyMelody sent to a virtual Buds Pro 2 (data/oneplus/emulator_vectors.json): virtual
 * testing, not hardware. The replies fed in here are invented from HeyMelody's parsers (docs/oneplus/PROTOCOL.md).
 */
class OneplusSessionTest {
    private val vectors = Json.parseToJsonElement(repoFile("data/oneplus/emulator_vectors.json").readText()).jsonObject.getValue("cases").jsonArray
        .map { it.jsonObject }

    private fun vector(command: String, payload: String) = vectors.last { it.getValue("command").jsonPrimitive.content == command && it.getValue("payload").jsonPrimitive.content == payload }

    private fun anc(name: String) = Command.ANC_MODE.labels.entries.first { it.value == name }.key

    private fun parsed(bytes: ByteArray) = OneplusParser().feed(bytes).single()

    private fun reply(command: Int, vararg payload: Int) = OneplusPacket(command or 0x8000, 0, ByteArray(payload.size) { payload[it].toByte() }).bytes()

    private fun budsPro2() = OneplusSession(OneplusModels.byId("062014")).also { assertNotNull(it.model) }

    @Test
    fun theCatalogHasTheBudsPro2WithTheVirtualRunsPresets() {
        val m = OneplusModels.byId("062014")!!
        assertEquals("OnePlus Buds Pro 2", m.name)
        assertEquals(listOf("Balanced (Default)" to 0, "Bold" to 3, "Clear Vocals" to 2, "Bass" to 1), m.presets.map { it.name to it.index })
        assertEquals(m, OneplusModels.fromName("OnePlus Buds Pro 2"))
        assertEquals(85, OneplusModels.ALL.size)
    }

    @Test
    fun startSendsOnlyReadsTheModelListsAndTheFirstFramesAreTheAppsOwn() {
        val s = budsPro2()
        val frames = s.start()
        assertTrue(frames.all { s.allowed(it) })
        val sent = frames.map { parsed(it) }
        assertEquals(listOf(0x0100, 0x0102, 0x0106, 0x0103, 0x0101, 0x010C, 0x010C, 0x010F, 0x010D, 0x0112, 0x0108, 0x0122), sent.map { it.command })
        // The app's own startup: 0100, 0102 FFFF, 0106, 0103, 0101 0002 (the order of the later ones differs).
        for ((command, payload) in listOf("0102" to "ffff", "0101" to "0002")) {
            val v = vector(command, payload)
            val mine = sent.first { "%04x".format(it.command) == command }
            assertEquals(v.getValue("payload").jsonPrimitive.content, mine.payload.joinToString("") { "%02x".format(it) })
        }
    }

    @Test
    fun theWritesMatchWhatHeyMelodySent() {
        val s = budsPro2()
        s.start()
        // Replies as HeyMelody's parsers take them: battery 80/85/100, the mode bitmap, the preset, the game mode switch.
        assertTrue(s.receive(reply(0x0106, 0, 3, 1, 80, 2, 85, 3, 100)))
        assertEquals(Value.Number(80), s.state.values[Command.BATTERY_LEFT])
        assertEquals(Value.Number(100), s.state.values[Command.BATTERY_CASE])
        s.receive(reply(0x010C, 0, 1, 1, 0x01))
        assertEquals(Value.Number(anc("off")), s.state.values[Command.ANC_MODE])
        s.receive(reply(0x010F, 0, 0))
        s.receive(reply(0x010D, 0, 1, 6, 0))
        val groups = s.groups.flatMap { it.settings }
        assertEquals(BandEqReport("Balanced (Default)", null), s.eqReport)

        fun payloadOf(frame: ByteArray) = parsed(frame).let { "%04x".format(it.command) to it.payload.joinToString("") { b -> "%02x".format(b) } }

        val nc = payloadOf(s.set(Command.ANC_MODE, anc("anc"))!!.single())
        assertEquals("0404" to vector("0404", "010102").getValue("payload").jsonPrimitive.content, nc)
        val transparency = payloadOf(s.set(Command.ANC_MODE, anc("transparency"))!!.single())
        assertEquals("0404" to vector("0404", "010104").getValue("payload").jsonPrimitive.content, transparency)
        // Off is not in the captured trace (the clicks wrote nothing): the bit of index 0.
        assertEquals("0404" to "010101", payloadOf(s.set(Command.ANC_MODE, anc("off"))!!.single()))
        for ((name, payload) in listOf("Bold" to "03", "Clear Vocals" to "02", "Bass" to "01", "Balanced (Default)" to "00")) {
            assertEquals("0406" to vector("0406", payload).getValue("payload").jsonPrimitive.content, payloadOf(s.eqPresetFrames(name)!!.also { assertEquals(BandEqReport(name, null), s.eqReport) }.single()))
        }
        assertEquals("0403" to "0601", payloadOf(s.change(SettingChange.Toggle("game_mode", true))!!.single()))
        assertEquals("0403" to "0600", payloadOf(s.change(SettingChange.Toggle("game_mode", false))!!.single()))
        assertEquals(vector("0403", "0601").getValue("payload").jsonPrimitive.content, "0601")
    }

    @Test
    fun noiseCancellingLevelsAreWrittenAsTheirOwnIndexBit() {
        val s = budsPro2()
        s.start()
        s.receive(reply(0x010C, 0, 1, 1, 0x02))
        val row = s.groups.flatMap { it.settings }.first { it.key == OneplusSession.STRENGTH_KEY } as Setting.Choice
        assertEquals(listOf("smart", "strong", "moderate", "light"), row.options)
        assertEquals(null, row.value)
        // Strong is protocol index 4: bit 4 of the first byte; the mode reads back as noise cancelling with the level chosen.
        val frame = parsed(s.change(SettingChange.Choice(OneplusSession.STRENGTH_KEY, "strong"))!!.single())
        assertEquals("0404", "%04x".format(frame.command))
        assertEquals("010110", frame.payload.joinToString("") { "%02x".format(it) })
        assertEquals(Value.Number(anc("anc")), s.state.values[Command.ANC_MODE])
        assertEquals("strong", (s.groups.flatMap { it.settings }.first { it.key == OneplusSession.STRENGTH_KEY } as Setting.Choice).value)
        // The Enco X4's auto mode has sub-modes at indices 12 to 14: a two-byte bitmap.
        val x4 = OneplusSession(OneplusModels.byId("06EC10"))
        x4.start()
        x4.receive(reply(0x010C, 0, 1, 1, 0x00, 0x08))
        val auto = x4.groups.flatMap { it.settings }.first { it.key == OneplusSession.STRENGTH_KEY } as Setting.Choice
        assertEquals(listOf("less_noise", "standard_noise", "more_noise"), auto.options)
        val write = parsed(x4.change(SettingChange.Choice(OneplusSession.STRENGTH_KEY, "more_noise"))!!.single())
        assertEquals("01010040", write.payload.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun touchControlsAreReadAsKeyRecordsAndWrittenAsTheAppDid() {
        val s = budsPro2()
        val start = s.start().map { parsed(it) }
        assertEquals(listOf(0x0108), start.filter { it.command == 0x0108 }.map { it.command })
        assertEquals("020301", start.first { it.command == 0x0108 }.payload.joinToString("") { "%02x".format(it) })
        // Left double press is previous (5), right double press is next (6), as invented replies.
        s.receive(reply(0x0108, 0, 2, 1, 1, 2, 5, 2, 1, 2, 6))
        val rows = s.groups.flatMap { it.settings }.filter { it.key.startsWith("touch_") }.associate { it.key to it as Setting.Choice }
        assertEquals("previous", rows.getValue("touch_double_left").value)
        assertEquals("next", rows.getValue("touch_double_right").value)
        assertTrue("next" in rows.getValue("touch_double_left").options)
        fun payloadOf(frames: List<ByteArray>) = parsed(frames.first()).let { "%04x".format(it.command) to it.payload.joinToString("") { b -> "%02x".format(b) } }
        // The two writes HeyMelody sent in the virtual run: left double press to next, right double press to previous.
        val left = s.change(SettingChange.Choice("touch_double_left", "next"))!!
        assertEquals("0401" to vector("0401", "0101010206").getValue("payload").jsonPrimitive.content, payloadOf(left))
        assertEquals(0x0108, parsed(left[1]).command)
        val right = s.change(SettingChange.Choice("touch_double_right", "previous"))!!
        assertEquals("0401" to vector("0401", "0102010205").getValue("payload").jsonPrimitive.content, payloadOf(right))
        assertEquals("next", (s.groups.flatMap { it.settings }.first { it.key == "touch_double_left" } as Setting.Choice).value)
    }

    @Test
    fun customEqualizersAreReadAndWrittenInHeyMelodysLayout() {
        val s = budsPro2()
        s.start()
        // One equalizer in use: id 3, "Mine", limits -6 and 6, the six default bands at 62, 250, 1000, 4000, 8000 and 16000 Hz, gains 0, 1, 2, 3, -1, -2.
        val bands = listOf(62 to 0, 250 to 1, 1000 to 2, 4000 to 3, 8000 to -1, 16000 to -2).flatMap { (hz, g) -> listOf(hz and 0xFF, hz shr 8, g and 0xFF) }
        val name = "Mine".map { it.code }
        s.receive(reply(0x0122, 0, 1, 1, 0xFA, 6, 3, 4, *name.toIntArray(), 6, *bands.toIntArray()))
        val rows = s.groups.flatMap { it.settings }
        assertEquals("Mine", (rows.first { it.key == OneplusSession.CUSTOM_EQ_KEY } as Setting.Choice).value)
        // The graphic equalizer sees the bands and what the earbuds hold; the rows hold no sliders.
        assertEquals(listOf(62, 250, 1000, 4000, 8000, 16000), s.eqBandsHz)
        assertEquals(BandEqReport(null, listOf(0, 1, 2, 3, -1, -2)), s.eqReport)
        assertTrue(rows.none { it is Setting.Level })
        assertTrue(rows.any { it is Setting.Action && it.key == OneplusSession.NEW_EQ_KEY })
        // Raising the 250 Hz band to +3 writes the whole record again: operation 2, the limits, the id, the name and every band.
        val write = parsed(s.eqCustomFrames(listOf(0, 3, 2, 3, -1, -2))!!.single())
        assertEquals(0x0418, write.command)
        val expected = listOf(2, 0xFA, 6, 3, 4) + name + listOf(6) +
            listOf(62 to 0, 250 to 3, 1000 to 2, 4000 to 3, 8000 to -1, 16000 to -2).flatMap { (hz, g) -> listOf(hz and 0xFF, hz shr 8, g and 0xFF) }
        assertEquals(expected.joinToString("") { "%02x".format(it) }, write.payload.joinToString("") { "%02x".format(it) })
        // A new one: operation 1 with the next free name, flat gains and the default limits, then the read again.
        val frames = s.change(SettingChange.Press(OneplusSession.NEW_EQ_KEY))!!
        val add = parsed(frames[0])
        assertEquals(1, add.payload[0].toInt())
        assertEquals("Custom 1", add.payload.copyOfRange(5, 13).decodeToString())
        assertEquals(0x0122, parsed(frames[1]).command)
        // A model without a custom equalizer asks nothing and shows nothing.
        val none = OneplusSession(OneplusModels.byId("063410"))
        assertFalse(none.start().map { parsed(it).command }.contains(0x0122))
    }

    @Test
    fun theLongPressNoiseCycleIsABitmaskOfModeIndices() {
        val s = budsPro2()
        val start = s.start().map { parsed(it) }.filter { it.command == 0x010C }.map { it.payload.joinToString("") { b -> "%02x".format(b) } }
        assertEquals(listOf("0101", "0201"), start)
        // Noise cancelling (index 1) and transparency (index 2) are in the cycle: bits 1 and 2.
        s.receive(reply(0x010C, 0, 2, 1, 0x06))
        fun toggles() = s.groups.flatMap { it.settings }.filterIsInstance<Setting.Toggle>().associate { it.key to it.on }
        assertEquals(mapOf("cycle_off" to false, "cycle_anc" to true, "cycle_transparency" to true), toggles())
        fun payloadOf(frames: List<ByteArray>?) = frames!!.single().let { parsed(it) }.let { "%04x".format(it.command) to it.payload.joinToString("") { b -> "%02x".format(b) } }
        assertEquals("0404" to "020107", payloadOf(s.change(SettingChange.Toggle("cycle_off", true))))
        assertEquals("0404" to "020105", payloadOf(s.change(SettingChange.Toggle("cycle_anc", false))))
        // Two modes stay: turning a third one off is refused.
        assertEquals(null, s.change(SettingChange.Toggle("cycle_transparency", false)))
        assertEquals(mapOf("cycle_off" to true, "cycle_anc" to false, "cycle_transparency" to true), toggles())
        // A model whose long press differs per earbud (catalog longPressType set) is not asked.
        val perEar = OneplusModels.ALL.first { it.noise.isNotEmpty() && !it.noiseCycle }
        assertFalse(OneplusSession(perEar).start().map { parsed(it) }.any { it.command == 0x010C && it.payload.firstOrNull()?.toInt() == 2 })
    }

    @Test
    fun callControlsAndHoldRowsFollowTheCatalog() {
        // The Buds Pro 3's double press in a call: none or reject (key records for device type 4, button 6); read from the app's layouts, not recorded.
        val pro3 = OneplusSession(OneplusModels.byId("064014"))
        pro3.start()
        pro3.receive(reply(0x0108, 0, 1, 4, 6, 2, 28))
        val call = pro3.groups.flatMap { it.settings }.first { it.key == "touch_double_call_whole" } as Setting.Choice
        assertEquals("reject_call", call.value)
        val frames = pro3.change(SettingChange.Choice("touch_double_call_whole", "none"))!!
        assertEquals("0401" to "0104060200", parsed(frames[0]).let { "%04x".format(it.command) to it.payload.joinToString("") { b -> "%02x".format(b) } })
        // An older Enco Air's hold (byte 4): volume up on the right earbud.
        val air = OneplusSession(OneplusModels.byId("061810"))
        air.start()
        air.receive(reply(0x0108, 0, 2, 1, 1, 4, 11, 2, 1, 4, 12))
        val rows = air.groups.flatMap { it.settings }.filterIsInstance<Setting.Choice>().associate { it.key to it.value }
        assertEquals("volume_up", rows["touch_hold_left"])
        assertEquals("volume_down", rows["touch_hold_right"])
    }

    @Test
    fun spatialAudioAndFindEarbuds() {
        val m = OneplusModels.ALL.first { it.spatialTypes.size >= 2 && it.find && !it.hidden }
        val s = OneplusSession(m)
        val sent = s.start().map { parsed(it) }
        assertTrue(sent.any { it.command == 0x012A })
        s.receive(reply(0x0106, 0, 1, 1, 80))
        s.receive(reply(0x012A, 0, 1))
        val row = s.groups.flatMap { it.settings }.first { it.key == OneplusSession.SPATIAL_KEY } as Setting.Choice
        assertEquals("fixed", row.value)
        assertTrue("head_tracking" in row.options || m.spatialTypes.none { it == 2 })
        val write = parsed(s.change(SettingChange.Choice(OneplusSession.SPATIAL_KEY, "off"))!!.single())
        assertEquals("0422" to "00", "%04x".format(write.command) to write.payload.joinToString("") { "%02x".format(it) })
        // The find button starts and stops the sound with 0400.
        assertTrue(s.groups.flatMap { it.settings }.any { it is Setting.Action && it.key == OneplusSession.FIND_KEY && !it.active })
        assertEquals("01", parsed(s.change(SettingChange.Press(OneplusSession.FIND_KEY))!!.single()).payload.joinToString("") { "%02x".format(it) })
        assertEquals("00", parsed(s.change(SettingChange.Press(OneplusSession.FIND_KEY))!!.single()).payload.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun nothingIsSentThatTheModelDidNotAnswerAndDebugCommandsAreRefused() {
        val s = budsPro2()
        assertEquals(null, s.set(Command.ANC_MODE, anc("anc")))
        assertEquals(null, s.change(SettingChange.Toggle("game_mode", true)))
        assertFalse(s.allowed(OneplusPacket(0x0F00, 0).bytes()))
        assertFalse(s.allowed(OneplusPacket(0x0107, 0).bytes()))
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test
    fun plainSwitchesAreReadFromTheCatalogsIdsAndWrittenAsFeatureIdAndState() {
        val s = budsPro2()
        val sent = s.start().map { parsed(it) }
        // The Buds Pro 2's flags: in-ear detection 4, hearing enhancement 11, dual connection 17, high-quality audio 24, automatic volume 28, bass engine 29 (and game mode 6).
        assertEquals("07" + "06" + "04" + "0b" + "11" + "18" + "1c" + "1d", hex(sent.first { it.command == 0x010D }.payload))
        s.receive(reply(0x010D, 0, 3, 11, 1, 24, 0, 29, 1))
        val rows = s.groups.flatMap { it.settings }.filterIsInstance<Setting.Toggle>().associate { it.key to it.on }
        assertEquals(true, rows["op_sw_11"])
        assertEquals(false, rows["op_sw_24"])
        assertEquals(true, rows["op_sw_29"])
        assertEquals(null, rows["op_sw_4"])
        assertEquals("0403" to "1800", "%04x".format(parsed(s.change(SettingChange.Toggle("op_sw_24", false))!!.single()).command) to hex(parsed(s.change(SettingChange.Toggle("op_sw_24", false))!!.single()).payload))
        assertEquals(null, s.change(SettingChange.Toggle("op_sw_4", true)))
    }

    @Test
    fun dualConnectionListsOtherDevicesAndConnectsOrDisconnectsOne() {
        val s = budsPro2()
        s.start()
        s.receive(reply(0x010D, 0, 1, 17, 1))
        fun device(address: List<Int>, state: Int, flags: Int, name: String) = address + listOf(3 + name.length, state, flags, name.length) + name.encodeToByteArray().map { it.toInt() }
        // This phone (connected), a phone that is connected and one that is not.
        s.receive(reply(0x0112, 0, 3, *(device(listOf(1, 2, 3, 4, 5, 6), 2, 1, "Me") + device(listOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66), 2, 0, "Laptop") + device(listOf(0xA1, 0xA2, 0xA3, 0xA4, 0xA5, 0xA6), 0, 0, "Tablet")).toIntArray()))
        val rows = s.groups.flatMap { it.settings }.filterIsInstance<Setting.Choice>().associateBy { it.key }
        assertEquals(listOf("Tablet"), rows.getValue(OneplusSession.PEER_CONNECT_KEY).options)
        assertEquals(listOf("Laptop"), rows.getValue(OneplusSession.PEER_DISCONNECT_KEY).options)
        val connect = s.change(SettingChange.Choice(OneplusSession.PEER_CONNECT_KEY, "Tablet"))!!.map { parsed(it) }
        // The address goes in the order HeyMelody's text shows it: the read's bytes reversed.
        assertEquals("0429" to "01a6a5a4a3a2a1", "%04x".format(connect[0].command) to hex(connect[0].payload))
        assertEquals(0x0112, connect[1].command)
        assertEquals("02665544332211", hex(parsed(s.change(SettingChange.Choice(OneplusSession.PEER_DISCONNECT_KEY, "Laptop"))!![0]).payload))
        // This phone is never offered.
        assertEquals(null, s.change(SettingChange.Choice(OneplusSession.PEER_DISCONNECT_KEY, "Me")))
    }

    @Test
    fun promptVolumeTapSensitivityAndHeadGesturesAreReadAndWritten() {
        val m = OneplusModels.ALL.first { it.promptVolume.isNotEmpty() && !it.hidden }
        val s = OneplusSession(m)
        assertTrue(s.start().map { parsed(it).command }.contains(0x0130))
        s.receive(reply(0x0130, 0, 6))
        val level = s.groups.flatMap { it.settings }.first { it.key == OneplusSession.PROMPT_KEY } as Setting.Level
        assertEquals(m.promptVolume[0] to m.promptVolume[1], level.min to level.max)
        assertEquals("0427" to "08", "%04x".format(parsed(s.change(SettingChange.Level(OneplusSession.PROMPT_KEY, 8))!!.single()).command) to hex(parsed(s.change(SettingChange.Level(OneplusSession.PROMPT_KEY, 8))!!.single()).payload))
        val tap = OneplusModels.ALL.first { it.tapLevel }
        val t = OneplusSession(tap)
        assertTrue(t.start().map { parsed(it).command }.contains(0x0133))
        t.receive(reply(0x0133, 0, 2, 3))
        assertEquals(2, (t.groups.flatMap { it.settings }.first { it.key == OneplusSession.TAP_KEY } as Setting.Level).value)
        assertEquals("042d" to "05", "%04x".format(parsed(t.change(SettingChange.Level(OneplusSession.TAP_KEY, 9))!!.single()).command) to hex(parsed(t.change(SettingChange.Level(OneplusSession.TAP_KEY, 9))!!.single()).payload))
        val head = OneplusModels.ALL.first { 59 in it.switches }
        val h = OneplusSession(head)
        assertTrue(h.start().map { parsed(it).command }.contains(0x0134))
        h.receive(reply(0x010D, 0, 1, 59, 1))
        h.receive(reply(0x0134, 0, 1))
        assertEquals("shake", (h.groups.flatMap { it.settings }.first { it.key == OneplusSession.HEAD_KEY } as Setting.Choice).value)
        assertEquals("0431" to "00", "%04x".format(parsed(h.change(SettingChange.Choice(OneplusSession.HEAD_KEY, "nod"))!!.single()).command) to hex(parsed(h.change(SettingChange.Choice(OneplusSession.HEAD_KEY, "nod"))!!.single()).payload))
    }

    @Test
    fun theFitTestRegistersForItsEventStartsTheTestAndReadsTheResult() {
        val m = OneplusModels.ALL.first { it.fitTest && !it.hidden }
        val s = OneplusSession(m)
        assertTrue(s.state.hasFitTest)
        val frames = s.startFitTest()!!
        assertTrue(frames.all { s.allowed(it) })
        assertEquals(listOf(0x0201 to "04", 0x0205 to "0104", 0x0405 to "01"), frames.map { parsed(it) }.map { it.command to hex(it.payload) })
        // The event: 0204, not a reply: fit event 4, then (device type, status) for the left and right earbud.
        assertTrue(s.receive(OneplusPacket(0x0204, 0, byteArrayOf(4, 1, 1, 2, 3)).bytes()))
        assertEquals(io.github.omegadestroyer.pairfree.protocol.FitResult(io.github.omegadestroyer.pairfree.protocol.FitResult.Fit.GOOD, io.github.omegadestroyer.pairfree.protocol.FitResult.Fit.NOT_FIT), s.state.fit)
        assertEquals(null, OneplusSession(OneplusModels.ALL.first { !it.fitTest }).startFitTest())
    }
}
