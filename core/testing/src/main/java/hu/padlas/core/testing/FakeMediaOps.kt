package hu.padlas.core.testing

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import hu.padlas.core.media.MediaOps
import hu.padlas.core.media.TrashedMedia
import java.io.InputStream

/**
 * The system dialogs, minus the dialogs. Every request a screen builds is recorded; the
 * test then answers it the way the user would, through the screen's own result callback.
 * Like MediaStore it refuses (null) a request over [MediaOps.MAX_REQUEST], so a caller that
 * forgets to cut its rounds shows up as a refusal and an oversized entry in [requests].
 */
class FakeMediaOps(
    private val context: Context = ApplicationProvider.getApplicationContext(),
) : MediaOps {

    enum class Kind { TRASH, UNTRASH, DELETE, FAVORITE }

    class Request(val kind: Kind, val uris: List<Uri>, val sender: IntentSender?) {
        val ids: List<Long> get() = uris.map(ContentUris::parseId)
    }

    val requests = mutableListOf<Request>()

    /** Sizes of every request so far, in order: the rounds a test usually asserts. */
    val rounds: List<Int> get() = requests.map { it.uris.size }

    /** Every id any request asked for, in order, duplicates included. */
    val requestedIds: List<Long> get() = requests.flatMap { it.ids }

    /** MediaStore refuses every request from now on, as it does for a revoked permission. */
    var refusing = false

    /** "Manage media" granted: the system skips its own confirmation. */
    var manageMedia = false

    /** The system trash as [trashedItems] reports it. */
    val bin = mutableListOf<TrashedMedia>()

    override suspend fun trashRequest(uris: List<Uri>): IntentSender? = request(Kind.TRASH, uris)

    override suspend fun untrashRequest(uris: List<Uri>): IntentSender? = request(Kind.UNTRASH, uris)

    override suspend fun deleteRequest(uris: List<Uri>): IntentSender? = request(Kind.DELETE, uris)

    override suspend fun favoriteRequest(uris: List<Uri>, favorite: Boolean): IntentSender? =
        request(Kind.FAVORITE, uris)

    override fun canManageMedia(): Boolean = manageMedia

    override fun uriFor(mediaStoreId: Long, isVideo: Boolean): Uri {
        val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return ContentUris.withAppendedId(collection, mediaStoreId)
    }

    override suspend fun openInputStream(uri: Uri): InputStream? = null

    override suspend fun fileSize(uri: Uri): Long = 0L

    override suspend fun trashedBytes(): Long = bin.sumOf { it.sizeBytes }

    override suspend fun trashedItems(): List<TrashedMedia> = bin.toList()

    /** What the system does once the user confirms [request]: a delete or a restore empties those bin slots. */
    fun carryOut(request: Request = requests.last()) {
        if (request.kind == Kind.DELETE || request.kind == Kind.UNTRASH) {
            val gone = request.ids.toSet()
            bin.removeAll { it.id in gone }
        }
    }

    fun putInBin(ids: Iterable<Long>, sizeBytes: Long = 1_000) {
        ids.forEach { id ->
            bin += TrashedMedia(
                id = id,
                uri = uriFor(id, isVideo = false),
                isVideo = false,
                sizeBytes = sizeBytes,
                displayName = "IMG_$id.jpg",
                expiresAt = JULY_2024 + 30 * DAY_MS,
                durationMs = null,
            )
        }
    }

    private fun request(kind: Kind, uris: List<Uri>): IntentSender? {
        val sender = if (refusing || uris.isEmpty() || uris.size > MediaOps.MAX_REQUEST) {
            null
        } else {
            PendingIntent.getBroadcast(
                context,
                requests.size,
                Intent(ACTION).setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE,
            ).intentSender
        }
        requests += Request(kind, uris.toList(), sender)
        return sender
    }

    private companion object {
        const val ACTION = "hu.padlas.test.SYSTEM_REQUEST"
    }
}
