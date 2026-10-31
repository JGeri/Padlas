package hu.padlas.feature.browse

import android.content.IntentSender
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.common.format.Format
import hu.padlas.core.common.result.outcomeOf
import hu.padlas.core.common.text.UiText
import hu.padlas.core.common.text.uiPlural
import hu.padlas.core.common.text.uiText
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.dao.AuditDao
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.database.entity.AuditEntry
import hu.padlas.core.design.component.ButtonStyle
import hu.padlas.core.design.component.ConfirmDialog
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.IconBubble
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.component.SelectableTile
import hu.padlas.core.design.component.Toast
import hu.padlas.core.design.component.asString
import hu.padlas.core.design.component.hairlineTop
import hu.padlas.core.design.component.rememberSystemRequest
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.auroraBackground
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space
import hu.padlas.core.media.MediaOps
import hu.padlas.core.media.MediaThumb
import hu.padlas.core.media.OneDriveGuard
import hu.padlas.core.media.TrashedMedia
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
internal data class TrashUiState(
    val loading: Boolean = true,
    val items: List<TrashedMedia> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val samsungBytes: Long = 0,
    val busy: Boolean = false,
    val message: UiText? = null,
) {
    val totalBytes: Long get() = items.sumOf { it.sizeBytes }
    val selectedBytes: Long get() = items.filter { it.id in selected }.sumOf { it.sizeBytes }
}

private enum class TrashOp { EMPTY, DELETE_SELECTED, RESTORE }

/** A dialog's worth of ids is more than one statement may bind on an older SQLite. */
private const val SQLITE_ARGUMENT_LIMIT = 900

/** [rest] is what did not fit in this dialog; it goes in the next round. */
private class BinRequest(
    val sender: IntentSender,
    val op: TrashOp,
    val items: List<TrashedMedia>,
    val rest: List<TrashedMedia>,
)

/**
 * The system trash still occupies space for thirty days (INV-2). This is where the user
 * either gets the files back or gets the space back.
 */
