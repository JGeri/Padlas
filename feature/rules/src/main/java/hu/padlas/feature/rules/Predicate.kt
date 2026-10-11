package hu.padlas.feature.rules

import hu.padlas.core.database.entity.ShotKind
import hu.padlas.core.database.entity.ValueClass
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A rule is a predicate plus an action, and the predicate has to survive a database round
 * trip, so it is a closed tree with an explicit encoding rather than a lambda.
 */
sealed interface Predicate {
    data class PathContains(val value: String) : Predicate
    data class OlderThan(val days: Int) : Predicate
    data class LargerThan(val bytes: Long) : Predicate
    data class MimeIs(val prefix: String) : Predicate
    data class ValueClassIs(val valueClass: ValueClass) : Predicate
    data class ShotKindIs(val kind: ShotKind) : Predicate
    data class BucketIs(val bucketId: Long) : Predicate
    data class And(val terms: List<Predicate>) : Predicate
    data class Or(val terms: List<Predicate>) : Predicate
    data class Not(val term: Predicate) : Predicate
}

/**
 * Hand-rolled encoding: this module has the serialization runtime but not the compiler
 * plugin, and a seven-case tree is smaller than the build change would be.
 */
object PredicateJson {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun encode(predicate: Predicate): String = toJson(predicate).toString()

    fun decode(text: String): Predicate? = runCatching {
        fromJson(json.parseToJsonElement(text).jsonObject)
    }.getOrNull()

    private fun toJson(predicate: Predicate): JsonObject = buildJsonObject {
        when (predicate) {
            is Predicate.PathContains -> {
                put("type", JsonPrimitive("pathContains"))
                put("value", JsonPrimitive(predicate.value))
            }

            is Predicate.OlderThan -> {
                put("type", JsonPrimitive("olderThan"))
                put("days", JsonPrimitive(predicate.days))
            }

            is Predicate.LargerThan -> {
                put("type", JsonPrimitive("largerThan"))
                put("bytes", JsonPrimitive(predicate.bytes))
            }

            is Predicate.MimeIs -> {
                put("type", JsonPrimitive("mimeIs"))
                put("prefix", JsonPrimitive(predicate.prefix))
            }

            is Predicate.ValueClassIs -> {
                put("type", JsonPrimitive("valueClassIs"))
                put("valueClass", JsonPrimitive(predicate.valueClass.name))
            }

            is Predicate.ShotKindIs -> {
                put("type", JsonPrimitive("shotKindIs"))
                put("kind", JsonPrimitive(predicate.kind.name))
            }

            is Predicate.BucketIs -> {
                put("type", JsonPrimitive("bucketIs"))
                put("bucketId", JsonPrimitive(predicate.bucketId))
            }

            is Predicate.And -> {
                put("type", JsonPrimitive("and"))
                put("terms", predicate.terms.toJsonArray())
            }

            is Predicate.Or -> {
                put("type", JsonPrimitive("or"))
                put("terms", predicate.terms.toJsonArray())
            }

            is Predicate.Not -> {
                put("type", JsonPrimitive("not"))
                put("term", toJson(predicate.term))
            }
        }
    }

    private fun List<Predicate>.toJsonArray(): JsonArray = buildJsonArray {
        forEach { add(toJson(it)) }
    }

    private fun fromJson(obj: JsonObject): Predicate = when (obj.text("type")) {
        "pathContains" -> Predicate.PathContains(obj.text("value").orEmpty())
        "olderThan" -> Predicate.OlderThan(obj.text("days")?.toIntOrNull() ?: 0)
        "largerThan" -> Predicate.LargerThan(obj.text("bytes")?.toLongOrNull() ?: 0L)
        "mimeIs" -> Predicate.MimeIs(obj.text("prefix").orEmpty())
        "valueClassIs" -> Predicate.ValueClassIs(
            ValueClass.valueOf(obj.text("valueClass") ?: ValueClass.REPLACEABLE.name),
        )

        "shotKindIs" -> Predicate.ShotKindIs(ShotKind.valueOf(obj.text("kind") ?: ShotKind.OTHER.name))
        "bucketIs" -> Predicate.BucketIs(obj.text("bucketId")?.toLongOrNull() ?: 0L)
        "and" -> Predicate.And(obj.terms())
        "or" -> Predicate.Or(obj.terms())
        "not" -> Predicate.Not(fromJson(obj["term"]!!.jsonObject))
        else -> error("Ismeretlen predikátum: ${obj.text("type")}")
    }

    private fun JsonObject.terms(): List<Predicate> =
        this["terms"]?.jsonArray?.map { fromJson(it.jsonObject) }.orEmpty()

    private fun JsonObject.text(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotEmpty() }
}

/** Hungarian one-liner for the rule list; the builder shows the same wording. */
fun Predicate.describe(): String = when (this) {
    is Predicate.PathContains -> "az útvonal tartalmazza: \"$value\""
    is Predicate.OlderThan -> "régebbi mint $days nap"
    is Predicate.LargerThan -> "nagyobb mint ${hu.padlas.core.common.format.Format.bytes(bytes)}"
    is Predicate.MimeIs -> "típusa $prefix"
    is Predicate.ValueClassIs -> "besorolása ${valueClass.hungarian()}"
    is Predicate.ShotKindIs -> "képernyőkép típusa ${kind.name.lowercase()}"
    is Predicate.BucketIs -> "mappaazonosítója $bucketId"
    is Predicate.And -> terms.joinToString(" és ") { it.describe() }
    is Predicate.Or -> terms.joinToString(" vagy ") { it.describe() }
    is Predicate.Not -> "nem (${term.describe()})"
}

private fun ValueClass.hungarian(): String = when (this) {
    ValueClass.IRREPLACEABLE -> "pótolhatatlan"
    ValueClass.REPLACEABLE -> "pótolható"
    ValueClass.EPHEMERAL -> "eldobható"
}
