package hu.padlas.feature.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.design.component.ButtonSize
import hu.padlas.core.design.component.ButtonStyle
import hu.padlas.core.design.component.Chip
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasIconButton
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.component.PhotoViewer
import hu.padlas.core.design.component.SelectableTile
import hu.padlas.core.design.component.Toast
import hu.padlas.core.design.component.asString
import hu.padlas.core.design.component.hairlineTop
import hu.padlas.core.design.component.rememberSystemRequest
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.auroraBackground
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space
import hu.padlas.core.media.MediaThumb
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pick a folder, pick a month, see the actual pictures, act on a selection. The grid is
 * paged straight out of SQLite, so 25 000 items open as fast as 25.
 */
@Composable
fun BrowseRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    olderThanDaysPreset: Int? = null,
    bucketPreset: Long? = null,
    onArchiveQueued: () -> Unit = {},
) {
    val viewModel: BrowseViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()
    var preview by remember { mutableStateOf<android.net.Uri?>(null) }

    val launchTrash = rememberSystemRequest(viewModel::onTrashResult)

    LaunchedEffect(olderThanDaysPreset, bucketPreset) {
        if (olderThanDaysPreset != null) viewModel.applyPreset(olderThanDaysPreset)
        if (bucketPreset != null && bucketPreset != 0L) viewModel.toggleBucket(bucketPreset)
    }

    LaunchedEffect(state.trashRequest) {
        val request = state.trashRequest ?: return@LaunchedEffect
        launchTrash(request.intentSender)
    }

    Box(modifier = modifier.fillMaxSize().auroraBackground().systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            PadlasTopBar(
                title = stringResource(R.string.browse_title),
                subtitle = pluralStringResource(
                    R.plurals.browse_items_size,
                    state.totals.itemCount,
                    Format.count(state.totals.itemCount),
                    Format.bytes(state.totals.totalBytes),
                ),
                onBack = onBack,
                action = {
                    if (!state.filter.isEmpty) {
                        PadlasIconButton(
                            icon = Icons.Rounded.FilterAltOff,
                            contentDescription = stringResource(R.string.browse_clear_filter),
                            onClick = viewModel::clearFilter,
                            background = Ink.Surface,
                        )
                    }
                },
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Space.l, end = Space.l, bottom = if (state.selected.isEmpty()) Space.xl else 180.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
                    FilterRails(state = state, viewModel = viewModel)
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "select") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Space.s, bottom = Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (state.selected.isEmpty()) {
                                stringResource(R.string.browse_select_hint)
                            } else {
                                stringResource(R.string.browse_selected_count, Format.count(state.selected.size))
                            },
                            style = PadlasTheme.type.caption,
                            color = Ink.TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.totals.itemCount > 0) {
                            PadlasTextButton(
                                text = stringResource(if (state.selected.isEmpty()) R.string.browse_select_all else R.string.browse_clear_selection),
                                onClick = {
                                    if (state.selected.isEmpty()) viewModel.selectAllMatching() else viewModel.clearSelection()
                                },
                            )
                        }
                    }
                }
                if (items.itemCount == 0) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                        EmptyState(
                            title = stringResource(if (state.filter.isEmpty) R.string.browse_empty_title else R.string.browse_no_results_title),
                            text = stringResource(if (state.filter.isEmpty) R.string.browse_empty_text else R.string.browse_no_results_text),
                            icon = if (state.filter.isEmpty) Icons.Rounded.PhotoLibrary else Icons.Rounded.SearchOff,
                            actionLabel = if (state.filter.isEmpty) null else stringResource(R.string.browse_clear_filter),
                            onAction = viewModel::clearFilter,
                        )
                    }
                } else {
                    items(count = items.itemCount, key = items.itemKey { it.mediaStoreId }) { index ->
                        val asset = items[index] ?: return@items
                        AssetTile(
                            asset = asset,
                            selected = asset.mediaStoreId in state.selected,
                            model = viewModel.uriOf(asset),
                            onToggle = { viewModel.toggleSelection(asset) },
                            onLongPress = { preview = viewModel.uriOf(asset) },
                        )
                    }
                }
            }
        }

        if (state.selected.isNotEmpty()) {
            SelectionBar(
                count = state.selected.size,
                bytes = state.selectedBytes,
                busy = state.busy,
                onArchive = { viewModel.archiveSelected(onArchiveQueued) },
                onTrash = viewModel::trashSelected,
                onProtect = viewModel::protectSelected,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        Toast(
            message = state.message?.asString(),
            onShown = viewModel::dismissMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (state.selected.isEmpty()) Space.xl else 190.dp),
        )
    }

    preview?.let { uri ->
        PhotoViewer(onDismiss = { preview = null }) { modifier ->
            AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
        }
    }
}

