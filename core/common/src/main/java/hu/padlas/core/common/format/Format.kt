package hu.padlas.core.common.format

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Every number the user sees goes through here, in the conventions of the language the app
 * runs in: "12,8 GB" and "2 341" in Hungarian, "12.8 GB" and "2,341" in English. The unit is
 * always present.
 */
object Format {

    /** The app's language, which Android keeps as the default; it may differ from the phone's. */
    private val locale: Locale get() = Locale.getDefault()

    /** U+202F. */
    private const val GROUP = ' '

    /**
     * Depending on the CLDR build the JDK emits U+00A0 or a plain space as a space-like
     * grouping separator (Hungarian, French). Pinning one character keeps the tabular figures of Geist Mono
     * aligned across every screen.
     */
    private fun String.normalizeGrouping(): String =
        replace(' ', GROUP).replace(' ', GROUP)

    /** 13 458 291 -> "12,8 GB". Decimal units are a lie on Android, so these are binary. */
    fun bytes(value: Long): String {
        val negative = value < 0
        var bytes = abs(value).toDouble()
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var unit = 0
        while (bytes >= 1024.0 && unit < units.lastIndex) {
            bytes /= 1024.0
            unit++
        }
        val decimals = when {
            unit == 0 -> 0
            bytes >= 100 -> 0
            else -> 1
        }
        val formatted = String.format(locale, "%,.${decimals}f", bytes).normalizeGrouping()
        return buildString {
            if (negative) append('-')
            append(formatted)
            append(' ')
            append(units[unit])
        }
    }

    /** Same as [bytes] but without the unit, for the hero digit roll. */
    fun bytesValue(value: Long): String = bytes(value).substringBeforeLast(' ')

    fun bytesUnit(value: Long): String = bytes(value).substringAfterLast(' ')

    /** 2341 -> "2 341" */
    fun count(value: Long): String = String.format(locale, "%,d", value).normalizeGrouping()

    fun count(value: Int): String = count(value.toLong())

    /** 0.34 -> "34%". 0 / 0 is a real case (an empty folder, a volume that reports nothing); NaN cannot be rounded. */
    fun percent(fraction: Float): String = if (fraction.isNaN()) "0%" else "${(fraction * 100f).roundToLong()}%"

    fun duration(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes >= 60) {
            String.format(locale, "%d:%02d:%02d", minutes / 60, minutes % 60, seconds)
        } else {
            String.format(locale, "%d:%02d", minutes, seconds)
        }
    }
}
