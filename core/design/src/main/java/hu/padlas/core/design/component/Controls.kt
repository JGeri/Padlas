package hu.padlas.core.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space

/**
 * Brass on dark. Rust is reserved for confirmations - a destructive button that lives on a
 * list instead of on a confirmation sheet is a bug, not a colour choice.
 */
@Composable
fun PadlasButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.985f else 1f,
        animationSpec = motionSpec(Motion.snapSpring()),
        label = "buttonPress",
    )
    val fill = when {
        !enabled -> Ink.Raised
        destructive -> Ink.Rust
        else -> Ink.Brass
    }
    val label = when {
        !enabled -> Ink.Dust
        destructive -> Ink.Paper
        else -> Ink.Void
    }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .scale(scale)
            .raisedSurface(shape = Shapes.card, background = fill)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = Space.xl, vertical = Space.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = label, style = PadlasTheme.type.label, maxLines = 1)
    }
}

@Composable
fun PadlasTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val color by animateColorAsState(
        targetValue = if (pressed) Ink.BrassDim else Ink.Brass,
        animationSpec = motionSpec(Motion.snapSpring()),
        label = "textButtonPress",
    )
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Space.m, vertical = Space.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = color, style = PadlasTheme.type.label, maxLines = 1)
    }
}

@Composable
fun Chip(
    text: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val outline by animateColorAsState(
        targetValue = if (selected) Ink.Brass else Ink.Hairline,
        animationSpec = motionSpec(Motion.settle()),
        label = "chipOutline",
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .background(if (selected) Ink.Raised else Color.Transparent, Shapes.chip)
            .hairlineOutline(outline, Shapes.chip)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = Space.m, vertical = Space.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Ink.Paper else Ink.Faded,
            style = PadlasTheme.type.label,
            maxLines = 1,
        )
    }
}
