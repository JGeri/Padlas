package hu.padlas.feature.sort.pending

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.SwipeChoice
import hu.padlas.core.design.component.ButtonStyle
import hu.padlas.core.design.component.ConfirmDialog
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.component.SelectableTile
import hu.padlas.core.design.component.Toast
import hu.padlas.core.design.component.hairlineTop
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.auroraBackground
import hu.padlas.core.design.theme.Space
import hu.padlas.core.media.MediaOps
import hu.padlas.core.media.MediaThumb
import hu.padlas.feature.sort.DeleteBatch
import hu.padlas.feature.sort.SwipeRepository
import hu.padlas.feature.sort.ui.ZoomViewer
import hu.padlas.feature.sort.ui.rememberSystemRequest
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
internal data class PendingItem(val asset: Asset, val thumb: MediaThumb, val marked: Boolean)

@Immutable
internal data class PendingUiState(
    val loading: Boolean = true,
    val items: List<PendingItem> = emptyList(),
    val busy: Boolean = false,
    val done: Boolean = false,
    val message: String? = null,
) {
    val marked: List<PendingItem> get() = items.filter { it.marked }
    val markedBytes: Long get() = marked.sumOf { it.asset.sizeBytes }
}

/**
 * The marked pile, in a grid. The list is a snapshot taken on entry, so a picture that is
 * tapped back to "keep" stays on screen and can be tapped again.
 */
@HiltViewModel
internal class PendingViewModel @Inject constructor(
    private val repository: SwipeRepository,
    private val mediaOps: MediaOps,
) : ViewModel() {

    private val _state = MutableStateFlow(PendingUiState())
    val state: StateFlow<PendingUiState> = _state.asStateFlow()

    private val _requests = Channel<DeleteBatch>(Channel.BUFFERED)
    val requests: Flow<DeleteBatch> = _requests.receiveAsFlow()
    private var batch: DeleteBatch? = null

    init {
        viewModelScope.launch {
            val assets = repository.pendingNow()
            _state.value = PendingUiState(
                loading = false,
                items = assets.map {
                    PendingItem(it, MediaThumb(mediaOps.uriFor(it.mediaStoreId, it.isVideo), 384), marked = true)
                },
            )
        }
    }

    fun toggle(item: PendingItem) {
        val marked = !item.marked
        _state.update { state ->
            state.copy(items = state.items.map { if (it.asset.mediaStoreId == item.asset.mediaStoreId) it.copy(marked = marked) else it })
        }
        viewModelScope.launch {
            repository.setChoice(listOf(item.asset.mediaStoreId), if (marked) SwipeChoice.DELETE else SwipeChoice.KEEP)
        }
    }

    fun skipsSystemDialog(): Boolean = repository.canSkipSystemDialog()

    fun delete() {
        val assets = _state.value.marked.map { it.asset }
        val request = repository.deleteRequest(assets) ?: return
        batch = request
        _state.update { it.copy(busy = true) }
        viewModelScope.launch { _requests.send(request) }
    }

    fun onResult(confirmed: Boolean) {
        val request = batch
        batch = null
        if (request == null || !confirmed) {
            _state.update { it.copy(busy = false, message = if (request != null) "A törlés elmaradt." else null) }
            return
        }
        viewModelScope.launch {
            repository.onDeleted(request)
            val gone = request.ids.toSet()
            _state.update { state ->
                val left = state.items.filterNot { it.asset.mediaStoreId in gone }
                state.copy(
                    busy = false,
                    items = left,
                    done = left.none { it.marked },
                    message = if (request.permanent) {
                        "${Format.bytes(request.bytes)} felszabadítva"
                    } else {
                        "${Format.count(request.ids.size)} elem a kukába került"
                    },
                )
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

@Composable
fun PendingDeletionsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: PendingViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launch = rememberSystemRequest(viewModel::onResult)
    LaunchedEffect(viewModel) { viewModel.requests.collect { launch(it.intentSender) } }
    var confirm by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<android.net.Uri?>(null) }

    Box(modifier = modifier.fillMaxSize().auroraBackground().systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            PadlasTopBar(
                title = "Törlésre jelölve",
                subtitle = "${Format.count(state.marked.size)} elem · ${Format.bytes(state.markedBytes)} · koppints, ami maradjon",
                onBack = onBack,
            )
            when {
                state.loading -> Unit
                state.items.isEmpty() || state.done && state.marked.isEmpty() -> EmptyState(
                    title = "Nincs több törlendő",
                    text = "Minden megjelölt elemet elintéztél.",
                    icon = Icons.Rounded.DoneAll,
                    tint = Ink.Success,
                    actionLabel = "Vissza",
                    onAction = onBack,
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 108.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Space.l, end = Space.l, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(state.items, key = { it.asset.mediaStoreId }) { item ->
                        SelectableTile(
                            modifier = Modifier.animateItem(),
                            model = item.thumb,
                            selected = item.marked,
                            onToggle = { viewModel.toggle(item) },
                            selectedTint = Ink.Danger,
                            onLongPress = { preview = item.thumb.uri },
                            badge = if (item.asset.isVideo) item.asset.durationMs?.let(Format::duration) ?: "Videó" else null,
                            image = { tileModifier ->
                                AsyncImage(
                                    model = item.thumb,
                                    contentDescription = item.asset.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = tileModifier,
                                )
                            },
                        )
                    }
                }
            }
        }

        if (state.marked.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(BarTint)
                    .hairlineTop(Ink.HairlineStrong)
                    .padding(start = Space.page, end = Space.page, top = Space.xl, bottom = Space.l),
            ) {
                PadlasButton(
                    text = "${Format.count(state.marked.size)} elem törlése · ${Format.bytes(state.markedBytes)}",
                    onClick = { if (viewModel.skipsSystemDialog()) confirm = true else viewModel.delete() },
                    style = ButtonStyle.Destructive,
                    icon = Icons.Rounded.Delete,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Toast(
            message = state.message,
            onShown = viewModel::consumeMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp),
        )
    }

    preview?.let { uri -> ZoomViewer(uri = uri, onDismiss = { preview = null }) }

    if (confirm) {
        ConfirmDialog(
            title = "${Format.count(state.marked.size)} elem törlése?",
            text = "${Format.bytes(state.markedBytes)} kerül törlésre a telefonról.",
            confirmLabel = "Törlés",
            destructive = true,
            icon = Icons.Rounded.Delete,
            onConfirm = {
                confirm = false
                viewModel.delete()
            },
            onDismiss = { confirm = false },
        )
    }
}

/** The selection bar over the grid: dense enough to read over photos, no blur to pay for. */
private val BarTint = Ink.SurfaceSolid.copy(alpha = 0.94f)
