package hu.padlas.feature.map

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.ButtonSize
import hu.padlas.core.design.component.ButtonStyle
import hu.padlas.core.design.component.Chip
import hu.padlas.core.design.component.IconBubble
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasIconButton
import hu.padlas.core.design.component.RollingNumber
import hu.padlas.core.design.component.frostedPane
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.finite
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.motion.popIn
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space
import dev.chrisbanes.haze.HazeState
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val GroupRadius = 18.dp

/** Rows of one list share a single surface; only the ends of the group are rounded. */
internal fun groupShape(index: Int, count: Int): Shape {
    val top = if (index == 0) GroupRadius else 0.dp
    val bottom = if (index == count - 1) GroupRadius else 0.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GroupedRow(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    dividerInset: Dp = 66.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill by animateColorAsState(
        targetValue = when {
            selected -> Ink.AccentSoft
            pressed -> Ink.SurfaceHigh
            else -> Ink.Surface
        },
        animationSpec = motionSpec(Motion.snapSpring()),
        label = "rowFill",
    )
    val last = index == count - 1
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(groupShape(index, count))
            .background(fill)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick ?: {},
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                },
            )
            .drawBehind {
                if (!last) {
                    val y = size.height - 0.5f
                    drawLine(Ink.Hairline, Offset(dividerInset.toPx(), y), Offset(size.width, y), strokeWidth = 1f)
                }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Photos and videos show themselves; everything else gets its type icon. */
@Composable
internal fun ItemThumb(item: StorageItem, size: Dp, modifier: Modifier = Modifier) {
    val visual = !item.isDirectory && (item.category == FileCategory.IMAGES || item.category == FileCategory.VIDEOS)
    if (!visual) {
        IconBubble(
            icon = item.icon,
            tint = item.category.color,
            size = size,
            iconSize = size * 0.48f,
            modifier = modifier,
        )
        return
    }
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val isVideo = item.category == FileCategory.VIDEOS
    val request = remember(item.path, px) {
        ImageRequest.Builder(context)
            .data(File(item.path))
            .size(px)
            .crossfade(true)
            .apply { if (isVideo) decoderFactory(VideoFrameDecoder.Factory()) }
            .build()
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(Shapes.small)
            .background(item.category.color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(item.category.icon, contentDescription = null, tint = item.category.color, modifier = Modifier.size(size * 0.42f))
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        if (isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(3.dp)
                    .size(16.dp)
                    .clip(Shapes.pill)
                    .background(Ink.Background.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Ink.Text, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
internal fun Breadcrumbs(crumbs: List<Crumb>, onJump: (String) -> Unit, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(crumbs) {
        // The newest level sits at the end; the row follows it once it has been measured.
        snapshotFlow { scroll.maxValue }.collect { scroll.animateScrollTo(it) }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = Space.page),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val reduced = LocalReducedMotion.current
        crumbs.forEachIndexed { index, crumb ->
            key(crumb.path) {
                // A level that was already there stays put; only the one just opened pops in.
                val shown = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(visibleState = shown, enter = popIn(reduced)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (index > 0) {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = Ink.TextTertiary,
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .size(18.dp),
                            )
                        }
                        Chip(
                            text = crumb.label,
                            selected = index == crumbs.lastIndex,
                            onClick = { onJump(crumb.path) },
                            icon = if (index == 0) Icons.Rounded.PhoneAndroid else null,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The detail sheet for whatever is selected on the map or in a list: frosted glass over the
 * list, with a grabber that means what it shows. Pulled down far enough, it lets go.
 */
@Composable
internal fun FocusPanel(
    focus: Focus,
    rootPath: String,
    hint: String?,
    haze: HazeState,
    onOpen: (StorageItem) -> Unit,
    onDelete: (StorageItem) -> Unit,
    onShowAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val pull = remember { Animatable(0f) }
    val dismissAt = with(LocalDensity.current) { SheetDismiss.toPx() }
    val close by rememberUpdatedState(onClose)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, pull.value.roundToInt()) }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    scope.launch { pull.snapTo((pull.value + delta).coerceAtLeast(0f)) }
                },
                onDragStopped = { velocity ->
                    if (pull.value > dismissAt || velocity > SheetFlingVelocity) {
                        close()
                    } else {
                        pull.animateTo(0f, if (reduced) snap() else Motion.snapback())
                    }
                },
            )
            .frostedPane(haze, Shapes.sheet)
            .padding(start = Space.l, end = Space.l, bottom = Space.l, top = Space.s),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .clip(Shapes.pill)
                .background(Ink.HairlineStrong),
        )
        Spacer(Modifier.height(Space.m))
        AnimatedContent(
            targetState = focus,
            contentKey = { current ->
                when (current) {
                    is Focus.Item -> current.item.path
                    is Focus.Rest -> REST_KEY
                }
            },
            transitionSpec = {
                (fadeIn(finite(reduced)) + slideInHorizontally(finite(reduced)) { it / 6 }) togetherWith
                    fadeOut(finite(reduced)) using SizeTransform(clip = false)
            },
            label = "focusDetails",
        ) { shown ->
            Column {
                when (shown) {
                    is Focus.Item -> ItemDetails(shown, rootPath, hint, onOpen, onDelete, onClose)
                    is Focus.Rest -> RestDetails(shown, onShowAll, onClose)
                }
            }
        }
    }
}

private val SheetDismiss = 96.dp
private const val SheetFlingVelocity = 1_800f

@Composable
private fun ItemDetails(
    focus: Focus.Item,
    rootPath: String,
    hint: String?,
    onOpen: (StorageItem) -> Unit,
    onDelete: (StorageItem) -> Unit,
    onClose: () -> Unit,
) {
    val item = focus.item
    val share = if (focus.parentBytes > 0L) item.bytes.toFloat() / focus.parentBytes else 0f
    val protected = StoragePaths.isProtected(item.path, rootPath)
    Row(verticalAlignment = Alignment.CenterVertically) {
        ItemThumb(item, 48.dp)
        Spacer(Modifier.width(Space.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = PadlasTheme.type.title,
                color = Ink.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = displayPath(item.parentPath, rootPath),
                style = PadlasTheme.type.caption,
                color = Ink.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        Spacer(Modifier.width(Space.s))
        PadlasIconButton(
            icon = Icons.Rounded.Close,
            contentDescription = "Bezárás",
            onClick = onClose,
            background = Ink.SurfaceHighest,
            size = 36.dp,
            iconSize = 18.dp,
        )
    }
    Spacer(Modifier.height(Space.l))
    Row(modifier = Modifier.fillMaxWidth()) {
        DetailStat(label = "Méret", value = Format.bytes(item.bytes), modifier = Modifier.weight(1f))
        DetailStat(label = "A mappából", value = Format.percent(share), modifier = Modifier.weight(1f))
        if (item.isDirectory) {
            DetailStat(label = "Fájl", value = Format.count(item.fileCount), modifier = Modifier.weight(1f))
        } else {
            DetailStat(label = "Módosítva", value = TimeText.date(item.modified), modifier = Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(Space.l))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        if (item.isDirectory) {
            PadlasButton(
                text = "Megnyitás",
                onClick = { onOpen(item) },
                icon = Icons.Rounded.FolderOpen,
                style = ButtonStyle.Secondary,
                size = ButtonSize.Medium,
                modifier = Modifier.weight(1f),
            )
        }
        PadlasButton(
            text = "Törlés",
            onClick = { onDelete(item) },
            icon = Icons.Rounded.DeleteOutline,
            style = ButtonStyle.Destructive,
            size = ButtonSize.Medium,
            enabled = !protected,
            modifier = Modifier.weight(1f),
        )
    }
    val note = if (protected) "Rendszermappa, ezt nem törölheted." else hint
    if (note != null) {
        Spacer(Modifier.height(Space.s))
        Text(
            text = note,
            style = PadlasTheme.type.micro,
            color = Ink.TextTertiary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RestDetails(focus: Focus.Rest, onShowAll: () -> Unit, onClose: () -> Unit) {
    val share = if (focus.parentBytes > 0L) focus.bytes.toFloat() / focus.parentBytes else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBubble(icon = Icons.Rounded.GridView, tint = Ink.TextSecondary, size = 48.dp, iconSize = 22.dp)
        Spacer(Modifier.width(Space.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${Format.count(focus.count)} kisebb elem",
                style = PadlasTheme.type.title,
                color = Ink.Text,
                maxLines = 1,
            )
            Text(
                text = "${Format.bytes(focus.bytes)} · ${Format.percent(share)} a mappából",
                style = PadlasTheme.type.caption,
                color = Ink.TextSecondary,
                maxLines = 1,
            )
        }
        PadlasIconButton(
            icon = Icons.Rounded.Close,
            contentDescription = "Bezárás",
            onClick = onClose,
            background = Ink.SurfaceHighest,
            size = 36.dp,
            iconSize = 18.dp,
        )
    }
    Spacer(Modifier.height(Space.l))
    PadlasButton(
        text = "Megnézem a listában",
        onClick = onShowAll,
        style = ButtonStyle.Secondary,
        size = ButtonSize.Medium,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DetailStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = value, style = PadlasTheme.type.numericLarge, color = Ink.Text, maxLines = 1)
        Text(text = label, style = PadlasTheme.type.micro, color = Ink.TextTertiary, maxLines = 1)
    }
}

/**
 * What is selected and what it is worth, on a floating pane of glass. Both figures roll,
 * so every tick in the list visibly moves the batch.
 */
@Composable
internal fun SelectionBar(
    selection: SelectionUi,
    haze: HazeState,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dirs = selection.items.values.count { it.isDirectory }
    val files = selection.count - dirs
    val (count, noun) = when {
        dirs == 0 -> files to "fájl"
        files == 0 -> dirs to "mappa"
        else -> selection.count to "elem"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .frostedPane(haze, Shapes.pill)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PadlasIconButton(
            icon = Icons.Rounded.Close,
            contentDescription = "Kijelölés megszüntetése",
            onClick = onClear,
            background = Ink.SurfaceHighest,
            size = 44.dp,
        )
        Spacer(Modifier.width(Space.m))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                RollingNumber(value = Format.count(count), style = PadlasTheme.type.bodyStrong, color = Ink.Text)
                Text(text = " $noun", style = PadlasTheme.type.bodyStrong, color = Ink.Text, maxLines = 1)
            }
            RollingNumber(value = Format.bytes(selection.bytes), style = PadlasTheme.type.caption, color = Ink.TextSecondary)
        }
        Spacer(Modifier.width(Space.s))
        PadlasButton(
            text = "Törlés",
            onClick = onDelete,
            icon = Icons.Rounded.DeleteOutline,
            style = ButtonStyle.Destructive,
            size = ButtonSize.Medium,
        )
    }
}

@Composable
internal fun BusyPill(text: String, haze: HazeState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .frostedPane(haze, Shapes.pill)
            .padding(horizontal = Space.l, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Ink.Danger, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.s))
        Text(text = text, style = PadlasTheme.type.label, color = Ink.Text)
    }
}
