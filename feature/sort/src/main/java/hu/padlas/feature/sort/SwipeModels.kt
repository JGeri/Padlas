package hu.padlas.feature.sort

import android.net.Uri
import androidx.compose.runtime.Immutable
import hu.padlas.core.database.entity.SwipeChoice
import hu.padlas.core.database.model.SwipeCandidate
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Left, right, up. */
enum class SwipeAction(val choice: SwipeChoice) {
    DELETE(SwipeChoice.DELETE),
    KEEP(SwipeChoice.KEEP),
    FAVORITE(SwipeChoice.FAVORITE),
}

enum class MediaFilter { ALL, PHOTOS, VIDEOS }

enum class SwipeOrder { NEWEST, OLDEST, LARGEST, RANDOM }

enum class DeleteMode { TRASH, PERMANENT }

/** What the deck runs over. Every axis is optional; an empty filter means everything. */
@Immutable
data class SwipeFilter(
    val buckets: Set<Long> = emptySet(),
    /** "2023-02", local time, exactly as SQLite strftime('%Y-%m') produces it. */
    val months: Set<String> = emptySet(),
    val media: MediaFilter = MediaFilter.ALL,
    val onlyUnreviewed: Boolean = true,
    val order: SwipeOrder = SwipeOrder.NEWEST,
) {
    val hasSelection: Boolean get() = buckets.isNotEmpty() || months.isNotEmpty()
}

@Immutable
data class SwipeCard(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val folder: String,
    val sizeBytes: Long,
    val dateTaken: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long?,
    val isFavorite: Boolean,
    val isProtected: Boolean,
    /** The decision from an earlier run, when the deck includes reviewed items. */
    val previous: SwipeChoice?,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}

internal fun SwipeCandidate.toCard(): SwipeCard = SwipeCard(
    id = mediaStoreId,
    uri = Uri.parse(uri),
    displayName = displayName,
    mimeType = mimeType,
    folder = bucketName.ifBlank { relativePath.trimEnd('/').substringAfterLast('/') },
    sizeBytes = sizeBytes,
    dateTaken = dateTaken,
    width = width,
    height = height,
    durationMs = durationMs,
    isFavorite = isFavorite,
    isProtected = protectedManually,
    previous = choice?.let { runCatching { SwipeChoice.valueOf(it) }.getOrNull() },
)

internal object HungarianDates {

    private val locale: Locale = Locale.forLanguageTag("hu-HU")
    private val fullDate = DateTimeFormatter.ofPattern("yyyy. MMMM d., EEEE", locale)
    private val monthYear = DateTimeFormatter.ofPattern("yyyy. MMMM", locale)
    private val monthName = DateTimeFormatter.ofPattern("LLLL", locale)

    fun day(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(fullDate)

    /** "2023-02" to "2023. február"; an unparseable key is shown as it is. */
    fun month(key: String): String =
        runCatching { YearMonth.parse(key).format(monthYear) }.getOrDefault(key)

    /** "2023-02" to "Február". */
    fun monthName(key: String): String =
        runCatching {
            YearMonth.parse(key).format(monthName).replaceFirstChar { it.titlecase(locale) }
        }.getOrDefault(key)

    fun year(key: String): Int = key.substringBefore('-').toIntOrNull() ?: 0
}
