package hu.padlas.core.design.theme

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import hu.padlas.core.design.motion.LocalHaptics
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.rememberPadlasHaptics
import kotlin.random.Random

val LocalPadlasType = staticCompositionLocalOf { PadlasTypography() }

object PadlasTheme {
    val type: PadlasTypography
        @Composable @ReadOnlyComposable get() = LocalPadlasType.current
}

private val PadlasColorScheme = darkColorScheme(
    primary = Ink.Brass,
    onPrimary = Ink.Void,
    secondary = Ink.Patina,
    onSecondary = Ink.Void,
    error = Ink.Rust,
    onError = Ink.Paper,
    background = Ink.Void,
    onBackground = Ink.Paper,
    surface = Ink.Surface,
    onSurface = Ink.Paper,
    surfaceVariant = Ink.Raised,
    onSurfaceVariant = Ink.Faded,
    outline = Ink.Dust,
    outlineVariant = Ink.Hairline,
)

@Composable
fun PadlasTheme(content: @Composable () -> Unit) {
    // The app has exactly one appearance; the system theme is irrelevant to an archive
    // that is meant to look like a lit room at night.
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()

    val context = LocalContext.current
    val type = remember { PadlasTypography() }
    val haptics = rememberPadlasHaptics()
    val reducedMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }

    CompositionLocalProvider(
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

/**
 * 3% tiled noise over the whole background. Without it dark gradients band on AMOLED and
 * the surface reads as cheap digital; the grain is what makes it a material.
 */
@Composable
fun Modifier.grain(alpha: Float = 0.03f): Modifier {
    val noise = remember { noiseTile() }
    val brush = remember(noise) {
        ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated))
    }
    return this.drawWithCache {
        onDrawWithContent {
            drawContent()
            drawRect(brush = brush, alpha = alpha)
        }
    }
}

private fun noiseTile(size: Int = 128): ImageBitmap {
    val random = Random(20240712)
    val pixels = IntArray(size * size)
    for (i in pixels.indices) {
        val v = random.nextInt(0, 256)
        pixels[i] = android.graphics.Color.rgb(v, v, v)
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}
