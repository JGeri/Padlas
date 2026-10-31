package hu.padlas.core.media

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
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
 *
 * One request carries at most [MAX_REQUEST] items: the URIs travel inside the PendingIntent
 * through the binder twice, and a few thousand of them overflow it. Callers take that many
 * and send the rest in a following round. A request that cannot be built comes back null
 * instead of throwing, so a refusal from MediaStore never takes the app down with it.
 */
interface MediaOps {

    suspend fun trashRequest(uris: List<Uri>): IntentSender?

    suspend fun untrashRequest(uris: List<Uri>): IntentSender?

    suspend fun deleteRequest(uris: List<Uri>): IntentSender?

    suspend fun favoriteRequest(uris: List<Uri>, favorite: Boolean = true): IntentSender?

    /** With "manage media" granted the system skips its confirmation dialogs entirely. */
    fun canManageMedia(): Boolean

    fun uriFor(mediaStoreId: Long, isVideo: Boolean): Uri

    suspend fun openInputStream(uri: Uri): InputStream?

    suspend fun fileSize(uri: Uri): Long

    /**
     * INV-2: trashed content still occupies bytes for 30 days. The trash is only visible
     * through a bundle query with QUERY_ARG_MATCH_TRASHED, not through a plain selection.
     */
    suspend fun trashedBytes(): Long

    /** Everything in the system trash: ours, the gallery's, any app's. */
    suspend fun trashedItems(): List<TrashedMedia>

    companion object {
        /** The most items one system dialog is asked to carry. */
        const val MAX_REQUEST = 1_000
    }
}

/**
 * Behind [MediaOps] so the delete rounds of every screen can be exercised without a system
 * dialog to answer them.
 */
@Singleton
class MediaStoreOps @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : MediaOps {

    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun trashRequest(uris: List<Uri>): IntentSender? =
        request(uris) { MediaStore.createTrashRequest(resolver, it, true) }

    override suspend fun untrashRequest(uris: List<Uri>): IntentSender? =
        request(uris) { MediaStore.createTrashRequest(resolver, it, false) }

    override suspend fun deleteRequest(uris: List<Uri>): IntentSender? =
        request(uris) { MediaStore.createDeleteRequest(resolver, it) }

    override suspend fun favoriteRequest(uris: List<Uri>, favorite: Boolean): IntentSender? =
        request(uris) { MediaStore.createFavoriteRequest(resolver, it, favorite) }

    private suspend fun request(uris: List<Uri>, build: (List<Uri>) -> PendingIntent): IntentSender? {
        if (uris.isEmpty()) return null
        if (uris.size > MediaOps.MAX_REQUEST) {
            Log.e(TAG, "${uris.size} items in one request, the limit is ${MediaOps.MAX_REQUEST}")
            return null
        }
        return withContext(io) {
            try {
                build(uris).intentSender
            } catch (e: Exception) {
                Log.w(TAG, "MediaStore refused a request for ${uris.size} items", e)
                null
            }
        }
    }

    override fun canManageMedia(): Boolean = runCatching { MediaStore.canManageMedia(context) }.getOrDefault(false)

    override fun uriFor(mediaStoreId: Long, isVideo: Boolean): Uri = MediaUris.forId(mediaStoreId, isVideo)

    override suspend fun openInputStream(uri: Uri): InputStream? = withContext(io) {
        runCatching { resolver.openInputStream(uri) }.getOrNull()
    }

    override suspend fun fileSize(uri: Uri): Long = withContext(io) {
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

    override suspend fun trashedBytes(): Long = withContext(io) {
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

    override suspend fun trashedItems(): List<TrashedMedia> = withContext(io) {
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
        const val TAG = "MediaOps"
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
