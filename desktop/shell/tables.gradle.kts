// Generates the desktop apps' text table from the one strings file translators work from (android/app/src/main/res/values*/strings.xml)
// and the desktop's own wording where a computer differs from a phone (desktop/strings/<language>.xml, docs/DESKTOP.md).
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

abstract class GenerateStrings : DefaultTask() {
    @get:InputDirectory abstract val resDir: DirectoryProperty
    @get:InputDirectory abstract val overrideDir: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    /** Android's own reading of a string's text: quotes keep spaces, escapes become characters. */
    private fun unescape(raw: String): String {
        var s = raw.trim()
        val quoted = s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")
        if (quoted) s = s.substring(1, s.length - 1) else s = s.replace(Regex("\\s+"), " ")
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    'u' -> { out.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                    else -> out.append(n)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    private class Table(val strings: LinkedHashMap<String, String> = LinkedHashMap(), val plurals: LinkedHashMap<String, Map<String, String>> = LinkedHashMap())

    private fun read(file: File): Table {
        val table = Table()
        if (!file.exists()) return table
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val nodes = root.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            val name = e.getAttribute("name")
            when (e.tagName) {
                "string" -> table.strings[name] = unescape(e.textContent)
                "plurals" -> {
                    val items = LinkedHashMap<String, String>()
                    val kids = e.getElementsByTagName("item")
                    for (k in 0 until kids.length) { val item = kids.item(k) as Element; items[item.getAttribute("quantity")] = unescape(item.textContent) }
                    table.plurals[name] = items
                }
            }
        }
        return table
    }

    private fun lit(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("\n", "\\n").replace("\t", "\\t").replace("\r", "") + "\""

    @TaskAction
    fun generate() {
        val res = resDir.get().asFile
        val languages = listOf("en" to File(res, "values")) + res.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values-") && File(it, "strings.xml").exists() }
            .map { q -> q.name.removePrefix("values-").let { if (it.startsWith("b+")) it.removePrefix("b+").replace('+', '-') else it.replace("-r", "-") } to q }
            .sortedBy { it.first }
        val out = StringBuilder()
        out.appendLine("// Generated from the strings files by the generateStrings task. Do not edit.")
        out.appendLine("package io.github.omegadestroyer.pairfree.shell")
        out.appendLine()
        out.appendLine("internal class RawTexts(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)")
        out.appendLine()
        out.appendLine("internal object StringTables {")
        for ((tag, dir) in languages) {
            val main = read(File(dir, "strings.xml"))
            val desktop = read(File(overrideDir.get().asFile, "$tag.xml"))
            for ((kind, t) in listOf("main" to main, "desktop" to desktop)) {
                val id = "${tag.replace('-', '_')}_$kind"
                out.appendLine("    private fun $id(): RawTexts {")
                out.appendLine("        val s = HashMap<String, String>(${t.strings.size * 2})")
                for (chunk in t.strings.entries.chunked(150).withIndex()) {
                    out.appendLine("        s.putAll(part${id}_${chunk.index}())")
                }
                out.appendLine("        val p = HashMap<String, Map<String, String>>()")
                for ((k, v) in t.plurals) out.appendLine("        p[${lit(k)}] = mapOf(" + v.entries.joinToString(", ") { "${lit(it.key)} to ${lit(it.value)}" } + ")")
                out.appendLine("        return RawTexts(s, p)")
                out.appendLine("    }")
                for (chunk in t.strings.entries.chunked(150).withIndex()) {
                    out.appendLine("    private fun part${id}_${chunk.index}() = mapOf(")
                    for ((k, v) in chunk.value) out.appendLine("        ${lit(k)} to ${lit(v)},")
                    out.appendLine("    )")
                }
            }
        }
        out.appendLine("    /** The languages built in, as tags, English first. */")
        out.appendLine("    val tags: List<String> = listOf(" + languages.joinToString(", ") { lit(it.first) } + ")")
        out.appendLine()
        out.appendLine("    /** Each language's own text and its desktop wording; read the first time a language is asked for. */")
        out.appendLine("    fun load(tag: String): Pair<RawTexts, RawTexts>? = when (tag) {")
        for ((tag, _) in languages) out.appendLine("        ${lit(tag)} -> ${tag.replace('-', '_')}_main() to ${tag.replace('-', '_')}_desktop()")
        out.appendLine("        else -> null")
        out.appendLine("    }")
        out.appendLine("}")
        val dir = outputDir.get().asFile.resolve("io/github/omegadestroyer/pairfree/shell")
        dir.mkdirs()
        dir.resolve("StringTables.kt").writeText(out.toString())
    }
}

val generateStrings = tasks.register<GenerateStrings>("generateStrings") {
    resDir = rootProject.layout.projectDirectory.dir("../android/app/src/main/res")
    overrideDir = rootProject.layout.projectDirectory.dir("../desktop/strings")
    outputDir = layout.buildDirectory.dir("generated/strings")
}
