package io.github.omegadestroyer.pairfree.protocol.samsung

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.hexBytes
import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun load(name: String): JsonObject = Json.parseToJsonElement(repoFile("data/samsung/$name").readText()).jsonObject

private val vectors = load("test_vectors.json")

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

/** Framing, the model table and the state parser, against GalaxyBudsClient's tests. */
class SamsungProtocolTest {
    private val devices = load("devices.json").getValue("devices").jsonArray.map { it.jsonObject }
    private val cases = vectors.getValue("galaxybudsclient_extended_status").jsonArray.map { it.jsonObject }

    @Test
    fun crcIsXmodem() {
        assertEquals(0x31C3, SamsungPacket.crc16("123456789".encodeToByteArray()), "CRC-16/XMODEM check value")
    }

    @Test
    fun builtFramesParseBack() {
        for (legacy in listOf(false, true)) {
            val frame = SamsungPacket.build(SamsungMessage.NOISE_CONTROLS, byteArrayOf(1), legacy)
            assertEquals(SamsungPacket(SamsungMessage.NOISE_CONTROLS, byteArrayOf(1)), SamsungPacket.parse(frame, legacy))
        }
        // Standard: FD, length 4 (id, payload, CRC) little-endian, id 120, payload, CRC low first, DD.
        // Both examples are in docs/samsung/PROTOCOL.md section 2.
        fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        assertEquals("FD 04 00 78 01 D1 91 DD", hex(SamsungPacket.build(SamsungMessage.NOISE_CONTROLS, byteArrayOf(1))))
        assertEquals("FD 03 00 63 C5 5C DD", hex(SamsungPacket.build(SamsungMessage.VERSION_INFO)))
    }

    @Test
    fun theParserSplitsAStreamAndSkipsBrokenFrames() {
        val a = SamsungPacket.build(SamsungMessage.AMBIENT_VOLUME, byteArrayOf(2))
        val b = SamsungPacket.build(SamsungMessage.TOUCH_UPDATED, byteArrayOf(1))
        val broken = a.copyOf().also { it[4] = 9 }
        val parser = SamsungParser()
        val stream = byteArrayOf(0x00, 0x11) + broken + a + b
        val out = parser.feed(stream.copyOfRange(0, 9)) + parser.feed(stream.copyOfRange(9, stream.size))
        assertEquals(listOf(SamsungMessage.AMBIENT_VOLUME, SamsungMessage.TOUCH_UPDATED), out.map { it.id })
        assertEquals(0, parser.pending)
    }

    @Test
    fun modelsMatchTheData() {
        assertEquals(devices.map { it.str("model") }, SamsungModels.ALL.map { it.model })
        assertEquals(16, SamsungModels.ALL.size)
        for (d in devices) {
            val m = SamsungModels.of(d.str("model"))!!
            // Samsung's own plugin (samsung_app) wins over GalaxyBudsClient where both exist.
            val app = d["samsung_app"]?.jsonObject
            val g = d["galaxybudsclient"]?.jsonObject
            assertEquals((app ?: g)!!.getValue("features").jsonObject.keys, m.features.keys, m.model)
            assertEquals(app != null, m.buds4Layout, m.model)
            if (g != null) {
                assertEquals(g.str("base_name"), m.baseName)
                assertEquals(g.str("framing") == "legacy", m.legacy)
            }
        }
        assertTrue(SamsungModels.of("SM-R170")!!.legacy, "the Galaxy Buds (2019)")
        assertEquals("00001102-0000-1000-8000-00805f9b34fd", SamsungModels.of("SM-R170")!!.serviceUuid)
        assertEquals("2e73a4ad-332d-41fc-90e2-16bef06523f2", SamsungModels.of("SM-R630")!!.serviceUuid)
        assertTrue(SamsungModels.of("SM-R630")!!.supports("hot_command_language_update", 2))
        assertTrue(!SamsungModels.of("SM-R630")!!.supports("hot_command_language_update", 1), "from revision 2")
        assertTrue(!SamsungModels.of("SM-R175")!!.supports("anc", 13), "the Buds+ have no noise cancelling")
    }

