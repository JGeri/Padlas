package hu.padlas.feature.browse

/**
 * Folders and months are the two axes people actually think in: "the Balaton pictures" is
 * a month, "WhatsApp" is a folder. Everything on this screen is one of the two, combined
 * with an age cutoff.
 */
data class BrowseFilter(
    val buckets: Set<Long> = emptySet(),
    /** "2024-07", local time, exactly as `strftime('%Y-%m')` produces it. */
    val months: Set<String> = emptySet(),
    val olderThanDays: Int? = null,
    val mediaType: BrowseMediaType = BrowseMediaType.ALL,
) {
    val isEmpty: Boolean
        get() = buckets.isEmpty() && months.isEmpty() && olderThanDays == null &&
            mediaType == BrowseMediaType.ALL
}

enum class BrowseMediaType { ALL, PHOTO, VIDEO }

data class BrowseQuery(val sql: String, val args: List<Any>)

/**
 * The WHERE clause is assembled here rather than in a `@Query`, because Room cannot bind
 * an empty `IN ()` list and the two axes are independently optional. Every value is still
 * a bound argument.
 */
object BrowseSql {

    const val BASE_CLAUSE = "state IN ('LOCAL', 'PROTECTED')"

    /** Kept identical to the month aggregate query so a chip always matches its own rows. */
    const val MONTH_EXPRESSION = "strftime('%Y-%m', dateTaken / 1000, 'unixepoch', 'localtime')"

    fun where(filter: BrowseFilter, nowMillis: Long): BrowseQuery {
        val clauses = ArrayList<String>()
        val args = ArrayList<Any>()
        clauses += BASE_CLAUSE

        if (filter.buckets.isNotEmpty()) {
            clauses += "bucketId IN (${placeholders(filter.buckets.size)})"
            args.addAll(filter.buckets.sorted())
        }
        if (filter.months.isNotEmpty()) {
            clauses += "$MONTH_EXPRESSION IN (${placeholders(filter.months.size)})"
            args.addAll(filter.months.sorted())
        }
        filter.olderThanDays?.let { days ->
            clauses += "dateTaken < ?"
            args += nowMillis - days.toLong() * DAY_MS
        }
        when (filter.mediaType) {
            BrowseMediaType.PHOTO -> {
                clauses += "mimeType LIKE ?"
                args += "image/%"
            }

            BrowseMediaType.VIDEO -> {
                clauses += "mimeType LIKE ?"
                args += "video/%"
            }

            BrowseMediaType.ALL -> Unit
        }
        return BrowseQuery(clauses.joinToString(" AND "), args)
    }

    fun page(filter: BrowseFilter, nowMillis: Long): BrowseQuery {
        val clause = where(filter, nowMillis)
        return clause.copy(sql = "SELECT * FROM asset WHERE ${clause.sql} ORDER BY dateTaken DESC")
    }

    fun totals(filter: BrowseFilter, nowMillis: Long): BrowseQuery {
        val clause = where(filter, nowMillis)
        return clause.copy(
            sql = "SELECT COUNT(*) AS itemCount, COALESCE(SUM(sizeBytes), 0) AS totalBytes " +
                "FROM asset WHERE ${clause.sql}",
        )
    }

    /** Ids for "select everything that matches", capped so one tap cannot load 25 000 rows. */
    fun ids(filter: BrowseFilter, nowMillis: Long, limit: Int): BrowseQuery {
        val clause = where(filter, nowMillis)
        return clause.copy(
            sql = "SELECT * FROM asset WHERE ${clause.sql} ORDER BY dateTaken DESC LIMIT $limit",
        )
    }

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(", ")

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
