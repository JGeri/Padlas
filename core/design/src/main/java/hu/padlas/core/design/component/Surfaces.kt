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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space

/**
 * No Material elevation. On a dark warm surface a blurred drop shadow reads as fog;
 * depth comes from a tight dark shadow, a wide soft one, and a 1px light on the top edge.
 */
fun Modifier.raisedSurface(
    shape: Shape = Shapes.card,
    background: Color = Ink.Surface,
    hairline: Color = Ink.Hairline,
    highlight: Color = Ink.Highlight,
): Modifier = this
    .drawBehind {
        // tight shadow
        drawRoundedShadow(shape, 6.dp.toPx(), Color.Black.copy(alpha = 0.55f), 2.dp.toPx())
        // wide soft shadow
        drawRoundedShadow(shape, 24.dp.toPx(), Color.Black.copy(alpha = 0.35f), 10.dp.toPx())
    }
    .background(background, shape)
    .drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, color = hairline, style = Stroke(width = 1f))
        drawOutline(
            outline,
            brush = Brush.verticalGradient(
                0f to highlight,
                0.06f to Color.Transparent,
                startY = 0f,
                endY = size.height,
            ),
            style = Stroke(width = 1.5f),
        )
    }

private fun DrawScope.drawRoundedShadow(
    shape: Shape,
    spread: Float,
    color: Color,
    offsetY: Float,
) {
    val outline = shape.createOutline(
        Size(size.width, size.height),
        layoutDirection,
        this,
    )
    translate(0f, offsetY) {
        drawOutline(outline, color = color.copy(alpha = color.alpha * 0.6f), style = Stroke(spread))
    }
}

@Composable
fun PadlasCard(
    modifier: Modifier = Modifier,
    shape: Shape = Shapes.card,
    background: Color = Ink.Surface,
    contentPadding: Dp = Space.l,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = motionSpec(Motion.snapSpring()),
        label = "cardPress",
    )
    Box(
        modifier = modifier
            .scale(scale)
            .raisedSurface(shape = shape, background = background)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
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
