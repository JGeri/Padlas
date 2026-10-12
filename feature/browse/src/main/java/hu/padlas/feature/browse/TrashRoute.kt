package hu.padlas.feature.browse

import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.common.format.Format
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
import hu.padlas.core.design.component.hairlineTop
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
    val message: String? = null,
) {
    val totalBytes: Long get() = items.sumOf { it.sizeBytes }
    val selectedBytes: Long get() = items.filter { it.id in selected }.sumOf { it.sizeBytes }
}

private enum class TrashOp { EMPTY, DELETE_SELECTED, RESTORE }

private class BinRequest(val sender: IntentSender, val op: TrashOp, val items: List<TrashedMedia>)

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

    fun emptyAll() = launch(TrashOp.EMPTY, _state.value.items)

    fun deleteSelected() = launch(TrashOp.DELETE_SELECTED, _state.value.items.filter { it.id in _state.value.selected })

    fun restoreSelected() = launch(TrashOp.RESTORE, _state.value.items.filter { it.id in _state.value.selected })

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
            withContext(io) {
                if (current.op == TrashOp.RESTORE) {
                    assetDao.markUntrashed(ids)
                } else {
                    assetDao.deleteByIds(ids)
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
            }
            _state.update {
                it.copy(
                    busy = false,
                    selected = emptySet(),
                    message = if (current.op == TrashOp.RESTORE) {
                        "${Format.count(ids.size)} elem visszaállítva"
                    } else {
                        "${Format.bytes(bytes)} felszabadítva"
                    },
                )
            }
            reload()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun launch(op: TrashOp, items: List<TrashedMedia>) {
        if (items.isEmpty() || request != null) return
        val batch = items.take(MAX_BATCH)
        val uris = batch.map { it.uri }
        val sender = runCatching {
            if (op == TrashOp.RESTORE) mediaOps.untrashRequest(uris) else mediaOps.deleteRequest(uris)
        }.getOrNull()
        if (sender == null) {
            _state.update { it.copy(message = "A rendszer most nem engedte a műveletet.") }
            return
        }
        request = BinRequest(sender, op, batch)
        _state.update { it.copy(busy = true) }
        viewModelScope.launch { _requests.send(sender) }
    }

    private companion object {
        const val MAX_BATCH = 1_500
    }
}

@Composable
fun TrashRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: TrashViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onResult(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(viewModel) {
        viewModel.requests.collect { launcher.launch(IntentSenderRequest.Builder(it).build()) }
    }
    var confirm by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmText by remember { mutableStateOf("") }
    val guarded: (String, () -> Unit) -> Unit = { text, action ->
        if (viewModel.skipsSystemDialog()) {
            confirmText = text
            confirm = action
        } else {
            action()
        }
    }

    Box(modifier = modifier.fillMaxSize().auroraBackground().systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            PadlasTopBar(
                title = "Kuka",
                subtitle = if (state.items.isEmpty()) null else "${Format.count(state.items.size)} elem · ${Format.bytes(state.totalBytes)}",
                onBack = onBack,
                action = {
                    if (state.items.isNotEmpty()) {
                        PadlasTextButton(
                            text = if (state.selected.size == state.items.size) "Kijelölés törlése" else "Mind",
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
                        title = "Üres a kuka",
                        text = "Nincs törölt fotó vagy videó, ami még helyet foglalna.",
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
                                    guarded("${Format.bytes(state.totalBytes)} véglegesen törlődik. Ez nem vonható vissza.") {
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
                    text = "${Format.count(state.selected.size)} kijelölve · ${Format.bytes(state.selectedBytes)}",
                    style = PadlasTheme.type.caption,
                    color = Ink.TextSecondary,
                )
                Spacer(Modifier.height(Space.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    PadlasButton(
                        text = "Visszaállítás",
                        onClick = viewModel::restoreSelected,
                        style = ButtonStyle.Secondary,
                        icon = Icons.Rounded.RestoreFromTrash,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    )
                    PadlasButton(
                        text = "Törlés",
                        onClick = {
                            guarded("${Format.bytes(state.selectedBytes)} véglegesen törlődik. Ez nem vonható vissza.") {
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
            message = state.message,
            onShown = viewModel::consumeMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp),
        )
    }

    confirm?.let { action ->
        ConfirmDialog(
            title = "Végleges törlés?",
            text = confirmText,
            confirmLabel = "Törlés",
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
                    Text(text = "foglal helyet a kukában", style = PadlasTheme.type.caption, color = Ink.TextSecondary)
                }
            }
            Text(
                text = "A rendszer 30 nap után magától törli őket. Ha most kell a hely, ürítsd a kukát; a kijelölt elemeket vissza is állíthatod.",
                style = PadlasTheme.type.body,
                color = Ink.TextSecondary,
            )
            PadlasButton(
                text = "Kuka ürítése",
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
                text = "A Samsung Galéria saját kukájában további ${Format.bytes(bytes)} van. Azt a Galéria appban ürítheted.",
                style = PadlasTheme.type.caption,
                color = Ink.TextSecondary,
                modifier = Modifier.padding(start = Space.m),
            )
        }
    }
}

private fun daysLeft(expiresAt: Long): String? {
    if (expiresAt <= 0L) return null
    val days = ((expiresAt - System.currentTimeMillis()) / DAY_MS).coerceAtLeast(0L)
    return if (days == 0L) "ma törlődik" else "${Format.count(days)} nap"
}

private const val DAY_MS = 24L * 60 * 60 * 1000

/** The selection bar over the grid: dense enough to read over photos, no blur to pay for. */
private val BarTint = Ink.SurfaceSolid.copy(alpha = 0.94f)
