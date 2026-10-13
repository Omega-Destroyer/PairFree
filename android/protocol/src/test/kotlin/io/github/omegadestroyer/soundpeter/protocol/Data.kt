package io.github.omegadestroyer.soundpeter.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** The repo's data/soundpeats/ folder. Gradle passes its location. */
internal val dataDir: File
    get() = File(System.getProperty("soundpeter.data") ?: error("soundpeter.data is not set; run the tests through Gradle"))

/** A file in the repo, by its path from the repo's root. */
internal fun repoFile(path: String): File = File(dataDir, "../../$path").canonicalFile

/** Loads a JSON file from the repo's data/soundpeats/ folder. */
internal fun loadData(name: String): JsonObject = Json.parseToJsonElement(File(dataDir, name).readText()).jsonObject

internal fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content

internal fun JsonObject.double(key: String): Double = str(key).toDouble()

internal fun JsonObject.int(key: String): Int = str(key).toInt()

internal fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

internal fun Frame.Companion.reply(command: Int, payloadHex: String): Frame =
    FrameParser().feed(build(command, payloadHex.hexBytes())).single()
