package hu.padlas.feature.map

import androidx.compose.runtime.Immutable
import java.util.Locale
import java.util.PriorityQueue

/** One file or folder as every list on the screen sees it. */
@Immutable
internal data class StorageItem(
    val path: String,
    val name: String,
    val parentPath: String,
    val bytes: Long,
    val modified: Long,
    val category: FileCategory,
    val isDirectory: Boolean,
    /** Files under a folder, recursively; zero for a file. */
    val fileCount: Int,
)

@Immutable
internal data class CategoryTotal(val category: FileCategory, val bytes: Long, val count: Int)

@Immutable
internal data class ExtensionTotal(
    val extension: String,
    val category: FileCategory,
    val bytes: Long,
    val count: Int,
)

internal fun DirNode.toItem(): StorageItem = StorageItem(
    path = path,
    name = name,
    parentPath = parent?.path.orEmpty(),
    bytes = bytes,
    modified = modified,
    category = dominantCategory(),
    isDirectory = true,
    fileCount = fileCount,
)

internal fun DirNode.fileItem(index: Int): StorageItem = StorageItem(
    path = path + "/" + files.names[index],
    name = files.names[index],
    parentPath = path,
    bytes = files.sizes[index],
    modified = files.modified[index],
    category = files.category(index),
    isDirectory = false,
    fileCount = 0,
)

/** Folders the system or other apps rely on; offering them for deletion would be a trap. */
internal object StoragePaths {

    private val standardTopLevel = setOf(
        "android", "dcim", "pictures", "movies", "music", "download", "documents", "alarms",
        "notifications", "podcasts", "ringtones", "audiobooks", "recordings",
    )
    private val androidChildren = setOf("media", "data", "obb")

    fun isProtected(path: String, rootPath: String): Boolean {
        if (path == rootPath || !path.startsWith("$rootPath/")) return true
        val segments = path.removePrefix("$rootPath/").lowercase(Locale.ROOT).split('/')
        return when (segments.size) {
            1 -> segments[0] == "android"
            2 -> segments[0] == "android" && segments[1] in androidChildren
            else -> false
        }
    }

    /** An empty standard folder is not clutter: the system recreates it on the next boot. */
    fun isEmptyDirCandidate(node: DirNode): Boolean {
        val parent = node.parent ?: return false
        val lowered = node.name.lowercase(Locale.ROOT)
        if (parent.parent == null) return lowered !in standardTopLevel
        val grand = parent.parent
        if (grand?.parent == null && parent.name.equals("android", ignoreCase = true)) {
            return lowered !in androidChildren
        }
        return true
    }
}

/**
 * Everything the type, file and cleanup views need, derived from the tree in one pass. It
 * is rebuilt after a delete rather than patched: a pass over the in-memory tree takes a few
 * dozen milliseconds, and a patched top list would quietly shrink instead of refilling.
 */
