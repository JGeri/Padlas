package hu.padlas.feature.transcode.data

import kotlin.math.roundToInt

/** Pure arithmetic of the video pipeline, kept out of the encoder so it stays testable. */
internal object TranscodeMath {

    /** Nothing under this is worth an encoder session. */
    const val MIN_CANDIDATE_BYTES = 80L * 1024 * 1024

    private const val TARGET_RATIO = 0.5f
    private const val MIN_BITRATE = 2_000_000
    private const val MAX_BITRATE = 40_000_000

    /**
     * HEVC at half the source bitrate is the 40-60% saving the spec promises; the clamp
     * keeps a badly reported duration from asking the encoder for something absurd.
     */
    fun targetBitrateBps(sourceBytes: Long, durationMs: Long): Int {
        if (sourceBytes <= 0 || durationMs <= 0) return MIN_BITRATE
        val sourceBps = sourceBytes.toDouble() * 8_000.0 / durationMs.toDouble()
        return (sourceBps * TARGET_RATIO).roundToInt().coerceIn(MIN_BITRATE, MAX_BITRATE)
    }

    fun savedBytes(originalBytes: Long, resultBytes: Long): Long =
        (originalBytes - resultBytes).coerceAtLeast(0L)

    /** 1.0 = nothing gained, 0.4 = the card shrinks to 40% of its height. */
    fun shrinkRatio(originalBytes: Long, resultBytes: Long): Float {
        if (originalBytes <= 0L || resultBytes <= 0L) return 1f
        return (resultBytes.toFloat() / originalBytes.toFloat()).coerceIn(0.1f, 1f)
    }

    /** A card's height is its share of the largest file in the list, never below the floor. */
    fun heightFraction(sizeBytes: Long, largestBytes: Long): Float {
        if (largestBytes <= 0L) return 1f
        return (sizeBytes.toFloat() / largestBytes.toFloat()).coerceIn(0.34f, 1f)
    }

    /** A trial decode is only believable if the output kept the source's length. */
    fun durationWithinTolerance(expectedMs: Long, actualMs: Long): Boolean {
        if (expectedMs <= 0L) return actualMs > 0L
        val tolerance = (expectedMs / 20L).coerceAtLeast(500L)
        return actualMs > 0L && kotlin.math.abs(actualMs - expectedMs) <= tolerance
    }
}
