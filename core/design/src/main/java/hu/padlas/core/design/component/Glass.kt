package hu.padlas.core.design.component

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.Shapes

/**
 * Frosted glass over the aurora: a translucent fill and a rim that is brighter on top,
 * the way light catches the edge of a pane. Over the drifting background the fill alone
 * already reads as frosted; [frosted] adds a real blur where content moves behind.
 */
fun Modifier.glass(
    shape: Shape = Shapes.card,
    fill: Color = Ink.Surface,
    rim: Color = Ink.Hairline,
): Modifier = this
    .clip(shape)
    .background(fill, shape)
    .drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(
            outline = outline,
            brush = Brush.verticalGradient(
                0f to Ink.Highlight,
                0.35f to rim,
                1f to rim.copy(alpha = rim.alpha * 0.5f),
            ),
            style = Stroke(width = 1.dp.toPx()),
        )
    }

/**
 * Real backdrop blur for floating bars and sheets; [state] is the content behind them. The
 * content is blurred at a third of its resolution: under this much blur nobody can tell,
 * and over a scrolling grid of photos it halves what the GPU spends on each frame.
 */
@OptIn(ExperimentalHazeApi::class)
fun Modifier.frosted(state: HazeState, tint: Color = Ink.SurfaceSolid.copy(alpha = 0.62f)): Modifier =
    this.hazeEffect(state = state) {
        backgroundColor = Ink.Background
        tints = listOf(HazeTint(tint))
        blurRadius = 26.dp
        noiseFactor = 0.04f
        inputScale = HazeInputScale.Fixed(0.33f)
    }

/**
 * A soft coloured light behind an element. Elevation shadows cannot do this for glass: the
 * platform draws them under the whole outline, and through a translucent fill they show
 * up as a dark slab. A blurred fill of the same shape only lights the surroundings and
 * tints the pane a little, which is what a lit pane does.
 */
fun Modifier.glow(
    color: Color,
    radius: Dp = 22.dp,
    shape: Shape = Shapes.pill,
    alpha: Float = 0.42f,
): Modifier = drawBehind { drawGlow(shape, color, radius.toPx(), alpha) }

fun DrawScope.drawGlow(shape: Shape, color: Color, radiusPx: Float, alpha: Float) {
    if (alpha <= 0f || radiusPx <= 0f) return
    val paint = Paint().asFrameworkPaint().apply {
        isAntiAlias = true
        this.color = color.copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()
        maskFilter = BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL)
    }
    // A blurred rounded rectangle is one the GPU draws analytically; a blurred path is a
    // mask rasterised again on every frame something else on screen moves. Under this much
    // blur any other outline casts the same light as its rounded bounds.
    val outline = shape.createOutline(size, layoutDirection, this)
    val bounds = outline.bounds
    val corner = when (outline) {
        is Outline.Rectangle -> 0f
        is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
        is Outline.Generic -> minOf(bounds.width, bounds.height, GenericCorner.toPx() * 2f) / 2f
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRoundRect(bounds.left, bounds.top, bounds.right, bounds.bottom, corner, corner, paint)
    }
}

private val GenericCorner = 28.dp
