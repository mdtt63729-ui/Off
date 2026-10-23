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

## v3.3.0 — library, queue and equalizer
- **Favourites**: heart in the full player and on every search result; persisted.
- **Playlists**: create, add, remove, delete; "Add to playlist" in the player and the search menu;
  playlist detail with "Play all". Persisted.
- **Persistent queue**: the queue and current index are saved, so the last track comes back ready
  (paused) after the app is closed. "Play next" and "Add to queue" from the search menu, plus a
  queue pane in the full player.
- **Equalizer**: Android's own Equalizer effect attached to the player's audio session, with a
  pane in the full player (band sliders + presets). Hides itself if the device offers no session.
- **Library** screen now has Favourites / Playlists / Files tabs.
- **CI signing**: the workflow signs the release APK when KEYSTORE_BASE64 / KEYSTORE_PASSWORD /
  KEY_PASSWORD / KEY_ALIAS secrets are set, and uploads both signed and unsigned artifacts.
- (v3.2.0) Synced lyrics from LRCLIB with the karaoke line sweep, and the BitChord-style
  full-screen and mini players.

## v3.4.0 — download video from a link
- **New "Video" tab**: paste a YouTube link (watch / youtu.be / shorts / live / embed, or a bare
  id) and download it.
- **Resolution**: NewPipe Extractor locally first, with the **Piped API** (the backend LibreTube
  is built on, several community instances tried in order) as the fallback.
- **Muxed renditions** (already contain audio) are saved straight out. **Video-only** renditions
  are paired with the best audio stream and joined with FFmpeg — `-c copy` first, and if the
  containers disagree it re-encodes just the audio to AAC.
- **Progress notification** in the status bar with a Cancel action; finished videos land in
  `Movies/Off`.
- New `video` package: `VideoModels`, `PipedApi`, `VideoRepository`, `VideoDownloadService`,
  `VideoDownloadScreen`.

