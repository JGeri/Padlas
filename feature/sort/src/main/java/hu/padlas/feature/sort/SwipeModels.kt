package hu.padlas.feature.sort

import android.net.Uri
import androidx.compose.runtime.Immutable
import hu.padlas.core.database.entity.SwipeChoice
import hu.padlas.core.database.model.SwipeCandidate
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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

/**
 * Dates in the app's language. Hungarian puts the year first ("2023. február 3., péntek"),
 * spelled out here so it reads exactly as it always has; every other language gets its own
 * long date and a "February 2023" style month.
 */
internal object SwipeDates {

    fun day(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(patterns(locale).fullDate)

    /** "2023-02" to "2023. február" or "February 2023"; an unparseable key is shown as it is. */
    fun month(key: String, locale: Locale = Locale.getDefault()): String =
        runCatching { YearMonth.parse(key).format(patterns(locale).monthYear) }.getOrDefault(key)

    /** "2023-02" to "Február" or "February". */
    fun monthName(key: String, locale: Locale = Locale.getDefault()): String =
        runCatching {
            YearMonth.parse(key).format(patterns(locale).monthName).replaceFirstChar { it.titlecase(locale) }
        }.getOrDefault(key)

    fun year(key: String): Int = key.substringBefore('-').toIntOrNull() ?: 0

    /** The language can change while the app runs, so the formatters follow the locale asked for. */
    @Volatile
    private var cached: Patterns? = null

    private fun patterns(locale: Locale): Patterns =
        cached?.takeIf { it.locale == locale } ?: Patterns(locale).also { cached = it }

    private class Patterns(val locale: Locale) {
        private val hungarian = locale.language == "hu"

        val fullDate: DateTimeFormatter = if (hungarian) {
            DateTimeFormatter.ofPattern("yyyy. MMMM d., EEEE", locale)
        } else {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
        }

        /** Stand-alone month names, because some languages inflect the month inside a date. */
        val monthYear: DateTimeFormatter =
            DateTimeFormatter.ofPattern(if (hungarian) "yyyy. MMMM" else "LLLL yyyy", locale)

        val monthName: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL", locale)
    }
}
