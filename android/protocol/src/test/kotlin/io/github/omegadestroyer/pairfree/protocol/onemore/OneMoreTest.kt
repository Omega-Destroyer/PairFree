package io.github.omegadestroyer.pairfree.protocol.onemore

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value
import io.github.omegadestroyer.pairfree.protocol.hexBytes
import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun load(name: String): JsonObject = Json.parseToJsonElement(repoFile("data/1more/$name").readText()).jsonObject

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

private fun hex(bytes: ByteArray) = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

/** A frame from the earbuds. */
private fun reply(command: Int, payload: String = "", sequence: Int = 0) = OneMoreFrame(command, payload.hexBytes(), sequence, OneMoreWire.REPLY)

private fun anc(name: String) = Command.ANC_MODE.labels.entries.first { it.value == name }.key

/** Frames against the ones 1MORE's app writes, the tables against the data, and sessions against replies built from the format. */
class OneMoreTest {
    private val aero = OneMoreModels.fromName("1MORE Aero")!!

    @Test
    fun framesAsTheAppWritesThem() {
        val sent = load("test_vectors.json").getValue("sent").jsonArray.map { it.jsonObject }
        assertTrue(sent.size >= 20)
        for (v in sent) {
            val frame = OneMoreFrame(v.str("command").toInt(16), v.str("payload").hexBytes(), v.getValue("sequence").jsonPrimitive.int)
            assertEquals(v.str("hex"), hex(frame.bytes()), v.str("command"))
        }
    }

    @Test
    fun tablesMatchTheData() {
        val commands = load("commands.json").getValue("commands").jsonArray.map { it.jsonObject }
        assertEquals(commands.size, OneMoreCommand.NAMES.size)
        val never = commands.filter { it["never_send"]?.jsonPrimitive?.boolean == true }.map { it.str("code").toInt(16) }.toSet()
        assertEquals(never, OneMoreCommand.NEVER_SEND)
        assertTrue(OneMoreCommand.HANDSHAKE in never && OneMoreCommand.DELETE_MUSIC in never && OneMoreCommand.TRANSFER_DATA in never)
        val devices = load("devices.json").getValue("devices").jsonArray.map { it.jsonObject }
        assertEquals(devices.size, OneMoreModels.ALL.size)
        assertEquals(devices.map { it.str("name") }.toSet().size, devices.size, "one entry per Bluetooth name")
        assertEquals(OneMoreWire.SERVICE_UUID, "00001101-0000-1000-8000-00805F9B34FB")
    }

    @Test
    fun modelsByName() {
        assertEquals("ES903", OneMoreModels.fromName(" 1more aero ")?.model)
        assertNull(OneMoreModels.fromName("1MORE Aero Pro"), "the whole name, as 1MORE's app matches it")
        // Tuya's AI earbuds are known but not controlled.
        assertNotNull(OneMoreModels.fromName("1MORE S52"))
        assertNull(OneMoreModels.identify("1MORE S52"))
        assertNull(OneMoreModels.identify("1MORE S22Ultra"))
        assertNotNull(OneMoreModels.identify("1MORE HQ51"))
        assertEquals(Form.HEADPHONES, OneMoreModels.fromName("1MORE HQ51")?.form)
        assertEquals("1MORE Aero", OneMoreModels.model("ES903")?.names?.single())
    }

    @Test
    fun parserSplitsByLength() {
        val a = reply(OneMoreCommand.GET_LISTEN_MODE, "07", 3).bytes()
        val b = reply(OneMoreCommand.GET_BINAURAL_INFO, "00010203500102035a28").bytes()
        val p = OneMoreParser()
        // Stray bytes first, then a frame split across reads, then one after it in the same read.
        assertTrue(p.feed(byteArrayOf(0x55, 0x01) + a.copyOfRange(0, 4)).isEmpty())
        val got = p.feed(a.copyOfRange(4, a.size) + b)
        assertEquals(listOf(OneMoreCommand.GET_LISTEN_MODE, OneMoreCommand.GET_BINAURAL_INFO), got.map { it.command })
        assertEquals(3, got[0].sequence)
        assertTrue(got.all { it.fromEarbuds })
        assertEquals(0, p.pending)
        // 1MORE's app never checks the XOR: a known command still counts without it, an unknown one does not.
        val bad = b.copyOf().also { it[8] = (it[8] + 1).toByte() }
        assertEquals(1, OneMoreParser().feed(bad).size)
        val unknown = reply(0x0042, "01").bytes().also { it[8] = (it[8] + 1).toByte() }
        assertTrue(OneMoreParser().feed(unknown).isEmpty())
    }

