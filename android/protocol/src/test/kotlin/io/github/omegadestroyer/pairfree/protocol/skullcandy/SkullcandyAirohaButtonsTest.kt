package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.jbl.JblAirohaGesture
import io.github.omegadestroyer.pairfree.protocol.jbl.JblRace
import io.github.omegadestroyer.pairfree.protocol.jbl.RacePacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The buttons and voice prompts of Skullcandy's Airoha models without an SDK configuration (docs/skullcandy/PROTOCOL.md section 9.5; data/skullcandy/buttons.json), against what
 * Skullcandy 5.0.6's code builds: Airoha's own gesture encoders (libmmi158x, libmmi, libmmi1562 Utility.a) and the app's vendor key map code.
 */
class SkullcandyAirohaButtonsTest {
    private fun hex(b: ByteArray) = b.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }
    private fun unhex(s: String) = s.split(' ').filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()
    private val tables = SKULLCANDY_AIROHA_GESTURE_VARIANTS

    @Test
    fun twelveProductsHaveButtonsOnTheRightRoute() {
        val with = SkullcandyProducts.ALL.filter { SkullcandyAirohaButtons(it).has }.map { it.enumName }.toSet()
        assertEquals(setOf("mod", "hanc", "tanc", "iconANC", "dimeEvo", "aviatorANC", "hesh540ANC", "push720open", "push540Open", "dime360", "crusher720", "crusher900ANC"), with)
        val vendor = SkullcandyProducts.ALL.filter { SkullcandyAirohaButtons(it).vendor }.map { it.enumName }.toSet()
        assertEquals(setOf("iconANC", "aviatorANC", "hesh540ANC", "crusher720"), vendor)
    }

    @Test
    fun theAb158xEncoderWritesAirohasEvents() {
        // libmmi158x.Utility.a.e: play/pause is 83 then 85, Spotify 330, take photo F001, each with the key id and a state of 0
        assertEquals("01 18 53 00 00 00 00 00 01 18 55 00 00 00 00 00", hex(JblAirohaGesture.encode("v3", 0x18, listOf(1 to 8), agentRight = false, relay = false, peer = false, tables)))
        assertEquals("0A 18 4A 01 00 00 00 00", hex(JblAirohaGesture.encode("v3", 0x18, listOf(11 to 212), agentRight = false, relay = false, peer = false, tables)))
        assertEquals("03 18 01 F0 00 00 00 00", hex(JblAirohaGesture.encode("v3", 0x18, listOf(7 to 240), agentRight = false, relay = false, peer = false, tables)))
        // an earbud with nothing to write: one entry of type 0 (Utility.a.c), not JBL's eight zero bytes
        assertEquals("00 18 00 00 00 00 00 00", hex(JblAirohaGesture.encode("v3", 0x18, listOf(2 to 8), agentRight = false, relay = false, peer = false, tables)))
    }

    @Test
    fun theOlderChipsHaveNoSpotifyOrPhoto() {
        // libmmi.Utility.a.e has no case for 212 or 240, so nothing is written for them
        assertEquals("00 18 00 00 00 00 00 00", hex(JblAirohaGesture.encode("1568", 0x18, listOf(1 to 212), agentRight = false, relay = false, peer = false, tables)))
        assertFalse(tables.getValue("plain").write.containsKey(240))
    }

    @Test
    fun theAb1562EncoderCarriesTheEventsStateMask() {
        // libmmi1562: the tap is key type 0, play/pause event 83 with the state bit 3; Spotify 190 with bits 3 and 24
        assertEquals("00 00 53 00 08 00 00 00", hex(JblAirohaGesture.encode("ab1562", 0, listOf(1 to 8), agentRight = false, relay = false, peer = false, tables)))
        assertEquals("05 00 BE 00 08 00 00 01", hex(JblAirohaGesture.encode("ab1562", 0, listOf(3 to 212), agentRight = false, relay = false, peer = false, tables)))
    }

    @Test
    fun aReadGivesAirohasActionsAndTheDefaultForTheRest() {
        // the left earbud's map holds Spotify on the long press release and volume up on the tap; the other key types are the firmware's default (209)
        val map = unhex("0A 18 4A 01 00 00 00 00 01 18 0A 00 00 00 00 00")
        val read = JblAirohaGesture.decode("v3", map, agentRight = false, relay = false, tables)
        assertEquals(212, read[11])
        assertEquals(1, read[1])
        assertEquals(209, read[3])
        assertEquals(209, read[7])
    }

    @Test
    fun theVendorKeyMapIsReadAndOnlyTheChangedPressRewritten() {
        val t = SkullcandyRaceTalk(SkullcandyModels.fromName("AVIATOR 900 ANC")!!)
        val start = t.start()
        val read = start.first { it.id == JblRace.NV_READ && it.payload.size == 4 && (it.payload[0].toInt() and 0xFF) == 0xD6 }
        assertEquals("05 5A 06 00 00 0A D6 F2 64 00", hex(read.bytes()))
        assertTrue(t.allowed(read))
        // answer: the tap cycles the EQ (0220), the double press is head tracking (F402), and the power button (key 18) holds its own entry
        val data = unhex("01 7E 20 02 00 00 00 00 02 7E 02 F4 00 00 00 00 01 18 0A 00 00 00 00 00")
        t.bytes(read)
        t.feed(RacePacket(JblRace.NV_READ, byteArrayOf(data.size.toByte(), 0) + data, JblRace.TYPE_RESPONSE).bytes())
        val rows = t.groups.first { it.key == "sk_buttons" }.settings.filterIsInstance<Setting.Choice>().associate { it.key to it.value }
        assertEquals("cycle_eq", rows["sk_button_single"])
        assertEquals("head_tracking", rows["sk_button_double"])
        assertNull(rows["sk_button_triple"])
        // the tap to Spotify: the power button's entry and the double press stay as they were read
        val out = t.change(SettingChange.Choice("sk_button_single", "spotify"))!!.single()
        assertEquals("05 5A 1A 00 D6 F2 02 7E 02 F4 00 00 00 00 01 18 0A 00 00 00 00 00 01 7E 4A 01 00 00 00 00", hex(out.bytes()))
        assertTrue(t.allowed(out))
        assertEquals(listOf(read), t.takeReadBacks())
    }

    @Test
    fun theEarbudsOptionsFollowTheManifest() {
        val b = SkullcandyAirohaButtons(SkullcandyModels.fromName("Dime Evo")!!)
        assertTrue(b.generic)
        // no chip yet: nothing starts
        assertNull(b.start(null))
    }

    @Test
    fun theSlyrVoicePromptsAreSkullcandysCommand2C81() {
        val t = SkullcandyRaceTalk(SkullcandyProducts.ALL.first { it.enumName == "slyrPro" })
        assertTrue(t.hasVoicePrompts)
        val get = t.start().first { hex(it.bytes()) == "05 5A 04 00 81 2C 02 FF" }
        assertTrue(t.allowed(get))
        t.bytes(get)
        // the answer's last byte 1 is off
        t.feed(unhex("05 5B 04 00 81 2C 00 01"))
        assertEquals(false, (t.groups.flatMap { it.settings }.first { it.key == "voice_prompts" } as Setting.Toggle).on)
        // a notification of a change
        t.feed(unhex("05 5D 03 00 81 2C 00"))
        assertEquals(true, (t.groups.flatMap { it.settings }.first { it.key == "voice_prompts" } as Setting.Toggle).on)
        val on = t.change(SettingChange.Toggle("voice_prompts", false))!!.single()
        assertEquals("05 5A 04 00 81 2C 01 FF", hex(on.bytes()))
        assertTrue(t.allowed(on))
    }
}
