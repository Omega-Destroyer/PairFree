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

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val qcyJson: RegularFileProperty

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
        out.appendLine("    /** True when the reply holds other people's devices or the like: never put it in a report, even from a recording. */")
        out.appendLine("    public val private: Boolean = false,")
        out.appendLine(") {")
        for (c in commands) {
            val id = c.getValue("id") as String
            val get = c["get"]?.let(::hex) ?: "null"
            val set = c["set"]?.let(::hex) ?: "null"
            val kind = (c.getValue("kind") as String).uppercase()
            val private = if (c["private"] == true) ", private = true" else ""
            out.appendLine("    ${id.uppercase()}(${str(id)}, $get, $set, Kind.$kind, ${intMap(c["values"])}, ${c["probe"] != false}, ${c["per_model"] == true}$private),")
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
        // Each model's EQ is built in a function of its own: in one initializer they pass the JVM's 64 KB method limit.
        val eqFunctions = StringBuilder()
        for ((index, d) in devices.withIndex()) {
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
                val fixed = eqJson["fixed_bands"]?.let { ", fixedBands = ${bands(it, "gain_db")}" } ?: ""
                eqFunctions.appendLine("private fun eq$index(): EqLayout = EqLayout(${hex(eqJson["command"])}, ${num(eqJson["master_gain_db"])}, " +
                    "${(eqJson.getValue("user_range_db") as Number).toInt()}, ${bands(eqJson["bands"], "base_gain_db")}, $presets$fixed)")
                "eq$index()"
            }
            // The server's noise control for the model: absent features means the list was not fetched (null).
            @Suppress("UNCHECKED_CAST")
            val featuresJson = d["features"] as Map<String, Any?>?
            val noise = if (featuresJson == null) "" else {
                @Suppress("UNCHECKED_CAST")
                val modes = (featuresJson["noise"] as List<Map<String, Any?>>? ?: emptyList()).joinToString(";") { m ->
                    val scenes = (m["scenes"] as List<*>).joinToString(",")
                    "${m["mode"]}" + if (scenes.isEmpty()) "" else ":$scenes"
                }
                ", noiseModes = noiseModes(\"$modes\")"
            }
            out.appendLine("    Model(${str(d["firmware_code"])}, $names, ${str(d["family"])}, $excludes, $either, $eq, $confirmed, $keepsAnc, fastPair = ${d["fast_pair"]}$noise, touchInverted = ${d["touch_disable_inverted"] == true}),")
        }
        out.appendLine(")")
        out.appendLine()
        out.append(eqFunctions)

        // The Capsule3 Pro's QCY protocol (data/soundpeats/qcy_commands.json, docs/soundpeats/PROTOCOL.md section 10).
        @Suppress("UNCHECKED_CAST")
        val qcy = JsonSlurper().parse(qcyJson.get().asFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val qcyItems = (qcy.getValue("items") as List<Map<String, Any?>>).associateBy { it.getValue("name") as String }
        @Suppress("UNCHECKED_CAST")
        val noise = qcyItems.getValue("noise_mode").getValue("values") as Map<String, String>
        @Suppress("UNCHECKED_CAST")
        val buttons = obj(qcy, "buttons").getValue("functions") as Map<String, String>
        out.appendLine()
        out.appendLine("/** The SOUNDPEATS models built by QCY (data/soundpeats/qcy_commands.json). */")
        out.appendLine("public object CapsuleWire {")
        out.appendLine("    public const val COMPANY: Int = ${hex(obj(obj(qcy, "transport"), "advertisement")["manufacturer_id"])}")
        for ((v, name) in noise) out.appendLine("    public const val NOISE_${name.uppercase()}: Int = ${hex(v)}")
        for (name in listOf("noise_mode", "game_mode", "touch_disabled", "ldac", "multipoint")) {
            out.appendLine("    public const val ${name.uppercase()}: Int = ${hex(qcyItems.getValue(name)["id"])}")
        }
        out.appendLine("    public const val ACTION_ANC_CYCLE: Int = ${buttons.entries.single { it.value == "ANC cycle" }.key}")
        out.appendLine("}")

        val dir = outputDir.get().asFile.resolve(packageName.get().replace('.', '/'))
        dir.mkdirs()
        dir.resolve("Tables.kt").writeText(out.toString())
    }
}

val generateTables = tasks.register<GenerateTables>("generateTables") {
    commandsJson = dataDir.file("commands.json")
    devicesJson = dataDir.file("devices.json")
    qcyJson = dataDir.file("qcy_commands.json")
    packageName = "io.github.omegadestroyer.pairfree.protocol"
    outputDir = layout.buildDirectory.dir("generated/tables")
}

/** Writes the Soundcore frame constants, command numbers and models from data/soundcore. */
abstract class GenerateSoundcoreTables : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val commandsJson: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val devicesJson: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val eqPresetsJson: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val buttonActionsJson: RegularFileProperty

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
        out.appendLine("    /** Commands PairFree never sends. */")
        out.appendLine("    public val NEVER_SEND: Set<Int> = setOf($never)")
        val private = commands.filter { it["private"] == true }.joinToString(", ") { "0x${it.getValue("command")}" }
        out.appendLine("    /** Commands whose body can carry personal data; a recording of the official app leaves it out. */")
        out.appendLine("    public val PRIVATE: Set<Int> = setOf($private)")
        val names = commands.joinToString(", ") { "0x${it.getValue("command")} to \"${it.getValue("id")}\"" }
        out.appendLine("    /** Each command's name in data/soundcore/commands.json. */")
        out.appendLine("    public val NAMES: Map<Int, String> = mapOf($names)")
        val captureSizes = commands.filter { it["capture_max_bytes"] != null }
            .joinToString(", ") { "0x${it.getValue("command")} to ${it.getValue("capture_max_bytes")}" }
        out.appendLine("    /** Maximum verified recording body size; larger bodies get only a short preview. */")
        out.appendLine("    public val CAPTURE_MAX_BYTES: Map<Int, Int> = mapOf($captureSizes)")
        out.appendLine("}")

        @Suppress("UNCHECKED_CAST")
        val devices = (JsonSlurper().parse(devicesJson.get().asFile) as Map<String, Any?>).getValue("devices") as List<Map<String, Any?>>
        fun str(v: Any?) = "\"" + (v as String) + "\""
        out.appendLine()
        out.appendLine("/** Every model in data/soundcore/devices.json, as a [io.github.omegadestroyer.pairfree.protocol.Model] for the screens. */")
        out.appendLine("internal val SOUNDCORE_MODEL_TABLE: List<io.github.omegadestroyer.pairfree.protocol.Model> = listOf(")
        for (d in devices) {
            val names = (d.getValue("names") as List<*>).joinToString(", ", "listOf(", ")") { str(it) }
            val excludes = (d["ldac_excludes"] as List<*>? ?: emptyList<Any>())
                .joinToString(", ", "setOf(", ")") { "io.github.omegadestroyer.pairfree.protocol.Command.${(it as String).uppercase()}" }
            val form = "io.github.omegadestroyer.pairfree.protocol.Form." + ((d["kind"] as String?) ?: "earbuds").uppercase()
            out.appendLine("    io.github.omegadestroyer.pairfree.protocol.Model(${str(d["model"])}, $names, \"SOUNDCORE\", ldacExcludes = $excludes, form = $form, fastPair = ${d["fast_pair"]}, reportOnly = ${d["report_only"] == true}),")
        }
        out.appendLine(")")

        out.appendLine()
        out.appendLine("/** Every model's state layout and quirks (data/soundcore/devices.json), read from OpenSCQ30's parsers. */")
        // Split into functions of a dozen layouts each: one initialiser holding every model overflows the JVM's method size limit.
        val layoutLines = mutableListOf<String>()
        for (d in devices) {
            @Suppress("UNCHECKED_CAST")
            val state = d["state"] as List<List<Any?>>? ?: continue
            val fields = state.joinToString(", ", "listOf(", ")") { f ->
                val name = f[0] as String
                val optional = if (name.startsWith("optional:")) ", true" else ""
                "SoundcoreField(${str(name.removePrefix("optional:"))}, ${(f[1] as Number).toInt()}$optional)"
            }
            @Suppress("UNCHECKED_CAST")
            val battery = d["battery"] as Map<String, Any?>? ?: emptyMap()
            fun scale(key: String): String {
                @Suppress("UNCHECKED_CAST")
                val s = battery[key] as Map<String, Any?>? ?: return "null"
                return "BatteryScale(${(s.getValue("max") as Number).toInt()}, ${(s.getValue("offset") as Number).toInt()})"
            }
            val checksum = d["checksum"] != false
            val uuid = (d["service_uuid"] as String?)?.let { str(it) } ?: "null"
            @Suppress("UNCHECKED_CAST")
            val gaming = ((d["commands"] as Map<String, Any?>?)?.get("gaming_mode") as String?)?.let { "0x$it" }
                ?: "SoundcoreCommand.GAMING_MODE"
            @Suppress("UNCHECKED_CAST")
            val minFirmware = (d["min_firmware"] as Map<String, Any?>? ?: emptyMap())
                .entries.joinToString(", ", "mapOf(", ")") { (k, v) -> "${str(k)} to ${str(v)}" }
            // What OpenSCQ30 offers, what the Soundcore app's own code shows on a model OpenSCQ30 lacks, and what PairFree
            // confirmed, less what the Soundcore app shows the model does not have.
            @Suppress("UNCHECKED_CAST")
            val app = d["soundcore_app"] as Map<String, Any?>? ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val features = ((((d["openscq30"] as Map<String, Any?>?)?.get("features") as List<*>? ?: emptyList<Any>()) +
                (app["features"] as List<*>? ?: emptyList<Any>()) + (d["extra_features"] as List<*>? ?: emptyList<Any>())) -
                (app["lacks"] as List<*>? ?: emptyList<Any>()).toSet()).distinct().joinToString(", ", "setOf(", ")") { str(it) }
            val vendorIds = (d["vendor_ids"] as List<*>? ?: emptyList<Any>()).joinToString(", ", "setOf(", ")") { str(it) }
            layoutLines += "        SoundcoreLayout(${str(d["model"])}, $fields, ${scale("earbuds")}, ${scale("case")}, $checksum, $uuid, $gaming, $minFirmware, $features, $vendorIds),"
        }
        val chunks = layoutLines.chunked(12)
        chunks.forEachIndexed { i, lines ->
            out.appendLine("private fun soundcoreLayouts$i(): List<SoundcoreLayout> = listOf(")
            lines.forEach(out::appendLine)
            out.appendLine("    )")
        }
        out.appendLine("internal val SOUNDCORE_LAYOUTS: List<SoundcoreLayout> = " + chunks.indices.joinToString(" + ") { "soundcoreLayouts$it()" })

        @Suppress("UNCHECKED_CAST")
        val actionSets = (JsonSlurper().parse(buttonActionsJson.get().asFile) as Map<String, Any?>).getValue("sets") as Map<String, List<List<Any?>>>
        fun enumName(v: Any?) = (v as String).uppercase()
        fun side(button: String) = if (button.startsWith("left_")) "TouchSide.LEFT" else "TouchSide.RIGHT"
        fun press(name: String) = "TouchPress.${enumName(name)}"
        out.appendLine()
        out.appendLine("/** Every model's touch controls (data/soundcore/devices.json buttons, data/soundcore/button_actions.json), read from OpenSCQ30. */")
        out.appendLine("internal val SOUNDCORE_BUTTONS: List<SoundcoreButtonSpec> = listOf(")
        for (d in devices) {
            @Suppress("UNCHECKED_CAST")
            val buttons = d["buttons"] as Map<String, Any?>? ?: continue
            @Suppress("UNCHECKED_CAST")
            val groups = (buttons.getValue("groups") as List<Map<String, Any?>>).joinToString(",\n        ", "listOf(\n        ", ",\n    )") { g ->
                val order = (g.getValue("order") as List<*>).joinToString(", ", "listOf(", ")") {
                    val b = it as String
                    "${side(b)} to ${press(b.substringAfter('_'))}"
                }
                @Suppress("UNCHECKED_CAST")
                val presses = (g.getValue("presses") as Map<String, Map<String, Any?>>).entries.joinToString(", ", "mapOf(", ")") { (name, p) ->
                    val actions = actionSets.getValue(p.getValue("actions") as String).joinToString(", ", "listOf(", ")") { a ->
                        "SoundcoreAction(${(a[0] as Number).toInt()}, ${str(a[1])})"
                    }
                    val off = when (p.getValue("off")) {
                        "never" -> "TurnOff.NEVER"
                        "each_side" -> "TurnOff.EACH_SIDE"
                        "both_sides" -> "TurnOff.BOTH_SIDES"
                        else -> error("unknown off rule ${p["off"]}")
                    }
                    "${press(name)} to SoundcorePressSpec(${press(name)}, ${(p.getValue("id") as Number).toInt()}, " +
                        "ButtonByte.${enumName(p["enabled"])}, ButtonByte.${enumName(p["action"])}, $off, $actions)"
                }
                val command = (g["command"] as String?)?.let { "0x$it" } ?: "SoundcoreCommand.BUTTON_ACTION"
                "SoundcoreButtonGroup(${(g.getValue("fields") as List<*>).joinToString(", ", "listOf(", ")") { str(it) }}, $order, $presses, " +
                    "setAll = ${g["set_all"] == true}, ignoreEnabled = ${g["ignore_enabled"] == true}, actionCommand = $command, " +
                    "duringCall = ${g["during"] == "call"})"
            }
            @Suppress("UNCHECKED_CAST")
            val fixed = (buttons["fixed"] as Map<String, Any?>? ?: emptyMap()).entries.joinToString(", ", "mapOf(", ")") { (k, v) -> "${press(k)} to ${str(v)}" }
            out.appendLine(
                "    SoundcoreButtonSpec(${str(d["model"])}, $groups, reset = ${buttons["reset"] == true}, fixed = $fixed, " +
                    "checked = ${(buttons["checked"] as String?)?.let { str(it) } ?: "null"}),",
            )
        }
        out.appendLine(")")

        fun ints(v: Any?) = (v as List<*>).joinToString(", ") { (it as Number).toInt().toString() }
        @Suppress("UNCHECKED_CAST")
        val sets = (JsonSlurper().parse(eqPresetsJson.get().asFile) as Map<String, Any?>).getValue("sets") as Map<String, Map<String, Any?>>
        out.appendLine()
        out.appendLine("/** The EQ preset sets in data/soundcore/eq_presets.json, read from OpenSCQ30. */")
        out.appendLine("internal val SOUNDCORE_PRESET_SETS: Map<String, SoundcorePresetSet> = mapOf(")
        for ((name, s) in sets) {
            @Suppress("UNCHECKED_CAST")
            val presets = (s.getValue("presets") as List<Map<String, Any?>>).joinToString(",\n        ", "listOf(\n        ", ",\n    )") { p ->
                "SoundcorePreset(${(p.getValue("id") as Number).toInt()}, ${str(p["key"])}, ${str(p["name"])}, intArrayOf(${ints(p["values"])}))"
            }
            val custom = (s["custom_preset"] as Number?)?.toInt()?.toString() ?: "null"
            val range = s.getValue("custom_range") as List<*>
            out.appendLine("    ${str(name)} to SoundcorePresetSet($custom, listOf(${ints(s["band_hz"])}), listOf(${ints(s["hidden_bands"])}), " +
                "${(range[0] as Number).toInt()}, ${(range[1] as Number).toInt()}, $presets),")
        }
        out.appendLine(")")

        out.appendLine()
        out.appendLine("/** Every model's equalizer (data/soundcore/devices.json eq), read from OpenSCQ30. */")
        out.appendLine("internal val SOUNDCORE_EQ: List<SoundcoreEqSpec> = listOf(")
        for (d in devices) {
            @Suppress("UNCHECKED_CAST")
            val eq = d["eq"] as Map<String, Any?>? ?: continue
            val range = eq.getValue("range") as List<*>
            val channels = (eq.getValue("channels") as Number).toInt()
            @Suppress("UNCHECKED_CAST")
            val hearId = (eq["hear_id"] as Map<String, Any?>?)?.let { h ->
                "SoundcoreHearIdLayout(${str(h["field"])}, ${(h.getValue("channels") as Number).toInt()}, " +
                    "${(h.getValue("bands") as Number).toInt()}, listOf(${(h.getValue("parts") as List<*>).joinToString(", ") { str(it) }}), " +
                    "${h["empty_curve_ends_in_zero"] == true})"
            } ?: "null"
            out.appendLine(
                "    SoundcoreEqSpec(${str(d["model"])}, ${str(eq["write"])}, $channels, ${(eq.getValue("bands") as Number).toInt()}, " +
                    "${(range[0] as Number).toInt()}, ${(range[1] as Number).toInt()}, ${(eq.getValue("digits") as Number).toInt()}, " +
                    "presets = ${str(eq["presets"])}, presetBytes = ${(eq["preset_bytes"] as Number?)?.toInt() ?: 2}, " +
                    "stateChannels = ${(eq["state_channels"] as Number?)?.toInt() ?: channels}, presetOnly = ${eq["state"] == "preset_only"}, " +
                    "hearId = $hearId, gender = ${(eq["gender"] as Number?)?.toInt() ?: "null"}, " +
                    "checked = ${(eq["checked"] as String?)?.let { str(it) } ?: "null"}),",
            )
        }
        out.appendLine(")")

        val dir = outputDir.get().asFile.resolve(packageName.get().replace('.', '/'))
        dir.mkdirs()
        dir.resolve("SoundcoreTables.kt").writeText(out.toString())
    }
}

