package hu.padlas.feature.transcode.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.database.entity.Asset
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Moves the finished file out of the app cache and into the gallery. DATE_TAKEN and the
 * rotation hint are carried over by hand: losing them turns a re-encoded holiday into a
 * pile of clips dated today, which is a worse outcome than not compressing at all.
 */
class TranscodeOutputPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun publish(original: Asset, output: File, trial: TrialResult.Playable): Uri? = withContext(io) {
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, desiredName(original.displayName))
            put(MediaStore.Video.Media.MIME_TYPE, OUTPUT_MIME)
            put(MediaStore.Video.Media.RELATIVE_PATH, original.relativePath)
            put(MediaStore.Video.Media.DATE_TAKEN, original.dateTaken)
            put(MediaStore.Video.Media.ORIENTATION, trial.rotationDegrees)
            put(MediaStore.Video.Media.DURATION, trial.durationMs)
            put(MediaStore.Video.Media.WIDTH, trial.width)
            put(MediaStore.Video.Media.HEIGHT, trial.height)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val target = resolver.insert(collection, values) ?: return@withContext null
        val copied = runCatching {
            resolver.openOutputStream(target)?.use { sink -> output.inputStream().use { it.copyTo(sink) } }
        }.isSuccess
        if (!copied) {
            runCatching { resolver.delete(target, null, null) }
            return@withContext null
        }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        runCatching { resolver.update(target, values, null, null) }
        target
    }

    /**
     * Called once the original has left the gallery, so the compressed file can take the
     * name back. Best effort: a failed rename costs a suffix, not a file.
     */
    suspend fun reclaimName(target: Uri, displayName: String) = withContext(io) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, desiredName(displayName))
        }
        runCatching { context.contentResolver.update(target, values, null, null) }
        Unit
    }

    private fun desiredName(displayName: String): String = displayName.substringBeforeLast('.') + ".mp4"

    private companion object {
        const val OUTPUT_MIME = "video/mp4"
    }
}