    @Test
    fun modelsFromTheirBluetoothNames() {
        assertEquals("SM-R630", SamsungModels.fromName("Galaxy Buds3 Pro (A1B2)")?.model)
        assertEquals("SM-R530", SamsungModels.fromName("Galaxy Buds3 (A1B2)")?.model)
        assertEquals("SM-R510", SamsungModels.fromName("Galaxy Buds2 Pro (A1B2)")?.model)
        assertEquals("SM-R177", SamsungModels.fromName("Galaxy Buds2 (A1B2)")?.model)
        assertEquals("SM-R170", SamsungModels.fromName("Galaxy Buds (A1B2)")?.model)
        assertEquals("SM-R175", SamsungModels.fromName("Galaxy Buds+ (A1B2)")?.model)
        assertEquals("SM-R180", SamsungModels.fromName("Galaxy Buds Live (A1B2)")?.model)
        assertEquals("SM-R420", SamsungModels.fromName("Galaxy Buds3 FE (A1B2)")?.model)
        assertNull(SamsungModels.fromName("Soundcore Space A40"))
        // Models Samsung's plugin matches by more than one name.
        assertEquals("SM-R640", SamsungModels.fromName("Galaxy Buds4 Pro (A1B2)")?.model)
        assertEquals("SM-R540", SamsungModels.fromName("Galaxy Buds4 (A1B2)")?.model)
        assertEquals("SM-U600", SamsungModels.fromName("Galaxy Buds On (A1B2)")?.model)
        assertEquals("SM-U700", SamsungModels.fromName("Samsung H1")?.model)
        assertEquals("SM-U700", SamsungModels.fromName("Galaxy H1 (A1B2)")?.model)
        assertEquals("SM-R545", SamsungModels.fromName("Galaxy T1 (A1B2)")?.model)
        assertNull(SamsungModels.fromName("SOUNDPEATS H1"), "another brand's H1")
    }

    @Test
    fun modelsFromTheirServiceList() {
        val sppNew = "2e73a4ad-332d-41fc-90e2-16bef06523f2"
        val spp = "00001101-0000-1000-8000-00805f9b34fb"
        val handsFree = "0000111e-0000-1000-8000-00805f9b34fb"
        val leAudio = "0000184e-0000-1000-8000-00805f9b34fb"
        // A Buds2 or later publishes its model id (a colour code) in its service list, so a renamed pair is found.
        assertEquals(326, SamsungModels.deviceIdIn(listOf(sppNew, "D908AAB5-7A90-4CBE-8641-86A553DB0146")))
        assertEquals("SM-R510", SamsungModels.identify("My earbuds", listOf(sppNew, "d908aab5-7a90-4cbe-8641-86a553db0146"))?.model)
        assertEquals("SM-R640", SamsungModels.identify("Galaxy Buds4 Pro (A1B2)", listOf("d908aab5-7a90-4cbe-8641-86a553db0169"))?.model)
        assertEquals("SM-U600", SamsungModels.identify("Buds", listOf(sppNew, "d908aab5-7a90-4cbe-8641-86a553db016b"))?.model)
        // The id wins over the name.
        assertEquals("SM-R530", SamsungModels.identify("Galaxy Buds3 Pro (A1B2)", listOf(sppNew, "d908aab5-7a90-4cbe-8641-86a553db014d"))?.model)
        // Without an id, the name, when the device has the model's service or Android has no service list yet.
        assertEquals("SM-R190", SamsungModels.identify("Galaxy Buds Pro (A1B2)", listOf(spp, handsFree))?.model)
        assertEquals("SM-R190", SamsungModels.identify("Galaxy Buds Pro (A1B2)", emptyList())?.model)
        // Another brand's name holding a base name, without Samsung's service: not Galaxy Buds (a CMF Buds Pro 2).
        assertNull(SamsungModels.identify("CMF Buds Pro 2", listOf("aeac4a03-dff5-498f-843a-34487cf133eb", handsFree)))
        // The Buds2-and-later service with neither: Samsung's plugin's own guesses.
        assertEquals("SM-R510", SamsungModels.identify("Renamed", listOf(sppNew, handsFree, leAudio))?.model)
        assertEquals("SM-R177", SamsungModels.identify("Renamed", listOf(sppNew, handsFree))?.model)
        assertNull(SamsungModels.identify("Soundcore Space A40", listOf(spp, handsFree)))
    }

    @Test
    fun reportsMaskTheDigitsInTheName() {
        assertEquals("Galaxy Buds2 Pro (xxxx)", SamsungModels.maskedName("Galaxy Buds2 Pro (A1b2)"))
        assertEquals("Galaxy Buds3 (Kitchen)", SamsungModels.maskedName("Galaxy Buds3 (Kitchen)"))
    }

