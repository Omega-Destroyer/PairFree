package io.github.omegadestroyer.soundpeter.protocol

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The decode cases in data/soundpeats/test_vectors.json: replies recorded from an H3, and edge cases. */
class DecodeVectorsTest {
    private val cases = loadData("test_vectors.json").getValue("decode").jsonArray.map { it.jsonObject }

    private fun pairs(json: JsonElement): List<Pair<Int, JsonPrimitive>> =
        json.jsonArray.map { it.jsonArray[0].jsonPrimitive.int to it.jsonArray[1].jsonPrimitive }

    private fun expected(command: Command, json: JsonElement): Value = when {
        json is JsonNull -> Value.Number(null)
        json is JsonArray && command.kind == Kind.LEVEL_LIST ->
            Value.Levels(pairs(json).map { Level(it.first, it.second.booleanOrNull!!) })
        json is JsonArray -> Value.Keys(pairs(json).map { KeyBinding(it.first, it.second.int) })
        json.jsonPrimitive.isString -> Value.Text(json.jsonPrimitive.content)
        json.jsonPrimitive.booleanOrNull != null -> Value.Switch(json.jsonPrimitive.booleanOrNull!!)
        else -> Value.Number(json.jsonPrimitive.intOrNull)
    }

    @Test
    fun decode() {
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val frame = Frame.reply(case.str("reply").toInt(16), case.str("payload"))
            val command = Command.entries.single { it.get == frame.answers }
            val decoded = Replies.decode(command, frame.payload)
            if (case["ignored"]?.jsonPrimitive?.booleanOrNull == true) {
                assertNull(decoded, case.str("name"))
                assertEquals(EarbudState(), EarbudState().update(frame), case.str("name"))
            } else {
                assertEquals(expected(command, case.getValue("value")), decoded, case.str("name"))
            }
        }
    }

    @Test
    fun firmwareVersionFields() {
        val version = FirmwareVersion("S5_20250605_V0.4.9")
        assertEquals("S5", version.code)
        assertEquals("0.4.9", version.version)
    }
}
