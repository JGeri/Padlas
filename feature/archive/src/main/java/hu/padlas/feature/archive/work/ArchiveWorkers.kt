package hu.padlas.feature.archive.work

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.dao.CardDao
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.database.dao.AuditDao
import hu.padlas.core.database.entity.AuditEntry
import hu.padlas.feature.archive.R
import hu.padlas.feature.archive.data.ArchiveRepository
import hu.padlas.feature.archive.drive.ArchiveKind
import hu.padlas.feature.archive.drive.DriveClient
import hu.padlas.feature.archive.drive.DriveFolders
import hu.padlas.feature.archive.drive.DrivePaths
import java.io.File
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private const val CHANNEL_ID = "padlas.work"
private const val UPLOAD_NOTIFICATION_ID = 4201
private const val CARD_NOTIFICATION_ID = 4202

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: ArchiveRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        setForegroundSafely(applicationContext.getString(R.string.archive_notification_upload))
        return runCatching { repository.drainQueue() }
            .fold(
                onSuccess = { Result.success() },
                // Retry rather than fail: an interrupted transfer resumes from its
                // persisted session URI, so a retry costs the last chunk at most.
                onFailure = { if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure() },
            )
    }

    private suspend fun setForegroundSafely(title: String) {
        runCatching { setForeground(foregroundInfo(applicationContext, UPLOAD_NOTIFICATION_ID, title)) }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}

/**
 * The card (spec 1.6) is what survives a deletion: a thumbnail, the recognised text and
 * the original path. Twenty kilobytes instead of two megabytes, and the answer to "did I
 * ever have a screenshot of my tax number" stays available.
 */
@HiltWorker
class CardUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val cardDao: CardDao,
    private val auditDao: AuditDao,
    private val client: DriveClient,
    private val folders: DriveFolders,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val title = applicationContext.getString(R.string.archive_notification_cards)
        runCatching { setForeground(foregroundInfo(applicationContext, CARD_NOTIFICATION_ID, title)) }

        val pending = cardDao.pendingUpload(BATCH)
        if (pending.isEmpty()) return Result.success()

        var written = 0
        for (card in pending) {
            val folderId = folders.folderFor(ArchiveKind.CARD, card.dateTaken) ?: return Result.retry()
            val payload = buildJsonObject {
                put("cardId", JsonPrimitive(card.cardId))
                put("assetId", JsonPrimitive(card.assetId))
                put("displayName", JsonPrimitive(card.displayName))
                put("originalPath", JsonPrimitive(card.originalPath))
                put("dateTaken", JsonPrimitive(card.dateTaken))
                put("sizeBytes", JsonPrimitive(card.sizeBytes))
                put("ocrText", JsonPrimitive(card.ocrText.orEmpty()))
            }.toString()

            val jsonId = client.uploadSmall(
                name = "${card.cardId}.json",
                parentId = folderId,
                mimeType = "application/json",
                content = payload.encodeToByteArray(),
            ) ?: return Result.retry()

            card.thumbPath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    client.uploadSmall(
                        name = "${card.cardId}.jpg",
                        parentId = folderId,
                        mimeType = "image/jpeg",
                        content = file.readBytes(),
                    )
                }
            }

            cardDao.setDriveFileId(card.cardId, jsonId)
            written++
        }

        auditDao.insert(
            AuditEntry(
                timestamp = System.currentTimeMillis(),
                action = AuditAction.CARD_WRITTEN,
                assetId = null,
                sizeBytes = 0,
                reversible = false,
                // The log stores text, so the line is written in the language the app runs in now.
                note = applicationContext.resources.getQuantityString(
                    R.plurals.archive_audit_cards_written,
                    written,
                    Format.count(written),
                    DrivePaths.CARDS,
                ),
            ),
        )
        return Result.success()
    }

    private companion object {
        const val BATCH = 200
    }
}

/** Weekly drift check between the index and the Drive tree (spec 10). */
@HiltWorker
class ReconcileWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: ArchiveRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        runCatching { repository.reconcile() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
}

private fun foregroundInfo(context: Context, id: Int, title: String): ForegroundInfo {
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setContentTitle(title)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setOngoing(true)
        .setSilent(true)
        .build()
    return ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
}
