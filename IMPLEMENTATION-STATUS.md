# Off v3.1 implementation status

## Fixes in this build
- **Video export failing** — the conversion engine now probes the input for an audio stream,
  maps the first audio track explicitly, and tries `libmp3lame` then `libshine` before giving up.
  Real FFmpeg output is surfaced in the failure message instead of a generic string.
- **Raw JSON shown as the artist** — the JioSaavn client now reads `artists.primary` (an array of
  artist objects) properly, so titles/artists/albums are clean text.
- **Translucent chrome** — the top app bar and the bottom cluster now sit on gradient scrims over
  an edge-to-edge layout, so content scrolls beneath them instead of being cut off.
- **Full-screen player** — rebuilt: larger artwork with a shadow, gradient backdrop, animated
  play/pause glyph, source/quality badge, and a swipe-down-to-close gesture.
- **Mini player** — swipe it downwards to stop playback and dismiss it.
- **Downloads** — the status-bar notification now has Pause/Resume and Cancel actions, and the
  Library shows the same controls with live progress.
- **Settings persistence** — bitrate, speed, volume, fades, reduced-motion and source chips are
  saved and restored across page changes and app restarts.
- **Animation polish** — springy floating nav bar with press feedback, animated bitrate chips,
  and slide + fade + scale page transitions.
- **Search** — rebuilt around a "top result" card, per-source sections, source/quality badges,
  duration, recent searches, and clearer loading/empty states.

## Still to do
- Playlists, favourites and a persistent queue.
- Lyrics, equaliser, sleep timer.
- Signed release build (the CI artifact is intentionally unsigned).
- Device/API test pass — search, playback, download and conversion have not been run on a real
  device or emulator in this environment.

## Validation
- Built with the GitHub Actions workflow (`assembleRelease`, JDK 17, Gradle 8.9); local Gradle
  builds are not possible in this environment.
- NewPipe Extractor, the JioSaavn mirror and YouTube itself are third-party and can change
  without notice. Download only content you are authorised to save.
