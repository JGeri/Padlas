package hu.padlas.feature.dedup.ui

import android.content.Context
import android.content.IntentSender
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import hu.padlas.core.database.PadlasDatabase
import hu.padlas.core.database.dao.DupDao
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.entity.DupGroupEntity
import hu.padlas.core.database.entity.DupKind
import hu.padlas.core.database.entity.DupMemberEntity
import hu.padlas.core.media.Hasher
import hu.padlas.core.testing.FakeMediaOps
import hu.padlas.core.testing.MainDispatcherRule
import hu.padlas.core.testing.asset
import hu.padlas.core.testing.testDatabase
import hu.padlas.feature.dedup.hash.HashPipeline
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A burst or a "similar" group can hold the one frame where somebody turned their head.
 * Only byte-identical copies are ever binned without the user pointing at them.
 */
@RunWith(RobolectricTestRunner::class)
class DedupViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private lateinit var db: PadlasDatabase
    private lateinit var media: FakeMediaOps

    @Before
    fun setUp() {
        db = testDatabase(main.dispatcher)
        media = FakeMediaOps()
    }

    @After
    fun tearDown() = db.close()

    // ---- review groups ------------------------------------------------------

    @Test
    fun `only exact copies come marked, bursts and similar shots wait for a human`() = runTest(main.dispatcher) {
        val screen = screen(mixedLibrary(), favourites = setOf(13))

        with(screen.vm.state.value) {
            assertThat(exact.loserCount).isEqualTo(2)
            assertThat(burst.groups).hasSize(1)
            assertThat(similar.groups).hasSize(1)
            assertThat(burst.loserCount).isEqualTo(0)
            assertThat(similar.loserCount).isEqualTo(0)
        }
    }

    @Test
    fun `binning every group never takes an unmarked burst or similar frame`() = runTest(main.dispatcher) {
        val screen = screen(mixedLibrary(), favourites = setOf(13))

        screen.vm.requestTrash(screen.vm.state.value.allGroupIds)
        advanceUntilIdle()
        assertThat(media.requests.single().ids).containsExactly(2L, 3L)

        screen.answer(accepted = true)

        assertThat(db.assetDao().trashedIds()).containsExactly(2L, 3L)
        assertThat(db.dupDao().group(EXACT)?.group?.resolvedAt).isNotNull()
        assertThat(db.dupDao().group(BURST)?.group?.resolvedAt).isNull()
        assertThat(db.dupDao().group(SIMILAR)?.group?.resolvedAt).isNull()
        assertThat(media.requests).hasSize(1)
    }

    @Test
    fun `a frame the user marked goes along, and only that one`() = runTest(main.dispatcher) {
        val screen = screen(mixedLibrary(), favourites = setOf(13))

        screen.vm.toggleMark(BURST, 11)
        screen.vm.toggleMark(SIMILAR, 22)
        screen.vm.toggleMark(SIMILAR, 22)
        advanceUntilIdle()
        screen.vm.requestTrash(screen.vm.state.value.allGroupIds)
        advanceUntilIdle()

        assertThat(media.requests.single().ids).containsExactly(2L, 3L, 11L)
    }

    @Test
    fun `keeping only the suggested frame marks the rest, but never a protected one`() = runTest(main.dispatcher) {
        val screen = screen(mixedLibrary(), favourites = setOf(13))

        screen.vm.keepOnlySuggested(BURST)
        advanceUntilIdle()
        screen.vm.requestTrash(listOf(BURST))
        advanceUntilIdle()

        // 10 is the suggested keeper, 13 is a favourite.
        assertThat(media.requests.single().ids).containsExactly(11L, 12L)
    }

    @Test
    fun `a protected picture that was marked still never reaches the dialog`() = runTest(main.dispatcher) {
        val screen = screen(mixedLibrary(), favourites = setOf(13))

        screen.vm.toggleMark(BURST, 13)
        advanceUntilIdle()
        screen.vm.requestTrash(listOf(BURST))
        advanceUntilIdle()

        assertThat(media.requests).isEmpty()
        assertThat(screen.vm.state.value.pendingGroupIds).isEmpty()
    }

    @Test
    fun `marks are frozen while their group waits behind the dialog`() = runTest(main.dispatcher) {
        val screen = screen(mixedLibrary(), favourites = setOf(13))
        screen.vm.toggleMark(BURST, 11)
        advanceUntilIdle()

        screen.vm.requestTrash(listOf(BURST))
        advanceUntilIdle()
        screen.vm.toggleMark(BURST, 12)
        screen.vm.keepOnlySuggested(BURST)
        advanceUntilIdle()

        assertThat(screen.vm.state.value.pendingGroupIds).containsExactly(BURST)
        assertThat(screen.vm.state.value.burst.groups.single().marked).containsExactly(11L)
    }

    @Test
    fun `a cancelled dialog bins nothing and keeps the marks`() = runTest(main.dispatcher) {
        val screen = screen(mixedLibrary(), favourites = setOf(13))
        screen.vm.toggleMark(BURST, 11)
        advanceUntilIdle()

        screen.vm.requestTrash(screen.vm.state.value.allGroupIds)
        advanceUntilIdle()
        screen.answer(accepted = false)

        assertThat(db.assetDao().trashedIds()).isEmpty()
        assertThat(db.dupDao().openGroupIds()).containsExactly(EXACT, BURST, SIMILAR)
        with(screen.vm.state.value) {
            assertThat(pendingGroupIds).isEmpty()
            assertThat(burst.groups.single().marked).containsExactly(11L)
        }
    }

    // ---- rounds -------------------------------------------------------------

    @Test
    fun `a group bigger than one dialog is cut over rounds and closed by the last one`() = runTest(main.dispatcher) {
        val screen = screen(listOf(Group(EXACT, DupKind.EXACT, winner = 1, others = (2L..2_501L).toList())))

        screen.vm.requestTrash(listOf(EXACT))
        advanceUntilIdle()
        assertThat(screen.vm.state.value.pendingGroupIds).containsExactly(EXACT)
        screen.answer(accepted = true)
        assertThat(db.dupDao().group(EXACT)?.group?.resolvedAt).isNull()
        screen.answer(accepted = true)
        screen.answer(accepted = true)

        assertThat(media.rounds).containsExactly(1_000, 1_000, 500).inOrder()
        assertThat(media.requestedIds).containsNoDuplicates()
        assertThat(media.requestedIds).doesNotContain(1L)
        assertThat(db.assetDao().trashedIds()).hasSize(2_500)
        assertThat(db.assetDao().byId(1)?.state).isEqualTo(AssetState.LOCAL)
        assertThat(db.dupDao().group(EXACT)?.group?.resolvedAt).isNotNull()
        assertThat(screen.requests).hasSize(3)
    }

    @Test
    fun `groups that do not fit one dialog together wait for the next round whole`() = runTest(main.dispatcher) {
        val screen = screen(
            listOf(
                Group("a", DupKind.EXACT, winner = 1, others = (2L..601L).toList()),
                Group("b", DupKind.EXACT, winner = 1_001, others = (1_002L..1_601L).toList()),
            ),
        )

        screen.vm.requestTrash(screen.vm.state.value.allGroupIds)
        advanceUntilIdle()
        screen.answer(accepted = true)
        screen.answer(accepted = true)

        assertThat(media.rounds).containsExactly(600, 600)
        assertThat(media.requests.map { request -> request.ids.map { it / 1_000 }.toSet() })
            .containsExactly(setOf(0L), setOf(1L))
        assertThat(db.dupDao().openGroupIds()).isEmpty()
    }

    // ---- fixtures -----------------------------------------------------------

    private data class Group(val id: String, val kind: DupKind, val winner: Long, val others: List<Long>)

    private class Screen(val vm: DedupViewModel, val requests: List<IntentSender>, private val scope: TestScope) {
        fun answer(accepted: Boolean) {
            vm.onTrashResult(accepted)
            scope.advanceUntilIdle()
        }
    }

    /** One exact group, one burst with a favourite in it, one similar group. */
    private fun mixedLibrary() = listOf(
        Group(EXACT, DupKind.EXACT, winner = 1, others = listOf(2, 3)),
        Group(BURST, DupKind.BURST, winner = 10, others = listOf(11, 12, 13)),
        Group(SIMILAR, DupKind.SIMILAR, winner = 20, others = listOf(21, 22)),
    )

    private suspend fun TestScope.screen(groups: List<Group>, favourites: Set<Long> = emptySet()): Screen {
        val ids = groups.flatMap { listOf(it.winner) + it.others }
        db.assetDao().upsertAll(ids.map { id -> asset(id, isFavorite = id in favourites) })
        db.dupDao().replaceAll(
            groups.map { DupGroupEntity(it.id, it.kind, it.winner, it.others.size + 1, it.others.size * 1_000L, createdAt = 1) },
            groups.flatMap { group ->
                listOf(DupMemberEntity(group.id, group.winner, isWinner = true, sharpness = 1f, distance = 0)) +
                    group.others.map { DupMemberEntity(group.id, it, isWinner = false, sharpness = 0.5f, distance = 3) }
            },
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pipeline = HashPipeline(db.assetDao(), Hasher(context, main.dispatcher), main.dispatcher)
        val vm = DedupViewModel(context, db.assetDao(), db.dupDao(), db.auditDao(), media, main.dispatcher, pipeline)
        val requests = mutableListOf<IntentSender>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.trashRequests.collect { requests += it } }
        advanceUntilIdle()
        return Screen(vm, requests, this)
    }

    private suspend fun DupDao.openGroupIds(): List<String> =
        openGroupsOfKind(DupKind.EXACT).map { it.group.id } +
            openGroupsOfKind(DupKind.BURST).map { it.group.id } +
            openGroupsOfKind(DupKind.SIMILAR).map { it.group.id }

    private companion object {
        const val EXACT = "exact"
        const val BURST = "burst"
        const val SIMILAR = "similar"
    }
}
