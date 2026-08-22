package hu.padlas.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import hu.padlas.core.database.entity.JobState
import hu.padlas.core.database.entity.TranscodeJobEntity
import hu.padlas.core.database.entity.UploadJobEntity
import hu.padlas.core.database.model.SizeAndCount
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscodeJobDao {

    @Upsert
    suspend fun upsert(job: TranscodeJobEntity)

    @Upsert
    suspend fun upsertAll(jobs: List<TranscodeJobEntity>)

    @Query("SELECT * FROM transcode_job WHERE assetId = :assetId")
    suspend fun byAsset(assetId: Long): TranscodeJobEntity?

    @Query("SELECT * FROM transcode_job WHERE state = :state ORDER BY originalBytes DESC LIMIT :limit")
    suspend fun byState(state: JobState, limit: Int): List<TranscodeJobEntity>

    @Query("SELECT * FROM transcode_job ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<TranscodeJobEntity>>

    @Query("SELECT COUNT(*) FROM transcode_job WHERE state IN ('PENDING', 'RUNNING')")
    fun observePendingCount(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) AS itemCount,
               COALESCE(SUM(originalBytes - resultBytes), 0) AS totalBytes
        FROM transcode_job WHERE state = 'SUCCEEDED'
        """,
    )
    fun observeSaved(): Flow<SizeAndCount>

    @Query(
        """
        UPDATE transcode_job SET state = :state, failureReason = :reason,
            attempts = attempts + 1, updatedAt = :now
        WHERE assetId = :assetId
        """,
    )
    suspend fun setState(assetId: Long, state: JobState, reason: String?, now: Long)

    @Query(
        """
        UPDATE transcode_job SET state = 'SUCCEEDED', resultBytes = :resultBytes,
            outputPath = :outputPath, updatedAt = :now, failureReason = NULL
        WHERE assetId = :assetId
        """,
    )
    suspend fun markSucceeded(assetId: Long, resultBytes: Long, outputPath: String?, now: Long)

    @Query("DELETE FROM transcode_job WHERE assetId IN (:assetIds)")
    suspend fun delete(assetIds: List<Long>)
}

@Dao
interface UploadJobDao {

    @Upsert
    suspend fun upsert(job: UploadJobEntity)

    @Upsert
    suspend fun upsertAll(jobs: List<UploadJobEntity>)

    @Query("SELECT * FROM upload_job WHERE assetId = :assetId")
    suspend fun byAsset(assetId: Long): UploadJobEntity?

    @Query("SELECT * FROM upload_job WHERE state IN ('PENDING', 'RUNNING') ORDER BY updatedAt ASC LIMIT :limit")
    suspend fun nextBatch(limit: Int): List<UploadJobEntity>

    @Query("SELECT * FROM upload_job ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<UploadJobEntity>>

    @Query("SELECT COUNT(*) FROM upload_job WHERE state IN ('PENDING', 'RUNNING')")
    fun observePendingCount(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) AS itemCount, COALESCE(SUM(totalBytes), 0) AS totalBytes
        FROM upload_job WHERE state IN ('PENDING', 'RUNNING')
        """,
    )
    fun observePending(): Flow<SizeAndCount>

    @Query("UPDATE upload_job SET uploadUri = :uploadUri, updatedAt = :now WHERE assetId = :assetId")
    suspend fun setSessionUri(assetId: Long, uploadUri: String, now: Long)

    @Query("UPDATE upload_job SET bytesSent = :bytesSent, updatedAt = :now WHERE assetId = :assetId")
    suspend fun setProgress(assetId: Long, bytesSent: Long, now: Long)

    @Query(
        """
        UPDATE upload_job SET state = :state, failureReason = :reason,
            attempts = attempts + 1, updatedAt = :now
        WHERE assetId = :assetId
        """,
    )
    suspend fun setState(assetId: Long, state: JobState, reason: String?, now: Long)

    @Query(
        """
        UPDATE upload_job SET state = 'SUCCEEDED', driveFileId = :driveFileId,
            bytesSent = totalBytes, updatedAt = :now, failureReason = NULL
        WHERE assetId = :assetId
        """,
    )
    suspend fun markSucceeded(assetId: Long, driveFileId: String, now: Long)

    @Query("DELETE FROM upload_job WHERE assetId IN (:assetIds)")
    suspend fun delete(assetIds: List<Long>)

    @Query("DELETE FROM upload_job WHERE state = 'SUCCEEDED'")
    suspend fun pruneCompleted()
}
