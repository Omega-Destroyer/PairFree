import groovy.json.JsonSlurper
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The protocol core: frames, reassembly, EQ encoding, and the command and model tables.
// No Android dependencies, so iOS can reuse it. Keep src/main free of java.* imports
// so this can become a Kotlin Multiplatform module without rewriting it.

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

val dataDir: Directory = rootProject.layout.projectDirectory.dir("../data/soundpeats")

/** Writes Kotlin constants and tables from data/soundpeats/commands.json and data/soundpeats/devices.json, so no number is retyped. */
abstract class GenerateTables : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val commandsJson: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val devicesJson: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        @Suppress("UNCHECKED_CAST")
        val root = JsonSlurper().parse(commandsJson.get().asFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        fun obj(m: Map<String, Any?>, key: String) = m.getValue(key) as Map<String, Any?>
        fun hex(v: Any?) = "0x" + (v as String).uppercase()
        fun str(v: Any?) = "\"" + (v as String).replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
        fun intMap(v: Any?): String {
            @Suppress("UNCHECKED_CAST")
            val m = v as Map<String, Any?>? ?: emptyMap()
            return if (m.isEmpty()) "emptyMap()"
            else m.entries.joinToString(", ", "mapOf(", ")") { "${it.key.toInt()} to ${str(it.value)}" }
        }

        val frame = obj(root, "frame")
        val eq = obj(root, "eq")
        val transports = obj(root, "transports")
        val spp = obj(transports, "spp")
        val ble = obj(transports, "ble")
        val keys = obj(root, "custom_keys")
        val gaia = obj(root, "gaia")
        @Suppress("UNCHECKED_CAST")
        val commands = root.getValue("commands") as List<Map<String, Any?>>
        val kinds = commands.map { it.getValue("kind") as String }.distinct()

        val out = StringBuilder()
        out.appendLine("// Generated from data/soundpeats/commands.json by the generateTables task. Do not edit.")
        out.appendLine("package ${packageName.get()}")
        out.appendLine()
        out.appendLine("/** Fixed values of the frame format and the transports. */")
        out.appendLine("public object Wire {")
        out.appendLine("    public const val SOF: Int = ${hex(frame["sof"])}")
        out.appendLine("    public const val VERSION: Int = ${hex(frame["version"])}")
        out.appendLine("    public const val FLAGS: Int = ${hex(frame["flags"])}")
        out.appendLine("    public const val VENDOR_MAIN: Int = ${hex(frame["vendor_main"])}")
        out.appendLine("    public const val VENDOR_EQ: Int = ${hex(frame["vendor_eq"])}")
        out.appendLine("    public const val REPLY_MASK: Int = ${hex(frame["reply_mask"])}")
        out.appendLine("    public const val EQ_COMMAND_QUALCOMM: Int = ${hex(eq["command_qualcomm"])}")
        out.appendLine("    public const val EQ_COMMAND_OTHER: Int = ${hex(eq["command_other"])}")
        out.appendLine("    public const val SPP_UUID: String = ${str(spp["uuid"])}")
        out.appendLine("    public const val BLE_SERVICE: String = ${str(ble["service"])}")
        out.appendLine("    public const val BLE_WRITE: String = ${str(ble["write"])}")
        out.appendLine("    public const val BLE_NOTIFY: String = ${str(ble["notify"])}")
        out.appendLine("}")
        out.appendLine()
        out.appendLine("/** How a command's value is encoded. */")
        out.appendLine("public enum class Kind { ${kinds.joinToString(", ") { it.uppercase() }} }")
        out.appendLine()
        out.appendLine("/** Every command in data/soundpeats/commands.json. [get] or [set] is null when there is none. */")
        out.appendLine("public enum class Command(")
        out.appendLine("    public val id: String,")
        out.appendLine("    public val get: Int?,")
        out.appendLine("    public val set: Int?,")
        out.appendLine("    public val kind: Kind,")
        out.appendLine("    public val labels: Map<Int, String>,")
        out.appendLine("    /** False when data/soundpeats/commands.json marks the command do-not-probe: never read it unasked. */")
        out.appendLine("    public val probe: Boolean,")
        out.appendLine("    /** True when a model must list the command under confirmed in data/soundpeats/devices.json for the app to offer it. */")
        out.appendLine("    public val perModel: Boolean,")
        out.appendLine(") {")
        for (c in commands) {
            val id = c.getValue("id") as String
            val get = c["get"]?.let(::hex) ?: "null"
            val set = c["set"]?.let(::hex) ?: "null"
            val kind = (c.getValue("kind") as String).uppercase()
            out.appendLine("    ${id.uppercase()}(${str(id)}, $get, $set, Kind.$kind, ${intMap(c["values"])}, ${c["probe"] != false}, ${c["per_model"] == true}),")
        }
        out.appendLine("}")
        out.appendLine()
        out.appendLine("/** Gesture and action numbers for the custom keys command. */")
        out.appendLine("public object CustomKeys {")
        out.appendLine("    public val GESTURES: Map<Int, String> = ${intMap(keys["gestures"])}")
        out.appendLine("    public val ACTIONS: Map<Int, String> = ${intMap(keys["actions"])}")
        out.appendLine("}")
        out.appendLine()
        out.appendLine("/** Qualcomm GAIA v3 on [VENDOR], for QUALCOMM models only (docs/soundpeats/PROTOCOL.md section 11). */")
        out.appendLine("public object Gaia {")
        out.appendLine("    public const val VENDOR: Int = ${hex(gaia["vendor"])}")
        out.appendLine("    /** Added to a command's number in the earbuds' response to it. */")
        out.appendLine("    public const val RESPONSE: Int = ${hex(gaia["response"])}")
        out.appendLine("    public const val GET_FEATURES: Int = ${hex(gaia["get_features"])}")
        out.appendLine("    public const val REGISTER_NOTIFICATION: Int = ${hex(gaia["register_notification"])}")
        out.appendLine("    public const val FIT_FEATURE: Int = ${hex(gaia["fit_feature"])}")
        out.appendLine("    public const val FIT_START: Int = ${hex(gaia["fit_start"])}")
        out.appendLine("    public const val FIT_START_PAYLOAD: Int = ${hex(gaia["fit_start_payload"])}")
        out.appendLine("    public const val FIT_RESULT: Int = ${hex(gaia["fit_result"])}")
        out.appendLine("    /** What each per-earbud byte of a fit result means. */")
        out.appendLine("    public val FIT_RESULTS: Map<Int, String> = ${intMap(gaia["fit_results"])}")
        out.appendLine("}")
        out.appendLine()
        out.appendLine("/** Band layouts the official app's custom EQ screen offers. */")
        out.appendLine("public object CustomEqBands {")
        out.appendLine("    public val HZ: List<Int> = listOf(${(eq["custom_bands_hz"] as List<*>).joinToString(", ")})")
        out.appendLine("    public val HZ_BLUETRUM: List<Int> = listOf(${(eq["custom_bands_hz_bluetrum"] as List<*>).joinToString(", ")})")
        out.appendLine("    public const val Q: Double = ${eq["custom_band_q"]}")
        out.appendLine("}")

        @Suppress("UNCHECKED_CAST")
        val devices = (JsonSlurper().parse(devicesJson.get().asFile) as Map<String, Any?>).getValue("devices") as List<Map<String, Any?>>
        out.appendLine()
        out.appendLine("/** Every model in data/soundpeats/devices.json. Use [Models] to look one up. */")
        out.appendLine("internal val MODEL_TABLE: List<Model> = listOf(")
        fun num(v: Any?) = (v as Number).toDouble().toString()
        for (d in devices) {
            val names = (d.getValue("names") as List<*>).joinToString(", ", "listOf(", ")") { str(it) }
            val excludes = (d["ldac_excludes"] as List<*>? ?: emptyList<Any>())
                .joinToString(", ", "setOf(", ")") { "Command.${(it as String).uppercase()}" }
            val either = d["ldac_or_multipoint"] == true
            val keepsAnc = d["fit_keeps_anc"] == true
            val confirmed = (d["confirmed"] as List<*>? ?: emptyList<Any>())
                .joinToString(", ", "setOf(", ")") { "Command.${(it as String).uppercase()}" }
            @Suppress("UNCHECKED_CAST")
            val eqJson = d["eq"] as Map<String, Any?>?
            val eq = if (eqJson == null) {
                "null"
            } else {
                @Suppress("UNCHECKED_CAST")
                fun bands(list: Any?, gainKey: String) = (list as List<Map<String, Any?>>).joinToString(", ", "listOf(", ")") {
                    "EqBand(${(it.getValue("hz") as Number).toInt()}, ${num(it["q"])}, ${num(it[gainKey])})"
                }
                @Suppress("UNCHECKED_CAST")
                val presets = (eqJson["presets"] as List<Map<String, Any?>>? ?: emptyList()).joinToString(", ", "listOf(", ")") {
                    "EqPreset(${str(it["name"])}, ${bands(it["bands"], "gain_db")})"
                }
                "EqLayout(${hex(eqJson["command"])}, ${num(eqJson["master_gain_db"])}, " +
                    "${(eqJson.getValue("user_range_db") as Number).toInt()}, ${bands(eqJson["bands"], "base_gain_db")}, $presets)"
            }
            out.appendLine("    Model(${str(d["firmware_code"])}, $names, ${str(d["family"])}, $excludes, $either, $eq, $confirmed, $keepsAnc),")
        }
        out.appendLine(")")

        val dir = outputDir.get().asFile.resolve(packageName.get().replace('.', '/'))
        dir.mkdirs()
        dir.resolve("Tables.kt").writeText(out.toString())
    }
}

