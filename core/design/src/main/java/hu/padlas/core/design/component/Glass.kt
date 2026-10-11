package hu.padlas.core.design.component

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
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

/** Real backdrop blur for floating bars and sheets; [state] is the content behind them. */
fun Modifier.frosted(state: HazeState, tint: Color = Ink.SurfaceSolid.copy(alpha = 0.62f)): Modifier =
    this.hazeEffect(state = state) {
        backgroundColor = Ink.Background
        tints = listOf(HazeTint(tint))
        blurRadius = 26.dp
        noiseFactor = 0.04f
    }
