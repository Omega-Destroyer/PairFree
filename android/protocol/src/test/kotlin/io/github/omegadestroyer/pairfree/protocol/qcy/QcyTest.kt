package io.github.omegadestroyer.pairfree.protocol.qcy

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.GattEnvelope
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

private fun load(name: String): JsonObject = Json.parseToJsonElement(repoFile("data/qcy/$name").readText()).jsonObject

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

private fun hex(bytes: ByteArray) = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

/** A report on the settings characteristic. */
private fun settings(hex: String) = GattEnvelope.received(QcyWire.SETTINGS_SHORT, hex.hexBytes())

/** The packet a session's single message writes to the command characteristic. */
private fun packet(messages: List<ByteArray>?): String {
    val out = GattEnvelope.out(messages!!.single())!!
    assertEquals(GattEnvelope.WRITE, out.operation)
    assertEquals(QcyWire.COMMAND_SHORT, out.short)
    return hex(out.value)
}

/** QCY's packets, adverts, model table and a session, against OpenQCY's MeloBuds N70 transcript and PeatsAudio's Capsule3 Pro examples. */
class QcyTest {
    private val vectors = load("test_vectors.json")
    private val n70 = vectors.getValue("n70").jsonObject

    private fun n70(): QcySession = QcySession(QcyModels.of(n70.getValue("vendor_id").jsonPrimitive.int))

    private fun setting(s: QcySession, key: String) = s.groups.flatMap { it.settings }.firstOrNull { it.key == key }

    @Test
    fun requestsAndRepliesAsTheN70SentThem() {
        for (r in n70.getValue("requests").jsonArray.map { it.jsonObject }) {
            assertArrayEquals(r.str("bytes").hexBytes(), QcyPacket.request(r.str("item").toInt(16)), r.str("what"))
        }
        for (r in n70.getValue("replies").jsonArray.map { it.jsonObject }) {
            val e = QcyPacket.parse(r.str("bytes").hexBytes()).single()
            assertEquals(r.str("item").toInt(16), e.item, r.str("what"))
            assertEquals(r.str("value"), hex(e.value), r.str("what"))
        }
        // Several items in one packet, and a value that runs past the end stops the list.
        assertEquals(listOf(QcyItem.GAME_MODE, QcyItem.LDAC), QcyPacket.parse("ff060901022301 01".replace(" ", "").hexBytes()).map { it.item })
        assertEquals(1, QcyPacket.parse("ff0609010223 05 01".replace(" ", "").hexBytes()).size)
    }

    @Test
    fun noiseScenesAsOpenQcyWroteThem() {
        val s = n70()
        val noisy = n70.getValue("replies").jsonArray.map { it.jsonObject }.first { it.str("item") == "17" }.str("bytes")
        val writes = n70.getValue("writes").jsonArray.map { it.jsonObject.str("bytes") }
        s.receive(settings(noisy))
        assertEquals(ancNumber("anc"), s.state.number(Command.ANC_MODE))
        val scene = setting(s, "anc_scene") as Setting.Choice
        assertEquals("noisy", scene.value)
        assertEquals(listOf("adaptive", "indoor", "commuting", "noisy", "anti_wind"), scene.options)
        assertEquals(Setting.Level("anc_scene_level", 3, 1, 3), setting(s, "anc_scene_level"))
        // From the noisy scene at value 2, each scene keeps the strength where it has one.
        for ((option, expected) in listOf("indoor" to 1, "commuting" to 2, "noisy" to 3, "anti_wind" to 4, "adaptive" to 0)) {
            s.receive(settings(noisy))
            assertEquals(writes[expected], packet(s.change(SettingChange.Choice("anc_scene", option))), option)
        }
        assertEquals(GattEnvelope.write(QcyWire.COMMAND_SHORT, QcyPacket.request(QcyItem.ANC_SETTING)).toList(), s.takeReadBacks().single().toList())
        // Off, then back to cancelling at the scene last used.
        assertEquals("ff051703020000", packet(s.set(Command.ANC_MODE, ancNumber("off"))))
        assertEquals("ff051703010500", packet(s.set(Command.ANC_MODE, ancNumber("anc"))))
        // Transparency has seven levels on the N70; QCY's app sends scene 1 at value 0 as scene 2.
        assertEquals("ff051703030104", packet(s.set(Command.ANC_MODE, ancNumber("transparency"))))
        assertEquals(Setting.Level("transparency_strength", 5, 1, 7), setting(s, "transparency_strength"))
        assertEquals("ff051703030200", packet(s.change(SettingChange.Level("transparency_strength", 1))))
    }

