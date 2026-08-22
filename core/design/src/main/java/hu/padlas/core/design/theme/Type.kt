package hu.padlas.core.design.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import hu.padlas.core.design.R

/**
 * Three roles, three faces. The serif hero number is the whole luxury argument: a
 * seriffed figure in a storage app borrows the visual language of watchmaking, not of
 * system settings. Everything else stays quiet around it.
 *
 * The faces are bundled rather than pulled through the downloadable-fonts provider: the
 * first screen the user ever sees is the one with the 56sp hero number on it, and a font
 * that arrives a second late would land exactly there.
 */
val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal),
)

val InstrumentSans = FontFamily(
    Font(R.font.instrument_sans_regular, FontWeight.Normal),
    Font(R.font.instrument_sans_medium, FontWeight.Medium),
    Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
)

val GeistMono = FontFamily(
    Font(R.font.geist_mono_regular, FontWeight.Normal),
    Font(R.font.geist_mono_medium, FontWeight.Medium),
)

/** Padlas type scale. Sizes are fixed in sp; the app never scales the hero digits. */
data class PadlasTypography(
    val display: TextStyle = TextStyle(
        fontFamily = InstrumentSerif,
        fontSize = 56.sp,
        lineHeight = 60.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.01).em,
    ),
    val displaySmall: TextStyle = TextStyle(
        fontFamily = InstrumentSerif,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Normal,
    ),
    val title: TextStyle = TextStyle(
        fontFamily = InstrumentSans,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Medium,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = InstrumentSans,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    ),
    val label: TextStyle = TextStyle(
        fontFamily = InstrumentSans,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
    val micro: TextStyle = TextStyle(
        fontFamily = InstrumentSans,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.02.em,
    ),
    /** Tabular figures, always. Every byte count and percentage uses this. */
    val numeric: TextStyle = TextStyle(
        fontFamily = GeistMono,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    ),
    val numericLarge: TextStyle = TextStyle(
        fontFamily = GeistMono,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    ),
)

internal fun material3Typography(type: PadlasTypography): Typography = Typography(
    displayLarge = type.display,
    displayMedium = type.displaySmall,
    headlineSmall = type.title,
    titleLarge = type.title,
    bodyLarge = type.body,
    bodyMedium = type.body,
    labelLarge = type.label,
    labelSmall = type.micro,
)
