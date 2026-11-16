// Phase 0 of the desktop plan: a go/no-go spike, its own build so the Android build is untouched.
// Run from android/ with: gradlew.bat :protocol:compileKotlin, then gradlew.bat -p ..\desktop\spike linkReleaseExecutableMingwX64
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "PairFreeDesktopSpike"
