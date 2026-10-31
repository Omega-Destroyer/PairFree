package io.github.omegadestroyer.pairfree.protocol.pixel

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value
import io.github.omegadestroyer.pairfree.protocol.hexBytes
import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun load(name: String): JsonObject = Json.parseToJsonElement(repoFile("data/pixel/$name").readText()).jsonObject

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

private fun hex(bytes: ByteArray) = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

private const val CHANNEL = 18

/** A reply from the earbuds on channel 18 to [method], as HDLC bytes. */
private fun reply(method: String, payload: ByteArray = ByteArray(0), status: Int = 0, type: Int = PixelWire.PACKET_RESPONSE): ByteArray =
    PixelHdlc.encode(PixelWire.CHANNELS.getValue(CHANNEL), PixelRpcPacket(type, CHANNEL, PixelRpc.SERVICE, PixelRpc.hash(method), payload, status).encode())

/** A setting value: the family's message in [slot] holding field [field] = [value] (a varint). */
private fun settingValue(slot: Int, field: Int, value: Long): ByteArray = ProtoWriter().message(slot, ProtoWriter().varint(field, value)).bytes()

private fun settingMessage(slot: Int, field: Int, inner: ProtoWriter): ByteArray = ProtoWriter().message(slot, ProtoWriter().message(field, inner)).bytes()

/** The plain frames, group 135. */
private fun tlv(code: Int, payload: ByteArray = ByteArray(0)) = PixelTlvFrame(PixelWire.TLV_GROUP, code, payload).bytes()

/** Pigweed's framing, Maestro's ids, the plain frames and both kinds of session, against frames built independently. */
class PixelTest {
    private val vectors = load("test_vectors.json")
    private val requests = vectors.getValue("requests").jsonArray.map { it.jsonObject }

    private fun request(what: String): ByteArray = requests.first { it.str("what").startsWith(what) }.str("bytes").hexBytes()

    /** A Maestro session that has found channel 18 and taken [reads] (field to value) and the device type [type]. */
    private fun maestro(type: Int = 6, vararg reads: Pair<Int, Long>): PixelSession = PixelSession(PixelTransport.MAESTRO).also { s ->
        s.start()
        s.feed(reply(PixelWire.METHOD_SOFTWARE_INFO))
        s.feed(reply(PixelWire.METHOD_HARDWARE_INFO, ProtoWriter().int32(1, type).bytes()))
        for ((field, value) in reads) s.feed(reply(PixelWire.METHOD_READ_SETTING, settingValue(PixelWire.SLOT_ALLEGRO, field, value)))
        s.takeOutbox()
    }

    private fun setting(s: PixelSession, key: String) = s.groups.flatMap { it.settings }.firstOrNull { it.key == key }

    @Test
    fun theCrcIsPigweeds() {
        for (c in vectors.getValue("crc32").jsonArray.map { it.jsonObject }) {
            assertEquals(c.str("crc").toLong(16).toInt(), PixelHdlc.crc32(c.str("ascii").encodeToByteArray()), c.str("ascii"))
        }
    }

    @Test
    fun hdlcFramesAreBuiltAndReadByteForByte() {
        for (f in vectors.getValue("hdlc").jsonArray.map { it.jsonObject }) {
            val data = f.str("data").hexBytes()
            val bytes = PixelHdlc.encode(f.getValue("address").jsonPrimitive.int, data, f.getValue("control").jsonPrimitive.int)
            assertEquals(f.str("bytes"), hex(bytes))
            val back = PixelHdlc.Decoder().feed(bytes).single()
            assertEquals(f.getValue("address").jsonPrimitive.int, back.address)
            assertArrayEquals(data, back.data)
        }
    }

    @Test
    fun aDamagedFrameIsDropped() {
        val bytes = request("read Allegro setting 13")
        bytes[bytes.size - 3] = (bytes[bytes.size - 3] + 1).toByte()
        assertTrue(PixelHdlc.Decoder().feed(bytes).isEmpty())
    }

    @Test
    fun framesSplitAcrossReadsAreJoined() {
        val bytes = request("read Allegro setting 13") + request("write noise control adaptive")
        val d = PixelHdlc.Decoder()
        val got = bytes.toList().chunked(5).flatMap { d.feed(it.toByteArray()) }
        assertEquals(2, got.size)
    }

