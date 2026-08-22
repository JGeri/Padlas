package hu.padlas.feature.archive.drive

import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.feature.archive.data.ArchivePreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ManifestEntry(
    val assetId: Long,
    val name: String,
    val originalPath: String,
    val dateTaken: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val sha256: String?,
    val driveFileId: String,
    val driveMd5: String?,
)

/**
 * The folder tree plus a manifest per folder is the disaster-recovery contract: if the
 * phone dies, the Drive side alone has to be enough to rebuild the index, which means the
 * original path and the hashes have to travel with the bytes.
 */
@Singleton
class DriveFolders @Inject constructor(
    private val client: DriveClient,
    private val preferences: ArchivePreferences,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** Folder creation is not idempotent on Drive; two racing uploads would make two folders. */
    private val mutex = Mutex()

    suspend fun folderFor(kind: ArchiveKind, dateMillis: Long): String? {
        val segments = DrivePaths.segments(kind, dateMillis)
        return ensure(segments)
    }

    suspend fun ensure(segments: List<String>): String? = mutex.withLock {
        withContext(io) {
            var parentId: String? = null
            val walked = ArrayList<String>(segments.size)
            for (segment in segments) {
                walked += segment
                val path = DrivePaths.displayPath(walked)
                val cached = preferences.folderId(path)
                if (cached != null) {
                    parentId = cached
                    continue
                }
                val existing = client.findChild(segment, parentId, folder = true)?.id
                val id = existing ?: client.createFolder(segment, parentId) ?: return@withContext null
                preferences.setFolderId(path, id)
                parentId = id
            }
            parentId
        }
    }

    /**
     * Rewrites the folder manifest with [entries] merged in by asset id. Entries already in
     * the file are replaced, never duplicated, so a re-upload cannot double the catalogue.
     */
    suspend fun mergeManifest(
        folderId: String,
        displayPath: String,
        entries: List<ManifestEntry>,
    ): Boolean = withContext(io) {
        if (entries.isEmpty()) return@withContext true
        val existingId = preferences.manifestFileId(displayPath)
            ?: client.findChild(DrivePaths.MANIFEST, folderId, folder = false)?.id

        val previous = existingId?.let { id ->
            client.openStream(id)?.use { stream -> stream.readBytes().decodeToString() }
        }.orEmpty()

        val merged = merge(previous, entries)
        val uploadedId = client.uploadSmall(
            name = DrivePaths.MANIFEST,
            parentId = folderId,
            mimeType = "application/json",
            content = merged.encodeToByteArray(),
            existingFileId = existingId,
        ) ?: return@withContext false

        preferences.setManifestFileId(displayPath, uploadedId)
        true
    }

    private fun merge(previousBody: String, entries: List<ManifestEntry>): String {
        val byId = LinkedHashMap<Long, JsonObject>()
        (DriveJson.parse(previousBody) as? JsonArray)?.forEach { element ->
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return@forEach
            val id = runCatching { obj["assetId"]?.jsonPrimitive?.content?.toLongOrNull() }.getOrNull()
            if (id != null) byId[id] = obj
        }
        entries.forEach { entry -> byId[entry.assetId] = entry.toJson() }
        return buildJsonArray { byId.values.forEach { add(it) } }.toString()
    }

    private fun ManifestEntry.toJson(): JsonObject = buildJsonObject {
        put("assetId", JsonPrimitive(assetId))
        put("name", JsonPrimitive(name))
        put("originalPath", JsonPrimitive(originalPath))
        put("dateTaken", JsonPrimitive(dateTaken))
        put("sizeBytes", JsonPrimitive(sizeBytes))
        put("mimeType", JsonPrimitive(mimeType))
        put("sha256", JsonPrimitive(sha256.orEmpty()))
        put("driveFileId", JsonPrimitive(driveFileId))
        put("driveMd5", JsonPrimitive(driveMd5.orEmpty()))
    }
}
