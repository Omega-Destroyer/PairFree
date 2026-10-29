package io.github.omegadestroyer.pairfree.protocol.nothing

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
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun load(name: String): JsonObject = Json.parseToJsonElement(repoFile("data/nothing/$name").readText()).jsonObject

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

private fun hex(bytes: ByteArray) = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

/** A reply from the earbuds to [request], with a CRC and response code [code]. */
private fun reply(request: Int, payload: String = "", code: Int = 0) =
    NothingFrame(request and NothingWire.REPLY_MASK, payload.hexBytes(), 0x0120 or code)

/** Frames, the tables and a session, against frames built by hand and payloads nada and nadamas read from real earbuds. */
class NothingTest {
    private val vectors = load("test_vectors.json")
    private val payloads = vectors.getValue("payloads").jsonObject

    private fun payload(name: String) = payloads.getValue(name).jsonObject

    /** A session that has read a model's product id and firmware. */
    private fun session(productId: String, firmware: String = "1.0.1.80", hint: NothingModel? = null): NothingSession = NothingSession(hint).also { s ->
        val id = productId.chunked(2).reversed().joinToString("")
        s.receive(reply(NothingCommand.GET_DEVICE_MODEL, id))
        s.receive(reply(NothingCommand.GET_HOST_VERSION_DEVICE, hex(firmware.encodeToByteArray())))
    }

    private fun setting(s: NothingSession, key: String) = s.groups.flatMap { it.settings }.firstOrNull { it.key == key }

    @Test
    fun theCrcIsModbus() {
        val check = vectors.getValue("crc_check").jsonObject
        assertEquals(check.str("crc").toInt(16), NothingFrame.crc16(check.str("ascii").encodeToByteArray()))
    }

    @Test
    fun framesAreBuiltByteForByte() {
        for (f in vectors.getValue("frames").jsonArray.map { it.jsonObject }) {
            val crc = f.getValue("crc").jsonPrimitive.boolean
            val frame = NothingFrame(f.str("command").toInt(16), f.str("payload").hexBytes(), NothingFrame.control(crc), f.getValue("fsn").jsonPrimitive.int)
            assertArrayEquals(f.str("bytes").hexBytes(), frame.bytes(), f.str("what"))
        }
    }

    @Test
    fun theParserSplitsJoinsAndChecks() {
        val r = vectors.getValue("replies").jsonArray.single().jsonObject
        val bytes = r.str("bytes").hexBytes()
        val p = NothingParser()
        // Split across reads, after a stray byte.
        assertTrue(p.feed(byteArrayOf(0x13) + bytes.copyOfRange(0, 6)).isEmpty())
        val frames = p.feed(bytes.copyOfRange(6, bytes.size) + bytes)
        assertEquals(2, frames.size)
        assertEquals(r.str("command").toInt(16), frames[0].command)
        assertEquals(r.str("payload"), hex(frames[0].payload))
        assertEquals(NothingCommand.GET_CURRENT_NOISE_REDUCTION, frames[0].answers)
        assertTrue(frames[0].ok)
        assertFalse(frames[0].isRequest)
        assertEquals(0, p.pending)
        // A wrong CRC is dropped, and the next frame still comes through.
        val bad = bytes.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertEquals(1, NothingParser().feed(bad + bytes).size)
        // The Ear (1)'s frames carry no CRC.
        val plain = NothingFrame(0x4007, "010255".hexBytes(), NothingFrame.control(false))
        assertEquals("010255", hex(NothingParser().feed(plain.bytes()).single().payload))
    }

    @Test
    fun theSessionReadsWhatNothingXReads() {
        val start = NothingSession().start()
        assertEquals(NothingCommand.GET_PROTOCOL_VERSION, start.first().command)
        assertEquals(NothingCommand.GET_DEVICE_MODEL, start[1].command)
        val noise = start.first { it.command == NothingCommand.GET_CURRENT_NOISE_REDUCTION }
        assertEquals("03", hex(noise.payload))
        // Sequence numbers count up from 0.
        assertEquals((0 until start.size).toList(), start.map { it.fsn })
        // The Ear (2) and Ear (a) go without the protocol version; nothing private or forbidden is read.
        assertEquals(NothingCommand.GET_DEVICE_MODEL, NothingSession(NothingModels.of("B155")).start().first().command)
        assertTrue(start.all { NothingSession().allowed(it) })
        val s = NothingSession()
        assertFalse(s.allowed(s.frame(NothingCommand.RESTORE_FACTORY_SETTING)))
        assertFalse(s.allowed(s.frame(NothingCommand.GET_REMOTE_CONFIGURATION)))
        assertFalse(s.allowed(s.frame(NothingCommand.OTA_FIND_NEW_VERSION)))
    }

