package hu.padlas.feature.dedup.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.dao.AuditDao
import hu.padlas.core.database.dao.DupDao
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.database.entity.AuditEntry
import hu.padlas.feature.dedup.R
import hu.padlas.feature.dedup.group.DupCandidate
import hu.padlas.feature.dedup.group.DupGrouper
import hu.padlas.feature.dedup.group.toDupCandidate
import hu.padlas.feature.dedup.hash.HashPipeline
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Hash everything that is missing a fingerprint, then rebuild the open groups. The worker
 * only ever writes hashes and groups; it never touches a file, because automation is not
 * allowed to delete anything (INV-1b).
 */
@HiltWorker
class DupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val pipeline: HashPipeline,
    private val grouper: DupGrouper,
    private val assetDao: AssetDao,
    private val dupDao: DupDao,
    private val auditDao: AuditDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(io) {
        try {
            pipeline.run()
            val now = System.currentTimeMillis()
            val grouping = grouper.group(loadCandidates(), now)
            dupDao.replaceAll(grouping.groups, grouping.members)
            record(now, grouping.exactReclaimableBytes, note(grouping.groups.size, grouping.exactLoserCount))
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    /** The log shows the note as it was written, in the language the app ran in at the time. */
    private fun note(groupCount: Int, identicalCount: Int): String {
        val resources = applicationContext.resources
        val groups = resources.getQuantityString(R.plurals.dedup_groups, groupCount, Format.count(groupCount))
        val identical = resources.getQuantityString(R.plurals.dedup_log_identical, identicalCount, Format.count(identicalCount))
        return "$groups, $identical"
    }

    /**
     * The scan runs every time the app opens. A result that matches the last one moves that
     * entry to now instead of adding a row, so the log keeps one line per change rather
     * than one per launch.
     */
    private suspend fun record(now: Long, bytes: Long, note: String) {
        val last = auditDao.latest(AuditAction.HASHED)
        if (last != null && last.note == note && last.sizeBytes == bytes) {
            auditDao.touch(last.id, now)
        } else {
            auditDao.insert(
                AuditEntry(
                    timestamp = now,
                    action = AuditAction.HASHED,
                    assetId = null,
                    sizeBytes = bytes,
                    reversible = false,
                    note = note,
                ),
            )
        }
    }

    /**
     * Only assets that can possibly be in a group are loaded: the members of a quickHash
     * collision and everything with a perceptual hash. The id lists come from the light
     * projections first so the full rows - which carry the protection flags the grouper
     * needs for INV-4 - are read once, in id chunks SQLite will accept.
     */
    private suspend fun loadCandidates(): List<DupCandidate> {
        val ids = HashSet<Long>()
        assetDao.quickHashCollisions().forEach { collision ->
            assetDao.rowsWithQuickHash(collision.quickHash).mapTo(ids) { it.mediaStoreId }
        }
        assetDao.perceptualRows().mapTo(ids) { it.mediaStoreId }
        return ids.chunked(SQLITE_ARGUMENT_LIMIT)
            .flatMap { chunk -> assetDao.byIds(chunk) }
            .map { it.toDupCandidate() }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val SQLITE_ARGUMENT_LIMIT = 900
    }
}
