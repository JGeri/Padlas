package hu.padlas.feature.sort.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import androidx.compose.material3.Text
import hu.padlas.core.design.component.raisedSurface
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space
import hu.padlas.feature.sort.SortCard

/**
 * A card is a photograph on a heavy stock, not a list row. The wireframe pass is what
 * makes archiving feel unlike deleting: the picture drains to grey and a patina outline
 * draws on before it leaves through the hatch.
 */
@Composable
internal fun SortCardFace(
    card: SortCard,
    modifier: Modifier = Modifier,
    wireframe: Boolean = false,
    colourAlpha: () -> Float = { 1f },
    outlineAlpha: () -> Float = { 0f },
) {
    val desaturated = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }

    Box(
        modifier = modifier
            .raisedSurface(shape = Shapes.card)
            .clip(Shapes.card),
    ) {
        // The grey twin only exists while a card is on its way to the attic; cross-fading
        // to it is a saturation ramp that costs the draw phase and not a recomposition.
        if (wireframe) {
            AsyncImage(
                model = card.uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                colorFilter = desaturated,
            )
        }
        AsyncImage(
            model = card.uri,
            contentDescription = card.displayName,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = colourAlpha() },
            contentScale = ContentScale.Crop,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    val alpha = outlineAlpha()
                    if (alpha <= 0f) return@drawWithContent
                    drawOutline(
                        outline = Shapes.card.createOutline(size, layoutDirection, this),
                        color = Ink.Patina.copy(alpha = alpha),
                        style = Stroke(width = 1f),
                    )
                },
        )

        CardCaption(card, Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun CardCaption(card: SortCard, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Ink.Void.copy(alpha = 0f),
                    1f to Ink.Void.copy(alpha = 0.78f),
                ),
            )
            .padding(horizontal = Space.l, vertical = Space.m),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = card.displayName,
            style = PadlasTheme.type.label,
            color = Ink.Paper,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = listOfNotNull(card.durationLabel, card.sizeLabel).joinToString(" · "),
            style = PadlasTheme.type.numeric,
            color = Ink.Faded,
            maxLines = 1,
        )
    }
}
