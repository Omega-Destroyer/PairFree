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
    fun onlySupportedSoundpeatsNamesAreRecognised() {
        // models in the table, by their Bluetooth names
        assertTrue(Models.isSupported("SOUNDPEATS H3"))
        assertTrue(Models.isSupported("soundpeats air5 pro+"))   // case does not matter
        assertTrue(Models.isSupported("  SOUNDPEATS Air3  "))    // surrounding spaces ignored
        assertTrue(Models.isSupported("TRUEFREE C1"))            // a sub-brand in the table
        assertTrue(Models.isSupported("Hi Listening"))           // a table name without the brand prefix
        // not in the table but a known brand: a newer model, still the common protocol
        assertTrue(Models.isSupported("SOUNDPEATS Air7 Pro"))
        // the Capsule3 Pro uses QCY's protocol, which the app does not speak
        assertFalse(Models.isSupported("SOUNDPEATS Capsule3 Pro"))
        // other makers and empty names
        assertFalse(Models.isSupported("Sony WH-1000XM5"))
        assertFalse(Models.isSupported("Galaxy Buds3 Pro"))
        assertFalse(Models.isSupported(""))
    }

    @Test
    fun everyTableNameIsRecognisedExceptTheQcyOne() {
        for (model in Models.ALL) {
            val qcy = model.family == "QCY-WUQI"
            for (name in model.names) {
                assertEquals(!qcy, Models.isSupported(name), "$name (${model.code})")
            }
        }
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
