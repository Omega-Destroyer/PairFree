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

// The generate*Tables tasks: Kotlin constants and tables written from data/<brand>/*.json, so no number is retyped.
apply(from = "tables.gradle.kts")

val dataDir: Directory = rootProject.layout.projectDirectory.dir("../data/soundpeats")

kotlin.sourceSets.main {
    kotlin.srcDir(tasks.named("generateHuaweiTables"))
    kotlin.srcDir(tasks.named("generateCambridgeTables"))
    kotlin.srcDir(tasks.named("generateSennheiserTables"))
    kotlin.srcDir(tasks.named("generateJabraTables"))
    kotlin.srcDir(tasks.named("generateSkullcandyTables"))
    kotlin.srcDir(tasks.named("generateSkullcandyModels"))
    kotlin.srcDir(tasks.named("generateOneplusTables"))
    kotlin.srcDir(tasks.named("generateOneplusModels"))
    kotlin.srcDir(tasks.named("generateHearingTables"))
    kotlin.srcDir(tasks.named("generateIdentifyTables"))
    kotlin.srcDir(tasks.named("generateEarfunTables"))
    kotlin.srcDir(tasks.named("generateTables"))
    kotlin.srcDir(tasks.named("generateNothingTables"))
    kotlin.srcDir(tasks.named("generatePixelTables"))
    kotlin.srcDir(tasks.named("generateQcyTables"))
    kotlin.srcDir(tasks.named("generateOneMoreTables"))
    kotlin.srcDir(tasks.named("generateAppleTables"))
    kotlin.srcDir(tasks.named("generateSoundcoreTables"))
    kotlin.srcDir(tasks.named("generateSamsungTables"))
    kotlin.srcDir(tasks.named("generateSonyTables"))
    kotlin.srcDir(tasks.named("generateBoseTables"))
    kotlin.srcDir(tasks.named("generateJblTables"))
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
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/sennheiser")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/jabra")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/pixel")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/qcy")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/1more")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("../data/earfun")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("../tools/probe.py")).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("pairfree.data", dataDir.asFile.absolutePath)
}
