# Off v3.0 implementation status

## Added in this source package
- **Unified music search** across JioSaavn and YouTube with per-source results, source filter
  chips and a debounced query.
- **YouTube source** via NewPipe Extractor v0.26.3 (JitPack) with a plain `HttpURLConnection`
  downloader and just-in-time audio-stream resolution.
- **JioSaavn source** with a multi-mirror fallback list.
- **Media3 playback** in a `MediaSessionService`: background playback, media notification,
  lock-screen / headset controls.
- **Full-screen player** with artwork, seek bar, transport and source badge.
- **Floating mini player** that persists above the navigation bar on every screen.
- **Download service** running as a `dataSync` foreground service with a determinate progress
  notification in the status bar, saving to `Downloads/Off` via MediaStore.
- **Floating Material 3 navigation bar** (pill shape, animated selection).
- **Premium theme**: expanded Material 3 colour roles, tuned type scale, larger corner radii.
- Library screen now shows both downloads (with live progress) and saved conversions.
- Runtime `POST_NOTIFICATIONS` request; new manifest permissions for media playback and data-sync
  foreground services.

## Still to do
- Playlists, favourites and a persistent queue (currently a single in-memory queue).
- Word-synced lyrics, equalizer, sleep timer, Android Auto.
- Signed release build (the CI artifact is intentionally unsigned).
- A device/API test pass: playback, download and search have not been executed on a real device
  or emulator in this environment.

## Validation
- Local Gradle/Android SDK builds are not possible in this environment; the GitHub Actions
  workflow (`assembleRelease`, JDK 17, Gradle 8.9) is the build of record.
- NewPipe Extractor, the JioSaavn mirror and YouTube itself are third-party and can change
  without notice. Download only content you are authorised to save.
