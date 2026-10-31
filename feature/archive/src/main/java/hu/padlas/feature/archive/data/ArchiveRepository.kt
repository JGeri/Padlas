package hu.padlas.feature.archive.data

import android.content.Context
import android.content.IntentSender
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.common.result.Outcome
import hu.padlas.core.common.result.outcomeOf
import hu.padlas.core.common.text.UiText
import hu.padlas.core.common.text.uiText
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.dao.AuditDao
import hu.padlas.core.database.dao.UploadJobDao
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.database.entity.AuditEntry
import hu.padlas.core.database.entity.JobState
import hu.padlas.core.database.entity.UploadJobEntity
import hu.padlas.core.media.Hasher
import hu.padlas.core.media.MediaOps
import hu.padlas.core.media.ThumbnailStore
import hu.padlas.feature.archive.R
import hu.padlas.feature.archive.drive.ArchiveKind
import hu.padlas.feature.archive.drive.ChunkOutcome
import hu.padlas.feature.archive.drive.DriveClient
import hu.padlas.feature.archive.drive.DriveFolders
import hu.padlas.feature.archive.drive.DrivePaths
import hu.padlas.feature.archive.drive.ManifestEntry
import hu.padlas.feature.archive.drive.ResumableRange
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Upload, then verify, then and only then change state. "Probably uploaded" and "provably
 * identical" are different things, and the whole reason this app talks to the REST API
 * instead of SAF is that only one of them can be checked (spec 3.1).
 */
