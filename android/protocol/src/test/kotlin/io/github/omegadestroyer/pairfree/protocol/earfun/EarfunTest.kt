package io.github.omegadestroyer.pairfree.protocol.earfun

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.Value
import io.github.omegadestroyer.pairfree.protocol.hexBytes
import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun hex(bytes: ByteArray) = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

private fun anc(name: String) = Command.ANC_MODE.labels.entries.first { it.value == name }.key

private fun model(name: String) = EarfunModels.fromName(name)!!

/** A Qualcomm model's answer to [code]: GAIA vendor 000A, 83 and the code, a status byte, then [data]. */
private fun qccReply(code: Int, data: String) = "ff0400${"%02x".format(data.length / 2 + 1)}000a83${"%02x".format(code)}00$data".hexBytes()

/** An EarFun-frame answer to [command] with [data]. */
private fun efReply(command: Int, data: String) = EarfunFrame(command, data.hexBytes()).bytes()

/**
 * Frames against the bytes EarFun's app builds for the same settings (QualcommProtocolParser.buildCommond,
 * EarfunProtocolParser.buildEarfunCommond and the models' equalizers, worked out by hand from their code), the
 * tables against each other, and sessions against answers built from the formats.
 */
class EarfunTest {
    @Test
    fun theTableHoldsTogether() {
        val models = EarfunModels.ALL
        assertTrue(models.size >= 20)
        assertEquals(models.size, models.map { it.name.lowercase() }.toSet().size, "one entry per Bluetooth name")
        for (m in models.filter { it.covered }) {
            assertTrue(m.protocol == EarfunModel.QUALCOMM || m.protocol == EarfunModel.EARFUN, m.name)
            m.noise?.let { n ->
                assertTrue("off" in n.values, m.name)
                n.cancelling?.let { c -> assertTrue(c.options.all { it in EarfunValues.NOISE_CANCEL_MODE.getValue(c.table) }, m.name) }
                n.ambient?.let { a -> assertTrue(a.options.all { it in EarfunValues.AMBIENT_MODE.getValue(a.table) }, m.name) }
            }
            if (m.autoOff.isNotEmpty()) assertTrue(m.autoOff.all { it in EarfunValues.AUTO_OFF.getValue(m.autoOffTable!!) }, m.name)
            m.touch?.let { t ->
                val actions = EarfunValues.KEY_ACTIONS.getValue(t.map)
                val calls = t.callMap?.let { EarfunValues.KEY_ACTIONS.getValue(it) }.orEmpty()
                for ((gesture, options) in t.gestures) assertTrue(options.all { it in (if (gesture.endsWith("_call")) calls else actions) }, "${m.name} $gesture")
                if (m.qualcomm) assertEquals(EarfunModel.QUALCOMM, t.map, m.name)
            }
            m.eq?.let { e ->
                assertEquals(e.hz.size, e.q.size, m.name)
                val presets = EarfunValues.PRESETS.getValue(e.presets)
                assertTrue(presets.all { it.second.size == e.hz.size }, m.name)
                assertTrue(e.master.byPeak == null || e.master.byPeak!!.size == 2 * EarfunSession.POSITIONS + 1, m.name)
            }
        }
        // Factory reset and renaming are never sent, nor the reads of phones' names and addresses.
        assertTrue(EarfunQcc.RESET in EarfunQcc.NEVER_SEND && EarfunQcc.SET_NAME in EarfunQcc.NEVER_SEND && EarfunCommand.RESET in EarfunCommand.NEVER_SEND)
        // Restarting and powering off go only from an action the user confirms (owner, 2026-11-03).
        assertEquals(setOf(EarfunQcc.RESTART, EarfunQcc.SET_CONTROL_PLAY), EarfunQcc.CONFIRM)
        assertEquals(setOf(EarfunCommand.SET_CONTROL_PLAY), EarfunCommand.CONFIRM)
        assertTrue(EarfunQcc.GET_DEVICE_LIST in EarfunQcc.PRIVATE && EarfunQcc.GET_SN in EarfunQcc.PRIVATE && EarfunCommand.GET_DEVICE_LIST in EarfunCommand.PRIVATE)
    }

