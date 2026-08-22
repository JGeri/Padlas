package hu.padlas.feature.archive.drive

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ArchiveKind { PHOTO, VIDEO, DUP_LOSER, CARD }

/**
 * The folder layout on Drive is part of the disaster recovery story, not a convenience:
 * a dead phone has to be reconstructable from the Drive tree plus the per-folder
 * manifest alone, so the path is derived from the capture date and nothing else.
 */
object DrivePaths {

    const val ROOT = "Padlás"
    const val VIDEO = "_video"
    const val DUP_LOSERS = "_dup-losers"
    const val CARDS = "_cedula"
    const val UNDATED = "_datalan"
    const val MANIFEST = ".padlas-manifest.json"
    const val FOLDER_MIME = "application/vnd.google-apps.folder"

    private val YEAR = DateTimeFormatter.ofPattern("yyyy")
    private val MONTH = DateTimeFormatter.ofPattern("yyyy-MM")

    fun segments(
        kind: ArchiveKind,
        dateMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<String> = when (kind) {
        ArchiveKind.VIDEO -> listOf(ROOT, VIDEO)
        ArchiveKind.DUP_LOSER -> listOf(ROOT, DUP_LOSERS)
        ArchiveKind.CARD -> if (dateMillis <= 0L) {
            listOf(ROOT, CARDS, UNDATED)
        } else {
            listOf(ROOT, CARDS, yearOf(dateMillis, zone))
        }
        ArchiveKind.PHOTO -> if (dateMillis <= 0L) {
            listOf(ROOT, UNDATED)
        } else {
            listOf(ROOT, yearOf(dateMillis, zone), monthOf(dateMillis, zone))
        }
    }

    /** "/Padlás/2024/2024-07/" - the form the manifest and the user-facing copy use. */
    fun displayPath(segments: List<String>): String =
        segments.joinToString(separator = "/", prefix = "/", postfix = "/")

    fun yearOf(dateMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        YEAR.format(Instant.ofEpochMilli(dateMillis).atZone(zone))

    fun monthOf(dateMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        MONTH.format(Instant.ofEpochMilli(dateMillis).atZone(zone))

    /** Drive's query language has no escape sequence beyond this one. */
    fun escapeQueryLiteral(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'")
}
