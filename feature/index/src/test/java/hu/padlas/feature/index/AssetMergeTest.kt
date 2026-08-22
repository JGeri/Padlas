package hu.padlas.feature.index

import com.google.common.truth.Truth.assertThat
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.entity.ClassifiedBy
import hu.padlas.core.database.entity.ShotKind
import hu.padlas.core.database.entity.ValueClass
import org.junit.Test

class AssetMergeTest {

    @Test
    fun `a new asset is stored as scanned`() {
        val fresh = scanned()

        val merged = mergeAsset(existing = null, fresh = fresh, now = NOW)

        assertThat(merged.mediaStoreId).isEqualTo(fresh.mediaStoreId)
        assertThat(merged.lastSeenAt).isEqualTo(NOW)
    }

    @Test
    fun `an unchanged asset keeps every derived column`() {
        val existing = scanned().copy(
            quickHash = "quick",
            sha256 = "full",
            dHash = 42L,
            blurScore = 3.5f,
            valueClass = ValueClass.IRREPLACEABLE,
            classifiedBy = ClassifiedBy.EXIF,
            state = AssetState.ARCHIVED,
            driveFileId = "drive-1",
            hashedAt = 10L,
            triagedAt = 11L,
        )

        val merged = mergeAsset(existing, scanned(), NOW)

        assertThat(merged.quickHash).isEqualTo("quick")
        assertThat(merged.sha256).isEqualTo("full")
        assertThat(merged.dHash).isEqualTo(42L)
        assertThat(merged.valueClass).isEqualTo(ValueClass.IRREPLACEABLE)
        assertThat(merged.state).isEqualTo(AssetState.ARCHIVED)
        assertThat(merged.driveFileId).isEqualTo("drive-1")
        assertThat(merged.hashedAt).isEqualTo(10L)
    }

    @Test
    fun `a rewritten file drops its hashes but keeps the manual class`() {
        val existing = scanned().copy(
            quickHash = "quick",
            sha256 = "full",
            dHash = 42L,
            valueClass = ValueClass.IRREPLACEABLE,
            classifiedBy = ClassifiedBy.MANUAL,
            hashedAt = 10L,
        )
        val rewritten = scanned().copy(sizeBytes = 999L, dateModified = 5_000L)

        val merged = mergeAsset(existing, rewritten, NOW)

        assertThat(merged.quickHash).isNull()
        assertThat(merged.sha256).isNull()
        assertThat(merged.dHash).isNull()
        assertThat(merged.hashedAt).isEqualTo(0L)
        assertThat(merged.sizeBytes).isEqualTo(999L)
        assertThat(merged.valueClass).isEqualTo(ValueClass.IRREPLACEABLE)
        assertThat(merged.classifiedBy).isEqualTo(ClassifiedBy.MANUAL)
    }

    @Test
    fun `a secret screenshot stays secret through a rewrite`() {
        val existing = scanned().copy(
            shotKind = ShotKind.SECRET,
            protectedManually = true,
            state = AssetState.PROTECTED,
            triagedAt = 7L,
        )

        val merged = mergeAsset(existing, scanned().copy(sizeBytes = 1L), NOW)

        assertThat(merged.shotKind).isEqualTo(ShotKind.SECRET)
        assertThat(merged.protectedManually).isTrue()
        assertThat(merged.state).isEqualTo(AssetState.PROTECTED)
        assertThat(merged.triagedAt).isEqualTo(7L)
    }

    @Test
    fun `a ghost seen again on the device becomes archived`() {
        val existing = scanned().copy(state = AssetState.GHOST, driveFileId = "drive-1")

        val merged = mergeAsset(existing, scanned(), NOW)

        assertThat(merged.state).isEqualTo(AssetState.ARCHIVED)
        assertThat(merged.driveFileId).isEqualTo("drive-1")
    }

    private fun scanned(): Asset = Asset(
        mediaStoreId = 1L,
        uri = "content://media/external/images/media/1",
        relativePath = "DCIM/Camera/",
        displayName = "IMG_0001.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 100L,
        dateTaken = 1_000L,
        dateModified = 2_000L,
        width = 4000,
        height = 3000,
        durationMs = null,
        bucketId = 7L,
        bucketName = "Camera",
        latitude = null,
        longitude = null,
    )

    private companion object {
        const val NOW = 123_456L
    }
}
