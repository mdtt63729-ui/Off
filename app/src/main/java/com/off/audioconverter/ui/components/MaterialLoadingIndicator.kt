package com.off.audioconverter.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.loadingindicator.LoadingIndicator

/**
 * The Material Components `LoadingIndicator` — the M3 Expressive indicator whose
 * shape morphs and rotates — hosted inside Compose.
 *
 * Compose's own `LoadingIndicator` only exists from material3 1.5.0-beta, which
 * requires compileSdk 37; the Views component has no such requirement and is the
 * exact one from the Material Components catalogue.
 */
@Composable
fun MaterialLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color,
    size: Dp = 22.dp
) {
    AndroidView(
        factory = { context ->
            LoadingIndicator(context).apply {
                indicatorSize = (size.value * context.resources.displayMetrics.density).toInt()
                setIndicatorColor(color.toArgb())
            }
        },
        update = { view ->
            view.indicatorSize = (size.value * view.resources.displayMetrics.density).toInt()
            view.setIndicatorColor(color.toArgb())
        },
        modifier = modifier.size(size)
    )
}
