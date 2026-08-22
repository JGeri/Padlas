package hu.padlas.feature.dedup

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.padlas.feature.dedup.ui.DedupScreen
import hu.padlas.feature.dedup.ui.DedupViewModel

/**
 * M2. One system dialog per batch (INV-3), and the database only learns about the deletion
 * after the system confirms it - anything else would leave the index claiming space that
 * is still occupied.
 */
@Composable
fun DedupRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: DedupViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onTrashResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(viewModel) {
        viewModel.trashRequests.collect { sender ->
            trashLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    DedupScreen(
        state = state,
        onBack = onBack,
        onResolveGroup = { groupId -> viewModel.requestTrash(listOf(groupId)) },
        onResolveSection = { kind ->
            val section = state.sections.firstOrNull { it.kind == kind }
            viewModel.requestTrash(section?.groups.orEmpty().map { it.id })
        },
        onPickWinner = viewModel::pickWinner,
        onRescan = viewModel::rescan,
        modifier = modifier,
    )
}
