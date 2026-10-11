package hu.padlas.core.design.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space
import kotlinx.coroutines.delay

/**
 * A floating message. The caller's state can clear the message straight away through
 * [onShown]; the toast keeps its own copy on screen for [durationMs].
 */
@Composable
fun Toast(
    message: String?,
    modifier: Modifier = Modifier,
    onShown: () -> Unit = {},
    icon: ImageVector = Icons.Rounded.CheckCircle,
    tint: Color = Ink.Success,
    durationMs: Long = 3_200,
) {
    val reduced = LocalReducedMotion.current
    var shown by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        shown = message
        visible = true
        onShown()
        delay(durationMs)
        visible = false
    }

    AnimatedVisibility(
        visible = visible && shown != null,
        modifier = modifier,
        enter = slideInVertically(finiteSpec(reduced, Motion.settle())) { it / 2 } + fadeIn(),
        exit = slideOutVertically { it / 2 } + fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = Space.l, vertical = Space.m)
                .widthIn(max = 480.dp)
                .clip(Shapes.pill)
                .background(Ink.SurfaceHighest)
                .border(1.dp, Ink.HairlineStrong, Shapes.pill)
                .padding(horizontal = Space.l, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.s))
            Text(text = shown.orEmpty(), style = PadlasTheme.type.label, color = Ink.Text)
        }
    }
}

/** Confirmation in the app's own style; destructive confirmations get the red button. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "Mégse",
    destructive: Boolean = false,
    icon: ImageVector = Icons.Rounded.Info,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = Space.xl), contentAlignment = Alignment.Center) {
            Column(
                modifier = modifier
                    .widthIn(max = 420.dp)
                    .clip(Shapes.sheet)
                    .background(Ink.SurfaceHigh)
                    .border(1.dp, Ink.Hairline, Shapes.sheet)
                    .padding(Space.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconBubble(
                    icon = icon,
                    tint = if (destructive) Ink.Danger else Ink.Accent,
                    size = 52.dp,
                    iconSize = 26.dp,
                )
                Spacer(Modifier.height(Space.l))
                Text(text = title, style = PadlasTheme.type.title, color = Ink.Text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Space.s))
                Text(text = text, style = PadlasTheme.type.body, color = Ink.TextSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Space.xl))
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    PadlasButton(
                        text = confirmLabel,
                        onClick = onConfirm,
                        style = if (destructive) ButtonStyle.Destructive else ButtonStyle.Primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PadlasButton(
                        text = dismissLabel,
                        onClick = onDismiss,
                        style = ButtonStyle.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** A labelled figure: "Duplikátum · 2,4 GB". */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Ink.Accent,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
) {
    PadlasCard(modifier = modifier, onClick = onClick, contentPadding = Space.l) {
        Column {
            if (icon != null) {
                IconBubble(icon = icon, tint = tint, size = 36.dp, iconSize = 18.dp)
                Spacer(Modifier.height(Space.m))
            }
            Text(text = value, style = PadlasTheme.type.numericLarge, color = Ink.Text, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(text = label, style = PadlasTheme.type.caption, color = Ink.TextSecondary, maxLines = 1)
            if (caption != null) {
                Text(text = caption, style = PadlasTheme.type.micro, color = Ink.TextTertiary, maxLines = 1)
            }
        }
    }
}
