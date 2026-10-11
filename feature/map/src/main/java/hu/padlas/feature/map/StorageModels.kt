package hu.padlas.feature.map

import androidx.compose.runtime.Immutable
import hu.padlas.core.common.format.Format
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal enum class StorageView(val label: String) {
    MAP("Térkép"),
    FOLDERS("Mappák"),
    TYPES("Típusok"),
    FILES("Fájlok"),
    APPS("Appok"),
}

internal enum class Cleanup(val title: String, val description: String) {
    INSTALLERS(
        "Telepítő fájlok (APK)",
        "Ha az app már fent van a telefonon, a telepítőjére nincs többé szükség.",
    ),
    OLD_DOWNLOADS(
        "Régi letöltések",
        "A Letöltések mappa 90 napnál régebbi fájljai.",
    ),
    THUMBNAILS(
        "Bélyegkép-gyorsítótár (.thumbnails)",
        "Előnézeti képek. A galéria szükség esetén újra elkészíti őket.",
    ),
    EMPTY_DIRS(
        "Üres mappák",
        "Mappák, amelyekben egyetlen fájl sincs.",
    ),
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
    val label: String,
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
internal data class ToastUi(val message: String, val tone: ToastTone)

/** "3 fájl", "2 mappa", "2 mappa és 3 fájl": what a selection actually holds. */
internal fun describeCount(files: Int, dirs: Int): String = when {
    dirs == 0 -> "${Format.count(files)} fájl"
    files == 0 -> "${Format.count(dirs)} mappa"
    else -> "${Format.count(dirs)} mappa és ${Format.count(files)} fájl"
}

/** Paths are shown relative to the card; the absolute prefix is noise to the user. */
internal fun displayPath(path: String, rootPath: String): String {
    if (path == rootPath || rootPath.isEmpty()) return DirectoryListing.ROOT_LABEL
    return path.removePrefix("$rootPath/")
}

internal object TimeText {

    private val months = arrayOf(
        "jan.", "febr.", "márc.", "ápr.", "máj.", "jún.", "júl.", "aug.", "szept.", "okt.", "nov.", "dec.",
    )
    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    /** "az imént", "12 perce", "ma 14:32", "tegnap 09:10", "márc. 3." */
    fun relative(millis: Long, now: Long = System.currentTimeMillis()): String {
        val zone = ZoneId.systemDefault()
        val then = Instant.ofEpochMilli(millis).atZone(zone)
        val minutes = (now - millis) / 60_000L
        val today = LocalDate.now(zone)
        val days = ChronoUnit.DAYS.between(then.toLocalDate(), today)
        return when {
            minutes < 1 -> "az imént"
            minutes < 60 -> "${Format.count(minutes)} perce"
            days == 0L -> "ma ${clock.format(then)}"
            days == 1L -> "tegnap ${clock.format(then)}"
            else -> date(millis)
        }
    }

    /** "2024. márc. 3."; the year is left off for this year. */
    fun date(millis: Long): String {
        if (millis <= 0L) return ""
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val short = "${months[day.monthValue - 1]} ${day.dayOfMonth}."
        return if (day.year == LocalDate.now(zone).year) short else "${day.year}. $short"
    }
}
