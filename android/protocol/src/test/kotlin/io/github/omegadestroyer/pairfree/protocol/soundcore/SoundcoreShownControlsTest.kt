package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.hexBytes
import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Every control the screens would show must write something when used (issue #76: the LDAC switch showed and did nothing). Each recorded or OpenSCQ30 state is fed to a
 * session, then every shared switch and every row is used once.
 */
class SoundcoreShownControlsTest {
    private val vectors = Json.parseToJsonElement(repoFile("data/soundcore/test_vectors.json").readText()).jsonObject

    /** Issue #79: Find Earbuds on the Liberty Buds 2 (the earbuds beep, 10 89 left, right, case) and on the Space A40 (the phone makes the sound). */
    @Test
    fun findEarbuds() {
        fun session(model: String) = SoundcoreSession(model).also { it.receive(SoundcorePacket(SoundcoreCommand.STATE, ByteArray(255))) }
        val a40 = session("A3936")
        assertTrue(a40.groups.any { g -> g.settings.any { it.key == SoundcoreSession.FIND_PHONE } })
        assertEquals(emptyList<SoundcorePacket>(), a40.change(SettingChange.Press(SoundcoreSession.FIND_PHONE)))
        val q30i = session("A3955")
        fun bytes(change: SettingChange) = q30i.change(change)!!.single().let { it.command to it.body.joinToString("") { b -> b.toString() } }
        assertEquals(SoundcoreCommand.FIND_DEVICE to "110", bytes(SettingChange.Press(SoundcoreSession.FIND)))
        assertEquals(SoundcoreCommand.FIND_DEVICE to "010", bytes(SettingChange.Toggle("find_mute_left", true)))
        assertEquals(SoundcoreCommand.FIND_DEVICE to "000", bytes(SettingChange.Press(SoundcoreSession.FIND)))
        assertTrue(SoundcoreLayout.of("D1206")!!.features.contains("find_device"))
    }

    /**
     * The Liberty Buds 2's switches from records 25 to 27 (D1206AnalysisService): built from the app's parser, not recorded. Each shows with the state the records give and
     * writes the command the app's own table names.
     */
    @Test
    fun theLibertyBuds2sRecords() {
        fun record(tag: Int, vararg v: Int) = byteArrayOf(tag.toByte(), v.size.toByte()) + ByteArray(v.size) { v[it].toByte() }
        // Auto power off on at the second duration; touch tone and low battery tone on; game mode off, LDAC on, two devices on; wear detection on.
        val body = record(25, 1, 1) + record(26, 0b0011) + record(27, 0b1100, 0b0001, 0, 0, 0)
        val s = SoundcoreSession("D1206")
        assertTrue(s.receive(SoundcorePacket(SoundcoreCommand.STATE, body)))
        assertEquals(true, s.state.switch(Command.LDAC))
        assertEquals(true, s.state.switch(Command.MULTIPOINT))
        assertEquals(false, s.state.switch(Command.GAME_MODE))
        val rows = s.groups.flatMap { it.settings }.associateBy { it.key }
        assertEquals(Setting.Toggle("sc_wearing_detection", true), rows["sc_wearing_detection"])
        assertEquals(Setting.Toggle("sc_low_battery_prompt", true), rows["sc_low_battery_prompt"])
        assertEquals(Setting.Toggle("sc_in_ear_tone", false), rows["sc_in_ear_tone"])
        assertEquals("20", (rows["a3954_auto_power_off_duration"] as Setting.Choice).value)
        val write = s.change(SettingChange.Toggle("sc_wearing_detection", false))!!.single()
        assertEquals(SoundcoreCommand.WEARING_DETECTION, write.command)
    }

    @Test
    fun everyShownControlWrites() {
        val recorded = (vectors.getValue("openscq30_states").jsonArray + vectors.getValue("report_states").jsonArray).map { it.jsonObject }
            .map { it.getValue("model").jsonPrimitive.content to it.getValue("body").jsonPrimitive.content.hexBytes() }
        // Most models have no recorded state: a body of zeros and one of ones stand in for theirs wherever the layout can read them.
        val made = SoundcoreLayout.ALL.filter { it.tlv.isEmpty() }.flatMap { l -> listOf(l.model to ByteArray(255), l.model to ByteArray(255) { 1 }) }
        val dead = mutableListOf<String>()
        var read = 0
        for ((model, body) in recorded + made) {
            fun fresh() = SoundcoreSession(model).also {
                it.receive(SoundcorePacket(SoundcoreCommand.STATE, body))
                // The earbuds' own notices, which must not add a control that cannot be written.
                it.receive(SoundcorePacket(SoundcoreCommand.LDAC_STATE, byteArrayOf(1)))
                it.receive(SoundcorePacket(SoundcoreCommand.GAMING_MODE_UPDATE, byteArrayOf(0)))
            }
            val s = fresh()
            if (s.lastState == null) continue
            read++
            for (command in listOf(Command.ANC_MODE, Command.GAME_MODE, Command.LDAC, Command.MULTIPOINT)) {
                if (!s.state.supports(command)) continue
                val value = if (command == Command.ANC_MODE) s.state.number(command) ?: continue else if (s.state.switch(command) == true) 0 else 1
                if (fresh().set(command, value) == null) dead += "$model $command"
            }
            for (group in s.groups) for (row in group.settings) {
                val change = when (row) {
                    is Setting.Toggle -> if (row.enabled) SettingChange.Toggle(row.key, !row.on) else null
                    is Setting.Choice -> row.options.firstOrNull { it != row.value }?.let { SettingChange.Choice(row.key, it) }
                    is Setting.Level -> if (row.enabled) SettingChange.Level(row.key, if (row.value == row.max) row.min else row.value + 1) else null
                    // Where the phone makes the sound, there is nothing to write: the screen plays it.
                    is Setting.Action -> if (row.key == SoundcoreSession.FIND_PHONE) null else SettingChange.Press(row.key)
                    else -> null
                } ?: continue
                if (fresh().change(change).isNullOrEmpty()) dead += "$model ${row.key}"
            }
        }
        assertTrue(read > 20, "states read: $read")
        assertTrue(dead.isEmpty(), "shown but writes nothing: ${dead.distinct()}")
    }
}