@HiltViewModel
internal class TrashViewModel @Inject constructor(
    private val mediaOps: MediaOps,
    private val assetDao: AssetDao,
    private val auditDao: AuditDao,
    private val oneDriveGuard: OneDriveGuard,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(TrashUiState())
    val state: StateFlow<TrashUiState> = _state.asStateFlow()

    private val _requests = Channel<IntentSender>(Channel.BUFFERED)
    val requests: Flow<IntentSender> = _requests.receiveAsFlow()
    private var request: BinRequest? = null

    /** What the rounds of one operation have done so far; the last round reports the total. */
    private var doneCount = 0
    private var doneBytes = 0L

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val items = mediaOps.trashedItems()
            val samsung = withContext(io) { runCatching { oneDriveGuard.samsungGalleryTrashBytes() }.getOrDefault(0L) }
            _state.update { current ->
                current.copy(
                    loading = false,
                    items = items,
                    selected = current.selected.intersect(items.map { it.id }.toSet()),
                    samsungBytes = samsung,
                )
            }
        }
    }

    fun toggle(id: Long) = _state.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    fun selectAll() = _state.update {
        it.copy(selected = if (it.selected.size == it.items.size) emptySet() else it.items.map { item -> item.id }.toSet())
    }

    fun emptyAll() = begin(TrashOp.EMPTY, _state.value.items)

    fun deleteSelected() = begin(TrashOp.DELETE_SELECTED, _state.value.items.filter { it.id in _state.value.selected })

    fun restoreSelected() = begin(TrashOp.RESTORE, _state.value.items.filter { it.id in _state.value.selected })

    fun skipsSystemDialog(): Boolean = mediaOps.canManageMedia()

    fun onResult(confirmed: Boolean) {
        val current = request
        request = null
        if (current == null) return
        if (!confirmed) {
            _state.update { it.copy(busy = false) }
            return
        }
        viewModelScope.launch {
            val ids = current.items.map { it.id }
            val now = System.currentTimeMillis()
            val bytes = current.items.sumOf { it.sizeBytes }
            // The system already did it; a failed write here only leaves rows for the next
            // index pass to square, it must not take the screen down.
            val recorded = withContext(io) {
                outcomeOf {
                    ids.chunked(SQLITE_ARGUMENT_LIMIT).forEach { chunk ->
                        if (current.op == TrashOp.RESTORE) {
                            assetDao.markUntrashed(chunk)
                        } else {
                            assetDao.deleteByIds(chunk)
                        }
                    }
                    auditDao.insertAll(
                        current.items.map {
                            AuditEntry(
                                timestamp = now,
                                action = if (current.op == TrashOp.RESTORE) AuditAction.UNTRASHED else AuditAction.DELETED,
                                assetId = it.id,
                                sizeBytes = it.sizeBytes,
                                note = "Kuka",
                            )
                        },
                    )
                }.isSuccess
            }
            doneCount += ids.size
            doneBytes += bytes
            val text = if (current.op == TrashOp.RESTORE) {
                uiPlural(R.plurals.browse_trash_restored, doneCount, Format.count(doneCount))
            } else {
                uiText(R.string.browse_trash_freed, Format.bytes(doneBytes))
            }
            _state.update {
                it.copy(
                    busy = false,
                    selected = if (current.rest.isEmpty()) emptySet() else it.selected,
                    message = when {
                        !recorded -> uiText(R.string.browse_trash_not_recorded)
                        current.rest.isNotEmpty() -> uiText(R.string.browse_next_batch, text)
                        else -> text
                    },
                )
            }
            reload()
            startRound(current.op, current.rest)
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun begin(op: TrashOp, items: List<TrashedMedia>) {
        if (request != null || _state.value.busy) return
        doneCount = 0
        doneBytes = 0L
        startRound(op, items)
    }

    private fun startRound(op: TrashOp, items: List<TrashedMedia>) {
        if (items.isEmpty() || request != null || _state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val batch = items.take(MediaOps.MAX_REQUEST)
            val uris = batch.map { it.uri }
            val sender = if (op == TrashOp.RESTORE) mediaOps.untrashRequest(uris) else mediaOps.deleteRequest(uris)
            if (sender == null) {
                _state.update { it.copy(busy = false, message = uiText(R.string.browse_trash_refused)) }
                return@launch
            }
            request = BinRequest(sender, op, batch, items.drop(batch.size))
            _requests.send(sender)
        }
    }
}

@Composable
fun TrashRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: TrashViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launch = rememberSystemRequest(viewModel::onResult)
    LaunchedEffect(viewModel) {
        viewModel.requests.collect { launch(it) }
    }
    var confirm by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmBytes by remember { mutableLongStateOf(0L) }
    val guarded: (Long, () -> Unit) -> Unit = { bytes, action ->
        if (viewModel.skipsSystemDialog()) {
            confirmBytes = bytes
            confirm = action
        } else {
            action()
        }
    }

    Box(modifier = modifier.fillMaxSize().auroraBackground().systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            PadlasTopBar(
                title = stringResource(R.string.browse_trash_title),
                subtitle = if (state.items.isEmpty()) {
                    null
                } else {
                    pluralStringResource(
                        R.plurals.browse_items_size,
                        state.items.size,
                        Format.count(state.items.size),
                        Format.bytes(state.totalBytes),
                    )
                },
                onBack = onBack,
                action = {
                    if (state.items.isNotEmpty()) {
                        PadlasTextButton(
                            text = stringResource(
                                if (state.selected.size == state.items.size) R.string.browse_clear_selection else R.string.browse_select_all,
                            ),
                            onClick = viewModel::selectAll,
                        )
                    }
                },
            )
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Ink.Accent, strokeWidth = 3.dp)
                }

                state.items.isEmpty() -> Column {
                    EmptyState(
                        title = stringResource(R.string.browse_trash_empty_title),
                        text = stringResource(R.string.browse_trash_empty_text),
                        icon = Icons.Rounded.DeleteSweep,
                        tint = Ink.Success,
                    )
                    if (state.samsungBytes > 0) SamsungNote(state.samsungBytes, Modifier.padding(horizontal = Space.page))
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 108.dp),
                    contentPadding = PaddingValues(start = Space.l, end = Space.l, bottom = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "hero") {
                        Column(Modifier.padding(bottom = Space.m), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                            TrashHero(
                                bytes = state.totalBytes,
                                busy = state.busy,
                                onEmpty = {
                                    guarded(state.totalBytes) {
                                        viewModel.emptyAll()
                                    }
                                },
                            )
                            if (state.samsungBytes > 0) SamsungNote(state.samsungBytes)
                        }
                    }
                    items(state.items, key = { it.id }) { item ->
                        val thumb = remember(item.uri) { MediaThumb(item.uri, 384) }
                        SelectableTile(
                            modifier = Modifier.animateItem(),
                            model = thumb,
                            selected = item.id in state.selected,
                            onToggle = { viewModel.toggle(item.id) },
                            badge = daysLeft(item.expiresAt),
                            image = { tileModifier ->
                                Box(tileModifier, contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (item.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Delete,
                                        contentDescription = null,
                                        tint = Ink.TextTertiary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    AsyncImage(
                                        model = thumb,
                                        contentDescription = item.displayName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.matchParentSize(),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }

        if (state.selected.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(BarTint)
                    .hairlineTop(Ink.HairlineStrong)
                    .padding(start = Space.page, end = Space.page, top = Space.xl, bottom = Space.l),
            ) {
                Text(
                    text = stringResource(R.string.browse_selected_size, Format.count(state.selected.size), Format.bytes(state.selectedBytes)),
                    style = PadlasTheme.type.caption,
                    color = Ink.TextSecondary,
                )
                Spacer(Modifier.height(Space.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    PadlasButton(
                        text = stringResource(R.string.browse_trash_restore),
                        onClick = viewModel::restoreSelected,
                        style = ButtonStyle.Secondary,
                        icon = Icons.Rounded.RestoreFromTrash,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    )
                    PadlasButton(
                        text = stringResource(R.string.browse_delete),
                        onClick = {
                            guarded(state.selectedBytes) {
                                viewModel.deleteSelected()
                            }
                        },
                        style = ButtonStyle.Destructive,
                        icon = Icons.Rounded.Delete,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Toast(
            message = state.message?.asString(),
            onShown = viewModel::consumeMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp),
        )
    }

    confirm?.let { action ->
        ConfirmDialog(
            title = stringResource(R.string.browse_trash_confirm_title),
            text = stringResource(R.string.browse_trash_confirm_text, Format.bytes(confirmBytes)),
            confirmLabel = stringResource(R.string.browse_delete),
            destructive = true,
            icon = Icons.Rounded.Delete,
            onConfirm = {
                confirm = null
                action()
            },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun TrashHero(bytes: Long, busy: Boolean, onEmpty: () -> Unit) {
    PadlasCard(modifier = Modifier.fillMaxWidth(), border = Ink.Danger.copy(alpha = 0.3f)) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(icon = Icons.Rounded.DeleteSweep, tint = Ink.Danger, size = 48.dp, iconSize = 24.dp)
                Column(Modifier.padding(start = Space.m)) {
                    Text(text = Format.bytes(bytes), style = PadlasTheme.type.displaySmall, color = Ink.Text)
                    Text(text = stringResource(R.string.browse_trash_space), style = PadlasTheme.type.caption, color = Ink.TextSecondary)
                }
            }
            Text(
                text = stringResource(R.string.browse_trash_explain),
                style = PadlasTheme.type.body,
                color = Ink.TextSecondary,
            )
            PadlasButton(
                text = stringResource(R.string.browse_trash_empty_action),
                onClick = onEmpty,
                style = ButtonStyle.Destructive,
                icon = Icons.Rounded.DeleteSweep,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SamsungNote(bytes: Long, modifier: Modifier = Modifier) {
    PadlasCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon = Icons.Rounded.Info, tint = Ink.Info)
            Text(
                text = stringResource(R.string.browse_trash_samsung, Format.bytes(bytes)),
                style = PadlasTheme.type.caption,
                color = Ink.TextSecondary,
                modifier = Modifier.padding(start = Space.m),
            )
        }
    }
}

@Composable
private fun daysLeft(expiresAt: Long): String? {
    if (expiresAt <= 0L) return null
    val days = ((expiresAt - System.currentTimeMillis()) / DAY_MS).coerceAtLeast(0L)
    return if (days == 0L) {
        stringResource(R.string.browse_trash_expires_today)
    } else {
        pluralStringResource(R.plurals.browse_trash_days_left, days.toInt(), Format.count(days))
    }
}

private const val DAY_MS = 24L * 60 * 60 * 1000

/** The selection bar over the grid: dense enough to read over photos, no blur to pay for. */
private val BarTint = Ink.SurfaceSolid.copy(alpha = 0.94f)
