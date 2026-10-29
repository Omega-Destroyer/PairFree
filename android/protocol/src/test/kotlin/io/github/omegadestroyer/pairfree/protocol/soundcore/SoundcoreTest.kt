package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.BatteryLevel
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.hexBytes
import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun load(name: String): JsonObject = Json.parseToJsonElement(repoFile("data/soundcore/$name").readText()).jsonObject

private val vectors = load("test_vectors.json")

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

private fun ints(o: JsonObject, key: String) = o.getValue(key).jsonArray.map { it.jsonPrimitive.int }.toIntArray()

/** The generated constants must say exactly what data/soundcore/commands.json says. */
class SoundcoreTablesTest {
    @Test
    fun commandNumbers() {
        val commands = load("commands.json").getValue("commands").jsonArray.map { it.jsonObject }
        assertTrue(commands.size >= 19)
        for (c in commands) {
            val field = SoundcoreCommand::class.java.getDeclaredField(c.str("id").uppercase())
            assertEquals(c.str("command").toInt(16), field.getInt(null), c.str("id"))
        }
        val never = commands.filter { it["never_send"]?.jsonPrimitive?.boolean == true }.map { it.str("command").toInt(16) }.toSet()
        assertEquals(never, SoundcoreCommand.NEVER_SEND)
        assertEquals(setOf(0x0581), SoundcoreCommand.NEVER_SEND)
    }
}

/** Every frame recorded from the Space A40, byte for byte (data/soundcore/test_vectors.json). */
class SoundcorePacketTest {
    private val frames = vectors.getValue("frames").jsonArray.map { it.jsonObject }

    @Test
    fun buildsEveryRecordedFrame() {
        assertEquals(57, frames.size)
        for (f in frames) {
            val command = f.str("command").toInt(16)
            val body = f.str("body").hexBytes()
            val built = if (f.str("direction") == "to_earbuds") SoundcorePacket.build(command, body) else SoundcorePacket.buildFromEarbuds(command, body)
            assertEquals(f.str("frame"), built.hex(), f.str("command"))
        }
    }

    @Test
    fun requestStateMatchesOpenScq30() {
        assertEquals("08ee00000001010a0002", SoundcorePacket.build(SoundcoreCommand.STATE).hex())
    }

    @Test
    fun parsesAStreamInPieces() {
        val incoming = frames.filter { it.str("direction") == "from_earbuds" }
        val stream = incoming.flatMap { it.str("frame").hexBytes().toList() }.toByteArray()
        val parser = SoundcoreParser()
        val got = stream.toList().chunked(7).flatMap { parser.feed(it.toByteArray()) }
        assertEquals(incoming.map { SoundcorePacket(it.str("command").toInt(16), it.str("body").hexBytes()) }, got)
        assertEquals(0, parser.rejected)
    }

    @Test
    fun dropsAPacketWithABadChecksum() {
        val good = SoundcorePacket.buildFromEarbuds(SoundcoreCommand.SURROUND_SOUND, byteArrayOf(1))
        val bad = good.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        val parser = SoundcoreParser()
        assertEquals(listOf(SoundcorePacket(SoundcoreCommand.SURROUND_SOUND, byteArrayOf(1))), parser.feed(byteArrayOf(0x55) + bad + good))
        assertEquals(1, parser.rejected)
    }
}

class A3936StateTest {
    private val case = vectors.getValue("a3936_state").jsonObject
    private val state = A3936State.parse(case.str("body").hexBytes())!!
    private val expect = case.getValue("expect").jsonObject

    @Test
    fun decodesTheRecordedState() {
        fun b(key: String) = expect.getValue(key).jsonPrimitive.boolean
        fun i(key: String) = expect.getValue(key).jsonPrimitive.int
        assertEquals(i("host_device"), state.hostDevice)
        assertEquals(b("both_connected"), state.bothConnected)
        assertEquals(i("battery_left"), state.batteryLeft)
        assertEquals(i("battery_right"), state.batteryRight)
        assertEquals(b("charging_left"), state.chargingLeft)
        assertEquals(b("charging_right"), state.chargingRight)
        assertEquals(expect.str("firmware_left"), state.firmwareLeft)
        assertEquals(expect.str("firmware_right"), state.firmwareRight)
        assertTrue(state.serialNumber.startsWith(expect.str("serial_prefix")))
        assertEquals(i("eq_preset"), state.eqPreset)
        assertEquals(i("age_range"), state.ageRange)
        assertEquals(i("sound_mode_cycle"), state.soundModeCycle)
        assertEquals(expect.str("sound_modes"), state.soundModes.hex())
        assertEquals(AmbientMode.of(i("ambient_mode")), state.ambientMode)
        assertEquals(b("touch_tone"), state.touchTone)
        assertEquals(i("case_battery"), state.caseBattery)
        assertEquals(i("color"), state.color)
        assertEquals(b("ldac"), state.ldac)
        assertEquals(b("multipoint"), state.multipoint)
        assertEquals(b("auto_power_off_enabled"), state.autoPowerOff)
        assertEquals(i("auto_power_off_index"), state.autoPowerOffIndex)
        assertEquals(b("gaming_mode"), state.gamingMode)
        assertEquals(expect.str("trailing"), state.trailing.hex())
    }

    @Test
    fun soundModeWritesMatchTheRecording() {
        // The Soundcore app turned noise cancelling on, then back to normal.
        val sent = vectors.getValue("frames").jsonArray.map { it.jsonObject }
            .filter { it.str("direction") == "to_earbuds" && it.str("command") == "0681" }.map { it.str("body") }
        assertEquals(listOf(state.soundModesWith(AmbientMode.NOISE_CANCELLING).hex(), state.soundModesWith(AmbientMode.NORMAL).hex()), sent)
    }

    @Test
    fun tooShortIsNotAState() {
        assertNull(A3936State.parse(ByteArray(A3936State.KNOWN_SIZE - 1)))
    }
}

class SoundcoreEqTest {
    @Test
    fun smoothingMatchesEveryRecordedCurve() {
        val cases = vectors.getValue("eq_smoothing").jsonArray.map { it.jsonObject }
        assertTrue(cases.size >= 4)
        for (c in cases) {
            assertEquals(c.str("smoothed"), SoundcoreEq.bytes(SoundcoreEq.smooth(ints(c, "bands_tenths"))).hex(), c.toString())
        }
    }

    @Test
    fun rebuildsEveryRecordedEqWrite() {
        val state = A3936State.parse(vectors.getValue("a3936_state").jsonObject.str("body").hexBytes())!!
        val writes = vectors.getValue("eq_writes").jsonArray.map { it.jsonObject }
        assertEquals(7, writes.size)
        for (w in writes) {
            val body = state.equalizerWrite(w.getValue("preset").jsonPrimitive.int, ints(w, "left_tenths"), ints(w, "right_tenths"))
            assertEquals(w.str("body"), body.hex())
        }
    }

    @Test
    fun byteEncoding() {
        assertEquals(0x78.toByte(), SoundcoreEq.toByte(0))
        assertEquals(0xB4.toByte(), SoundcoreEq.toByte(60))
        assertEquals(0x3C.toByte(), SoundcoreEq.toByte(-60))
        assertArrayEquals(intArrayOf(0, 60, -60), byteArrayOf(0x78, 0xB4.toByte(), 0x3C).map { SoundcoreEq.toTenths(it) }.toIntArray())
    }
}