    @Test
    fun namesMatchWhole() {
        assertEquals("EarFun Air Pro 4", EarfunModels.fromName("EarFun Air Pro 4")?.name)
        assertEquals("EarFun Air Pro 4+", EarfunModels.fromName("EarFun Air Pro 4+AI")?.name)
        assertEquals("EarFun Air Pro 4i", EarfunModels.fromName(" earfun air pro 4i ")?.name)
        assertNull(EarfunModels.fromName("EarFun Air Pro"))
        assertFalse(EarfunModels.fromName("EarFun Wave Pro")!!.covered)
        assertEquals(Form.HEADPHONES, model("EarFun Wave Pro 2").form)
    }

    @Test
    fun earfunFrames() {
        assertEquals("ef20800000fe", hex(EarfunFrame(EarfunCommand.GET_NOISE).bytes()))
        assertEquals("ef2085010405fe", hex(EarfunFrame(EarfunCommand.SET_NOISE, byteArrayOf(4)).bytes()))
        // The parser takes frames split anywhere and skips what is not one.
        val parser = EarfunParser()
        assertEquals(emptyList<EarfunFrame>(), parser.feed("00ef1014".hexBytes()))
        assertEquals(listOf(EarfunFrame(EarfunCommand.GET_BATTERY, "504b".hexBytes())), parser.feed("02504b9dfe".hexBytes()))
        assertEquals(emptyList<EarfunFrame>(), EarfunParser().feed("ef1014025049aafe".hexBytes()), "a wrong checksum")
    }

    @Test
    fun qualcommStart() {
        val s = EarfunSession(model("EarFun Free Pro 3"))
        val start = s.start().map(::hex)
        // GAIA v3's opening first, as EarFun's app sends it, without firmware update's notifications.
        assertEquals(
            listOf("ff040000000a0300", "ff040000001d0001", "ff040005001d000d0700000004", "ff040001001d00070d"),
            start.take(4),
        )
        assertFalse("ff040001001d000706" in start)
        assertTrue("ff040000000a0306" in start, "the left battery")
        assertTrue(start.all { s.allowed(it.hexBytes()) })
        // The Air Pro 4 has none (TW600ProtocolParser.initCommand).
        assertFalse(EarfunSession(model("EarFun Air Pro 4")).start().any { hex(it).startsWith("ff040000001d") })
    }

    @Test
    fun neverSent() {
        val s = EarfunSession(model("EarFun Free Pro 3"))
        assertFalse(s.allowed("ff040000000a0336".hexBytes()), "reset")
        assertFalse(s.allowed("ff040000000a032c".hexBytes()), "the phones' names and addresses")
        assertFalse(s.allowed("ff040001001d000706".hexBytes()), "firmware update's notifications")
        assertFalse(s.allowed("ff040000001d0600".hexBytes()), "another GAIA v3 command")
        val e = EarfunSession(model("EarFun Air 2 NC"))
        assertFalse(e.allowed("ef100002afa556fe".hexBytes()), "reset")
        assertFalse(e.allowed(EarfunFrame(EarfunCommand.GET_DEVICE_LIST).bytes()))
        assertTrue(e.allowed(EarfunFrame(EarfunCommand.GET_NOISE).bytes()))
    }

    @Test
    fun qualcommReplies() {
        val s = EarfunSession(model("EarFun Air Pro 4+"))
        val fed = s.feed(qccReply(EarfunQcc.GET_LEFT_BATTERY, "55") + qccReply(EarfunQcc.GET_BOX_BATTERY, "c6"))
        assertTrue(fed.changed)
        assertEquals(listOf(EarfunQcc.GET_LEFT_BATTERY, EarfunQcc.GET_BOX_BATTERY), fed.replies)
        assertEquals(Value.Number(85), s.state.values[Command.BATTERY_LEFT])
        assertEquals(Value.Number(70), s.state.values[Command.BATTERY_CASE], "bit 7 is charging")
        s.feed(qccReply(EarfunQcc.GET_FW_VERSION, "312e322e33"))
        assertEquals(Value.Text("TW700_1.2.3"), s.state.values[Command.FIRMWARE_VERSION])
        assertEquals(EarfunQcc.GET_LEFT_BATTERY, s.awaits("ff040000000a0306".hexBytes()))
        assertNull(s.awaits("ff040001000a031201".hexBytes()), "a write")
    }

