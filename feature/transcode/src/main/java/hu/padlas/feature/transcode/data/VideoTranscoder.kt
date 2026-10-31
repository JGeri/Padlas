package hu.padlas.feature.transcode.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class TranscodeRequest(
    val source: Uri,
    val output: File,
    val sourceBytes: Long,
    val durationMs: Long,
)

sealed interface ExportOutcome {
    data class Success(val resultBytes: Long, val durationMs: Long) : ExportOutcome
    /** [reason] is a [TranscodeFailure] name, or the encoder's own message. */
    data class Failure(val reason: String) : ExportOutcome {
        constructor(failure: TranscodeFailure) : this(failure.name)
    }
}

/**
 * Media3 Transformer, not raw MediaCodec: the fallback logic around a hardware encoder
 * that refuses a profile is the part that takes months to get right, and Transformer
 * already has it. Transformer is bound to the Looper it was built on, so the whole
 * session lives on the main thread and the encoder does its work off it anyway.
 */
@Singleton
class VideoTranscoder @Inject constructor(@ApplicationContext private val context: Context) {

    @androidx.annotation.OptIn(UnstableApi::class)
    suspend fun transcode(
        request: TranscodeRequest,
        onProgress: (Float) -> Unit = {},
    ): ExportOutcome = withContext(Dispatchers.Main.immediate) {
        request.output.parentFile?.mkdirs()
        if (request.output.exists()) request.output.delete()

        var settled = false
        val transformer = buildTransformer(request)
        try {
            coroutineScope {
                val progress = launch {
                    val holder = ProgressHolder()
                    while (isActive) {
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(holder.progress / 100f)
                        }
                        delay(PROGRESS_POLL_MS)
                    }
                }
                try {
                    awaitExport(transformer, request) { settled = true }
                } finally {
                    progress.cancel()
                }
            }
        } finally {
            if (!settled) {
                runCatching { transformer.cancel() }
                request.output.delete()
            }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun buildTransformer(request: TranscodeRequest): Transformer {
        val bitrate = TranscodeMath.targetBitrateBps(request.sourceBytes, request.durationMs)
        val encoders = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
            // Without fallback a device that refuses HEVC at this bitrate loses the whole item.
            .setEnableFallback(true)
            .build()
        return Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H265)
            .setEncoderFactory(encoders)
            .build()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private suspend fun awaitExport(
        transformer: Transformer,
        request: TranscodeRequest,
        onSettled: () -> Unit,
    ): ExportOutcome = suspendCancellableCoroutine { continuation ->
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                onSettled()
                val bytes = request.output.length()
                if (bytes <= 0L) {
                    continuation.resume(ExportOutcome.Failure(TranscodeFailure.EMPTY_OUTPUT))
                    return
                }
                continuation.resume(ExportOutcome.Success(bytes, exportResult.durationMs))
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                onSettled()
                continuation.resume(ExportOutcome.Failure(exportException.message ?: TranscodeFailure.ENCODER_REFUSED.name))
            }
        }
        transformer.addListener(listener)
        continuation.invokeOnCancellation { transformer.removeListener(listener) }
        val item = EditedMediaItem.Builder(MediaItem.fromUri(request.source)).build()
        runCatching { transformer.start(item, request.output.absolutePath) }
            .onFailure { failure ->
                onSettled()
                continuation.resume(ExportOutcome.Failure(failure.message ?: TranscodeFailure.ENCODER_REFUSED.name))
            }
    }

    private companion object {
        const val PROGRESS_POLL_MS = 400L
    }
}