/** The A40 on PairFree's screens, replayed from the recording. */
class SoundcoreSessionTest {
    private val frames = vectors.getValue("frames").jsonArray.map { it.jsonObject }

    private fun packet(direction: String, command: String, nth: Int = 0): SoundcorePacket =
        frames.filter { it.str("direction") == direction && it.str("command") == command }[nth]
            .let { SoundcorePacket(it.str("command").toInt(16), it.str("body").hexBytes()) }

    private fun connected(): SoundcoreSession =
        SoundcoreSession(SoundcoreSession.A3936).also { assertTrue(it.receive(packet("from_earbuds", "0101"))) }

    private fun ancNumber(name: String) = Command.ANC_MODE.labels.entries.first { it.value == name }.key

    @Test
    fun stateBecomesWhatTheScreensRead() {
        val s = connected().state
        assertEquals(100, s.batteryLeft)
        assertEquals(100, s.batteryRight)
        assertEquals(15, s.batteryCase)
        assertEquals(ancNumber("off"), s.number(Command.ANC_MODE))
        assertEquals(true, s.switch(Command.LDAC))
        assertEquals(false, s.switch(Command.MULTIPOINT))
        assertEquals(false, s.switch(Command.GAME_MODE))
        assertEquals("A3936", s.firmware?.code)
        assertEquals("06.43", s.firmware?.version)
    }

    @Test
    fun noiseModesSendWhatTheSoundcoreAppSent() {
        val session = connected()
        val anc = Command.ANC_MODE
        val sent = frames.filter { it.str("direction") == "to_earbuds" && it.str("command") == "0681" }.map { it.str("frame") }
        assertEquals(sent[0], session.set(anc, ancNumber("anc"))!!.hex())
        assertEquals(ancNumber("anc"), session.state.number(anc))
        session.receive(packet("from_earbuds", "0601", 0))
        assertEquals(sent[1], session.set(anc, ancNumber("off"))!!.hex())
        session.receive(packet("from_earbuds", "0601", 1))
        assertEquals(ancNumber("off"), session.state.number(anc))
    }

    @Test
    fun pushesUpdateTheScreens() {
        val session = connected()
        assertTrue(!session.receive(packet("from_earbuds", "0103")), "the same battery changes nothing")
        assertTrue(!session.receive(SoundcorePacket(SoundcoreCommand.SET_SOUND_MODES, ByteArray(0))), "an acknowledgement changes nothing")
        assertTrue(session.receive(SoundcorePacket(SoundcoreCommand.BATTERY, byteArrayOf(4, 3, 50))))
        assertEquals(80, session.state.batteryLeft)
        assertEquals(60, session.state.batteryRight)
        assertEquals(50, session.state.batteryCase)
        // The earbuds report fifths, shown as bars as in the Soundcore app; the A40's case reports percent.
        assertEquals(BatteryLevel(4, 5), session.state.battery(Command.BATTERY_LEFT))
        assertEquals(BatteryLevel(3, 5), session.state.battery(Command.BATTERY_RIGHT))
        assertEquals(BatteryLevel(50), session.state.battery(Command.BATTERY_CASE))
        assertTrue(session.state.battery(Command.BATTERY_CASE)!!.isPercent)
        assertTrue(session.receive(SoundcorePacket(SoundcoreCommand.BATTERY, byteArrayOf(-1, 3, 50))))
        assertEquals(BatteryLevel(null, 5), session.state.battery(Command.BATTERY_LEFT), "the left earbud is away")
    }

    @Test
    fun theStateGivesBatteriesInTheModelsSteps() {
        val session = connected()
        assertEquals(BatteryLevel(5, 5), session.state.battery(Command.BATTERY_LEFT), "05 in the recorded state")
        assertEquals(BatteryLevel(5, 5), session.state.battery(Command.BATTERY_RIGHT))
        assertEquals(BatteryLevel(15), session.state.battery(Command.BATTERY_CASE), "the case reads 15%")
    }

    @Test
    fun switchesAreOneByteFlags() {
        val session = connected()
        assertEquals(SoundcorePacket.build(SoundcoreCommand.GAMING_MODE, byteArrayOf(1)).hex(), session.set(Command.GAME_MODE, 1)!!.hex())
        assertEquals(SoundcorePacket.build(SoundcoreCommand.LDAC, byteArrayOf(0)).hex(), session.set(Command.LDAC, 0)!!.hex())
        assertEquals(SoundcorePacket.build(SoundcoreCommand.DUAL_CONNECTIONS_ENABLED, byteArrayOf(1)).hex(), session.set(Command.MULTIPOINT, 1)!!.hex())
        assertNull(session.set(Command.PROMPT_VOLUME, 50), "settings the A40 does not share are refused")
    }

    @Test
    fun neverBuildsANeverSendCommand() {
        val session = connected()
        for (command in Command.entries) {
            for (value in 0..2) {
                val frame = session.set(command, value) ?: continue
                val number = ((frame[5].toInt() and 0xFF) shl 8) or (frame[6].toInt() and 0xFF)
                assertTrue(number !in SoundcoreCommand.NEVER_SEND, command.name)
            }
        }
    }
}

/**
 * Every model's state layout (data/soundcore/devices.json), read from OpenSCQ30's parsers
 * (github.com/Oppzippy/OpenSCQ30, GPL-3.0-or-later) and checked against the replies in its tests.
 */
class SoundcoreLayoutTest {
    private val devices = load("devices.json").getValue("devices").jsonArray.map { it.jsonObject }
    private val replies = vectors.getValue("openscq30_states").jsonArray.map { it.jsonObject }
    private val version = Regex("""\d\d\.\d\d""")

    private fun reply(model: String, nth: Int = 0) = replies.filter { it.str("model") == model }[nth].str("body").hexBytes()

