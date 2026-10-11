package hu.padlas.feature.sort.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.ButtonSize
import hu.padlas.core.design.component.ButtonStyle
import hu.padlas.core.design.component.Chip
import hu.padlas.core.design.component.ConfirmDialog
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.IconBubble
import hu.padlas.core.design.component.LargeHeader
import hu.padlas.core.design.component.LinearMeter
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.PadlasIconButton
import hu.padlas.core.design.component.SegmentedControl
import hu.padlas.core.design.component.SelectionMark
import hu.padlas.core.design.component.StaggeredColumnItem
import hu.padlas.core.design.component.Toast
import hu.padlas.core.design.component.frosted
import hu.padlas.core.design.component.glass
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.morphBounds
import hu.padlas.core.design.motion.morphElement
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space
import hu.padlas.core.media.MediaThumb
import hu.padlas.feature.sort.ui.Cover
import hu.padlas.feature.sort.MediaFilter
import hu.padlas.feature.sort.SwipeOrder
import hu.padlas.feature.sort.ui.rememberSystemRequest

private val RiseSpring = spring<IntOffset>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)

/**
 * The "Válogatás" tab. A tap on a month or a folder opens its deck straight away, the tile
 * growing into the first card; a long press switches to picking several, and the start
 * bar rises from the bottom. Whatever is already marked for deletion waits on top.
 */
