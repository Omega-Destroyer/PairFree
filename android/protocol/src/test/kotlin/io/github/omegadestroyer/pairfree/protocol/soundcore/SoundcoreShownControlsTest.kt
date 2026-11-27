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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Every control the screens would show must write something when used (issue #76: the LDAC switch showed and did nothing). Each recorded or OpenSCQ30 state is fed to a
 * session, then every shared switch and every row is used once.
 */
class SoundcoreShownControlsTest {
    private val vectors = Json.parseToJsonElement(repoFile("data/soundcore/test_vectors.json").readText()).jsonObject

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
                    is Setting.Action -> SettingChange.Press(row.key)
                    else -> null
                } ?: continue
                if (fresh().change(change).isNullOrEmpty()) dead += "$model ${row.key}"
            }
        }
        assertTrue(read > 20, "states read: $read")
        assertTrue(dead.isEmpty(), "shown but writes nothing: ${dead.distinct()}")
    }
}
