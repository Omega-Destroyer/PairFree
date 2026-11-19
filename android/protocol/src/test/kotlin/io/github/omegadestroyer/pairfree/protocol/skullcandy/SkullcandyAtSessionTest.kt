package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkullcandyAtSessionTest {
    private fun session(name: String) = SkullcandyAtSession(SkullcandyModels.fromName(name)!!)

    private fun answer(s: SkullcandyAtSession, sent: String, reply: String) {
        s.bytes(sent)
        s.feed(reply.encodeToByteArray())
    }

    @Test
    fun theCrusherAnc2OpensWithBatteryNoiseCancellingAndTransparency() {
        val s = session("Crusher ANC 2")
        assertEquals(listOf(SkullcandyAt.BATTERY, SkullcandyAt.ANC, SkullcandyAt.TRANSPARENCY), s.start())
        assertTrue(s.hasAnc && s.hasTransparency)
    }

    @Test
    fun aGrindHasTransparencyAndNoNoiseCancelling() {
        val s = session("Grind")
        assertEquals(listOf(SkullcandyAt.BATTERY, SkullcandyAt.TRANSPARENCY), s.start())
        assertFalse(s.hasAnc)
    }

    @Test
    fun repliesAreReadAgainstTheLineThatAskedForThem() {
        val s = session("Crusher ANC 2")
        answer(s, SkullcandyAt.BATTERY, ".SYSINFO:battery\tright:70\tleft:65\r\nOK\r\n")
        assertEquals(65, s.state.batteryLeft)
        assertEquals(70, s.state.batteryRight)
        answer(s, SkullcandyAt.ANC, "+UIAUDIO:on\r\nOK\r\n")
        assertNull(s.state.number(Command.ANC_MODE))
        answer(s, SkullcandyAt.TRANSPARENCY, "+UIAUDIO:off\r\nOK\r\n")
        assertEquals("anc", Command.ANC_MODE.labels[s.state.number(Command.ANC_MODE)])
    }

    @Test
    fun aReplyArrivingInPiecesIsHeldUntilItsOk() {
        val s = session("Grind")
        s.bytes(SkullcandyAt.TRANSPARENCY)
        assertFalse(s.feed("+UIAUDIO:on,4".encodeToByteArray()).changed)
        val fed = s.feed("0\r\nOK\r\n".encodeToByteArray())
        assertTrue(fed.changed)
        assertEquals("transparency", Command.ANC_MODE.labels[s.state.number(Command.ANC_MODE)])
    }

    @Test
    fun changingTheModeSendsTheAppsLinesAndReadsBack() {
        val s = session("Crusher ANC 2")
        answer(s, SkullcandyAt.ANC, "+UIAUDIO:off\r\nOK\r\n")
        answer(s, SkullcandyAt.TRANSPARENCY, "+UIAUDIO:off\r\nOK\r\n")
        val anc = Command.ANC_MODE.labels.entries.first { it.value == "anc" }.key
        assertEquals(listOf("AT.UIAUDIO=anc,on", "AT.UIAUDIO=transparency,off"), s.set(Command.ANC_MODE, anc))
        assertEquals("anc", Command.ANC_MODE.labels[s.state.number(Command.ANC_MODE)])
        assertEquals(listOf(SkullcandyAt.ANC, SkullcandyAt.TRANSPARENCY), s.takeReadBacks())
        val off = Command.ANC_MODE.labels.entries.first { it.value == "off" }.key
        assertEquals(listOf("AT.UIAUDIO=anc,off", "AT.UIAUDIO=transparency,off"), s.set(Command.ANC_MODE, off))
        val transparency = Command.ANC_MODE.labels.entries.first { it.value == "transparency" }.key
        assertNull(session("Grind").set(Command.ANC_MODE, Command.ANC_MODE.labels.entries.first { it.value == "anc" }.key))
        assertEquals(listOf("AT.UIAUDIO=transparency,on"), session("Grind").set(Command.ANC_MODE, transparency))
    }

    @Test
    fun anErrorIsAnAnswerThatChangesNothing() {
        val s = session("Grind")
        s.bytes(SkullcandyAt.TRANSPARENCY)
        val fed = s.feed("ERROR\r\n".encodeToByteArray())
        assertFalse(fed.changed)
        assertEquals(1, fed.answers.size)
        assertNull(s.state.number(Command.ANC_MODE))
    }

    @Test
    fun theTransparencyLevelAndGamingModeAreRowsOnceTheyAnswer() {
        val s = session("Grind")
        assertTrue(s.groups.isEmpty())
        answer(s, SkullcandyAt.TRANSPARENCY, "+UIAUDIO:on,40\r\nOK\r\n")
        val level = s.groups.single { it.key == "noise" }.settings.single() as Setting.Level
        assertEquals(4, level.value)
        assertEquals(listOf("AT.UIAUDIO=transparency,,70"), s.change(SettingChange.Level(SkullcandyAtSession.TRANSPARENCY_LEVEL, 7)))
        assertEquals(listOf(SkullcandyAt.TRANSPARENCY), s.takeReadBacks())
        if (s.hasGamingMode) {
            assertTrue(SkullcandyAt.LATENCY in s.start())
            answer(s, SkullcandyAt.LATENCY, "+LATENCY:off\r\nOK\r\n")
            assertEquals(listOf("AT.LATENCY=gaming_mode,on"), s.change(SettingChange.Toggle(SkullcandyAtSession.GAMING_MODE, true)))
        }
        assertNull(s.change(SettingChange.Toggle("sp_other", true)))
    }
}