    @Test
    fun layoutsMatchDevicesJson() {
        assertEquals(devices.map { it.str("model") }, SoundcoreLayout.ALL.map { it.model })
        for (d in devices) {
            val layout = SoundcoreLayout.of(d.str("model"))!!
            val fields = d.getValue("state").jsonArray.map { f ->
                val name = f.jsonArray[0].jsonPrimitive.content
                SoundcoreField(name.removePrefix("optional:"), f.jsonArray[1].jsonPrimitive.int, name.startsWith("optional:"))
            }
            assertEquals(fields, layout.fields, layout.model)
            fun scale(key: String) = d["battery"]?.jsonObject?.get(key)?.jsonObject
                ?.let { BatteryScale(it.getValue("max").jsonPrimitive.int, it.getValue("offset").jsonPrimitive.int) }
            assertEquals(scale("earbuds"), layout.battery, layout.model)
            assertEquals(scale("case"), layout.case, layout.model)
            assertEquals(d["checksum"]?.jsonPrimitive?.boolean ?: true, layout.checksum, layout.model)
            assertEquals(d["service_uuid"]?.jsonPrimitive?.content, layout.serviceUuid, layout.model)
            val gaming = d["commands"]?.jsonObject?.get("gaming_mode")?.jsonPrimitive?.content?.toInt(16)
            assertEquals(gaming ?: SoundcoreCommand.GAMING_MODE, layout.gamingModeCommand, layout.model)
            val minFirmware = d["min_firmware"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty()
            assertEquals(minFirmware, layout.minFirmware, layout.model)
            val app = d["soundcore_app"]?.jsonObject
            val lacks = app?.get("lacks")?.jsonArray.orEmpty().map { it.jsonPrimitive.content }.toSet()
            val features = d.getValue("openscq30").jsonObject.getValue("features").jsonArray + app?.get("features")?.jsonArray.orEmpty() +
                d["extra_features"]?.jsonArray.orEmpty()
            assertEquals(features.map { it.jsonPrimitive.content }.toSet() - lacks, layout.features, layout.model)
            assertEquals(d["vendor_ids"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }.toSet(), layout.vendorIds, layout.model)
        }
        assertEquals(BatteryScale(4), SoundcoreLayout.of("A3926")!!.battery, "the Soundcore app reads the A3926's levels from 0 to 4")
        assertTrue("sound_modes" !in SoundcoreLayout.of("A3926")!!.features, "the Soundcore app shows the A3926 no noise control")
        assertEquals(0x1085, SoundcoreLayout.of("A3947")!!.gamingModeCommand, "OpenSCQ30 a3947/modules/flag.rs")
        assertEquals(false, SoundcoreLayout.of("A3116")!!.checksum, "OpenSCQ30 a3116.rs: ChecksumKind::None")
        assertEquals(131, SoundcoreLayout.of("A3936")!!.minSize, "the A40's reply, as recorded")
    }

    @Test
    fun everyModelsEqualizerWrites() {
        // The Sound tab offers every model's EQ, confirmed or not: each preset and a custom curve must make a write.
        val models = SoundcoreLayout.ALL.map { it.model }.filter { SoundcoreEqSpec.of(it) != null }
        assertTrue(models.size >= 38, "models with an EQ: ${models.size}")
        for (model in models) {
            val spec = SoundcoreEqSpec.of(model)!!
            for (preset in spec.presetSet.presets) {
                val packets = SoundcoreEqualizer.write(spec, null, SoundcoreEqualizer.forPreset(spec, preset))
                assertTrue(packets.isNotEmpty() && packets.all { it.body.isNotEmpty() }, "$model ${preset.name}")
            }
            // Some models take their presets only (the P20i's set has no custom preset).
            if (spec.presetSet.customPreset == null) continue
            val custom = SoundcoreEqScreen.custom(spec, List(SoundcoreEqScreen.layout(spec).bands.size) { 0 })
            assertNotNull(custom, "$model custom")
            assertTrue(SoundcoreEqualizer.write(spec, null, custom!!).isNotEmpty(), "$model custom write")
        }
    }

    @Test
    fun readsUsersReports() {
        val reports = vectors.getValue("report_states").jsonArray.map { it.jsonObject }
        assertTrue(reports.isNotEmpty())
        for (c in reports) {
            val where = c.str("report")
            val s = SoundcoreState.parse(SoundcoreLayout.of(c.str("model"))!!, c.str("body").hexBytes())!!
            assertEquals(c.getValue("battery_left").jsonPrimitive.int, s.batteryLeft, where)
            assertEquals(c.getValue("battery_right").jsonPrimitive.int, s.batteryRight, where)
            assertEquals(c.getValue("case_battery").jsonPrimitive.int, s.batteryCase, where)
            assertEquals(c.str("firmware"), s.firmware, where)
            for ((flag, on) in c.getValue("flags").jsonObject) assertEquals(on.jsonPrimitive.boolean, s.flag(flag), "$where: $flag")
        }
    }

    @Test
    fun readsEveryReplyInOpenScq30sTests() {
        assertEquals(37, replies.size)
        assertEquals(21, replies.map { it.str("model") }.toSet().size)
        for (c in replies) {
            val where = c.str("openscq30_test")
            val layout = SoundcoreLayout.of(c.str("model"))!!
            val body = c.str("body").hexBytes()
            val s = SoundcoreState.parse(layout, body)
            assertNotNull(s, where)
            s!!
            for (level in listOf(s.batteryLeft, s.batteryRight, s.batteryCase)) assertTrue(level == null || level in 0..100, where)
            assertTrue(s.batteryLeft != null || s.batteryRight != null, "one earbud at least: $where")
            if (layout.has("firmware") || layout.has("dual_firmware")) assertTrue(s.firmware?.matches(version) == true, where)
            if (layout.has("sound_modes")) assertNotNull(s.ambientMode, where)
            assertEquals(body.size, s.reportHex().split(" ").size, where)
            if (layout.has("serial")) assertTrue("XX" in s.reportHex(), "serial masked: $where")
        }
    }

    @Test
    fun aReplyWhoseLeftEarbudIsAwayTakesTheRightFirmware() {
        // OpenSCQ30 a3955.rs: the left earbud's firmware bytes are not a version.
        val s = SoundcoreState.parse(SoundcoreLayout.of("A3955")!!, reply("A3955", 1))!!
        assertEquals("01.72", s.firmware)
        assertEquals(listOf("01.72"), s.firmwares)
    }

    @Test
    fun gameModeNeedsTheFirmwareThatHasIt() {
        // OpenSCQ30 a3959.rs: the P30i has game mode from firmware 01.60 on.
        val layout = SoundcoreLayout.of("A3959")!!
        val replies = replies.filter { it.str("model") == "A3959" }.map { SoundcoreState.parse(layout, it.str("body").hexBytes())!! }
        val old = replies.single { it.firmware == "01.59" }
        assertNull(old.flag("gaming_mode"))
        assertTrue(replies.filter { it.firmware!! >= "01.60" }.all { it.flag("gaming_mode") != null })
    }

    @Test
    fun theGenericReaderReadsTheA40AsItsOwnReaderDoes() {
        val body = vectors.getValue("a3936_state").jsonObject.str("body").hexBytes()
        val own = A3936State.parse(body)!!
        val s = SoundcoreState.parse(SoundcoreLayout.of("A3936")!!, body)!!
        assertEquals(own.firmwareLeft, s.firmware)
        assertEquals(listOf(own.firmwareLeft, own.firmwareRight), s.firmwares)
        assertEquals(own.batteryLeft * 20, s.batteryLeft)
        assertEquals(own.batteryRight * 20, s.batteryRight)
        assertEquals(own.caseBattery, s.batteryCase)
        assertArrayEquals(own.soundModes, s.soundModes)
        assertEquals(own.ambientMode, s.ambientMode)
        assertEquals(own.ldac, s.flag("ldac"))
        assertEquals(own.multipoint, s.flag("dual_connections_enabled"))
        assertEquals(own.gamingMode, s.flag("gaming_mode"))
        assertEquals(own.touchTone, s.flag("touch_tone"))
    }

    @Test
    fun batteryScales() {
        assertEquals(100, BatteryScale(5).percent(5))
        assertEquals(60, BatteryScale(5).percent(3))
        assertNull(BatteryScale(5).percent(255), "an earbud that is away")
        // An offset of 1, as OpenSCQ30 reads it: 0 is the first of ten steps and 9 the last (an AeroClip's report).
        assertEquals(10, BatteryScale(10, 1).percent(0))
        assertEquals(20, BatteryScale(10, 1).percent(1))
        assertEquals(100, BatteryScale(10, 1).percent(9))
        assertNull(BatteryScale(10, 1).percent(10), "above the scale")
        // Any number of steps: the reading keeps them, and only 100 is a percentage.
        assertEquals(BatteryLevel(3, 5), BatteryScale(5).reading(3))
        assertEquals(BatteryLevel(6, 7), BatteryScale(7).reading(6))
        assertEquals(BatteryLevel(10, 10), BatteryScale(10, 1).reading(9))
        assertEquals(BatteryLevel(1, 10), BatteryScale(10, 1).reading(0))
        assertEquals(BatteryLevel(null, 10), BatteryScale(10, 1).reading(255), "an earbud that is away")
        assertEquals(BatteryLevel(null, 5), BatteryScale(5).reading(null))
        assertTrue(!BatteryScale(5).reading(3).isPercent)
        assertTrue(BatteryScale(100).reading(42).isPercent)
        assertEquals(85, BatteryLevel(6, 7).percent)
    }
}

/** Other models on PairFree's screens, from the replies in OpenSCQ30's tests. */
class SoundcoreModelSessionTest {
    private val replies = vectors.getValue("openscq30_states").jsonArray.map { it.jsonObject }

