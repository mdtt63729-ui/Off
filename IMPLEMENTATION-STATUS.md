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

## v3.11.0 — sliders slide smoothly
Three separate things made the sliders feel notchy; all three are fixed.
- **The `steps` were removed.** The speed slider snapped in 6 steps and the fade sliders in 9,
  which is exactly the "it jumps instead of sliding" feel. They are continuous now.
- **The editor's seek slider no longer fights the playback ticker.** The position ticker was
  overwriting the thumb while it was being dragged. The slider now keeps its own value for as long
  as it is held, seeks straight into the player (no effect round trip), and the time readout
  follows the finger.
- **The equalizer band sliders no longer round-trip through the controller** on every frame; the
  value is held locally while dragging.
- The volume control is back in the editor, under the transport.
- `atempo` is now chained, so the speed slider's full 0.25x-2x range actually works; a single
  `atempo` only covers 0.5x-2x, so 0.25x used to do nothing.

## v4.0.0 — on-device yt-dlp, so links from almost anywhere work
NewPipe Extractor and the Piped API are still there; yt-dlp is added alongside them as the
catch-all for every site they cannot read.

- **Engine order.** YouTube (and the sites NewPipe knows natively) still goes through NewPipe
  first, then Piped, then yt-dlp. Anything else — Instagram, TikTok, Facebook, X, Reddit, Vimeo,
  Dailymotion, Twitch VOD, SoundCloud and the rest of yt-dlp's list — goes to yt-dlp. Keeping
  NewPipe first matters: yt-dlp's YouTube extraction needs an external JavaScript runtime, which
  is the riskiest part of a yt-dlp-only setup, and NewPipe already covers YouTube.
- **On-device, no backend.** `app/src/main/python/bridge.py` runs yt-dlp through Chaquopy. No
  Off-owned server and no third-party extraction API; requests go straight to the target site.
- **Bundled ffmpeg.** `scripts/build-ffmpeg-android.sh` builds a minimal LGPL ffmpeg + ffprobe
  from pinned source (n7.1.1) for arm64 in CI and ships them as `libffmpeg.so` / `libffprobe.so`,
  so yt-dlp can merge separate video and audio streams. Legacy jniLibs packaging is what lets the
  app execute them.
- **Downloads.** A yt-dlp job runs on a worker thread while the service polls its progress file, so
  the same notification and download list show live percentage, speed and ETA. MP3 conversion for
  yt-dlp downloads reuses the bundled ffmpeg-kit.
- **Share into Off.** `ACTION_SEND` text/plain is accepted: sharing a link from any app opens the
  downloader and fetches it immediately.
- **ARM64 only.** Chaquopy builds a Python runtime per ABI and the bundled ffmpeg is arm64, so the
  app now ships `arm64-v8a` alone.
- Only download what you own or have permission to save.

## v4.0.1 — the launcher icon is adaptive now
- The icon was a **legacy bitmap** (`mipmap-*/ic_launcher.png`), so Android treated it as a square image and
  drew its own **white background** behind it on the Android 12+ splash screen. That is the white box with a
  sharp-cornered icon inside it.
- Added a proper **adaptive icon**: `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`, with
  `drawable/ic_launcher_background.xml` (the original navy gradient, sampled from the old artwork) and
  `drawable/ic_launcher_foreground.xml` (the tick, drawn inside the 108dp canvas's safe zone so no launcher
  mask can clip it).
- The theme also forces `windowSplashScreenBackground` to Off's background and
  `windowSplashScreenIconBackgroundColor` to transparent, so no OEM skin can put that white box back.
- Result: the launcher masks the icon to the device's own shape and Off's own background fills it, so the
  icon sits fully inside a rounded-corner box. This also fixes the icon on the home screen.

## v4.0.2 — dragging one trim edge no longer moves the other
- **Symptom**: trim the end of the selection, then grab the start handle, and the end jumped back to
  where it was before — e.g. End 00:20 became 00:24 the moment Start was dragged.
- **Cause**: the waveform's `pointerInput` handler is created once and is *not* re-created as the trim
  values change. It read `trimStart`/`trimEnd` straight from its parameters, so it was working with the
  values captured on the first composition. Moving the start handle called
  `onSelect(newStart, staleTrimEnd)` — which wrote the stale end back over the current one.
- **Fix**, three parts:
  1. The handler now moves **only the edge being dragged** (`onStartChange` / `onEndChange`), so it can
     never write the other edge at all.
  2. Clamping moved to `MainActivity`, next to the state, where `trimStart`/`trimEnd`/`durationSec` are
     read from Compose state and are therefore always current.
  3. `rememberUpdatedState` keeps the drag-start hit test (which edge is nearest the finger) current, and
     the "which handle am I holding" flag is now a plain array rather than Compose state, so no
     recomposition happens mid-drag.
- Applies to both the editor waveform and the Trim screen, which share the component.

## v4.0.3 — the video screen's switch animates as one piece
- **The list under the switch swapped instantly.** Changing Video/Audio replaced the whole block
  ("Quality / 932p" ↔ "Audio format / M4A") in a single frame while the chips above were still
  cross-fading, which read as a jump. It is now an `AnimatedContent`: the outgoing list fades and
  slides up while the incoming one fades and slides in, and the container's height animates with it
  (`SizeTransform(clip = false)`).
- **The Download button's label swapped instantly too** — "Download 932p" → "Download M4A · original"
  in one frame. It cross-fades now, and the button keeps a fixed height so it cannot jump.
- **The chips no longer sit permanently enlarged.** A selected chip used to stay 7% bigger for as long
  as it was selected, so the two chips were always at different sizes and the bigger one could overlap
  its neighbour. The pop is now a transient bounce: press dips it, selecting bounces it past its size
  and it settles straight back to 1.0.
