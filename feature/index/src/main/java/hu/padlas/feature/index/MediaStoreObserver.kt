package hu.padlas.feature.index

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.padlas.core.common.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.shareIn

/**
 * A change signal for the shared media volume. The app collects it and asks
 * [IndexScheduler] for an incremental sync.
 *
 * Deliberately not `MediaStore.getGeneration()`: on Samsung the generation counter does
 * not reliably move for every write (in-place rewrites and Gallery/OneDrive round trips
 * both miss it), so the index compares `dateModified` plus `sizeBytes` per row instead and
 * treats this observer purely as a "something happened" nudge (spec 2.3).
 */
@Singleton
class MediaStoreObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {

    @OptIn(FlowPreview::class)
    val changes: Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            true,
            observer,
        )
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }
        // A burst shot fires this dozens of times in a second; one sync per quiet period.
        .conflate()
        .debounce(SETTLE_MS)
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 0)

    private companion object {
        const val SETTLE_MS = 4_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
