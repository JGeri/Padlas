package hu.padlas.feature.triage

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.HoldToConfirm
import hu.padlas.core.design.component.IconBubble
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.component.Toast
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space
import kotlinx.coroutines.delay

/**
 * The discard pile (spec M9). Not a swipe deck: the ephemeral set arrives grouped and
 * already selected for deletion, and the user only taps the survivors. 19 left swipes per
 * right one is the wrong interaction; a grid of 4 000 tiles is two or three minutes.
 */
@Composable
fun DiscardRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: DiscardViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = haptics()

    // The fall-away timer and the back button can both fire; popping twice would take the
    // overview with it.
    var left by remember { mutableStateOf(false) }
    val leave = {
        if (!left) {
            left = true
            onBack()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onDeleteResult(result.resultCode == Activity.RESULT_OK)
    }

    BackHandler { leave() }

    LaunchedEffect(viewModel, launcher) {
        viewModel.events.collect { event ->
            when (event) {
                is DiscardEvent.Confirm ->
                    launcher.launch(IntentSenderRequest.Builder(event.intentSender).build())
            }
        }
    }

    // Two beats, not one: the grid falls here, the meter drains on the overview.
    LaunchedEffect(state.falling) {
        if (!state.falling) return@LaunchedEffect
        haptics?.commit()
        delay(FALL_AWAY_MS)
        leave()
    }

    DiscardScreen(
        state = state,
        selection = selection,
        onBack = leave,
        onToggle = viewModel::toggleKeep,
        onKeepAll = viewModel::keepAll,
        onDiscardAll = viewModel::discardAll,
        onUnlock = viewModel::unlock,
        onConfirm = viewModel::requestDelete,
        onRetriage = { TriageScheduler.enqueueTriage(context) },
        onMessageShown = viewModel::dismissMessage,
        modifier = modifier,
    )
}