    private fun connected(model: String, nth: Int = 0): SoundcoreSession {
        val body = replies.filter { it.str("model") == model }[nth].str("body").hexBytes()
        return SoundcoreSession(model).also { assertTrue(it.receive(SoundcorePacket(SoundcoreCommand.STATE, body)), model) }
    }

    @Test
    fun everyReplyReachesTheScreens() {
        for (c in replies) {
            val model = c.str("model")
            val session = SoundcoreSession(model)
            assertTrue(session.receive(SoundcorePacket(SoundcoreCommand.STATE, c.str("body").hexBytes())), c.str("openscq30_test"))
            assertEquals(model, session.state.firmware?.code)
        }
    }

    @Test
    fun liberty4NcHasNoLdacAndItsOwnGameModeCommand() {
        val session = connected("A3947")
        val s = session.state
        assertEquals(40, s.batteryLeft)
        assertEquals(80, s.batteryRight)
        assertEquals(40, s.batteryCase)
        assertEquals("05.88", s.firmware?.version)
        assertEquals(false, s.switch(Command.GAME_MODE))
        assertNull(s.values[Command.LDAC], "the Liberty 4 NC has no LDAC")
        assertNull(session.set(Command.LDAC, 1))
        assertEquals(SoundcorePacket.build(0x1085, byteArrayOf(1)).hex(), session.set(Command.GAME_MODE, 1)!!.hex())
    }

    @Test
    fun headphonesHaveOneBattery() {
        val s = connected("A3005").state
        // 7 in OpenSCQ30's test reply, which with its level_offset of 1 is 8 of 10.
        assertEquals(80, s.batteryLeft)
        assertNull(s.values[Command.BATTERY_RIGHT])
        assertNull(s.values[Command.BATTERY_CASE])
        assertEquals(true, s.switch(Command.MULTIPOINT))
    }

    @Test
    fun theCaseInABatteryUpdateIsReadOnlyOnTheA40() {
        val session = connected("A3947")
        assertTrue(session.receive(SoundcorePacket(SoundcoreCommand.BATTERY, byteArrayOf(5, 5, 1))))
        assertEquals(100, session.state.batteryLeft)
        assertEquals(40, session.state.batteryCase, "unchanged: OpenSCQ30 does not name the third byte")
    }

    @Test
    fun oldP30iFirmwareHasNoGameMode() {
        val layout = SoundcoreLayout.of("A3959")!!
        val nth = replies.filter { it.str("model") == "A3959" }
            .indexOfFirst { SoundcoreState.parse(layout, it.str("body").hexBytes())!!.firmware == "01.59" }
        val session = connected("A3959", nth)
        assertNull(session.state.values[Command.GAME_MODE])
        assertNull(session.set(Command.GAME_MODE, 1))
    }

    @Test
    fun theMotionPlusSendsNoChecksum() {
        // OpenSCQ30 a3116.rs: ChecksumKind::None, and the length leaves the checksum out.
        val session = SoundcoreSession("A3116")
        assertEquals("08ee00000001010900", session.requestState().hex())
        val body = replies.first { it.str("model") == "A3116" }.str("body").hexBytes()
        val parser = SoundcoreParser(checksum = false)
        val packets = parser.feed(SoundcorePacket.buildFromEarbuds(SoundcoreCommand.STATE, body, checksum = false))
        assertEquals(listOf(SoundcorePacket(SoundcoreCommand.STATE, body)), packets)
        assertTrue(session.receive(packets.single()))
        assertEquals(100, session.state.batteryLeft)
    }

    @Test
    fun nothingIsSentBeforeTheFirstState() {
        val session = SoundcoreSession("A3947")
        for (command in listOf(Command.ANC_MODE, Command.GAME_MODE, Command.LDAC, Command.MULTIPOINT)) assertNull(session.set(command, 1))
    }
}

/**
 * Every model's EQ (data/soundcore/devices.json eq, eq_presets.json), checked against OpenSCQ30's EQ
 * tests (github.com/Oppzippy/OpenSCQ30, GPL-3.0-or-later) and the Space A40's recorded writes.
 */
class SoundcoreEqualizerTest {
    private val devices = load("devices.json").getValue("devices").jsonArray.map { it.jsonObject }
    private val presetSets = load("eq_presets.json").getValue("sets").jsonObject

    private fun packets(list: List<SoundcorePacket>) = list.map { "%04x".format(it.command) to it.body.hex() }

    private fun values(e: kotlinx.serialization.json.JsonElement?): IntArray? =
        if (e == null || e is kotlinx.serialization.json.JsonNull) null else e.jsonArray.map { it.jsonPrimitive.int }.toIntArray()

    @Test
    fun specsMatchTheData() {
        val withEq = devices.filter { "eq" in it }
        assertEquals(withEq.map { it.str("model") }, SoundcoreEqSpec.ALL.map { it.model })
        assertEquals(46, withEq.size, "every model PairFree reads but the Sleep A30 and the AeroClip")
        for (d in withEq) {
            val eq = d.getValue("eq").jsonObject
            val spec = SoundcoreEqSpec.of(d.str("model"))!!
            assertEquals(eq.str("write"), spec.write, spec.model)
            assertEquals(eq.getValue("channels").jsonPrimitive.int, spec.channels, spec.model)
            assertEquals(eq.getValue("bands").jsonPrimitive.int, spec.bands, spec.model)
            assertEquals(eq.str("presets"), spec.presets, spec.model)
            val set = presetSets.getValue(spec.presets).jsonObject
            assertEquals(set.getValue("presets").jsonArray.size, spec.presetSet.presets.size, spec.model)
            assertTrue(spec.presetSet.presets.isNotEmpty(), spec.model)
            // the EQ field in the state layout has room for what the spec reads
            val layout = SoundcoreLayout.of(spec.model)!!
            if (!spec.presetOnly) assertEquals(spec.presetBytes + spec.stateChannels * spec.bands, layout.locate("equalizer")!!.second, spec.model)
            spec.hearId?.let { h -> assertTrue(layout.has(h.field), "${spec.model} ${h.field}") }
        }
        assertEquals(255, SoundcoreEqSpec.of("A3936")!!.gender, "the Soundcore app sends FF on the A40")
        assertTrue(!SoundcoreEqSpec.of("A3949")!!.hasCustom, "OpenSCQ30: the P20i has no custom EQ")
    }

    @Test
    fun rebuildsOpenScq30sEqTests() {
        val cases = vectors.getValue("openscq30_eq").jsonArray.map { it.jsonObject }
        assertEquals(14, cases.size)
        assertEquals(11, cases.map { it.str("model") }.toSet().size)
        for (c in cases) {
            val where = c.str("openscq30_test")
            val spec = SoundcoreEqSpec.of(c.str("model"))!!
            val state = SoundcoreState.parse(SoundcoreLayout.of(spec.model)!!, c.str("state").hexBytes())!!
            val target = c["preset"]?.let { SoundcoreEqualizer.forPreset(spec, spec.preset(it.jsonPrimitive.content)!!) }
                ?: SoundcoreEqualizer.forCustom(spec, values(c["bands"])!!)!!
            val expected = c.getValue("packets").jsonArray.map { it.jsonObject.str("command") to it.jsonObject.str("body") }
            assertEquals(expected, packets(SoundcoreEqualizer.write(spec, state, target)), where)
        }
    }

