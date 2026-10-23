package com.off.audioconverter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.off.audioconverter.video.VideoDownloadStatus
import com.off.audioconverter.video.VideoDownloadStore
import com.off.audioconverter.video.VideoDownloadTask
import com.off.audioconverter.ui.components.SkeletonImage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** A green that reads as "finished" without inventing a Material role for it. */
private val Success = Color(0xFF9DC8A7)

/**
 * The home screen: paste a link, and everything you have downloaded underneath.
 *
 * The layout follows the supplied design. The rows are the real downloads rather
 * than sample data, so the list is empty until something is fetched.
 */
@Composable
fun OffHomeScreen(
    contentPadding: PaddingValues,
    onOpenLink: (String) -> Unit,
    onSettings: () -> Unit,
    onOpen: (VideoDownloadTask) -> Unit,
    onEditAudio: (VideoDownloadTask) -> Unit,
    onDelete: (VideoDownloadTask) -> Unit,
    onNotice: (String) -> Unit
) {
    val tasks by VideoDownloadStore.tasks.collectAsState()
    var url by remember { mutableStateOf("") }
    var newestFirst by remember { mutableStateOf(true) }
    var deleteTarget by remember { mutableStateOf<VideoDownloadTask?>(null) }
    var detailTarget by remember { mutableStateOf<VideoDownloadTask?>(null) }

    val downloads = remember(tasks, newestFirst) {
        if (newestFirst) tasks else tasks.reversed()
    }
    val finished = downloads.count { it.status == VideoDownloadStatus.COMPLETED }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 18.dp)
            .padding(top = 10.dp, bottom = 28.dp)
    ) {
        // ---- header ----
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Off", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Video downloader", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        // ---- paste a link ----
        Text(
            "Download anything.",
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "Paste a webpage link to get started.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(18.dp))

        Surface(
            Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(23.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(23.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (url.isBlank()) {
                            Text(
                                "Paste video webpage URL…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                        BasicTextField(
                            value = url,
                            onValueChange = { url = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    }
                    if (url.isNotBlank()) {
                        IconButton(onClick = { url = "" }, modifier = Modifier.size(30.dp)) {
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = "Clear the link",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = Success,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "Paste a link from a supported website",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val text = url.trim()
                            if (text.isBlank()) {
                                onNotice("Paste a webpage link first.")
                            } else {
                                onOpenLink(text)
                                url = ""
                            }
                        },
                        shape = RoundedCornerShape(13.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Go", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ---- the downloads ----
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Your downloads",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (downloads.isEmpty()) "Nothing yet"
                    else "$finished saved · ${downloads.size} in total",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            if (downloads.size > 1) {
                TextButton(onClick = { newestFirst = !newestFirst }) {
                    Icon(
                        Icons.Default.Sort,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(if (newestFirst) "Newest" else "Oldest", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (downloads.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("No downloads yet", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Your downloaded videos will appear here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        }

        downloads.forEach { task ->
            HomeDownloadRow(
                task = task,
                menuOpen = false,
                onOpen = { onOpen(task) },
                onDetails = { detailTarget = task },
                onEditAudio = { onEditAudio(task) },
                onDelete = { deleteTarget = task }
            )
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(25.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Saved locally",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        "Videos land in Movies/Off, audio in Music/Off",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Success)
            }
        }
    }

    deleteTarget?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Delete download?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "“${task.title}” will be removed from this list, and its half-finished file, if there is one, will be deleted.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(task); deleteTarget = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    detailTarget?.let { task ->
        AlertDialog(
            onDismissRequest = { detailTarget = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("File details", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(task.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                    Text("Source: ${task.source ?: "unknown"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Format: ${task.label.ifBlank { "—" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Size: ${if (task.sizeBytes > 0) formatSize(task.sizeBytes) else "—"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Duration: ${if (task.durationSec > 0) formatDuration(task.durationSec) else "—"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Status: ${statusLabel(task)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Started: ${formatDate(task.createdAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    task.savedName?.let { Text("Saved as: $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailTarget = null }) {
                    Text("Done", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}

@Composable
private fun HomeDownloadRow(
    task: VideoDownloadTask,
    menuOpen: Boolean,
    onOpen: () -> Unit,
    onDetails: () -> Unit,
    onEditAudio: () -> Unit,
    onDelete: () -> Unit
) {
    var open by remember { mutableStateOf(menuOpen) }
    val done = task.status == VideoDownloadStatus.COMPLETED
    val failed = task.status == VideoDownloadStatus.FAILED
    val active = VideoDownloadStore.isActive(task.status)
    val isAudio = task.label.contains("MP3", ignoreCase = true) ||
        task.label.contains("M4A", ignoreCase = true)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(19.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 16:10 thumbnail with the duration in the corner.
                Box(
                    Modifier
                        .width(118.dp)
                        .aspectRatio(16f / 10f)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    SkeletonImage(
                        model = task.thumbnailUrl,
                        contentDescription = task.title,
                        shape = RoundedCornerShape(13.dp),
                        fallback = if (isAudio) Icons.Default.MusicNote else Icons.Default.PlayCircle,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (task.durationSec > 0) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(5.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xDD111110))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                formatDuration(task.durationSec),
                                color = Color(0xFFFFF4EC),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        task.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            task.source ?: task.label.ifBlank { "Off" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (task.sizeBytes > 0) {
                        Text(
                            formatSize(task.sizeBytes),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (failed) Icons.Default.Info else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (failed) MaterialTheme.colorScheme.error else Success,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            when {
                                done -> "Downloaded · ${formatDate(task.createdAt)}"
                                failed -> task.message ?: "Failed"
                                else -> task.stage ?: "Working…"
                            },
                            color = if (failed) MaterialTheme.colorScheme.error else Success,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box {
                    IconButton(onClick = { open = true }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Download options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = open,
                        onDismissRequest = { open = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = {
                                Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                            },
                            enabled = done,
                            onClick = { open = false; onOpen() }
                        )
                        DropdownMenuItem(
                            text = { Text("Details", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = {
                                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                            },
                            onClick = { open = false; onDetails() }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit audio", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = {
                                Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                            },
                            enabled = done,
                            onClick = { open = false; onEditAudio() }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = { open = false; onDelete() }
                        )
                    }
                }
            }

            if (active) {
                Spacer(Modifier.height(10.dp))
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

/* --------------------------------- helpers --------------------------------- */

private fun statusLabel(task: VideoDownloadTask): String = when (task.status) {
    VideoDownloadStatus.QUEUED -> "Queued"
    VideoDownloadStatus.RUNNING -> "Downloading"
    VideoDownloadStatus.PAUSED -> "Paused"
    VideoDownloadStatus.COMPLETED -> "Completed"
    VideoDownloadStatus.FAILED -> "Failed"
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

private fun formatDuration(seconds: Long): String {
    val total = seconds.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val secs = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%d:%02d".format(minutes, secs)
}

private fun formatDate(timestamp: Long): String {
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val now = Calendar.getInstance()
    val sameYear = then.get(Calendar.YEAR) == now.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "Today"
        sameYear && dayDiff == 1 -> "Yesterday"
        sameYear -> SimpleDateFormat("MMM d", Locale.US).format(Date(timestamp))
        else -> SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(timestamp))
    }
}
