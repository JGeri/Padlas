package hu.padlas.core.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.ProgressGradient
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space

/** Rounded meter. No percentage text inside: a number that sticks at 99 is worse than none. */
@Composable
fun LinearMeter(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = Ink.Accent,
    track: Color = Ink.SurfaceHigh,
    height: Dp = 6.dp,
    brush: Brush? = null,
) {
    val filled by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = motionSpec(Motion.settle()),
        label = "meter",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(Shapes.pill)
            .drawBehind {
                drawRect(track)
                if (filled > 0f) {
                    val radius = CornerRadius(size.height / 2f)
                    if (brush != null) {
                        drawRoundRect(brush, size = Size(size.width * filled, size.height), cornerRadius = radius)
                    } else {
                        drawRoundRect(color, size = Size(size.width * filled, size.height), cornerRadius = radius)
                    }
                }
            },
    )
}

@Composable
fun ProgressStrip(fraction: Float, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = PadlasTheme.type.caption, color = Ink.TextSecondary, maxLines = 1)
        Spacer(Modifier.height(Space.s))
        LinearMeter(fraction = fraction, brush = ProgressGradient)
    }
}

/** A circular meter; the caller puts whatever it wants in the middle. */
@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    stroke: Dp = 4.dp,
    color: Color = Ink.Accent,
    track: Color = Ink.SurfaceHigh,
    content: @Composable () -> Unit = {},
) {
    val swept by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = motionSpec(Motion.settle()),
        label = "ring",
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val width = stroke.toPx()
            val inset = width / 2f
            val arcSize = Size(this.size.width - width, this.size.height - width)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(width))
            if (swept > 0f) {
                drawArc(color, -90f, 360f * swept, false, Offset(inset, inset), arcSize, style = Stroke(width, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** One horizontal bar split into coloured shares, with small gaps between them. */
@Composable
fun SegmentBar(
    segments: List<Pair<Long, Color>>,
    total: Long,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    track: Color = Ink.SurfaceHigh,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(Shapes.pill)
            .drawBehind {
                drawRect(track)
                if (total <= 0L) return@drawBehind
                val gap = 2.dp.toPx()
                var x = 0f
                segments.forEach { (bytes, color) ->
                    val width = (bytes.toDouble() / total * size.width).toFloat()
                    if (width >= 1f) {
                        drawRect(color, topLeft = Offset(x, 0f), size = Size((width - gap).coerceAtLeast(1f), size.height))
                    }
                    x += width
                }
            },
    )
}

/** Dot, label, value: the legend under every chart in the app. */
@Composable
fun LegendItem(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Box(
            Modifier
                .size(10.dp)
                .clip(Shapes.pill)
                .drawBehind { drawRect(color) },
        )
        Text(text = label, style = PadlasTheme.type.caption, color = Ink.TextSecondary, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        Text(text = value, style = PadlasTheme.type.numeric, color = Ink.Text, maxLines = 1)
    }
}
