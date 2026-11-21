package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.jbl.JblRace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Airoha models without an SDK configuration are driven through Airoha's SDK alone, over Airoha's serial service; the app's AT client serves only the four models
 * `getShouldUseATCommands` names (docs/skullcandy/PROTOCOL.md section 9.4, corrected 2026-11-20 after issue #66).
 */
class SkullcandyAirohaSdkTest {
    private val dime = SkullcandyModels.fromName("Dime Evo")!!

    @Test
    fun twentyEightAirohaModelsUseAirohasSerialService() {
        assertTrue(dime.usesAirohaSdk)
        assertEquals(28, SkullcandyProducts.ALL.count { it.usesAirohaSdk })
        assertTrue(SkullcandyProducts.ALL.filter { it.name.startsWith("Session") }.none { it.usesAirohaSdk })
        assertTrue(SkullcandyProducts.ALL.none { it.usesAirohaSdk && it.usesAt })
        // the app's AT client: Grind, Grind Fuel, Push Active and Crusher ANC 2 only
        assertEquals(setOf("GRIND", "GRIND_FUEL", "PUSH_ACTIVE", "CHP200"), SkullcandyProducts.ALL.filter { it.usesAt }.mapNotNull { it.sdk }.toSet())
        assertEquals(AIROHA_SPP, dime.service)
        assertEquals(SkullcandyServices.AT, SkullcandyModels.fromName("Grind")!!.service)
    }

    @Test
    fun theServiceIsTheAirohaOneTheDeviceLists() {
        // the services issue #66's Dime Evo listed
        val listed = listOf(
            "00000000-0000-0000-0099-aabbccddeeff", "00001108-0000-1000-8000-00805f9b34fb", "0000110b-0000-1000-8000-00805f9b34fb",
            "0000110e-0000-1000-8000-00805f9b34fb", "0000111e-0000-1000-8000-00805f9b34fb", "00001124-0000-1000-8000-00805f9b34fb", "df21fe2c-2515-4fdb-8886-f12c4d67927c",
        )
        assertEquals(AIROHA_SPP, dime.serviceFor(listed))
        assertEquals(AIROHA_SERVICE, dime.serviceFor(listOf(AIROHA_SERVICE.lowercase())))
        assertEquals(AIROHA_SPP, dime.serviceFor(emptyList()))
        assertEquals(AIROHA_SERVICE, SkullcandyModels.fromName("Rail")!!.serviceFor(emptyList()))
        assertEquals(SkullcandyServices.AT, SkullcandyModels.fromName("Grind")!!.serviceFor(listed))
    }

    @Test
    fun theRaceTalkIsTheMainOneWithoutTheRailsOwnCommands() {
        val t = SkullcandyRaceTalk(dime)
        assertTrue(t.primary)
        assertFalse(t.hasGain)
        val start = t.start()
        assertTrue(start.all { t.allowed(it) })
        assertTrue(start.any { it.id == JblRace.BATTERY })
        // the Dime Evo has transparency (stayAwareMode): the hearing mode is read
        assertTrue(t.hasNoise)
        // no Bragi button or voice reads: those come from the Rail's SDK configuration
        val sent = start.map { p -> p.bytes().joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() } }
        assertTrue(sent.none { it.startsWith("05 5A 03 00 07 00") || it.startsWith("05 5A 03 00 09 00") }, sent.toString())
        // the Rail itself still reads them
        val railSent = SkullcandyRaceTalk(SkullcandyModels.fromName("Rail")!!).start().map { p -> p.bytes().joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() } }
        assertTrue(railSent.any { it.startsWith("05 5A 03 00 07 00") }, railSent.toString())
    }

    @Test
    fun theAtSessionSendsPlainLinesAndReadsPlainReplies() {
        val s = SkullcandyAtSession(SkullcandyModels.fromName("Grind")!!)
        val first = s.start()
        assertTrue(SkullcandyAt.BATTERY in first)
        s.feed("+UIAUDIO:on\r\nOK\r\n".encodeToByteArray())
        assertTrue(s.state.number(Command.ANC_MODE) == null || Command.ANC_MODE.labels[s.state.number(Command.ANC_MODE)] != null)
    }
}