val generateSoundcoreTables = tasks.register<GenerateSoundcoreTables>("generateSoundcoreTables") {
    commandsJson = rootProject.layout.projectDirectory.file("../data/soundcore/commands.json")
    devicesJson = rootProject.layout.projectDirectory.file("../data/soundcore/devices.json")
    eqPresetsJson = rootProject.layout.projectDirectory.file("../data/soundcore/eq_presets.json")
    buttonActionsJson = rootProject.layout.projectDirectory.file("../data/soundcore/button_actions.json")
    packageName = "io.github.omegadestroyer.pairfree.protocol.soundcore"
    outputDir = layout.buildDirectory.dir("generated/soundcore")
}

/** Writes the Samsung message numbers and models from data/samsung. */
abstract class GenerateSamsungTables : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val messagesJson: RegularFileProperty

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
        val messages = (JsonSlurper().parse(messagesJson.get().asFile) as Map<String, Any?>).getValue("messages") as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val devices = (JsonSlurper().parse(devicesJson.get().asFile) as Map<String, Any?>).getValue("devices") as List<Map<String, Any?>>
        fun str(v: Any?) = "\"" + (v as String) + "\""
        fun int(v: Any?) = (v as Number).toInt()

        val out = StringBuilder()
        out.appendLine("// Generated from data/samsung by the generateSamsungTables task. Do not edit.")
        out.appendLine("package ${packageName.get()}")
        out.appendLine()
        out.appendLine("/** Samsung message numbers (data/samsung/messages.json), from GalaxyBudsClient. */")
        out.appendLine("public object SamsungMessage {")
        for (m in messages) {
            m["note"]?.let { out.appendLine("    /** $it */") }
            out.appendLine("    public const val ${(m.getValue("id") as String).uppercase()}: Int = ${int(m["number"])}")
        }
        val never = messages.filter { it["never_send"] == true }.joinToString(", ") { int(it["number"]).toString() }
        out.appendLine("    /** Messages PairFree never sends. */")
        out.appendLine("    public val NEVER_SEND: Set<Int> = setOf($never)")
        val names = messages.joinToString(", ") { "${int(it["number"])} to \"${it.getValue("id")}\"" }
        out.appendLine("    /** Each message's name in data/samsung/messages.json. */")
        out.appendLine("    public val NAMES: Map<Int, String> = mapOf($names)")
        out.appendLine("}")
        out.appendLine()
        out.appendLine("/** Every model in data/samsung/devices.json, with GalaxyBudsClient's specification of it. */")
        out.appendLine("internal val SAMSUNG_MODELS: List<SamsungModel> = listOf(")
        for (d in devices) {
            // Samsung's own plugin (samsung_app) wins over GalaxyBudsClient field by field.
            @Suppress("UNCHECKED_CAST")
            val app = d["samsung_app"] as Map<String, Any?>? ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val g = (d["galaxybudsclient"] as Map<String, Any?>? ?: emptyMap()) + app
            val names = (d.getValue("names") as List<*>).joinToString(", ", "listOf(", ")") { str(it) }
            val matchNames = (app["match_names"] as List<*>? ?: emptyList<Any>())
            val match = matchNames.joinToString(", ", "listOf(", ")") { str(it) }
            val baseName = g["base_name"] ?: matchNames.first()
            val layout = app["layout"]?.let { str(it) } ?: "null"
            val form = if (d["kind"] == "headphones") "io.github.omegadestroyer.pairfree.protocol.Form.HEADPHONES" else "io.github.omegadestroyer.pairfree.protocol.Form.EARBUDS"
            val touch = (g.getValue("touch_hold") as List<*>).joinToString(", ", "listOf(", ")") { p ->
                val pair = p as List<*>
                "${int(pair[0])} to ${str(pair[1])}"
            }
            @Suppress("UNCHECKED_CAST")
            val colors = (g.getValue("colors") as Map<String, Any?>).entries.joinToString(", ", "mapOf(", ")") { (k, v) -> "$k to ${str(v)}" }
            @Suppress("UNCHECKED_CAST")
            val features = (g.getValue("features") as Map<String, Any?>).entries.joinToString(", ", "mapOf(", ")") { (k, v) ->
                "${str(k)} to ${(v as Number?)?.toInt() ?: "null"}"
            }
            out.appendLine(
                "    SamsungModel(${str(d["model"])}, $names, ${str(g["device"])}, ${int(g["generation"])}, ${str(baseName)}, " +
                    "${str(g["service_uuid"])}, legacy = ${g["framing"] == "legacy"}, maxAmbientVolume = ${int(g["max_ambient_volume"])}, " +
                    "touchHold = $touch, colors = $colors, features = $features, layout = $layout, matchNames = $match, form = $form),",
            )
        }
        out.appendLine(")")

        val dir = outputDir.get().asFile.resolve(packageName.get().replace('.', '/'))
        dir.mkdirs()
        dir.resolve("SamsungTables.kt").writeText(out.toString())
    }
}

val generateSamsungTables = tasks.register<GenerateSamsungTables>("generateSamsungTables") {
    messagesJson = rootProject.layout.projectDirectory.file("../data/samsung/messages.json")
    devicesJson = rootProject.layout.projectDirectory.file("../data/samsung/devices.json")
    packageName = "io.github.omegadestroyer.pairfree.protocol.samsung"
    outputDir = layout.buildDirectory.dir("generated/samsung")
}

