package hu.padlas.vault

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.dao.DupDao
import hu.padlas.core.database.model.BucketAggregate
import hu.padlas.core.database.model.MediaTypeAggregate
import hu.padlas.core.database.model.SizeAndCount
import hu.padlas.core.media.MediaOps
import hu.padlas.core.media.OneDriveGuard
import hu.padlas.core.media.StorageProbe
import hu.padlas.core.media.VolumeInfo
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val assetDao: AssetDao,
    dupDao: DupDao,
    private val storageProbe: StorageProbe,
    private val mediaOps: MediaOps,
    private val oneDriveGuard: OneDriveGuard,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val device = MutableStateFlow(DeviceState())

    val state: StateFlow<VaultUiState> = combine(
        assetDao.observeByMediaType(),
        assetDao.observeCount(),
        dupDao.observeOpenTotals(),
        assetDao.observeTranscodeCandidates(LARGE_VIDEO_BYTES),
        combine(
            assetDao.observeArchiveCandidates(oneYearAgo()),
            assetDao.observeDiscardable(),
            assetDao.observeBuckets(),
            device,
        ) { archivable, discardable, buckets, deviceState ->
            Extras(archivable, discardable, buckets, deviceState)
        },
    ) { byType, indexed, dups, videos, extras ->
        buildState(byType, indexed, dups, videos, extras)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUiState())

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
            val oneDriveRisk = withContext(io) { oneDriveGuard.isSamsungOneDriveSyncLikely() }
            device.value = DeviceState(
                volume = volume,
                trashBytes = trash,
                samsungTrashBytes = samsungTrash,
                oneDriveRisk = oneDriveRisk,
                allFilesAccess = storageProbe.hasAllFilesAccess(),
                usageStats = storageProbe.hasUsageStatsPermission(),
            )
        }
    }

    /** INV-7: one switch stops every background job, immediately and visibly. */
    fun killAllWork() {
        WorkManager.getInstance(context).cancelAllWork()
    }

    private fun buildState(
        byType: List<MediaTypeAggregate>,
        indexedCount: Int,
        dups: SizeAndCount,
        videos: SizeAndCount,
        extras: Extras,
    ): VaultUiState {
        val archivable = extras.archivable
        val discardable = extras.discardable
        val deviceState = extras.device
        val actions = buildList {
            if (dups.itemCount > 0) {
                add(
                    VaultAction(
                        kind = VaultActionKind.DEDUP,
                        count = dups.itemCount.toLong(),
                        bytes = dups.totalBytes,
                    ),
                )
            }
            if (videos.itemCount > 0) {
                add(
                    VaultAction(
                        kind = VaultActionKind.VIDEO,
                        count = videos.itemCount.toLong(),
                        // Transcoding gives back roughly half of a H.264 4K clip.
                        bytes = (videos.totalBytes * VIDEO_SAVING_RATIO).toLong(),
                    ),
                )
            }
            if (archivable.itemCount > 0) {
                add(
                    VaultAction(
                        kind = VaultActionKind.ARCHIVE,
                        count = archivable.itemCount.toLong(),
                        bytes = archivable.totalBytes,
                    ),
                )
            }
            if (discardable.itemCount > 0) {
                add(
                    VaultAction(
                        kind = VaultActionKind.DISCARD,
                        count = discardable.itemCount.toLong(),
                        bytes = discardable.totalBytes,
                    ),
                )
            }
        }.sortedByDescending { it.bytes }

        return VaultUiState(
            junkFolders = extras.buckets
                .filter { bucket -> JUNK_PATHS.any { bucket.relativePath.contains(it, ignoreCase = true) } }
                .sortedByDescending { it.totalBytes }
                .take(4),
            volume = deviceState.volume,
            mediaTypes = byType,
            indexedCount = indexedCount,
            actions = actions,
            trashBytes = deviceState.trashBytes,
            samsungTrashBytes = deviceState.samsungTrashBytes,
            oneDriveRisk = deviceState.oneDriveRisk,
            allFilesAccess = deviceState.allFilesAccess,
            usageStatsAccess = deviceState.usageStats,
        )
    }

    private fun oneYearAgo(): Long = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000

    private data class Extras(
        val archivable: SizeAndCount,
        val discardable: SizeAndCount,
        val buckets: List<BucketAggregate>,
        val device: DeviceState,
    )

    private data class DeviceState(
        val volume: VolumeInfo = VolumeInfo(0, 0, 0),
        val trashBytes: Long = 0,
        val samsungTrashBytes: Long = 0,
        val oneDriveRisk: Boolean = false,
        val allFilesAccess: Boolean = false,
        val usageStats: Boolean = false,
    )

    private companion object {
        const val LARGE_VIDEO_BYTES = 200L * 1024 * 1024

        /** Folders where nine out of ten files are going: inverse mode belongs here. */
        val JUNK_PATHS = listOf("WhatsApp", "Telegram", "Download", "Screenshots")
        const val VIDEO_SAVING_RATIO = 0.45
    }
}

enum class VaultActionKind { DEDUP, VIDEO, ARCHIVE, DISCARD }

data class VaultAction(
    val kind: VaultActionKind,
    val count: Long,
    val bytes: Long,
)

data class VaultUiState(
    val junkFolders: List<BucketAggregate> = emptyList(),
    val volume: VolumeInfo = VolumeInfo(0, 0, 0),
    val mediaTypes: List<MediaTypeAggregate> = emptyList(),
    val indexedCount: Int = 0,
    val actions: List<VaultAction> = emptyList(),
    val trashBytes: Long = 0,
    val samsungTrashBytes: Long = 0,
    val oneDriveRisk: Boolean = false,
    val allFilesAccess: Boolean = false,
    val usageStatsAccess: Boolean = false,
)
