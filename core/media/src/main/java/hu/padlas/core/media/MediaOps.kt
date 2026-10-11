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

    fun favoriteRequest(uris: List<Uri>, favorite: Boolean = true): IntentSender =
        MediaStore.createFavoriteRequest(resolver, uris, favorite).intentSender

    /** With "manage media" granted the system skips its confirmation dialogs entirely. */
    fun canManageMedia(): Boolean = runCatching { MediaStore.canManageMedia(context) }.getOrDefault(false)

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

    /** Everything in the system trash: ours, the gallery's, any app's. */
    suspend fun trashedItems(): List<TrashedMedia> = withContext(io) {
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
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.MediaColumns.DATE_EXPIRES),
            )
        }
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_EXPIRES,
            MediaStore.Files.FileColumns.DURATION,
        )
        runCatching {
            resolver.query(COLLECTION, projection, args, null)?.use { cursor ->
                val id = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val type = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val size = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val name = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val expires = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_EXPIRES)
                val duration = cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
                buildList {
                    while (cursor.moveToNext()) {
                        val mediaId = cursor.longAt(id)
                        val isVideo = cursor.intAt(type) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                        add(
                            TrashedMedia(
                                id = mediaId,
                                uri = MediaUris.forId(mediaId, isVideo),
                                isVideo = isVideo,
                                sizeBytes = cursor.longAt(size),
                                displayName = cursor.textAt(name),
                                // MediaStore keeps this in seconds.
                                expiresAt = cursor.longAt(expires) * 1000L,
                                durationMs = cursor.longAt(duration).takeIf { it > 0L },
                            ),
                        )
                    }
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private companion object {
        val COLLECTION: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    }
}

data class TrashedMedia(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val displayName: String,
    val expiresAt: Long,
    val durationMs: Long?,
)

/**
 * A request for the system's own thumbnail of a MediaStore item. The app's image loader
 * resolves it through ContentResolver.loadThumbnail, which is cached by the platform and
 * works the same way for photos and videos.
 */
data class MediaThumb(val uri: Uri, val size: Int = 384)
