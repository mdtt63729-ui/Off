package com.off.audioconverter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.off.audioconverter.ui.components.BouncyChipWide
import com.off.audioconverter.ui.components.SkeletonBox
import kotlin.math.abs
import kotlin.math.sin

/* ------------------------------------------------------------------ helpers */

private fun clock(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 24.dp, bottom = 12.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp
    )
}

@Composable
private fun OffCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content
    )
}

/**
 * The waveform, and the trim selection, in one control.
 *
 * The selected stretch is drawn in the accent colour and everything outside it in
 * grey, so the selection reads at a glance. Dragging picks up whichever edge is
 * nearer and moves it, which is how you set the trim range without leaving the
 * waveform.
 */
@Composable
private fun SelectionWaveform(
    trimStart: Float,
    trimEnd: Float,
    maxDuration: Float,
    progress: Float,
    onStartChange: (Float) -> Unit,
    onEndChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val bars = remember { List(96) { i -> (0.16f + abs(sin(i * 0.47)) * 0.58f + abs(sin(i * 0.19 + 1.4)) * 0.26f).toFloat() } }
    val accent = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.outline
    val playhead = MaterialTheme.colorScheme.onSurface

    // Deliberately not Compose state. Writing state from the gesture handler would
    // recompose mid-drag, and the handler must keep running while the finger is down.
    val dragHandle = remember { intArrayOf(-1) }

    // The pointer-input handler is created once and is not re-created as the trim
    // values change, so reading `trimStart`/`trimEnd` directly would give the values
    // from the first composition. That is exactly what made dragging one edge snap the
    // other edge back to where it was: the handler echoed a stale copy of it.
    val latestStart = rememberUpdatedState(trimStart)
    val latestEnd = rememberUpdatedState(trimEnd)

    Canvas(
        modifier = modifier.pointerInput(maxDuration) {
            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    // Pick up whichever edge is nearer the finger.
                    val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f) * maxDuration
                    dragHandle[0] =
                        if (abs(fraction - latestStart.value) <= abs(fraction - latestEnd.value)) 0 else 1
                },
                onDragEnd = { dragHandle[0] = -1 },
                onDragCancel = { dragHandle[0] = -1 },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f) * maxDuration
                    // Only ever move the edge being dragged. The screen clamps it, so
                    // the other edge is never written from here at all.
                    when (dragHandle[0]) {
                        0 -> onStartChange(fraction)
                        1 -> onEndChange(fraction)
                    }
                }
            )
        }
    ) {
        val gap = 3.dp.toPx()
        val barWidth = (size.width - gap * (bars.size - 1)) / bars.size
        val mid = size.height / 2f

        bars.forEachIndexed { index, height ->
            val fraction = index.toFloat() / (bars.size - 1).toFloat() * maxDuration
            val inside = fraction >= trimStart && fraction <= trimEnd
            val x = index * (barWidth + gap) + barWidth / 2f
            val barHeight = size.height * height
            drawLine(
                color = if (inside) accent else idle,
                start = Offset(x, mid - barHeight / 2f),
                end = Offset(x, mid + barHeight / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }

        // The two trim handles.
        listOf(trimStart, trimEnd).forEach { edge ->
            val x = (edge / maxDuration).coerceIn(0f, 1f) * size.width
            drawLine(
                color = accent,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Playhead.
        val head = progress.coerceIn(0f, 1f) * size.width
        drawLine(
            color = playhead,
            start = Offset(head, 0f),
            end = Offset(head, size.height),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

/** The fade envelope preview on the Fade screen. */
@Composable
private fun FadeWave(fadeIn: Float, fadeOut: Float, modifier: Modifier = Modifier) {
    val bars = remember { List(56) { i -> (0.2f + abs(sin(i * 0.63)) * 0.75f).toFloat() } }
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val gap = 3.dp.toPx()
        val barWidth = (size.width - gap * (bars.size - 1)) / bars.size
        val mid = size.height / 2f
        bars.forEachIndexed { index, height ->
            val p = index.toFloat() / (bars.size - 1).toFloat()
            val inFactor = if (fadeIn <= 0f) 1f else (p / (fadeIn / 30f)).coerceIn(0.08f, 1f)
            val outFactor = if (fadeOut <= 0f) 1f else ((1f - p) / (fadeOut / 30f)).coerceIn(0.08f, 1f)
            val barHeight = size.height * height * inFactor * outFactor
            val x = index * (barWidth + gap) + barWidth / 2f
            drawLine(
                color = if (p < 0.18f || p > 0.82f) accent else muted,
                start = Offset(x, mid - barHeight / 2f),
                end = Offset(x, mid + barHeight / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun ToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(17.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(39.dp).clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, maxLines = 1)
    }
}

/* ------------------------------------------------------------------- editor */

@Composable
fun OffEditorScreen(
    contentPadding: PaddingValues,
    artwork: ImageBitmap?,
    artworkResolved: Boolean,
    name: String,
    duration: Int,
    bitrate: Int,
    onBitrate: (Int) -> Unit,
    trimStart: Float,
    trimEnd: Float,
    speed: Float,
    fadeIn: Float,
    fadeOut: Float,
    playing: Boolean,
    positionMs: Long,
    volume: Float,
    onVolume: (Float) -> Unit,
    onTrimStart: (Float) -> Unit,
    onTrimEnd: (Float) -> Unit,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onTrim: () -> Unit,
    onSpeed: () -> Unit,
    onFade: () -> Unit,
    onReset: () -> Unit,
    onExport: () -> Unit
) {
    val maxDuration = duration.coerceAtLeast(1).toFloat()
    val totalMs = (trimEnd * 1000).toLong().coerceAtLeast(1L)
    val played = (positionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    val frameAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (artwork != null) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(420),
        label = "frame-fade"
    )
    // While the user has hold of the slider its own value wins; otherwise the playback
    // ticker keeps overwriting it and the thumb jumps under the finger.
    var scrubbing by remember { mutableStateOf<Float?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = 20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(92.dp).clip(RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    artwork != null -> Image(
                        bitmap = artwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = frameAlpha }
                    )
                    !artworkResolved -> SkeletonBox(Modifier.fillMaxSize(), RoundedCornerShape(20.dp))
                    else -> Box(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.VideoFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(15.dp))
            Column {
                Text(
                    name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
                Text("Audio file", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${clock(duration.toFloat())}     ·     $bitrate kbps",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 9.dp)
                )
            }
        }

        Label("WAVEFORM")
        SelectionWaveform(
            trimStart = trimStart,
            trimEnd = trimEnd,
            maxDuration = maxDuration,
            progress = played,
            onStartChange = onTrimStart,
            onEndChange = onTrimEnd,
            modifier = Modifier.fillMaxWidth().height(100.dp).padding(horizontal = 22.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Drag on the waveform to select the part you keep — the rest is greyed out.",
            modifier = Modifier.padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )

        Spacer(Modifier.height(8.dp))
        val sliderMax = totalMs.toFloat().coerceAtLeast(1f)
        Slider(
            value = (scrubbing ?: positionMs.toFloat()).coerceIn(0f, sliderMax),
            onValueChange = {
                scrubbing = it
                onSeek(it.toLong())
            },
            onValueChangeFinished = { scrubbing = null },
            valueRange = 0f..sliderMax,
            modifier = Modifier.padding(horizontal = 22.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline
            )
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                clock((scrubbing ?: positionMs.toFloat()) / 1000f),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp
            )
            Text(
                "Selected ${clock(trimEnd - trimStart)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Text(clock(trimEnd), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }

        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "−15s",
                modifier = Modifier.clickable { onSeek((positionMs - 15_000).coerceAtLeast(0)) }.padding(14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(62.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause preview" else "Play preview",
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                "+15s",
                modifier = Modifier.clickable { onSeek((positionMs + 15_000).coerceAtMost(totalMs)) }.padding(14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Slider(
                value = volume,
                onValueChange = onVolume,
                valueRange = 0f..1.5f,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(Modifier.width(10.dp))
            Text("${(volume * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }

        Spacer(Modifier.height(25.dp))
        Label("TOOLS")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            ToolCard("Trim", "${clock(trimEnd - trimStart)}", Icons.Default.ContentCut, Modifier.weight(1f), onTrim)
            ToolCard("Speed", "${"%.2f".format(speed)}×", Icons.Default.Speed, Modifier.weight(1f), onSpeed)
            ToolCard(
                "Fade",
                if (fadeIn > 0f || fadeOut > 0f) "Customized" else "In & out",
                Icons.Default.GraphicEq,
                Modifier.weight(1f),
                onFade
            )
            ToolCard("Reset", "Clear edits", Icons.Default.RestartAlt, Modifier.weight(1f), onReset)
        }

        Spacer(Modifier.height(24.dp))
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Export audio", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        Text(
                            "MP3 · $bitrate kbps",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = onExport,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) { Text("Export") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(128, 192, 256, 320).forEach { kbps ->
                        BouncyChipWide(
                            selected = bitrate == kbps,
                            onClick = { onBitrate(kbps) },
                            label = "$kbps",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/* --------------------------------------------------------------------- trim */

@Composable
fun TrimScreen(
    contentPadding: PaddingValues,
    duration: Int,
    trimStart: Float,
    trimEnd: Float,
    onTrimStart: (Float) -> Unit,
    onTrimEnd: (Float) -> Unit,
    onSelectAll: () -> Unit,
    onNotify: (String) -> Unit
) {
    val maxDuration = duration.coerceAtLeast(1).toFloat()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding)
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text("Drag either edge of the selection.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            OffCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(17.dp)) {
                    Label("SELECTION")
                    SelectionWaveform(
                        trimStart = trimStart,
                        trimEnd = trimEnd,
                        maxDuration = maxDuration,
                        progress = 0f,
                        onStartChange = onTrimStart,
                        onEndChange = onTrimEnd,
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Start · ${clock(trimStart)}", color = MaterialTheme.colorScheme.onSurface)
                        Text("End · ${clock(trimEnd)}", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onSelectAll(); onNotify("Full length selected") },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) { Text("Select all") }
                Button(
                    onClick = { onNotify("Trim saved") },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) { Text("Save trim", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/* -------------------------------------------------------------------- speed */

@Composable
fun SpeedScreen(
    contentPadding: PaddingValues,
    speed: Float,
    preservePitch: Boolean,
    onSpeed: (Float) -> Unit,
    onPreservePitch: (Boolean) -> Unit,
    onNotify: (String) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding)
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text("Adjust the pace", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(22.dp))
            OffCard(Modifier.fillMaxWidth(), RoundedCornerShape(26.dp)) {
                Column(
                    Modifier.padding(22.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(72.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "%.2f×".format(speed),
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text("playback speed", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Spacer(Modifier.height(20.dp))
                    Slider(
                        value = speed,
                        onValueChange = onSpeed,
                        valueRange = 0.25f..2f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("0.25×", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Text("1.0× normal", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Text("2.0×", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Label("QUICK PRESETS")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                    BouncyChipWide(
                        selected = abs(speed - value) < 0.001f,
                        onClick = { onSpeed(value) },
                        label = "${value}×",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            OffCard(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Preserve pitch", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                        Text(
                            "Keep the voice tone natural",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = preservePitch,
                        onCheckedChange = onPreservePitch,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onSpeed(1f); onPreservePitch(true); onNotify("Speed reset to 1.00×") },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) { Text("Reset") }
                Button(
                    onClick = { onNotify("Playback speed applied") },
                    modifier = Modifier.weight(1.25f).height(54.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Apply speed", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/* --------------------------------------------------------------------- fade */

@Composable
fun FadeScreen(
    contentPadding: PaddingValues,
    fadeIn: Float,
    fadeOut: Float,
    curve: String,
    onFadeIn: (Float) -> Unit,
    onFadeOut: (Float) -> Unit,
    onCurve: (String) -> Unit,
    onNotify: (String) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding)
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text("Make transitions sound natural", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            OffCard(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Label("PREVIEW")
                    FadeWave(fadeIn, fadeOut, Modifier.fillMaxWidth().height(68.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Start", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Text("Audio waveform", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                        Text("End", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            FadeSliderCard("Fade in", "Gradually raise volume at the start", fadeIn, onFadeIn)
            Spacer(Modifier.height(13.dp))
            FadeSliderCard("Fade out", "Gradually lower volume at the end", fadeOut, onFadeOut)

            Spacer(Modifier.height(22.dp))
            Label("FADE CURVE")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Linear", "Smooth", "Exponential").forEach { value ->
                    BouncyChipWide(
                        selected = curve == value,
                        onClick = { onCurve(value) },
                        label = value,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onFadeIn(0f); onFadeOut(0f); onNotify("Fades cleared") },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) { Text("Clear fades") }
                Button(
                    onClick = { onNotify("Fade settings applied") },
                    modifier = Modifier.weight(1.25f).height(54.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Apply fades", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FadeSliderCard(title: String, description: String, value: Float, onChange: (Float) -> Unit) {
    OffCard(Modifier.fillMaxWidth(), RoundedCornerShape(19.dp)) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Text(
                    if (value <= 0f) "Off" else "${value.toInt()}s",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = 0f..10f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0s", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Text("10s", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

/* -------------------------------------------------------------------- reset */

@Composable
fun ResetScreen(
    contentPadding: PaddingValues,
    onResetAll: () -> Unit,
    onNotify: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(96.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.RestartAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "Start fresh?",
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(9.dp))
            Text(
                "Your editing settings will return to their original values.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(26.dp))
            OffCard(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    ResetItem("Playback speed", "Return to 1.0×")
                    ResetItem("Fade effects", "Turn fade in and fade out off")
                    ResetItem("Trim selection", "Clear the current selection")
                    ResetItem("Original audio", "Keep your source file safe", accent = true)
                }
            }
        }
        Button(
            onClick = { onResetAll(); onNotify("Edits reset") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(55.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Default.RestartAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Reset all edits", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ResetItem(title: String, description: String, accent: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(33.dp).clip(CircleShape).background(
                if (accent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}
