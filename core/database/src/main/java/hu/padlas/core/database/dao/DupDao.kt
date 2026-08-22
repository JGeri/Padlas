package hu.padlas.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import hu.padlas.core.database.entity.DupGroupEntity
import hu.padlas.core.database.entity.DupKind
import hu.padlas.core.database.entity.DupMemberEntity
import hu.padlas.core.database.model.DupGroupWithMembers
import hu.padlas.core.database.model.SizeAndCount
import kotlinx.coroutines.flow.Flow

@Dao
interface DupDao {

    @Upsert
    suspend fun upsertGroups(groups: List<DupGroupEntity>)

    @Upsert
    suspend fun upsertMembers(members: List<DupMemberEntity>)

    @Transaction
    suspend fun replaceAll(groups: List<DupGroupEntity>, members: List<DupMemberEntity>) {
        clearUnresolved()
        upsertGroups(groups)
        upsertMembers(members)
    }

    @Query("DELETE FROM dup_group WHERE resolvedAt IS NULL")
    suspend fun clearUnresolved()

    @Query("DELETE FROM dup_group")
    suspend fun clear()

    @Transaction
    @Query("SELECT * FROM dup_group WHERE resolvedAt IS NULL ORDER BY reclaimableBytes DESC")
    fun observeOpenGroups(): Flow<List<DupGroupWithMembers>>

    @Transaction
    @Query("SELECT * FROM dup_group WHERE kind = :kind AND resolvedAt IS NULL ORDER BY reclaimableBytes DESC")
    suspend fun openGroupsOfKind(kind: DupKind): List<DupGroupWithMembers>

    @Transaction
    @Query("SELECT * FROM dup_group WHERE id = :id")
    suspend fun group(id: String): DupGroupWithMembers?

    @Query(
        """
        SELECT COUNT(*) AS itemCount, COALESCE(SUM(reclaimableBytes), 0) AS totalBytes
        FROM dup_group WHERE resolvedAt IS NULL
        """,
    )
    fun observeOpenTotals(): Flow<SizeAndCount>

    @Query("SELECT COALESCE(SUM(memberCount), 0) FROM dup_group WHERE resolvedAt IS NULL")
    fun observeOpenMemberCount(): Flow<Int>

    @Query("UPDATE dup_group SET resolvedAt = :now WHERE id = :id")
    suspend fun markResolved(id: String, now: Long)

    @Query("UPDATE dup_group SET resolvedAt = :now WHERE id IN (:ids)")
    suspend fun markResolvedAll(ids: List<String>, now: Long)

    @Query("UPDATE dup_member SET isWinner = (assetId = :winnerId) WHERE groupId = :groupId")
    suspend fun setWinner(groupId: String, winnerId: Long)

    @Query("SELECT assetId FROM dup_member WHERE groupId = :groupId AND isWinner = 0")
    suspend fun losersOf(groupId: String): List<Long>

    @Query(
        """
        SELECT assetId FROM dup_member
        WHERE isWinner = 0 AND groupId IN (SELECT id FROM dup_group WHERE resolvedAt IS NULL)
        """,
    )
    suspend fun allOpenLosers(): List<Long>

    @Query("DELETE FROM dup_member WHERE assetId IN (:assetIds)")
    suspend fun forgetAssets(assetIds: List<Long>)
}