/** Writes Sony's command numbers, function codes and models from data/sony. */
abstract class GenerateSonyTables : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val commandsJson: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val functionsJson: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val devicesJson: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val presetsJson: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        @Suppress("UNCHECKED_CAST")
        val commands = JsonSlurper().parse(commandsJson.get().asFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val functions = JsonSlurper().parse(functionsJson.get().asFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val devices = (JsonSlurper().parse(devicesJson.get().asFile) as Map<String, Any?>).getValue("devices") as List<Map<String, Any?>>
        fun str(v: Any?) = "\"" + (v as String) + "\""
        fun int(v: Any?) = (v as Number).toInt()
        fun hex(v: Int) = "0x" + v.toString(16).uppercase().padStart(2, '0')

        val out = StringBuilder()
        out.appendLine("// Generated from data/sony by the generateSonyTables task. Do not edit.")
        out.appendLine("package ${packageName.get()}")
        out.appendLine()
        @Suppress("UNCHECKED_CAST")
        val services = commands.getValue("services") as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val types = commands.getValue("data_types") as Map<String, Any?>
        out.appendLine("/** Sony's services and data types (data/sony/commands.json). */")
        out.appendLine("public object SonyWire {")
        out.appendLine("    /** The RFCOMM service of table set 1. */")
        out.appendLine("    public const val SET1_SERVICE: String = ${str(services["set1"])}")
        out.appendLine("    /** The RFCOMM service of table set 2. */")
        out.appendLine("    public const val SET2_SERVICE: String = ${str(services["set2"])}")
        out.appendLine("    public const val ACK: Int = ${hex(int(types["ack"]))}")
        out.appendLine("    /** The data type of table 1's messages. */")
        out.appendLine("    public const val TABLE1: Int = ${hex(int(types["table1"]))}")
        out.appendLine("    /** The data type of table 2's messages. */")
        out.appendLine("    public const val TABLE2: Int = ${hex(int(types["table2"]))}")
        out.appendLine("}")
        out.appendLine()
        @Suppress("UNCHECKED_CAST")
        val tables = commands.getValue("tables") as Map<String, Map<String, Any?>>
        val objects = mapOf("set1_table1" to "Set1", "set1_table2" to "Set1Table2", "set2_table1" to "Set2", "set2_table2" to "Set2Table2")
        out.appendLine("/** Sony's command numbers (data/sony/commands.json), one object per table: Set1 and Set2 are table 1 of each set. */")
        out.appendLine("public object SonyCommand {")
        for ((key, obj) in objects) {
            @Suppress("UNCHECKED_CAST")
            val rows = tables.getValue(key)["commands"] as List<Map<String, Any?>>
            out.appendLine("    public object $obj {")
            for (c in rows) out.appendLine("        public const val ${c["name"]}: Int = ${hex(int(c["number"]))}")
            val never = rows.filter { it["never_send"] == true }.joinToString(", ") { hex(int(it["number"])) }
            out.appendLine("        /** Commands PairFree never sends: firmware update, test and logs. */")
            out.appendLine("        public val NEVER_SEND: Set<Int> = setOf($never)")
            out.appendLine("        public val NAMES: Map<Int, String> = mapOf(${rows.joinToString(", ") { "${hex(int(it["number"]))} to ${str(it["name"])}" }})")
            out.appendLine("    }")
        }
        out.appendLine("}")
        out.appendLine()
        out.appendLine("/** The functions earbuds list in their reply to CONNECT_GET_SUPPORT_FUNCTION (data/sony/functions.json). */")
        out.appendLine("public object SonyFunction {")
        for ((key, obj) in listOf("set1" to "Set1", "set2_table1" to "Set2", "set2_table2" to "Set2Table2")) {
            @Suppress("UNCHECKED_CAST")
            val rows = functions.getValue(key) as List<Map<String, Any?>>
            out.appendLine("    public object $obj {")
            for (f in rows) out.appendLine("        public const val ${f["name"]}: Int = ${hex(int(f["code"]))}")
            out.appendLine("        public val NAMES: Map<Int, String> = mapOf(${rows.joinToString(", ") { "${hex(int(it["code"]))} to ${str(it["name"])}" }})")
            out.appendLine("    }")
        }
        out.appendLine("}")
        out.appendLine()
        @Suppress("UNCHECKED_CAST")
        val presetData = JsonSlurper().parse(presetsJson.get().asFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val presets = presetData.getValue("presets") as List<Map<String, Any?>>
        out.appendLine("/** Sony's EQ presets by id (data/sony/eq_presets.json). */")
        out.appendLine("internal val SONY_EQ_PRESETS: List<SonyEqPreset> = listOf(")
        for (e in presets) out.appendLine("    SonyEqPreset(${hex(int(e["id"]))}, ${str(e["name"])}, ${str(e["label"])}, custom = ${e["custom"] == true}),")
        out.appendLine(")")
        out.appendLine()
        out.appendLine("/** The preset id that names no preset, which set 1 sends with its own bands. */")
        out.appendLine("internal const val SONY_EQ_UNSPECIFIED: Int = ${hex(int(presetData["unspecified"]))}")
        out.appendLine()
        out.appendLine("/** Every model in data/sony/devices.json. */")
        out.appendLine("internal val SONY_MODELS: List<SonyModel> = listOf(")
        for (d in devices) {
            val names = (d.getValue("names") as List<*>).joinToString(", ", "listOf(", ")") { str(it) }
            val form = when (d["kind"]) {
                "earbuds" -> "EARBUDS"
                "headphones", "neckband" -> "HEADPHONES"
                else -> error("unknown kind ${d["kind"]}")
            }
            val set = (d["table_set"] as Number?)?.toInt()?.toString() ?: "null"
            out.appendLine("    SonyModel(${str(d["name"])}, $names, io.github.omegadestroyer.pairfree.protocol.Form.$form, tableSet = $set),")
        }
        out.appendLine(")")

        val dir = outputDir.get().asFile.resolve(packageName.get().replace('.', '/'))
        dir.mkdirs()
        dir.resolve("SonyTables.kt").writeText(out.toString())
    }
}

val generateSonyTables = tasks.register<GenerateSonyTables>("generateSonyTables") {
    commandsJson = rootProject.layout.projectDirectory.file("../data/sony/commands.json")
    functionsJson = rootProject.layout.projectDirectory.file("../data/sony/functions.json")
    devicesJson = rootProject.layout.projectDirectory.file("../data/sony/devices.json")
    presetsJson = rootProject.layout.projectDirectory.file("../data/sony/eq_presets.json")
    packageName = "io.github.omegadestroyer.pairfree.protocol.sony"
    outputDir = layout.buildDirectory.dir("generated/sony")
}

/** Writes Bose's BMAP functions and models from data/bose. */
abstract class GenerateBoseTables : DefaultTask() {
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
        val commands = JsonSlurper().parse(commandsJson.get().asFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val devices = (JsonSlurper().parse(devicesJson.get().asFile) as Map<String, Any?>).getValue("devices") as List<Map<String, Any?>>
        fun str(v: Any?) = "\"" + (v as String) + "\""
        fun int(v: Any?) = (v as Number).toInt()
        fun hex(v: Int, digits: Int) = "0x" + v.toString(16).uppercase().padStart(digits, '0')
        // SettingsMultipoint -> SETTINGS_MULTIPOINT, ControlANROnly -> CONTROL_ANR_ONLY.
        fun snake(name: String) = name.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2").uppercase()

        val out = StringBuilder()
        out.appendLine("// Generated from data/bose by the generateBoseTables task. Do not edit.")
        out.appendLine("package ${packageName.get()}")
        out.appendLine()
        @Suppress("UNCHECKED_CAST")
        val operators = commands.getValue("operators") as Map<String, Any?>
        out.appendLine("/** BMAP operators (data/bose/commands.json), the low four bits of a packet's third byte. */")
        out.appendLine("public object BoseOperator {")
        for ((name, value) in operators) out.appendLine("    public const val ${name.uppercase()}: Int = ${int(value)}")
        out.appendLine("}")
        out.appendLine()
        @Suppress("UNCHECKED_CAST")
        val blocks = commands.getValue("blocks") as List<Map<String, Any?>>
        out.appendLine("/**")
        out.appendLine(" * Bose's BMAP functions (data/bose/commands.json), each as its function block shifted left by 8 and its")
        out.appendLine(" * function: [block] and [function] take them apart.")
        out.appendLine(" */")
        out.appendLine("public object BoseFunction {")
        val seen = mutableSetOf<String>()
        val all = mutableListOf<Triple<String, Int, Boolean>>()
        for (b in blocks) {
            @Suppress("UNCHECKED_CAST")
            for (f in b.getValue("functions") as List<Map<String, Any?>>) {
                val name = snake(f.getValue("name") as String)
                check(seen.add(name)) { "two functions named $name" }
                val id = (int(b["block"]) shl 8) or int(f["function"])
                all += Triple(f.getValue("name") as String, id, f["never_send"] == true)
                out.appendLine("    public const val $name: Int = ${hex(id, 4)}")
            }
        }
        out.appendLine("    /** Functions PairFree never sends: firmware update, factory default, ship mode, reset, power and authentication. */")
        out.appendLine("    public val NEVER_SEND: Set<Int> = setOf(${all.filter { it.third }.joinToString(", ") { hex(it.second, 4) }})")
        out.appendLine("    public val NAMES: Map<Int, String> = mapOf(${all.joinToString(", ") { "${hex(it.second, 4)} to ${str(it.first)}" }})")
        out.appendLine("    public fun block(id: Int): Int = id shr 8")
        out.appendLine("    public fun function(id: Int): Int = id and 0xFF")
        out.appendLine("    public fun of(block: Int, function: Int): Int = (block shl 8) or function")
        out.appendLine("}")
        out.appendLine()
        out.appendLine("/** Every model in data/bose/devices.json. */")
        out.appendLine("internal val BOSE_MODELS: List<BoseModel> = listOf(")
        for (d in devices) {
            val form = when (d["kind"]) {
                "earbuds" -> "EARBUDS"
                "headphones", "neckband" -> "HEADPHONES"
                else -> error("unknown kind ${d["kind"]}")
            }
            val id = (d.getValue("product_id") as String).toInt(16)
            out.appendLine("    BoseModel(${hex(id, 4)}, ${str(d["name"])}, ${str(d["codename"])}, io.github.omegadestroyer.pairfree.protocol.Form.$form),")
        }
        out.appendLine(")")

        val dir = outputDir.get().asFile.resolve(packageName.get().replace('.', '/'))
        dir.mkdirs()
        dir.resolve("BoseTables.kt").writeText(out.toString())
    }
}

val generateBoseTables = tasks.register<GenerateBoseTables>("generateBoseTables") {
    commandsJson = rootProject.layout.projectDirectory.file("../data/bose/commands.json")
    devicesJson = rootProject.layout.projectDirectory.file("../data/bose/devices.json")
    packageName = "io.github.omegadestroyer.pairfree.protocol.bose"
    outputDir = layout.buildDirectory.dir("generated/bose")
}

/** Writes JBL's Harman commands and models from data/jbl. */
abstract class GenerateJblTables : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val commandsJson: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val devicesJson: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val eqPresetsJson: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val gestureConfigJson: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        @Suppress("UNCHECKED_CAST")
        val commands = JsonSlurper().parse(commandsJson.get().asFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val devices = (JsonSlurper().parse(devicesJson.get().asFile) as Map<String, Any?>).getValue("devices") as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val eqPresets = JsonSlurper().parse(eqPresetsJson.get().asFile) as Map<String, Any?>
        fun str(v: Any?) = "\"" + (v as String) + "\""
        fun hex(v: Int) = "0x" + v.toString(16).uppercase().padStart(2, '0')
        fun byte(v: Any?) = (v as String).toInt(16)
        fun int(v: Any?) = (v as Number).toInt()
        @Suppress("UNCHECKED_CAST")
        fun map(key: String) = commands.getValue(key) as Map<String, Any?>
        fun constants(name: String, doc: String, values: Map<String, Int>, out: StringBuilder) {
            out.appendLine("/** $doc */")
            out.appendLine("public object $name {")
            for ((k, v) in values) out.appendLine("    public const val ${k.uppercase()}: Int = ${hex(v)}")
            out.appendLine("}")
            out.appendLine()
        }

        val out = StringBuilder()
        out.appendLine("// Generated from data/jbl by the generateJblTables task. Do not edit.")
        out.appendLine("package ${packageName.get()}")
        out.appendLine()
        @Suppress("UNCHECKED_CAST")
        val list = commands.getValue("commands") as List<Map<String, Any?>>
        out.appendLine("/** Harman's command numbers (data/jbl/commands.json); a reply comes back under the command's reply number. */")
        out.appendLine("public object JblCommand {")
        out.appendLine("    public const val START: Int = ${hex(int(commands["start"]))}")
        for (c in list) {
            val name = (c.getValue("name") as String).uppercase()
            out.appendLine("    public const val $name: Int = ${hex(byte(c["command"]))}")
            c["reply"]?.let { out.appendLine("    public const val ${name}_REPLY: Int = ${hex(byte(it))}") }
        }
        out.appendLine("    /** Commands with a two-byte length, low byte first. */")
        out.appendLine("    public val WIDE: Set<Int> = setOf(${list.filter { it["wide"] == true }.joinToString(", ") { hex(byte(it["command"])) }})")
        @Suppress("UNCHECKED_CAST")
        val never = commands.getValue("never_send") as Map<String, Any?>
        out.appendLine("    /** Commands PairFree never sends: ${never.values.joinToString(", ")}. */")
        out.appendLine("    public val NEVER_SEND: Set<Int> = setOf(${never.keys.joinToString(", ") { hex(byte(it)) }})")
        out.appendLine("}")
        out.appendLine()
        constants("JblStatus", "The status types of command 21 (data/jbl/commands.json).", map("status_types").mapValues { byte(it.value) }, out)
        constants("JblNoise", "Command 91's sub-commands, and its modes (data/jbl/commands.json).",
            map("noise_sub").mapValues { byte(it.value) } + map("noise_modes").mapKeys { "mode_" + it.key }.mapValues { int(it.value) }, out)
        constants("JblAmbient", "Command 32's values (data/jbl/commands.json).", map("ambient_values").mapValues { int(it.value) }, out)
        constants("JblEqWire", "The EQ command's sub-commands and categories (data/jbl/commands.json eq).", map("eq").mapValues { int(it.value) }, out)
        constants("JblSegmentEq", "Realtek's segment EQ's sub-commands, provider, part spacing and rate codes (data/jbl/commands.json segment_eq).",
            map("segment_eq").filterKeys { !it.startsWith("_") }.mapValues { int(it.value) }, out)
        constants("JblEq41", "The older BES classes' EQ commands, sizes and categories (data/jbl/commands.json eq41).",
            map("eq41").filterKeys { !it.startsWith("_") }.mapValues { (_, v) -> if (v is String) byte(v) else int(v) }, out)
        constants("JblFeature", "Harman's feature record types and the feature ids PairFree reads or writes (data/jbl/commands.json).",
            map("feature_types").mapKeys { "type_" + it.key }.mapValues { int(it.value) } + map("features").mapValues { int(it.value) }, out)
        val p4 = map("protocol4")
        @Suppress("UNCHECKED_CAST")
        val p4Never = p4.getValue("never_send") as Map<String, Any?>
        out.appendLine("/** JBL's protocol 4: its identifiers, commands and the feature ids PairFree uses (data/jbl/commands.json). */")
        out.appendLine("public object JblP4 {")
        out.appendLine("    public const val IDENTIFIER: Int = ${hex(int(p4["identifier"]))}")
        out.appendLine("    public const val IDENTIFIER_CASE: Int = ${hex(int(p4["identifier_case"]))}")
        @Suppress("UNCHECKED_CAST")
        for ((k, v) in p4.getValue("commands") as Map<String, Any?>) out.appendLine("    public const val ${k.uppercase()}: Int = ${hex((v as String).toInt(16))}")
        @Suppress("UNCHECKED_CAST")
        for ((k, v) in p4.getValue("features") as Map<String, Any?>) out.appendLine("    public const val FEATURE_${k.uppercase()}: Int = ${hex(int(v))}")
        out.appendLine("    /** The EQ info's category for a curve of the user's own, which JBL's app calls 230 elsewhere. */")
        out.appendLine("    public const val EQ_CUSTOM: Int = ${int(p4["eq_custom"])}")
        out.appendLine("    /** A level of 127 leaves the mode's level as it is. */")
        out.appendLine("    public const val IGNORE_LEVEL: Int = ${int(p4["ignore_level"])}")
        out.appendLine("    /** Commands PairFree never sends: ${p4Never.values.joinToString(", ")}. */")
        out.appendLine("    public val NEVER_SEND: Set<Int> = setOf(${p4Never.keys.joinToString(", ") { hex(it.toInt(16)) }})")
        out.appendLine("}")
        out.appendLine()
        val race = map("airoha")
        @Suppress("UNCHECKED_CAST")
        fun raceMap(key: String) = race.getValue(key) as Map<String, Any?>
        out.appendLine("/** Airoha's RACE commands as JBL's app sends them to its Airoha models (data/jbl/commands.json, airoha). */")
        out.appendLine("public object JblRace {")
        out.appendLine("    public const val START: Int = ${hex(byte(race["start"]))}")
        for ((k, v) in raceMap("types")) out.appendLine("    public const val TYPE_${k.uppercase()}: Int = ${hex(byte(v))}")
        out.appendLine("    public const val SPP_UUID: String = ${str(race["spp_uuid"])}")
        for ((k, v) in raceMap("gatt")) out.appendLine("    public const val GATT_${k.uppercase()}: String = ${str(v)}")
        for ((k, v) in raceMap("commands")) out.appendLine("    public const val ${k.uppercase()}: Int = 0x${(v as String).uppercase()}")
        for ((k, v) in raceMap("mmi_modules")) out.appendLine("    public const val MMI_${k.uppercase()}: Int = ${int(v)}")
        for ((k, v) in raceMap("config_modules")) out.appendLine("    public const val CONFIG_${k.uppercase()}: Int = ${int(v)}")
        for ((k, v) in raceMap("notify_modules")) out.appendLine("    public const val NOTIFY_${k.uppercase()}: Int = ${int(v)}")
        for ((k, v) in raceMap("nv_keys")) out.appendLine("    public const val NV_${k.uppercase()}: Int = 0x${(v as String).uppercase()}")
        for ((k, v) in raceMap("peq")) if (!k.startsWith("_")) out.appendLine("    public const val PEQ_${k.uppercase()}: Int = ${if (v is String) "0x" + v.uppercase() else int(v).toString()}")
        for ((k, v) in raceMap("gesture")) if (v is String && !k.startsWith("_")) out.appendLine("    public const val GESTURE_${k.uppercase()}: Int = 0x${v.uppercase()}")
        out.appendLine("    public const val GESTURE_READ_LENGTH: Int = ${int(raceMap("gesture")["read_length"])}")
        for ((k, v) in raceMap("anc")) out.appendLine("    public const val ANC_${k.uppercase()}: Int = ${if (v is String) hex(byte(v)) else int(v).toString()}")
        for ((k, v) in raceMap("key_events")) out.appendLine("    public const val KEY_${k.uppercase()}: Int = 0x${(v as String).uppercase()}")
        for ((k, v) in raceMap("prompt_language")) out.appendLine("    public const val PROMPT_${k.uppercase()}: Int = ${int(v)}")
        out.appendLine("    /** The sound effects' numbers in the voice EQ setting, by the names devices.json gives them. */")
        out.appendLine("    public val SOUND_EFFECTS: Map<String, Int> = mapOf(${raceMap("sound_effects").entries.joinToString(", ") { "${str(it.key)} to ${int(it.value)}" }})")
        val raceNever = raceMap("never_send")
        out.appendLine("    /** Commands PairFree never sends: ${raceNever.values.joinToString(", ")}. */")
        out.appendLine("    public val NEVER_SEND: Set<Int> = setOf(${raceNever.keys.joinToString(", ") { "0x" + it.uppercase() }})")
        out.appendLine("    /** The only key events PairFree sends; the others include factory reset, power off and pairing. */")
        out.appendLine("    public val KEY_EVENTS: Set<Int> = setOf(${raceMap("key_events").values.joinToString(", ") { "0x" + (it as String).uppercase() }})")
        out.appendLine("}")
        out.appendLine()
        // Airoha's gesture key map per chip variant (data/jbl/commands.json, airoha.gesture): K, the gesture's own key type, is -1.
        @Suppress("UNCHECKED_CAST")
        val gestureVariants = raceMap("gesture").getValue("variants") as Map<String, Map<String, Any?>>
        fun keyType(v: Any?) = if (v == "K") "-1" else int(v).toString()
        out.appendLine("/** Airoha's gesture key map per chip variant (data/jbl/commands.json, airoha.gesture). */")
        out.appendLine("internal val AIROHA_GESTURE_VARIANTS: Map<String, AirohaGestureVariant> = mapOf(")
        for ((name, v) in gestureVariants) {
            @Suppress("UNCHECKED_CAST")
            fun entries(list: Any?) = (list as List<List<Any?>>).joinToString(", ", "listOf(", ")") { e -> "intArrayOf(${keyType(e[0])}, ${e.drop(1).joinToString(", ") { "${(it as Number).toLong()}L.toInt()" }})" }
            @Suppress("UNCHECKED_CAST")
            val write = (v.getValue("write") as Map<String, Any?>).entries.joinToString(", ", "mapOf(", ")") { "${it.key} to ${entries(it.value)}" }
            @Suppress("UNCHECKED_CAST")
            val overrides = (v.getValue("write_overrides") as Map<String, Any?>).entries.joinToString(", ", "mapOf(", ")") { (k, e) ->
                val (g, act) = k.split("/")
                "($g to $act) to ${entries(e)}"
            }
            @Suppress("UNCHECKED_CAST")
            val keyTypes = (v.getValue("key_types") as Map<String, Any?>).entries.joinToString(", ", "mapOf(", ")") { "${it.key} to ${int(it.value)}" }
            @Suppress("UNCHECKED_CAST")
            val read = (v.getValue("read") as List<List<Any?>>).joinToString(", ", "listOf(", ")") { r -> "${int(r[0])} to ${entries(r[1])}" }
            @Suppress("UNCHECKED_CAST")
            val order = (v.getValue("read_order") as List<Any?>).joinToString(", ", "listOf(", ")") { int(it).toString() }
            out.appendLine("    ${str(name)} to AirohaGestureVariant(${int(v["key_id"])}, ${v["key_id_from_read"]}, ${v["fills_unmatched"]}, $keyTypes,")
            out.appendLine("        $write,")
            out.appendLine("        $overrides,")
            out.appendLine("        $order, $read),")
        }
        out.appendLine(")")
        out.appendLine()

        // JBL's presets (data/jbl/eq_presets.json): one list per preset file, and per series the standard ones its group names
        // after an Off of flat bands, as JBL's app adds it (pp.a.f).
        fun f(v: Any?) = (v as Number).toFloat().toString() + "f"
        @Suppress("UNCHECKED_CAST")
        fun preset(p: Map<String, Any?>): String {
            val gains = (p["gains"] as List<Number>?) ?: listOf(0, 0)
            val bands = (p.getValue("bands") as List<List<Number>>).joinToString(", ") { b -> "JblEqBand(${b[0].toInt()}, ${f(b[1])}, ${f(b[2])}, ${f(b[3])})" }
            return "JblEqPreset(${str(p["name"])}, ${int(p["category"])}, ${(p["index"] as Number?)?.toInt()}, ${f(gains[0])}, ${f(gains[1])}, listOf($bands))"
        }
        @Suppress("UNCHECKED_CAST")
        val presetFiles = eqPresets.getValue("files") as Map<String, List<Map<String, Any?>>>
        @Suppress("UNCHECKED_CAST")
        val standard = (eqPresets.getValue("standard") as List<Map<String, Any?>>)
        @Suppress("UNCHECKED_CAST")
        val groups = eqPresets.getValue("groups") as Map<String, List<Number>>
        fun ident(key: String) = "EQ_" + key.uppercase().replace(Regex("[^A-Z0-9]"), "_")
        val off = "JblEqPreset(\"Off\", 0, null, 0f, 0f, listOf(" + listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000).joinToString(", ") { "JblEqBand(1, $it.0f, 0f, 1f)" } + "))"
        // One function per list: as values they would all be built in one class initialiser, which grows past the JVM's limit.
        for ((key, list) in presetFiles) out.appendLine("private fun ${ident(key)}(): List<JblEqPreset> = listOf(${list.joinToString(", ") { preset(it) }})")
        for ((series, categories) in groups) {
            val picked = categories.map { c -> standard.first { int(it["category"]) == c.toInt() } }
            out.appendLine("private fun ${ident("series_" + series)}(): List<JblEqPreset> = listOf($off, ${picked.joinToString(", ") { preset(it) }})")
        }
        out.appendLine()
        // The protocol 4 models' gesture page (data/jbl/gesture_config.json).
        @Suppress("UNCHECKED_CAST")
        val gestureConfig = JsonSlurper().parse(gestureConfigJson.get().asFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        fun p5(entry: Any?): String = ((entry as Map<String, Any?>).getValue("gestures") as List<Map<String, Any?>>).joinToString(", ", "listOf(", ")") { g ->
            @Suppress("UNCHECKED_CAST")
            val functions = (g.getValue("functions") as Map<String, Any?>).entries.joinToString(", ", "linkedMapOf(", ")") { "${str(it.key)} to ${int(it.value)}" }
            "JblP5Gesture(0x${g["feature"]}, ${str(g["place"])}, ${str(g["gesture"])}, ${g["default"]?.let { str(it) } ?: "null"}, $functions)"
        }
        out.appendLine("/** The protocol 4 models' gesture pages, \"common\" or by product id (data/jbl/gesture_config.json). */")
        out.appendLine("internal val JBL_P5_GESTURES: Map<String, List<JblP5Gesture>> = mapOf(")
        out.appendLine("    \"common\" to ${p5(gestureConfig["common"])},")
        @Suppress("UNCHECKED_CAST")
        for ((pid, e) in gestureConfig.getValue("products") as Map<String, Any?>) out.appendLine("    ${str(pid)} to ${p5(e)},")
        out.appendLine(")")
        out.appendLine()
        out.appendLine("/** Every model in data/jbl/devices.json. */")
        out.appendLine("internal val JBL_MODELS: List<JblModel> = listOf(")
        for (d in devices) {
            val form = when (d["kind"]) {
                "earbuds" -> "EARBUDS"
                "headphones", "glasses" -> "HEADPHONES"
                else -> error("unknown kind ${d["kind"]}")
            }
            @Suppress("UNCHECKED_CAST")
            val pids = (d.getValue("pids") as List<String>).joinToString(", ") { "0x" + it.uppercase() }
            val family = when (d["family"]) {
                "direct" -> "DIRECT"
                "feature" -> "FEATURE"
                "p4" -> "P4"
                "airoha" -> "AIROHA"
                else -> error("unknown family ${d["family"]}")
            }
            val noise = d["noise"]?.let { "JblModel.Noise." + (it as String).uppercase() } ?: "null"
            @Suppress("UNCHECKED_CAST")
            val modes = (d["modes"] as List<String>?).orEmpty().joinToString(", ") { "JblModel.Mode." + it.uppercase() }
            val autoOff = d["auto_off"]?.let { "JblModel.AutoOff." + (it as String).uppercase() } ?: "null"
            @Suppress("UNCHECKED_CAST")
            val minutes = (d["auto_off_minutes"] as List<Number>?).orEmpty().joinToString(", ")
            val gatt = when (d["connect"]) {
                "BLE" -> "true"
                "BT" -> "false"
                else -> error("unknown connect ${d["connect"]}")
            }
            val security = (d["security"] as Number?)?.toInt() ?: 0
            val extras = (d["settings"] as List<*>? ?: emptyList<Any>()).joinToString(", ", "setOf(", ")") { str(it) }
            @Suppress("UNCHECKED_CAST")
            val sav = (d["smart_audio_video"] as Map<String, List<Number>>?)?.let { m ->
                m.entries.joinToString(", ", "mapOf(", ")") { (k, v) -> "${str(k)} to listOf(${v.joinToString(", ") { it.toInt().toString() }})" }
            } ?: "emptyMap()"
            val effects = (d["effects"] as List<*>? ?: emptyList<Any>()).joinToString(", ", "listOf(", ")") { str(it) }
            @Suppress("UNCHECKED_CAST")
            val eq = (d["eq"] as Map<String, Any?>?)?.let { e ->
                val path = when (e["path"]) {
                    "a2" -> "A2"
                    "a2_q10" -> "A2_Q10"
                    "41" -> "CMD41"
                    "club" -> "CLUB"
                    "segment" -> "SEGMENT"
                    "p4" -> "P4"
                    "airoha" -> "AIROHA"
                    else -> error("unknown eq path ${e["path"]}")
                }
                val presets = ((e["presets"] as String?)?.let { ident(it) } ?: ident("series_" + (e["series"] as String))) + "()"
                val range = (e.getValue("range") as List<Number>).maxOf { Math.abs(it.toInt()) }
                "JblEqSpec(JblEqSpec.Path.$path, $presets, $range, readsDesign = ${e["design"] == true}, calibrate = ${e["calibrate"] == true}, airohaLib = ${e["lib"] == "airoha"}, " +
                    "halveCustomUntil = ${(e["halve_custom_until"] as String?)?.let { str(it) }}, " +
                    "airohaRates = listOf(${(e["rates"] as List<*>?).orEmpty().joinToString(", ") { int(it).toString() }}))"
            } ?: "null"
            out.appendLine(
                "    JblModel(${str(d["name"])}, listOf($pids), io.github.omegadestroyer.pairfree.protocol.Form.$form, gatt = $gatt, " +
                    "family = JblModel.Family.$family, noise = $noise, modes = listOf($modes), autoOff = $autoOff, " +
                    "autoOffMinutes = listOf($minutes), autoPlayPause = ${d["auto_play_pause"] == true}, security = $security, " +
                    "extras = $extras, smartAudioVideo = $sav, besSmartSwitch = ${d["smart_switch"] == "bes"}, effects = $effects, gestureNoise = ${(d["gesture_noise"] as Number?)?.toInt() ?: 11}, " +
                    "racePush = ${d["push_status"] != false}, names = ${(d["names"] as List<*>? ?: emptyList<Any>()).joinToString(", ", "listOf(", ")") { str(it) }}, eq = $eq, " +
                    "airohaGestures = ${d["airoha_gestures"]?.let { str(it) } ?: "null"}, gestureUll = ${d["gesture_ull"] == true}, " +
                    "p5Gestures = ${d["p5_gestures"]?.let { str(it) } ?: "null"}),",
            )
        }
        out.appendLine(")")

        val dir = outputDir.get().asFile.resolve(packageName.get().replace('.', '/'))
        dir.mkdirs()
        dir.resolve("JblTables.kt").writeText(out.toString())
    }
}

