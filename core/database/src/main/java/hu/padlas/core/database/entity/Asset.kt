package hu.padlas.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "asset",
    indices = [
        Index("quickHash"),
        Index("sha256"),
        Index("dHash"),
        Index("bucketId"),
        Index("dateTaken"),
        Index("state"),
        Index("valueClass"),
        Index("shotKind"),
        Index("sizeBytes"),
        Index("relativePath"),
    ],
)
data class Asset(
    @PrimaryKey val mediaStoreId: Long,
    val uri: String,
    /** "DCIM/Camera/" - always with the trailing slash, exactly as MediaStore reports it. */
    val relativePath: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** epoch millis, EXIF wins over MediaStore when both are present */
    val dateTaken: Long,
    val dateModified: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long?,
    val bucketId: Long,
    val bucketName: String,
    val latitude: Double?,
    val longitude: Double?,
    val isFavorite: Boolean = false,

    /** size + first 64KB + last 64KB, SHA-256. Cheap: the disk never reads the middle. */
    val quickHash: String? = null,
    /** Only computed for quickHash collision candidates, under 2% of the library. */
    val sha256: String? = null,
    /** 64 bit perceptual hash, 9x8 grayscale horizontal gradient. */
    val dHash: Long? = null,
    /** Laplace variance on a 256x256 grayscale bitmap. Compared inside a group only. */
    val blurScore: Float? = null,
    val ocrText: String? = null,

    val valueClass: ValueClass = ValueClass.REPLACEABLE,
    val classifiedBy: String = ClassifiedBy.PATH,
    val shotKind: ShotKind? = null,

    val state: AssetState = AssetState.LOCAL,
    val driveFileId: String? = null,
    val driveMd5: String? = null,
    val archivedAt: Long? = null,
    /** Catalogue card on Drive when the file itself was thrown out (spec 1.6). */
    val cardId: String? = null,
    val protectedManually: Boolean = false,

    @ColumnInfo(defaultValue = "0") val hashedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val triagedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val lastSeenAt: Long = 0,
    /** Set when the user parked the asset with a "decide later" swipe. */
    @ColumnInfo(defaultValue = "0") val deferredAt: Long = 0,
    val trashedAt: Long? = null,
    /** Local thumbnail kept after the full resolution file is gone. */
    val ghostThumbPath: String? = null,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isImage: Boolean get() = mimeType.startsWith("image/")

    /** True while the asset still occupies bytes on the device. */
    val occupiesStorage: Boolean
        get() = state == AssetState.LOCAL || state == AssetState.UPLOADING ||
            state == AssetState.ARCHIVED || state == AssetState.PROTECTED ||
            state == AssetState.TRASHED
}