    @Test
    fun theModelAndFirmwareComeFromTheReplies() {
        val p = payload("b187_model")
        val s = NothingSession()
        s.receive(reply(NothingCommand.GET_DEVICE_MODEL, p.str("payload")))
        assertEquals(p.str("product_id"), s.productId)
        assertEquals("CMF Buds Pro 2", s.model?.name)
        val fw = payload("b187_firmware")
        s.receive(reply(NothingCommand.GET_HOST_VERSION_DEVICE, fw.str("payload")))
        assertEquals(fw.str("firmware"), s.firmware)
        assertEquals("B187_1.0.1.74", (s.state.values[Command.FIRMWARE_VERSION] as Value.Text).text)
        val ear3a = NothingSession().also { it.receive(reply(NothingCommand.GET_DEVICE_MODEL, payload("b190_model").str("payload"))) }
        assertEquals("Nothing Ear (3a)", ear3a.model?.name)
    }

    @Test
    fun theNoiseReportAsTheB184SentIt() {
        val s = session("B184")
        s.receive(reply(NothingCommand.GET_CURRENT_NOISE_REDUCTION, payload("b184_noise_transparency").str("payload")))
        assertEquals(ancNumber("transparency"), s.state.number(Command.ANC_MODE))
        assertNull(setting(s, "noise_strength"), "no strength while in transparency")
        s.receive(reply(NothingCommand.GET_CURRENT_NOISE_REDUCTION, payload("b184_noise_high").str("payload")))
        assertEquals(ancNumber("anc"), s.state.number(Command.ANC_MODE))
        val strength = setting(s, "noise_strength") as Setting.Choice
        assertEquals("high", strength.value)
        assertEquals(listOf("adaptive", "high", "mid", "low"), strength.options)
        assertEquals(SettingTab.HOME, s.groups.first { g -> g.settings.any { it.key == "noise_strength" } }.tab)
        // Low, then off, then back on: cancelling returns at the strength last set.
        assertEquals("01" + "03" + "00", hex(s.change(SettingChange.Choice("noise_strength", "low"))!!.single().payload))
        assertEquals("010500", hex(s.set(Command.ANC_MODE, ancNumber("off"))!!.single().payload))
        val on = s.set(Command.ANC_MODE, ancNumber("anc"))!!.single()
        assertEquals(NothingCommand.SET_CURRENT_NOISE_REDUCTION, on.command)
        assertEquals("010300", hex(on.payload))
        assertEquals(NothingCommand.GET_CURRENT_NOISE_REDUCTION, s.takeReadBacks().single().command)
        // The earbuds' own change arrives as an event.
        s.receive(NothingFrame(NothingCommand.EVENT_NOISE_REDUCTION_LEVEL_CHANGED, "010700020300".hexBytes(), 0x0120))
        assertEquals(ancNumber("transparency"), s.state.number(Command.ANC_MODE))
    }

    @Test
    fun modelsWithoutTransparencyOrNoiseCancelling() {
        // The Ear (stick) has noise cancelling only from firmware x.x.2, and never transparency: a switch.
        val stick = session("B157", "1.0.2.10")
        stick.receive(reply(NothingCommand.GET_CURRENT_NOISE_REDUCTION, "010500020100"))
        assertNull(stick.state.values[Command.ANC_MODE])
        assertEquals(Setting.Toggle("anc", false), setting(stick, "anc"))
        assertEquals("010100", hex(stick.change(SettingChange.Toggle("anc", true))!!.single().payload))
        val older = session("B157", "1.0.1.79")
        older.receive(reply(NothingCommand.GET_CURRENT_NOISE_REDUCTION, "010500020100"))
        assertNull(setting(older, "anc"))
        // The Ear (open) has none at all; the CMF Buds have one strength, so no strength row.
        val open = session("B174")
        open.receive(reply(NothingCommand.GET_CURRENT_NOISE_REDUCTION, "010500020100"))
        assertNull(open.state.values[Command.ANC_MODE])
        val buds = session("B168")
        buds.receive(reply(NothingCommand.GET_CURRENT_NOISE_REDUCTION, "010100020100"))
        assertEquals(ancNumber("anc"), buds.state.number(Command.ANC_MODE))
        assertNull(setting(buds, "noise_strength"))
    }