val generateJblTables = tasks.register<GenerateJblTables>("generateJblTables") {
    commandsJson = rootProject.layout.projectDirectory.file("../data/jbl/commands.json")
    devicesJson = rootProject.layout.projectDirectory.file("../data/jbl/devices.json")
    eqPresetsJson = rootProject.layout.projectDirectory.file("../data/jbl/eq_presets.json")
    gestureConfigJson = rootProject.layout.projectDirectory.file("../data/jbl/gesture_config.json")
    packageName = "io.github.omegadestroyer.pairfree.protocol.jbl"
    outputDir = layout.buildDirectory.dir("generated/jbl")
}

/** Writes Apple's accessory protocol constants and the AirPods and Beats models from data/apple. */
abstract class GenerateAppleTables : DefaultTask() {
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
        val commands = JsonSlurper().parse(commandsJson.get().asFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val devices = (JsonSlurper().parse(devicesJson.get().asFile) as Map<String, Any?>).getValue("devices") as List<Map<String, Any?>>
        fun str(v: Any?) = "\"" + (v as String) + "\""
        fun hex(v: Int) = "0x" + v.toString(16).uppercase().padStart(2, '0')
        fun byte(v: Any?) = (v as String).toInt(16)
        fun bytes(v: Any?) = "byteArrayOf(" + (v as String).chunked(2).joinToString(", ") { "0x${it.uppercase()}.toByte()" } + ")"
        @Suppress("UNCHECKED_CAST")
        fun map(key: String) = commands.getValue(key) as Map<String, Any?>

        val out = StringBuilder()
        out.appendLine("// Generated from data/apple by the generateAppleTables task. Do not edit.")
        out.appendLine("package ${packageName.get()}")
        out.appendLine()
        out.appendLine("/** The channel and the fixed packets of Apple's accessory protocol (data/apple/commands.json). */")
        out.appendLine("public object AppleWire {")
        out.appendLine("    /** The L2CAP channel (PSM) the AirPods listen on. */")
        out.appendLine("    public const val PSM: Int = ${(commands["psm"] as Number).toInt()}")
        out.appendLine("    /** The service AirPods and Apple-chip Beats list, by which a paired device is known as one. */")
        out.appendLine("    public const val SERVICE_UUID: String = ${str(commands["service_uuid"])}")
        out.appendLine("    public val HEADER: ByteArray get() = ${bytes(commands["header"])}")
        out.appendLine("    /** Sent first, whole; the AirPods answer nothing before it. */")
        out.appendLine("    public val HANDSHAKE: ByteArray get() = ${bytes(commands["handshake"])}")
        out.appendLine("    /** The opcode and data that switch on features Apple keeps to its own devices. */")
        out.appendLine("    public val FEATURE_FLAGS: ByteArray get() = ${bytes(commands["feature_flags"])}")
        out.appendLine("    /** The opcode and data that ask for notifications: battery, ear detection, settings. */")
        out.appendLine("    public val NOTIFICATIONS: ByteArray get() = ${bytes(commands["notifications"])}")
        val switch = map("switch")
        out.appendLine("    public const val ON: Int = ${(switch["on"] as Number).toInt()}")
        out.appendLine("    public const val OFF: Int = ${(switch["off"] as Number).toInt()}")
        out.appendLine("}")
        out.appendLine()
        @Suppress("UNCHECKED_CAST")
        val never = commands.getValue("never_send") as Map<String, Map<String, Any?>>
        out.appendLine("/** The opcodes PairFree reads (data/apple/commands.json). */")
        out.appendLine("public object AppleOpcode {")
        for ((name, v) in map("opcodes")) out.appendLine("    public const val ${name.uppercase()}: Int = ${hex(byte(v))}")
        out.appendLine("    /** Opcodes PairFree never sends: ${never.getValue("opcodes").values.joinToString("; ")}. */")
        out.appendLine("    public val NEVER_SEND: Set<Int> = setOf(${never.getValue("opcodes").keys.joinToString(", ") { hex(byte(it)) }})")
        out.appendLine("}")
        out.appendLine()
        @Suppress("UNCHECKED_CAST")
        val controls = commands.getValue("controls") as List<Map<String, Any?>>
        out.appendLine("/** Control command ids (opcode 09) and the values of those with named ones (data/apple/commands.json). */")
        out.appendLine("public object AppleControl {")
        for (c in controls) out.appendLine("    public const val ${(c.getValue("name") as String).uppercase()}: Int = ${hex(byte(c["id"]))}")
        out.appendLine("    /** Control commands PairFree never sends: ${never.getValue("controls").values.joinToString("; ")}. */")
        out.appendLine("    public val NEVER_SEND: Set<Int> = setOf(${never.getValue("controls").keys.joinToString(", ") { hex(byte(it)) }})")
        out.appendLine("    public val VALUES: Map<Int, Map<Int, String>> = mapOf(")
        for (c in controls) {
            @Suppress("UNCHECKED_CAST")
            val values = c["values"] as Map<String, Any?>? ?: continue
            out.appendLine("        ${hex(byte(c["id"]))} to mapOf(${values.entries.joinToString(", ") { "${it.key.toInt()} to ${str(it.value)}" }}),")
        }
        out.appendLine("    )")
        out.appendLine("    public val SWITCHES: Set<Int> = setOf(${controls.filter { it["kind"] == "switch" }.joinToString(", ") { hex(byte(it["id"])) }})")
        out.appendLine("}")
        out.appendLine()
        out.appendLine("/** Every model in data/apple/devices.json. */")
        out.appendLine("internal val APPLE_MODELS: List<AppleModel> = listOf(")
        for (d in devices) {
            val form = when (d["kind"]) {
                "earbuds" -> "EARBUDS"
                "headphones" -> "HEADPHONES"
                else -> error("unknown kind ${d["kind"]}")
            }
            @Suppress("UNCHECKED_CAST")
            val numbers = (d.getValue("model_numbers") as List<String>).joinToString(", ") { str(it) }
            out.appendLine(
                "    AppleModel(${str(d["name"])}, io.github.omegadestroyer.pairfree.protocol.Form.$form, listOf($numbers), " +
                    "0x${d["product_id"]}, adaptive = ${d["adaptive"] == true}, b2p = ${d["b2p"] == true}, bcd = ${d["bcd"] == true}, features = listOf(${((d["features"] as? List<String>) ?: emptyList()).joinToString(", ") { str(it) }})),",
            )
        }
        out.appendLine(")")

        val dir = outputDir.get().asFile.resolve(packageName.get().replace('.', '/'))
        dir.mkdirs()
        dir.resolve("AppleTables.kt").writeText(out.toString())
    }
}

val generateAppleTables = tasks.register<GenerateAppleTables>("generateAppleTables") {
    commandsJson = rootProject.layout.projectDirectory.file("../data/apple/commands.json")
    devicesJson = rootProject.layout.projectDirectory.file("../data/apple/devices.json")
    packageName = "io.github.omegadestroyer.pairfree.protocol.apple"
    outputDir = layout.buildDirectory.dir("generated/apple")
}

