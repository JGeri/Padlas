package hu.padlas.feature.sort

internal data class SwipeQuery(val sql: String, val args: List<Any>)

/**
 * The deck WHERE clause is assembled in Kotlin because Room cannot bind an empty `IN ()`
 * and every axis of the filter is optional. Every value is still a bound argument.
 */
internal object SwipeSql {

    /** Kept identical to the month aggregate in SwipeDao so a month cell matches its rows. */
    const val MONTH_EXPRESSION = "strftime('%Y-%m', a.dateTaken / 1000, 'unixepoch', 'localtime')"

    private const val FROM =
        "FROM asset a LEFT JOIN swipe_decision d ON d.assetId = a.mediaStoreId"

    private const val COLUMNS =
        "a.mediaStoreId AS mediaStoreId, a.uri AS uri, a.displayName AS displayName, " +
            "a.mimeType AS mimeType, a.relativePath AS relativePath, a.bucketName AS bucketName, " +
            "a.sizeBytes AS sizeBytes, a.dateTaken AS dateTaken, a.width AS width, " +
            "a.height AS height, a.durationMs AS durationMs, a.isFavorite AS isFavorite, " +
            "a.protectedManually AS protectedManually, d.choice AS choice"

    fun where(filter: SwipeFilter): SwipeQuery {
        val clauses = ArrayList<String>()
        val args = ArrayList<Any>()
        clauses += "a.state IN ('LOCAL', 'PROTECTED')"

        if (filter.buckets.isNotEmpty()) {
            clauses += "a.bucketId IN (${placeholders(filter.buckets.size)})"
            args.addAll(filter.buckets.sorted())
        }
        if (filter.months.isNotEmpty()) {
            clauses += "$MONTH_EXPRESSION IN (${placeholders(filter.months.size)})"
            args.addAll(filter.months.sorted())
        }
        when (filter.media) {
            MediaFilter.PHOTOS -> clauses += "a.mimeType LIKE 'image/%'"
            MediaFilter.VIDEOS -> clauses += "a.mimeType LIKE 'video/%'"
            MediaFilter.ALL -> clauses += "(a.mimeType LIKE 'image/%' OR a.mimeType LIKE 'video/%')"
        }
        if (filter.onlyUnreviewed) clauses += "d.assetId IS NULL"
        return SwipeQuery(clauses.joinToString(" AND "), args)
    }

    fun candidates(filter: SwipeFilter, limit: Int): SwipeQuery {
        val where = where(filter)
        val order = when (filter.order) {
            SwipeOrder.NEWEST -> "a.dateTaken DESC"
            SwipeOrder.OLDEST -> "a.dateTaken ASC"
            SwipeOrder.LARGEST -> "a.sizeBytes DESC"
            SwipeOrder.RANDOM -> "RANDOM()"
        }
        return SwipeQuery(
            "SELECT $COLUMNS $FROM WHERE ${where.sql} ORDER BY $order LIMIT $limit",
            where.args,
        )
    }

    fun count(filter: SwipeFilter): SwipeQuery {
        val where = where(filter)
        return SwipeQuery(
            "SELECT COUNT(*) AS itemCount, COALESCE(SUM(a.sizeBytes), 0) AS totalBytes $FROM WHERE ${where.sql}",
            where.args,
        )
    }

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(", ")
}
