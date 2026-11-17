// The desktop apps' shared shell code: what Windows and macOS both need and Android does not (the settings file, the strings
// table, the looks), written once in common Kotlin (docs/DESKTOP.md). Only the OS calls live in desktop/windows and desktop/macos.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    mingwX64()

    sourceSets {
        commonMain {
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
