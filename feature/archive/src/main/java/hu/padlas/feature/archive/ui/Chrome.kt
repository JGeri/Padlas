package hu.padlas.feature.archive.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.RollingNumber
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space

/** What the list keeps free under its last row, so the pinned action never hides it. */
internal val PinnedBarClearance = 104.dp

@Composable
internal fun navigationBarBottom(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * The screen's one primary action, floating over the list. The fade under it keeps the
 * thumbnails from cutting hard into the button while they scroll past.
 */
@Composable
internal fun PinnedActionBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth(),
        enter = slideInVertically(finite(reduced)) { it } + fadeIn(finite(reduced)),
        exit = slideOutVertically(finite(reduced)) { it } + fadeOut(finite(reduced)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Ink.Background.copy(alpha = 0.94f),
                        1f to Ink.Background,
                    ),
                )
                .navigationBarsPadding()
                .padding(start = Space.page, end = Space.page, top = Space.xl, bottom = Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
            content = content,
        )
    }
}

/** The figure the screen exists for, set large, with one sentence on what it means. */
@Composable
internal fun ReclaimHero(
    label: String,
    bytes: Long,
    caption: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    PadlasCard(modifier = modifier.fillMaxWidth(), shape = Shapes.hero, contentPadding = Space.xl) {
        Column {
            Text(text = label, style = PadlasTheme.type.label, color = Ink.TextSecondary, maxLines = 1)
            Spacer(Modifier.height(Space.xs))
            BigBytes(bytes = bytes)
            if (caption != null) {
                Spacer(Modifier.height(Space.s))
                Text(text = caption, style = PadlasTheme.type.body, color = Ink.TextSecondary)
            }
            content()
        }
    }
}

@Composable
internal fun BigBytes(bytes: Long, modifier: Modifier = Modifier, color: Color = Ink.Text) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        RollingNumber(value = Format.bytesValue(bytes), style = PadlasTheme.type.display, color = color)
        Spacer(Modifier.width(Space.s))
        Text(
            text = Format.bytesUnit(bytes),
            style = PadlasTheme.type.title,
            color = Ink.TextSecondary,
            maxLines = 1,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

internal fun <T> finite(reduced: Boolean): FiniteAnimationSpec<T> = if (reduced) snap() else Motion.settle()
