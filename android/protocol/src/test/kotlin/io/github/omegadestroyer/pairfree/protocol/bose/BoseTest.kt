package io.github.omegadestroyer.pairfree.protocol.bose

import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun load(name: String): JsonObject = Json.parseToJsonElement(repoFile("data/bose/$name").readText()).jsonObject

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

private fun status(function: Int, hex: String) = BosePacket(function, BoseOperator.STATUS, hex.hexBytes())

private fun setGet(function: Int, vararg bytes: Int) = BosePacket.setGet(function, *bytes)

/** Packets, the tables and a session, against replies bosectl read from real Bose devices. */
class BoseTest {
    private val vectors = load("test_vectors.json")

    /** A session that has read the QC Ultra Headphones (2nd Gen) as bosectl captured them. */
    private fun ultra2(): BoseSession = BoseSession().also { s ->
        for (r in vectors.getValue("qc_ultra2_headphones").jsonArray.map { it.jsonObject }) {
            val (block, function) = r.str("function").split(".").map(String::toInt)
            s.receive(status(BoseFunction.of(block, function), r.str("payload")))
        }
    }

    private fun setting(s: BoseSession, key: String) = s.groups.flatMap { it.settings }.firstOrNull { it.key == key }

    @Test
    fun requestsAreBuiltAsBosectlSendsThem() {
        val built = listOf(
            BosePacket.get(BoseFunction.STATUS_BATTERY_LEVEL),
            BosePacket.start(BoseFunction.AUDIO_MODES_CURRENT_MODE, 0, 0),
            BosePacket.setGet(BoseFunction.AUDIO_MODES_SETTINGS_CONFIG, 5, 0, 0, 0, 1),
        )
        val expected = vectors.getValue("requests").jsonArray.map { it.jsonObject.str("bytes").hexBytes() }
        assertEquals(expected.size, built.size)
        built.zip(expected).forEach { (p, bytes) -> assertArrayEquals(bytes, p.bytes(), p.toString()) }
    }

    @Test
    fun theParserSplitsByLength() {
        val a = status(BoseFunction.STATUS_BATTERY_LEVEL, "50ffff00").bytes()
        val b = status(BoseFunction.SETTINGS_MULTIPOINT, "07").bytes()
        val p = BoseParser()
        assertTrue(p.feed(a.copyOfRange(0, 5)).isEmpty())
        val got = p.feed(a.copyOfRange(5, a.size) + b)
        assertEquals(listOf(BoseFunction.STATUS_BATTERY_LEVEL, BoseFunction.SETTINGS_MULTIPOINT), got.map { it.function })
        assertArrayEquals("50ffff00".hexBytes(), got[0].payload)
        assertEquals(0, p.pending)
        // An operator above 7 means the stream is out of step: what is kept is dropped.
        assertTrue(p.feed(byteArrayOf(1, 2, 0x0F, 0)).isEmpty())
        assertEquals(0, p.pending)
    }

    @Test
    fun theGeneratedTableMatchesTheData() {
        val blocks = load("commands.json").getValue("blocks").jsonArray.map { it.jsonObject }
        val names = blocks.flatMap { b ->
            b.getValue("functions").jsonArray.map { f ->
                BoseFunction.of(b.getValue("block").jsonPrimitive.int, f.jsonObject.getValue("function").jsonPrimitive.int) to f.jsonObject.str("name")
            }
        }.toMap()
        assertEquals(names, BoseFunction.NAMES)
        assertEquals(0x0105, BoseFunction.SETTINGS_CNC)
        assertEquals(0x1F03, BoseFunction.AUDIO_MODES_CURRENT_MODE)
        assertTrue(BoseFunction.of(3, 1) in BoseFunction.NEVER_SEND, "firmware update")
        assertTrue(BoseFunction.CONTROL_FACTORY_DEFAULT in BoseFunction.NEVER_SEND)
        assertFalse(BoseSession().allowed(BosePacket.start(BoseFunction.CONTROL_FACTORY_DEFAULT)))
        assertTrue(BoseSession().allowed(BosePacket.get(BoseFunction.SETTINGS_CNC)))
    }

