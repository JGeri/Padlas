package hu.padlas.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.dao.DupDao
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.model.MediaTypeAggregate
import hu.padlas.core.database.model.SizeAndCount
import hu.padlas.core.media.MediaOps
import hu.padlas.core.media.OneDriveGuard
import hu.padlas.core.media.StorageProbe
import hu.padlas.core.media.VolumeInfo
import hu.padlas.feature.index.IndexState
import hu.padlas.feature.index.IndexStatus
import hu.padlas.feature.sort.SwipeRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class HomeUiState(
    val loading: Boolean = true,
    val device: DeviceState = DeviceState(),
    val media: List<MediaTypeAggregate> = emptyList(),
    val indexedCount: Int = 0,
    val index: IndexState = IndexState.Idle,
    val pending: SizeAndCount = SizeAndCount(0, 0),
    val reviewed: Int = 0,
    val reviewable: Int = 0,
    val duplicates: SizeAndCount = SizeAndCount(0, 0),
    val largeVideos: SizeAndCount = SizeAndCount(0, 0),
    val discardable: SizeAndCount = SizeAndCount(0, 0),
    val oldPhotos: SizeAndCount = SizeAndCount(0, 0),
    val newest: List<Asset> = emptyList(),
) {
    val photoBytes: Long get() = media.firstOrNull { it.mediaType == "image" }?.totalBytes ?: 0L
    val videoBytes: Long get() = media.firstOrNull { it.mediaType == "video" }?.totalBytes ?: 0L
    val photoCount: Int get() = media.firstOrNull { it.mediaType == "image" }?.itemCount ?: 0
    val videoCount: Int get() = media.firstOrNull { it.mediaType == "video" }?.itemCount ?: 0
    val otherBytes: Long get() = (device.volume.usedBytes - photoBytes - videoBytes).coerceAtLeast(0L)
    val trashTotal: Long get() = device.trashBytes + device.samsungTrashBytes
}

@Immutable
data class DeviceState(
    val volume: VolumeInfo = VolumeInfo(0, 0, 0),
    val trashBytes: Long = 0,
    val samsungTrashBytes: Long = 0,
    val oneDriveRisk: Boolean = false,
    val mediaAccess: Boolean = false,
    val partialMediaAccess: Boolean = false,
    val allFilesAccess: Boolean = false,
    val usageAccess: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    assetDao: AssetDao,
    dupDao: DupDao,
    indexStatus: IndexStatus,
    swipeRepository: SwipeRepository,
    private val storageProbe: StorageProbe,
    private val mediaOps: MediaOps,
    private val oneDriveGuard: OneDriveGuard,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val device = MutableStateFlow(DeviceState(mediaAccess = hasMediaAccess()))

    private val review = swipeRepository.monthStats().map { months ->
        months.sumOf { it.reviewedCount } to months.sumOf { it.itemCount }
    }

    val state: StateFlow<HomeUiState> = combine(
        combine(assetDao.observeByMediaType(), assetDao.observeCount(), indexStatus.state, ::Triple),
        combine(swipeRepository.pending, review, assetDao.observeNewestPhotos(3), ::Triple),
        combine(
            dupDao.observeOpenTotals(),
            assetDao.observeTranscodeCandidates(LARGE_VIDEO_BYTES),
            assetDao.observeDiscardable(),
            assetDao.observeArchiveCandidates(System.currentTimeMillis() - YEAR_MS),
        ) { dups, videos, discard, old -> Suggestions(dups, videos, discard, old) },
        device,
    ) { (media, count, index), (pending, reviewPair, newest), suggestions, deviceState ->
        HomeUiState(
            loading = false,
            device = deviceState,
            media = media,
            indexedCount = count,
            index = index,
            pending = pending,
            reviewed = reviewPair.first,
            reviewable = reviewPair.second,
            duplicates = suggestions.duplicates,
            largeVideos = suggestions.videos,
            discardable = suggestions.discardable,
            oldPhotos = suggestions.old,
            newest = newest,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val volume = withContext(io) { storageProbe.volume() }
            val trash = withContext(io) { runCatching { mediaOps.trashedBytes() }.getOrDefault(0L) }
            val samsungTrash = withContext(io) {
                runCatching { oneDriveGuard.samsungGalleryTrashBytes() }.getOrDefault(0L)
            }
            val oneDrive = withContext(io) { runCatching { oneDriveGuard.isSamsungOneDriveSyncLikely() }.getOrDefault(false) }
            device.value = DeviceState(
                volume = volume,
                trashBytes = trash,
                samsungTrashBytes = samsungTrash,
                oneDriveRisk = oneDrive,
                mediaAccess = hasMediaAccess(),
                partialMediaAccess = hasPartialAccess(),
                allFilesAccess = storageProbe.hasAllFilesAccess(),
                usageAccess = storageProbe.hasUsageStatsPermission(),
            )
        }
    }

    private fun hasMediaAccess(): Boolean =
        granted(Manifest.permission.READ_MEDIA_IMAGES) && granted(Manifest.permission.READ_MEDIA_VIDEO)

    /** Android 14 "selected photos only": the index sees a handful of files and says so. */
    private fun hasPartialAccess(): Boolean =
        !hasMediaAccess() && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

    private fun granted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private data class Suggestions(
        val duplicates: SizeAndCount,
        val videos: SizeAndCount,
        val discardable: SizeAndCount,
        val old: SizeAndCount,
    )

    private companion object {
        const val LARGE_VIDEO_BYTES = 200L * 1024 * 1024
        const val YEAR_MS = 365L * 24 * 60 * 60 * 1000
    }
}
