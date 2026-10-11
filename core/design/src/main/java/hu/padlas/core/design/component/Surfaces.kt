package hu.padlas.core.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space

/** Kept for callers of the first design: every raised surface is now glass. */
fun Modifier.raisedSurface(
    shape: Shape = Shapes.card,
    background: Color = Ink.Surface,
    hairline: Color = Ink.Hairline,
    @Suppress("UNUSED_PARAMETER") highlight: Color = Ink.Highlight,
): Modifier = glass(shape = shape, fill = background, rim = hairline)

@Composable
fun PadlasCard(
    modifier: Modifier = Modifier,
    shape: Shape = Shapes.card,
    background: Color = Ink.Surface,
    contentPadding: Dp = Space.l,
    onClick: (() -> Unit)? = null,
    border: Color = Ink.Hairline,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = motionSpec(Motion.rest()),
        label = "cardPress",
    )
    Box(
        modifier = modifier
            .scale(scale)
            .raisedSurface(shape = shape, background = background, hairline = border)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        content = content,
    )
}

/** A 1px rule, the only divider the app uses. */
fun Modifier.hairlineBottom(color: Color = Ink.Hairline): Modifier = drawBehind {
    drawLine(
        color = color,
        start = Offset(0f, size.height),
        end = Offset(size.width, size.height),
        strokeWidth = 1f,
    )
}

fun Modifier.hairlineTop(color: Color = Ink.Hairline): Modifier = drawBehind {
    drawLine(color = color, start = Offset(0f, 0f), end = Offset(size.width, 0f), strokeWidth = 1f)
}
