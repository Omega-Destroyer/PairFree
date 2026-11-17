// PairFree's Windows side: Windows' Bluetooth, behind the core module's Transport. The window, the tray and the rest of the shell join it
// in the phases after the first (docs/DESKTOP.md).

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    mingwX64()

    sourceSets {
        commonMain {
            dependencies {
                api(project(":shell"))
            }
        }
    }
}
