package io.github.omegadestroyer.soundpeter.protocol

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.roundToInt

class EqLayoutTest {
    private val cases = loadData("test_vectors.json").getValue("eq_layout").jsonArray.map { it.jsonObject }

    @Test
    fun sameBytesAsTheOfficialApp() {
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val layout = Models.find(case.str("model"), null)?.eq ?: error("no EQ layout for ${case.str("model")}")
            val gains = case.getValue("user_gains_db").jsonArray.map { it.jsonPrimitive.int }
            val expected = case.getValue("payloads").jsonArray.map { it.jsonPrimitive.content }
            assertEquals(expected, layout.payloads(gains).map { it.hex() }, case.str("name"))
        }
    }

    @Test
    fun presetsSameBytesAsTheOfficialApp() {
        val presets = loadData("test_vectors.json").getValue("eq_preset").jsonArray.map { it.jsonObject }
        assertTrue(presets.isNotEmpty())
        for (case in presets) {
            val layout = Models.find(case.str("model"), null)?.eq ?: error("no EQ layout for ${case.str("model")}")
            val preset = layout.presets.single { it.name == case.str("preset") }
            val expected = case.getValue("payloads").jsonArray.map { it.jsonPrimitive.content }
            assertEquals(expected, layout.payloads(preset).map { it.hex() }, preset.name)
        }
    }

    @Test
    fun everySliderPositionIsExact() {
        // Gains travel as dB x 60, truncated. Base curve plus whole-dB steps must never lose a unit to rounding.
        for (model in Models.ALL) {
            val layout = model.eq ?: continue
            val range = -layout.userRangeDb..layout.userRangeDb
            for (preamp in -6..0) {
                for (user in range) {
                    val payloads = layout.payloads(List(layout.bands.size) { user }, preamp)
                    payloads.forEachIndexed { i, p ->
                        val master = p.u16(1).toShort().toInt()
                        val gain = p.u16(5).toShort().toInt()
                        assertEquals(((layout.masterGainDb + preamp) * 60).roundToInt(), master, "${model.code} master, preamp $preamp")
                        assertEquals(((layout.bands[i].gainDb + user) * 60).roundToInt(), gain, "${model.code} band ${i + 1}, user $user")
                    }
                }
            }
        }
    }

    @Test
    fun framesUseTheEqVendor() {
        val layout = Models.find("S5", null)!!.eq!!
        val frames = layout.frames(List(layout.bands.size) { 0 }).map { FrameParser().feed(it).single() }
        assertEquals(layout.bands.size, frames.size)
        assertTrue(frames.all { it.vendor == Wire.VENDOR_EQ && it.command == Wire.EQ_COMMAND_QUALCOMM })
    }
}