    @Test
    fun modelsByProductIdAndDevicesByServiceOrName() {
        val devices = load("devices.json").getValue("devices").jsonArray.map { it.jsonObject }
        assertEquals(devices.map { it.str("product_id").toInt(16) }, BoseModels.ALL.map { it.productId })
        assertEquals("Bose QC Ultra 2 HP", BoseModels.of(0x4082)?.name)
        assertEquals(Form.EARBUDS, BoseModels.of(0x4062)?.form)
        assertTrue(BoseModels.isBose("Fargo", listOf("0000febe-0000-1000-8000-00805f9b34fb")), "Bose's own service, whatever the name")
        assertTrue(BoseModels.isBose("Fargo", listOf("0000FDD2-0000-1000-8000-00805F9B34FB")))
        assertTrue(BoseModels.isBose("Bose QC45", emptyList()))
        assertFalse(BoseModels.isBose("WH-1000XM4", listOf("00001101-0000-1000-8000-00805f9b34fb")))
    }

    @Test
    fun startsWithTheReadsBosectlSends() {
        val start = BoseSession().start()
        assertEquals(BosePacket.get(BoseFunction.PRODUCT_INFO_BMAP_VERSION), start.first(), "the version first, as the QC35 and QC45 need")
        assertTrue(BosePacket.start(BoseFunction.AUDIO_MODES_GET_ALL) in start)
        assertTrue(start.all { BoseSession().allowed(it) })
    }

    @Test
    fun readsTheUltra2AsCaptured() {
        val s = ultra2()
        assertEquals(0x4082, s.productId)
        assertEquals("Bose QC Ultra 2 HP", s.model?.name)
        assertEquals("Bose QC Ultra 2 HP", s.state.firmware?.code)
        assertEquals("8.2.20+g34cf029", s.state.firmware?.version)
        assertEquals(80, s.state.batteryLeft, "one battery, component 0")
        assertNull(s.state.batteryRight)
        // Voice prompts 41: off, default, and not switchable (bit 7), as Bose's app reads it.
        assertNull(setting(s, "voice_prompts"))
        assertEquals(Setting.Level("noise_level", 0, 0, 10), setting(s, "noise_level"))
        assertEquals(listOf(0, -2, -6), listOf("eq_bass", "eq_mid", "eq_treble").map { (setting(s, it) as Setting.Level).value })
        assertEquals(-10, (setting(s, "eq_bass") as Setting.Level).min)
        // Multipoint 07: on, supported, can be turned off (Bose's bits; bosectl reads bit 1).
        assertEquals(Setting.Toggle("multipoint", on = true, enabled = true), setting(s, "multipoint"))
        assertEquals("medium", (setting(s, "sidetone_level") as Setting.Choice).value)
        assertEquals(listOf("off", "low", "medium", "high"), (setting(s, "sidetone_level") as Setting.Choice).options)
        assertEquals(true, (setting(s, "pause_taken_off") as Setting.Toggle).on)
        assertEquals(true, (setting(s, "auto_answer") as Setting.Toggle).on)
        assertTrue(s.replies.keys.none { it.startsWith("31.6") })
    }

    @Test
    fun earbudsReportEachSide() {
        val s = BoseSession()
        s.receive(status(BoseFunction.STATUS_BATTERY_LEVEL, vectors.str("qc_ultra2_earbuds_battery")))
        assertEquals(60, s.state.batteryRight)
        assertEquals(60, s.state.batteryLeft)
        assertEquals(80, s.state.batteryCase)
    }