@Composable
private fun FilterRails(state: BrowseUiState, viewModel: BrowseViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.padding(bottom = Space.s)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            item {
                Chip(
                    text = stringResource(R.string.browse_older_than_year),
                    icon = Icons.Rounded.History,
                    selected = state.filter.olderThanDays == 365,
                    onClick = { viewModel.applyPreset(if (state.filter.olderThanDays == 365) null else 365) },
                )
            }
            item {
                Chip(
                    text = stringResource(R.string.browse_older_than_half_year),
                    selected = state.filter.olderThanDays == 180,
                    onClick = { viewModel.applyPreset(if (state.filter.olderThanDays == 180) null else 180) },
                )
            }
            item {
                Chip(
                    text = stringResource(R.string.browse_photos),
                    icon = Icons.Rounded.Photo,
                    selected = state.filter.mediaType == BrowseMediaType.PHOTO,
                    onClick = {
                        viewModel.setMediaType(if (state.filter.mediaType == BrowseMediaType.PHOTO) BrowseMediaType.ALL else BrowseMediaType.PHOTO)
                    },
                )
            }
            item {
                Chip(
                    text = stringResource(R.string.browse_videos),
                    icon = Icons.Rounded.Videocam,
                    selected = state.filter.mediaType == BrowseMediaType.VIDEO,
                    onClick = {
                        viewModel.setMediaType(if (state.filter.mediaType == BrowseMediaType.VIDEO) BrowseMediaType.ALL else BrowseMediaType.VIDEO)
                    },
                )
            }
        }
        if (state.buckets.isNotEmpty()) {
            Text(text = stringResource(R.string.browse_folders), style = PadlasTheme.type.label, color = Ink.TextSecondary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                items(state.buckets, key = { it.bucketId }) { bucket ->
                    Chip(
                        text = bucket.bucketName.ifBlank { bucket.relativePath },
                        icon = Icons.Rounded.Folder,
                        trailing = Format.bytes(bucket.totalBytes),
                        selected = bucket.bucketId in state.filter.buckets,
                        onClick = { viewModel.toggleBucket(bucket.bucketId) },
                    )
                }
            }
        }
        if (state.months.isNotEmpty()) {
            Text(text = stringResource(R.string.browse_months), style = PadlasTheme.type.label, color = Ink.TextSecondary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                items(state.months, key = { it.month }) { month ->
                    Chip(
                        text = month.month.monthLabel(),
                        trailing = Format.bytes(month.totalBytes),
                        selected = month.month in state.filter.months,
                        onClick = { viewModel.toggleMonth(month.month) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetTile(asset: Asset, selected: Boolean, model: android.net.Uri, onToggle: () -> Unit, onLongPress: () -> Unit) {
    val thumb = remember(model) { MediaThumb(model, 384) }
    SelectableTile(
        model = thumb,
        selected = selected,
        onToggle = onToggle,
        onLongPress = onLongPress,
        badge = if (asset.isVideo) asset.durationMs?.let(Format::duration) ?: stringResource(R.string.browse_video) else null,
        image = { tileModifier ->
            AsyncImage(
                model = thumb,
                contentDescription = asset.displayName,
                modifier = tileModifier,
                contentScale = ContentScale.Crop,
            )
        },
    )
}

@Composable
private fun SelectionBar(
    count: Int,
    bytes: Long,
    busy: Boolean,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onProtect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BarTint)
                    .hairlineTop(Ink.HairlineStrong)
            .padding(start = Space.page, end = Space.page, top = Space.xl, bottom = Space.l),
    ) {
        Text(
            text = stringResource(R.string.browse_selected_size, Format.count(count), Format.bytes(bytes)),
            style = PadlasTheme.type.bodyStrong,
            color = Ink.Text,
        )
        Spacer(Modifier.height(Space.s))
        PadlasButton(
            text = stringResource(R.string.browse_move_to_trash),
            onClick = onTrash,
            enabled = !busy,
            style = ButtonStyle.Destructive,
            icon = Icons.Rounded.Delete,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            PadlasButton(
                text = stringResource(R.string.browse_upload),
                onClick = onArchive,
                enabled = !busy,
                style = ButtonStyle.Secondary,
                size = ButtonSize.Medium,
                icon = Icons.Rounded.CloudUpload,
                modifier = Modifier.weight(1f),
            )
            PadlasButton(
                text = stringResource(R.string.browse_protect),
                onClick = onProtect,
                enabled = !busy,
                style = ButtonStyle.Secondary,
                size = ButtonSize.Medium,
                icon = Icons.Rounded.Lock,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * "2024-07" -> "2024. július" in Hungarian, "July 2024" in English, in the app's language; an
 * unparseable value stays as it is rather than crashing. Stand-alone month names, because some
 * languages inflect the month inside a full date.
 */
private fun String.monthLabel(locale: Locale = Locale.getDefault()): String = runCatching {
    val pattern = if (locale.language == "hu") "yyyy. MMMM" else "LLLL yyyy"
    YearMonth.parse(this).format(DateTimeFormatter.ofPattern(pattern, locale))
}.getOrDefault(this)

/** The selection bar over the grid: dense enough to read over photos, no blur to pay for. */
private val BarTint = Ink.SurfaceSolid.copy(alpha = 0.94f)
