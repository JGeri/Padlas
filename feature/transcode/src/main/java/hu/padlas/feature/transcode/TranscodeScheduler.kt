package hu.padlas.feature.transcode

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import hu.padlas.feature.transcode.work.TranscodeWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Charger only, battery not low: a transcode batch is a night job by design. The thermal
 * governor handles the rest of the throttling from inside the worker.
 */
object TranscodeScheduler {

    const val WORK_BATCH = "padlas.transcode.batch"
    const val TAG = "padlas.transcode"

    fun enqueueBatch(context: Context) {
        val request = OneTimeWorkRequestBuilder<TranscodeWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresCharging(true)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, COOLDOWN_MINUTES, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_BATCH, ExistingWorkPolicy.KEEP, request)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    fun observeRun(context: Context): Flow<RunStatus> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK_BATCH).map { infos ->
            val live = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
            val queued = infos.any { it.state == WorkInfo.State.ENQUEUED }
            RunStatus(
                running = live != null || queued,
                label = live?.progress?.getString(TranscodeWorker.KEY_CURRENT).orEmpty(),
                fraction = live?.progress?.getFloat(TranscodeWorker.KEY_FRACTION, 0f) ?: 0f,
            )
        }

    data class RunStatus(val running: Boolean = false, val label: String = "", val fraction: Float = 0f)

    /** A retry after a thermal pause is a wait, not a failure; back off in minutes. */
    private const val COOLDOWN_MINUTES = 15L
}
