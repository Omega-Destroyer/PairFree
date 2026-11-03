package io.github.omegadestroyer.pairfree.protocol.earfun

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.Value
import io.github.omegadestroyer.pairfree.protocol.hexBytes
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
                for ((gesture, options) in t.gestures) assertTrue(options.all { it in actions }, "${m.name} $gesture")
                if (m.qualcomm) assertEquals(EarfunModel.QUALCOMM, t.map, m.name)
            }
            m.eq?.let { e ->
                assertEquals(e.hz.size, e.q.size, m.name)
                val presets = EarfunValues.PRESETS.getValue(e.presets)
                assertTrue(presets.all { it.second.size == e.hz.size }, m.name)
                assertTrue(e.master.byPeak == null || e.master.byPeak!!.size == 2 * EarfunSession.POSITIONS + 1, m.name)
            }
        }
        // EarFun's power off, factory reset, restart and renaming are never sent; nor are the reads of phones' names and addresses.
        assertTrue(EarfunQcc.RESET in EarfunQcc.NEVER_SEND && EarfunQcc.RESTART in EarfunQcc.NEVER_SEND && EarfunQcc.SET_CONTROL_PLAY in EarfunQcc.NEVER_SEND)
        assertTrue(EarfunCommand.RESET in EarfunCommand.NEVER_SEND && EarfunCommand.SET_CONTROL_PLAY in EarfunCommand.NEVER_SEND)
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
        assertFalse(s.allowed("ff040001000a035101".hexBytes()), "power off")
        assertFalse(s.allowed("ff040000000a032c".hexBytes()), "the phones' names and addresses")
        assertFalse(s.allowed("ff040001001d000706".hexBytes()), "firmware update's notifications")
        assertFalse(s.allowed("ff040000001d0600".hexBytes()), "another GAIA v3 command")
        val e = EarfunSession(model("EarFun Air 2 NC"))
        assertFalse(e.allowed("ef100002afa556fe".hexBytes()), "reset")
        assertFalse(e.allowed(EarfunFrame(EarfunCommand.SET_CONTROL_PLAY, byteArrayOf(1)).bytes()), "power off")
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
}
