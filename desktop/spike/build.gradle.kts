// The phase 0 spike (RESULTS.md): one EXE that links the protocol module, opens a window and reads earbuds over Windows' Bluetooth.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    mingwX64 {
        binaries {
            executable {
                entryPoint = "io.github.omegadestroyer.pairfree.spike.main"
                baseName = "PairFreeSpike"
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
