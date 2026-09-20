plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.motionstudio.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.motionstudio.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        // Your project uses OpenGL ES 2.0 + 3.0 features.
        // ARM64 and ARMv7 cover every modern Android device.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        // Not using Compose — traditional Views only.
        compose = false
        buildConfig = true
    }

    // The project has no XML layouts — everything is built in Kotlin.
    // Keep this false so resources aren't searched for at build time.
    androidResources {
        // Leave defaults; the manifest and mipmaps still get packaged.
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
            )
        }
    }
}

dependencies {
    // --- Kotlin + Coroutines ---
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // --- AndroidX (minimal — the app is Activity-based, not AppCompat) ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.lifecycle.runtime)

    // --- ML Kit: on-device AI ---
    // Each of these backs one feature in MotionStudioAiSuite.kt.
    // Comment out any you don't want to ship — each one adds ~2-5 MB.
    implementation(libs.mlkit.segmentation.selfie)     // Background removal
    implementation(libs.mlkit.pose.detection.accurate) // Auto-reframe
    implementation(libs.mlkit.object.detection)        // Object tracking
    implementation(libs.mlkit.image.labeling)          // Content tagging
    implementation(libs.mlkit.face.detection)          // Face boxes
    implementation(libs.mlkit.text.recognition)        // OCR

    // --- No media dependencies needed ---
    // Video decoding uses android.media.MediaCodec from the framework.
    // Audio uses android.media.MediaPlayer.
    // No ExoPlayer, no Media3, no FFmpeg.
}
