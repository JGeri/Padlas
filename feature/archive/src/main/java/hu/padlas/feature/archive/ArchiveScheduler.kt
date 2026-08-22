package hu.padlas.feature.archive

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import hu.padlas.feature.archive.work.CardUploadWorker
import hu.padlas.feature.archive.work.ReconcileWorker
import hu.padlas.feature.archive.work.UploadWorker
import java.util.concurrent.TimeUnit

/**
 * Uploads only run on unmetered power: a two terabyte archive over mobile data is not a
 * bug the user gets to discover on their bill. Android 15 also caps `dataSync` foreground
 * work at roughly six hours a day, which is why the onboarding asks for the first bulk
 * upload to happen from a desktop and leaves the delta to this scheduler.
 */
object ArchiveScheduler {

    const val TAG = "padlas"
    const val WORK_UPLOAD = "padlas.archive.upload"
    const val WORK_CARDS = "padlas.archive.cards"
    const val WORK_RECONCILE = "padlas.archive.reconcile"

    private val uploadConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresCharging(true)
        .setRequiresBatteryNotLow(true)
        .build()

    fun enqueueUploads(context: Context) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(uploadConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_UPLOAD, ExistingWorkPolicy.KEEP, request)
    }

    fun enqueueCardUpload(context: Context) {
        val request = OneTimeWorkRequestBuilder<CardUploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_CARDS, ExistingWorkPolicy.KEEP, request)
    }

    fun enqueueReconcile(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReconcileWorker>(7, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_RECONCILE, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** INV-7: the Vault kill switch has to be able to stop everything this module started. */
    fun cancelAll(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(WORK_UPLOAD)
        manager.cancelUniqueWork(WORK_CARDS)
        manager.cancelUniqueWork(WORK_RECONCILE)
    }
}
