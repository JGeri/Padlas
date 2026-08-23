package hu.padlas.feature.triage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.HoldToConfirm
import hu.padlas.core.design.component.NumericText
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.SectionLabel
import hu.padlas.core.design.component.SelectableTile
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Spec 6.6 ⑥: after the batch is committed the grid does not disappear, it collapses.
 * The scatter is what makes it read as sand rather than as a screen refresh.
 */
@Composable
internal fun FallAway(falling: Boolean, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scatter = remember { Random.nextInt(0, 2 * SCATTER_MS + 1).toLong() }
    val spec = motionSpec(Motion.weight<Float>())
    LaunchedEffect(falling) {
        if (!falling) return@LaunchedEffect
        delay(scatter)
        progress.animateTo(1f, spec)
    }
    Box(
        modifier = Modifier.graphicsLayer {
            translationY = progress.value * FALL_DISTANCE_PX
            alpha = 1f - progress.value
        },
    ) {
        content()
    }
}

@Composable
internal fun ShotTile(
    item: ShotItem,
    kept: Boolean,
    falling: Boolean,
    onToggle: () -> Unit,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = haptics()
    val dim by animateFloatAsState(
        targetValue = if (kept || item.locked) 1f else DISCARD_ALPHA,
        animationSpec = motionSpec(Motion.settle()),
        label = "keepDim",
    )
    Box(modifier = modifier) {
        FallAway(falling) {
            SelectableTile(
                model = item.model,
                // Inverse mode: what is going gets the quiet look, what you rescued stays
                // at full colour. Selected here means "still in the delete batch".
                selected = !kept && !item.locked,
                onToggle = {
                    if (item.locked) return@SelectableTile
                    haptics?.tick()
                    onToggle()
                },
                modifier = Modifier.alpha(dim),
                badge = when {
                    item.locked -> "Védett"
                    kept -> "Marad"
                    else -> null
                },
                onLongPress = if (item.locked) {
                    {
                        haptics?.threshold()
                        onUnlock()
                    }
                } else {
                    null
                },
                image = { tileModifier ->
                    AsyncImage(
                        model = item.model,
                        contentDescription = null,
                        modifier = tileModifier,
                        contentScale = ContentScale.Crop,
                    )
                },
            )
        }
    }
}

@Composable
internal fun GroupHeader(
    group: TriageGroup,
    tally: GroupTally,
    onKeepAll: () -> Unit,
    onDiscardAll: () -> Unit,
    onUnlockAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = Space.xl, bottom = Space.s)) {
        SectionLabel(text = group.title)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumericText(
                text = if (group.locked) {
                    "${Format.count(group.items.size)} db · védett"
                } else {
                    "${Format.count(tally.discardCount)} db · ${Format.bytes(tally.discardBytes)}"
                },
                color = if (group.locked) Ink.Patina else Ink.Brass,
            )
        }
        Text(
            text = group.hint,
            style = PadlasTheme.type.micro,
            color = Ink.Dust,
            modifier = Modifier.padding(top = Space.xs),
        )
        if (group.locked) {
            HoldToConfirm(
                label = "Védelem feloldása",
                onConfirm = onUnlockAll,
                holdMillis = UNLOCK_HOLD_MS,
                modifier = Modifier.padding(top = Space.s),
            )
        } else {
            Row(
                modifier = Modifier.padding(top = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                PadlasTextButton(text = "Mindet megtartom", onClick = onKeepAll)
                PadlasTextButton(text = "Mindet kidobom", onClick = onDiscardAll)
            }
        }
    }
}

@Composable
internal fun ScreenMessage(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = PadlasTheme.type.body,
        color = Ink.Faded,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
    )
}

private const val DISCARD_ALPHA = 0.5f
private const val SCATTER_MS = 40
private const val FALL_DISTANCE_PX = 220f
private const val UNLOCK_HOLD_MS = 1_500
internal const val FALL_AWAY_MS = 520L
internal val TileSize = 104.dp
