package hu.padlas.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The catalogue card (spec 1.6). When a file is thrown out its card goes up to Drive
 * instead: about 20 KB per asset rather than 2-4 MB. The object is gone, the record is not.
 */
@Entity(tableName = "card", indices = [Index("createdAt"), Index("assetId")])
data class CardEntity(
    @PrimaryKey val cardId: String,
    val assetId: Long,
    val thumbPath: String?,
    val ocrText: String?,
    val dateTaken: Long,
    val originalPath: String,
    val displayName: String,
    val sizeBytes: Long,
    val driveFileId: String? = null,
    val createdAt: Long,
)