    @Test
    fun noiseOnTheAirPro4() {
        val s = EarfunSession(model("EarFun Air Pro 4"))
        s.feed(qccReply(EarfunQcc.GET_NOISE_MODE, "01") + qccReply(EarfunQcc.GET_NOISE_CANCEL_MODE, "03"))
        assertEquals(Value.Number(anc("anc")), s.state.values[Command.ANC_MODE])
        val row = s.groups.flatMap { it.settings }.first { it.key == "ef_anc_mode" } as Setting.Choice
        assertEquals("balance", row.value)
        assertEquals(listOf("ff040001000a033a04"), s.change(SettingChange.Choice("ef_anc_mode", "adaptive"))!!.map(::hex))
        assertEquals(listOf("ff040001000a031402"), s.set(Command.ANC_MODE, anc("transparency"))!!.map(::hex))
    }

    @Test
    fun noiseOnTheAir2Nc() {
        val s = EarfunSession(model("EarFun Air 2 NC"))
        s.feed(efReply(EarfunCommand.GET_NOISE, "05"))
        assertEquals(Value.Number(anc("anc")), s.state.values[Command.ANC_MODE])
        val row = s.groups.flatMap { it.settings }.first { it.key == "ef_anc_mode" } as Setting.Choice
        assertEquals("adaptive", row.value)
        assertEquals(listOf("anc", "adaptive", "wind"), row.options)
        assertEquals(listOf("ef2085010203fe"), s.set(Command.ANC_MODE, anc("transparency"))!!.map(::hex))
        // Back to cancelling: the cancelling mode it was in.
        assertEquals(listOf("ef2085010506fe"), s.set(Command.ANC_MODE, anc("anc"))!!.map(::hex))
    }

    @Test
    fun earfunReplies() {
        val s = EarfunSession(model("EarFun Air 2 NC"))
        s.feed(efReply(EarfunCommand.GET_BATTERY, "504b") + efReply(EarfunCommand.GET_VERSION, "010203") + efReply(EarfunCommand.GET_GAME_MODE, "02"))
        assertEquals(Value.Number(80), s.state.values[Command.BATTERY_LEFT])
        assertEquals(Value.Number(75), s.state.values[Command.BATTERY_RIGHT])
        assertEquals(Value.Text("TW220_1.2.3"), s.state.values[Command.FIRMWARE_VERSION])
        assertEquals(Value.Switch(true), s.state.values[Command.GAME_MODE])
        assertEquals(listOf("ef2035010102fe"), s.set(Command.GAME_MODE, 0)!!.map(::hex))
        s.feed(efReply(EarfunCommand.GET_AUTO_OFF, "05"))
        assertEquals("90", (s.groups.flatMap { it.settings }.first { it.key == "auto_off" } as Setting.Choice).value)
        assertEquals(listOf("ef213501ff00fe"), s.change(SettingChange.Choice("auto_off", "never"))!!.map(::hex))
    }

    @Test
    fun touchOnTheFreePro3() {
        val s = EarfunSession(model("EarFun Free Pro 3"))
        // Action bytes at every other byte: left and right one tap, double tap, triple tap, press and hold.
        s.feed(qccReply(EarfunQcc.GET_KEY, "01050104020102020306031011071109"))
        val rows = s.groups.flatMap { it.settings }.filterIsInstance<Setting.Choice>().associate { it.key to it.value }
        assertEquals("next", rows["touch_single_left"])
        assertEquals("previous", rows["touch_single_right"])
        assertEquals("play_pause", rows["touch_double_left"])
        assertEquals("volume_up", rows["touch_double_right"])
        assertEquals("none", rows["touch_triple_right"])
        assertEquals("anc_off", rows["touch_hold_right"])
        assertEquals(listOf("ff040004000a030a00000205"), s.change(SettingChange.Choice("touch_double_right", "next"))!!.map(::hex))
        assertEquals(listOf("ff040004000a030a11070000"), s.change(SettingChange.Choice("touch_hold_left", "game_mode"))!!.map(::hex))
        assertNull(s.change(SettingChange.Choice("touch_hold_left", "noise_control")), "not offered for press and hold")
    }

