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
This project uses `com.arthenica:ffmpeg-kit-full-gpl:6.0-2`. FFmpegKit upstream has been retired, and availability of its Android artifacts can change. Verify that the dependency is resolvable in your build environment before treating this project as release-ready. If it cannot be resolved, replace it with a maintained, legally compatible FFmpeg Android distribution and update the license notices. The build has not been executed or verified in this delivery.

## Build
1. Open the project in Android Studio or a compatible Android Gradle environment.
2. Use JDK 17.
3. Allow Gradle to sync.
4. Run `./gradlew assembleDebug` or `./gradlew assembleRelease`.
5. GitHub Actions runs `assembleRelease` and uploads the generated APK as an artifact.

The repository includes a Gradle wrapper. If the wrapper JAR is not present in your environment, generate it with a compatible Gradle installation (`gradle wrapper --gradle-version 8.9`) before building.

## Privacy
Conversion is designed to run locally. No account or upload server is included. The app does not require the INTERNET permission in its manifest; Gradle needs network access to download build dependencies.
