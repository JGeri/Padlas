package hu.padlas.core.design.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Near-black with a hint of blue, so photographs - which are what this app is about - are
 * the most colourful thing on screen. One accent carries every primary action; the three
 * swipe fates each own a colour and never borrow another's.
 */
object Ink {
    val Background = Color(0xFF0A0A0D)
    val Surface = Color(0xFF141418)
    val SurfaceHigh = Color(0xFF1C1C22)
    val SurfaceHighest = Color(0xFF26262E)
    val Hairline = Color(0x14FFFFFF)
    val HairlineStrong = Color(0x26FFFFFF)
    val Highlight = Color(0x0DFFFFFF)
    val Scrim = Color(0xB3000000)

    val Text = Color(0xFFF6F6F8)
    val TextSecondary = Color(0xFFA3A3AE)
    val TextTertiary = Color(0xFF6C6C78)

    val Accent = Color(0xFF8C7BFF)
    val AccentDim = Color(0xFF6A5AE0)
    val AccentSoft = Color(0x298C7BFF)
    val OnAccent = Color(0xFFFFFFFF)

    /** Keep. */
    val Success = Color(0xFF3DDC97)
    val SuccessSoft = Color(0x293DDC97)

    /** Delete. */
    val Danger = Color(0xFFFF5D5D)
    val DangerSoft = Color(0x29FF5D5D)

    /** Favourite. */
    val Favorite = Color(0xFFFFC53D)
    val FavoriteSoft = Color(0x29FFC53D)

    val Warning = Color(0xFFFFA94D)
    val WarningSoft = Color(0x29FFA94D)
    val Info = Color(0xFF52C7F5)
    val InfoSoft = Color(0x2952C7F5)
}

/** The hero gradient: used on exactly one element per screen. */
val AccentGradient: Brush
    get() = Brush.linearGradient(listOf(Color(0xFF9D8CFF), Color(0xFF6E8BFF)))

/**
 * Storage categories. The index is part of the contract with the treemap and the gauges:
 * photos, videos, apps, system, other, cloud, audio, documents, archives.
 */
val Spectrum: List<Color> = listOf(
    Color(0xFF8C7BFF), // photos
    Color(0xFFFF6B9E), // videos
    Color(0xFF3DDC97), // apps
    Color(0xFF3A3A44), // system
    Color(0xFF8E8E9A), // other
    Color(0xFF52C7F5), // cloud
    Color(0xFFFFB547), // audio
    Color(0xFF4FD1C5), // documents
    Color(0xFFFF8A4C), // archives and installers
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
