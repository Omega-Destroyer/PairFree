package io.github.omegadestroyer.pairfree.protocol.sony

import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.hexBytes
import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun load(name: String): JsonObject = Json.parseToJsonElement(repoFile("data/sony/$name").readText()).jsonObject

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

private fun hex(s: String) = s.replace(" ", "").hexBytes()

private fun message(vararg bytes: Int, table: Int = 1, seq: Int = 0) =
    SonyMessage(if (table == 2) SonyWire.TABLE2 else SonyWire.TABLE1, seq, ByteArray(bytes.size) { bytes[it].toByte() })

private fun out(vararg bytes: Int, table: Int = 1) = SonyOut(table, ByteArray(bytes.size) { bytes[it].toByte() })

/** The frame, the tables and the model table, against Sony's app's constants and Gadgetbridge's notes. */
class SonyProtocolTest {
    private val vectors = load("test_vectors.json")

    @Test
    fun acknowledgementsAreSonysOwn() {
        for (case in vectors.getValue("acks").jsonArray.map { it.jsonObject }) {
            assertArrayEquals(hex(case.str("frame")), SonyFrame.ack(case.getValue("received_seq").jsonPrimitive.int))
        }
    }

    @Test
    fun framesEscapeAndRoundTrip() {
        val payload = byteArrayOf(0x3C, 0x3D, 0x3E, 0x01, 0x10)
        val frame = SonyFrame.build(SonyWire.TABLE1, 1, payload)
        assertEquals(0x3E, frame.first().toInt())
        assertEquals(0x3C, frame.last().toInt())
        // Inside, none of the three special bytes appears unescaped.
        val inside = frame.copyOfRange(1, frame.size - 1).toList()
        assertTrue(inside.none { it == 0x3C.toByte() || it == 0x3E.toByte() })
        assertTrue(inside.windowed(2).any { it == listOf(0x3D.toByte(), 0x2C.toByte()) })
        val parsed = SonyParser().feed(frame).single()
        assertEquals(SonyWire.TABLE1, parsed.dataType)
        assertEquals(1, parsed.seq)
        assertArrayEquals(payload, parsed.payload)
    }

    @Test
    fun theParserWaitsForWholeFramesAndDropsBadOnes() {
        val a = SonyFrame.build(SonyWire.TABLE1, 0, byteArrayOf(0x01, 0x00, 0x40, 0x10))
        val b = SonyFrame.build(SonyWire.TABLE2, 1, byteArrayOf(0x47, 0x01, 0x01, 0x01))
        val bad = a.copyOf().also { it[it.size - 2] = (it[it.size - 2] + 1).toByte() }
        val p = SonyParser()
        assertTrue(p.feed(a.copyOfRange(0, 5)).isEmpty())
        assertEquals(1, p.feed(a.copyOfRange(5, a.size) + bad + b.copyOfRange(0, 3)).size, "the bad checksum is dropped")
        assertEquals(listOf(SonyWire.TABLE2), p.feed(b.copyOfRange(3, b.size)).map { it.dataType })
        assertEquals(0, p.pending)
    }

    @Test
    fun theLinkAlternatesSequenceNumbers() {
        val link = SonyLink()
        assertEquals(0, SonyParser().feed(link.frame(out(0x00, 0x00))).single().seq)
        assertFalse(link.acked(SonyMessage(SonyWire.ACK, 0, ByteArray(0))), "an ACK with the same number is stale")
        assertTrue(link.acked(SonyMessage(SonyWire.ACK, 1, ByteArray(0))))
        assertEquals(1, SonyParser().feed(link.frame(out(0x04, 0x01))).single().seq)
        val (ack, fresh) = link.received(message(0x01, 0x00, 0x40, 0x10, seq = 0))
        assertArrayEquals(SonyFrame.ack(0), ack)
        assertTrue(fresh)
        assertFalse(link.received(message(0x01, 0x00, 0x40, 0x10, seq = 0)).second, "a resend is not handled twice")
    }

    @Test
    fun theFirstReplySaysTheTableSet() {
        for (case in vectors.getValue("first_replies").jsonArray.map { it.jsonObject }) {
            // Start from the other set: the reply's length decides.
            val expected = case.getValue("table_set").jsonPrimitive.int
            val s = SonySession(3 - expected)
            s.receive(SonyMessage(SonyWire.TABLE1, 0, hex(case.str("payload"))))
            assertEquals(expected, s.tableSet, case.str("model"))
            // Set 2 also asks for the functions of its second table.
            val table2 = if (expected == 2) listOf(out(0x06, 0x00, table = 2)) else emptyList()
            assertEquals(listOf(out(0x04, 0x01), out(0x04, 0x02), out(0x06, 0x00)) + table2, s.takeOutbox())
        }
    }

