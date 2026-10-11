package hu.padlas.feature.map

/**
 * Files are parallel primitive arrays rather than one object each: on a card with 300 000
 * files the object headers alone would weigh more than the names and sizes they describe.
 */
internal class FileLeaves(
    val names: Array<String>,
    val sizes: LongArray,
    val modified: LongArray,
    val categories: ByteArray,
) {
    val count: Int get() = names.size

    fun category(index: Int): FileCategory = FileCategory.fromOrdinal(categories[index].toInt())

    fun indexOf(name: String): Int {
        for (i in names.indices) if (names[i] == name) return i
        return -1
    }

    fun without(index: Int): FileLeaves {
        if (index !in 0 until count) return this
        if (count == 1) return Empty
        val keep = { i: Int -> if (i < index) i else i + 1 }
        return FileLeaves(
            names = Array(count - 1) { names[keep(it)] },
            sizes = LongArray(count - 1) { sizes[keep(it)] },
            modified = LongArray(count - 1) { modified[keep(it)] },
            categories = ByteArray(count - 1) { categories[keep(it)] },
        )
    }

    companion object {
        val Empty = FileLeaves(emptyArray(), LongArray(0), LongArray(0), ByteArray(0))
    }
}

internal class FileLeavesBuilder {
    private var names = arrayOfNulls<String>(INITIAL)
    private var sizes = LongArray(INITIAL)
    private var modified = LongArray(INITIAL)
    private var categories = ByteArray(INITIAL)

    var count: Int = 0
        private set
    var bytes: Long = 0L
        private set

    fun add(name: String, size: Long, modifiedAt: Long, category: FileCategory) {
        if (count == names.size) grow()
        names[count] = name
        sizes[count] = size
        modified[count] = modifiedAt
        categories[count] = category.ordinal.toByte()
        bytes += size
        count++
    }

    fun build(): FileLeaves {
        if (count == 0) return FileLeaves.Empty
        return FileLeaves(
            names = Array(count) { names[it].orEmpty() },
            sizes = sizes.copyOf(count),
            modified = modified.copyOf(count),
            categories = categories.copyOf(count),
        )
    }

    private fun grow() {
        val next = names.size * 2
        names = names.copyOf(next)
        sizes = sizes.copyOf(next)
        modified = modified.copyOf(next)
        categories = categories.copyOf(next)
    }

    private companion object {
        const val INITIAL = 8
    }
}

/**
 * One directory of the scanned card. Totals are recursive, and so is the byte split per
 * category: that is what lets a folder be coloured by what actually fills it.
 */
internal class DirNode(val name: String, val parent: DirNode?) {

    /** Stored rather than rebuilt from the parent chain, the UI asks for it constantly. */
    val path: String = if (parent == null) name else parent.path + "/" + name

    var modified: Long = 0L
    var bytes: Long = 0L
    var fileCount: Int = 0
    val categoryBytes = LongArray(CATEGORY_COUNT)

    /** Replaced, never mutated in place, so a reader holding the old list stays consistent. */
    var dirs: List<DirNode> = emptyList()
    var files: FileLeaves = FileLeaves.Empty

    val childCount: Int get() = dirs.size + files.count

    val depth: Int
        get() {
            var level = 0
            var node = parent
            while (node != null) {
                level++
                node = node.parent
            }
            return level
        }

    fun child(name: String): DirNode? = dirs.firstOrNull { it.name == name }

    fun dominantCategory(): FileCategory {
        var best = -1
        var bestBytes = 0L
        for (i in 0 until CATEGORY_COUNT) {
            if (categoryBytes[i] > bestBytes) {
                best = i
                bestBytes = categoryBytes[i]
            }
        }
        return if (best < 0) FileCategory.OTHER else FileCategory.fromOrdinal(best)
    }

    /** Own files plus the children's totals; the children have to be final already. */
    fun recomputeTotals() {
        var total = 0L
        var count = files.count
        categoryBytes.fill(0L)
        for (i in 0 until files.count) {
            val size = files.sizes[i]
            total += size
            categoryBytes[files.categories[i].toInt()] += size
        }
        for (dir in dirs) {
            total += dir.bytes
            count += dir.fileCount
            for (c in 0 until CATEGORY_COUNT) categoryBytes[c] += dir.categoryBytes[c]
        }
        bytes = total
        fileCount = count
    }

    internal fun adjust(deltaBytes: Long, deltaFiles: Int, deltaCategories: LongArray) {
        bytes += deltaBytes
        fileCount += deltaFiles
        for (c in 0 until CATEGORY_COUNT) categoryBytes[c] += deltaCategories[c]
    }
}

internal fun DirNode.find(path: String): DirNode? {
    if (path == this.path) return this
    if (!path.startsWith(this.path + "/")) return null
    var node = this
    for (segment in path.substring(this.path.length + 1).split('/')) {
        if (segment.isEmpty()) continue
        node = node.child(segment) ?: return null
    }
    return node
}

/** The deepest folder on the way to [path] that still exists, at worst this root. */
internal fun DirNode.deepestExisting(path: String): DirNode {
    if (!isSameOrAncestorOf(path)) return this
    var node = this
    val rest = path.removePrefix(this.path).trim('/')
    if (rest.isEmpty()) return this
    for (segment in rest.split('/')) {
        node = node.child(segment) ?: return node
    }
    return node
}

internal fun DirNode.isSameOrAncestorOf(path: String): Boolean =
    path == this.path || path.startsWith(this.path + "/")

/** Removes one file and takes its bytes off every ancestor. */
internal fun DirNode.removeFile(name: String): Boolean {
    val index = files.indexOf(name)
    if (index < 0) return false
    val size = files.sizes[index]
    val delta = LongArray(CATEGORY_COUNT)
    delta[files.categories[index].toInt()] = -size
    files = files.without(index)
    propagate(this, -size, -1, delta)
    return true
}

/** Detaches [child] and takes its whole subtree off every ancestor. */
internal fun DirNode.removeDir(child: DirNode): Boolean {
    if (dirs.none { it === child }) return false
    dirs = dirs.filterNot { it === child }
    val delta = LongArray(CATEGORY_COUNT) { -child.categoryBytes[it] }
    propagate(this, -child.bytes, -child.fileCount, delta)
    return true
}

/** Swaps a subtree for a freshly measured one and moves the difference up the chain. */
internal fun DirNode.replaceDir(old: DirNode, fresh: DirNode): Boolean {
    if (dirs.none { it === old }) return false
    dirs = dirs.map { if (it === old) fresh else it }
    val delta = LongArray(CATEGORY_COUNT) { fresh.categoryBytes[it] - old.categoryBytes[it] }
    propagate(this, fresh.bytes - old.bytes, fresh.fileCount - old.fileCount, delta)
    return true
}

private fun propagate(from: DirNode, bytes: Long, files: Int, categories: LongArray) {
    var node: DirNode? = from
    while (node != null) {
        node.adjust(bytes, files, categories)
        node = node.parent
    }
}
