package hu.padlas.core.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.padlas.core.common.di.IoDispatcher
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Shared by the scanner and by [MediaOps] so both build byte identical asset URIs. */
internal object MediaUris {

    fun forId(mediaStoreId: Long, isVideo: Boolean): Uri {
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }
        return ContentUris.withAppendedId(collection, mediaStoreId)
    }
}

/**
 * Every destructive call here is batched into a single system dialog (INV-3) and every
 * deletion goes through the trash first (INV-2).
 */
@Singleton
class MediaOps @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val resolver: ContentResolver get() = context.contentResolver

    fun trashRequest(uris: List<Uri>): IntentSender =
        MediaStore.createTrashRequest(resolver, uris, true).intentSender

    fun untrashRequest(uris: List<Uri>): IntentSender =
        MediaStore.createTrashRequest(resolver, uris, false).intentSender

    fun deleteRequest(uris: List<Uri>): IntentSender =
        MediaStore.createDeleteRequest(resolver, uris).intentSender

    fun uriFor(mediaStoreId: Long, isVideo: Boolean): Uri = MediaUris.forId(mediaStoreId, isVideo)

    suspend fun openInputStream(uri: Uri): InputStream? = withContext(io) {
        runCatching { resolver.openInputStream(uri) }.getOrNull()
    }

    suspend fun fileSize(uri: Uri): Long = withContext(io) {
        runCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) {
                    it.longAt(it.getColumnIndex(MediaStore.MediaColumns.SIZE))
                } else {
                    0L
                }
            } ?: 0L
        }.getOrDefault(0L)
    }

    /**
     * INV-2: trashed content still occupies bytes for 30 days. The trash is only visible
     * through a bundle query with QUERY_ARG_MATCH_TRASHED, not through a plain selection.
     */
    suspend fun trashedBytes(): Long = withContext(io) {
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?)",
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                ),
            )
        }
        runCatching {
            val projection = arrayOf(MediaStore.Files.FileColumns.SIZE)
            resolver.query(COLLECTION, projection, args, null)?.use { cursor ->
                val index = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                var total = 0L
                while (cursor.moveToNext()) total += cursor.longAt(index)
                total
            } ?: 0L
        }.getOrDefault(0L)
    }

    private companion object {
        val COLLECTION: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    }
}
