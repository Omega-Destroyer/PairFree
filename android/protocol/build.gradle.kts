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
            out.appendLine("    Model(${str(d["firmware_code"])}, $names, ${str(d["family"])}, $excludes, $either, $eq, $confirmed, $keepsAnc, fastPair = ${d["fast_pair"]}),")
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
            out.appendLine("    io.github.omegadestroyer.pairfree.protocol.Model(${str(d["model"])}, $names, \"SOUNDCORE\", ldacExcludes = $excludes, form = $form, fastPair = ${d["fast_pair"]}),")
        }
        out.appendLine(")")

        out.appendLine()
        out.appendLine("/** Every model's state layout and quirks (data/soundcore/devices.json), read from OpenSCQ30's parsers. */")
        out.appendLine("internal val SOUNDCORE_LAYOUTS: List<SoundcoreLayout> = listOf(")
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
            @Suppress("UNCHECKED_CAST")
            val features = (((d["openscq30"] as Map<String, Any?>?)?.get("features") as List<*>? ?: emptyList<Any>()) +
                (d["extra_features"] as List<*>? ?: emptyList<Any>())).distinct().joinToString(", ", "setOf(", ")") { str(it) }
            out.appendLine("    SoundcoreLayout(${str(d["model"])}, $fields, ${scale("earbuds")}, ${scale("case")}, $checksum, $uuid, $gaming, $minFirmware, $features),")
        }
        out.appendLine(")")

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
        out.appendLine("    /** A level of 127 leaves the mode's level as it is. */")
        out.appendLine("    public const val IGNORE_LEVEL: Int = ${int(p4["ignore_level"])}")
        out.appendLine("    /** Commands PairFree never sends: ${p4Never.values.joinToString(", ")}. */")
        out.appendLine("    public val NEVER_SEND: Set<Int> = setOf(${p4Never.keys.joinToString(", ") { hex(it.toInt(16)) }})")
        out.appendLine("}")
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
            out.appendLine(
                "    JblModel(${str(d["name"])}, listOf($pids), io.github.omegadestroyer.pairfree.protocol.Form.$form, gatt = $gatt, " +
                    "family = JblModel.Family.$family, noise = $noise, modes = listOf($modes), autoOff = $autoOff, " +
                    "autoOffMinutes = listOf($minutes), autoPlayPause = ${d["auto_play_pause"] == true}, security = $security),",
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
                    "0x${d["product_id"]}, adaptive = ${d["adaptive"] == true}),",
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

kotlin.sourceSets.main {
    kotlin.srcDir(generateTables)
    kotlin.srcDir(generateAppleTables)
    kotlin.srcDir(generateSoundcoreTables)
    kotlin.srcDir(generateSamsungTables)
    kotlin.srcDir(generateSonyTables)
    kotlin.srcDir(generateBoseTables)
    kotlin.srcDir(generateJblTables)
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
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/samsung")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/sony")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/bose")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/jbl")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/apple")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("../tools/probe.py")).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("pairfree.data", dataDir.asFile.absolutePath)
}
