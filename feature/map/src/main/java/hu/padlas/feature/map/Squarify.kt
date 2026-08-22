package hu.padlas.feature.map

import kotlin.math.max
import kotlin.math.min

/**
 * A rectangle in layout pixels. Deliberately not `androidx.compose.ui.geometry.Rect`:
 * the layout is pure arithmetic and has to stay testable on a plain JVM.
 */
data class MapRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {

    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom

    fun lerpTo(other: MapRect, t: Float): MapRect = MapRect(
        left = left + (other.left - left) * t,
        top = top + (other.top - top) * t,
        right = right + (other.right - right) * t,
        bottom = bottom + (other.bottom - bottom) * t,
    )

    companion object {
        val Zero = MapRect(0f, 0f, 0f, 0f)

        fun ofSize(width: Float, height: Float) = MapRect(0f, 0f, width, height)
    }
}

data class SquarifyItem(val id: String, val weight: Long)

data class SquarifiedTile(val id: String, val weight: Long, val rect: MapRect)

/**
 * Bruls, Huizing and van Wijk: fill the shorter side of the free rectangle with a row of
 * items for as long as the worst aspect ratio in that row keeps improving, then recurse on
 * what is left. Rows tile the input rectangle exactly, so the tiles never overlap and
 * never leave a gap.
 */
fun squarify(items: List<SquarifyItem>, rect: MapRect): List<SquarifiedTile> {
    if (rect.width <= 0f || rect.height <= 0f) return emptyList()
    val sorted = items.filter { it.weight > 0L }.sortedByDescending { it.weight }
    if (sorted.isEmpty()) return emptyList()

    val total = sorted.sumOf { it.weight.toDouble() }
    val scale = rect.width.toDouble() * rect.height.toDouble() / total

    val out = ArrayList<SquarifiedTile>(sorted.size)
    val row = ArrayList<SquarifyItem>()
    var free = rect
    var rowArea = 0.0
    var rowMin = Double.MAX_VALUE
    var rowMax = 0.0
    var index = 0

    while (index < sorted.size) {
        val item = sorted[index]
        val area = item.weight * scale
        val side = min(free.width, free.height).toDouble()
        val current = worst(rowArea, rowMin, rowMax, side)
        val extended = worst(rowArea + area, min(rowMin, area), max(rowMax, area), side)

        if (row.isEmpty() || extended <= current) {
            row += item
            rowArea += area
            rowMin = min(rowMin, area)
            rowMax = max(rowMax, area)
            index++
        } else {
            free = placeRow(row, rowArea, free, scale, out, fillsFree = false)
            row.clear()
            rowArea = 0.0
            rowMin = Double.MAX_VALUE
            rowMax = 0.0
        }
    }
    if (row.isNotEmpty()) placeRow(row, rowArea, free, scale, out, fillsFree = true)
    return out
}

/** Worst (largest) aspect ratio in a row of the given areas laid along [side]. */
private fun worst(sum: Double, minArea: Double, maxArea: Double, side: Double): Double {
    if (sum <= 0.0 || side <= 0.0 || minArea <= 0.0 || minArea == Double.MAX_VALUE) {
        return Double.MAX_VALUE
    }
    val sumSquared = sum * sum
    val sideSquared = side * side
    return max(sideSquared * maxArea / sumSquared, sumSquared / (sideSquared * minArea))
}

/**
 * Lays [row] along the shorter side of [free] and returns what is left. The last tile in a
 * row snaps to the row end and the last row consumes the whole free rectangle, so float
 * drift can never open a seam between tiles.
 */
private fun placeRow(
    row: List<SquarifyItem>,
    rowArea: Double,
    free: MapRect,
    scale: Double,
    out: MutableList<SquarifiedTile>,
    fillsFree: Boolean,
): MapRect {
    val acrossWidth = free.width < free.height
    val thickness = when {
        fillsFree -> if (acrossWidth) free.height else free.width
        acrossWidth -> (rowArea / free.width).toFloat().coerceIn(0f, free.height)
        else -> (rowArea / free.height).toFloat().coerceIn(0f, free.width)
    }
    if (thickness <= 0f) {
        row.forEach { out += SquarifiedTile(it.id, it.weight, MapRect(free.left, free.top, free.left, free.top)) }
        return free
    }

    return if (acrossWidth) {
        var x = free.left
        row.forEachIndexed { i, item ->
            val end = if (i == row.lastIndex) free.right else min(x + (item.weight * scale / thickness).toFloat(), free.right)
            out += SquarifiedTile(item.id, item.weight, MapRect(x, free.top, end, free.top + thickness))
            x = end
        }
        MapRect(free.left, free.top + thickness, free.right, free.bottom)
    } else {
        var y = free.top
        row.forEachIndexed { i, item ->
            val end = if (i == row.lastIndex) free.bottom else min(y + (item.weight * scale / thickness).toFloat(), free.bottom)
            out += SquarifiedTile(item.id, item.weight, MapRect(free.left, y, free.left + thickness, end))
            y = end
        }
        MapRect(free.left + thickness, free.top, free.right, free.bottom)
    }
}
