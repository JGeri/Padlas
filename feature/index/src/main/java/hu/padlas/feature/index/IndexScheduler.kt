package hu.padlas.feature.index

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Everything that starts index work goes through here, including the Vault kill switch. */
object IndexScheduler {

    const val WORK_FULL = "padlas.index.full"
    const val WORK_INCREMENTAL = "padlas.index.incremental"

    fun enqueueFullScan(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_FULL,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<IndexWorker>()
                .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build(),
        )
    }

    fun enqueueIncremental(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_INCREMENTAL,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<IncrementalIndexWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        // Indexing never leaves the device, so a network constraint would
                        // only delay it for no reason.
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build(),
        )
    }

    /** The MediaStore observer fired: sync now rather than waiting out the six hours. */
    fun enqueueIncrementalNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_INCREMENTAL_NOW,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<IncrementalIndexWorker>()
                .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build(),
        )
    }

    /** INV-7: one switch stops every background pass this module owns. */
    fun cancelAll(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(WORK_FULL)
        manager.cancelUniqueWork(WORK_INCREMENTAL)
        manager.cancelUniqueWork(WORK_INCREMENTAL_NOW)
    }

    private const val WORK_INCREMENTAL_NOW = "padlas.index.incremental.now"
    private const val PERIOD_HOURS = 6L
    private const val BACKOFF_SECONDS = 30L
}
