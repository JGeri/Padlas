package hu.padlas.feature.index

import com.google.common.truth.Truth.assertThat
import hu.padlas.core.common.result.Outcome
import hu.padlas.core.database.PadlasDatabase
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.testing.DAY_MS
import hu.padlas.core.testing.FakeMediaStoreScanner
import hu.padlas.core.testing.asset
import hu.padlas.core.testing.testDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IndexRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: PadlasDatabase
    private lateinit var mediaStore: FakeMediaStoreScanner
    private lateinit var index: IndexRepository

    private val now: Long get() = System.currentTimeMillis()

    @Before
    fun setUp() {
        db = testDatabase(dispatcher)
        mediaStore = FakeMediaStoreScanner()
        index = IndexRepository(mediaStore, db.assetDao(), db.auditDao(), dispatcher)
    }

    @After
    fun tearDown() = db.close()

    // ---- the bin ------------------------------------------------------------

    @Test
    fun `a binned file keeps its row while it can still come back`() = runTest(dispatcher) {
        db.assetDao().upsertAll(listOf(asset(1, state = AssetState.TRASHED, trashedAt = now - 5 * DAY_MS), asset(2)))
        mediaStore.put(asset(2))

        assertThat(index.runIncremental()).isInstanceOf(Outcome.Success::class.java)

        assertThat(db.assetDao().byId(1)?.state).isEqualTo(AssetState.TRASHED)
    }

    @Test
    fun `a binned row older than the bin itself is dropped`() = runTest(dispatcher) {
        db.assetDao().upsertAll(listOf(asset(1, state = AssetState.TRASHED, trashedAt = now - 40 * DAY_MS), asset(2)))
        mediaStore.put(asset(2))

        index.runIncremental()

        assertThat(db.assetDao().byId(1)).isNull()
        assertThat(db.assetDao().byId(2)).isNotNull()
    }

    @Test
    fun `a binned file that MediaStore lists again is local again, even under the watermark`() = runTest(dispatcher) {
        // Restored from the gallery's bin: back in MediaStore with its old timestamp.
        db.assetDao().upsertAll(
            listOf(
                asset(1, dateModified = 100, state = AssetState.TRASHED, trashedAt = now - DAY_MS),
                asset(2, dateModified = 5_000),
            ),
        )
        mediaStore.put(asset(1, dateModified = 100), asset(2, dateModified = 5_000))

        index.runIncremental()

        assertThat(db.assetDao().byId(1)?.state).isEqualTo(AssetState.LOCAL)
        assertThat(db.assetDao().byId(1)?.trashedAt).isNull()
        assertThat(db.assetDao().trashedIds()).isEmpty()
    }

    @Test
    fun `a full scan brings a restored file back as local too`() = runTest(dispatcher) {
        db.assetDao().upsertAll(listOf(asset(1, state = AssetState.TRASHED, trashedAt = now - DAY_MS)))
        mediaStore.put(asset(1))

        index.runFullScan()

        assertThat(db.assetDao().byId(1)?.state).isEqualTo(AssetState.LOCAL)
    }

    // ---- the watermark ------------------------------------------------------

    @Test
    fun `the incremental pass reads files the index lacks, whatever their timestamp says`() = runTest(dispatcher) {
        db.assetDao().upsertAll(listOf(asset(1, dateModified = 5_000)))
        // Copied in from a PC: it keeps a modification time far below the watermark.
        mediaStore.put(asset(1, dateModified = 5_000), asset(2, dateModified = 100))

        index.runIncremental()

        assertThat(db.assetDao().byId(2)).isNotNull()
        assertThat(mediaStore.readById).containsExactly(2L)
    }

    @Test
    fun `a file rewritten after the watermark is picked up with its new size`() = runTest(dispatcher) {
        db.assetDao().upsertAll(listOf(asset(1, dateModified = 5_000, sizeBytes = 1_000)))
        mediaStore.put(asset(1, dateModified = 6_000, sizeBytes = 2_000))

        index.runIncremental()

        assertThat(db.assetDao().byId(1)?.sizeBytes).isEqualTo(2_000)
        assertThat(mediaStore.readById).isEmpty()
    }

    // ---- reconciliation -----------------------------------------------------

    @Test
    fun `an empty answer from MediaStore never wipes the index`() = runTest(dispatcher) {
        db.assetDao().upsertAll(listOf(asset(1), asset(2), asset(3)))
        mediaStore.unavailable = true

        index.runIncremental()

        assertThat(db.assetDao().allIds()).containsExactly(1L, 2L, 3L)
    }

    @Test
    fun `an archived file gone from the phone becomes a ghost, a plain one is dropped`() = runTest(dispatcher) {
        db.assetDao().upsertAll(
            listOf(
                asset(1, state = AssetState.ARCHIVED).copy(driveFileId = "drive-1"),
                asset(2),
                asset(3),
            ),
        )
        mediaStore.put(asset(3))

        index.runIncremental()

        assertThat(db.assetDao().byId(1)?.state).isEqualTo(AssetState.GHOST)
        assertThat(db.assetDao().byId(2)).isNull()
        assertThat(db.auditDao().latest(AuditAction.RECONCILED)).isNotNull()
    }

    @Test
    fun `a pass that finds nothing new moves the last log line instead of adding one`() = runTest(dispatcher) {
        db.assetDao().upsertAll(listOf(asset(1, dateModified = 5_000)))
        mediaStore.put(asset(1, dateModified = 5_000))

        index.runIncremental()
        index.runIncremental()
        index.runIncremental()

        assertThat(db.auditDao().all().count { it.action == AuditAction.INDEXED }).isEqualTo(1)
    }
}