    @Test
    fun theFramesEarFunsAppSentToAVirtualAirPro4() {
        // data/earfun/emulator_vectors.json: EarFun Audio 20.1.61 against a synthetic Air Pro 4 / TW600 (docs/earfun/VIRTUAL_TESTING.md).
        val v = Json.parseToJsonElement(repoFile("data/earfun/emulator_vectors.json").readText()).jsonObject
        val s = EarfunSession(model("EarFun Air Pro 4"))
        val eq = s.model.eq!!
        val presets = EarfunValues.PRESETS.getValue(eq.presets).toMap()
        // Every preset batch: the ten band frames the app wrote, from the preset's slider positions.
        val missing = mutableListOf<String>()
        for (batch in v.getValue("preset_batches").jsonArray.map { it.jsonObject }) {
            val name = batch.getValue("name").jsonPrimitive.content
            // The app's Default is the flat curve, which PairFree calls Stock.
            val positions = if (name == "Default") List(10) { 0 } else presets[name]
            if (positions == null) { missing += name; continue }
            val frames = batch.getValue("frames").jsonArray.map { it.jsonPrimitive.content }
            assertEquals(frames, s.eqFrames(positions)!!.map(::hex), name)
            assertEquals(batch.getValue("decoded_gains_db").jsonArray.map { it.jsonPrimitive.content.toDouble() }, positions.map(s::gainDb), name)
        }
        assertEquals(emptyList<String>(), missing, "presets the app has and the table lacks")
        assertEquals(30, v.getValue("preset_batches").jsonArray.size)
        // Noise cancelling's sub-modes (033a), the ambient and cancelling icons (0314) and game mode (0312).
        val sent = v.getValue("cases").jsonArray.map { it.jsonObject }.groupBy({ it.getValue("command").jsonPrimitive.content }, { it.getValue("payload").jsonPrimitive.content })
        val n = EarfunSession(model("EarFun Air Pro 4"))
        n.feed(qccReply(EarfunQcc.GET_NOISE_MODE, "01") + qccReply(EarfunQcc.GET_NOISE_CANCEL_MODE, "03") + qccReply(EarfunQcc.GET_GAME_MODE, "00"))
        val row = n.groups.flatMap { it.settings }.first { it.key == "ef_anc_mode" } as Setting.Choice
        val modes = row.options.map { o -> hex(n.change(SettingChange.Choice("ef_anc_mode", o))!!.single()).takeLast(2) }.toSet()
        assertEquals(sent.getValue("033a").toSet(), modes, "the cancelling sub-modes " + row.options)
        assertEquals(sent.getValue("0314").toSet(), setOf(anc("anc"), anc("transparency")).map { hex(n.set(Command.ANC_MODE, it)!!.single()).takeLast(2) }.toSet() + setOf("00").filter { "00" in sent.getValue("0314") })
        assertEquals(sent.getValue("0312").toSet(), listOf(1, 0).map { hex(n.set(Command.GAME_MODE, it)!!.single()).takeLast(2) }.toSet())
        // Gestures (030a): action bytes 03 volume down, 05 next, 02 volume up and 06 the assistant, on the left earbud; the right earbud's
        // four gestures all set to volume down. Read the key table first, so the rows show.
        n.feed(qccReply(EarfunQcc.GET_KEY, "01050104020102020306031011071109"))
        val touch = n.groups.flatMap { it.settings }.filterIsInstance<Setting.Choice>().associate { it.key to it.options }
        fun key(k: String, o: String) = hex(n.change(SettingChange.Choice(k, o)).orEmpty().single()).takeLast(8)
        val gestures = sent.getValue("030a").toSet()
        assertEquals("01030000", key("touch_single_left", "volume_down").also { assertTrue(it in gestures, it) })
        assertEquals("02050000", key("touch_double_left", "next").also { assertTrue(it in gestures, it) })
        assertEquals("03020000", key("touch_triple_left", "volume_up").also { assertTrue(it in gestures, it) })
        assertEquals("00000103", key("touch_single_right", "volume_down").also { assertTrue(it in gestures, it) })
        assertEquals("00001103", key("touch_hold_right", "volume_down").also { assertTrue(it in gestures, it) })
        assertTrue("touch_hold_left" in touch)
        // The sliders' single-band frames (the app's limit tests; a drag can stop short of the end): each is a slider position
        // PairFree writes for that band, the others flat.
        val singles = v.getValue("cases").jsonArray.map { it.jsonObject }.filter { it.getValue("command").jsonPrimitive.content == "0e01" }
        assertTrue(singles.size >= 20)
        for (c in singles) {
            val frame = c.getValue("frame").jsonPrimitive.content
            val band = frame.substring(16, 18).toInt(16) - 0xa1
            val reachable = (-EarfunSession.POSITIONS..EarfunSession.POSITIONS).any { p ->
                s.eqFrames(List(10) { if (it == band) p else 0 })!!.map(::hex)[band] == frame
            }
            assertTrue(reachable, c.getValue("action").jsonPrimitive.content)
        }
    }

