package hu.padlas.core.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.padlas.core.common.di.IoDispatcher
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Spec 1.6. When the file itself is gone the card keeps a thumbnail, so these bitmaps are
 * the only remaining pixels of a thrown out asset and live in app storage, not in MediaStore.
 */
@Singleton
class ThumbnailStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val root: File get() = File(context.filesDir, ROOT_DIR)

    suspend fun thumbnail(uri: Uri, size: Int = CARD_SIZE): Bitmap? = withContext(io) {
        runCatching {
            context.contentResolver.loadThumbnail(uri, Size(size, size), null)
        }.getOrNull()?.let { fitInside(it, size) }
    }

    suspend fun writeGhostThumbnail(assetId: Long, uri: Uri): String? =
        write(ghostThumbnailFile(assetId), uri, GHOST_SIZE)

    suspend fun writeCardThumbnail(cardId: String, uri: Uri): String? =
        write(cardThumbnailFile(cardId), uri, CARD_SIZE)

    fun ghostThumbnailFile(assetId: Long): File =
        File(File(root, GHOST_DIR), "$assetId$JPEG_SUFFIX")

    fun cardThumbnailFile(cardId: String): File =
        File(File(root, CARD_DIR), safeName(cardId) + JPEG_SUFFIX)

    private suspend fun write(target: File, uri: Uri, size: Int): String? = withContext(io) {
        val bitmap = thumbnail(uri, size) ?: return@withContext null
        try {
            target.parentFile?.mkdirs()
            FileOutputStream(target).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    return@withContext null
                }
            }
            target.absolutePath
        } catch (_: IOException) {
            target.delete()
            null
        } finally {
            bitmap.recycle()
        }
    }

    /** loadThumbnail may hand back something larger than asked; the card budget is ~20 KB. */
    private fun fitInside(bitmap: Bitmap, size: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= size || longest == 0) return bitmap
        val scale = size.toFloat() / longest
        val width = maxOf(1, (bitmap.width * scale).toInt())
        val height = maxOf(1, (bitmap.height * scale).toInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun safeName(cardId: String): String =
        cardId.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
            .joinToString(separator = "")

    private companion object {
        const val ROOT_DIR = "thumbs"
        const val GHOST_DIR = "ghost"
        const val CARD_DIR = "card"
        const val JPEG_SUFFIX = ".jpg"
        const val JPEG_QUALITY = 82
        const val GHOST_SIZE = 512
        const val CARD_SIZE = 256
    }
}