    @Test
    fun theGeneratedTablesMatchTheData() {
        val tables = load("commands.json").getValue("tables").jsonObject
        val objects = mapOf(
            "set1_table1" to SonyCommand.Set1.NAMES,
            "set1_table2" to SonyCommand.Set1Table2.NAMES,
            "set2_table1" to SonyCommand.Set2.NAMES,
            "set2_table2" to SonyCommand.Set2Table2.NAMES,
        )
        for ((key, names) in objects) {
            val rows = tables.getValue(key).jsonObject.getValue("commands").jsonArray.map { it.jsonObject }
            assertEquals(rows.associate { it.getValue("number").jsonPrimitive.int to it.str("name") }, names, key)
        }
        // PairFree reads the commands it uses through set 1's constants; set 2 numbers them alike.
        for (name in listOf("CONNECT_GET_PROTOCOL_INFO", "CONNECT_RET_DEVICE_INFO", "CONNECT_RET_SUPPORT_FUNCTION", "NCASM_GET_PARAM",
            "NCASM_RET_PARAM", "NCASM_SET_PARAM", "NCASM_NTFY_PARAM", "EQEBB_GET_CAPABILITY", "EQEBB_RET_CAPABILITY", "EQEBB_GET_PARAM",
            "EQEBB_RET_PARAM", "EQEBB_SET_PARAM", "EQEBB_NTFY_PARAM", "EQEBB_GET_EXTENDED_INFO", "EQEBB_RET_EXTENDED_INFO",
            "AUDIO_GET_PARAM", "AUDIO_RET_PARAM", "AUDIO_SET_PARAM", "AUDIO_NTFY_PARAM", "SYSTEM_GET_PARAM", "SYSTEM_RET_PARAM",
            "SYSTEM_SET_PARAM", "SYSTEM_NTFY_PARAM")
        ) {
            val one = SonyCommand.Set1.NAMES.entries.single { it.value == name }.key
            assertEquals(name, SonyCommand.Set2.NAMES[one], name)
        }
        // Firmware update is never sent.
        assertTrue(SonyCommand.Set2.UPDT_SET_PARAM in SonyCommand.Set2.NEVER_SEND)
        assertFalse(SonySession(2).allowed(out(SonyCommand.Set2.UPDT_SET_PARAM, 0)))
        assertTrue(SonySession(2).allowed(out(0x66, 0x17)))
    }

    @Test
    fun modelsAreKnownByServiceOrName() {
        val devices = load("devices.json").getValue("devices").jsonArray.map { it.jsonObject }
        assertEquals(devices.map { it.str("name") }, SonyModels.ALL.map { it.name })
        assertEquals("LinkBuds S", SonyModels.fromName("LinkBuds S")?.name, "the longest name wins")
        assertEquals("LinkBuds", SonyModels.fromName("LinkBuds")?.name)
        assertEquals("WH-1000XM4", SonyModels.fromName("WH-1000XM4")?.name)
        assertNull(SonyModels.fromName("LE_WH-1000XM5"), "the LE Audio side is not opened")
        assertNull(SonyModels.fromName("SOUNDPEATS H3"))
        // By the services the phone lists: set 2's first.
        val both = listOf(SonyWire.SET1_SERVICE.lowercase(), SonyWire.SET2_SERVICE.lowercase())
        assertEquals(SonyWire.SET2_SERVICE, SonyModels.identify("My headphones", both)?.service)
        assertNull(SonyModels.identify("My headphones", both)?.model)
        assertEquals(SonyWire.SET1_SERVICE, SonyModels.identify("WH-1000XM3", emptyList())?.service, "Gadgetbridge saw set 1")
        assertEquals(SonyWire.SET2_SERVICE, SonyModels.identify("WH-1000XM4", emptyList())?.service, "set 2 when nobody saw")
        assertNull(SonyModels.identify("Galaxy Buds3", emptyList()))
        assertEquals(1, SonyModels.tableSetOf(SonyWire.SET1_SERVICE))
        assertEquals(2, SonyModels.tableSetOf(SonyWire.SET2_SERVICE))
        assertEquals(Form.HEADPHONES, SonyModels.model("WH-1000XM4")?.form)
        assertEquals(Form.EARBUDS, SonyModels.model("WF-1000XM5")?.form)
        assertEquals(Form.EARBUDS, SonyModels.model("WF-9999", Form.EARBUDS)?.form, "an unknown model takes the form it is given")
    }

