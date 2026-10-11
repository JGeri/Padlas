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
 * Sora for display (geometric, wide, confident at large sizes) over Manrope for text.
 * Both are variable fonts; each weight below is one position on the same file's axis.
 */
val Sora = FontFamily(
    Font(R.font.sora, FontWeight.Medium),
    Font(R.font.sora, FontWeight.SemiBold),
    Font(R.font.sora, FontWeight.Bold),
)

val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.Normal),
    Font(R.font.manrope, FontWeight.Medium),
    Font(R.font.manrope, FontWeight.SemiBold),
    Font(R.font.manrope, FontWeight.Bold),
)

private const val Tabular = "tnum"

data class PadlasTypography(
    /** Hero figures: free space, reclaimable bytes. */
    val display: TextStyle = TextStyle(
        fontFamily = Sora,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.03).em,
        fontFeatureSettings = Tabular,
    ),
    val displaySmall: TextStyle = TextStyle(
        fontFamily = Sora,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.025).em,
        fontFeatureSettings = Tabular,
    ),
    /** Screen titles. */
    val headline: TextStyle = TextStyle(
        fontFamily = Sora,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.025).em,
    ),
    val title: TextStyle = TextStyle(
        fontFamily = Sora,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.015).em,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = Manrope,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Medium,
    ),
    val bodyStrong: TextStyle = TextStyle(
        fontFamily = Manrope,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Bold,
    ),
    val label: TextStyle = TextStyle(
        fontFamily = Manrope,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Bold,
    ),
    val caption: TextStyle = TextStyle(
        fontFamily = Manrope,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium,
    ),
    val micro: TextStyle = TextStyle(
        fontFamily = Manrope,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.02.em,
    ),
    val numeric: TextStyle = TextStyle(
        fontFamily = Manrope,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = Tabular,
    ),
    val numericLarge: TextStyle = TextStyle(
        fontFamily = Sora,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.02).em,
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