/** Writes Nothing's commands, frame constants and models from data/nothing. */
abstract class GenerateNothingTables : DefaultTask() {
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
        val commands = JsonSlurper().parse(commandsJson.get().asFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val devices = (JsonSlurper().parse(devicesJson.get().asFile) as Map<String, Any?>).getValue("devices") as List<Map<String, Any?>>
        fun str(v: Any?) = "\"" + (v as String).replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
        fun int(v: Any?) = (v as Number).toInt()
        fun hex(v: Int) = "0x" + v.toString(16).uppercase().padStart(4, '0')
        fun strings(v: Any?) = (v as List<*>).joinToString(", ", "listOf(", ")") { str(it) }
        fun ints(v: Any?) = (v as List<*>).joinToString(", ", "listOf(", ")") { int(it).toString() }

        val out = StringBuilder()
        out.appendLine("// Generated from data/nothing by the generateNothingTables task. Do not edit.")
        out.appendLine("package ${packageName.get()}")
        out.appendLine()
        @Suppress("UNCHECKED_CAST")
        val frame = commands.getValue("frame") as Map<String, Any?>
        out.appendLine("/** The frame's constants (data/nothing/commands.json, frame). */")
        out.appendLine("public object NothingWire {")
        out.appendLine("    /** The serial service Nothing's earbuds offer for control. */")
        out.appendLine("    public const val SERVICE_UUID: String = ${str(frame["service_uuid"])}")
        out.appendLine("    public const val START: Int = 0x${frame["start"]}")
        out.appendLine("    /** The control field Nothing's app sends: a TWS headset, multi-frame bit, and a CRC. */")
        out.appendLine("    public const val CONTROL_SENT: Int = 0x${frame["control_sent"]}")
        out.appendLine("    public const val DEVICE_TYPE_TWS: Int = ${int(frame["device_type_tws"])}")
        out.appendLine("    /** A reply is the command it answers with this mask applied. */")
        out.appendLine("    public const val REPLY_MASK: Int = 0x${frame["reply_mask"]}")
        out.appendLine("    /** The frame sequence number counts up to this, then starts over at 0. */")
        out.appendLine("    public const val FSN_WRAP: Int = ${int(frame["fsn_wrap"])}")
        out.appendLine("}")
        out.appendLine()
        @Suppress("UNCHECKED_CAST")
        val list = commands.getValue("commands") as List<Map<String, Any?>>
        out.appendLine("/** Nothing's commands (data/nothing/commands.json), by the names Nothing X gives them. */")
        out.appendLine("public object NothingCommand {")
        val seen = mutableSetOf<String>()
        for (c in list) {
            val name = c.getValue("name") as String
            check(seen.add(name)) { "two commands named $name" }
            out.appendLine("    public const val $name: Int = 0x${c["code"]}")
        }
        fun codes(pred: (Map<String, Any?>) -> Boolean) = list.filter(pred).joinToString(", ") { "0x${it["code"]}" }
        out.appendLine("    /** Firmware update, factory reset, calibration, test and debug traffic: never sent. */")
        out.appendLine("    public val NEVER_SEND: Set<Int> = setOf(${codes { it["never_send"] == true }})")
        out.appendLine("    /** Reads whose replies hold a serial number or other devices' addresses: never sent. */")
        out.appendLine("    public val PRIVATE: Set<Int> = setOf(${codes { it["private"] == true }})")
        out.appendLine("    public val NAMES: Map<Int, String> = mapOf(${list.joinToString(", ") { "0x${it["code"]} to ${str(it["name"])}" }})")
        out.appendLine("}")
        out.appendLine()
        out.appendLine("/** Every model in data/nothing/devices.json. */")
        out.appendLine("internal val NOTHING_MODELS: List<NothingModel> = listOf(")
        for (d in devices) {
            val form = when (d["kind"]) {
                "earbuds" -> "EARBUDS"
                "headphones", "neckband" -> "HEADPHONES"
                else -> error("unknown kind ${d["kind"]}")
            }
            out.appendLine("    NothingModel(")
            out.appendLine("        ${str(d["product_id"])}, ${str(d["name"])}, ${str(d["codename"])}, ${strings(d["names"])}, io.github.omegadestroyer.pairfree.protocol.Form.$form,")
            out.appendLine("        crc = ${d["crc"]}, activate = ${d["activate"]}, headTracking = ${d["head_tracking"]},")
            out.appendLine("        configs = listOf(")
            @Suppress("UNCHECKED_CAST")
            for (c in d.getValue("configs") as List<Map<String, Any?>>) {
                @Suppress("UNCHECKED_CAST")
                val gestures = (c["gestures"] as List<Map<String, Any?>>).joinToString(", ", "listOf(", ")") { g ->
                    "NothingGesture(${int(g["device"])}, ${int(g["button"])}, ${int(g["gesture"])}, ${g["custom"]}, ${ints(g["operations"])}, ${ints(g["fixed"])})"
                }
                out.appendLine("            NothingConfig(")
                out.appendLine("                minFirmware = ${c["min_firmware"]?.let(::str) ?: "null"}, maxFirmware = ${c["max_firmware"]?.let(::str) ?: "null"},")
                out.appendLine("                anc = ${strings(c["anc"])}, eqPresets = ${c["eq_presets"]}, bassBoost = ${c["bass_boost"]}, spatialAudio = ${c["spatial_audio"]},")
                out.appendLine("                codecs = ${strings(c["codecs"])}, earDetection = ${c["ear_detection"]}, lowLatency = ${c["low_latency"]}, fitTest = ${c["fit_test"] == true}, dualConnection = ${c["dual_connection"] == true}, diracOpteo = ${c["dirac_opteo"] == true}, personalizedAnc = ${c["personalized_anc"] == true}, powerOffTime = ${c["power_off_time"] == true}, callTransparency = ${c["call_transparency"] == true}, advancedEq = ${c["advanced_eq"] == true},")
                out.appendLine("                gestures = $gestures,")
                @Suppress("UNCHECKED_CAST")
                val simple = c["simple_eq"] as Map<String, Any?>?
                if (simple != null) {
                    fun num(key: String) = (simple.getValue(key) as Number).toDouble()
                    out.appendLine("                simpleEq = NothingSimpleEq(${num("freqLow")}, ${num("qLow")}, ${num("freqPeak")}, ${num("qPeak")}, ${num("freqHigh")}, ${num("qHigh")}),")
                }
                out.appendLine("            ),")
            }
            out.appendLine("        ),")
            out.appendLine("    ),")
        }
        out.appendLine(")")

        val dir = outputDir.get().asFile.resolve(packageName.get().replace('.', '/'))
        dir.mkdirs()
        dir.resolve("NothingTables.kt").writeText(out.toString())
    }
}

val generateNothingTables = tasks.register<GenerateNothingTables>("generateNothingTables") {
    commandsJson = rootProject.layout.projectDirectory.file("../data/nothing/commands.json")
    devicesJson = rootProject.layout.projectDirectory.file("../data/nothing/devices.json")
    packageName = "io.github.omegadestroyer.pairfree.protocol.nothing"
    outputDir = layout.buildDirectory.dir("generated/nothing")
}

/** Writes the Pixel Buds' transports, settings, values and models from data/pixel. */
abstract class GeneratePixelTables : DefaultTask() {
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
        val c = JsonSlurper().parse(commandsJson.get().asFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val devices = (JsonSlurper().parse(devicesJson.get().asFile) as Map<String, Any?>).getValue("devices") as List<Map<String, Any?>>
        fun str(v: Any?) = "\"" + (v as String).replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
        fun int(v: Any?) = (v as Number).toLong().toInt()
        fun num(v: Any?) = (v as Number).toDouble().toString() + "f"
        @Suppress("UNCHECKED_CAST")
        fun obj(v: Any?) = v as Map<String, Any?>
        fun intMap(v: Any?) = obj(v).entries.joinToString(", ", "mapOf(", ")") { "${str(it.key)} to ${int(it.value)}" }
        fun strings(v: Any?) = (v as List<*>).joinToString(", ", "listOf(", ")") { str(it) }

        val maestro = obj(c["maestro"])
        val tlv = obj(c["tlv"])
        val peers = obj(maestro["peers"])
        val codes = obj(tlv["codes"])
        val types = obj(maestro["packet_types"])
        val methods = obj(maestro["methods"])
        val slots = obj(c["family_slots"])
        val out = StringBuilder()
        out.appendLine("// Generated from data/pixel by the generatePixelTables task. Do not edit.")
        out.appendLine("package ${packageName.get()}")
        out.appendLine()
        out.appendLine("/** The Pixel Buds' two transports (data/pixel/commands.json, maestro and tlv). */")
        out.appendLine("public object PixelWire {")
        out.appendLine("    /** Maestro's serial service, on the Pixel Buds Pro, Pro 2 and 2a, and as some phones list it, byte-reversed. */")
        out.appendLine("    public const val MAESTRO_UUID: String = ${str(maestro["uuid"])}")
        out.appendLine("    public const val MAESTRO_UUID_REVERSED: String = ${str(maestro["uuid_reversed"])}")
        out.appendLine("    /** The plain frames' serial service, on the Pixel Buds (2020) and A-Series. */")
        out.appendLine("    public const val TLV_UUID: String = ${str(tlv["uuid"])}")
        out.appendLine("    public const val TLV_UUID_REVERSED: String = ${str(tlv["uuid_reversed"])}")
        out.appendLine("    public const val HDLC_CONTROL: Int = ${int(maestro["hdlc_control"])}")
        out.appendLine("    /** The RPC channels to try, each as its HDLC address: source << 6 | target << 10. */")
        @Suppress("UNCHECKED_CAST")
        val channels = maestro["channels"] as List<Map<String, Any?>>
        out.appendLine("    public val CHANNELS: Map<Int, Int> = linkedMapOf(" + channels.joinToString(", ") {
            val address = (int(peers[it["source"]]) and 0xF shl 6) or (int(peers[it["target"]]) and 0xF shl 10)
            "${int(it["id"])} to $address"
        } + ")")
        out.appendLine("    public const val PROBE_CALL_ID: Int = ${(maestro["probe_call_id"] as Number).toLong().toInt()}")
        out.appendLine("    public const val CALL_ID: Int = ${int(maestro["call_id"])}")
        out.appendLine("    public const val PACKET_REQUEST: Int = ${int(types["request"])}")
        out.appendLine("    public const val PACKET_RESPONSE: Int = ${int(types["response"])}")
        out.appendLine("    public const val PACKET_SERVER_ERROR: Int = ${int(types["server_error"])}")
        out.appendLine("    public const val PACKET_SERVER_STREAM: Int = ${int(types["server_stream"])}")
        out.appendLine("    public const val SERVICE: String = ${str(maestro["service"])}")
        val fit = obj(maestro["fit_test"])
        out.appendLine("    /** The ear tip seal test's service, its methods and the thresholds of its verdict (data/pixel/commands.json, maestro.fit_test). */")
        out.appendLine("    public const val FIT_SERVICE: String = ${str(fit["service"])}")
        for ((k, v) in obj(fit["methods"])) out.appendLine("    public const val FIT_METHOD_${k.uppercase()}: String = ${str(v)}")
        for (k in listOf("good_min", "max", "invalid", "noise_limit", "noise_limit_high", "left_peer", "right_peer")) out.appendLine("    public const val FIT_${k.uppercase()}: Int = ${int(fit[k])}")
        out.appendLine("    public val FIT_NOISE_LIMIT_HIGH_TYPES: Set<Int> = setOf(${(fit["noise_limit_high_types"] as List<*>).joinToString(", ") { int(it).toString() }})")
        for ((k, v) in methods) out.appendLine("    public const val METHOD_${k.uppercase()}: String = ${str(v)}")
        out.appendLine("    public const val TLV_GROUP: Int = ${int(tlv["group"])}")
        out.appendLine("    public const val TLV_MAX_LENGTH: Int = ${int(tlv["max_length"])}")
        out.appendLine("    /** Firmware update, crash reports, firmware-update debug and usage statistics: never sent. */")
        out.appendLine("    public val NEVER_SEND_GROUPS: Set<Int> = setOf(${(tlv["never_send_groups"] as List<*>).joinToString(", ") { int(it).toString() }})")
        for ((k, v) in codes) out.appendLine("    public const val CODE_${k.uppercase()}: Int = ${int(v)}")
        for ((k, v) in slots) out.appendLine("    public const val SLOT_${k.uppercase()}: Int = ${int(v)}")
        out.appendLine("}")
        out.appendLine()

        val eq = obj(c["eq"])
        val bass = obj(c["bass_level"])
        val balance = obj(c["balance"])
        out.appendLine("/** What the settings take (data/pixel/commands.json). */")
        out.appendLine("public object PixelValues {")
        out.appendLine("    public val ANC_STATES: Map<String, Int> = ${intMap(c["anc_states"])}")
        out.appendLine("    public val NOISE_CYCLE: Map<String, Int> = ${intMap(c["noise_cycle"])}")
        out.appendLine("    public val HEAD_GESTURES: Map<String, Int> = ${intMap(c["head_gestures"])}")
        out.appendLine("    public val EQ_MODE: Map<String, Int> = ${intMap(c["eq_mode"])}")
        out.appendLine("    public const val BASS_MIN: Int = ${int(bass["min"])}")
        out.appendLine("    public const val BASS_MAX: Int = ${int(bass["max"])}")
        out.appendLine("    public const val BASS_OFFSET: Int = ${int(bass["offset"])}")
        out.appendLine("    public const val BALANCE_MIN: Int = ${int(balance["min"])}")
        out.appendLine("    public const val BALANCE_MAX: Int = ${int(balance["max"])}")
        out.appendLine("    public val HOLD_SIDES: Map<String, Int> = ${intMap(c["hold_sides"])}")
        out.appendLine("    public val TOUCH_ACTIONS: Map<String, Int> = ${intMap(c["touch_actions"])}")
        out.appendLine("    public val HOLD_OPTIONS: List<String> = ${strings(c["hold_options"])}")
        out.appendLine("    public val ATTENTION_ALERTS: Map<String, Int> = ${intMap(c["attention_alerts"])}")
        out.appendLine("    public val EQ_BANDS: List<String> = ${strings(eq["bands"])}")
        out.appendLine("    public const val EQ_RANGE_DB: Int = ${int(eq["range_db"])}")
        out.appendLine("    public const val EQ_STEP_DB: Float = ${num(eq["step_db"])}")
        @Suppress("UNCHECKED_CAST")
        val presets = eq["presets"] as List<Map<String, Any?>>
        out.appendLine("    public val EQ_PRESETS: List<PixelEqPreset> = listOf(" + presets.joinToString(", ") { p ->
            "PixelEqPreset(${str(p["name"])}, listOf(${(p["gains"] as List<*>).joinToString(", ") { num(it) }}))"
        } + ")")
        out.appendLine("}")
        out.appendLine()

        out.appendLine("/** Each family's settings fields (data/pixel/commands.json, settings). */")
        out.appendLine("internal val PIXEL_SETTINGS: Map<PixelFamily, List<PixelField>> = mapOf(")
        @Suppress("UNCHECKED_CAST")
        for ((family, fields) in obj(c["settings"]) as Map<String, List<Map<String, Any?>>>) {
            out.appendLine("    PixelFamily.${family.uppercase()} to listOf(")
            for (f in fields) {
                val type = when (f["type"]) {
                    "bool" -> "BOOL"; "enum" -> "ENUM"; "int" -> "INT"; "sint32" -> "SINT32"; "message" -> "MESSAGE"
                    else -> error("unknown type ${f["type"]}")
                }
                out.appendLine(
                    "        PixelField(${int(f["field"])}, PixelFieldType.$type, ${str(f["name"])}, read = ${f["read"] == true}, " +
                        "neverSend = ${f["never_send"] == true}, key = ${f["key"]?.let(::str) ?: "null"}, shared = ${f["shared"]?.let(::str) ?: "null"}),",
                )
            }
            out.appendLine("    ),")
        }
        out.appendLine(")")
        out.appendLine()

        out.appendLine("/** Every device type in data/pixel/devices.json. */")
        out.appendLine("internal val PIXEL_MODELS: List<PixelModel> = listOf(")
        for (d in devices) {
            @Suppress("UNCHECKED_CAST")
            val features = (d["features"] as List<List<Any?>>).joinToString(", ", "listOf(", ")") { f ->
                "${int(f[0])} to ${f[1]?.let { "${(it as Number).toLong()}L" } ?: "null"}"
            }
            out.appendLine(
                "    PixelModel(${int(d["device_type"])}, ${str(d["name"])}, ${str(d["codename"])}, PixelFamily.${(d["family"] as String).uppercase()}, " +
                    "PixelTransport.${(d["transport"] as String).uppercase()}, adaptive = ${d["adaptive"]}, names = ${strings(d["names"])}, features = $features),",
            )
        }
        out.appendLine(")")

        val dir = outputDir.get().asFile.resolve(packageName.get().replace('.', '/'))
        dir.mkdirs()
        dir.resolve("PixelTables.kt").writeText(out.toString())
    }
}

