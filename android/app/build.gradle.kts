plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.omegadestroyer.soundpeter"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.omegadestroyer.soundpeter"
        // Android 12: BLUETOOTH_CONNECT is the only permission (docs/DESIGN.md).
        // Going lower would also need the old BLUETOOTH permission.
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
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
    debugImplementation(libs.compose.ui.tooling)
}
