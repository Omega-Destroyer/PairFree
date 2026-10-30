package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.Value
import io.github.omegadestroyer.pairfree.protocol.hexBytes
import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JBL's Airoha models over RACE (docs/jbl/PROTOCOL.md section 10), against the frames in data/jbl/test_vectors.json (race). */
class JblRaceTest {
    private val race = Json.parseToJsonElement(repoFile("data/jbl/test_vectors.json").readText()).jsonObject.getValue("race").jsonObject
    private fun list(key: String): Map<String, String> =
        race.getValue(key).jsonArray.associate { it.jsonObject.getValue("name").jsonPrimitive.content to it.jsonObject.getValue("hex").jsonPrimitive.content }
    private val requests = list("requests")
    private val answers = list("replies")

    private fun JblFrame.hex() = bytes().joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    private fun model(name: String) = JblModels.ALL.first { it.name == name }
    private fun setting(s: JblSession, key: String) = s.groups.flatMap { it.settings }.firstOrNull { it.key == key }

    /** Feeds a reply from the vectors through the parser, as the connection does. */
    private fun JblSession.reply(name: String) = RaceParser().feed(answers.getValue(name).hexBytes()).forEach { receive(it) }

    /** A session that has read its chip name [chip]. */
    private fun session(name: String, chip: String): JblSession {
        val s = JblSession(model(name))
        s.start()
        val text = chip.encodeToByteArray()
        s.receive(RacePacket(JblRace.NV_READ, byteArrayOf(text.size.toByte(), 0) + text, JblRace.TYPE_RESPONSE))
        return s
    }

    @Test
    fun theAirohaModelsAreKnown() {
        val buds3 = model("JBL Live Buds 3")
        assertEquals(JblModel.Family.AIROHA, buds3.family)
        assertEquals(JblModel.Noise.RACE, buds3.noise)
        assertFalse(buds3.gatt, "the Live Buds 3 are reached over Airoha's serial channel")
        assertTrue(model("JBL Endurance Race 2").gatt)
        assertEquals(model("JBL Quantum910"), JblModels.fromName("JBL Quantum910 Wireless"), "the name JBL's app shows")
        assertFalse(model("JBL Quantum910").racePush)
        assertEquals(Form.HEADPHONES, model("JBL Tune 775NC").form)
        // The LE Audio models, on the same Airoha layer.
        assertEquals(JblModel.Family.AIROHA, model("JBL Tour Pro 3").family)
        assertTrue(model("JBL Tune 670NC").gatt)
        assertEquals(model("JBL Tune 770NC"), JblModels.of(0x20B8))
    }

    @Test
    fun startReadsAsJblsAppDoes() {
        val start = JblSession(model("JBL Live Buds 3")).start().map { it.hex() }
        val expected = listOf("chip_name", "push_status_on", "agent_channel", "firmware", "battery_agent", "battery_partner", "case_battery", "anc_read")
        assertEquals(expected.map { requests.getValue(it) }, start.take(expected.size))
        assertTrue(requests.getValue("smart_switch_read") in start)
        // The Quantum 910 headsets: no push or agent read, one battery.
        val q910 = JblSession(model("JBL Quantum910")).start().map { it.hex() }
        assertFalse(requests.getValue("push_status_on") in q910)
        assertFalse(requests.getValue("battery_partner") in q910)
        assertTrue(requests.getValue("battery_agent") in q910)
    }

    @Test
    fun theChipDecidesTheReadsThatFollow() {
        val s = JblSession(model("JBL Live Buds 3"))
        s.start()
        s.reply("chip_name_ab158x")
        assertEquals(
            listOf(requests.getValue("auto_off_read_ab1568"), RacePacket.configGet(JblRace.CONFIG_IN_EAR).hex(), requests.getValue("model_name_ab158x")),
            s.takeOutbox().map { it.hex() },
        )
        assertEquals("AB1585", s.chipName, "for the device report")
        assertEquals(RaceChip.AB158X, RaceChip.of("AB1585"))
        assertEquals(RaceChip.AB1562, RaceChip.of("AB1562E"))
        assertEquals(RaceChip.AB1568, RaceChip.of("AB1568_DUAL_CHIP"))
        assertEquals(RaceChip.AB155X, RaceChip.of("AB1552"))
    }

