import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Writes the app's looks from data/looks: one JSON file per look (docs/LOOKS.md), so a new look is offered in the app
 * without a line of code, as a new language file is. A colour is "#RRGGBB" or one of Android's wallpaper colours by its
 * resource name (system_accent1_200).
 */
abstract class GenerateLooks : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val looksDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val roles = listOf("background", "panel", "raised", "hairline", "text", "secondary_text", "label", "accent", "on_accent", "destructive")
        // Roles only some looks need, and the role each falls back to (docs/LOOKS.md).
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
        out.appendLine("// Generated from data/looks by the generateLooks task. Do not edit.")
        out.appendLine("package io.github.omegadestroyer.pairfree.look")
        out.appendLine()
        out.appendLine("import io.github.omegadestroyer.pairfree.R")
        out.appendLine()
        out.appendLine("/** The looks built in, in the order the picker lists them (data/looks, docs/LOOKS.md). */")
        out.appendLine("internal val BUILT_IN_LOOKS: List<LookSpec> = listOf(")
        for ((id, look) in looks) {
            fun fail(why: String): Nothing = throw GradleException("data/looks/$id.json: $why")
            if (!Regex("[a-z][a-z0-9-]*").matches(id)) fail("the file name is the look's id: small letters, digits and hyphens")
            fun palette(key: String): String {
                @Suppress("UNCHECKED_CAST")
                val p = look[key] as? Map<String, Any?> ?: return "null"
                val unknown = p.keys - roles.toSet() - optional.keys
                if (unknown.isNotEmpty()) fail("$key has roles the app does not know: $unknown")
                return (roles + optional.keys).joinToString(", ", "Palette(", ")") { role ->
                    // A role a look leaves out takes another role's colour.
                    val v = (p[role] ?: p[optional[role]]) as? String ?: fail("$key lacks $role")
                    when {
                        hex.matches(v) -> "${camel(role)} = Tone.fixed(0xFF${v.drop(1).uppercase()}.toInt())"
                        system.matches(v) -> "${camel(role)} = Tone.system(android.R.color.$v)"
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
            out.appendLine("    LookSpec(")
            out.appendLine("        id = ${str(id)},")
            // The name people see is in the language file (CLAUDE.md, Text and translations); the file's own "name" is for readers of the file.
            out.appendLine("        name = R.string.look_${id.replace('-', '_')},")
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
        val dir = outputDir.get().asFile.resolve("io/github/omegadestroyer/pairfree/look")
        dir.mkdirs()
        dir.resolve("BuiltInLooks.kt").writeText(out.toString())
    }
}

val generateLooks = tasks.register<GenerateLooks>("generateLooks") {
    looksDir = rootProject.layout.projectDirectory.dir("../data/looks")
    outputDir = layout.buildDirectory.dir("generated/looks")
}

androidComponents {
    onVariants { variant ->
        variant.sources.kotlin?.addGeneratedSourceDirectory(generateLooks, GenerateLooks::outputDir)
    }
}

// The languages built in: the English file, and each res/values-<code>/strings.xml as a language tag (ko, pt-BR), so a
// new language file is offered in the app without a line of code (docs/TRANSLATING.md).
val builtInLanguages = listOf("en") + file("src/main/res").listFiles().orEmpty()
    .filter { it.isDirectory && it.name.startsWith("values-") && File(it, "strings.xml").exists() }
    .map { it.name.removePrefix("values-") }
    .map { q -> if (q.startsWith("b+")) q.removePrefix("b+").replace('+', '-') else q.replace("-r", "-") }
    .sorted()

android {
    namespace = "io.github.omegadestroyer.pairfree"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.omegadestroyer.pairfree"
        // Android 12: BLUETOOTH_CONNECT is the only permission (docs/DESIGN.md).
        // Going lower would also need the old BLUETOOTH permission.
        minSdk = 31
        targetSdk = 37
        // x.y.z, and the code is x * 1,000,000 + y * 1,000 + z (CLAUDE.md, Versions; tools/test_version.py checks it).
        versionCode = 14000
        versionName = "0.14.0"
        buildConfigField("String[]", "LANGUAGES", builtInLanguages.joinToString(prefix = "{", postfix = "}") { "\"$it\"" })
    }

    // The release key lives outside the repo. Its file and passwords come from
    // ~/.gradle/gradle.properties (soundpeter.*); without them a release build is unsigned.
    val releaseStore = providers.gradleProperty("soundpeter.storeFile").orNull
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = providers.gradleProperty("soundpeter.storePassword").get()
                keyAlias = providers.gradleProperty("soundpeter.keyAlias").get()
                keyPassword = providers.gradleProperty("soundpeter.keyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Builds for testers (assembleDev): a debug build under its own id and name ("PairFree Dev",
        // src/dev), so it installs beside the released app without touching it or its settings. It is
        // signed with this computer's debug key, so later dev builds install over it only from here.
        // Shrunk as release is, so it carries no unused library code, but with readable names
        // (proguard-dev.pro) so its crash traces and logs need no mapping file.
        create("dev") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            matchingFallbacks += "debug"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro", "proguard-dev.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Android 13 and later list the app's languages under the phone's per-app language setting, from the same folders
    // (res/resources.properties names English as the language of res/values).
    androidResources {
        generateLocaleConfig = true
    }

    // A translation may lag behind the English file, or keep an entry the English file dropped: it still builds, and
    // an entry it lacks shows in English (docs/TRANSLATING.md). tools/test_translations.py checks what must hold.
    lint {
        warning += listOf("MissingTranslation", "ExtraTranslation")
    }

    // Plain JVM tests of the app's own tables (the names in the language file); nothing here needs a device.
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all { it.useJUnitPlatform() }
    }

    // The build tools add an encrypted list of dependencies to the APK's signing block for Google
    // Play. Nothing else reads it, and F-Droid rejects APKs that carry it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":protocol"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    implementation(libs.kotlinx.io.core)
    // Switches Android's hidden-API protection off for BluetoothSocket only, to open the AirPods' channel (Apache-2.0; docs/apple/PROTOCOL.md section 7).
    implementation(libs.hiddenapibypass)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
