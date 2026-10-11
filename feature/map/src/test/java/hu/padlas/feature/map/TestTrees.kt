package hu.padlas.feature.map

internal const val ROOT = "/storage/emulated/0"

/** Builds a tree the way the walker would: children first, then the totals. */
internal fun tree(rootPath: String = ROOT, block: TreeBuilder.() -> Unit): DirNode {
    val root = DirNode(rootPath, null)
    TreeBuilder(root).apply(block).finish()
    return root
}

internal class TreeBuilder(private val node: DirNode) {
    private val leaves = FileLeavesBuilder()
    private val dirs = ArrayList<DirNode>()

    fun file(name: String, size: Long, modified: Long = 1L) {
        leaves.add(name, size, modified, FileTypes.categoryOf(name))
    }

    fun dir(name: String, block: TreeBuilder.() -> Unit = {}) {
        val child = DirNode(name, node)
        TreeBuilder(child).apply(block).finish()
        dirs += child
    }

    fun finish() {
        node.files = leaves.build()
        node.dirs = dirs
        node.recomputeTotals()
    }
}

/** Every node's totals must equal what a fresh recount of its subtree gives. */
internal fun DirNode.assertConsistent() {
    dirs.forEach { it.assertConsistent() }
    val expectedBytes = (0 until files.count).sumOf { files.sizes[it] } + dirs.sumOf { it.bytes }
    val expectedFiles = files.count + dirs.sumOf { it.fileCount }
    check(bytes == expectedBytes) { "$path: bytes $bytes, expected $expectedBytes" }
    check(fileCount == expectedFiles) { "$path: files $fileCount, expected $expectedFiles" }
    for (c in 0 until CATEGORY_COUNT) {
        val own = (0 until files.count).filter { files.categories[it].toInt() == c }.sumOf { files.sizes[it] }
        val expected = own + dirs.sumOf { it.categoryBytes[c] }
        check(categoryBytes[c] == expected) { "$path: category $c is ${categoryBytes[c]}, expected $expected" }
    }
}