    @Test
    fun presetsComeFromTheData() {
        val presets = load("eq_presets.json").getValue("presets").jsonArray.map { it.jsonObject }
        assertEquals(presets.map { it.getValue("id").jsonPrimitive.int }, SonyEqs.ALL.map { it.id })
        assertEquals(presets.map { it["custom"]?.jsonPrimitive?.booleanOrNull == true }, SonyEqs.ALL.map { it.custom })
        assertEquals("Bright", SonyEqs.of(0x10)?.label)
        assertTrue(SonyEqs.of(0xA0)!!.custom)
        assertTrue(presets.none { it.getValue("id").jsonPrimitive.int == SONY_EQ_UNSPECIFIED }, "FF names no preset")
        assertTrue(presets.all { it.getValue("label").jsonPrimitive.content.isNotBlank() })
        assertTrue(presets.filter { it.containsKey("custom") }.all { it.getValue("custom").jsonPrimitive.boolean })
    }
}

/** One connection, set by set, against the layouts read from Sony's app. */
class SonySessionTest {
    private fun setting(s: SonySession, key: String) = s.groups.flatMap { it.settings }.firstOrNull { it.key == key }

    /** A set 2 session that has listed [functions] (each with version 0). */
    private fun set2(vararg functions: Int): SonySession {
        val s = SonySession(2)
        s.receive(message(0x01, 0x00, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00))
        s.takeOutbox()
        s.receive(message(*(intArrayOf(0x07, 0x00, functions.size) + functions.flatMap { listOf(it, 0) })))
        return s
    }

    private fun set1(vararg functions: Int): SonySession {
        val s = SonySession(1)
        s.receive(message(0x01, 0x00, 0x40, 0x10))
        s.takeOutbox()
        s.receive(message(0x07, 0x00, functions.size, *functions))
        return s
    }

    @Test
    fun startsWithTheProtocolRequest() {
        assertEquals(listOf(out(0x00, 0x00)), SonySession(2).start())
    }

    @Test
    fun readsWhatSet2Lists() {
        val f = SonyFunction.Set2
        val s = set2(
            f.LEFT_RIGHT_BATTERY_LEVEL_INDICATOR, f.CRADLE_BATTERY_LEVEL_INDICATOR,
            f.MODE_NC_ASM_NOISE_CANCELLING_DUAL_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT, f.PRESET_EQ, f.UPSCALING_AUTO_OFF,
            f.PLAYBACK_CONTROL_BY_WEARING_REMOVING_HEADPHONE_ON_OFF, f.SMART_TALKING_MODE_TYPE2,
        )
        assertEquals(
            listOf(
                out(0x22, 0x01), out(0x22, 0x02), out(0x66, 0x17), out(0x50, 0x00), out(0x5A, 0x00), out(0x56, 0x00),
                out(0xE6, 0x01), out(0xF6, 0x01), out(0xF6, 0x0C),
            ),
            s.takeOutbox(),
        )
        assertEquals(listOf(out(0x22, 0x01), out(0x22, 0x02)), s.batteryRequests())
    }

    @Test
    fun readsWhatSet1Lists() {
        val f = SonyFunction.Set1
        val s = set1(f.BATTERY_LEVEL, f.NOISE_CANCELLING_AND_AMBIENT_SOUND_MODE, f.PRESET_EQ, f.UPSCALING, f.CONTROL_BY_WEARING,
            f.SMART_TALKING_MODE, f.VOICE_GUIDANCE)
        assertEquals(
            listOf(
                out(0x10, 0x00), out(0x66, 0x02), out(0x50, 0x01), out(0x5A, 0x01), out(0x56, 0x01),
                out(0xE6, 0x02), out(0xF6, 0x03), out(0xF6, 0x05), out(0x46, 0x01, 0x01, table = 2),
            ),
            s.takeOutbox(),
        )
    }

    @Test
    fun theModelNameAndFirmwareMakeTheFirmwareLine() {
        val s = SonySession(2)
        s.receive(message(0x05, 0x01, 10, *"WH-1000XM5".encodeToByteArray().map { it.toInt() }.toIntArray()))
        s.receive(message(0x05, 0x02, 5, *"1.1.3".encodeToByteArray().map { it.toInt() }.toIntArray()))
        assertEquals("WH-1000XM5", s.state.firmware?.code)
        assertEquals("1.1.3", s.state.firmware?.version)
    }

