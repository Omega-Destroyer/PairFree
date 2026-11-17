// Generates the desktop apps' list of looks from data/looks, the files Android's build reads too (docs/LOOKS.md): one look file
// is one look on every platform. A colour is "#RRGGBB" or one of Android's wallpaper colours by its resource name
// (system_accent1_200), which on a computer stands for a tone of the system's accent colour.
import groovy.json.JsonSlurper

abstract class GenerateDesktopLooks : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val looksDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val roles = listOf("background", "panel", "raised", "hairline", "text", "secondary_text", "label", "accent", "on_accent", "destructive")
        val optional = mapOf("outline" to "hairline", "shadow" to "hairline", "highlight" to "raised", "art_fill" to "panel", "art_accent" to "accent")
        val system = Regex("system_(accent[123]|neutral[12])_(0|10|50|100|200|300|400|500|600|700|800|900|1000)")
        val hex = Regex("#[0-9A-Fa-f]{6}")
        fun camel(s: String) = s.split('_').mapIndexed { i, w -> if (i == 0) w else w.replaceFirstChar(Char::uppercase) }.joinToString("")
        fun str(v: Any?) = "\"" + (v as String).replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        fun num(v: Any?) = (v as Number).toFloat().toString() + "f"
        val files = looksDir.get().asFile.listFiles().orEmpty().filter { it.name.endsWith(".json") }
        val looks = files.map { file ->
            @Suppress("UNCHECKED_CAST")
            file.name.removeSuffix(".json") to (JsonSlurper().parse(file) as Map<String, Any?>)
        }.sortedWith(compareBy({ (it.second["order"] as? Number)?.toInt() ?: Int.MAX_VALUE }, { it.first }))
        val out = StringBuilder()
        out.appendLine("// Generated from data/looks by the generateDesktopLooks task. Do not edit.")
        out.appendLine("package io.github.omegadestroyer.pairfree.shell")
        out.appendLine()
        out.appendLine("/** The looks built in, in the order the picker lists them (data/looks, docs/LOOKS.md). */")
        out.appendLine("val BUILT_IN_LOOKS: List<DesktopLook> = listOf(")
        for ((id, look) in looks) {
            fun fail(why: String): Nothing = throw GradleException("data/looks/$id.json: $why")
            if (!Regex("[a-z][a-z0-9-]*").matches(id)) fail("the file name is the look's id: small letters, digits and hyphens")
            fun palette(key: String): String {
                @Suppress("UNCHECKED_CAST")
                val p = look[key] as? Map<String, Any?> ?: return "null"
                val unknown = p.keys - roles.toSet() - optional.keys
                if (unknown.isNotEmpty()) fail("$key has roles the app does not know: $unknown")
                return (roles + optional.keys).joinToString(", ", "DesktopPalette(", ")") { role ->
                    val v = (p[role] ?: p[optional[role]]) as? String ?: fail("$key lacks $role")
                    when {
                        hex.matches(v) -> "${camel(role)} = Tone.Fixed(0xFF${v.drop(1).uppercase()}.toInt())"
                        system.matches(v) -> "${camel(role)} = Tone.System(${str(v)})"
                        else -> fail("$key.$role is neither #RRGGBB nor a system colour: $v")
                    }
                }
            }
            if (look["dark"] == null && look["light"] == null) fail("needs a dark or a light palette")
            @Suppress("UNCHECKED_CAST")
            val type = look["type"] as? Map<String, Any?> ?: fail("lacks type")
            @Suppress("UNCHECKED_CAST")
            val shape = look["shape"] as? Map<String, Any?> ?: fail("lacks shape")
            @Suppress("UNCHECKED_CAST")
            val art = look["art"] as? Map<String, Any?> ?: fail("lacks art")
            out.appendLine("    DesktopLook(")
            out.appendLine("        id = ${str(id)},")
            out.appendLine("        nameKey = ${str("look_" + id.replace('-', '_'))},")
            out.appendLine("        dark = ${palette("dark")},")
            out.appendLine("        light = ${palette("light")},")
            out.appendLine("        textFont = ${str(type["text"])},")
            out.appendLine("        headingFont = ${str(type["headings"] ?: type["text"])},")
            out.appendLine("        numberFont = ${str(type["numbers"])},")
            out.appendLine("        labelCaps = ${type["label_caps"] as Boolean},")
            out.appendLine("        hairline = ${num(shape["hairline"])},")
            out.appendLine("        corner = ${num(shape["corner"])},")
            out.appendLine("        smallCorner = ${num(shape["small_corner"])},")
            out.appendLine("        panelBorder = ${shape["panel_border"] as Boolean},")
            out.appendLine("        uneven = ${num(shape["uneven"] ?: 0)},")
            out.appendLine("        shadow = ${num(shape["shadow"] ?: 0)},")
            out.appendLine("        selectedFill = ${(shape["selected_fill"] ?: false) as Boolean},")
            out.appendLine("        artStyle = ${str(art["style"])},")
            out.appendLine("        artStroke = ${num(art["stroke"])},")
            out.appendLine("        artOffset = ${num(art["offset"] ?: 0)},")
            out.appendLine("    ),")
        }
        out.appendLine(")")
        val dir = outputDir.get().asFile.resolve("io/github/omegadestroyer/pairfree/shell")
        dir.mkdirs()
        dir.resolve("BuiltInLooks.kt").writeText(out.toString())
    }
}

tasks.register<GenerateDesktopLooks>("generateDesktopLooks") {
    looksDir = rootProject.layout.projectDirectory.dir("../data/looks")
    outputDir = layout.buildDirectory.dir("generated/looks")
}