    @Test
    fun theIdsAreTheOnesAProTwoReceived() {
        for (i in vectors.getValue("ids").jsonArray.map { it.jsonObject }) {
            assertEquals(i.str("id").toLong(16).toInt(), PixelRpc.hash(i.str("name")), i.str("name"))
        }
    }

    @Test
    fun maestroOpensByProbingTheChannels() {
        val s = PixelSession(PixelTransport.MAESTRO)
        val start = s.start()
        assertEquals(PixelWire.CHANNELS.size, start.size)
        assertEquals(hex(request("probe channel 18")), hex(start.first().bytes))
        assertTrue(start.all { it.reply == null })
        // Channel 18 answers: the rest goes out on it, and the other probes no longer do.
        s.feed(reply(PixelWire.METHOD_SOFTWARE_INFO))
        assertEquals(CHANNEL, s.channel)
        assertFalse(s.willSend(start.last()))
        val next = s.takeOutbox()
        assertTrue(next.any { hex(it.bytes) == hex(request("read Allegro setting 13")) }, "reads the noise control")
        // Never a read of what PairFree never writes either: auto update, setup, diagnostics.
        val read = next.mapNotNull { it.toString().removePrefix("read setting ").toIntOrNull() }
        assertTrue(read.isNotEmpty())
        for (f in PixelModels.fields(PixelFamily.ALLEGRO).filter { it.neverSend }) assertFalse(f.field in read, f.name)
    }

    @Test
    fun theFirmwareAndModelComeFromTheEarbuds() {
        val s = PixelSession(PixelTransport.MAESTRO)
        s.start()
        val version = ProtoWriter().bytes(2, "release_5.203".encodeToByteArray())
        val firmware = ProtoWriter().message(2, version).message(3, version)
        s.feed(reply(PixelWire.METHOD_SOFTWARE_INFO, ProtoWriter().message(4, firmware).bytes()))
        assertEquals("release_5.203", s.firmware)
        // Hardware info holds the serial numbers too (field 7): only the device type is kept.
        val serials = ProtoWriter().bytes(1, "CASESERIAL".encodeToByteArray()).bytes(2, "RIGHTSERIAL".encodeToByteArray())
        s.feed(reply(PixelWire.METHOD_HARDWARE_INFO, ProtoWriter().int32(1, 6).message(7, serials).bytes()))
        assertEquals("Pixel Buds Pro 2", s.model?.name)
        assertEquals(Value.Text("6_release_5.203"), s.state.values[Command.FIRMWARE_VERSION])
        val report = s.replies.values.joinToString(" ") { hex(it) }
        assertFalse(report.contains(hex("SERIAL".encodeToByteArray())), "no serial number in the report")
    }

    @Test
    fun theBatteryStreams() {
        val s = maestro()
        fun part(level: Int) = ProtoWriter().int32(1, level).int32(2, 1)
        val battery = ProtoWriter().message(1, part(60)).message(2, part(80)).message(3, part(75))
        s.feed(reply(PixelWire.METHOD_RUNTIME_INFO, ProtoWriter().message(6, battery).bytes(), type = PixelWire.PACKET_SERVER_STREAM))
        assertEquals(Value.Number(80), s.state.values[Command.BATTERY_LEFT])
        assertEquals(Value.Number(75), s.state.values[Command.BATTERY_RIGHT])
        assertEquals(Value.Number(60), s.state.values[Command.BATTERY_CASE])
    }

    @Test
    fun noiseControlIsSharedAndAdaptiveIsItsOwnRow() {
        val s = maestro(6, 13 to 2L)
        assertEquals(Command.ANC_MODE.labels.entries.first { it.value == "anc" }.key, s.state.number(Command.ANC_MODE))
        val adaptive = setting(s, "adaptive_noise") as Setting.Toggle
        assertFalse(adaptive.on)
        val out = s.change(SettingChange.Toggle("adaptive_noise", true))!!
        assertEquals(hex(request("write noise control adaptive")), hex(out.single().bytes))
        // The change shows at once, and the read comes later to confirm it.
        assertEquals(Command.ANC_MODE.labels.entries.first { it.value == "adaptive" }.key, s.state.number(Command.ANC_MODE))
        assertEquals(listOf("read setting 13"), s.takeReadBacks().map { it.toString() })
    }

