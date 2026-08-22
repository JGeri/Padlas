package hu.padlas.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import hu.padlas.core.database.entity.AssetSearchEntity
import hu.padlas.core.database.entity.AuditEntry
import hu.padlas.core.database.entity.BucketSnapshotEntity
import hu.padlas.core.database.entity.CardEntity
import hu.padlas.core.database.entity.RuleEntity
import hu.padlas.core.database.entity.StorageSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    @Upsert
    suspend fun upsert(rule: RuleEntity): Long

    @Upsert
    suspend fun upsertAll(rules: List<RuleEntity>)

    @Query("SELECT * FROM rule ORDER BY enabled DESC, name ASC")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rule WHERE enabled = 1")
    suspend fun enabled(): List<RuleEntity>

    @Query("SELECT * FROM rule WHERE id = :id")
    suspend fun byId(id: Long): RuleEntity?

    @Query("SELECT COUNT(*) FROM rule")
    suspend fun count(): Int

    @Query("UPDATE rule SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query(
        """
        UPDATE rule SET lastRunAt = :now, lastMatchCount = :matchCount, lastMatchBytes = :matchBytes
        WHERE id = :id
        """,
    )
    suspend fun recordRun(id: Long, now: Long, matchCount: Int, matchBytes: Long)

    @Query("DELETE FROM rule WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AuditDao {

    @Insert
    suspend fun insert(entry: AuditEntry): Long

    @Insert
    suspend fun insertAll(entries: List<AuditEntry>)

    @Query("SELECT * FROM audit_entry ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditEntry>>

    @Query("SELECT * FROM audit_entry ORDER BY timestamp ASC")
    suspend fun all(): List<AuditEntry>

    @Query("SELECT * FROM audit_entry WHERE reversible = 1 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun reversible(limit: Int): List<AuditEntry>

    @Query("UPDATE audit_entry SET reversible = 0 WHERE id = :id")
    suspend fun markConsumed(id: Long)

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM audit_entry WHERE action = 'DELETED' OR action = 'GHOSTED'")
    fun observeReclaimedBytes(): Flow<Long>

    @Query("DELETE FROM audit_entry WHERE timestamp < :before")
    suspend fun prune(before: Long)
}

@Dao
interface CardDao {

    @Upsert
    suspend fun upsert(card: CardEntity)

    @Upsert
    suspend fun upsertAll(cards: List<CardEntity>)

    @Query("SELECT * FROM card ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<CardEntity>>

    @Query("SELECT * FROM card WHERE driveFileId IS NULL LIMIT :limit")
    suspend fun pendingUpload(limit: Int): List<CardEntity>

    @Query("SELECT * FROM card WHERE cardId IN (:ids)")
    suspend fun byIds(ids: List<String>): List<CardEntity>

    @Query("UPDATE card SET driveFileId = :driveFileId WHERE cardId = :cardId")
    suspend fun setDriveFileId(cardId: String, driveFileId: String)

    @Query("SELECT COUNT(*) FROM card")
    fun observeCount(): Flow<Int>
}

@Dao
interface SearchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AssetSearchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<AssetSearchEntity>)

    @Query("SELECT assetId FROM asset_search WHERE asset_search MATCH :query LIMIT :limit")
    suspend fun search(query: String, limit: Int): List<String>

    @Query("DELETE FROM asset_search WHERE assetId = :assetId")
    suspend fun forget(assetId: String)
}

@Dao
interface StorageDao {

    @Insert
    suspend fun insertSnapshot(snapshot: StorageSnapshotEntity): Long

    @Insert
    suspend fun insertBuckets(buckets: List<BucketSnapshotEntity>)

    @Query("SELECT * FROM storage_snapshot ORDER BY takenAt DESC LIMIT 1")
    suspend fun latest(): StorageSnapshotEntity?

    @Query("SELECT * FROM storage_snapshot ORDER BY takenAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<StorageSnapshotEntity>>

    @Query("SELECT * FROM storage_snapshot WHERE takenAt <= :before ORDER BY takenAt DESC LIMIT 1")
    suspend fun snapshotBefore(before: Long): StorageSnapshotEntity?

    @Query("SELECT * FROM bucket_snapshot WHERE snapshotId = :snapshotId")
    suspend fun buckets(snapshotId: Long): List<BucketSnapshotEntity>

    @Query("DELETE FROM storage_snapshot WHERE takenAt < :before")
    suspend fun prune(before: Long)
}
