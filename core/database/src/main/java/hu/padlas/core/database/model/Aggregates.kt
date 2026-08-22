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
