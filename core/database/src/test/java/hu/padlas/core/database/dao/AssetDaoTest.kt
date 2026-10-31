package hu.padlas.core.database.dao

import com.google.common.truth.Truth.assertThat
import hu.padlas.core.database.PadlasDatabase
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.entity.DupGroupEntity
import hu.padlas.core.database.entity.DupKind
import hu.padlas.core.database.entity.DupMemberEntity
import hu.padlas.core.database.entity.ShotKind
import hu.padlas.core.database.entity.ValueClass
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
class AssetDaoTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: PadlasDatabase
    private lateinit var dao: AssetDao

    @Before
    fun open() {
        db = testDatabase(dispatcher)
        dao = db.assetDao()
    }

    @After
    fun close() = db.close()

    // ---- the bin ------------------------------------------------------------

    @Test
    fun `markTrashed moves rows to the bin with the time it happened`() = runTest(dispatcher) {
        dao.upsertAll(listOf(asset(1), asset(2), asset(3)))

        dao.markTrashed(listOf(1, 2), now = 5_000)

        assertThat(dao.byId(1)?.state).isEqualTo(AssetState.TRASHED)
        assertThat(dao.byId(1)?.trashedAt).isEqualTo(5_000)
        assertThat(dao.byId(2)?.state).isEqualTo(AssetState.TRASHED)
        assertThat(dao.byId(3)?.state).isEqualTo(AssetState.LOCAL)
        assertThat(dao.byId(3)?.trashedAt).isNull()
    }

    @Test
    fun `markUntrashed brings a row back as local and forgets when it was binned`() = runTest(dispatcher) {
        dao.upsertAll(listOf(asset(1), asset(2)))
        dao.markTrashed(listOf(1, 2), now = 5_000)

        dao.markUntrashed(listOf(1))

        assertThat(dao.byId(1)?.state).isEqualTo(AssetState.LOCAL)
        assertThat(dao.byId(1)?.trashedAt).isNull()
        assertThat(dao.byId(2)?.state).isEqualTo(AssetState.TRASHED)
    }

    @Test
    fun `trashedIds lists only what sits in the bin`() = runTest(dispatcher) {
        dao.upsertAll(
            listOf(
                asset(1, state = AssetState.TRASHED, trashedAt = 1),
                asset(2, state = AssetState.GHOST),
                asset(3, state = AssetState.ARCHIVED),
                asset(4, state = AssetState.PROTECTED),
                asset(5),
                asset(6, state = AssetState.TRASHED, trashedAt = 2),
            ),
        )

        assertThat(dao.trashedIds()).containsExactly(1L, 6L)
    }

    // ---- folders and months -------------------------------------------------

    @Test
    fun `folders count only what still occupies the phone, not the bin and not Drive-only ghosts`() = runTest(dispatcher) {
        dao.upsertAll(
            listOf(
                asset(1, bucketId = 1, sizeBytes = 1_000),
                asset(2, bucketId = 1, sizeBytes = 1_000),
                asset(3, bucketId = 1, sizeBytes = 50_000, state = AssetState.TRASHED, trashedAt = 1),
                asset(4, bucketId = 1, sizeBytes = 50_000, state = AssetState.GHOST),
                asset(5, bucketId = 2, bucketName = "Bin only", state = AssetState.TRASHED, trashedAt = 1),
                asset(6, bucketId = 3, bucketName = "WhatsApp", relativePath = "WhatsApp/", sizeBytes = 9_000, state = AssetState.PROTECTED),
                asset(7, bucketId = 3, bucketName = "WhatsApp", relativePath = "WhatsApp/", sizeBytes = 1_000, state = AssetState.ARCHIVED),
            ),
        )

        val buckets = dao.observeBuckets().first()

        assertThat(buckets.map { it.bucketId }).containsExactly(3L, 1L).inOrder()
        assertThat(buckets.first { it.bucketId == 1L }.itemCount).isEqualTo(2)
        assertThat(buckets.first { it.bucketId == 1L }.totalBytes).isEqualTo(2_000)
        assertThat(buckets.first { it.bucketId == 3L }.totalBytes).isEqualTo(10_000)
    }

    @Test
    fun `months list only what is still on the phone and has a date, newest first`() = runTest(dispatcher) {
        val february2023 = JULY_2024 - 520 * DAY_MS
        dao.upsertAll(
            listOf(
                asset(1, dateTaken = JULY_2024),
                asset(2, dateTaken = JULY_2024 + 1_000),
                asset(3, dateTaken = february2023),
                asset(5, dateTaken = 0),
                asset(6, dateTaken = JULY_2024, state = AssetState.TRASHED, trashedAt = 1),
            ),
        )

        val months = dao.observeMonths().first()

        assertThat(months.map { it.month }).containsExactly("2024-07", "2023-02").inOrder()
        assertThat(months.first().itemCount).isEqualTo(2)
    }

    // ---- protection ---------------------------------------------------------

    @Test
    fun `protecting flips local and protected both ways and leaves a trashed row alone`() = runTest(dispatcher) {
        dao.upsertAll(listOf(asset(1), asset(2, state = AssetState.TRASHED, trashedAt = 1)))

        dao.setProtected(listOf(1, 2), protectedManually = true)
        assertThat(dao.byId(1)?.state).isEqualTo(AssetState.PROTECTED)
        assertThat(dao.byId(2)?.state).isEqualTo(AssetState.TRASHED)

        dao.setProtected(listOf(1, 2), protectedManually = false)
        assertThat(dao.byId(1)?.state).isEqualTo(AssetState.LOCAL)
        assertThat(dao.byId(1)?.protectedManually).isFalse()
        assertThat(dao.byId(2)?.state).isEqualTo(AssetState.TRASHED)
    }

    @Test
    fun `a screenshot read as secret is protected on the spot`() = runTest(dispatcher) {
        dao.upsertAll(listOf(asset(1, valueClass = ValueClass.EPHEMERAL), asset(2, valueClass = ValueClass.EPHEMERAL)))

        dao.setTriage(1, text = "PIN 1234", kind = ShotKind.SECRET, now = 10)
        dao.setTriage(2, text = "Lidl", kind = ShotKind.RECEIPT, now = 10)

        assertThat(dao.byId(1)?.state).isEqualTo(AssetState.PROTECTED)
        assertThat(dao.byId(1)?.protectedManually).isTrue()
        assertThat(dao.byId(2)?.state).isEqualTo(AssetState.LOCAL)
        assertThat(dao.byId(2)?.protectedManually).isFalse()
    }

    @Test
    fun `the discardable total never counts a secret or protected shot`() = runTest(dispatcher) {
        dao.upsertAll(
            listOf(
                asset(1, sizeBytes = 100, valueClass = ValueClass.EPHEMERAL),
                asset(2, sizeBytes = 200, valueClass = ValueClass.EPHEMERAL, shotKind = ShotKind.OTHER),
                asset(3, sizeBytes = 400, valueClass = ValueClass.EPHEMERAL, shotKind = ShotKind.SECRET),
                asset(4, sizeBytes = 800, valueClass = ValueClass.EPHEMERAL, protectedManually = true),
                asset(5, sizeBytes = 1_600, valueClass = ValueClass.IRREPLACEABLE),
            ),
        )

        val discardable = dao.observeDiscardable().first()

        assertThat(discardable.itemCount).isEqualTo(2)
        assertThat(discardable.totalBytes).isEqualTo(300)
    }

    @Test
    fun `the sorting deck leaves out everything an earlier layer already decided`() = runTest(dispatcher) {
        dao.upsertAll(
            listOf(
                asset(1),
                asset(2, protectedManually = true),
                asset(3, isFavorite = true),
                asset(4, shotKind = ShotKind.SECRET),
                asset(5),
                asset(6),
                asset(7, state = AssetState.TRASHED, trashedAt = 1),
                asset(8),
            ),
        )
        dao.defer(listOf(5), now = 1)
        db.dupDao().replaceAll(
            listOf(DupGroupEntity("g", DupKind.EXACT, representativeAssetId = 8, memberCount = 2, reclaimableBytes = 1_000, createdAt = 1)),
            listOf(
                DupMemberEntity("g", assetId = 8, isWinner = true, sharpness = null, distance = 0),
                DupMemberEntity("g", assetId = 6, isWinner = false, sharpness = null, distance = 0),
            ),
        )

        val deck = dao.sortingDeck(notNewerThan = Long.MAX_VALUE, limit = 100)

        assertThat(deck.map { it.mediaStoreId }).containsExactly(1L, 8L)
    }

    @Test
    fun `replacing the whole index leaves exactly the new rows`() = runTest(dispatcher) {
        dao.upsertAll(listOf(asset(1), asset(2)))

        dao.replaceWholeIndex(listOf(asset(3), asset(4), asset(5)))

        assertThat(dao.allIds()).containsExactly(3L, 4L, 5L)
        assertThat(dao.count()).isEqualTo(3)
    }
}
