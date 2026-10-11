package hu.padlas.core.design.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space

/** What is true, and what to do about it. No illustration, no apology. */
@Composable
fun EmptyState(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Inbox,
    title: String? = null,
    tint: Color = Ink.Accent,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xl, vertical = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBubble(icon = icon, tint = tint, size = 64.dp, iconSize = 30.dp)
        Spacer(Modifier.height(Space.l))
        if (title != null) {
            Text(text = title, color = Ink.Text, style = PadlasTheme.type.title, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Space.xs))
        }
        Text(
            text = text,
            color = Ink.TextSecondary,
            style = PadlasTheme.type.body,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.l))
            PadlasButton(text = actionLabel, onClick = onAction, size = ButtonSize.Medium)
        }
    }
}
