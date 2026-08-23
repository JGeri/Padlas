package hu.padlas.feature.browse

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.design.component.Chip
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.NumericText
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.component.SectionLabel
import hu.padlas.core.design.component.SelectableTile
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pick a folder, pick a month, see the actual pictures, decide. Every other screen decides
 * for you and shows you the result; this one is the opposite, and it is the answer to
 * "four thousand pictures older than a year" being a number you cannot look at.
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

    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onTrashResult(result.resultCode == Activity.RESULT_OK) }

    LaunchedEffect(olderThanDaysPreset, bucketPreset) {
        if (olderThanDaysPreset != null) viewModel.applyPreset(olderThanDaysPreset)
        if (bucketPreset != null && bucketPreset != 0L) viewModel.toggleBucket(bucketPreset)
    }

    LaunchedEffect(state.trashRequest) {
        val request = state.trashRequest ?: return@LaunchedEffect
        trashLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = Space.l),
    ) {
        PadlasTopBar(
            title = "VÁLOGATÁS",
            onBack = onBack,
            action = {
                if (!state.filter.isEmpty) {
                    PadlasTextButton(text = "Szűrő törlése", onClick = viewModel::clearFilter)
                }
            },
        )

        FilterRails(state = state, viewModel = viewModel)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Space.s),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            NumericText(
                text = "${Format.count(state.totals.itemCount)} db · ${Format.bytes(state.totals.totalBytes)}",
            )
            if (state.totals.itemCount > 0) {
                PadlasTextButton(
                    text = if (state.selected.isEmpty()) "Mind kijelöl" else "Kijelölés törlése",
                    onClick = {
                        if (state.selected.isEmpty()) viewModel.selectAllMatching() else viewModel.clearSelection()
                    },
                )
            }
        }

        if (items.itemCount == 0) {
            EmptyState(
                text = if (state.filter.isEmpty) {
                    "Nincs mit mutatni. Fut már a beolvasás?"
                } else {
                    "Erre a szűrőre nincs találat."
                },
                actionLabel = if (state.filter.isEmpty) null else "Szűrő törlése",
                onAction = viewModel::clearFilter,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                items(
                    count = items.itemCount,
                    key = items.itemKey { it.mediaStoreId },
                ) { index ->
                    val asset = items[index] ?: return@items
                    AssetTile(
                        asset = asset,
                        selected = asset.mediaStoreId in state.selected,
                        model = viewModel.uriOf(asset),
                        onToggle = { viewModel.toggleSelection(asset) },
                    )
                }
            }
        }

        if (state.selected.isNotEmpty()) {
            ActionBar(
                count = state.selected.size,
                bytes = state.selectedBytes,
                busy = state.busy,
                onArchive = { viewModel.archiveSelected(onArchiveQueued) },
                onTrash = viewModel::trashSelected,
                onProtect = viewModel::protectSelected,
            )
        }

        state.message?.let { message ->
            Text(
                text = message,
                style = PadlasTheme.type.label,
                color = Ink.Faded,
                modifier = Modifier.padding(bottom = Space.s),
            )
            LaunchedEffect(message) { viewModel.dismissMessage() }
        }
    }
}

@Composable
private fun FilterRails(state: BrowseUiState, viewModel: BrowseViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            item {
                Chip(
                    text = "1 évnél régebbi",
                    selected = state.filter.olderThanDays == 365,
                    onClick = { viewModel.applyPreset(if (state.filter.olderThanDays == 365) null else 365) },
                )
            }
            item {
                Chip(
                    text = "fél évnél régebbi",
                    selected = state.filter.olderThanDays == 180,
                    onClick = { viewModel.applyPreset(if (state.filter.olderThanDays == 180) null else 180) },
                )
            }
            item {
                Chip(
                    text = "csak videó",
                    selected = state.filter.mediaType == BrowseMediaType.VIDEO,
                    onClick = {
                        viewModel.setMediaType(
                            if (state.filter.mediaType == BrowseMediaType.VIDEO) {
                                BrowseMediaType.ALL
                            } else {
                                BrowseMediaType.VIDEO
                            },
                        )
                    },
                )
            }
            item {
                Chip(
                    text = "csak fotó",
                    selected = state.filter.mediaType == BrowseMediaType.PHOTO,
                    onClick = {
                        viewModel.setMediaType(
                            if (state.filter.mediaType == BrowseMediaType.PHOTO) {
                                BrowseMediaType.ALL
                            } else {
                                BrowseMediaType.PHOTO
                            },
                        )
                    },
                )
            }
        }

        SectionLabel(text = "MAPPÁK")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            items(state.buckets, key = { it.bucketId }) { bucket ->
                Chip(
                    text = "${bucket.bucketName.ifBlank { bucket.relativePath }} · ${Format.bytes(bucket.totalBytes)}",
                    selected = bucket.bucketId in state.filter.buckets,
                    onClick = { viewModel.toggleBucket(bucket.bucketId) },
                )
            }
        }

        SectionLabel(text = "HÓNAPOK")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            items(state.months, key = { it.month }) { month ->
                Chip(
                    text = "${month.month.hungarianMonth()} · ${Format.bytes(month.totalBytes)}",
                    selected = month.month in state.filter.months,
                    onClick = { viewModel.toggleMonth(month.month) },
                )
            }
        }
    }
}

@Composable
private fun AssetTile(asset: Asset, selected: Boolean, model: Any?, onToggle: () -> Unit) {
    SelectableTile(
        model = model,
        selected = selected,
        onToggle = onToggle,
        badge = if (asset.isVideo) asset.durationMs?.let(Format::duration) ?: "videó" else null,
        image = { tileModifier ->
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = tileModifier,
                contentScale = ContentScale.Crop,
            )
        },
    )
}

@Composable
private fun ActionBar(
    count: Int,
    bytes: Long,
    busy: Boolean,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onProtect: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = Space.m)) {
        NumericText(text = "${Format.count(count)} kijelölve · ${Format.bytes(bytes)}")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.s),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            PadlasButton(
                text = "Padlásra",
                onClick = onArchive,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
            PadlasButton(
                text = "Kukába",
                onClick = onTrash,
                enabled = !busy,
                destructive = true,
                modifier = Modifier.weight(1f),
            )
        }
        PadlasTextButton(text = "Védetté teszem", onClick = onProtect)
    }
}

private val MONTH_LABEL: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy. MMMM", Locale.forLanguageTag("hu-HU"))

/** "2024-07" -> "2024. július"; an unparseable value stays as it is rather than crashing. */
private fun String.hungarianMonth(): String =
    runCatching { YearMonth.parse(this).format(MONTH_LABEL) }.getOrDefault(this)
