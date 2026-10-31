package hu.padlas.core.testing

import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.entity.ShotKind
import hu.padlas.core.database.entity.ValueClass

/** 2024-07-15 12:00 UTC: old enough that no fresh-camera protection applies. */
const val JULY_2024: Long = 1_721_044_800_000L

const val DAY_MS: Long = 24L * 60 * 60 * 1000

/**
 * A row as the scanner would write it. The defaults are an ordinary, unprotected, local
 * photo; a test spells out only what it is about.
 */
fun asset(
    id: Long,
    sizeBytes: Long = 1_000,
    dateTaken: Long = JULY_2024,
    dateModified: Long = dateTaken / 1000,
    mimeType: String = "image/jpeg",
    displayName: String = if (mimeType.startsWith("video/")) "VID_$id.mp4" else "IMG_$id.jpg",
    relativePath: String = "DCIM/Camera/",
    bucketId: Long = 1,
    bucketName: String = "Camera",
    state: AssetState = AssetState.LOCAL,
    valueClass: ValueClass = ValueClass.REPLACEABLE,
    shotKind: ShotKind? = null,
    isFavorite: Boolean = false,
    protectedManually: Boolean = false,
    trashedAt: Long? = null,
): Asset {
    val collection = if (mimeType.startsWith("video/")) "video" else "images"
    return Asset(
        mediaStoreId = id,
        uri = "content://media/external/$collection/media/$id",
        relativePath = relativePath,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        dateTaken = dateTaken,
        dateModified = dateModified,
        width = 4000,
        height = 3000,
        durationMs = if (mimeType.startsWith("video/")) 10_000L else null,
        bucketId = bucketId,
        bucketName = bucketName,
        latitude = null,
        longitude = null,
        isFavorite = isFavorite,
        valueClass = valueClass,
        shotKind = shotKind,
        state = state,
        protectedManually = protectedManually,
        trashedAt = trashedAt,
    )
}

/** [count] ordinary photos with consecutive ids from [firstId]. */
fun assets(count: Int, firstId: Long = 1, sizeBytes: Long = 1_000): List<Asset> =
    List(count) { asset(firstId + it, sizeBytes = sizeBytes) }
