package hu.padlas.feature.rules

/** A WHERE clause and the arguments that go with it, never a concatenated literal. */
data class CompiledPredicate(val where: String, val args: List<Any>)

/**
 * Every value is bound as an argument. The predicate tree carries user-entered folder
 * names, and a rule that can be typed is a rule that can be typed wrong on purpose.
 */
object PredicateCompiler {

    /** Automation only ever looks at rows that are still on the device (INV-1b). */
    const val BASE_CLAUSE = "state IN ('LOCAL', 'PROTECTED')"

    fun compile(predicate: Predicate, nowMillis: Long): CompiledPredicate {
        val args = ArrayList<Any>()
        val where = build(predicate, nowMillis, args)
        return CompiledPredicate(where, args)
    }

    /** The full statement the rule engine runs, protected set and all. */
    fun selectAssets(predicate: Predicate, nowMillis: Long, limit: Int? = null): CompiledPredicate {
        val compiled = compile(predicate, nowMillis)
        val limitClause = limit?.let { " LIMIT $it" }.orEmpty()
        return compiled.copy(
            where = "SELECT * FROM asset WHERE $BASE_CLAUSE AND (${compiled.where})" +
                " ORDER BY sizeBytes DESC$limitClause",
        )
    }

    fun selectTotals(predicate: Predicate, nowMillis: Long): CompiledPredicate {
        val compiled = compile(predicate, nowMillis)
        return compiled.copy(
            where = "SELECT COUNT(*) AS itemCount, COALESCE(SUM(sizeBytes), 0) AS totalBytes " +
                "FROM asset WHERE $BASE_CLAUSE AND (${compiled.where})",
        )
    }

    private fun build(predicate: Predicate, now: Long, args: MutableList<Any>): String = when (predicate) {
        is Predicate.PathContains -> {
            args += "%${predicate.value}%"
            "relativePath LIKE ?"
        }

        is Predicate.OlderThan -> {
            args += now - predicate.days.toLong() * DAY_MS
            "dateTaken < ?"
        }

        is Predicate.LargerThan -> {
            args += predicate.bytes
            "sizeBytes > ?"
        }

        is Predicate.MimeIs -> {
            args += "${predicate.prefix}%"
            "mimeType LIKE ?"
        }

        is Predicate.ValueClassIs -> {
            args += predicate.valueClass.name
            "valueClass = ?"
        }

        is Predicate.ShotKindIs -> {
            args += predicate.kind.name
            "shotKind = ?"
        }

        is Predicate.BucketIs -> {
            args += predicate.bucketId
            "bucketId = ?"
        }

        is Predicate.And -> predicate.terms.joinClause("AND", now, args)
        is Predicate.Or -> predicate.terms.joinClause("OR", now, args)
        is Predicate.Not -> "NOT (${build(predicate.term, now, args)})"
    }

    private fun List<Predicate>.joinClause(
        operator: String,
        now: Long,
        args: MutableList<Any>,
    ): String {
        if (isEmpty()) return "1"
        return joinToString(separator = " $operator ", prefix = "(", postfix = ")") { term ->
            build(term, now, args)
        }
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
