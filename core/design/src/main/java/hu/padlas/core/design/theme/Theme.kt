package hu.padlas.core.design.theme

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import hu.padlas.core.design.motion.LocalHaptics
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.rememberPadlasHaptics

val LocalPadlasType = staticCompositionLocalOf { PadlasTypography() }

object PadlasTheme {
    val type: PadlasTypography
        @Composable @ReadOnlyComposable get() = LocalPadlasType.current
}

private val PadlasColorScheme = darkColorScheme(
    primary = Ink.Accent,
    onPrimary = Ink.OnAccent,
    primaryContainer = Ink.AccentSoft,
    onPrimaryContainer = Ink.Text,
    secondary = Ink.Success,
    onSecondary = Ink.Background,
    tertiary = Ink.Favorite,
    error = Ink.Danger,
    onError = Ink.Text,
    background = Ink.Background,
    onBackground = Ink.Text,
    surface = Ink.SurfaceSolid,
    onSurface = Ink.Text,
    surfaceVariant = Ink.SurfaceSolidHigh,
    onSurfaceVariant = Ink.TextSecondary,
    surfaceContainer = Ink.SurfaceSolid,
    surfaceContainerHigh = Ink.SurfaceSolidHigh,
    surfaceContainerHighest = Ink.SurfaceSolidHigh,
    surfaceContainerLow = Ink.SurfaceSolid,
    surfaceContainerLowest = Ink.Background,
    outline = Ink.HairlineStrong,
    outlineVariant = Ink.Hairline,
    scrim = Ink.Scrim,
)

/**
 * Dark only: a photo app is read with the pictures as the light source. [liveBackground]
 * lets the aurora drift; off, it holds still (and costs nothing per frame).
 */
@Composable
fun PadlasTheme(liveBackground: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val type = remember { PadlasTypography() }
    val haptics = rememberPadlasHaptics()
    val reducedMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }

    val aurora = rememberAuroraPhase(liveBackground && !reducedMotion)

    CompositionLocalProvider(
        LocalAuroraPhase provides aurora,
        LocalPadlasType provides type,
        LocalHaptics provides haptics,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = PadlasColorScheme,
            typography = material3Typography(type),
            content = content,
        )
    }
}
