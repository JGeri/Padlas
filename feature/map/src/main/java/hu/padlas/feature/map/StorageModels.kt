package hu.padlas.feature.map

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import hu.padlas.core.common.format.Format
import hu.padlas.core.common.text.UiText
import hu.padlas.core.common.text.uiPlural
import hu.padlas.core.common.text.uiText
import hu.padlas.core.design.component.asString
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal enum class StorageView(@StringRes val label: Int) {
    MAP(R.string.map_view_map),
    FOLDERS(R.string.map_view_folders),
    TYPES(R.string.map_view_types),
    FILES(R.string.map_view_files),
    APPS(R.string.map_view_apps),
}

internal enum class Cleanup(@StringRes val title: Int, @StringRes val description: Int) {
    INSTALLERS(R.string.map_cleanup_installers_title, R.string.map_cleanup_installers_text),
    OLD_DOWNLOADS(R.string.map_cleanup_old_downloads_title, R.string.map_cleanup_old_downloads_text),
    THUMBNAILS(R.string.map_cleanup_thumbnails_title, R.string.map_cleanup_thumbnails_text),
    EMPTY_DIRS(R.string.map_cleanup_empty_dirs_title, R.string.map_cleanup_empty_dirs_text),
}

@Immutable
internal data class Access(val allFiles: Boolean, val usage: Boolean)

@Immutable
internal data class CleanupSummary(val kind: Cleanup, val count: Int, val bytes: Long)

@Immutable
internal data class FilesUi(
    val filter: FileCategory?,
    val cleanup: Cleanup?,
    val items: List<StorageItem>,
    val shortcuts: List<CleanupSummary>,
)

@Immutable
internal data class TypesUi(
    val totalBytes: Long,
    val categories: List<CategoryTotal>,
    val extensions: List<ExtensionTotal>,
)

internal enum class SliceKind { FILES, APPS, SYSTEM }

@Immutable
internal data class SummarySlice(
    @StringRes val label: Int,
    val bytes: Long,
    val kind: SliceKind,
    val category: FileCategory? = null,
)

@Immutable
internal data class SummaryUi(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val slices: List<SummarySlice>,
)

@Immutable
internal data class AppsUi(
    val hasAccess: Boolean,
    val loading: Boolean,
    val apps: List<AppEntry>,
    val cacheStops: List<AppEntry>,
    val tourIndex: Int,
)

@Immutable
internal sealed interface Focus {
    val parentBytes: Long

    @Immutable
    data class Item(val item: StorageItem, override val parentBytes: Long) : Focus

    @Immutable
    data class Rest(val count: Int, val bytes: Long, override val parentBytes: Long) : Focus
}

@Immutable
internal data class SelectionUi(val items: Map<String, StorageItem> = emptyMap()) {
    val active: Boolean get() = items.isNotEmpty()
    val count: Int get() = items.size
    val bytes: Long = items.values.sumOf { it.bytes }

    operator fun contains(path: String): Boolean = path in items
}

@Immutable
internal data class DeleteRequest(val items: List<StorageItem>) {
    val bytes: Long = items.sumOf { it.bytes }
    val dirCount: Int = items.count { it.isDirectory }
    val fileCount: Int = items.size - dirCount
}

internal enum class ToastTone { SUCCESS, WARNING, ERROR }

@Immutable
internal data class ToastUi(val message: UiText, val tone: ToastTone)

/** "3 files", "2 folders", "2 folders and 3 files": what a selection actually holds. */
internal fun describeCount(files: Int, dirs: Int): UiText {
    val fileText = uiPlural(R.plurals.map_count_files, files, Format.count(files))
    val dirText = uiPlural(R.plurals.map_count_folders, dirs, Format.count(dirs))
    return when {
        dirs == 0 -> fileText
        files == 0 -> dirText
        else -> uiText(R.string.map_count_folders_and_files, dirText, fileText)
    }
}

/** Paths are shown relative to the card; the absolute prefix is noise to the user. */
@Composable
internal fun displayPath(path: String, rootPath: String): String {
    if (path == rootPath || rootPath.isEmpty()) return DirectoryListing.ROOT_LABEL.asString()
    return path.removePrefix("$rootPath/")
}

/**
 * The patterns live in the resources as java.util.Formatter date conversions: a time stays
 * a [UiText] like any other sentence, and month names follow the language it resolves in.
 */
internal object TimeText {

    /** "just now", "12 minutes ago", "today at 2:32 PM", "yesterday at 9:10 AM", "Mar 3" */
    fun relative(millis: Long, now: Long = System.currentTimeMillis()): UiText {
        val zone = ZoneId.systemDefault()
        val then = Instant.ofEpochMilli(millis).atZone(zone)
        val minutes = (now - millis) / 60_000L
        val today = LocalDate.now(zone)
        val days = ChronoUnit.DAYS.between(then.toLocalDate(), today)
        return when {
            minutes < 1 -> uiText(R.string.map_time_just_now)
            minutes < 60 -> uiPlural(R.plurals.map_time_minutes_ago, minutes.toInt(), Format.count(minutes))
            days == 0L -> uiText(R.string.map_time_today, millis)
            days == 1L -> uiText(R.string.map_time_yesterday, millis)
            else -> date(millis)
        }
    }

    /** "Mar 3, 2024"; the year is left off for this year. Empty for an unknown date. */
    fun date(millis: Long): UiText {
        if (millis <= 0L) return UiText.Raw("")
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val pattern = if (day.year == LocalDate.now(zone).year) R.string.map_date_this_year else R.string.map_date_other_year
        return uiText(pattern, millis)
    }
}