    @Test
    fun qualcommEq() {
        val pro3 = EarfunSession(model("EarFun Free Pro 3")).eqFrames(List(10) { 0 })!!.map(::hex)
        assertEquals(10, pro3.size)
        assertEquals("ff040009001d0e01a1fe98005e00000b33", pro3[0])
        assertEquals("ff040009001d0e01aafe98bb8000000b33", pro3[9])
        // The Air Pro 4+'s bands end in 0D (TW700Equailzer).
        assertEquals("ff04000a001d0e01a1fe98005e00000b330d", hex(EarfunSession(model("EarFun Air Pro 4+")).eqFrames(List(10) { 0 })!![0]))
        // The Air S: six bands in dB, then EarFun's four fixed ones (Qualcomm6Equailzer).
        val airS = EarfunSession(model("EarFun Air S")).eqFrames(listOf(3, 0, 0, 0, 0, 0))!!.map(::hex)
        assertEquals(10, airS.size)
        assertEquals("ff040009001d0e01a1fe9800bd00b40b33", airS[0])
        assertEquals("ff040009001d0e01a7fe980063010e0800", airS[6])
    }

    @Test
    fun earfunEq() {
        val nc = EarfunSession(model("EarFun Air 2 NC")).eqFrames(List(10) { 0 })!!.map(::hex)
        assertEquals("ef20950a0a01fe98005e00000b3347fe", nc[0])
        // The Tune Pro's master gain: -8 dB less the highest gain, cut to whole dB (HP220Equailzer): -8 - 1.5 = -9.
        val tune = EarfunSession(model("EarFun Tune Pro"))
        assertEquals("ef20950a0a01fde4005e005a0b33ecfe", hex(tune.eqFrames(listOf(3) + List(9) { 0 })!![0]))
        // The Wave Life's is not cut: -2 - 1.5 = -3.5 (HP201Equailzer).
        val life = EarfunSession(model("EarFun Wave Life")).eqFrames(listOf(3) + List(9) { 0 })!![0]
        assertEquals("ff2e", hex(life.copyOfRange(6, 8)))
        // A gain in dB lands on its slider position.
        assertEquals(listOf(-10, -4, 0, 2, 10, 0, 0, 0, 0, 0), tune.positions(listOf(-6.0, -2.0, 0.0, 1.0, 6.0, 0.0, 0.0, 0.0, 0.0, 0.0)))
        assertNotNull(EarfunSession(model("EarFun OpenJump")).eqFrames(List(10) { 10 }))
    }