    @Test
    fun batteriesBySet() {
        val s = set2(SonyFunction.Set2.LEFT_RIGHT_BATTERY_LEVEL_INDICATOR)
        // Left 70 charging, right away (0, as Gadgetbridge reads a missing earbud), then the case.
        s.receive(message(0x23, 0x01, 70, 1, 0, 0))
        s.receive(message(0x25, 0x02, 55, 0))
        assertEquals(70, s.state.batteryLeft)
        assertNull(s.state.batteryRight)
        assertEquals(55, s.state.batteryCase)
        val one = set1(SonyFunction.Set1.BATTERY_LEVEL)
        one.receive(message(0x11, 0x00, 40, 0))
        assertEquals(40, one.state.batteryLeft)
        // With thresholds (set 2's types 8 to 10), two more bytes follow.
        val t = set2(SonyFunction.Set2.LR_BATTERY_LEVEL_WITH_THRESHOLD)
        assertEquals(listOf(out(0x22, 0x09)), t.batteryRequests())
        t.receive(message(0x23, 0x09, 60, 0, 65, 0, 20, 20))
        assertEquals(65, t.state.batteryRight)
    }

    @Test
    fun set2NoiseControlKeepsTheEarbudsLayout() {
        val s = set2(SonyFunction.Set2.MODE_NC_ASM_NOISE_CANCELLING_DUAL_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT)
        // 0x17: changed, on, ambient (1), voice off, level 12.
        s.receive(message(0x67, 0x17, 0x01, 0x01, 0x01, 0x00, 12))
        assertEquals(2, s.state.number(Command.ANC_MODE), "transparency")
        assertEquals(12, (setting(s, "ambient_volume") as Setting.Level).value)
        assertEquals(listOf(out(0x68, 0x17, 0x01, 0x01, 0x00, 0x00, 12)), s.set(Command.ANC_MODE, 1))
        assertEquals(1, s.state.number(Command.ANC_MODE))
        assertEquals(listOf(out(0x68, 0x17, 0x01, 0x00, 0x00, 0x00, 12)), s.set(Command.ANC_MODE, 0))
        assertEquals(listOf(out(0x68, 0x17, 0x01, 0x00, 0x00, 0x01, 12)), s.change(SettingChange.Toggle("voice_focus", true)))
        assertEquals(listOf(out(0x68, 0x17, 0x01, 0x00, 0x00, 0x01, 20)), s.change(SettingChange.Level("ambient_volume", 25)))
        // A notification replaces what showed.
        s.receive(message(0x69, 0x17, 0x01, 0x01, 0x00, 0x00, 5))
        assertEquals(1, s.state.number(Command.ANC_MODE))
    }

    @Test
    fun set2EightByteLayoutsCarryTheNoiseCancellingValue() {
        // 0x16 (dual or single): on, ambient, NC value off, voice on, level 10.
        val s = set2(SonyFunction.Set2.MODE_NC_ASM_NOISE_CANCELLING_DUAL_SINGLE_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT)
        s.receive(message(0x67, 0x16, 0x01, 0x01, 0x01, 0x00, 0x01, 10))
        // To noise cancelling: mode 0 and, as Gadgetbridge writes, dual (2).
        assertEquals(listOf(out(0x68, 0x16, 0x01, 0x01, 0x00, 0x02, 0x01, 10)), s.set(Command.ANC_MODE, 1))
        assertEquals(true, (setting(s, "wind_reduction") as Setting.Toggle).enabled)
        assertEquals(listOf(out(0x68, 0x16, 0x01, 0x01, 0x00, 0x01, 0x01, 10)), s.change(SettingChange.Toggle("wind_reduction", true)))
        // 0x17 with 8 bytes, as Gadgetbridge reads the WF-1000XM4, is read the same way.
        val x = set2(SonyFunction.Set2.MODE_NC_ASM_NOISE_CANCELLING_DUAL_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT)
        x.receive(message(0x67, 0x17, 0x01, 0x01, 0x00, 0x02, 0x00, 3))
        assertEquals(1, x.state.number(Command.ANC_MODE))
        assertNull(setting(x, "wind_reduction"))
    }

    @Test
    fun ambientOnlyIsASwitch() {
        val s = set2(SonyFunction.Set2.AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT)
        s.receive(message(0x67, 0x22, 0x01, 0x00, 0x00, 8))
        assertNull(s.state.number(Command.ANC_MODE), "no noise cancelling to choose")
        assertEquals(false, (setting(s, "ambient_sound") as Setting.Toggle).on)
        assertEquals(listOf(out(0x68, 0x22, 0x01, 0x01, 0x00, 8)), s.change(SettingChange.Toggle("ambient_sound", true)))
        assertNull(s.set(Command.ANC_MODE, 1))
    }

