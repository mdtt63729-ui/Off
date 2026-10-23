package com.off.audioconverter

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.off.audioconverter.conversion.AudioConversionEngine
import com.off.audioconverter.conversion.ConversionOptions
import com.off.audioconverter.conversion.ConversionResult
import com.off.audioconverter.domain.ConversionStatus
import com.off.audioconverter.download.DownloadService
import com.off.audioconverter.download.DownloadStatus
import com.off.audioconverter.download.DownloadStore
import com.off.audioconverter.download.DownloadTask
import com.off.audioconverter.library.LibraryStore
import com.off.audioconverter.library.Playlist
import com.off.audioconverter.music.MusicRepository
import com.off.audioconverter.music.Track
import com.off.audioconverter.music.MusicSearchScreen
import com.off.audioconverter.player.FullPlayerOverlay
import com.off.audioconverter.player.MiniPlayer
import com.off.audioconverter.player.PlayerController
import com.off.audioconverter.ui.SettingsStore
import com.off.audioconverter.video.VideoDownloadScreen
import com.off.audioconverter.ui.FadeScreen
import com.off.audioconverter.ui.OffEditorScreen
import com.off.audioconverter.ui.ResetScreen
import com.off.audioconverter.ui.SpeedScreen
import com.off.audioconverter.ui.TrimScreen
import com.off.audioconverter.ui.components.BouncyChip
import com.off.audioconverter.ui.components.SkeletonBox
import com.off.audioconverter.ui.components.SkeletonImage
import com.off.audioconverter.ui.components.SkeletonLine
import com.off.audioconverter.ui.components.FloatingNavBar
import com.off.audioconverter.ui.components.NavItem
import com.off.audioconverter.ui.theme.OffTheme
import android.graphics.BitmapFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.UUID

private enum class OffPage {
    HOME, SEARCH, VIDEO, LIBRARY, EDITOR, TRIM, SPEED, FADE, RESET, PROGRESS, RESULT, SETTINGS
}
private data class SavedAudio(val id: String, val name: String, val uri: String, val createdAt: Long, val bytes: Long)

private val BOTTOM_PAGES = listOf(OffPage.HOME, OffPage.SEARCH, OffPage.VIDEO, OffPage.LIBRARY)

/**
 * Rough "how deep in the app" rank, used to pick the direction of the page
 * transition: going deeper slides forward, coming back slides the other way.
 */
private fun pageRank(page: OffPage): Int = when (page) {
    OffPage.HOME -> 0
    OffPage.SEARCH -> 1
    OffPage.VIDEO -> 2
    OffPage.LIBRARY -> 3
    OffPage.EDITOR -> 10
    OffPage.TRIM -> 11
    OffPage.SPEED -> 12
    OffPage.FADE -> 13
    OffPage.RESET -> 14
    OffPage.PROGRESS -> 20
    OffPage.RESULT -> 21
    OffPage.SETTINGS -> 22
}

class MainActivity : ComponentActivity() {

