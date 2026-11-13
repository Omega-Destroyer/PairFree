package io.github.omegadestroyer.pairfree.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The vendor-app gap check (owner, 2026-11-12: features come from the vendor's app, and "not read" is not an answer). Every feature-level id a brand's
 * generated table holds (a Sony function, a Bose function, a Nothing, EarFun, QCY, 1MORE, Soundcore or Samsung command...) must either appear in that brand's
 * hand-written code or be listed in `data/unused_ids.json` with the reason. An id nobody uses and nobody explains fails here; so does a listed id that has
 * since been used (delete its entry). A reason that starts with "gap-audit" is an open item from the 2026-11-12 audit, not an explanation:
 * `python tools/gap_audit.py` lists them.
 */
class GapAuditTest {
    private class Brand(val key: String, val generated: String, val objects: List<String>, val protocolDir: String, val appNames: List<String>)

    private val brands = listOf(
        Brand("sony", "sony", listOf("SonyFunction"), "sony", listOf("Sony")),
        Brand("bose", "bose", listOf("BoseFunction"), "bose", listOf("Bose")),
        Brand("nothing", "nothing", listOf("NothingCommand"), "nothing", listOf("Nothing")),
        Brand("earfun", "earfun", listOf("EarfunCommand", "EarfunQcc"), "earfun", listOf("Earfun")),
        Brand("qcy", "qcy", listOf("QcyItem", "JieliWire"), "qcy", listOf("Qcy")),
        Brand("onemore", "onemore", listOf("OneMoreCommand"), "onemore", listOf("OneMore")),
        Brand("soundcore", "soundcore", listOf("SoundcoreCommand"), "soundcore", listOf("Soundcore")),
        Brand("samsung", "samsung", listOf("SamsungMessage"), "samsung", listOf("Samsung")),
        Brand("jbl", "jbl", listOf("JblCommand"), "jbl", listOf("Jbl")),
        Brand("oneplus", "oneplus", listOf("OneplusCommand"), "oneplus", listOf("Oneplus")),
        Brand("pixel", "pixel", listOf("PixelWire"), "pixel", listOf("Pixel")),
        Brand("apple", "apple", listOf("AppleControl", "AppleOpcode"), "apple", listOf("Apple")),
        Brand("cambridge", "cambridge", listOf("CambridgeCommand"), "cambridge", listOf("Cambridge")),
    )

    private val listed: JsonObject = Json.parseToJsonElement(repoFile("data/unused_ids.json").readText()).jsonObject

    /** `Object.NAME` of every constant in the generated file whose top object is one of [objects]. */
    private fun constants(b: Brand): List<String> {
        val out = mutableListOf<String>()
        val dir = repoFile("android/protocol/build/generated/${b.generated}")
        assertTrue(dir.exists(), "the generated tables of ${b.key} are not built: $dir")
        for (file in dir.walkTopDown().filter { it.extension == "kt" }) {
            val stack = mutableListOf<Pair<Int, String>>()
            for (line in file.readLines()) {
                Regex("""^(\s*)(?:public |internal )?object (\w+)""").find(line)?.let { stack += it.groupValues[1].length to it.groupValues[2] }
                val indent = line.length - line.trimStart().length
                if (line.trim() == "}" && stack.isNotEmpty() && indent == stack.last().first) stack.removeAt(stack.size - 1)
                val c = Regex("""^\s*(?:public )?const val (\w+)""").find(line) ?: continue
                if (stack.isNotEmpty() && stack.first().second in b.objects) out += stack.joinToString(".") { it.second } + "." + c.groupValues[1]
            }
        }
        return out
    }

    /** The brand's hand-written code: its protocol package, its app files and the shared screens that draw settings. */
    private fun handWritten(b: Brand): String {
        val parts = mutableListOf<File>()
        parts += repoFile("android/protocol/src/main/kotlin/io/github/omegadestroyer/pairfree/protocol/${b.protocolDir}").walkTopDown().filter { it.extension == "kt" }
        val app = "android/app/src/main/kotlin/io/github/omegadestroyer/pairfree"
        parts += repoFile("$app/brands").walkTopDown().filter { f -> f.extension == "kt" && b.appNames.any { f.name.startsWith(it) } }
        parts += repoFile("$app/eq").walkTopDown().filter { it.extension == "kt" }
        parts += repoFile("$app/ui").walkTopDown().filter { it.extension == "kt" }
        parts += repoFile("android/protocol/src/main/kotlin/io/github/omegadestroyer/pairfree/protocol").listFiles { f -> f.extension == "kt" }.orEmpty()
        return parts.joinToString("\n") { it.readText() }
    }

    @Test
    fun everyFeatureIdIsUsedOrExplained() {
        for (b in brands) {
            val text = handWritten(b)
            val unused = constants(b).filter { id -> !Regex("""\b${Regex.escape(id.substringAfterLast('.'))}\b""").containsMatchIn(text) }.toSet()
            val entries = (listed[b.key] as? JsonObject)?.keys.orEmpty()
            val missing = unused - entries
            val stale = entries - unused
            assertTrue(missing.isEmpty(), "${b.key}: ids nobody uses and data/unused_ids.json does not explain (build them, or list them with the reason): ${missing.sorted()}")
            assertTrue(stale.isEmpty(), "${b.key}: ids listed in data/unused_ids.json that code uses now (delete the entries): ${stale.sorted()}")
            for (id in entries) {
                val reason = (listed[b.key] as JsonObject).getValue(id).jsonPrimitive.content
                assertTrue(reason.length > 10, "${b.key} $id: say why")
            }
        }
        assertEquals(brands.size, brands.map { it.key }.toSet().size)
    }
}
