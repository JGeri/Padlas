package hu.padlas.feature.dedup.group

import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.ShotKind

/**
 * The only thing the grouper is allowed to know about an asset. It exists separately from
 * [Asset] so the grouping rules stay a pure function over 25 000 rows: no Room, no Android,
 * unit testable in milliseconds.
 */
data class DupCandidate(
    val assetId: Long,
    val sizeBytes: Long,
    val dateTaken: Long,
    val quickHash: String? = null,
    val sha256: String? = null,
    val dHash: Long? = null,
    val blurScore: Float? = null,
    /** Always with the trailing slash, exactly as MediaStore reports it. */
    val relativePath: String = "",
    val isFavorite: Boolean = false,
    val protectedManually: Boolean = false,
    val shotKind: ShotKind? = null,
)

/** INV-4 / INV-1c: the window in which a camera shot is still considered fresh. */
const val FRESH_CAMERA_WINDOW_MILLIS: Long = 30L * 24 * 60 * 60 * 1000

private const val CAMERA_PATH_PREFIX = "DCIM/Camera"

/**
 * INV-4 and INV-1c. A protected asset is still allowed to sit in a group - seeing the
 * favourite next to its three siblings is the whole point - it just can never be the one
 * that gets thrown away.
 *
 * Freshness is measured on `dateTaken` rather than on the file timestamp: a photo copied
 * back from a backup yesterday is not a shot from yesterday, and the user's memory of
 * "I took this last week" follows the capture time.
 */
fun DupCandidate.isProtected(now: Long): Boolean = when {
    protectedManually -> true
    isFavorite -> true
    shotKind == ShotKind.SECRET -> true
    else -> relativePath.startsWith(CAMERA_PATH_PREFIX, ignoreCase = true) &&
        now - dateTaken <= FRESH_CAMERA_WINDOW_MILLIS
}

fun Asset.toDupCandidate(): DupCandidate = DupCandidate(
    assetId = mediaStoreId,
    sizeBytes = sizeBytes,
    dateTaken = dateTaken,
    quickHash = quickHash,
    sha256 = sha256,
    dHash = dHash,
    blurScore = blurScore,
    relativePath = relativePath,
    isFavorite = isFavorite,
    protectedManually = protectedManually,
    shotKind = shotKind,
)