    @Test
    fun set1NoiseControlAsGadgetbridgeWritesIt() {
        val s = set1(SonyFunction.Set1.NOISE_CANCELLING_AND_AMBIENT_SOUND_MODE)
        // Type 2: on, dual/single/off setting, dual (NC), level adjustment, voice off, level 14.
        s.receive(message(0x67, 0x02, 0x01, 0x02, 0x02, 0x01, 0x00, 14))
        assertEquals(1, s.state.number(Command.ANC_MODE))
        assertEquals(false, (setting(s, "wind_reduction") as Setting.Toggle).on)
        // Ambient: 0x11 for on, NC value 0.
        assertEquals(listOf(out(0x68, 0x02, 0x11, 0x02, 0x00, 0x01, 0x00, 14)), s.set(Command.ANC_MODE, 2))
        assertEquals(2, s.state.number(Command.ANC_MODE))
        assertEquals(false, (setting(s, "wind_reduction") as Setting.Toggle).enabled, "wind is a noise cancelling setting")
        // Off: the effect byte 0, the rest kept.
        assertEquals(listOf(out(0x68, 0x02, 0x00, 0x02, 0x00, 0x01, 0x00, 14)), s.set(Command.ANC_MODE, 0))
        // On/off setting type: NC is 1.
        val o = set1(SonyFunction.Set1.NOISE_CANCELLING_AND_AMBIENT_SOUND_MODE)
        o.receive(message(0x67, 0x02, 0x00, 0x00, 0x00, 0x01, 0x01, 7))
        assertEquals(listOf(out(0x68, 0x02, 0x11, 0x00, 0x01, 0x01, 0x01, 7)), o.set(Command.ANC_MODE, 1))
        assertNull(setting(o, "wind_reduction"))
    }

    @Test
    fun theEqualizerFromWhatTheEarbudsSay() {
        val s = set2(SonyFunction.Set2.PRESET_EQ)
        s.takeOutbox()
        // Capability: 6 bands, 21 steps, Off (no name) and Bright (named), then Custom.
        s.receive(message(0x51, 0x00, 6, 21, 3, 0x00, 0, 0x10, 2, 'B'.code, 'r'.code, 0xA0, 0))
        // Band information: Clear Bass, then 400 Hz, 1 kHz, 2500 Hz, 6300 Hz, 16 kHz.
        s.receive(message(0x5B, 0x00, 6, 0x10, 0, 1, 0x01, 0x01, 0x90, 0x02, 0, 1, 0x01, 0x09, 0xC4, 0x01, 0x18, 0x9C, 0x02, 0, 16))
        s.receive(message(0x57, 0x00, 0x10, 6, 12, 10, 11, 9, 10, 13))
        val eq = s.eq!!
        assertEquals(listOf(0x00 to "Off", 0x10 to "Br", 0xA0 to "Custom"), eq.presets)
        assertEquals(listOf(0x00 to "Off", 0x10 to "Br"), eq.offered)
        assertEquals(SonyEq.STANDARD, eq.bands)
        assertEquals(listOf(400, 1000, 2500, 6300, 16000), eq.frequencies)
        assertEquals(2, eq.clearBass)
        assertEquals(listOf(0, 1, -1, 0, 3), eq.gains)
        assertFalse(eq.holdsCustom)
        assertEquals(2, (setting(s, "clear_bass") as Setting.Level).value)
        // A preset: its id, no values. The user's curve: Custom (A0) with every value, Clear Bass first.
        assertEquals(listOf(out(0x58, 0x00, 0x10, 0)), s.eqPreset(0x10))
        assertEquals(listOf(out(0x58, 0x00, 0xA0, 6, 12, 15, 5, 10, 10, 10)), s.eqCurve(listOf(5, -5, 0, 0, 0)))
        assertEquals(listOf(out(0x58, 0x00, 0xA0, 6, 7, 15, 5, 10, 10, 10)), s.change(SettingChange.Level("clear_bass", -3)))
        assertTrue(s.eq!!.holdsCustom)
        assertNull(s.eqPreset(0x77), "not one of theirs")
    }

