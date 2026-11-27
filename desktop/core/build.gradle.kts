// The app's shared logic for the desktop apps: the very sources Android compiles in android/core (the brands, the connection, the
// equalizer's logic), built as Kotlin Multiplatform for Windows. Nothing is copied. `compileKotlinMingwX64` is also the check that
// those sources stay free of Android and JVM-only calls.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    mingwX64()

    sourceSets {
        commonMain {
            kotlin.srcDir("../../android/core/src/main/kotlin")
            dependencies {
                api(project(":protocol"))
                api(libs.coroutines.core)
                api(libs.kotlinx.io.core)
                api(libs.kotlinx.serialization.json)
            }
        }
    }
}
