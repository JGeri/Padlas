package hu.padlas.core.database.converter

import androidx.room.TypeConverter
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.database.entity.DupKind
import hu.padlas.core.database.entity.JobState
import hu.padlas.core.database.entity.RuleAction
import hu.padlas.core.database.entity.ShotKind
import hu.padlas.core.database.entity.SwipeChoice
import hu.padlas.core.database.entity.ValueClass

/**
 * Enums are stored by name, not by ordinal. Ordinals turn a reordered enum into silent
 * data corruption, and this database holds the only record of what was deleted.
 */
class Converters {

    @TypeConverter fun assetStateToString(value: AssetState): String = value.name

    @TypeConverter fun stringToAssetState(value: String): AssetState = AssetState.valueOf(value)

    @TypeConverter fun valueClassToString(value: ValueClass): String = value.name

    @TypeConverter fun stringToValueClass(value: String): ValueClass = ValueClass.valueOf(value)

    @TypeConverter fun shotKindToString(value: ShotKind?): String? = value?.name

    @TypeConverter fun stringToShotKind(value: String?): ShotKind? = value?.let(ShotKind::valueOf)

    @TypeConverter fun dupKindToString(value: DupKind): String = value.name

    @TypeConverter fun stringToDupKind(value: String): DupKind = DupKind.valueOf(value)

    @TypeConverter fun jobStateToString(value: JobState): String = value.name

    @TypeConverter fun stringToJobState(value: String): JobState = JobState.valueOf(value)

    @TypeConverter fun ruleActionToString(value: RuleAction): String = value.name

    @TypeConverter fun stringToRuleAction(value: String): RuleAction = RuleAction.valueOf(value)

    @TypeConverter fun auditActionToString(value: AuditAction): String = value.name

    @TypeConverter fun stringToAuditAction(value: String): AuditAction = AuditAction.valueOf(value)

    @TypeConverter fun swipeChoiceToString(value: SwipeChoice): String = value.name

    @TypeConverter fun stringToSwipeChoice(value: String): SwipeChoice = SwipeChoice.valueOf(value)
}
