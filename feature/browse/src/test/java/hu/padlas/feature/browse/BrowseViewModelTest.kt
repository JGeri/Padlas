package hu.padlas.feature.browse

import com.google.common.truth.Truth.assertThat
import hu.padlas.core.database.PadlasDatabase
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.testing.FakeMediaOps
import hu.padlas.core.testing.FullDiskAssetDao
import hu.padlas.core.testing.MainDispatcherRule
import hu.padlas.core.testing.asset
import hu.padlas.core.testing.assets
import hu.padlas.core.testing.testDatabase
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
class BrowseViewModelTest {

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
    fun `a big selection goes to the bin in rounds of a thousand`() = runTest(main.dispatcher) {
        val rows = assets(2_300)
        val vm = browse(rows)
        rows.forEach(vm::toggleSelection)

        vm.trashSelected()
        advanceUntilIdle()
        assertThat(vm.state.value.trashRequest).isNotNull()
        repeat(3) { answer(vm, confirmed = true) }

        assertThat(media.rounds).containsExactly(1_000, 1_000, 300).inOrder()
        assertThat(media.requests.map { it.kind }.toSet()).containsExactly(FakeMediaOps.Kind.TRASH)
        assertThat(media.requestedIds).containsNoDuplicates()
        assertThat(db.assetDao().trashedIds()).hasSize(2_300)
        with(vm.state.value) {
            assertThat(selected).isEmpty()
            assertThat(selectedBytes).isEqualTo(0)
            assertThat(trashRequest).isNull()
            assertThat(busy).isFalse()
            assertThat(message).isNotNull()
        }
    }

    @Test
    fun `protected pictures in the selection never reach the dialog`() = runTest(main.dispatcher) {
        val rows = listOf(
            asset(1),
            asset(2, protectedManually = true),
            asset(3, state = AssetState.PROTECTED),
            asset(4),
        )
        val vm = browse(rows)
        rows.forEach(vm::toggleSelection)

        vm.trashSelected()
        advanceUntilIdle()

        assertThat(media.requests.single().ids).containsExactly(1L, 4L)
    }

    @Test
    fun `a selection of protected pictures only asks for nothing`() = runTest(main.dispatcher) {
        val rows = listOf(asset(1, protectedManually = true), asset(2, state = AssetState.PROTECTED))
        val vm = browse(rows)
        rows.forEach(vm::toggleSelection)

        vm.trashSelected()
        advanceUntilIdle()

        assertThat(media.requests).isEmpty()
        assertThat(vm.state.value.busy).isFalse()
        assertThat(vm.state.value.message).isNotNull()
    }

    @Test
    fun `a cancelled dialog bins nothing and keeps the selection`() = runTest(main.dispatcher) {
        val rows = assets(1_200)
        val vm = browse(rows)
        rows.forEach(vm::toggleSelection)

        vm.trashSelected()
        advanceUntilIdle()
        answer(vm, confirmed = false)

        assertThat(media.rounds).containsExactly(1_000)
        assertThat(db.assetDao().trashedIds()).isEmpty()
        assertThat(vm.state.value.selected).hasSize(1_200)
        assertThat(vm.state.value.busy).isFalse()
    }

    @Test
    fun `a failed write after the dialog does not crash and never asks for a file twice`() = runTest(main.dispatcher) {
        val rows = assets(1_200)
        val vm = browse(rows, FullDiskAssetDao(db.assetDao()))
        rows.forEach(vm::toggleSelection)

        vm.trashSelected()
        advanceUntilIdle()
        answer(vm, confirmed = true)
        answer(vm, confirmed = true)

        assertThat(media.rounds).containsExactly(1_000, 200).inOrder()
        assertThat(media.requestedIds).containsNoDuplicates()
        assertThat(vm.state.value.busy).isFalse()
        assertThat(vm.state.value.message).isNotNull()
    }

    private suspend fun TestScope.browse(rows: List<Asset>, assetDao: AssetDao = db.assetDao()): BrowseViewModel {
        db.assetDao().upsertAll(rows)
        val vm = BrowseViewModel(assetDao, db.uploadJobDao(), db.auditDao(), media, main.dispatcher)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        advanceUntilIdle()
        return vm
    }

    private fun TestScope.answer(vm: BrowseViewModel, confirmed: Boolean) {
        vm.onTrashResult(confirmed)
        advanceUntilIdle()
    }
}
