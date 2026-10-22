package com.off.audioconverter.video

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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Download a video — or just its audio — by pasting a link. Resolution goes
 * through NewPipe Extractor first and the Piped API second, so "any video" means
 * any link that either backend can read.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoDownloadScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { VideoRepository() }
    val downloads by VideoDownloadStore.tasks.collectAsState()

    var link by remember { mutableStateOf("") }
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

    val selection: DownloadSelection? = if (mode == 0) {
        quality?.let {
            DownloadSelection(
                label = it.label,
                videoUrl = it.videoUrl,
                audioUrl = it.audioUrl,
                sourceMime = it.mimeType,
                outputFormat = if (it.mimeType.contains("webm")) "webm" else "mp4"
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
                mp3BitrateKbps = it.mp3BitrateKbps
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("https://youtube.com/watch?v=…") },
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
            trailingIcon = {
                if (link.isNotEmpty()) {
                    IconButton(onClick = { link = ""; info = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear link")
                    }
                }
            },
            shape = RoundedCornerShape(22.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { fetch() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )

        Button(
            onClick = { fetch() },
            enabled = !loading && link.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (loading) {
                // The Material 3 Expressive loading indicator, exactly as shipped.
                LoadingIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Default.Download, contentDescription = null)
            }
            Spacer(Modifier.width(10.dp))
            Text(if (loading) "Reading the link…" else "Fetch video")
        }

        info?.let { video ->
            Card(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // 16:9 thumbnail, edge to edge inside the card.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    ) {
                        if (video.thumbnailUrl != null) {
                            AsyncImage(
                                model = video.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }

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
                            FilterChip(
                                selected = mode == 0,
                                onClick = { mode = 0 },
                                label = { Text("Video") },
                                leadingIcon = { Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            FilterChip(
                                selected = mode == 1,
                                onClick = { mode = 1 },
                                enabled = video.audioOptions.isNotEmpty(),
                                label = { Text("Audio") },
                                leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }

                        Text(
                            if (mode == 0) "Quality" else "Audio format",
                            style = MaterialTheme.typography.titleSmall
                        )

                        if (mode == 0) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                video.qualities.chunked(4).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        row.forEach { option ->
                                            FilterChip(
                                                selected = quality == option,
                                                onClick = { quality = option },
                                                label = { Text(option.label) }
                                            )
                                        }
                                    }
                                }
                            }
                            quality?.let { option ->
                                Text(
                                    if (option.muxed) {
                                        "This rendition already contains audio — saved straight out."
                                    } else {
                                        "Video and audio arrive separately and are joined with FFmpeg after download."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                video.audioOptions.forEach { option ->
                                    FilterChip(
                                        selected = audio == option,
                                        onClick = { audio = option },
                                        label = { Text(option.label) }
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

                    Button(
                        onClick = {
                            val chosen = selection ?: return@Button
                            VideoDownloadService.enqueue(context, video, chosen, UUID.randomUUID().toString())
                            notice = "Downloading “${video.title}” — watch the notification for progress."
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
                        Text(
                            "Download ${selection?.label ?: ""}".trim(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (downloads.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Downloads",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { VideoDownloadStore.clearFinished() }) { Text("Clear finished") }
            }
            downloads.forEach { task ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    if (task.label.contains("MP3", ignoreCase = true) ||
                                        task.label.contains("M4A", ignoreCase = true)
                                    ) Icons.Default.MusicNote else Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (VideoDownloadStore.isActive(task.status)) {
                                IconButton(onClick = { VideoDownloadService.cancel(context) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel download")
                                }
                            }
                        }
                        if (VideoDownloadStore.isActive(task.status)) {
                            Spacer(Modifier.height(8.dp))
                            val progress = task.progress
                            if (progress != null) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }

        notice?.let { message ->
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { notice = null }) { Text("OK") }
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
                    Text("Paste any supported link", fontWeight = FontWeight.SemiBold)
                    Text(
                        "YouTube works in every shape — watch, youtu.be, shorts, live and embed — and the sites NewPipe handles (PeerTube, SoundCloud, Bandcamp) work too. Download it as video, or as MP3 / M4A audio.",
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
