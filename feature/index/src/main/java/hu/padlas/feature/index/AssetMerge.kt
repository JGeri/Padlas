package hu.padlas.feature.index

import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.entity.ClassifiedBy
import hu.padlas.core.database.entity.ShotKind

/**
 * Room's `@Upsert` replaces the whole row, so a naive re-scan would throw away every
 * column the later pipelines paid for: hashes, OCR text, the value class, the Drive
 * pointer. The scanner only owns the MediaStore columns; everything else is carried over.
 *
 * Change detection is `dateModified` + `sizeBytes`, deliberately not
 * `MediaStore.getGeneration()`: on Samsung the generation counter does not always move
 * when a file is rewritten in place (spec 2.3).
 */
internal fun mergeAsset(existing: Asset?, fresh: Asset, now: Long): Asset {
    if (existing == null) return fresh.copy(lastSeenAt = now)

    val contentChanged =
        existing.sizeBytes != fresh.sizeBytes || existing.dateModified != fresh.dateModified
    val manual = existing.classifiedBy == ClassifiedBy.MANUAL
    // INV-1c: a screenshot once classified SECRET never loses that classification, and a
    // rewrite of the file must not silently drop it back into the deletable pool.
    val secret = existing.shotKind == ShotKind.SECRET
    val keepDerived = !contentChanged

    return fresh.copy(
        // EXIF beats MediaStore, and only the enrichment pass knows the EXIF value.
        dateTaken = if (existing.dateTaken > 0 && keepDerived) existing.dateTaken else fresh.dateTaken,
        latitude = fresh.latitude ?: existing.latitude,
        longitude = fresh.longitude ?: existing.longitude,

        quickHash = if (keepDerived) existing.quickHash else null,
        sha256 = if (keepDerived) existing.sha256 else null,
        dHash = if (keepDerived) existing.dHash else null,
        blurScore = if (keepDerived) existing.blurScore else null,
        ocrText = if (keepDerived || secret) existing.ocrText else null,

        valueClass = if (keepDerived || manual) existing.valueClass else fresh.valueClass,
        classifiedBy = if (keepDerived || manual) existing.classifiedBy else fresh.classifiedBy,
        shotKind = when {
            secret -> ShotKind.SECRET
            keepDerived -> existing.shotKind
            else -> null
        },

        state = restoredState(existing),
        driveFileId = existing.driveFileId,
        driveMd5 = existing.driveMd5,
        archivedAt = existing.archivedAt,
        cardId = existing.cardId,
        protectedManually = existing.protectedManually,

        hashedAt = if (keepDerived) existing.hashedAt else 0L,
        triagedAt = if (keepDerived || secret) existing.triagedAt else 0L,
        deferredAt = existing.deferredAt,
        trashedAt = if (existing.state == AssetState.TRASHED) null else existing.trashedAt,
        ghostThumbPath = existing.ghostThumbPath,
        lastSeenAt = now,
    )
}

/**
 * A GHOST row whose file turned up in MediaStore again was restored from Drive or copied
 * back by hand; it is the same asset, not a new one. A TRASHED one that MediaStore reports
 * again came back out of the bin, since MediaStore never lists what is still in it.
 */
private fun restoredState(existing: Asset): AssetState = when {
    existing.state == AssetState.TRASHED -> AssetState.LOCAL
    existing.state != AssetState.GHOST -> existing.state
    existing.driveFileId != null -> AssetState.ARCHIVED
    else -> AssetState.LOCAL
}
