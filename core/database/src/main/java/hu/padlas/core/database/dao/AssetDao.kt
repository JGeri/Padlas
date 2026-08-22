package hu.padlas.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.entity.ShotKind
import hu.padlas.core.database.entity.ValueClass
import hu.padlas.core.database.model.BucketAggregate
import hu.padlas.core.database.model.ClassAggregate
import hu.padlas.core.database.model.HashRow
import hu.padlas.core.database.model.MediaTypeAggregate
import hu.padlas.core.database.model.PathAggregate
import hu.padlas.core.database.model.QuickHashCandidate
import hu.padlas.core.database.model.ShotKindAggregate
import hu.padlas.core.database.model.SizeAndCount
import hu.padlas.core.database.model.StateAggregate
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {

    // ---- writes -------------------------------------------------------------

    @Upsert
    suspend fun upsertAll(assets: List<Asset>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(assets: List<Asset>): List<Long>

    @Query("DELETE FROM asset WHERE mediaStoreId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM asset")
    suspend fun clear()

    // ---- single reads -------------------------------------------------------

    @Query("SELECT * FROM asset WHERE mediaStoreId = :id")
    suspend fun byId(id: Long): Asset?

    @Query("SELECT * FROM asset WHERE mediaStoreId IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<Asset>

    @Query("SELECT * FROM asset WHERE mediaStoreId = :id")
    fun observeById(id: Long): Flow<Asset?>

    // ---- totals -------------------------------------------------------------

    @Query("SELECT COUNT(*) FROM asset")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM asset")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM asset WHERE state != 'GHOST'")
    fun observeLocalBytes(): Flow<Long>

    @Query(
        """
        SELECT COUNT(*) AS itemCount, COALESCE(SUM(sizeBytes), 0) AS totalBytes
        FROM asset WHERE state = :state
        """,
    )
    fun observeState(state: AssetState): Flow<SizeAndCount>

    @Query(
        """
        SELECT COUNT(*) AS itemCount, COALESCE(SUM(sizeBytes), 0) AS totalBytes
        FROM asset WHERE valueClass = :valueClass AND state IN ('LOCAL', 'PROTECTED')
        """,
    )
    fun observeClass(valueClass: ValueClass): Flow<SizeAndCount>

    @Query(
        """
        SELECT state, COUNT(*) AS itemCount, COALESCE(SUM(sizeBytes), 0) AS totalBytes
        FROM asset GROUP BY state
        """,
    )
    fun observeByState(): Flow<List<StateAggregate>>

    @Query(
        """
        SELECT valueClass, COUNT(*) AS itemCount, COALESCE(SUM(sizeBytes), 0) AS totalBytes
        FROM asset WHERE state != 'GHOST' GROUP BY valueClass
        """,
    )
    fun observeByClass(): Flow<List<ClassAggregate>>

    @Query(
        """
        SELECT shotKind, COUNT(*) AS itemCount, COALESCE(SUM(sizeBytes), 0) AS totalBytes
        FROM asset
        WHERE valueClass = 'EPHEMERAL' AND state IN ('LOCAL', 'PROTECTED')
        GROUP BY shotKind ORDER BY totalBytes DESC
        """,
    )
    fun observeByShotKind(): Flow<List<ShotKindAggregate>>

    @Query(
        """
        SELECT
            CASE
                WHEN mimeType LIKE 'video/%' THEN 'video'
                WHEN mimeType LIKE 'image/%' THEN 'image'
                WHEN mimeType LIKE 'audio/%' THEN 'audio'
                ELSE 'other'
            END AS mediaType,
            COUNT(*) AS itemCount,
            COALESCE(SUM(sizeBytes), 0) AS totalBytes
        FROM asset WHERE state != 'GHOST'
        GROUP BY mediaType ORDER BY totalBytes DESC
        """,
    )
    fun observeByMediaType(): Flow<List<MediaTypeAggregate>>

    @Query(
        """
        SELECT bucketId, bucketName, MIN(relativePath) AS relativePath,
               COUNT(*) AS itemCount, COALESCE(SUM(sizeBytes), 0) AS totalBytes
        FROM asset WHERE state != 'GHOST'
        GROUP BY bucketId, bucketName ORDER BY totalBytes DESC
        """,
    )
    fun observeBuckets(): Flow<List<BucketAggregate>>

    @Query(
        """
        SELECT relativePath, COUNT(*) AS itemCount, COALESCE(SUM(sizeBytes), 0) AS totalBytes
        FROM asset
        WHERE state != 'GHOST' AND relativePath LIKE :prefix || '%'
        GROUP BY relativePath ORDER BY totalBytes DESC
        """,
    )
    suspend fun pathsUnder(prefix: String): List<PathAggregate>

    // ---- listings -----------------------------------------------------------

    @Query(
        """
        SELECT * FROM asset
        WHERE valueClass = :valueClass AND state IN ('LOCAL', 'PROTECTED')
        ORDER BY dateTaken DESC
        """,
    )
    fun pageByClass(valueClass: ValueClass): PagingSource<Int, Asset>

    @Query(
        """
        SELECT * FROM asset
        WHERE bucketId = :bucketId AND state IN ('LOCAL', 'PROTECTED')
        ORDER BY dateTaken DESC
        """,
    )
    fun pageByBucket(bucketId: Long): PagingSource<Int, Asset>

    @Query(
        """
        SELECT * FROM asset
        WHERE valueClass = 'EPHEMERAL' AND shotKind IS :shotKind
          AND state = 'LOCAL' AND protectedManually = 0
        ORDER BY dateTaken DESC
        """,
    )
    suspend fun ephemeralOfKind(shotKind: ShotKind?): List<Asset>

    @Query(
        """
        SELECT * FROM asset
        WHERE bucketId = :bucketId AND state = 'LOCAL' AND protectedManually = 0
        ORDER BY dateTaken DESC LIMIT :limit
        """,
    )
    suspend fun bucketAssets(bucketId: Long, limit: Int): List<Asset>

    @Query(
        """
        SELECT * FROM asset
        WHERE state = 'TRASHED' ORDER BY trashedAt DESC
        """,
    )
    fun observeTrashed(): Flow<List<Asset>>

    @Query(
        """
        SELECT * FROM asset
        WHERE state IN ('ARCHIVED', 'GHOST') ORDER BY archivedAt DESC
        """,
    )
    fun pageArchived(): PagingSource<Int, Asset>

    @Query(
        """
        SELECT * FROM asset
        WHERE state IN ('ARCHIVED', 'GHOST', 'UPLOADING')
        ORDER BY COALESCE(archivedAt, dateTaken) DESC LIMIT :limit
        """,
    )
    fun observeArchived(limit: Int): Flow<List<Asset>>

    /**
     * The sorting deck. Everything the earlier layers could not decide, newest first,
     * with the protected set (INV-4) already excluded.
     */
    @Query(
        """
        SELECT * FROM asset
        WHERE state = 'LOCAL'
          AND protectedManually = 0
          AND isFavorite = 0
          AND deferredAt = 0
          AND (shotKind IS NULL OR shotKind != 'SECRET')
          AND mediaStoreId NOT IN (SELECT assetId FROM dup_member WHERE isWinner = 0)
          AND dateTaken < :notNewerThan
        ORDER BY dateTaken DESC
        LIMIT :limit
        """,
    )
    suspend fun sortingDeck(notNewerThan: Long, limit: Int): List<Asset>

    @Query("SELECT * FROM asset WHERE deferredAt > 0 ORDER BY deferredAt DESC")
    suspend fun deferred(): List<Asset>

    // ---- pipeline work queues ----------------------------------------------

    @Query(
        """
        SELECT mediaStoreId, uri, sizeBytes, dateTaken, dHash, blurScore, quickHash, sha256
        FROM asset
        WHERE quickHash IS NULL AND state IN ('LOCAL', 'PROTECTED')
        ORDER BY sizeBytes DESC LIMIT :limit
        """,
    )
    suspend fun needingQuickHash(limit: Int): List<HashRow>

    @Query(
        """
        SELECT quickHash, COUNT(*) AS memberCount FROM asset
        WHERE quickHash IS NOT NULL AND state IN ('LOCAL', 'PROTECTED')
        GROUP BY quickHash HAVING COUNT(*) > 1
        """,
    )
    suspend fun quickHashCollisions(): List<QuickHashCandidate>

    @Query(
        """
        SELECT mediaStoreId, uri, sizeBytes, dateTaken, dHash, blurScore, quickHash, sha256
        FROM asset WHERE quickHash = :quickHash AND state IN ('LOCAL', 'PROTECTED')
        """,
    )
    suspend fun rowsWithQuickHash(quickHash: String): List<HashRow>

    @Query(
        """
        SELECT mediaStoreId, uri, sizeBytes, dateTaken, dHash, blurScore, quickHash, sha256
        FROM asset
        WHERE dHash IS NULL AND mimeType LIKE 'image/%' AND state IN ('LOCAL', 'PROTECTED')
        ORDER BY dateTaken DESC LIMIT :limit
        """,
    )
    suspend fun needingPerceptualHash(limit: Int): List<HashRow>

    @Query(
        """
        SELECT mediaStoreId, uri, sizeBytes, dateTaken, dHash, blurScore, quickHash, sha256
        FROM asset
        WHERE dHash IS NOT NULL AND state IN ('LOCAL', 'PROTECTED')
        ORDER BY dHash
        """,
    )
    suspend fun perceptualRows(): List<HashRow>

    @Query(
        """
        SELECT * FROM asset
        WHERE triagedAt = 0 AND mimeType LIKE 'image/%'
          AND valueClass = 'EPHEMERAL' AND state IN ('LOCAL', 'PROTECTED')
        ORDER BY dateTaken DESC LIMIT :limit
        """,
    )
    suspend fun needingTriage(limit: Int): List<Asset>

    @Query(
        """
        SELECT * FROM asset
        WHERE mimeType LIKE 'video/%' AND state = 'LOCAL'
          AND sizeBytes > :minBytes
          AND mediaStoreId NOT IN (SELECT assetId FROM transcode_job WHERE state IN ('SUCCEEDED', 'RUNNING'))
        ORDER BY sizeBytes DESC LIMIT :limit
        """,
    )
    suspend fun transcodeCandidates(minBytes: Long, limit: Int): List<Asset>

    @Query(
        """
        SELECT * FROM asset
        WHERE state = 'LOCAL' AND valueClass = 'IRREPLACEABLE'
          AND dateTaken < :olderThan
        ORDER BY dateTaken ASC LIMIT :limit
        """,
    )
    suspend fun archiveCandidates(olderThan: Long, limit: Int): List<Asset>

    @Query(
        """
        SELECT COUNT(*) AS itemCount, COALESCE(SUM(sizeBytes), 0) AS totalBytes FROM asset
        WHERE state = 'LOCAL' AND valueClass = 'IRREPLACEABLE' AND dateTaken < :olderThan
        """,
    )
    fun observeArchiveCandidates(olderThan: Long): Flow<SizeAndCount>

    @Query(
        """
        SELECT COUNT(*) AS itemCount, COALESCE(SUM(sizeBytes), 0) AS totalBytes FROM asset
        WHERE mimeType LIKE 'video/%' AND state = 'LOCAL' AND sizeBytes > :minBytes
        """,
    )
    fun observeTranscodeCandidates(minBytes: Long): Flow<SizeAndCount>

    @Query(
        """
        SELECT COUNT(*) AS itemCount, COALESCE(SUM(sizeBytes), 0) AS totalBytes FROM asset
        WHERE valueClass = 'EPHEMERAL' AND state = 'LOCAL' AND protectedManually = 0
          AND (shotKind IS NULL OR shotKind != 'SECRET')
        """,
    )
    fun observeDiscardable(): Flow<SizeAndCount>

    // ---- field updates ------------------------------------------------------

    @Query("UPDATE asset SET quickHash = :quickHash, hashedAt = :now WHERE mediaStoreId = :id")
    suspend fun setQuickHash(id: Long, quickHash: String, now: Long)

    @Query("UPDATE asset SET sha256 = :sha256 WHERE mediaStoreId = :id")
    suspend fun setSha256(id: Long, sha256: String)

    @Query("UPDATE asset SET dHash = :dHash, blurScore = :blurScore WHERE mediaStoreId = :id")
    suspend fun setPerceptual(id: Long, dHash: Long?, blurScore: Float?)

    @Query(
        """
        UPDATE asset SET ocrText = :text, shotKind = :kind, triagedAt = :now,
            protectedManually = CASE WHEN :kind = 'SECRET' THEN 1 ELSE protectedManually END,
            state = CASE WHEN :kind = 'SECRET' AND state = 'LOCAL' THEN 'PROTECTED' ELSE state END
        WHERE mediaStoreId = :id
        """,
    )
    suspend fun setTriage(id: Long, text: String?, kind: ShotKind, now: Long)

    @Query("UPDATE asset SET triagedAt = :now WHERE mediaStoreId = :id")
    suspend fun markTriaged(id: Long, now: Long)

    @Query(
        """
        UPDATE asset SET valueClass = :valueClass, classifiedBy = :source
        WHERE mediaStoreId = :id AND classifiedBy != 'manual'
        """,
    )
    suspend fun setValueClass(id: Long, valueClass: ValueClass, source: String)

    @Query("UPDATE asset SET valueClass = :valueClass, classifiedBy = 'manual' WHERE mediaStoreId IN (:ids)")
    suspend fun setValueClassManually(ids: List<Long>, valueClass: ValueClass)

    @Query(
        """
        UPDATE asset SET protectedManually = :protectedManually,
            state = CASE
                WHEN :protectedManually = 1 AND state = 'LOCAL' THEN 'PROTECTED'
                WHEN :protectedManually = 0 AND state = 'PROTECTED' THEN 'LOCAL'
                ELSE state
            END
        WHERE mediaStoreId IN (:ids)
        """,
    )
    suspend fun setProtected(ids: List<Long>, protectedManually: Boolean)

    @Query("UPDATE asset SET state = :state WHERE mediaStoreId IN (:ids)")
    suspend fun setState(ids: List<Long>, state: AssetState)

    @Query("UPDATE asset SET state = 'TRASHED', trashedAt = :now WHERE mediaStoreId IN (:ids)")
    suspend fun markTrashed(ids: List<Long>, now: Long)

    @Query("UPDATE asset SET state = 'LOCAL', trashedAt = NULL WHERE mediaStoreId IN (:ids)")
    suspend fun markUntrashed(ids: List<Long>)

    @Query(
        """
        UPDATE asset SET state = 'ARCHIVED', driveFileId = :driveFileId, driveMd5 = :driveMd5,
            archivedAt = :now
        WHERE mediaStoreId = :id
        """,
    )
    suspend fun markArchived(id: Long, driveFileId: String, driveMd5: String, now: Long)

    @Query("UPDATE asset SET state = 'GHOST', ghostThumbPath = :thumbPath WHERE mediaStoreId IN (:ids)")
    suspend fun markGhost(ids: List<Long>, thumbPath: String?)

    @Query("UPDATE asset SET cardId = :cardId WHERE mediaStoreId = :id")
    suspend fun setCardId(id: Long, cardId: String)

    @Query("UPDATE asset SET deferredAt = :now WHERE mediaStoreId IN (:ids)")
    suspend fun defer(ids: List<Long>, now: Long)

    @Query("UPDATE asset SET deferredAt = 0 WHERE deferredAt > 0")
    suspend fun clearDeferred()

    @Query(
        """
        UPDATE asset SET sizeBytes = :sizeBytes, uri = :uri, mimeType = :mimeType,
            quickHash = NULL, sha256 = NULL
        WHERE mediaStoreId = :id
        """,
    )
    suspend fun replaceAfterTranscode(id: Long, uri: String, sizeBytes: Long, mimeType: String)

    @Query("SELECT MAX(dateModified) FROM asset")
    suspend fun latestDateModified(): Long?

    @Query("SELECT mediaStoreId FROM asset")
    suspend fun allIds(): List<Long>

    // ---- rule engine --------------------------------------------------------

    /** The rule engine builds its own WHERE clause; dry-run always runs through here. */
    @RawQuery(observedEntities = [Asset::class])
    suspend fun rawAssets(query: SupportSQLiteQuery): List<Asset>

    @RawQuery(observedEntities = [Asset::class])
    suspend fun rawSizeAndCount(query: SupportSQLiteQuery): SizeAndCount

    @Transaction
    suspend fun replaceWholeIndex(assets: List<Asset>) {
        clear()
        upsertAll(assets)
    }
}
