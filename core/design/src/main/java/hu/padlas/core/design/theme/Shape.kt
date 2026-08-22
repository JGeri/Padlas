package hu.padlas.core.design.theme

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
 * A superellipse, not a rounded rectangle. Compose corners are circular arcs, which read
 * as a break in the outline next to an iOS-trained eye. n = 4.2 is the value that stops
 * looking like a bubble and starts looking machined.
 */
class SquircleShape(private val radius: Dp, private val n: Float = 4.2f) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }.coerceAtMost(min(size.width, size.height) / 2f)
        if (r <= 0f) return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
        return Outline.Generic(superellipsePath(size, r, n))
    }

    private fun superellipsePath(size: Size, r: Float, n: Float): Path {
        val path = Path()
        val steps = SEGMENTS
        val exponent = 2f / n

        // One corner is sampled and then mirrored into the other three, so the four
        // corners are guaranteed identical to the pixel.
        val corner = ArrayList<Pair<Float, Float>>(steps + 1)
        for (i in 0..steps) {
            val t = i.toFloat() / steps * (Math.PI / 2.0)
            val cos = kotlin.math.cos(t).toFloat()
            val sin = kotlin.math.sin(t).toFloat()
            val x = sign(cos) * abs(cos).pow(exponent) * r
            val y = sign(sin) * abs(sin).pow(exponent) * r
            corner += x to y
        }

        val w = size.width
        val h = size.height

        path.moveTo(w - r + corner[0].first, r - corner[0].second)
        for ((x, y) in corner) path.lineTo(w - r + x, r - y) // top right
        path.lineTo(w, h - r)
        for (i in corner.indices.reversed()) {
            val (x, y) = corner[i]
            path.lineTo(w - r + x, h - r + y) // bottom right
        }
        path.lineTo(r, h)
        for ((x, y) in corner) path.lineTo(r - x, h - r + y) // bottom left
        path.lineTo(0f, r)
        for (i in corner.indices.reversed()) {
            val (x, y) = corner[i]
            path.lineTo(r - x, r - y) // top left
        }
        path.close()
        return path
    }

    private companion object {
        const val SEGMENTS = 24
    }
}

/** Radii the app actually uses. Anything above 12dp gets the squircle treatment. */
object Radius {
    val chip = 4.dp
    val card = 12.dp
    val sheet = 20.dp
    val hero = 28.dp
}

object Shapes {
    val chip: Shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.chip)
    val card: Shape = SquircleShape(Radius.card)
    val sheet: Shape = SquircleShape(Radius.sheet)
    val hero: Shape = SquircleShape(Radius.hero)
}

/** 4dp base grid; these are the only steps in use. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 40.dp
    val huge = 64.dp
}