## v3.5.0 — text colour, real-time editor
- **Fixed: every Text without an explicit colour rendered black.** Replacing Scaffold with a plain
  Box in v3.1 dropped the Surface that sets `LocalContentColor`, so headings ("Make audio your
  own.", "Recent searches", "No favourites yet", "Download a video", "Volume · 100%") were black
  on black. `OffTheme` now wraps the app in a Surface with `contentColor = onBackground`.
- **Editor is now a real editor**: a live preview player (ExoPlayer) bound to the selected video.
  Play/pause, and speed and volume are applied to the running player as you move the sliders, so
  you hear the change immediately. The trim range is enforced during preview (it loops inside it).
- **Real waveform**: FFmpeg renders the actual peaks (`showwavespic`) to a PNG the editor shows,
  instead of the placeholder bars (kept as a fallback).
- **Page padding**: pages without the navigation bar no longer reserve its height, so the editor
  is not cut off and does not end in a large empty gap.

## v3.6.0 — video card, audio downloads, real LoadingIndicator, nav bar
- **Nav bar fixed**: the selected pill used to be drawn per item, so mid-switch TWO pills were
  on screen and their labels crossed over each other (the garbled "Studio/Search"). There is now
  a single indicator whose offset and width come from the same animated item widths, so it tracks
  the items exactly.
- **Video card redesigned**: the thumbnail is 16:9 across the card, the video's description sits
  where the quality chips were, and the quality chips are below the description.
- **Audio downloads**: "Download as → Video / Audio". Audio offers the original M4A plus MP3 at
  320 and 192 kbps; MP3 is re-encoded with `libmp3lame` and saved to `Music/Off`.
- **Real LoadingIndicator**: Compose Material3 pinned to 1.4.0 so the Fetch button shows the
  M3 Expressive `LoadingIndicator` (the morphing shape), not a circular spinner.

## v3.7.0 — editor screen rebuilt to the Radhe design
- The audio editor screen now follows the Radhe Audio Editor layout: a 16:9 header card (a real
  frame pulled from the selected video), the 0:00 / current / total time row with the current time
  in the accent colour, a rounded-bar waveform that fills in as it plays, the seek slider, four
  square shortcut cards, a 64dp circular play button with prev/next, the volume row and a
  full-width export button.
- The shortcuts are wired to real actions (Trim = full range, Speed = 1.00x, Fade = 2s each,
  Reset = everything) rather than being decorative.
- Live preview playback is kept, so speed, volume and the trim range still apply as you move them.
- The FFmpeg waveform image was dropped in favour of the design's bar waveform, driven by the real
  playback position.

## v3.8.0 — skeleton loading and fade-in
- New `ui/components/Skeleton.kt`: `SkeletonBox` (a shimmering sweep), `SkeletonLine`, and
  `SkeletonImage`, which shows a skeleton until the picture has actually loaded and then fades it
  in over ~400ms. A failed image falls back to an icon instead of shimmering forever.
- **Video screen**: tapping Fetch now shows a full skeleton card — a 16:9 block, title and
  subtitle lines, a description block and chip placeholders — and the real card fades in when the
  data arrives. The thumbnail itself shimmers until the image loads, then fades in.
- **Search**: cover art in every result row shimmers and fades in, and a running search shows five
  skeleton rows instead of a bare spinner.
- **Library**: favourite/playlist rows use the same image treatment.
- **Editor**: the 16:9 header frame shimmers until the frame has been pulled out of the video,
  then fades in.

## v3.9.0 — editor per the supplied UI, plus player and transition fixes
- **Editor rebuilt to the supplied Off Audio Editor UI**: file row, WAVEFORM label, the canvas
  waveform, playback slider, −15s / play / +15s transport, TOOLS row with Trim / Speed / Fade /
  Reset cards (each with a subtitle), and an export card. The bitrate choice lives in that card.
- **Trim directly on the waveform**: drag either edge of the selection. The selected stretch is
  drawn in the accent colour and everything outside it in grey, with handles and a playhead.
- **Trim / Speed / Fade / Reset are real screens**, not decoration:
  - Trim: the same selection waveform, Select all, Save trim.
  - Speed: 0.25×–2.0×, quick presets, Preserve pitch, Reset / Apply. Speed and pitch apply to the
    live preview.
  - Fade: an envelope preview, fade in/out sliders, Linear / Smooth / Exponential curves that are
    passed to FFmpeg's `afade` (tri / qsin / exp), Clear / Apply.
  - Reset: the confirmation screen; resets trim, speed, fades and volume.
- **Fade in/out now visibly work**: the envelope updates as the sliders move, and the curve
  reaches the export pipeline.
- **Dismissing the mini player now clears the saved queue**, so the track no longer comes back the
  next time the app is opened.
- **Tapping the mini player opens the full player again**: the tap and the swipe-down dismiss are
  now two separate gesture detectors, so the drag can no longer swallow the tap.
- **Page transitions**: the exit is the mirror of the entrance, and the direction follows depth —
  going deeper slides forward, closing a page slides back the other way.

## v3.10.0 — bouncy chips everywhere, seamless nav bar
- New `ui/components/BouncyChip.kt`: `BouncyChip` and `BouncyChipWide`. Selecting one dips
  slightly, pops ~7% past its resting size and lets a soft spring settle it back, with the colours
  cross-fading on their own spring and the icon settling a beat behind. A plain chip's colour change
  reads as nothing happening; this reads as a switch being thrown.
- Every selectable chip in the app now uses it: the **Video / Audio** switch and the quality and
  audio-format chips on the video screen, the source chips in search, the equalizer presets, the
  library tabs, and the bitrate, fade-curve and speed-preset chips in the editor.
- **Nav bar**: a single animated value — the fractional position of the selection — now drives the
  slot widths, the label fades and the indicator together, so nothing can drift a frame apart. The
  row always fills the bar, so the bar no longer shifts sideways as one slot opens and another
  closes, and labels fade and slide in behind the arriving pill.