    @Test
    fun noiseControlOnTheAero() {
        val s = OneMoreSession(aero)
        val start = s.start().map { it.command }
        assertEquals(OneMoreCommand.GET_BINAURAL_INFO, start.first())
        assertTrue(OneMoreCommand.GET_LISTEN_MODE in start && OneMoreCommand.GET_ALL_CUSTOM in start && OneMoreCommand.FIND_DEVICE_SUPPORT in start)
        assertFalse(OneMoreCommand.GET_CONNECTION_MODE in start, "the Aero has no connection modes")
        assertTrue(s.start().all(s::allowed))
        // Adaptive cancelling: the shared control shows cancelling, the strength choice adaptive.
        assertTrue(s.receive(reply(OneMoreCommand.GET_LISTEN_MODE, "07")))
        assertEquals(anc("anc"), s.state.number(Command.ANC_MODE))
        val strength = s.groups.single { it.key == "noise" }.settings.single() as Setting.Choice
        assertEquals("noise_strength", strength.key)
        assertEquals("adaptive", strength.value)
        assertEquals(listOf("adaptive", "high", "low", "wind"), strength.options)
        // Transparency: plain transparency, as the Aero has no other choice made yet.
        val t = s.set(Command.ANC_MODE, anc("transparency"))!!.single()
        assertEquals(OneMoreCommand.SET_LISTEN_MODE, t.command)
        assertEquals("03", hex(t.payload))
        assertEquals(listOf(OneMoreCommand.GET_LISTEN_MODE), s.takeReadBacks().map { it.command })
        val kind = s.groups.single { it.key == "noise" }.settings.single() as Setting.Choice
        assertEquals("transparency_mode", kind.key)
        assertEquals(listOf("normal", "pass_through"), kind.options)
        assertEquals("05", hex(s.change(SettingChange.Choice("transparency_mode", "pass_through"))!!.single().payload))
        // Back to cancelling: the strength last used.
        assertEquals("07", hex(s.set(Command.ANC_MODE, anc("anc"))!!.single().payload))
        // A refused write is read again at once.
        s.receive(reply(OneMoreCommand.SET_LISTEN_MODE, "00"))
        assertEquals(listOf(OneMoreCommand.GET_LISTEN_MODE), s.takeOutbox().map { it.command })
        s.receive(reply(OneMoreCommand.SET_LISTEN_MODE, "01"))
        assertTrue(s.takeOutbox().isEmpty())
    }

    @Test
    fun batteryAndFirmware() {
        val s = OneMoreSession(aero)
        // Left master, left 1.0.16 at 80 %, right 1.0.16 at 90 %, case 40 %.
        s.receive(reply(OneMoreCommand.GET_BINAURAL_INFO, "00010010500100105a28"))
        assertEquals(listOf(80, 90, 40), listOf(s.state.batteryLeft, s.state.batteryRight, s.state.batteryCase))
        assertEquals("1.0.10", s.firmware)
        assertEquals("ES903_1.0.10", (s.state.values[Command.FIRMWARE_VERSION] as Value.Text).text)
        // Headphones: one battery.
        val hq = OneMoreSession(OneMoreModels.fromName("1MORE HQ51"))
        hq.receive(reply(OneMoreCommand.GET_BINAURAL_INFO, "00010203460000000000"))
        assertEquals(70, hq.state.batteryLeft)
        assertFalse(Command.BATTERY_RIGHT in hq.state.values)
        assertFalse(Command.BATTERY_CASE in hq.state.values)
        // The S11 has no case to report.
        val s11 = OneMoreSession(OneMoreModels.fromName("1MORE S11"))
        s11.receive(reply(OneMoreCommand.GET_BINAURAL_INFO, "00010203460102033c00"))
        assertFalse(Command.BATTERY_CASE in s11.state.values)
    }

    @Test
    fun touchPerEarbud() {
        val s = OneMoreSession(aero)
        assertTrue(s.groups.none { it.tab == SettingTab.CONTROLS }, "nothing before the earbuds report them")
        // Double left previous, double right next, triple left play/pause, triple right assistant.
        s.receive(reply(OneMoreCommand.GET_ALL_CUSTOM, "0201050600"))
        val left = s.groups.single { it.key == "touch_left" }.settings.map { it as Setting.Choice }
        assertEquals(listOf("touch_double_left" to "previous", "touch_triple_left" to "play_pause"), left.map { it.key to it.value })
        assertEquals(listOf("previous", "next", "volume_down", "volume_up", "play_pause", "voice_assistant", "none"), left[0].options)
        val w = s.change(SettingChange.Choice("touch_triple_right", "volume_up"))!!.single()
        assertEquals(OneMoreCommand.SET_CUSTOM, w.command)
        assertEquals("0404", hex(w.payload))
        assertEquals("volume_up", (s.groups.single { it.key == "touch_right" }.settings[1] as Setting.Choice).value)
        // The S25 has no assistant on a gesture.
        val s25 = OneMoreSession(OneMoreModels.fromName("1MORE S25"))
        s25.receive(reply(OneMoreCommand.GET_ALL_CUSTOM, "02010506"))
        assertFalse("voice_assistant" in (s25.groups.single { it.key == "touch_left" }.settings[0] as Setting.Choice).options)
    }

