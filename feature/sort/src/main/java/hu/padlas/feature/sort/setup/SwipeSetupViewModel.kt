package hu.padlas.feature.sort.setup

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.padlas.core.common.format.Format
import hu.padlas.core.common.text.UiText
import hu.padlas.core.common.text.uiPlural
import hu.padlas.core.common.text.uiText
import hu.padlas.core.database.model.SizeAndCount
import hu.padlas.core.database.model.SwipeBucketStat
import hu.padlas.core.database.model.SwipeMonthStat
import hu.padlas.feature.sort.DeleteBatch
import hu.padlas.feature.sort.DeleteTally
import hu.padlas.feature.sort.MediaFilter
import hu.padlas.feature.sort.R
import hu.padlas.feature.sort.SwipeDates
import hu.padlas.feature.sort.SwipeFilter
import hu.padlas.feature.sort.SwipeOrder
import hu.padlas.feature.sort.SwipePreferences
import hu.padlas.feature.sort.SwipeRepository
import hu.padlas.feature.sort.joinedByDots
import hu.padlas.feature.sort.withNextBatch
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal enum class SetupTab { MONTHS, FOLDERS }

@Immutable
internal data class MonthCell(
    val key: String,
    val itemCount: Int,
    val totalBytes: Long,
    val reviewedCount: Int,
    val selected: Boolean,
    val cover: Uri?,
) {
    val reviewedFraction: Float get() = if (itemCount == 0) 0f else reviewedCount.toFloat() / itemCount
    val complete: Boolean get() = itemCount > 0 && reviewedCount >= itemCount
}

@Immutable
internal data class YearGroup(
    val year: Int,
    val months: List<MonthCell>,
) {
    val itemCount: Int get() = months.sumOf { it.itemCount }
    val totalBytes: Long get() = months.sumOf { it.totalBytes }
    val allSelected: Boolean get() = months.isNotEmpty() && months.all { it.selected }
}

@Immutable
internal data class FolderItem(
    val bucketId: Long,
    val name: UiText,
    val path: String,
    val itemCount: Int,
    val totalBytes: Long,
    val reviewedCount: Int,
    val cover: Uri?,
    val selected: Boolean,
) {
    val reviewedFraction: Float get() = if (itemCount == 0) 0f else reviewedCount.toFloat() / itemCount
}

