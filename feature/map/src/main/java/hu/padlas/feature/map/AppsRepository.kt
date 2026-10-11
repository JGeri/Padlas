package hu.padlas.feature.map

import android.content.Context
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.media.StorageProbe
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Immutable
internal data class AppEntry(
    val packageName: String,
    val label: String,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
) {
    val totalBytes: Long get() = appBytes + dataBytes + cacheBytes
}

internal data class AppsSnapshot(val apps: List<AppEntry>, val measuredAt: Long)

/** Asking StorageStatsManager about every package takes seconds, so the answer is kept. */
@Singleton
internal class AppsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val probe: StorageProbe,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val _apps = MutableStateFlow<AppsSnapshot?>(null)
    val apps: StateFlow<AppsSnapshot?> = _apps.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val refreshLock = Mutex()
    private val icons = LruCache<String, ImageBitmap>(ICON_CACHE)

    fun hasAccess(): Boolean = probe.hasUsageStatsPermission()

    suspend fun refresh(force: Boolean) {
        if (!hasAccess()) {
            _apps.value = null
            return
        }
        refreshLock.withLock {
            val current = _apps.value
            // Coming back from an app's settings page usually means a cache was just cleared.
            val fresh = current != null && System.currentTimeMillis() - current.measuredAt < FRESH_MS
            if (!force && fresh) return
            _loading.value = true
            try {
                val measured = runCatching { probe.appUsage() }.getOrDefault(emptyList())
                    .map { AppEntry(it.packageName, it.label, it.appBytes, it.dataBytes, it.cacheBytes) }
                    .filter { it.totalBytes > 0L }
                    .sortedByDescending { it.totalBytes }
                _apps.value = AppsSnapshot(measured, System.currentTimeMillis())
            } finally {
                _loading.value = false
            }
        }
    }

    /** Rasterised once off the main thread; adaptive icons are expensive to draw per frame. */
    suspend fun icon(packageName: String, sizePx: Int): ImageBitmap? {
        icons.get(packageName)?.let { return it }
        return withContext(io) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(sizePx, sizePx)
                    .asImageBitmap()
            }.getOrNull()?.also { icons.put(packageName, it) }
        }
    }

    private companion object {
        const val ICON_CACHE = 160
        const val FRESH_MS = 30_000L
    }
}
