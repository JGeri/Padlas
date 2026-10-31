package hu.padlas.feature.map

import androidx.compose.runtime.Immutable
import hu.padlas.core.common.text.UiText
import hu.padlas.core.common.text.uiText
import java.util.PriorityQueue

/** A grandchild as the treemap draws it inside its folder: only weight and colour. */
@Immutable
internal data class PreviewSlice(
    val key: Int,
    val bytes: Long,
    val category: FileCategory,
    val isRest: Boolean,
)

@Immutable
internal data class Crumb(val path: String, val label: UiText)

@Immutable
internal data class DirectoryUi(
    val path: String,
    val label: UiText,
    val crumbs: List<Crumb>,
    val bytes: Long,
    val fileCount: Int,
    /** Indexed by [FileCategory.ordinal]. */
    val categoryBytes: List<Long>,
    val entries: List<StorageItem>,
    /** The treemap's cut of [entries]; folders in it carry a [previews] entry. */
    val plan: TreemapPlan,
    val previews: Map<String, List<PreviewSlice>>,
    val version: Int,
) {
    val isRoot: Boolean get() = crumbs.size <= 1
    val parentPath: String? get() = crumbs.getOrNull(crumbs.size - 2)?.path
}

internal object DirectoryListing {

    val ROOT_LABEL: UiText = uiText(R.string.map_root_label)
    const val PREVIEW_LIMIT = 24

    fun build(root: DirNode, path: String, version: Int): DirectoryUi {
        val dir = root.deepestExisting(path)
        val entries = entriesOf(dir)
        val plan = TreemapPlanner.plan(entries)
        val previews = HashMap<String, List<PreviewSlice>>()
        for (item in plan.items) {
            if (!item.isDirectory) continue
            val node = dir.child(item.name) ?: continue
            previews[item.path] = previewOf(node)
        }
        return DirectoryUi(
            path = dir.path,
            label = if (dir.parent == null) ROOT_LABEL else UiText.Raw(dir.name),
            crumbs = crumbsOf(dir),
            bytes = dir.bytes,
            fileCount = dir.fileCount,
            categoryBytes = dir.categoryBytes.toList(),
            entries = entries,
            plan = plan,
            previews = previews,
            version = version,
        )
    }

    fun entriesOf(dir: DirNode): List<StorageItem> {
        val out = ArrayList<StorageItem>(dir.childCount)
        dir.dirs.forEach { out += it.toItem() }
        for (i in 0 until dir.files.count) out += dir.fileItem(i)
        out.sortWith(compareByDescending<StorageItem> { it.bytes }.thenBy { it.name.lowercase() })
        return out
    }

    fun crumbsOf(dir: DirNode): List<Crumb> {
        val chain = ArrayList<Crumb>()
        var node: DirNode? = dir
        while (node != null) {
            chain += Crumb(node.path, if (node.parent == null) ROOT_LABEL else UiText.Raw(node.name))
            node = node.parent
        }
        chain.reverse()
        return chain
    }

    /**
     * The biggest children of [dir] with everything else folded into one slice. A heap keeps
     * this linear, which matters for a camera folder with thousands of photos in it.
     */
    fun previewOf(dir: DirNode, limit: Int = PREVIEW_LIMIT): List<PreviewSlice> {
        class Child(val bytes: Long, val category: FileCategory)

        val heap = PriorityQueue<Child>(limit + 1) { a, b -> a.bytes.compareTo(b.bytes) }
        var restBytes = 0L
        fun offer(child: Child) {
            if (child.bytes <= 0L) return
            heap.add(child)
            if (heap.size > limit) restBytes += heap.poll()?.bytes ?: 0L
        }
        dir.dirs.forEach { offer(Child(it.bytes, it.dominantCategory())) }
        for (i in 0 until dir.files.count) offer(Child(dir.files.sizes[i], dir.files.category(i)))

        val slices = heap.sortedByDescending { it.bytes }
            .mapIndexed { index, child -> PreviewSlice(index, child.bytes, child.category, isRest = false) }
        return if (restBytes > 0L) slices + PreviewSlice(slices.size, restBytes, FileCategory.OTHER, isRest = true) else slices
    }
}
