package hu.padlas.feature.index

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.ProgressRing
import hu.padlas.core.design.component.RollingNumber
import hu.padlas.core.design.component.StatTile
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space

@Composable
internal fun IndexScreen(
    state: IndexUiState,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ink.Background)
            .systemBarsPadding()
            .padding(horizontal = Space.page),
    ) {
        if (state.error != null) {
            EmptyState(
                title = "Nem sikerült a beolvasás",
                text = state.error,
                icon = Icons.Rounded.ErrorOutline,
                tint = Ink.Danger,
                actionLabel = "Újrapróbálom",
                onAction = onRetry,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }

        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            IndexProgress(state = state)
            Spacer(Modifier.weight(1f))
            if (!state.finished) {
                Text(
                    text = "Nyugodtan elhagyhatod, a beolvasás a háttérben is folytatódik.",
                    style = PadlasTheme.type.caption,
                    color = Ink.TextTertiary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Space.xs))
                PadlasTextButton(text = "Folytatás a háttérben", onClick = onContinue)
                Spacer(Modifier.height(Space.l))
            }
        }
    }
}

/**
 * The count, always present. A spinner with no number tells the user nothing about a
 * pass that takes a minute and a half (spec 2.3).
 */
@Composable
private fun IndexProgress(state: IndexUiState, modifier: Modifier = Modifier) {
    val ringColor by animateColorAsState(
        targetValue = if (state.finished) Ink.Success else Ink.Accent,
        animationSpec = motionSpec(Motion.settle()),
        label = "ringColor",
    )
    Column(
        modifier = modifier.fillMaxWidth().widthIn(max = 420.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProgressRing(
            fraction = if (state.finished) 1f else state.fraction,
            size = 232.dp,
            stroke = 10.dp,
            color = ringColor,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RollingNumber(
                    value = Format.count(state.scanned),
                    style = PadlasTheme.type.display,
                    color = Ink.Text,
                )
                Text(text = "fájl", style = PadlasTheme.type.label, color = Ink.TextSecondary)
            }
        }

        Spacer(Modifier.height(Space.xl))
        Text(
            text = if (state.finished) "Kész a beolvasás" else "Galéria beolvasása",
            style = PadlasTheme.type.headline,
            color = Ink.Text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.s))
        Text(
            text = subtitle(state),
            style = PadlasTheme.type.body,
            color = Ink.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.m))
        Text(
            text = phase(state),
            style = PadlasTheme.type.label,
            color = Ink.TextTertiary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(Space.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            StatTile(
                label = "Átnézve",
                value = Format.bytes(state.bytes),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = if (state.finished) "Időtartam" else "Haladás",
                value = when {
                    state.finished && state.durationMs > 0L -> Format.duration(state.durationMs)
                    state.finished -> Format.percent(1f)
                    else -> Format.percent(state.fraction)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun subtitle(state: IndexUiState): String = when {
    state.finished -> "Mindent megtaláltunk. Mindjárt megmutatjuk, hol szabadíthatsz fel helyet."
    else -> "Semmit nem mozgatunk és nem törlünk, csak megnézzük, mi hol van."
}

private fun phase(state: IndexUiState): String = when {
    state.finished -> "${Format.count(state.scanned)} fájl · ${Format.bytes(state.bytes)}"
    state.bucket.isNotBlank() -> "Most: ${state.bucket}"
    state.total > 0 -> "${Format.count(state.total)} fájl vár átnézésre"
    else -> "Mappák bejárása…"
}
