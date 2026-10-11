package hu.padlas.feature.map

import android.app.usage.StorageStatsManager
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.Process
import android.os.storage.StorageManager
import androidx.compose.runtime.Immutable
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.padlas.core.common.di.ApplicationScope
import hu.padlas.core.common.di.DefaultDispatcher
import hu.padlas.core.common.di.IoDispatcher
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Immutable
internal data class ScanProgress(
    val files: Long,
    val bytes: Long,
    val currentPath: String,
    /** A best guess of what the walk will find; zero when nothing sensible is known. */
    val expectedBytes: Long,
    val categoryBytes: List<Long>,
)

/** The tree is shared and mutable; it is only ever touched under [StorageAnalyzer.read]. */
internal class StorageSnapshot(
    val root: DirNode,
    val index: StorageIndex,
    val scannedAt: Long,
    val version: Int,
)

internal data class AnalyzerState(
    val snapshot: StorageSnapshot? = null,
    /** Non-null exactly while a scan runs. */
    val progress: ScanProgress? = null,
)

internal data class DeleteOutcome(val freedBytes: Long, val removed: Int, val failed: Int)

/**
 * Owns the one scan of the shared storage. It lives as long as the process, so switching
 * tabs or rotating never walks the card again; only an explicit rescan does.
 */