    @Test
    fun aProWithoutAdaptiveGetsNoAdaptiveRow() {
        val s = maestro(4, 13 to 3L)
        assertNull(setting(s, "adaptive_noise"))
        assertNull(s.set(Command.ANC_MODE, Command.ANC_MODE.labels.entries.first { it.value == "adaptive" }.key))
        assertNotNull(s.set(Command.ANC_MODE, Command.ANC_MODE.labels.entries.first { it.value == "off" }.key))
    }

    @Test
    fun aSettingTheEarbudsRefuseStaysHidden() {
        val s = PixelSession(PixelTransport.MAESTRO)
        s.start()
        s.feed(reply(PixelWire.METHOD_SOFTWARE_INFO))
        val read = s.takeOutbox().first { it.toString() == "read setting 22" }
        assertTrue(s.willSend(read))
        s.feed(reply(PixelWire.METHOD_READ_SETTING, status = 2, type = PixelWire.PACKET_SERVER_ERROR))
        assertTrue(22 in s.refused)
        assertNull(setting(s, "conversation_detection"))
    }

    @Test
    fun theEqIsReadAndWrittenAsFiveFloats() {
        val s = maestro()
        val curve = ProtoWriter().float(1, 5f).float(2, 3f).float(3, 0f).float(4, 0f).float(5, 0f)
        s.feed(reply(PixelWire.METHOD_READ_SETTING, settingMessage(PixelWire.SLOT_ALLEGRO, 16, curve)))
        assertTrue(s.hasEq)
        assertEquals(listOf(5f, 3f, 0f, 0f, 0f), s.eq)
        val heavy = PixelValues.EQ_PRESETS.first { it.name == "Heavy bass" }
        assertEquals(hex(request("write the Heavy bass preset")), hex(s.eqWrite(heavy.gains)!!.single().bytes))
    }

    @Test
    fun thePresetsAreGooglesSix() {
        assertEquals(listOf("Default", "Heavy bass", "Light bass", "Balanced", "Vocal boost", "Clarity"), PixelValues.EQ_PRESETS.map { it.name })
        assertEquals(listOf(-3.5f, 0.5f, 1f, -1f, 2.5f), PixelValues.EQ_PRESETS.first { it.name == "Balanced" }.gains)
        assertTrue(PixelValues.EQ_PRESETS.all { p -> p.gains.size == PixelValues.EQ_BANDS.size && p.gains.all { it in -6f..6f } })
    }

    @Test
    fun pressAndHoldIsSetOneEarbudAtATime() {
        val s = maestro()
        fun side(action: Int) = ProtoWriter().message(4, ProtoWriter().int32(1, action))
        s.feed(reply(PixelWire.METHOD_READ_SETTING, settingMessage(PixelWire.SLOT_ALLEGRO, 7, ProtoWriter().message(1, side(5)).message(2, side(5)))))
        val left = setting(s, "hold_left") as Setting.Choice
        assertEquals("noise_control", left.value)
        assertEquals(PixelValues.HOLD_OPTIONS, left.options)
        val out = s.change(SettingChange.Choice("hold_right", "digital_assistant"))!!
        assertEquals(hex(request("press and hold on the right earbud")), hex(out.single().bytes))
        // The left earbud's row stays as it was.
        assertEquals("noise_control", (setting(s, "hold_left") as Setting.Choice).value)
        assertEquals("digital_assistant", (setting(s, "hold_right") as Setting.Choice).value)
    }

    @Test
    fun pressAndHoldKeepsTwoModesAtLeast() {
        val s = maestro()
        val cycle = ProtoWriter().bool(1, true).bool(2, true).bool(3, false).bool(4, false)
        s.feed(reply(PixelWire.METHOD_READ_SETTING, settingMessage(PixelWire.SLOT_ALLEGRO, 12, cycle)))
        assertNull(s.change(SettingChange.Toggle("cycle_off", false)), "one mode left is refused")
        assertNotNull(s.change(SettingChange.Toggle("cycle_transparency", true)))
    }

    @Test
    fun balanceRunsFromLeftToRight() {
        val s = maestro(6, 17 to ((-40 shl 1) xor (-40 shr 31)).toLong())
        val balance = setting(s, "balance") as Setting.Level
        assertEquals(60, balance.value)
        assertEquals(0, balance.min)
        assertEquals(200, balance.max)
        assertNotNull(s.change(SettingChange.Level("balance", 100)))
    }

