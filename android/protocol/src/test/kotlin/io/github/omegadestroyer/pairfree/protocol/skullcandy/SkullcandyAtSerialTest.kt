package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.Command
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The Airoha models without an SDK configuration are reached by the app's own AT client over the standard serial port (docs/skullcandy/PROTOCOL.md section 9.4). */
class SkullcandyAtSerialTest {
    private val product = SkullcandyModels.fromName("Hesh 540 ANC")!!

    @Test
    fun thirtyTwoAirohaModelsUseTheStandardSerialPort() {
        assertTrue(product.usesAtSerial)
        assertEquals(32, SkullcandyProducts.ALL.count { it.usesAtSerial })
        assertTrue(SkullcandyProducts.ALL.none { it.usesAtSerial && it.usesAt })
        // no vendor service: the channel is the standard serial port
        assertNull(product.service)
        assertEquals(SkullcandyServices.AT, SkullcandyModels.fromName("Grind")!!.service)
    }

    @Test
    fun theSessionSendsPlainLinesAndReadsPlainReplies() {
        val s = SkullcandyAtSession(product)
        assertEquals(listOf(SkullcandyAt.BATTERY, SkullcandyAt.ANC, SkullcandyAt.TRANSPARENCY), s.start().take(3))
        assertEquals("AT.UIAUDIO=anc\r\n", s.bytes(SkullcandyAt.ANC).decodeToString())
        s.feed("+UIAUDIO:on\r\nOK\r\n".encodeToByteArray())
        s.bytes(SkullcandyAt.TRANSPARENCY)
        s.feed("+UIAUDIO:off\r\nOK\r\n".encodeToByteArray())
        assertEquals("anc", Command.ANC_MODE.labels[s.state.number(Command.ANC_MODE)])
    }
}
