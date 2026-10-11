package hu.padlas.feature.audit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.BigBytes
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space

/**
 * The figure the screen exists for, set large on a lit card, with one sentence on what it
 * means. Kept apart from the shared [hu.padlas.core.design.component.ReclaimHero]: the
 * log's light is green and a step dimmer.
 */
@Composable
internal fun ReclaimHero(
    label: String,
    bytes: Long,
    caption: String?,
    modifier: Modifier = Modifier,
    glow: Color = Ink.Success,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    PadlasCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(Shapes.hero)
            .heroGlow(glow),
        shape = Shapes.hero,
        contentPadding = Space.xl,
    ) {
        Column {
            Text(text = label, style = PadlasTheme.type.label, color = Ink.TextSecondary, maxLines = 1)
            Spacer(Modifier.height(Space.xs))
            BigBytes(value = Format.bytesValue(bytes), unit = Format.bytesUnit(bytes))
            if (caption != null) {
                Spacer(Modifier.height(Space.s))
                Text(text = caption, style = PadlasTheme.type.body, color = Ink.TextSecondary)
            }
            content()
        }
    }
}

/** A light source in the corner of the hero card, the one place on the screen that glows. */
private fun Modifier.heroGlow(color: Color): Modifier = drawBehind {
    val radius = size.maxDimension * 0.75f
    val center = Offset(size.width * 0.92f, size.height * 0.05f)
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = 0.24f),
            0.55f to color.copy(alpha = 0.06f),
            1f to Color.Transparent,
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