@Singleton
class ArchiveRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assetDao: AssetDao,
    private val uploadJobDao: UploadJobDao,
    private val auditDao: AuditDao,
    private val client: DriveClient,
    private val folders: DriveFolders,
    private val hasher: Hasher,
    private val mediaOps: MediaOps,
    private val thumbnails: ThumbnailStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val _progress = MutableStateFlow(UploadProgress())
    val progress: StateFlow<UploadProgress> = _progress.asStateFlow()

    suspend fun enqueue(assetIds: List<Long>) = withContext(io) {
        val assets = assetIds.chunked(SQLITE_ARGUMENT_LIMIT).flatMap { assetDao.byIds(it) }
            .filter { it.state == AssetState.LOCAL || it.state == AssetState.PROTECTED }
        if (assets.isEmpty()) return@withContext
        uploadJobDao.upsertAll(
            assets.map { asset ->
                UploadJobEntity(
                    assetId = asset.mediaStoreId,
                    totalBytes = asset.sizeBytes,
                    state = JobState.PENDING,
                    updatedAt = System.currentTimeMillis(),
                )
            },
        )
        assets.map { it.mediaStoreId }.chunked(SQLITE_ARGUMENT_LIMIT).forEach { assetDao.setState(it, AssetState.UPLOADING) }
    }

    /** Runs the queue until it is empty or the coroutine is cancelled. */
    suspend fun drainQueue(batchLimit: Int = BATCH): Int = withContext(io) {
        var uploaded = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val batch = uploadJobDao.nextBatch(batchLimit)
            if (batch.isEmpty()) break
            for (job in batch) {
                currentCoroutineContext().ensureActive()
                if (upload(job) is Outcome.Success) uploaded++
            }
        }
        _progress.value = UploadProgress()
        uploaded
    }

    private suspend fun upload(job: UploadJobEntity): Outcome<Unit> {
        val now = System.currentTimeMillis()
        val asset = assetDao.byId(job.assetId)
            ?: return fail(job, uiText(R.string.archive_error_asset_gone)).also { uploadJobDao.delete(listOf(job.assetId)) }

        uploadJobDao.setState(job.assetId, JobState.RUNNING, null, now)
        _progress.value = UploadProgress(
            assetId = asset.mediaStoreId,
            name = asset.displayName,
            bytesSent = job.bytesSent,
            totalBytes = asset.sizeBytes,
            active = true,
        )

        val kind = if (asset.isVideo) ArchiveKind.VIDEO else ArchiveKind.PHOTO
        val folderId = folders.folderFor(kind, asset.dateTaken)
            ?: return fail(job, uiText(R.string.archive_error_folder))

        var session = job.uploadUri
        if (session == null) {
            session = client.startResumableSession(
                name = asset.displayName,
                parentId = folderId,
                mimeType = asset.mimeType,
                totalBytes = asset.sizeBytes,
            ) ?: return fail(job, uiText(R.string.archive_error_session))
            // Persisted before the first byte moves, per spec 3.3 step 1.
            uploadJobDao.setSessionUri(job.assetId, session, System.currentTimeMillis())
        }

        val fileId = when (val outcome = transfer(job.assetId, session, asset)) {
            is TransferResult.Done -> outcome.fileId
            is TransferResult.Restart -> {
                uploadJobDao.upsert(job.copy(uploadUri = null, bytesSent = 0, state = JobState.PENDING))
                return Outcome.Failure(text(R.string.archive_error_session_expired))
            }

            is TransferResult.Failed -> return fail(job, outcome.message)
        }

        return verify(asset, fileId, folderId)
    }

    private suspend fun transfer(assetId: Long, sessionUri: String, asset: Asset): TransferResult {
        var offset = when (val probe = client.probeOffset(sessionUri, asset.sizeBytes)) {
            is ChunkOutcome.Incomplete -> probe.nextOffset
            is ChunkOutcome.Complete -> return TransferResult.Done(probe.fileId)
            ChunkOutcome.SessionGone -> return TransferResult.Restart
            is ChunkOutcome.Fatal -> return TransferResult.Failed(probe.message)
            is ChunkOutcome.Retryable -> 0L
        }

        val uri = asset.uri.toUriOrNull() ?: return TransferResult.Failed(uiText(R.string.archive_error_bad_uri))
        val stream = mediaOps.openInputStream(uri)
            ?: return TransferResult.Failed(uiText(R.string.archive_error_unreadable))

        stream.use { input ->
            var skipped = 0L
            while (skipped < offset) {
                val jumped = input.skip(offset - skipped)
                if (jumped <= 0) return TransferResult.Failed(uiText(R.string.archive_error_shorter))
                skipped += jumped
            }

            val buffer = ByteArray(ResumableRange.CHUNK_BYTES)
            while (offset < asset.sizeBytes) {
                currentCoroutineContext().ensureActive()
                val expected = ResumableRange.chunkLength(offset, asset.sizeBytes)
                val read = input.readNBytesCompat(buffer, expected)
                if (read <= 0) return TransferResult.Failed(uiText(R.string.archive_error_ended))
                val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)

                when (val outcome = client.uploadChunk(sessionUri, chunk, offset, asset.sizeBytes)) {
                    is ChunkOutcome.Complete -> {
                        uploadJobDao.setProgress(assetId, asset.sizeBytes, System.currentTimeMillis())
                        return TransferResult.Done(outcome.fileId)
                    }

                    is ChunkOutcome.Incomplete -> {
                        offset = outcome.nextOffset
                        uploadJobDao.setProgress(assetId, offset, System.currentTimeMillis())
                        _progress.value = _progress.value.copy(bytesSent = offset)
                    }

                    ChunkOutcome.SessionGone -> return TransferResult.Restart
                    is ChunkOutcome.Retryable -> return TransferResult.Failed(outcome.message)
                    is ChunkOutcome.Fatal -> return TransferResult.Failed(outcome.message)
                }
            }
        }
        return TransferResult.Failed(uiText(R.string.archive_error_unfinished))
    }

    /** Spec 3.3 step 5: server MD5 against the locally computed one, or it did not happen. */
    private suspend fun verify(asset: Asset, fileId: String, folderId: String): Outcome<Unit> = outcomeOf {
        val remote = client.getFile(fileId)
        val localMd5 = hasher.md5(asset.uri.toUriOrNull() ?: error(text(R.string.archive_error_bad_uri)))
        val remoteMd5 = remote?.md5

        if (remoteMd5 == null || localMd5 == null || !remoteMd5.equals(localMd5, ignoreCase = true)) {
            client.delete(fileId)
            uploadJobDao.setState(
                asset.mediaStoreId,
                JobState.FAILED,
                text(R.string.archive_error_checksum),
                System.currentTimeMillis(),
            )
            error(text(R.string.archive_error_checksum_deleted))
        }

        val now = System.currentTimeMillis()
        assetDao.markArchived(asset.mediaStoreId, fileId, remoteMd5, now)
        uploadJobDao.markSucceeded(asset.mediaStoreId, fileId, now)
        auditDao.insert(
            AuditEntry(
                timestamp = now,
                action = AuditAction.ARCHIVED,
                assetId = asset.mediaStoreId,
                sizeBytes = asset.sizeBytes,
                reversible = false,
                note = DrivePaths.displayPath(
                    DrivePaths.segments(
                        if (asset.isVideo) ArchiveKind.VIDEO else ArchiveKind.PHOTO,
                        asset.dateTaken,
                    ),
                ),
            ),
        )

        folders.mergeManifest(
            folderId = folderId,
            displayPath = DrivePaths.displayPath(
                DrivePaths.segments(
                    if (asset.isVideo) ArchiveKind.VIDEO else ArchiveKind.PHOTO,
                    asset.dateTaken,
                ),
            ),
            entries = listOf(
                ManifestEntry(
                    assetId = asset.mediaStoreId,
                    name = asset.displayName,
                    originalPath = asset.relativePath + asset.displayName,
                    dateTaken = asset.dateTaken,
                    sizeBytes = asset.sizeBytes,
                    mimeType = asset.mimeType,
                    sha256 = asset.sha256,
                    driveFileId = fileId,
                    driveMd5 = remoteMd5,
                ),
            ),
        )
    }

    /**
     * Freeing the local bytes is a separate, explicit step (INV-1): being on Drive is a
     * precondition for deleting, never a trigger for it.
     */
    suspend fun prepareGhosting(assetIds: List<Long>): GhostPreparation = withContext(io) {
        try {
            val rows = assetIds.chunked(SQLITE_ARGUMENT_LIMIT).flatMap { assetDao.byIds(it) }.filter {
                it.state == AssetState.ARCHIVED && it.driveMd5 != null
            }
            if (rows.isEmpty()) {
                return@withContext GhostPreparation.Refused(uiText(R.string.archive_ghost_needs_verified))
            }
            val batch = rows.take(MediaOps.MAX_REQUEST)
            batch.forEach { asset ->
                asset.uri.toUriOrNull()?.let { uri ->
                    thumbnails.writeGhostThumbnail(asset.mediaStoreId, uri)
                }
            }
            val intentSender = mediaOps.deleteRequest(batch.map { mediaOps.uriFor(it.mediaStoreId, it.isVideo) })
                ?: return@withContext GhostPreparation.Refused(uiText(R.string.archive_ghost_system_refused))
            GhostPreparation.Ready(
                GhostBatch(
                    sizes = batch.associate { it.mediaStoreId to it.sizeBytes },
                    intentSender = intentSender,
                    more = rows.size > batch.size,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            GhostPreparation.Refused(uiText(R.string.archive_ghost_prepare_failed))
        }
    }

    /** Null when the bookkeeping could not be written (a full disk); the files are gone either way. */
    suspend fun finishGhosting(batch: GhostBatch): Long? = withContext(io) {
        outcomeOf {
            val now = System.currentTimeMillis()
            val ids = batch.sizes.keys.toList()
            ids.forEach { id ->
                assetDao.markGhost(listOf(id), thumbnails.ghostThumbnailFile(id).absolutePath)
            }
            auditDao.insertAll(
                batch.sizes.map { (id, size) ->
                    AuditEntry(
                        timestamp = now,
                        action = AuditAction.GHOSTED,
                        assetId = id,
                        sizeBytes = size,
                        reversible = false,
                    )
                },
            )
            batch.bytes
        }.valueOrNull()
    }

    /**
     * Weekly drift check (spec 10). If a file was removed on the Drive side the index would
     * otherwise keep claiming the asset is safe, which is the one lie this app cannot tell.
     */
    suspend fun reconcile(): Int = withContext(io) {
        val archived = assetDao.allIds().chunked(SQLITE_ARGUMENT_LIMIT).flatMap { assetDao.byIds(it) }.filter {
            (it.state == AssetState.ARCHIVED || it.state == AssetState.GHOST) && it.driveFileId != null
        }
        var drifted = 0
        for (asset in archived) {
            currentCoroutineContext().ensureActive()
            val remote = client.getFile(asset.driveFileId ?: continue)
            if (remote == null || remote.trashed) {
                drifted++
                auditDao.insert(
                    AuditEntry(
                        timestamp = System.currentTimeMillis(),
                        action = AuditAction.RECONCILED,
                        assetId = asset.mediaStoreId,
                        sizeBytes = asset.sizeBytes,
                        reversible = false,
                        note = context.getString(R.string.archive_audit_missing_on_drive, asset.displayName),
                    ),
                )
            }
        }
        drifted
    }

    /** The screen gets the sentence; the job row and the outcome keep it as text, in the app's language. */
    private suspend fun fail(job: UploadJobEntity, message: UiText): Outcome<Unit> {
        val reason = message.resolve(context.resources)
        uploadJobDao.setState(job.assetId, JobState.FAILED, reason, System.currentTimeMillis())
        _progress.value = _progress.value.copy(active = false, message = message)
        return Outcome.Failure(reason)
    }

    private fun text(@StringRes id: Int): String = context.getString(id)

    private sealed interface TransferResult {
        data class Done(val fileId: String) : TransferResult
        data object Restart : TransferResult
        data class Failed(val message: UiText) : TransferResult
    }

    private companion object {
        const val BATCH = 8
        const val SQLITE_ARGUMENT_LIMIT = 900
    }
}

data class UploadProgress(
    val assetId: Long = 0,
    val name: String = "",
    val bytesSent: Long = 0,
    val totalBytes: Long = 0,
    val active: Boolean = false,
    val message: UiText? = null,
) {
    val fraction: Float get() = ResumableRange.progress(bytesSent, totalBytes)
}

/** A batch ready for the system dialog, or the reason none can be shown. */
sealed interface GhostPreparation {
    data class Ready(val batch: GhostBatch) : GhostPreparation
    data class Refused(val reason: UiText) : GhostPreparation
}

data class GhostBatch(
    val sizes: Map<Long, Long>,
    val intentSender: IntentSender,
    /** More was asked for than one dialog carries; the rest goes in the next round. */
    val more: Boolean = false,
) {
    val bytes: Long get() = sizes.values.sum()
}

private fun String.toUriOrNull(): android.net.Uri? = runCatching { android.net.Uri.parse(this) }.getOrNull()

/** `InputStream.readNBytes` exists on API 33+, but the loop is clearer about partial reads. */
private fun java.io.InputStream.readNBytesCompat(buffer: ByteArray, length: Int): Int {
    var read = 0
    while (read < length) {
        val count = read(buffer, read, length - read)
        if (count < 0) break
        read += count
    }
    return read
}