    @Test
    fun batteriesFirmwareAndNoise() {
        val s = session("JBL Live Buds 3", "AB1585")
        s.reply("agent_right")
        s.reply("battery_agent")
        s.reply("battery_partner")
        s.reply("case_battery")
        s.reply("firmware")
        val v = s.state.values
        assertEquals(Value.Number(80), v[Command.BATTERY_RIGHT], "the agent is the right earbud")
        assertEquals(Value.Number(70), v[Command.BATTERY_LEFT])
        assertEquals(Value.Number(60), v[Command.BATTERY_CASE])
        assertEquals(Value.Text("JBL Live Buds 3_1.2.3"), v[Command.FIRMWARE_VERSION])

        s.reply("anc_ambient_aware")
        assertEquals(Value.Number(anc("transparency")), s.state.values[Command.ANC_MODE])
        assertEquals(false, (setting(s, "talk_thru") as Setting.Toggle).on)
        s.reply("anc_changed_to_anc")
        assertEquals(Value.Number(anc("anc")), s.state.values[Command.ANC_MODE], "a change made on the earbuds")

        assertEquals(requests.getValue("anc_off"), s.set(Command.ANC_MODE, anc("off"))!!.single().hex())
        assertEquals(requests.getValue("ambient_aware_ab1568"), s.set(Command.ANC_MODE, anc("transparency"))!!.single().hex())
        assertEquals(requests.getValue("anc_on_ab1568"), s.set(Command.ANC_MODE, anc("anc"))!!.single().hex())
        assertEquals(listOf(requests.getValue("anc_read")), s.takeReadBacks().map { it.hex() })
    }

    @Test
    fun theProductIdNamesTheModel() {
        // Found by the name "JBL LIVE FREE 2 TWS"; the earbuds say they are 20F1, the Live Buds 3.
        val s = session("JBL LIVE FREE 2 TWS", "AB1585")
        s.takeOutbox()
        s.reply("model_name_20f1")
        assertEquals("20F1", s.modelName)
        assertEquals(0x20F1, s.productId)
        assertEquals("JBL Live Buds 3", s.model.name)
        assertTrue(s.takeOutbox().any { it.hex() == RacePacket.of(JblRace.HI_RES_GET).hex() }, "the Live Buds 3's own settings are read")
        assertFalse(s.replies.values.any { it.size > 8 && it.decodeToString().contains("20F1") }, "the model name key is kept as text, not as bytes")
        // The AB1562 reads it with the AE information read, which is in the firmware commands' range.
        val old = session("JBL REFLECT MINI NC", "AB1562")
        assertTrue(old.takeOutbox().any { it.hex() == requests.getValue("ae_info") })
        assertTrue(old.allowed(RacePacket.of(JblRace.AE_INFO, 1, 0xFF)))
    }

    @Test
    fun noiseWaitsForTheChip() {
        val s = JblSession(model("JBL Live Buds 3"))
        s.start()
        s.reply("anc_changed_to_anc")
        assertNull(s.set(Command.ANC_MODE, anc("off")), "the write depends on the chip")
    }

    @Test
    fun olderChips() {
        val old = session("JBL CLUB PRO+ TWS", "AB1552")
        assertEquals(
            listOf(requests.getValue("auto_off_read_old"), RacePacket.nvRead(JblRace.NV_MODEL_NAME_OLD, 100).hex()),
            old.takeOutbox().map { it.hex() },
        )
        old.reply("anc_changed_to_anc")
        assertEquals(requests.getValue("talk_thru_ab155x"), old.change(SettingChange.Toggle("talk_thru", true))!!.single().hex())
        // The power control NV key: its length (4), then on and 30 minutes, two bytes each.
        old.receive(RacePacket(JblRace.NV_READ, "040001001e00".hexBytes(), JblRace.TYPE_RESPONSE))
        assertEquals(Setting.Choice("auto_off", "30", listOf("never", "30", "60", "120")), setting(old, "auto_off"))
        assertEquals(requests.getValue("auto_off_on_30_old"), old.change(SettingChange.Choice("auto_off", "30"))!!.single().hex())

        val ab1562 = session("JBL REFLECT MINI NC", "AB1562E")
        assertFalse(ab1562.batteryRequests().any { it.hex() == requests.getValue("battery_partner") }, "the AB1562 relays that read")
        ab1562.reply("anc_changed_to_anc")
        assertEquals(requests.getValue("anc_off_ab1562"), ab1562.set(Command.ANC_MODE, anc("off"))!!.single().hex())
        assertEquals(requests.getValue("anc_on_ab1562"), ab1562.set(Command.ANC_MODE, anc("anc"))!!.single().hex())
    }

