package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.jbl.RacePacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkullcandyAtOverRaceTest {
    private val product = SkullcandyModels.fromName("Hesh 540 ANC")!!

    private fun notification(text: String, tag: Boolean = true): ByteArray =
        RacePacket(SkullcandyAtSession.TUNNEL_ID, (if (tag) byteArrayOf(0x05, 0xFF.toByte()) else ByteArray(0)) + text.encodeToByteArray(), 0x5D).bytes()

    @Test
    fun theModelsWithoutAnSdkConfigurationUseTheRaceTunnel() {
        assertTrue(product.usesAtOverRace)
        assertEquals(32, SkullcandyProducts.ALL.count { it.usesAtOverRace })
        assertTrue(SkullcandyProducts.ALL.none { it.usesAtOverRace && it.usesAt })
    }

    @Test
    fun aLineGoesOutAsTheTunnelCommand() {
        val s = SkullcandyAtSession(product, viaRace = true)
        val frame = s.bytes(SkullcandyAt.ANC)
        val line = "AT.UIAUDIO=anc\r\n".encodeToByteArray()
        // 05 5A <length u16 LE: tag, id and line> 01 0D 05 FF <line>
        val expected = byteArrayOf(0x05, 0x5A, (line.size + 4).toByte(), 0x00, 0x01, 0x0D, 0x05, 0xFF.toByte()) + line
        assertEquals(expected.toList(), frame.toList())
    }

    @Test
    fun theAnswerTextComesBackInNotifications() {
        val s = SkullcandyAtSession(product, viaRace = true)
        s.bytes(SkullcandyAt.ANC)
        assertTrue(s.feed(notification("+UIAUDIO:on\r\n")).answers.isEmpty())
        s.feed(notification("OK\r\n"))
        s.bytes(SkullcandyAt.TRANSPARENCY)
        s.feed(notification("+UIAUDIO:off\r\nOK\r\n", tag = false))
        assertEquals("anc", Command.ANC_MODE.labels[s.state.number(Command.ANC_MODE)])
    }
}
