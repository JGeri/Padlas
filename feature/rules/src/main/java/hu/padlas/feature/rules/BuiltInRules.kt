package hu.padlas.feature.rules

import hu.padlas.core.database.entity.RuleAction
import hu.padlas.core.database.entity.RuleEntity
import hu.padlas.core.database.entity.ValueClass

/**
 * Shipped disabled, on purpose. A rule that starts moving files before the user has seen
 * its dry run would break the one promise this app makes.
 */
object BuiltInRules {

    /**
     * Stored as the rule's name in every database seeded so far, so these are keys now:
     * [RuleCopy] turns them into the screen's language and they stay exactly as they shipped.
     */
    internal const val OLD_WHATSAPP = "Régi WhatsApp-képek a felhőbe"
    internal const val OLD_CAMERA = "Egy évnél régebbi fotók a felhőbe"
    internal const val LARGE_VIDEOS = "Nagy videók tömörítése"
    internal const val OLD_SCREENSHOTS = "Régi képernyőképek az eldobhatók közé"

    fun all(): List<RuleEntity> = listOf(
        rule(
            name = OLD_WHATSAPP,
            action = RuleAction.ARCHIVE,
            predicate = Predicate.And(
                listOf(
                    Predicate.PathContains("WhatsApp"),
                    Predicate.OlderThan(90),
                ),
            ),
        ),
        rule(
            name = OLD_CAMERA,
            action = RuleAction.ARCHIVE,
            predicate = Predicate.And(
                listOf(
                    Predicate.PathContains("DCIM/Camera"),
                    Predicate.OlderThan(365),
                    Predicate.ValueClassIs(ValueClass.IRREPLACEABLE),
                ),
            ),
        ),
        rule(
            name = LARGE_VIDEOS,
            action = RuleAction.TRANSCODE,
            predicate = Predicate.And(
                listOf(
                    Predicate.MimeIs("video/"),
                    Predicate.LargerThan(500L * 1024 * 1024),
                ),
            ),
        ),
        rule(
            name = OLD_SCREENSHOTS,
            action = RuleAction.TRASH,
            predicate = Predicate.And(
                listOf(
                    Predicate.ValueClassIs(ValueClass.EPHEMERAL),
                    Predicate.OlderThan(180),
                ),
            ),
        ),
    )

    private fun rule(name: String, action: RuleAction, predicate: Predicate) = RuleEntity(
        name = name,
        predicateJson = PredicateJson.encode(predicate),
        action = action,
        enabled = false,
        builtIn = true,
    )
}
