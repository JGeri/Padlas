package hu.padlas.feature.sort

import android.content.IntentSender
import androidx.sqlite.db.SimpleSQLiteQuery
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.common.result.outcomeOf
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

    fun monthStats(media: MediaFilter = MediaFilter.ALL): Flow<List<SwipeMonthStat>> =
        swipeDao.observeMonthStats(media.mimePattern)

    fun bucketStats(media: MediaFilter = MediaFilter.ALL): Flow<List<SwipeBucketStat>> =
        swipeDao.observeBucketStats(media.mimePattern)

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

    /**
     * A deck read by the screen that opens it, before it navigates, so the session's first
     * frame already holds cards for the tapped tile to grow into. Taken once.
     */
    @Volatile
    private var prepared: Pair<SwipeFilter, List<SwipeCard>>? = null

    suspend fun prepare(filter: SwipeFilter) {
        prepared = filter to deck(filter)
    }

    fun takePrepared(filter: SwipeFilter): List<SwipeCard>? =
        prepared?.also { prepared = null }?.takeIf { it.first == filter }?.second

    suspend fun record(cardId: Long, action: SwipeAction): Boolean = tryWrite {
        swipeDao.upsert(SwipeDecisionEntity(cardId, action.choice, System.currentTimeMillis()))
    }

    /** Undo puts back whatever was there before the swipe, including "never decided". */
    suspend fun restore(cardId: Long, previous: SwipeChoice?): Boolean = tryWrite {
        if (previous == null) {
            swipeDao.delete(listOf(cardId))
        } else {
            swipeDao.upsert(SwipeDecisionEntity(cardId, previous, System.currentTimeMillis()))
        }
    }

    suspend fun setChoice(ids: List<Long>, choice: SwipeChoice): Boolean = tryWrite {
        val now = System.currentTimeMillis()
        swipeDao.upsertAll(ids.map { SwipeDecisionEntity(it, choice, now) })
    }

    suspend fun forgetReviewed(): Boolean = tryWrite { swipeDao.forgetReviewed() }

    suspend fun pendingNow(): List<Asset> = withContext(io) { swipeDao.pendingAssets() }

    fun canSkipSystemDialog(): Boolean = mediaOps.canManageMedia()

    /**
     * Builds the system request for [assets]. Trash is the default because it can be
     * undone for thirty days; the permanent mode is the user's explicit choice.
     */
    suspend fun deleteRequest(assets: List<Asset>): DeleteBatch? {
        if (assets.isEmpty()) return null
        val batch = assets.take(MediaOps.MAX_REQUEST)
        val permanent = preferences.deleteMode.value == DeleteMode.PERMANENT
        val uris = batch.map { mediaOps.uriFor(it.mediaStoreId, it.isVideo) }
        val sender = (if (permanent) mediaOps.deleteRequest(uris) else mediaOps.trashRequest(uris)) ?: return null
        return DeleteBatch(
            intentSender = sender,
            ids = batch.map { it.mediaStoreId },
            bytes = batch.sumOf { it.sizeBytes },
            permanent = permanent,
            truncated = assets.size > batch.size,
        )
    }

    /** The files are already gone when this runs; if the write fails the next index pass squares the rows. */
    suspend fun onDeleted(batch: DeleteBatch): Boolean = tryWrite { recordDeleted(batch) }

    private suspend fun recordDeleted(batch: DeleteBatch) {
        val now = System.currentTimeMillis()
        // A full round is more ids than one statement may bind on an older SQLite.
        val chunks = batch.ids.chunked(SQLITE_ARGUMENT_LIMIT)
        val sizes = chunks.flatMap { assetDao.byIds(it) }.associate { it.mediaStoreId to it.sizeBytes }
        chunks.forEach { chunk ->
            if (batch.permanent) {
                assetDao.deleteByIds(chunk)
            } else {
                assetDao.markTrashed(chunk, now)
            }
            swipeDao.delete(chunk)
        }
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
        val assets = ids.chunked(SQLITE_ARGUMENT_LIMIT).flatMap { assetDao.byIds(it) }.filter { !it.isFavorite }
        if (assets.isEmpty()) return@withContext null
        val batch = assets.take(MediaOps.MAX_REQUEST)
        val sender = mediaOps.favoriteRequest(batch.map { mediaOps.uriFor(it.mediaStoreId, it.isVideo) })
            ?: return@withContext null
        FavoriteBatch(sender, batch.map { it.mediaStoreId })
    }

    suspend fun onFavorited(batch: FavoriteBatch): Boolean = tryWrite {
        batch.ids.chunked(SQLITE_ARGUMENT_LIMIT).forEach { assetDao.setFavorite(it, true) }
    }

    /**
     * A failed write - a full disk, most likely, on a phone that is being cleaned - is
     * reported to the screen instead of thrown into it.
     */
    private suspend fun tryWrite(block: suspend () -> Unit): Boolean = withContext(io) {
        writeLock.withLock { outcomeOf { block() }.isSuccess }
    }

    private val MediaFilter.mimePattern: String
        get() = when (this) {
            MediaFilter.ALL -> "%"
            MediaFilter.PHOTOS -> "image/%"
            MediaFilter.VIDEOS -> "video/%"
        }

    companion object {
        const val DECK_LIMIT = 6_000
        private const val SQLITE_ARGUMENT_LIMIT = 900
    }
}
