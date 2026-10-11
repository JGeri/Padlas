package hu.padlas.core.database.model

import androidx.room.Embedded
import androidx.room.Relation
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.entity.DupGroupEntity
import hu.padlas.core.database.entity.DupMemberEntity
import hu.padlas.core.database.entity.ShotKind
import hu.padlas.core.database.entity.ValueClass

data class BucketAggregate(
    val bucketId: Long,
    val bucketName: String,
    val relativePath: String,
    val itemCount: Int,
    val totalBytes: Long,
)

data class PathAggregate(
    val relativePath: String,
    val itemCount: Int,
    val totalBytes: Long,
)

/** "2024-07" plus what that month still occupies. Buckets and months are the two axes
 *  the user actually thinks in when deciding what may leave the phone. */
data class MonthAggregate(
    val month: String,
    val itemCount: Int,
    val totalBytes: Long,
)

data class ClassAggregate(
    val valueClass: ValueClass,
    val itemCount: Int,
    val totalBytes: Long,
)

data class ShotKindAggregate(
    val shotKind: ShotKind?,
    val itemCount: Int,
    val totalBytes: Long,
)

data class StateAggregate(
    val state: AssetState,
    val itemCount: Int,
    val totalBytes: Long,
)

data class MediaTypeAggregate(
    val mediaType: String,
    val itemCount: Int,
    val totalBytes: Long,
)

/** Light projection for the hash pipeline: never load full rows for 25k assets. */
data class HashRow(
    val mediaStoreId: Long,
    val uri: String,
    val sizeBytes: Long,
    val dateTaken: Long,
    val dHash: Long?,
    val blurScore: Float?,
    val quickHash: String?,
    val sha256: String?,
)

data class QuickHashCandidate(
    val quickHash: String,
    val memberCount: Int,
)

data class DupGroupWithMembers(
    @Embedded val group: DupGroupEntity,
    @Relation(parentColumn = "id", entityColumn = "groupId")
    val members: List<DupMemberEntity>,
)

data class DupMemberAsset(
    @Embedded val member: DupMemberEntity,
    @Relation(parentColumn = "assetId", entityColumn = "mediaStoreId")
    val asset: Asset?,
)

data class SizeAndCount(
    val itemCount: Int,
    val totalBytes: Long,
)

data class SwipeMonthStat(
    /** "2024-07", local time. */
    val month: String,
    val itemCount: Int,
    val totalBytes: Long,
    val reviewedCount: Int,
    val videoCount: Int,
    val latestTaken: Long,
    val coverId: Long?,
    val coverMime: String?,
)

data class SwipeBucketStat(
    val bucketId: Long,
    val bucketName: String,
    val relativePath: String,
    val itemCount: Int,
    val totalBytes: Long,
    val reviewedCount: Int,
    val latestTaken: Long,
    val coverId: Long?,
    val coverMime: String?,
)

/** What the swipe deck needs from a row, and nothing else. */
data class SwipeCandidate(
    val mediaStoreId: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val relativePath: String,
    val bucketName: String,
    val sizeBytes: Long,
    val dateTaken: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long?,
    val isFavorite: Boolean,
    val protectedManually: Boolean,
    val choice: String?,
)
