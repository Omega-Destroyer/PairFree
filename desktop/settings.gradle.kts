// The desktop build (docs/DESKTOP.md): PairFree for Windows and macOS, built with Kotlin/Native. It is a build of its own, so the
// Android build (android/), which F-Droid reproduces, is not touched by it. It compiles the same protocol sources as Android does.
// Run it with Android's wrapper: from android/, `gradlew.bat -p ..\desktop <task>`.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
    // One list of versions for both builds.
    versionCatalogs {
        create("libs") {
            from(files("../android/gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "PairFreeDesktop"
include(":protocol", ":core", ":spike")
