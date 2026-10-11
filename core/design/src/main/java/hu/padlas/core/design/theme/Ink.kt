package hu.padlas.core.design.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Aurora: a near-black ground with slow colour drifting behind frosted glass. Surfaces
 * are translucent on purpose, so the light behind them shows through; anything that has
 * to stay legible over photos or moving content uses the solid variants.
 */
object Ink {
    val Background = Color(0xFF07070D)

    /** Glass steps, white over whatever is behind. */
    val Surface = Color(0x0FFFFFFF)
    val SurfaceHigh = Color(0x1AFFFFFF)
    val SurfaceHighest = Color(0x29FFFFFF)

    /** Opaque glass for overlays: dialogs, menus, toasts, sheets. */
    val SurfaceSolid = Color(0xFF13131C)
    val SurfaceSolidHigh = Color(0xFF1C1C28)

    val Hairline = Color(0x1FFFFFFF)
    val HairlineStrong = Color(0x33FFFFFF)
    val Highlight = Color(0x24FFFFFF)
    val Scrim = Color(0xB3030308)

    val Text = Color(0xFFF4F3FF)
    val TextSecondary = Color(0xA8F4F3FF)
    val TextTertiary = Color(0x6BF4F3FF)

    val Accent = Color(0xFF8C7BFF)
    val AccentDim = Color(0xFF6A5AE0)
    val AccentSoft = Color(0x388C7BFF)
    val AccentCyan = Color(0xFF42E8E0)
    val OnAccent = Color(0xFFFFFFFF)

    /** Keep. */
    val Success = Color(0xFF3EE8A6)
    val SuccessSoft = Color(0x293EE8A6)

    /** Delete. */
    val Danger = Color(0xFFFF5C7A)
    val DangerSoft = Color(0x29FF5C7A)

    /** Favourite. */
    val Favorite = Color(0xFFFFC94A)
    val FavoriteSoft = Color(0x29FFC94A)

    val Warning = Color(0xFFFFA94D)
    val WarningSoft = Color(0x29FFA94D)
    val Info = Color(0xFF52C7F5)
    val InfoSoft = Color(0x2952C7F5)

    /** The three aurora lights behind every screen. */
    val AuroraViolet = Color(0xFF6E55FF)
    val AuroraCyan = Color(0xFF1FD3D0)
    val AuroraMagenta = Color(0xFFE244C9)
}

/** Violet into blue: primary actions and the one hero element of a screen. */
val AccentGradient: Brush
    get() = Brush.linearGradient(listOf(Color(0xFF9D8BFF), Color(0xFF6E7BFF)))

/** Violet into cyan: progress that is going well. */
val ProgressGradient: Brush
    get() = Brush.horizontalGradient(listOf(Color(0xFF9D8BFF), Color(0xFF42E8E0)))

val DangerGradient: Brush
    get() = Brush.linearGradient(listOf(Color(0xFFFF5C7A), Color(0xFFFF8A5C)))

/**
 * Storage categories. The index is part of the contract with the treemap and the gauges:
 * photos, videos, apps, system, other, cloud, audio, documents, archives.
 */
val Spectrum: List<Color> = listOf(
    Color(0xFF9D8BFF), // photos
    Color(0xFFFF6FA8), // videos
    Color(0xFF3FE6C8), // apps
    Color(0xFF4A4A5C), // system
    Color(0xFF9A9AB0), // other
    Color(0xFF52C7F5), // cloud
    Color(0xFFFFC94A), // audio
    Color(0xFF7FD8FF), // documents
    Color(0xFFFF8A5C), // archives and installers
)

object CategoryColor {
    val Photos = Spectrum[0]
    val Videos = Spectrum[1]
    val Apps = Spectrum[2]
    val System = Spectrum[3]
    val Other = Spectrum[4]
    val Cloud = Spectrum[5]
    val Audio = Spectrum[6]
    val Documents = Spectrum[7]
    val Archives = Spectrum[8]
}
