package hu.padlas.feature.archive.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.padlas.core.common.format.Format
import hu.padlas.core.common.result.outcomeOf
import hu.padlas.core.common.text.UiText
import hu.padlas.core.common.text.uiPlural
import hu.padlas.core.common.text.uiText
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.dao.UploadJobDao
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.model.SizeAndCount
import hu.padlas.core.media.MediaOps
import hu.padlas.feature.archive.R
import hu.padlas.feature.archive.data.ArchivePreferences
import hu.padlas.feature.archive.data.ArchiveRepository
import hu.padlas.feature.archive.data.GhostBatch
import hu.padlas.feature.archive.data.GhostPreparation
import hu.padlas.feature.archive.data.UploadProgress
import hu.padlas.feature.archive.drive.AuthState
import hu.padlas.feature.archive.drive.DriveAuth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
internal class AtticViewModel @Inject constructor(
    private val assetDao: AssetDao,
    uploadJobDao: UploadJobDao,
    private val repository: ArchiveRepository,
    private val auth: DriveAuth,
    private val preferences: ArchivePreferences,
    private val mediaOps: MediaOps,
) : ViewModel() {

    private val selection = MutableStateFlow<Set<Long>>(emptySet())
    private val local = MutableStateFlow(LocalState())
    private var pendingGhost: GhostBatch? = null

    val state: StateFlow<AtticUiState> = combine(
        assetDao.observeArchived(LIST_LIMIT),
        uploadJobDao.observePending(),
        auth.state,
        repository.progress,
        combine(selection, local, preferences.desktopNoticeReadFlow) { picked, localState, noticeRead ->
            Triple(picked, localState, noticeRead)
        },
    ) { assets, queue, authState, progress, (picked, localState, noticeRead) ->
        AtticUiState(
            loading = false,
            assets = assets,
            selected = picked,
            queue = queue,
            authState = authState,
            progress = progress,
            desktopNoticeRead = noticeRead,
            ghostRequest = localState.request,
            message = localState.message,
            messageOk = localState.messageOk,
            busy = localState.busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AtticUiState())

    init {
        viewModelScope.launch { auth.refreshState() }
    }

    fun toggle(assetId: Long) = selection.update { current ->
        if (assetId in current) current - assetId else current + assetId
    }

    fun clearSelection() = selection.update { emptySet() }

    fun connect() {
        viewModelScope.launch { auth.accessToken() }
    }

    fun onConsentResult(data: Intent?) {
        viewModelScope.launch { auth.onConsentResult(data) }
    }

    fun dismissDesktopNotice() {
        viewModelScope.launch { outcomeOf { preferences.markDesktopNoticeRead() } }
    }

    /** Queue rows, then ask the scheduler to run; the route owns the WorkManager call. */
    fun enqueueSelected(onQueued: (List<Long>) -> Unit) {
        val ids = selection.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            local.update { it.copy(busy = true) }
            if (!outcomeOf { repository.enqueue(ids) }.isSuccess) {
                local.update {
                    it.copy(busy = false, message = uiText(R.string.archive_queue_failed), messageOk = false)
                }
                return@launch
            }
            selection.value = emptySet()
            local.update {
                it.copy(
                    busy = false,
                    message = uiPlural(R.plurals.archive_queued, ids.size, Format.count(ids.size)),
                    messageOk = true,
                )
            }
            onQueued(ids)
        }
    }

    /**
     * Every archived file at once: the same verified-copies-only path as a hand-picked
     * selection, just without making the user tick three hundred tiles first.
     */
    fun freeUpAll() {
        val ids = state.value.assets.filter { it.state == AssetState.ARCHIVED }.map { it.mediaStoreId }
        if (ids.isEmpty()) return
        selection.value = ids.toSet()
        requestGhosting()
    }

    /** INV-1: only verified copies can free local bytes, and only through one dialog. */
    fun requestGhosting() {
        val ids = selection.value.toList()
        if (ids.isEmpty() || pendingGhost != null) return
        viewModelScope.launch {
            local.update { it.copy(busy = true) }
            when (val preparation = repository.prepareGhosting(ids)) {
                is GhostPreparation.Ready -> {
                    pendingGhost = preparation.batch
                    local.update { it.copy(request = preparation.batch) }
                }

                is GhostPreparation.Refused -> local.update {
                    it.copy(busy = false, message = preparation.reason, messageOk = false)
                }
            }
        }
    }

    fun onGhostResult(confirmed: Boolean) {
        val batch = pendingGhost
        pendingGhost = null
        local.update { it.copy(request = null) }
        if (batch == null) return
        if (!confirmed) {
            local.update {
                it.copy(busy = false, message = uiText(R.string.archive_ghost_cancelled), messageOk = false)
            }
            return
        }
        viewModelScope.launch {
            val bytes = repository.finishGhosting(batch)
            val more = bytes != null && batch.more
            selection.value = if (more) selection.value - batch.sizes.keys else emptySet()
            local.update {
                when {
                    bytes == null -> it.copy(
                        busy = false,
                        message = uiText(R.string.archive_ghost_records_failed),
                        messageOk = false,
                    )
                    more -> it.copy(
                        busy = false,
                        message = uiText(R.string.archive_ghost_freed_more, Format.bytes(bytes)),
                        messageOk = true,
                    )
                    else -> it.copy(
                        busy = false,
                        message = uiText(R.string.archive_ghost_freed, Format.bytes(bytes)),
                        messageOk = true,
                    )
                }
            }
            // More was selected than one dialog carries; the freed ones left the selection.
            if (more) requestGhosting()
        }
    }

    fun dismissMessage() = local.update { it.copy(message = null) }

    fun uriOf(asset: Asset) = mediaOps.uriFor(asset.mediaStoreId, asset.isVideo)

    private data class LocalState(
        val busy: Boolean = false,
        val request: GhostBatch? = null,
        val message: UiText? = null,
        val messageOk: Boolean = true,
    )

    private companion object {
        const val LIST_LIMIT = 300
    }
}

internal data class AtticUiState(
    val loading: Boolean = true,
    val assets: List<Asset> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val queue: SizeAndCount = SizeAndCount(0, 0),
    val authState: AuthState = AuthState.Disconnected,
    val progress: UploadProgress = UploadProgress(),
    val desktopNoticeRead: Boolean = true,
    val ghostRequest: GhostBatch? = null,
    val message: UiText? = null,
    /** False for a cancel or a failure, so the toast does not wear a success tick. */
    val messageOk: Boolean = true,
    val busy: Boolean = false,
) {
    val ghosts: Int get() = assets.count { it.state == AssetState.GHOST }

    val archivedBytes: Long get() = assets.sumOf { it.sizeBytes }

    val reclaimable: Long
        get() = assets.filter { it.state == AssetState.ARCHIVED }.sumOf { it.sizeBytes }
}
