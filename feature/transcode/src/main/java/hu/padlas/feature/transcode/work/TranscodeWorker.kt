package hu.padlas.feature.transcode.work

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import hu.padlas.core.common.format.Format
import hu.padlas.feature.transcode.data.TranscodeRepository

/**
 * Long-running by declaration: a 4K clip takes minutes and the batch takes a night, so
 * the work runs as a dataSync foreground service and reports honestly while it does.
 */
@HiltWorker
class TranscodeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: TranscodeRepository,
) : CoroutineWorker(appContext, params) {

    private var lastNotifiedAt = 0L

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(STARTING, 0f)

    override suspend fun doWork(): Result {
        setForegroundSafely(STARTING, 0f)
        val queued = repository.enqueueAll()
        val outcome = repository.runBatch { progress ->
            setForegroundBlocking(progress.displayName, progress.fraction)
        }
        val data = workDataOf(
            KEY_DONE to outcome.done,
            KEY_FAILED to outcome.failed,
            KEY_PAUSED to outcome.paused,
            KEY_QUEUED to queued,
        )
        return when {
            outcome.paused -> Result.retry()
            outcome.failed > 0 && outcome.done == 0 -> Result.failure(data)
            else -> Result.success(data)
        }
    }

    private fun setForegroundBlocking(name: String, fraction: Float) {
        val now = System.currentTimeMillis()
        if (now - lastNotifiedAt < NOTIFY_INTERVAL_MS) return
        lastNotifiedAt = now
        // Fire and forget: a dropped progress tick must never stall the encoder.
        runCatching { notificationManager()?.notify(NOTIFICATION_ID, notification(name, fraction)) }
        runCatching { setProgressAsync(workDataOf(KEY_CURRENT to name, KEY_FRACTION to fraction)) }
    }

    private suspend fun setForegroundSafely(name: String, fraction: Float) {
        runCatching { setForeground(foregroundInfo(name, fraction)) }
    }

    private fun foregroundInfo(name: String, fraction: Float) = ForegroundInfo(
        NOTIFICATION_ID,
        notification(name, fraction),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )

    private fun notification(name: String, fraction: Float): Notification =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(TITLE)
            .setContentText(if (name.isBlank()) STARTING else name)
            .setSubText(Format.percent(fraction))
            .setProgress(PROGRESS_MAX, (fraction * PROGRESS_MAX).toInt(), fraction <= 0f)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

    private fun notificationManager() =
        applicationContext.getSystemService(android.app.NotificationManager::class.java)

    companion object {
        const val CHANNEL_ID = "padlas.work"
        const val NOTIFICATION_ID = 4201
        const val KEY_DONE = "done"
        const val KEY_FAILED = "failed"
        const val KEY_PAUSED = "paused"
        const val KEY_QUEUED = "queued"
        const val KEY_CURRENT = "current"
        const val KEY_FRACTION = "fraction"
        private const val PROGRESS_MAX = 1000
        private const val NOTIFY_INTERVAL_MS = 1200L
        private const val TITLE = "Videók tömörítése"
        private const val STARTING = "Indul a tömörítés"
    }
}
