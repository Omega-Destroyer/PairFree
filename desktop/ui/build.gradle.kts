// The desktop apps' screens and the small toolkit they are drawn with, in common Kotlin for Windows and macOS (docs/DESKTOP.md).
// The toolkit draws through the Canvas interface; each system's module backs it with its own drawing calls.

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
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
