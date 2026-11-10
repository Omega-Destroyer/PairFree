package io.github.omegadestroyer.pairfree.protocol.oneplus

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
        assertEquals(listOf(0x0100, 0x0102, 0x0106, 0x0103, 0x0101, 0x010C, 0x010F, 0x010D, 0x0108), sent.map { it.command })
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
        assertEquals("Balanced (Default)", (groups.first { it.key == "op_eq_preset" } as Setting.Choice).value)

        fun payloadOf(frame: ByteArray) = parsed(frame).let { "%04x".format(it.command) to it.payload.joinToString("") { b -> "%02x".format(b) } }

        val nc = payloadOf(s.set(Command.ANC_MODE, anc("anc"))!!.single())
        assertEquals("0404" to vector("0404", "010102").getValue("payload").jsonPrimitive.content, nc)
        val transparency = payloadOf(s.set(Command.ANC_MODE, anc("transparency"))!!.single())
        assertEquals("0404" to vector("0404", "010104").getValue("payload").jsonPrimitive.content, transparency)
        // Off is not in the captured trace (the clicks wrote nothing): the bit of index 0.
        assertEquals("0404" to "010101", payloadOf(s.set(Command.ANC_MODE, anc("off"))!!.single()))
        for ((name, payload) in listOf("Bold" to "03", "Clear Vocals" to "02", "Bass" to "01", "Balanced (Default)" to "00")) {
            assertEquals("0406" to vector("0406", payload).getValue("payload").jsonPrimitive.content, payloadOf(s.change(SettingChange.Choice("op_eq_preset", name))!!.single()))
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
    fun nothingIsSentThatTheModelDidNotAnswerAndDebugCommandsAreRefused() {
        val s = budsPro2()
        assertEquals(null, s.set(Command.ANC_MODE, anc("anc")))
        assertEquals(null, s.change(SettingChange.Toggle("game_mode", true)))
        assertFalse(s.allowed(OneplusPacket(0x0F00, 0).bytes()))
        assertFalse(s.allowed(OneplusPacket(0x0107, 0).bytes()))
    }
}
