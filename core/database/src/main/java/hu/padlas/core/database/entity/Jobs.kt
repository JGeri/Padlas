package hu.padlas.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "transcode_job", indices = [Index("state")])
data class TranscodeJobEntity(
    @PrimaryKey val assetId: Long,
    val originalBytes: Long,
    val resultBytes: Long = 0,
    val state: JobState = JobState.PENDING,
    val attempts: Int = 0,
    val failureReason: String? = null,
    val outputPath: String? = null,
    val updatedAt: Long = 0,
)

/**
 * The resumable session URI is persisted so an upload survives process death; without
 * it every reboot restarts the transfer from byte zero.
 */
@Entity(tableName = "upload_job", indices = [Index("state")])
data class UploadJobEntity(
    @PrimaryKey val assetId: Long,
    val driveFileId: String? = null,
    val uploadUri: String? = null,
    val bytesSent: Long = 0,
    val totalBytes: Long = 0,
    val state: JobState = JobState.PENDING,
    val attempts: Int = 0,
    val failureReason: String? = null,
    val updatedAt: Long = 0,
)
