package hu.padlas.core.design.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Radius {
    val chip = 999.dp
    val small = 12.dp
    val tile = 16.dp
    val card = 26.dp
    val sheet = 30.dp
    val hero = 34.dp
}

/**
 * Circular corners throughout. A superellipse looks a touch softer, but it can only clip
 * through a path, which the GPU rasterises as a mask on every frame: on a screen of tiles
 * over a moving background that alone cost more than half the frame budget.
 */
object Shapes {
    val chip: Shape = RoundedCornerShape(Radius.chip)
    val pill: Shape = RoundedCornerShape(Radius.chip)
    val small: Shape = RoundedCornerShape(Radius.small)
    val tile: Shape = RoundedCornerShape(Radius.tile)
    val card: Shape = RoundedCornerShape(Radius.card)
    val sheet: Shape = RoundedCornerShape(Radius.sheet)
    val hero: Shape = RoundedCornerShape(Radius.hero)
}

/** 4dp grid. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 40.dp
    val huge = 64.dp

    /** Horizontal page margin. */
    val page = 20.dp
}