    @Test
    fun batteries() {
        val s = session("B184")
        // Left 80 % and charging, right 72 %, case 90 %.
        s.receive(reply(NothingCommand.GET_REMOTE_BATTERY_LEVEL, "03" + "02d0" + "0348" + "045a"))
        assertEquals(80, s.state.batteryLeft)
        assertEquals(72, s.state.batteryRight)
        assertEquals(90, s.state.batteryCase)
        s.receive(NothingFrame(NothingCommand.EVENT_BATTERY_CHANGED, "01023c".hexBytes(), 0x0120))
        assertEquals(60, s.state.batteryLeft)
        // Headphones report one battery for the whole product.
        val hp = session("B170")
        hp.receive(reply(NothingCommand.GET_REMOTE_BATTERY_LEVEL, "010646"))
        assertEquals(70, hp.state.batteryLeft)
        assertNull(hp.state.values[Command.BATTERY_RIGHT])
    }

    @Test
    fun soundSettings() {
        val s = session("B184")
        s.receive(reply(NothingCommand.GET_EQ_MODE, "0300"))
        assertEquals(Setting.Choice("eq_preset", "bass", listOf("balanced", "bass", "treble", "voice")), setting(s, "eq_preset"))
        val eq = s.change(SettingChange.Choice("eq_preset", "voice"))!!.single()
        assertEquals(NothingCommand.SET_EQ_MODE, eq.command)
        assertEquals("0100", hex(eq.payload))
        s.receive(reply(NothingCommand.GET_BASS_BOOST, "0003"))
        assertEquals(Setting.Toggle("bass_boost", false), setting(s, "bass_boost"))
        assertNull(setting(s, "bass_level"), "the level shows while Ultra Bass is on")
        assertEquals("0103", hex(s.change(SettingChange.Toggle("bass_boost", true))!!.single().payload))
        assertEquals("0105", hex(s.change(SettingChange.Level("bass_level", 5))!!.single().payload))
        s.receive(reply(NothingCommand.GET_SPATIAL_AUDIO, "00"))
        assertEquals("01", hex(s.change(SettingChange.Toggle("spatial_audio", true))!!.single().payload))
        assertNull(setting(s, "head_tracking"), "earbuds have no head tracking")
        // Dirac models (eq 0 in Nothing X's table) have no presets PairFree knows.
        val dirac = session("B172")
        dirac.receive(reply(NothingCommand.GET_EQ_MODE, "0000"))
        assertNull(setting(dirac, "eq_preset"))
        // Headphones with head tracking write both bytes.
        val hp = session("B170")
        hp.receive(reply(NothingCommand.GET_SPATIAL_AUDIO, "0100"))
        val ht = setting(hp, "head_tracking") as Setting.Toggle
        assertTrue(ht.enabled)
        assertEquals("0101", hex(hp.change(SettingChange.Toggle("head_tracking", true))!!.single().payload))
    }

    @Test
    fun sharedSwitchesAndTheCodec() {
        val s = session("B162")
        s.receive(reply(NothingCommand.GET_EXTRA_FEATURE_STATUS, "010101"))
        s.receive(reply(NothingCommand.GET_HOST_LAG_MODE, "0200"))
        s.receive(reply(NothingCommand.GET_LHDC_COMMANDS, "0000"))
        assertEquals(true, s.state.switch(Command.IN_EAR_DETECTION))
        assertEquals(false, s.state.switch(Command.GAME_MODE))
        assertEquals("010100", hex(s.set(Command.IN_EAR_DETECTION, 0)!!.single().payload))
        assertEquals("0100", hex(s.set(Command.GAME_MODE, 1)!!.single().payload))
        assertEquals(Setting.Choice("codec", "standard", listOf("standard", "ldac")), setting(s, "codec"))
        assertEquals("0200", hex(s.change(SettingChange.Choice("codec", "ldac"))!!.single().payload))
        // Finding: each earbud on its own, by its battery part number.
        val find = s.set(Command.FIND_RIGHT, 1)!!.single()
        assertEquals(NothingCommand.SET_WHERE_AM_I, find.command)
        assertEquals("0301", hex(find.payload))
        assertEquals(true, s.state.switch(Command.FIND_RIGHT))
        // The Ear (open) has no in-ear detection, whatever it answers.
        val open = session("B174")
        open.receive(reply(NothingCommand.GET_EXTRA_FEATURE_STATUS, "010101"))
        assertNull(open.state.values[Command.IN_EAR_DETECTION])
        // Headphones ring as one.
        val hp = session("B170")
        assertEquals("01", hex(hp.change(SettingChange.Press("find"))!!.single().payload))
    }

