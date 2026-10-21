# Product Requirements Document (PRD): Off
**Version:** 1.0  
**Platform:** Android  
**Application type:** Native offline MP4-to-MP3 converter  
**Language:** Kotlin  
**UI:** Jetpack Compose + official AndroidX Material 3 components  
**Release target:** GitHub Actions unsigned release APK

## 1. Product overview

Off is a native Android application that extracts audio from a locally selected video and writes an MP3 file to a user-selected destination. Conversion should run on the device and must not upload media to a server.

### Goals
- Convert a supported local video into MP3 without requiring internet access during conversion.
- Provide a coherent, accessible, dark Material 3 interface.
- Keep long-running conversion work off the UI thread.
- Save output using Android's Storage Access Framework.
- Produce a reproducible project and CI build artifact.

### Non-goals for v1
- Cloud conversion, account/login, advertising, analytics, online media downloading.
- Batch queue, waveform editing, trimming, advanced metadata editor, cloud sync.
- Guaranteeing support for every possible video/audio codec combination.

## 2. Users and primary flow

### Primary flow
1. Open Off.
2. Tap **Choose video**.
3. Select a video using the system picker.
4. Review the selected file and choose an MP3 bitrate.
5. Tap **Convert to MP3**.
6. Choose a destination/name in the system save dialog.
7. Observe conversion state.
8. On success, open or share the saved file.

### User stories
| Story | Acceptance criteria |
|---|---|
| Select a video | The Android picker opens and returns a content URI without broad storage access. |
| Configure output | User can select 128, 192, 256, or 320 kbps. |
| Convert offline | The conversion engine reads local media and writes output locally; no upload endpoint exists. |
| Show progress | UI distinguishes preparing, converting, saving, completed, cancelled, and failed states. |
| Cancel | Cancellation is offered while conversion is active; partial temporary output is not shown as complete. |
| Save output | User chooses a destination via `ACTION_CREATE_DOCUMENT` / Storage Access Framework. |
| Handle failures | Unsupported input, no audio stream, write failure, and encoder errors show understandable messages. |
| Accessible UI | Controls have meaningful labels, usable touch targets, and readable contrast. |

## 3. Feature scope and priority

| Feature | Value | Effort | Priority | Version |
|---|---|---|---|---|
| Native Material 3 home screen | High | Medium | Must | 1.0 |
| System video picker | High | Low | Must | 1.0 |
| Bitrate selector | High | Low | Must | 1.0 |
| Local MP3 conversion engine | High | High | Must | 1.0 |
| Save dialog and output writing | High | Medium | Must | 1.0 |
| Conversion state/error handling | High | Medium | Must | 1.0 |
| Cancel conversion | High | Medium | Must | 1.0 |
| Settings | Medium | Low | Should | 1.0 |
| Persistent conversion history | Medium | Medium | Should | 1.1 |
| Batch conversion | Medium | High | Could | 1.2 |
| Trim audio before conversion | Medium | High | Could | 1.2 |
| Embedded metadata editor | Low | Medium | Could | Later |

## 4. Design system: strict Material 3

### Rules
- All application UI must be built with Kotlin and Jetpack Compose.
- Use official Material 3 components wherever an equivalent exists: `Scaffold`, `TopAppBar`, `Card`, `Button`, `FilledTonalButton`, `OutlinedButton`, `IconButton`, `FilterChip`, `AlertDialog`, `SnackbarHost`, `CircularProgressIndicator`, `LinearProgressIndicator`, `ListItem`, `Switch`, and `ModalBottomSheet`.
- No HTML, CSS, JavaScript UI, WebView, or generic web-like mock components.
- Do not use emojis as action icons. Use Material icons/vector assets with content descriptions.
- Define colors, typography, shapes, and component defaults centrally in `ui/theme`.
- Provide loading, empty, disabled, selected, success, and error states.
- Respect system font scaling and reduced-animation preferences.
- Animations should be subtle and purposeful, not block interaction.

### Suggested dark palette
| Token | Value |
|---|---|
| Background | `#111318` |
| Surface | `#191B20` |
| Elevated surface | `#272A31` |
| Primary | `#FFB59E` |
| On primary | `#561F0D` |
| On background | `#E3E1E9` |
| Secondary text | `#C7C5D0` |
| Error | Material 3 error role |

Colors are starting values; verify contrast and tonal roles before release. Support system dark/light theme in a later iteration if required; dark theme is the v1 default.

## 5. Screens and detailed requirements

### 5.1 Home
- Top app bar with app name “Off” and Settings action.
- Prominent card explaining local video-to-audio conversion.
- Primary **Choose video** button.
- Selected-video summary when a video has been picked.
- Recent output section or a clean empty state.
- No login, ads, or unnecessary navigation rail/drawer.

### 5.2 Video selection
- Use Android Photo Picker or Storage Access Framework.
- Request only access necessary to read the selected URI.
- Handle cancellation without displaying an error.
- If the URI becomes unreadable, explain that the user should select the file again.

### 5.3 Video details and options
- Display file name and best-effort metadata (duration, size, MIME type).
- Show MP3 as the output format.
- Provide single-choice bitrate chips: 128, 192, 256, 320 kbps.
- Validate and sanitize the proposed output filename.
- Provide **Convert to MP3** and a secondary change-video action.
- Explain that higher bitrates may increase file size and cannot restore source audio quality.

### 5.4 Destination picker
- Use Android `ACTION_CREATE_DOCUMENT` for `audio/mpeg`.
- Suggest a `.mp3` filename.
- Handle cancellation and write permission errors.
- Do not overwrite a destination without the platform's confirmation flow.

