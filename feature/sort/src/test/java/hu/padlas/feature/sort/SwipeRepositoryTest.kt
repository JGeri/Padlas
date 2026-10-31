package hu.padlas.feature.sort

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import hu.padlas.core.database.PadlasDatabase
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.entity.SwipeChoice
import hu.padlas.core.database.entity.SwipeDecisionEntity
import hu.padlas.core.media.MediaOps
import hu.padlas.core.testing.DAY_MS
import hu.padlas.core.testing.FakeMediaOps
import hu.padlas.core.testing.JULY_2024
import hu.padlas.core.testing.asset
import hu.padlas.core.testing.assets
import hu.padlas.core.testing.testDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The deck WHERE clause is built in Kotlin; here it runs on SQLite next to the month stats it must agree with. */
@RunWith(RobolectricTestRunner::class)
class SwipeRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: PadlasDatabase
    private lateinit var media: FakeMediaOps
    private lateinit var repository: SwipeRepository

    @Before
    fun setUp() {
        db = testDatabase(dispatcher)
        media = FakeMediaOps()
        val preferences = SwipePreferences(ApplicationProvider.getApplicationContext())
        repository = SwipeRepository(db.swipeDao(), db.assetDao(), db.auditDao(), media, preferences, dispatcher)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a month's deck holds exactly what its tile counts`() = runTest(dispatcher) {
        db.assetDao().upsertAll(
            listOf(
                asset(1, dateTaken = JULY_2024),
                asset(2, dateTaken = JULY_2024 + DAY_MS, mimeType = "video/mp4"),
                asset(3, dateTaken = JULY_2024 - 60 * DAY_MS),
                asset(4, dateTaken = JULY_2024, state = AssetState.TRASHED, trashedAt = 1),
                asset(5, dateTaken = JULY_2024),
            ),
        )
        db.swipeDao().upsert(SwipeDecisionEntity(5, SwipeChoice.KEEP, 1))
        val july = repository.monthStats().first().single { it.month == "2024-07" }

        val all = repository.deck(SwipeFilter(months = setOf("2024-07"), onlyUnreviewed = false))
        val fresh = repository.deck(SwipeFilter(months = setOf("2024-07")))
        val videos = repository.deck(SwipeFilter(months = setOf("2024-07"), media = MediaFilter.VIDEOS))

        assertThat(all.map { it.id }).containsExactly(1L, 2L, 5L)
        assertThat(all).hasSize(july.itemCount)
        assertThat(fresh.map { it.id }).containsExactly(1L, 2L)
        assertThat(fresh).hasSize(july.itemCount - july.reviewedCount)
        assertThat(videos.map { it.id }).containsExactly(2L)
        assertThat(all.single { it.id == 5L }.previous).isEqualTo(SwipeChoice.KEEP)
        assertThat(repository.count(SwipeFilter(months = setOf("2024-07"))).itemCount).isEqualTo(2)
    }

    @Test
    fun `a delete request carries one dialog at most and says when more is waiting`() = runTest(dispatcher) {
        val rows = assets(MediaOps.MAX_REQUEST + 1)

        val batch = checkNotNull(repository.deleteRequest(rows))

        assertThat(batch.ids).hasSize(MediaOps.MAX_REQUEST)
        assertThat(batch.truncated).isTrue()
        assertThat(batch.permanent).isFalse()
        assertThat(media.requests.single().kind).isEqualTo(FakeMediaOps.Kind.TRASH)
        assertThat(repository.deleteRequest(emptyList())).isNull()
    }

    @Test
    fun `undo restores the earlier decision, or none at all`() = runTest(dispatcher) {
        db.assetDao().upsertAll(assets(2))
        db.swipeDao().upsert(SwipeDecisionEntity(2, SwipeChoice.FAVORITE, 1))

        repository.record(1, SwipeAction.DELETE)
        repository.record(2, SwipeAction.DELETE)
        repository.restore(1, previous = null)
        repository.restore(2, previous = SwipeChoice.FAVORITE)

        assertThat(db.swipeDao().byAsset(1)).isNull()
        assertThat(db.swipeDao().byAsset(2)?.choice).isEqualTo(SwipeChoice.FAVORITE)
        assertThat(repository.pendingNow()).isEmpty()
    }
}
