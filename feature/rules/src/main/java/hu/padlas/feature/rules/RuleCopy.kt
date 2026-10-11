package hu.padlas.feature.rules

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import hu.padlas.core.common.format.Format
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
     * Built-in rules were seeded into existing databases under their first names, which
     * leaned on the app's internal metaphors. Those rows keep their stored name, so the
     * screen translates them instead of migrating user data.
     */
    private val legacyNames = mapOf(
        "Régi WhatsApp a padlásra" to "Régi WhatsApp-képek a felhőbe",
        "Egy évnél régebbi kamerafotó a padlásra" to "Egy évnél régebbi fotók a felhőbe",
        "Nagy videók tömörítésre" to "Nagy videók tömörítése",
        "Fél évnél régebbi efemer a Kidobóba" to "Régi képernyőképek az eldobhatók közé",
    )

    fun displayName(rule: RuleEntity): String = legacyNames[rule.name] ?: rule.name

    fun actionSentence(action: RuleAction): String = when (action) {
        RuleAction.ARCHIVE -> "Feltölti a Google Drive-ra"
        RuleAction.TRASH -> "Az eldobható képek közé teszi, te hagyod jóvá"
        RuleAction.PROTECT -> "Védetté teszi, így semmi nem törli"
        RuleAction.TRANSCODE -> "Tömörítésre jelöli"
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
    fun conditions(rule: RuleEntity): List<String> {
        val predicate = PredicateJson.decode(rule.predicateJson) ?: return listOf("Hibás szabály")
        return flatten(predicate).map(::condition)
    }

    fun dryRunLine(matchCount: Int, matchBytes: Long): String =
        if (matchCount == 0) {
            "Most egy fájlt sem érintene."
        } else {
            "Most ${Format.count(matchCount)} fájlt érintene · ${Format.bytes(matchBytes)}"
        }

    fun lastRunLine(rule: RuleEntity): String? {
        val at = rule.lastRunAt ?: return null
        val day = DAY.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))
        return "Legutóbb $day: ${Format.count(rule.lastMatchCount)} fájl · ${Format.bytes(rule.lastMatchBytes)}"
    }

    fun deltaText(delta: Long): String = (if (delta >= 0) "+" else "") + Format.bytes(delta)

    private fun flatten(predicate: Predicate): List<Predicate> = when (predicate) {
        is Predicate.And -> predicate.terms.flatMap(::flatten)
        else -> listOf(predicate)
    }

    private fun condition(predicate: Predicate): String = when (predicate) {
        is Predicate.PathContains -> when {
            predicate.value.contains("WhatsApp", ignoreCase = true) -> "WhatsApp-mappa"
            predicate.value.contains("DCIM/Camera", ignoreCase = true) -> "Kamera mappa"
            predicate.value.contains("Screenshots", ignoreCase = true) -> "Képernyőképek mappa"
            predicate.value.contains("Download", ignoreCase = true) -> "Letöltések mappa"
            else -> "Mappa: ${predicate.value.trim('/')}"
        }
        is Predicate.OlderThan -> age(predicate.days)
        is Predicate.LargerThan -> "${Format.bytes(predicate.bytes)} fölött"
        is Predicate.MimeIs -> when {
            predicate.prefix.startsWith("video") -> "Videó"
            predicate.prefix.startsWith("image") -> "Kép"
            predicate.prefix.startsWith("audio") -> "Hang"
            else -> predicate.prefix
        }
        is Predicate.ValueClassIs -> when (predicate.valueClass) {
            ValueClass.IRREPLACEABLE -> "Saját fotó"
            ValueClass.REPLACEABLE -> "Pótolható"
            ValueClass.EPHEMERAL -> "Képernyőkép vagy eldobható kép"
        }
        is Predicate.ShotKindIs -> when (predicate.kind) {
            ShotKind.RECEIPT -> "Nyugta"
            ShotKind.TICKET -> "Jegy"
            ShotKind.SECRET -> "Védett adat"
            ShotKind.PLACE -> "Cím vagy térkép"
            ShotKind.CHAT -> "Chat"
            ShotKind.OTHER -> "Egyéb képernyőkép"
        }
        is Predicate.BucketIs -> "Kiválasztott mappa"
        is Predicate.And -> predicate.terms.joinToString(" és ") { condition(it) }
        is Predicate.Or -> predicate.terms.joinToString(" vagy ") { condition(it) }
        is Predicate.Not -> "Nem: ${condition(predicate.term)}"
    }

    private fun age(days: Int): String = when {
        days >= 365 && days % 365 == 0 -> "${Format.count(days / 365)} évnél régebbi"
        days >= 60 && days % 30 == 0 -> "${Format.count(days / 30)} hónapnál régebbi"
        else -> "${Format.count(days)} napnál régebbi"
    }

    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d.", Locale.forLanguageTag("hu-HU"))
}
