package hu.padlas.feature.triage

import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Date mining over OCR output. Deliberately conservative: a year is always required,
 * because a ticket wrongly called expired is a deleted ticket.
 *
 * When several dates are present the latest one wins - on a ticket the printed purchase
 * date is always older than the event date, and the event date is the one that decides.
 */
internal object TextDates {

    fun latest(text: String): ShotDate? = all(text).maxByOrNull { it.startMillis }

    fun all(text: String): List<ShotDate> {
        if (text.isEmpty()) return emptyList()
        val found = ArrayList<ShotDate>(4)
        YMD.findAll(text).forEach { m -> add(found, m.groupValues[1], m.groupValues[2], m.groupValues[3]) }
        DMY.findAll(text).forEach { m -> add(found, m.groupValues[3], m.groupValues[2], m.groupValues[1]) }
        YEAR_MONTH_DAY.findAll(text).forEach { m ->
            val month = monthOf(m.groupValues[2]) ?: return@forEach
            add(found, m.groupValues[1], month.toString(), m.groupValues[3])
        }
        DAY_MONTH_YEAR.findAll(text).forEach { m ->
            val month = monthOf(m.groupValues[2]) ?: return@forEach
            add(found, m.groupValues[3], month.toString(), m.groupValues[1])
        }
        MONTH_DAY_YEAR.findAll(text).forEach { m ->
            val month = monthOf(m.groupValues[1]) ?: return@forEach
            add(found, m.groupValues[3], month.toString(), m.groupValues[2])
        }
        return found
    }

    private fun add(into: MutableList<ShotDate>, year: String, month: String, day: String) {
        val date = dateOf(year.toIntOrNull(), month.toIntOrNull(), day.toIntOrNull()) ?: return
        val start = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        into += ShotDate(start, start + DAY_MILLIS)
    }

    private fun dateOf(year: Int?, month: Int?, day: Int?): LocalDate? {
        if (year == null || month == null || day == null) return null
        if (year < 1990 || year > 2100) return null
        // "05/12/2024" is d/m/y here; only fall back to the American order when the
        // first number cannot possibly be a month.
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
            ?: runCatching { LocalDate.of(year, day, month) }.getOrNull()
    }

    private fun monthOf(token: String): Int? {
        val flat = deaccent(token.lowercase())
        if (flat.length < 3) return null
        return MONTHS.indexOfFirst { prefixes -> prefixes.any { flat.startsWith(it) } }
            .takeIf { it >= 0 }
            ?.plus(1)
    }

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    private val YMD = Regex("""(\d{4})[.\-/]\s?(\d{1,2})[.\-/]\s?(\d{1,2})""")
    private val DMY = Regex("""(?<!\d)(\d{1,2})[.\-/]\s?(\d{1,2})[.\-/]\s?(\d{4})""")
    private val YEAR_MONTH_DAY = Regex("""(\d{4})\.?\s+(\p{L}{3,12})\.?\s+(\d{1,2})""")
    private val DAY_MONTH_YEAR = Regex("""(?<!\d)(\d{1,2})\.?\s+(\p{L}{3,12})\.?,?\s+(\d{4})""")
    private val MONTH_DAY_YEAR = Regex("""(\p{L}{3,12})\.?\s+(\d{1,2})\.?,?\s+(\d{4})""")

    /** Hungarian and English month prefixes, de-accented. Order is January..December. */
    private val MONTHS: List<List<String>> = listOf(
        listOf("jan"),
        listOf("feb"),
        listOf("mar"),
        listOf("apr"),
        listOf("maj", "may"),
        listOf("jun"),
        listOf("jul"),
        listOf("aug"),
        listOf("sze", "sep"),
        listOf("okt", "oct"),
        listOf("nov"),
        listOf("dec"),
    )
}
