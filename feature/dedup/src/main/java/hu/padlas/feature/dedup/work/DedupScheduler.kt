package hu.padlas.feature.dedup.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The app module talks to the duplicate scan only through here. `cancelAll` is what the
 * Vault kill switch reaches (INV-7).
 */
object DedupScheduler {

    const val WORK_SCAN = "padlas.dedup.scan"

    private const val TAG = "padlas.dedup"
    private const val BACKOFF_MINUTES = 1L

    /**
     * Hashing spins the disk and the CPU for minutes, so it waits for a battery that can
     * take it. No network constraint: nothing here leaves the device.
     */
    fun enqueueScan(context: Context) {
        val request = OneTimeWorkRequestBuilder<DupWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_SCAN, ExistingWorkPolicy.KEEP, request)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    /** True while a scan is enqueued or running, so the screen can say so before the first hash lands. */
    fun observeScan(context: Context): Flow<Boolean> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(WORK_SCAN)
            .map { infos -> infos.any { !it.state.isFinished } }
}
