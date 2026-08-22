package hu.padlas.core.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space

enum class ActionDirection { UP, DOWN, COMPRESS, NONE }

/**
 * The Vault rows are a priority queue, not a menu: the caller sorts them by how much space
 * they win back. The glyph on the right is the same two directions as the swipe deck, so the
 * gesture is learned once and reused everywhere.
 */
@Composable
fun ActionBar(
    title: String,
    detail: String,
    direction: ActionDirection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
) {
    StaggeredColumnItem(index) {
        PadlasCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Ink.Paper,
                        style = PadlasTheme.type.body,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(Space.xs))
                    NumericText(detail)
                }
                Spacer(Modifier.width(Space.m))
                DirectionGlyph(direction)
            }
        }
    }
}

/** Drawn, not typed: an arrow that falls back to a tofu box would be worse than none. */
@Composable
private fun DirectionGlyph(direction: ActionDirection, modifier: Modifier = Modifier) {
    val color = when (direction) {
        ActionDirection.UP -> Ink.Patina
        ActionDirection.DOWN -> Ink.Faded
        ActionDirection.COMPRESS -> Ink.Brass
        ActionDirection.NONE -> return
    }
    Canvas(modifier.size(14.dp)) {
        val stroke = 1.5.dp.toPx()
        val centreX = size.width / 2f
        val centreY = size.height / 2f
        val wing = size.width * 0.3f
        when (direction) {
            ActionDirection.UP, ActionDirection.DOWN -> {
                val up = direction == ActionDirection.UP
                val tip = if (up) 0f else size.height
                val tail = if (up) size.height else 0f
                val shoulder = if (up) size.height * 0.42f else size.height * 0.58f
                drawLine(color, Offset(centreX, tail), Offset(centreX, tip), stroke, StrokeCap.Round)
                drawLine(color, Offset(centreX, tip), Offset(centreX - wing, shoulder), stroke, StrokeCap.Round)
                drawLine(color, Offset(centreX, tip), Offset(centreX + wing, shoulder), stroke, StrokeCap.Round)
            }
            else -> {
                // Two arrows facing each other: the file shrinks, it does not go anywhere.
                val top = centreY - size.height * 0.2f
                val bottom = centreY + size.height * 0.2f
                val wingY = size.height * 0.16f
                drawLine(color, Offset(0f, top), Offset(size.width, top), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width, top), Offset(size.width - wing, top - wingY), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width, top), Offset(size.width - wing, top + wingY), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width, bottom), Offset(0f, bottom), stroke, StrokeCap.Round)
                drawLine(color, Offset(0f, bottom), Offset(wing, bottom - wingY), stroke, StrokeCap.Round)
                drawLine(color, Offset(0f, bottom), Offset(wing, bottom + wingY), stroke, StrokeCap.Round)
            }
        }
    }
}
