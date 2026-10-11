package hu.padlas.core.design.component

import java.util.Locale
import kotlin.math.abs

private val HU: Locale = Locale.forLanguageTag("hu-HU")
private const val GROUP = ' ' // narrow no-break space, the Hungarian thousands group
private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

/**
 * A deliberate duplicate of `core:common` Format.bytes. The gauge takes raw bytes and has to
 * split the figure from its unit to roll them separately, but core:design cannot see
 * core:common - so the conventions live here too, and both must stay identical.
 */
internal fun bytesValue(value: Long): String {
    var bytes = abs(value).toDouble()
    var unit = 0
    while (bytes >= 1024.0 && unit < UNITS.lastIndex) {
        bytes /= 1024.0
        unit++
    }
    val decimals = if (unit == 0 || bytes >= 100) 0 else 1
    // The JDK emits U+00A0 or a plain space as the Hungarian group, depending on its CLDR.
    val text = String.format(HU, "%,.${decimals}f", bytes).replace(' ', GROUP).replace('\u00A0', GROUP)
    return if (value < 0) "-$text" else text
}

internal fun bytesUnit(value: Long): String {
    var bytes = abs(value).toDouble()
    var unit = 0
    while (bytes >= 1024.0 && unit < UNITS.lastIndex) {
        bytes /= 1024.0
        unit++
    }
    return UNITS[unit]
}
