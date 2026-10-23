plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    // Chaquopy runs yt-dlp on-device (Python). 16.0.0 supports AGP 8.6-8.8.
    id("com.chaquo.python") version "16.0.0" apply false
}