    @Test
    fun writesAreSetGets() {
        val s = ultra2()
        assertEquals(listOf(setGet(BoseFunction.SETTINGS_RANGE_CONTROL, 0xFE, 1)), s.change(SettingChange.Level("eq_mid", -2)))
        assertEquals(listOf(setGet(BoseFunction.SETTINGS_RANGE_CONTROL, 4, 0)), s.change(SettingChange.Level("eq_bass", 4)))
        assertEquals(4, (setting(s, "eq_bass") as Setting.Level).value)
        assertEquals(listOf(setGet(BoseFunction.SETTINGS_MULTIPOINT, 0)), s.change(SettingChange.Toggle("multipoint", false)))
        assertEquals(false, (setting(s, "multipoint") as Setting.Toggle).on)
        assertEquals(listOf(setGet(BoseFunction.SETTINGS_AUTO_PLAY_PAUSE, 0)), s.change(SettingChange.Toggle("pause_taken_off", false)))
        // Sidetone: kept (bit 0 of the reply), then the level; low is 3.
        assertEquals(listOf(setGet(BoseFunction.SETTINGS_SIDETONE, 1, 3)), s.change(SettingChange.Choice("sidetone_level", "low")))
        // Without AudioModes' settings, the level goes through Settings' CNC: the step and whether it is on.
        assertEquals(listOf(setGet(BoseFunction.SETTINGS_CNC, 7, 1)), s.change(SettingChange.Level("noise_level", 7)))
        // With them (31.10), there, the rest as the device reported.
        s.receive(status(BoseFunction.AUDIO_MODES_SETTINGS_CONFIG, "0000000001"))
        assertEquals(listOf(setGet(BoseFunction.AUDIO_MODES_SETTINGS_CONFIG, 3, 0, 0, 0, 1)), s.change(SettingChange.Level("noise_level", 3)))
    }

    @Test
    fun theButtonAsBosesAppReadsItsMasks() {
        // bosectl's QC Ultra 2: the shortcut (80), long press (09), disabled (0E), then the mask 00 09 40 02. Read as
        // Bose's app reads it (bit 0 in the last byte): voice assistant, disabled, Spotify Tap and immersive audio;
        // the device's own action is among them, which bosectl's reading (switch device, track back, 22, 25) misses.
        val s = ultra2()
        assertEquals(
            Setting.Choice("bose_button_128", "disabled", listOf("voice_assistant", "disabled", "spatial_audio")),
            setting(s, "bose_button_128"),
            "Spotify Tap needs Spotify set up, so it is not offered",
        )
        assertEquals(SettingTab.CONTROLS, s.groups.single { g -> g.settings.any { it.key == "bose_button_128" } }.tab)
        // bosectl's notes: SETGET with the button, its gesture and the action.
        assertEquals(listOf(setGet(BoseFunction.SETTINGS_BUTTONS, 0x80, 0x09, 1)), s.change(SettingChange.Choice("bose_button_128", "voice_assistant")))
        assertEquals("voice_assistant", (setting(s, "bose_button_128") as Setting.Choice).value)
        // An action the device cannot take now (the second mask) is not offered.
        s.receive(status(BoseFunction.SETTINGS_BUTTONS, "80090e0009400200000002"))
        assertEquals(listOf("disabled", "spatial_audio"), (setting(s, "bose_button_128") as Setting.Choice).options)
    }

    @Test
    fun standbyTimerAndSpatialAudio() {
        val s = BoseSession()
        assertTrue(BosePacket.get(BoseFunction.SETTINGS_STANDBY_TIMER) in s.start())
        assertTrue(BosePacket.get(BoseFunction.AUDIO_MANAGEMENT_SPATIAL_AUDIO_MODE) in s.start())
        // Minutes, as based-connect writes them to the QC35 (0 is never).
        s.receive(status(BoseFunction.SETTINGS_STANDBY_TIMER, "14"))
        assertEquals(Setting.Choice("auto_off", "20", listOf("5", "20", "40", "60", "180", "never")), setting(s, "auto_off"))
        assertEquals(listOf(setGet(BoseFunction.SETTINGS_STANDBY_TIMER, 0)), s.change(SettingChange.Choice("auto_off", "never")))
        assertEquals("never", (setting(s, "auto_off") as Setting.Choice).value)
        // Bose's app reads three bytes as the first and the third: 24 hours here, kept as a choice.
        s.receive(status(BoseFunction.SETTINGS_STANDBY_TIMER, "a00005"))
        assertEquals("1440", (setting(s, "auto_off") as Setting.Choice).value)
        assertEquals(listOf(setGet(BoseFunction.SETTINGS_STANDBY_TIMER, 0xA0, 0x05)), s.change(SettingChange.Choice("auto_off", "1440")))

        // Spatial audio (5.15): 0 off, 1 fixed to the room, 2 to the head.
        s.receive(status(BoseFunction.AUDIO_MANAGEMENT_SPATIAL_AUDIO_MODE, "01"))
        assertEquals(Setting.Choice("spatial_audio_mode", "room", listOf("off", "room", "head")), setting(s, "spatial_audio_mode"))
        assertEquals(listOf(setGet(BoseFunction.AUDIO_MANAGEMENT_SPATIAL_AUDIO_MODE, 2)), s.change(SettingChange.Choice("spatial_audio_mode", "head")))
    }