    @Test
    fun switchesAndWearing() {
        val s = n70()
        for (r in n70.getValue("replies").jsonArray.map { it.jsonObject }) s.receive(settings(r.str("bytes")))
        assertEquals(false, s.state.switch(Command.GAME_MODE))
        assertEquals(true, s.state.switch(Command.LDAC))
        assertEquals(true, s.state.switch(Command.MULTIPOINT))
        assertEquals(false, s.state.switch(Command.IN_EAR_DETECTION))
        assertEquals(Setting.Toggle("sleep_mode", false), setting(s, "sleep_mode"))
        assertEquals(Setting.Toggle("wind_detection", true), setting(s, "wind_detection"))
        assertEquals("ff03090101", packet(s.set(Command.GAME_MODE, 1)))
        assertEquals("ff03230102", packet(s.set(Command.LDAC, 0)))
        // Wearing detection keeps the music and noise bytes it reported.
        val writes = n70.getValue("writes").jsonArray.map { it.jsonObject.str("bytes") }
        assertEquals(writes[5], packet(s.set(Command.IN_EAR_DETECTION, 1)))
        assertEquals(writes[6], packet(s.set(Command.IN_EAR_DETECTION, 0)))
        assertEquals("ff03100101", packet(s.change(SettingChange.Toggle("sleep_mode", true))))
    }

    @Test
    fun theSessionAsksForWhatTheModelHas() {
        val start = n70().start()
        val reads = start.map { GattEnvelope.out(it)!! }.filter { it.operation == GattEnvelope.READ }.map { it.short }
        assertEquals(listOf(QcyWire.VERSION_SHORT, QcyWire.BATTERY_SHORT, QcyWire.SETTINGS_SHORT, QcyWire.KEYS_SHORT), reads)
        val asked = start.map { GattEnvelope.out(it)!! }.filter { it.operation == GattEnvelope.WRITE }.map { QcyPacket.parse(it.value).single().u8(0) }
        assertEquals(listOf(QcyItem.ANC_SETTING, QcyItem.GAME_MODE, QcyItem.WIND_DETECTION, QcyItem.SLEEP_MODE, QcyItem.LDAC, QcyItem.MULTIPOINT, QcyItem.BALANCE), asked)
        val s = n70()
        assertTrue(start.all(s::allowed))
        assertFalse(s.allowed(GattEnvelope.write(QcyWire.COMMAND_SHORT, QcyPacket.of(QcyItem.FACTORY_RESET))))
        assertFalse(s.allowed(GattEnvelope.write(QcyWire.COMMAND_SHORT, QcyPacket.of(QcyItem.PAIR_NAME, 0x41))))
        assertFalse(s.allowed(GattEnvelope.write(QcyWire.SETTINGS_SHORT, byteArrayOf(1))))
    }

    @Test
    fun modelsWithTwoOfTheThreeModes() {
        // The Era 5 has transparency and off: a switch, and no shared noise control.
        val era = QcySession(QcyModels.ALL.first { it.name == "QCY Era 5" })
        era.receive(settings("ff051703020000"))
        assertNull(era.state.values[Command.ANC_MODE])
        assertEquals(Setting.Toggle("ambient_sound", false), setting(era, "ambient_sound"))
        assertEquals("ff051703030104", packet(era.change(SettingChange.Toggle("ambient_sound", true))))
        // Adaptive noise cancelling and off: a noise cancelling switch.
        val adaptive = QcySession(QcyModels.ALL.first { m -> m.anc.size == 2 && m.mode("cancelling") != null })
        adaptive.receive(settings("ff051703010500"))
        assertEquals(Setting.Toggle("anc", true), setting(adaptive, "anc"))
        assertEquals("ff051703020000", packet(adaptive.change(SettingChange.Toggle("anc", false))))
    }

