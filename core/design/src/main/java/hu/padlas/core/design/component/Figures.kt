package hu.padlas.core.design.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space

/**
 * The two figures a batch action is about. Both roll, so ticking one more tile visibly
 * moves the count instead of swapping the label. They arrive formatted (`Format.count`,
 * `Format.bytesValue` / `bytesUnit`), and [noun] already agrees with the count. [valuePrefix]
 * qualifies a size that is only an estimate.
 */
@Composable
fun BarFigures(
    count: String,
    noun: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    valuePrefix: String = "",
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
            RollingNumber(value = count, style = PadlasTheme.type.numericLarge, color = Ink.Text)
            Text(
                text = " $noun",
                style = PadlasTheme.type.label,
                color = Ink.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Spacer(Modifier.width(Space.s))
        if (valuePrefix.isNotEmpty()) {
            Text(
                text = "$valuePrefix ",
                style = PadlasTheme.type.label,
                color = Ink.TextSecondary,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        RollingNumber(value = value, style = PadlasTheme.type.numericLarge, color = Ink.Text)
        Text(
            text = " $unit",
            style = PadlasTheme.type.label,
            color = Ink.TextSecondary,
            maxLines = 1,
            modifier = Modifier.padding(bottom = 3.dp),
        )
    }
}

/** The figure the screen exists for, set large on a lit card, with one sentence on what it means. */
@Composable
fun ReclaimHero(
    label: String,
    value: String,
    unit: String,
    caption: String?,
    modifier: Modifier = Modifier,
    glow: Color = Ink.Accent,
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
            BigBytes(value = value, unit = unit)
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
            0f to color.copy(alpha = 0.26f),
            0.55f to color.copy(alpha = 0.07f),
            1f to Color.Transparent,
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/**
 * A size at display scale, split so the figure can roll while the unit stays put. Both
 * halves come from `Format.bytesValue` / `bytesUnit`.
 */
@Composable
fun BigBytes(value: String, unit: String, modifier: Modifier = Modifier, color: Color = Ink.Text) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        RollingNumber(value = value, style = PadlasTheme.type.display, color = color)
        Spacer(Modifier.width(Space.s))
        Text(
            text = unit,
            style = PadlasTheme.type.title,
            color = Ink.TextSecondary,
            maxLines = 1,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}
