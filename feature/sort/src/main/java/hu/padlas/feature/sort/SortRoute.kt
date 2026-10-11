package hu.padlas.feature.sort

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.NumericText
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.component.ProgressStrip
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space
import hu.padlas.feature.sort.ui.SwipeDeck

/**
 * The sorting deck runs last, on the six to eight percent no earlier layer could decide.
 * Nothing is deleted here: left swipes pile up and leave through a single system dialog.
 *
 * [onArchiveRequested] receives the ids whose upload rows were just queued, so the host
 * can wake its upload worker without this module depending on the archive feature.
 */
@Composable
fun SortRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onArchiveRequested: (List<Long>) -> Unit = {},
) {
    val viewModel: SortViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val trashRequest by viewModel.trashRequest.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onTrashResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(trashRequest) {
        val request = trashRequest ?: return@LaunchedEffect
        launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
    }

    LaunchedEffect(Unit) {
        viewModel.archiveRequests.collect { ids -> onArchiveRequested(ids) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = Space.l),
    ) {
        PadlasTopBar(title = "VÁLOGATÓ", onBack = onBack)

        val session = state.session
        when {
            state.loading -> EmptyState(text = "Betöltés.")

            session == null || state.finished -> EmptyState(
                text = if (state.totalCount == 0) {
                    "Nincs mit válogatni. A korábbi rétegek elvitték a maradékot is."
                } else {
                    "Kész: ${Format.count(state.decidedCount)} döntés."
                },
                actionLabel = "Újra",
                onAction = viewModel::load,
            )

            else -> {
                SessionHeader(state = state, session = session)

                SwipeDeck(
                    cards = state.visibleCards,
                    undoTicket = state.undoTicket,
                    undoneDirection = state.undoneDirection,
                    onSwipe = viewModel::onSwipe,
                    onProtect = viewModel::onProtect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )

                Footer(state = state, onUndo = viewModel::undo, onFinish = viewModel::finish)
            }
        }

        state.message?.let { message ->
            Text(
                text = message,
                style = PadlasTheme.type.label,
                color = Ink.TextSecondary,
                modifier = Modifier.padding(vertical = Space.s),
            )
            LaunchedEffect(message) { viewModel.consumeMessage() }
        }
    }
}

@Composable
private fun SessionHeader(state: SortUiState, session: SortSession) {
    Column(modifier = Modifier.padding(vertical = Space.s)) {
        Text(text = session.header, style = PadlasTheme.type.title, color = Ink.Text)
        ProgressStrip(
            fraction = if (state.totalCount == 0) 0f else state.decidedCount.toFloat() / state.totalCount,
            label = "${Format.count(state.decidedCount)} / ${Format.count(state.totalCount)}",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.s),
        )
    }
}

@Composable
private fun Footer(state: SortUiState, onUndo: () -> Unit, onFinish: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.l),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.undoDepth > 0) {
            PadlasTextButton(text = "Visszavonás", onClick = onUndo)
        } else {
            Text(text = "Tartsd nyomva: védett", style = PadlasTheme.type.micro, color = Ink.TextTertiary)
        }

        if (state.pendingCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumericText(text = "${Format.count(state.pendingCount)} · ${Format.bytes(state.pendingBytes)}")
                PadlasTextButton(text = "Kukába", onClick = onFinish)
            }
        }
    }
}
