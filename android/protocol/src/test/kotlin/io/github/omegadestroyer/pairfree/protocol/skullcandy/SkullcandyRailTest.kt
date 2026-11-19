package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.jbl.JblRace
import io.github.omegadestroyer.pairfree.protocol.jbl.RacePacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The Rail and Rail ANC: everything over Airoha's RACE (docs/skullcandy/PROTOCOL.md section 16). */
class SkullcandyRailTest {
    private fun hex(b: ByteArray) = b.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }
    private fun anc() = SkullcandyRaceTalk(SkullcandyModels.fromName("Rail ANC")!!)
    private fun rail() = SkullcandyRaceTalk(SkullcandyModels.fromName("Rail")!!)

    private fun response(id: Int, vararg payload: Int, type: Int = JblRace.TYPE_RESPONSE) = RacePacket(id, ByteArray(payload.size) { payload[it].toByte() }, type).bytes()

    private fun number(label: String) = Command.ANC_MODE.labels.entries.first { it.value == label }.key

    @Test
    fun theRailsRunOnAirohasServiceAndTheirRaceTalkIsTheMainOne() {
        for (name in listOf("Rail", "Rail ANC")) {
            val p = SkullcandyModels.fromName(name)!!
            assertTrue(p.usesRail)
            assertEquals(AIROHA_SERVICE, p.service)
            assertTrue(SkullcandyRaceTalk(p).primary)
        }
        assertFalse(SkullcandyRaceTalk(SkullcandyModels.fromName("Grind")!!).primary)
        // the second-channel talk of an AT model is not the main one and starts as before
        assertTrue(SkullcandyRaceTalk(SkullcandyModels.fromName("Grind")!!).start().none { it.id == JblRace.BATTERY })
    }

    @Test
    fun everyOpeningReadIsOneItMaySend() {
        val t = anc()
        val start = t.start()
        assertTrue(start.all { t.allowed(it) })
        assertEquals("05 5A 03 00 06 00 01", hex(start.first().bytes()))
        assertTrue(start.any { it.id == JblRace.MMI_GET && it.module == JblRace.MMI_ANC })
        assertTrue(rail().start().any { it.id == JblRace.MMI_GET })
    }

    @Test
    fun theBatteriesAndTheModeComeFromTheEarbuds() {
        val t = anc()
        t.start()
        t.feed(response(JblRace.AGENT_CHANNEL, 0, 1))
        t.feed(response(JblRace.BATTERY, 0, 0, 80))
        t.feed(response(JblRace.CASE_BATTERY, 0, 60))
        // the partner's battery arrives unasked, as a module indication
        t.feed(response(JblRace.MMI_GET, JblRace.NOTIFY_PARTNER_BATTERY and 0xFF, JblRace.NOTIFY_PARTNER_BATTERY shr 8, 0, 70, type = 0x5D))
        assertEquals(80, t.state.number(Command.BATTERY_RIGHT))
        assertEquals(70, t.state.number(Command.BATTERY_LEFT))
        assertEquals(60, t.state.number(Command.BATTERY_CASE))
        t.feed(response(JblRace.MMI_GET, 5, 0, 0, 2))
        assertEquals("anc", Command.ANC_MODE.labels[t.state.number(Command.ANC_MODE)])
        // the strength of the mode in force is read when the mode is known
        assertTrue(t.takeOutbox().any { hex(it.bytes()) == SkullcandyRace.GAIN_GET })
        t.feed(response(JblRace.MMI_GET, 5, 0, 0, 10))
        assertEquals("transparency", Command.ANC_MODE.labels[t.state.number(Command.ANC_MODE)])
        t.feed(response(JblRace.MMI_GET, 5, 0, 0, 0))
        assertEquals("off", Command.ANC_MODE.labels[t.state.number(Command.ANC_MODE)])
    }

    @Test
    fun theModesAreWrittenAsTheSdkWritesThem() {
        val t = anc()
        val on = t.set(Command.ANC_MODE, number("anc"))!!.single()
        assertEquals("05 5A 07 00 06 0E 00 0A 03 00 01", hex(on.bytes()))
        assertTrue(t.allowed(on))
        val tr = t.set(Command.ANC_MODE, number("transparency"))!!.single()
        assertEquals("05 5A 07 00 06 0E 00 0A 04 04 01", hex(tr.bytes()))
        val off = t.set(Command.ANC_MODE, number("off"))!!.single()
        assertEquals("05 5A 05 00 06 0E 00 0B 01", hex(off.bytes()))
        assertTrue(t.allowed(off))
        assertEquals(1, t.takeReadBacks().size)
    }

    @Test
    fun theRailHasTransparencyOnly() {
        val t = rail()
        assertNull(t.set(Command.ANC_MODE, number("anc")))
        assertEquals("05 5A 07 00 06 0E 00 0A 04 04 01", hex(t.set(Command.ANC_MODE, number("transparency"))!!.single().bytes()))
        t.feed(response(JblRace.MMI_GET, 5, 0, 0, 2))
        // byte 2 is a noise cancelling value, which this model has no mode for
        assertEquals("off", Command.ANC_MODE.labels[t.state.number(Command.ANC_MODE)])
    }

    @Test
    fun theOtherHeadsKeepTheirOwnBehaviour() {
        val t = SkullcandyRaceTalk(SkullcandyModels.fromName("Grind")!!)
        assertNull(t.set(Command.ANC_MODE, number("anc")))
        assertTrue(t.poll().isEmpty())
    }
}
