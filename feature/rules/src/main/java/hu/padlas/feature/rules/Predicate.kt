package hu.padlas.feature.rules

import androidx.annotation.StringRes
import hu.padlas.core.common.format.Format
import hu.padlas.core.common.text.UiText
import hu.padlas.core.common.text.uiPlural
import hu.padlas.core.common.text.uiText
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

/** A one-liner for a whole predicate tree, in the language the screen resolves it in. */
fun Predicate.describe(): UiText = when (this) {
    is Predicate.PathContains -> uiText(R.string.rules_describe_path, value)
    is Predicate.OlderThan -> uiPlural(R.plurals.rules_describe_older, days, Format.count(days))
    is Predicate.LargerThan -> uiText(R.string.rules_describe_larger, Format.bytes(bytes))
    is Predicate.MimeIs -> uiText(R.string.rules_describe_mime, prefix)
    is Predicate.ValueClassIs -> uiText(R.string.rules_describe_value_class, uiText(valueClass.describedAs()))
    is Predicate.ShotKindIs -> uiText(R.string.rules_describe_shot_kind, kind.name.lowercase())
    is Predicate.BucketIs -> uiText(R.string.rules_describe_bucket, bucketId.toString())
    is Predicate.And -> terms.map { it.describe() }.joined(R.string.rules_join_and)
    is Predicate.Or -> terms.map { it.describe() }.joined(R.string.rules_join_or)
    is Predicate.Not -> uiText(R.string.rules_describe_not, term.describe())
}

/** Pairwise, because word order around "and" is the translation's business, not a separator's. */
internal fun List<UiText>.joined(@StringRes pair: Int): UiText =
    reduceOrNull { joined, next -> uiText(pair, joined, next) } ?: UiText.Raw("")

@StringRes
private fun ValueClass.describedAs(): Int = when (this) {
    ValueClass.IRREPLACEABLE -> R.string.rules_describe_irreplaceable
    ValueClass.REPLACEABLE -> R.string.rules_describe_replaceable
    ValueClass.EPHEMERAL -> R.string.rules_describe_ephemeral
}
