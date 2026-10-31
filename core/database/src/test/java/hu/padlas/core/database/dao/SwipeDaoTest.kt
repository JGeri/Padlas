package hu.padlas.core.database.dao

import com.google.common.truth.Truth.assertThat
import hu.padlas.core.database.PadlasDatabase
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.entity.SwipeChoice
import hu.padlas.core.database.entity.SwipeDecisionEntity
import hu.padlas.core.testing.DAY_MS
import hu.padlas.core.testing.JULY_2024
import hu.padlas.core.testing.asset
import hu.padlas.core.testing.testDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SwipeDaoTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: PadlasDatabase
    private lateinit var dao: SwipeDao
    private lateinit var assets: AssetDao

    @Before
    fun open() {
        db = testDatabase(dispatcher)
        dao = db.swipeDao()
        assets = db.assetDao()
    }

    @After
    fun close() = db.close()

    @Test
    fun `the pending pile counts deletions only while the file is still on the phone`() = runTest(dispatcher) {
        assets.upsertAll(
            listOf(
                asset(1, sizeBytes = 1_000),
                asset(2, sizeBytes = 2_000, state = AssetState.PROTECTED),
                asset(3, sizeBytes = 4_000, state = AssetState.TRASHED, trashedAt = 1),
                asset(4, sizeBytes = 8_000, state = AssetState.GHOST),
                asset(5, sizeBytes = 16_000),
                asset(6, sizeBytes = 32_000),
            ),
        )
        dao.upsertAll(
            listOf(
                decision(1, SwipeChoice.DELETE),
                decision(2, SwipeChoice.DELETE),
                decision(3, SwipeChoice.DELETE),
                decision(4, SwipeChoice.DELETE),
                decision(5, SwipeChoice.KEEP),
                decision(6, SwipeChoice.FAVORITE),
            ),
        )

        val pending = dao.observePending().first()

        assertThat(pending.itemCount).isEqualTo(2)
        assertThat(pending.totalBytes).isEqualTo(3_000)
        assertThat(dao.pendingAssets().map { it.mediaStoreId }).containsExactly(1L, 2L)
        assertThat(dao.observePendingAssets().first().map { it.mediaStoreId }).containsExactly(1L, 2L)
    }

    @Test
    fun `pending deletions come newest decision first`() = runTest(dispatcher) {
        assets.upsertAll(listOf(asset(1), asset(2), asset(3)))
        dao.upsertAll(
            listOf(
                decision(1, SwipeChoice.DELETE, at = 20),
                decision(2, SwipeChoice.DELETE, at = 30),
                decision(3, SwipeChoice.DELETE, at = 10),
            ),
        )

        assertThat(dao.pendingAssets().map { it.mediaStoreId }).containsExactly(2L, 1L, 3L).inOrder()
    }

    @Test
    fun `forgetting what was reviewed never forgets what is waiting to be deleted`() = runTest(dispatcher) {
        assets.upsertAll(listOf(asset(1), asset(2), asset(3)))
        dao.upsertAll(listOf(decision(1, SwipeChoice.DELETE), decision(2, SwipeChoice.KEEP), decision(3, SwipeChoice.FAVORITE)))

        dao.forgetReviewed()

        assertThat(dao.byAsset(1)?.choice).isEqualTo(SwipeChoice.DELETE)
        assertThat(dao.byAsset(2)).isNull()
        assertThat(dao.byAsset(3)).isNull()
    }

    @Test
    fun `deleting decisions removes only the ones named`() = runTest(dispatcher) {
        assets.upsertAll(listOf(asset(1), asset(2), asset(3)))
        dao.upsertAll(listOf(decision(1, SwipeChoice.DELETE), decision(2, SwipeChoice.DELETE), decision(3, SwipeChoice.KEEP)))

        dao.delete(listOf(1, 3))

        assertThat(dao.byAsset(1)).isNull()
        assertThat(dao.byAsset(2)?.choice).isEqualTo(SwipeChoice.DELETE)
        assertThat(dao.byAsset(3)).isNull()
    }

    @Test
    fun `a month counts what was reviewed and its videos, with the newest item as the cover`() = runTest(dispatcher) {
        assets.upsertAll(
            listOf(
                asset(1, dateTaken = JULY_2024 - 10 * DAY_MS),
                asset(2, dateTaken = JULY_2024 + 5 * DAY_MS, mimeType = "video/mp4"),
                asset(3, dateTaken = JULY_2024),
            ),
        )
        dao.upsert(decision(3, SwipeChoice.KEEP))

        val month = dao.observeMonthStats("%").first().single()

        assertThat(month.month).isEqualTo("2024-07")
        assertThat(month.itemCount).isEqualTo(3)
        assertThat(month.reviewedCount).isEqualTo(1)
        assertThat(month.videoCount).isEqualTo(1)
        assertThat(month.coverId).isEqualTo(2L)
        assertThat(month.coverMime).isEqualTo("video/mp4")
    }

    // A month past the year 9999 (a date in microseconds) is NULL on the phone's SQLite but not
    // on Robolectric's, so the HAVING guard for it cannot be exercised here.
    @Test
    fun `month stats skip the bin and the undated`() = runTest(dispatcher) {
        assets.upsertAll(
            listOf(
                asset(1, dateTaken = JULY_2024),
                asset(2, dateTaken = JULY_2024, state = AssetState.TRASHED, trashedAt = 1),
                asset(3, dateTaken = 0),
            ),
        )

        val months = dao.observeMonthStats("%").first()

        assertThat(months.map { it.month }).containsExactly("2024-07")
        assertThat(months.single().itemCount).isEqualTo(1)
    }

    @Test
    fun `the media pattern narrows the months to photos or to videos`() = runTest(dispatcher) {
        assets.upsertAll(
            listOf(
                asset(1, dateTaken = JULY_2024),
                asset(2, dateTaken = JULY_2024 - 400 * DAY_MS, mimeType = "video/mp4"),
            ),
        )

        assertThat(dao.observeMonthStats("image/%").first().map { it.month }).containsExactly("2024-07")
        assertThat(dao.observeMonthStats("video/%").first().map { it.month }).containsExactly("2023-06")
    }

    @Test
    fun `a folder's cover is its newest item still on the phone`() = runTest(dispatcher) {
        assets.upsertAll(
            listOf(
                asset(1, bucketId = 7, dateTaken = JULY_2024),
                asset(2, bucketId = 7, dateTaken = JULY_2024 + DAY_MS),
                asset(3, bucketId = 7, dateTaken = JULY_2024 + 2 * DAY_MS, state = AssetState.TRASHED, trashedAt = 1),
                asset(4, bucketId = 8, bucketName = "Screenshots", dateTaken = JULY_2024),
            ),
        )
        dao.upsert(decision(1, SwipeChoice.DELETE))

        val folders = dao.observeBucketStats("%").first().associateBy { it.bucketId }

        assertThat(folders.getValue(7).itemCount).isEqualTo(2)
        assertThat(folders.getValue(7).reviewedCount).isEqualTo(1)
        assertThat(folders.getValue(7).coverId).isEqualTo(2L)
        assertThat(folders.getValue(8).coverId).isEqualTo(4L)
    }

    private fun decision(id: Long, choice: SwipeChoice, at: Long = 1) = SwipeDecisionEntity(id, choice, at)
}
