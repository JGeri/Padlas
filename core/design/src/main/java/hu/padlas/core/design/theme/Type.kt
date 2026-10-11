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
 * One family carries the whole app. Instrument Sans has real tabular figures, so byte
 * counts line up in lists without falling back to a monospaced face.
 */
val InstrumentSans = FontFamily(
    Font(R.font.instrument_sans_regular, FontWeight.Normal),
    Font(R.font.instrument_sans_medium, FontWeight.Medium),
    Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
    Font(R.font.instrument_sans_semibold, FontWeight.Bold),
)

private const val Tabular = "tnum"

data class PadlasTypography(
    /** Hero figures: free space, reclaimable bytes. */
    val display: TextStyle = TextStyle(
        fontFamily = InstrumentSans,
        fontSize = 46.sp,
        lineHeight = 50.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.03).em,
        fontFeatureSettings = Tabular,
    ),
    val displaySmall: TextStyle = TextStyle(
        fontFamily = InstrumentSans,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.02).em,
        fontFeatureSettings = Tabular,
    ),
    /** Screen titles. */
    val headline: TextStyle = TextStyle(
        fontFamily = InstrumentSans,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.02).em,
    ),
    val title: TextStyle = TextStyle(
        fontFamily = InstrumentSans,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.01).em,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = InstrumentSans,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
    ),
    val bodyStrong: TextStyle = TextStyle(
        fontFamily = InstrumentSans,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Medium,
    ),
    val label: TextStyle = TextStyle(
        fontFamily = InstrumentSans,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
    val caption: TextStyle = TextStyle(
        fontFamily = InstrumentSans,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Normal,
    ),
    val micro: TextStyle = TextStyle(
        fontFamily = InstrumentSans,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.03.em,
    ),
    val numeric: TextStyle = TextStyle(
        fontFamily = InstrumentSans,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = Tabular,
    ),
    val numericLarge: TextStyle = TextStyle(
        fontFamily = InstrumentSans,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.01).em,
        fontFeatureSettings = Tabular,
    ),
)

internal fun material3Typography(type: PadlasTypography): Typography = Typography(
    displayLarge = type.display,
    displayMedium = type.displaySmall,
    headlineMedium = type.headline,
    headlineSmall = type.title,
    titleLarge = type.title,
    titleMedium = type.bodyStrong,
    bodyLarge = type.body,
    bodyMedium = type.body,
    bodySmall = type.caption,
    labelLarge = type.label,
    labelMedium = type.caption,
    labelSmall = type.micro,
)
