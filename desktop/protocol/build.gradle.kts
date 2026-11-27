// The protocol module for the desktop apps: the very sources Android compiles (android/protocol/src/main/kotlin) and the same
// generated tables, built as Kotlin Multiplatform for Windows. Nothing is copied: a brand added for Android is here at once.
// `compileKotlinMingwX64` is also the check that those sources stay free of JVM-only calls.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

// The generate*Tables tasks, shared with the Android build; they write into this module's build folder.
apply(from = "../../android/protocol/tables.gradle.kts")

kotlin {
    explicitApi()
    mingwX64()

    sourceSets {
        commonMain {
            kotlin.srcDir("../../android/protocol/src/main/kotlin")
            tasks.withType<DefaultTask>().matching { it.name.startsWith("generate") && (it.name.endsWith("Tables") || it.name.endsWith("Models")) }.all {
                kotlin.srcDir(this)
            }
            dependencies {
                api(libs.coroutines.core)
            }
        }
    }
}
