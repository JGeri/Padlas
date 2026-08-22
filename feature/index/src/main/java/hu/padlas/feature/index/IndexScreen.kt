package hu.padlas.feature.index

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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.NumericText
import hu.padlas.core.design.component.ProgressStrip
import hu.padlas.core.design.component.RollingNumber
import hu.padlas.core.design.component.SectionLabel
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space
import hu.padlas.core.design.theme.grain

@Composable
internal fun IndexScreen(
    state: IndexUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ink.Void)
            .grain()
            .padding(horizontal = Space.xl, vertical = Space.xxl),
    ) {
        if (state.error != null) {
            EmptyState(
                text = state.error,
                actionLabel = "Újra",
                onAction = onRetry,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            IndexProgress(state)
        }
    }
}

@Composable
private fun IndexProgress(state: IndexUiState) {
    Column(modifier = Modifier.fillMaxSize()) {
        SectionLabel(text = "Padlás")
        Spacer(Modifier.height(Space.xxl))
        Text(
            text = if (state.finished) "Kész" else "Beolvasás",
            style = PadlasTheme.type.title,
            color = Ink.Paper,
        )
        Spacer(Modifier.height(Space.s))
        Text(
            text = subtitle(state),
            style = PadlasTheme.type.body,
            color = Ink.Faded,
        )

        Spacer(Modifier.weight(1f))
        Tally(state)
        Spacer(Modifier.height(Space.xl))
        Progress(state)
        Spacer(Modifier.weight(1f))
    }
}

/**
 * The two numbers, always present. A spinner with no number tells the user nothing about
 * a pass that takes a minute and a half (spec 2.3).
 */
@Composable
private fun Tally(state: IndexUiState) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            RollingNumber(
                value = Format.count(state.scanned),
                style = PadlasTheme.type.display,
                color = Ink.Paper,
            )
            Spacer(Modifier.width(Space.m))
            Text(
                text = "fájl",
                style = PadlasTheme.type.label,
                color = Ink.Dust,
                modifier = Modifier.padding(bottom = Space.m),
            )
        }
        Spacer(Modifier.height(Space.s))
        RollingNumber(
            value = Format.bytes(state.bytes),
            style = PadlasTheme.type.numericLarge,
            color = Ink.Brass,
        )
    }
}

@Composable
private fun Progress(state: IndexUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        ProgressStrip(
            fraction = if (state.finished) 1f else state.fraction,
            label = progressLabel(state),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.bucket.ifEmpty { if (state.finished) "" else "Mappák bejárása" },
                style = PadlasTheme.type.label,
                color = Ink.Dust,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (state.finished && state.durationMs > 0L) {
                NumericText(text = Format.duration(state.durationMs), color = Ink.Dust)
            } else if (state.total > 0) {
                NumericText(text = Format.count(state.total) + " várható", color = Ink.Dust)
            }
        }
    }
}

private fun subtitle(state: IndexUiState): String = when {
    state.finished -> "A padlás leltárba vette a készüléket."
    state.total > 0 -> "Semmit nem mozgatunk, csak megnézzük, mi hol van."
    else -> "Indul a leltár."
}

private fun progressLabel(state: IndexUiState): String = when {
    state.finished -> Format.percent(1f)
    state.total > 0 -> Format.percent(state.fraction)
    else -> Format.percent(0f)
}