    /** A link shared into Off from another app, if there is one. */
    private val sharedLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        sharedLink.value = linkFrom(intent)
        addOnNewIntentListener { sharedLink.value = linkFrom(it) }
        setContent { OffTheme { OffApp(initialLink = sharedLink.value) } }
    }

    /** Pulls the first http(s) URL out of a shared message. */
    private fun linkFrom(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        return Regex("https?://\\S+").find(text)?.value
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OffApp(initialLink: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val engine = remember { AudioConversionEngine(context.contentResolver, context.cacheDir) }
    val settings = remember { SettingsStore(context) }

    var page by remember {
        mutableStateOf(if (initialLink.isNullOrBlank()) OffPage.HOME else OffPage.VIDEO)
    }

    // A link that arrives later (share while Off is already open) opens the downloader.
    LaunchedEffect(initialLink) {
        if (!initialLink.isNullOrBlank()) page = OffPage.VIDEO
    }
    var showPlayer by remember { mutableStateOf(false) }

    // Conversion state — restored from the settings store so it survives a restart.
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var durationSec by remember { mutableIntStateOf(0) }
    var bitrate by remember { mutableIntStateOf(settings.bitrate) }
    var trimStart by remember { mutableFloatStateOf(0f) }
    var trimEnd by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableFloatStateOf(settings.speed) }
    var fadeIn by remember { mutableFloatStateOf(settings.fadeIn) }
    var fadeOut by remember { mutableFloatStateOf(settings.fadeOut) }
    var volume by remember { mutableFloatStateOf(settings.volume) }
    var fadeCurve by remember { mutableStateOf(settings.fadeCurve) }
    var preservePitch by remember { mutableStateOf(settings.preservePitch) }
    var editorNotice by remember { mutableStateOf<String?>(null) }

    // The editor preview player lives above the pages, so it keeps playing while
    // the Trim / Speed / Fade screens are open.
    val editorPlayer = remember { ExoPlayer.Builder(context).build() }
    var editorPlaying by remember { mutableStateOf(false) }
    var editorPositionMs by remember { mutableLongStateOf(0L) }
    var editorPlayPauseRequest by remember { mutableIntStateOf(0) }

    // A real frame from the selected video, cropped 16:9 for the editor header.
    var artwork by remember { mutableStateOf<ImageBitmap?>(null) }
    var artworkResolved by remember { mutableStateOf(false) }

    LaunchedEffect(selectedUri) {
        artwork = null
        artworkResolved = false
        val target = selectedUri ?: return@LaunchedEffect
        artwork = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, target)
                val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                frame?.asImageBitmap()
            }.getOrNull()
        }
        artworkResolved = true
    }
    var status by remember { mutableStateOf(ConversionStatus.IDLE) }
    var conversionJobId by remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf("Ready when you are") }
    var pendingOutput by remember { mutableStateOf<File?>(null) }
    var currentOutputUri by remember { mutableStateOf<String?>(null) }
    var currentOutputName by remember { mutableStateOf<String?>(null) }
    var currentHistoryId by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<SavedAudio?>(null) }
    var showDeleteCurrent by remember { mutableStateOf(false) }
    var reducedMotion by remember { mutableStateOf(settings.reducedMotion) }
    val history = remember { mutableStateListOf<SavedAudio>().apply { addAll(loadHistory(context)) } }

    fun persistHistory() = saveHistory(context, history)

    LaunchedEffect(Unit) {
        LibraryStore.init(context)
        PlayerController.connect(context)
    }

    // Editor screens report what they just did through the app snackbar.
    LaunchedEffect(editorNotice) {
        val message = editorNotice ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        editorNotice = null
    }

    // ---- editor preview player ----
    DisposableEffect(editorPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                editorPlaying = isPlaying
            }
        }
        editorPlayer.addListener(listener)
        onDispose {
            editorPlayer.removeListener(listener)
            editorPlayer.release()
        }
    }

    LaunchedEffect(selectedUri) {
        val target = selectedUri
        if (target != null) {
            editorPlayer.setMediaItem(MediaItem.fromUri(target))
            editorPlayer.prepare()
        }
    }

    // Speed (and pitch) and volume follow the sliders live.
    LaunchedEffect(speed, preservePitch) {
        editorPlayer.setPlaybackParameters(
            PlaybackParameters(speed, if (preservePitch) 1f else speed)
        )
    }
    LaunchedEffect(volume) { editorPlayer.volume = volume.coerceIn(0f, 1.5f) }

    LaunchedEffect(editorPlayPauseRequest) {
        if (editorPlayPauseRequest == 0) return@LaunchedEffect
        if (editorPlayer.isPlaying) {
            editorPlayer.pause()
        } else {
            val startMs = (trimStart * 1000).toLong()
            val endMs = (trimEnd * 1000).toLong()
            val position = editorPlayer.currentPosition
            if (position < startMs || (endMs > startMs && position >= endMs)) {
                editorPlayer.seekTo(startMs)
            }
            editorPlayer.play()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            editorPositionMs = editorPlayer.currentPosition.coerceAtLeast(0L)
            val endMs = (trimEnd * 1000).toLong()
            if (editorPlaying && endMs > 0L && editorPositionMs >= endMs) {
                editorPlayer.seekTo((trimStart * 1000).toLong())
            }
            delay(120)
        }
    }


    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val playerMessage by PlayerController.message.collectAsState()
    LaunchedEffect(playerMessage) {
        playerMessage?.let {
            snackbar.showSnackbar(it)
            PlayerController.clearMessage()
        }
    }

    // Insets so the floating bars can overlay the content (edge to edge).
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topPad: Dp = statusBarTop + 74.dp
    val bottomPad: Dp = navBarBottom + 176.dp
    // Pages that show the navigation bar need room for it; the others only need
    // room for the floating mini player, so they are not left with a big gap.
    val contentPadding = PaddingValues(
        top = topPad,
        bottom = if (page in BOTTOM_PAGES) bottomPad else navBarBottom + 104.dp
    )

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedName = queryDisplayName(context, uri) ?: "Selected video"
            durationSec = queryDuration(context, uri)
            trimStart = 0f
            trimEnd = durationSec.toFloat()
            status = ConversionStatus.IDLE
            statusMessage = "Video ready to edit"
            page = OffPage.EDITOR
        }
    }

    val legacySave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mpeg")) { uri ->
        val source = pendingOutput
        if (uri != null && source != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "w").use { output ->
                            requireNotNull(output) { "Cannot open the selected save location." }
                            FileInputStream(source).use { input -> input.copyTo(output) }
                        }
                    }
                    val name = queryDisplayName(context, uri) ?: source.name
                    currentOutputUri = uri.toString()
                    currentOutputName = name
                    val savedId = UUID.randomUUID().toString()
                    currentHistoryId = savedId
                    history.add(0, SavedAudio(savedId, name, uri.toString(), System.currentTimeMillis(), source.length()))
                    persistHistory()
                    snackbar.showSnackbar("Saved to your chosen folder")
                } catch (e: Exception) {
                    snackbar.showSnackbar(e.message ?: "Could not save audio")
                } finally {
                    isSaving = false
                }
            }
        } else {
            isSaving = false
        }
    }

    fun saveCurrentAudio() {
        val source = pendingOutput ?: return
        if (isSaving || currentOutputUri != null) return
        isSaving = true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            legacySave.launch(source.name)
            return
        }
        scope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    val chosenName = uniqueDownloadName(context, safeMp3Name(selectedName ?: source.name))
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, chosenName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Off")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val created = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("Android could not create the output file.")
                    try {
                        resolver.openOutputStream(created, "w").use { output ->
                            requireNotNull(output) { "Could not open the output file." }
                            FileInputStream(source).use { input -> input.copyTo(output) }
                        }
                        resolver.update(created, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                        created
                    } catch (e: Exception) {
                        resolver.delete(created, null, null)
                        throw e
                    }
                }
                val name = queryDisplayName(context, uri) ?: source.name
                currentOutputUri = uri.toString()
                currentOutputName = name
                val savedId = UUID.randomUUID().toString()
                currentHistoryId = savedId
                history.add(0, SavedAudio(savedId, name, uri.toString(), System.currentTimeMillis(), source.length()))
                persistHistory()
                snackbar.showSnackbar("Saved in Downloads/Off")
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: "Could not save audio")
            } finally {
                isSaving = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 200.dp))

        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (reducedMotion) {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                } else {
                    // Deeper pages slide in from the right while the old one leaves to the
                    // left; coming back is the exact mirror, so closing a page runs the
                    // entrance backwards instead of playing the same animation again.
                    val forward = pageRank(targetState) >= pageRank(initialState)
                    val direction = if (forward) 1 else -1
                    (slideInHorizontally(tween(340)) { direction * it / 4 } + fadeIn(tween(260)) +
                        scaleIn(tween(340), initialScale = 0.94f)) togetherWith
                        (slideOutHorizontally(tween(320)) { -direction * it / 4 } + fadeOut(tween(220)) +
                            scaleOut(tween(320), targetScale = 0.94f))
                }
            },
            label = "off-page-transition",
            modifier = Modifier.fillMaxSize()
        ) { target ->
            when (target) {
                OffPage.TRIM -> TrimScreen(
                    contentPadding = contentPadding,
                    duration = durationSec,
                    trimStart = trimStart,
                    trimEnd = trimEnd,
                    onSelect = { newStart, newEnd -> trimStart = newStart; trimEnd = newEnd },
                    onNotify = { editorNotice = it }
                )
                OffPage.SPEED -> SpeedScreen(
                    contentPadding = contentPadding,
                    speed = speed,
                    preservePitch = preservePitch,
                    onSpeed = { speed = it; settings.speed = it },
                    onPreservePitch = { preservePitch = it; settings.preservePitch = it },
                    onNotify = { editorNotice = it }
                )
                OffPage.FADE -> FadeScreen(
                    contentPadding = contentPadding,
                    fadeIn = fadeIn,
                    fadeOut = fadeOut,
                    curve = fadeCurve,
                    onFadeIn = { fadeIn = it; settings.fadeIn = it },
                    onFadeOut = { fadeOut = it; settings.fadeOut = it },
                    onCurve = { fadeCurve = it; settings.fadeCurve = it },
                    onNotify = { editorNotice = it }
                )
                OffPage.RESET -> ResetScreen(
                    contentPadding = contentPadding,
                    onResetAll = {
                        trimStart = 0f
                        trimEnd = durationSec.coerceAtLeast(1).toFloat()
                        speed = 1f; settings.speed = 1f
                        fadeIn = 0f; settings.fadeIn = 0f
                        fadeOut = 0f; settings.fadeOut = 0f
                        volume = 1f; settings.volume = 1f
                        preservePitch = true; settings.preservePitch = true
                        page = OffPage.EDITOR
                    },
                    onNotify = { editorNotice = it }
                )
                OffPage.HOME -> HomeScreen(
                    contentPadding = contentPadding,
                    selectedName = selectedName,
                    recentCount = history.size,
                    onPick = { pickVideo.launch(arrayOf("video/*")) },
                    onLibrary = { page = OffPage.LIBRARY },
                    onSearch = { page = OffPage.SEARCH }
                )
                OffPage.SEARCH -> MusicSearchScreen(
                    modifier = Modifier.padding(contentPadding)
                )
                OffPage.VIDEO -> VideoDownloadScreen(contentPadding = contentPadding, initialLink = initialLink)
                OffPage.EDITOR -> OffEditorScreen(
                    contentPadding = contentPadding,
                    artwork = artwork,
                    artworkResolved = artworkResolved,
                    name = selectedName ?: "Selected video",
                    duration = durationSec,
                    bitrate = bitrate,
                    onBitrate = { bitrate = it; settings.bitrate = it },
                    trimStart = trimStart,
                    trimEnd = trimEnd,
                    speed = speed,
                    fadeIn = fadeIn,
                    fadeOut = fadeOut,
                    volume = volume,
                    onVolume = { volume = it; settings.volume = it },
                    playing = editorPlaying,
                    positionMs = editorPositionMs,
                    onSelect = { newStart, newEnd -> trimStart = newStart; trimEnd = newEnd },
                    onSeek = { target -> editorPlayer.seekTo(target); editorPositionMs = target },
                    onPlayPause = { editorPlayPauseRequest += 1 },
                    onTrim = { page = OffPage.TRIM },
                    onSpeed = { page = OffPage.SPEED },
                    onFade = { page = OffPage.FADE },
                    onReset = { page = OffPage.RESET },
                    onExport = {
                        val uri = selectedUri
                        if (uri != null) {
                            conversionJobId += 1
                            val thisJobId = conversionJobId
                            status = ConversionStatus.PREPARING
                            statusMessage = "Preparing your audio…"
                            page = OffPage.PROGRESS
                            scope.launch {
                                val options = ConversionOptions(
                                    bitrateKbps = bitrate,
                                    trimStartSeconds = trimStart,
                                    trimEndSeconds = trimEnd,
                                    speed = speed,
                                    fadeInSeconds = fadeIn,
                                    fadeOutSeconds = fadeOut,
                                    volume = volume,
                                    fadeCurve = fadeCurve
                                )
                                val result = engine.convert(uri, options) { message -> statusMessage = message }
                                if (thisJobId == conversionJobId && status != ConversionStatus.CANCELLED) {
                                    when (result) {
                                        is ConversionResult.Success -> {
                                            pendingOutput?.delete()
                                            pendingOutput = result.outputFile
                                            currentOutputUri = null
                                            currentHistoryId = null
                                            currentOutputName = safeMp3Name(selectedName ?: "off-audio.mp3")
                                            status = ConversionStatus.COMPLETED
                                            statusMessage = "Conversion complete. Save a copy to Downloads when you’re ready."
                                            page = OffPage.RESULT
                                        }
                                        is ConversionResult.Failure -> {
                                            status = ConversionStatus.FAILED
                                            statusMessage = result.message
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
                OffPage.PROGRESS -> ProgressScreen(
                    contentPadding = contentPadding,
                    statusMessage = statusMessage,
                    failed = status == ConversionStatus.FAILED,
                    onCancel = {
                        conversionJobId += 1
                        engine.cancel()
                        status = ConversionStatus.CANCELLED
                        statusMessage = "Conversion cancelled."
                        page = OffPage.EDITOR
                    },
                    onRetry = { page = OffPage.EDITOR }
                )
                OffPage.RESULT -> ResultScreen(
                    contentPadding = contentPadding,
                    name = currentOutputName ?: "off-audio.mp3",
                    bytes = pendingOutput?.length() ?: 0L,
                    saved = currentOutputUri != null,
                    saving = isSaving,
                    onSave = { saveCurrentAudio() },
                    onShare = {
                        val uri = currentOutputUri?.let(Uri::parse)
                        if (uri != null) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/mpeg"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(Intent.createChooser(intent, "Share audio")) }
                        } else {
                            scope.launch { snackbar.showSnackbar("Save the audio first, then share it from your Library.") }
                        }
                    },
                    onDelete = { showDeleteCurrent = true },
                    onDone = { page = OffPage.HOME }
                )
                OffPage.LIBRARY -> LibraryScreen(
                    contentPadding = contentPadding,
                    items = history,
                    onOpen = { item ->
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(item.uri), "audio/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        }.onFailure {
                            scope.launch { snackbar.showSnackbar("This file can’t be opened. It may have been moved or deleted.") }
                        }
                    },
                    onShare = { item ->
                        runCatching {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/mpeg"
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share audio"))
                        }
                    },
                    onDelete = { showDeleteDialog = it },
                    onOpenUri = { raw ->
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(raw), "audio/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        }
                    },
                    onClearDownloads = { DownloadStore.clearFinished() }
                )
                OffPage.SETTINGS -> SettingsScreen(
                    contentPadding = contentPadding,
                    reducedMotion = reducedMotion,
                    onReducedMotion = { reducedMotion = it; settings.reducedMotion = it },
                    onBack = { page = OffPage.HOME }
                )
            }
        }

        // Translucent top bar: content scrolls beneath it.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background,
                        0.72f to MaterialTheme.colorScheme.background.copy(alpha = 0.82f),
                        1f to Color.Transparent
                    )
                )
                .statusBarsPadding()
        ) {
            TopAppBar(
                navigationIcon = {
                    if (page !in BOTTOM_PAGES) {
                        IconButton(onClick = { page = if (page == OffPage.RESULT) OffPage.EDITOR else OffPage.HOME }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                title = {
                    Column {
                        Text("Off", fontWeight = FontWeight.Bold)
                        Text(
                            when (page) {
                                OffPage.SEARCH -> "Search · Saavn + YouTube"
                                OffPage.VIDEO -> "Download a video"
                                OffPage.EDITOR -> "Audio editor"
                                OffPage.TRIM -> "Trim"
                                OffPage.SPEED -> "Playback speed"
                                OffPage.FADE -> "Fade effects"
                                OffPage.RESET -> "Reset edits"
                                OffPage.PROGRESS -> "Export studio"
                                OffPage.RESULT -> "Your audio is ready"
                                OffPage.LIBRARY -> "Library & downloads"
                                OffPage.SETTINGS -> "Preferences"
                                else -> "Offline audio studio"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (page in BOTTOM_PAGES) {
                        IconButton(onClick = { page = OffPage.SETTINGS }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }

        // Translucent bottom scrim behind the floating cluster.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(224.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                        1f to MaterialTheme.colorScheme.background
                    )
                )
        )

        // Floating bottom cluster: mini player + navigation bar.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MiniPlayer(
                onExpand = { showPlayer = true },
                onDismiss = { PlayerController.stop() },
                modifier = Modifier.fillMaxWidth()
            )
            if (page in BOTTOM_PAGES) {
                FloatingNavBar(
                    items = listOf(
                        NavItem("Studio", Icons.Default.Home),
                        NavItem("Search", Icons.Default.Search),
                        NavItem("Video", Icons.Default.Movie),
                        NavItem("Library", Icons.Default.LibraryMusic)
                    ),
                    selectedIndex = BOTTOM_PAGES.indexOf(page).coerceAtLeast(0),
                    onSelect = { index -> page = BOTTOM_PAGES[index] }
                )
            }
        }
    }

    FullPlayerOverlay(
        visible = showPlayer,
        onCollapse = { showPlayer = false },
        onDownload = { track ->
            scope.launch {
                runCatching {
                    val resolved = MusicRepository().resolveStream(track)
                    DownloadService.enqueue(context, track, resolved, UUID.randomUUID().toString())
                    snackbar.showSnackbar("Downloading “${track.title}”…")
                }.onFailure { snackbar.showSnackbar(it.message ?: "Could not start the download.") }
            }
        }
    )

    if (showDeleteCurrent) {
        AlertDialog(
            onDismissRequest = { showDeleteCurrent = false },
            title = { Text("Delete this conversion?") },
            text = { Text("The temporary conversion and, if saved, its linked audio file will be deleted. Other Downloads will not be touched.") },
            confirmButton = {
                Button(onClick = {
                    val linkedFileDeleted = currentOutputUri?.let { deleteLinkedAudio(context, it) } ?: true
                    if (linkedFileDeleted) {
                        pendingOutput?.delete(); pendingOutput = null
                        currentHistoryId?.let { id -> history.removeAll { it.id == id }; persistHistory() }
                        currentHistoryId = null; currentOutputUri = null; currentOutputName = null
                        showDeleteCurrent = false; page = OffPage.HOME
                        scope.launch { snackbar.showSnackbar("Conversion deleted") }
                    } else {
                        showDeleteCurrent = false
                        scope.launch { snackbar.showSnackbar("Android could not delete the saved file. It remains in your Library.") }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { OutlinedButton(onClick = { showDeleteCurrent = false }) { Text("Keep") } }
        )
    }
    showDeleteDialog?.let { item ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete saved audio?") },
            text = { Text("This removes “${item.name}” from Off and attempts to delete only its linked audio file.") },
            confirmButton = {
                Button(onClick = {
                    if (deleteLinkedAudio(context, item.uri)) {
                        history.removeAll { it.id == item.id }; persistHistory(); showDeleteDialog = null
                        scope.launch { snackbar.showSnackbar("Audio item deleted") }
                    } else {
                        showDeleteDialog = null
                        scope.launch { snackbar.showSnackbar("Android could not delete that file. The item remains in your Library.") }
                    }
                }) { Text("Delete audio") }
            },
            dismissButton = { OutlinedButton(onClick = { showDeleteDialog = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun HomeScreen(
    contentPadding: PaddingValues,
    selectedName: String?,
    recentCount: Int,
    onPick: () -> Unit,
    onLibrary: () -> Unit,
    onSearch: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(16.dp))
                }
                Text("Make audio your own.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Stream and download from JioSaavn and YouTube, or extract audio from a video. Everything plays in a full player with a mini player that follows you around.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onSearch, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Icon(Icons.Default.Search, contentDescription = null); Spacer(Modifier.width(10.dp)); Text("Search music")
                }
                OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null); Spacer(Modifier.width(10.dp))
                    Text(if (selectedName == null) "Convert a video" else "Choose another video")
                }
            }
        }
        Text("Your workspace", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ElevatedCard(onClick = onLibrary, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Text("Library", style = MaterialTheme.typography.titleMedium)
                    Text("$recentCount ${if (recentCount == 1) "item" else "items"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ElevatedCard(onClick = onPick, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Text("Audio editor", style = MaterialTheme.typography.titleMedium)
                    Text("Trim · speed · fades", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("Local by design", style = MaterialTheme.typography.titleSmall)
                    Text("Conversion never uploads your media. Music search uses public catalogues.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EditorCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content
    )
}

/** One of the small square shortcuts above the transport controls. */
@Composable
private fun EditorTool(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    EditorCard(modifier.clickable(onClick = onClick)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable
private fun AnimatedBitrateChip(kbps: Int, selected: Boolean, onSelect: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bitrate-scale"
    )
    FilterChip(
        selected = selected,
        onClick = onSelect,
        label = { Text("$kbps") },
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    )
}

@Composable
private fun WaveformPreview() {
    Canvas(Modifier.fillMaxWidth().height(76.dp).clip(MaterialTheme.shapes.large)) {
        val count = 84
        val gap = size.width / (count * 1.6f)
        val barWidth = gap * 0.6f
        for (i in 0 until count) {
            val wave = (0.18f + ((i * 37) % 83) / 100f) * size.height
            val x = i * gap + gap / 2
            drawLine(
                color = if (i < count * 0.72f) androidx.compose.ui.graphics.Color(0xFFFFB59E)
                else androidx.compose.ui.graphics.Color(0xFF55515A),
                start = androidx.compose.ui.geometry.Offset(x, (size.height - wave) / 2),
                end = androidx.compose.ui.geometry.Offset(x, (size.height + wave) / 2),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun ProgressScreen(contentPadding: PaddingValues, statusMessage: String, failed: Boolean, onCancel: () -> Unit, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(92.dp)) {
            if (!failed) CircularProgressIndicator(modifier = Modifier.padding(28.dp), strokeCap = StrokeCap.Round)
            else Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.padding(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(24.dp))
        Text(if (failed) "Export needs attention" else "Creating your audio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(statusMessage, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        if (!failed) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        if (failed) Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Back to editor") }
        else OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel export") }
    }
}

@Composable
private fun ResultScreen(
    contentPadding: PaddingValues,
    name: String, bytes: Long, saved: Boolean, saving: Boolean,
    onSave: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit, onDone: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(104.dp)) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(28.dp))
        }
        Text("Audio is ready", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Your conversion finished successfully.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(54.dp)) {
                    Icon(Icons.Default.AudioFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(13.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("MP3 · ${formatBytes(bytes)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Button(onClick = onSave, enabled = !saved && !saving, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(if (saved) Icons.Default.CheckCircle else Icons.Default.SaveAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (saved) "Saved to Downloads" else if (saving) "Saving audio…" else "Save to Downloads")
        }
        OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Share, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Share audio") }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Back to studio") }
        Text("Delete removes this conversion and its linked saved file only.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.DeleteOutline, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Delete conversion") }
    }
}

@Composable
private fun LibraryScreen(
    contentPadding: PaddingValues,
    items: List<SavedAudio>,
    onOpen: (SavedAudio) -> Unit,
    onShare: (SavedAudio) -> Unit,
    onDelete: (SavedAudio) -> Unit,
    onOpenUri: (String) -> Unit,
    onClearDownloads: () -> Unit
) {
    val context = LocalContext.current
    val favourites by LibraryStore.favourites.collectAsState()
    val playlists by LibraryStore.playlists.collectAsState()
    val downloads by DownloadStore.tasks.collectAsState()
    val finished = downloads.filter { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED }
    val active = downloads.filter { DownloadStore.isActive(it.status) }

    var tab by remember { mutableIntStateOf(0) }
    var openPlaylistName by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Favourites", "Playlists", "Files").forEachIndexed { index, label ->
                BouncyChip(
                    selected = tab == index,
                    onClick = { tab = index; openPlaylistName = null },
                    label = label
                )
            }
        }

        val openPlaylist = playlists.firstOrNull { it.name == openPlaylistName }
        if (openPlaylist != null) {
            PlaylistDetail(
                playlist = openPlaylist,
                onBack = { openPlaylistName = null },
                onDelete = {
                    LibraryStore.deletePlaylist(openPlaylist.name)
                    openPlaylistName = null
                }
            )
        } else when (tab) {
            0 -> {
                if (favourites.isEmpty()) {
                    LibraryEmptyHint(
                        title = "No favourites yet",
                        body = "Tap the heart on a track in the player or in search to keep it here."
                    )
                } else {
                    SectionLabel("${favourites.size} favourite${if (favourites.size == 1) "" else "s"}")
                    favourites.forEachIndexed { index, track ->
                        LibraryTrackCard(
                            track = track,
                            onPlay = { PlayerController.playQueue(favourites, index) },
                            onRemove = { LibraryStore.toggleFavourite(track) }
                        )
                    }
                }
            }
            1 -> {
                if (playlists.isEmpty()) {
                    LibraryEmptyHint(
                        title = "No playlists yet",
                        body = "Use “Add to playlist” in the player, or the menu on any search result."
                    )
                } else {
                    playlists.forEach { playlist ->
                        Card(modifier = Modifier.fillMaxWidth().clickable { openPlaylistName = playlist.name }) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Default.QueueMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(playlist.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${playlist.tracks.size} track${if (playlist.tracks.size == 1) "" else "s"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { LibraryStore.deletePlaylist(playlist.name) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete playlist")
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                if (active.isNotEmpty()) {
                    SectionLabel("Downloading")
                    active.forEach { task ->
                        DownloadRow(
                            task = task,
                            onOpen = {},
                            onPauseResume = {
                                if (task.status == DownloadStatus.PAUSED) DownloadService.resume(context)
                                else DownloadService.pause(context)
                            },
                            onCancel = { DownloadService.cancel(context) }
                        )
                    }
                }

                if (finished.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(
                            "${finished.size} in this session",
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = onClearDownloads) { Text("Clear list") }
                    }
                    finished.forEach { task ->
                        DownloadRow(
                            task = task,
                            onOpen = { task.savedUri?.let(onOpenUri) },
                            onPauseResume = null,
                            onCancel = null
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                }

                if (items.isEmpty() && finished.isEmpty() && active.isEmpty()) {
                    LibraryEmptyHint(
                        title = "Your library is waiting",
                        body = "Downloaded songs, favourites and saved conversions appear here."
                    )
                }

                if (items.isNotEmpty()) {
                    SectionLabel("${items.size} saved ${if (items.size == 1) "track" else "tracks"}")
                    items.forEach { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AudioFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                                Column(Modifier.weight(1f).clickable { onOpen(item) }) {
                                    Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${formatBytes(item.bytes)} · ${java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(item.createdAt))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onShare(item) }) {
                                    Icon(Icons.Default.Share, contentDescription = "Share ${item.name}")
                                }
                                IconButton(onClick = { onDelete(item) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${item.name}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LibraryEmptyHint(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.LibraryMusic,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun LibraryTrackCard(
    track: Track,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                SkeletonImage(
                    model = track.artworkUrl,
                    contentDescription = null,
                    shape = MaterialTheme.shapes.large,
                    fallback = Icons.Default.MusicNote,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(Modifier.weight(1f).clickable { onPlay() }) {
                Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play ${track.title}")
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove ${track.title}")
            }
        }
    }
}

@Composable
private fun PlaylistDetail(
    playlist: Playlist,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "${playlist.tracks.size} track${if (playlist.tracks.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { PlayerController.playQueue(playlist.tracks, 0) },
                enabled = playlist.tracks.isNotEmpty()
            ) { Text("Play all") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete playlist")
            }
        }
        if (playlist.tracks.isEmpty()) {
            LibraryEmptyHint("Empty playlist", "Add tracks to it from the player or from search.")
        } else {
            playlist.tracks.forEachIndexed { index, track ->
                LibraryTrackCard(
                    track = track,
                    onPlay = { PlayerController.playQueue(playlist.tracks, index) },
                    onRemove = { LibraryStore.removeFromPlaylist(playlist.name, track.id) }
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(
    task: DownloadTask,
    onOpen: () -> Unit,
    onPauseResume: (() -> Unit)?,
    onCancel: (() -> Unit)?
) {
    val running = task.status == DownloadStatus.RUNNING
    val paused = task.status == DownloadStatus.PAUSED
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(12.dp))
                }
                Column(Modifier.weight(1f).clickable(enabled = task.savedUri != null) { onOpen() }) {
                    Text(task.track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        task.message ?: task.track.source.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onPauseResume != null) {
                    IconButton(onClick = onPauseResume) {
                        Icon(
                            if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (paused) "Resume download" else "Pause download"
                        )
                    }
                }
                if (onCancel != null) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel download")
                    }
                }
            }
            if (running || paused) {
                Spacer(Modifier.height(8.dp))
                val progress = task.progress
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(contentPadding: PaddingValues, reducedMotion: Boolean, onReducedMotion: (Boolean) -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Playback & motion", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Reduce page animations", style = MaterialTheme.typography.titleMedium)
                        Text("Use gentle fades instead of sliding transitions.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = reducedMotion, onCheckedChange = onReducedMotion)
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sources", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("JioSaavn (unofficial catalogue API) and YouTube (NewPipe Extractor). Both are third-party and can change without notice.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("About Off", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Version 4.0.0", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Offline-first audio conversion plus a streaming music player. Only download or keep content you are authorised to use.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

/* ---------------------------------- helpers --------------------------------- */

private fun deleteLinkedAudio(context: Context, rawUri: String): Boolean = try {
    val uri = Uri.parse(rawUri)
    val deleted = context.contentResolver.delete(uri, null, null)
    if (deleted > 0) true else {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            !cursor.moveToFirst()
        } ?: true
    }
} catch (_: Exception) {
    false
}

private fun queryDisplayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
} catch (_: Exception) {
    null
}

private fun queryDuration(context: Context, uri: Uri): Int = try {
    val retriever = MediaMetadataRetriever()
    retriever.setDataSource(context, uri)
    val millis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    retriever.release()
    (millis / 1000L).toInt().coerceAtLeast(0)
} catch (_: Exception) {
    0
}

private fun safeMp3Name(raw: String): String {
    val base = raw.substringBeforeLast('.', raw).replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "off-audio" }
    return (if (base.endsWith(".mp3", true)) base else "$base.mp3").take(120)
}

private fun uniqueDownloadName(context: Context, requested: String): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return requested
    val resolver = context.contentResolver
    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    val base = requested.substringBeforeLast('.', requested)
    val extension = requested.substringAfterLast('.', "mp3")
    var candidate = requested
    var suffix = 2
    while (true) {
        val exists = resolver.query(
            collection, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(candidate, Environment.DIRECTORY_DOWNLOADS + "/Off/"), null
        )?.use { it.moveToFirst() } ?: false
        if (!exists) return candidate
        candidate = "$base ($suffix).$extension"
        suffix++
    }
}

private fun formatTime(seconds: Int): String = "%d:%02d".format(seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

private fun loadHistory(context: Context): List<SavedAudio> = runCatching {
    val raw = context.getSharedPreferences("off_v3", Context.MODE_PRIVATE).getString("history", "[]") ?: "[]"
    val json = JSONArray(raw)
    buildList {
        for (i in 0 until json.length()) {
            val item = json.getJSONObject(i)
            add(
                SavedAudio(
                    item.getString("id"),
                    item.getString("name"),
                    item.getString("uri"),
                    item.getLong("createdAt"),
                    item.optLong("bytes", 0L)
                )
            )
        }
    }
}.getOrDefault(emptyList())

private fun saveHistory(context: Context, items: List<SavedAudio>) {
    val json = JSONArray()
    items.forEach { item ->
        json.put(JSONObject().apply {
            put("id", item.id); put("name", item.name); put("uri", item.uri)
            put("createdAt", item.createdAt); put("bytes", item.bytes)
        })
    }
    context.getSharedPreferences("off_v3", Context.MODE_PRIVATE).edit().putString("history", json.toString()).apply()
}
