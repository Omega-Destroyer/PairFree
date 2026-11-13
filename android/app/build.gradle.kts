plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The languages built in: the English file, and each res/values-<code>/strings.xml as a language tag (ko, pt-BR), so a
// new language file is offered in the app without a line of code (docs/TRANSLATING.md).
val builtInLanguages = listOf("en") + file("src/main/res").listFiles().orEmpty()
    .filter { it.isDirectory && it.name.startsWith("values-") && File(it, "strings.xml").exists() }
    .map { it.name.removePrefix("values-") }
    .map { q -> if (q.startsWith("b+")) q.removePrefix("b+").replace('+', '-') else q.replace("-r", "-") }
    .sorted()

android {
    namespace = "io.github.omegadestroyer.pairfree"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.omegadestroyer.pairfree"
        // Android 12: BLUETOOTH_CONNECT is the only permission (docs/DESIGN.md).
        // Going lower would also need the old BLUETOOTH permission.
        minSdk = 31
        targetSdk = 37
        // x.y.z, and the code is x * 1,000,000 + y * 1,000 + z (CLAUDE.md, Versions; tools/test_version.py checks it).
        versionCode = 12000
        versionName = "0.12.0"
        buildConfigField("String[]", "LANGUAGES", builtInLanguages.joinToString(prefix = "{", postfix = "}") { "\"$it\"" })
    }

    // The release key lives outside the repo. Its file and passwords come from
    // ~/.gradle/gradle.properties (soundpeter.*); without them a release build is unsigned.
    val releaseStore = providers.gradleProperty("soundpeter.storeFile").orNull
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = providers.gradleProperty("soundpeter.storePassword").get()
                keyAlias = providers.gradleProperty("soundpeter.keyAlias").get()
                keyPassword = providers.gradleProperty("soundpeter.keyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Builds for testers (assembleDev): a debug build under its own id and name ("PairFree Dev",
        // src/dev), so it installs beside the released app without touching it or its settings. It is
        // signed with this computer's debug key, so later dev builds install over it only from here.
        // Shrunk as release is, so it carries no unused library code, but with readable names
        // (proguard-dev.pro) so its crash traces and logs need no mapping file.
        create("dev") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            matchingFallbacks += "debug"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro", "proguard-dev.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Android 13 and later list the app's languages under the phone's per-app language setting, from the same folders
    // (res/resources.properties names English as the language of res/values).
    androidResources {
        generateLocaleConfig = true
    }

    // A translation may lag behind the English file, or keep an entry the English file dropped: it still builds, and
    // an entry it lacks shows in English (docs/TRANSLATING.md). tools/test_translations.py checks what must hold.
    lint {
        warning += listOf("MissingTranslation", "ExtraTranslation")
    }

    // Plain JVM tests of the app's own tables (the names in the language file); nothing here needs a device.
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all { it.useJUnitPlatform() }
    }

    // The build tools add an encrypted list of dependencies to the APK's signing block for Google
    // Play. Nothing else reads it, and F-Droid rejects APKs that carry it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":protocol"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    // Switches Android's hidden-API protection off for BluetoothSocket only, to open the AirPods' channel (Apache-2.0; docs/apple/PROTOCOL.md section 7).
    implementation(libs.hiddenapibypass)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
