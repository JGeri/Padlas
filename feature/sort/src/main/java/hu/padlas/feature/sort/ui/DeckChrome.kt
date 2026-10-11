package hu.padlas.feature.sort.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink

/**
 * The bin is a physical tray, not an icon: the card has to land somewhere, and an undo
 * has to have somewhere to climb out of.
 */
@Composable
internal fun BinTray(active: Boolean, lid: () -> Float, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(
        targetValue = if (active) TargetScale else 1f,
        animationSpec = motionSpec(Motion.snapSpring<Float>()),
        label = "binScale",
    )
    val outline = if (active) Ink.Danger else Ink.TextTertiary

    Canvas(
        modifier = modifier
            .size(width = 104.dp, height = 66.dp)
            .scale(scale),
    ) {
        val width = size.width
        val height = size.height
        val body = Size(width, height * 0.66f)
        val bodyTop = Offset(0f, height * 0.34f)
        val corner = CornerRadius(7.dp.toPx())

        drawRoundRect(color = Ink.SurfaceHigh, topLeft = bodyTop, size = body, cornerRadius = corner)
        drawRoundRect(
            color = outline,
            topLeft = bodyTop,
            size = body,
            cornerRadius = corner,
            style = Stroke(width = 1f),
        )
        // Three slats, so the tray reads as a container even at this size.
        repeat(3) { index ->
            val x = width * (0.28f + 0.22f * index)
            drawLine(
                color = outline.copy(alpha = 0.45f),
                start = Offset(x, height * 0.48f),
                end = Offset(x, height * 0.86f),
                strokeWidth = 1f,
            )
        }

        // The lid: pinned at its left end, tilted down as anticipation before it shuts.
        rotate(degrees = -16f * lid(), pivot = Offset(width * 0.06f, height * 0.30f)) {
            drawRoundRect(
                color = outline,
                topLeft = Offset(width * 0.02f, height * 0.20f),
                size = Size(width * 0.96f, height * 0.11f),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }
    }
}

/** The slot at the top of the screen the archived card disappears through. */
@Composable
internal fun AtticHatch(active: Boolean, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(
        targetValue = if (active) TargetScale else 1f,
        animationSpec = motionSpec(Motion.snapSpring<Float>()),
        label = "hatchScale",
    )
    val stroke = if (active) Ink.Success else Ink.TextTertiary

    Canvas(
        modifier = modifier
            .size(width = 132.dp, height = 26.dp)
            .scale(scale),
    ) {
        val width = size.width
        val height = size.height
        drawRoundRect(
            color = Ink.Background,
            topLeft = Offset(0f, height * 0.36f),
            size = Size(width, height * 0.30f),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
        drawLine(
            color = stroke,
            start = Offset(0f, height * 0.36f),
            end = Offset(width, height * 0.36f),
            strokeWidth = 1f,
        )
        drawLine(
            color = stroke.copy(alpha = 0.4f),
            start = Offset(0f, height * 0.66f),
            end = Offset(width, height * 0.66f),
            strokeWidth = 1f,
        )
    }
}

/** Keep. A check drawn by hand, because the app owns no icon font. */
@Composable
internal fun KeepGlyph(active: Boolean, modifier: Modifier = Modifier) {
    GlyphCanvas(active, if (active) Ink.Accent else Ink.TextTertiary, modifier) { color ->
        val width = size.width
        val height = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(width * 0.22f, height * 0.52f)
            lineTo(width * 0.43f, height * 0.72f)
            lineTo(width * 0.80f, height * 0.28f)
        }
        drawPath(path, color = color, style = Stroke(width = 2.dp.toPx()))
    }
}

/** Decide later: the card is parked, so the glyph points down and stops. */
@Composable
internal fun LaterGlyph(active: Boolean, modifier: Modifier = Modifier) {
    GlyphCanvas(active, if (active) Ink.Text else Ink.TextTertiary, modifier) { color ->
        val width = size.width
        val height = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(width * 0.28f, height * 0.42f)
            lineTo(width * 0.50f, height * 0.62f)
            lineTo(width * 0.72f, height * 0.42f)
        }
        drawPath(path, color = color, style = Stroke(width = 2.dp.toPx()))
        drawLine(
            color = color.copy(alpha = 0.5f),
            start = Offset(width * 0.30f, height * 0.76f),
            end = Offset(width * 0.70f, height * 0.76f),
            strokeWidth = 1f,
        )
    }
}

@Composable
private fun GlyphCanvas(
    active: Boolean,
    color: Color,
    modifier: Modifier,
    draw: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (active) TargetScale else 1f,
        animationSpec = motionSpec(Motion.snapSpring<Float>()),
        label = "glyphScale",
    )
    Canvas(
        modifier = modifier
            .size(44.dp)
            .scale(scale),
    ) {
        draw(color)
    }
}

/** The snap the spec asks for when the threshold is crossed. */
private const val TargetScale = 1.15f
