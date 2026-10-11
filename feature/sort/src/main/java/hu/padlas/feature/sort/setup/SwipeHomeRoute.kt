package hu.padlas.feature.sort.setup

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
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
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.SegmentedControl
import hu.padlas.core.design.component.SelectionMark
import hu.padlas.core.design.component.Toast
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space
import hu.padlas.core.media.MediaThumb
import hu.padlas.feature.sort.MediaFilter
import hu.padlas.feature.sort.SwipeOrder
import hu.padlas.feature.sort.ui.rememberSystemRequest

/**
 * The "Válogatás" tab: pick months and folders, see how much is still unreviewed in each,
 * and start a deck. Whatever is already marked for deletion waits at the top.
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
    val bottom = contentPadding.calculateBottomPadding()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = modifier.fillMaxSize().background(Ink.Background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.page,
                end = Space.page,
                top = top,
                bottom = bottom + 140.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            item(key = "header") {
                LargeHeader(
                    title = "Válogatás",
                    subtitle = "Húzd balra, ami mehet, jobbra, ami marad, felfelé a kedvenceket.",
                )
            }

            if (state.pending.itemCount > 0) {
                item(key = "pending") {
                    PendingCard(
                        count = state.pending.itemCount,
                        bytes = state.pending.totalBytes,
                        busy = state.busy,
                        onReview = onOpenPending,
                        onDelete = { if (viewModel.skipsSystemDialog()) confirmDelete = true else viewModel.deleteNow() },
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
                    SegmentedControl(
                        options = listOf("Hónapok", "Mappák"),
                        selectedIndex = state.tab.ordinal,
                        onSelect = { viewModel.setTab(SetupTab.entries[it]) },
                    )
                }
                item(key = "filters") {
                    FilterRow(
                        media = state.filter.media,
                        onlyUnreviewed = state.filter.onlyUnreviewed,
                        order = state.filter.order,
                        onMedia = viewModel::setMedia,
                        onOnlyUnreviewed = viewModel::setOnlyUnreviewed,
                        onOrder = viewModel::setOrder,
                    )
                }
                if (state.tab == SetupTab.MONTHS) {
                    state.years.forEach { year ->
                        item(key = "year-${year.year}") {
                            YearHeader(year = year, onToggle = { viewModel.toggleYear(year) })
                        }
                        item(key = "months-${year.year}") {
                            MonthGrid(months = year.months, onToggle = viewModel::toggleMonth)
                        }
                    }
                } else {
                    items(state.folders, key = { "folder-${it.bucketId}" }) { folder ->
                        FolderRow(folder = folder, onToggle = { viewModel.toggleBucket(folder.bucketId) })
                    }
                }
            }
        }

        if (!state.isEmpty && !state.loading) {
            StartBar(
                label = state.selectionLabel,
                count = state.preview?.itemCount,
                bytes = state.preview?.totalBytes ?: 0L,
                hasSelection = state.filter.hasSelection,
                onClear = viewModel::clearSelection,
                onStart = {
                    viewModel.commitFilter()
                    onStartSession()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottom),
            )
        }

        Toast(
            message = state.message,
            onShown = viewModel::consumeMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottom + 150.dp),
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
private fun PendingCard(count: Int, bytes: Long, busy: Boolean, onReview: () -> Unit, onDelete: () -> Unit) {
    PadlasCard(modifier = Modifier.fillMaxWidth(), border = Ink.Danger.copy(alpha = 0.35f)) {
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
                    containerColor = Ink.SurfaceHighest,
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
                            colors = MenuDefaults.itemColors(),
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
private fun YearHeader(year: YearGroup, onToggle: () -> Unit) {
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
        PadlasTextButton(
            text = if (year.allSelected) "Kijelölés törlése" else "Teljes év",
            onClick = onToggle,
        )
    }
}

@Composable
private fun MonthGrid(months: List<MonthCell>, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        months.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                row.forEach { cell ->
                    MonthTile(cell = cell, onClick = { onToggle(cell.key) }, modifier = Modifier.weight(1f))
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MonthTile(cell: MonthCell, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val background by animateColorAsState(
        targetValue = if (cell.selected) Ink.AccentSoft else Ink.Surface,
        animationSpec = motionSpec(Motion.settle()),
        label = "monthFill",
    )
    val outline by animateColorAsState(
        targetValue = if (cell.selected) Ink.Accent else Ink.Hairline,
        animationSpec = motionSpec(Motion.settle()),
        label = "monthOutline",
    )
    Column(
        modifier = modifier
            .clip(Shapes.tile)
            .background(background)
            .border(1.dp, outline, Shapes.tile)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onClick = onClick,
            )
            .padding(horizontal = Space.m, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = cell.name,
                style = PadlasTheme.type.label,
                color = Ink.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (cell.selected) {
                SelectionMark(selected = true, modifier = Modifier.size(18.dp))
            } else if (cell.complete) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = "Átnézve", tint = Ink.Success, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(text = "${Format.count(cell.itemCount)} elem", style = PadlasTheme.type.caption, color = Ink.TextSecondary, maxLines = 1)
        Text(text = Format.bytes(cell.totalBytes), style = PadlasTheme.type.micro, color = Ink.TextTertiary, maxLines = 1)
        Spacer(Modifier.height(Space.s))
        LinearMeter(fraction = cell.reviewedFraction, color = Ink.Success, height = 3.dp)
    }
}

@Composable
private fun FolderRow(folder: FolderItem, onToggle: () -> Unit) {
    val background by animateColorAsState(
        targetValue = if (folder.selected) Ink.AccentSoft else Ink.Surface,
        animationSpec = motionSpec(Motion.settle()),
        label = "folderFill",
    )
    val outline by animateColorAsState(
        targetValue = if (folder.selected) Ink.Accent else Ink.Hairline,
        animationSpec = motionSpec(Motion.settle()),
        label = "folderOutline",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(background)
            .border(1.dp, outline, Shapes.card)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onClick = onToggle,
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
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
            Spacer(Modifier.height(6.dp))
            LinearMeter(fraction = folder.reviewedFraction, color = Ink.Success, height = 3.dp)
        }
        SelectionMark(selected = folder.selected)
    }
}

@Composable
private fun StartBar(
    label: String,
    count: Int?,
    bytes: Long,
    hasSelection: Boolean,
    onClear: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Ink.Background.copy(alpha = 0.92f), Ink.Background)))
            .padding(start = Space.page, end = Space.page, top = Space.xl, bottom = Space.m),
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
            if (hasSelection) {
                PadlasTextButton(text = "Törlés", onClick = onClear, color = Ink.TextSecondary)
            }
        }
        Spacer(Modifier.height(Space.s))
        val ready = count != null && count > 0
        PadlasButton(
            text = when {
                count == null -> "Számolás…"
                count == 0 -> "Nincs átnézendő elem"
                else -> "Indítás · ${Format.count(count)} elem · ${Format.bytes(bytes)}"
            },
            onClick = onStart,
            enabled = ready,
            icon = Icons.Rounded.PlayArrow,
            modifier = Modifier.fillMaxWidth(),
        )
        if (count == 0) {
            Spacer(Modifier.height(Space.xs))
            Text(
                text = "Kapcsold ki a „Csak új” szűrőt, ha a már átnézett elemeket is újra látnád.",
                style = PadlasTheme.type.micro,
                color = Ink.TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(0.dp))
    }
}
