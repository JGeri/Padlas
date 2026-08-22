package hu.padlas.feature.triage

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * One triage pass. The budget keeps a single run well inside the WorkManager execution
 * window; if the queue is still not empty the worker appends its own successor rather
 * than trying to swallow 25 000 assets in one go.
 */
@HiltWorker
class TriageWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val triage: ScreenshotTriage,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val budget = inputData.getInt(KEY_BUDGET, DEFAULT_BUDGET)
        return try {
            val done = triage.runPass(budget) { progress, total ->
                setProgress(workDataOf(KEY_DONE to progress, KEY_BUDGET to total))
            }
            if (done >= budget) TriageScheduler.enqueueMore(applicationContext)
            Result.success(workDataOf(KEY_DONE to done))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_DONE = "triage.done"
        const val KEY_BUDGET = "triage.budget"
        const val DEFAULT_BUDGET = 600
        private const val MAX_ATTEMPTS = 3
    }
}
