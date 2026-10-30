package hu.padlas.feature.map

import java.io.IOException
import java.nio.file.DirectoryIteratorException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Written by every walker thread, sampled by the progress ticker. */
internal class ScanCounters {
    val files = AtomicLong()
    val bytes = AtomicLong()
    val categoryBytes = AtomicLongArray(CATEGORY_COUNT)

    @Volatile
    var current: String = ""

    fun categorySnapshot(): List<Long> = List(CATEGORY_COUNT) { categoryBytes.get(it) }
}

/**
 * Walks a directory tree with one lstat per entry: the directory stream hands back the
 * attributes in the same call, where File.length() plus isDirectory() would cost two more
 * round trips through the FUSE daemon per file.
 *
 * Symbolic links are never followed (that is how a walk ends up in a loop or on another
 * volume) and an unreadable folder simply counts as empty instead of failing the scan.
 */
internal class TreeWalker(
    private val dispatcher: CoroutineDispatcher,
    private val counters: ScanCounters = ScanCounters(),
    skippedPaths: Collection<String> = emptyList(),
    private val parallelDepth: Int = PARALLEL_DEPTH,
) {

    private val skipped: Set<String> = skippedPaths.mapTo(HashSet()) { it.trimEnd('/').lowercase(Locale.ROOT) }

    suspend fun walk(rootPath: String): DirNode {
        val root = DirNode(rootPath.trimEnd('/'), null)
        val path = Paths.get(root.path)
        attributesOf(path)?.let { root.modified = it.lastModifiedTime().toMillis() }
        fill(root, path, depth = 0)
        return root
    }

    /** Measures one folder again, for example after a delete that only partly went through. */
    suspend fun remeasure(parent: DirNode, name: String): DirNode {
        val node = DirNode(name, parent)
        val path = Paths.get(node.path)
        attributesOf(path)?.let { node.modified = it.lastModifiedTime().toMillis() }
        fill(node, path, depth = parallelDepth)
        return node
    }

    private suspend fun fill(node: DirNode, dir: Path, depth: Int) {
        currentCoroutineContext().ensureActive()
        counters.current = node.path
        val leaves = FileLeavesBuilder()
        val children = ArrayList<DirNode>()
        val categoryDelta = LongArray(CATEGORY_COUNT)
        val mangled = HashSet<String>()

        try {
            Files.newDirectoryStream(dir).use { stream ->
                for (entry in stream) {
                    val attrs = attributesOf(entry)
                    val name = entry.fileName?.toString()
                    if (attrs == null || name == null || attrs.isSymbolicLink) continue
                    // Bytes that are not UTF-8 decode to U+FFFD, so two different names can
                    // read the same here; the copy would be a duplicate key in every list.
                    if (REPLACEMENT in name && !mangled.add(name)) continue
                    if (attrs.isDirectory) {
                        val child = DirNode(name, node)
                        if (child.path.lowercase(Locale.ROOT) !in skipped) {
                            child.modified = attrs.lastModifiedTime().toMillis()
                            children += child
                        }
                    } else if (attrs.isRegularFile) {
                        val category = FileTypes.categoryOf(name)
                        val size = attrs.size()
                        leaves.add(name, size, attrs.lastModifiedTime().toMillis(), category)
                        categoryDelta[category.ordinal] += size
                    }
                }
            }
        } catch (e: IOException) {
            // Unreadable: whatever was listed before the failure still counts.
        } catch (e: DirectoryIteratorException) {
        } catch (e: SecurityException) {
        }

        node.files = leaves.build()
        node.dirs = children
        counters.files.addAndGet(leaves.count.toLong())
        counters.bytes.addAndGet(leaves.bytes)
        for (c in 0 until CATEGORY_COUNT) {
            if (categoryDelta[c] != 0L) counters.categoryBytes.addAndGet(c, categoryDelta[c])
        }

        // Only the top levels fan out: below that a folder is usually small, and the
        // coroutine bookkeeping would cost more than the parallelism wins.
        if (depth < parallelDepth && children.size > 1) {
            coroutineScope {
                for (child in children) {
                    launch(dispatcher) { fill(child, dir.resolve(child.name), depth + 1) }
                }
            }
        } else if (depth < MAX_DEPTH) {
            for (child in children) fill(child, dir.resolve(child.name), depth + 1)
        }
        node.recomputeTotals()
    }

    private fun attributesOf(path: Path): BasicFileAttributes? = try {
        Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    companion object {
        const val PARALLEL_DEPTH = 2

        /** Real trees are never this deep; the cap only guards against a pathological one. */
        const val MAX_DEPTH = 96

        private const val REPLACEMENT = '\uFFFD'
    }
}
