package com.off.audioconverter.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter

/**
 * A shimmering placeholder. Used wherever something is still loading, so a screen
 * shows the shape of what is coming instead of an empty gap or a spinner.
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest

    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeleton-sweep"
    )

    Box(
        modifier
            .clip(shape)
            .drawBehind {
                drawRect(color = base)
                val width = size.width.coerceAtLeast(1f)
                val sweep = width * 0.55f
                val startX = -sweep + progress * (width + 2f * sweep)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(base, highlight, base),
                        start = Offset(startX, 0f),
                        end = Offset(startX + sweep, 0f)
                    )
                )
            }
    )
}

/** A placeholder for one line of text. */
@Composable
fun SkeletonLine(
    width: Dp = 120.dp,
    height: Dp = 12.dp,
    shape: Shape = RoundedCornerShape(6.dp)
) {
    SkeletonBox(Modifier.width(width).height(height), shape)
}

/**
 * An image that shows a skeleton until it has actually loaded, then fades in.
 *
 * [fallback] is drawn if the image fails, so a broken URL does not leave a
 * shimmer running forever.
 */
@Composable
fun SkeletonImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    contentScale: ContentScale = ContentScale.Crop,
    fallback: ImageVector? = null
) {
    val painter = rememberAsyncImagePainter(model = model)
    val state = painter.state
    val loaded = state is AsyncImagePainter.State.Success
    val fade by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "image-fade"
    )

    Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = !loaded,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(tween(0)),
            exit = fadeOut(tween(durationMillis = 320))
        ) {
            SkeletonBox(Modifier.fillMaxSize(), shape)
        }

        if (state is AsyncImagePainter.State.Error && fallback != null) {
            Icon(
                fallback,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Image(
            painter = painter,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = fade }
        )
    }
}