    @Test
    fun readsEveryStateInGalaxyBudsClientsTests() {
        assertEquals(7, cases.size)
        var checked = 0
        for (c in cases) {
            val model = SamsungModels.of(c.str("model"))!!
            val where = c.str("galaxybudsclient_test")
            val packet = SamsungPacket.parse(c.str("frame").hexBytes(), model.legacy)
            assertNotNull(packet, "frame and CRC: $where")
            assertEquals(SamsungMessage.EXTENDED_STATUS_UPDATED, packet!!.id)
            val status = SamsungStatus.parse(model, packet.payload)!!
            assertEquals(c.getValue("revision").jsonPrimitive.int, status.revision, where)
            for ((name, want) in c.getValue("expect").jsonObject) {
                val p = want as JsonPrimitive
                val have = status.values[name]
                val expected: Any = p.booleanOrNull ?: p.intOrNull ?: p.content
                // Fields GalaxyBudsClient leaves at their defaults are not sent by the model: absent here.
                if (have == null) {
                    assertTrue(expected == false || expected == 0 || (name == "detect_conversations" && expected == true), "$where $name = $expected")
                } else {
                    assertEquals(expected, have, "$where $name")
                    checked++
                }
            }
        }
        assertTrue(checked > 250, "values checked: $checked")
    }

    @Test
    fun eqPresetsAndTheCustomCurve() {
        val pro2 = SamsungModels.of("SM-R510")!!
        val pro3 = SamsungModels.of("SM-R630")!!
        val buds = SamsungModels.of("SM-R170")!!
        // GalaxyBudsClient's SetEqualizerEncoder: off is 0, a preset its number + 1; the 2019 Buds: on, preset + 5.
        assertEquals(SamsungPacket(SamsungMessage.EQUALIZER, byteArrayOf(3)), SamsungEq.preset(pro2, "dynamic"))
        assertEquals(SamsungPacket(SamsungMessage.EQUALIZER, byteArrayOf(0)), SamsungEq.preset(pro2, SamsungEq.NORMAL))
        assertEquals(SamsungPacket(SamsungMessage.EQUALIZER, byteArrayOf(1, 6)), SamsungEq.preset(buds, "soft"))
        assertNull(SamsungEq.custom(pro2, List(9) { 0 }), "the Buds2 Pro have no custom curve")
        val custom = SamsungEq.custom(pro3, listOf(-10, -2, 0, 1, 2, 3, 4, 5, 12))!!
        assertEquals(
            listOf(
                SamsungPacket(SamsungMessage.CUSTOM_EQUALIZE_SEND, byteArrayOf(9, -10, -2, 0, 1, 2, 3, 4, 5, 10)),
                SamsungPacket(SamsungMessage.EQUALIZER, byteArrayOf(7)),
            ),
            custom,
        )
        fun reported(model: String) = cases.first { it.str("model") == model }.let { c ->
            val m = SamsungModels.of(model)!!
            SamsungEq.reported(SamsungStatus.parse(m, SamsungPacket.parse(c.str("frame").hexBytes(), m.legacy)!!.payload)!!)
        }
        assertEquals("bass_boost", reported("SM-R510"))
        assertEquals("dynamic", reported("SM-R170"), "the 2019 Buds report 7: dynamic without Dolby")
    }

    @Test
    fun aShortStateKeepsWhatItHolds() {
        val c = cases.first { it.str("model") == "SM-R510" }
        val model = SamsungModels.of("SM-R510")!!
        val payload = SamsungPacket.parse(c.str("frame").hexBytes())!!.payload
        val short = SamsungStatus.parse(model, payload.copyOfRange(0, 30))!!
        assertEquals(100, short.int("battery_l"))
        assertNull(short.flag("side_tone_enabled"))
        assertArrayEquals(payload, SamsungPacket.parse(c.str("frame").hexBytes())!!.payload)
    }
}

/** The session on GalaxyBudsClient's recorded Buds2 Pro state: what the screens show and what each change sends. */
class SamsungSessionTest {
    private val cases = vectors.getValue("galaxybudsclient_extended_status").jsonArray.map { it.jsonObject }

