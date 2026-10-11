package hu.padlas.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space

enum class ActionDirection { UP, DOWN, COMPRESS, NONE }

/** A tinted icon on a soft disc of the same colour. */
@Composable
fun IconBubble(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = Ink.Accent,
    background: Color = tint.copy(alpha = 0.16f),
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    shape: androidx.compose.ui.graphics.Shape = Shapes.tile,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/**
 * The standard list row: icon, two lines, a value on the right and a chevron when it
 * leads somewhere.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = Ink.Accent,
    value: String? = null,
    valueColor: Color = Ink.TextSecondary,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    background: Color = Ink.Surface,
    trailing: (@Composable () -> Unit)? = null,
) {
    PadlasCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        background = background,
        contentPadding = Space.m,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                IconBubble(icon = icon, tint = iconTint)
                Spacer(Modifier.width(Space.m))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = title,
                    color = Ink.Text,
                    style = PadlasTheme.type.bodyStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = Ink.TextSecondary,
                        style = PadlasTheme.type.caption,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (value != null) {
                Spacer(Modifier.width(Space.s))
                Text(text = value, color = valueColor, style = PadlasTheme.type.numeric, maxLines = 1)
            }
            trailing?.let {
                Spacer(Modifier.width(Space.s))
                it()
            }
            if (showChevron) {
                Spacer(Modifier.width(Space.xs))
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Ink.TextTertiary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** A suggestion row on the overview: what can be won back and how. */
@Composable
fun ActionBar(
    title: String,
    detail: String,
    direction: ActionDirection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") index: Int = 0,
) {
    val (icon, tint) = when (direction) {
        ActionDirection.UP -> Icons.Rounded.CloudUpload to Ink.Info
        ActionDirection.DOWN -> Icons.Rounded.DeleteSweep to Ink.Danger
        ActionDirection.COMPRESS -> Icons.Rounded.Compress to Ink.Warning
        ActionDirection.NONE -> Icons.Rounded.Tune to Ink.Accent
    }
    ListRow(
        title = title,
        subtitle = detail,
        icon = icon,
        iconTint = tint,
        onClick = onClick,
        modifier = modifier,
    )
}
