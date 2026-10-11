package hu.padlas.feature.rules

import hu.padlas.core.database.entity.RuleAction
import hu.padlas.core.database.entity.RuleEntity
import hu.padlas.core.database.entity.ValueClass

/**
 * Shipped disabled, on purpose. A rule that starts moving files before the user has seen
 * its dry run would break the one promise this app makes.
 */
object BuiltInRules {

    fun all(): List<RuleEntity> = listOf(
        rule(
            name = "Régi WhatsApp-képek a felhőbe",
            action = RuleAction.ARCHIVE,
            predicate = Predicate.And(
                listOf(
                    Predicate.PathContains("WhatsApp"),
                    Predicate.OlderThan(90),
                ),
            ),
        ),
        rule(
            name = "Egy évnél régebbi fotók a felhőbe",
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
            name = "Nagy videók tömörítése",
            action = RuleAction.TRANSCODE,
            predicate = Predicate.And(
                listOf(
                    Predicate.MimeIs("video/"),
                    Predicate.LargerThan(500L * 1024 * 1024),
                ),
            ),
        ),
        rule(
            name = "Régi képernyőképek az eldobhatók közé",
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
