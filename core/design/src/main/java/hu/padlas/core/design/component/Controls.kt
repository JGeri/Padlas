package hu.padlas.core.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.AccentGradient
import hu.padlas.core.design.theme.DangerGradient
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space

enum class ButtonStyle { Primary, Secondary, Destructive, Success, Favorite, Ghost }

enum class ButtonSize(val height: Dp, val horizontal: Dp, val iconSize: Dp) {
    Large(54.dp, 24.dp, 20.dp),
    Medium(44.dp, 18.dp, 18.dp),
    Small(34.dp, 14.dp, 16.dp),
}

private data class ButtonColors(val fill: Brush, val content: Color, val glow: Color)

private fun colorsFor(style: ButtonStyle, enabled: Boolean): ButtonColors = when {
    !enabled -> ButtonColors(SolidColor(Ink.SurfaceHigh), Ink.TextTertiary, Color.Transparent)
    style == ButtonStyle.Primary -> ButtonColors(AccentGradient, Ink.OnAccent, Ink.Accent)
    style == ButtonStyle.Secondary -> ButtonColors(SolidColor(Ink.SurfaceHigh), Ink.Text, Color.Transparent)
    style == ButtonStyle.Destructive -> ButtonColors(DangerGradient, Color.White, Ink.Danger)
    style == ButtonStyle.Success -> ButtonColors(SolidColor(Ink.Success), Ink.Background, Ink.Success)
    style == ButtonStyle.Favorite -> ButtonColors(SolidColor(Ink.Favorite), Ink.Background, Ink.Favorite)
    else -> ButtonColors(SolidColor(Color.Transparent), Ink.Accent, Color.Transparent)
}

/**
 * Pill button. Primary and destructive ones carry a soft glow of their own colour, so the
 * one action that matters on a screen is lit, not just coloured. A press sinks it a little.
 */
@Composable
fun PadlasButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    icon: ImageVector? = null,
    style: ButtonStyle = if (destructive) ButtonStyle.Destructive else ButtonStyle.Primary,
    size: ButtonSize = ButtonSize.Large,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.95f else 1f,
        animationSpec = motionSpec(Motion.rest()),
        label = "buttonPress",
    )
    val colors = colorsFor(style, enabled)
    val haptic = haptics()
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = size.height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (colors.glow != Color.Transparent && size != ButtonSize.Small) {
                    Modifier.shadow(
                        elevation = 18.dp,
                        shape = Shapes.pill,
                        ambientColor = colors.glow,
                        spotColor = colors.glow,
                    )
                } else {
                    Modifier
                },
            )
            .clip(Shapes.pill)
            .background(colors.fill)
            .then(if (style == ButtonStyle.Secondary) Modifier.glass(Shapes.pill, Color.Transparent) else Modifier)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = {
                    haptic?.tick(0.2f)
                    onClick()
                },
            )
            .padding(horizontal = size.horizontal),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.content, modifier = Modifier.size(size.iconSize))
            Spacer(Modifier.width(Space.s))
        }
        Text(
            text = text,
            color = colors.content,
            style = if (size == ButtonSize.Small) PadlasTheme.type.caption.copy(fontWeight = FontWeight.Bold) else PadlasTheme.type.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun PadlasTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Ink.Accent,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (pressed) 0.55f else 1f,
        animationSpec = motionSpec(Motion.snapSpring()),
        label = "textButtonPress",
    )
    val tint = if (enabled) color.copy(alpha = color.alpha * alpha) else Ink.TextTertiary
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(Shapes.pill)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text = text, color = tint, style = PadlasTheme.type.label, maxLines = 1)
    }
}

/** A round icon button; every toolbar action in the app is one of these. */
@Composable
fun PadlasIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Ink.Text,
    background: Color = Ink.Surface,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.86f else 1f,
        animationSpec = motionSpec(Motion.rest()),
        label = "iconButtonPress",
    )
    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .glass(Shapes.pill, background)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else Ink.TextTertiary,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun Chip(
    text: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: String? = null,
) {
    val fill by animateColorAsState(
        targetValue = if (selected) Ink.AccentSoft else Ink.Surface,
        animationSpec = motionSpec(Motion.settle()),
        label = "chipFill",
    )
    val outline by animateColorAsState(
        targetValue = if (selected) Ink.Accent.copy(alpha = 0.7f) else Ink.Hairline,
        animationSpec = motionSpec(Motion.settle()),
        label = "chipOutline",
    )
    val content = if (selected) Ink.Text else Ink.TextSecondary
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(38.dp)
            .glass(Shapes.pill, fill, outline)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Checkbox,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) Ink.Accent else Ink.TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text = text, color = content, style = PadlasTheme.type.label, maxLines = 1)
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            Text(text = trailing, color = Ink.TextTertiary, style = PadlasTheme.type.numeric, maxLines = 1)
        }
    }
}

/** iOS-style segmented control with a sliding thumb. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .glass(Shapes.pill)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(
            targetValue = segment * selectedIndex.coerceIn(0, options.lastIndex),
            animationSpec = motionSpec(Motion.rest()),
            label = "segmentThumb",
        )
        Box(
            modifier = Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .glass(Shapes.pill, Ink.SurfaceHighest, Ink.HairlineStrong),
        )
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, label ->
                val interaction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(Shapes.pill)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = PadlasTheme.type.label,
                        color = if (index == selectedIndex) Ink.Text else Ink.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun PadlasSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Ink.Accent,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Ink.TextSecondary,
            uncheckedTrackColor = Ink.SurfaceHigh,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** Small coloured pill for status: "Védett", "Videó", "12 MB". */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Ink.Text,
    background: Color = Ink.SurfaceHighest,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(Shapes.pill)
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text = text, color = color, style = PadlasTheme.type.micro, maxLines = 1)
    }
}
