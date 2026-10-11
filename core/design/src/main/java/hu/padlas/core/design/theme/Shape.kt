package hu.padlas.core.design.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

/**
 * A superellipse rather than circular corners: the curvature starts before the straight
 * edge ends, which is what makes a large card read as one continuous object.
 */
class SquircleShape(private val radius: Dp, private val n: Float = 4.2f) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }.coerceAtMost(min(size.width, size.height) / 2f)
        if (r <= 0f) return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
        return Outline.Generic(superellipsePath(size, r, n))
    }

    private fun superellipsePath(size: Size, r: Float, n: Float): Path {
        val path = Path()
        val exponent = 2f / n

        // One corner is sampled and mirrored into the other three, so all four match.
        val corner = ArrayList<Pair<Float, Float>>(SEGMENTS + 1)
        for (i in 0..SEGMENTS) {
            val t = i.toFloat() / SEGMENTS * (Math.PI / 2.0)
            val cos = kotlin.math.cos(t).toFloat()
            val sin = kotlin.math.sin(t).toFloat()
            corner += sign(cos) * abs(cos).pow(exponent) * r to sign(sin) * abs(sin).pow(exponent) * r
        }

        val w = size.width
        val h = size.height
        path.moveTo(w - r + corner[0].first, r - corner[0].second)
        for ((x, y) in corner) path.lineTo(w - r + x, r - y)
        path.lineTo(w, h - r)
        for (i in corner.indices.reversed()) {
            val (x, y) = corner[i]
            path.lineTo(w - r + x, h - r + y)
        }
        path.lineTo(r, h)
        for ((x, y) in corner) path.lineTo(r - x, h - r + y)
        path.lineTo(0f, r)
        for (i in corner.indices.reversed()) {
            val (x, y) = corner[i]
            path.lineTo(r - x, r - y)
        }
        path.close()
        return path
    }

    private companion object {
        const val SEGMENTS = 24
    }
}

object Radius {
    val chip = 999.dp
    val small = 10.dp
    val tile = 14.dp
    val card = 22.dp
    val sheet = 28.dp
    val hero = 32.dp
}

object Shapes {
    val chip: Shape = RoundedCornerShape(Radius.chip)
    val pill: Shape = RoundedCornerShape(Radius.chip)
    val small: Shape = RoundedCornerShape(Radius.small)
    val tile: Shape = SquircleShape(Radius.tile)
    val card: Shape = SquircleShape(Radius.card)
    val sheet: Shape = SquircleShape(Radius.sheet)
    val hero: Shape = SquircleShape(Radius.hero)
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
