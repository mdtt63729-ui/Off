package com.off.audioconverter.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.floor

data class NavItem(val label: String, val icon: ImageVector)

/**
 * A floating, pill-shaped navigation bar.
 *
 * One animated value — the fractional position of the selection — drives
 * everything: each slot's width, the label fades and the indicator itself. That is
 * what makes the shift seamless. Animating the slots and the pill separately (as
 * this used to) lets them drift a frame apart, and drawing a pill per item put two
 * of them on screen mid-switch.
 *
 * The row always fills the bar, so the bar itself never jumps around while the
 * selection grows one slot and shrinks another.
 */
@Composable
fun FloatingNavBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    val labelWidths = items.map { item ->
        with(density) { measurer.measure(item.label, labelStyle).size.width.toDp().value }
    }

    val safeSelected = selectedIndex.coerceIn(0, items.lastIndex)

    val progress by animateFloatAsState(
        targetValue = safeSelected.toFloat(),
        // Enough bounce to feel alive, not so much that the label overshoots its slot.
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "nav-progress"
    )

    // How far open each slot is, as a smooth triangle centred on the selection.
    val expand = items.indices.map { i -> (1f - abs(progress - i)).coerceIn(0f, 1f) }
    val weights = items.indices.map { i ->
        ICON_SLOT + (labelWidths[i] + LABEL_GAP) * expand[i]
    }
    val totalWeight = weights.sum().takeIf { it > 0f } ?: 1f

    val lower = floor(progress).toInt().coerceIn(0, items.lastIndex)
    val upper = (lower + 1).coerceAtMost(items.lastIndex)
    val fraction = (progress - lower).coerceIn(0f, 1f)

    Surface(
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 14.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val slotWidths = weights.map { maxWidth * (it / totalWeight) }
            val before = slotWidths.take(lower).fold(0.dp) { acc, w -> acc + w }
            val pillOffset = before + slotWidths[lower] * fraction
            val pillWidth = slotWidths[lower] + (slotWidths[upper] - slotWidths[lower]) * fraction

            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = pillOffset)
                    .width(pillWidth)
                    .height(PILL_HEIGHT)
                    .clip(RoundedCornerShape(PILL_HEIGHT / 2))
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { index, item ->
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val selected = index == safeSelected
                    val open = expand[index]

                    val pressScale by animateFloatAsState(
                        targetValue = if (pressed) 0.92f else 1f,
                        animationSpec = spring(dampingRatio = 0.40f, stiffness = Spring.StiffnessHigh),
                        label = "nav-press-$index"
                    )
                    val content by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                        label = "nav-content-$index"
                    )

                    Row(
                        Modifier
                            .weight(weights[index])
                            .height(PILL_HEIGHT)
                            .clipToBounds()
                            .graphicsLayer {
                                scaleX = pressScale
                                scaleY = pressScale
                            }
                            .selectable(
                                selected = selected,
                                interactionSource = interaction,
                                indication = null
                            ) { onSelect(index) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.width(ICON_SLOT.dp).fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                tint = content,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            item.label,
                            color = content,
                            style = labelStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.graphicsLayer {
                                // Fades and slides in behind the arriving pill.
                                alpha = ((open - 0.3f) / 0.7f).coerceIn(0f, 1f)
                                translationX = (1f - open) * 30f
                            }
                        )
                    }
                }
            }
        }
    }
}

private const val ICON_SLOT = 56f
private const val LABEL_GAP = 8f
private val PILL_HEIGHT = 44.dp
