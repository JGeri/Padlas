package hu.padlas.feature.triage

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.Space
import kotlinx.coroutines.delay

/**
 * The Kidobó (spec M9). Not a swipe deck: the ephemeral set arrives grouped and already
 * selected for deletion, and the user only taps the survivors. 19 left swipes per right
 * one is the wrong interaction; a grid of 4 000 tiles is two or three minutes.
 */
@Composable
fun DiscardRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: DiscardViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = haptics()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onDeleteResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(viewModel, launcher) {
        viewModel.events.collect { event ->
            when (event) {
                is DiscardEvent.Confirm ->
                    launcher.launch(IntentSenderRequest.Builder(event.intentSender).build())
            }
        }
    }

    // Two beats, not one: the grid falls here, the meter drains on the Vault.
    LaunchedEffect(state.falling) {
        if (!state.falling) return@LaunchedEffect
        haptics?.commit()
        delay(FALL_AWAY_MS)
        onBack()
    }

    DiscardScreen(
        state = state,
        selection = selection,
        onBack = onBack,
        onToggle = viewModel::toggleKeep,
        onKeepAll = viewModel::keepAll,
        onDiscardAll = viewModel::discardAll,
        onUnlock = viewModel::unlock,
        onConfirm = viewModel::requestDelete,
        onRetriage = { TriageScheduler.enqueueTriage(context) },
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
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(Ink.Background)) {
        PadlasTopBar(title = "Kidobó", onBack = onBack)
        when {
            state.loading -> ScreenMessage(message = "Betöltés.")
            state.groups.isEmpty() -> EmptyState(
                text = "Nincs kidobható képernyőkép. A triázs nem talált semmit. Újra?",
                actionLabel = "Triázs indítása",
                onAction = onRetriage,
            )

            else -> DiscardGrid(
                state = state,
                selection = selection,
                onToggle = onToggle,
                onKeepAll = onKeepAll,
                onDiscardAll = onDiscardAll,
                onUnlock = onUnlock,
                modifier = Modifier.weight(1f),
            )
        }
        state.message?.let { ScreenMessage(message = it) }
        CommitBar(
            count = selection.discardCount,
            bytes = selection.discardBytes,
            busy = state.committing || state.falling,
            onConfirm = onConfirm,
        )
    }
}

@Composable
private fun DiscardGrid(
    state: DiscardUiState,
    selection: DiscardSelection,
    onToggle: (Long) -> Unit,
    onKeepAll: (String) -> Unit,
    onDiscardAll: (String) -> Unit,
    onUnlock: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(TileSize),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.s),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        state.groups.forEach { group ->
            item(key = "fejlec-${group.key}", span = { GridItemSpan(maxLineSpan) }) {
                GroupHeader(
                    group = group,
                    tally = selection.tally(group.key),
                    onKeepAll = { onKeepAll(group.key) },
                    onDiscardAll = { onDiscardAll(group.key) },
                    onUnlockAll = { onUnlock(group.items.filter { it.locked }.map { it.id }) },
                )
            }
            items(items = group.items, key = { it.id }) { item ->
                ShotTile(
                    item = item,
                    kept = item.id in selection.kept,
                    falling = state.falling,
                    onToggle = { onToggle(item.id) },
                    onUnlock = { onUnlock(listOf(item.id)) },
                )
            }
        }
        item(key = "kuka", span = { GridItemSpan(maxLineSpan) }) {
            TrashSection(
                trash = state.trash,
                pendingCount = selection.discardCount,
                pendingBytes = selection.discardBytes,
            )
        }
    }
}
