package hu.padlas.feature.triage

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.NumericText
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.theme.Space

/**
 * One folder, everything preselected for deletion. A thirty second pass over WhatsApp
 * Images is the point; anything slower and the folder never gets cleared at all.
 */
@Composable
fun FolderRoute(bucketId: Long, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: FolderViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onDeleteResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(bucketId) { viewModel.load(bucketId) }

    LaunchedEffect(state.request) {
        val request = state.request ?: return@LaunchedEffect
        launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
    }

    LaunchedEffect(state.falling) {
        if (state.falling) onBack()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = Space.l),
    ) {
        PadlasTopBar(title = state.title.uppercase(), onBack = onBack)

        if (state.loading) {
            EmptyState(text = "Betöltés.")
            return@Column
        }
        if (state.items.isEmpty()) {
            EmptyState(text = "Ez a mappa üres.", actionLabel = "Vissza", onAction = onBack)
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Space.s),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            NumericText(
                text = "${Format.count(state.discardCount)} kidobásra · ${Format.bytes(state.discardBytes)}",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                PadlasTextButton(text = "Mindet megtartom", onClick = viewModel::keepAll)
                PadlasTextButton(text = "Mindet kidobom", onClick = viewModel::discardAll)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            items(state.items, key = { it.id }) { item ->
                ShotTile(
                    item = item,
                    kept = item.id in state.kept,
                    falling = state.falling,
                    onToggle = { viewModel.toggleKeep(item.id) },
                    onUnlock = {},
                )
            }
        }

        CommitBar(
            count = state.discardCount,
            bytes = state.discardBytes,
            busy = state.committing,
            onConfirm = viewModel::requestDelete,
            modifier = Modifier.padding(vertical = Space.m),
        )

        state.message?.let { message ->
            ScreenMessage(message = message)
            LaunchedEffect(message) { viewModel.dismissMessage() }
        }
    }
}
