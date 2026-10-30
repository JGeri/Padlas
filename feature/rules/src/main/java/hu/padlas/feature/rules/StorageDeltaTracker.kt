package hu.padlas.feature.rules

import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.dao.StorageDao
import hu.padlas.core.database.entity.BucketSnapshotEntity
import hu.padlas.core.database.entity.StorageSnapshotEntity
import hu.padlas.core.media.StorageProbe
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class BucketDelta(
    val label: String,
    val bytesNow: Long,
    val bytesBefore: Long,
) {
    val delta: Long get() = bytesNow - bytesBefore
}

/**
 * A cleaning app is single use by definition unless it can say what grew. This is the only
 * reason the app gets opened in the second month, so the snapshot runs whether or not any
 * rule is enabled.
 */
@Singleton
class StorageDeltaTracker @Inject constructor(
    private val assetDao: AssetDao,
    private val storageDao: StorageDao,
    private val probe: StorageProbe,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun snapshot(now: Long = System.currentTimeMillis()): Long = withContext(io) {
        val volume = probe.volume()
        val byType = assetDao.observeByMediaType().first().associateBy { it.mediaType }
        val apps = runCatching { probe.appUsage() }.getOrDefault(emptyList())

        val id = storageDao.insertSnapshot(
            StorageSnapshotEntity(
                takenAt = now,
                totalBytes = volume.totalBytes,
                freeBytes = volume.freeBytes,
                imagesBytes = byType["image"]?.totalBytes ?: 0,
                videosBytes = byType["video"]?.totalBytes ?: 0,
                audioBytes = byType["audio"]?.totalBytes ?: 0,
                otherBytes = byType["other"]?.totalBytes ?: 0,
                appBytes = apps.sumOf { it.appBytes + it.dataBytes },
                cacheBytes = apps.sumOf { it.cacheBytes },
                archivedBytes = 0,
            ),
        )

        val buckets = assetDao.observeBuckets().first().map { bucket ->
            BucketSnapshotEntity(
                snapshotId = id,
                bucketKey = bucket.relativePath.ifBlank { bucket.bucketName },
                label = bucket.bucketName.ifBlank { bucket.relativePath },
                bytes = bucket.totalBytes,
                itemCount = bucket.itemCount,
            )
        } + apps.map { app ->
            BucketSnapshotEntity(
                snapshotId = id,
                bucketKey = app.packageName,
                label = app.label,
                bytes = app.dataBytes + app.cacheBytes,
                itemCount = 0,
            )
        }
        storageDao.insertBuckets(buckets.mergedByKey())
        storageDao.prune(now - RETENTION_MS)
        id
    }

    /** "a Telegram +4,2 GB-t nőtt" - the deltas that are worth a line on the rules screen. */
    suspend fun weeklyDeltas(now: Long = System.currentTimeMillis()): List<BucketDelta> = withContext(io) {
        val latest = storageDao.latest() ?: return@withContext emptyList()
        val previous = storageDao.snapshotBefore(now - WEEK_MS) ?: return@withContext emptyList()
        if (previous.id == latest.id) return@withContext emptyList()

        val before = storageDao.buckets(previous.id).associateBy { it.bucketKey }
        storageDao.buckets(latest.id)
            .map { current ->
                BucketDelta(
                    label = current.label,
                    bytesNow = current.bytes,
                    bytesBefore = before[current.bucketKey]?.bytes ?: 0,
                )
            }
            .filter { kotlin.math.abs(it.delta) > NOISE_FLOOR }
            .sortedByDescending { kotlin.math.abs(it.delta) }
            .take(LIMIT)
    }

    private companion object {
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
        const val RETENTION_MS = 180L * 24 * 60 * 60 * 1000
        const val NOISE_FLOOR = 100L * 1024 * 1024
        const val LIMIT = 8
    }
}

/**
 * One row per key, because (snapshot, key) is the table's primary key. MediaStore buckets
 * are per directory and volume, so an SD card with its own DCIM/Camera/ reports the same
 * relative path twice, and inserting both aborted the whole snapshot.
 */
internal fun List<BucketSnapshotEntity>.mergedByKey(): List<BucketSnapshotEntity> =
    groupBy { it.bucketKey }.values.map { same ->
        same.singleOrNull() ?: same.first().copy(
            bytes = same.sumOf { it.bytes },
            itemCount = same.sumOf { it.itemCount },
        )
    }
