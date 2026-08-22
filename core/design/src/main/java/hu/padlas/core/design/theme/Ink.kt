package hu.padlas.core.design.theme

import androidx.compose.ui.graphics.Color

/**
 * One metal, aged. Every colour in the app is a point on the oxidation scale of brass:
 * warm archive black through paper, brass, and finally patina. Nothing here is a hue
 * chosen for decoration.
 */
object Ink {
    val Void = Color(0xFF0D0B09) // background, warm black-brown
    val Surface = Color(0xFF16130F) // card
    val Raised = Color(0xFF1F1B16) // raised surface, sheet
    val Hairline = Color(0x14FFFFFF) // 8% white, 1px rules
    val Highlight = Color(0x0FFFFFFF) // 6% white, top edge light

    val Paper = Color(0xFFF2EDE4) // primary text, warm off-white
    val Faded = Color(0xFF9A9186) // secondary
    val Dust = Color(0xFF645C52) // tertiary, labels

    val Brass = Color(0xFFC9974A) // accent: space reclaimed
    val BrassDim = Color(0xFF8A6A36)
    val Patina = Color(0xFF5C8A7E) // accent: archived / safe
    val Rust = Color(0xFFB4553F) // destructive, ONLY on confirmation
}

/** Category scale for the meter and the treemap: brass to patina on a single hue arc. */
val Spectrum: List<Color> = listOf(
    Color(0xFFD8A85C), // photos
    Color(0xFFB98B4A), // videos
    Color(0xFF95793F), // apps
    Color(0xFF6E6B45), // system
    Color(0xFF52785F), // other
    Color(0xFF5C8A7E), // archived (the attic)
)