    @Test
    fun theOlderModelsUseThePlainFrames() {
        val s = PixelSession(PixelTransport.TLV, PixelModels.fromName("Pixel Buds"))
        val start = s.start()
        assertEquals(hex(request("the plain frames' start")), hex(start.first().bytes))
        assertTrue(start.any { hex(it.bytes) == hex(request("read Pixel Buds (2020) setting 1")) })
        s.feed(tlv(PixelWire.CODE_HARDWARE_INFO, ProtoWriter().int32(1, 1).bytes()))
        assertEquals("Pixel Buds", s.model?.name)
        s.feed(tlv(PixelWire.CODE_SETTING_VALUE, settingValue(PixelWire.SLOT_PRESTO, 1, 1)))
        assertEquals(Value.Switch(true), s.state.values[Command.IN_EAR_DETECTION])
        val out = s.set(Command.IN_EAR_DETECTION, 0)!!
        // The write, then straight away the read, as Google's app sends them.
        assertEquals(hex(request("write Pixel Buds (2020) in-ear detection off")), hex(out[0].bytes))
        assertEquals(hex(request("read Pixel Buds (2020) setting 1")), hex(out[1].bytes))
    }

    @Test
    fun theTwentyTwentyModelsAttentionAlerts() {
        val s = PixelSession(PixelTransport.TLV, PixelModels.fromName("Pixel Buds"))
        s.start()
        s.feed(tlv(PixelWire.CODE_SETTING_VALUE, settingMessage(PixelWire.SLOT_PRESTO, 9, ProtoWriter().bool(1, true).bool(2, true))))
        assertTrue((setting(s, "baby_cry") as Setting.Toggle).on)
        val out = s.change(SettingChange.Toggle("dog_bark", true))!!
        assertEquals(hex(request("write Pixel Buds (2020) attention alerts")), hex(out[0].bytes))
        assertTrue((setting(s, "baby_cry") as Setting.Toggle).on, "the other sound stays as it was")
        assertEquals(SettingTab.SOUND, s.groups.first { g -> g.settings.any { it.key == "dog_bark" } }.tab)
    }

    @Test
    fun theASeriesBassLevel() {
        val s = PixelSession(PixelTransport.TLV, PixelModels.fromName("Pixel Buds A-Series"))
        s.start()
        s.feed(tlv(PixelWire.CODE_SETTING_VALUE, settingValue(PixelWire.SLOT_STRETTO, 14, 1)))
        val level = setting(s, "bass_level") as Setting.Level
        assertEquals(0, level.value)
        assertEquals(PixelValues.BASS_MIN, level.min)
        val out = s.change(SettingChange.Level("bass_level", 2))!!
        assertEquals(hex(request("write the A-Series bass level 2")), hex(out[0].bytes))
    }

    @Test
    fun whatIsNeverSentIsRefused() {
        val s = PixelSession(PixelTransport.TLV, PixelModels.fromName("Pixel Buds"))
        s.start()
        // Firmware update and statistics groups, and a write of the setup or diagnostics settings.
        val update = PixelMessage(PixelTlvFrame(2, 1, ByteArray(0)).bytes(), null, label = "update")
        assertFalse(s.allowed(update))
        val stats = PixelMessage(PixelTlvFrame(136, 2, ByteArray(0)).bytes(), null, label = "stats")
        assertFalse(s.allowed(stats))
        for (family in PixelFamily.entries) for (f in PixelModels.fields(family).filter { it.neverSend }) {
            assertTrue(f.key == null && f.shared == null, "${family.name} ${f.name} has no row")
        }
    }

    @Test
    fun everyModelHasAUniqueDeviceType() {
        val types = PixelModels.ALL.map { it.deviceType }
        assertEquals(types.size, types.toSet().size)
        assertEquals(PixelTransport.TLV, PixelModels.of(2)?.transport)
        assertEquals(PixelTransport.MAESTRO, PixelModels.transportOf(listOf(PixelWire.MAESTRO_UUID.uppercase())))
        assertEquals(PixelTransport.TLV, PixelModels.transportOf(listOf(PixelWire.TLV_UUID_REVERSED)))
    }
}