    @Test
    fun whatTheEarbudsDoNotHaveStaysHidden() {
        val s = session("B184")
        // An empty payload (the CMF Buds Pro 2's way) and "not supported" (response code 3) both hide the setting.
        s.receive(reply(NothingCommand.GET_EQ_MODE, ""))
        s.receive(reply(NothingCommand.GET_BASS_BOOST, "", code = 3))
        assertNull(setting(s, "eq_preset"))
        assertNull(setting(s, "bass_boost"))
        // A refused write is read again.
        s.receive(reply(NothingCommand.GET_LHDC_COMMANDS, "0000"))
        s.change(SettingChange.Choice("codec", "ldac"))
        s.receive(reply(NothingCommand.SET_LHDC_COMMANDS, "", code = 4))
        assertEquals(NothingCommand.GET_LHDC_COMMANDS, s.takeOutbox().single().command)
        // Not activated: activate once, then read everything again.
        val fresh = NothingSession()
        fresh.receive(reply(NothingCommand.GET_DEVICE_MODEL, "", code = 2))
        val again = fresh.takeOutbox()
        assertEquals(NothingCommand.SET_PROTOCOL_ACTIVATED, again.first().command)
        assertTrue(again.any { it.command == NothingCommand.GET_REMOTE_BATTERY_LEVEL })
        fresh.receive(reply(NothingCommand.GET_EQ_MODE, "", code = 2))
        assertTrue(fresh.takeOutbox().isEmpty(), "only once")
    }

    @Test
    fun theEarOneTakesNoCrc() {
        val ear1 = NothingModels.fromName("ear (1)")
        assertEquals("B181", ear1?.productId)
        val s = NothingSession(ear1)
        assertFalse(s.crc)
        assertFalse(s.start().first().hasCrc)
        assertTrue(NothingSession(NothingModels.fromName("CMF Buds 2a")).crc)
    }

    @Test
    fun touchControls() {
        val s = session("B155")
        // The Ear (2)'s default key table: each side's double, triple, hold and double-hold.
        val defaults = NothingModels.of("B155")!!.configs.last()
        s.receive(reply(NothingCommand.GET_KEY_CONFIGURATION, "080201020903010209020103080301030802010716030107160201090103010901"))
        val left = s.groups.first { it.key == "touch_left" }
        assertEquals(SettingTab.CONTROLS, left.tab)
        assertEquals(Setting.Choice("touch_double_left", "next", listOf("previous", "next", "voice_assistant")), left.settings.first { it.key == "touch_double_left" })
        // The default noise cycle shows as noise control.
        assertEquals("noise_control", (left.settings.first { it.key == "touch_hold_left" } as Setting.Choice).value)
        assertTrue(s.groups.any { it.key == "touch_right" })
        val write = s.change(SettingChange.Choice("touch_double_right", "previous"))!!.single()
        assertEquals(NothingCommand.SET_KEY_CONFIGURATION, write.command)
        assertEquals("0103010208", hex(write.payload))
        assertEquals(NothingCommand.GET_KEY_CONFIGURATION, s.takeReadBacks().single().command)
        assertTrue(defaults.gestures.isNotEmpty())
        // Headphones: the whole product's own group.
        val hp = session("B164")
        hp.receive(reply(NothingCommand.GET_KEY_CONFIGURATION, "03060102090601030806010716"))
        assertNotNull(hp.groups.firstOrNull { it.key == "touch" })
    }

    @Test
    fun theHeadphonesButtonsRollerAndSlider() {
        // The Headphone (1)'s default key table from Nothing X: the roller's hold (the noise cycle), the control
        // button's press (voice assistant) and its hold (nothing).
        val s = session("B170")
        s.receive(reply(NothingCommand.GET_KEY_CONFIGURATION, "0306010716060a010b060a0701"))
        val touch = s.groups.first { it.key == "touch" }
        assertEquals(listOf("touch_single_button_whole", "touch_hold_button_whole", "touch_hold_roller_whole"), touch.settings.map { it.key })
        // Nothing's radio, news and platform-only actions (32, 31, 33) are left out.
        assertEquals(
            Setting.Choice("touch_single_button_whole", "voice_assistant", listOf("voice_assistant", "noise_control", "spatial_audio", "mic_mute", "eq_preset", "none")),
            setting(s, "touch_single_button_whole"),
        )
        assertEquals(Setting.Choice("touch_hold_roller_whole", "noise_control", listOf("noise_control", "none")), setting(s, "touch_hold_roller_whole"))
        assertEquals("01060a071b", hex(s.change(SettingChange.Choice("touch_hold_button_whole", "spatial_audio"))!!.single().payload))
        assertEquals("0106010701", hex(s.change(SettingChange.Choice("touch_hold_roller_whole", "none"))!!.single().payload))

        // The CMF Headphone Pro's slider: bass or treble.
        val cmf = session("B175")
        cmf.receive(reply(NothingCommand.GET_KEY_CONFIGURATION, "04060a0701060a010b0605012306010716"))
        assertEquals(Setting.Choice("touch_single_slider_whole", "bass_boost", listOf("bass_boost", "treble_boost")), setting(cmf, "touch_single_slider_whole"))
        assertEquals("0106050124", hex(cmf.change(SettingChange.Choice("touch_single_slider_whole", "treble_boost"))!!.single().payload))
    }

