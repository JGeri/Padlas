package hu.padlas.feature.browse

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import hu.padlas.core.database.PadlasDatabase
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.media.OneDriveGuard
import hu.padlas.core.testing.FakeMediaOps
import hu.padlas.core.testing.FullDiskAssetDao
import hu.padlas.core.testing.MainDispatcherRule
import hu.padlas.core.testing.asset
import hu.padlas.core.testing.testDatabase
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
class TrashViewModelTest {

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
    fun `emptying a big bin goes in rounds of a thousand and drops the rows`() = runTest(main.dispatcher) {
        val vm = trash(binned = 2_300)

        vm.emptyAll()
        advanceUntilIdle()
        repeat(3) { systemAnswers(vm, confirmed = true) }

        assertThat(media.rounds).containsExactly(1_000, 1_000, 300).inOrder()
        assertThat(media.requests.map { it.kind }.toSet()).containsExactly(FakeMediaOps.Kind.DELETE)
        assertThat(media.requestedIds).containsNoDuplicates()
        assertThat(db.assetDao().count()).isEqualTo(0)
        assertThat(db.auditDao().all().count { it.action == AuditAction.DELETED }).isEqualTo(2_300)
        with(vm.state.value) {
            assertThat(items).isEmpty()
            assertThat(busy).isFalse()
            assertThat(message).isNotNull()
        }
    }

    @Test
    fun `restoring a selection brings exactly those rows back, round by round`() = runTest(main.dispatcher) {
        val vm = trash(binned = 2_000)
        (1L..1_500L).forEach(vm::toggle)

        vm.restoreSelected()
        advanceUntilIdle()
        repeat(2) { systemAnswers(vm, confirmed = true) }

        assertThat(media.rounds).containsExactly(1_000, 500).inOrder()
        assertThat(media.requests.map { it.kind }.toSet()).containsExactly(FakeMediaOps.Kind.UNTRASH)
        assertThat(media.requestedIds.toSet()).isEqualTo((1L..1_500L).toSet())
        assertThat(db.assetDao().trashedIds()).containsExactlyElementsIn(1_501L..2_000L)
        assertThat(db.assetDao().byId(1)?.state).isEqualTo(AssetState.LOCAL)
        assertThat(db.auditDao().all().count { it.action == AuditAction.UNTRASHED }).isEqualTo(1_500)
        assertThat(vm.state.value.selected).isEmpty()
        assertThat(vm.state.value.items).hasSize(500)
    }

    @Test
    fun `a cancelled dialog stops the rounds and changes nothing`() = runTest(main.dispatcher) {
        val vm = trash(binned = 2_300)

        vm.emptyAll()
        advanceUntilIdle()
        vm.onResult(confirmed = false)
        advanceUntilIdle()

        assertThat(media.rounds).containsExactly(1_000)
        assertThat(db.assetDao().trashedIds()).hasSize(2_300)
        assertThat(vm.state.value.busy).isFalse()
    }

    @Test
    fun `a refused request frees the screen`() = runTest(main.dispatcher) {
        val vm = trash(binned = 3)
        media.refusing = true

        vm.emptyAll()
        advanceUntilIdle()

        assertThat(vm.state.value.busy).isFalse()
        assertThat(vm.state.value.message).isNotNull()
        assertThat(db.assetDao().trashedIds()).hasSize(3)
    }

    @Test
    fun `a failed write after the dialog does not crash and never asks for a file twice`() = runTest(main.dispatcher) {
        val vm = trash(binned = 1_200, assetDao = { FullDiskAssetDao(it) })

        vm.emptyAll()
        advanceUntilIdle()
        repeat(2) { systemAnswers(vm, confirmed = true) }

        assertThat(media.rounds).containsExactly(1_000, 200).inOrder()
        assertThat(media.requestedIds).containsNoDuplicates()
        assertThat(vm.state.value.busy).isFalse()
        assertThat(vm.state.value.message).isNotNull()
    }

    private suspend fun TestScope.trash(binned: Int, assetDao: (AssetDao) -> AssetDao = { it }): TrashViewModel {
        val ids = 1L..binned
        db.assetDao().upsertAll(ids.map { asset(it, state = AssetState.TRASHED, trashedAt = 1) })
        media.putInBin(ids)
        val guard = OneDriveGuard(ApplicationProvider.getApplicationContext(), main.dispatcher)
        return TrashViewModel(media, assetDao(db.assetDao()), db.auditDao(), guard, main.dispatcher)
            .also { advanceUntilIdle() }
    }

    /** The user confirms and MediaStore does what the dialog said. */
    private fun TestScope.systemAnswers(vm: TrashViewModel, confirmed: Boolean) {
        if (confirmed) media.carryOut()
        vm.onResult(confirmed)
        advanceUntilIdle()
    }
}