@Composable
fun SwipeHomeRoute(
    onStartSession: () -> Unit,
    onOpenPending: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val viewModel: SwipeSetupViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launch = rememberSystemRequest(viewModel::onDeleteResult)
    LaunchedEffect(viewModel) {
        viewModel.deleteRequests.collect { batch -> launch(batch.intentSender) }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val haze = rememberHazeState()
    val bottom = contentPadding.calculateBottomPadding()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(haze),
            contentPadding = PaddingValues(
                start = Space.page,
                end = Space.page,
                top = top + Space.s,
                bottom = bottom + if (state.selecting) 160.dp else Space.l,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") {
                StaggeredColumnItem(0) {
                    LargeHeader(
                        title = "Válogatás",
                        subtitle = "Koppints egy hónapra, és jöhetnek a kártyák.",
                    )
                }
            }

            if (state.pending.itemCount > 0) {
                item(key = "pending") {
                    PendingCard(
                        count = state.pending.itemCount,
                        bytes = state.pending.totalBytes,
                        busy = state.busy,
                        onReview = onOpenPending,
                        onDelete = { if (viewModel.skipsSystemDialog()) confirmDelete = true else viewModel.deleteNow() },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            if (state.isEmpty) {
                item(key = "empty") {
                    EmptyState(
                        title = "Még üres a galéria",
                        text = "A beolvasás a háttérben fut. Pár perc, és itt látod a hónapokat és a mappákat.",
                        icon = Icons.Rounded.PhotoLibrary,
                    )
                }
            } else {
                item(key = "tabs") {
                    StaggeredColumnItem(1) {
                        SegmentedControl(
                            options = listOf("Hónapok", "Mappák"),
                            selectedIndex = state.tab.ordinal,
                            onSelect = { viewModel.setTab(SetupTab.entries[it]) },
                        )
                    }
                }
                item(key = "filters") {
                    StaggeredColumnItem(2) {
                        FilterRow(
                            media = state.filter.media,
                            onlyUnreviewed = state.filter.onlyUnreviewed,
                            order = state.filter.order,
                            onMedia = viewModel::setMedia,
                            onOnlyUnreviewed = viewModel::setOnlyUnreviewed,
                            onOrder = viewModel::setOrder,
                        )
                    }
                }
                if (!state.selecting) {
                    item(key = "hint") {
                        Row(
                            modifier = Modifier.animateItem(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = Ink.TextTertiary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Hosszan nyomva többet is kijelölhetsz.",
                                style = PadlasTheme.type.caption,
                                color = Ink.TextTertiary,
                            )
                        }
                    }
                }
                if (state.tab == SetupTab.MONTHS) {
                    state.years.forEachIndexed { yearIndex, year ->
                        item(key = "year-${year.year}") {
                            StaggeredColumnItem(3 + yearIndex) {
                                YearHeader(year = year, selecting = state.selecting, onToggle = { viewModel.toggleYear(year) })
                            }
                        }
                        year.months.chunked(2).forEachIndexed { rowIndex, row ->
                            item(key = "months-${year.year}-$rowIndex") {
                                StaggeredColumnItem(4 + yearIndex + rowIndex) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        row.forEach { cell ->
                                            MonthTile(
                                                cell = cell,
                                                selecting = state.selecting,
                                                onOpen = { viewModel.openMonth(cell, then = onStartSession) },
                                                onToggle = { viewModel.toggleMonth(cell.key) },
                                                onLongPress = { viewModel.startSelecting(month = cell.key) },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        if (row.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(state.folders, key = { "folder-${it.bucketId}" }) { folder ->
                        FolderRow(
                            folder = folder,
                            selecting = state.selecting,
                            onOpen = { viewModel.openFolder(folder, then = onStartSession) },
                            onToggle = { viewModel.toggleBucket(folder.bucketId) },
                            onLongPress = { viewModel.startSelecting(bucketId = folder.bucketId) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.selecting && !state.isEmpty,
            enter = slideInVertically(RiseSpring) { it } + fadeIn(),
            exit = slideOutVertically(RiseSpring) { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottom),
        ) {
            StartBar(
                label = state.selectionLabel,
                count = state.preview?.itemCount,
                bytes = state.preview?.totalBytes ?: 0L,
                onClear = viewModel::clearSelection,
                onStart = { viewModel.startSelection(then = onStartSession) },
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(Shapes.sheet)
                    .frosted(haze)
                    .glass(Shapes.sheet, Color.Transparent, Ink.HairlineStrong),
            )
        }

        Toast(
            message = state.message,
            onShown = viewModel::consumeMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottom + if (state.selecting) 170.dp else Space.l),
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "${Format.count(state.pending.itemCount)} elem törlése?",
            text = "${Format.bytes(state.pending.totalBytes)} kerül törlésre a telefonról.",
            confirmLabel = "Törlés",
            destructive = true,
            icon = Icons.Rounded.Delete,
            onConfirm = {
                confirmDelete = false
                viewModel.deleteNow()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun PendingCard(
    count: Int,
    bytes: Long,
    busy: Boolean,
    onReview: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PadlasCard(modifier = modifier.fillMaxWidth().morphBounds("swipe-pending"), border = Ink.Danger.copy(alpha = 0.45f)) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(icon = Icons.Rounded.Delete, tint = Ink.Danger)
                Column(Modifier.padding(start = Space.m).weight(1f)) {
                    Text(
                        text = "${Format.count(count)} elem vár törlésre",
                        style = PadlasTheme.type.bodyStrong,
                        color = Ink.Text,
                    )
                    Text(
                        text = "${Format.bytes(bytes)} szabadul fel",
                        style = PadlasTheme.type.caption,
                        color = Ink.TextSecondary,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                PadlasButton(
                    text = "Átnézés",
                    onClick = onReview,
                    style = ButtonStyle.Secondary,
                    size = ButtonSize.Medium,
                    icon = Icons.Rounded.GridView,
                    modifier = Modifier.weight(1f),
                )
                PadlasButton(
                    text = "Törlés most",
                    onClick = onDelete,
                    style = ButtonStyle.Destructive,
                    size = ButtonSize.Medium,
                    icon = Icons.Rounded.Delete,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FilterRow(
    media: MediaFilter,
    onlyUnreviewed: Boolean,
    order: SwipeOrder,
    onMedia: (MediaFilter) -> Unit,
    onOnlyUnreviewed: (Boolean) -> Unit,
    onOrder: (SwipeOrder) -> Unit,
) {
    var orderMenu by remember { mutableStateOf(false) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        item {
            Chip(text = "Mind", selected = media == MediaFilter.ALL, onClick = { onMedia(MediaFilter.ALL) })
        }
        item {
            Chip(text = "Fotók", icon = Icons.Rounded.Photo, selected = media == MediaFilter.PHOTOS, onClick = {
                onMedia(if (media == MediaFilter.PHOTOS) MediaFilter.ALL else MediaFilter.PHOTOS)
            })
        }
        item {
            Chip(text = "Videók", icon = Icons.Rounded.Videocam, selected = media == MediaFilter.VIDEOS, onClick = {
                onMedia(if (media == MediaFilter.VIDEOS) MediaFilter.ALL else MediaFilter.VIDEOS)
            })
        }
        item {
            Chip(
                text = "Csak új",
                icon = Icons.Rounded.NewReleases,
                selected = onlyUnreviewed,
                onClick = { onOnlyUnreviewed(!onlyUnreviewed) },
            )
        }
        item {
            Box {
                Chip(text = order.label(), icon = Icons.Rounded.SwapVert, onClick = { orderMenu = true })
                DropdownMenu(
                    expanded = orderMenu,
                    onDismissRequest = { orderMenu = false },
                    containerColor = Ink.SurfaceSolidHigh,
                    shape = Shapes.card,
                ) {
                    SwipeOrder.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label(),
                                    style = PadlasTheme.type.body,
                                    color = if (option == order) Ink.Accent else Ink.Text,
                                )
                            },
                            onClick = {
                                orderMenu = false
                                onOrder(option)
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun SwipeOrder.label(): String = when (this) {
    SwipeOrder.NEWEST -> "Legújabb elöl"
    SwipeOrder.OLDEST -> "Legrégebbi elöl"
    SwipeOrder.LARGEST -> "Legnagyobb elöl"
    SwipeOrder.RANDOM -> "Véletlen sorrend"
}

@Composable
private fun YearHeader(year: YearGroup, selecting: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = year.year.toString(), style = PadlasTheme.type.title, color = Ink.Text)
            Text(
                text = "${Format.count(year.itemCount)} elem · ${Format.bytes(year.totalBytes)}",
                style = PadlasTheme.type.caption,
                color = Ink.TextSecondary,
            )
        }
        if (selecting) {
            Chip(
                text = if (year.allSelected) "Kijelölés le" else "Teljes év",
                selected = year.allSelected,
                onClick = onToggle,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthTile(
    cell: MonthCell,
    selecting: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.95f
            cell.selected -> 0.96f
            else -> 1f
        },
        animationSpec = motionSpec(Motion.rest()),
        label = "monthScale",
    )
    val fill by animateColorAsState(
        targetValue = if (cell.selected) Ink.AccentSoft else Ink.Surface,
        animationSpec = motionSpec(Motion.settle()),
        label = "monthFill",
    )
    val rim by animateColorAsState(
        targetValue = if (cell.selected) Ink.Accent else Ink.Hairline,
        animationSpec = motionSpec(Motion.settle()),
        label = "monthRim",
    )
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .morphBounds("swipe-month-${cell.key}")
            .glass(Shapes.card, fill, rim)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { if (selecting) onToggle() else onOpen() },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
            )
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .clip(Shapes.tile)
                .background(Ink.SurfaceHigh),
        ) {
            if (cell.cover != null) {
                val context = LocalPlatformContext.current
                AsyncImage(
                    model = remember(cell.cover) {
                        ImageRequest.Builder(context)
                            .data(MediaThumb(cell.cover, 384))
                            .memoryCacheKey(Cover.cacheKey(cell.cover))
                            .build()
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .morphElement(Cover.morphKey(cell.cover)),
                )
            }
            if (selecting) {
                SelectionMark(
                    selected = cell.selected,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            } else if (cell.complete) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(Shapes.pill)
                        .background(Ink.Background.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = "Átnézve", tint = Ink.Success, modifier = Modifier.size(18.dp))
                }
            }
        }
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = cell.name,
                style = PadlasTheme.type.title.copy(fontSize = PadlasTheme.type.bodyStrong.fontSize),
                color = Ink.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(text = Format.count(cell.itemCount), style = PadlasTheme.type.numeric, color = Ink.TextSecondary)
        }
        Text(
            text = Format.bytes(cell.totalBytes),
            style = PadlasTheme.type.micro,
            color = Ink.TextTertiary,
            modifier = Modifier.padding(start = 6.dp, top = 2.dp),
        )
        LinearMeter(
            fraction = cell.reviewedFraction,
            color = Ink.Success,
            height = 4.dp,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    folder: FolderItem,
    selecting: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = motionSpec(Motion.rest()),
        label = "folderScale",
    )
    val fill by animateColorAsState(
        targetValue = if (folder.selected) Ink.AccentSoft else Ink.Surface,
        animationSpec = motionSpec(Motion.settle()),
        label = "folderFill",
    )
    val rim by animateColorAsState(
        targetValue = if (folder.selected) Ink.Accent else Ink.Hairline,
        animationSpec = motionSpec(Motion.settle()),
        label = "folderRim",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .morphBounds("swipe-folder-${folder.bucketId}")
            .glass(Shapes.card, fill, rim)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { if (selecting) onToggle() else onOpen() },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(Shapes.tile)
                .background(Ink.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Folder, contentDescription = null, tint = Ink.TextTertiary, modifier = Modifier.size(24.dp))
            if (folder.cover != null) {
                AsyncImage(
                    model = MediaThumb(folder.cover, 192),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Space.m),
        ) {
            Text(text = folder.name, style = PadlasTheme.type.bodyStrong, color = Ink.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = "${Format.count(folder.itemCount)} elem · ${Format.bytes(folder.totalBytes)}",
                style = PadlasTheme.type.caption,
                color = Ink.TextSecondary,
                maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            LinearMeter(fraction = folder.reviewedFraction, color = Ink.Success, height = 4.dp)
        }
        if (selecting) SelectionMark(selected = folder.selected)
    }
}

@Composable
private fun StartBar(
    label: String,
    count: Int?,
    bytes: Long,
    onClear: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = PadlasTheme.type.caption,
                color = Ink.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            PadlasIconButton(icon = Icons.Rounded.Close, contentDescription = "Kijelölés törlése", onClick = onClear, size = 36.dp, iconSize = 18.dp)
        }
        Spacer(Modifier.height(Space.m))
        PadlasButton(
            text = when {
                count == null -> "Számolás…"
                count == 0 -> "Nincs átnézendő elem"
                else -> "Indítás · ${Format.count(count)} elem · ${Format.bytes(bytes)}"
            },
            onClick = onStart,
            enabled = count != null && count > 0,
            icon = Icons.Rounded.PlayArrow,
            modifier = Modifier
                .fillMaxWidth()
                .morphBounds(SwipeSetupViewModel.HERO_START),
        )
    }
}