    @Test
    fun rebuildsOpenScq30sPacketTests() {
        val cases = vectors.getValue("openscq30_eq_formats").jsonArray.map { it.jsonObject }
        assertEquals(8, cases.size)
        for (c in cases) {
            val where = c.str("openscq30_test")
            val channels = c.getValue("channels").jsonArray.map { values(it) }
            val bands = channels.first()!!.size
            val h = c["hear_id"]?.jsonObject
            val hearChannels = h?.getValue("curve")?.jsonArray?.size ?: 0
            val format = c.str("format").let { if (it == "hear_id" && c["forced"]?.jsonPrimitive?.boolean == true) "hear_id_forced" else it }
            val spec = SoundcoreEqSpec(
                "test", format, channels.size, bands, -120, 134, 1, presets = "common",
                hearId = h?.let { SoundcoreHearIdLayout("custom_hear_id", hearChannels, bands, emptyList()) },
            )
            val hearId = h?.let {
                SoundcoreHearId(
                    it.getValue("curve").jsonArray.map { v -> values(v) },
                    it.getValue("time").jsonPrimitive.content.toLong(),
                    it.getValue("type").jsonPrimitive.int,
                    it.getValue("genre").jsonPrimitive.int,
                    it.getValue("custom").jsonArray.map { v -> values(v) },
                )
            }
            val got = SoundcoreEqualizer.write(
                spec, SoundcoreEqConfig(c.getValue("preset").jsonPrimitive.int, channels), hearId,
                gender = c["gender"]?.jsonPrimitive?.int ?: 0, age = c["age"]?.jsonPrimitive?.int ?: 0,
                hearIdInitialized = true, current = null,
            ).single()
            if ("frame" in c) {
                assertEquals(c.str("frame"), SoundcorePacket.build(got.command, got.body).hex(), where)
            } else {
                assertEquals(c.str("command") to c.str("body"), "%04x".format(got.command) to got.body.hex(), where)
            }
        }
    }

    @Test
    fun rebuildsTheA40sRecordedWrites() {
        // The Soundcore app's own writes, recorded from the owner's Space A40.
        val spec = SoundcoreEqSpec.of("A3936")!!
        val state = SoundcoreState.parse(SoundcoreLayout.of("A3936")!!, vectors.getValue("a3936_state").jsonObject.str("body").hexBytes())!!
        val writes = vectors.getValue("eq_writes").jsonArray.map { it.jsonObject }
        assertEquals(7, writes.size)
        for (w in writes) {
            val target = SoundcoreEqConfig(w.getValue("preset").jsonPrimitive.int, listOf(ints(w, "left_tenths"), ints(w, "right_tenths")))
            assertEquals(listOf("0387" to w.str("body")), packets(SoundcoreEqualizer.write(spec, state, target)))
        }
    }

    @Test
    fun presetsAndCustomCurvesAsTheSoundcoreAppSendsThem() {
        // Recorded on the A40: a custom curve sends bands 9 and 10 as 0, Soundcore Signature sends band 10 as -12 dB.
        val spec = SoundcoreEqSpec.of("A3936")!!
        val writes = vectors.getValue("eq_writes").jsonArray.map { it.jsonObject }
        val custom = writes.first { it.getValue("preset").jsonPrimitive.int == SoundcoreEq.CUSTOM_PRESET && ints(it, "left_tenths")[0] == 60 }
        val curve = ints(custom, "left_tenths").copyOf(8)
        assertEquals(SoundcoreEqConfig(SoundcoreEq.CUSTOM_PRESET, listOf(ints(custom, "left_tenths"), ints(custom, "right_tenths"))), SoundcoreEqualizer.forCustom(spec, curve))
        val signature = writes.first { it.getValue("preset").jsonPrimitive.int == 0 }
        assertEquals(SoundcoreEqConfig(0, listOf(ints(signature, "left_tenths"), ints(signature, "right_tenths"))), SoundcoreEqualizer.forPreset(spec, spec.preset("SoundcoreSignature")!!))
    }

    @Test
    fun checkedMeansARecordingOrAnOpenScq30Test() {
        val tested = vectors.getValue("openscq30_eq").jsonArray.map { it.jsonObject.str("model") }.toSet()
        for (spec in SoundcoreEqSpec.ALL) {
            val expected = when {
                spec.model == "A3936" -> "hardware"
                spec.model in tested -> "openscq30_tests"
                else -> null
            }
            assertEquals(expected, spec.checked, spec.model)
        }
        assertEquals(12, SoundcoreEqSpec.ALL.count { it.checked != null })
    }

    @Test
    fun theScreenWorksInWholeDb() {
        val a40 = SoundcoreEqSpec.of("A3936")!!
        val layout = SoundcoreEqScreen.layout(a40)
        assertEquals(listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800), layout.bands.map { it.hz })
        assertEquals(6, layout.userRangeDb)
        assertEquals(a40.presetSet.presets.size, layout.presets.size)
        assertEquals(4.0, layout.presets.first { it.name == "Acoustic" }.bands[0].gainDb)
        // +6 dB on the first band, as recorded from the Soundcore app
        val custom = SoundcoreEqScreen.custom(a40, listOf(6, 0, 0, 0, 0, 0, 0, 0))!!
        assertEquals(listOf(60, 0, 0, 0, 0, 0, 0, 0, 0, 0), custom.channels[0]!!.toList())
        assertEquals(listOf(6, 0, 0, 0, 0, 0, 0, 0), SoundcoreEqScreen.gainsDb(a40, custom))
        assertNull(SoundcoreEqScreen.preset(a40, custom))
        assertEquals("Soundcore Signature", SoundcoreEqScreen.preset(a40, SoundcoreEqualizer.forPreset(a40, a40.presetSet.presets[0]))!!.name)
        // whole-dB models keep their own range
        assertEquals(6, SoundcoreEqScreen.layout(SoundcoreEqSpec.of("A3116")!!).userRangeDb)
        assertEquals(12, SoundcoreEqScreen.layout(SoundcoreEqSpec.of("A3909")!!).userRangeDb)
        assertNull(SoundcoreEqScreen.custom(SoundcoreEqSpec.of("A3949")!!, List(8) { 0 }), "no custom curve on the P20i")
    }

    @Test
    fun readsTheEqBackFromTheState() {
        val spec = SoundcoreEqSpec.of("A3936")!!
        val body = vectors.getValue("a3936_state").jsonObject.str("body").hexBytes()
        val own = A3936State.parse(body)!!
        val read = SoundcoreEqualizer.read(spec, SoundcoreState.parse(SoundcoreLayout.of("A3936")!!, body)!!)!!
        assertEquals(own.eqPreset, read.preset)
        // Every model with a state reply in OpenSCQ30's tests: the EQ reads, with a preset or a curve in range.
        for (c in vectors.getValue("openscq30_states").jsonArray.map { it.jsonObject }) {
            val s = SoundcoreEqSpec.of(c.str("model")) ?: continue
            val state = SoundcoreState.parse(SoundcoreLayout.of(s.model)!!, c.str("body").hexBytes())!!
            val eq = SoundcoreEqualizer.read(s, state)
            assertNotNull(eq, c.str("openscq30_test"))
            for (ch in eq!!.channels.filterNotNull()) assertTrue(ch.all { it in s.min..s.max }, c.str("openscq30_test"))
        }
    }
}

