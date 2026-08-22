package hu.padlas.core.media

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.padlas.core.common.di.IoDispatcher
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Spec 4.1. With Samsung Gallery to OneDrive sync on, a local delete can take the cloud copy
 * with it, and the Gallery keeps a second trash that MediaStore knows nothing about.
 */
@Singleton
class OneDriveGuard @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    fun isSamsungOneDriveSyncLikely(): Boolean =
        isInstalled(ONEDRIVE_PACKAGE) && hasSamsungGallerySync()

    /**
     * Best effort: the Gallery trash layout differs per One UI version, so every plausible
     * hidden folder is summed and a missing one simply contributes zero.
     */
    suspend fun samsungGalleryTrashBytes(): Long = withContext(io) {
        val root = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
            ?: File(EXTERNAL_FALLBACK)
        val roots = listOf(File(root, "DCIM"), File(root, "Pictures"), root)
        roots.sumOf { directory -> trashFoldersIn(directory).sumOf { sizeOf(it, 0) } }
    }

    private fun trashFoldersIn(directory: File): List<File> = runCatching {
        directory.listFiles()
            ?.filter { it.isDirectory && it.name.lowercase().startsWith(TRASH_PREFIX) }
            .orEmpty()
    }.getOrDefault(emptyList())

    private fun sizeOf(file: File, depth: Int): Long = runCatching {
        when {
            depth > MAX_DEPTH -> 0L
            !file.isDirectory -> file.length()
            else -> file.listFiles().orEmpty().sumOf { sizeOf(it, depth + 1) }
        }
    }.getOrDefault(0L)

    private fun hasSamsungGallerySync(): Boolean =
        isInstalled(SAMSUNG_GALLERY_PACKAGE) ||
            isInstalled(SAMSUNG_CLOUD_PACKAGE) ||
            SYNC_AUTHORITIES.any { hasProvider(it) }

    private fun isInstalled(packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        true
    }.getOrDefault(false)

    private fun hasProvider(authority: String): Boolean = runCatching {
        context.packageManager.resolveContentProvider(
            authority,
            PackageManager.ComponentInfoFlags.of(0L),
        ) != null
    }.getOrDefault(false)

    private companion object {
        const val ONEDRIVE_PACKAGE = "com.microsoft.skydrive"
        const val SAMSUNG_GALLERY_PACKAGE = "com.sec.android.gallery3d"
        const val SAMSUNG_CLOUD_PACKAGE = "com.samsung.android.scloud"
        const val EXTERNAL_FALLBACK = "/storage/emulated/0"
        const val TRASH_PREFIX = ".trash"
        const val MAX_DEPTH = 6

        val SYNC_AUTHORITIES = listOf(
            "com.sec.android.gallery3d.provider",
            "com.samsung.android.scloud.provider",
            "com.microsoft.skydrive.content.external",
        )
    }
}
