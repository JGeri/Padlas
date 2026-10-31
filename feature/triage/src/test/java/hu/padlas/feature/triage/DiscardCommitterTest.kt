package hu.padlas.feature.triage

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import hu.padlas.core.common.result.Outcome
import hu.padlas.core.database.PadlasDatabase
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.media.ThumbnailStore
import hu.padlas.core.testing.FakeMediaOps
import hu.padlas.core.testing.asset
import hu.padlas.core.testing.assets
import hu.padlas.core.testing.testDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiscardCommitterTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: PadlasDatabase
    private lateinit var media: FakeMediaOps
    private lateinit var committer: DiscardCommitter

    @Before
    fun setUp() {
        db = testDatabase(dispatcher)
        media = FakeMediaOps()
        val cards = CardWriter(db.assetDao(), db.cardDao(), db.auditDao(), ThumbnailStore(ApplicationProvider.getApplicationContext(), dispatcher), dispatcher)
        committer = DiscardCommitter(db.assetDao(), db.auditDao(), cards, media, dispatcher)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a protected row never reaches the dialog, even when asked for by id`() = runTest(dispatcher) {
        db.assetDao().upsertAll(listOf(asset(1), asset(2, protectedManually = true), asset(3, state = AssetState.PROTECTED)))

        val pending = (committer.prepare(listOf(1, 2, 3)) as Outcome.Success).value

        assertThat(pending.ids).containsExactly(1L)
        assertThat(media.requests.single().ids).containsExactly(1L)
    }

    @Test
    fun `a batch of protected rows only is refused before any dialog`() = runTest(dispatcher) {
        db.assetDao().upsertAll(listOf(asset(1, protectedManually = true)))

        assertThat(committer.prepare(listOf(1))).isInstanceOf(Outcome.Failure::class.java)
        assertThat(media.requests).isEmpty()
    }

    @Test
    fun `every row gets its card before the dialog, and a batch over one dialog is cut`() = runTest(dispatcher) {
        db.assetDao().upsertAll(assets(1_200))

        val pending = (committer.prepare((1L..1_200L).toList()) as Outcome.Success).value

        assertThat(pending.count).isEqualTo(1_000)
        assertThat(pending.more).isTrue()
        assertThat(pending.ids.chunked(900).flatMap { db.assetDao().byIds(it) }.all { it.cardId != null }).isTrue()
        assertThat(db.assetDao().byId(1_200)?.cardId).isNull()
        assertThat(db.auditDao().all().count { it.action == AuditAction.CARD_WRITTEN }).isEqualTo(1_000)
    }

    @Test
    fun `finishing drops the rows and logs a deletion that cannot be undone`() = runTest(dispatcher) {
        db.assetDao().upsertAll(assets(3, sizeBytes = 500))
        val pending = (committer.prepare(listOf(1, 2, 3)) as Outcome.Success).value

        val freed = committer.finish(pending)

        assertThat(freed).isEqualTo(1_500)
        assertThat(db.assetDao().count()).isEqualTo(0)
        assertThat(db.auditDao().all().filter { it.action == AuditAction.DELETED }.map { it.reversible })
            .containsExactly(false, false, false)
    }
}
