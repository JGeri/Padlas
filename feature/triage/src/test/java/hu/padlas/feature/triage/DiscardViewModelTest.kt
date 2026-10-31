package hu.padlas.feature.triage

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import hu.padlas.core.database.PadlasDatabase
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.database.entity.ShotKind
import hu.padlas.core.database.entity.ValueClass
import hu.padlas.core.media.OneDriveGuard
import hu.padlas.core.media.ThumbnailStore
import hu.padlas.core.testing.FakeMediaOps
import hu.padlas.core.testing.FullDiskAssetDao
import hu.padlas.core.testing.MainDispatcherRule
import hu.padlas.core.testing.asset
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
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class DiscardViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private lateinit var db: PadlasDatabase
    private lateinit var media: FakeMediaOps
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        db = testDatabase(main.dispatcher)
        media = FakeMediaOps()
    }

    @After
    fun tearDown() = db.close()

    // ---- what starts marked -------------------------------------------------

    @Test
    fun `navigation, home, game and recognised shots start marked, social, media and unread ones start kept`() =
        runTest(main.dispatcher) {
            installGame("Pottery 2")
            val screen = screen(mixedShots())

            screen.vm.requestDelete()
            advanceUntilIdle()

            assertThat(media.requests.single().ids).containsExactly(WAZE, HOME, GAME, RECOGNISED, CHAT)
            assertThat(screen.vm.selection.value.kept).containsAtLeast(INSTAGRAM, YOUTUBE, UNREAD)
        }

    @Test
    fun `shots are grouped by the app they came from, and secret beats the app`() = runTest(main.dispatcher) {
        installGame("Pottery 2")
        val screen = screen(mixedShots())

        val groups = screen.vm.state.value.groups
        fun groupOf(id: Long) = groups.single { group -> group.items.any { it.id == id } }

        assertThat(groupOf(WAZE).source).isEqualTo(ShotSource.NAVIGATION)
        assertThat(groupOf(HOME).source).isEqualTo(ShotSource.HOME)
        assertThat(groupOf(GAME).source).isEqualTo(ShotSource.GAME)
        assertThat(groupOf(INSTAGRAM).source).isEqualTo(ShotSource.SOCIAL)
        assertThat(groupOf(YOUTUBE).source).isEqualTo(ShotSource.MEDIA)
        assertThat(groupOf(CHAT).kind).isEqualTo(ShotKind.CHAT)
        assertThat(groupOf(UNREAD).kind).isNull()
        assertThat(groupOf(UNREAD).source).isNull()
        // Named after Waze, read as a password: it stays in the locked group.
        assertThat(groupOf(SECRET).kind).isEqualTo(ShotKind.SECRET)
        assertThat(groupOf(SECRET).locked).isTrue()
        assertThat(groupOf(SECRET).items.single().locked).isTrue()
    }

    @Test
    fun `a secret shot stays out of the dialog whatever is tapped`() = runTest(main.dispatcher) {
        val screen = screen(mixedShots())

        screen.vm.toggleKeep(SECRET)
        screen.vm.toggleKeep(SECRET)
        screen.vm.discardAll(groupKey(ShotKind.SECRET))
        screen.vm.groups().forEach { screen.vm.discardAll(it.key) }
        advanceUntilIdle()
        screen.vm.requestDelete()
        advanceUntilIdle()

        assertThat(media.requestedIds).doesNotContain(SECRET)
        assertThat(media.requestedIds).containsAtLeast(INSTAGRAM, YOUTUBE, UNREAD)
    }

    // ---- rounds -------------------------------------------------------------

    @Test
    fun `a big set goes in rounds of a thousand and leaves the index empty`() = runTest(main.dispatcher) {
        val screen = screen(carded(2_300))

        screen.vm.requestDelete()
        advanceUntilIdle()
        repeat(3) { screen.answer(confirmed = true) }

        assertThat(media.rounds).containsExactly(1_000, 1_000, 300).inOrder()
        assertThat(media.requests.map { it.kind }.toSet()).containsExactly(FakeMediaOps.Kind.DELETE)
        assertThat(media.requestedIds).containsNoDuplicates()
        assertThat(screen.confirms).hasSize(3)
        assertThat(db.assetDao().count()).isEqualTo(0)
        assertThat(db.auditDao().all().count { it.action == AuditAction.DELETED && !it.reversible }).isEqualTo(2_300)
        with(screen.vm.state.value) {
            assertThat(committing).isFalse()
            assertThat(falling).isTrue()
            assertThat(message).isNotNull()
        }
    }

    @Test
    fun `a cancelled dialog deletes nothing and stops`() = runTest(main.dispatcher) {
        val screen = screen(carded(1_500))

        screen.vm.requestDelete()
        advanceUntilIdle()
        screen.answer(confirmed = false)

        assertThat(media.rounds).containsExactly(1_000)
        assertThat(db.assetDao().count()).isEqualTo(1_500)
        assertThat(screen.vm.state.value.committing).isFalse()
        assertThat(screen.vm.state.value.falling).isFalse()
        assertThat(screen.vm.state.value.message).isNotNull()
    }

    @Test
    fun `a refused request frees the screen`() = runTest(main.dispatcher) {
        media.refusing = true
        val screen = screen((1L..5L).map { shot(it, kind = ShotKind.OTHER) })

        screen.vm.requestDelete()
        advanceUntilIdle()

        assertThat(screen.confirms).isEmpty()
        assertThat(screen.vm.state.value.committing).isFalse()
        assertThat(screen.vm.state.value.message).isNotNull()
    }

    @Test
    fun `a failed write after the dialog does not crash and does not start another round`() = runTest(main.dispatcher) {
        val screen = screen(carded(1_500), committerDao = { FullDiskAssetDao(it) })

        screen.vm.requestDelete()
        advanceUntilIdle()
        screen.answer(confirmed = true)

        // The rows are still in the index, so a next round could only ask for the same files.
        assertThat(media.rounds).containsExactly(1_000)
        assertThat(screen.vm.state.value.committing).isFalse()
        assertThat(screen.vm.state.value.falling).isFalse()
        assertThat(screen.vm.state.value.message).isNotNull()
    }

    // ---- fixtures -----------------------------------------------------------

    private class Screen(val vm: DiscardViewModel, val confirms: List<DiscardEvent>, private val scope: TestScope) {
        fun answer(confirmed: Boolean) {
            vm.onDeleteResult(confirmed)
            scope.advanceUntilIdle()
        }
    }

    private fun DiscardViewModel.groups(): List<TriageGroup> = state.value.groups

    private fun mixedShots(): List<Asset> = listOf(
        shot(WAZE, app = "Waze"),
        shot(HOME, app = "One UI Home"),
        shot(GAME, app = "Pottery 2"),
        shot(INSTAGRAM, app = "Instagram", kind = ShotKind.OTHER),
        shot(YOUTUBE, app = "YouTube"),
        shot(UNREAD),
        shot(RECOGNISED, kind = ShotKind.OTHER),
        shot(CHAT, app = "Messenger", kind = ShotKind.CHAT),
        shot(SECRET, app = "Waze", kind = ShotKind.SECRET).copy(protectedManually = true, state = AssetState.PROTECTED),
    )

    /**
     * Recognised throwaway shots whose cards already exist. Writing a thousand cards is the
     * committer's test; here each card write would only re-run the grouping under the screen.
     */
    private fun carded(count: Int): List<Asset> =
        (1L..count).map { shot(it, kind = ShotKind.OTHER).copy(cardId = "card-$it") }

    private fun shot(id: Long, app: String? = null, kind: ShotKind? = null): Asset = asset(
        id,
        displayName = "Screenshot_20240715_12${"%04d".format(id % 10_000)}" + (app?.let { "_$it" } ?: "") + ".jpg",
        relativePath = "Pictures/Screenshots/",
        bucketName = "Screenshots",
        valueClass = ValueClass.EPHEMERAL,
        shotKind = kind,
    )

    private fun installGame(label: String) {
        val info = ApplicationInfo().apply {
            packageName = "com.example.game"
            category = ApplicationInfo.CATEGORY_GAME
            nonLocalizedLabel = label
        }
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                packageName = info.packageName
                applicationInfo = info
            },
        )
    }

    private suspend fun TestScope.screen(shots: List<Asset>, committerDao: (AssetDao) -> AssetDao = { it }): Screen {
        db.assetDao().upsertAll(shots)
        val cards = CardWriter(db.assetDao(), db.cardDao(), db.auditDao(), ThumbnailStore(context, main.dispatcher), main.dispatcher)
        val committer = DiscardCommitter(committerDao(db.assetDao()), db.auditDao(), cards, media, main.dispatcher)
        val vm = DiscardViewModel(
            db.assetDao(),
            committer,
            media,
            OneDriveGuard(context, main.dispatcher),
            InstalledApps(context, main.dispatcher),
        )
        val confirms = mutableListOf<DiscardEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.selection.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { confirms += it } }
        advanceUntilIdle()
        return Screen(vm, confirms, this)
    }

    private companion object {
        const val WAZE = 1L
        const val HOME = 2L
        const val GAME = 3L
        const val INSTAGRAM = 4L
        const val YOUTUBE = 5L
        const val UNREAD = 6L
        const val RECOGNISED = 7L
        const val CHAT = 8L
        const val SECRET = 9L
    }
}
