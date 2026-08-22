package hu.padlas.feature.transcode.data

import android.media.MediaMetadataRetriever
import hu.padlas.core.common.di.IoDispatcher
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

sealed interface TrialResult {
    data class Playable(val durationMs: Long, val rotationDegrees: Int, val width: Int, val height: Int) : TrialResult
    data class Broken(val reason: String) : TrialResult
}

/**
 * A file that the muxer wrote is not the same thing as a file that plays. Nothing may be
 * deleted on the strength of an encoder's exit code, so the output is opened, measured
 * and forced to hand back a real frame before the original becomes removable.
 */
class TrialDecoder @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun verify(output: File, expectedDurationMs: Long): TrialResult = withContext(io) {
        if (!output.exists() || output.length() <= 0L) return@withContext TrialResult.Broken(MISSING)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(output.absolutePath)
            val duration = retriever.longMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (!TranscodeMath.durationWithinTolerance(expectedDurationMs, duration)) {
                return@withContext TrialResult.Broken(TRUNCATED)
            }
            val width = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (width <= 0 || height <= 0) return@withContext TrialResult.Broken(NO_VIDEO_TRACK)
            val rotation = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

            // A frame from the middle, not from zero: a broken file often still yields
            // its first keyframe and then falls apart.
            val midpointUs = duration * 500L
            val frame = retriever.getFrameAtTime(midpointUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (frame == null) {
                TrialResult.Broken(NO_FRAME)
            } else {
                frame.recycle()
                TrialResult.Playable(duration, rotation, width, height)
            }
        } catch (failure: Exception) {
            TrialResult.Broken(failure.message ?: UNREADABLE)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun MediaMetadataRetriever.longMetadata(key: Int): Long =
        extractMetadata(key)?.toLongOrNull() ?: 0L

    private fun MediaMetadataRetriever.intMetadata(key: Int): Int =
        extractMetadata(key)?.toIntOrNull() ?: 0

    private companion object {
        const val MISSING = "A kimeneti fájl nem jött létre."
        const val TRUNCATED = "A kimenet rövidebb az eredetinél."
        const val NO_VIDEO_TRACK = "A kimenetben nincs videósáv."
        const val NO_FRAME = "A kimenetből nem sikerült képkockát kinyerni."
        const val UNREADABLE = "A kimenet nem olvasható vissza."
    }
}
