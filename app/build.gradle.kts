plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // yt-dlp runs on-device through Chaquopy, so no Off-owned backend is needed.
    id("com.chaquo.python")
}

android {
    namespace = "com.off.audioconverter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.off.audioconverter"
        minSdk = 26
        targetSdk = 35
        versionCode = 24
        versionName = "5.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        // Chaquopy builds a Python runtime per ABI, and the bundled ffmpeg is
        // arm64-only, so the app ships arm64-v8a alone to keep the APK sane.
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        // Required by NewPipe Extractor on minSdk < 33 (java.nio / java.time APIs).
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        // The bundled ffmpeg/ffprobe are shipped as libffmpeg.so / libffprobe.so.
        // Legacy packaging extracts native libraries into the app's library
        // directory, which is the only place an app may execute its own binaries.
        jniLibs {
            useLegacyPackaging = true
        }
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        resources.excludes += "/META-INF/DEPENDENCIES"
        resources.excludes += "/META-INF/LICENSE*"
        resources.excludes += "/META-INF/NOTICE*"
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"
        // CI passes an absolute interpreter path in BUILD_PYTHON; locally it
        // falls back to the python3.11 command.
        buildPython(System.getenv("BUILD_PYTHON") ?: "python3.11")
        pip {
            // yt-dlp is the extractor for every site NewPipe Extractor does not
            // cover natively. Pin it so a rebuild is reproducible.
            install("yt-dlp==2026.10.21")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Material Components: provides the M3 Expressive LoadingIndicator view.
    implementation("com.google.android.material:material:1.13.0")

    // Media3 / ExoPlayer: background playback, media session, mini + full player.
    implementation("androidx.media3:media3-exoplayer:1.5.1")

    // YouTube search + audio stream resolution (drop-in for the retired extractor).
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.3")

    // Maintained LGPL FFmpegKit fork; includes libmp3lame for MP3 encoding.
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.9")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
