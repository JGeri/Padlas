package hu.padlas.feature.transcode

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.IntentSender
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.JobState
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.component.ProgressStrip
import hu.padlas.core.design.component.SectionLabel
import hu.padlas.core.design.component.StaggeredColumnItem
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space
import hu.padlas.core.design.theme.grain
import hu.padlas.feature.transcode.ui.VideoCard

/**
 * The video list, largest first, where a card's height is the file size. Nothing moves
 * until a compression finishes; then exactly one card changes shape.
 */
@Composable
fun VideoRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: VideoViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val removalRequest by viewModel.removalRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(removalRequest) {
        val request = removalRequest ?: return@LaunchedEffect
        context.findActivity()?.let { activity -> startTrashDialog(activity, request) }
        viewModel.removalRequestConsumed()
    }

    // MediaStore is the only source of truth for what the system dialog actually did.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPendingRemovals()
        onPauseOrDispose { }
    }

    Column(modifier = modifier.fillMaxSize().grain()) {
        PadlasTopBar(title = "Videók", onBack = onBack)
        Header(
            state = state,
            onStart = viewModel::startBatch,
            onStop = viewModel::stopBatch,
            onReplace = viewModel::replaceOriginals,
        )
        if (state.videos.isEmpty()) {
            EmptyState(
                text = "Nincs tömöríthető videó. Ide csak a 80 MB fölötti klipek kerülnek.",
                modifier = Modifier.padding(Space.l),
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            itemsIndexed(items = state.videos, key = { _, item -> item.assetId }) { index, item ->
                StaggeredColumnItem(index = index) {
                    VideoCard(item = item, onRetry = { viewModel.retry(item.assetId) })
                }
            }
        }
    }
}

@Composable
private fun Header(
    state: VideoUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReplace: () -> Unit,
) {
    val finished = state.videos.count { it.state == JobState.SUCCEEDED && !it.published }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l)) {
        SectionLabel(text = "Nyereség")
        Text(
            text = Format.bytes(state.saved.totalBytes) + " · " +
                Format.count(state.saved.itemCount) + " videó",
            style = PadlasTheme.type.numericLarge,
            color = Ink.Paper,
        )
        when {
            state.throttled -> Notice("Szünetel: a készülék meleg.", Ink.Brass)
            state.running -> Notice("Csak töltőn és feltöltött akkumulátorral halad.", Ink.Faded)
            state.pendingJobs > 0 -> Notice(Format.count(state.pendingJobs) + " videó vár a sorban.", Ink.Dust)
            else -> Unit
        }
        if (state.run.running && state.run.fraction > 0f) {
            ProgressStrip(
                fraction = state.run.fraction,
                label = state.run.label,
                modifier = Modifier.fillMaxWidth().padding(top = Space.s),
            )
        }
        if (state.pendingRemovalIds.isNotEmpty()) {
            Notice(
                "Az eredetik még " + Format.bytes(state.pendingRemovalBytes) + " helyet foglalnak.",
                Ink.Faded,
            )
        }
        Column(modifier = Modifier.padding(vertical = Space.m)) {
            if (finished > 0) {
                PadlasButton(
                    text = "Eredetik cseréje · " + Format.count(finished) + " videó",
                    onClick = onReplace,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PadlasButton(
                text = if (state.running) "Leállítás" else "Tömörítés indítása",
                onClick = if (state.running) onStop else onStart,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().padding(top = Space.s),
            )
        }
    }
}

@Composable
private fun Notice(text: String, color: Color) {
    Text(
        text = text,
        style = PadlasTheme.type.label,
        color = color,
        modifier = Modifier.padding(top = Space.xs),
    )
}

private fun startTrashDialog(activity: Activity, request: IntentSender) {
    runCatching {
        activity.startIntentSenderForResult(request, TRASH_REQUEST_CODE, null, 0, 0, 0)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val TRASH_REQUEST_CODE = 4202
