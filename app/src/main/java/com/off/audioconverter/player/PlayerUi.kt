package com.off.audioconverter.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.off.audioconverter.library.LibraryStore
import com.off.audioconverter.lyrics.Lyrics
import com.off.audioconverter.lyrics.LyricsRepository
import com.off.audioconverter.lyrics.LyricsView
import com.off.audioconverter.music.Track
import kotlin.math.roundToInt

private fun formatMs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

private enum class PlayerPane { ART, LYRICS, EQ, QUEUE }

/**
 * Compact now-playing bar in the style BitChord uses: a slim row with small
 * artwork, a hairline progress line along the bottom, and transport glyphs that
 * fill the row's height. Tap to expand, swipe down to stop and dismiss.
 */
@Composable
fun MiniPlayer(
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track by PlayerController.current.collectAsState()
    val isPlaying by PlayerController.isPlaying.collectAsState()
    val position by PlayerController.positionMs.collectAsState()
    val duration by PlayerController.durationMs.collectAsState()
    val buffering by PlayerController.isBuffering.collectAsState()

    var dragOffset by remember { mutableFloatStateOf(0f) }
    val dismissThreshold = 150f

    AnimatedVisibility(
        visible = track != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, dragOffset.roundToInt()) }
                .graphicsLayer { alpha = 1f - (dragOffset / 420f).coerceIn(0f, 0.65f) }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffset > dismissThreshold) {
                                dragOffset = 0f
                                onDismiss()
                            } else {
                                dragOffset = 0f
                            }
                        },
                        onVerticalDrag = { _, delta -> dragOffset = (dragOffset + delta).coerceAtLeast(0f) }
                    )
                }
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(42.dp)
                    ) {
                        if (track?.artworkUrl != null) {
                            AsyncImage(
                                model = track?.artworkUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            track?.title ?: "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            track?.subtitle ?: "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { PlayerController.togglePlayPause() }, modifier = Modifier.size(40.dp)) {
                        PlayPauseGlyph(buffering = buffering, isPlaying = isPlaying, size = 30.dp)
                    }
                    IconButton(onClick = { PlayerController.next() }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(26.dp))
                    }
                }
                val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(2.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayPauseGlyph(
    buffering: Boolean,
    isPlaying: Boolean,
    size: androidx.compose.ui.unit.Dp,
    tint: Color = LocalContentColor.current
) {
    AnimatedContent(
        targetState = if (buffering) 2 else if (isPlaying) 1 else 0,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
        label = "play-pause"
    ) { state ->
        when (state) {
            2 -> CircularProgressIndicator(modifier = Modifier.size(size), strokeWidth = 2.dp, color = tint)
            1 -> Icon(Icons.Default.Pause, contentDescription = "Pause", tint = tint, modifier = Modifier.size(size))
            else -> Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = tint, modifier = Modifier.size(size))
        }
    }
}

/** Full-screen now-playing view. Slides up from the bottom; swipe down to close. */
@Composable
fun FullPlayerOverlay(
    visible: Boolean,
    onCollapse: () -> Unit,
    onDownload: (Track) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(160))
    ) {
        FullPlayerContent(onCollapse = onCollapse, onDownload = onDownload)
    }
}

