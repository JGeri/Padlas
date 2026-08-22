package hu.padlas.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dup_group",
    indices = [Index("kind"), Index("resolvedAt")],
)
data class DupGroupEntity(
    @PrimaryKey val id: String,
    val kind: DupKind,
    val representativeAssetId: Long,
    val memberCount: Int,
    /** Bytes that disappear if every loser goes. */
    val reclaimableBytes: Long,
    val createdAt: Long,
    val resolvedAt: Long? = null,
)

@Entity(
    tableName = "dup_member",
    primaryKeys = ["groupId", "assetId"],
    indices = [Index("assetId"), Index("groupId")],
    foreignKeys = [
        ForeignKey(
            entity = DupGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DupMemberEntity(
    val groupId: String,
    val assetId: Long,
    val isWinner: Boolean,
    val sharpness: Float?,
    /** Hamming distance from the representative; 0 for exact groups. */
    val distance: Int,
)