    @Test
    fun aRefusedWriteIsReadAgain() {
        val s = ultra2()
        s.change(SettingChange.Toggle("multipoint", false))
        // Error 5: the operator needs Bose's sign-in on this model.
        s.receive(BosePacket(BoseFunction.SETTINGS_MULTIPOINT, BoseOperator.ERROR, byteArrayOf(5)))
        assertEquals(listOf(BosePacket.get(BoseFunction.SETTINGS_MULTIPOINT)), s.takeOutbox())
        s.receive(status(BoseFunction.SETTINGS_MULTIPOINT, "07"))
        assertEquals(true, (setting(s, "multipoint") as Setting.Toggle).on)
        // An error to a read only means the device does not have it: nothing to read again.
        s.receive(BosePacket(BoseFunction.STATUS_IN_EAR, BoseOperator.ERROR, byteArrayOf(4)))
        assertTrue(s.takeOutbox().isEmpty())
    }

    @Test
    fun modesFromTheirConfigurations() {
        val s = ultra2()
        fun config(index: Int, prompt: Int, userSlot: Boolean, configured: Boolean, name: String): BosePacket {
            val p = ByteArray(48)
            p[0] = index.toByte()
            p[2] = prompt.toByte()
            p[3] = (if (userSlot) 1 else 0).toByte()
            p[4] = (if (configured) 1 else 0).toByte()
            name.encodeToByteArray().copyInto(p, 6)
            return BosePacket(BoseFunction.AUDIO_MODES_MODE_CONFIG, BoseOperator.STATUS, p)
        }
        s.receive(config(0, 1, false, false, "Quiet"))
        s.receive(config(1, 2, false, false, "Aware"))
        s.receive(config(4, 10, true, true, "Home"))
        s.receive(config(5, 0, true, false, "None"))
        s.receive(status(BoseFunction.AUDIO_MODES_CURRENT_MODE, "01"))
        val mode = setting(s, "audio_mode") as Setting.Choice
        assertEquals(listOf("Quiet", "Aware", "Home"), mode.options, "an empty user slot is left out")
        assertEquals("Aware", mode.value)
        assertEquals(listOf(BosePacket.start(BoseFunction.AUDIO_MODES_CURRENT_MODE, 4, 0)), s.change(SettingChange.Choice("audio_mode", "Home")))
        assertEquals("Home", (setting(s, "audio_mode") as Setting.Choice).value)
        // The device answers the switch with the mode it took.
        s.receive(BosePacket(BoseFunction.AUDIO_MODES_CURRENT_MODE, BoseOperator.RESULT, byteArrayOf(0)))
        assertEquals("Quiet", (setting(s, "audio_mode") as Setting.Choice).value)
    }

    @Test
    fun theQc35sNoiseCancellingIsAChoice() {
        val s = BoseSession()
        s.receive(status(BoseFunction.SETTINGS_ANR, "0100"))
        assertEquals(Setting.Choice("anr", "high", listOf("off", "low", "high", "wind")), setting(s, "anr"))
        assertEquals(listOf(setGet(BoseFunction.SETTINGS_ANR, 2)), s.change(SettingChange.Choice("anr", "wind")))
    }
}