class SoundcoreModelsTest {
    @Test
    fun identifiesPairedDevices() {
        val a40 = "0cf12d31-fac3-4553-bd80-d6832e7b3936"
        val spp = "00001101-0000-1000-8000-00805f9b34fb"
        assertEquals(SoundcoreModels.Match("A3936", a40), SoundcoreModels.identify("Space A40 renamed", listOf(spp, a40)))
        assertEquals(SoundcoreModels.Match("A3947", null), SoundcoreModels.identify("soundcore liberty 4 nc", listOf(spp)))
        // A vendor service that names no known model: the name says the model, the service is still opened (as OpenSCQ30 does).
        val other = "0cf12d31-fac3-4553-bd80-d6832e7c0001"
        assertEquals(SoundcoreModels.Match("A3947", other), SoundcoreModels.identify("Soundcore Liberty 4 NC", listOf(other)))
        // Models whose service OpenSCQ30 fixes.
        assertEquals(SoundcoreModels.Match("A3930", spp), SoundcoreModels.identify("Soundcore Liberty 2 Pro+", listOf(other)))
        assertEquals(SoundcoreModels.Match("A3116", "0cf12d31-fac3-4553-bd80-d6832e7b3116"), SoundcoreModels.identify("Soundcore Motion+", emptyList()))
        assertNull(SoundcoreModels.identify("SOUNDPEATS H3", listOf(spp)))
        assertNull(SoundcoreModels.identify("", emptyList()))
    }

    @Test
    fun reportOnlyModels() {
        // Models only the Soundcore app's strings name: found by their service or name, with nothing to read but a report.
        val liberty4 = "0cf12d31-fac3-4553-bd80-d6832e7b3953"
        assertEquals(SoundcoreModels.Match("A3953", liberty4), SoundcoreModels.identify("soundcore Liberty 4", listOf(liberty4)))
        assertEquals(SoundcoreModels.Match("A3953", null), SoundcoreModels.identify("Soundcore Liberty 4", emptyList()))
        assertTrue(SoundcoreModels.model("A3953")!!.reportOnly)
        assertTrue(SoundcoreLayout.of("A3953")!!.fields.isEmpty())
        assertTrue(!SoundcoreModels.model("A3936")!!.reportOnly)
        // A Soundcore vendor service naming a number PairFree does not know at all: report only too.
        val unknown = "0cf12d31-fac3-4553-bd80-d6832e7b9999"
        assertEquals(SoundcoreModels.Match("A9999", unknown), SoundcoreModels.identify("Some new earbuds", listOf(unknown)))
        assertTrue(SoundcoreLayout.orReportOnly("A9999").fields.isEmpty())
        // The session keeps the reply for the report, with anything that looks like a serial number masked.
        val session = SoundcoreSession("A9999")
        val serial = "9999A1B2C3D4E5F6".encodeToByteArray()
        val body = byteArrayOf(1, 1, 5, 5) + "01.23".encodeToByteArray() + serial + byteArrayOf(0x10, 0x20)
        session.receive(SoundcorePacket(SoundcoreCommand.STATE, body))
        val hex = session.lastState!!.reportHex().split(" ")
        assertEquals(body.size, hex.size)
        assertEquals(List(serial.size) { "XX" }, hex.subList(9, 9 + serial.size))
        assertEquals(listOf("01", "01", "05", "05"), hex.subList(0, 4))
    }

    @Test
    fun everyNameNamesOneModel() {
        val names = SoundcoreModels.ALL.flatMap { m -> m.names.map { it.lowercase() } }
        assertEquals(names.size, names.toSet().size)
        for (m in SoundcoreModels.ALL) for (n in m.names) assertEquals(m.code, SoundcoreModels.fromName(n))
    }

    @Test
    fun oneBatteryMeansHeadphonesOrASpeaker() {
        for (m in SoundcoreModels.ALL) {
            if (m.reportOnly) continue // no layout yet: their form comes from the Soundcore app's product names
            val layout = SoundcoreLayout.of(m.code)!!
            val twoEarbuds = layout.has("dual_battery") || layout.has("dual_battery_level")
            assertEquals(twoEarbuds, m.form == Form.EARBUDS, m.code)
        }
        assertEquals(Form.SPEAKER, SoundcoreModels.model("A3116")!!.form, "Motion+")
        assertEquals(Form.HEADPHONES, SoundcoreModels.model("A3028")!!.form, "Life Q30")
        assertEquals(Form.EARBUDS, SoundcoreModels.model("A3936")!!.form, "Space A40")
    }

    @Test
    fun everyModelCanBeRead() {
        assertEquals(SoundcoreModels.ALL.map { it.code }.toSet(), SoundcoreSession.SUPPORTED_MODELS)
    }

    @Test
    fun tableMatchesDevicesJson() {
        val devices = load("devices.json").getValue("devices").jsonArray.map { it.jsonObject }
        assertEquals(devices.map { it.str("model") }, SoundcoreModels.ALL.map { it.code })
        for (d in devices) {
            val model = SoundcoreModels.model(d.str("model"))!!
            val excludes = d["ldac_excludes"]?.jsonArray?.map { Command.valueOf(it.jsonPrimitive.content.uppercase()) }.orEmpty().toSet()
            assertEquals(excludes, model.ldacExcludes)
            assertTrue(!model.isQualcomm, "never send GAIA frames to Soundcore earbuds")
        }
        val codes = SoundcoreModels.ALL.map { it.code }.toSet()
        assertTrue(codes.containsAll(SoundcoreSession.SUPPORTED_MODELS), "a model PairFree can read is in the table")
        assertEquals(setOf(Command.MULTIPOINT), SoundcoreModels.model("A3936")!!.ldacExcludes)
    }

    @Test
    fun modelFromServiceUuid() {
        assertEquals("A3936", SoundcoreModels.fromServiceUuid("0cf12d31-fac3-4553-bd80-d6832e7b3936"))
        assertEquals("A3936", SoundcoreModels.fromServiceUuid("0CF12D31-FAC3-4553-BD80-D6832E7B3936"))
        assertEquals("A3116", SoundcoreModels.fromServiceUuid("0cf12d31-fac3-4553-bd80-d6832e7b3116"))
        assertNull(SoundcoreModels.fromServiceUuid("00001101-0000-1000-8000-00805f9b34fb"))
        assertTrue(SoundcoreModels.isVendorService("0cf12d31-fac3-4553-bd80-d6832e7b3936"))
        // Hex ids, as the Soundcore app (2.9.9) has them: a variant a model lists, else an id of its own, for a report.
        assertEquals("A3926", SoundcoreModels.fromServiceUuid("0cf12d31-fac3-4553-bd80-d6832e7b392b"))
        assertEquals("A39AC", SoundcoreModels.fromServiceUuid("0cf12d31-fac3-4553-bd80-d6832e7b39ac"))
        assertNull(SoundcoreModels.fromServiceUuid("0cf12d31-fac3-4553-bd80-d6832e7b39zz"))
        val variant = "0cf12d31-fac3-4553-bd80-d6832e7b392b"
        assertEquals(SoundcoreModels.Match("A3926", variant), SoundcoreModels.identify("Soundcore Life Dot 2S", listOf(variant)))
        val unknown = "0cf12d31-fac3-4553-bd80-d6832e7b39ac"
        assertEquals(SoundcoreModels.Match("A39AC", unknown), SoundcoreModels.identify("New earbuds", listOf(unknown)))
    }

