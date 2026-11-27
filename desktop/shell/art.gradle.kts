// Generates the desktop apps' device drawings from the very files Android shows: the vector drawables tools/gen_device_art.py writes
// (android/app/src/main/res/drawable/device_*.xml) and its lookup (data/device_art_index.json). Nothing is drawn twice: a change to a
// model's drawing reaches both. A drawing is kept as one text, a line a path: stroke role, fill role, stroke width, path data.
import groovy.json.JsonSlurper
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

abstract class GenerateDeviceArt : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val drawableDir: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val indexFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** The seven colours the drawings are written in, each standing for a role of the look (docs/LOOKS.md). */
    private val roles = mapOf(
        "#101214" to 'B', "#181B1F" to 'P', "#1F2328" to 'R', "#2B3037" to 'H', "#E8E6E1" to 'I', "#8A9099" to 'G', "#E6A23C" to 'A',
    )

    private fun lit(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("\n", "\\n") + "\""

    private fun role(color: String): String {
        if (color.isEmpty()) return "-"
        return (roles[color.uppercase()] ?: throw GradleException("a device drawing holds the colour $color, which stands for no role")).toString()
    }

    @TaskAction
    fun generate() {
        @Suppress("UNCHECKED_CAST")
        val index = JsonSlurper().parse(indexFile.get().asFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val byName = index["by_name"] as Map<String, List<String?>>
        @Suppress("UNCHECKED_CAST")
        val byCode = index["by_code"] as Map<String, List<String?>>
        @Suppress("UNCHECKED_CAST")
        val generic = index["generic"] as Map<String, List<String?>>
        val used = sortedSetOf<String>()
        for (pair in byName.values + byCode.values + generic.values) pair.filterNotNull().forEach { used += it }
        val drawings = used.map { name ->
            val file = drawableDir.get().asFile.resolve("$name.xml")
            if (!file.exists()) throw GradleException("data/device_art_index.json names $name, which has no drawable")
            val root = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file).documentElement
            val a = "http://schemas.android.com/apk/res/android"
            val text = StringBuilder()
            text.append(root.getAttributeNS(a, "viewportWidth")).append(' ').append(root.getAttributeNS(a, "viewportHeight"))
            val paths = root.getElementsByTagName("path")
            for (i in 0 until paths.length) {
                val p = paths.item(i) as Element
                text.append('\n')
                text.append(role(p.getAttributeNS(a, "strokeColor"))).append(role(p.getAttributeNS(a, "fillColor"))).append(' ')
                text.append(p.getAttributeNS(a, "strokeWidth").ifEmpty { "0" }).append(' ')
                text.append(p.getAttributeNS(a, "pathData"))
            }
            name to text.toString()
        }
        fun pair(p: List<String?>) = "DeviceArt(${lit(p[0]!!)}, ${p[1]?.let { lit(it) } ?: "null"})"
        val out = StringBuilder()
        out.appendLine("// Generated from data/device_art_index.json and the device drawables by the generateDeviceArt task. Do not edit.")
        out.appendLine("package io.github.omegadestroyer.pairfree.shell")
        out.appendLine()
        out.appendLine("internal object DeviceArtTables {")
        fun chunked(name: String, type: String, rows: List<Pair<String, String>>) {
            val parts = rows.chunked(120)
            for ((i, rowsOfPart) in parts.withIndex()) {
                out.appendLine("    private fun ${name}_$i(): Map<String, $type> = mapOf(")
                for ((k, v) in rowsOfPart) out.appendLine("        ${lit(k)} to $v,")
                out.appendLine("    )")
            }
            out.appendLine("    val $name: Map<String, $type> by lazy {")
            out.appendLine("        val m = HashMap<String, $type>()")
            for (i in parts.indices) out.appendLine("        m.putAll(${name}_$i())")
            out.appendLine("        m")
            out.appendLine("    }")
        }
        chunked("byName", "DeviceArt", byName.map { it.key to pair(it.value) })
        chunked("byCode", "DeviceArt", byCode.map { it.key to pair(it.value) })
        chunked("drawings", "String", drawings.map { it.first to lit(it.second) })
        out.appendLine("    val genericEarbuds = ${pair(generic["earbuds"]!!)}")
        out.appendLine("    val genericHeadphones = ${pair(generic["headphones"]!!)}")
        out.appendLine("    val genericSpeaker = ${pair(generic["speaker"]!!)}")
        out.appendLine("}")
        val dir = outputDir.get().asFile.resolve("io/github/omegadestroyer/pairfree/shell")
        dir.mkdirs()
        dir.resolve("DeviceArtTables.kt").writeText(out.toString())
    }
}

tasks.register<GenerateDeviceArt>("generateDeviceArt") {
    drawableDir = rootProject.layout.projectDirectory.dir("../android/app/src/main/res/drawable")
    indexFile = rootProject.layout.projectDirectory.file("../data/device_art_index.json")
    outputDir = layout.buildDirectory.dir("generated/art")
}
