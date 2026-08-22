package hu.padlas.feature.map

import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.common.result.outcomeOf
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.model.BucketAggregate
import hu.padlas.core.database.model.MediaTypeAggregate
import hu.padlas.core.media.AppUsage
import hu.padlas.core.media.FileNode
import hu.padlas.core.media.StorageProbe
import hu.padlas.core.media.VolumeInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * The map is three sources laid over each other: the file tree gives the bytes, the index
 * gives item counts and the media split, and StorageStatsManager gives the app, data and
 * cache breakdown that no file walk can see.
 */
@Singleton
class MapRepository @Inject constructor(
    private val assetDao: AssetDao,
    private val probe: StorageProbe,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val refreshes = MutableStateFlow(0)

    val map: Flow<StorageMap> = combine(
        refreshes.map { probeSnapshot() },
        assetDao.observeByMediaType(),
        assetDao.observeBuckets(),
    ) { snapshot, media, buckets -> build(snapshot, media, buckets) }
        // The layout is only allowed to be recomputed when a size actually moved (spec 7).
        .distinctUntilChanged { old, new -> old.signature == new.signature }
        .flowOn(io)

    fun refresh() = refreshes.update { it + 1 }

    fun hasUsageStatsPermission(): Boolean = probe.hasUsageStatsPermission()

    /**
     * The probe only walks two levels at a time; drilling deeper asks for the next two
     * from where the user actually went instead of walking the whole card up front.
     */
    suspend fun expand(path: String): MapNode? = withContext(io) {
        val node = outcomeOf(WALK_FAILED) { probe.treeUnder(path, TREE_DEPTH) }.valueOrNull()
        node?.let { fileNode(it, emptyMap(), colorFor(it.name)) }
    }

    private suspend fun probeSnapshot(): Probe {
        val hasUsageStats = probe.hasUsageStatsPermission()
        val hasAllFiles = probe.hasAllFilesAccess()
        val volume = outcomeOf(VOLUME_FAILED) { probe.volume() }.valueOrNull()
            ?: VolumeInfo(0L, 0L, 0L)
        val apps = if (hasUsageStats) {
            outcomeOf(APPS_FAILED) { probe.appUsage() }.valueOrNull().orEmpty()
        } else {
            emptyList()
        }
        val tree = if (hasAllFiles) {
            outcomeOf(WALK_FAILED) { probe.treeUnder(SHARED_ROOT, TREE_DEPTH) }.valueOrNull()
        } else {
            null
        }
        return Probe(volume, apps, tree, hasUsageStats, hasAllFiles)
    }

    private fun build(
        snapshot: Probe,
        media: List<MediaTypeAggregate>,
        buckets: List<BucketAggregate>,
    ): StorageMap {
        val bucketsByPath = buckets.associateBy { normalize(it.relativePath) }
        val children = ArrayList<MapNode>()

        val tree = snapshot.tree
        if (tree != null && tree.children.isNotEmpty()) {
            tree.children.forEach { children += fileNode(it, bucketsByPath, colorFor(it.name)) }
        } else {
            // Without all-files access the only thing we can honestly draw is what the
            // index itself holds: the media buckets.
            children += buckets.map(::bucketNode)
        }
        if (snapshot.apps.isNotEmpty()) children += appsNode(snapshot.apps)

        val measured = children.sumOf { it.sizeBytes }
        val used = if (snapshot.volume.usedBytes > 0L) snapshot.volume.usedBytes else measured
        val remainder = used - measured
        if (remainder > REMAINDER_FLOOR) {
            children += MapNode(
                id = "system",
                label = "Rendszer és egyéb",
                sizeBytes = remainder,
                kind = MapNodeKind.SYSTEM,
                colorIndex = SYSTEM_COLOR,
            )
        }

        val sorted = children.sortedByDescending { it.sizeBytes }
        return StorageMap(
            root = MapNode(
                id = ROOT_ID,
                label = "Belső tárhely",
                sizeBytes = maxOf(used, measured),
                kind = MapNodeKind.ROOT,
                path = SHARED_ROOT,
                children = sorted,
            ),
            media = media.map(::mediaSlice),
            cacheStops = snapshot.apps
                .filter { it.cacheBytes > 0L }
                .sortedByDescending { it.cacheBytes }
                .map { CacheStop(it.packageName, it.label, it.cacheBytes) },
            totalBytes = snapshot.volume.totalBytes,
            freeBytes = snapshot.volume.freeBytes,
            usedBytes = used,
            hasUsageStats = snapshot.hasUsageStats,
            hasAllFiles = snapshot.hasAllFiles,
        )
    }

    private fun fileNode(
        node: FileNode,
        buckets: Map<String, BucketAggregate>,
        color: Int,
    ): MapNode = MapNode(
        id = node.path,
        label = node.name,
        sizeBytes = node.sizeBytes,
        kind = if (node.isDirectory) MapNodeKind.FOLDER else MapNodeKind.FILE,
        colorIndex = color,
        itemCount = buckets[relativeOf(node.path)]?.itemCount ?: 0,
        path = node.path,
        children = node.children
            .map { fileNode(it, buckets, color) }
            .sortedByDescending { it.sizeBytes },
    )

    private fun bucketNode(bucket: BucketAggregate): MapNode = MapNode(
        id = "bucket:" + bucket.bucketId,
        label = bucket.bucketName,
        sizeBytes = bucket.totalBytes,
        kind = MapNodeKind.FOLDER,
        colorIndex = colorFor(bucket.relativePath),
        itemCount = bucket.itemCount,
        path = bucket.relativePath,
    )

    private fun appsNode(apps: List<AppUsage>): MapNode {
        val children = apps
            .map { app ->
                MapNode(
                    id = APP_PREFIX + app.packageName,
                    label = app.label,
                    sizeBytes = app.appBytes + app.dataBytes + app.cacheBytes,
                    kind = MapNodeKind.APP,
                    colorIndex = APPS_COLOR,
                    packageName = app.packageName,
                    children = listOfNotNull(
                        part(app, "Program", app.appBytes, MapNodeKind.APP_CODE),
                        part(app, "Adat", app.dataBytes, MapNodeKind.APP_DATA),
                        part(app, "Gyorsítótár", app.cacheBytes, MapNodeKind.APP_CACHE),
                    ),
                )
            }
            .sortedByDescending { it.sizeBytes }
        return MapNode(
            id = APPS_ID,
            label = "Appok",
            sizeBytes = children.sumOf { it.sizeBytes },
            kind = MapNodeKind.APPS,
            colorIndex = APPS_COLOR,
            children = children,
        )
    }

    private fun part(app: AppUsage, label: String, bytes: Long, kind: MapNodeKind): MapNode? =
        if (bytes <= 0L) {
            null
        } else {
            MapNode(
                id = APP_PREFIX + app.packageName + ":" + kind.name,
                label = label,
                sizeBytes = bytes,
                kind = kind,
                colorIndex = APPS_COLOR,
                packageName = app.packageName,
            )
        }

    private fun mediaSlice(aggregate: MediaTypeAggregate): MediaSlice = when (aggregate.mediaType) {
        "image" -> MediaSlice("Fotók", aggregate.totalBytes, aggregate.itemCount, PHOTO_COLOR)
        "video" -> MediaSlice("Videók", aggregate.totalBytes, aggregate.itemCount, VIDEO_COLOR)
        "audio" -> MediaSlice("Hang", aggregate.totalBytes, aggregate.itemCount, SYSTEM_COLOR)
        else -> MediaSlice("Egyéb", aggregate.totalBytes, aggregate.itemCount, OTHER_COLOR)
    }

    private data class Probe(
        val volume: VolumeInfo,
        val apps: List<AppUsage>,
        val tree: FileNode?,
        val hasUsageStats: Boolean,
        val hasAllFiles: Boolean,
    )

    private companion object {
        const val SHARED_ROOT = "/storage/emulated/0"
        const val ROOT_ID = "root"
        const val APPS_ID = "apps"
        const val APP_PREFIX = "app:"
        const val TREE_DEPTH = 2

        /** Below this the remainder is measurement noise, not a category. */
        const val REMAINDER_FLOOR = 64L * 1024L * 1024L

        const val VOLUME_FAILED = "A tárhely mérete nem olvasható."
        const val APPS_FAILED = "Az appok mérete nem olvasható."
        const val WALK_FAILED = "A mappák nem járhatók be."

        fun relativeOf(path: String): String = normalize(path.removePrefix(SHARED_ROOT))

        fun normalize(path: String): String = path.trim('/')

        /** Top level directories carry the category colour; everything under them inherits it. */
        fun colorFor(name: String): Int = when (name.trim('/').substringBefore('/').lowercase()) {
            "dcim", "pictures", "screenshots" -> PHOTO_COLOR
            "movies", "video", "videos" -> VIDEO_COLOR
            "android" -> APPS_COLOR
            "system", "system_dcim" -> SYSTEM_COLOR
            else -> OTHER_COLOR
        }
    }
}