    private fun connected(model: String): SamsungSession {
        val m = SamsungModels.of(model)!!
        val frame = cases.first { it.str("model") == model }.str("frame").hexBytes()
        return SamsungSession(m).also { assertTrue(it.receive(SamsungPacket.parse(frame, m.legacy)!!)) }
    }

    private fun SamsungSession.setting(key: String): Setting? = groups.flatMap { it.settings }.firstOrNull { it.key == key }

    private fun packet(id: Int, vararg values: Int) = SamsungPacket(id, ByteArray(values.size) { values[it].toByte() })

    @Test
    fun connectingAsksForTheVersionAndSendsTheManagerInfo() {
        val s = connected("SM-R510")
        assertEquals(listOf(packet(SamsungMessage.MANAGER_INFO, 1, 2, 34), packet(SamsungMessage.VERSION_INFO)), s.takeOutbox())
        assertEquals(emptyList<SamsungPacket>(), s.takeOutbox())
        assertEquals(100, s.state.batteryLeft)
        assertEquals(100, s.state.batteryRight)
        assertEquals(88, s.state.batteryCase)
        assertEquals(null, s.state.firmware, "no firmware before the version reply")
        assertTrue(s.state.hasFitTest)
        assertTrue(connected("SM-R400N").takeOutbox().any { it.id == SamsungMessage.VERSION_INFO }, "the Buds FE still answer it")
    }

    @Test
    fun settingsFromTheState() {
        val s = connected("SM-R510")
        assertEquals(Setting.Level("ambient_volume", 3, 0, 3), s.setting("ambient_volume"), "extra loud adds a step")
        assertEquals(Setting.Toggle("extra_loud", true), s.setting("extra_loud"))
        assertEquals(Setting.Choice("voice_detect_wait", "10", listOf("5", "10", "15")), s.setting("voice_detect_wait"))
        assertEquals(Setting.Choice("hold_left", "noise_control", listOf("voice_assistant", "noise_control", "volume", "spotify")), s.setting("hold_left"))
        assertEquals("anc_off", (s.setting("hold_cycle_right") as Setting.Choice).value)
        assertEquals("ambient_off", (s.setting("hold_cycle_left") as Setting.Choice).value)
        assertEquals(Setting.Level("balance", 16, 0, 32), s.setting("balance"))
        assertEquals(Setting.Toggle("seamless_connection", true), s.setting("seamless_connection"))
        assertNull(s.setting("anc_level"), "the Buds2 Pro have one noise cancelling level")
        assertNull(s.setting("ambient_sound"), "noise control goes through the shared screens")
        val pro = connected("SM-R190")
        assertEquals(Setting.Choice("anc_level", "high", listOf("low", "high")), pro.setting("anc_level"))
        val buds = connected("SM-R170")
        assertEquals(Setting.Toggle("ambient_sound", false), buds.setting("ambient_sound"))
        assertEquals(Setting.Level("ambient_volume", 2, 0, 4), buds.setting("ambient_volume"))
        assertEquals("volume", (buds.setting("hold_left") as Setting.Choice).value, "the 2019 Buds' own touch map")
    }

    @Test
    fun eachChangeSendsGalaxyBudsClientsMessage() {
        val s = connected("SM-R510")
        val anc = io.github.omegadestroyer.pairfree.protocol.Command.ANC_MODE
        val ancOn = anc.labels.entries.first { it.value == "anc" }.key
        assertEquals(listOf(packet(SamsungMessage.NOISE_CONTROLS, 1)), s.set(anc, ancOn))
        assertEquals(ancOn, s.state.number(anc))
        assertEquals(listOf(packet(SamsungMessage.SET_SEAMLESS_CONNECTION, 1)), s.change(SettingChange.Toggle("seamless_connection", false)), "0 is on")
        assertEquals(listOf(packet(SamsungMessage.SET_TOUCHPAD_OPTION, 2, 1)), s.change(SettingChange.Choice("hold_right", "voice_assistant")))
        assertEquals(
            listOf(packet(SamsungMessage.LOCK_TOUCHPAD, 1, 1, 1, 0, 1, 1, 1)),
            s.change(SettingChange.Toggle("touch_triple", false)),
            "touch on, then each gesture; the calls ones last",
        )
        assertEquals(
            listOf(packet(SamsungMessage.EXTRA_HIGH_AMBIENT, 0), packet(SamsungMessage.AMBIENT_VOLUME, 2)),
            s.change(SettingChange.Toggle("extra_loud", false)),
        )
        assertEquals(listOf(packet(SamsungMessage.SET_HEARING_ENHANCEMENTS, 20)), s.change(SettingChange.Level("balance", 20)))
        assertEquals(listOf(packet(SamsungMessage.SET_DETECT_CONVERSATIONS_DURATION, 2)), s.change(SettingChange.Choice("voice_detect_wait", "15")))
        assertEquals(
            listOf(packet(SamsungMessage.CUSTOMIZE_AMBIENT_SOUND, 1, 1, 2, 3)),
            s.change(SettingChange.Level("ambient_custom_left", 1)),
            "on, left, right, tone",
        )
        assertEquals(listOf(packet(SamsungMessage.FIND_MY_EARBUDS_ON_WEARING_START)), s.change(SettingChange.Press("find")))
        assertEquals(listOf(packet(SamsungMessage.MUTE_EARBUD, 1, 0)), s.change(SettingChange.Toggle("find_mute_left", true)))
        assertEquals(listOf(packet(SamsungMessage.FIND_MY_EARBUDS_STOP)), s.change(SettingChange.Press("find")))
        assertNull(s.change(SettingChange.Toggle("find_mute_left", true)), "only while finding")
        assertNull(s.change(SettingChange.Choice("hold_left", "custom")), "set in Samsung's app only")
    }

