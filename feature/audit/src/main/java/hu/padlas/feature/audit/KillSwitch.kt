package hu.padlas.feature.audit

import android.content.Context
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * INV-7. Every feature tags its work `padlas`, so one call stops indexing, hashing,
 * transcoding and uploading at once. The tag sweep is backed up by a blanket cancel
 * because a kill switch that half works is worse than none at all.
 */
@Singleton
class KillSwitch @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun stopEverything() {
        val manager = WorkManager.getInstance(context)
        manager.cancelAllWorkByTag(TAG)
        manager.cancelAllWork()
    }

    companion object {
        const val TAG = "padlas"
    }
}
