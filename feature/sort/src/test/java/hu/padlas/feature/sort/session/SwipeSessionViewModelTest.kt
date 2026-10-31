package hu.padlas.feature.sort.session

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import hu.padlas.core.database.PadlasDatabase
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.database.entity.SwipeChoice
import hu.padlas.core.database.entity.SwipeDecisionEntity
import hu.padlas.core.testing.FakeMediaOps
import hu.padlas.core.testing.FullDiskAssetDao
import hu.padlas.core.testing.MainDispatcherRule
import hu.padlas.core.testing.assets
import hu.padlas.core.testing.testDatabase
import hu.padlas.feature.sort.DeleteMode
import hu.padlas.feature.sort.SwipeAction
import hu.padlas.feature.sort.SwipePreferences
import hu.padlas.feature.sort.SwipeRepository
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

@RunWith(RobolectricTestRunner::class)
class SwipeSessionViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private lateinit var db: PadlasDatabase
    private lateinit var media: FakeMediaOps
    private lateinit var preferences: SwipePreferences

    @Before
    fun setUp() {
        db = testDatabase(main.dispatcher)
        media = FakeMediaOps()
        preferences = SwipePreferences(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `more than one dialog's worth goes in rounds of a thousand until the pile is empty`() = runTest(main.dispatcher) {
        markForDeletion(2_300)
        val session = session()

        session.vm.deleteNow()
        advanceUntilIdle()
        assertThat(media.rounds).containsExactly(1_000)
        assertThat(session.vm.state.value.busy).isTrue()

        session.answer(confirmed = true)
        assertThat(media.rounds).containsExactly(1_000, 1_000).inOrder()
        session.answer(confirmed = true)
        assertThat(media.rounds).containsExactly(1_000, 1_000, 300).inOrder()
        session.answer(confirmed = true)

        assertThat(media.rounds).hasSize(3)
        assertThat(session.launches).hasSize(3)
        assertThat(media.requests.map { it.kind }.toSet()).containsExactly(FakeMediaOps.Kind.TRASH)
        assertThat(media.requestedIds).containsNoDuplicates()
        assertThat(media.requestedIds).hasSize(2_300)
        assertThat(db.assetDao().trashedIds()).hasSize(2_300)
        assertThat(db.swipeDao().pendingAssets()).isEmpty()
        assertThat(db.auditDao().all().count { it.action == AuditAction.TRASHED && it.reversible }).isEqualTo(2_300)
        with(session.vm.state.value) {
            assertThat(busy).isFalse()
            assertThat(freedBytes).isEqualTo(2_300 * 1_000L)
            assertThat(pending.itemCount).isEqualTo(0)
            assertThat(message).isNotNull()
        }
    }

    @Test
    fun `the permanent mode asks for deletion and drops the rows instead of binning them`() = runTest(main.dispatcher) {
        preferences.setDeleteMode(DeleteMode.PERMANENT)
        markForDeletion(3)
        val session = session()

        session.vm.deleteNow()
        advanceUntilIdle()
        session.answer(confirmed = true)

        assertThat(media.requests.single().kind).isEqualTo(FakeMediaOps.Kind.DELETE)
        assertThat(db.assetDao().byIds(listOf(1, 2, 3))).isEmpty()
        assertThat(db.auditDao().all().filter { it.action == AuditAction.DELETED }.map { it.reversible })
            .containsExactly(false, false, false)
        assertThat(session.vm.state.value.freedPermanently).isTrue()
    }

    @Test
    fun `a cancelled dialog stops the rounds and keeps every mark`() = runTest(main.dispatcher) {
        markForDeletion(2_300)
        val session = session()

        session.vm.deleteNow()
        advanceUntilIdle()
        session.answer(confirmed = false)

        assertThat(media.rounds).containsExactly(1_000)
        assertThat(db.assetDao().trashedIds()).isEmpty()
        assertThat(db.swipeDao().pendingAssets()).hasSize(2_300)
        assertThat(session.vm.state.value.busy).isFalse()
        assertThat(session.vm.state.value.message).isNotNull()

        // Nothing is stuck: the next tap starts over.
        session.vm.deleteNow()
        advanceUntilIdle()
        assertThat(media.rounds).containsExactly(1_000, 1_000)
    }

    @Test
    fun `a request the system refuses frees the screen and keeps the marks`() = runTest(main.dispatcher) {
        markForDeletion(10)
        media.refusing = true
        val session = session()

        session.vm.deleteNow()
        advanceUntilIdle()

        assertThat(session.launches).isEmpty()
        assertThat(session.vm.state.value.busy).isFalse()
        assertThat(session.vm.state.value.message).isNotNull()
        assertThat(db.swipeDao().pendingAssets()).hasSize(10)
    }

    @Test
    fun `a write that fails after the dialog neither crashes nor asks for the same files again`() = runTest(main.dispatcher) {
        markForDeletion(2_300)
        val session = session(assetDao = FullDiskAssetDao(db.assetDao()))

        session.vm.deleteNow()
        advanceUntilIdle()
        session.answer(confirmed = true)

        // The decisions are still in the database, so another round would ask for the very
        // files the system just binned: it must stop here.
        assertThat(media.rounds).containsExactly(1_000)
        assertThat(session.vm.state.value.busy).isFalse()
        assertThat(session.vm.state.value.message).isNotNull()
    }

    @Test
    fun `a second tap while the dialog is open does not open another`() = runTest(main.dispatcher) {
        markForDeletion(5)
        val session = session()

        session.vm.deleteNow()
        advanceUntilIdle()
        session.vm.deleteNow()
        advanceUntilIdle()

        assertThat(media.rounds).containsExactly(5)
        assertThat(session.launches).hasSize(1)
    }

    @Test
    fun `swiping the last card opens one dialog for everything marked left`() = runTest(main.dispatcher) {
        db.assetDao().upsertAll(assets(3))
        val session = session()
        val actions = listOf(SwipeAction.DELETE, SwipeAction.KEEP, SwipeAction.DELETE)
        val decided = actions.map { action ->
            val card = checkNotNull(session.vm.state.value.current)
            session.vm.onSwiped(card, action)
            advanceUntilIdle()
            card.id to action
        }

        val marked = decided.filter { it.second == SwipeAction.DELETE }.map { it.first }
        val kept = decided.single { it.second == SwipeAction.KEEP }.first
        assertThat(media.requests.single().ids).containsExactlyElementsIn(marked)
        assertThat(db.swipeDao().byAsset(kept)?.choice).isEqualTo(SwipeChoice.KEEP)
        assertThat(session.vm.state.value.deleted).isEqualTo(2)
    }

    @Test
    fun `undo puts back what was there before the swipe`() = runTest(main.dispatcher) {
        db.assetDao().upsertAll(assets(2))
        val session = session()
        val card = checkNotNull(session.vm.state.value.current)

        session.vm.onSwiped(card, SwipeAction.DELETE)
        advanceUntilIdle()
        assertThat(db.swipeDao().byAsset(card.id)?.choice).isEqualTo(SwipeChoice.DELETE)

        session.vm.undo()
        advanceUntilIdle()

        assertThat(db.swipeDao().byAsset(card.id)).isNull()
        assertThat(session.vm.state.value.index).isEqualTo(0)
        assertThat(session.vm.state.value.deleted).isEqualTo(0)
    }

    @Test
    fun `binned cards leave the deck, so undo can never land on a file that is gone`() = runTest(main.dispatcher) {
        db.assetDao().upsertAll(assets(3))
        val session = session()
        repeat(2) {
            session.vm.onSwiped(checkNotNull(session.vm.state.value.current), SwipeAction.DELETE)
            advanceUntilIdle()
        }

        session.vm.deleteNow()
        advanceUntilIdle()
        session.answer(confirmed = true)

        with(session.vm.state.value) {
            assertThat(cards).hasSize(1)
            assertThat(index).isEqualTo(0)
            assertThat(removed).isEqualTo(2)
            assertThat(position).isEqualTo(2)
            assertThat(total).isEqualTo(3)
            assertThat(canUndo).isFalse()
        }
    }

    private class Session(val vm: SwipeSessionViewModel, val launches: List<SessionEvent>, private val scope: TestScope) {
        fun answer(confirmed: Boolean) {
            vm.onRequestResult(RequestKind.DELETE, confirmed)
            scope.advanceUntilIdle()
        }
    }

    private fun TestScope.session(assetDao: AssetDao = db.assetDao()): Session {
        val repository = SwipeRepository(db.swipeDao(), assetDao, db.auditDao(), media, preferences, main.dispatcher)
        val vm = SwipeSessionViewModel(repository, preferences)
        val launches = mutableListOf<SessionEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { launches += it } }
        advanceUntilIdle()
        return Session(vm, launches, this)
    }

    private suspend fun markForDeletion(count: Int) {
        db.assetDao().upsertAll(assets(count))
        db.swipeDao().upsertAll((1L..count).map { SwipeDecisionEntity(it, SwipeChoice.DELETE, decidedAt = it) })
    }
}