    @Test
    fun theTouchAndHoldCycle() {
        val s = connected("SM-R510")
        // GalaxyBudsClient's TouchAndHoldNoiseControls before the Buds3: per side ANC, ambient, off; left side first.
        assertEquals(
            listOf(packet(SamsungMessage.SET_TOUCH_AND_HOLD_NOISE_CONTROLS, 1, 1, 0, 1, 0, 1)),
            s.change(SettingChange.Choice("hold_cycle_left", "anc_ambient")),
        )
        assertEquals("anc_ambient", (s.setting("hold_cycle_left") as Setting.Choice).value)
    }

    @Test
    fun acknowledgementsAndUpdatesFollowTheEarbuds() {
        val s = connected("SM-R510")
        val anc = io.github.omegadestroyer.pairfree.protocol.Command.ANC_MODE
        s.receive(packet(SamsungMessage.UNIVERSAL_ACK, SamsungMessage.NOISE_CONTROLS, 2))
        assertEquals(anc.labels.entries.first { it.value == "transparency" }.key, s.state.number(anc))
        s.receive(packet(SamsungMessage.UNIVERSAL_ACK, SamsungMessage.SET_SEAMLESS_CONNECTION, 1))
        assertEquals(Setting.Toggle("seamless_connection", false), s.setting("seamless_connection"))
        s.receive(packet(SamsungMessage.AMBIENT_VOLUME, 1))
        assertEquals(1, (s.setting("ambient_volume") as Setting.Level).value)
        // Status update: revision, batteries, linked, main, placements, case, charging (left charging).
        s.receive(packet(SamsungMessage.STATUS_UPDATED, 13, 55, 60, 1, 0, 0x33, 70, 0x10))
        assertEquals(55, s.state.batteryLeft)
        assertEquals(70, s.state.batteryCase)
        s.receive(packet(SamsungMessage.CHECK_THE_FIT_OF_EARBUDS_RESULT, 1, 0))
        assertEquals(io.github.omegadestroyer.pairfree.protocol.FitResult.Fit.GOOD, s.state.fit?.left)
        assertEquals(io.github.omegadestroyer.pairfree.protocol.FitResult.Fit.NOT_FIT, s.state.fit?.right)
    }

    @Test
    fun theVersionReply() {
        val s = connected("SM-R177")
        // Hardware revisions, then per earbud: variant, year and month, release; then the touch firmware.
        s.receive(packet(SamsungMessage.VERSION_INFO, 0x11, 0x11, 1, 0x95, 3, 1, 0x95, 3, 0, 0))
        assertEquals("R177XXU0AXF3", s.state.firmware?.version)
    }

    @Test
    fun framesNeverCarryAForbiddenMessage() {
        val s = connected("SM-R510")
        val frames = s.frames(listOf(packet(SamsungMessage.RESET), packet(SamsungMessage.FIND_MY_EARBUDS_STOP)))
        assertEquals(1, frames.size)
        assertEquals(SamsungMessage.FIND_MY_EARBUDS_STOP, SamsungPacket.parse(frames.single())!!.id)
    }
}