    @Test
    fun touchForBothAndTheOtherSwitches() {
        val mini = OneMoreSession(OneMoreModels.fromName("1MORE ComfoBuds Mini"))
        val start = mini.start().map { it.command }
        assertTrue(OneMoreCommand.GET_DOUBLE_CLICK in start && OneMoreCommand.GET_TRIPLE_CLICK in start && OneMoreCommand.GET_SLEEP_DETECTION in start)
        mini.receive(reply(OneMoreCommand.GET_DOUBLE_CLICK, "01"))
        mini.receive(reply(OneMoreCommand.GET_TRIPLE_CLICK, "02"))
        val touch = mini.groups.single { it.key == "touch" }.settings.map { it as Setting.Choice }
        assertEquals(listOf("touch_double_whole" to "play_pause", "touch_triple_whole" to "previous_next"), touch.map { it.key to it.value })
        assertEquals("03", hex(mini.change(SettingChange.Choice("touch_double_whole", "volume"))!!.single().payload))
        mini.receive(reply(OneMoreCommand.GET_SLEEP_DETECTION, "01"))
        mini.receive(reply(OneMoreCommand.GET_AUTOPLAY, "00"))
        val other = mini.groups.single { it.key == "other" }.settings
        assertEquals(Setting.Choice("smart_playback", "pause", listOf("pause_play", "pause", "off")), other[0])
        assertEquals(Setting.Toggle("sleep_detection", true), other[1])
        assertEquals("00", hex(mini.change(SettingChange.Toggle("sleep_detection", false))!!.single().payload))
        // Two devices and the connection mode on the Q21.
        val q21 = OneMoreSession(OneMoreModels.fromName("1MORE Q21"))
        q21.receive(reply(OneMoreCommand.GET_DUAL_DEVICE, "01"))
        assertEquals(true, q21.state.switch(Command.MULTIPOINT))
        assertEquals("00", hex(q21.set(Command.MULTIPOINT, 0)!!.single().payload))
        q21.receive(reply(OneMoreCommand.GET_CONNECTION_MODE, "02"))
        val mode = q21.groups.single { it.key == "connection" }.settings.single() as Setting.Choice
        assertEquals(Setting.Choice("connection_mode", "ldac", listOf("standard", "game", "ldac")), mode)
        val game = q21.change(SettingChange.Choice("connection_mode", "game"))!!.single()
        assertEquals(OneMoreCommand.SET_CONNECTION_MODE, game.command)
        assertEquals("01", hex(game.payload))
    }

    @Test
    fun earReturnAndFind() {
        val hq20 = OneMoreSession(OneMoreModels.fromName("1MORE HQ20"))
        hq20.receive(reply(OneMoreCommand.GET_EAR_RETURN, "00"))
        hq20.receive(reply(OneMoreCommand.GET_EAR_RETURN_DEPTH, "02"))
        hq20.receive(reply(OneMoreCommand.GET_MAX_SOUND_LIMIT, "03"))
        val other = hq20.groups.single { it.key == "other" }.settings
        assertEquals(Setting.Choice("ear_return", "off", listOf("off", "low", "medium", "high")), other[0])
        assertEquals(Setting.Choice("volume_limit", "high", listOf("off", "low", "medium", "high")), other[1])
        // From off: the switch on, then the depth, as 1MORE's app writes them.
        val on = hq20.change(SettingChange.Choice("ear_return", "medium"))!!
        assertEquals(listOf(OneMoreCommand.SET_EAR_RETURN to "01", OneMoreCommand.SET_EAR_RETURN_DEPTH to "02"), on.map { it.command to hex(it.payload) })
        assertEquals(listOf(OneMoreCommand.SET_EAR_RETURN_DEPTH to "03"), hq20.change(SettingChange.Choice("ear_return", "high"))!!.map { it.command to hex(it.payload) })
        // Earbuds that answer the find probe can ring each side.
        val s = OneMoreSession(aero)
        assertNull(s.set(Command.FIND_LEFT, 1))
        s.receive(reply(OneMoreCommand.FIND_DEVICE_SUPPORT, "01"))
        assertEquals("0100", hex(s.set(Command.FIND_LEFT, 1)!!.single().payload))
        assertEquals("0101", hex(s.set(Command.FIND_RIGHT, 1)!!.single().payload))
        s.receive(reply(OneMoreCommand.FIND_DEVICE, "0001"))
        assertEquals(false, s.state.switch(Command.FIND_LEFT))
        assertEquals(true, s.state.switch(Command.FIND_RIGHT))
    }

