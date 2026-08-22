package hu.padlas.feature.archive.drive

import hu.padlas.core.common.di.IoDispatcher
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Drive REST v3 straight over OkHttp. The SDK is deliberately absent: the only thing this
 * app needs from Drive that SAF cannot give is the server-side MD5, and one `files.get`
 * call is a smaller dependency than the Play services Drive stack.
 */
@Singleton
class DriveClient @Inject constructor(
    private val auth: DriveAuth,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun findChild(name: String, parentId: String?, folder: Boolean): DriveFile? = withContext(io) {
        val escaped = DrivePaths.escapeQueryLiteral(name)
        val parent = parentId ?: "root"
        val mime = if (folder) " and mimeType = '${DrivePaths.FOLDER_MIME}'" else ""
        val query = "name = '$escaped' and '$parent' in parents and trashed = false$mime"
        val url = "$API/files?q=${query.urlEncoded()}&fields=${FILE_FIELDS.urlEncoded()}&pageSize=10"
        request(Request.Builder().url(url).get()) { body ->
            DriveJson.listing(body).files.firstOrNull()
        }
    }

    suspend fun createFolder(name: String, parentId: String?): String? = withContext(io) {
        val metadata = buildJsonObject {
            put("name", JsonPrimitive(name))
            put("mimeType", JsonPrimitive(DrivePaths.FOLDER_MIME))
            if (parentId != null) {
                put("parents", buildJsonArray { add(JsonPrimitive(parentId)) })
            }
        }
        request(
            Request.Builder()
                .url("$API/files?fields=id")
                .post(metadata.toString().toRequestBody(JSON)),
        ) { body -> DriveJson.fileId(body) }
    }

    suspend fun getFile(fileId: String): DriveFile? = withContext(io) {
        request(
            Request.Builder().url("$API/files/$fileId?fields=id,name,mimeType,size,md5Checksum,trashed").get(),
        ) { body -> DriveJson.file(DriveJson.parse(body)) }
    }

    suspend fun list(parentId: String, pageToken: String? = null): DriveListing = withContext(io) {
        val query = "'$parentId' in parents and trashed = false"
        val page = pageToken?.let { "&pageToken=${it.urlEncoded()}" }.orEmpty()
        val url = "$API/files?q=${query.urlEncoded()}&fields=${LIST_FIELDS.urlEncoded()}&pageSize=200$page"
        request(Request.Builder().url(url).get()) { body -> DriveJson.listing(body) }
            ?: DriveListing(emptyList(), null)
    }

    suspend fun delete(fileId: String): Boolean = withContext(io) {
        val token = auth.accessToken() ?: return@withContext false
        val call = Request.Builder()
            .url("$API/files/$fileId")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        runCatching { client.newCall(call).execute().use { it.isSuccessful || it.code == 404 } }
            .getOrDefault(false)
    }

    /** Small JSON or thumbnail payloads: manifests and catalogue cards. */
    suspend fun uploadSmall(
        name: String,
        parentId: String,
        mimeType: String,
        content: ByteArray,
        existingFileId: String? = null,
    ): String? = withContext(io) {
        val metadata = buildJsonObject {
            put("name", JsonPrimitive(name))
            if (existingFileId == null && parentId.isNotEmpty()) {
                put("parents", buildJsonArray { add(JsonPrimitive(parentId)) })
            }
        }
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toString().toRequestBody(JSON))
            .addPart(content.toRequestBody(mimeType.toMediaType()))
            .build()
        val builder = Request.Builder().url(
            if (existingFileId == null) {
                "$UPLOAD/files?uploadType=multipart&fields=id"
            } else {
                "$UPLOAD/files/$existingFileId?uploadType=multipart&fields=id"
            },
        )
        val call = if (existingFileId == null) builder.post(body) else builder.patch(body)
        request(call) { response -> DriveJson.fileId(response) }
    }

    /**
     * Step 1 of spec 3.3. The returned session URI must be persisted before a single byte
     * moves, otherwise a process death restarts a multi-gigabyte transfer from zero.
     */
    suspend fun startResumableSession(
        name: String,
        parentId: String,
        mimeType: String,
        totalBytes: Long,
    ): String? = withContext(io) {
        val token = auth.accessToken() ?: return@withContext null
        val metadata = buildJsonObject {
            put("name", JsonPrimitive(name))
            put("parents", buildJsonArray { add(JsonPrimitive(parentId)) })
        }
        val call = Request.Builder()
            .url("$UPLOAD/files?uploadType=resumable&fields=id,md5Checksum,size")
            .header("Authorization", "Bearer $token")
            .header("X-Upload-Content-Type", mimeType)
            .header("X-Upload-Content-Length", totalBytes.toString())
            .post(metadata.toString().toRequestBody(JSON))
            .build()
        runCatching {
            client.newCall(call).execute().use { response ->
                if (!response.isSuccessful) null else response.header("Location")
            }
        }.getOrNull()
    }

    /** Step 3: an empty PUT asks the server how much of the file it already holds. */
    suspend fun probeOffset(sessionUri: String, totalBytes: Long): ChunkOutcome = withContext(io) {
        val call = Request.Builder()
            .url(sessionUri)
            .header("Content-Range", ResumableRange.probeRange(totalBytes))
            .put(EMPTY)
            .build()
        runCatching { client.newCall(call).execute().use { interpret(it, 0, 0, totalBytes) } }
            .getOrElse { ChunkOutcome.Retryable(0, it.message.orEmpty()) }
    }

    /** Step 2: one 8 MB chunk with an explicit Content-Range. */
    suspend fun uploadChunk(
        sessionUri: String,
        chunk: ByteArray,
        offset: Long,
        totalBytes: Long,
    ): ChunkOutcome = withContext(io) {
        val call = Request.Builder()
            .url(sessionUri)
            .header("Content-Range", ResumableRange.contentRange(offset, chunk.size, totalBytes))
            .put(chunk.toRequestBody(OCTET))
            .build()
        runCatching {
            client.newCall(call).execute().use { interpret(it, offset, chunk.size, totalBytes) }
        }.getOrElse { ChunkOutcome.Retryable(0, it.message ?: "Hálózati hiba.") }
    }

    /** Ghost assets are streamed back from here when the user opens one. */
    suspend fun openStream(fileId: String): InputStream? = withContext(io) {
        val token = auth.accessToken() ?: return@withContext null
        val call = Request.Builder()
            .url("$API/files/$fileId?alt=media")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        runCatching {
            val response = client.newCall(call).execute()
            if (response.isSuccessful) response.body?.byteStream() else null.also { response.close() }
        }.getOrNull()
    }

    private fun interpret(
        response: Response,
        offset: Long,
        length: Int,
        totalBytes: Long,
    ): ChunkOutcome = when {
        response.code == 200 || response.code == 201 -> {
            val id = DriveJson.fileId(response.body?.string().orEmpty())
            if (id != null) ChunkOutcome.Complete(id) else ChunkOutcome.Retryable(response.code, "Hiányzó fájlazonosító.")
        }

        response.code == 308 -> {
            val resumed = ResumableRange.parseResumeOffset(response.header("Range"))
            val next = if (resumed > 0) resumed else offset + length
            ChunkOutcome.Incomplete(next.coerceAtMost(totalBytes))
        }

        response.code == 404 || response.code == 410 -> ChunkOutcome.SessionGone

        response.code == 429 || response.code >= 500 ->
            ChunkOutcome.Retryable(response.code, "A Drive most nem fogadja a köteget.")

        else -> ChunkOutcome.Fatal(response.code, response.message)
    }

    private suspend fun <T> request(builder: Request.Builder, parse: (String) -> T?): T? {
        val token = auth.accessToken() ?: return null
        val call = builder.header("Authorization", "Bearer $token").build()
        return runCatching {
            client.newCall(call).execute().use { response ->
                if (!response.isSuccessful) null else parse(response.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    private fun String.urlEncoded(): String = java.net.URLEncoder.encode(this, "UTF-8")

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val FILE_FIELDS = "files(id,name,mimeType,size,md5Checksum,trashed)"
        const val LIST_FIELDS = "nextPageToken,files(id,name,mimeType,size,md5Checksum,trashed)"
        val JSON = "application/json; charset=utf-8".toMediaType()
        val OCTET = "application/octet-stream".toMediaType()
        val EMPTY: RequestBody = ByteArray(0).toRequestBody(null)
    }
}