    @Test
    fun restartAndPowerOff() {
        val pro5 = EarfunSession(model("EarFun Air Pro 5"))
        assertEquals(listOf("ff040000000a0308"), pro5.change(SettingChange.Press(EarfunSession.RESTART_KEY))!!.map(::hex))
        assertEquals(listOf("ff040001000a035101"), pro5.change(SettingChange.Press(EarfunSession.POWER_OFF_KEY))!!.map(::hex))
        assertTrue(pro5.allowed("ff040000000a0308".hexBytes()) && pro5.allowed("ff040001000a035101".hexBytes()))
        val clip = EarfunSession(model("EarFun Clip"))
        assertEquals(listOf("ef2025010102fe"), clip.change(SettingChange.Press(EarfunSession.POWER_OFF_KEY))!!.map(::hex))
        assertNull(clip.change(SettingChange.Press(EarfunSession.RESTART_KEY)), "EarFun's own frame has no restart")
        assertNull(EarfunSession(model("EarFun Air Pro 4")).change(SettingChange.Press(EarfunSession.POWER_OFF_KEY)), "EarFun's app offers none on the Air Pro 4")
    }

    @Test
    fun soundQuality() {
        val s = EarfunSession(model("EarFun Air Pro 4"))
        s.feed(qccReply(EarfunQcc.GET_QUALITY, "08") + qccReply(EarfunQcc.GET_MULTIPOINT, "00") + qccReply(EarfunQcc.GET_GAME_MODE, "00"))
        val rows = s.groups.flatMap { it.settings }.filterIsInstance<Setting.Toggle>().filter { it.key.startsWith("ef_") }.associate { it.key to it.on }
        assertEquals(mapOf("ef_ldac" to true, "ef_aptx" to false, "ef_aptx_adaptive" to false, "ef_aptx_lossless" to false), rows)
        // aptX Lossless brings aptX and Adaptive with it and turns LDAC off: 1 + 2 + 16.
        assertEquals(listOf("ff040001000a032e13"), s.change(SettingChange.Toggle("ef_aptx_lossless", true))!!.map(::hex))
        // aptX off takes Adaptive and Lossless with it.
        assertEquals(listOf("ff040001000a032e00"), s.change(SettingChange.Toggle("ef_aptx", false))!!.map(::hex))
        // With LDAC on, two devices and game mode cannot go on (the Air Pro 4's flags).
        assertEquals(listOf("ff040001000a032e08"), s.change(SettingChange.Toggle("ef_ldac", true))!!.map(::hex))
        assertNull(s.set(Command.MULTIPOINT, 1))
        assertNull(s.set(Command.GAME_MODE, 1))
        val nc = EarfunSession(model("EarFun Air 2 NC"))
        nc.feed(efReply(EarfunCommand.GET_QUALITY, "00"))
        assertEquals(listOf("ef2105010405fe"), nc.change(SettingChange.Toggle("ef_ldac", true))!!.map(::hex))
    }

    @Test
    fun twoDevicesRestartsTheAirPro4Plus() {
        val s = EarfunSession(model("EarFun Air Pro 4+"))
        s.feed(qccReply(EarfunQcc.GET_MULTIPOINT, "00") + qccReply(EarfunQcc.GET_QUALITY, "01"))
        assertEquals(listOf("ff040001000a032701"), s.set(Command.MULTIPOINT, 1)!!.map(::hex))
        assertEquals(listOf("ff040000000a0326", "ff040000000a0308"), s.takeReadBacks().map(::hex), "the read, then the restart, 3 s later")
    }

    @Test
    fun voiceLanguage() {
        val s = EarfunSession(model("EarFun Clip"))
        s.feed(efReply(EarfunCommand.GET_VOICE_WIZARD, "03"))
        val row = s.groups.flatMap { it.settings }.first { it.key == "ef_voice_language" } as Setting.Choice
        assertEquals("chinese", row.value)
        assertEquals(listOf("english", "japanese", "chinese", "german"), row.options)
        assertEquals(listOf("ef2115010405fe"), s.change(SettingChange.Choice("ef_voice_language", "german"))!!.map(::hex))
        assertTrue(EarfunSession(model("EarFun Air Pro 4")).start().none { hex(it) == "ff040000000a0371" }, "the Qualcomm models' languages are voice packs")
    }