- Note on the grey circle with the ring that shows up at the touch point: that is Android's **"Show
  taps"** developer option drawing over the app, not something Off renders — it is drawn outside the
  chip's clip, which an in-app ripple could never be. Turn it off in Settings → Developer options.

## v4.1.0 — downloads that survive, and pause/resume
- **The crash.** Tapping Download on a yt-dlp link killed the app process. The yt-dlp job runs on a
  worker thread, and an exception escaping that thread takes the whole process with it — it is not
  catchable from the code that started it. That body is now wrapped, `YtDlpEngine` never throws from
  any entry point, `runDownload` catches `Throwable` rather than `Exception`, `onStartCommand` is
  guarded, and failing to enter the foreground is reported and handled instead of swallowed (that
  failure is exactly what makes Android kill an app a few seconds after a download starts).
- **An error log, on the device.** `CrashLog` records the last failure — uncaught ones and handled
  ones alike — to a file in the app's private storage. Nothing is sent anywhere. Settings →
  Diagnostics → **Last error log** shows it, with a Share button. If anything still goes wrong, that
  text is what I need.
- **Pause and resume.** Tap a downloading item's thumbnail to pause it, tap again to resume. Long
  press any item for Pause/Resume, Open (when finished) and Delete. Pausing keeps the half-finished
  file, and resuming continues from those bytes — HTTP downloads send a `Range` request and yt-dlp
  picks up its own `.part` files.
- **The list moves instead of jumping.** The fetched video card now expands into place
  (`AnimatedContent` with `expandVertically`), so the download list below it slides down smoothly,
  and each new download fades and expands in.
- Video downloads live in the Video section only; the Library is untouched, as before.

## v4.1.1 — messages sit above the screen, not behind it
- The snackbar was the **first** child of the root `Box`, and in a `Box` the later children are
  drawn on top — so every message rendered *underneath* the page content, the top bar and the
  navigation bar. A message could be half-hidden behind a card, which is what the screenshot showed.
- It is now declared **last** inside that `Box`, after the pages, the top bar and the nav bar, so it
  is drawn above all of them. Position and padding are unchanged.

## v4.2.0 — Off is a downloader and an editor, not a player
- **The full-screen player and the mini player are gone.** Off no longer plays anything in-app, so
  neither is mounted any more, and the app no longer connects to the playback service at startup.
- **Search is download-only.** The play button on every result row and on the top result is gone; the
  row, the top card and its button all download. "Play next" and "Add to queue" are gone from the row
  menu — there is no queue to add to.
- **The Library follows suit.** A favourite or a playlist track now downloads rather than plays
  ("Download all" instead of "Play all"), and the row button is a download arrow. Saved files still
  open in whatever app the phone uses, as before.
- The bottom inset no longer reserves room for a mini player, so sub-pages do not end with a gap.
- The player classes (`PlayerController`, `PlayerUi`, `PlaybackService`) are left in the project
  unused rather than deleted, so playback can be brought back without rewriting it.

## v5.0.0 — the player is gone for good, and there is a new home screen
- **The player code is deleted, not just unhooked.** The whole `player` package is gone —
  `PlayerController`, `PlayerUi`, `PlaybackService`, `EqualizerController`, `EqualizerPane` — along
  with the `PlaybackService` entry and the media-playback permission in the manifest, and the
  `media3-session` / `media3-ui` dependencies. `media3-exoplayer` stays, because the editor's live
  preview uses it.
- **Other dead code removed too**: `WaveformGenerator` (superseded by the drawn waveform), and the
  editor helpers `EditorCard`, `EditorTool`, `AnimatedBitrateChip` and `WaveformPreview`, none of
  which were reachable any more.
- **New home screen**, built to the supplied design: the Off header with a Settings button, the
  "Download anything." prompt, the URL card with a Go button, "Your downloads" with a newest/oldest
  toggle, the download rows with a 16:10 thumbnail, duration badge, source, size and date, a
  three-dot menu (Play, Details, Edit audio, Delete), the "Saved locally" footer, and the delete and
  details dialogs. The rows are the **real downloads**, not sample data: the URL bar's Go button
  hands the link to the downloader, Play opens the saved file, Edit audio opens it in the editor,
  and Delete removes the download and its partial file.
- The download model now carries the source site, duration and final size, so those rows can show
  them.
- The navigation bar's first tab is now labelled **Home** instead of Studio.

- `AddToPlaylistDialog` moved out of the deleted player package into `library/`, since playlists are a library feature.

## v5.0.1 — the search list runs to the bottom of the screen
- The search screen was inset by the whole bottom padding, and its results live in a bounded
  `LazyColumn` (`Modifier.weight(1f)`). That combination left a **fixed dead band** between the end
  of the list and the navigation bar, with the last row cut off in the middle of its card — it read
  as an opaque slab sitting over the content.
- Now only the **top** inset is applied to the screen. The bottom inset is handed to the list as
  `contentPadding`, so the results run to the bottom of the display and scroll **under** the floating
  navigation bar, and the last row can still be scrolled clear of it. The band is gone.
- The search notice is padded so it does not sit behind the bar either.
- The other screens were already scrollable columns, where a bottom inset is part of the scroll and
  does not create a band, so they are unchanged.

## v5.0.2 — one header on the home screen, not two
- The home screen had its **own** header (the app-icon tile, "Off", "Video downloader" and a
  Settings button) while the app's top bar above it already showed "Off", a page subtitle and the
  same Settings button — so the screen opened with two titles and two gears.
- The home screen's header row is gone. Its information moved to the top bar, which now reads
  **"Off · Video downloader"** on the home page, so nothing was lost.
