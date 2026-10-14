package io.github.omegadestroyer.soundpeter.protocol.soundcore

import io.github.omegadestroyer.soundpeter.protocol.Command
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

class SoundcoreModelsTest {
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