    @Test
    fun callGestures() {
        val s = EarfunSession(model("EarFun Air Pro 5"))
        // The music gestures, the call one and double taps (zero here), then the call triple tap and press and hold, left and right in turn.
        s.feed(qccReply(EarfunQcc.GET_KEY, "01050104020102020306031011071109" + "000000000000000000060005000400 05".replace(" ", "")))
        val rows = s.groups.flatMap { it.settings }.filterIsInstance<Setting.Choice>().associate { it.key to it.value }
        assertEquals("switch_calls", rows["touch_triple_call_left"])
        assertEquals("mic_mute", rows["touch_triple_call_right"])
        assertEquals("decline_call", rows["touch_hold_call_left"])
        assertEquals("mic_mute", rows["touch_hold_call_right"])
        assertEquals(listOf("ff040004000a030a13010000"), s.change(SettingChange.Choice("touch_hold_call_left", "answer_hang_up"))!!.map(::hex))
        // The Clip 2: twelve bytes, the call gestures after the music ones.
        val clip2 = EarfunSession(model("EarFun Clip 2"))
        clip2.feed(efReply(EarfunCommand.GET_KEY, "010203040506070811131214"))
        assertEquals("answer_hang_up", clip2.groups.flatMap { it.settings }.filterIsInstance<Setting.Choice>().first { it.key == "touch_hold_call_left" }.value)
        assertEquals(listOf("ef20750c01020304050607081113121278fe"), clip2.change(SettingChange.Choice("touch_hold_call_right", "mic_mute"))!!.map(::hex))
    }

    @Test
    fun theExtrasEarFunsAppEnablesPerModel() {
        // The Clip 2 (EarFun frame): bass 2340/2345, manner mode, game EQ, DAC and the balance byte; a switch is 2 on and 1 off.
        val clip2 = EarfunSession(model("EarFun Clip 2"))
        val reads = clip2.start().map(::hex)
        for (c in listOf(EarfunCommand.GET_BASS, EarfunCommand.GET_MANNER_MODE, EarfunCommand.GET_GAME_EQ, EarfunCommand.GET_DAC, EarfunCommand.GET_BALANCE)) {
            assertTrue(hex(EarfunFrame(c, ByteArray(0)).bytes()) in reads, "read $c")
        }
        clip2.feed(efReply(EarfunCommand.GET_BASS, "02"))
        clip2.feed(efReply(EarfunCommand.GET_DAC, "01"))
        clip2.feed(efReply(EarfunCommand.GET_BALANCE, "32"))
        val rows = clip2.groups.flatMap { it.settings }
        assertEquals(true, (rows.first { it.key == "ef_extra_bass" } as Setting.Toggle).on)
        assertEquals(false, (rows.first { it.key == "ef_extra_dac" } as Setting.Toggle).on)
        assertEquals(50, (rows.first { it.key == "ef_extra_balance" } as Setting.Level).value)
        assertEquals(listOf(hex(EarfunFrame(EarfunCommand.SET_DAC, byteArrayOf(2)).bytes())), clip2.change(SettingChange.Toggle("ef_extra_dac", true))!!.map(::hex))
        assertEquals(listOf(hex(EarfunFrame(EarfunCommand.SET_BALANCE, byteArrayOf(30)).bytes())), clip2.change(SettingChange.Level("ef_extra_balance", 30))!!.map(::hex))
        // No manner mode answer on the Clip 2 yet, so no row (a setting shows once its read answered).
        assertTrue(rows.none { it.key == "ef_extra_manner" })

        // The Wave Pro X (Qualcomm): USB charging and the call environment are 1 and 0.
        val x = EarfunSession(model("EarFun Wave Pro X"))
        x.feed(qccReply(EarfunQcc.GET_USB_CHARGE, "01"))
        x.feed(qccReply(EarfunQcc.GET_BALANCE, "28"))
        val xRows = x.groups.flatMap { it.settings }
        assertEquals(true, (xRows.first { it.key == "ef_extra_usb_charge" } as Setting.Toggle).on)
        assertEquals(40, (xRows.first { it.key == "ef_extra_balance" } as Setting.Level).value)
        assertEquals(listOf("ff040001000a0363" + "00"), x.change(SettingChange.Toggle("ef_extra_usb_charge", false))!!.map(::hex))
        // The voice prompts switch (Qualcomm 33/32) and the codec in use (77, 1 to 7), shown by name.
        x.feed(qccReply(EarfunQcc.GET_VOICE_TIP, "01"))
        x.feed(qccReply(EarfunQcc.GET_AUDIO_MODE, "07"))
        val x2 = x.groups.flatMap { it.settings }
        assertEquals(true, (x2.first { it.key == "ef_extra_voice_tip" } as Setting.Toggle).on)
        assertEquals("LDAC", (x2.first { it.key == "ef_codec_in_use" } as Setting.Choice).value)
        assertEquals(listOf("ff040001000a0320" + "00"), x.change(SettingChange.Toggle("ef_extra_voice_tip", false))!!.map(::hex))
        // A model without them has no rows.
        val air = EarfunSession(model("EarFun Air Pro 3"))
        air.feed(qccReply(EarfunQcc.GET_BALANCE, "28"))
        assertTrue(air.groups.flatMap { it.settings }.none { it.key.startsWith("ef_extra_") })
    }

