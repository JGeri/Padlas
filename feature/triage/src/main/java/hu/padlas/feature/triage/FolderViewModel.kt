package hu.padlas.feature.triage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.common.result.Outcome
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.media.MediaOps
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The inverse mode of spec M6. In these folders nine out of ten files are going, so the
 * grid arrives fully selected for deletion and the user taps out what stays. A swipe deck
 * here would mean nine left swipes for every right one.
 */
@HiltViewModel
internal class FolderViewModel @Inject constructor(
    private val assetDao: AssetDao,
    private val committer: DiscardCommitter,
    private val mediaOps: MediaOps,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(FolderUiState())
    val state: StateFlow<FolderUiState> = _state.asStateFlow()

    private var pending: PendingDelete? = null
    private var bucketId: Long = 0

    fun load(bucketId: Long) {
        if (this.bucketId == bucketId && !_state.value.loading) return
        this.bucketId = bucketId
        viewModelScope.launch {
            val assets = withContext(io) { assetDao.bucketAssets(bucketId, LIMIT) }
            val items = assets.map { asset ->
                ShotItem(
                    id = asset.mediaStoreId,
                    model = mediaOps.uriFor(asset.mediaStoreId, asset.isVideo),
                    sizeBytes = asset.sizeBytes,
                    locked = asset.protectedManually || asset.state == AssetState.PROTECTED ||
                        asset.isFavorite,
                )
            }
            _state.update {
                it.copy(
                    loading = false,
                    title = assets.firstOrNull()?.bucketName ?: "Mappa",
                    items = items,
                    // Locked rows start kept: they are never part of a batch (INV-4).
                    kept = items.filter { item -> item.locked }.map { item -> item.id }.toSet(),
                )
            }
        }
    }

    fun toggleKeep(id: Long) {
        val item = _state.value.items.firstOrNull { it.id == id } ?: return
        if (item.locked) return
        _state.update { current ->
            val kept = if (id in current.kept) current.kept - id else current.kept + id
            current.copy(kept = kept)
        }
    }

    fun keepAll() = _state.update { it.copy(kept = it.items.map(ShotItem::id).toSet()) }

    fun discardAll() = _state.update { current ->
        current.copy(kept = current.items.filter(ShotItem::locked).map(ShotItem::id).toSet())
    }

    fun requestDelete() {
        val current = _state.value
        val ids = current.discardIds()
        if (ids.isEmpty() || current.committing) return
        viewModelScope.launch {
            _state.update { it.copy(committing = true) }
            when (val outcome = committer.prepare(ids)) {
                is Outcome.Success -> {
                    pending = outcome.value
                    _state.update { it.copy(request = outcome.value) }
                }

                is Outcome.Failure -> _state.update {
                    it.copy(committing = false, message = outcome.message)
                }
            }
        }
    }

    fun onDeleteResult(confirmed: Boolean) {
        val batch = pending
        pending = null
        _state.update { it.copy(request = null) }
        if (batch == null) return
        if (!confirmed) {
            _state.update { it.copy(committing = false, message = "Nem történt törlés.") }
            return
        }
        viewModelScope.launch {
            val bytes = committer.finish(batch)
            _state.update { current ->
                current.copy(
                    committing = false,
                    falling = true,
                    items = current.items.filterNot { it.id in batch.sizes.keys },
                    message = "${hu.padlas.core.common.format.Format.bytes(bytes)} szabadult fel.",
                )
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private companion object {
        const val LIMIT = 3_000
    }
}

internal data class FolderUiState(
    val loading: Boolean = true,
    val title: String = "",
    val items: List<ShotItem> = emptyList(),
    val kept: Set<Long> = emptySet(),
    val committing: Boolean = false,
    val falling: Boolean = false,
    val request: PendingDelete? = null,
    val message: String? = null,
) {
    val discardCount: Int get() = items.count { it.id !in kept && !it.locked }

    val discardBytes: Long get() = items.filter { it.id !in kept && !it.locked }.sumOf { it.sizeBytes }

    fun discardIds(): List<Long> = items.filter { it.id !in kept && !it.locked }.map { it.id }
}
