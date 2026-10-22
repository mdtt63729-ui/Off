package com.off.audioconverter.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class NavItem(val label: String, val icon: ImageVector)

/**
 * A floating, pill-shaped navigation bar.
 *
 * The selected item's pill is a SINGLE box that slides and resizes between items.
 * Drawing one pill per item (as this used to) meant that mid-switch two pills were
 * on screen at once, overlapping, with both labels fading across each other — the
 * garbled "Studio/Search" text you could see while switching.
 *
 * Each item's width is driven by an animated value that this composable owns, and
 * the pill's offset is the running sum of those same values, so the pill tracks the
 * items exactly instead of chasing a re-layout.
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

    // Measure each label once so the selected item can reserve exactly the right width.
    val labelWidths: List<Dp> = items.map { item ->
        with(density) { measurer.measure(item.label, labelStyle).size.width.toDp() }
    }

    val widthSpring = spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    // Every item animates between an icon-only slot and an icon + label slot.
    val itemWidths: List<Dp> = items.indices.map { index ->
        val target = if (index == selectedIndex) labelWidths[index] + ICON_SLOT + LABEL_GAP else ICON_SLOT
        animateDpAsState(targetValue = target, animationSpec = widthSpring, label = "nav-width-$index").value
    }

    val safeSelected = selectedIndex.coerceIn(0, items.lastIndex)
    val pillOffset = itemWidths.take(safeSelected).fold(0.dp) { acc, w -> acc + w }
    val pillWidth = itemWidths[safeSelected]

    Surface(
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 14.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box {
                // The one and only indicator.
                Box(
                    modifier = Modifier
                        .offset(x = pillOffset)
                        .width(pillWidth)
                        .height(PILL_HEIGHT)
                        .clip(RoundedCornerShape(PILL_HEIGHT / 2))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    items.forEachIndexed { index, item ->
                        val selected = index == safeSelected
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()

                        val pressScale by animateFloatAsState(
                            targetValue = if (pressed) 0.93f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessHigh),
                            label = "nav-press"
                        )
                        val content by animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "nav-content"
                        )

                        Row(
                            modifier = Modifier
                                .width(itemWidths[index])
                                .height(PILL_HEIGHT)
                                // Clip so the label cannot spill out while the slot shrinks.
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                tint = content,
                                modifier = Modifier.size(22.dp)
                            )
                            AnimatedVisibility(
                                visible = selected,
                                enter = fadeIn(tween(160)),
                                exit = fadeOut(tween(90))
                            ) {
                                Text(
                                    item.label,
                                    color = content,
                                    style = labelStyle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    modifier = Modifier.padding(start = LABEL_GAP)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val ICON_SLOT = 56.dp
private val LABEL_GAP = 8.dp
private val PILL_HEIGHT = 44.dp
