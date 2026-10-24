package com.off.audioconverter.video

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.key
import androidx.compose.ui.input.pointer.pointerInput
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.off.audioconverter.ui.components.BouncyChip
import com.off.audioconverter.ui.components.MaterialLoadingIndicator
import com.off.audioconverter.ui.components.SkeletonBox
import com.off.audioconverter.ui.components.SkeletonImage
import com.off.audioconverter.ui.components.SkeletonLine
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The fetched card: whatever the link resolved to, the format picker and the
 * Download button. The home screen owns the URL bar and asks for the fetch, so
 * this stays a plain section that can be dropped in anywhere.
 */
@Composable
fun VideoFetchSection(
    link: String,
    fetchRequest: Int,
    onNotice: (String) -> Unit,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { VideoRepository(context) }
    val downloads by VideoDownloadStore.tasks.collectAsState()

    var info by remember { mutableStateOf<VideoInfo?>(null) }
    var mode by remember { mutableIntStateOf(0) }          // 0 = video, 1 = audio
    var quality by remember { mutableStateOf<VideoQuality?>(null) }
    var audio by remember { mutableStateOf<AudioOption?>(null) }
    var loading by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun fetch() {
        val text = link.trim()
        if (text.isEmpty()) return
        loading = true
        notice = null
        info = null
        scope.launch {
            runCatching { repository.resolve(text) }
                .onSuccess {
                    info = it
                    quality = it.best
                    audio = it.audioOptions.firstOrNull()
                    mode = 0
                    if (it.qualities.isEmpty() && it.audioOptions.isEmpty()) {
                        notice = "This video has no downloadable streams."
                    }
                }
                .onFailure { notice = it.message ?: "Could not read that link." }
            loading = false
        }
    }

    val infoAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (info != null) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(420),
        label = "video-info-fade"
    )

    // The home screen asks for a fetch by bumping the counter, so it owns the URL
    // bar and this stays a pure fetch-and-pick section.
    LaunchedEffect(fetchRequest) {
        if (fetchRequest > 0) fetch()
    }

    // Messages go up to the app snackbar rather than a second strip on screen.
    LaunchedEffect(notice) {
        notice?.let { onNotice(it); notice = null }
    }

    LaunchedEffect(loading) { onLoading(loading) }

    val selection: DownloadSelection? = if (mode == 0) {
        quality?.let {
            DownloadSelection(
                label = it.label,
                videoUrl = it.videoUrl,
                audioUrl = it.audioUrl,
                sourceMime = it.mimeType,
                outputFormat = when {
                    it.isImage -> it.mimeType.substringAfterLast('/')
                    it.mimeType.contains("webm") -> "webm"
                    else -> "mp4"
                },
                pageUrl = link.trim(),
                ytDlpSelector = it.ytDlpSelector,
                isImage = it.isImage
            )
        }
    } else {
        audio?.let {
            DownloadSelection(
                label = it.label,
                videoUrl = null,
                audioUrl = it.url,
                sourceMime = it.sourceMime,
                outputFormat = it.outputFormat,
                mp3BitrateKbps = it.mp3BitrateKbps,
                pageUrl = link.trim(),
                ytDlpSelector = it.ytDlpSelector
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (loading && info == null) {
            VideoInfoSkeleton()
        }

        AnimatedContent(
            targetState = info,
            transitionSpec = {
                (fadeIn(tween(280)) + expandVertically(tween(380), expandFrom = Alignment.Top)) togetherWith
                    (fadeOut(tween(160)) + shrinkVertically(tween(260), shrinkTowards = Alignment.Top))
            },
            label = "video-card"
        ) { shown ->
            if (shown == null) return@AnimatedContent
            val video = shown
            Card(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // 16:9 thumbnail, edge to edge inside the card.
                    SkeletonImage(
                        model = video.thumbnailUrl,
                        contentDescription = null,
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                        fallback = Icons.Default.Movie,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    )

                    Column(
                        Modifier.padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            video.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                video.uploader,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(8.dp))
                            Badge("via ${video.source}")
                            if (video.durationSec > 0) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    formatDuration(video.durationSec),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // The video's description, where the quality chips used to sit.
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                    ) {
                        Text(
                            video.description.ifBlank { "This video has no description." },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 7,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(12.dp)
                                .heightIn(max = 180.dp)
                        )
                    }

                    // Then the format, and the qualities beneath it.
                    Column(
                        Modifier.padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Download as", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BouncyChip(
                                selected = mode == 0,
                                onClick = { mode = 0 },
                                label = "Video",
                                leadingIcon = Icons.Default.Movie
                            )
                            BouncyChip(
                                selected = mode == 1,
                                onClick = { mode = 1 },
                                enabled = video.audioOptions.isNotEmpty(),
                                label = "Audio",
                                leadingIcon = Icons.Default.MusicNote
                            )
                        }

                        // The list under the switch used to be swapped outright the moment the
                        // mode changed, so it jumped while the chips were still cross-fading.
                        // It now cross-fades and slides with them.
                        AnimatedContent(
                            targetState = mode,
                            transitionSpec = {
                                (fadeIn(tween(240)) + slideInVertically(tween(300)) { it / 8 }) togetherWith
                                    (fadeOut(tween(170)) + slideOutVertically(tween(240)) { -it / 8 }) using
                                    SizeTransform(clip = false)
                            },
                            label = "download-mode",
                            modifier = Modifier.fillMaxWidth()
                        ) { activeMode ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    if (activeMode == 0) "Quality" else "Audio format",
                                    style = MaterialTheme.typography.titleSmall
                                )

                                if (activeMode == 0) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        video.qualities.chunked(4).forEach { row ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                row.forEach { option ->
                                                    BouncyChip(
                                                        selected = quality == option,
                                                        onClick = { quality = option },
                                                        label = option.label
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    quality?.let { option ->
                                        Text(
                                            when {
                                                option.isImage -> "Saved to Pictures/Off as a photo."
                                                option.muxed -> "This rendition already contains audio — saved straight out."
                                                else -> "Video and audio arrive separately and are joined with FFmpeg after download."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        video.audioOptions.forEach { option ->
                                            BouncyChip(
                                                selected = audio == option,
                                                onClick = { audio = option },
                                                label = option.label
                                            )
                                        }
                                    }
                                    audio?.let { option ->
                                        Text(
                                            if (option.outputFormat == "mp3") {
                                                "The audio is re-encoded to MP3 (${option.mp3BitrateKbps} kbps) after download and saved to Music/Off."
                                            } else {
                                                "The original audio stream is saved untouched to Music/Off."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val chosen = selection ?: return@Button
                            VideoDownloadService.enqueue(context, video, chosen, UUID.randomUUID().toString())
                            notice = "Downloading “${video.title}”. Tap its thumbnail to pause, or long-press for more."
                        },
                        enabled = selection != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 14.dp)
                            .height(50.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        // Cross-fade the label: it used to swap instantly while the chips
                        // above were still animating.
                        AnimatedContent(
                            targetState = selection?.label ?: "",
                            transitionSpec = {
                                fadeIn(tween(210)) togetherWith fadeOut(tween(140))
                            },
                            label = "download-label"
                        ) { label ->
                            Text(
                                "Download $label".trim(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (info == null && !loading && notice == null) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text("Paste a link from almost anywhere", fontWeight = FontWeight.SemiBold)
                    Text(
                        "YouTube in every shape (watch, youtu.be, shorts, live, embed) goes through NewPipe. Instagram, TikTok, Facebook, X, Reddit, Vimeo, Dailymotion, Twitch VOD, SoundCloud and the rest of yt-dlp's list are read by the on-device yt-dlp engine. Download it as video, or as MP3 / M4A audio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Only download videos you are allowed to save.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}

/** Placeholder for the whole video card while the link is being read. */
@Composable
private fun VideoInfoSkeleton() {
    Card(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBox(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            )
            Column(
                Modifier.padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SkeletonLine(width = 250.dp, height = 16.dp)
                SkeletonLine(width = 150.dp, height = 12.dp)
            }
            SkeletonBox(
                Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 14.dp),
                RoundedCornerShape(14.dp)
            )
            Row(
                Modifier.padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) {
                    SkeletonBox(Modifier.width(74.dp).height(32.dp), RoundedCornerShape(16.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

/**
 * One download. The thumbnail doubles as the pause/resume control while the
 * download is running, and a long press opens the same actions as a menu.
 */
@Composable
private fun DownloadRow(
    task: VideoDownloadTask,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val active = VideoDownloadStore.isActive(task.status)
    val paused = task.status == VideoDownloadStatus.PAUSED
    val completed = task.status == VideoDownloadStatus.COMPLETED
    val failed = task.status == VideoDownloadStatus.FAILED
    val isAudio = task.label.contains("MP3", ignoreCase = true) ||
        task.label.contains("M4A", ignoreCase = true)
    val fallbackIcon = if (isAudio) Icons.Default.MusicNote else Icons.Default.Movie

    Card(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .pointerInput(task.id) {
                detectTapGestures(onLongPress = { menuOpen = true })
            }
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .pointerInput(task.id, active, paused, completed) {
                            detectTapGestures(
                                onTap = {
                                    when {
                                        active -> onToggle()
                                        completed -> onOpen()
                                        else -> Unit
                                    }
                                },
                                onLongPress = { menuOpen = true }
                            )
                        }
                ) {
                    SkeletonImage(
                        model = task.thumbnailUrl,
                        contentDescription = task.title,
                        shape = RoundedCornerShape(13.dp),
                        fallback = fallbackIcon,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (active) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (paused) "Resume download" else "Pause download",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        task.stage ?: task.message ?: task.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                when {
                    completed -> Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Saved",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    failed -> Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = "Failed",
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Download options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (active) {
                            DropdownMenuItem(
                                text = { Text(if (paused) "Resume" else "Pause") },
                                leadingIcon = {
                                    Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                                },
                                onClick = { menuOpen = false; onToggle() }
                            )
                        }
                        if (completed) {
                            DropdownMenuItem(
                                text = { Text("Open") },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                                onClick = { menuOpen = false; onOpen() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                            onClick = { menuOpen = false; onDelete() }
                        )
                    }
                }
            }

            if (active) {
                Spacer(Modifier.height(10.dp))
                val progress = task.progress
                if (progress != null && !paused) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { if (paused) (progress ?: 0f) else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val total = seconds.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val secs = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%d:%02d".format(minutes, secs)
}
