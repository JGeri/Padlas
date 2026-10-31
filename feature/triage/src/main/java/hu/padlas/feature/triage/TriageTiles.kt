package hu.padlas.feature.triage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SportsEsports
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.ShotKind
import hu.padlas.core.design.component.ButtonStyle
import hu.padlas.core.design.component.IconBubble
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.Pill
import hu.padlas.core.design.component.RollingNumber
import hu.padlas.core.design.component.SelectableTile
import hu.padlas.core.design.component.ZoomTarget
import hu.padlas.core.design.component.ZoomableImage
import hu.padlas.core.design.component.asString
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.finite
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.motion.popIn
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
    onView: (() -> Unit)? = null,
) {
    val haptics = haptics()
    val keepBadge = stringResource(R.string.triage_keep_badge)
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
                    badge = if (kept && !item.locked) keepBadge else null,
                    // A tap already decides, so the closer look sits on the long press; a
                    // locked shot keeps the long press for lifting its protection.
                    onLongPress = when {
                        item.locked && onUnlock != null -> {
                            {
                                haptics?.threshold()
                                onUnlock()
                            }
                        }
                        onView != null -> {
                            {
                                haptics?.tick(0.2f)
                                onView()
                            }
                        }
                        else -> null
                    },
                    image = { tileModifier ->
                        ZoomableImage(key = item.id, modifier = tileModifier) { imageModifier, onLoaded ->
                            AsyncImage(
                                model = item.model,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                onSuccess = { onLoaded(it.painter.intrinsicSize, it.result.memoryCacheKey) },
                                modifier = imageModifier,
                            )
                        }
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
                            contentDescription = stringResource(R.string.triage_protected),
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
            IconBubble(icon = groupIcon(group), tint = groupTint(group), size = 40.dp)
            Spacer(Modifier.width(Space.m))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.title.asString(),
                    style = PadlasTheme.type.title,
                    color = Ink.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RollingNumber(
                        value = Format.count(tally.discardCount),
                        style = PadlasTheme.type.numeric,
                        color = if (tally.discardCount > 0) Ink.Danger else Ink.TextSecondary,
                    )
                    Text(
                        text = stringResource(
                            R.string.triage_group_tally,
                            Format.count(group.items.size),
                            Format.bytes(tally.discardBytes),
                        ),
                        style = PadlasTheme.type.numeric,
                        color = Ink.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            if (tally.discardCount > 0) {
                PadlasTextButton(text = stringResource(R.string.triage_keep_all), onClick = onKeepAll)
            } else {
                PadlasTextButton(
                    text = stringResource(R.string.triage_delete_all),
                    onClick = onDiscardAll,
                    color = Ink.Danger,
                )
            }
        }
        Text(
            text = group.hint.asString(),
            style = PadlasTheme.type.caption,
            color = Ink.TextTertiary,
            modifier = Modifier.padding(top = Space.s),
        )
    }
}

internal fun groupIcon(group: TriageGroup): ImageVector = when (group.source) {
    ShotSource.NAVIGATION -> Icons.Rounded.Navigation
    ShotSource.HOME -> Icons.Rounded.Smartphone
    ShotSource.GAME -> Icons.Rounded.SportsEsports
    ShotSource.MEDIA -> Icons.Rounded.PlayCircle
    ShotSource.SOCIAL -> Icons.Rounded.Groups
    null -> kindIcon(group.kind)
}

internal fun groupTint(group: TriageGroup): Color = when (group.source) {
    ShotSource.NAVIGATION -> Ink.Info
    ShotSource.HOME -> Ink.TextSecondary
    ShotSource.GAME -> Ink.Warning
    ShotSource.MEDIA -> Ink.Danger
    ShotSource.SOCIAL -> Ink.Accent
    null -> kindTint(group.kind)
}

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

private enum class Verdict { PROTECTED, KEEP, DELETE }

/** Under the enlarged picture: where it stands now, and the same choice the tile offers. */
@Composable
internal fun ColumnScope.KeepVerdict(kept: Boolean, locked: Boolean, onToggle: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val haptics = haptics()
    val verdict = when {
        locked -> Verdict.PROTECTED
        kept -> Verdict.KEEP
        else -> Verdict.DELETE
    }
    AnimatedContent(
        targetState = verdict,
        transitionSpec = {
            (popIn(reduced) togetherWith fadeOut(finite(reduced))).using(SizeTransform(clip = false))
        },
        label = "keepVerdict",
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) { shown ->
        when (shown) {
            Verdict.PROTECTED -> Pill(
                text = stringResource(R.string.triage_protected),
                icon = Icons.Rounded.Lock,
                color = Ink.Text,
                background = Ink.Info.copy(alpha = 0.85f),
            )
            Verdict.KEEP -> Pill(
                text = stringResource(R.string.triage_keep_badge),
                icon = Icons.Rounded.Check,
                color = Ink.Background,
                background = Ink.Success,
            )
            Verdict.DELETE -> Pill(
                text = stringResource(R.string.triage_marked_for_deletion),
                icon = Icons.Rounded.DeleteOutline,
                color = Ink.Text,
                background = Ink.Danger.copy(alpha = 0.85f),
            )
        }
    }
    if (!locked) {
        PadlasButton(
            text = stringResource(if (kept) R.string.triage_delete_anyway else R.string.triage_keep_it),
            onClick = {
                if (kept) haptics?.tick() else haptics?.keep()
                onToggle()
            },
            icon = if (kept) Icons.Rounded.DeleteOutline else Icons.Rounded.Check,
            style = if (kept) ButtonStyle.Secondary else ButtonStyle.Success,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The full picture, on top of the tile's cached thumbnail until it has loaded. */
@Composable
internal fun ZoomPicture(target: ZoomTarget, placeholder: Any?, modifier: Modifier) {
    val context = LocalContext.current
    val request = remember(target.model, placeholder) {
        ImageRequest.Builder(context)
            .data(target.model)
            .placeholderMemoryCacheKey(placeholder as? MemoryCache.Key)
            .crossfade(true)
            .build()
    }
    AsyncImage(model = request, contentDescription = target.title, contentScale = ContentScale.Crop, modifier = modifier)
}

private const val SCATTER_MS = 40
private const val FALL_DISTANCE_PX = 220f
internal const val FALL_AWAY_MS = 520L
/**
 * The rest of a big group folded into one tile, so a thousand Instagram shots do not stand
 * between the user and the next group. A tap lays them all out.
 */
@Composable
internal fun MoreTile(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(Shapes.small)
            .background(Ink.SurfaceHigh)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "+${Format.count(count)}", style = PadlasTheme.type.title, color = Ink.Text, maxLines = 1)
        Text(text = stringResource(R.string.triage_more_all), style = PadlasTheme.type.caption, color = Ink.TextSecondary)
    }
}

/** A group shows two rows until it is opened; the more tile takes the last place. */
internal const val PREVIEW_TILES = 8

internal val TileSpacing = 3.dp
internal const val GRID_COLUMNS = 4
