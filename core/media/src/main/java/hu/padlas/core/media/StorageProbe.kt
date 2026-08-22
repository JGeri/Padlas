package hu.padlas.core.media

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.padlas.core.common.di.IoDispatcher
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class VolumeInfo(val totalBytes: Long, val freeBytes: Long, val usedBytes: Long)

data class AppUsage(
    val packageName: String,
    val label: String,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
)

data class FileNode(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val children: List<FileNode>,
)

/** Feeds the map screen (M1): volume totals, the app/data/cache split and the treemap. */
@Singleton
class StorageProbe @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun volume(): VolumeInfo = withContext(io) {
        val stats = context.getSystemService(StorageStatsManager::class.java)
        val total = runCatching { stats?.getTotalBytes(StorageManager.UUID_DEFAULT) ?: 0L }
            .getOrDefault(0L)
        val free = runCatching { stats?.getFreeBytes(StorageManager.UUID_DEFAULT) ?: 0L }
            .getOrDefault(0L)
        if (total > 0L) VolumeInfo(total, free, total - free) else statFsVolume()
    }

    suspend fun appUsage(): List<AppUsage> = withContext(io) {
        if (!hasUsageStatsPermission()) return@withContext emptyList()
        val stats = context.getSystemService(StorageStatsManager::class.java)
            ?: return@withContext emptyList()
        val packageManager = context.packageManager
        val user = Process.myUserHandle()
        val installed = runCatching {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        }.getOrDefault(emptyList())

        installed.mapNotNull { info ->
            currentCoroutineContext().ensureActive()
            runCatching {
                val measured = stats.queryStatsForPackage(
                    StorageManager.UUID_DEFAULT,
                    info.packageName,
                    user,
                )
                AppUsage(
                    packageName = info.packageName,
                    label = packageManager.getApplicationLabel(info).toString(),
                    appBytes = measured.appBytes,
                    // dataBytes already contains the cache, so the split has to subtract it.
                    dataBytes = (measured.dataBytes - measured.cacheBytes).coerceAtLeast(0L),
                    cacheBytes = measured.cacheBytes,
                )
            }.getOrNull()
        }.sortedByDescending { it.appBytes + it.dataBytes + it.cacheBytes }
    }

    suspend fun treeUnder(path: String, maxDepth: Int = DEFAULT_DEPTH): FileNode = withContext(io) {
        walk(File(path), 0, maxDepth)
    }

    fun hasUsageStatsPermission(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = runCatching {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }.getOrDefault(AppOpsManager.MODE_ERRORED)
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT ->
                context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                    PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }

    fun hasAllFilesAccess(): Boolean =
        runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)

    /**
     * Sizes stay accurate below [maxDepth] because the treemap needs real folder weights;
     * only the child lists are cut off, so a deep tree still reports the right bytes.
     */
    private fun walk(file: File, depth: Int, maxDepth: Int): FileNode {
        if (!file.isDirectory) {
            return FileNode(file.name, file.path, file.length(), false, emptyList())
        }
        if (isOffLimits(file) || depth >= HARD_DEPTH_LIMIT) {
            return FileNode(file.name, file.path, 0L, true, emptyList())
        }
        val entries = file.listFiles()
            ?: return FileNode(file.name, file.path, 0L, true, emptyList())
        val children = entries.map { walk(it, depth + 1, maxDepth) }
        val size = children.sumOf { it.sizeBytes }
        val visible = if (depth >= maxDepth) {
            emptyList()
        } else {
            children.sortedByDescending { it.sizeBytes }
        }
        return FileNode(file.name, file.path, size, true, visible)
    }

    /**
     * /Android/data is unreadable even with MANAGE_EXTERNAL_STORAGE and only burns time.
     * /Android/media is readable and often holds gigabytes of WhatsApp media, so it stays in.
     */
    private fun isOffLimits(file: File): Boolean {
        val path = file.absolutePath
        return path.endsWith(ANDROID_DATA, ignoreCase = true) ||
            path.contains("$ANDROID_DATA/", ignoreCase = true)
    }

    private fun statFsVolume(): VolumeInfo = runCatching {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        VolumeInfo(total, free, total - free)
    }.getOrDefault(VolumeInfo(0L, 0L, 0L))

    private companion object {
        const val DEFAULT_DEPTH = 2
        const val HARD_DEPTH_LIMIT = 32
        const val ANDROID_DATA = "/Android/data"
    }
}
