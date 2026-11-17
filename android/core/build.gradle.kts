import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// What the app does that is not a screen and not Android: the brands (their specs and drivers), the connection, the equalizer's
// logic and the capture analysis. Plain Kotlin, which the desktop build (desktop/core) compiles from these very folders for
// Windows and macOS (docs/DESKTOP.md). Keep it free of android.*, androidx.* and java.* imports; what the system provides
// comes in through the interfaces `Transport`, `TextStore`, `PlatformInfo` and `ControlLink`.

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    api(project(":protocol"))
    api(libs.coroutines.core)
    api(libs.kotlinx.io.core)
    api(libs.kotlinx.serialization.json)
}
