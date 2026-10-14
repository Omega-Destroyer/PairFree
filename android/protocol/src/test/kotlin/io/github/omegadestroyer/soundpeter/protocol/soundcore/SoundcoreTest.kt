package io.github.omegadestroyer.soundpeter.protocol.soundcore

import io.github.omegadestroyer.soundpeter.protocol.Command
import io.github.omegadestroyer.soundpeter.protocol.Form
import io.github.omegadestroyer.soundpeter.protocol.hexBytes
import io.github.omegadestroyer.soundpeter.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
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

/** The A40 on SoundPeter's screens, replayed from the recording. */
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
        }
        assertEquals(0x1085, SoundcoreLayout.of("A3947")!!.gamingModeCommand, "OpenSCQ30 a3947/modules/flag.rs")
        assertEquals(false, SoundcoreLayout.of("A3116")!!.checksum, "OpenSCQ30 a3116.rs: ChecksumKind::None")
        assertEquals(131, SoundcoreLayout.of("A3936")!!.minSize, "the A40's reply, as recorded")
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
        assertEquals(0, BatteryScale(10, 1).percent(1))
        assertNull(BatteryScale(10, 1).percent(0))
    }
}

/** Other models on SoundPeter's screens, from the replies in OpenSCQ30's tests. */
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
        assertEquals(60, s.batteryLeft)
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
    fun everyNameNamesOneModel() {
        val names = SoundcoreModels.ALL.flatMap { m -> m.names.map { it.lowercase() } }
        assertEquals(names.size, names.toSet().size)
        for (m in SoundcoreModels.ALL) for (n in m.names) assertEquals(m.code, SoundcoreModels.fromName(n))
    }

    @Test
    fun oneBatteryMeansHeadphonesOrASpeaker() {
        for (m in SoundcoreModels.ALL) {
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
        assertTrue(codes.containsAll(SoundcoreSession.SUPPORTED_MODELS), "a model SoundPeter can read is in the table")
        assertEquals(setOf(Command.MULTIPOINT), SoundcoreModels.model("A3936")!!.ldacExcludes)
    }

    @Test
    fun modelFromServiceUuid() {
        assertEquals("A3936", SoundcoreModels.fromServiceUuid("0cf12d31-fac3-4553-bd80-d6832e7b3936"))
        assertEquals("A3936", SoundcoreModels.fromServiceUuid("0CF12D31-FAC3-4553-BD80-D6832E7B3936"))
        assertEquals("A3116", SoundcoreModels.fromServiceUuid("0cf12d31-fac3-4553-bd80-d6832e7b3116"))
        assertNull(SoundcoreModels.fromServiceUuid("00001101-0000-1000-8000-00805f9b34fb"))
        assertTrue(SoundcoreModels.isVendorService("0cf12d31-fac3-4553-bd80-d6832e7b3936"))
    }
}
