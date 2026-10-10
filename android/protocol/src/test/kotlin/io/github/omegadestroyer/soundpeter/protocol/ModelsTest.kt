package io.github.omegadestroyer.soundpeter.protocol

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelsTest {
    private val devices = loadData("devices.json").getValue("devices").jsonArray.map { it.jsonObject }

    @Test
    fun generatedTableMatchesJson() {
        assertEquals(devices.size, Models.ALL.size)
        for ((json, model) in devices.zip(Models.ALL)) {
            assertEquals(json.str("firmware_code"), model.code)
            assertEquals(json.getValue("names").jsonArray.map { it.jsonPrimitive.content }, model.names, model.code)
            assertEquals(json.str("family"), model.family, model.code)
            val excludes = json["ldac_excludes"]?.jsonArray?.map { Command.valueOf(it.jsonPrimitive.content.uppercase()) }
            assertEquals(excludes.orEmpty().toSet(), model.ldacExcludes, model.code)
            assertEquals(json["ldac_or_multipoint"]?.jsonPrimitive?.boolean ?: false, model.ldacOrMultipoint, model.code)
            val eq = json["eq"]?.jsonObject
            if (eq == null) {
                assertNull(model.eq, model.code)
            } else {
                val layout = model.eq!!
                assertEquals(eq.str("command").toInt(16), layout.command, model.code)
                assertEquals(eq.double("master_gain_db"), layout.masterGainDb, model.code)
                assertEquals(eq.int("user_range_db"), layout.userRangeDb, model.code)
                val bands = eq.getValue("bands").jsonArray.map { it.jsonObject }
                    .map { EqBand(it.int("hz"), it.double("q"), it.double("base_gain_db")) }
                assertEquals(bands, layout.bands, model.code)
                val presets = eq["presets"]?.jsonArray.orEmpty().map { it.jsonObject }.map { p ->
                    EqPreset(p.str("name"), p.getValue("bands").jsonArray.map { it.jsonObject }.map { EqBand(it.int("hz"), it.double("q"), it.double("gain_db")) })
                }
                assertEquals(presets, layout.presets, model.code)
            }
        }
    }

    @Test
    fun findByCodeThenName() {
        assertEquals("S5", Models.find("S5", null)?.code)
        assertEquals("S5", Models.find(null, "soundpeats h3")?.code)
        assertEquals("S5", Models.find("S999", "SOUNDPEATS H3")?.code)
        assertNull(Models.find(null, "Someone else's earbuds"))
    }

    @Test
    fun h3BlocksGameModeAndTwoDevicesWhileLdacIsOn() {
        val h3 = Models.find("S5", null)
        fun state(ldac: String) = EarbudState()
            .update(Frame.reply(Command.LDAC.get!! or Wire.REPLY_MASK, ldac))
            .update(Frame.reply(Command.MULTIPOINT.get!! or Wire.REPLY_MASK, "0000"))
        assertTrue(state("0001").blockedByLdac(Command.MULTIPOINT, h3))
        assertTrue(state("0001").blockedByLdac(Command.GAME_MODE, h3))
        assertFalse(state("0000").blockedByLdac(Command.MULTIPOINT, h3))
        assertFalse(state("0001").blockedByLdac(Command.ANC_MODE, h3))
        assertFalse(state("0001").blockedByLdac(Command.MULTIPOINT, null))
    }
}