@Singleton
internal class StorageAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    @DefaultDispatcher private val cpu: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(AnalyzerState())
    val state: StateFlow<AnalyzerState> = _state.asStateFlow()

    private val treeLock = Mutex()
    private var scanJob: Job? = null

    /** Paths deleted while a scan was running: the fresh tree may have seen them alive. */
    private val tombstones = ConcurrentLinkedQueue<String>()

    /** FUSE answers several stat calls at once; past four the daemon itself is the queue. */
    private val walkDispatcher = io.limitedParallelism(PARALLELISM)

    val rootPath: String by lazy { Environment.getExternalStorageDirectory().absolutePath.trimEnd('/') }

    private val isScanning: Boolean get() = scanJob?.isActive == true

    fun scan() {
        if (isScanning) return
        scanJob = scope.launch(io) { runScan() }
    }

    fun cancelScan() {
        scanJob?.cancel()
    }

    /** Runs [block] against the current tree while nothing can change it. */
    suspend fun <T> read(block: (StorageSnapshot) -> T): T? = treeLock.withLock {
        val snapshot = _state.value.snapshot ?: return@withLock null
        withContext(cpu) { block(snapshot) }
    }

    /**
     * Runs in the application scope: once files start disappearing the tree has to be
     * brought in line with the disk, even if the screen that asked has gone away.
     */
    suspend fun delete(items: List<StorageItem>): DeleteOutcome =
        scope.async(io) { performDelete(items) }.await()

    private suspend fun runScan() {
        val counters = ScanCounters()
        val expected = expectedBytes()
        tombstones.clear()
        publish(counters, expected)
        try {
            val root = coroutineScope {
                val ticker = launch {
                    while (isActive) {
                        delay(TICK_MS)
                        publish(counters, expected)
                    }
                }
                val tree = TreeWalker(walkDispatcher, counters, skippedPaths()).walk(rootPath)
                ticker.cancel()
                tree
            }
            val now = System.currentTimeMillis()
            treeLock.withLock {
                applyTombstones(root)
                val index = withContext(cpu) { StorageIndex.build(root, now) }
                val version = (_state.value.snapshot?.version ?: 0) + 1
                _state.value = AnalyzerState(StorageSnapshot(root, index, now, version), progress = null)
            }
        } catch (e: CancellationException) {
            _state.update { it.copy(progress = null) }
            throw e
        } catch (e: Exception) {
            // A failed walk keeps whatever result was there before instead of a half tree.
            _state.update { it.copy(progress = null) }
        }
    }

    private fun publish(counters: ScanCounters, expected: Long) {
        val progress = ScanProgress(
            files = counters.files.get(),
            bytes = counters.bytes.get(),
            currentPath = counters.current,
            expectedBytes = expected,
            categoryBytes = counters.categorySnapshot(),
        )
        _state.update { it.copy(progress = progress) }
    }

    /**
     * Shared storage as the system books it, minus the app-private folders the walk skips.
     * Without usage access the previous result is the next best guess.
     */
    private fun expectedBytes(): Long {
        val measured = runCatching {
            val stats = context.getSystemService(StorageStatsManager::class.java)
            val external = stats?.queryExternalStatsForUser(StorageManager.UUID_DEFAULT, Process.myUserHandle())
            if (external == null) 0L else (external.totalBytes - external.appBytes).coerceAtLeast(0L)
        }.getOrDefault(0L)
        return if (measured > 0L) measured else _state.value.snapshot?.root?.bytes ?: 0L
    }

    /** Unreadable since Android 11 even with all-files access; walking them only burns time. */
    private fun skippedPaths(): List<String> = listOf("$rootPath/Android/data", "$rootPath/Android/obb")

    private suspend fun performDelete(items: List<StorageItem>): DeleteOutcome {
        val root = rootPath
        val candidates = items.distinctBy { it.path }.filterNot { StoragePaths.isProtected(it.path, root) }
        // A file inside a folder that goes too would otherwise be counted twice.
        val targets = candidates.filter { item ->
            candidates.none { other -> other.isDirectory && item.path.startsWith(other.path + "/") }
        }

        var report = DeleteReport()
        val gone = ArrayList<StorageItem>()
        val partial = ArrayList<StorageItem>()
        for (target in targets) {
            val path = Paths.get(target.path)
            report += if (target.isDirectory && target.fileCount == 0) {
                FileDeleter.deleteEmptyTree(path)
            } else {
                FileDeleter.delete(path)
            }
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) partial += target else gone += target
        }

        treeLock.withLock {
            val snapshot = _state.value.snapshot
            if (snapshot != null) {
                gone.forEach { removeFromTree(snapshot.root, it) }
                partial.filter { it.isDirectory }.forEach { remeasure(snapshot.root, it) }
                val index = withContext(cpu) { StorageIndex.build(snapshot.root, System.currentTimeMillis()) }
                val next = StorageSnapshot(snapshot.root, index, snapshot.scannedAt, snapshot.version + 1)
                _state.update { it.copy(snapshot = next) }
            }
            if (isScanning) gone.forEach { tombstones += it.path }
        }

        notifyMediaScanner(report.mediaPaths, gone)
        return DeleteOutcome(report.freedBytes, gone.size, partial.size)
    }

    private fun removeFromTree(root: DirNode, item: StorageItem) {
        if (item.isDirectory) {
            val node = root.find(item.path) ?: return
            node.parent?.removeDir(node)
        } else {
            root.find(item.parentPath)?.removeFile(item.name)
        }
    }

    private suspend fun remeasure(root: DirNode, item: StorageItem) {
        val node = root.find(item.path) ?: return
        val parent = node.parent ?: return
        val fresh = TreeWalker(io, ScanCounters(), skippedPaths()).remeasure(parent, node.name)
        parent.replaceDir(node, fresh)
    }

    private fun applyTombstones(root: DirNode) {
        while (true) {
            val path = tombstones.poll() ?: break
            if (Files.exists(Paths.get(path), LinkOption.NOFOLLOW_LINKS)) continue
            val dir = root.find(path)
            if (dir != null) {
                dir.parent?.removeDir(dir)
            } else {
                root.find(path.substringBeforeLast('/'))?.removeFile(path.substringAfterLast('/'))
            }
        }
    }

    /**
     * MediaProvider notices deletes that go through FUSE on its own, but a scan request is
     * what makes the gallery drop its cached rows straight away.
     */
    private fun notifyMediaScanner(mediaPaths: List<String>, gone: List<StorageItem>) {
        val paths = (mediaPaths + gone.filter { it.isDirectory }.map { it.path }).distinct()
        if (paths.isEmpty()) return
        runCatching { MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null) }
    }

    private companion object {
        const val PARALLELISM = 4
        const val TICK_MS = 100L
    }
}
