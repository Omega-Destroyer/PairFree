// The desktop apps' shared shell code: what Windows and macOS both need and Android does not (the settings file, the strings
// table, the looks), written once in common Kotlin (docs/DESKTOP.md). Only the OS calls live in desktop/windows and desktop/macos.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

apply(from = "tables.gradle.kts")
apply(from = "looks.gradle.kts")

kotlin {
    mingwX64()

    sourceSets {
        commonMain {
            kotlin.srcDir(files(layout.buildDirectory.dir("generated/strings")).builtBy("generateStrings"))
            kotlin.srcDir(files(layout.buildDirectory.dir("generated/looks")).builtBy("generateDesktopLooks"))
            dependencies {
                api(project(":core"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
