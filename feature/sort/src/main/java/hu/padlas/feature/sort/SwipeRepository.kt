package hu.padlas.feature.sort

import android.content.IntentSender
import androidx.sqlite.db.SimpleSQLiteQuery
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.dao.AuditDao
import hu.padlas.core.database.dao.SwipeDao
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.database.entity.AuditEntry
import hu.padlas.core.database.entity.SwipeChoice
import hu.padlas.core.database.entity.SwipeDecisionEntity
import hu.padlas.core.database.model.SizeAndCount
import hu.padlas.core.database.model.SwipeBucketStat
import hu.padlas.core.database.model.SwipeMonthStat
import hu.padlas.core.media.MediaOps
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One system dialog for the whole batch; nothing changes in the index before it returns. */
class DeleteBatch internal constructor(
    val intentSender: IntentSender,
    val ids: List<Long>,
    val bytes: Long,
    val permanent: Boolean,
    /** True when more was pending than one dialog can carry. */
    val truncated: Boolean,
)

class FavoriteBatch internal constructor(
    val intentSender: IntentSender,
    val ids: List<Long>,
)

/**
 * Everything the swipe screens persist. Decisions are written as they happen, so a deck
 * that is left halfway resumes exactly where it stopped and nothing marked for deletion
 * is ever forgotten.
 */
@Singleton
class SwipeRepository @Inject constructor(
    private val swipeDao: SwipeDao,
    private val assetDao: AssetDao,
    private val auditDao: AuditDao,
    private val mediaOps: MediaOps,
    private val preferences: SwipePreferences,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** A decision and its undo must reach the database in the order they were made. */
    private val writeLock = Mutex()

    val monthStats: Flow<List<SwipeMonthStat>> = swipeDao.observeMonthStats()

    val bucketStats: Flow<List<SwipeBucketStat>> = swipeDao.observeBucketStats()

    val pending: Flow<SizeAndCount> = swipeDao.observePending()

    val pendingAssets: Flow<List<Asset>> = swipeDao.observePendingAssets()

    suspend fun count(filter: SwipeFilter): SizeAndCount = withContext(io) {
        val query = SwipeSql.count(filter)
        swipeDao.count(SimpleSQLiteQuery(query.sql, query.args.toTypedArray()))
    }

    suspend fun deck(filter: SwipeFilter, limit: Int = DECK_LIMIT): List<SwipeCard> = withContext(io) {
        val query = SwipeSql.candidates(filter, limit)
        swipeDao.candidates(SimpleSQLiteQuery(query.sql, query.args.toTypedArray())).map { it.toCard() }
    }

    suspend fun record(cardId: Long, action: SwipeAction) = write {
        swipeDao.upsert(SwipeDecisionEntity(cardId, action.choice, System.currentTimeMillis()))
    }

    /** Undo puts back whatever was there before the swipe, including "never decided". */
    suspend fun restore(cardId: Long, previous: SwipeChoice?) = write {
        if (previous == null) {
            swipeDao.delete(listOf(cardId))
        } else {
            swipeDao.upsert(SwipeDecisionEntity(cardId, previous, System.currentTimeMillis()))
        }
    }

    suspend fun setChoice(ids: List<Long>, choice: SwipeChoice) = write {
        val now = System.currentTimeMillis()
        swipeDao.upsertAll(ids.map { SwipeDecisionEntity(it, choice, now) })
    }

    suspend fun forgetReviewed() = write { swipeDao.forgetReviewed() }

    suspend fun pendingNow(): List<Asset> = withContext(io) { swipeDao.pendingAssets() }

    fun canSkipSystemDialog(): Boolean = mediaOps.canManageMedia()

    /**
     * Builds the system request for [assets]. Trash is the default because it can be
     * undone for thirty days; the permanent mode is the user's explicit choice.
     */
    fun deleteRequest(assets: List<Asset>): DeleteBatch? {
        if (assets.isEmpty()) return null
        val batch = assets.take(MAX_BATCH)
        val permanent = preferences.deleteMode.value == DeleteMode.PERMANENT
        val uris = batch.map { mediaOps.uriFor(it.mediaStoreId, it.isVideo) }
        val sender = runCatching {
            if (permanent) mediaOps.deleteRequest(uris) else mediaOps.trashRequest(uris)
        }.getOrNull() ?: return null
        return DeleteBatch(
            intentSender = sender,
            ids = batch.map { it.mediaStoreId },
            bytes = batch.sumOf { it.sizeBytes },
            permanent = permanent,
            truncated = assets.size > batch.size,
        )
    }

    suspend fun onDeleted(batch: DeleteBatch) = write {
        val now = System.currentTimeMillis()
        val sizes = assetDao.byIds(batch.ids).associate { it.mediaStoreId to it.sizeBytes }
        if (batch.permanent) {
            assetDao.deleteByIds(batch.ids)
        } else {
            assetDao.markTrashed(batch.ids, now)
        }
        swipeDao.delete(batch.ids)
        auditDao.insertAll(
            batch.ids.map { id ->
                AuditEntry(
                    timestamp = now,
                    action = if (batch.permanent) AuditAction.DELETED else AuditAction.TRASHED,
                    assetId = id,
                    sizeBytes = sizes[id] ?: 0L,
                    reversible = !batch.permanent,
                    note = "Válogatás",
                )
            },
        )
    }

    /** Favourites marked in the deck that the system gallery does not know about yet. */
    suspend fun favoriteRequest(ids: List<Long>): FavoriteBatch? = withContext(io) {
        val assets = assetDao.byIds(ids).filter { !it.isFavorite }
        if (assets.isEmpty()) return@withContext null
        val uris = assets.take(MAX_BATCH).map { mediaOps.uriFor(it.mediaStoreId, it.isVideo) }
        val sender = runCatching { mediaOps.favoriteRequest(uris) }.getOrNull() ?: return@withContext null
        FavoriteBatch(sender, assets.take(MAX_BATCH).map { it.mediaStoreId })
    }

    suspend fun onFavorited(batch: FavoriteBatch) = write {
        assetDao.setFavorite(batch.ids, true)
    }

    private suspend fun <T> write(block: suspend () -> T): T = withContext(io) {
        writeLock.withLock { block() }
    }

    companion object {
        const val DECK_LIMIT = 6_000

        /** Thousands of URIs in one PendingIntent would hit the binder size limit. */
        const val MAX_BATCH = 1_500
    }
}
