package io.github.omegadestroyer.soundpeter.protocol.soundcore

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

class SoundcoreModelsTest {
    @Test
    fun modelFromServiceUuid() {
        assertEquals("A3936", SoundcoreModels.fromServiceUuid("0cf12d31-fac3-4553-bd80-d6832e7b3936"))
        assertEquals("A3936", SoundcoreModels.fromServiceUuid("0CF12D31-FAC3-4553-BD80-D6832E7B3936"))
        assertEquals("A3116", SoundcoreModels.fromServiceUuid("0cf12d31-fac3-4553-bd80-d6832e7b3116"))
        assertNull(SoundcoreModels.fromServiceUuid("00001101-0000-1000-8000-00805f9b34fb"))
        assertTrue(SoundcoreModels.isVendorService("0cf12d31-fac3-4553-bd80-d6832e7b3936"))
    }
}
