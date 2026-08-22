package hu.padlas.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Weekly storage delta tracking. Without this the app is single use by definition. */
@Entity(tableName = "storage_snapshot", indices = [Index("takenAt")])
data class StorageSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val takenAt: Long,
    val totalBytes: Long,
    val freeBytes: Long,
    val imagesBytes: Long,
    val videosBytes: Long,
    val audioBytes: Long,
    val otherBytes: Long,
    val appBytes: Long,
    val cacheBytes: Long,
    val archivedBytes: Long,
)

@Entity(
    tableName = "bucket_snapshot",
    primaryKeys = ["snapshotId", "bucketKey"],
    indices = [Index("snapshotId")],
)
data class BucketSnapshotEntity(
    val snapshotId: Long,
    /** Package name or relative path - whatever identifies the consumer of the bytes. */
    val bucketKey: String,
    val label: String,
    val bytes: Long,
    val itemCount: Int,
)
