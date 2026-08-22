package hu.padlas.feature.archive.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.GhostTile
import hu.padlas.core.design.component.NumericText
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.component.ProgressStrip
import hu.padlas.core.design.component.SectionLabel
import hu.padlas.core.design.component.SelectableTile
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space
import hu.padlas.feature.archive.ArchiveScheduler
import hu.padlas.feature.archive.drive.AuthState
import java.io.File

/**
 * The attic browser. A ghost keeps its place in the grid: the tile stays where it was,
 * one pixel of patina around an eight percent thumbnail, and the grid never re-flows.
 */
@Composable
fun AtticRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: AtticViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onConsentResult(result.data) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onGhostResult(result.resultCode == Activity.RESULT_OK) }

    LaunchedEffect(state.ghostRequest) {
        val request = state.ghostRequest ?: return@LaunchedEffect
        deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
    }

    val authState = state.authState
    LaunchedEffect(authState) {
        if (authState is AuthState.ConsentNeeded) {
            consentLauncher.launch(IntentSenderRequest.Builder(authState.intent.intentSender).build())
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = Space.l),
    ) {
        PadlasTopBar(
            title = "PADLÁS",
            onBack = onBack,
            action = {
                if (state.selected.isNotEmpty()) {
                    PadlasTextButton(text = "Kijelölés törlése", onClick = viewModel::clearSelection)
                }
            },
        )

        AuthCard(state = state, onConnect = viewModel::connect)

        if (!state.desktopNoticeRead) {
            DesktopNotice(onDismiss = viewModel::dismissDesktopNotice)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Space.s),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            NumericText(
                text = "${Format.count(state.assets.size)} elem · ${Format.count(state.ghosts)} szellem",
            )
            NumericText(text = "${Format.bytes(state.reclaimable)} helyben is megvan")
        }

        if (state.progress.active) {
            ProgressStrip(
                fraction = state.progress.fraction,
                label = "${state.progress.name} · ${Format.bytes(state.progress.bytesSent)}",
                modifier = Modifier.padding(bottom = Space.s),
            )
        } else if (state.queue.itemCount > 0) {
            NumericText(
                text = "${Format.count(state.queue.itemCount)} vár a sorban · ${Format.bytes(state.queue.totalBytes)}",
            )
        }

        if (state.assets.isEmpty()) {
            EmptyState(
                text = "A padlás üres. A válogatóból vagy a rácsból emelhetsz ide.",
                actionLabel = "Vissza",
                onAction = onBack,
            )
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            items(state.assets, key = { it.mediaStoreId }) { asset ->
                AtticTile(
                    asset = asset,
                    selected = asset.mediaStoreId in state.selected,
                    onToggle = { viewModel.toggle(asset.mediaStoreId) },
                    model = if (asset.state == AssetState.GHOST) {
                        asset.ghostThumbPath?.let(::File)
                    } else {
                        viewModel.uriOf(asset)
                    },
                )
            }
        }

        ActionRow(
            selectedCount = state.selected.size,
            busy = state.busy,
            onUpload = {
                viewModel.enqueueSelected { ArchiveScheduler.enqueueUploads(context) }
            },
            onGhost = viewModel::requestGhosting,
        )

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
private fun AtticTile(
    asset: Asset,
    selected: Boolean,
    onToggle: () -> Unit,
    model: Any?,
) {
    val image: @Composable (Modifier) -> Unit = { tileModifier ->
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = tileModifier,
            contentScale = ContentScale.Crop,
        )
    }
    if (asset.state == AssetState.GHOST) {
        GhostTile(model = model, image = image)
    } else {
        SelectableTile(
            model = model,
            selected = selected,
            onToggle = onToggle,
            badge = if (asset.state == AssetState.UPLOADING) "fel" else null,
            image = image,
        )
    }
}

@Composable
private fun AuthCard(state: AtticUiState, onConnect: () -> Unit) {
    when (val auth = state.authState) {
        AuthState.Connected -> Unit

        AuthState.Unconfigured -> PadlasCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionLabel(text = "DRIVE")
                Text(
                    text = "Hiányzik a Drive kliensazonosító. A README leírja, hogyan tedd be a " +
                        "drive_web_client_id resource-ba.",
                    style = PadlasTheme.type.body,
                    color = Ink.Faded,
                )
            }
        }

        is AuthState.Failed -> PadlasCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionLabel(text = "DRIVE")
                Text(text = auth.message, style = PadlasTheme.type.body, color = Ink.Paper)
                PadlasTextButton(text = "Újra", onClick = onConnect)
            }
        }

        else -> PadlasCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionLabel(text = "DRIVE")
                Text(
                    text = "A padlás a te Drive-od. Az app csak azokat a fájlokat látja, amiket ő tett fel.",
                    style = PadlasTheme.type.body,
                    color = Ink.Faded,
                )
                PadlasTextButton(text = "Összekötés", onClick = onConnect)
            }
        }
    }
}

/**
 * Android 15 caps `dataSync` foreground work at about six hours a day, so a first upload
 * of a full library over the phone would take weeks. Saying so once is more honest than
 * letting the queue crawl.
 */
@Composable
private fun DesktopNotice(onDismiss: () -> Unit) {
    PadlasCard(background = Ink.Raised, modifier = Modifier.fillMaxWidth()) {
        Column {
            SectionLabel(text = "ELSŐ FELTÖLTÉS")
            Text(
                text = "A teljes könyvtárat asztali géppel érdemes felvinni: a rendszer napi hat órára " +
                    "korlátozza a háttérfeltöltést. Az app utána már csak a napi különbséget kezeli.",
                style = PadlasTheme.type.body,
                color = Ink.Paper,
            )
            PadlasTextButton(text = "Értem", onClick = onDismiss)
        }
    }
}

@Composable
private fun ActionRow(
    selectedCount: Int,
    busy: Boolean,
    onUpload: () -> Unit,
    onGhost: () -> Unit,
) {
    if (selectedCount == 0) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.m),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        PadlasButton(
            text = "Padlásra (${Format.count(selectedCount)})",
            onClick = onUpload,
            enabled = !busy,
            modifier = Modifier.weight(1f),
        )
        PadlasButton(
            text = "Helyet szabadítok",
            onClick = onGhost,
            enabled = !busy,
            modifier = Modifier.weight(1f),
        )
    }
}
