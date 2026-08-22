package hu.padlas.core.media

import hu.padlas.core.database.entity.ClassifiedBy
import hu.padlas.core.database.entity.ValueClass

data class ClassificationInput(
    val relativePath: String,
    val displayName: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val hasCameraExif: Boolean,
    val isFavorite: Boolean,
    val isVideo: Boolean,
)

data class Classification(val valueClass: ValueClass, val classifiedBy: String)

/**
 * Spec 1.5. Every signal is cheap and pure: no framework call, no file access, so the
 * indexer can run it on 25 000 rows inside the cursor loop.
 */
object AssetClassifier {

    fun classify(candidate: ClassificationInput): Classification {
        // A favourite is a deliberate user act, so it is recorded as MANUAL and the
        // later EXIF/hash passes will not overwrite it (INV-4).
        if (candidate.isFavorite) {
            return Classification(ValueClass.IRREPLACEABLE, ClassifiedBy.MANUAL)
        }
        if (candidate.hasCameraExif) {
            return Classification(ValueClass.IRREPLACEABLE, ClassifiedBy.EXIF)
        }

        val segments = segmentsOf(candidate.relativePath)
        if (isScreenshotFolder(segments) || isScreenshotName(candidate.displayName)) {
            return Classification(ValueClass.EPHEMERAL, ClassifiedBy.PATH)
        }
        // Resolution beats path deliberately: a screenshot stays a screenshot after it was
        // renamed or moved into a "Sent" folder.
        if (isDisplayResolution(candidate.width, candidate.height)) {
            return Classification(ValueClass.EPHEMERAL, ClassifiedBy.RESOLUTION)
        }
        pathClass(segments)?.let { return Classification(it, ClassifiedBy.PATH) }
        if (isGeneratedImage(candidate)) {
            return Classification(ValueClass.EPHEMERAL, ClassifiedBy.MIME)
        }
        return Classification(ValueClass.REPLACEABLE, ClassifiedBy.PATH)
    }

    /** Lowercased, empty-free path segments. MediaStore reports "DCIM/Camera/". */
    private fun segmentsOf(relativePath: String): List<String> =
        relativePath.lowercase().split('/').filter { it.isNotEmpty() }

    private fun isScreenshotFolder(segments: List<String>): Boolean =
        segments.any { it in SCREENSHOT_FOLDERS }

    private fun isScreenshotName(displayName: String): Boolean =
        displayName.startsWith("screenshot", ignoreCase = true)

    private fun pathClass(segments: List<String>): ValueClass? {
        if (segments.isEmpty()) return null
        // ".../Sent" is what you shared, not what you received - most cleaners get this wrong.
        if (segments.last() == SENT_FOLDER) return ValueClass.IRREPLACEABLE
        if (isCameraFolder(segments)) return ValueClass.IRREPLACEABLE
        if (segments.any { segment -> MESSENGER_TOKENS.any { segment.contains(it) } }) {
            return ValueClass.REPLACEABLE
        }
        if (segments.any { it in DOWNLOAD_FOLDERS }) return ValueClass.REPLACEABLE
        return null
    }

    private fun isCameraFolder(segments: List<String>): Boolean {
        val dcim = segments.indexOf("dcim")
        return dcim >= 0 && segments.getOrNull(dcim + 1) == "camera"
    }

    private fun isGeneratedImage(candidate: ClassificationInput): Boolean =
        !candidate.isVideo &&
            candidate.mimeType.equals(MIME_PNG, ignoreCase = true) &&
            isScreenAspect(candidate.width, candidate.height)

    /** Orientation independent exact match against known Samsung panel sizes. */
    fun isDisplayResolution(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        return resolutionKey(minOf(width, height), maxOf(width, height)) in DISPLAY_RESOLUTIONS
    }

    /**
     * Tall phone-panel aspect. 16:9 (1,78) is excluded on purpose because it collides with
     * ordinary video and camera output.
     */
    fun isScreenAspect(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        val ratio = maxOf(width, height).toFloat() / minOf(width, height).toFloat()
        return ratio in MIN_SCREEN_RATIO..MAX_SCREEN_RATIO
    }

    private fun resolutionKey(shortSide: Int, longSide: Int): Long =
        (shortSide.toLong() shl 32) or longSide.toLong()

    private const val MIME_PNG = "image/png"
    private const val SENT_FOLDER = "sent"
    private const val MIN_SCREEN_RATIO = 1.90f
    private const val MAX_SCREEN_RATIO = 2.45f

    private val SCREENSHOT_FOLDERS = setOf(
        "screenshots",
        "screenshot",
        "screen recordings",
        "screenrecordings",
    )

    private val MESSENGER_TOKENS = listOf("whatsapp", "telegram")

    private val DOWNLOAD_FOLDERS = setOf("download", "downloads")

    private val DISPLAY_RESOLUTIONS: Set<Long> = setOf(
        // QHD+ flagships
        resolutionKey(1440, 3088),
        resolutionKey(1440, 3120),
        resolutionKey(1440, 3200),
        resolutionKey(1440, 3040),
        resolutionKey(1440, 2960),
        // FHD+ flagships and A series
        resolutionKey(1080, 2316),
        resolutionKey(1080, 2340),
        resolutionKey(1080, 2400),
        resolutionKey(1080, 2280),
        resolutionKey(1080, 2220),
        resolutionKey(1080, 2640),
        // HD+ budget panels
        resolutionKey(720, 1480),
        resolutionKey(720, 1520),
        resolutionKey(720, 1560),
        resolutionKey(720, 1600),
        // Foldable cover screens
        resolutionKey(904, 2316),
        resolutionKey(968, 2376),
        // Foldable inner screens - squarish, so only the exact match can catch them
        resolutionKey(1768, 2208),
        resolutionKey(1812, 2176),
        resolutionKey(1856, 2160),
    )
}
