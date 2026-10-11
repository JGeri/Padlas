package hu.padlas.core.design.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space

/**
 * A grid tile that can be selected. The selected look is the familiar one from every
 * gallery: a filled check in the corner and the picture pulled in slightly.
 *
 * The image is a slot because core:design does not depend on Coil; feature modules pass an
 * AsyncImage lambda.
 */
@Composable
fun SelectableTile(
    model: Any?,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onLongPress: (() -> Unit)? = null,
    selectionMode: Boolean = true,
    selectedTint: Color = Ink.Accent,
    image: @Composable (Modifier) -> Unit = { TilePlaceholder(it) },
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 0.9f else 1f,
        animationSpec = motionSpec(Motion.settle()),
        label = "tileScale",
    )
    val outline by animateColorAsState(
        targetValue = if (selected) selectedTint else Color.Transparent,
        animationSpec = motionSpec(Motion.settle()),
        label = "tileOutline",
    )
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(Shapes.small)
            .background(if (selected) selectedTint.copy(alpha = 0.18f) else Color.Transparent)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClick = onLongPress,
                onClick = onToggle,
            ),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .scale(scale)
                .clip(Shapes.small)
                .background(Ink.SurfaceHigh)
                .border(2.dp, outline, Shapes.small),
        ) {
            key(model) { image(Modifier.matchParentSize()) }
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(Shapes.pill)
                    .background(Ink.Background.copy(alpha = 0.65f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(text = badge, color = Ink.Text, style = PadlasTheme.type.micro, maxLines = 1)
            }
        }
        if (selectionMode) {
            SelectionMark(
                selected = selected,
                tint = selectedTint,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            )
        }
    }
}

@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier, tint: Color = Ink.Accent) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(Shapes.pill)
            .background(if (selected) tint else Ink.Background.copy(alpha = 0.35f))
            .border(1.5.dp, if (selected) tint else Color.White.copy(alpha = 0.85f), Shapes.pill),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(finiteSpec(LocalReducedMotion.current, Motion.snapback())) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
    }
}

/** What stays behind once the file only lives in the cloud. */
@Composable
fun GhostTile(
    model: Any?,
    modifier: Modifier = Modifier,
    image: @Composable (Modifier) -> Unit = { TilePlaceholder(it) },
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(Shapes.small)
            .background(Ink.SurfaceHigh),
    ) {
        key(model) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = 0.35f }) {
                image(Modifier.matchParentSize())
            }
        }
        Icon(
            Icons.Rounded.CloudDone,
            contentDescription = null,
            tint = Ink.Info,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Space.s)
                .size(16.dp),
        )
    }
}

@Composable
internal fun TilePlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.background(Ink.SurfaceHigh))
}
