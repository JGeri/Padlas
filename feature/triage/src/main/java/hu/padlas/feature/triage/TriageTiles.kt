package hu.padlas.feature.triage

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.ShotKind
import hu.padlas.core.design.component.IconBubble
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.SelectableTile
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
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

/**
 * Inverse mode: a ticked tile is on its way out, an empty circle stays. Red is the colour
 * of deleting everywhere in the app, so the tick here is red too.
 */
@Composable
internal fun ShotTile(
    item: ShotItem,
    kept: Boolean,
    falling: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onUnlock: (() -> Unit)? = null,
) {
    val haptics = haptics()
    Box(modifier = modifier) {
        FallAway(falling) {
            Box {
                SelectableTile(
                    model = item.model,
                    selected = !kept && !item.locked,
                    onToggle = {
                        if (item.locked) return@SelectableTile
                        haptics?.tick()
                        onToggle()
                    },
                    selectionMode = !item.locked,
                    selectedTint = Ink.Danger,
                    badge = if (kept && !item.locked) KEEP_BADGE else null,
                    onLongPress = if (item.locked && onUnlock != null) {
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
                if (item.locked) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(Shapes.pill)
                            .background(Ink.Background.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Védett",
                            tint = Ink.Text,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }
    }
}

/** One kind of picture: what it is, why it is here, and how much of it is going. */
@Composable
internal fun GroupHeader(
    group: TriageGroup,
    tally: GroupTally,
    onKeepAll: () -> Unit,
    onDiscardAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = Space.xl, bottom = Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon = kindIcon(group.kind), tint = kindTint(group.kind), size = 40.dp)
            Spacer(Modifier.width(Space.m))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.title,
                    style = PadlasTheme.type.title,
                    color = Ink.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tallyLine(group, tally),
                    style = PadlasTheme.type.numeric,
                    color = Ink.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (tally.discardCount > 0) {
                PadlasTextButton(text = "Mind marad", onClick = onKeepAll)
            } else {
                PadlasTextButton(text = "Mind törlése", onClick = onDiscardAll, color = Ink.Danger)
            }
        }
        Text(
            text = group.hint,
            style = PadlasTheme.type.caption,
            color = Ink.TextTertiary,
            modifier = Modifier.padding(top = Space.s),
        )
    }
}

private fun tallyLine(group: TriageGroup, tally: GroupTally): String =
    "${Format.count(tally.discardCount)} / ${Format.count(group.items.size)} törlésre · " +
        Format.bytes(tally.discardBytes)

internal fun kindIcon(kind: ShotKind?): ImageVector = when (kind) {
    ShotKind.SECRET -> Icons.Rounded.Lock
    ShotKind.TICKET -> Icons.Rounded.ConfirmationNumber
    ShotKind.RECEIPT -> Icons.AutoMirrored.Rounded.ReceiptLong
    ShotKind.PLACE -> Icons.Rounded.Place
    ShotKind.CHAT -> Icons.Rounded.Forum
    ShotKind.OTHER -> Icons.Rounded.Image
    null -> Icons.Rounded.HourglassEmpty
}

internal fun kindTint(kind: ShotKind?): Color = when (kind) {
    ShotKind.SECRET -> Ink.Success
    ShotKind.TICKET -> Ink.Info
    ShotKind.RECEIPT -> Ink.Favorite
    ShotKind.PLACE -> Ink.Success
    ShotKind.CHAT -> Ink.Accent
    ShotKind.OTHER -> Ink.Warning
    null -> Ink.TextSecondary
}

internal const val KEEP_BADGE = "Marad"
private const val SCATTER_MS = 40
private const val FALL_DISTANCE_PX = 220f
internal const val FALL_AWAY_MS = 520L
internal val TileSpacing = 3.dp
internal const val GRID_COLUMNS = 4