    @Test
    fun set1NamesNoPresetWithItsCurveAndWorksWithoutBandInformation() {
        val s = set1(SonyFunction.Set1.PRESET_EQ)
        s.receive(message(0x57, 0x01, 0xA0, 6, 10, 10, 10, 10, 10, 10))
        val eq = s.eq!!
        assertEquals(SonyEq.STANDARD, eq.bands, "six values: Clear Bass and five bands, as Sony's app draws them")
        assertEquals(listOf(out(0x58, 0x01, 0xFF, 6, 10, 20, 0, 10, 10, 10)), s.eqCurve(listOf(10, -10, 0, 0, 0)))
        assertNull(s.eqPreset(0x16), "the earbuds have not listed their presets")
        assertEquals(out(0x58, 0x01, 0x16, 0), eq.selectPreset(0x16))
    }

    @Test
    fun switchesBySet() {
        val f2 = SonyFunction.Set2
        val s = set2(f2.UPSCALING_AUTO_OFF, f2.PLAYBACK_CONTROL_BY_WEARING_REMOVING_HEADPHONE_ON_OFF, f2.SMART_TALKING_MODE_TYPE2)
        // Set 2: DSEE auto is 1; the others' on is 0.
        s.receive(message(0xE7, 0x01, 0x01))
        s.receive(message(0xF7, 0x01, 0x01))
        s.receive(message(0xF7, 0x0C, 0x00, 0x01))
        assertEquals(true, (setting(s, "dsee") as Setting.Toggle).on)
        assertEquals(false, (setting(s, "pause_taken_off") as Setting.Toggle).on)
        assertEquals(true, (setting(s, "speak_to_chat") as Setting.Toggle).on)
        assertEquals(listOf(out(0xE8, 0x01, 0x00)), s.change(SettingChange.Toggle("dsee", false)))
        assertEquals(listOf(out(0xF8, 0x01, 0x00)), s.change(SettingChange.Toggle("pause_taken_off", true)))
        assertEquals(listOf(out(0xF8, 0x0C, 0x01, 0x01)), s.change(SettingChange.Toggle("speak_to_chat", false)))
        assertEquals(false, (setting(s, "speak_to_chat") as Setting.Toggle).on)

        val f1 = SonyFunction.Set1
        val o = set1(f1.UPSCALING, f1.CONTROL_BY_WEARING, f1.SMART_TALKING_MODE, f1.VOICE_GUIDANCE)
        // Set 1: 1 is on, after a setting type.
        o.receive(message(0xE7, 0x02, 0x00, 0x00))
        o.receive(message(0xF7, 0x03, 0x00, 0x01))
        o.receive(message(0xF7, 0x05, 0x01, 0x00))
        o.receive(message(0x47, 0x01, 0x01, 0x01, table = 2))
        assertEquals(false, (setting(o, "dsee") as Setting.Toggle).on)
        assertEquals(true, (setting(o, "pause_taken_off") as Setting.Toggle).on)
        assertEquals(true, (setting(o, "voice_guidance") as Setting.Toggle).on)
        assertEquals(listOf(out(0xE8, 0x02, 0x00, 0x01)), o.change(SettingChange.Toggle("dsee", true)))
        assertEquals(listOf(out(0xF8, 0x03, 0x00, 0x00)), o.change(SettingChange.Toggle("pause_taken_off", false)))
        assertEquals(listOf(out(0xF8, 0x05, 0x01, 0x01)), o.change(SettingChange.Toggle("speak_to_chat", true)))
        assertEquals(listOf(out(0x48, 0x01, 0x01, 0x00, table = 2)), o.change(SettingChange.Toggle("voice_guidance", false)))
    }

