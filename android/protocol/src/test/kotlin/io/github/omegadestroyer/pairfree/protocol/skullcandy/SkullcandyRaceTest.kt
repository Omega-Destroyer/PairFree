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
}
