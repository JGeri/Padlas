package hu.padlas.core.media

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.database.entity.Asset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Spec 2.3. One explicit projection over MediaStore.Files, never SELECT *, and nothing per
 * row that touches the file itself: no EXIF, no bitmap. That is what keeps a 25 000 asset
 * cold index under 90 seconds.
 */
@Singleton
class MediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /**
     * @param sinceDateModified MediaStore DATE_MODIFIED in **seconds**, matching what
     * AssetDao.latestDateModified() returns. 0 means a full scan.
     */
    fun scan(sinceDateModified: Long = 0L): Flow<List<Asset>> = flow {
        val seenAt = System.currentTimeMillis()
        var batch = ArrayList<Asset>(BATCH_SIZE)
        val incremental = sinceDateModified > 0L
        val selection = if (incremental) {
            MEDIA_SELECTION + " AND " + MediaStore.Files.FileColumns.DATE_MODIFIED + " > ?"
        } else {
            MEDIA_SELECTION
        }
        val args = if (incremental) arrayOf(sinceDateModified.toString()) else null
        val order = MediaStore.Files.FileColumns._ID + " ASC"

        query(PROJECTION, selection, args, order)?.use { cursor ->
            val columns = Columns(cursor)
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                batch += cursor.toAsset(columns, seenAt)
                if (batch.size >= BATCH_SIZE) {
                    emit(batch)
                    batch = ArrayList(BATCH_SIZE)
                }
            }
        }
        if (batch.isNotEmpty()) emit(batch)
    }.flowOn(io)

    /**
     * Rows by id, whatever their timestamps say. A file copied in with its old modification
     * time sorts below the incremental watermark, so the indexer asks for it by name.
     */
    fun scanIds(ids: Collection<Long>): Flow<List<Asset>> = flow {
        val seenAt = System.currentTimeMillis()
        for (chunk in ids.chunked(ID_CHUNK)) {
            val selection = MEDIA_SELECTION + " AND " + MediaStore.Files.FileColumns._ID +
                " IN (" + chunk.joinToString(",") { "?" } + ")"
            val batch = ArrayList<Asset>(chunk.size)
            query(PROJECTION, selection, chunk.map { it.toString() }.toTypedArray(), null)?.use { cursor ->
                val columns = Columns(cursor)
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    batch += cursor.toAsset(columns, seenAt)
                }
            }
            if (batch.isNotEmpty()) emit(batch)
        }
    }.flowOn(io)

    /** Ids currently in MediaStore, so the indexer can drop rows deleted outside the app. */
    suspend fun currentIds(): Set<Long> = withContext(io) {
        val ids = HashSet<Long>()
        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        query(projection, MEDIA_SELECTION, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                ids += cursor.longAt(index)
            }
        }
        ids
    }

    suspend fun assetById(mediaStoreId: Long): Asset? = withContext(io) {
        val selection = MEDIA_SELECTION + " AND " + MediaStore.Files.FileColumns._ID + " = ?"
        query(PROJECTION, selection, arrayOf(mediaStoreId.toString()), null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.toAsset(Columns(cursor), System.currentTimeMillis())
            } else {
                null
            }
        }
    }

    private fun query(
        projection: Array<String>,
        selection: String,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? = runCatching {
        context.contentResolver.query(COLLECTION, projection, selection, selectionArgs, sortOrder)
    }.getOrNull()

    private fun Cursor.toAsset(columns: Columns, seenAt: Long): Asset {
        val id = longAt(columns.id)
        val mimeType = textAt(columns.mimeType)
        val isVideo = intAt(columns.mediaType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
        val relativePath = textAt(columns.relativePath)
        val displayName = textAt(columns.displayName)
        val width = intAt(columns.width)
        val height = intAt(columns.height)
        val isFavorite = intAt(columns.favorite) == 1
        val dateModified = longAt(columns.dateModified)
        val dateTaken = longAt(columns.dateTaken).takeIf { it > 0L }
            ?: (dateModified * MILLIS_PER_SECOND)

        // hasCameraExif stays false here on purpose: an EXIF read per row would blow the
        // 90 s budget. The hash pass reclassifies later with that stronger signal.
        val classification = AssetClassifier.classify(
            ClassificationInput(
                relativePath = relativePath,
                displayName = displayName,
                mimeType = mimeType,
                width = width,
                height = height,
                hasCameraExif = false,
                isFavorite = isFavorite,
                isVideo = isVideo,
            ),
        )

        return Asset(
            mediaStoreId = id,
            uri = MediaUris.forId(id, isVideo).toString(),
            relativePath = relativePath,
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = longAt(columns.size),
            dateTaken = dateTaken,
            dateModified = dateModified,
            width = width,
            height = height,
            durationMs = longAt(columns.duration).takeIf { it > 0L },
            bucketId = longAt(columns.bucketId),
            bucketName = textAt(columns.bucketName),
            latitude = null,
            longitude = null,
            isFavorite = isFavorite,
            valueClass = classification.valueClass,
            classifiedBy = classification.classifiedBy,
            lastSeenAt = seenAt,
        )
    }

    /** Column indices resolved once per cursor instead of once per row. */
    private class Columns(cursor: Cursor) {
        val id = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
        val displayName = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val relativePath = cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
        val mimeType = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
        val size = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
        val dateModified = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
        val dateTaken = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_TAKEN)
        val width = cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
        val height = cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
        val duration = cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
        val bucketId = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
        val bucketName = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
        val mediaType = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val favorite = cursor.getColumnIndex(MediaStore.MediaColumns.IS_FAVORITE)
    }

    companion object {
        const val BATCH_SIZE = 500

        /** Well under SQLite's variable limit, which MediaProvider's own queries share. */
        private const val ID_CHUNK = 500

        private const val MILLIS_PER_SECOND = 1000L

        private val COLLECTION = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

        /** Parenthesised because callers append " AND ..." and OR binds looser than AND. */
        private val MEDIA_SELECTION =
            "(" + MediaStore.Files.FileColumns.MEDIA_TYPE + " = " +
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE + " OR " +
                MediaStore.Files.FileColumns.MEDIA_TYPE + " = " +
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ")"

        private val PROJECTION = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.IS_FAVORITE,
        )
    }
}

internal fun Cursor.longAt(index: Int): Long =
    if (index >= 0 && !isNull(index)) getLong(index) else 0L

internal fun Cursor.intAt(index: Int): Int =
    if (index >= 0 && !isNull(index)) getInt(index) else 0

internal fun Cursor.textAt(index: Int): String =
    if (index >= 0 && !isNull(index)) getString(index).orEmpty() else ""