    @Test
    fun otherSettings() {
        val s = session("JBL Live Buds 3", "AB1585")
        s.takeOutbox()
        s.reply("auto_off_on_30_ab1568")
        assertEquals(Setting.Choice("auto_off", "30", listOf("never", "30", "60", "120")), setting(s, "auto_off"))
        assertEquals(requests.getValue("auto_off_on_30_ab1568"), s.change(SettingChange.Choice("auto_off", "30"))!!.single().hex())
        s.receive(RacePacket.configGet(JblRace.CONFIG_IN_EAR).let { RacePacket(it.id, byteArrayOf(0, 2, 0, 0), JblRace.TYPE_RESPONSE) })
        assertEquals(requests.getValue("in_ear_on_ab1568"), s.change(SettingChange.Toggle("pause_taken_off", true))!!.single().hex())

        s.reply("smart_switch_audio")
        assertEquals("audio", (setting(s, JblExtras.SMART_KEY) as Setting.Choice).value)
        assertEquals(requests.getValue("smart_switch_audio_live_buds_3"), s.change(SettingChange.Choice(JblExtras.SMART_KEY, "audio"))!!.single().hex())

        s.reply("prompts_spoken")
        assertEquals(true, (setting(s, JblExtras.PROMPTS_KEY) as Setting.Toggle).on)
        assertEquals(requests.getValue("prompts_tones"), s.change(SettingChange.Toggle(JblExtras.PROMPTS_KEY, false))!!.single().hex())

        s.reply("balance_off")
        assertEquals(16, (setting(s, JblExtras.BALANCE_KEY) as Setting.Level).value, "the middle")
        assertEquals(requests.getValue("balance_left"), s.change(SettingChange.Level(JblExtras.BALANCE_KEY, 0))!!.single().hex())

        s.receive(RacePacket(JblRace.CONFIG_GET, byteArrayOf(0, 7, 0, 0), JblRace.TYPE_RESPONSE))
        assertEquals("off", (setting(s, JblExtras.VOICE_AWARE_KEY) as Setting.Choice).value)
        assertEquals(
            listOf(requests.getValue("voice_aware_on"), requests.getValue("voice_aware_level_high")),
            s.change(SettingChange.Choice(JblExtras.VOICE_AWARE_KEY, "high"))!!.map { it.hex() },
        )

        // The voice EQ setting: the microphone effect, the sound effect (5 standard) and the audio setting.
        s.receive(RacePacket(JblRace.CONFIG_GET, byteArrayOf(0, 19, 0, 0, 5, 0), JblRace.TYPE_RESPONSE))
        val effect = setting(s, JblExtras.EFFECT_KEY) as Setting.Choice
        assertEquals("standard", effect.value)
        assertFalse("off" in effect.options, "no off on Airoha models")
        assertEquals(requests.getValue("sound_effect_bass"), s.change(SettingChange.Choice(JblExtras.EFFECT_KEY, "bass"))!!.single().hex())

        val find = s.change(SettingChange.Press(JblExtras.FIND_KEY))!!.map { it.hex() }
        assertEquals(requests.getValue("find_left"), find.first())
        assertEquals(requests.getValue("find_right_stop"), s.change(SettingChange.Toggle(JblExtras.MUTE_RIGHT, true))!!.single().hex())
    }

    @Test
    fun neverSent() {
        val s = JblSession(model("JBL Live Buds 3"))
        assertTrue(s.start().all { s.allowed(it) })
        assertFalse(s.allowed(RacePacket.of(JblRace.KEY_EVENT, 0x96, 0)), "factory reset")
        assertFalse(s.allowed(RacePacket.of(JblRace.KEY_EVENT, 0x95, 0)), "power off")
        assertFalse(s.allowed(RacePacket.of(JblRace.KEY_EVENT, 0x02, 0)), "pairing")
        assertTrue(s.allowed(RacePacket.of(JblRace.KEY_EVENT, 0xA9, 0)))
        assertFalse(s.allowed(RacePacket.of(0x0D01)), "relay to the other earbud")
        assertFalse(s.allowed(RacePacket.of(0x0A01)), "NV key write")
        assertFalse(s.allowed(RacePacket.of(0x1C01)), "firmware update")
        assertTrue(s.allowed(RacePacket.of(JblRace.FIRMWARE, 0)), "the firmware version read")
    }

    @Test
    fun parserSplitsAndJoins() {
        val a = answers.getValue("battery_agent").hexBytes()
        val b = answers.getValue("firmware").hexBytes()
        val p = RaceParser()
        assertEquals(0, p.feed(byteArrayOf(0x11, 0x22) + a.copyOfRange(0, 3)).size, "junk, then half a frame")
        val out = p.feed(a.copyOfRange(3, a.size) + b)
        assertEquals(listOf(JblRace.BATTERY, JblRace.FIRMWARE), out.map { it.id })
        assertEquals(JblRace.TYPE_INDICATION, out[0].type)
        assertEquals(listOf(0, 80), out[0].data.map { it.toInt() and 0xFF }, "the status comes off")
        val reader = JblReader(race = true)
        assertTrue(reader.feed(a).single() is RacePacket)
    }

    private fun anc(name: String): Int = Command.ANC_MODE.labels.entries.first { it.value == name }.key
}