    @Test
    fun modelsTheSoundcoreAppReadsLikeAnother() {
        // Not in OpenSCQ30: found by their service or name, with the layout of the model the Soundcore app reads them like.
        val lifeA1 = "0cf12d31-fac3-4553-bd80-d6832e7b3927"
        assertEquals(SoundcoreModels.Match("A3927", lifeA1), SoundcoreModels.identify("Soundcore Life A1", listOf(lifeA1)))
        val spp = "00001101-0000-1000-8000-00805f9b34fb"
        assertEquals(SoundcoreModels.Match("A3913", spp), SoundcoreModels.identify("Soundcore Liberty 2", emptyList()), "SPP, as the app opens")
        assertEquals(SoundcoreModels.Match("A3910", spp), SoundcoreModels.identify("Soundcore Liberty Air 2", emptyList()))
        for ((model, like) in mapOf("A3927" to "A3926", "A3982" to "A3933", "A3993" to "A3933", "A3913" to "A3909", "A3025" to "A3029")) {
            assertTrue(!SoundcoreModels.model(model)!!.reportOnly, model)
            assertEquals(SoundcoreLayout.of(like)!!.fields, SoundcoreLayout.of(model)!!.fields, model)
            assertEquals(SoundcoreButtonSpec.of(like)?.groups, SoundcoreButtonSpec.of(model)?.groups, model)
        }
        assertEquals(Form.HEADPHONES, SoundcoreModels.model("A3025")!!.form, "Life Q20")
    }
}

/** Touch controls: OpenSCQ30's tests and the A40's recorded writes, rebuilt byte for byte. */
class SoundcoreButtonsTest {
    private val devices = load("devices.json").getValue("devices").jsonArray.map { it.jsonObject }
    private val actionSets = load("button_actions.json").getValue("sets").jsonObject
    private val a40Body = vectors.getValue("a3936_state").jsonObject.str("body").hexBytes()
    private val a40 = SoundcoreState.parse(SoundcoreLayout.of("A3936")!!, a40Body)!!

    private fun packets(list: List<SoundcorePacket>) = list.map { "%04x".format(it.command) to it.body.hex() }

    private fun button(name: String) =
        TouchSide.valueOf(name.substringBefore('_').uppercase()) to TouchPress.valueOf(name.substringAfter('_').uppercase())

    private fun JsonObject.bool(key: String) = getValue(key).jsonPrimitive.boolean

    /** One step of a test case: a button set to an action (null: off), or a switch. */
    private fun apply(touch: SoundcoreTouch, step: JsonObject): SoundcoreButtons.Change? = when {
        "button" in step -> {
            val (side, press) = button(step.str("button"))
            val call = step["during_call"]?.jsonPrimitive?.boolean == true
            val (group, buttons) = touch.groups.first { it.first.duringCall == call }
            val id = step["action"]?.jsonPrimitive?.contentOrNull?.let { a ->
                buttons.first { it.side == side && it.press == press }.choices.first { it.name == a }.id
            }
            touch.setButton(group, side, press, id)
        }
        "touch_tone" in step -> touch.setTouchTone(step.bool("touch_tone"))
        "touch_lock" in step -> touch.setTouchLock(step.bool("touch_lock"))
        "touch_enabled" in step -> touch.setTouchEnabled(step.bool("touch_enabled"))
        else -> error("unknown step $step")
    }

    private fun read(touch: SoundcoreTouch, name: String): TouchButton {
        val (side, press) = button(name)
        return touch.groups.filter { !it.first.duringCall }.flatMap { it.second }.first { it.side == side && it.press == press }
    }

    @Test
    fun specsMatchTheData() {
        val withButtons = devices.filter { "buttons" in it }
        assertEquals(withButtons.map { it.str("model") }, SoundcoreButtonSpec.ALL.map { it.model })
        assertEquals(37, withButtons.size)
        for (d in devices) {
            // OpenSCQ30, or for a model it lacks the Soundcore app, offers touch actions on exactly these models (the
            // A3031's state holds them, but OpenSCQ30 does not offer them).
            val app = d["soundcore_app"]?.jsonObject?.get("features")?.jsonArray.orEmpty()
            val features = (d.getValue("openscq30").jsonObject.getValue("features").jsonArray + app).map { it.jsonPrimitive.content }
            assertEquals(features.any { it.endsWith("button_configuration") && !it.startsWith("reset") }, "buttons" in d, d.str("model"))
        }
        for (d in withButtons) {
            val spec = SoundcoreButtonSpec.of(d.str("model"))!!
            val layout = SoundcoreLayout.of(spec.model)!!
            val b = d.getValue("buttons").jsonObject
            assertEquals(b["reset"]?.jsonPrimitive?.boolean ?: false, spec.reset, spec.model)
            assertEquals(b["checked"]?.jsonPrimitive?.content, spec.checked, spec.model)
            val groups = b.getValue("groups").jsonArray.map { it.jsonObject }
            assertEquals(groups.size, spec.groups.size, spec.model)
            for ((g, json) in spec.groups.zip(groups)) {
                // A group fills its state fields exactly: a byte per enabled flag and a byte per action.
                val size = g.fields.sumOf { layout.locate(it)!!.second }
                val bytes = g.order.sumOf { (_, press) -> if (g.presses.getValue(press).enabled == ButtonByte.NONE) 1 else 2 }
                assertEquals(size, bytes, spec.model)
                assertEquals(json.getValue("order").jsonArray.size, g.order.size, spec.model)
                for ((name, p) in json.getValue("presses").jsonObject) {
                    val press = g.presses.getValue(TouchPress.valueOf(name.uppercase()))
                    val set = actionSets.getValue(p.jsonObject.str("actions")).jsonArray.map {
                        SoundcoreAction(it.jsonArray[0].jsonPrimitive.int, it.jsonArray[1].jsonPrimitive.content)
                    }
                    assertEquals(set, press.actions, spec.model)
                    assertEquals(p.jsonObject.getValue("id").jsonPrimitive.int, press.id, spec.model)
                    assertTrue(press.actions.none { it.id == 0xF }, "F means off: ${spec.model}")
                }
            }
        }
        assertEquals(0x0487, SoundcoreButtonSpec.of("A3330")!!.groups.single { it.duringCall }.actionCommand, "OpenSCQ30 a3330.rs")
    }

    @Test
    fun rebuildsOpenScq30sTests() {
        val cases = vectors.getValue("openscq30_buttons").jsonArray.map { it.jsonObject }
        assertEquals(9, cases.size)
        for (c in cases) {
            val where = c.str("openscq30_test")
            var state = SoundcoreState.parse(SoundcoreLayout.of(c.str("model"))!!, c.str("state").hexBytes())!!
            for ((name, want) in c["reads"]?.jsonObject.orEmpty()) {
                val touch = SoundcoreTouch.of(state)
                when (name) {
                    "touch_tone" -> assertEquals(want.jsonPrimitive.boolean, touch.touchTone, where)
                    "touch_enabled" -> assertEquals(want.jsonPrimitive.boolean, touch.touchEnabled, where)
                    else -> {
                        val b = read(touch, name)
                        assertEquals(want.jsonPrimitive.contentOrNull, b.action?.name, "$where $name")
                        if (want.jsonPrimitive.contentOrNull == null) assertNull(b.actionId, "$where $name is off")
                    }
                }
            }
            val sent = mutableListOf<SoundcorePacket>()
            for (step in c["steps"]?.jsonArray.orEmpty().map { it.jsonObject }) {
                val change = apply(SoundcoreTouch.of(state), step)
                assertNotNull(change, "$where $step")
                sent += change!!.packets
                state = change.state
            }
            val expected = c["packets"]?.jsonArray.orEmpty().map { it.jsonObject.str("command") to it.jsonObject.str("body") }
            assertEquals(expected.sortedBy { it.toString() }, packets(sent).sortedBy { it.toString() }, "in any order: $where")
        }
    }

