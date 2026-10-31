package io.github.omegadestroyer.pairfree.protocol

import io.github.omegadestroyer.pairfree.protocol.jbl.JblModels
import io.github.omegadestroyer.pairfree.protocol.pixel.PixelModels
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The brand of a paired device, against the names in data/identify.json and every brand's own model names. */
class IdentifyTest {
    private fun load(path: String): JsonObject = Json.parseToJsonElement(repoFile(path).readText()).jsonObject

    @Test
    fun theCorpus() {
        val names = load("data/identify.json").getValue("names").jsonArray.map { it.jsonObject }
        assertTrue(names.size >= 30)
        for (e in names) {
            val name = e.getValue("name").jsonPrimitive.content
            val uuids = (e["uuids"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
            val want = e["brand"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.let(Maker::valueOf)
            assertEquals(want, Identify.maker(name, uuids), name)
            (e["model"] as? JsonPrimitive)?.content?.let { model ->
                when (want) {
                    Maker.JBL -> assertEquals(model, JblModels.fromName(name)?.name, name)
                    Maker.PIXEL -> assertEquals(model, PixelModels.fromName(name)?.name, name)
                    else -> error("no model check for $want")
                }
            }
        }
    }

    /** Every name a brand's own table gives its models must come out as that brand, with no list of services. */
    @Test
    fun everyBrandsOwnNames() {
        val tables = mapOf(
            Maker.SOUNDPEATS to "data/soundpeats/devices.json",
            Maker.SOUNDCORE to "data/soundcore/devices.json",
            Maker.SAMSUNG to "data/samsung/devices.json",
            Maker.SONY to "data/sony/devices.json",
            Maker.BOSE to "data/bose/devices.json",
            Maker.JBL to "data/jbl/devices.json",
            Maker.NOTHING to "data/nothing/devices.json",
            Maker.PIXEL to "data/pixel/devices.json",
        )
        val wrong = mutableListOf<String>()
        for ((maker, path) in tables) {
            for (d in load(path).getValue("devices").jsonArray.map { it.jsonObject }) {
                val names = listOfNotNull((d["name"] as? JsonPrimitive)?.content) + (d["names"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
                for (n in names) {
                    // The Capsule3 Pro is SOUNDPEATS' but speaks QCY's protocol: still SOUNDPEATS here, left out later.
                    if (Identify.maker(n, emptyList()) != maker) wrong += "$n: ${Identify.maker(n, emptyList())}, not $maker"
                }
            }
        }
        assertEquals(emptyList<String>(), wrong)
    }
}
