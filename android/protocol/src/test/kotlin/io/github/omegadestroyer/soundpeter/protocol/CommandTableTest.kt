package io.github.omegadestroyer.soundpeter.protocol

import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The generated table must say exactly what data/soundpeats/commands.json says. */
class CommandTableTest {
    private val commands = loadData("commands.json").getValue("commands").jsonArray.map { it.jsonObject }

    @Test
    fun generatedTableMatchesJson() {
        assertEquals(commands.map { it.str("id") }, Command.entries.map { it.id })
        for (json in commands) {
            val command = Command.valueOf(json.str("id").uppercase())
            assertEquals(json["get"]?.jsonPrimitive?.content?.toInt(16), command.get, command.id)
            assertEquals(json["set"]?.jsonPrimitive?.content?.toInt(16), command.set, command.id)
            assertEquals(json.str("kind").uppercase(), command.kind.name, command.id)
            val labels = json["values"]?.jsonObject?.entries?.associate { it.key.toInt() to it.value.jsonPrimitive.content }
            assertEquals(labels ?: emptyMap<Int, String>(), command.labels, command.id)
            assertEquals(json["probe"]?.jsonPrimitive?.booleanOrNull ?: true, command.probe, command.id)
            assertEquals(json["per_model"]?.jsonPrimitive?.booleanOrNull ?: false, command.perModel, command.id)
        }
    }

    @Test
    fun numbersAreUnique() {
        val gets = Command.entries.mapNotNull { it.get }
        val sets = Command.entries.mapNotNull { it.set }
        assertEquals(gets.size, gets.toSet().size, "a GET number appears twice")
        assertEquals(sets.size, sets.toSet().size, "a SET number appears twice")
        assertTrue(gets.intersect(sets.toSet()).isEmpty(), "a GET number is also a SET number")
    }

    @Test
    fun getsAreNotReplies() {
        for (command in Command.entries) {
            val get = command.get ?: continue
            assertEquals(0, get and Wire.REPLY_MASK, command.id)
        }
    }
}
