// PairFree for Windows: the one EXE (docs/DESKTOP.md). It holds the shared code of every brand, the toolkit and Windows' Bluetooth.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    mingwX64 {
        binaries {
            executable {
                entryPoint = "io.github.omegadestroyer.pairfree.app.main"
                baseName = "PairFree"
                // A window program: no console opens with it.
                linkerOpts("-Wl,--subsystem,windows")
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":windows"))
            }
        }
    }
}
