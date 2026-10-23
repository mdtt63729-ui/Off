package com.off.audioconverter.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A selectable chip that reacts the way a physical switch does.
 *
 * Picking one pops it slightly past its resting size and lets a soft spring settle
 * it back, so the choice registers as a bounce rather than a colour change. The
 * press state dips inwards first, and the colours cross-fade on a spring of their
 * own so nothing snaps.
 */
@Composable
fun BouncyChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // The chip pops past its size the instant it is picked and settles straight back
    // to 1.0. It used to sit permanently 7% larger while selected, which meant the two
    // chips were always at different sizes and the selected one could overlap its
    // neighbour for the whole time it stayed selected.
    val scale = remember { Animatable(1f) }
    LaunchedEffect(selected, pressed, enabled) {
        when {
            !enabled -> scale.animateTo(1f, spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow))
            pressed -> scale.animateTo(0.94f, spring(dampingRatio = 0.60f, stiffness = Spring.StiffnessHigh))
            selected -> {
                // A low damping ratio is what makes the pop overshoot before it settles.
                scale.animateTo(1.06f, spring(dampingRatio = 0.34f, stiffness = Spring.StiffnessMedium))
                scale.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
            }
            else -> scale.animateTo(1f, spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    val container by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "chip-container"
    )

    val content by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            selected -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "chip-content"
    )

    val outline by animateColorAsState(
        targetValue = if (selected || !enabled) Color.Transparent
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "chip-outline"
    )

    // The icon settles in a beat behind the chip, which reads as the bounce carrying through.
    val iconScale by animateFloatAsState(
        targetValue = if (selected && leadingIcon != null) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "chip-icon"
    )

    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(shape)
            .background(container)
            .border(1.dp, outline, shape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                )
            }
            Text(
                label,
                color = content,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The same bounce for a chip that fills the width it is given — used for preset
 * grids and curve pickers, where the chips sit in an even row.
 */
@Composable
fun BouncyChipWide(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scale = remember { Animatable(1f) }
    LaunchedEffect(selected, pressed, enabled) {
        when {
            !enabled -> scale.animateTo(1f, spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow))
            pressed -> scale.animateTo(0.95f, spring(dampingRatio = 0.60f, stiffness = Spring.StiffnessHigh))
            selected -> {
                scale.animateTo(1.04f, spring(dampingRatio = 0.36f, stiffness = Spring.StiffnessMedium))
                scale.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
            }
            else -> scale.animateTo(1f, spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "wide-chip-container"
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "wide-chip-content"
    )
    val outline by animateColorAsState(
        targetValue = if (selected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "wide-chip-outline"
    )

    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(shape)
            .background(container)
            .border(1.dp, outline, shape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = content,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
