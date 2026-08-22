package hu.padlas.feature.triage

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Triage is pure local compute: no network, and never on a battery the user is about to
 * need. It is also cancellable from the Vault kill switch (INV-7) through [cancelAll].
 */
object TriageScheduler {

    const val WORK_TRIAGE = "padlas.triage.pass"
    const val TAG_TRIAGE = "padlas.triage"

    fun enqueueTriage(context: Context) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_TRIAGE, ExistingWorkPolicy.KEEP, request())
    }

    /** Continuation of a pass that used up its budget; appended behind the running one. */
    internal fun enqueueMore(context: Context) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_TRIAGE, ExistingWorkPolicy.APPEND, request())
    }

    fun cancelAll(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(WORK_TRIAGE)
        manager.cancelAllWorkByTag(TAG_TRIAGE)
    }

    private fun request(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<TriageWorker>()
        .addTag(TAG_TRIAGE)
        .setInputData(workDataOf(TriageWorker.KEY_BUDGET to TriageWorker.DEFAULT_BUDGET))
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build(),
        )
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()
}
