// PairFree for Windows: the one EXE (docs/DESKTOP.md). It holds the shared code of every brand, the toolkit and Windows' Bluetooth.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

// The program's icon (PairFree.ico, made by tools/gen_app_icon.py) goes into the EXE as a resource, compiled with the windres of the
// toolchain Kotlin/Native downloads, so Explorer and the taskbar show PairFree's icon instead of the default one.
val iconObject = layout.buildDirectory.file("icon/PairFree_res.o")
val compileIcon = tasks.register<Exec>("compileIcon") {
    val windres = File(System.getProperty("user.home"), ".konan/dependencies").listFiles().orEmpty()
        .filter { it.name.startsWith("msys2-mingw-w64") }
        .map { File(it, "bin/windres.exe") }
        .firstOrNull { it.exists() }
    inputs.files("PairFree.rc", "PairFree.ico")
    outputs.file(iconObject)
    onlyIf { windres != null }
    doFirst { iconObject.get().asFile.parentFile.mkdirs() }
    workingDir = projectDir
    commandLine(windres?.absolutePath ?: "windres", "-i", "PairFree.rc", "-O", "coff", "-o", iconObject.get().asFile.absolutePath)
}

kotlin {
    mingwX64 {
        binaries {
            executable {
                entryPoint = "io.github.omegadestroyer.pairfree.app.main"
                baseName = "PairFree"
                // A window program: no console opens with it.
                linkerOpts("-Wl,--subsystem,windows", "-lwinmm", "-lole32", iconObject.get().asFile.absolutePath)
                linkTaskProvider.configure { dependsOn(compileIcon) }
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
