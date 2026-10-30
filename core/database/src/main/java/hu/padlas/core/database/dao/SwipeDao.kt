package hu.padlas.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.SwipeDecisionEntity
import hu.padlas.core.database.model.SizeAndCount
import hu.padlas.core.database.model.SwipeBucketStat
import hu.padlas.core.database.model.SwipeCandidate
import hu.padlas.core.database.model.SwipeMonthStat
import kotlinx.coroutines.flow.Flow

@Dao
interface SwipeDao {

    @Upsert
    suspend fun upsert(decision: SwipeDecisionEntity)

    @Upsert
    suspend fun upsertAll(decisions: List<SwipeDecisionEntity>)

    @Query("SELECT * FROM swipe_decision WHERE assetId = :assetId")
    suspend fun byAsset(assetId: Long): SwipeDecisionEntity?

    @Query("DELETE FROM swipe_decision WHERE assetId IN (:assetIds)")
    suspend fun delete(assetIds: List<Long>)

    /** Forgets keep and favourite decisions so everything can be reviewed again. */
    @Query("DELETE FROM swipe_decision WHERE choice != 'DELETE'")
    suspend fun forgetReviewed()

    /** Pending deletions only count while the file is still on the device. */
    @Query(
        """
        SELECT COUNT(*) AS itemCount, COALESCE(SUM(a.sizeBytes), 0) AS totalBytes
        FROM swipe_decision d JOIN asset a ON a.mediaStoreId = d.assetId
        WHERE d.choice = 'DELETE' AND a.state IN ('LOCAL', 'PROTECTED')
        """,
    )
    fun observePending(): Flow<SizeAndCount>

    @Query(
        """
        SELECT a.* FROM swipe_decision d JOIN asset a ON a.mediaStoreId = d.assetId
        WHERE d.choice = 'DELETE' AND a.state IN ('LOCAL', 'PROTECTED')
        ORDER BY d.decidedAt DESC
        """,
    )
    fun observePendingAssets(): Flow<List<Asset>>

    @Query(
        """
        SELECT a.* FROM swipe_decision d JOIN asset a ON a.mediaStoreId = d.assetId
        WHERE d.choice = 'DELETE' AND a.state IN ('LOCAL', 'PROTECTED')
        ORDER BY d.decidedAt DESC
        """,
    )
    suspend fun pendingAssets(): List<Asset>

    /**
     * With a single MAX() in the query, SQLite fills the bare cover columns from the row
     * that holds the maximum: the newest item of each month, without a per-month subquery.
     * A date past the year 9999 has no strftime month at all; that group is dropped rather
     * than read as a NULL month.
     */
    @Query(
        """
        SELECT strftime('%Y-%m', a.dateTaken / 1000, 'unixepoch', 'localtime') AS month,
               COUNT(*) AS itemCount,
               COALESCE(SUM(a.sizeBytes), 0) AS totalBytes,
               SUM(CASE WHEN d.assetId IS NULL THEN 0 ELSE 1 END) AS reviewedCount,
               SUM(CASE WHEN a.mimeType LIKE 'video/%' THEN 1 ELSE 0 END) AS videoCount,
               MAX(a.dateTaken) AS latestTaken,
               a.mediaStoreId AS coverId,
               a.mimeType AS coverMime
        FROM asset a LEFT JOIN swipe_decision d ON d.assetId = a.mediaStoreId
        WHERE a.state IN ('LOCAL', 'PROTECTED') AND a.dateTaken > 0 AND a.mimeType LIKE :mimePattern
        GROUP BY month HAVING month IS NOT NULL ORDER BY month DESC
        """,
    )
    fun observeMonthStats(mimePattern: String): Flow<List<SwipeMonthStat>>

    @Query(
        """
        SELECT a.bucketId AS bucketId, a.bucketName AS bucketName,
               MIN(a.relativePath) AS relativePath,
               COUNT(*) AS itemCount,
               COALESCE(SUM(a.sizeBytes), 0) AS totalBytes,
               SUM(CASE WHEN d.assetId IS NULL THEN 0 ELSE 1 END) AS reviewedCount,
               MAX(a.dateTaken) AS latestTaken,
               (SELECT c.mediaStoreId FROM asset c
                 WHERE c.bucketId = a.bucketId AND c.state IN ('LOCAL', 'PROTECTED')
                   AND c.mimeType LIKE :mimePattern
                 ORDER BY c.dateTaken DESC LIMIT 1) AS coverId,
               (SELECT c.mimeType FROM asset c
                 WHERE c.bucketId = a.bucketId AND c.state IN ('LOCAL', 'PROTECTED')
                   AND c.mimeType LIKE :mimePattern
                 ORDER BY c.dateTaken DESC LIMIT 1) AS coverMime
        FROM asset a LEFT JOIN swipe_decision d ON d.assetId = a.mediaStoreId
        WHERE a.state IN ('LOCAL', 'PROTECTED') AND a.mimeType LIKE :mimePattern
        GROUP BY a.bucketId ORDER BY totalBytes DESC
        """,
    )
    fun observeBucketStats(mimePattern: String): Flow<List<SwipeBucketStat>>

    /** The deck filter is built in Kotlin: folders, months and media type are all optional. */
    @RawQuery(observedEntities = [Asset::class, SwipeDecisionEntity::class])
    suspend fun candidates(query: SupportSQLiteQuery): List<SwipeCandidate>

    @RawQuery(observedEntities = [Asset::class, SwipeDecisionEntity::class])
    suspend fun count(query: SupportSQLiteQuery): SizeAndCount
}