val generateTables = tasks.register<GenerateTables>("generateTables") {
    commandsJson = dataDir.file("commands.json")
    devicesJson = dataDir.file("devices.json")
    packageName = "io.github.omegadestroyer.soundpeter.protocol"
    outputDir = layout.buildDirectory.dir("generated/tables")
}

/** Writes the Soundcore frame constants and command numbers from data/soundcore/commands.json. */
abstract class GenerateSoundcoreTables : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val commandsJson: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        @Suppress("UNCHECKED_CAST")
        val json = JsonSlurper().parse(commandsJson.get().asFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val frame = json.getValue("frame") as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val commands = json.getValue("commands") as List<Map<String, Any?>>
        fun bytes(hex: String) = hex.chunked(2).joinToString(", ") { "0x$it.toByte()" }

        val out = StringBuilder()
        out.appendLine("// Generated from data/soundcore/commands.json by the generateSoundcoreTables task. Do not edit.")
        out.appendLine("package ${packageName.get()}")
        out.appendLine()
        out.appendLine("/** Soundcore frame constants (docs/soundcore/PROTOCOL.md section 2). */")
        out.appendLine("public object SoundcoreWire {")
        out.appendLine("    /** Direction header, command and length: the bytes before the body. */")
        out.appendLine("    public const val HEADER_SIZE: Int = ${frame.getValue("header_size")}")
        out.appendLine("    /** The earbuds' vendor RFCOMM service UUIDs start with this; the last five hex digits vary. */")
        out.appendLine("    public const val VENDOR_SERVICE_PREFIX: String = \"${frame.getValue("vendor_service_uuid_prefix")}\"")
        out.appendLine("    internal val TO_EARBUDS_HEADER: ByteArray = byteArrayOf(${bytes(frame.getValue("to_earbuds_header") as String)})")
        out.appendLine("    internal val FROM_EARBUDS_HEADER: ByteArray = byteArrayOf(${bytes(frame.getValue("from_earbuds_header") as String)})")
        out.appendLine("}")
        out.appendLine()
        out.appendLine("/** Soundcore command numbers: the two command bytes as one number, so `01 01` is 0x0101. */")
        out.appendLine("public object SoundcoreCommand {")
        for (c in commands) {
            c["note"]?.let { out.appendLine("    /** $it */") }
            out.appendLine("    public const val ${(c.getValue("id") as String).uppercase()}: Int = 0x${c.getValue("command")}")
        }
        val never = commands.filter { it["never_send"] == true }.joinToString(", ") { "0x${it.getValue("command")}" }
        out.appendLine("    /** Commands SoundPeter never sends. */")
        out.appendLine("    public val NEVER_SEND: Set<Int> = setOf($never)")
        out.appendLine("}")

        val dir = outputDir.get().asFile.resolve(packageName.get().replace('.', '/'))
        dir.mkdirs()
        dir.resolve("SoundcoreTables.kt").writeText(out.toString())
    }
}

val generateSoundcoreTables = tasks.register<GenerateSoundcoreTables>("generateSoundcoreTables") {
    commandsJson = rootProject.layout.projectDirectory.file("../data/soundcore/commands.json")
    packageName = "io.github.omegadestroyer.soundpeter.protocol.soundcore"
    outputDir = layout.buildDirectory.dir("generated/soundcore")
}

kotlin.sourceSets.main {
    kotlin.srcDir(generateTables)
    kotlin.srcDir(generateSoundcoreTables)
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.serialization.json)
}

tasks.test {
    useJUnitPlatform()
    inputs.dir(dataDir).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/soundcore")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("../tools/probe.py")).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("soundpeter.data", dataDir.asFile.absolutePath)
}
