package hu.padlas.feature.index

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import hu.padlas.core.common.result.Outcome

/** First run and every manual re-scan: the whole cursor, from zero. */
@HiltWorker
class IndexWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: IndexRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = repository.runFullScan().toWorkerResult(runAttemptCount)
}

/**
 * The periodic delta pass. Reads only rows newer than the watermark and reconciles what
 * disappeared, so it stays cheap enough to run on a battery-not-low constraint.
 */
@HiltWorker
class IncrementalIndexWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: IndexRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = repository.runIncremental().toWorkerResult(runAttemptCount)
}

private const val MAX_ATTEMPTS = 3

private fun Outcome<*>.toWorkerResult(attempt: Int): androidx.work.ListenableWorker.Result = when (this) {
    is Outcome.Success -> androidx.work.ListenableWorker.Result.success()
    is Outcome.Failure ->
        if (attempt < MAX_ATTEMPTS) {
            androidx.work.ListenableWorker.Result.retry()
        } else {
            androidx.work.ListenableWorker.Result.failure()
        }
}
