package hu.padlas.feature.triage

import android.content.IntentSender
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.common.result.Outcome
import hu.padlas.core.common.result.outcomeOf
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.dao.AuditDao
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.database.entity.AuditEntry
import hu.padlas.core.media.MediaOps
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A batch that has its cards written and its system dialog ready, but is not gone yet. */
data class PendingDelete(
    val sizes: Map<Long, Long>,
    val intentSender: IntentSender,
    /** More was marked than one dialog carries; the rest goes in the next round. */
    val more: Boolean = false,
) {
    val ids: List<Long> get() = sizes.keys.toList()
    val count: Int get() = sizes.size
    val bytes: Long get() = sizes.values.sum()
}

/**
 * The single permanent deletion path of the module, shared by the Kidobó and the inverse
 * folder mode. Order matters and is not negotiable: cards first (spec 1.6), then one
 * batched system dialog for the whole set (INV-3), then the audit trail (INV-5).
 */
@Singleton
class DiscardCommitter @Inject constructor(
    private val assetDao: AssetDao,
    private val auditDao: AuditDao,
    private val cardWriter: CardWriter,
    private val mediaOps: MediaOps,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun prepare(ids: List<Long>): Outcome<PendingDelete> = withContext(io) {
        outcomeOf("A törlés előkészítése nem sikerült.") {
            // INV-1c and INV-4: a protected row never reaches the dialog, however the
            // selection was assembled. SECRET assets stay protected until the user
            // explicitly lifts the protection.
            val rows = ids.chunked(SQLITE_ARGUMENT_LIMIT).flatMap { assetDao.byIds(it) }.filter {
                !it.protectedManually && it.state != AssetState.PROTECTED
            }
            require(rows.isNotEmpty()) { "Nincs törölhető elem a kötegben." }

            val batch = rows.take(MediaOps.MAX_REQUEST)
            cardWriter.write(batch)
            val sender = mediaOps.deleteRequest(
                batch.map { mediaOps.uriFor(it.mediaStoreId, it.isVideo) },
            ) ?: error("A rendszer most nem engedte a törlést.")
            PendingDelete(
                sizes = batch.associate { it.mediaStoreId to it.sizeBytes },
                intentSender = sender,
                more = rows.size > batch.size,
            )
        }
    }

    /**
     * Called only after the system dialog reported success: the bytes are really gone. Null
     * when the bookkeeping could not be written (a full disk); the next index pass squares it.
     */
    suspend fun finish(pending: PendingDelete): Long? = withContext(io) {
        outcomeOf {
            val now = System.currentTimeMillis()
            auditDao.insertAll(
                pending.sizes.map { (id, size) ->
                    AuditEntry(
                        timestamp = now,
                        action = AuditAction.DELETED,
                        assetId = id,
                        sizeBytes = size,
                        reversible = false,
                    )
                },
            )
            pending.ids.chunked(SQLITE_ARGUMENT_LIMIT).forEach { assetDao.deleteByIds(it) }
            pending.bytes
        }.valueOrNull()
    }

    private companion object {
        const val SQLITE_ARGUMENT_LIMIT = 900
    }
}