    @Test
    fun caseDial() {
        // The CMF Buds Pro 2's default key table from Nothing X: the earbuds, then the case dial (device 4), whose
        // double and triple press also have an entry for calls (button 9).
        val s = session("B172")
        s.receive(reply(NothingCommand.GET_KEY_CONFIGURATION,
            "0f03010209030103080301071603010901020102090201030802010716020109010401010104010201040103010401070104010a010409020104090301"))
        val dial = s.groups.first { it.key == "touch_dial" }
        assertEquals(SettingTab.CONTROLS, dial.tab)
        assertEquals(listOf("touch_single_dial", "touch_double_dial", "touch_double_call_dial", "touch_triple_dial", "touch_triple_call_dial", "touch_hold_dial", "touch_turn_dial"),
            dial.settings.map { it.key })
        // Nothing's news summary (31) is left out.
        assertEquals(Setting.Choice("touch_single_dial", "none", listOf("play_pause", "next", "previous", "voice_assistant", "game_mode", "none")),
            dial.settings.first { it.key == "touch_single_dial" })
        assertEquals(listOf("answer_call", "answer_mute", "none"), (dial.settings.first { it.key == "touch_double_call_dial" } as Setting.Choice).options)
        assertEquals(listOf("volume_control", "none"), (dial.settings.first { it.key == "touch_turn_dial" } as Setting.Choice).options)
        assertEquals("0104010a17", hex(s.change(SettingChange.Choice("touch_turn_dial", "volume_control"))!!.single().payload))
        assertEquals("0104090203", hex(s.change(SettingChange.Choice("touch_double_call_dial", "answer_call"))!!.single().payload))
        assertEquals("answer_call", (s.groups.first { it.key == "touch_dial" }.settings.first { it.key == "touch_double_call_dial" } as Setting.Choice).value)
        // The earbuds' own rows are unchanged.
        assertTrue(s.groups.any { it.key == "touch_left" } && s.groups.any { it.key == "touch_right" })
        // The Ear (3)'s case has a button, not a dial: its press and hold (double press has one action only).
        val ear3 = session("B173")
        ear3.receive(reply(NothingCommand.GET_KEY_CONFIGURATION, "010401070b"))
        assertEquals(listOf("touch_hold_case"), ear3.groups.first { it.key == "touch_case" }.settings.map { it.key })
    }

    @Test
    fun theModelTable() {
        val devices = load("devices.json").getValue("devices").jsonArray.map { it.jsonObject }
        assertEquals(devices.size, NothingModels.ALL.size)
        assertEquals(devices.size, NothingModels.ALL.map { it.productId }.toSet().size)
        assertEquals(Form.HEADPHONES, NothingModels.of("B170")?.form)
        assertEquals(Form.HEADPHONES, NothingModels.of("B164")?.form, "the neckband reports one battery")
        assertTrue(NothingModels.identify("CMF Buds Pro 2", emptyList()))
        assertTrue(NothingModels.identify("My buds", listOf(NothingWire.SERVICE_UUID.lowercase())))
        assertFalse(NothingModels.identify("CMF Watch Pro 2", emptyList()))
        assertFalse(NothingModels.identify("Galaxy Buds3", emptyList()))
        assertEquals("CMF Buds 2 Plus", NothingModels.model("B184")?.names?.single())
        assertEquals(true, NothingModels.model("B184")?.fastPair)
        // Nothing X's firmware ranges compare the last two fields.
        val stick = NothingModels.of("B157")!!
        assertEquals(listOf("high", "off"), stick.config("1.0.2.0").anc)
        assertEquals(emptyList<String>(), stick.config("1.0.1.79").anc)
        val commands = load("commands.json").getValue("commands").jsonArray.map { it.jsonObject }
        assertEquals(commands.size, NothingCommand.NAMES.size)
        assertTrue(NothingCommand.RESTORE_FACTORY_SETTING in NothingCommand.NEVER_SEND)
        assertTrue(NothingCommand.GET_REMOTE_CONFIGURATION in NothingCommand.PRIVATE)
    }

    private fun ancNumber(name: String): Int = Command.ANC_MODE.labels.entries.first { it.value == name }.key
}
