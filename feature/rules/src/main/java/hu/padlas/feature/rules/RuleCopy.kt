package hu.padlas.feature.rules

import android.text.format.DateFormat
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import hu.padlas.core.common.format.Format
import hu.padlas.core.common.text.UiText
import hu.padlas.core.common.text.uiPlural
import hu.padlas.core.common.text.uiText
import hu.padlas.core.database.entity.RuleAction
import hu.padlas.core.database.entity.RuleEntity
import hu.padlas.core.database.entity.ShotKind
import hu.padlas.core.database.entity.ValueClass
import hu.padlas.core.design.theme.Ink
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Plain words for a rule: what it looks at, what it does, and what it did last time. */
internal object RuleCopy {

    /**
     * Built-in rules are stored under the names they were seeded with: the current ones, and
     * in older databases the first names, which leaned on the app's internal metaphors. The
     * rows keep their stored name, so the screen translates both instead of migrating user data.
     */
    private val builtInNames: Map<String, Int> = mapOf(
        BuiltInRules.OLD_WHATSAPP to R.string.rules_builtin_old_whatsapp,
        "Régi WhatsApp a padlásra" to R.string.rules_builtin_old_whatsapp,
        BuiltInRules.OLD_CAMERA to R.string.rules_builtin_old_camera,
        "Egy évnél régebbi kamerafotó a padlásra" to R.string.rules_builtin_old_camera,
        BuiltInRules.LARGE_VIDEOS to R.string.rules_builtin_large_videos,
        "Nagy videók tömörítésre" to R.string.rules_builtin_large_videos,
        BuiltInRules.OLD_SCREENSHOTS to R.string.rules_builtin_old_screenshots,
        "Fél évnél régebbi efemer a Kidobóba" to R.string.rules_builtin_old_screenshots,
    )

    fun displayName(rule: RuleEntity): UiText =
        builtInNames[rule.name]?.let { uiText(it) } ?: UiText.Raw(rule.name)

    @StringRes
    fun actionSentence(action: RuleAction): Int = when (action) {
        RuleAction.ARCHIVE -> R.string.rules_action_archive
        RuleAction.TRASH -> R.string.rules_action_trash
        RuleAction.PROTECT -> R.string.rules_action_protect
        RuleAction.TRANSCODE -> R.string.rules_action_transcode
    }

    fun actionIcon(action: RuleAction): ImageVector = when (action) {
        RuleAction.ARCHIVE -> Icons.Rounded.CloudUpload
        RuleAction.TRASH -> Icons.Rounded.DeleteSweep
        RuleAction.PROTECT -> Icons.Rounded.Shield
        RuleAction.TRANSCODE -> Icons.Rounded.Compress
    }

    fun actionTint(action: RuleAction): Color = when (action) {
        RuleAction.ARCHIVE -> Ink.Info
        RuleAction.TRASH -> Ink.Danger
        RuleAction.PROTECT -> Ink.Success
        RuleAction.TRANSCODE -> Ink.Warning
    }

    /** One short tag per condition; the card shows them side by side instead of a sentence. */
    fun conditions(rule: RuleEntity): List<UiText> {
        val predicate = PredicateJson.decode(rule.predicateJson)
            ?: return listOf(uiText(R.string.rules_condition_broken))
        return flatten(predicate).map(::condition)
    }

    fun dryRunLine(matchCount: Int, matchBytes: Long): UiText =
        if (matchCount == 0) {
            uiText(R.string.rules_dry_run_none)
        } else {
            uiPlural(R.plurals.rules_dry_run_line, matchCount, Format.count(matchCount), Format.bytes(matchBytes))
        }

    fun lastRunLine(rule: RuleEntity): UiText? {
        val at = rule.lastRunAt ?: return null
        return uiPlural(
            R.plurals.rules_last_run,
            rule.lastMatchCount,
            shortDay(at),
            Format.count(rule.lastMatchCount),
            Format.bytes(rule.lastMatchBytes),
        )
    }

    fun deltaText(delta: Long): String = (if (delta >= 0) "+" else "") + Format.bytes(delta)

    private fun flatten(predicate: Predicate): List<Predicate> = when (predicate) {
        is Predicate.And -> predicate.terms.flatMap(::flatten)
        else -> listOf(predicate)
    }

    private fun condition(predicate: Predicate): UiText = when (predicate) {
        is Predicate.PathContains -> when {
            predicate.value.contains("WhatsApp", ignoreCase = true) -> uiText(R.string.rules_condition_whatsapp)
            predicate.value.contains("DCIM/Camera", ignoreCase = true) -> uiText(R.string.rules_condition_camera)
            predicate.value.contains("Screenshots", ignoreCase = true) -> uiText(R.string.rules_condition_screenshots)
            predicate.value.contains("Download", ignoreCase = true) -> uiText(R.string.rules_condition_downloads)
            else -> uiText(R.string.rules_condition_folder, predicate.value.trim('/'))
        }
        is Predicate.OlderThan -> age(predicate.days)
        is Predicate.LargerThan -> uiText(R.string.rules_condition_larger, Format.bytes(predicate.bytes))
        is Predicate.MimeIs -> when {
            predicate.prefix.startsWith("video") -> uiText(R.string.rules_condition_video)
            predicate.prefix.startsWith("image") -> uiText(R.string.rules_condition_picture)
            predicate.prefix.startsWith("audio") -> uiText(R.string.rules_condition_audio)
            else -> UiText.Raw(predicate.prefix)
        }
        is Predicate.ValueClassIs -> when (predicate.valueClass) {
            ValueClass.IRREPLACEABLE -> uiText(R.string.rules_condition_own_photo)
            ValueClass.REPLACEABLE -> uiText(R.string.rules_condition_replaceable)
            ValueClass.EPHEMERAL -> uiText(R.string.rules_condition_throwaway)
        }
        is Predicate.ShotKindIs -> when (predicate.kind) {
            ShotKind.RECEIPT -> uiText(R.string.rules_condition_receipt)
            ShotKind.TICKET -> uiText(R.string.rules_condition_ticket)
            ShotKind.SECRET -> uiText(R.string.rules_condition_secret)
            ShotKind.PLACE -> uiText(R.string.rules_condition_place)
            ShotKind.CHAT -> uiText(R.string.rules_condition_chat)
            ShotKind.OTHER -> uiText(R.string.rules_condition_other_screenshot)
        }
        is Predicate.BucketIs -> uiText(R.string.rules_condition_selected_folder)
        is Predicate.And -> predicate.terms.map(::condition).joined(R.string.rules_join_and)
        is Predicate.Or -> predicate.terms.map(::condition).joined(R.string.rules_join_or)
        is Predicate.Not -> uiText(R.string.rules_condition_not, condition(predicate.term))
    }

    private fun age(days: Int): UiText = when {
        days >= 365 && days % 365 == 0 -> plural(R.plurals.rules_condition_older_years, days / 365)
        days >= 60 && days % 30 == 0 -> plural(R.plurals.rules_condition_older_months, days / 30)
        else -> plural(R.plurals.rules_condition_older_days, days)
    }

    private fun plural(@PluralsRes id: Int, count: Int): UiText = uiPlural(id, count, Format.count(count))

    /**
     * The app language's own "month and day": "okt. 31." in Hungarian, which is exactly the
     * pattern this line always used, and "Oct 31" in English.
     */
    private fun shortDay(millis: Long): String {
        val locale = Locale.getDefault()
        val pattern = DateFormat.getBestDateTimePattern(locale, "MMMd")
        return DateTimeFormatter.ofPattern(pattern, locale)
            .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    }
}
