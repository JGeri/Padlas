package hu.padlas.feature.map

import androidx.compose.runtime.Immutable

internal const val REST_KEY = "\u0000rest"

/** What one level of the map draws: the big items one by one, the long tail as one tile. */
@Immutable
internal data class TreemapPlan(
    val items: List<StorageItem>,
    val restCount: Int,
    val restBytes: Long,
)

internal object TreemapPlanner {

    /** Beyond this a phone screen shows noise, not tiles. */
    const val MAX_TILES = 250

    /** Below this share an item would be a few pixels wide even on the full map. */
    const val MIN_SHARE = 1.0 / 4_000

    fun plan(
        entries: List<StorageItem>,
        maxTiles: Int = MAX_TILES,
        minShare: Double = MIN_SHARE,
    ): TreemapPlan {
        val weighted = entries.filter { it.bytes > 0L }.sortedByDescending { it.bytes }
        val zeroCount = entries.size - weighted.size
        val total = weighted.sumOf { it.bytes }
        if (total <= 0L) return TreemapPlan(emptyList(), zeroCount, 0L)

        val floor = total * minShare
        // One slot stays free for the folded tile, so the cap holds even with a long tail.
        val limit = (maxTiles - 1).coerceAtLeast(1)
        var cut = weighted.size
        for (i in weighted.indices) {
            if (i >= limit || weighted[i].bytes < floor) {
                cut = i
                break
            }
        }
        val kept = weighted.subList(0, cut).toMutableList()
        val rest = weighted.subList(cut, weighted.size)
        // Folding a single item into "1 kisebb elem" would only hide its name.
        if (rest.size == 1) {
            kept += rest[0]
            return TreemapPlan(kept, zeroCount, 0L)
        }
        return TreemapPlan(kept, rest.size + zeroCount, rest.sumOf { it.bytes })
    }
}

@Immutable
internal data class NestedTile(val rect: MapRect, val category: FileCategory, val isRest: Boolean)

@Immutable
internal data class MapTile(
    val key: String,
    /** The drawn rectangle; the gap to the neighbours is already taken out. */
    val rect: MapRect,
    /** Null for the folded tile of small items. */
    val item: StorageItem?,
    val bytes: Long,
    val restCount: Int,
    val category: FileCategory,
    /** Where the children sit inside a framed folder. */
    val inner: MapRect?,
    val nested: List<NestedTile>,
) {
    val isRest: Boolean get() = item == null

    /** The patch of this tile that the next level grows out of when the user drills in. */
    val focus: MapRect get() = inner ?: rect
}

@Immutable
internal class TreemapLayout(
    val path: String,
    val version: Int,
    val width: Float,
    val height: Float,
    val tiles: List<MapTile>,
) {
    fun hit(x: Float, y: Float): MapTile? = tiles.firstOrNull { it.rect.contains(x, y) }
        // A tap that lands in a gap still means the nearest tile.
        ?: tiles.minByOrNull { distanceSquared(it.rect, x, y) }

    fun tile(key: String): MapTile? = tiles.firstOrNull { it.key == key }

    /** The folder tile on this level that contains [path], if any. */
    fun tileLeadingTo(path: String): MapTile? = tiles.firstOrNull { tile ->
        val item = tile.item ?: return@firstOrNull false
        item.isDirectory && (path == item.path || path.startsWith(item.path + "/"))
    }

    private fun distanceSquared(rect: MapRect, x: Float, y: Float): Float {
        val dx = maxOf(rect.left - x, 0f, x - rect.right)
        val dy = maxOf(rect.top - y, 0f, y - rect.bottom)
        return dx * dx + dy * dy
    }
}

internal data class TreemapMetrics(
    val gap: Float,
    val header: Float,
    val padding: Float,
    val nestedGap: Float,
    val nestMinWidth: Float,
    val nestMinHeight: Float,
)

internal fun layoutTreemap(
    directory: DirectoryUi,
    plan: TreemapPlan,
    width: Float,
    height: Float,
    metrics: TreemapMetrics,
): TreemapLayout {
    val full = MapRect.ofSize(width, height)
    val weights = ArrayList<SquarifyItem>(plan.items.size + 1)
    plan.items.forEach { weights += SquarifyItem(it.path, it.bytes) }
    if (plan.restBytes > 0L) weights += SquarifyItem(REST_KEY, plan.restBytes)
    val byKey = plan.items.associateBy { it.path }

    val tiles = squarify(weights, full).map { placed ->
        val rect = placed.rect.inset(metrics.gap / 2f)
        val item = byKey[placed.id]
        val preview = item?.let { directory.previews[it.path] }.orEmpty()
        val nests = item?.isDirectory == true &&
            preview.isNotEmpty() &&
            rect.width >= metrics.nestMinWidth &&
            rect.height >= metrics.nestMinHeight
        val inner = if (nests) {
            MapRect(
                left = rect.left + metrics.padding,
                top = rect.top + metrics.header,
                right = rect.right - metrics.padding,
                bottom = rect.bottom - metrics.padding,
            )
        } else {
            null
        }
        val nested = if (inner != null) {
            val slices = preview.associateBy { it.key.toString() }
            squarify(preview.map { SquarifyItem(it.key.toString(), it.bytes) }, inner).mapNotNull { child ->
                slices[child.id]?.let { NestedTile(child.rect.inset(metrics.nestedGap / 2f), it.category, it.isRest) }
            }
        } else {
            emptyList()
        }
        MapTile(
            key = placed.id,
            rect = rect,
            item = item,
            bytes = placed.weight,
            restCount = if (item == null) plan.restCount else 0,
            category = item?.category ?: FileCategory.OTHER,
            inner = inner,
            nested = nested,
        )
    }
    return TreemapLayout(directory.path, directory.version, width, height, tiles)
}

internal fun MapRect.inset(amount: Float): MapRect {
    val dx = minOf(amount, width / 2f)
    val dy = minOf(amount, height / 2f)
    return MapRect(left + dx, top + dy, right - dx, bottom - dy)
}

/** Maps a rectangle given in [from] coordinates into [to], keeping its relative position. */
internal fun MapRect.mapBetween(from: MapRect, to: MapRect): MapRect {
    if (from.width <= 0f || from.height <= 0f) return to
    val sx = to.width / from.width
    val sy = to.height / from.height
    return MapRect(
        left = to.left + (left - from.left) * sx,
        top = to.top + (top - from.top) * sy,
        right = to.left + (right - from.left) * sx,
        bottom = to.top + (bottom - from.top) * sy,
    )
}
