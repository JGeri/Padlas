package hu.padlas.feature.map

import androidx.compose.runtime.Immutable

/** One rectangle of the map, and everything it needs to know about itself. */
@Immutable
data class MapNode(
    val id: String,
    val label: String,
    val sizeBytes: Long,
    val kind: MapNodeKind,
    /** Index into the design system Spectrum; children inherit their parent category. */
    val colorIndex: Int = OTHER_COLOR,
    val itemCount: Int = 0,
    val path: String? = null,
    val packageName: String? = null,
    val children: List<MapNode> = emptyList(),
) {
    val drillable: Boolean get() = children.isNotEmpty()
}

enum class MapNodeKind {
    ROOT,
    FOLDER,
    FILE,
    APPS,
    APP,
    APP_CODE,
    APP_DATA,
    APP_CACHE,
    SYSTEM,
}

@Immutable
data class MediaSlice(val label: String, val bytes: Long, val itemCount: Int, val colorIndex: Int)

@Immutable
data class CacheStop(val packageName: String, val label: String, val cacheBytes: Long)

@Immutable
data class StorageMap(
    val root: MapNode,
    val media: List<MediaSlice>,
    val cacheStops: List<CacheStop>,
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val hasUsageStats: Boolean,
    val hasAllFiles: Boolean,
) {
    /**
     * Cheap identity for the size list. The squarified layout is only allowed to be
     * recomputed when this changes (spec 7).
     */
    val signature: Long = root.signature() * 31L + usedBytes + freeBytes +
        (if (hasUsageStats) 1L else 0L) + (if (hasAllFiles) 2L else 0L)
}

private fun MapNode.signature(): Long {
    var acc = id.hashCode().toLong() * 31L + sizeBytes
    children.forEach { acc = acc * 31L + it.signature() }
    return acc
}

const val PHOTO_COLOR = 0
const val VIDEO_COLOR = 1
const val APPS_COLOR = 2
const val SYSTEM_COLOR = 3
const val OTHER_COLOR = 4
