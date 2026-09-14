plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.natron.Natron"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.natron.Natron"
        minSdk = 24
        targetSdk = 35

        versionCode = 1
        versionName = "2.5.0-android"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }

        debug {
            isDebuggable = true
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
}