@Composable
private fun FullPlayerContent(
    onCollapse: () -> Unit,
    onDownload: (Track) -> Unit
) {
    val track by PlayerController.current.collectAsState()
    val isPlaying by PlayerController.isPlaying.collectAsState()
    val position by PlayerController.positionMs.collectAsState()
    val duration by PlayerController.durationMs.collectAsState()
    val buffering by PlayerController.isBuffering.collectAsState()
    val hasNext by PlayerController.hasNext.collectAsState()
    val hasPrevious by PlayerController.hasPrevious.collectAsState()
    val favourites by LibraryStore.favourites.collectAsState()
    val queueTracks by PlayerController.queueTracks.collectAsState()
    val queueIndex by PlayerController.queueIndex.collectAsState()

    var scrub by remember { mutableStateOf<Float?>(null) }
    val shown = scrub ?: position.toFloat()

    var collapseDrag by remember { mutableFloatStateOf(0f) }
    var pane by remember { mutableStateOf(PlayerPane.ART) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf(Lyrics.EMPTY) }
    var lyricsLoading by remember { mutableStateOf(false) }

    LaunchedEffect(track?.id) {
        val current = track
        if (current == null) {
            lyrics = Lyrics.EMPTY
            return@LaunchedEffect
        }
        lyricsLoading = true
        val hint = if (duration > 0) duration / 1000 else current.durationSec
        lyrics = runCatching { LyricsRepository.fetch(current, hint) }.getOrDefault(Lyrics.EMPTY)
        lyricsLoading = false
    }

    val isFavourite = track?.let { t -> favourites.any { it.id == t.id } } == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.surfaceContainerHigh,
                    0.55f to MaterialTheme.colorScheme.surface,
                    1f to MaterialTheme.colorScheme.background
                )
            )
            .offset { IntOffset(0, collapseDrag.roundToInt()) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { if (collapseDrag > 160f) onCollapse() else collapseDrag = 0f },
                            onVerticalDrag = { _, delta -> collapseDrag = (collapseDrag + delta).coerceAtLeast(0f) }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close player", tint = Color.White)
                }
                Text(
                    "Now playing",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(
                    onClick = { track?.let { LibraryStore.toggleFavourite(it) } },
                    enabled = track != null
                ) {
                    Icon(
                        if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavourite) "Remove from favourites" else "Add to favourites",
                        tint = if (isFavourite) MaterialTheme.colorScheme.primary else Color.White
                    )
                }
                IconButton(onClick = { track?.let(onDownload) }, enabled = track != null) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download",
                        tint = Color.White.copy(alpha = if (track != null) 1f else 0.38f)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = pane,
                    transitionSpec = {
                        (fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 12 }) togetherWith
                            (fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 12 })
                    },
                    label = "player-pane"
                ) { current ->
                    when (current) {
                        PlayerPane.ART -> ArtworkPane(track)
                        PlayerPane.LYRICS -> LyricsView(
                            lyrics = lyrics,
                            positionMs = position,
                            loading = lyricsLoading,
                            onSeek = { PlayerController.seekTo(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                        PlayerPane.EQ -> EqualizerPane(Modifier.fillMaxSize())
                        PlayerPane.QUEUE -> QueuePane(
                            tracks = queueTracks,
                            currentIndex = queueIndex,
                            onPlay = { PlayerController.playAt(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                track?.title ?: "Nothing playing",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                track?.subtitle ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(16.dp))

            Slider(
                value = shown.coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                onValueChange = { scrub = it },
                onValueChangeFinished = {
                    scrub?.let { PlayerController.seekTo(it.toLong()) }
                    scrub = null
                },
                enabled = duration > 0,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMs(shown.toLong()), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                Text(formatMs(duration), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { PlayerController.previous() },
                    enabled = hasPrevious,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White.copy(alpha = if (hasPrevious) 1f else 0.38f),
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(Modifier.width(20.dp))
                FilledIconButton(
                    onClick = { PlayerController.togglePlayPause() },
                    modifier = Modifier.size(74.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    PlayPauseGlyph(
                        buffering = buffering,
                        isPlaying = isPlaying,
                        size = 34.dp,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(Modifier.width(20.dp))
                IconButton(
                    onClick = { PlayerController.next() },
                    enabled = hasNext,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White.copy(alpha = if (hasNext) 1f else 0.38f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PaneToggle(PlayerPane.LYRICS, pane, Icons.Default.Lyrics, "Lyrics") { pane = it }
                Spacer(Modifier.width(8.dp))
                PaneToggle(PlayerPane.EQ, pane, Icons.Default.Equalizer, "Equalizer") { pane = it }
                Spacer(Modifier.width(8.dp))
                PaneToggle(PlayerPane.QUEUE, pane, Icons.Default.QueueMusic, "Queue") { pane = it }
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(
                    onClick = { showPlaylistDialog = true },
                    enabled = track != null,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to playlist")
                }
            }

            Spacer(Modifier.height(8.dp))

            track?.let {
                Text(
                    "Source · ${it.source.label}" + (it.bitrateKbps?.let { q -> " · ${q}kbps" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }

    if (showPlaylistDialog) {
        track?.let { t ->
            AddToPlaylistDialog(track = t, onDismiss = { showPlaylistDialog = false })
        }
    }
}

@Composable
private fun PaneToggle(
    target: PlayerPane,
    current: PlayerPane,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onSelect: (PlayerPane) -> Unit
) {
    val active = current == target
    FilledTonalIconButton(
        onClick = { onSelect(if (active) PlayerPane.ART else target) },
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (active) Color.White else Color.White.copy(alpha = 0.12f),
            contentColor = if (active) MaterialTheme.colorScheme.onPrimary else Color.White
        )
    ) {
        Icon(icon, contentDescription = label)
    }
}

@Composable
private fun ArtworkPane(track: Track?) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxSize()
            .shadow(18.dp, RoundedCornerShape(28.dp))
    ) {
        if (track?.artworkUrl != null) {
            AsyncImage(
                model = track.artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp))
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(96.dp)
                )
            }
        }
    }
}

@Composable
private fun QueuePane(
    tracks: List<Track>,
    currentIndex: Int,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tracks.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "The queue is empty.",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }
    Column(modifier) {
        Text(
            "Up next · ${tracks.size} in queue",
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(tracks) { index, item ->
                val isCurrent = index == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCurrent) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { onPlay(index) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.10f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (item.artworkUrl != null) {
                            AsyncImage(model = item.artworkUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(9.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            item.artist,
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddToPlaylistDialog(track: Track, onDismiss: () -> Unit) {
    val playlists by LibraryStore.playlists.collectAsState()
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (playlists.isEmpty()) {
                    Text("No playlists yet — create one below.", style = MaterialTheme.typography.bodySmall)
                } else {
                    playlists.forEach { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    LibraryStore.addToPlaylist(playlist.name, track)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(playlist.name, modifier = Modifier.weight(1f))
                            Text("${playlist.tracks.size}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New playlist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val name = newName.trim()
                    if (name.isNotEmpty()) {
                        LibraryStore.createPlaylist(name)
                        LibraryStore.addToPlaylist(name, track)
                    }
                    onDismiss()
                },
                enabled = newName.trim().isNotEmpty()
            ) { Text("Create & add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
