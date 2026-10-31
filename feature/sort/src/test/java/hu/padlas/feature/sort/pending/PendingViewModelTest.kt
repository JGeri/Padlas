package hu.padlas.feature.sort.pending

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import hu.padlas.core.database.PadlasDatabase
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.entity.SwipeChoice
import hu.padlas.core.database.entity.SwipeDecisionEntity
import hu.padlas.core.testing.FakeMediaOps
import hu.padlas.core.testing.FullDiskAssetDao
import hu.padlas.core.testing.MainDispatcherRule
import hu.padlas.core.testing.assets
import hu.padlas.core.testing.testDatabase
import hu.padlas.feature.sort.SwipePreferences
import hu.padlas.feature.sort.SwipeRepository
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PendingViewModelTest {

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

    @Test
    fun `the whole marked grid goes in rounds of a thousand and empties`() = runTest(main.dispatcher) {
        markForDeletion(2_300)
        val vm = pending()
        assertThat(vm.state.value.marked).hasSize(2_300)

        vm.delete()
        advanceUntilIdle()
        repeat(3) { answer(vm, confirmed = true) }

        assertThat(media.rounds).containsExactly(1_000, 1_000, 300).inOrder()
        assertThat(media.requestedIds).containsNoDuplicates()
        assertThat(db.assetDao().trashedIds()).hasSize(2_300)
        with(vm.state.value) {
            assertThat(items).isEmpty()
            assertThat(done).isTrue()
            assertThat(busy).isFalse()
        }
    }

    @Test
    fun `a picture tapped back to keep stays, and is remembered as kept`() = runTest(main.dispatcher) {
        markForDeletion(5)
        val vm = pending()
        val spared = vm.state.value.items.first { it.asset.mediaStoreId == 3L }

        vm.toggle(spared)
        advanceUntilIdle()
        vm.delete()
        advanceUntilIdle()
        answer(vm, confirmed = true)

        assertThat(media.requests.single().ids).containsExactly(1L, 2L, 4L, 5L)
        assertThat(db.swipeDao().byAsset(3)?.choice).isEqualTo(SwipeChoice.KEEP)
        assertThat(vm.state.value.items.map { it.asset.mediaStoreId }).containsExactly(3L)
        assertThat(vm.state.value.done).isTrue()
    }

    @Test
    fun `a cancelled dialog keeps every marked picture on the grid`() = runTest(main.dispatcher) {
        markForDeletion(2_300)
        val vm = pending()

        vm.delete()
        advanceUntilIdle()
        answer(vm, confirmed = false)

        assertThat(media.rounds).containsExactly(1_000)
        assertThat(vm.state.value.marked).hasSize(2_300)
        assertThat(vm.state.value.busy).isFalse()
        assertThat(vm.state.value.message).isNotNull()
        assertThat(db.assetDao().trashedIds()).isEmpty()
    }

    @Test
    fun `a refused request frees the button`() = runTest(main.dispatcher) {
        markForDeletion(3)
        media.refusing = true
        val vm = pending()

        vm.delete()
        advanceUntilIdle()

        assertThat(vm.state.value.busy).isFalse()
        assertThat(vm.state.value.message).isNotNull()
        assertThat(vm.state.value.marked).hasSize(3)
    }

    @Test
    fun `a failed write after the dialog does not crash and never asks for a file twice`() = runTest(main.dispatcher) {
        markForDeletion(1_500)
        val vm = pending(FullDiskAssetDao(db.assetDao()))

        vm.delete()
        advanceUntilIdle()
        answer(vm, confirmed = true)
        // The grid is a snapshot, so what is left is still exactly what nobody asked for yet.
        answer(vm, confirmed = true)

        assertThat(media.rounds).containsExactly(1_000, 500).inOrder()
        assertThat(media.requestedIds).containsNoDuplicates()
        assertThat(vm.state.value.busy).isFalse()
        assertThat(vm.state.value.message).isNotNull()
    }

    private fun TestScope.pending(assetDao: AssetDao = db.assetDao()): PendingViewModel {
        val preferences = SwipePreferences(ApplicationProvider.getApplicationContext())
        val repository = SwipeRepository(db.swipeDao(), assetDao, db.auditDao(), media, preferences, main.dispatcher)
        return PendingViewModel(repository, media).also { advanceUntilIdle() }
    }

    private fun TestScope.answer(vm: PendingViewModel, confirmed: Boolean) {
        vm.onResult(confirmed)
        advanceUntilIdle()
    }

    private suspend fun markForDeletion(count: Int) {
        db.assetDao().upsertAll(assets(count))
        db.swipeDao().upsertAll((1L..count).map { SwipeDecisionEntity(it, SwipeChoice.DELETE, decidedAt = count - it) })
    }
}