    @Test
    fun automaticPowerOffBySet() {
        // Set 1: SYSTEM type 4. The earbuds list their choices, then hold the active one and a time (Gadgetbridge
        // writes off as 11 00 and 30 minutes as 01 01).
        val o = set1(SonyFunction.Set1.AUTO_POWER_OFF)
        assertEquals(listOf(out(0xF0, 0x04), out(0xF6, 0x04)), o.takeOutbox())
        assertNull(setting(o, "auto_off"), "nothing until the earbuds answer")
        o.receive(message(0xF1, 0x04, 0x04, 0x00, 0x01, 0x10, 0x11))
        o.receive(message(0xF7, 0x04, 0x01, 0x01, 0x01))
        assertEquals(Setting.Choice("auto_off", "30", listOf("5", "30", "when_taken_off", "never")), setting(o, "auto_off"))
        assertEquals(listOf(out(0xF8, 0x04, 0x01, 0x11, 0x01)), o.change(SettingChange.Choice("auto_off", "never")), "the time stays")
        assertEquals(listOf(out(0xF8, 0x04, 0x01, 0x00, 0x00)), o.change(SettingChange.Choice("auto_off", "5")))
        assertNull(o.change(SettingChange.Choice("auto_off", "60")), "not one of the earbuds' choices")

        // Set 2: POWER type 5, with when taken off; the two ids alone.
        val s = set2(SonyFunction.Set2.AUTO_POWER_OFF, SonyFunction.Set2.AUTO_POWER_OFF_WITH_WEARING_DETECTION)
        assertEquals(listOf(out(0x20, 0x05), out(0x26, 0x05)), s.takeOutbox())
        s.receive(message(0x21, 0x05, 0x04, 0x00, 0x04, 0x10, 0x11))
        s.receive(message(0x27, 0x05, 0x10, 0x00))
        assertEquals(Setting.Choice("auto_off", "when_taken_off", listOf("5", "15", "when_taken_off", "never")), setting(s, "auto_off"))
        assertEquals(listOf(out(0x28, 0x05, 0x04, 0x04)), s.change(SettingChange.Choice("auto_off", "15")))
        s.receive(message(0x29, 0x05, 0x11, 0x04))
        assertEquals("never", (setting(s, "auto_off") as Setting.Choice).value, "a notification updates it")
    }

    @Test
    fun touchKeysBySet() {
        // Set 1: SYSTEM type 6. Each key: id, sensor or button, default, then its presets with their actions.
        val o = set1(SonyFunction.Set1.ASSIGNABLE_SETTINGS)
        assertEquals(listOf(out(0xF0, 0x06), out(0xF6, 0x06)), o.takeOutbox())
        o.receive(
            message(
                0xF1, 0x06, 0x02,
                0x00, 0x00, 0x00, 0x03, 0x00, 0x01, 0x00, 0x01, 0x20, 0x01, 0x00, 0x20, 0x10, 0x01, 0x00, 0x00,
                0x01, 0x00, 0x20, 0x03, 0x00, 0x01, 0x00, 0x01, 0x20, 0x01, 0x00, 0x20, 0x31, 0x01, 0x00, 0x32,
            ),
        )
        o.receive(message(0xF7, 0x06, 0x02, 0x00, 0x31))
        assertEquals(Setting.Choice("assign_left", "noise_control", listOf("noise_control", "playback", "volume")), setting(o, "assign_left"))
        assertEquals(
            Setting.Choice("assign_right", "google_assistant", listOf("noise_control", "playback", "google_assistant")),
            setting(o, "assign_right"),
            "an assistant is shown but not offered",
        )
        assertEquals(SettingTab.CONTROLS, o.groups.single { g -> g.settings.any { it.key == "assign_left" } }.tab)
        // As Gadgetbridge writes it: the type, the count, then every key's preset.
        assertEquals(listOf(out(0xF8, 0x06, 0x02, 0x00, 0x20)), o.change(SettingChange.Choice("assign_right", "playback")))
        assertEquals(listOf(out(0xF8, 0x06, 0x02, 0x10, 0x20)), o.change(SettingChange.Choice("assign_left", "volume")))

        // Set 2: type 3, whose presets carry a list of actions and a list of actions with functions to switch between.
        val s = set2(SonyFunction.Set2.ASSIGNABLE_SETTING)
        assertEquals(listOf(out(0xF0, 0x03), out(0xF6, 0x03)), s.takeOutbox())
        s.receive(message(0xF1, 0x03, 0x01, 0x01, 0x00, 0x20, 0x02, 0x20, 0x01, 0x00, 0x00, 0x20, 0x21, 0x00, 0x01, 0x00, 0x21, 0x02, 0x21, 0x22))
        s.receive(message(0xF7, 0x03, 0x01, 0x21))
        assertEquals(Setting.Choice("assign_right", "track", listOf("playback", "track")), setting(s, "assign_right"))
        assertEquals(listOf(out(0xF8, 0x03, 0x01, 0x20)), s.change(SettingChange.Choice("assign_right", "playback")))

        // Type 14 (with limitations): one more byte before the keys.
        val l = set2(SonyFunction.Set2.ASSIGNABLE_SETTING_WITH_LIMITATION)
        assertEquals(listOf(out(0xF0, 0x0E), out(0xF6, 0x0E)), l.takeOutbox())
        l.receive(message(0xF1, 0x0E, 0x00, 0x01, 0x02, 0x01, 0x00, 0x02, 0x00, 0x01, 0x00, 0x00, 0x01, 0xFF, 0x01, 0x00, 0x00, 0x00))
        l.receive(message(0xF7, 0x0E, 0x01, 0x00))
        assertEquals(Setting.Choice("assign_custom", "noise_control", listOf("noise_control", "none")), setting(l, "assign_custom"))
        assertEquals(listOf(out(0xF8, 0x0E, 0x01, 0xFF)), l.change(SettingChange.Choice("assign_custom", "none")))
    }