    @Test
    fun anUnknownModelWithOneByteNoise() {
        // The Capsule3 Pro: QCY-built, reporting the older one-byte noise mode.
        val s = QcySession(null)
        s.receive(settings("ff030c0102"))
        assertEquals(ancNumber("off"), s.state.number(Command.ANC_MODE))
        val on = vectors.getValue("capsule3").jsonArray.map { it.jsonObject.str("bytes") }
        assertEquals(on[0], packet(s.set(Command.ANC_MODE, ancNumber("anc"))))
        assertEquals(on[1], hex(QcyPacket.request(QcyItem.MULTIPOINT)))
    }

    @Test
    fun batteryVersionAndAdvert() {
        val a = vectors.getValue("advert").jsonObject
        val advert = QcyAdvert.parse(a.str("bytes").hexBytes())!!
        assertEquals(a.getValue("vendor_id").jsonPrimitive.int, advert.vendorId)
        assertEquals(a.str("classic"), advert.classicAddress)
        assertEquals(a.str("other"), advert.otherAddress)
        assertEquals(listOf(60, 96, 43), listOf(advert.left, advert.right, advert.case))
        assertTrue(advert.matches(a.str("other").lowercase()))
        assertNull(QcyAdvert.parse(ByteArray(10)))
        // The advert's battery shows before anything is read; the battery characteristic replaces it.
        val s = QcySession(QcyModels.of(advert.vendorId), advert)
        assertEquals(60, s.state.batteryLeft)
        s.receive(GattEnvelope.received(QcyWire.BATTERY_SHORT, "d00000".hexBytes()))
        assertEquals(80, s.state.batteryLeft)
        assertNull(s.state.batteryRight, "0 is unknown")
        s.receive(GattEnvelope.received(QcyWire.VERSION_SHORT, byteArrayOf(3, 0, 13)))
        assertEquals("5D45_3.0.13", (s.state.values[Command.FIRMWARE_VERSION] as Value.Text).text)
        s.receive(GattEnvelope.received(QcyWire.VERSION_SHORT, "QCYTS04_20240101_V1.2".encodeToByteArray()))
        assertEquals("QCYTS04_20240101_V1.2", s.firmware)
    }

    @Test
    fun touchControls() {
        val s = n70()
        // Single, double, triple and hold on each side: play/pause, next and previous, the assistant, volume.
        s.receive(GattEnvelope.received(QcyWire.KEYS_SHORT, "0101 0201 0303 0402 0504 0604 0905 0A06".replace(" ", "").hexBytes()))
        val left = s.groups.first { it.key == "touch_left" }
        assertEquals(SettingTab.CONTROLS, left.tab)
        val double = left.settings.first { it.key == "touch_double_left" } as Setting.Choice
        assertEquals("next", double.value)
        assertEquals(listOf("none", "play_pause", "previous", "next", "voice_assistant", "volume_up", "volume_down", "game_mode"), double.options)
        val write = GattEnvelope.out(s.change(SettingChange.Choice("touch_double_left", "previous"))!!.single())!!
        assertEquals(QcyWire.KEYS_SHORT, write.short)
        assertEquals("0101020103020402050406040905" + "0a06", hex(write.value))
        assertNotNull(s.groups.firstOrNull { it.key == "touch_right" })
    }

    @Test
    fun theModelTable() {
        val devices = load("devices.json").getValue("devices").jsonArray.map { it.jsonObject }
        assertEquals(devices.size, QcyModels.ALL.size)
        assertEquals(devices.size, QcyModels.ALL.map { it.vendorId }.toSet().size)
        val m = QcyModels.of(23877)!!
        assertEquals("QCY MeloBuds N70", m.name)
        assertEquals(QcyProtocol.QCY, m.protocol)
        assertEquals("wuqi", m.family)
        assertEquals(Form.HEADPHONES, QcyModels.ALL.first { it.name == "QCY H3 Pro" }.form)
        assertEquals(QcyProtocol.JIELI, QcyModels.ALL.first { it.name == "QCY H3 Pro" }.protocol)
        assertTrue(QcyModels.fromName("QCY-T13").size > 1, "several vendor ids share the T13's name")
        assertTrue(QcyModels.identify("QCY MeloBuds N70"))
        assertTrue(QcyModels.identify("QCY-T13"))
        assertFalse(QcyModels.identify("Galaxy Buds3"))
        assertEquals("QCY MeloBuds N70", QcyModels.model("5D45")?.names?.single())
    }

    private fun ancNumber(name: String): Int = Command.ANC_MODE.labels.entries.first { it.value == name }.key
}