    @Test
    fun whatIsNeverSent() {
        val s = OneMoreSession(aero)
        assertFalse(s.allowed(s.frame(OneMoreCommand.HANDSHAKE, 1)))
        assertFalse(s.allowed(s.frame(OneMoreCommand.GET_AI_LICENSE)))
        assertFalse(s.allowed(s.frame(OneMoreCommand.DELETE_MUSIC, 0, 1)))
        assertFalse(s.allowed(s.frame(OneMoreCommand.TRANSFER_START)))
        assertFalse(s.allowed(reply(OneMoreCommand.GET_LISTEN_MODE, "00")), "a frame from the earbuds")
        assertTrue(s.allowed(s.frame(OneMoreCommand.SET_LISTEN_MODE, 0)))
    }

    @Test
    fun theEqAsTheAppWritesIt() {
        val s = OneMoreSession(aero)
        val start = s.start()
        assertTrue(OneMoreCommand.GET_PRESET_SOUND in start.map { it.command })
        // The read names every band's frequency, with the length field 1MORE's app writes (20).
        val read = start.single { it.command == OneMoreCommand.GET_EQ_PARAMETERS }
        assertEquals("0045011a02aa04d207dd0c2312211a8a265736e5", hex(read.payload))
        assertEquals("0014", hex(read.bytes().copyOfRange(4, 6)))
        // +3 dB on the first band and -3 dB on the last: raw 900 + 5 x the app's slider (60 + 20 per dB), with each band's frequency and Q.
        val w = s.eqCustom(listOf(3, 0, 0, 0, 0, 0, 0, 0, 0, -3))!!.single()
        assertEquals(OneMoreCommand.SET_EQ_PARAMETERS, w.command)
        assertEquals(70, w.payload.size)
        assertEquals("0105dc0045003c", hex(w.payload.copyOfRange(0, 7)))
        assertEquals("01038436e500c8", hex(w.payload.copyOfRange(63, 70)))
        // The earbuds answer the read in the same records; the custom preset sound (0C) means they hold the curve.
        s.receive(reply(OneMoreCommand.GET_EQ_PARAMETERS, hex(w.payload)))
        s.receive(reply(OneMoreCommand.GET_PRESET_SOUND, "0c"))
        assertEquals(OneMoreEq(null, true, listOf(3, 0, 0, 0, 0, 0, 0, 0, 0, -3)), s.eq)
        // A preset sound goes as its byte.
        assertEquals("0a", hex(s.eqPreset("pop")!!.single().payload))
        s.receive(reply(OneMoreCommand.GET_PRESET_SOUND, "0a"))
        assertEquals("pop", s.eq?.preset)
        assertNull(s.eqPreset("thx"), "the Aero does not list it")
    }

    @Test
    fun eqBandsThatDiffer() {
        // The HQ36 adds its own offset to some bands: band 1 at 0 dB is 1200 + 300 + 450.
        val hq36 = OneMoreSession(OneMoreModels.fromName("1MORE HQ36"))
        val w = hq36.eqCustom(List(10) { 0 })!!.single()
        assertEquals("01079e004a0050", hex(w.payload.copyOfRange(0, 7)))
        assertEquals("0102bc00c800c8", hex(w.payload.copyOfRange(7, 14)))
        // The Q12 has 12 bands in whole dB: 2 dB is (2 + 15) x 100.
        val q12 = OneMoreSession(OneMoreModels.fromName("1MORE Q12"))
        val w12 = q12.eqCustom(listOf(2) + List(11) { 0 })!!.single()
        assertEquals(84, w12.payload.size)
        assertEquals("0106a4005f0050", hex(w12.payload.copyOfRange(0, 7)))
        assertEquals("0014", hex(q12.start().single { it.command == OneMoreCommand.GET_EQ_PARAMETERS }.bytes().copyOfRange(4, 6)), "as 1MORE's app writes it")
        // Models without a custom EQ have presets only.
        assertNull(OneMoreSession(OneMoreModels.fromName("1MORE S20")).eqCustom(List(10) { 0 }))
        assertEquals(listOf("studio", "bass_reducer", "bass_booster", "loud", "classical", "pop"), OneMoreModels.fromName("1MORE S20")?.eqPresets)
    }
}
