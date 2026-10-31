package hu.padlas

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import dagger.hilt.android.HiltAndroidApp
import hu.padlas.image.MediaThumbFetcher
import hu.padlas.image.MediaThumbKeyer
import javax.inject.Inject

@HiltAndroidApp
class PadlasApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        createWorkChannel()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(MediaThumbKeyer())
                add(MediaThumbFetcher.Factory(this@PadlasApp))
                // Registered explicitly as well: R8 can drop service-loader entries.
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, MEMORY_CACHE_SHARE)
                    .build()
            }
            .crossfade(CROSSFADE_MS)
            .build()

    private fun createWorkChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            WORK_CHANNEL_ID,
            getString(R.string.app_work_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.app_work_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val WORK_CHANNEL_ID = "padlas.work"
        private const val MEMORY_CACHE_SHARE = 0.25
        private const val CROSSFADE_MS = 160
    }
}
