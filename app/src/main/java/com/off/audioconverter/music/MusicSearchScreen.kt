package com.off.audioconverter.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.off.audioconverter.download.DownloadService
import com.off.audioconverter.download.DownloadStatus
import com.off.audioconverter.download.DownloadStore
import com.off.audioconverter.download.DownloadTask
import com.off.audioconverter.library.LibraryStore
import com.off.audioconverter.library.AddToPlaylistDialog
import com.off.audioconverter.ui.components.BouncyChip
import com.off.audioconverter.ui.components.SkeletonBox
import com.off.audioconverter.ui.components.SkeletonImage
import com.off.audioconverter.ui.components.SkeletonLine
import com.off.audioconverter.ui.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * One search box over every source. Results from JioSaavn and YouTube are merged
 * into a single list, led by a "top result" card, with recent searches when idle.
 */
@Composable
fun MusicSearchScreen(
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { MusicRepository() }
    val settings = remember { SettingsStore(context) }
    val downloads by DownloadStore.tasks.collectAsState()

    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var enabledSources by remember {
        mutableStateOf(
            buildSet {
                if (settings.saavnEnabled) add(MusicSource.SAAVN)
                if (settings.youtubeEnabled) add(MusicSource.YOUTUBE)
            }.ifEmpty { MusicSource.entries.toSet() }
        )
    }
    var response by remember { mutableStateOf<SearchResponse?>(null) }
    var loading by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var recents by remember { mutableStateOf(settings.recentSearches()) }

    LaunchedEffect(query, enabledSources) {
        val term = query.trim()
        if (term.length < 2) {
            response = null
            loading = false
            return@LaunchedEffect
        }
        delay(380)
        loading = true
        response = runCatching { repository.search(term, enabledSources) }
            .onFailure { notice = it.message }
            .getOrNull()
        loading = false
    }

    fun commitSearch() {
        val term = query.trim()
        if (term.length < 2) return
        submitted = term
        settings.addRecentSearch(term)
        recents = settings.recentSearches()
    }

    val allTracks = response?.tracks.orEmpty()

    fun download(track: Track) {
        scope.launch {
            try {
                val resolved = repository.resolveStream(track)
                DownloadService.enqueue(context, track, resolved, UUID.randomUUID().toString())
                notice = "Downloading “${track.title}” — watch the notification for progress and controls."
            } catch (e: Exception) {
                notice = e.message ?: "Could not start the download."
            }
        }
    }

    // Only the top inset is applied to the screen. The bottom one is given to the
    // list itself as content padding, so the results run to the bottom of the
    // display and scroll under the floating navigation bar instead of stopping
    // short of it and leaving a dead band.
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = topPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
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
            shape = RoundedCornerShape(26.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { commitSearch() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MusicSource.entries.forEach { source ->
                val selected = source in enabledSources
                BouncyChip(
                    selected = selected,
                    onClick = {
                        val next = if (selected) enabledSources - source else enabledSources + source
                        enabledSources = next.ifEmpty { setOf(source) }
                        settings.saavnEnabled = MusicSource.SAAVN in enabledSources
                        settings.youtubeEnabled = MusicSource.YOUTUBE in enabledSources
                    },
                    label = source.label,
                    leadingIcon = if (selected) Icons.Default.DownloadDone else null
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                query.trim().length < 2 -> IdleState(
                    recents = recents,
                    onRecent = { query = it },
                    onClearRecents = {
                        settings.clearRecentSearches()
                        recents = emptyList()
                    }
                )

                loading && allTracks.isEmpty() -> SearchSkeleton()

                allTracks.isEmpty() && !loading -> EmptyState(query = query.trim())

                else -> {
                    val top = allTracks.first()
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(key = "top") {
                            TopResultCard(
                                track = top,
                                onDownload = { download(top) }
                            )
                        }
                        response?.results?.forEach { slice ->
                            if (slice.tracks.isNotEmpty()) {
                                item(key = "header-${slice.source.name}") {
                                    SectionHeader(slice.source.label, slice.tracks.size)
                                }
                                items(slice.tracks, key = { it.id }) { track ->
                                    TrackRow(
                                        track = track,
                                        task = downloads.firstOrNull { it.track.id == track.id },
                                        onDownload = { download(track) },
                                        onCancelDownload = { DownloadService.cancel(context) }
                                    )
                                }
                            }
                        }
                        response?.results?.filter { it.error != null }?.forEach { slice ->
                            item(key = "error-${slice.source.name}") {
                                Text(
                                    slice.error ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        item(key = "spacer") { Spacer(Modifier.height(12.dp)) }
                    }
                }
            }
        }

        notice?.let { message ->
            Surface(
                Modifier.fillMaxWidth().padding(bottom = bottomPadding),
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
    }
}

/* --------------------------------- sub views -------------------------------- */

@Composable
private fun SectionHeader(label: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TopResultCard(track: Track, onDownload: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable { onDownload() }
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "TOP RESULT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(track, size = 84.dp, radius = 20.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SourceBadge(track)
                        track.durationSec.takeIf { it > 0 }?.let {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                formatDuration(it),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = onDownload,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Download ${track.title}")
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDownload) { Text("Download") }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    task: DownloadTask?,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit
) {
    val busy = task != null && DownloadStore.isActive(task.status)
    val done = task?.status == DownloadStatus.COMPLETED
    val favourites by LibraryStore.favourites.collectAsState()
    val isFavourite = favourites.any { it.id == track.id }
    var menuOpen by remember { mutableStateOf(false) }
    var showPlaylist by remember { mutableStateOf(false) }

    if (showPlaylist) {
        AddToPlaylistDialog(track = track, onDismiss = { showPlaylist = false })
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(track, size = 54.dp, radius = 15.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        track.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(3.dp))
                    SourceBadge(track)
                }
                IconButton(
                    onClick = { if (busy) onCancelDownload() else onDownload() },
                    enabled = !done
                ) {
                    when {
                        busy -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        done -> Icon(Icons.Default.DownloadDone, contentDescription = "Downloaded")
                        else -> Icon(Icons.Default.Download, contentDescription = "Download ${track.title}")
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (isFavourite) "Remove favourite" else "Favourite") },
                            leadingIcon = {
                                Icon(
                                    if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = null
                                )
                            },
                            onClick = { menuOpen = false; LibraryStore.toggleFavourite(track) }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to playlist") },
                            leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                            onClick = { menuOpen = false; showPlaylist = true }
                        )
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
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (task?.status == DownloadStatus.PAUSED) "Paused · tap the icon to cancel"
                        else "Downloading… tap the icon to cancel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun Artwork(track: Track, size: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp) {
    // Shimmers until the cover has actually loaded, then fades in.
    SkeletonImage(
        model = track.artworkUrl,
        contentDescription = "${track.title} artwork",
        shape = RoundedCornerShape(radius),
        fallback = Icons.Default.MusicNote,
        modifier = Modifier.size(size)
    )
}

/** Placeholder rows shown while a search is in flight. */
@Composable
private fun SearchSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(5) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SkeletonBox(Modifier.size(54.dp), RoundedCornerShape(15.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonLine(width = 170.dp, height = 14.dp)
                        SkeletonLine(width = 110.dp, height = 11.dp)
                    }
                    SkeletonBox(Modifier.size(40.dp), RoundedCornerShape(20.dp))
                }
            }
        }
    }
}

@Composable
private fun SourceBadge(track: Track) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            track.source.label + (track.bitrateKbps?.let { " · ${it}kbps" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun LoadingState() {
    val pulse by animateFloatAsState(1f, label = "pulse")
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(
            "Searching JioSaavn and YouTube…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { alpha = 0.6f + 0.4f * pulse }
        )
    }
}

@Composable
private fun EmptyState(query: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("No results for “$query”", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Check the spelling, try fewer words, or switch sources on above.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IdleState(
    recents: List<String>,
    onRecent: (String) -> Unit,
    onClearRecents: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text("Search everything at once", fontWeight = FontWeight.SemiBold)
                Text(
                    "JioSaavn and YouTube are queried together and merged into one list. Pick a source chip to narrow things down.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(visible = recents.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Recent searches",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClearRecents) { Text("Clear") }
                }
                recents.forEach { term ->
                    Surface(
                        onClick = { onRecent(term) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(term, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val total = seconds.coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
