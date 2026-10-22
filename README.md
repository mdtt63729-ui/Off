# Off — offline audio studio + music player

Native Android app built with Kotlin, Jetpack Compose and Material 3.

## What it does
- **Search** across JioSaavn and YouTube from one box, with results merged and tagged by source.
- **Play** anything in a full-screen player, with a floating **mini player** that follows you
  across every screen. Playback runs in a Media3 `MediaSessionService` with a media notification,
  lock-screen and headset controls.
- **Download** tracks with a live progress notification in the status bar (foreground service).
- **Convert** a local video to MP3 with trim, speed, fades, volume and bitrate controls.
- **Library** of downloaded songs and saved conversions.
- Floating Material 3 navigation bar and a dark-first, premium theme.

## Architecture
```
com.off.audioconverter/
  MainActivity.kt                 app shell: screens, floating nav, mini player host
  conversion/AudioConversionEngine.kt   FFmpeg pipeline (trim/speed/fade/volume/bitrate)
  domain/ConversionModels.kt
  music/
    MusicModels.kt                Track / MusicSource / SearchResponse
    SaavnRepository.kt            unofficial JioSaavn catalogue client (multi-mirror)
    YouTubeRepository.kt          YouTube search + audio resolution via NewPipe Extractor
    MusicRepository.kt            fans one query out to every source
    MusicSearchScreen.kt          unified search UI
  player/
    PlaybackService.kt            Media3 MediaSessionService + ExoPlayer
    PlayerController.kt           app-wide MediaController + observable state
    PlayerUi.kt                   mini player + full-screen player
  download/
    DownloadStore.kt              observable download registry
    DownloadService.kt            foreground download + progress notification
  ui/theme/Theme.kt               Material 3 colour / type / shape system
  ui/components/FloatingNavBar.kt floating pill navigation bar
```

## Dependencies of note
- **Media3 / ExoPlayer** for playback, media session and notification.
- **NewPipe Extractor** (`com.github.teamnewpipe:NewPipeExtractor:v0.26.3`, via JitPack) for
  YouTube search and audio-stream resolution. It reads YouTube's public pages the way a browser
  would; it is not an official Google API and YouTube can change it at any time.
- **JioSaavn** is reached through a community API mirror (no official public API exists).
- **FFmpegKit** via the maintained fork `dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.9`
  (the original `com.arthenica:ffmpeg-kit-*` line was retired and removed from Maven Central).
- Core-library desugaring (`desugar_jdk_libs_nio`) is enabled because NewPipe Extractor needs
  `java.nio`/`java.time` on `minSdk 26`.

## Build
1. JDK 17, Android SDK platform 35.
2. `gradle --no-daemon assembleRelease` (GitHub Actions does this on every push).
3. The generated `app-release-unsigned.apk` is uploaded as a workflow artifact.

## Legal / responsible use
YouTube extraction and the JioSaavn mirror are third-party and unofficial. Download only content
you are authorised to save, and respect each service's terms of use and copyright.
