package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.Command
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The six older Qualcomm models' C-Chip frames over GATT (docs/skullcandy/PROTOCOL.md section 17). */
class SkullcandyQualcommTest {
    private fun hex(b: ByteArray) = b.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }
    private fun talk(name: String) = SkullcandyQualcommTalk(SkullcandyModels.leFromName(name)!!)
    private fun number(label: String) = Command.ANC_MODE.labels.entries.first { it.value == label }.key

    @Test
    fun theFramesAreTheAppsOwn() {
        // the command word, the type, the length (header and payload) and the payload
        assertEquals("F8 FF 07 05 01", hex(SkullcandyCChip.frame(SkullcandyCChip.EQ_SET, 1)))
        assertEquals("F8 FF 08 04", hex(SkullcandyCChip.frame(SkullcandyCChip.EQ_GET)))
        assertEquals("F8 FF 01 05 02", hex(SkullcandyCChip.frame(SkullcandyCChip.AMBIENT_SET, 2)))
        val r = SkullcandyCChip.parse(SkullcandyCChip.frame(9, 80) + SkullcandyCChip.frame(10, 75))
        assertEquals(listOf(9, 10), r.map { it.type })
        assertEquals(listOf(80, 75), r.map { it.payload.single().toInt() })
        // a frame that is cut short or of another command is not read
        assertTrue(SkullcandyCChip.parse(byteArrayOf(0xF8.toByte(), 0xFF.toByte(), 9, 6, 1)).isEmpty())
        assertTrue(SkullcandyCChip.parse(byteArrayOf(0x00, 0x0A, 9, 5, 1)).isEmpty())
    }

    @Test
    fun theSixModelsUseTheLeChannel() {
        val models = SkullcandyProducts.ALL.filter { it.usesQualcomm }
        assertEquals(6, models.size)
        assertTrue(models.all { it.service == null && !it.usesAt && !it.usesBes && !it.usesRpc && !it.usesRail })
    }

    @Test
    fun aNameTwoProductsShareIsToldByTheChannelThatOpens() {
        // the classic Airoha twin is the product by name; the LE entry is the sibling the driver falls back to
        for (name in listOf("Crusher Evo", "Crusher 540 Active", "Indy Evo", "Indy Fuel")) {
            assertEquals("airoha", SkullcandyModels.fromName(name)!!.chip, name)
            assertEquals("qualcomm", SkullcandyModels.leFromName(name)!!.chip, name)
        }
        // the two with no classic twin are the LE entry by name
        for (name in listOf("Crusher ANC", "Push Ultra")) {
            assertEquals("qualcomm", SkullcandyModels.fromName(name)!!.chip, name)
            assertEquals("qualcomm", SkullcandyModels.leFromName(name)!!.chip, name)
        }
        assertNull(SkullcandyModels.leFromName("Grind"))
    }

    @Test
    fun onlyTheModelsFeaturesAreAsked() {
        val indy = talk("Indy Evo")
        assertEquals(listOf("F8 FF 09 04", "F8 FF 0A 04", "F8 FF 08 04", "F8 FF 02 04"), indy.start().map { hex(it) })
        assertTrue(indy.start().all { indy.allowed(it) })
        val ultra = talk("Push Ultra")
        assertEquals(listOf("F8 FF 09 04", "F8 FF 0A 04", "F8 FF 08 04"), ultra.start().map { hex(it) })
        val crusher = talk("Crusher ANC")
        assertEquals(2, crusher.start().size)
        assertFalse(crusher.hasEq)
    }

    @Test
    fun repliesFillTheBatteryTheAmbientModeAndThePreset() {
        val t = talk("Indy Evo")
        t.start()
        t.feed(SkullcandyCChip.frame(9, 80))
        t.feed(SkullcandyCChip.frame(10, 60))
        t.feed(SkullcandyCChip.frame(2, 2))
        t.feed(SkullcandyCChip.frame(8, 2))
        assertEquals(80, t.state.number(Command.BATTERY_LEFT))
        assertEquals(60, t.state.number(Command.BATTERY_RIGHT))
        assertEquals("transparency", Command.ANC_MODE.labels[t.state.number(Command.ANC_MODE)])
        assertEquals("Podcast", t.eqPresetName)
        // a value outside what the writes send is ignored
        t.feed(SkullcandyCChip.frame(8, 9))
        assertEquals("Podcast", t.eqPresetName)
        t.feed(SkullcandyCChip.frame(9, 250))
        assertEquals(80, t.state.number(Command.BATTERY_LEFT))
    }

    @Test
    fun writesAreTheAppsActivateFeatureFrames() {
        val t = talk("Indy Evo")
        val on = t.set(Command.ANC_MODE, number("transparency"))!!.single()
        assertEquals("F8 FF 01 05 02", hex(on))
        assertTrue(t.allowed(on))
        val off = t.set(Command.ANC_MODE, number("off"))!!.single()
        assertEquals("F8 FF 01 05 01", hex(off))
        assertNull(t.set(Command.ANC_MODE, number("anc")))
        for ((i, name) in listOf("Music", "Podcast", "Movie").withIndex()) {
            val f = t.eqPreset(name)!!.single()
            assertEquals("F8 FF 07 05 0${i + 1}", hex(f))
            assertTrue(t.allowed(f))
        }
        assertNull(t.eqPreset("Bass Boost"))
        // nothing outside the app's own frames goes out
        assertFalse(t.allowed(SkullcandyCChip.frame(7, 4)))
        assertFalse(t.allowed(SkullcandyCChip.frame(3, 1)))
        assertFalse(talk("Push Ultra").allowed(SkullcandyCChip.frame(1, 2)))
        val band = SkullcandyQualcommBandEq(t)
        assertEquals(emptyList<Int>(), band.eqBandsHz)
        assertEquals(listOf("Music", "Podcast", "Movie"), band.eqPresetNames)
        assertNull(band.eqCustomFrames(listOf(0)))
    }
}
