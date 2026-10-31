package hu.padlas.feature.archive.drive

import hu.padlas.core.common.text.UiText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val md5: String?,
    val trashed: Boolean,
)

data class DriveListing(val files: List<DriveFile>, val nextPageToken: String?)

sealed interface ChunkOutcome {
    data class Incomplete(val nextOffset: Long) : ChunkOutcome
    data class Complete(val fileId: String) : ChunkOutcome
    /** The session URI is dead; the upload has to be started again from scratch. */
    data object SessionGone : ChunkOutcome
    data class Retryable(val code: Int, val message: UiText) : ChunkOutcome
    data class Fatal(val code: Int, val message: UiText) : ChunkOutcome
}

/**
 * Hand-rolled JSON reading. The module has kotlinx-serialization's runtime but not its
 * compiler plugin, so `@Serializable` is unavailable; the Drive payloads we consume are
 * five fields wide, which makes the tree API the cheaper answer rather than a workaround.
 */
internal object DriveJson {

    val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(body: String): JsonElement? = runCatching { lenient.parseToJsonElement(body) }.getOrNull()

    fun file(element: JsonElement?): DriveFile? {
        val obj = element as? JsonObject ?: return null
        val id = obj.text("id") ?: return null
        return DriveFile(
            id = id,
            name = obj.text("name").orEmpty(),
            mimeType = obj.text("mimeType").orEmpty(),
            sizeBytes = obj.text("size")?.toLongOrNull() ?: 0L,
            md5 = obj.text("md5Checksum"),
            trashed = obj.text("trashed")?.toBooleanStrictOrNull() ?: false,
        )
    }

    fun listing(body: String): DriveListing {
        val root = parse(body) as? JsonObject ?: return DriveListing(emptyList(), null)
        val files = runCatching {
            root["files"]?.jsonArray?.mapNotNull { file(it) }.orEmpty()
        }.getOrDefault(emptyList())
        return DriveListing(files, root.text("nextPageToken"))
    }

    fun fileId(body: String): String? = file(parse(body))?.id

    private fun JsonObject.text(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotEmpty() }

    fun objectOf(element: JsonElement?): JsonObject? =
        runCatching { element?.jsonObject }.getOrNull()
}
