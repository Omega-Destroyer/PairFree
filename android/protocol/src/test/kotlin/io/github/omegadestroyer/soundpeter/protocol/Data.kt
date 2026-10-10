package io.github.omegadestroyer.soundpeter.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** Loads a file from the repo's data/ folder. Gradle passes its location. */
internal fun loadData(name: String): JsonObject {
    val dir = System.getProperty("soundpeter.data") ?: error("soundpeter.data is not set; run the tests through Gradle")
    return Json.parseToJsonElement(File(dir, name).readText()).jsonObject
}

internal fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content

internal fun JsonObject.double(key: String): Double = str(key).toDouble()

internal fun JsonObject.int(key: String): Int = str(key).toInt()

internal fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
