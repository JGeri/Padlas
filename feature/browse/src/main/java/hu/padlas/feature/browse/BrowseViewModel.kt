package hu.padlas.feature.browse

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.sqlite.db.SimpleSQLiteQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.common.format.Format
import hu.padlas.core.common.result.outcomeOf
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.dao.AuditDao
import hu.padlas.core.database.dao.UploadJobDao
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.database.entity.AuditEntry
import hu.padlas.core.database.entity.JobState
import hu.padlas.core.database.entity.UploadJobEntity
import hu.padlas.core.database.model.BucketAggregate
import hu.padlas.core.database.model.MonthAggregate
import hu.padlas.core.database.model.SizeAndCount
import hu.padlas.core.media.MediaOps
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One system dialog for the whole batch (INV-3). */
internal class TrashRequest(
    val intentSender: IntentSender,
    val sizes: Map<Long, Long>,
    /** More was selected than one dialog carries; the next round follows this one. */
    val more: Boolean,
) {
    val bytes: Long get() = sizes.values.sum()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class BrowseViewModel @Inject constructor(
    private val assetDao: AssetDao,
    private val uploadJobDao: UploadJobDao,
    private val auditDao: AuditDao,
    private val mediaOps: MediaOps,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val filter = MutableStateFlow(BrowseFilter())
    private val local = MutableStateFlow(LocalState())
    private var pendingTrash: TrashRequest? = null

    /** What the rounds of one "Kukába" have binned so far; the last round reports the total. */
    private var trashedSoFar = 0L

    val items: Flow<PagingData<Asset>> = filter
        .flatMapLatest { current ->
            val query = BrowseSql.page(current, now())
            Pager(PagingConfig(pageSize = PAGE, initialLoadSize = PAGE * 2, enablePlaceholders = false)) {
                assetDao.rawPage(SimpleSQLiteQuery(query.sql, query.args.toTypedArray()))
            }.flow
        }
        .cachedIn(viewModelScope)

    val state: StateFlow<BrowseUiState> = combine(
        assetDao.observeBuckets(),
        assetDao.observeMonths(),
        filter,
        local,
    ) { buckets, months, current, localState ->
        BrowseUiState(
            loading = false,
            buckets = buckets.filter { it.itemCount > 0 },
            months = months,
            filter = current,
            totals = localState.totals,
            selected = localState.selected,
            selectedBytes = localState.selectedBytes,
            trashRequest = localState.trashRequest,
            busy = localState.busy,
            message = localState.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseUiState())

    init {
        recomputeTotals()
    }

    fun applyPreset(olderThanDays: Int?) {
        filter.update { it.copy(olderThanDays = olderThanDays) }
        onFilterChanged()
    }

    fun toggleBucket(bucketId: Long) {
        filter.update { current ->
            val buckets = if (bucketId in current.buckets) current.buckets - bucketId else current.buckets + bucketId
            current.copy(buckets = buckets)
        }
        onFilterChanged()
    }

    fun toggleMonth(month: String) {
        filter.update { current ->
            val months = if (month in current.months) current.months - month else current.months + month
            current.copy(months = months)
        }
        onFilterChanged()
    }

    fun setMediaType(type: BrowseMediaType) {
        filter.update { it.copy(mediaType = type) }
        onFilterChanged()
    }

    fun clearFilter() {
        filter.value = BrowseFilter()
        onFilterChanged()
    }

    fun toggleSelection(asset: Asset) = local.update { current ->
        val selected = if (asset.mediaStoreId in current.selected) {
            current.selected - asset.mediaStoreId
        } else {
            current.selected + asset.mediaStoreId
        }
        current.copy(
            selected = selected,
            selectedBytes = current.selectedBytes +
                if (asset.mediaStoreId in current.selected) -asset.sizeBytes else asset.sizeBytes,
        )
    }

    fun clearSelection() = local.update { it.copy(selected = emptySet(), selectedBytes = 0) }

    /** Selects what the current filter matches, capped so one tap stays predictable. */
    fun selectAllMatching() {
        viewModelScope.launch {
            local.update { it.copy(busy = true) }
            val query = BrowseSql.ids(filter.value, now(), SELECT_ALL_CAP)
            val rows = withContext(io) {
                assetDao.rawAssets(SimpleSQLiteQuery(query.sql, query.args.toTypedArray()))
            }
            local.update { current ->
                current.copy(
                    busy = false,
                    selected = rows.map { it.mediaStoreId }.toSet(),
                    selectedBytes = rows.sumOf { it.sizeBytes },
                    message = if (rows.size >= SELECT_ALL_CAP) {
                        "Az első ${Format.count(SELECT_ALL_CAP)} elem van kijelölve."
                    } else {
                        null
                    },
                )
            }
        }
    }

    /** Queues verified uploads; the local copy stays until the attic frees it (INV-1). */
    fun archiveSelected(onQueued: () -> Unit) {
        val ids = local.value.selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            local.update { it.copy(busy = true) }
            val rows = withContext(io) { ids.chunked(SQLITE_ARGUMENT_LIMIT).flatMap { assetDao.byIds(it) } }
            val now = now()
            val queued = withContext(io) {
                outcomeOf {
                    uploadJobDao.upsertAll(
                        rows.map { asset ->
                            UploadJobEntity(
                                assetId = asset.mediaStoreId,
                                totalBytes = asset.sizeBytes,
                                state = JobState.PENDING,
                                updatedAt = now,
                            )
                        },
                    )
                    rows.map { it.mediaStoreId }.chunked(SQLITE_ARGUMENT_LIMIT).forEach {
                        assetDao.setState(it, AssetState.UPLOADING)
                    }
                }.isSuccess
            }
            if (!queued) {
                local.update { it.copy(busy = false, message = NOT_WRITTEN) }
                return@launch
            }
            local.update {
                it.copy(
                    busy = false,
                    selected = emptySet(),
                    selectedBytes = 0,
                    message = "${Format.count(rows.size)} elem sorba állt a padlásra.",
                )
            }
            onQueued()
            recomputeTotals()
        }
    }

    /** Two phase (INV-2): the system bin first, and the bytes are still occupied there. */
    fun trashSelected() {
        if (pendingTrash != null) return
        trashedSoFar = 0L
        nextTrashRound()
    }

    private fun nextTrashRound() {
        val ids = local.value.selected.toList()
        if (ids.isEmpty() || pendingTrash != null) return
        viewModelScope.launch {
            local.update { it.copy(busy = true) }
            val rows = withContext(io) {
                ids.chunked(SQLITE_ARGUMENT_LIMIT)
                    .flatMap { assetDao.byIds(it) }
                    .filter { !it.protectedManually && it.state != AssetState.PROTECTED }
            }
            if (rows.isEmpty()) {
                local.update { it.copy(busy = false, message = "A kijelölésben csak védett elem van.") }
                return@launch
            }
            val batch = rows.take(MediaOps.MAX_REQUEST)
            val sender = mediaOps.trashRequest(batch.map { mediaOps.uriFor(it.mediaStoreId, it.isVideo) })
            if (sender == null) {
                local.update { it.copy(busy = false, message = "A kuka nem nyitható meg.") }
                return@launch
            }
            pendingTrash = TrashRequest(sender, batch.associate { it.mediaStoreId to it.sizeBytes }, more = rows.size > batch.size)
            local.update { it.copy(trashRequest = pendingTrash) }
        }
    }

    fun onTrashResult(confirmed: Boolean) {
        val batch = pendingTrash
        pendingTrash = null
        local.update { it.copy(trashRequest = null) }
        if (batch == null) return
        if (!confirmed) {
            local.update { it.copy(busy = false, message = "Nem került semmi a kukába.") }
            return
        }
        viewModelScope.launch {
            val now = now()
            // The files are in the bin whatever happens here; a failed write is left for the
            // next index pass to square instead of taking the screen down.
            val recorded = withContext(io) {
                outcomeOf {
                    assetDao.markTrashed(batch.sizes.keys.toList(), now)
                    auditDao.insertAll(
                        batch.sizes.map { (id, size) ->
                            AuditEntry(
                                timestamp = now,
                                action = AuditAction.TRASHED,
                                assetId = id,
                                sizeBytes = size,
                                reversible = true,
                            )
                        },
                    )
                }.isSuccess
            }
            trashedSoFar += batch.bytes
            local.update {
                it.copy(
                    busy = false,
                    selected = if (batch.more) it.selected - batch.sizes.keys else emptySet(),
                    selectedBytes = if (batch.more) (it.selectedBytes - batch.bytes).coerceAtLeast(0) else 0,
                    message = when {
                        !recorded -> NOT_WRITTEN
                        batch.more -> "${Format.bytes(trashedSoFar)} a kukában · jön a következő adag"
                        else -> "${Format.bytes(trashedSoFar)} a kukában. Amíg ott van, helyet foglal."
                    },
                )
            }
            recomputeTotals()
            if (batch.more) nextTrashRound()
        }
    }

    fun protectSelected() {
        val ids = local.value.selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val written = withContext(io) {
                outcomeOf { ids.chunked(SQLITE_ARGUMENT_LIMIT).forEach { assetDao.setProtected(it, true) } }.isSuccess
            }
            if (!written) {
                local.update { it.copy(message = NOT_WRITTEN) }
                return@launch
            }
            local.update {
                it.copy(
                    selected = emptySet(),
                    selectedBytes = 0,
                    message = "${Format.count(ids.size)} elem védett lett.",
                )
            }
        }
    }

    fun dismissMessage() = local.update { it.copy(message = null) }

    fun uriOf(asset: Asset) = mediaOps.uriFor(asset.mediaStoreId, asset.isVideo)

    private fun onFilterChanged() {
        clearSelection()
        recomputeTotals()
    }

    private fun recomputeTotals() {
        viewModelScope.launch {
            val query = BrowseSql.totals(filter.value, now())
            val totals = withContext(io) {
                assetDao.rawSizeAndCount(SimpleSQLiteQuery(query.sql, query.args.toTypedArray()))
            }
            local.update { it.copy(totals = totals) }
        }
    }

    private fun now(): Long = System.currentTimeMillis()

    private data class LocalState(
        val totals: SizeAndCount = SizeAndCount(0, 0),
        val selected: Set<Long> = emptySet(),
        val selectedBytes: Long = 0,
        val trashRequest: TrashRequest? = null,
        val busy: Boolean = false,
        val message: String? = null,
    )

    private companion object {
        const val PAGE = 60
        const val SELECT_ALL_CAP = 2_000
        const val SQLITE_ARGUMENT_LIMIT = 900
        const val NOT_WRITTEN = "A változást nem sikerült menteni. Megtelt a tárhely?"
    }
}

internal data class BrowseUiState(
    val loading: Boolean = true,
    val buckets: List<BucketAggregate> = emptyList(),
    val months: List<MonthAggregate> = emptyList(),
    val filter: BrowseFilter = BrowseFilter(),
    val totals: SizeAndCount = SizeAndCount(0, 0),
    val selected: Set<Long> = emptySet(),
    val selectedBytes: Long = 0,
    val trashRequest: TrashRequest? = null,
    val busy: Boolean = false,
    val message: String? = null,
)
