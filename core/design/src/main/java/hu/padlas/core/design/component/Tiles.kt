package hu.padlas.core.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space

/**
 * In inverse mode everything starts selected, so selected has to be the quiet look and the
 * kept picture the loud one: a selected tile sits back a little behind a brass hairline,
 * while the one you rescued stays at full colour and full size.
 *
 * The image is a slot rather than a Coil model because core:design does not depend on Coil;
 * feature modules pass an AsyncImage lambda and keep the loader configuration with them.
 */
@Composable
fun SelectableTile(
    model: Any?,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onLongPress: (() -> Unit)? = null,
    image: @Composable (Modifier) -> Unit = { TilePlaceholder(it) },
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 0.94f else 1f,
        animationSpec = motionSpec(Motion.settle()),
        label = "tileScale",
    )
    val outline by animateColorAsState(
        targetValue = if (selected) Ink.Brass else Color.Transparent,
        animationSpec = motionSpec(Motion.settle()),
        label = "tileOutline",
    )
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(Shapes.card)
            .background(Ink.Surface)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClick = onLongPress,
                onClick = onToggle,
            ),
    ) {
        key(model) { image(Modifier.matchParentSize()) }
        if (selected) {
            Box(Modifier.matchParentSize().background(Ink.Void.copy(alpha = 0.42f)))
        }
        Box(Modifier.matchParentSize().hairlineOutline(outline, Shapes.card))
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(Space.s)
                    .background(Ink.Void.copy(alpha = 0.6f), Shapes.chip)
                    .padding(horizontal = Space.xs, vertical = 2.dp),
            ) {
                Text(
                    text = badge,
                    color = Ink.Paper,
                    style = PadlasTheme.type.micro,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * What is left behind after a lift. The grid deliberately does not re-flow around it: an
 * archived picture keeps its place so you keep your spatial memory of the screen.
 */
@Composable
fun GhostTile(
    model: Any?,
    modifier: Modifier = Modifier,
    image: @Composable (Modifier) -> Unit = { TilePlaceholder(it) },
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(Shapes.card),
    ) {
        key(model) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = 0.08f }) {
                image(Modifier.matchParentSize())
            }
        }
        Box(Modifier.matchParentSize().hairlineOutline(Ink.Patina, Shapes.card))
        AtticGlyph(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Space.s)
                .size(12.dp),
        )
    }
}

/** A roof with a hatch under it: the attic, drawn small enough to read as a mark. */
@Composable
private fun AtticGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 1f
        val eaves = size.height * 0.55f
        drawLine(Ink.Patina, Offset(0f, eaves), Offset(size.width / 2f, 0f), stroke, StrokeCap.Round)
        drawLine(Ink.Patina, Offset(size.width / 2f, 0f), Offset(size.width, eaves), stroke, StrokeCap.Round)
        drawLine(
            Ink.Patina,
            Offset(size.width * 0.3f, size.height * 0.85f),
            Offset(size.width * 0.7f, size.height * 0.85f),
            stroke,
            StrokeCap.Round,
        )
    }
}

/** Stand-in for the caller's image slot; a flat raised surface, never a shimmer. */
@Composable
internal fun TilePlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.background(Ink.Raised))
}
