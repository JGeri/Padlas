package hu.padlas.feature.transcode.data

import android.media.MediaMetadataRetriever
import hu.padlas.core.common.di.IoDispatcher
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

sealed interface TrialResult {
    data class Playable(val durationMs: Long, val rotationDegrees: Int, val width: Int, val height: Int) : TrialResult
    /** [reason] is a [TranscodeFailure] name, or the decoder's own message. */
    data class Broken(val reason: String) : TrialResult {
        constructor(failure: TranscodeFailure) : this(failure.name)
    }
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
        if (!output.exists() || output.length() <= 0L) return@withContext TrialResult.Broken(TranscodeFailure.MISSING_OUTPUT)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(output.absolutePath)
            val duration = retriever.longMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (!TranscodeMath.durationWithinTolerance(expectedDurationMs, duration)) {
                return@withContext TrialResult.Broken(TranscodeFailure.TRUNCATED)
            }
            val width = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (width <= 0 || height <= 0) return@withContext TrialResult.Broken(TranscodeFailure.NO_VIDEO_TRACK)
            val rotation = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

            // A frame from the middle, not from zero: a broken file often still yields
            // its first keyframe and then falls apart.
            val midpointUs = duration * 500L
            val frame = retriever.getFrameAtTime(midpointUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (frame == null) {
                TrialResult.Broken(TranscodeFailure.NO_FRAME)
            } else {
                frame.recycle()
                TrialResult.Playable(duration, rotation, width, height)
            }
        } catch (failure: Exception) {
            TrialResult.Broken(failure.message ?: TranscodeFailure.UNREADABLE.name)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun MediaMetadataRetriever.longMetadata(key: Int): Long =
        extractMetadata(key)?.toLongOrNull() ?: 0L

    private fun MediaMetadataRetriever.intMetadata(key: Int): Int =
        extractMetadata(key)?.toIntOrNull() ?: 0
}