internal class StorageIndex(
    val totalBytes: Long,
    val totalFiles: Int,
    val totalDirs: Int,
    val categories: List<CategoryTotal>,
    val extensions: List<ExtensionTotal>,
    val largest: List<StorageItem>,
    val largestByCategory: Map<FileCategory, List<StorageItem>>,
    val installers: List<StorageItem>,
    val oldDownloads: List<StorageItem>,
    val thumbnailDirs: List<StorageItem>,
    val emptyDirs: List<StorageItem>,
    /** Android/media, which StorageStatsManager also books as app data. */
    val appMediaBytes: Long,
) {

    companion object {
        const val TOP_FILES = 300
        const val TOP_EXTENSIONS = 30
        const val LIST_CAP = 2_000
        const val OLD_DOWNLOAD_MS = 90L * 24 * 60 * 60 * 1000
        const val THUMBNAILS = ".thumbnails"

        fun build(
            root: DirNode,
            now: Long,
            topFiles: Int = TOP_FILES,
            topExtensions: Int = TOP_EXTENSIONS,
        ): StorageIndex {
            val categoryBytes = LongArray(CATEGORY_COUNT)
            val categoryCount = IntArray(CATEGORY_COUNT)
            val extensions = HashMap<String, ExtensionAccumulator>()
            val overall = TopFiles(topFiles)
            val perCategory = Array(CATEGORY_COUNT) { TopFiles(topFiles) }
            val installers = ArrayList<StorageItem>()
            val oldDownloads = ArrayList<StorageItem>()
            val thumbnails = ArrayList<StorageItem>()
            val empty = ArrayList<StorageItem>()
            val cutoff = now - OLD_DOWNLOAD_MS
            var dirs = 0

            val stack = ArrayDeque<Frame>()
            stack.addLast(Frame(root, inDownloads = false, inEmpty = false))
            while (stack.isNotEmpty()) {
                val (dir, inDownloads, inEmpty) = stack.removeLast()
                if (dir !== root) dirs++
                val files = dir.files
                for (i in 0 until files.count) {
                    val size = files.sizes[i]
                    val category = files.categories[i].toInt()
                    categoryBytes[category] += size
                    categoryCount[category]++
                    val extension = FileTypes.extensionOf(files.names[i])
                    extensions.getOrPut(extension) {
                        ExtensionAccumulator(FileCategory.fromOrdinal(category))
                    }.add(size)
                    overall.offer(dir, i)
                    perCategory[category].offer(dir, i)
                    if (category == FileCategory.INSTALLERS.ordinal && installers.size < LIST_CAP) {
                        installers += dir.fileItem(i)
                    }
                    val modified = files.modified[i]
                    if (inDownloads && modified in 1 until cutoff && oldDownloads.size < LIST_CAP) {
                        oldDownloads += dir.fileItem(i)
                    }
                }
                for (child in dir.dirs) {
                    if (child.name.equals(THUMBNAILS, ignoreCase = true) && child.bytes > 0L) {
                        thumbnails += child.toItem()
                    }
                    // Only the outermost empty folder is offered; deleting it takes the
                    // empty ones nested inside along with it.
                    val emptyHere = !inEmpty && child.fileCount == 0 && StoragePaths.isEmptyDirCandidate(child)
                    if (emptyHere && empty.size < LIST_CAP) empty += child.toItem()
                    val downloads = inDownloads || (dir === root && isDownloads(child.name))
                    stack.addLast(Frame(child, downloads, inEmpty || emptyHere))
                }
            }

            val categories = FileCategory.all
                .map { CategoryTotal(it, categoryBytes[it.ordinal], categoryCount[it.ordinal]) }
                .filter { it.count > 0 }
                .sortedByDescending { it.bytes }
            val topExtensionList = extensions.entries
                .map { (ext, acc) -> ExtensionTotal(ext, acc.category, acc.bytes, acc.count) }
                .sortedByDescending { it.bytes }
                .take(topExtensions)
            val appMedia = root.child("Android")?.child("media")?.bytes ?: 0L

            return StorageIndex(
                totalBytes = root.bytes,
                totalFiles = root.fileCount,
                totalDirs = dirs,
                categories = categories,
                extensions = topExtensionList,
                largest = overall.drain(),
                largestByCategory = FileCategory.all.associateWith { perCategory[it.ordinal].drain() },
                installers = installers.sortedByDescending { it.bytes },
                oldDownloads = oldDownloads.sortedByDescending { it.bytes },
                thumbnailDirs = thumbnails.sortedByDescending { it.bytes },
                emptyDirs = empty.sortedBy { it.path.lowercase(Locale.ROOT) },
                appMediaBytes = appMedia,
            )
        }

        private fun isDownloads(name: String): Boolean =
            name.equals("Download", ignoreCase = true) || name.equals("Downloads", ignoreCase = true)
    }

    private data class Frame(val dir: DirNode, val inDownloads: Boolean, val inEmpty: Boolean)

    private class ExtensionAccumulator(val category: FileCategory) {
        var bytes = 0L
        var count = 0

        fun add(size: Long) {
            bytes += size
            count++
        }
    }

    /**
     * A bounded min-heap: once full, a file only costs an allocation if it beats the
     * smallest one kept, which on a real card is almost never.
     */
    private class TopFiles(private val capacity: Int) {
        private class Candidate(val size: Long, val dir: DirNode, val index: Int)

        private val heap = PriorityQueue<Candidate>(capacity.coerceAtLeast(1)) { a, b -> a.size.compareTo(b.size) }

        fun offer(dir: DirNode, index: Int) {
            if (capacity <= 0) return
            val size = dir.files.sizes[index]
            if (heap.size < capacity) {
                heap.add(Candidate(size, dir, index))
            } else if (size > (heap.peek()?.size ?: Long.MAX_VALUE)) {
                heap.poll()
                heap.add(Candidate(size, dir, index))
            }
        }

        fun drain(): List<StorageItem> = heap
            .sortedByDescending { it.size }
            .map { it.dir.fileItem(it.index) }
    }
}
