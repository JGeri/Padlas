package hu.padlas.core.database.dao

import com.google.common.truth.Truth.assertThat
import hu.padlas.core.database.PadlasDatabase
import hu.padlas.core.database.entity.DupGroupEntity
import hu.padlas.core.database.entity.DupKind
import hu.padlas.core.database.entity.DupMemberEntity
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
class DupDaoTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: PadlasDatabase
    private lateinit var dao: DupDao

    @Before
    fun open() {
        db = testDatabase(dispatcher)
        dao = db.dupDao()
    }

    @After
    fun close() = db.close()

    @Test
    fun `the overview promises only open exact copies, never bursts or similar shots`() = runTest(dispatcher) {
        dao.replaceAll(
            listOf(
                group("exact-open", DupKind.EXACT, reclaimable = 100),
                group("exact-done", DupKind.EXACT, reclaimable = 200, resolvedAt = 5),
                group("burst", DupKind.BURST, reclaimable = 300),
                group("similar", DupKind.SIMILAR, reclaimable = 400),
            ),
            emptyList(),
        )

        val totals = dao.observeOpenTotals().first()

        assertThat(totals.itemCount).isEqualTo(1)
        assertThat(totals.totalBytes).isEqualTo(100)
    }

    @Test
    fun `a new scan replaces the open groups and their members but keeps the resolved ones`() = runTest(dispatcher) {
        dao.replaceAll(
            listOf(group("old", DupKind.EXACT), group("done", DupKind.EXACT)),
            members("old", winner = 1, losers = listOf(2)) + members("done", winner = 3, losers = listOf(4)),
        )
        dao.markResolved("done", now = 9)

        dao.replaceAll(listOf(group("new", DupKind.BURST)), members("new", winner = 5, losers = listOf(6, 7)))

        val open = dao.observeOpenGroups().first()
        assertThat(open.map { it.group.id }).containsExactly("new")
        assertThat(open.single().members.map { it.assetId }).containsExactly(5L, 6L, 7L)
        assertThat(dao.group("old")).isNull()
        assertThat(dao.group("done")?.group?.resolvedAt).isEqualTo(9)
        // The old group's members went with it (foreign key cascade), the resolved one kept its own.
        assertThat(dao.losersOf("old")).isEmpty()
        assertThat(dao.losersOf("done")).containsExactly(4L)
    }

    @Test
    fun `resolving several groups at once closes all of them and nothing else`() = runTest(dispatcher) {
        dao.replaceAll(
            listOf(group("a", DupKind.EXACT), group("b", DupKind.EXACT), group("c", DupKind.EXACT)),
            members("a", 1, listOf(2)) + members("b", 3, listOf(4, 5)) + members("c", 6, listOf(7)),
        )

        dao.markResolvedAll(listOf("a", "b"), now = 42)

        assertThat(dao.observeOpenGroups().first().map { it.group.id }).containsExactly("c")
        assertThat(dao.allOpenLosers()).containsExactly(7L)
        assertThat(dao.group("a")?.group?.resolvedAt).isEqualTo(42)
        assertThat(dao.group("b")?.group?.resolvedAt).isEqualTo(42)
    }

    @Test
    fun `open groups come biggest saving first, each with its members`() = runTest(dispatcher) {
        dao.replaceAll(
            listOf(group("small", DupKind.EXACT, reclaimable = 10), group("big", DupKind.SIMILAR, reclaimable = 99)),
            members("small", 1, listOf(2)) + members("big", 3, listOf(4, 5)),
        )

        val open = dao.observeOpenGroups().first()

        assertThat(open.map { it.group.id }).containsExactly("big", "small").inOrder()
        assertThat(open.first().members.single { it.isWinner }.assetId).isEqualTo(3L)
        assertThat(dao.openGroupsOfKind(DupKind.EXACT).map { it.group.id }).containsExactly("small")
    }

    @Test
    fun `forgetting an asset takes it out of every group`() = runTest(dispatcher) {
        dao.replaceAll(
            listOf(group("a", DupKind.EXACT), group("b", DupKind.BURST)),
            members("a", 1, listOf(2)) + members("b", 3, listOf(2, 4)),
        )

        dao.forgetAssets(listOf(2))

        assertThat(dao.losersOf("a")).isEmpty()
        assertThat(dao.losersOf("b")).containsExactly(4L)
    }

    private fun group(id: String, kind: DupKind, reclaimable: Long = 1_000, resolvedAt: Long? = null) =
        DupGroupEntity(id, kind, representativeAssetId = 0, memberCount = 2, reclaimableBytes = reclaimable, createdAt = 1, resolvedAt = resolvedAt)

    private fun members(groupId: String, winner: Long, losers: List<Long>): List<DupMemberEntity> =
        listOf(DupMemberEntity(groupId, winner, isWinner = true, sharpness = null, distance = 0)) +
            losers.map { DupMemberEntity(groupId, it, isWinner = false, sharpness = null, distance = 0) }
}