    @Test
    fun set2VoiceGuidanceByType() {
        // Set 2 lists its second table's functions on their own; voice guidance is one of them.
        val s = set2()
        s.receive(message(0x07, 0x00, 0x01, 0x45, 0x00, table = 2))
        assertEquals(setOf(0x45), s.functions2)
        assertEquals(listOf(out(0x46, 0x03, table = 2)), s.takeOutbox())
        s.receive(message(0x47, 0x03, 0x00, table = 2))
        assertEquals(true, (setting(s, "voice_guidance") as Setting.Toggle).on, "0 is on")
        assertEquals(listOf(out(0x48, 0x03, 0x01, table = 2)), s.change(SettingChange.Toggle("voice_guidance", false)))

        // The language-switch type sends the language back with the switch.
        val l = set2()
        l.receive(message(0x07, 0x00, 0x01, 0x44, 0x00, table = 2))
        assertEquals(listOf(out(0x46, 0x02, table = 2)), l.takeOutbox())
        l.receive(message(0x47, 0x02, 0x00, 0x05, table = 2))
        assertEquals(listOf(out(0x48, 0x02, 0x01, 0x05, table = 2)), l.change(SettingChange.Toggle("voice_guidance", false)))

        // The MTK types: on and off alone, though the reply of type 1 also holds the language.
        val m = set2()
        m.receive(message(0x07, 0x00, 0x02, 0x42, 0x00, 0x43, 0x00, table = 2))
        assertEquals(listOf(out(0x46, 0x01, table = 2)), m.takeOutbox())
        m.receive(message(0x47, 0x01, 0x01, 0x02, table = 2))
        assertEquals(false, (setting(m, "voice_guidance") as Setting.Toggle).on)
        assertEquals(listOf(out(0x48, 0x01, 0x00, table = 2)), m.change(SettingChange.Toggle("voice_guidance", true)))
    }

    @Test
    fun connectionModeBySet() {
        // Set 1: AUDIO type 1, its setting type, then sound quality (0) or a stable connection (1).
        val o = set1(SonyFunction.Set1.CONNECTION_MODE)
        assertEquals(listOf(out(0xE6, 0x01)), o.takeOutbox())
        o.receive(message(0xE7, 0x01, 0x00, 0x01))
        assertEquals(Setting.Choice("connection_priority", "stable", listOf("sound_quality", "stable")), setting(o, "connection_priority"))
        assertEquals(listOf(out(0xE8, 0x01, 0x00, 0x00)), o.change(SettingChange.Choice("connection_priority", "sound_quality")))

        // Set 2: type 2 where the earbuds also report LDAC, else 0; low latency only shows when they report it.
        val s = set2(SonyFunction.Set2.CONNECTION_MODE_SOUND_QUALITY_CONNECTION_QUALITY, SonyFunction.Set2.CONNECTION_MODE_SOUND_QUALITY_SOUND_WITH_LDAC_STATUS_QUALITY_CONNECTION_QUALITY)
        assertEquals(listOf(out(0xE6, 0x02)), s.takeOutbox())
        s.receive(message(0xE7, 0x02, 0x02))
        assertEquals(listOf("sound_quality", "stable", "low_latency"), (setting(s, "connection_priority") as Setting.Choice).options)
        assertEquals(listOf(out(0xE8, 0x02, 0x01)), s.change(SettingChange.Choice("connection_priority", "stable")))
        assertEquals("stable", (setting(s, "connection_priority") as Setting.Choice).value)
    }

    @Test
    fun theReportKeepsTheLastOfEachKind() {
        val s = set2(SonyFunction.Set2.LEFT_RIGHT_BATTERY_LEVEL_INDICATOR)
        s.receive(message(0x23, 0x01, 70, 0, 60, 0))
        s.receive(message(0x25, 0x01, 69, 0, 60, 0))
        assertArrayEquals(byteArrayOf(0x23, 0x01, 70, 0, 60, 0), s.replies["1 23 01"])
        assertArrayEquals(byteArrayOf(0x25, 0x01, 69, 0, 60, 0), s.replies["1 25 01"])
    }
}
