package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.jbl.RacePacket
import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkullcandyRaceTest {
    private fun builtByModel(cls: String, on: Boolean): Map<String, String> {
        val data = Json.parseToJsonElement(repoFile("data/skullcandy/race_commands.json").readText()).jsonObject
        val c = data.getValue("commands").jsonArray.map { it.jsonObject }.first { it.getValue("class").jsonPrimitive.content == cls }
        val groups = c.getValue("built_by_model").jsonObject.getValue(if (on) "on" else "off").jsonArray
        val out = LinkedHashMap<String, String>()
        for (g in groups) {
            val bytes = g.jsonObject.getValue("bytes").jsonPrimitive.content
            for (m in g.jsonObject.getValue("models").jsonArray) out[m.jsonPrimitive.content] = bytes
        }
        return out
    }

    @Test
    fun theMultipointFormsAgreeWithTheAppsOwnClassesForEveryModel() {
        val setOn = builtByModel("RaceCommandMultipointPairingSet", true)
        val setOff = builtByModel("RaceCommandMultipointPairingSet", false)
        val get = builtByModel("RaceCommandMultipointPairingGet", true)
        assertEquals(56, setOn.size)
        for (p in SkullcandyProducts.ALL) {
            assertEquals(setOn.getValue(p.enumName), SkullcandyRace.multipointSet(p.enumName, true), p.enumName)
            assertEquals(setOff.getValue(p.enumName), SkullcandyRace.multipointSet(p.enumName, false), p.enumName)
            assertEquals(get.getValue(p.enumName), SkullcandyRace.multipointGet(p.enumName), p.enumName)
        }
    }

    @Test
    fun everyRequestThatIsBuiltPassesTheAllowList() {
        for (p in SkullcandyProducts.ALL) {
            assertTrue(SkullcandyRace.isAllowed(SkullcandyRace.multipointGet(p.enumName)), p.enumName)
            assertTrue(SkullcandyRace.isAllowed(SkullcandyRace.multipointSet(p.enumName, true)), p.enumName)
        }
        assertTrue(SkullcandyRace.isAllowed(SkullcandyRace.wearDetectSet(false)))
        assertTrue(SkullcandyRace.isAllowed(SkullcandyRace.bassBoostSet(true)))
        assertFalse(SkullcandyRace.isAllowed("05 5A 03 00 FF FF 01"))
        assertEquals(listOf(0x05, 0x5A, 0x03, 0x00, 0x02, 0xF4, 0x01), SkullcandyRace.bytes(SkullcandyRace.wearDetectSet(true)).map { it.toInt() and 0xFF })
    }

    @Test
    fun aMultipointReadAndWriteGoThroughTheRaceChannel() {
        val product = SkullcandyModels.fromName("Hesh 360")!!
        val s = SkullcandyAtSession(product, viaRace = true)
        assertTrue(s.hasMultipoint)
        val read = s.start().first { it.startsWith("RACE:") }
        assertEquals("RACE:05 5A 06 00 00 0A 00 F5 01 00", read)
        s.bytes(read)
        // 05 5B 05 00 00 0A 01 00 01: the NV read's answer, the value last
        val fed = s.feed(RacePacket(0x0A00, byteArrayOf(0x01, 0x00, 0x01), 0x5B).bytes())
        assertTrue(fed.changed)
        val on = s.groups.single { it.key == "connection" }.settings.single() as Setting.Toggle
        assertTrue(on.on)
        val out = s.change(SettingChange.Toggle(SkullcandyAtSession.MULTIPOINT, false))!!
        assertEquals(listOf("RACE:05 5A 03 00 01 14 00"), out)
        assertTrue(s.allowed(out[0]))
        assertEquals(listOf(read), s.takeReadBacks())
    }

    @Test
    fun theSerialAtChannelNeverSendsRaceMessages() {
        val s = SkullcandyAtSession(SkullcandyModels.fromName("Crusher ANC 2")!!, viaRace = false)
        assertFalse(s.hasMultipoint)
        assertFalse(s.allowed("RACE:05 5A 03 00 02 F4 01"))
        assertNotNull(s.start().firstOrNull())
    }
}
