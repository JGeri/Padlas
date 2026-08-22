package hu.padlas.feature.triage

import java.text.Normalizer

private val COMBINING = Regex("""\p{Mn}+""")

/**
 * "Helyreállítási kód" -> "helyreallitasi kod". OCR drops accents often enough that
 * matching on the accented form alone loses roughly one Hungarian hit in ten, and the
 * de-accented form is also what lets ASCII word boundaries work in the patterns below.
 */
internal fun deaccent(value: String): String =
    COMBINING.replace(Normalizer.normalize(value, Normalizer.Form.NFD), "")
