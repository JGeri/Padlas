package hu.padlas.feature.rules

import com.google.common.truth.Truth.assertThat
import hu.padlas.core.database.entity.BucketSnapshotEntity
import org.junit.Test

class StorageDeltaTrackerTest {

    @Test
    fun `the same relative path on two volumes becomes one row with both sizes`() {
        val merged = listOf(
            bucket("DCIM/Camera/", bytes = 3_000, items = 3),
            bucket("Download/", bytes = 500, items = 1),
            bucket("DCIM/Camera/", bytes = 2_000, items = 2),
        ).mergedByKey()

        assertThat(merged.map { it.bucketKey }).containsExactly("DCIM/Camera/", "Download/").inOrder()
        val camera = merged.first()
        assertThat(camera.bytes).isEqualTo(5_000)
        assertThat(camera.itemCount).isEqualTo(5)
    }

    @Test
    fun `distinct keys pass through untouched`() {
        val input = listOf(bucket("Pictures/", bytes = 10, items = 1), bucket("com.whatsapp", bytes = 20, items = 0))

        assertThat(input.mergedByKey()).isEqualTo(input)
    }

    private fun bucket(key: String, bytes: Long, items: Int) = BucketSnapshotEntity(
        snapshotId = 7,
        bucketKey = key,
        label = key,
        bytes = bytes,
        itemCount = items,
    )
}
