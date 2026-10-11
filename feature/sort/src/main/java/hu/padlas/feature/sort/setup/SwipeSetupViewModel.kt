package hu.padlas.feature.sort.setup

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.model.SizeAndCount
import hu.padlas.core.database.model.SwipeBucketStat
import hu.padlas.core.database.model.SwipeMonthStat
import hu.padlas.feature.sort.DeleteBatch
import hu.padlas.feature.sort.HungarianDates
import hu.padlas.feature.sort.MediaFilter
import hu.padlas.feature.sort.SwipeFilter
import hu.padlas.feature.sort.SwipeOrder
import hu.padlas.feature.sort.SwipePreferences
import hu.padlas.feature.sort.SwipeRepository
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class SetupTab { MONTHS, FOLDERS }

@Immutable
internal data class MonthCell(
    val key: String,
    val name: String,
    val itemCount: Int,
    val totalBytes: Long,
    val reviewedCount: Int,
    val selected: Boolean,
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
    val name: String,
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
    val message: String? = null,
) {
    val isEmpty: Boolean get() = !loading && years.isEmpty() && folders.isEmpty()

    val selectionLabel: String
        get() {
            val parts = ArrayList<String>()
            val months = filter.months.sortedDescending()
            when {
                months.size == 1 -> parts += HungarianDates.month(months.first())
                months.size > 1 -> parts += "${Format.count(months.size)} hónap"
            }
            val folderNames = folders.filter { it.selected }.map { it.name }
            when {
                folderNames.size == 1 -> parts += folderNames.first()
                folderNames.size > 1 -> parts += "${Format.count(folderNames.size)} mappa"
            }
            if (parts.isEmpty()) parts += "Teljes galéria"
            when (filter.media) {
                MediaFilter.PHOTOS -> parts += "csak fotók"
                MediaFilter.VIDEOS -> parts += "csak videók"
                MediaFilter.ALL -> Unit
            }
            parts += if (filter.onlyUnreviewed) "csak át nem nézett" else "minden elem"
            return parts.joinToString(" · ")
        }
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class SwipeSetupViewModel @Inject constructor(
    private val repository: SwipeRepository,
    private val preferences: SwipePreferences,
) : ViewModel() {

    private val filter = MutableStateFlow(preferences.lastFilter ?: SwipeFilter())
    private val tab = MutableStateFlow(SetupTab.MONTHS)
    private val local = MutableStateFlow(LocalState())

    private val _deleteRequests = Channel<DeleteBatch>(Channel.BUFFERED)
    val deleteRequests: Flow<DeleteBatch> = _deleteRequests.receiveAsFlow()
    private var pendingBatch: DeleteBatch? = null

    private val preview: Flow<SizeAndCount?> = combine(filter, repository.monthStats) { current, _ -> current }
        .debounce(120)
        .mapLatest { repository.count(it) }

    val state: StateFlow<SetupUiState> = combine(
        combine(repository.monthStats, repository.bucketStats, ::Pair),
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

    fun clearSelection() = filter.update { it.copy(buckets = emptySet(), months = emptySet()) }

    /** The session screen reads the filter back from preferences, so a resumed app finds it too. */
    fun commitFilter() {
        preferences.lastFilter = filter.value
    }

    fun deleteNow() {
        if (local.value.busy) return
        viewModelScope.launch {
            local.update { it.copy(busy = true) }
            val batch = repository.deleteRequest(repository.pendingNow())
            if (batch == null) {
                local.update { it.copy(busy = false) }
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
            local.update { it.copy(busy = false, message = if (batch != null) "A törlés elmaradt." else null) }
            return
        }
        viewModelScope.launch {
            repository.onDeleted(batch)
            local.update {
                it.copy(
                    busy = false,
                    message = if (batch.permanent) {
                        "${Format.bytes(batch.bytes)} felszabadítva"
                    } else {
                        "${Format.count(batch.ids.size)} elem a kukába került"
                    },
                )
            }
        }
    }

    fun skipsSystemDialog(): Boolean = repository.canSkipSystemDialog()

    fun consumeMessage() = local.update { it.copy(message = null) }

    private fun years(months: List<SwipeMonthStat>, filter: SwipeFilter): List<YearGroup> =
        months
            .groupBy { HungarianDates.year(it.month) }
            .toSortedMap(compareByDescending { it })
            .map { (year, stats) ->
                YearGroup(
                    year = year,
                    months = stats.sortedBy { it.month }.map { stat ->
                        MonthCell(
                            key = stat.month,
                            name = HungarianDates.monthName(stat.month),
                            itemCount = stat.itemCount,
                            totalBytes = stat.totalBytes,
                            reviewedCount = stat.reviewedCount,
                            selected = stat.month in filter.months,
                        )
                    },
                )
            }

    private fun SwipeBucketStat.toFolder(filter: SwipeFilter): FolderItem = FolderItem(
        bucketId = bucketId,
        name = bucketName.ifBlank { relativePath.trimEnd('/').substringAfterLast('/').ifBlank { "Belső tárhely" } },
        path = relativePath,
        itemCount = itemCount,
        totalBytes = totalBytes,
        reviewedCount = reviewedCount,
        cover = coverId?.let { id ->
            val collection = if (coverMime?.startsWith("video/") == true) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            }
            ContentUris.withAppendedId(collection, id)
        },
        selected = bucketId in filter.buckets,
    )

    private data class LocalState(val busy: Boolean = false, val message: String? = null)
}
