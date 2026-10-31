package hu.padlas.feature.index

import android.os.SystemClock
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.common.result.Outcome
import hu.padlas.core.common.result.outcomeOf
import hu.padlas.core.common.text.UiText
import hu.padlas.core.common.text.uiText
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.dao.AuditDao
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.database.entity.AuditEntry
import hu.padlas.core.media.MediaStoreScanner
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Owns the MediaStore to Room index. One run at a time, batched writes, and a state flow
 * the first-run screen and the Vault both read; the screen must never wait for the run to
 * finish before it can show a number (spec 2.3).
 */
@Singleton
class IndexRepository @Inject constructor(
    private val scanner: MediaStoreScanner,
    private val assetDao: AssetDao,
    private val auditDao: AuditDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val _state = MutableStateFlow<IndexState>(IndexState.Idle)
    val state: StateFlow<IndexState> = _state.asStateFlow()

    private val runLock = Mutex()

    val indexed: Flow<Boolean> = assetDao.observeCount().map { it > 0 }

    suspend fun runFullScan(): Outcome<IndexState.Done> = run(fromScratch = true)

    suspend fun runIncremental(): Outcome<IndexState.Done> = run(fromScratch = false)

    private suspend fun run(fromScratch: Boolean): Outcome<IndexState.Done> {
        if (!runLock.tryLock()) return Outcome.Failure(ALREADY_RUNNING)
        return try {
            withContext(io) {
                outcomeOf(SCAN_FAILED) { scan(fromScratch) }
            }.also { outcome ->
                if (outcome is Outcome.Failure) _state.value = IndexState.Failed(failureText(outcome))
            }
        } finally {
            runLock.unlock()
        }
    }

    /** The system's own message when there is one, which no translation can improve on. */
    private fun failureText(failure: Outcome.Failure): UiText =
        failure.cause?.message?.let(UiText::Raw) ?: uiText(R.string.index_scan_interrupted)

    private suspend fun scan(fromScratch: Boolean): IndexState.Done {
        val startedAt = SystemClock.elapsedRealtime()
        val now = System.currentTimeMillis()

        // One id-only cursor pass up front: it gives the progress denominator and the set
        // the deletion reconciliation needs, for the price of a single cheap query.
        val liveIds = scanner.currentIds()
        _state.value = IndexState.Scanning(scanned = 0, total = liveIds.size, bytes = 0L)

        val since = if (fromScratch) 0L else sinceWatermark()
        var scanned = 0
        var changed = 0
        var bytes = 0L
        val store: suspend (List<Asset>) -> Unit = { batch ->
            val existing = assetDao.byIds(batch.map { it.mediaStoreId }).associateBy { it.mediaStoreId }
            val merged = batch.map { fresh -> mergeAsset(existing[fresh.mediaStoreId], fresh, now) }
            changed += batch.count { fresh -> existing[fresh.mediaStoreId]?.sameFileAs(fresh) != true }
            assetDao.upsertAll(merged)
            scanned += merged.size
            bytes += merged.sumOf { it.sizeBytes }
            _state.value = IndexState.Scanning(
                scanned = scanned,
                total = maxOf(liveIds.size, scanned),
                bytes = bytes,
                bucket = merged.lastOrNull()?.bucketName.orEmpty(),
            )
        }
        scanner.scan(since).collect(store)
        if (!fromScratch) {
            // The watermark only sees files modified after the newest one indexed. A photo
            // copied in from a PC, a backup or another phone keeps its old timestamp and
            // would never show up, so whatever MediaStore lists that the index lacks is
            // read by id.
            val known = assetDao.allIds().toHashSet()
            scanner.scanIds(liveIds.filterNot { it in known }).collect(store)
        }

        val reconciled = reconcileDeletions(liveIds, now)
        val count = assetDao.count()
        val localBytes = assetDao.observeLocalBytes().first()
        val durationMs = SystemClock.elapsedRealtime() - startedAt

        // One entry per run, never per asset: 25k audit rows would drown the log (INV-5).
        // A pass that found nothing new only moves the last such line to now; the sync runs
        // whenever any app touches shared storage, and would otherwise fill the log.
        val last = auditDao.latest(AuditAction.INDEXED)
        if (!fromScratch && changed == 0 && !reconciled.touched && last?.note == NOTE_INCREMENTAL) {
            auditDao.touch(last.id, now)
        } else {
            auditDao.insert(
                AuditEntry(
                    timestamp = now,
                    action = AuditAction.INDEXED,
                    assetId = null,
                    sizeBytes = localBytes,
                    reversible = false,
                    payloadJson = runPayload(scanned, count, reconciled, durationMs, fromScratch),
                    note = if (fromScratch) NOTE_FULL else NOTE_INCREMENTAL,
                ),
            )
        }
        if (reconciled.touched) {
            auditDao.insert(
                AuditEntry(
                    timestamp = now,
                    action = AuditAction.RECONCILED,
                    assetId = null,
                    sizeBytes = 0L,
                    reversible = false,
                    payloadJson = reconciled.payload(),
                    note = NOTE_RECONCILED,
                ),
            )
        }

        return IndexState.Done(count = count, bytes = localBytes, durationMs = durationMs)
            .also { _state.value = it }
    }

    /**
     * One tick back from the newest row we hold. MediaStore timestamps are coarse, so a
     * file written in the same tick as the last indexed one would otherwise be skipped
     * forever; re-reading a handful of rows is free, losing one is not.
     */
    private suspend fun sinceWatermark(): Long =
        ((assetDao.latestDateModified() ?: 0L) - 1L).coerceAtLeast(0L)

    private fun Asset.sameFileAs(fresh: Asset): Boolean =
        sizeBytes == fresh.sizeBytes && dateModified == fresh.dateModified

    /**
     * Rows we hold that MediaStore no longer reports. An ARCHIVED asset that vanished
     * locally is not a deletion but a ghost: the bytes are gone, the Drive copy and the
     * catalogue entry must survive (INV-3).
     *
     * A trashed file is not gone either: MediaStore hides it from every plain query, but it
     * sits in the bin for thirty days and the log can still bring it back, which needs its
     * row. Only a trashed row older than the bin is dropped. One that MediaStore reports
     * again was restored somewhere else, from the gallery's bin for example.
     */
    private suspend fun reconcileDeletions(liveIds: Set<Long>, now: Long): Reconciled {
        // An empty live set means the cursor failed or the permission was revoked between
        // passes. Wiping the whole index on that would be catastrophic and silent.
        if (liveIds.isEmpty()) return Reconciled(0, 0, 0)

        val restored = assetDao.trashedIds().filter { it in liveIds }
        restored.chunked(ID_CHUNK).forEach { assetDao.markUntrashed(it) }

        val missing = assetDao.allIds().filterNot { it in liveIds }
        if (missing.isEmpty()) return Reconciled(0, 0, restored.size)

        var removed = 0
        var ghosted = 0
        missing.chunked(ID_CHUNK).forEach { chunk ->
            val rows = assetDao.byIds(chunk)
            val toGhost = rows
                .filter { it.state == AssetState.ARCHIVED && it.driveFileId != null }
                .map { it.mediaStoreId }
            val toDelete = rows
                .filter { it.state != AssetState.GHOST && it.mediaStoreId !in toGhost && !it.inBin(now) }
                .map { it.mediaStoreId }
            if (toGhost.isNotEmpty()) {
                assetDao.setState(toGhost, AssetState.GHOST)
                ghosted += toGhost.size
            }
            if (toDelete.isNotEmpty()) {
                assetDao.deleteByIds(toDelete)
                removed += toDelete.size
            }
        }
        return Reconciled(removed, ghosted, restored.size)
    }

    private fun Asset.inBin(now: Long): Boolean =
        state == AssetState.TRASHED && (trashedAt ?: 0L) > now - TRASH_RETENTION_MS

    private fun runPayload(
        scanned: Int,
        total: Int,
        reconciled: Reconciled,
        durationMs: Long,
        fromScratch: Boolean,
    ): String = buildString {
        append("{")
        append(JSON_SCANNED).append(scanned).append(",")
        append(JSON_TOTAL).append(total).append(",")
        append(JSON_REMOVED).append(reconciled.removed).append(",")
        append(JSON_GHOSTED).append(reconciled.ghosted).append(",")
        append(JSON_RESTORED).append(reconciled.restored).append(",")
        append(JSON_DURATION).append(durationMs).append(",")
        append(JSON_FULL).append(fromScratch)
        append("}")
    }

    private data class Reconciled(val removed: Int, val ghosted: Int, val restored: Int) {
        val touched: Boolean get() = removed > 0 || ghosted > 0 || restored > 0

        fun payload(): String =
            "{" + JSON_REMOVED + removed + "," + JSON_GHOSTED + ghosted + "," + JSON_RESTORED + restored + "}"
    }

    private companion object {
        const val ID_CHUNK = 400

        /** MediaStore empties its bin after thirty days; two more cover a phone that was off. */
        const val TRASH_RETENTION_MS = 32L * 24 * 60 * 60 * 1000

        /** Only the worker reads these, to decide on a retry; the screen shows [failureText]. */
        const val ALREADY_RUNNING = "Index scan already running"
        const val SCAN_FAILED = "Index scan interrupted"

        /** Stored in the log and matched against earlier rows, so they stay as written. */
        const val NOTE_FULL = "Teljes beolvasás"
        const val NOTE_INCREMENTAL = "Új és módosult fájlok"
        const val NOTE_RECONCILED = "Eltűnt fájlok egyeztetése"

        const val JSON_SCANNED = "\"scanned\":"
        const val JSON_TOTAL = "\"total\":"
        const val JSON_REMOVED = "\"removed\":"
        const val JSON_GHOSTED = "\"ghosted\":"
        const val JSON_RESTORED = "\"restored\":"
        const val JSON_DURATION = "\"durationMs\":"
        const val JSON_FULL = "\"full\":"
    }
}