    @Test
    fun rebuildsTheA40sRecordedWrites() {
        val touch = SoundcoreTouch.of(a40)
        assertEquals("volume_down", read(touch, "left_single").action?.name)
        assertEquals("volume_up", read(touch, "right_single").action?.name, "on with both earbuds in (01), off with one")
        assertEquals("next_song", read(touch, "left_double").action?.name)
        assertEquals("play_pause", read(touch, "right_double").action?.name)
        assertEquals("ambient_sound_mode", read(touch, "left_long").action?.name)
        assertEquals("ambient_sound_mode", read(touch, "right_long").action?.name)
        assertEquals(false, touch.touchTone)
        assertEquals(setOf(AmbientMode.NOISE_CANCELLING, AmbientMode.NORMAL), touch.noiseCycle)
        assertNull(touch.touchLock)
        assertNull(touch.touchEnabled)
        assertTrue(touch.canReset)

        var state = a40
        for (step in vectors.getValue("a3936_buttons").jsonObject.getValue("steps").jsonArray.map { it.jsonObject }) {
            val change = apply(SoundcoreTouch.of(state), step)!!
            val frames = change.packets.map { SoundcorePacket.build(it.command, it.body).hex() }
            assertEquals(step.getValue("frames").jsonArray.map { it.jsonPrimitive.content }, frames, step.toString())
            state = change.state
        }
        assertEquals(a40.field("buttons")!!.hex(), state.field("buttons")!!.hex(), "every change was undone, as in the recording")
    }

    @Test
    fun withOneEarbudInTheHighBitsApply() {
        val touch = SoundcoreTouch.of(a40.with("tws_status", byteArrayOf(1, 0)))
        assertEquals("play_pause", read(touch, "left_double").action?.name, "63: 3 with both in, 6 with one")
        assertNull(read(touch, "right_single").actionId, "enabled 01: off with one earbud in")
        val change = touch.setButton(touch.groups.single().first, TouchSide.LEFT, TouchPress.SINGLE, 3)!!
        assertEquals(listOf("0481" to "000231"), packets(change.packets), "only the high four bits change")
    }

    @Test
    fun theSessionFollowsTheTwsStatus() {
        val session = SoundcoreSession("A3936")
        session.receive(SoundcorePacket(SoundcoreCommand.STATE, a40Body))
        assertEquals("next_song", read(session.touch!!, "left_double").action?.name)
        session.receive(SoundcorePacket(SoundcoreCommand.TWS_STATUS, byteArrayOf(1, 0)))
        assertEquals("play_pause", read(session.touch!!, "left_double").action?.name, "one earbud in after 01 02")
    }

    @Test
    fun turningOneSideOffTurnsTheOtherOffWhereOpenScq30Does() {
        // The A3926 uses OpenSCQ30's common settings: single presses go off together, and a change to
        // several buttons goes as one 04 84 with every button's bytes.
        val case = vectors.getValue("openscq30_buttons").jsonArray.map { it.jsonObject }.single { it.str("model") == "A3926" }
        val touch = SoundcoreTouch.of(SoundcoreState.parse(SoundcoreLayout.of("A3926")!!, case.str("state").hexBytes())!!)
        val group = touch.groups.single().first
        val change = touch.setButton(group, TouchSide.LEFT, TouchPress.SINGLE, null)!!
        assertEquals(listOf("0484" to "010001000100010000000000"), packets(change.packets))
        assertNull(read(SoundcoreTouch.of(change.state), "right_single").actionId)
        assertNull(touch.setButton(group, TouchSide.LEFT, TouchPress.DOUBLE, null), "double press cannot be turned off")
    }

    @Test
    fun theLibertyAir2SwitchesEachTouchOnItsOwn() {
        // The Soundcore app (2.9.9) reads a switch and an action for each touch, then on newer firmware single taps.
        val layout = SoundcoreLayout.of("A3910")!!
        val head = ByteArray(31).also { it[1] = 1 } // TWS status 00 01, zeros up to the end of the HearID
        val buttons = byteArrayOf(1, 2, 1, 5, 1, 3, 0, 6) // left double previous, left long assistant, right double next, right long off
        val touch = SoundcoreTouch.of(SoundcoreState.parse(layout, head + buttons + byteArrayOf(0, 1) + byteArrayOf(1, 6, 0, 6))!!)
        assertEquals(2, touch.groups.size)
        assertEquals("previous_song", read(touch, "left_double").action?.name)
        assertEquals("voice_assistant", read(touch, "left_long").action?.name)
        assertNull(read(touch, "right_long").actionId, "its switch is off")
        assertEquals("play_pause", read(touch, "left_single").action?.name)
        assertNull(read(touch, "right_single").actionId)
        assertTrue(read(touch, "left_double").canTurnOff)
        assertTrue(read(touch, "left_double").choices.none { it.name == "ambient_sound_mode" }, "the app offers no noise-mode action")
        val group = touch.groups.first().first
        val off = touch.setButton(group, TouchSide.LEFT, TouchPress.DOUBLE, null)!!
        assertEquals(listOf("0483" to "000000"), packets(off.packets), "only that switch, as the app sends it")
        assertNull(read(SoundcoreTouch.of(off.state), "left_double").actionId)
        assertEquals("next_song", read(SoundcoreTouch.of(off.state), "right_double").action?.name, "the other side keeps its own")
        val on = touch.setButton(group, TouchSide.RIGHT, TouchPress.LONG, 2)!!
        assertEquals(listOf("0481" to "010102", "0483" to "010101"), packets(on.packets))
        // Older firmware: no single taps.
        assertEquals(1, SoundcoreTouch.of(SoundcoreState.parse(layout, head + buttons + byteArrayOf(0))!!).groups.size)
    }

    @Test
    fun noiseCycleAndReset() {
        val touch = SoundcoreTouch.of(a40)
        val all = touch.setNoiseCycle(AmbientMode.entries.toSet())!!
        assertEquals(listOf("0682" to "07"), packets(all.packets))
        assertEquals(AmbientMode.entries.toSet(), SoundcoreTouch.of(all.state).noiseCycle)
        assertNull(touch.setNoiseCycle(emptySet()), "the cycle needs a mode")
        assertEquals("0482" to "", packets(listOf(touch.reset()!!)).single())
    }

    @Test
    fun everyReplyInOpenScq30sTestsReadsItsTouchControls() {
        for (c in vectors.getValue("openscq30_states").jsonArray.map { it.jsonObject }) {
            val spec = SoundcoreButtonSpec.of(c.str("model")) ?: continue
            val touch = SoundcoreTouch.of(SoundcoreState.parse(SoundcoreLayout.of(spec.model)!!, c.str("body").hexBytes())!!)
            assertEquals(spec.groups.size, touch.groups.size, c.str("openscq30_test"))
            for ((group, buttons) in touch.groups) assertEquals(group.order.size, buttons.size)
        }
    }
}