@Immutable
internal data class SetupUiState(
    val loading: Boolean = true,
    val tab: SetupTab = SetupTab.MONTHS,
    val years: List<YearGroup> = emptyList(),
    val folders: List<FolderItem> = emptyList(),
    val filter: SwipeFilter = SwipeFilter(),
    val preview: SizeAndCount? = null,
    val pending: SizeAndCount = SizeAndCount(0, 0),
    val busy: Boolean = false,
    val message: UiText? = null,
    /** Long-press turns tiles into checkboxes; until then a tap opens the deck at once. */
    val selecting: Boolean = false,
) {
    val isEmpty: Boolean get() = !loading && years.isEmpty() && folders.isEmpty()

    val selectionLabel: UiText
        get() {
            val parts = ArrayList<UiText>()
            val months = filter.months.sortedDescending()
            when {
                months.size == 1 -> parts += UiText.Raw(SwipeDates.month(months.first()))
                months.size > 1 -> parts += uiPlural(R.plurals.sort_title_months, months.size, Format.count(months.size))
            }
            val folderNames = folders.filter { it.selected }.map { it.name }
            when {
                folderNames.size == 1 -> parts += folderNames.first()
                folderNames.size > 1 -> parts += uiPlural(R.plurals.sort_title_folders, folderNames.size, Format.count(folderNames.size))
            }
            if (parts.isEmpty()) parts += uiText(R.string.sort_title_whole_gallery)
            when (filter.media) {
                MediaFilter.PHOTOS -> parts += uiText(R.string.sort_selection_photos_only)
                MediaFilter.VIDEOS -> parts += uiText(R.string.sort_selection_videos_only)
                MediaFilter.ALL -> Unit
            }
            parts += uiText(if (filter.onlyUnreviewed) R.string.sort_selection_unreviewed_only else R.string.sort_selection_all_items)
            return parts.joinedByDots()
        }
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class SwipeSetupViewModel @Inject constructor(
    private val repository: SwipeRepository,
    private val preferences: SwipePreferences,
) : ViewModel() {

    // The last deck's scope is not carried over as a selection: only its how (media, order,
    // unreviewed-only) is, the what starts empty every time the tab opens.
    private val filter = MutableStateFlow(
        preferences.lastFilter?.copy(buckets = emptySet(), months = emptySet()) ?: SwipeFilter(),
    )
    private val tab = MutableStateFlow(SetupTab.MONTHS)
    private val local = MutableStateFlow(LocalState())

    private val _deleteRequests = Channel<DeleteBatch>(Channel.BUFFERED)
    val deleteRequests: Flow<DeleteBatch> = _deleteRequests.receiveAsFlow()
    private var pendingBatch: DeleteBatch? = null
    private val tally = DeleteTally()

    private val media = filter.map { it.media }.distinctUntilChanged()
    private val monthStats = media.flatMapLatest { repository.monthStats(it) }
    private val bucketStats = media.flatMapLatest { repository.bucketStats(it) }

    private val preview: Flow<SizeAndCount?> = combine(filter, monthStats) { current, _ -> current }
        .debounce(120)
        .mapLatest { repository.count(it) }

    val state: StateFlow<SetupUiState> = combine(
        combine(monthStats, bucketStats, ::Pair),
        filter,
        preview,
        combine(repository.pending, tab, local, ::Triple),
    ) { (months, buckets), current, count, (pending, selectedTab, localState) ->
        SetupUiState(
            loading = false,
            tab = selectedTab,
            years = years(months, current),
            folders = buckets.map { it.toFolder(current) },
            filter = current,
            preview = count,
            pending = pending,
            busy = localState.busy,
            message = localState.message,
            selecting = localState.selecting || current.hasSelection,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupUiState())

    fun setTab(value: SetupTab) {
        tab.value = value
    }

    fun toggleMonth(key: String) = filter.update { current ->
        current.copy(months = if (key in current.months) current.months - key else current.months + key)
    }

    fun toggleYear(group: YearGroup) = filter.update { current ->
        val keys = group.months.map { it.key }.toSet()
        current.copy(months = if (group.allSelected) current.months - keys else current.months + keys)
    }

    fun toggleBucket(bucketId: Long) = filter.update { current ->
        current.copy(buckets = if (bucketId in current.buckets) current.buckets - bucketId else current.buckets + bucketId)
    }

    fun setMedia(media: MediaFilter) = filter.update { it.copy(media = media) }

    fun setOrder(order: SwipeOrder) = filter.update { it.copy(order = order) }

    fun setOnlyUnreviewed(value: Boolean) = filter.update { it.copy(onlyUnreviewed = value) }

    fun clearSelection() {
        filter.update { it.copy(buckets = emptySet(), months = emptySet()) }
        local.update { it.copy(selecting = false) }
    }

    fun startSelecting(month: String? = null, bucketId: Long? = null) {
        local.update { it.copy(selecting = true) }
        if (month != null && month !in filter.value.months) toggleMonth(month)
        if (bucketId != null && bucketId !in filter.value.buckets) toggleBucket(bucketId)
    }

    /**
     * One tap, one deck: the month alone, whatever else was selected. A month that is
     * already fully reviewed opens with its old decisions shown, instead of opening empty.
     */
    fun openMonth(cell: MonthCell, then: () -> Unit) = open(
        filter.value.copy(months = setOf(cell.key), buckets = emptySet(), onlyUnreviewed = filter.value.onlyUnreviewed && !cell.complete),
        heroKey = "swipe-month-${cell.key}",
        then = then,
    )

    fun openFolder(folder: FolderItem, then: () -> Unit) = open(
        filter.value.copy(buckets = setOf(folder.bucketId), months = emptySet(), onlyUnreviewed = filter.value.onlyUnreviewed && folder.reviewedFraction < 1f),
        heroKey = "swipe-folder-${folder.bucketId}",
        then = then,
    )

    fun startSelection(then: () -> Unit) = open(filter.value, heroKey = HERO_START, then = then)

    /**
     * The deck is read before the screen changes, so the tile grows into cards rather than
     * into an empty frame that fills in halfway through. A slow read only waits a moment:
     * past [PREPARE_BUDGET_MS] the session loads it itself, as it would without this.
     */
    private fun open(target: SwipeFilter, heroKey: String, then: () -> Unit) {
        if (opening) return
        opening = true
        preferences.heroKey = heroKey
        preferences.lastFilter = target
        viewModelScope.launch {
            withTimeoutOrNull(PREPARE_BUDGET_MS) { runCatching { repository.prepare(target) } }
            opening = false
            then()
        }
    }

    private var opening = false

    fun deleteNow() {
        if (local.value.busy || pendingBatch != null) return
        tally.reset()
        nextRound()
    }

    private fun nextRound() {
        if (local.value.busy || pendingBatch != null) return
        viewModelScope.launch {
            local.update { it.copy(busy = true) }
            val pending = repository.pendingNow()
            val batch = repository.deleteRequest(pending)
            if (batch == null) {
                local.update {
                    it.copy(busy = false, message = if (pending.isEmpty()) null else uiText(R.string.sort_refused))
                }
                return@launch
            }
            pendingBatch = batch
            _deleteRequests.send(batch)
        }
    }

    fun onDeleteResult(confirmed: Boolean) {
        val batch = pendingBatch
        pendingBatch = null
        if (batch == null || !confirmed) {
            local.update { it.copy(busy = false, message = if (batch != null) uiText(R.string.sort_cancelled) else null) }
            return
        }
        viewModelScope.launch {
            val recorded = repository.onDeleted(batch)
            val text = tally.add(batch)
            local.update {
                it.copy(
                    busy = false,
                    message = when {
                        !recorded -> uiText(R.string.sort_not_recorded)
                        batch.truncated -> text.withNextBatch()
                        else -> text
                    },
                )
            }
            // More was marked than one dialog carries: the next round follows on its own.
            if (recorded && batch.truncated) nextRound()
        }
    }

    fun skipsSystemDialog(): Boolean = repository.canSkipSystemDialog()

    fun consumeMessage() = local.update { it.copy(message = null) }

    private fun years(months: List<SwipeMonthStat>, filter: SwipeFilter): List<YearGroup> =
        months
            .groupBy { SwipeDates.year(it.month) }
            .toSortedMap(compareByDescending { it })
            .map { (year, stats) ->
                YearGroup(
                    year = year,
                    months = stats.sortedBy { it.month }.map { stat ->
                        MonthCell(
                            key = stat.month,
                            itemCount = stat.itemCount,
                            totalBytes = stat.totalBytes,
                            reviewedCount = stat.reviewedCount,
                            selected = stat.month in filter.months,
                            cover = coverUri(stat.coverId, stat.coverMime),
                        )
                    },
                )
            }

    private fun SwipeBucketStat.toFolder(filter: SwipeFilter): FolderItem = FolderItem(
        bucketId = bucketId,
        name = bucketName.ifBlank { relativePath.trimEnd('/').substringAfterLast('/') }
            .let { if (it.isBlank()) uiText(R.string.sort_internal_storage) else UiText.Raw(it) },
        path = relativePath,
        itemCount = itemCount,
        totalBytes = totalBytes,
        reviewedCount = reviewedCount,
        cover = coverUri(coverId, coverMime),
        selected = bucketId in filter.buckets,
    )

    private fun coverUri(id: Long?, mime: String?): Uri? = id?.let {
        val collection = if (mime?.startsWith("video/") == true) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }
        ContentUris.withAppendedId(collection, it)
    }

    private data class LocalState(val busy: Boolean = false, val message: UiText? = null, val selecting: Boolean = false)

    companion object {
        const val HERO_START = "swipe-start"
        private const val PREPARE_BUDGET_MS = 300L
    }
}
