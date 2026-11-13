package io.github.omegadestroyer.pairfree.protocol

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
            // 0E02 numbers a band with two bytes where 0E01 has one.
            val at = if (layout.command == Wire.EQ_COMMAND_QUALCOMM) 1 else 2
            val range = -layout.userRangeDb..layout.userRangeDb
            for (preamp in -6..0) {
                for (user in range) {
                    val payloads = layout.payloads(List(layout.bands.size) { user }, preamp)
                    assertEquals(layout.bandCount, payloads.size, model.code)
                    payloads.forEachIndexed { i, p ->
                        val master = p.u16(at).toShort().toInt()
                        val gain = p.u16(at + 4).toShort().toInt()
                        val expected = layout.fixedBands.getOrNull(i)?.gainDb ?: (layout.bands[i - layout.fixedBands.size].gainDb + user)
                        assertEquals(((layout.masterGainDb + preamp) * 60).roundToInt(), master, "${model.code} master, preamp $preamp")
                        assertEquals((expected * 60).roundToInt(), gain, "${model.code} band ${i + 1}, user $user")
                    }
                }
            }
        }
    }

    @Test
    fun halfStepsAreExactAndWholeStepsStayTheSame() {
        // Owner, 2026-11-13: half-dB steps where the wire takes them. A whole-dB list written as doubles is the same bytes as before,
        // and each half step moves the gain by exactly 30 units of the wire's 1/60 dB.
        for (model in Models.ALL) {
            val layout = model.eq ?: continue
            val at = if (layout.command == Wire.EQ_COMMAND_QUALCOMM) 1 else 2
            for (user in -layout.userRangeDb..layout.userRangeDb) {
                assertEquals(
                    layout.payloads(List(layout.bands.size) { user }).map { it.hex() },
                    layout.payloads(List(layout.bands.size) { user.toDouble() }).map { it.hex() },
                    model.code,
                )
            }
            val flat = layout.payloads(List(layout.bands.size) { 0.0 })
            val half = layout.payloads(List(layout.bands.size) { 0.5 })
            half.forEachIndexed { i, p ->
                if (i < layout.fixedBands.size) return@forEachIndexed
                assertEquals(30, p.u16(at + 4).toShort().toInt() - flat[i].u16(at + 4).toShort().toInt(), "${model.code} band ${i + 1}")
            }
        }
    }

    @Test
    fun fixedBandsGoFirstAndPresetsDrawOnlyTheirOwn() {
        // The Clip C10 (issue #9): TRUEFREE's curve, then the user's eight bands.
        val layout = Models.find("T2", null)!!.eq!!
        assertEquals(8, layout.fixedBands.size)
        assertEquals(8, layout.bands.size)
        val sent = layout.payloads(List(8) { if (it == 0) 3 else 0 })
        assertEquals(16, sent.size)
        assertEquals(listOf(16, 1), listOf(sent[0][0].toInt(), sent[0][1].toInt()))
        assertEquals(listOf(16, 9), listOf(sent[8][0].toInt(), sent[8][1].toInt()))
        assertEquals(180, sent[8].u16(6).toShort().toInt(), "the first user band at +3 dB")
        for (preset in layout.presets) assertEquals(8, layout.ownBands(preset).size, preset.name)
        // Models without fixed bands draw a preset whole.
        val h3 = Models.find("S5", null)!!.eq!!
        assertEquals(h3.presets.first().bands, h3.ownBands(h3.presets.first()))
    }

    @Test
    fun framesUseTheEqVendor() {
        val layout = Models.find("S5", null)!!.eq!!
        val frames = layout.frames(List(layout.bands.size) { 0 }).map { FrameParser().feed(it).single() }
        assertEquals(layout.bands.size, frames.size)
        assertTrue(frames.all { it.vendor == Wire.VENDOR_EQ && it.command == Wire.EQ_COMMAND_QUALCOMM })
    }
}
