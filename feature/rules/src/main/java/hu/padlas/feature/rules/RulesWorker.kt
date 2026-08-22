package hu.padlas.feature.rules

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class RulesWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val engine: RuleEngine,
    private val tracker: StorageDeltaTracker,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        // The snapshot runs first: even a week where no rule fires should leave a
        // measurement behind, otherwise the delta list has nothing to compare against.
        tracker.snapshot()
        engine.seedBuiltInsIfEmpty()
        engine.runEnabled()
    }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
}

object RulesScheduler {

    const val TAG = "padlas"
    const val WORK_WEEKLY = "padlas.rules.weekly"
    const val WORK_NOW = "padlas.rules.now"

    private val constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .build()

    fun enqueueWeekly(context: Context) {
        val request = PeriodicWorkRequestBuilder<RulesWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_WEEKLY, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RulesWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelAll(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(WORK_WEEKLY)
        manager.cancelUniqueWork(WORK_NOW)
    }
}