val generatePixelTables = tasks.register<GeneratePixelTables>("generatePixelTables") {
    commandsJson = rootProject.layout.projectDirectory.file("../data/pixel/commands.json")
    devicesJson = rootProject.layout.projectDirectory.file("../data/pixel/devices.json")
    packageName = "io.github.omegadestroyer.pairfree.protocol.pixel"
    outputDir = layout.buildDirectory.dir("generated/pixel")
}

/** Writes the brand words and the not-ours names from data/identify.json, for [Identify]. */
abstract class GenerateIdentifyTables : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val identifyJson: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        @Suppress("UNCHECKED_CAST")
        val data = JsonSlurper().parse(identifyJson.get().asFile) as Map<String, Any?>
        fun str(v: Any?) = "\"" + (v as String).replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        @Suppress("UNCHECKED_CAST")
        val words = data.getValue("brand_words") as Map<String, List<String>>
        @Suppress("UNCHECKED_CAST")
        val notOurs = data.getValue("not_ours") as List<String>
        val out = StringBuilder()
        out.appendLine("// Generated from data/identify.json by the generateIdentifyTables task. Do not edit.")
        out.appendLine("package ${packageName.get()}")
        out.appendLine()
        out.appendLine("/** The words that name each brand, and the names of devices that are not ours (data/identify.json). */")
        out.appendLine("internal object IdentifyTables {")
        out.appendLine("    val BRAND_WORDS: Map<String, List<String>> = mapOf(")
        for ((k, v) in words) out.appendLine("        ${str(k)} to listOf(${v.joinToString(", ") { str(it) }}),")
        out.appendLine("    )")
        out.appendLine("    val NOT_OURS: List<String> = listOf(${notOurs.joinToString(", ") { str(it) }})")
        out.appendLine("}")
        val dir = outputDir.get().asFile.resolve(packageName.get().replace('.', '/'))
        dir.mkdirs()
        dir.resolve("IdentifyTables.kt").writeText(out.toString())
    }
}

val generateIdentifyTables = tasks.register<GenerateIdentifyTables>("generateIdentifyTables") {
    identifyJson = rootProject.layout.projectDirectory.file("../data/identify.json")
    packageName = "io.github.omegadestroyer.pairfree.protocol"
    outputDir = layout.buildDirectory.dir("generated/identify")
}

/** Writes QCY's items, characteristics and models from data/qcy. */
abstract class GenerateQcyTables : DefaultTask() {
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
        val commands = JsonSlurper().parse(commandsJson.get().asFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val devices = (JsonSlurper().parse(devicesJson.get().asFile) as Map<String, Any?>).getValue("devices") as List<Map<String, Any?>>
        fun str(v: Any?) = "\"" + (v as String).replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
        fun int(v: Any?) = (v as Number).toInt()
        fun hex(v: Any?) = "0x" + (v as String).uppercase()
        fun strings(v: Any?) = (v as List<*>?).orEmpty().joinToString(", ", "listOf(", ")") { str(it) }

        val out = StringBuilder()
        out.appendLine("// Generated from data/qcy by the generateQcyTables task. Do not edit.")
        out.appendLine("package ${packageName.get()}")
        out.appendLine()
        @Suppress("UNCHECKED_CAST")
        val chars = commands.getValue("characteristics") as Map<String, Any?>
        out.appendLine("/** QCY's Bluetooth LE service, its characteristics and the advert's company id (data/qcy/commands.json). */")
        out.appendLine("public object QcyWire {")
        out.appendLine("    public const val SERVICE: String = ${str(commands["service"])}")
        out.appendLine("    public const val COMPANY: Int = ${hex(commands["company_id"])}")
        for ((name, uuid) in chars) {
            out.appendLine("    public const val ${name.uppercase()}: String = ${str(uuid)}")
            // The 16-bit short form, which the GATT envelope carries.
            out.appendLine("    public const val ${name.uppercase()}_SHORT: Int = 0x${(uuid as String).substring(4, 8)}")
        }
        out.appendLine("}")
        out.appendLine()
        @Suppress("UNCHECKED_CAST")
        val items = commands.getValue("items") as List<Map<String, Any?>>
        out.appendLine("/** The items QCY's packets carry (data/qcy/commands.json), by their names in QCY's app. */")
        out.appendLine("public object QcyItem {")
        for (i in items) out.appendLine("    public const val ${i["name"]}: Int = ${hex(i["id"])}")
        out.appendLine("    /** Resets, pairing, test and firmware traffic, renaming, clock and alarm writes: never sent. */")
        out.appendLine("    public val NEVER_SEND: Set<Int> = setOf(${items.filter { it["never_send"] == true }.joinToString(", ") { hex(it["id"]) }})")
        out.appendLine("    public val NAMES: Map<Int, String> = mapOf(${items.joinToString(", ") { "${hex(it["id"])} to ${str(it["name"])}" }})")
        out.appendLine("}")
        out.appendLine()
        @Suppress("UNCHECKED_CAST")
        val keys = commands.getValue("keys") as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        fun map(name: String) = (keys.getValue(name) as Map<String, Any?>).entries.joinToString(", ", "mapOf(", ")") { "${hex(it.key)} to ${str(it.value)}" }
        out.appendLine("/** The key table's keys and actions (data/qcy/commands.json, keys). */")
        out.appendLine("public object QcyKeys {")
        out.appendLine("    public val KEYS: Map<Int, String> = ${map("keys")}")
        out.appendLine("    public val ACTIONS: Map<Int, String> = ${map("actions")}")
        out.appendLine("}")
        out.appendLine()
        @Suppress("UNCHECKED_CAST")
        val jieli = commands.getValue("jieli") as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val opcodes = jieli.getValue("opcodes") as List<Map<String, Any?>>
        out.appendLine("/** JieLi's RCSP as QCY's JieLi models speak it (data/qcy/commands.json, jieli). */")
        out.appendLine("public object JieliWire {")
        out.appendLine("    public const val SERVICE: String = ${str(jieli["service"])}")
        for (name in listOf("write", "notify")) {
            out.appendLine("    public const val ${name.uppercase()}_SHORT: Int = 0x${(jieli[name] as String).substring(4, 8)}")
        }
        for (o in opcodes) out.appendLine("    public const val ${o["name"]}: Int = ${hex(o["id"])}")
        out.appendLine("    /** Opcodes PairFree never sends. */")
        out.appendLine("    public val NEVER_SEND: Set<Int> = setOf(${opcodes.filter { it["never_send"] == true }.joinToString(", ") { hex(it["id"]) }})")
        @Suppress("UNCHECKED_CAST")
        for ((name, v) in jieli.getValue("adv") as Map<String, Any?>) out.appendLine("    public const val ADV_$name: Int = ${int(v)}")
        @Suppress("UNCHECKED_CAST")
        for ((name, v) in jieli.getValue("target_info") as Map<String, Any?>) out.appendLine("    public const val TARGET_$name: Int = ${int(v)}")
        out.appendLine("}")
        out.appendLine()
        // The models in groups of 20, each in an object of its own: one initializer holding all of them passes the JVM's 64 KB method limit.
        val groups = devices.chunked(20)
        for ((n, group) in groups.withIndex()) {
        out.appendLine("private object QcyModelGroup$n {")
        out.appendLine("val MODELS: List<QcyModel> = listOf(")
        for (d in group) {
            val form = when (d["kind"]) {
                "earbuds" -> "EARBUDS"
                "headphones" -> "HEADPHONES"
                else -> error("unknown kind ${d["kind"]}")
            }
            val protocol = (d["protocol"] as String).uppercase()
            @Suppress("UNCHECKED_CAST")
            val anc = (d["anc"] as List<Map<String, Any?>>?).orEmpty().joinToString(", ", "listOf(", ")") { m ->
                @Suppress("UNCHECKED_CAST")
                val scenes = (m["scenes"] as List<Map<String, Any?>>?).orEmpty().joinToString(", ", "listOf(", ")") { s ->
                    "QcyScene(${str(s["scene"])}, ${hex(s["from"])}, ${hex(s["to"])})"
                }
                "QcyNoiseMode(${str(m["mode"])}, ${hex(m["from"])}, ${hex(m["to"])}, ${hex(m["default"])}, $scenes)"
            }
            @Suppress("UNCHECKED_CAST")
            val touch = (d["touch"] as Map<String, Any?>?)?.let { "QcyTouch(${strings(it["gestures"])}, ${strings(it["actions"])}${(it["extra_actions"] as Map<String, String>?)?.entries?.joinToString(", ", ", mapOf(", ")") { e -> "${e.key} to ${str(e.value)}" } ?: ""})" } ?: "null"
            @Suppress("UNCHECKED_CAST")
            val settings = (d["settings"] as List<Map<String, Any?>>?).orEmpty().joinToString(", ", "listOf(", ")") { "QcySetting(${hex(it["item"])}, ${str(it["setting"])}${(it["options"] as List<Map<String, Any?>>?)?.joinToString(", ", ", listOf(", ")") { o -> "${hex(o["key"])} to ${str(o["name"].toString().lowercase().removeSuffix(" mode"))}" } ?: ""})" }
            out.appendLine("    QcyModel(")
            out.appendLine("        ${int(d["vendor_id"])}, ${str(d["name"])}, ${strings(d["names"])}, ${str(d["family"])}, QcyProtocol.$protocol,")
            out.appendLine("        io.github.omegadestroyer.pairfree.protocol.Form.$form, anc = $anc,")
            @Suppress("UNCHECKED_CAST")
            val eq = (d["eq"] as Map<String, Any?>?)?.let { e ->
                @Suppress("UNCHECKED_CAST")
                // As one string per model ("name=g,g,g;name=..."): a list of doubles per preset passes the JVM's 64 KB method limit over 194 models.
                val curves = str((e["preset_gains"] as List<Map<String, Any?>>?).orEmpty().joinToString(";") { p ->
                    "${p["name"]}=${(p["gains"] as List<*>).joinToString(",") { (it as Number).toDouble().toString() }}"
                })
                "QcyEqSpec(${int(e["bands"])}, ${int(e["max_db"])}, ${(e["freqs"] as List<*>).joinToString(", ", "listOf(", ")") { int(it).toString() }}, ${strings(e["presets"])}, $curves)"
            } ?: "null"
            out.appendLine("        touch = $touch, balance = ${d["balance"] == true}, settings = $settings, sleepTimer = ${d["sleep_timer"] == true}, jieliNoise = ${d["jieli_noise"] == true},")
            out.appendLine("        eq = $eq,")
            out.appendLine("    ),")
        }
        out.appendLine(")")
        out.appendLine("}")
        out.appendLine()
        }
        out.appendLine("/** Every model in data/qcy/devices.json. */")
        out.appendLine("internal val QCY_MODELS: List<QcyModel> = " + groups.indices.joinToString(" + ") { "QcyModelGroup$it.MODELS" })

        val dir = outputDir.get().asFile.resolve(packageName.get().replace('.', '/'))
        dir.mkdirs()
        dir.resolve("QcyTables.kt").writeText(out.toString())
    }
}

val generateQcyTables = tasks.register<GenerateQcyTables>("generateQcyTables") {
    commandsJson = rootProject.layout.projectDirectory.file("../data/qcy/commands.json")
    devicesJson = rootProject.layout.projectDirectory.file("../data/qcy/devices.json")
    packageName = "io.github.omegadestroyer.pairfree.protocol.qcy"
    outputDir = layout.buildDirectory.dir("generated/qcy")
}

