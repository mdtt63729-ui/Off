# Off — Offline MP4 to MP3 Converter

Native Android application built with Kotlin, Jetpack Compose, and Material 3.

## What it does
- Pick a video from device storage using Android's system picker.
- Configure MP3 output bitrate (128/192/256/320 kbps).
- Convert locally with FFmpegKit.
- Save the resulting MP3 through Android's Storage Access Framework.
- View conversion status and basic app settings.
- Dark Material 3 visual design.

## Important build/dependency note
FFmpegKit upstream (the `com.arthenica:ffmpeg-kit-*` line) was retired in 2025 and its Maven artifacts were removed, so the original `com.arthenica:ffmpeg-kit-full-gpl:6.0-2` no longer resolves. This project now depends on the maintained drop-in fork `dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.9`, which keeps the same `com.arthenica.ffmpegkit` package and API. That build is LGPL-3.0 and includes `libmp3lame` (MP3 encoding). If you need `x264`/`x265` and your app is GPL-compatible, switch to the `-full-gpl` artifact instead.

## Build
1. Open the project in Android Studio or a compatible Android Gradle environment.
2. Use JDK 17.
3. Allow Gradle to sync.
4. Run `./gradlew assembleDebug` or `./gradlew assembleRelease`.
5. GitHub Actions runs `assembleRelease` and uploads the generated APK as an artifact.

The repository includes a Gradle wrapper. If the wrapper JAR is not present in your environment, generate it with a compatible Gradle installation (`gradle wrapper --gradle-version 8.9`) before building.

## Privacy
Conversion is designed to run locally. No account or upload server is included. The app does not require the INTERNET permission in its manifest; Gradle needs network access to download build dependencies.
