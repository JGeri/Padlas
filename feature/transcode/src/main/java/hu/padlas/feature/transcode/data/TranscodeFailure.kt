package hu.padlas.feature.transcode.data

import androidx.annotation.StringRes
import hu.padlas.feature.transcode.R

/**
 * Why a video could not be compressed. A job row keeps the [name], not a sentence, so the
 * reason reads in whatever language the app runs in when the list is shown. A message the
 * encoder or the decoder wrote itself is stored and shown as it came.
 */
enum class TranscodeFailure(@StringRes val text: Int) {
    MISSING_ASSET(R.string.transcode_failure_missing_asset),
    NO_GAIN(R.string.transcode_failure_no_gain),
    RETRIAL_FAILED(R.string.transcode_failure_retrial_failed),
    MISSING_OUTPUT(R.string.transcode_failure_missing_output),
    TRUNCATED(R.string.transcode_failure_truncated),
    NO_VIDEO_TRACK(R.string.transcode_failure_no_video_track),
    NO_FRAME(R.string.transcode_failure_no_frame),
    UNREADABLE(R.string.transcode_failure_unreadable),
    EMPTY_OUTPUT(R.string.transcode_failure_empty_output),
    ENCODER_REFUSED(R.string.transcode_failure_encoder_refused),
}