/** Writes 1MORE's commands, frame constants and models from data/1more. */
abstract class GenerateOneMoreTables : DefaultTask() {
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
        val commands = JsonSlurper().parse(commandsJson.get().asFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val devices = (JsonSlurper().parse(devicesJson.get().asFile) as Map<String, Any?>).getValue("devices") as List<Map<String, Any?>>
        fun str(v: Any?) = "\"" + (v as String).replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\$") + "\""
        fun strings(v: Any?) = (v as List<*>).joinToString(", ", "listOf(", ")") { str(it) }

        val out = StringBuilder()
        out.appendLine("// Generated from data/1more by the generateOneMoreTables task. Do not edit.")
        out.appendLine("package ${packageName.get()}")
        out.appendLine()
        @Suppress("UNCHECKED_CAST")
        val frame = commands.getValue("frame") as Map<String, Any?>
        out.appendLine("/** The frame's constants (data/1more/commands.json, frame). */")
        out.appendLine("public object OneMoreWire {")
        out.appendLine("    /** The serial service 1MORE's app opens: the standard serial port. */")
        out.appendLine("    public const val SERVICE_UUID: String = ${str(frame["service_uuid"])}")
        out.appendLine("    /** The first two bytes of a frame from the phone ... */")
        out.appendLine("    public const val SENT: Int = 0x${frame["sent"]}")
        out.appendLine("    /** ... and of one from the earbuds. */")
        out.appendLine("    public const val REPLY: Int = 0x${frame["reply"]}")
        out.appendLine("    public const val HEADER: Int = ${(frame["header"] as Number).toInt()}")
        out.appendLine("}")
        out.appendLine()
        @Suppress("UNCHECKED_CAST")
        val list = commands.getValue("commands") as List<Map<String, Any?>>
        out.appendLine("/** 1MORE's commands (data/1more/commands.json), by the names 1MORE's app gives them. */")
        out.appendLine("public object OneMoreCommand {")
        val seen = mutableSetOf<String>()
        for (c in list) {
            val name = c.getValue("name") as String
            check(seen.add(name)) { "two commands named $name" }
            out.appendLine("    public const val $name: Int = 0x${c["code"]}")
        }
        out.appendLine("    /** The handshake, the AI licence read, and deleting or uploading music: never sent. */")
        out.appendLine("    public val NEVER_SEND: Set<Int> = setOf(${list.filter { it["never_send"] == true }.joinToString(", ") { "0x${it["code"]}" }})")
        out.appendLine("    public val NAMES: Map<Int, String> = mapOf(${list.joinToString(", ") { "0x${it["code"]} to ${str(it["name"])}" }})")
        out.appendLine("}")
        out.appendLine()
        @Suppress("UNCHECKED_CAST")
        val presets = (commands.getValue("values") as Map<String, Any?>).getValue("preset_sound") as Map<String, Any?>
        out.appendLine("/** The preset sounds by the byte 1MORE's app sends for each (data/1more/commands.json, values.preset_sound). */")
        out.appendLine("public val ONEMORE_PRESET_SOUNDS: Map<Int, String> = mapOf(${presets.entries.joinToString(", ") { "${it.key} to ${str(it.value)}" }})")
        out.appendLine()
        out.appendLine("/** Every model in data/1more/devices.json. */")
        out.appendLine("internal val ONEMORE_MODELS: List<OneMoreModel> = listOf(")
        for (d in devices) {
            val form = when (d["kind"]) {
                "earbuds" -> "EARBUDS"
                "headphones" -> "HEADPHONES"
                else -> error("unknown kind ${d["kind"]}")
            }
            out.appendLine("    OneMoreModel(")
            out.appendLine("        ${str(d["name"])}, ${str(d["model"])}, io.github.omegadestroyer.pairfree.protocol.Form.$form, case = ${d["case"]}, tuya = ${d["tuya"]},")
            out.appendLine("        listenModes = ${strings(d["listen_modes"])}, connection = ${strings(d["connection"])},")
            out.appendLine("        autoplay = ${d["autoplay"]}, spatialAudio = ${d["spatial_audio"]}, sleepDetection = ${d["sleep_detection"]}, dualDevice = ${d["dual_device"]}, hotWord = ${d["hot_word"]},")
            out.appendLine("        touch = ${d["touch"]?.let(::str) ?: "null"}, touchAssistant = ${d["touch_assistant"]}, eq = ${strings(d["eq"])},")
            out.appendLine("        earReturn = ${d["ear_return"]}, volumeLimit = ${d["volume_limit"]}, lights = ${d["lights"] == true}, sleepMode = ${d["sleep_mode"] == true}, voiceEnhancement = ${d["voice_enhancement"] == true},")
            @Suppress("UNCHECKED_CAST")
            val bands = (d["eq_bands"] as Map<String, Any?>?)?.let { b ->
                fun ints(v: Any?) = (v as List<*>).joinToString(", ", "listOf(", ")") { (it as Number).toInt().toString() }
                "OneMoreEqBands(${ints(b["hz"])}, ${ints(b["q"])}, ${(b["zero"] as Number).toInt()}, " +
                    "${b["offsets"]?.let(::ints) ?: "emptyList()"}, wholeDb = ${b["whole_db"] == true})"
            } ?: "null"
            out.appendLine("        eqPresets = ${strings(d["eq_presets"])}, eqBands = $bands,")
            out.appendLine("    ),")
        }
        out.appendLine(")")

        val dir = outputDir.get().asFile.resolve(packageName.get().replace('.', '/'))
        dir.mkdirs()
        dir.resolve("OneMoreTables.kt").writeText(out.toString())
    }
}

val generateOneMoreTables = tasks.register<GenerateOneMoreTables>("generateOneMoreTables") {
    commandsJson = rootProject.layout.projectDirectory.file("../data/1more/commands.json")
    devicesJson = rootProject.layout.projectDirectory.file("../data/1more/devices.json")
    packageName = "io.github.omegadestroyer.pairfree.protocol.onemore"
    outputDir = layout.buildDirectory.dir("generated/onemore")
}

/** Writes EarFun's two command sets, its value tables, preset EQ curves and models from data/earfun. */
abstract class GenerateEarfunTables : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val commandsJson: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val devicesJson: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val presetsJson: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        @Suppress("UNCHECKED_CAST")
        val commands = JsonSlurper().parse(commandsJson.get().asFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val devices = (JsonSlurper().parse(devicesJson.get().asFile) as Map<String, Any?>).getValue("devices") as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val presets = JsonSlurper().parse(presetsJson.get().asFile) as Map<String, Any?>
        fun str(v: Any?) = "\"" + (v as String).replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\$") + "\""
        fun strings(v: Any?) = (v as List<*>?).orEmpty().joinToString(", ", "listOf(", ")") { str(it) }
        fun dbl(v: Any?) = (v as Number).toDouble().toString()
        fun doubles(v: Any?) = (v as List<*>).joinToString(", ", "listOf(", ")") { dbl(it) }
        fun ints(v: Any?) = (v as List<*>).joinToString(", ", "listOf(", ")") { (it as Number).toInt().toString() }
        fun intMap(v: Any?) = (v as Map<*, *>).entries.filter { !(it.key as String).startsWith("_") }
            .joinToString(", ", "mapOf(", ")") { "${str(it.key)} to ${(it.value as Number).toInt()}" }
        fun tables(v: Any?) = (v as Map<*, *>).entries.filter { !(it.key as String).startsWith("_") }
            .joinToString(", ", "mapOf(", ")") { "${str(it.key)} to ${intMap(it.value)}" }

        val out = StringBuilder()
        out.appendLine("// Generated from data/earfun by the generateEarfunTables task. Do not edit.")
        out.appendLine("package ${packageName.get()}")
        out.appendLine()
        @Suppress("UNCHECKED_CAST")
        val qcc = commands.getValue("qualcomm") as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val qccList = qcc.getValue("commands") as List<Map<String, Any?>>
        out.appendLine("/** The Qualcomm models' commands (data/earfun/commands.json, qualcomm): GAIA vendor 000A, command 03 and this number. */")
        out.appendLine("public object EarfunQcc {")
        out.appendLine("    public const val SERVICE_UUID: String = ${str(qcc["service_uuid"])}")
        out.appendLine("    public const val VENDOR: Int = 0x${qcc["vendor"]}")
        out.appendLine("    public const val COMMAND_PREFIX: Int = 0x${qcc["command_prefix"]}")
        out.appendLine("    public val REPLY_PREFIXES: Set<Int> = setOf(${(qcc["reply_prefixes"] as List<*>).joinToString(", ") { "0x$it" }})")
        out.appendLine("    public const val EQ_VENDOR: Int = 0x${qcc["eq_vendor"]}")
        out.appendLine("    public const val EQ_COMMAND: Int = 0x${qcc["eq_command"]}")
        val seen = mutableSetOf<String>()
        for (c in qccList) {
            val name = c.getValue("name") as String
            check(seen.add(name)) { "two qualcomm commands named $name" }
            out.appendLine("    public const val $name: Int = ${(c["code"] as Number).toInt()}")
        }
        out.appendLine("    /** Factory reset, renaming, firmware update and device management: never sent. */")
        out.appendLine("    public val NEVER_SEND: Set<Int> = setOf(${qccList.filter { it["never_send"] == true }.joinToString(", ") { (it["code"] as Number).toInt().toString() }})")
        out.appendLine("    /** Restarting and powering off: sent only from an action the user confirms on screen. */")
        out.appendLine("    public val CONFIRM: Set<Int> = setOf(${qccList.filter { it["confirm"] == true }.joinToString(", ") { (it["code"] as Number).toInt().toString() }})")
        out.appendLine("    /** Reads that return phones' names and addresses or a serial number: never sent. */")
        out.appendLine("    public val PRIVATE: Set<Int> = setOf(${qccList.filter { it["private"] == true }.joinToString(", ") { (it["code"] as Number).toInt().toString() }})")
        out.appendLine("    public val NAMES: Map<Int, String> = mapOf(${qccList.joinToString(", ") { "${(it["code"] as Number).toInt()} to ${str(it["name"])}" }})")
        out.appendLine("}")
        out.appendLine()
        @Suppress("UNCHECKED_CAST")
        val ef = commands.getValue("earfun") as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val efList = ef.getValue("commands") as List<Map<String, Any?>>
        out.appendLine("/** EarFun's own commands (data/earfun/commands.json, earfun), two bytes each. */")
        out.appendLine("public object EarfunCommand {")
        out.appendLine("    public const val SERVICE_UUID: String = ${str(ef["service_uuid"])}")
        out.appendLine("    public const val START: Int = 0x${ef["start"]}")
        out.appendLine("    public const val END: Int = 0x${ef["end"]}")
        seen.clear()
        for (c in efList) {
            val name = c.getValue("name") as String
            check(seen.add(name)) { "two earfun commands named $name" }
            out.appendLine("    public const val $name: Int = 0x${c["code"]}")
        }
        out.appendLine("    /** Factory reset, renaming and device management: never sent. */")
        out.appendLine("    public val NEVER_SEND: Set<Int> = setOf(${efList.filter { it["never_send"] == true }.joinToString(", ") { "0x${it["code"]}" }})")
        out.appendLine("    /** Powering off: sent only from an action the user confirms on screen. */")
        out.appendLine("    public val CONFIRM: Set<Int> = setOf(${efList.filter { it["confirm"] == true }.joinToString(", ") { "0x${it["code"]}" }})")
        out.appendLine("    /** Reads that return phones' names and addresses: never sent. */")
        out.appendLine("    public val PRIVATE: Set<Int> = setOf(${efList.filter { it["private"] == true }.joinToString(", ") { "0x${it["code"]}" }})")
        out.appendLine("    public val NAMES: Map<Int, String> = mapOf(${efList.joinToString(", ") { "0x${it["code"]} to ${str(it["name"])}" }})")
        out.appendLine("}")
        out.appendLine()
        @Suppress("UNCHECKED_CAST")
        val values = commands.getValue("values") as Map<String, Any?>
        out.appendLine("/** What EarFun's app writes and reads for each setting (data/earfun/commands.json, values), by table name. */")
        out.appendLine("public object EarfunValues {")
        out.appendLine("    public val AUTO_OFF: Map<String, Map<String, Int>> = ${tables(values["auto_off"])}")
        out.appendLine("    public val VOICE_VOLUME: Map<String, Map<String, Int>> = ${tables(values["voice_volume"])}")
        out.appendLine("    public val VOICE_PROTECT: Map<String, Int> = ${intMap(values["voice_protect"])}")
        out.appendLine("    public val NOISE_CANCEL_MODE: Map<String, Map<String, Int>> = ${tables(values["noise_cancel_mode"])}")
        out.appendLine("    public val AMBIENT_MODE: Map<String, Map<String, Int>> = ${tables(values["ambient_mode"])}")
        out.appendLine("    public val KEY_ACTIONS: Map<String, Map<String, Int>> = ${tables(values["key_actions"])}")
        out.appendLine("    public val KEY_GESTURES: Map<String, Map<String, Int>> = ${tables(values["key_gestures"])}")
        out.appendLine("    public val KEY_BUTTONS: Map<String, Map<String, Int>> = ${tables(values["key_buttons"])}")
        out.appendLine("    public val QUALITY_BITS: Map<String, Map<String, Int>> = ${tables(values["quality_bits"])}")
        out.appendLine("    public val VOICE_LANGUAGE: Map<String, Map<String, Int>> = ${tables(values["voice_language"])}")
        out.appendLine("    /** A slider position (-10 to 10) as a gain on the 10-band models (data/earfun/eq_presets.json, gain_db). */")
        out.appendLine("    public val GAIN_DB: List<Double> = ${doubles(presets["gain_db"])}")
        out.appendLine("    /** EarFun's preset curves as slider positions, by list (10 or 6 bands). */")
        val lists = presets.entries.filter { it.value is List<*> && it.key != "gain_db" }
        out.appendLine("    public val PRESETS: Map<String, List<Pair<String, List<Int>>>> = mapOf(")
        for ((key, list) in lists) {
            out.appendLine("        ${str(key)} to listOf(")
            @Suppress("UNCHECKED_CAST")
            for (p in list as List<Map<String, Any?>>) out.appendLine("            ${str(p["name"])} to ${ints(p["positions"])},")
            out.appendLine("        ),")
        }
        out.appendLine("    )")
        out.appendLine("}")
        out.appendLine()
        out.appendLine("/** Every model in data/earfun/devices.json. */")
        out.appendLine("internal val EARFUN_MODELS: List<EarfunModel> = listOf(")
        for (d in devices) {
            val form = when (d["kind"]) {
                "earbuds" -> "EARBUDS"
                "headphones" -> "HEADPHONES"
                else -> error("unknown kind ${d["kind"]}")
            }
            fun sub(v: Any?) = (v as Map<*, *>?)?.let { "EarfunSubMode(${str(it["table"])}, ${strings(it["options"])})" } ?: "null"
            val noise = (d["noise"] as Map<*, *>?)?.let { n -> "EarfunNoise(${intMap(n["values"])}, ${sub(n["cancelling"])}, ${sub(n["ambient"])})" } ?: "null"
            val touch = (d["touch"] as Map<*, *>?)?.let { t ->
                val gestures = (t["gestures"] as Map<*, *>).entries.joinToString(", ", "mapOf(", ")") { "${str(it.key)} to ${strings(it.value)}" }
                "EarfunTouch(${str(t["map"])}, $gestures, ${t["disable"]?.let(::str) ?: "null"}, callMap = ${t["call_map"]?.let(::str) ?: "null"})"
            } ?: "null"
            val eq = (d["eq"] as Map<*, *>?)?.let { e ->
                val m = e["master"] as Map<*, *>
                val master = "EarfunMaster(db = ${m["db"]?.let(::dbl) ?: "null"}, base = ${m["base"]?.let(::dbl) ?: "null"}, whole = ${m["whole"] == true}, " +
                    "byPeak = ${m["by_peak"]?.let(::doubles) ?: "null"})"
                val fixed = (e["fixed_after"] as List<*>?).orEmpty().joinToString(", ", "listOf(", ")") { b ->
                    val f = b as Map<*, *>
                    "EarfunFixedBand(${dbl(f["hz"])}, ${dbl(f["q"])}, ${dbl(f["db"])})"
                }
                "EarfunEqSpec(${doubles(e["hz"])}, ${doubles(e["q"])}, ${str(e["frame"])}, positionGain = ${e["gain"] == "position"}, master = $master, " +
                    "presets = ${str(e["presets"])}, fixedAfter = $fixed)"
            } ?: "null"
            val buttons = (d["buttons"] as Map<*, *>?)?.let { b ->
                val gestures = (b["gestures"] as Map<*, *>).entries.joinToString(", ", "mapOf(", ")") { "${str(it.key)} to ${strings(it.value)}" }
                "EarfunButtons(${str(b["layout"])}, ${str(b["map"])}, $gestures)"
            } ?: "null"
            out.appendLine("    EarfunModel(")
            out.appendLine("        ${strings(d["codes"])}, ${str(d["name"])}, ${strings(d["names"])}, ${str(d["protocol"])}, ${str(d["chip"])},")
            out.appendLine("        io.github.omegadestroyer.pairfree.protocol.Form.$form, case = ${d["case"]}, covered = ${d["covered"]},")
            out.appendLine("        noise = $noise,")
            out.appendLine("        game = ${d["game"]}, multipoint = ${d["multipoint"]}, mic = ${d["mic"]}, promptVolume = ${d["prompt_volume"]}, find = ${d["find"]},")
            out.appendLine("        hearingProtection = ${d["hearing_protection"]}, spatial = ${d["spatial"]}, inEar = ${d["in_ear"]}, caseChargeBit = ${d["case_charge_bit"]},")
            out.appendLine("        autoOff = ${strings(d["auto_off"])}, autoOffTable = ${d["auto_off_table"]?.let(::str) ?: "null"},")
            out.appendLine("        touch = $touch,")
            out.appendLine("        eq = $eq,")
            out.appendLine("        versionText = ${d["version"] == "text"}, gaiaInit = ${d["gaia_init"]},")
            out.appendLine("        quality = ${strings(d["quality"])}, ldacAptxConflict = ${d["ldac_aptx_conflict"]}, ldacMultipointConflict = ${d["ldac_multipoint_conflict"]},")
            out.appendLine("        ldacGameConflict = ${d["ldac_game_conflict"]}, multipointRestart = ${d["multipoint_restart"]}, restart = ${d["restart"]}, powerOff = ${d["power_off"]},")
            out.appendLine("        voiceLanguages = ${strings(d["voice_languages"])}, promptVolumes = ${strings(d["prompt_volumes"])}, buttons = $buttons, extras = ${strings(d["extras"])},")
            out.appendLine("    ),")
        }
        out.appendLine(")")

        val dir = outputDir.get().asFile.resolve(packageName.get().replace('.', '/'))
        dir.mkdirs()
        dir.resolve("EarfunTables.kt").writeText(out.toString())
    }
}

