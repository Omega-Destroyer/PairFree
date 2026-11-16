// The spike compiles the protocol module's sources, as they are, for Windows with Kotlin/Native, and links one EXE.
// The generated tables are read from the protocol module's build folder: run :protocol:compileKotlin in android/ first.

plugins {
    kotlin("multiplatform") version "2.4.21"
}

kotlin {
    mingwX64 {
        binaries {
            executable {
                entryPoint = "io.github.omegadestroyer.pairfree.spike.main"
                baseName = "PairFreeSpike"
            }
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir("../../android/protocol/src/main/kotlin")
            kotlin.srcDir("../../android/protocol/build/generated")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            }
        }
    }
}