@Composable
private fun DiscardScreen(
    state: DiscardUiState,
    selection: DiscardSelection,
    onBack: () -> Unit,
    onToggle: (Long) -> Unit,
    onKeepAll: (String) -> Unit,
    onDiscardAll: (String) -> Unit,
    onUnlock: (List<Long>) -> Unit,
    onConfirm: () -> Unit,
    onRetriage: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasContent = !state.loading && state.groups.isNotEmpty()
    val navBottom = navigationBarBottom()
    val gridState = rememberLazyGridState()
    val scrolled by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0 }
    }
    val zoom = rememberZoomState()

    ZoomHost(
        state = zoom,
        modifier = modifier.fillMaxSize(),
        actions = { target ->
            val id = target.key as? Long
            if (id != null) {
                KeepVerdict(
                    kept = id in selection.kept,
                    locked = target.tag == true,
                    onToggle = { onToggle(id) },
                )
            }
        },
    ) {
        GlassScreen(
            scrolled = scrolled,
            topBar = {
                PadlasTopBar(
                    title = "Eldobható képek",
                    onBack = onBack,
                    subtitle = if (hasContent) {
                        "${Format.count(selection.discardCount)} kijelölve · ${Format.bytes(selection.discardBytes)}"
                    } else {
                        null
                    },
                )
            },
            overlay = { haze ->
                PinnedActionBar(visible = hasContent, haze = haze, modifier = Modifier.align(Alignment.BottomCenter)) {
                    CommitBar(
                        count = selection.discardCount,
                        bytes = selection.discardBytes,
                        busy = state.committing || state.falling,
                        onConfirm = onConfirm,
                    )
                }
                Toast(
                    message = state.message,
                    onShown = onMessageShown,
                    icon = Icons.Rounded.Info,
                    tint = Ink.Info,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = navBottom + if (hasContent) CommitBarClearance else Space.l),
                )
            },
        ) {
            when {
                state.loading -> Unit
                state.groups.isEmpty() -> EmptyState(
                    title = "Nincs eldobható kép",
                    text = "Nem találtunk képernyőképet vagy letöltött képet, amire már nincs szükség.",
                    icon = Icons.Rounded.CheckCircle,
                    tint = Ink.Success,
                    actionLabel = "Átnézés újra",
                    onAction = onRetriage,
                    modifier = Modifier.padding(top = topBarClearance() + Space.xxl),
                )

                else -> DiscardGrid(
                    state = state,
                    selection = selection,
                    gridState = gridState,
                    onToggle = onToggle,
                    onKeepAll = onKeepAll,
                    onDiscardAll = onDiscardAll,
                    onUnlock = onUnlock,
                    onView = { group, item ->
                        zoom.open(
                            ZoomTarget(
                                key = item.id,
                                model = item.model,
                                title = group.title,
                                caption = Format.bytes(item.sizeBytes),
                                tag = item.locked,
                            ),
                        )
                    },
                    topPadding = topBarClearance() + Space.s,
                    bottomPadding = navBottom + CommitBarClearance,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun DiscardGrid(
    state: DiscardUiState,
    selection: DiscardSelection,
    gridState: LazyGridState,
    onToggle: (Long) -> Unit,
    onKeepAll: (String) -> Unit,
    onDiscardAll: (String) -> Unit,
    onUnlock: (List<Long>) -> Unit,
    onView: (TriageGroup, ShotItem) -> Unit,
    topPadding: Dp,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    var showProtected by rememberSaveable { mutableStateOf(false) }
    val open = state.groups.filterNot { it.locked }
    val protectedGroups = state.groups.filter { it.locked }
    val entrance = rememberEntrance()

    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        state = gridState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = Space.page, end = Space.page, top = topPadding, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(TileSpacing),
        verticalArrangement = Arrangement.spacedBy(TileSpacing),
    ) {
        item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
            ReclaimHero(
                label = "Felszabadítható",
                bytes = selection.discardBytes,
                caption = "Képernyőképek és letöltött képek, amikre valószínűleg már nincs szükséged. " +
                    "A felismert csoportok ki vannak jelölve törlésre, koppints arra, amit megtartanál. " +
                    "A még fel nem ismert képeket csak a „Mind törlése” jelöli ki. " +
                    "Hosszan nyomva bármelyik képet megnézheted nagyban.",
                modifier = Modifier.entrance(entrance),
            )
        }
        open.forEach { group ->
            item(key = "header-${group.key}", span = { GridItemSpan(maxLineSpan) }) {
                GroupHeader(
                    group = group,
                    tally = selection.tally(group.key),
                    onKeepAll = { onKeepAll(group.key) },
                    onDiscardAll = { onDiscardAll(group.key) },
                    modifier = Modifier.entrance(entrance).then(itemMotion()),
                )
            }
            items(items = group.items, key = { it.id }) { item ->
                ShotTile(
                    item = item,
                    kept = item.id in selection.kept,
                    falling = state.falling,
                    onToggle = { onToggle(item.id) },
                    onView = { onView(group, item) },
                    modifier = Modifier.entrance(entrance).then(itemMotion()),
                )
            }
        }
        protectedGroups.forEach { group ->
            protectedSection(
                group = group,
                expanded = showProtected,
                onExpand = { showProtected = !showProtected },
                keptIds = selection.kept,
                falling = state.falling,
                entrance = entrance,
                onToggle = onToggle,
                onUnlock = onUnlock,
                onView = { item -> onView(group, item) },
            )
        }
        item(key = "kuka", span = { GridItemSpan(maxLineSpan) }) {
            TrashSection(
                trash = state.trash,
                pendingCount = selection.discardCount,
                pendingBytes = selection.discardBytes,
                modifier = Modifier.then(itemMotion()),
            )
        }
    }
}

/**
 * INV-1c: SECRET shots are never part of the batch. They are folded away by default and
 * only a long, deliberate hold lifts the protection; even then they arrive kept, so a
 * second tap is needed before any of them is deleted.
 */
private fun LazyGridScope.protectedSection(
    group: TriageGroup,
    expanded: Boolean,
    onExpand: () -> Unit,
    keptIds: Set<Long>,
    falling: Boolean,
    entrance: Entrance,
    onToggle: (Long) -> Unit,
    onUnlock: (List<Long>) -> Unit,
    onView: (ShotItem) -> Unit,
) {
    item(key = "protected-${group.key}", span = { GridItemSpan(maxLineSpan) }) {
        PadlasCard(
            modifier = Modifier
                .entrance(entrance)
                .then(itemMotion())
                .fillMaxWidth()
                .padding(top = Space.xl, bottom = Space.s),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(icon = kindIcon(group.kind), tint = kindTint(group.kind))
                    Spacer(Modifier.width(Space.m))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = group.title,
                            style = PadlasTheme.type.bodyStrong,
                            color = Ink.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${Format.count(group.items.size)} kép · nem kerül törlésre",
                            style = PadlasTheme.type.caption,
                            color = Ink.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    PadlasTextButton(text = if (expanded) "Elrejtés" else "Megnézem", onClick = onExpand)
                }
                Text(
                    text = group.hint,
                    style = PadlasTheme.type.caption,
                    color = Ink.TextTertiary,
                    modifier = Modifier.padding(top = Space.s),
                )
            }
        }
    }
    if (!expanded) return
    items(items = group.items, key = { it.id }) { item ->
        ShotTile(
            item = item,
            kept = item.id in keptIds,
            falling = falling,
            onToggle = { onToggle(item.id) },
            onUnlock = { onUnlock(listOf(item.id)) },
            onView = { onView(item) },
            modifier = Modifier.then(itemMotion()),
        )
    }
    val locked = group.items.filter { it.locked }.map { it.id }
    if (locked.isNotEmpty()) {
        item(key = "unlock-${group.key}", span = { GridItemSpan(maxLineSpan) }) {
            Column(modifier = Modifier.then(itemMotion()).fillMaxWidth().padding(top = Space.m)) {
                HoldToConfirm(
                    label = "Tartsd lenyomva a feloldáshoz",
                    onConfirm = { onUnlock(locked) },
                    holdMillis = UNLOCK_HOLD_MS,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Feloldás után is megmaradnak, amíg egyenként ki nem jelölöd őket.",
                    style = PadlasTheme.type.caption,
                    color = Ink.TextTertiary,
                    modifier = Modifier.padding(top = Space.s),
                )
            }
        }
    }
}

private const val UNLOCK_HOLD_MS = 1_500
internal val CommitBarClearance = 172.dp