val generateEarfunTables = tasks.register<GenerateEarfunTables>("generateEarfunTables") {
    commandsJson = rootProject.layout.projectDirectory.file("../data/earfun/commands.json")
    devicesJson = rootProject.layout.projectDirectory.file("../data/earfun/devices.json")
    presetsJson = rootProject.layout.projectDirectory.file("../data/earfun/eq_presets.json")
    packageName = "io.github.omegadestroyer.pairfree.protocol.earfun"
    outputDir = layout.buildDirectory.dir("generated/earfun")
}

/** Writes the hearing tests' tone tables (data/<brand>/hearing_test.json). */
abstract class GenerateHearingTables : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val soundpeatsJson: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        @Suppress("UNCHECKED_CAST")
        val sp = JsonSlurper().parse(soundpeatsJson.get().asFile) as Map<String, Any?>
        fun f(v: Any?) = (v as Number).toFloat().toString() + "f"
        val out = StringBuilder()
        out.appendLine("// Generated from data/<brand>/hearing_test.json by the generateHearingTables task. Do not edit.")
        out.appendLine("package ${packageName.get()}")
        out.appendLine()
        @Suppress("UNCHECKED_CAST")
        val tones = (sp.getValue("tones") as List<List<Number>>).joinToString(", ") { "HearingTone(${f(it[0])}, ${f(it[1])}, ${f(it[2])})" }
        out.appendLine("/** SOUNDPEATS' hearing test (data/soundpeats/hearing_test.json). */")
        out.appendLine("internal val SOUNDPEATS_HEARING: HearingTable = HearingTable(listOf($tones), start = ${(sp.getValue("start") as Number).toInt()}, " +
            "q = ${f(sp["q"])}, seconds = ${(sp.getValue("seconds") as Number).toInt()})")
        val dir = outputDir.get().asFile.resolve(packageName.get().replace('.', '/'))
        dir.mkdirs()
        dir.resolve("HearingTables.kt").writeText(out.toString())
    }
}

val generateHearingTables = tasks.register<GenerateHearingTables>("generateHearingTables") {
    soundpeatsJson = dataDir.file("hearing_test.json")
    packageName = "io.github.omegadestroyer.pairfree.protocol.hearing"
    outputDir = layout.buildDirectory.dir("generated/hearing")
}

/** Cambridge's legacy GAIA commands, field offsets and two models, read from its own app. */
abstract class GenerateCambridgeTables : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val commandsJson: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val devicesJson: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        @Suppress("UNCHECKED_CAST")
        val c = JsonSlurper().parse(commandsJson.get().asFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val d = JsonSlurper().parse(devicesJson.get().asFile) as Map<String, Any?>
        fun quoted(v: Any?) = groovy.json.JsonOutput.toJson(v)
        fun connectConfig(m: Map<String, Any?>): String {
            @Suppress("UNCHECKED_CAST")
            val cfg = m["config"] as Map<String, Any?>? ?: return "null"
            @Suppress("UNCHECKED_CAST")
            val eq = m.getValue("eq") as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val presets = (eq.getValue("presets") as List<Map<String, Any?>>).joinToString(", ") { p -> "${quoted(p.getValue("name"))} to listOf(${(p.getValue("gains") as List<*>).joinToString(", ")})" }
            return "ConnectConfig(headphones = ${cfg["headphones"]}, touch = ${cfg["touch"]}, mono = ${cfg["mono"]}, sleep = ${cfg["sleep"]}, loudness = ${cfg["loudness"]}, " +
                "sideTone = ${cfg["side_tone"]}, connected = ${cfg["connected"]}, codec = ${cfg["codec"]}, spatial = ${cfg["spatial"]}, adaptive = ${cfg["adaptive"]}, " +
                "eqMinDb = ${eq["min_db"]}, eqMaxDb = ${eq["max_db"]}, eqCustom = ${eq["custom"]}, eqPresets = listOf($presets))"
        }
        val out = StringBuilder("// Generated from data/cambridge; do not edit.\npackage io.github.omegadestroyer.pairfree.protocol.cambridge\n\n")
        for ((section, type) in mapOf("wire" to "CambridgeWire", "commands" to "CambridgeCommand", "values" to "CambridgeValue", "offsets" to "CambridgeOffset")) {
            out.appendLine("public object $type {")
            @Suppress("UNCHECKED_CAST")
            for ((key, value) in c.getValue(section) as Map<String, Any>) {
                check(key.matches(Regex("[A-Z_]+")))
                out.appendLine("    public const val $key: ${if (value is String) "String" else "Int"} = ${quoted(value)}")
            }
            out.appendLine("}")
        }
        @Suppress("UNCHECKED_CAST")
        val connect = c.getValue("connect") as Map<String, Any?>
        for ((key, value) in connect) {
            if (value !is Map<*, *>) continue
            val name = "CambridgeConnect" + key.split('_').joinToString("") { it.replaceFirstChar(Char::uppercase) }
            out.appendLine("public object $name {")
            for ((k, v) in value) {
                check(k.toString().matches(Regex("[A-Z_0-9]+")))
                out.appendLine("    public const val $k: Int = $v")
            }
            out.appendLine("}")
        }
        out.appendLine("public val CAMBRIDGE_CONNECT_AUTO_OFF: List<Int> = listOf(${(connect.getValue("auto_power_down_seconds") as List<*>).joinToString(", ")})")
        out.appendLine("public object CambridgeValues {")
        for (key in listOf("languages", "codecs", "controls")) {
            val values = (c.getValue(key) as List<*>).joinToString(", ") { quoted(it) }
            out.appendLine("    public val ${key.uppercase()}: List<String> = listOf($values)")
        }
        @Suppress("UNCHECKED_CAST")
        val presets = c.getValue("presets") as List<Map<String, Any?>>
        out.appendLine("    public val PRESETS: List<Pair<String, List<Int>>> = listOf(")
        for (p in presets) out.appendLine("        ${quoted(p.getValue("name"))} to listOf(${(p.getValue("gains") as List<*>).joinToString(", ")}),")
        out.appendLine("    )\n}")
        out.appendLine("internal val CAMBRIDGE_MODELS: List<CambridgeModel> = listOf(")
        @Suppress("UNCHECKED_CAST")
        for (m in d.getValue("devices") as List<Map<String, Any?>>) {
            val names = (m.getValue("names") as List<*>).joinToString(", ") { quoted(it) }
            out.appendLine("    CambridgeModel(${quoted(m.getValue("name"))}, listOf($names), ${m.getValue("ambient")}, ${m.getValue("control_count")}, ${m["connect"] == true}, ${connectConfig(m)}),")
        }
        out.appendLine(")")
        val target = outputDir.get().asFile.resolve("io/github/omegadestroyer/pairfree/protocol/cambridge/CambridgeTables.kt")
        target.parentFile.mkdirs()
        target.writeText(out.toString())
    }
}

val generateCambridgeTables = tasks.register<GenerateCambridgeTables>("generateCambridgeTables") {
    commandsJson = rootProject.layout.projectDirectory.file("../data/cambridge/commands.json")
    devicesJson = rootProject.layout.projectDirectory.file("../data/cambridge/devices.json")
    outputDir = layout.buildDirectory.dir("generated/cambridge")
}

/** Generated wire constants for oneplus's protocol-core review draft. */
abstract class GenerateOneplusTables : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceJson: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        @Suppress("UNCHECKED_CAST")
        val sections = JsonSlurper().parse(sourceJson.get().asFile) as Map<String, Any?>
        val text = StringBuilder("// Generated from data/oneplus/commands.json; do not edit.\npackage io.github.omegadestroyer.pairfree.protocol.oneplus\n\n")
        for ((section, raw) in sections.filterKeys { !it.startsWith("_") }) {
            check(section.matches(Regex("[a-z]+")))
            text.appendLine("public object Oneplus${section.replaceFirstChar { it.uppercase() }} {")
            @Suppress("UNCHECKED_CAST")
            for ((key, value) in raw as Map<String, Any>) {
                check(key.matches(Regex("[A-Z_0-9]+")) && (value is String || value is Number))
                text.appendLine("    public const val $key: ${if (value is String) "String" else "Int"} = ${groovy.json.JsonOutput.toJson(value)}")
            }
            text.appendLine("}")
        }
        val target = outputDir.get().asFile.resolve("io/github/omegadestroyer/pairfree/protocol/oneplus/OneplusTables.kt")
        target.parentFile.mkdirs()
        target.writeText(text.toString())
    }
}
val generateOneplusTables = tasks.register<GenerateOneplusTables>("generateOneplusTables") {
    sourceJson = rootProject.layout.projectDirectory.file("../data/oneplus/commands.json")
    outputDir = layout.buildDirectory.dir("generated/oneplus")
}

/** The OnePlus and OPPO model table from data/oneplus/devices.json (tools/import_heymelody_catalog.py). */
abstract class GenerateOneplusModels : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val devicesJson: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        fun str(v: Any?) = "\"" + (v as String).replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
        @Suppress("UNCHECKED_CAST")
        val devices = (JsonSlurper().parse(devicesJson.get().asFile) as Map<String, Any?>).getValue("devices") as List<Map<String, Any?>>
        fun noise(list: Any?): String {
            @Suppress("UNCHECKED_CAST")
            val items = (list as? List<Map<String, Any?>>).orEmpty()
            return items.joinToString(", ", "listOf(", ")") { "OneplusNoise(${it["index"]}, ${str(it["type"])}, ${noise(it["children"])})" }
        }
        fun touch(list: Any?): String {
            @Suppress("UNCHECKED_CAST")
            val items = (list as? List<Map<String, Any?>>).orEmpty().filter { (it["button"] == 1 || it["button"] == 6) && it["functions"] != null }
            return items.joinToString(", ", "listOf(", ")") { t ->
                @Suppress("UNCHECKED_CAST")
                val fns = (t["functions"] as List<Map<String, Any?>>).filter { it["wire"] != null }.joinToString(", ", "listOf(", ")") { "OneplusFunction(${str(it["key"])}, ${it["wire"]})" }
                "OneplusTouch(${str(t["gesture"])}, ${t["action_byte"]}, $fns, ${t["button"]})"
            }
        }
        val out = StringBuilder("// Generated from data/oneplus/devices.json; do not edit.\npackage io.github.omegadestroyer.pairfree.protocol.oneplus\n\n")
        out.appendLine("internal val ONEPLUS_MODELS: List<OneplusModel> = listOf(")
        for (d in devices) {
            @Suppress("UNCHECKED_CAST")
            val eq = d["eq"] as? Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val presets = (eq?.get("presets") as? List<Map<String, Any?>>).orEmpty().joinToString(", ", "listOf(", ")") { "OneplusPreset(${it["index"]}, ${str(it["name"])})" }
            @Suppress("UNCHECKED_CAST")
            val features = d["features"] as? Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val names = (d["names"] as List<String>).joinToString(", ") { str(it) }
            out.appendLine(
                "    OneplusModel(${str(d["id"])}, ${str(d["name"])}, listOf($names), ${str(d["brand"])}, ${d["hidden"] == true}, " +
                    "${noise(d["noise"])}, $presets, ${eq?.get("custom") == true}, ${features?.get("gameMode") == 1}, ${touch(d["touch"])}, ${((eq?.get("frequencies") as? List<*>).orEmpty()).joinToString(", ", "listOf(", ")")}, ${eq?.get("slots") ?: 0}, ${d["noise"] != null && ((features?.get("longPressType") as? Number)?.toInt() ?: 0) == 0}, ${((features?.get("spatialTypes") as? List<*>).orEmpty()).joinToString(", ", "listOf(", ")")}, ${features?.get("heyFindDevice") == 1 || features?.get("findDevice") == 1}, ${((d["switches"] as? List<*>).orEmpty()).joinToString(", ", "listOf(", ")")}, ${(d["prompt_volume"] as? Map<*, *>)?.let { "listOf(${it["min"]}, ${it["max"]})" } ?: "emptyList()"}, ${d["tap_level"] == true}, ${d["fit_test"] == true}),",
            )
        }
        out.appendLine(")")
        val target = outputDir.get().asFile.resolve("io/github/omegadestroyer/pairfree/protocol/oneplus/OneplusModelTable.kt")
        target.parentFile.mkdirs()
        target.writeText(out.toString())
    }
}
val generateOneplusModels = tasks.register<GenerateOneplusModels>("generateOneplusModels") {
    devicesJson = rootProject.layout.projectDirectory.file("../data/oneplus/devices.json")
    outputDir = layout.buildDirectory.dir("generated/oneplus-models")
}

kotlin.sourceSets.main {
    kotlin.srcDir(generateCambridgeTables)
    kotlin.srcDir(generateOneplusTables)
    kotlin.srcDir(generateOneplusModels)
    kotlin.srcDir(generateHearingTables)
    kotlin.srcDir(generateIdentifyTables)
    kotlin.srcDir(generateEarfunTables)
    kotlin.srcDir(generateTables)
    kotlin.srcDir(generateNothingTables)
    kotlin.srcDir(generatePixelTables)
    kotlin.srcDir(generateQcyTables)
    kotlin.srcDir(generateOneMoreTables)
    kotlin.srcDir(generateAppleTables)
    kotlin.srcDir(generateSoundcoreTables)
    kotlin.srcDir(generateSamsungTables)
    kotlin.srcDir(generateSonyTables)
    kotlin.srcDir(generateBoseTables)
    kotlin.srcDir(generateJblTables)
}

dependencies {
    // The one conversation loop every brand shares (Conversation.kt) is coroutines; the library is multiplatform, as this module must stay.
    api(libs.coroutines.core)
    testImplementation(libs.coroutines.test)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.serialization.json)
}

tasks.test {
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/cambridge")).withPathSensitivity(PathSensitivity.RELATIVE)
    useJUnitPlatform()
    inputs.dir(dataDir).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/soundcore")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/samsung")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/sony")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/bose")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/jbl")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/apple")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/oneplus")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/nothing")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/pixel")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/qcy")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/1more")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/earfun")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("../tools/probe.py")).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("pairfree.data", dataDir.asFile.absolutePath)
}
