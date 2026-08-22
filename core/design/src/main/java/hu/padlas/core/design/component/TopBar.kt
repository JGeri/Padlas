package hu.padlas.core.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space

/**
 * The title is set in micro, uppercase and letterspaced: at this size the words read as a
 * caption on a drawer, which is what the header of an archive should be.
 */
@Composable
fun PadlasTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    action: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onBack,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                BackGlyph(Modifier.size(width = 18.dp, height = 14.dp))
            }
            Spacer(Modifier.width(Space.s))
        }
        Text(
            text = title.uppercase(),
            color = Ink.Paper,
            style = PadlasTheme.type.micro,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        action()
    }
}

/** Drawn rather than imported: the module carries no icon font and needs none. */
@Composable
private fun BackGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val middle = size.height / 2f
        val stroke = 1.5.dp.toPx()
        drawLine(Ink.Paper, Offset(size.width, middle), Offset(0f, middle), stroke, StrokeCap.Round)
        drawLine(Ink.Paper, Offset(0f, middle), Offset(size.width * 0.42f, 0f), stroke, StrokeCap.Round)
        drawLine(Ink.Paper, Offset(0f, middle), Offset(size.width * 0.42f, size.height), stroke, StrokeCap.Round)
    }
}
