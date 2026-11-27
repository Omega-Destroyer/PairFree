package io.github.omegadestroyer.pairfree.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** SoundPEATS' and the Capsule's EQ through [BandEq]: the Sound tab's layout is the model's own, and the frames are the layout's byte for byte. */
class CurveBandEqTest {
    private val modelsWithEq = Models.ALL.filter { it.eq != null }

    @Test
    fun theLayoutThroughBandEqIsTheModelsOwn() {
        assertTrue(modelsWithEq.isNotEmpty())
        for (m in modelsWithEq) {
            val layout = m.eq!!
            val rebuilt = SoundpeatsBandEq(layout).toLayout()
            assertEquals(layout.copy(command = 0), rebuilt.copy(command = 0), m.code)
        }
    }

    @Test
    fun soundpeatsFramesAreTheLayoutsFrames() {
        for (m in modelsWithEq) {
            val layout = m.eq!!
            val eq = SoundpeatsBandEq(layout)
            val gains = List(layout.bands.size) { (it % 5) - 2 }
            assertEquals(layout.frames(gains, -2).map { it.toList() }, eq.eqCustomFrames(gains, -2)!!.map { it.toList() }, m.code)
            for (p in layout.presets) {
                assertEquals(layout.frames(p, -1).map { it.toList() }, eq.eqPresetFrames(p.name, -1)!!.map { it.toList() }, "${m.code} ${p.name}")
            }
        }
    }

    @Test
    fun theCapsuleSendsTheWholeCurveInOneMessageWithThePreampOnTheMasterGain() {
        val layout = modelsWithEq.first().eq!!
        val eq = CapsuleBandEq(layout)
        val gains = List(layout.bands.size) { 1 }
        val flat = eq.eqCustomFrames(gains, 0)!!
        assertEquals(1, flat.size)
        assertEquals(flat.single().toList(), eq.eqCustomFrames(gains)!!.single().toList())
        assertTrue(flat.single().toList() != eq.eqCustomFrames(gains, -3)!!.single().toList())
    }
}
