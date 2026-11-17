package io.github.omegadestroyer.pairfree.eq

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Where the equalizer keeps its texts: saved profiles and the last setting, one text per key. On Android this is the
 * app's settings file (the keys and texts are the ones every release so far wrote); on the desktop the settings file
 * beside the program (docs/DESKTOP.md).
 */
interface TextStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

/** A stored or imported text that is not what it should be: the caller treats it as absent. */
internal class BadJson(message: String) : IllegalArgumentException(message)

/**
 * The few JSON calls the equalizer makes, with the leniency of Android's own JSON classes, which read these texts until
 * the code became shared with the desktop apps: a number may be written as a string and a string as a number, and a
 * missing optional value has a default. A parse that fails throws an IllegalArgumentException ([BadJson] or the library's own).
 */
internal object Stored {
    private val lenient = Json { isLenient = true }
    private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun objectOf(text: String): JsonObject = lenient.parseToJsonElement(text) as? JsonObject ?: throw BadJson("not an object")

    fun arrayOf(text: String): JsonArray = lenient.parseToJsonElement(text) as? JsonArray ?: throw BadJson("not an array")

    /** As a file for the user to read: one value a line, indented by two spaces. */
    fun printed(value: JsonObject): String = pretty.encodeToString(JsonObject.serializer(), value)

    /** A number as Android's JSON classes wrote it: a whole one without a fraction. */
    fun number(value: Double): JsonPrimitive = if (value == value.toLong().toDouble()) JsonPrimitive(value.toLong()) else JsonPrimitive(value)

    private fun text(e: JsonElement): String = if (e is JsonPrimitive) e.content else e.toString()

    private fun double(e: JsonElement): Double? = (e as? JsonPrimitive)?.takeIf { it.isString || it.content != "true" && it.content != "false" }?.content?.toDoubleOrNull()

    /** The value as text, or empty when it is absent. */
    fun JsonObject.optString(key: String): String = this[key]?.let(::text) ?: ""

    /** The value as text; absent is an error. */
    fun JsonObject.string(key: String): String = this[key]?.let(::text) ?: throw BadJson("no $key")

    /** The value as a whole number (a fraction is cut off), or [default] when it is absent or no number. */
    fun JsonObject.optInt(key: String, default: Int = 0): Int = this[key]?.let(::double)?.toInt() ?: default

    fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: throw BadJson("no array $key")

    fun JsonArray.objects(): List<JsonObject> = map { it as? JsonObject ?: throw BadJson("not an object") }

    fun JsonArray.ints(): List<Int> = map { double(it)?.toInt() ?: throw BadJson("not a number") }

    fun JsonArray.doubles(): List<Double> = map { double(it) ?: throw BadJson("not a number") }
}