### 5.5 Conversion progress
- States: Idle, Preparing, Converting, Saving, Completed, Cancelled, Failed.
- Show real progress only if the chosen engine provides trustworthy progress data.
- Otherwise show an indeterminate Material progress indicator and a truthful status label.
- Offer Cancel while conversion is cancellable.
- Avoid running conversion work on the main thread.
- If work must continue after app backgrounding, evaluate a foreground service with a user-visible notification and current Android restrictions.

### 5.6 Result
- Show success only after conversion and output writing have completed.
- Display output filename and destination if available.
- Provide Open/Share actions when supported.
- Offer Convert another video.
- On failure, show a plain-language message and Retry where sensible.

### 5.7 Settings
- Default bitrate.
- Dark theme (v1 default).
- About, app version, privacy statement, and dependency license notices.
- Do not include settings that are not implemented.

### 5.8 History (future or minimal v1)
- If implemented in v1, persist only output URI, display name, timestamp, and status.
- A history entry is valid only while its file can still be accessed.
- Provide rename/share/delete actions only when they can be implemented safely.

## 6. Technical architecture

### Layers
- **UI:** Compose screens and reusable Material 3 components.
- **ViewModel:** screen state, user actions, coroutine lifecycle.
- **Domain:** conversion request and result models.
- **Data:** URI metadata and output destination access.
- **Conversion engine:** isolated interface so FFmpeg implementation can be replaced.
- **Navigation:** explicit destinations and safe back handling.

### Suggested project layout
```text
app/src/main/java/com/off/audioconverter/
  MainActivity.kt
  navigation/AppNavigation.kt
  ui/theme/Color.kt
  ui/theme/Theme.kt
  ui/theme/Type.kt
  ui/screens/HomeScreen.kt
  ui/screens/SettingsScreen.kt
  ui/components/
  domain/ConversionModels.kt
  conversion/AudioConversionEngine.kt
  conversion/FfmpegConversionEngine.kt
  data/MediaMetadataReader.kt
```

### Conversion engine contract
- Input: readable local content URI, bitrate, and output destination.
- Output: typed success/failure result.
- Cancellation must terminate the active conversion session when the library supports it.
- No raw user-supplied shell command concatenation. Escape/quote paths safely or use a library API that accepts argument arrays.
- Ensure output is not marked complete until the conversion process returns success and the output can be read.
- Clean up partial files after failure/cancellation.
- Validate input has an audio stream before conversion where practical.

### FFmpeg dependency risk
FFmpegKit upstream is retired. The selected artifact in this starter project may no longer be resolvable in all build environments. Before release, verify repository availability, Android ABI support, license terms, and transitive dependencies. If unavailable, replace it with a maintained, legally compatible Android FFmpeg distribution. Do not claim release readiness until a clean CI build and real-device conversion tests pass.

## 7. Data, privacy, and permissions
- Media processing is local by design.
- No media upload, account, analytics, or advertising SDK in v1.
- Prefer Photo Picker / SAF over broad media/storage permissions.
- Do not request microphone, contacts, location, or unrelated permissions.
- Internet permission should be omitted unless an explicitly documented feature requires it.
- Gradle build dependency download requires network during build; that is separate from offline app conversion.
- Include third-party license notices for the selected conversion engine.

## 8. Non-functional requirements
- UI remains responsive while converting.
- No false percentage progress.
- No unbounded whole-file buffering in RAM.
- Handles app background/foreground transitions predictably.
- Clear errors for unsupported codecs, missing audio, inaccessible URI, destination write failure, and cancellation.
- Minimum touch target follows Android accessibility guidance.
- Contrast, TalkBack labels, font scaling, and dark theme tested.
- Avoid unnecessary wake locks and battery-intensive polling.

## 9. Testing plan
- Unit tests for bitrate mapping, filename validation, and state transitions.
- Instrumentation tests for picker flow and screen navigation.
- Test valid MP4 with AAC audio, MP4 with another supported audio codec, video with no audio, corrupted input, very large input, inaccessible URI, destination cancellation, and storage write failure.
- Test conversion cancellation and partial-file cleanup.
- Test airplane mode after installation.
- Test on at least one low-memory Android device and one current Android version.
- Verify generated output using an independent media probe/player.
- Verify CI artifact exists and matches the expected version.

## 10. CI and release
- Use JDK 17 and pinned compatible Android Gradle Plugin/Gradle versions.
- Commit the Gradle wrapper scripts and wrapper JAR.
- GitHub Actions runs `assembleRelease`.
- Upload the generated APK as an artifact.
- Keep signing keys out of source control.
- Label output clearly as unsigned if it is not signed.
- A successful workflow is required before calling the APK build successful.

## 11. Definition of done
- Every implemented screen uses the native Material 3 design system.
- No web UI or WebView.
- Offline conversion works on a real device for tested input formats.
- Save/cancel/error flows work.
- No crash or frozen UI during conversion tests.
- License notices and privacy description are present.
- CI produces a downloadable APK artifact.
- Build logs and tested device/Android versions are documented.

## 12. Risks and open decisions
1. FFmpeg Android distribution availability and licensing must be checked before release.
2. Some MP4 files may contain unsupported audio codecs or no audio.
3. Background execution limits differ by Android version.
4. “Unsigned APK” is a build artifact, not necessarily directly installable.
5. Final package ID, icon, and app version should be confirmed before store publication.