    @Test
    fun theNoiseModesAKeyCyclesThrough() {
        // EarFun's app (setKeyCustomNoise): a byte for the left key, one for the right; 1 off, 2 ambient, 4 noise cancelling, 8 wind, 16 adaptive.
        val s = EarfunSession(model("EarFun Air Pro 4i"))
        assertTrue(hex(EarfunFrame(EarfunCommand.GET_KEY_CUSTOM_NOISE, ByteArray(0)).bytes()) in s.start().map(::hex))
        s.feed(efReply(EarfunCommand.GET_KEY_CUSTOM_NOISE, "0705"))
        val rows = s.groups.flatMap { it.settings }.filterIsInstance<Setting.Toggle>().filter { it.key.startsWith("ef_cycle_") }
        assertTrue(rows.isNotEmpty())
        assertEquals(true, rows.first { it.key == "ef_cycle_left_off" }.on)
        assertEquals(false, rows.first { it.key == "ef_cycle_right_ambient" }.on)
        assertEquals(listOf(hex(EarfunFrame(EarfunCommand.SET_KEY_CUSTOM_NOISE, byteArrayOf(5, 5)).bytes())), s.change(SettingChange.Toggle("ef_cycle_left_ambient", false))!!.map(::hex))
        // Turning off the last mode of a key is refused.
        s.change(SettingChange.Toggle("ef_cycle_right_off", false))
        assertNull(s.change(SettingChange.Toggle("ef_cycle_right_anc", false)))
    }

    @Test
    fun headphoneButtons() {
        val life = EarfunSession(model("EarFun Wave Life"))
        life.feed(efReply(EarfunCommand.GET_KEY, "020504030800"))
        val rows = life.groups.flatMap { it.settings }.filterIsInstance<Setting.Choice>().associate { it.key to it.value }
        assertEquals("volume_up", rows["ef_button_plus_single"])
        assertEquals("noise_control", rows["ef_button_nc_single"])
        assertEquals("none", rows["ef_button_nc_hold"])
        assertEquals(listOf("ef20750602050403080723fe"), life.change(SettingChange.Choice("ef_button_nc_hold", "game_mode"))!!.map(::hex))
        val x = EarfunSession(model("EarFun Wave Pro X"))
        x.feed(qccReply(EarfunQcc.GET_KEY, "020005030004090d0d500050"))
        val xRows = x.groups.flatMap { it.settings }.filterIsInstance<Setting.Choice>().associate { it.key to it.value }
        assertEquals("multipoint", xRows["ef_button_nc_hold"])
        assertEquals("mic_mute", xRows["ef_button_nc_hold_call"])
        // The noise button's call gestures go as button 81 (HP1000ProtocolParser.setKeyMode).
        assertEquals(listOf("ff040003000a030a515209"), x.change(SettingChange.Choice("ef_button_nc_hold_call", "anc_off"))!!.map(::hex))
        assertEquals(listOf("ff040003000a030a010304"), x.change(SettingChange.Choice("ef_button_plus_hold", "previous"))!!.map(::hex))
    }
}
