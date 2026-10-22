package com.off.audioconverter.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.off.audioconverter.download.DownloadService
import com.off.audioconverter.download.DownloadStore
import com.off.audioconverter.player.PlayerController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * One search box over every source. Results from JioSaavn and YouTube are merged
 * into a single list, each tagged with where it came from.
 */
@Composable
fun MusicSearchScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { MusicRepository() }
    val downloads by DownloadStore.tasks.collectAsState()

    var query by remember { mutableStateOf("") }
    var enabledSources by remember { mutableStateOf(MusicSource.entries.toSet()) }
    var response by remember { mutableStateOf<SearchResponse?>(null) }
    var loading by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var downloadingId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query, enabledSources) {
        val term = query.trim()
        if (term.length < 2) {
            response = null
            loading = false
            return@LaunchedEffect
        }
        delay(400)
        loading = true
        response = runCatching { repository.search(term, enabledSources) }
            .onFailure { notice = it.message }
            .getOrNull()
        loading = false
    }

    val allTracks = response?.tracks.orEmpty()

    fun playAt(track: Track) {
        val list = allTracks.ifEmpty { listOf(track) }
        PlayerController.playQueue(list, list.indexOf(track).coerceAtLeast(0))
    }

    fun download(track: Track) {
        if (downloadingId != null) return
        downloadingId = track.id
        scope.launch {
            try {
                val resolved = repository.resolveStream(track)
                DownloadService.enqueue(context, track, resolved, UUID.randomUUID().toString())
                notice = "Downloading “${track.title}” — watch the notification for progress."
            } catch (e: Exception) {
                notice = e.message ?: "Could not start the download."
            } finally {
                downloadingId = null
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Songs, artists, albums…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            shape = RoundedCornerShape(24.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MusicSource.entries.forEach { source ->
                val selected = source in enabledSources
                FilterChip(
                    selected = selected,
                    onClick = {
                        enabledSources = if (selected) {
                            (enabledSources - source).ifEmpty { MusicSource.entries.toSet() }
                        } else {
                            enabledSources + source
                        }
                    },
                    label = { Text(source.label) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors()
                )
            }
        }

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        if (query.trim().length < 2) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Text("Search everything at once", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Type at least two characters. JioSaavn and YouTube are queried together and the results are merged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        response?.results?.forEach { slice ->
            if (slice.error != null) {
                Text(
                    slice.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(allTracks, key = { it.id }) { track ->
                val task = downloads.firstOrNull { it.track.id == track.id }
                val done = task?.savedName != null
                val busy = task?.status == com.off.audioconverter.download.DownloadStatus.RUNNING ||
                    task?.status == com.off.audioconverter.download.DownloadStatus.QUEUED
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(54.dp)
                            ) {
                                if (track.artworkUrl != null) {
                                    AsyncImage(
                                        model = track.artworkUrl,
                                        contentDescription = "${track.title} artwork",
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(13.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text(
                                    track.artist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    track.source.label + (track.bitrateKbps?.let { " · ${it}kbps" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { playAt(track) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play ${track.title}")
                            }
                            IconButton(
                                onClick = { download(track) },
                                enabled = !busy && !done && downloadingId != track.id
                            ) {
                                when {
                                    downloadingId == track.id || busy -> CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    done -> Icon(Icons.Default.DownloadDone, contentDescription = "Downloaded")
                                    else -> Icon(Icons.Default.Download, contentDescription = "Download ${track.title}")
                                }
                            }
                        }
                        if (busy) {
                            val p = task?.progress
                            if (p != null) {
                                LinearProgressIndicator(
                                    progress = { p },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp))
                            }
                            Spacer(Modifier.size(8.dp))
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
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { notice = null }) { Text("OK") }
                }
            }
        }
    }
}
