package com.off.audioconverter.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Synced lyrics panel.
 *
 * The line being sung is lifted, brightened and swept left-to-right in time with
 * the audio (a karaoke wipe driven by [LyricLine.progressAt]); everything else
 * sits back at a lower opacity. The list auto-scrolls to keep the active line
 * about a third of the way down, and tapping any line seeks there.
 */
@Composable
fun LyricsView(
    lyrics: Lyrics,
    positionMs: Long,
    loading: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        loading -> CenteredMessage(modifier) {
            CircularProgressIndicator(color = Color.White)
            Spacer(Modifier.height(14.dp))
            Text(
                "Looking for lyrics…",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        lyrics.instrumental -> CenteredMessage(modifier) {
            Text("Instrumental", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "This track has no lyrics.",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        lyrics.isEmpty -> CenteredMessage(modifier) {
            Text("No lyrics found", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Nothing matched for this track. Titles from search are matched by name, so a different edit may exist.",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        else -> SyncedLyrics(
            lyrics = lyrics,
            positionMs = positionMs,
            onSeek = onSeek,
            modifier = modifier
        )
    }
}

@Composable
private fun SyncedLyrics(
    lyrics: Lyrics,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val activeIndex = lyrics.activeIndex(positionMs)

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && lyrics.synced) {
            val viewport = listState.layoutInfo.viewportSize.height
            val offset = if (viewport > 0) -(viewport / 3) else 0
            runCatching { listState.animateScrollToItem(activeIndex + 1, offset) }
        }
    }

    Column(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item(key = "lead") { Spacer(Modifier.height(72.dp)) }
            itemsIndexed(lyrics.lines) { index, line ->
                LyricRow(
                    text = line.text.ifBlank { "♪" },
                    isActive = lyrics.synced && index == activeIndex,
                    progress = if (lyrics.synced && index == activeIndex) line.progressAt(positionMs) else 0f,
                    onClick = { if (lyrics.synced) onSeek(line.startMs) }
                )
            }
            item(key = "tail") { Spacer(Modifier.height(96.dp)) }
        }
        Text(
            if (lyrics.synced) "Lyrics · ${lyrics.source}" else "Lyrics · ${lyrics.source} (not time-synced)",
            color = Color.White.copy(alpha = 0.45f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LyricRow(
    text: String,
    isActive: Boolean,
    progress: Float,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "lyric-scale"
    )
    val baseColor by animateColorAsState(
        targetValue = if (isActive) Color.White.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.55f),
        animationSpec = tween(220),
        label = "lyric-base"
    )
    val weight = if (isActive) FontWeight.Bold else FontWeight.Medium
    val style = MaterialTheme.typography.titleMedium

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
    ) {
        Text(text, color = baseColor, style = style, fontWeight = weight)

        if (isActive) {
            Text(
                text,
                color = Color.White,
                style = style,
                fontWeight = weight,
                modifier = Modifier.drawWithContent {
                    if (progress >= 1f) {
                        drawContent()
                    } else {
                        clipRect(right = size.width * progress.coerceIn(0f, 1f)) {
                            this@drawWithContent.drawContent()
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun CenteredMessage(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) { content() }
    }
}
